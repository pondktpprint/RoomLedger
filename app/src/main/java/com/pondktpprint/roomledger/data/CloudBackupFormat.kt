package com.pondktpprint.roomledger.data

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.delay

data class CloudBackupVersion(
    val id: String,
    val createdAtMillis: Long,
    val formatVersion: Int,
    val databaseVersion: Int,
    val checksum: String,
    val chunkCount: Int,
    val totalByteSize: Int,
    val state: String,
)

data class CloudBackupChunk(val index: Int, val encodedBytes: String, val checksum: String)

object CloudBackupFormat {
    const val CHUNK_BYTES = 512 * 1024
    const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun split(bytes: ByteArray): List<CloudBackupChunk> {
        require(bytes.isNotEmpty()) { "ไฟล์สำรองว่าง" }
        require(bytes.size <= MAX_BACKUP_BYTES) { "ไฟล์สำรองเกินขนาด Cloud ที่รองรับ (20 MiB)" }
        val count = (bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES
        return (0 until count).map { index ->
            val start = index * CHUNK_BYTES
            val data = bytes.copyOfRange(start, minOf(start + CHUNK_BYTES, bytes.size))
            CloudBackupChunk(index, Base64.getEncoder().encodeToString(data), sha256(data)).also { data.fill(0) }
        }
    }

    fun latestThreeVerified(versions: List<CloudBackupVersion>): List<CloudBackupVersion> =
        versions.asSequence().filter { it.state == "COMPLETE" }.sortedByDescending(CloudBackupVersion::createdAtMillis).take(3).toList()

    fun assemble(version: CloudBackupVersion, chunks: List<CloudBackupChunk>): ByteArray {
        require(version.state == "COMPLETE") { "ข้อมูลสำรองยังไม่สมบูรณ์" }
        require(version.chunkCount > 0 && version.totalByteSize in 1..MAX_BACKUP_BYTES) { "manifest สำรองไม่ถูกต้อง" }
        require(chunks.size == version.chunkCount) { "ไฟล์สำรองมี chunk ไม่ครบ" }
        val ordered = chunks.sortedBy(CloudBackupChunk::index)
        require(ordered.map(CloudBackupChunk::index) == (0 until version.chunkCount).toList()) { "ลำดับ chunk ไม่ครบหรือซ้ำ" }
        val output = java.io.ByteArrayOutputStream(version.totalByteSize)
        for (chunk in ordered) {
            val bytes = try { Base64.getDecoder().decode(chunk.encodedBytes) }
            catch (_: IllegalArgumentException) { throw IllegalArgumentException("ข้อมูล chunk เสียหาย") }
            require(sha256(bytes) == chunk.checksum) { "ตรวจสอบ chunk ไม่ผ่าน" }
            output.write(bytes)
            bytes.fill(0)
        }
        val result = output.toByteArray()
        require(result.size == version.totalByteSize && sha256(result) == version.checksum) { "ตรวจสอบไฟล์สำรอง Cloud ไม่ผ่าน" }
        return result
    }
}

/** Retries only operations the caller has classified as transient. No credentials are persisted. */
suspend fun <T> retryCloudOperation(
    maxAttempts: Int = 3,
    initialDelayMillis: Long = 400,
    isTransient: (Throwable) -> Boolean,
    operation: suspend () -> T,
): T {
    require(maxAttempts >= 1)
    var waitMillis = initialDelayMillis
    var attempt = 1
    while (true) {
        try { return operation() }
        catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException || attempt >= maxAttempts || !isTransient(failure)) throw failure
            delay(waitMillis)
            waitMillis = (waitMillis * 2).coerceAtMost(5_000)
            attempt++
        }
    }
}
