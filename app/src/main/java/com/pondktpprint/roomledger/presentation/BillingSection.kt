package com.pondktpprint.roomledger.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pondktpprint.roomledger.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingSection(rooms: List<RoomRecord>, repository: RoomRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var historyRoom by remember { mutableStateOf<RoomRecord?>(null) }
    var bills by remember { mutableStateOf<List<MonthlyBill>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var selectedBill by remember { mutableStateOf<MonthlyBill?>(null) }
    var pendingDraft by remember { mutableStateOf<ReadingDraft?>(null) }
    var confirmEdit by remember { mutableStateOf(false) }
    fun refresh() { scope.launch { loading = true; runCatching { if (historyRoom == null) repository.billsForMonth(month) else repository.billHistory(historyRoom!!.id) }.onSuccess { bills = it; error = null }.onFailure { error = it.message ?: "โหลดข้อมูลไม่สำเร็จ" }; loading = false } }
    LaunchedEffect(month, historyRoom?.id) { refresh() }
    Scaffold(topBar = { TopAppBar(title = { Text(if (historyRoom == null) "บันทึกมิเตอร์รายเดือน" else "ประวัติบิล · ${historyRoom?.name}") }, navigationIcon = { TextButton(onClick = { if (historyRoom != null) historyRoom = null else onBack() }) { Text("กลับ") } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            if (historyRoom == null) {
                OutlinedTextField(month, { month = it.filter { c -> c.isDigit() || c == '-' }.take(7) }, label = { Text("รอบบิล (YYYY-MM)") }, supportingText = { Text("${thaiMonth(month)} · ระบุเดือนเป็น ค.ศ.") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp)); Text("ห้องที่บันทึกแล้ว ${bills.size}/${rooms.count { it.isActive }} ห้อง", style = MaterialTheme.typography.titleSmall)
                if (rooms.isEmpty()) Text("ยังไม่มีห้องพัก กรุณาเพิ่มห้องก่อน") else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(rooms.filter { it.isActive }, key = RoomRecord::id) { room -> MeterEntryCard(room, month, repository, bills.any { it.roomId == room.id }, { historyRoom = room }) { draft -> pendingDraft = draft; scope.launch { if (repository.laterReadingCount(room.id, month) > 0) confirmEdit = true else saveDraft(room, draft, false, repository, scope, { error = it }, { refresh() }) } } }
                }
            } else if (bills.isEmpty()) Text("ยังไม่มีประวัติบิลของห้องนี้", modifier = Modifier.padding(16.dp))
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) { items(bills, key = MonthlyBill::id) { bill -> BillCard(bill) { selectedBill = bill } } }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        }
    }
    if (confirmEdit && pendingDraft != null) AlertDialog(onDismissRequest = { confirmEdit = false }, title = { Text("ยืนยันแก้ไขรอบย้อนหลัง") }, text = { Text("มีรายการเดือนถัดไปแล้ว การแก้ไขนี้จะไม่เขียนทับเลขมิเตอร์เดิมของเดือนถัดไป โปรดตรวจสอบเลขตั้งต้นของเดือนถัดไปด้วยตนเอง") }, confirmButton = { Button(onClick = { confirmEdit = false; val draft = pendingDraft!!; val room = rooms.first { it.id == draft.roomId }; saveDraft(room, draft, true, repository, scope, { error = it }, { refresh() }) }) { Text("ยืนยันและบันทึก") } }, dismissButton = { TextButton(onClick = { confirmEdit = false }) { Text("ยกเลิก") } })
    selectedBill?.let { bill -> BillDetailDialog(bill, repository, { selectedBill = null }) { updated -> bills = bills.map { if (it.id == updated.id) updated else it } } }
}

private fun saveDraft(room: RoomRecord, draft: ReadingDraft, confirm: Boolean, repository: RoomRepository, scope: CoroutineScope, failure: (String) -> Unit, success: () -> Unit) {
    scope.launch { runCatching { repository.saveReading(room, draft, confirm, System.currentTimeMillis()) }.onSuccess { failure(""); success() }.onFailure { failure(it.message ?: "บันทึกไม่สำเร็จ") } }
}

@Composable
private fun MeterEntryCard(room: RoomRecord, month: String, repository: RoomRepository, completed: Boolean, onHistory: () -> Unit, onSave: (ReadingDraft) -> Unit) {
    var wp by remember(room.id, month) { mutableStateOf("") }; var wc by remember(room.id, month) { mutableStateOf("") }; var ep by remember(room.id, month) { mutableStateOf("") }; var ec by remember(room.id, month) { mutableStateOf("") }
    var wa by remember(room.id, month) { mutableStateOf("0") }; var ea by remember(room.id, month) { mutableStateOf("0") }; var error by remember(room.id, month) { mutableStateOf<String?>(null) }; var suggested by remember(room.id, month) { mutableStateOf(false) }
    LaunchedEffect(room.id, month) { runCatching { repository.priorReading(room.id, month) }.getOrNull()?.let { if (wp.isBlank()) wp = it.waterPrevious.toString(); if (ep.isBlank()) ep = it.electricityPrevious.toString(); suggested = true } }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(room.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); if (completed) Text("บันทึกแล้ว", color = MaterialTheme.colorScheme.primary); TextButton(onClick = onHistory) { Text("ประวัติ") } }
        if (suggested) Text("เลขครั้งก่อนแนะนำจากรอบก่อนหน้า แก้ไขได้", style = MaterialTheme.typography.bodySmall)
        Text("น้ำ (หน่วย)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumberField("ครั้งก่อน", wp, { wp = it }, Modifier.weight(1f)); NumberField("ปัจจุบัน", wc, { wc = it }, Modifier.weight(1f)) }
        NumberField("ปรับหน่วยกรณีเปลี่ยน/หมุนมิเตอร์", wa, { wa = it }, Modifier.fillMaxWidth())
        Text("ไฟ (หน่วย)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumberField("ครั้งก่อน", ep, { ep = it }, Modifier.weight(1f)); NumberField("ปัจจุบัน", ec, { ec = it }, Modifier.weight(1f)) }
        NumberField("ปรับหน่วยกรณีเปลี่ยน/หมุนมิเตอร์", ea, { ea = it }, Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { val values = listOf(wp, wc, wa, ep, ec, ea).map { it.trim().toLongOrNull() }; if (values.any { it == null }) error = "กรุณากรอกเลขมิเตอร์เป็นจำนวนเต็มให้ครบ" else { error = null; onSave(ReadingDraft(room.id, month, values[0]!!, values[1]!!, values[2]!!, values[3]!!, values[4]!!, values[5]!!)) } }, Modifier.fillMaxWidth()) { Text(if (completed) "แก้ไขและคำนวณบิล" else "บันทึกมิเตอร์และออกบิล") }
    } }
}

