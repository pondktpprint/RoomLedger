package com.pondktpprint.roomledger.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupFormatTest {
    @Test fun chunksReassembleInIndexOrderAndVerifyFullChecksum() {
        val payload = ByteArray(CloudBackupFormat.CHUNK_BYTES * 2 + 17) { (it * 31).toByte() }
        val chunks = CloudBackupFormat.split(payload).reversed()
        assertEquals(3, chunks.size)
        val version = CloudBackupVersion("backup", 100, 1, 3, CloudBackupFormat.sha256(payload), chunks.size, payload.size, "COMPLETE")
        assertTrue(payload.contentEquals(CloudBackupFormat.assemble(version, chunks)))
    }

    @Test fun missingChunkChecksumMismatchAndIncompleteManifestAreRejected() {
        val payload = ByteArray(CloudBackupFormat.CHUNK_BYTES + 5) { it.toByte() }
        val chunks = CloudBackupFormat.split(payload)
        val complete = CloudBackupVersion("backup", 100, 1, 3, CloudBackupFormat.sha256(payload), chunks.size, payload.size, "COMPLETE")
        assertThrows(IllegalArgumentException::class.java) { CloudBackupFormat.assemble(complete, chunks.dropLast(1)) }
        assertThrows(IllegalArgumentException::class.java) { CloudBackupFormat.assemble(complete.copy(state = "UPLOADING"), chunks) }
        assertThrows(IllegalArgumentException::class.java) { CloudBackupFormat.assemble(complete, chunks.mapIndexed { index, chunk -> if (index == 0) chunk.copy(checksum = "0".repeat(64)) else chunk }) }
    }

    @Test fun cloudHistoryKeepsOnlyThreeNewestVerifiedVersions() {
        val versions = (1L..5L).map { CloudBackupVersion("$it", it, 1, 3, "a".repeat(64), 1, 10, "COMPLETE") } +
            CloudBackupVersion("incomplete", 99, 1, 3, "b".repeat(64), 1, 10, "UPLOADING")
        assertEquals(listOf("5", "4", "3"), CloudBackupFormat.latestThreeVerified(versions).map(CloudBackupVersion::id))
    }

    @Test fun cloudAcceptsCurrentAndLegacyBackupPairsOnly() {
        assertTrue(RoomLedgerBackup.supportsBackupVersion(1, 3))
        assertTrue(RoomLedgerBackup.supportsBackupVersion(2, 4))
        assertEquals(false, RoomLedgerBackup.supportsBackupVersion(1, 4))
        assertEquals(false, RoomLedgerBackup.supportsBackupVersion(2, 3))
    }

    @Test fun transientUploadOperationsAreRetriedButPermanentFailuresAreNot() = runBlocking {
        var attempts = 0
        val result = retryCloudOperation(maxAttempts = 3, initialDelayMillis = 0, isTransient = { it is IllegalStateException }) {
            attempts++
            if (attempts < 3) throw IllegalStateException("temporary")
            "uploaded"
        }
        assertEquals("uploaded", result)
        assertEquals(3, attempts)

        attempts = 0
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                retryCloudOperation(maxAttempts = 3, initialDelayMillis = 0, isTransient = { false }) {
                    attempts++
                    throw IllegalArgumentException("permission denied")
                }
            }
        }
        assertEquals(1, attempts)
    }
}
