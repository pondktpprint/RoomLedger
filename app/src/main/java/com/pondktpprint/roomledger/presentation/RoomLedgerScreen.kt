package com.pondktpprint.roomledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pondktpprint.roomledger.domain.RoomRecord
import com.pondktpprint.roomledger.domain.RoomRepository
import com.pondktpprint.roomledger.domain.formatSatangAsBaht

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomLedgerScreen(repository: RoomRepository) {
    val viewModel: RoomLedgerViewModel = viewModel(factory = RoomLedgerViewModel.Factory(repository))
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val formError by viewModel.formError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editorRoom by remember { mutableStateOf<RoomRecord?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var billingDestination by rememberSaveable { mutableStateOf(0) }

    if (billingDestination != 0) {
        BillingSection(rooms, repository, onBack = { billingDestination = 0 }, startWithHistory = billingDestination == 2)
        return
    }

    LaunchedEffect(formError) {
        if (formError != null && !showEditor) snackbarHostState.showSnackbar(formError.orEmpty())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RoomLedger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("จัดการห้องเช่าของคุณ", style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            DashboardSummary(rooms = rooms)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { billingDestination = 1 }, modifier = Modifier.weight(1f)) { Text("บันทึกมิเตอร์") }
                OutlinedButton(onClick = { billingDestination = 2 }, modifier = Modifier.weight(1f)) { Text("ประวัติบิล") }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("ห้องพักทั้งหมด", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = {
                    editorRoom = null
                    viewModel.clearFormError()
                    showEditor = true
                }) {
                    Text("+ เพิ่มห้อง")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (rooms.isEmpty()) {
                EmptyRooms()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = rooms, key = RoomRecord::id) { room ->
                        RoomCard(
                            room = room,
                            onEdit = {
                                editorRoom = room
                                viewModel.clearFormError()
                                showEditor = true
                            },
                            onActiveChanged = { active -> viewModel.setRoomActive(room.id, active) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    if (showEditor) {
        RoomEditorDialog(
            room = editorRoom,
            errorMessage = formError,
            onDismiss = {
                showEditor = false
                viewModel.clearFormError()
            },
            onFieldChanged = viewModel::clearFormError,
            onSave = { name, rent, water, electricity ->
                viewModel.saveRoom(editorRoom?.id, name, rent, water, electricity) {
                    showEditor = false
                }
            },
        )
    }
}

@Composable
private fun DashboardSummary(rooms: List<RoomRecord>) {
    val activeRooms = rooms.filter(RoomRecord::isActive)
    val monthlyRent = activeRooms.sumOf(RoomRecord::monthlyRentSatang)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("ภาพรวม", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryValue(label = "ห้องที่เปิดใช้งาน", value = "${activeRooms.size} ห้อง")
                SummaryValue(label = "ค่าเช่ารวมต่อเดือน", value = "฿${formatSatangAsBaht(monthlyRent)}")
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun RoomCard(room: RoomRecord, onEdit: () -> Unit, onActiveChanged: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (room.isActive) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (room.isActive) "เปิดใช้งาน" else "ปิดใช้งาน",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (room.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onEdit) { Text("แก้ไข") }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            RoomPriceLine("ค่าเช่ารายเดือน", room.monthlyRentSatang)
            Spacer(Modifier.height(6.dp))
            RoomPriceLine("ค่าน้ำ / หน่วย", room.waterPricePerUnitSatang)
            Spacer(Modifier.height(6.dp))
            RoomPriceLine("ค่าไฟ / หน่วย", room.electricityPricePerUnitSatang)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
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
        Text("฿${formatSatangAsBaht(satang)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyRooms() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ยังไม่มีข้อมูลห้องพัก", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onFieldChanged() },
                    label = { Text("ชื่อห้อง") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                MoneyInput("ค่าเช่ารายเดือน (บาท)", rent, { rent = it; onFieldChanged() })
                MoneyInput("ค่าน้ำต่อหน่วย (บาท)", water, { water = it; onFieldChanged() })
                MoneyInput("ค่าไฟต่อหน่วย (บาท)", electricity, { electricity = it; onFieldChanged() })
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, rent, water, electricity) }) { Text("บันทึก") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("ยกเลิก") }
        },
    )
}

@Composable
private fun MoneyInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
