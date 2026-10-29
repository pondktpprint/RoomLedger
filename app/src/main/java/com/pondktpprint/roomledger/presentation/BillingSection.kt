package com.pondktpprint.roomledger.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pondktpprint.roomledger.domain.*
import kotlinx.coroutines.launch
import java.time.YearMonth

private enum class BillingPage { MONTHLY, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingSection(rooms: List<RoomRecord>, repository: RoomRepository, onBack: () -> Unit, startWithHistory: Boolean = false) {
    val scope = rememberCoroutineScope()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = parseBillingMonth(monthKey) ?: YearMonth.now()
    var page by remember { mutableStateOf(if (startWithHistory) BillingPage.HISTORY else BillingPage.MONTHLY) }
    var historyRoom by remember { mutableStateOf<RoomRecord?>(null) }
    var activeRoom by remember { mutableStateOf<RoomRecord?>(null) }
    var activeBill by remember { mutableStateOf<MonthlyBill?>(null) }
    var bills by remember { mutableStateOf<List<MonthlyBill>>(emptyList()) }
    var roomHistory by remember { mutableStateOf<List<MonthlyBill>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadMonth() { scope.launch { loading = true; bills = emptyList(); runCatching { repository.billsForMonth(monthKey) }.onSuccess { bills = it; error = null }.onFailure { error = it.message ?: "โหลดรายการไม่สำเร็จ" }; loading = false } }
    fun loadHistory(room: RoomRecord?) { historyRoom = room; scope.launch { loading = true; runCatching { if (room == null) rooms.flatMap { repository.billHistory(it.id) }.sortedByDescending(MonthlyBill::billingMonth) else repository.billHistory(room.id) }.onSuccess { roomHistory = it; error = null }.onFailure { error = it.message ?: "โหลดประวัติบิลไม่สำเร็จ" }; loading = false } }
    LaunchedEffect(monthKey) { loadMonth() }
    LaunchedEffect(page, startWithHistory) { if (page == BillingPage.HISTORY && historyRoom == null) loadHistory(null) }
    BackHandler(enabled = activeBill == null && activeRoom == null) {
        when { activeBill != null -> activeBill = null; activeRoom != null -> activeRoom = null; historyRoom != null -> { historyRoom = null; loadHistory(null) }; page == BillingPage.HISTORY -> { page = BillingPage.MONTHLY; onBack() }; else -> onBack() }
    }

    if (activeRoom != null) {
        MeterEntryPage(activeRoom!!, monthKey, repository, onBack = { activeRoom = null; loadMonth() }, onSaved = { activeRoom = null; loadMonth() }, onError = { error = it })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        return
    }
    if (activeBill != null) {
        BillDetailsPage(activeBill!!, repository, onBack = { activeBill = null }, onUpdated = { changed -> activeBill = changed; roomHistory = roomHistory.map { if (it.id == changed.id) changed else it }; bills = bills.map { if (it.id == changed.id) changed else it } })
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (page == BillingPage.MONTHLY) "บันทึกมิเตอร์รายเดือน" else if (historyRoom == null) "ประวัติบิล" else "ประวัติบิล · ${historyRoom?.name}") }, navigationIcon = { IconButton(onClick = { if (historyRoom != null) { historyRoom = null; loadHistory(null) } else { page = BillingPage.MONTHLY; onBack() } }) { Text("‹", style = MaterialTheme.typography.headlineMedium) } }, actions = { if (page == BillingPage.MONTHLY) TextButton(onClick = { page = BillingPage.HISTORY }) { Text("ประวัติบิล") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (page == BillingPage.MONTHLY) {
                MonthSelector(month, onMonthChange = { monthKey = it.toString() })
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                    val activeRooms = rooms.filter(RoomRecord::isActive)
                    val completedIds = bills.mapTo(mutableSetOf(), MonthlyBill::roomId)
                    val completedCount = completedIds.intersect(activeRooms.map(RoomRecord::id).toSet()).size
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("เสร็จ $completedCount ห้อง", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text("ค้าง ${activeRooms.size - completedCount} ห้อง", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (activeRooms.isEmpty()) EmptyBillingMessage("ยังไม่มีห้องที่เปิดใช้งาน") else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                        items(activeRooms, key = RoomRecord::id) { room -> RoomMonthCard(room, bills.firstOrNull { it.roomId == room.id }) { activeRoom = room; error = null } }
                    }
                }
            } else if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                if (historyRoom == null) {
                    if (rooms.isEmpty()) EmptyBillingMessage("ยังไม่มีห้องพัก") else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                        items(rooms, key = RoomRecord::id) { room -> ListItem(headlineContent = { Text(room.name) }, supportingContent = { Text("ดูบิลย้อนหลังทั้งหมด") }, trailingContent = { Text("${roomHistory.count { it.roomId == room.id }} บิล") }, modifier = Modifier.clickable { loadHistory(room) }) }
                    }
                } else if (roomHistory.none { it.roomId == historyRoom?.id }) EmptyBillingMessage("ห้องนี้ยังไม่มีประวัติบิล")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    items(roomHistory.filter { it.roomId == historyRoom?.id }, key = MonthlyBill::id) { bill -> BillHistoryCard(bill) { activeBill = bill } }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        }
    }

}