@Composable private fun NumberField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier) { OutlinedTextField(value, change, label = { Text(label) }, modifier = modifier, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
@Composable private fun BillCard(bill: MonthlyBill, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(thaiMonth(bill.billingMonth), style = MaterialTheme.typography.titleMedium); Text(if (bill.paymentState == PaymentState.PAID) "ชำระแล้ว" else "ยังไม่ชำระ") }; Text(formatSatangCurrency(bill.totalSatang), fontWeight = FontWeight.Bold) } } }

@Composable private fun BillDetailDialog(bill: MonthlyBill, repository: RoomRepository, close: () -> Unit, update: (MonthlyBill) -> Unit) {
    val scope = rememberCoroutineScope(); var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("บิล ${thaiMonth(bill.billingMonth)} · ${bill.roomName}") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailLine("ค่าเช่า", formatSatangCurrency(bill.rentSatang)); DetailLine("น้ำ ${bill.waterPrevious} → ${bill.waterCurrent} (${bill.waterUnits} หน่วย × ${formatSatangCurrency(bill.waterRateSatang)})", formatSatangCurrency(bill.waterChargeSatang)); DetailLine("ไฟ ${bill.electricityPrevious} → ${bill.electricityCurrent} (${bill.electricityUnits} หน่วย × ${formatSatangCurrency(bill.electricityRateSatang)})", formatSatangCurrency(bill.electricityChargeSatang)); HorizontalDivider(); DetailLine("รวม", formatSatangCurrency(bill.totalSatang)); Text("สถานะ: ${if (bill.paymentState == PaymentState.PAID) "ชำระแล้ว" else "ยังไม่ชำระ"}"); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { if (bill.paymentState == PaymentState.UNPAID) TextButton(onClick = { scope.launch { runCatching { repository.reconcilePayment(bill, PaymentState.PAID, System.currentTimeMillis()) }.onSuccess { update(bill.copy(paymentState = PaymentState.PAID, updatedAtMillis = System.currentTimeMillis())); close() }.onFailure { error = it.message } } }) { Text("ยืนยันชำระแล้ว") } else TextButton(onClick = close) { Text("ปิด") } }, dismissButton = { if (bill.paymentState == PaymentState.UNPAID) TextButton(onClick = close) { Text("ปิด") } else TextButton(onClick = { scope.launch { runCatching { repository.reconcilePayment(bill, PaymentState.UNPAID, System.currentTimeMillis()) }.onSuccess { update(bill.copy(paymentState = PaymentState.UNPAID, updatedAtMillis = System.currentTimeMillis())); close() }.onFailure { error = it.message } } }) { Text("กระทบยอดสถานะชำระ") } })
}
@Composable private fun DetailLine(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Text(value, fontWeight = FontWeight.Medium) } }
