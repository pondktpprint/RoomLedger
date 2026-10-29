package com.pondktpprint.roomledger.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthException
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.pondktpprint.roomledger.data.BackupPreview
import com.pondktpprint.roomledger.data.CloudBackupFormat
import com.pondktpprint.roomledger.data.CloudBackupVersion
import com.pondktpprint.roomledger.data.FirestoreCloudBackup
import com.pondktpprint.roomledger.data.RoomLedgerBackup
import com.pondktpprint.roomledger.data.firebaseAuthOrNull
import com.pondktpprint.roomledger.data.googleWebClientId
import com.pondktpprint.roomledger.data.signInWithGoogle
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupSettingsSection(database: RoomLedgerDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { firebaseAuthOrNull(context) }
    val firestore = remember(auth) { auth?.app?.let { runCatching { FirebaseFirestore.getInstance(it) }.getOrNull() } }
    val cloud = remember(firestore) { firestore?.let(::FirestoreCloudBackup) }
    val webClientId = remember { googleWebClientId(context) }
    var user by remember(auth) { mutableStateOf<FirebaseUser?>(auth?.currentUser) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var versions by remember { mutableStateOf<List<CloudBackupVersion>>(emptyList()) }
    var selectedVersion by remember { mutableStateOf<CloudBackupVersion?>(null) }
    var lastLocalChange by remember { mutableStateOf(0L) }
    var backupPassphrase by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }
    var showBackupPassphrase by remember { mutableStateOf(false) }
    var showRestorePassphrase by remember { mutableStateOf(false) }
    var cloudRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var restoreSecret by remember { mutableStateOf<CharArray?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            restoreSecret?.fill('\u0000')
            cloudRestoreBytes?.fill(0)
        }
    }

    DisposableEffect(auth) {
        val listener = auth?.let { currentAuth ->
            FirebaseAuth.AuthStateListener { changed -> user = changed.currentUser }.also(currentAuth::addAuthStateListener)
        }
        onDispose { if (auth != null && listener != null) auth.removeAuthStateListener(listener) }
    }

    suspend fun refreshBackups(uid: String) {
        val service = cloud ?: error("Firebase ยังไม่ได้ตั้งค่า")
        runCatching { service.deleteIncomplete(uid) }
        versions = service.listVerified(uid)
        lastLocalChange = RoomLedgerBackup.latestLocalChangeAt(database)
    }

    LaunchedEffect(user?.uid, cloud) {
        val uid = user?.uid
        if (uid != null && cloud != null) {
            busy = true
            runCatching { refreshBackups(uid) }
                .onFailure { status = "โหลดรายการสำรองไม่สำเร็จ ตรวจสอบอินเทอร์เน็ตและสิทธิ์ Firestore" }
            busy = false
        } else {
            versions = emptyList()
            lastLocalChange = RoomLedgerBackup.latestLocalChangeAt(database)
        }
    }

    fun clearCloudRestore() {
        restoreSecret?.fill('\u0000'); restoreSecret = null
        cloudRestoreBytes?.fill(0); cloudRestoreBytes = null
        selectedVersion = null; preview = null; showRestoreConfirmation = false
        restorePassphrase = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("บัญชีและ Cloud Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("บัญชี Google", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                when {
                    user != null -> {
                        Text(user?.displayName ?: user?.email.orEmpty(), fontWeight = FontWeight.Medium)
                        user?.email?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !busy, onClick = {
                                auth?.signOut()
                                versions = emptyList(); selectedVersion = null
                                scope.launch {
                                    runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
                                    status = "ออกจากระบบแล้ว ข้อมูลในเครื่องยังคงอยู่"
                                }
                            }) { Text("ออกจากระบบ") }
                            OutlinedButton(enabled = !busy, onClick = { user?.uid?.let { uid -> scope.launch { busy = true; runCatching { refreshBackups(uid) }.onFailure { status = "โหลดรายการสำรองไม่สำเร็จ กรุณาลองใหม่" }; busy = false } } }) { Text("โหลดรายการใหม่") }
                        }
                    }
                    auth == null || webClientId == null -> {
                        Text("ยังไม่ได้ตั้งค่า Firebase สำหรับแอปนี้ กรุณาทำตามคู่มือใน README", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(enabled = false, onClick = {}) { Text("เข้าสู่ระบบด้วย Google") }
                    }
                    else -> Button(enabled = !busy, onClick = {
                        val firebaseAuth = auth ?: return@Button
                        val clientId = webClientId ?: return@Button
                        busy = true; status = null
                        scope.launch {
                            try { user = signInWithGoogle(context, firebaseAuth, clientId); status = "เข้าสู่ระบบ Google สำเร็จ" }
                            catch (cancelled: CancellationException) { status = "ยกเลิกการเข้าสู่ระบบ" }
                            catch (_: FirebaseAuthInvalidCredentialsException) { status = "การตั้งค่า Google Sign-in ไม่ถูกต้อง ตรวจสอบ OAuth client และ SHA-1" }
                            catch (_: FirebaseAuthException) { status = "เข้าสู่ระบบไม่สำเร็จ กรุณาตรวจสอบบัญชีและลองอีกครั้ง" }
                            catch (_: Exception) { status = "เข้าสู่ระบบไม่สำเร็จ กรุณาลองอีกครั้ง" }
                            busy = false
                        }
                    }) { Text("เข้าสู่ระบบด้วย Google") }
                }
            }
        }

        val newest = versions.firstOrNull()
        Text("สำรองล่าสุด: ${newest?.let { RoomLedgerBackup.formattedDate(it.createdAtMillis) } ?: "ยังไม่มีข้อมูลสำรอง Cloud"}", style = MaterialTheme.typography.bodySmall)
        Text(
            if (newest == null || lastLocalChange > newest.createdAtMillis) "สถานะการสำรอง: มีการเปลี่ยนแปลงที่ยังไม่ได้สำรอง" else "สถานะการสำรอง: ข้อมูลในเครื่องตรงกับสำรองล่าสุด",
            color = if (newest == null || lastLocalChange > newest.createdAtMillis) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (user != null) {
            Button(enabled = !busy && cloud != null, onClick = { backupPassphrase = ""; showBackupPassphrase = true }, modifier = Modifier.fillMaxWidth()) { Text("สำรองข้อมูลขึ้น Cloud") }
            OutlinedButton(enabled = !busy && cloud != null, onClick = { user?.uid?.let { uid ->
                busy = true; status = null
                scope.launch {
                    runCatching { refreshBackups(uid) }
                        .onSuccess { if (versions.isEmpty()) status = "ไม่พบข้อมูลสำรองที่ตรวจสอบแล้ว" }
                        .onFailure { status = "โหลดรายการสำรองไม่สำเร็จ ตรวจสอบอินเทอร์เน็ตและสิทธิ์ Firestore" }
                    busy = false
                }
            } }, modifier = Modifier.fillMaxWidth()) { Text("กู้คืนจาก Cloud · โหลดรายการ") }

            if (versions.isNotEmpty()) {
                Text("เลือกเวอร์ชันสำรองสำหรับกู้คืน", style = MaterialTheme.typography.titleSmall)
                versions.forEach { version ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selectedVersion = version }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedVersion?.id == version.id, onClick = { selectedVersion = version }, enabled = !busy)
                        Column(Modifier.weight(1f)) {
                            Text(RoomLedgerBackup.formattedDate(version.createdAtMillis), fontWeight = FontWeight.Medium)
                            Text("${version.totalByteSize / 1024} KiB · ${version.chunkCount} ส่วน", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedButton(enabled = !busy && selectedVersion != null, onClick = {
                    val choice = selectedVersion ?: return@OutlinedButton
                    val uid = user?.uid ?: return@OutlinedButton
                    busy = true; progress = 0f; status = "กำลังดาวน์โหลดและตรวจสอบข้อมูลสำรอง…"
                    scope.launch {
                        runCatching { cloud!!.download(uid, choice) }
                            .onSuccess { bytes -> cloudRestoreBytes = bytes; selectedVersion = choice; restorePassphrase = ""; showRestorePassphrase = true; status = null }
                            .onFailure { status = "ดาวน์โหลดไม่สำเร็จหรือข้อมูลสำรองตรวจสอบไม่ผ่าน กรุณาลองใหม่" }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("เลือกข้อมูลสำรอง") }
            }
        } else Text("เข้าสู่ระบบก่อนจึงจะใช้ Cloud Backup ได้ ข้อมูลใน Room ยังใช้ได้ตามปกติโดยไม่ต้องลงชื่อเข้าใช้", style = MaterialTheme.typography.bodySmall)
        if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(); Text(if (progress > 0f) "กำลังอัปโหลด ${(progress * 100).toInt()}%" else "กำลังดำเนินการ…") }
        if (busy && progress > 0f) androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        status?.let { Text(it, color = if (it.contains("ไม่สำเร็จ") || it.contains("ไม่ผ่าน")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        Text("สำรอง Cloud ทำงานเมื่อสั่งด้วยตนเองเท่านั้น เนื่องจากรหัสผ่านเข้ารหัสจะไม่ถูกเก็บไว้เพื่อให้งานเบื้องหลังนำไปใช้", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
    }

    if (showBackupPassphrase) AlertDialog(
        onDismissRequest = { showBackupPassphrase = false; backupPassphrase = "" },
        title = { Text("เข้ารหัสข้อมูลก่อนอัปโหลด") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("กรอกรหัสผ่านสำรองอย่างน้อย 8 ตัวอักษร รหัสผ่านนี้ไม่ถูกเก็บในแอป") ; CloudPasswordField(backupPassphrase) { backupPassphrase = it } } },
        confirmButton = { Button(enabled = backupPassphrase.length >= RoomLedgerBackup.PASSPHRASE_MIN_LENGTH && !busy, onClick = {
            val secret = backupPassphrase.toCharArray(); backupPassphrase = ""; showBackupPassphrase = false
            val uid = user?.uid ?: return@Button
            busy = true; progress = 0f; status = "กำลังสร้างไฟล์สำรองที่เข้ารหัส…"
            scope.launch {
                try {
                    val snapshotTime = System.currentTimeMillis()
                    val encrypted = RoomLedgerBackup.encryptedBytes(database, secret, snapshotTime)
                    try {
                        require(encrypted.size <= CloudBackupFormat.MAX_BACKUP_BYTES) { "ไฟล์สำรองเกินขนาด Cloud ที่รองรับ (20 MiB)" }
                        val uploaded = cloud?.upload(uid, encrypted, snapshotTime) { progress = it } ?: error("Firebase ยังไม่ได้ตั้งค่า")
                        versions = CloudBackupFormat.latestThreeVerified(versions + uploaded)
                        lastLocalChange = RoomLedgerBackup.latestLocalChangeAt(database)
                    } finally { encrypted.fill(0) }
                    status = "สำรองข้อมูลขึ้น Cloud สำเร็จและตรวจสอบครบทุกส่วนแล้ว"
                    runCatching { refreshBackups(uid) }.onFailure { status = "สำรองข้อมูลสำเร็จ แต่โหลดประวัติล่าสุดไม่สำเร็จ กดโหลดรายการใหม่ได้" }
                } catch (_: CancellationException) { status = "ยกเลิกการสำรอง Cloud" }
                catch (failure: Exception) { status = failure.message?.takeIf { it.contains("20 MiB") } ?: "สำรองข้อมูล Cloud ไม่สำเร็จ ตรวจสอบอินเทอร์เน็ตและลองใหม่" }
                finally { secret.fill('\u0000'); busy = false; progress = 0f }
            }
        }) { Text("เข้ารหัสและสำรอง") } },
        dismissButton = { TextButton(onClick = { showBackupPassphrase = false; backupPassphrase = "" }) { Text("ยกเลิก") } },
    )

    if (showRestorePassphrase) AlertDialog(
        onDismissRequest = { showRestorePassphrase = false; restorePassphrase = ""; cloudRestoreBytes?.fill(0); cloudRestoreBytes = null },
        title = { Text("ตรวจสอบรหัสผ่านสำรอง") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("ระบบดาวน์โหลดและตรวจ checksum แล้ว กรอกรหัสผ่านเพื่อถอดรหัสและตรวจข้อมูล") ; CloudPasswordField(restorePassphrase) { restorePassphrase = it } } },
        confirmButton = { Button(enabled = restorePassphrase.length >= RoomLedgerBackup.PASSPHRASE_MIN_LENGTH && !busy, onClick = {
            val secret = restorePassphrase.toCharArray(); restorePassphrase = ""; showRestorePassphrase = false; busy = true
            scope.launch {
                runCatching { RoomLedgerBackup.inspect(database, cloudRestoreBytes ?: error("ไม่พบไฟล์สำรอง"), secret) }
                    .onSuccess { preview = it; restoreSecret = secret; status = null }
                    .onFailure { status = it.message?.takeIf(String::isNotBlank) ?: "ถอดรหัสหรือยืนยันข้อมูลสำรองไม่สำเร็จ"; secret.fill('\u0000'); showRestorePassphrase = true }
                busy = false
            }
        }) { Text("ตรวจสอบและแสดงตัวอย่าง") } },
        dismissButton = { TextButton(onClick = { showRestorePassphrase = false; restorePassphrase = ""; cloudRestoreBytes?.fill(0); cloudRestoreBytes = null }) { Text("ยกเลิก") } },
    )

    preview?.let { info -> AlertDialog(
        onDismissRequest = { clearCloudRestore() },
        title = { Text("ยืนยันกู้คืนจาก Cloud") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("วันที่สำรอง: ${RoomLedgerBackup.formattedDate(info.createdAtMillis)}")
            Text("ห้อง ${info.roomCount} · มิเตอร์ ${info.readingCount} · บิล ${info.billCount} · รายการรับเงิน ${info.paymentCount}")
            Text("การกู้คืนจะแทนที่ข้อมูลในเครื่องทั้งหมด และไม่รวมบิลหรือประวัติการชำระเงิน")
        } },
        confirmButton = { Button(enabled = !busy, onClick = { showRestoreConfirmation = true }) { Text("ดำเนินการต่อ") } },
        dismissButton = { TextButton(onClick = { clearCloudRestore() }) { Text("ยกเลิก") } },
    ) }

    if (showRestoreConfirmation) AlertDialog(
        onDismissRequest = { if (!busy) showRestoreConfirmation = false },
        title = { Text("ยืนยันแทนที่ข้อมูลในเครื่อง") },
        text = { Text("ยืนยันใช้ข้อมูลสำรองที่เลือกแทนที่ห้อง บิล การรับชำระ และยอดยกหนี้ทั้งหมดในเครื่องหรือไม่") },
        confirmButton = { Button(enabled = !busy, onClick = {
            val bytes = cloudRestoreBytes?.copyOf() ?: return@Button
            val secret = restoreSecret ?: return@Button
            if (user?.uid == null) return@Button
            showRestoreConfirmation = false; busy = true
            scope.launch {
                try {
                    require(selectedVersion != null) { "ไม่พบเวอร์ชันสำรองที่เลือก" }
                    RoomLedgerBackup.restore(database, bytes, secret)
                    status = "กู้คืนข้อมูลจาก Cloud สำเร็จ"
                    versions = cloud.listVerified(uid)
                    lastLocalChange = RoomLedgerBackup.latestLocalChangeAt(database)
                } catch (_: CancellationException) { status = "ยกเลิกการกู้คืน ข้อมูลในเครื่องยังคงอยู่" }
                catch (_: Exception) { status = "กู้คืนไม่สำเร็จ ข้อมูลเดิมในเครื่องยังคงอยู่ กรุณาลองใหม่" }
                finally { bytes.fill(0); clearCloudRestore(); busy = false }
            }
        }) { Text("ยืนยันกู้คืนและแทนที่") } },
        dismissButton = { TextButton(onClick = { showRestoreConfirmation = false }) { Text("กลับ") } },
    )
}

@Composable
private fun CloudPasswordField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text("รหัสผ่าน") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
