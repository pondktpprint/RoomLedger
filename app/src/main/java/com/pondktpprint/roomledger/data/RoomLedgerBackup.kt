package com.pondktpprint.roomledger.data

import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import androidx.room.withTransaction
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupPreview(
    val createdAtMillis: Long,
    val roomCount: Int,
    val readingCount: Int,
    val billCount: Int,
    val paymentCount: Int,
    val billingMonths: String,
    val groupCount: Int = 0,
)

/** Versioned AES-GCM backup for the existing Room database. No passphrase is persisted. */
object RoomLedgerBackup {
    const val FORMAT_VERSION = 2
    const val DATABASE_VERSION = 4
    const val LEGACY_FORMAT_VERSION = 1
    const val LEGACY_DATABASE_VERSION = 3
    const val PASSPHRASE_MIN_LENGTH = 8
    private const val ITERATIONS = 250_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val MAX_BACKUP_BYTES = 128 * 1024 * 1024
    private val magic = byteArrayOf('R'.code.toByte(), 'L'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private val tables = listOf("room_groups", "rooms", "meter_readings", "bills", "payments", "payment_allocations", "debt_carry_forwards")
    private val legacyTables = listOf("rooms", "meter_readings", "bills", "payments", "payment_allocations", "debt_carry_forwards")
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

    fun supportsBackupVersion(formatVersion: Int, databaseVersion: Int): Boolean =
        (formatVersion == LEGACY_FORMAT_VERSION && databaseVersion == LEGACY_DATABASE_VERSION) ||
            (formatVersion == FORMAT_VERSION && databaseVersion == DATABASE_VERSION)

    suspend fun encryptedBytes(database: RoomLedgerDatabase, passphrase: CharArray, nowMillis: Long = System.currentTimeMillis()): ByteArray {
        val output = ByteArrayOutputStream()
        write(database, output, passphrase, nowMillis)
        return output.toByteArray()
    }

    suspend fun latestLocalChangeAt(database: RoomLedgerDatabase): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            var latest = 0L
            for ((table, column) in listOf("room_groups" to "updatedAtMillis", "rooms" to "updatedAtMillis", "meter_readings" to "updatedAtMillis", "bills" to "updatedAtMillis", "payments" to "updatedAtMillis", "payment_allocations" to "createdAtMillis", "debt_carry_forwards" to "updatedAtMillis")) {
                database.openHelper.writableDatabase.query("SELECT COALESCE(MAX(`$column`), 0) FROM `$table`").use { cursor ->
                    if (cursor.moveToFirst()) latest = maxOf(latest, cursor.getLong(0))
                }
            }
            latest
        }
    }

    suspend fun write(database: RoomLedgerDatabase, output: OutputStream, passphrase: CharArray, nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        requirePassphrase(passphrase)
        val snapshot = database.withTransaction { snapshotJson(database) }
        val plain = makeEnvelope(snapshot, nowMillis).toByteArray(Charsets.UTF_8)
        try {
            require(plain.size <= MAX_BACKUP_BYTES) { "ข้อมูลสำรองมีขนาดใหญ่เกินกำหนด" }
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
            val header = ByteBuffer.allocate(4 + 4 + 4 + 16 + 12).order(ByteOrder.BIG_ENDIAN)
                .put(magic).putInt(FORMAT_VERSION).putInt(ITERATIONS).put(salt).put(nonce).array()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, ITERATIONS), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(header)
            val encrypted = cipher.doFinal(plain)
            try {
                require(header.size + encrypted.size <= MAX_BACKUP_BYTES) { "ข้อมูลสำรองมีขนาดใหญ่เกินกำหนด" }
                output.use { it.write(header); it.write(encrypted); it.flush() }
            } finally { encrypted.fill(0) }
        } finally { plain.fill(0) }
    }

    suspend fun inspect(database: RoomLedgerDatabase, input: InputStream, passphrase: CharArray): BackupPreview =
        withContext(Dispatchers.IO) { decodeAndValidate(database, readLimited(input), passphrase) }

    suspend fun inspect(database: RoomLedgerDatabase, bytes: ByteArray, passphrase: CharArray): BackupPreview =
        withContext(Dispatchers.IO) { decodeAndValidate(database, bytes, passphrase) }

    fun readBackup(input: InputStream): ByteArray = readLimited(input)

    /** Revalidates before opening a single atomic transaction. A failure rolls back the old database. */
    suspend fun restore(database: RoomLedgerDatabase, bytes: ByteArray, passphrase: CharArray) {
        val decoded = withContext(Dispatchers.IO) { decodeSnapshot(database, bytes, passphrase) }
        database.withTransaction {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("PRAGMA defer_foreign_keys = ON")
            listOf("payment_allocations", "payments", "debt_carry_forwards", "bills", "meter_readings", "rooms", "room_groups").forEach { sql.execSQL("DELETE FROM `$it`") }
            for (table in tables) {
                val rows = decoded.getJSONObject("tables").getJSONArray(table)
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val values = ContentValues()
                    val keys = row.keys()
                    while (keys.hasNext()) {
                        val column = keys.next()
                        val value = row.get(column)
                        if (value == JSONObject.NULL) values.putNull(column)
                        else if (value is JSONObject && value.optString("$type") == "blob") values.put(column, Base64.decode(value.getString("data"), Base64.NO_WRAP))
                        else when (value) {
                            is Boolean -> values.put(column, if (value) 1 else 0)
                            is Int -> values.put(column, value)
                            is Long -> values.put(column, value)
                            is Number -> values.put(column, value.toDouble())
                            is String -> values.put(column, value)
                            else -> error("ชนิดข้อมูลสำรองไม่รองรับ")
                        }
                    }
                    check(sql.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values) != -1L) { "นำเข้าตาราง $table ไม่สำเร็จ" }
                }
            }
            sql.query("PRAGMA foreign_key_check").use { cursor -> check(!cursor.moveToFirst()) { "ข้อมูลสำรองมีความสัมพันธ์ที่ไม่ถูกต้อง" } }
        }
    }

    private suspend fun decodeAndValidate(database: RoomLedgerDatabase, bytes: ByteArray, passphrase: CharArray): BackupPreview {
        val decoded = decodeSnapshot(database, bytes, passphrase)
        return previewFrom(decoded)
    }

    private fun decodeSnapshot(database: RoomLedgerDatabase, bytes: ByteArray, passphrase: CharArray): JSONObject {
        requirePassphrase(passphrase)
        require(bytes.size in (HEADER_SIZE + 16)..MAX_BACKUP_BYTES) { "ไฟล์สำรองเสียหายหรือมีขนาดไม่ถูกต้อง" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val header = ByteArray(HEADER_SIZE).also(buffer::get)
        val parsed = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val actualMagic = ByteArray(4).also(parsed::get)
        require(actualMagic.contentEquals(magic)) { "ไม่ใช่ไฟล์สำรอง RoomLedger" }
        val headerFormatVersion = parsed.int
        require(headerFormatVersion in LEGACY_FORMAT_VERSION..FORMAT_VERSION) { "ไม่รองรับไฟล์สำรองเวอร์ชันนี้" }
        val iterations = parsed.int
        require(iterations in 100_000..1_000_000) { "รูปแบบไฟล์สำรองไม่ถูกต้อง" }
        val salt = ByteArray(16).also(parsed::get)
        val nonce = ByteArray(12).also(parsed::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(header)
        val plain = try { cipher.doFinal(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE) }
        catch (_: AEADBadTagException) { throw IllegalArgumentException("ถอดรหัสไม่สำเร็จ: รหัสผ่านไม่ถูกต้องหรือไฟล์ถูกแก้ไข") }
        try {
            val wrapper = JSONObject(String(plain, Charsets.UTF_8))
            val payloadText = wrapper.getString("payload")
            val payload = payloadText.toByteArray(Charsets.UTF_8)
            val expectedHash = wrapper.getString("sha256")
            val actualHash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
            require(MessageDigest.isEqual(expectedHash.toByteArray(), actualHash.toByteArray())) { "ตรวจสอบความถูกต้องของไฟล์ไม่ผ่าน" }
            val snapshot = JSONObject(payloadText)
            return validateAndNormalizeSnapshot(database, snapshot, headerFormatVersion)
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { throw IllegalArgumentException("ไฟล์สำรองเสียหายหรือข้อมูลไม่ครบถ้วน", e) }
        finally { plain.fill(0) }
    }

    private fun validateAndNormalizeSnapshot(database: RoomLedgerDatabase, snapshot: JSONObject, headerFormatVersion: Int): JSONObject {
        val payloadFormatVersion = snapshot.getInt("formatVersion")
        val sourceDatabaseVersion = snapshot.getInt("databaseVersion")
        require(payloadFormatVersion == headerFormatVersion) { "เวอร์ชันไฟล์สำรองไม่ตรงกัน" }
        require(snapshot.getString("applicationId") == "com.pondktpprint.roomledger") { "ไฟล์นี้ไม่ได้สำรองจาก RoomLedger" }
        require(snapshot.getString("databaseName") == "room-ledger.db") { "ไฟล์นี้ไม่ได้สำรองจากฐานข้อมูล RoomLedger" }

        val isLegacy = payloadFormatVersion == LEGACY_FORMAT_VERSION && sourceDatabaseVersion == LEGACY_DATABASE_VERSION
        require(supportsBackupVersion(payloadFormatVersion, sourceDatabaseVersion)) {
            "เวอร์ชันฐานข้อมูลไม่เข้ากัน กรุณาใช้แอปเวอร์ชันที่รองรับ"
        }
        val tableData = snapshot.getJSONObject("tables")
        val sourceTables = if (isLegacy) legacyTables else tables
        require(tableData.keys().asSequence().toSet() == sourceTables.toSet()) { "ไฟล์สำรองมีรายการตารางไม่ครบ" }
        val sourceColumns = snapshot.getJSONObject("columns")
        require(sourceColumns.keys().asSequence().toSet() == sourceTables.toSet()) { "ไฟล์สำรองมีโครงสร้างตารางไม่ครบ" }
        val liveDb = database.openHelper.writableDatabase
        val liveColumnsByTable = mutableMapOf<String, List<String>>()
        for (table in sourceTables) {
            val liveColumns = liveDb.query("PRAGMA table_info(`$table`)").use(::columnNames)
            val expectedColumns = if (isLegacy && table == "rooms") liveColumns - "groupId" else liveColumns
            val storedColumns = snapshot.getJSONObject("columns").getJSONArray(table).let { arr -> (0 until arr.length()).map(arr::getString).toSet() }
            require(storedColumns == expectedColumns) { "โครงสร้างตาราง $table ไม่เข้ากัน" }
            liveColumnsByTable[table] = liveDb.query("PRAGMA table_info(`$table`)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            val rows = tableData.getJSONArray(table)
            for (i in 0 until rows.length()) require(rows.getJSONObject(i).keys().asSequence().toSet() == storedColumns) { "ข้อมูลตาราง $table ไม่ครบถ้วน" }
        }

        if (isLegacy) {
            val upgradedRooms = JSONArray()
            val legacyRooms = tableData.getJSONArray("rooms")
            for (index in 0 until legacyRooms.length()) upgradedRooms.put(legacyRooms.getJSONObject(index).put("groupId", JSONObject.NULL))
            tableData.put("rooms", upgradedRooms)
            tableData.put("room_groups", JSONArray())

            val upgradedColumns = JSONObject()
            for (table in tables) {
                val names = when (table) {
                    "room_groups" -> listOf("id", "name", "sortOrder", "createdAtMillis", "updatedAtMillis")
                    "rooms" -> liveColumnsByTable.getValue(table)
                    else -> snapshot.getJSONObject("columns").getJSONArray(table).let { arr -> (0 until arr.length()).map(arr::getString) }
                }
                upgradedColumns.put(table, JSONArray(names))
            }
            snapshot.put("columns", upgradedColumns)
            snapshot.put("formatVersion", FORMAT_VERSION)
            snapshot.put("databaseVersion", DATABASE_VERSION)
        }

        val normalizedTables = snapshot.getJSONObject("tables")
        for (table in tables) {
            val liveColumns = liveDb.query("PRAGMA table_info(`$table`)").use(::columnNames)
            val storedColumns = snapshot.getJSONObject("columns").getJSONArray(table).let { arr -> (0 until arr.length()).map(arr::getString).toSet() }
            require(storedColumns == liveColumns) { "โครงสร้างตาราง $table ไม่เข้ากัน" }
            val rows = normalizedTables.getJSONArray(table)
            for (index in 0 until rows.length()) require(rows.getJSONObject(index).keys().asSequence().toSet() == liveColumns) { "ข้อมูลตาราง $table ไม่ครบถ้วน" }
        }
        validateRelations(tableData)
        return snapshot
    }

    private fun validateRelations(data: JSONObject) {
        fun rows(table: String) = data.getJSONArray(table).let { arr -> (0 until arr.length()).map(arr::getJSONObject) }
        fun ids(table: String) = rows(table).map { it.getString("id") }.toSet()
        fun requireUnique(rows: List<JSONObject>, key: (JSONObject) -> String, message: String) {
            require(rows.map(key).toSet().size == rows.size) { message }
        }
        val roomIds = ids("rooms")
        val groupRows = rows("room_groups")
        requireUnique(groupRows, { it.getString("id") }, "พบรหัสกลุ่มซ้ำ")
        val groupIds = groupRows.map { it.getString("id") }.toSet()
        for (row in groupRows) require(row.getString("name").isNotBlank() && row.getInt("sortOrder") >= 0) { "ข้อมูลกลุ่มห้องไม่ถูกต้อง" }
        val readingRows = rows("meter_readings")
        requireUnique(rows("rooms"), { it.getString("id") }, "พบรหัสห้องซ้ำ")
        requireUnique(readingRows, { it.getString("id") }, "พบรหัสมิเตอร์ซ้ำ")
        val readingIds = readingRows.map { it.getString("id") }.toSet()
        val readingById = readingRows.associateBy { it.getString("id") }
        val billRows = rows("bills")
        requireUnique(billRows, { it.getString("id") }, "พบรหัสบิลซ้ำ")
        requireUnique(billRows, { it.getString("readingId") }, "พบรายการบิลซ้ำ")
        val billIds = billRows.map { it.getString("id") }.toSet()
        val paymentRows = rows("payments")
        requireUnique(paymentRows, { it.getString("id") }, "พบรหัสรายการรับเงินซ้ำ")
        val reversedPayments = paymentRows.mapNotNull { row -> row.optString("reversesPaymentId").takeUnless { it.isEmpty() || it == "null" } }
        require(reversedPayments.toSet().size == reversedPayments.size) { "พบรายการกลับเงินซ้ำ" }
        val paymentIds = paymentRows.map { it.getString("id") }.toSet()
        val paymentById = paymentRows.associateBy { it.getString("id") }
        val allocationRows = rows("payment_allocations")
        requireUnique(allocationRows, { "${it.getString("paymentId")}\u0000${it.getString("sourceBillId")}" }, "พบรายการจัดสรรเงินซ้ำ")
        val allocationsByPayment = allocationRows.groupBy { it.getString("paymentId") }
        val tupleMonths = mutableSetOf<String>()
        for (row in readingRows) {
            require(row.getString("roomId") in roomIds) { "มิเตอร์อ้างอิงห้องที่ไม่มีอยู่" }
            require(tupleMonths.add("${row.getString("roomId")}\u0000${row.getString("billingMonth")}")) { "พบรอบมิเตอร์ซ้ำ" }
            requireValidMonth(row.getString("billingMonth"))
        }
        for (row in rows("rooms")) {
            val groupId = row.opt("groupId")
            require(groupId == null || groupId == JSONObject.NULL || groupId.toString() in groupIds) { "ห้องอ้างอิงกลุ่มที่ไม่มีอยู่" }
        }
        tupleMonths.clear()
        val billRoomById = mutableMapOf<String, String>()
        for (row in billRows) {
            require(row.getString("roomId") in roomIds && row.getString("readingId") in readingIds) { "บิลอ้างอิงข้อมูลที่ไม่มีอยู่" }
            val reading = readingById.getValue(row.getString("readingId"))
            require(reading.getString("roomId") == row.getString("roomId") && reading.getString("billingMonth") == row.getString("billingMonth")) { "บิลและเลขมิเตอร์อ้างอิงไม่ตรงกัน" }
            require(tupleMonths.add("${row.getString("roomId")}\u0000${row.getString("billingMonth")}")) { "พบบิลซ้ำในรอบเดียวกัน" }
            requireValidMonth(row.getString("billingMonth"))
            val monthlyTotal = Math.addExact(row.getLong("totalSatang"), row.getLong("carriedDebtSatang"))
            require(row.getLong("totalSatang") >= 0 && row.getLong("carriedDebtSatang") >= 0 && monthlyTotal == row.getLong("totalDueSatang")) { "ยอดบิลในไฟล์สำรองไม่สอดคล้องกัน" }
            billRoomById[row.getString("id")] = row.getString("roomId")
        }
        for (row in paymentRows) {
            require(row.getString("billId") in billIds && row.getLong("amountSatang") > 0L) { "รายการรับเงินไม่ถูกต้อง" }
            val reversal = row.optString("reversesPaymentId").takeUnless { it.isEmpty() || it == "null" }
            if (reversal != null) {
                val original = paymentById[reversal]
                require(original != null && original.getString("entryType") == "RECEIPT" && row.getString("entryType") == "REVERSAL" && original.getString("billId") == row.getString("billId") && original.getLong("amountSatang") == row.getLong("amountSatang")) { "รายการกลับเงินอ้างอิงไม่ถูกต้อง" }
            } else require(row.getString("entryType") == "RECEIPT") { "ประเภทรายการรับเงินไม่ถูกต้อง" }
            val allocated = allocationsByPayment[row.getString("id")].orEmpty().fold(0L) { sum, allocation -> Math.addExact(sum, allocation.getLong("amountSatang")) }
            val expected = if (reversal == null) row.getLong("amountSatang") else Math.negateExact(row.getLong("amountSatang"))
            require(allocated == expected) { "ยอดจัดสรรของรายการชำระไม่สอดคล้องกัน" }
        }
        val allocatedBySource = mutableMapOf<String, Long>()
        for (row in allocationRows) {
            require(row.getString("paymentId") in paymentIds && row.getString("sourceBillId") in billIds) { "รายการจัดสรรเงินอ้างอิงไม่ถูกต้อง" }
            val source = row.getString("sourceBillId")
            val payment = paymentById.getValue(row.getString("paymentId"))
            require(billRoomById[payment.getString("billId")] == billRoomById[source]) { "รายการจัดสรรเงินข้ามห้องไม่ถูกต้อง" }
            allocatedBySource[source] = Math.addExact(allocatedBySource[source] ?: 0L, row.getLong("amountSatang"))
        }
        for ((source, allocated) in allocatedBySource) {
            val bill = billRows.first { it.getString("id") == source }
            require(allocated in 0L..bill.getLong("totalSatang")) { "ยอดจัดสรรเกินยอดหนี้ต้นทาง" }
        }
        val carries = rows("debt_carry_forwards")
        requireUnique(carries, { "${it.getString("sourceBillId")}\u0000${it.getString("targetMonth")}" }, "พบรายการยกหนี้ซ้ำ")
        for (row in carries) {
            val source = row.getString("sourceBillId")
            val target = row.optString("targetBillId").takeUnless { it.isEmpty() || it == "null" }
            require(source in billIds && row.getString("roomId") in roomIds && billRoomById[source] == row.getString("roomId")) { "รายการยกหนี้อ้างอิงบิลไม่ถูกต้อง" }
            require(row.getLong("amountSatang") >= 0L) { "ยอดยกหนี้ติดลบ" }
            requireValidMonth(row.getString("targetMonth"))
            val sourceMonth = billRows.first { it.getString("id") == source }.getString("billingMonth")
            require(row.getString("targetMonth") > sourceMonth) { "รอบบิลปลายทางต้องอยู่หลังบิลต้นทาง" }
            if (target != null) {
                val targetBill = billRows.firstOrNull { it.getString("id") == target }
                require(targetBill != null && billRoomById[target] == row.getString("roomId") && targetBill.getString("billingMonth") == row.getString("targetMonth")) { "รายการยกหนี้อ้างอิงบิลปลายทางไม่ถูกต้อง" }
            }
        }
    }

    private fun requireValidMonth(value: String) {
        require(Regex("\\d{4}-(0[1-9]|1[0-2])").matches(value)) { "รอบบิลในไฟล์สำรองไม่ถูกต้อง" }
    }

    private fun snapshotJson(database: RoomLedgerDatabase): JSONObject {
        val db = database.openHelper.writableDatabase
        check(db.version == DATABASE_VERSION) { "ไม่รองรับเวอร์ชันฐานข้อมูลนี้" }
        val data = JSONObject()
        val columns = JSONObject()
        for (table in tables) {
            val rows = JSONArray()
            db.query("SELECT * FROM `$table`").use { cursor ->
                val columnList = (0 until cursor.columnCount).map(cursor::getColumnName)
                columns.put(table, JSONArray(columnList))
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (i in columnList.indices) {
                        val value: Any = when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                            Cursor.FIELD_TYPE_BLOB -> JSONObject().put("$type", "blob").put("data", Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP))
                            else -> error("ชนิดข้อมูลฐานข้อมูลไม่รองรับ")
                        }
                        row.put(columnList[i], value)
                    }
                    rows.put(row)
                }
            }
            data.put(table, rows)
        }
        return JSONObject().put("formatVersion", FORMAT_VERSION).put("applicationId", "com.pondktpprint.roomledger").put("databaseName", "room-ledger.db")
            .put("databaseVersion", db.version).put("columns", columns).put("tables", data).put("appSettings", JSONObject())
    }

    private fun makeEnvelope(snapshot: JSONObject, now: Long): String {
        val payload = snapshot.put("createdAtMillis", now).toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return JSONObject().put("sha256", hash).put("payload", payload).toString()
    }

    private fun previewFrom(snapshot: JSONObject): BackupPreview {
        val tables = snapshot.getJSONObject("tables")
        val months = tables.getJSONArray("bills").let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).getString("billingMonth") }.distinct().sorted() }
        return BackupPreview(snapshot.getLong("createdAtMillis"), tables.getJSONArray("rooms").length(), tables.getJSONArray("meter_readings").length(), tables.getJSONArray("bills").length(), tables.getJSONArray("payments").length(), months.joinToString(", "), tables.getJSONArray("room_groups").length())
    }

    private fun columnNames(cursor: Cursor): Set<String> = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }

    private fun readLimited(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val size = it.read(buffer)
            if (size < 0) break
            total += size
            require(total <= MAX_BACKUP_BYTES) { "ไฟล์สำรองมีขนาดใหญ่เกินกำหนด" }
            output.write(buffer, 0, size)
        }
        output.toByteArray()
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(encoded, "AES") } finally { encoded.fill(0) }
        }
        finally { spec.clearPassword() }
    }

    private fun requirePassphrase(passphrase: CharArray) {
        require(passphrase.size >= PASSPHRASE_MIN_LENGTH) { "รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร" }
    }

    fun formattedDate(timeMillis: Long): String = formatter.format(Instant.ofEpochMilli(timeMillis))

    private val HEADER_SIZE = 4 + 4 + 4 + 16 + 12
    private const val type = "__roomledger_type"
}
