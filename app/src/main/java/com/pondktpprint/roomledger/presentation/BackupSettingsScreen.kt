package com.pondktpprint.roomledger.presentation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pondktpprint.roomledger.data.BackupPreview
import com.pondktpprint.roomledger.data.RoomLedgerBackup
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(database: RoomLedgerDatabase, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("roomledger_backup", 0) }
    var lastBackup by remember { mutableStateOf(preferences.getLong("last_backup_at", 0L)) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var restorePasswordError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var pendingBackupPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingRestorePassword by remember { mutableStateOf<CharArray?>(null) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            pendingBackupPassword?.fill('\u0000')
            pendingRestorePassword?.fill('\u0000')
            restoreBytes?.fill(0)
        }
    }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.roomledger.backup")) { uri: Uri? ->
        val secret = pendingBackupPassword
        pendingBackupPassword = null
        if (uri == null || secret == null) { secret?.fill('\u0000'); if (uri == null) status = "ยกเลิกการเลือกตำแหน่งบันทึก" }
        else scope.launch {
            busy = true
            runCatching {
                val stream = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri) } ?: error("เปิดไฟล์ปลายทางไม่ได้")
                RoomLedgerBackup.write(database, stream, secret)
                val now = System.currentTimeMillis()
                preferences.edit().putLong("last_backup_at", now).apply()
                lastBackup = now
            }.onSuccess { status = "สำรองข้อมูลเรียบร้อย ไฟล์ถูกเข้ารหัสและตรวจสอบความถูกต้องแล้ว" }
                .onFailure { context.contentResolver.delete(uri, null, null); status = "สำรองข้อมูลไม่สำเร็จ กรุณาเลือกตำแหน่งอื่นหรือลองอีกครั้ง" }
            secret.fill('\u0000')
            busy = false
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) status = "ยกเลิกการเลือกไฟล์สำรอง"
        else scope.launch {
            busy = true
            runCatching {
                val stream = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) } ?: error("เปิดไฟล์สำรองไม่ได้")
                restoreBytes = withContext(Dispatchers.IO) { RoomLedgerBackup.readBackup(stream) }
                password = ""; restorePasswordError = null
                showRestorePassword = true
            }.onFailure { status = "อ่านไฟล์สำรองไม่สำเร็จ กรุณาตรวจสอบสิทธิ์เข้าถึงไฟล์" }
            busy = false
        }
    }

    fun clearRestoreSecrets() {
        pendingRestorePassword?.fill('\u0000')
        pendingRestorePassword = null
        restoreBytes?.fill(0)
        restoreBytes = null
        preview = null
        confirmRestore = false
    }

    BackHandler(enabled = !busy) { if (confirmRestore || preview != null) clearRestoreSecrets() else onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text("สำรองและกู้คืนข้อมูล") }, navigationIcon = { TextButton(onClick = onBack, enabled = !busy) { Text("‹ กลับ") } }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CloudBackupSettingsSection(database)
            HorizontalDivider()
            Text("สำรองและกู้คืนไฟล์ในเครื่อง", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("สำรองข้อมูลไว้ในที่ปลอดภัย และใช้รหัสผ่านเดียวกันเมื่อต้องการกู้คืน", style = MaterialTheme.typography.bodyMedium)
            Text("สำรองข้อมูลล่าสุด: ${if (lastBackup > 0) RoomLedgerBackup.formattedDate(lastBackup) else "ยังไม่มีประวัติการสำรอง"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { password = ""; showBackupPassword = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("สำรองข้อมูล") }
            OutlinedButton(onClick = { openDocument.launch(arrayOf("application/vnd.roomledger.backup", "application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("กู้คืนข้อมูล") }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text("กำลังประมวลผลข้อมูลอย่างปลอดภัย…") }
            status?.let { Text(it, color = if (it.contains("ไม่สำเร็จ") || it.contains("ไม่ได้")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(4.dp))
            Text("ไฟล์สำรองถูกเข้ารหัสด้วยรหัสผ่านที่คุณกำหนด รหัสผ่านจะไม่ถูกบันทึกในแอป หากลืมรหัสผ่านจะไม่สามารถกู้คืนไฟล์ได้", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showBackupPassword) AlertDialog(
        onDismissRequest = { showBackupPassword = false; password = "" },
        title = { Text("ตั้งรหัสผ่านไฟล์สำรอง") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("ใช้รหัสผ่านอย่างน้อย 8 ตัวอักษร และเก็บไว้ให้ปลอดภัย") ; SecretField(password) { password = it } } },
        confirmButton = { Button(enabled = password.length >= RoomLedgerBackup.PASSPHRASE_MIN_LENGTH, onClick = {
            pendingBackupPassword = password.toCharArray(); password = ""; showBackupPassword = false
            createDocument.launch("RoomLedger-${System.currentTimeMillis()}.rlbk")
        }) { Text("เลือกที่บันทึก") } },
        dismissButton = { TextButton(onClick = { showBackupPassword = false; password = "" }) { Text("ยกเลิก") } },
    )

    if (showRestorePassword) AlertDialog(
        onDismissRequest = { showRestorePassword = false; password = ""; restoreBytes?.fill(0); restoreBytes = null },
        title = { Text("ใส่รหัสผ่านไฟล์สำรอง") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("ไฟล์จะถูกตรวจสอบก่อนแสดงตัวอย่างและก่อนแทนที่ข้อมูล") ; SecretField(password) { password = it }; restorePasswordError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { Button(enabled = password.length >= RoomLedgerBackup.PASSPHRASE_MIN_LENGTH && !busy, onClick = {
            val secret = password.toCharArray(); password = ""; showRestorePassword = false; busy = true
            scope.launch {
                runCatching { RoomLedgerBackup.inspect(database, restoreBytes ?: error("ไม่พบไฟล์สำรอง"), secret) }
                    .onSuccess { preview = it; pendingRestorePassword = secret; status = null }
                    .onFailure { restorePasswordError = it.message ?: "ตรวจสอบไฟล์สำรองไม่สำเร็จ"; secret.fill('\u0000'); showRestorePassword = true }
                busy = false
            }
        }) { Text("ตรวจสอบไฟล์") } },
        dismissButton = { TextButton(onClick = { showRestorePassword = false; password = ""; restoreBytes?.fill(0); restoreBytes = null }) { Text("ยกเลิก") } },
    )

    preview?.takeIf { !confirmRestore }?.let { info -> AlertDialog(
        onDismissRequest = { clearRestoreSecrets() },
        title = { Text("ตรวจสอบข้อมูลสำรองแล้ว") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("วันที่สำรอง: ${RoomLedgerBackup.formattedDate(info.createdAtMillis)}")
            Text("ห้อง ${info.roomCount} · มิเตอร์ ${info.readingCount} · บิล ${info.billCount} · รายการรับเงิน ${info.paymentCount}")
            if (info.billingMonths.isNotBlank()) Text("รอบบิล: ${info.billingMonths}")
            Text("การกู้คืนจะแทนที่ข้อมูลทั้งหมดในอุปกรณ์นี้ หลังยืนยันแล้วจะไม่รวมกับข้อมูลปัจจุบัน", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(enabled = !busy, onClick = { confirmRestore = true }) { Text("ดำเนินการต่อ") } },
        dismissButton = { TextButton(onClick = { clearRestoreSecrets() }) { Text("ยกเลิก") } },
    ) }

    if (confirmRestore) AlertDialog(
        onDismissRequest = { if (!busy) confirmRestore = false },
        title = { Text("ยืนยันแทนที่ข้อมูล") },
        text = { Text("ยืนยันกู้คืนข้อมูลสำรองนี้และแทนที่ห้อง บิล ประวัติรับเงิน และยอดยกหนี้ทั้งหมดในเครื่องหรือไม่") },
        confirmButton = { Button(enabled = !busy, onClick = {
            val bytes = restoreBytes?.copyOf() ?: return@Button
            val secret = pendingRestorePassword ?: return@Button
            confirmRestore = false; busy = true
            scope.launch {
                runCatching { RoomLedgerBackup.restore(database, bytes, secret) }
                    .onSuccess { status = "กู้คืนข้อมูลเรียบร้อย" }
                    .onFailure { status = "กู้คืนไม่สำเร็จ ข้อมูลเดิมยังคงอยู่ กรุณาตรวจสอบไฟล์สำรองและลองอีกครั้ง" }
                bytes.fill(0); clearRestoreSecrets(); busy = false
            }
        }) { Text("ยืนยันกู้คืนและแทนที่") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("กลับ") } },
    )
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text("รหัสผ่าน") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