@Composable private fun MeterValueField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), readOnly = !enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable private fun CalculationLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal); Spacer(Modifier.width(8.dp)); Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BillDetailsPage(bill: MonthlyBill, repository: RoomRepository, onBack: () -> Unit, onUpdated: (MonthlyBill) -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmPayment by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("รายละเอียดบิล") }, navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(bill.roomName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(thaiMonth(bill.billingMonth), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ค่าเช่า", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); CalculationLine("ค่าเช่ารายเดือน", formatSatangCurrency(bill.rentSatang))
                HorizontalDivider(); Text("ค่าน้ำ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                CalculationLine("ครั้งก่อน", bill.waterPrevious.toString()); CalculationLine("ปัจจุบัน", bill.waterCurrent.toString()); CalculationLine("ใช้ไป", "${bill.waterUnits} หน่วย"); CalculationLine("อัตราต่อหน่วย", formatSatangCurrency(bill.waterRateSatang)); CalculationLine("รวมค่าน้ำ", formatSatangCurrency(bill.waterChargeSatang))
                HorizontalDivider(); Text("ค่าไฟ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                CalculationLine("ครั้งก่อน", bill.electricityPrevious.toString()); CalculationLine("ปัจจุบัน", bill.electricityCurrent.toString()); CalculationLine("ใช้ไป", "${bill.electricityUnits} หน่วย"); CalculationLine("อัตราต่อหน่วย", formatSatangCurrency(bill.electricityRateSatang)); CalculationLine("รวมค่าไฟ", formatSatangCurrency(bill.electricityChargeSatang))
                HorizontalDivider(); CalculationLine("ยอดรวมทั้งสิ้น", formatSatangCurrency(bill.totalSatang), true)
            } }
            Text(if (bill.paymentState == PaymentState.PAID) "สถานะ: ชำระแล้ว" else "สถานะ: ยังไม่ชำระ", style = MaterialTheme.typography.titleMedium, color = if (bill.paymentState == PaymentState.PAID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (bill.paymentState == PaymentState.UNPAID) Button(onClick = { confirmPayment = true }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("ทำเครื่องหมายว่าชำระแล้ว") }
        }
    }
    if (confirmPayment) AlertDialog(onDismissRequest = { if (!saving) confirmPayment = false }, title = { Text("ยืนยันการชำระเงิน") }, text = { Text("ยืนยันว่าห้อง ${bill.roomName} ชำระบิล ${thaiMonth(bill.billingMonth)} จำนวน ${formatSatangCurrency(bill.totalSatang)} แล้วหรือไม่") }, confirmButton = { Button(enabled = !saving, onClick = { confirmPayment = false; saving = true; scope.launch { runCatching { repository.reconcilePayment(bill, PaymentState.PAID, System.currentTimeMillis()) }.onSuccess { onUpdated(bill.copy(paymentState = PaymentState.PAID, updatedAtMillis = System.currentTimeMillis())) }.onFailure { error = it.message ?: "ปรับสถานะไม่สำเร็จ" }; saving = false } }) { Text("ยืนยันว่าชำระแล้ว") } }, dismissButton = { TextButton(enabled = !saving, onClick = { confirmPayment = false }) { Text("ยังไม่ชำระ") } })
}

@Composable private fun MonthSelector(month: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Text("‹", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { contentDescription = "เดือนก่อนหน้า" }) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(formatBillingMonth(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Text("›", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { contentDescription = "เดือนถัดไป" }) }
    } }
}

