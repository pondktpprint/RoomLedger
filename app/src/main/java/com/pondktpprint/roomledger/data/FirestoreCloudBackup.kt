package com.pondktpprint.roomledger.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class FirestoreCloudBackup(private val firestore: FirebaseFirestore) {
    private val transientCodes = setOf(
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.INTERNAL,
    )

    suspend fun upload(
        uid: String,
        encryptedSnapshot: ByteArray,
        snapshotCreatedAtMillis: Long = System.currentTimeMillis(),
        progress: (Float) -> Unit = {},
    ): CloudBackupVersion {
        require(uid.isNotBlank()) { "กรุณาเข้าสู่ระบบก่อนสำรองข้อมูล Cloud" }
        require(encryptedSnapshot.size <= CloudBackupFormat.MAX_BACKUP_BYTES) { "ไฟล์สำรองเกินขนาด Cloud ที่รองรับ (20 MiB)" }
        val (parts, checksum) = withContext(Dispatchers.Default) {
            CloudBackupFormat.split(encryptedSnapshot) to CloudBackupFormat.sha256(encryptedSnapshot)
        }
        val id = UUID.randomUUID().toString()
        val createdAt = snapshotCreatedAtMillis
        val backupRef = firestore.collection("users").document(uid).collection("backups").document(id)
        val manifest = mutableMapOf<String, Any>(
            "backupId" to id,
            "ownerUid" to uid,
            "formatVersion" to RoomLedgerBackup.FORMAT_VERSION,
            "databaseVersion" to RoomLedgerBackup.DATABASE_VERSION,
            "createdAtMillis" to createdAt,
            "checksumSha256" to checksum,
            "chunkCount" to parts.size,
            "totalByteSize" to encryptedSnapshot.size,
            "state" to "UPLOADING",
        )
        var createdManifest = false
        try {
            retry { backupRef.set(manifest).await() }
            createdManifest = true
            for ((offset, part) in parts.withIndex()) {
                val chunkRef = backupRef.collection("chunks").document(chunkId(part.index))
                retry {
                    chunkRef.set(mapOf(
                        "backupId" to id,
                        "ownerUid" to uid,
                        "chunkIndex" to part.index,
                        "totalChunks" to parts.size,
                        "checksumSha256" to part.checksum,
                        "dataBase64" to part.encodedBytes,
                    )).await()
                }
                progress(0.05f + 0.7f * (offset + 1) / parts.size)
            }

            val pendingVersion = CloudBackupVersion(id, createdAt, RoomLedgerBackup.FORMAT_VERSION, RoomLedgerBackup.DATABASE_VERSION, checksum, parts.size, encryptedSnapshot.size, "COMPLETE")
            val verifiedBytes = downloadChunks(uid, backupRef, pendingVersion)
            try { require(withContext(Dispatchers.Default) { verifiedBytes.contentEquals(encryptedSnapshot) }) { "ตรวจสอบข้อมูลสำรอง Cloud ไม่ผ่าน" } }
            finally { verifiedBytes.fill(0) }
            progress(0.9f)

            retry {
                val current = backupRef.get().await()
                val state = current.getString("state")
                if (state != "COMPLETE") {
                    require(state == "UPLOADING") { "ข้อมูลสำรองไม่ได้อยู่ในสถานะอัปโหลด" }
                    backupRef.update(mapOf("state" to "COMPLETE", "completedAtMillis" to System.currentTimeMillis())).await()
                }
            }
            val completed = retry { backupRef.get().await() }
            val completedVersion = completed.data?.let { versionFrom(completed.id, it) }
            require(completed.exists() && completed.getString("ownerUid") == uid && completed.getString("backupId") == id && completedVersion == pendingVersion) { "ยืนยันสถานะสำรอง Cloud ไม่สำเร็จ" }
            progress(1f)
            try { retainLatestThree(uid) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
            cleanStaleIncomplete(uid)
            return pendingVersion
        } catch (failure: Throwable) {
            if (createdManifest) runCatching { retry { backupRef.update("state", "FAILED").await() } }
            throw failure
        }
    }

    /** Complete manifests are exposed only after all chunks and the full snapshot hash verify. */
    suspend fun listVerified(uid: String): List<CloudBackupVersion> = CloudBackupFormat.latestThreeVerified(listAllVerified(uid))

    private suspend fun listAllVerified(uid: String): List<CloudBackupVersion> {
        require(uid.isNotBlank()) { "กรุณาเข้าสู่ระบบก่อนดูข้อมูลสำรอง Cloud" }
        val snapshot = retry {
            firestore.collection("users").document(uid).collection("backups")
                .whereEqualTo("state", "COMPLETE").get().await()
        }
        val verified = mutableListOf<CloudBackupVersion>()
        for (document in snapshot.documents) {
            if (document.getString("ownerUid") != uid || document.getString("backupId") != document.id) continue
            val version = versionFrom(document.id, document.data ?: continue) ?: continue
            if (version.state != "COMPLETE" || version.id != document.id) continue
            try {
                downloadChunks(uid, document.reference, version).fill(0)
                verified += version
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* An incomplete or corrupt version is intentionally hidden. */ }
        }
        return verified.sortedByDescending(CloudBackupVersion::createdAtMillis)
    }

    suspend fun download(uid: String, version: CloudBackupVersion): ByteArray {
        require(version.state == "COMPLETE") { "ไม่สามารถกู้คืนข้อมูลสำรองที่ยังไม่สมบูรณ์" }
        val reference = firestore.collection("users").document(uid).collection("backups").document(version.id)
        val manifest = retry { reference.get().await() }
        require(manifest.exists() && manifest.getString("ownerUid") == uid && manifest.getString("backupId") == version.id && manifest.getString("state") == "COMPLETE") { "ไม่พบข้อมูลสำรองที่สมบูรณ์" }
        val stored = versionFrom(manifest.id, manifest.data ?: error("manifest ไม่ถูกต้อง")) ?: error("manifest ไม่ถูกต้อง")
        require(stored == version) { "ข้อมูลสำรองเปลี่ยนแปลง กรุณาโหลดรายการใหม่" }
        return downloadChunks(uid, reference, version)
    }

    suspend fun deleteIncomplete(uid: String, olderThanMillis: Long = System.currentTimeMillis() - INCOMPLETE_MAX_AGE_MILLIS) {
        val manifests = retry {
            firestore.collection("users").document(uid).collection("backups")
                .whereIn("state", listOf("UPLOADING", "FAILED")).get().await()
        }
        for (document in manifests.documents) {
            val createdAt = document.getLong("createdAtMillis") ?: continue
            if (createdAt <= olderThanMillis && document.getString("ownerUid") == uid) deleteBackup(document.reference)
        }
    }

    private suspend fun cleanStaleIncomplete(uid: String) {
        try { deleteIncomplete(uid) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }

    private suspend fun retainLatestThree(uid: String) {
        val all = listAllVerified(uid)
        val verified = CloudBackupFormat.latestThreeVerified(all)
        val retainedIds = verified.mapTo(mutableSetOf(), CloudBackupVersion::id)
        for (version in all.filter { it.id !in retainedIds }) {
            deleteBackup(firestore.collection("users").document(uid).collection("backups").document(version.id))
        }
    }

    private suspend fun downloadChunks(uid: String, reference: DocumentReference, version: CloudBackupVersion): ByteArray {
        require(version.chunkCount in 1..(CloudBackupFormat.MAX_BACKUP_BYTES / CloudBackupFormat.CHUNK_BYTES)) { "จำนวน chunk ไม่ถูกต้อง" }
        val chunks = ArrayList<CloudBackupChunk>(version.chunkCount)
        for (index in 0 until version.chunkCount) {
            val document = retry { reference.collection("chunks").document(chunkId(index)).get().await() }
            require(document.exists() && document.getString("ownerUid") == uid && document.getString("backupId") == version.id) { "ข้อมูลสำรองมี chunk ที่ขาดหาย" }
            require(document.getLong("chunkIndex") == index.toLong() && document.getLong("totalChunks") == version.chunkCount.toLong()) { "ลำดับ chunk ไม่ถูกต้อง" }
            chunks += CloudBackupChunk(index, document.getString("dataBase64") ?: error("ข้อมูล chunk ว่าง"), document.getString("checksumSha256") ?: error("ไม่มี checksum ของ chunk"))
        }
        return withContext(Dispatchers.Default) { CloudBackupFormat.assemble(version, chunks) }
    }

    private suspend fun deleteBackup(reference: DocumentReference) {
        val chunks = retry { reference.collection("chunks").get().await() }
        for (group in chunks.documents.chunked(400)) {
            val batch = firestore.batch()
            group.forEach { batch.delete(it.reference) }
            retry { batch.commit().await() }
        }
        retry { reference.delete().await() }
    }

    private suspend fun <T> retry(operation: suspend () -> T): T = retryCloudOperation(
        isTransient = { failure -> failure is IOException || (failure is FirebaseFirestoreException && failure.code in transientCodes) },
        operation = operation,
    )

    private fun versionFrom(id: String, fields: Map<String, Any>): CloudBackupVersion? = try {
        val version = CloudBackupVersion(
            id = id,
            createdAtMillis = (fields["createdAtMillis"] as Number).toLong(),
            formatVersion = (fields["formatVersion"] as Number).toInt(),
            databaseVersion = (fields["databaseVersion"] as Number).toInt(),
            checksum = fields["checksumSha256"] as String,
            chunkCount = (fields["chunkCount"] as Number).toInt(),
            totalByteSize = (fields["totalByteSize"] as Number).toInt(),
            state = fields["state"] as String,
        )
        if (version.formatVersion != RoomLedgerBackup.FORMAT_VERSION || version.databaseVersion != RoomLedgerBackup.DATABASE_VERSION ||
            version.chunkCount !in 1..(CloudBackupFormat.MAX_BACKUP_BYTES / CloudBackupFormat.CHUNK_BYTES) ||
            version.totalByteSize !in 1..CloudBackupFormat.MAX_BACKUP_BYTES || !version.checksum.matches(Regex("[0-9a-f]{64}"))) null else version
    } catch (_: Exception) { null }

    private fun chunkId(index: Int) = index.toString().padStart(6, '0')

    companion object {
        private const val INCOMPLETE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
    }
}
