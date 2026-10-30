package com.pondktpprint.roomledger.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pondktpprint.roomledger.RoomLedgerApplication
import com.pondktpprint.roomledger.domain.MonthlyBill
import com.pondktpprint.roomledger.domain.RoomRecord
import com.pondktpprint.roomledger.domain.RoomRepository
import com.pondktpprint.roomledger.domain.formatBillingMonth
import com.pondktpprint.roomledger.domain.formatSatangAsBaht
import com.pondktpprint.roomledger.domain.formatSatangCurrency
import java.time.YearMonth

private data class DashboardFigures(
    val bills: List<MonthlyBill> = emptyList(),
    val billed: Long = 0L,
    val received: Long = 0L,
    val outstanding: Long = 0L,
)

private enum class MainTab(val label: String, val symbol: String) {
    HOME("หน้าหลัก", "⌂"), READINGS("จดมิเตอร์", "◷"), BILLS("บิล", "▤"), SETTINGS("ตั้งค่า", "⚙"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomLedgerScreen(repository: RoomRepository) {
    val viewModel: RoomLedgerViewModel = viewModel(factory = RoomLedgerViewModel.Factory(repository))
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val formError by viewModel.formError.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = (context.applicationContext as RoomLedgerApplication).database
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    val activeTab = MainTab.valueOf(selectedTab)
    var editorRoom by remember { mutableStateOf<RoomRecord?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showBackupSettings by rememberSaveable { mutableStateOf(false) }
    var dashboardMonthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val dashboardMonth = runCatching { YearMonth.parse(dashboardMonthKey) }.getOrDefault(YearMonth.now())
    var figures by remember(dashboardMonthKey, rooms) { mutableStateOf(DashboardFigures()) }
    var dashboardLoading by remember(dashboardMonthKey, rooms) { mutableStateOf(true) }
    var dashboardError by remember(dashboardMonthKey, rooms) { mutableStateOf<String?>(null) }

    LaunchedEffect(dashboardMonthKey, rooms) {
        dashboardLoading = true
        runCatching {
            val bills = repository.billsForMonth(dashboardMonthKey)
            var billed = 0L
            var received = 0L
            var outstanding = 0L
            bills.forEach { bill ->
                val summary = repository.financialSummary(bill.id)
                billed = Math.addExact(billed, summary.totalBilledSatang)
                received = Math.addExact(received, summary.totalReceivedSatang)
                outstanding = Math.addExact(outstanding, summary.outstandingSatang)
            }
            DashboardFigures(bills, billed, received, outstanding)
        }.onSuccess { figures = it; dashboardError = null }
            .onFailure { dashboardError = "โหลดสรุปประจำเดือนไม่สำเร็จ กรุณาลองใหม่" }
        dashboardLoading = false
    }

    if (showBackupSettings) {
        BackupSettingsScreen(database = database, onBack = { showBackupSettings = false })
        return
    }

    Scaffold(
        topBar = {
            if (activeTab == MainTab.HOME || activeTab == MainTab.SETTINGS) TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoomLedgerMark(Modifier.size(34.dp), color = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("RoomLedger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("จัดการห้องเช่า ใช้งานง่าย", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { selectedTab = tab.name },
                        icon = { Text(tab.symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (activeTab) {
            MainTab.HOME -> DashboardPage(
                modifier = Modifier.padding(innerPadding),
                rooms = rooms,
                month = dashboardMonth,
                figures = figures,
                loading = dashboardLoading,
                error = dashboardError,
                onMonthChanged = { dashboardMonthKey = it.toString() },
                onReadings = { selectedTab = MainTab.READINGS.name },
                onBills = { selectedTab = MainTab.BILLS.name },
                onPayments = { selectedTab = MainTab.BILLS.name },
                onRooms = { selectedTab = MainTab.SETTINGS.name },
            )
            MainTab.READINGS -> Box(Modifier.padding(innerPadding)) {
                BillingSection(rooms, repository, onBack = { selectedTab = MainTab.HOME.name }, startWithHistory = false)
            }
            MainTab.BILLS -> Box(Modifier.padding(innerPadding)) {
                BillingSection(rooms, repository, onBack = { selectedTab = MainTab.HOME.name }, startWithHistory = true)
            }
            MainTab.SETTINGS -> SettingsPage(
                modifier = Modifier.padding(innerPadding),
                rooms = rooms,
                errorMessage = formError,
                onBackup = { showBackupSettings = true },
                onAddRoom = { editorRoom = null; viewModel.clearFormError(); showEditor = true },
                onEditRoom = { room -> editorRoom = room; viewModel.clearFormError(); showEditor = true },
                onRoomActiveChanged = { room, active -> viewModel.setRoomActive(room.id, active) },
            )
        }
    }

    if (showEditor) {
        RoomEditorDialog(
            room = editorRoom,
            errorMessage = formError,
            onDismiss = { showEditor = false; viewModel.clearFormError() },
            onFieldChanged = viewModel::clearFormError,
            onSave = { name, rent, water, electricity ->
                viewModel.saveRoom(editorRoom?.id, name, rent, water, electricity) { showEditor = false }
            },
        )
    }
}

@Composable
private fun DashboardPage(
    modifier: Modifier,
    rooms: List<RoomRecord>,
    month: YearMonth,
    figures: DashboardFigures,
    loading: Boolean,
    error: String?,
    onMonthChanged: (YearMonth) -> Unit,
    onReadings: () -> Unit,
    onBills: () -> Unit,
    onPayments: () -> Unit,
    onRooms: () -> Unit,
) {
    val activeRooms = rooms.filter(RoomRecord::isActive)
    val billsByRoom = figures.bills.associateBy(MonthlyBill::roomId)
    val completed = activeRooms.count { billsByRoom.containsKey(it.id) }
    val progress = if (activeRooms.isEmpty()) 0f else completed.toFloat() / activeRooms.size
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DashboardHero(month = month, onMonthChanged = onMonthChanged)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("จำนวนห้อง", "${activeRooms.size}", "ห้อง", "⌂", Color(0xFFD9F5E7), Modifier.weight(1f))
                SummaryCard("ยอดเรียกเก็บ", formatSatangCurrency(figures.billed), "เดือนนี้", "▤", Color(0xFFE1ECFF), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("รับชำระแล้ว", formatSatangCurrency(figures.received), "เดือนนี้", "฿", Color(0xFFDDF6E9), Modifier.weight(1f))
                SummaryCard("ยอดค้างชำระ", formatSatangCurrency(figures.outstanding), "เดือนนี้", "!", Color(0xFFFFE5E5), Modifier.weight(1f), isError = true)
            }
        }
        item {
            Text("เมนูลัด", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("จดมิเตอร์", "◷", Color(0xFFFFF0D7), onReadings, Modifier.weight(1f))
                QuickAction("ออกบิล / ดูบิล", "▤", Color(0xFFDDF6E9), onBills, Modifier.weight(1f))
                QuickAction("รับชำระเงิน", "฿", Color(0xFFE4EDFF), onPayments, Modifier.weight(1f))
                QuickAction("จัดการห้อง", "⌂", Color(0xFFF0E5FF), onRooms, Modifier.weight(1f))
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("งานประจำเดือน · ${formatBillingMonth(month)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("ความคืบหน้าการจดมิเตอร์", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("บันทึกแล้ว  $completed ห้อง", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        Text("ยังไม่บันทึก  ${activeRooms.size - completed} ห้อง", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else if (loading) Text("กำลังโหลดข้อมูลประจำเดือน…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (activeRooms.isEmpty()) item { EmptyRooms() }
    }
}

@Composable
private fun DashboardHero(month: YearMonth, onMonthChanged: (YearMonth) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .14f), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { RoomLedgerMark(Modifier.size(31.dp), color = Color.White) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("สวัสดี 👋", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("ดูแลห้องเช่าได้ง่ายในทุกวัน", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .88f))
                }
                Text("●", color = Color(0xFF9CE7C3), style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("รอบบิลที่กำลังดู", color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonthArrow("‹", "เดือนก่อนหน้า") { onMonthChanged(month.minusMonths(1)) }
                    Text(formatBillingMonth(month), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    MonthArrow("›", "เดือนถัดไป") { onMonthChanged(month.plusMonths(1)) }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(label: String, description: String, onClick: () -> Unit) {
    Text(label, Modifier
        .clip(CircleShape)
        .clickable(onClickLabel = description, onClick = onClick)
        .padding(horizontal = 11.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun SummaryCard(label: String, value: String, sublabel: String, symbol: String, tint: Color, modifier: Modifier, isError: Boolean = false) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(Modifier.size(32.dp), shape = CircleShape, color = tint) { Box(contentAlignment = Alignment.Center) { Text(symbol, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
            Text(sublabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickAction(label: String, symbol: String, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(Modifier.size(48.dp), shape = CircleShape, color = tint) { Box(contentAlignment = Alignment.Center) { Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 2, minLines = 2)
    }
}

@Composable
private fun SettingsPage(
    modifier: Modifier,
    rooms: List<RoomRecord>,
    errorMessage: String?,
    onBackup: () -> Unit,
    onAddRoom: () -> Unit,
    onEditRoom: (RoomRecord) -> Unit,
    onRoomActiveChanged: (RoomRecord, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("ตั้งค่า", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("จัดการห้องและดูแลข้อมูลของคุณ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SettingsLink("สำรองและกู้คืนข้อมูล", "Cloud Backup · ไฟล์สำรองในเครื่อง", "↗", onBackup) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("จัดการห้องพัก", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${rooms.count(RoomRecord::isActive)} ห้องเปิดใช้งาน · ${rooms.size} ห้องทั้งหมด", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onAddRoom) { Text("+ เพิ่มห้อง") }
            }
        }
        if (errorMessage != null) item { Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (rooms.isEmpty()) item { EmptyRooms() }
        items(rooms, key = RoomRecord::id) { room -> RoomCard(room, { onEditRoom(room) }, { onRoomActiveChanged(room, it) }) }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoomLedgerMark(Modifier.size(38.dp), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("RoomLedger", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("จัดการห้องเช่า · ทำงานแบบออฟไลน์", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, symbol: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Text(symbol, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoomLedgerMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val scale = size.minDimension / 100f
        fun point(x: Float, y: Float) = Offset(x * scale + (size.width - 100f * scale) / 2f, y * scale + (size.height - 100f * scale) / 2f)
        val roof = Path().apply { moveTo(point(12f, 48f).x, point(12f, 48f).y); lineTo(point(50f, 16f).x, point(50f, 16f).y); lineTo(point(88f, 48f).x, point(88f, 48f).y) }
        drawPath(roof, color, style = Stroke(width = 9f * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        val walls = Path().apply { moveTo(point(22f, 45f).x, point(22f, 45f).y); lineTo(point(22f, 86f).x, point(22f, 86f).y); lineTo(point(78f, 86f).x, point(78f, 86f).y); lineTo(point(78f, 45f).x, point(78f, 45f).y) }
        drawPath(walls, color, style = Stroke(width = 7f * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawRoundRect(color, topLeft = point(41f, 55f), size = Size(18f * scale, 31f * scale), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale))
        drawCircle(color, radius = 5f * scale, center = point(67f, 61f))
    }
}

@Composable
private fun RoomCard(room: RoomRecord, onEdit: () -> Unit, onActiveChanged: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (room.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(40.dp), shape = CircleShape, color = if (room.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Box(contentAlignment = Alignment.Center) { RoomLedgerMark(Modifier.size(24.dp), if (room.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (room.isActive) "เปิดใช้งาน" else "ปิดใช้งาน", style = MaterialTheme.typography.bodySmall, color = if (room.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onEdit) { Text("แก้ไข") }
            }
            Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
            RoomPriceLine("ค่าเช่ารายเดือน", room.monthlyRentSatang)
            Spacer(Modifier.height(6.dp)); RoomPriceLine("ค่าน้ำ / หน่วย", room.waterPricePerUnitSatang)
            Spacer(Modifier.height(6.dp)); RoomPriceLine("ค่าไฟ / หน่วย", room.electricityPricePerUnitSatang)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("เปิดใช้งานห้องนี้", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = room.isActive, onCheckedChange = onActiveChanged)
            }
        }
    }
}

@Composable
private fun RoomPriceLine(label: String, satang: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatSatangCurrency(satang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyRooms() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RoomLedgerMark(Modifier.size(52.dp))
            Spacer(Modifier.height(10.dp))
            Text("ยังไม่มีข้อมูลห้องพัก", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("เพิ่มห้องแรกเพื่อเริ่มจัดการค่าเช่าและอัตราค่าน้ำค่าไฟ", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RoomEditorDialog(
    room: RoomRecord?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onFieldChanged: () -> Unit,
    onSave: (name: String, rent: String, water: String, electricity: String) -> Unit,
) {
    var name by remember(room?.id) { mutableStateOf(room?.name.orEmpty()) }
    var rent by remember(room?.id) { mutableStateOf(room?.monthlyRentSatang?.let(::formatSatangAsBaht).orEmpty()) }
    var water by remember(room?.id) { mutableStateOf(room?.waterPricePerUnitSatang?.let(::formatSatangAsBaht).orEmpty()) }
    var electricity by remember(room?.id) { mutableStateOf(room?.electricityPricePerUnitSatang?.let(::formatSatangAsBaht).orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (room == null) "เพิ่มห้องพัก" else "แก้ไขห้องพัก") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it; onFieldChanged() }, label = { Text("ชื่อห้อง") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                MoneyInput("ค่าเช่ารายเดือน (บาท)", rent) { rent = it; onFieldChanged() }
                MoneyInput("ค่าน้ำต่อหน่วย (บาท)", water) { water = it; onFieldChanged() }
                MoneyInput("ค่าไฟต่อหน่วย (บาท)", electricity) { electricity = it; onFieldChanged() }
                if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(name, rent, water, electricity) }) { Text("บันทึก") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}

@Composable
private fun MoneyInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}