@Composable private fun RoomMonthCard(room: RoomRecord, bill: MonthlyBill?, open: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = open)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(if (bill == null) "รอบิล" else "บันทึกแล้ว · ${if (bill.paymentState == PaymentState.PAID) "ชำระแล้ว" else "ยังไม่ชำระ"}", style = MaterialTheme.typography.bodySmall, color = if (bill == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary) }
        Text(bill?.let { formatSatangCurrency(it.totalSatang) } ?: "บันทึกมิเตอร์ ›", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    } }
}

@Composable private fun BillHistoryCard(bill: MonthlyBill, open: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = open)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(thaiMonth(bill.billingMonth), style = MaterialTheme.typography.titleMedium); Text(if (bill.paymentState == PaymentState.PAID) "ชำระแล้ว" else "ยังไม่ชำระ", style = MaterialTheme.typography.bodySmall) }
        Text(formatSatangCurrency(bill.totalSatang), fontWeight = FontWeight.Bold)
    } }
}

@Composable private fun EmptyBillingMessage(message: String) { Surface(Modifier.fillMaxWidth().padding(top = 16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) { Text(message, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MeterEntryPage(room: RoomRecord, month: String, repository: RoomRepository, onBack: () -> Unit, onSaved: () -> Unit, onError: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var wp by remember(room.id, month) { mutableStateOf("") }; var wc by remember(room.id, month) { mutableStateOf("") }
    var ep by remember(room.id, month) { mutableStateOf("") }; var ec by remember(room.id, month) { mutableStateOf("") }
    var pricingRoom by remember(room.id, month) { mutableStateOf(room) }
    var wa by remember(room.id, month) { mutableStateOf("0") }; var ea by remember(room.id, month) { mutableStateOf("0") }
    var firstTime by remember(room.id, month) { mutableStateOf(false) }; var showAdvanced by rememberSaveable(room.id, month) { mutableStateOf(false) }; var editPrevious by rememberSaveable(room.id, month) { mutableStateOf(false) }
    var loading by remember(room.id, month) { mutableStateOf(true) }; var saving by remember { mutableStateOf(false) }; var formError by remember(room.id, month) { mutableStateOf<String?>(null) }; var confirmHistoricalEdit by remember { mutableStateOf(false) }; var draftToSave by remember { mutableStateOf<ReadingDraft?>(null) }
    fun commit(draft: ReadingDraft, confirmImpact: Boolean) {
        saving = true; formError = null
        scope.launch {
            runCatching { repository.saveReading(room, draft, confirmImpact, System.currentTimeMillis()) }
                .onSuccess { saving = false; onError(null); onSaved() }
                .onFailure { saving = false; formError = it.message ?: "บันทึกไม่สำเร็จ" }
        }
    }
    LaunchedEffect(room.id, month) {
        runCatching {
            val saved = repository.readingForMonth(room.id, month)
            if (saved != null) { wp = saved.waterPrevious.toString(); wc = saved.waterCurrent.toString(); ep = saved.electricityPrevious.toString(); ec = saved.electricityCurrent.toString(); wa = saved.waterAdjustment.toString(); ea = saved.electricityAdjustment.toString(); firstTime = false; repository.billForRoomMonth(room.id, month)?.let { bill -> pricingRoom = room.copy(name = bill.roomName, monthlyRentSatang = bill.rentSatang, waterPricePerUnitSatang = bill.waterRateSatang, electricityPricePerUnitSatang = bill.electricityRateSatang) } }
            else {
                val prior = repository.priorReading(room.id, month)
                if (prior == null) firstTime = true else { wp = prior.waterPrevious.toString(); ep = prior.electricityPrevious.toString() }
                firstTime = prior == null
            }
        }.onFailure { formError = it.message ?: "โหลดข้อมูลมิเตอร์ไม่สำเร็จ" }
        loading = false
    }
    val waterPrevious = wp.toLongOrNull(); val waterCurrent = wc.toLongOrNull(); val electricPrevious = ep.toLongOrNull(); val electricCurrent = ec.toLongOrNull()
    val adjustmentWater = if (showAdvanced) wa.toLongOrNull() ?: 0 else 0L; val adjustmentElectricity = if (showAdvanced) ea.toLongOrNull() ?: 0 else 0L
    val calculation = runCatching { BillingCalculator.calculate(pricingRoom, MeterInput(waterPrevious, waterCurrent, adjustmentWater), MeterInput(electricPrevious, electricCurrent, adjustmentElectricity)) }.getOrNull()
    val calculationError = runCatching { BillingCalculator.calculate(pricingRoom, MeterInput(waterPrevious, waterCurrent, adjustmentWater), MeterInput(electricPrevious, electricCurrent, adjustmentElectricity)) }.exceptionOrNull()?.message
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("บันทึกมิเตอร์") }, navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) } }) }) { padding ->
        if (loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(room.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(thaiMonth(month), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (firstTime) Text("เริ่มบันทึกครั้งแรก: กรอกเลขมิเตอร์ครั้งก่อนและเลขปัจจุบัน", style = MaterialTheme.typography.bodyMedium)
            else if (!editPrevious) TextButton(onClick = { editPrevious = true }) { Text("แก้ไขเลขครั้งก่อน…") }
            MeterValueField("น้ำ · ครั้งก่อน", wp, { wp = it }, enabled = firstTime || editPrevious)
            MeterValueField("น้ำ · ปัจจุบัน", wc, { wc = it })
            MeterValueField("ไฟ · ครั้งก่อน", ep, { ep = it }, enabled = firstTime || editPrevious)
            MeterValueField("ไฟ · ปัจจุบัน", ec, { ec = it })
            if (editPrevious && !firstTime) Text("การเปลี่ยนเลขครั้งก่อนอาจมีผลต่อการตรวจสอบบิลเดือนถัดไป", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "ซ่อนตัวเลือกขั้นสูง" else "ตัวเลือกขั้นสูง · เปลี่ยน/หมุนมิเตอร์") }
            if (showAdvanced) { Text("ระบุหน่วยปรับเฉพาะกรณีเปลี่ยนหรือมิเตอร์หมุน", style = MaterialTheme.typography.bodySmall); MeterValueField("หน่วยปรับน้ำ", wa, { wa = it }); MeterValueField("หน่วยปรับไฟ", ea, { ea = it }) }
            if (calculation == null && (wc.isNotBlank() || ec.isNotBlank())) Text(calculationError ?: "กรุณาตรวจสอบเลขมิเตอร์", color = MaterialTheme.colorScheme.error)
            calculation?.let { result ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("สรุปค่าใช้จ่าย", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    CalculationLine("น้ำ ${result.waterUnits} หน่วย × ${formatSatangCurrency(pricingRoom.waterPricePerUnitSatang)}", formatSatangCurrency(result.waterSatang)); CalculationLine("ไฟ ${result.electricityUnits} หน่วย × ${formatSatangCurrency(pricingRoom.electricityPricePerUnitSatang)}", formatSatangCurrency(result.electricitySatang)); CalculationLine("ค่าเช่า", formatSatangCurrency(result.rentSatang)); HorizontalDivider(); CalculationLine("รวมทั้งสิ้น", formatSatangCurrency(result.totalSatang), bold = true)
                } }
            }
            formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !saving && calculation != null, onClick = {
                val values = listOf(waterPrevious, waterCurrent, electricPrevious, electricCurrent)
                if (values.any { it == null } || (showAdvanced && (wa.toLongOrNull() == null || ea.toLongOrNull() == null))) formError = "กรุณากรอกเลขมิเตอร์เป็นจำนวนเต็มให้ครบ"
                else {
                    val draft = ReadingDraft(room.id, month, waterPrevious!!, waterCurrent!!, adjustmentWater, electricPrevious!!, electricCurrent!!, adjustmentElectricity); draftToSave = draft
                    scope.launch {
                        runCatching { repository.laterReadingCount(room.id, month) }
                            .onSuccess { laterCount -> if (laterCount > 0) confirmHistoricalEdit = true else commit(draft, false) }
                            .onFailure { formError = it.message ?: "ตรวจสอบรอบบิลถัดไปไม่สำเร็จ" }
                    }
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("บันทึกมิเตอร์", style = MaterialTheme.typography.titleMedium) }
        }
    }
    if (confirmHistoricalEdit && draftToSave != null) AlertDialog(onDismissRequest = { confirmHistoricalEdit = false }, title = { Text("ยืนยันแก้ไขเลขมิเตอร์ย้อนหลัง") }, text = { Text("มีบิลของเดือนถัดไปแล้ว ระบบจะไม่แก้ไขบิลเดือนถัดไปให้อัตโนมัติ ต้องการบันทึกเลขครั้งก่อนใหม่นี้หรือไม่") }, confirmButton = { Button(onClick = { confirmHistoricalEdit = false; commit(draftToSave!!, true) }) { Text("ยืนยันการแก้ไข") } }, dismissButton = { TextButton(onClick = { confirmHistoricalEdit = false }) { Text("ยกเลิก") } })
}
