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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pondktpprint.roomledger.domain.RoomRecord
import com.pondktpprint.roomledger.domain.RoomGroup
import com.pondktpprint.roomledger.domain.RoomDeletionResult
import com.pondktpprint.roomledger.domain.RoomRepository
import com.pondktpprint.roomledger.domain.formatSatangAsBaht
import com.pondktpprint.roomledger.RoomLedgerApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomLedgerScreen(repository: RoomRepository) {
    val viewModel: RoomLedgerViewModel = viewModel(factory = RoomLedgerViewModel.Factory(repository))
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val roomGroups by viewModel.roomGroups.collectAsStateWithLifecycle()
    val formError by viewModel.formError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editorRoom by remember { mutableStateOf<RoomRecord?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var billingDestination by rememberSaveable { mutableStateOf(0) }
    var showBackupSettings by rememberSaveable { mutableStateOf(false) }
    var groupEditor by remember { mutableStateOf<GroupEditorState?>(null) }
    var groupToDelete by remember { mutableStateOf<RoomGroup?>(null) }
    var assignmentRoom by remember { mutableStateOf<RoomRecord?>(null) }
    var roomToDelete by remember { mutableStateOf<RoomRecord?>(null) }
    var roomDeletionBlocked by remember { mutableStateOf<RoomRecord?>(null) }
    var collapsedGroups by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var ungroupedCollapsed by rememberSaveable { mutableStateOf(false) }
    var selectedManagementGroup by rememberSaveable { mutableStateOf("ALL") }

    if (showBackupSettings) {
        BackupSettingsScreen(
            database = (androidx.compose.ui.platform.LocalContext.current.applicationContext as RoomLedgerApplication).database,
            onBack = { showBackupSettings = false },
        )
        return
    }

    if (billingDestination != 0) {
        BillingSection(rooms, roomGroups, repository, onBack = { billingDestination = 0 }, startWithHistory = billingDestination == 2)
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showBackupSettings = true }, modifier = Modifier.fillMaxWidth()) { Text("ตั้งค่า · สำรองและกู้คืนข้อมูล") }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("จัดการห้องพัก", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${rooms.size} ห้อง · ${roomGroups.size} กลุ่ม", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = {
                    editorRoom = null
                    viewModel.clearFormError()
                    showEditor = true
                }) {
                    Text("+ เพิ่มห้อง")
                }
            }
            if (roomGroups.isEmpty()) {
                OutlinedButton(onClick = { groupEditor = GroupEditorState(null) }, modifier = Modifier.fillMaxWidth()) { Text("+ เพิ่มกลุ่มห้อง") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { groupEditor = GroupEditorState(null) }) { Text("+ เพิ่มกลุ่ม") }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selectedManagementGroup == "ALL", onClick = { selectedManagementGroup = "ALL" }, label = { Text("ทุกกลุ่ม") }) }
                items(roomGroups, key = RoomGroup::id) { group ->
                    FilterChip(selectedManagementGroup == group.id, onClick = { selectedManagementGroup = group.id }, label = { Text(group.name) })
                }
                item { FilterChip(selectedManagementGroup == "UNGROUPED", onClick = { selectedManagementGroup = "UNGROUPED" }, label = { Text("ไม่จัดกลุ่ม") }) }
            }
            val managementRooms = when (selectedManagementGroup) {
                "ALL" -> rooms
                "UNGROUPED" -> rooms.filter { it.groupId == null || roomGroups.none { group -> group.id == it.groupId } }
                else -> rooms.filter { it.groupId == selectedManagementGroup }
            }
            if (rooms.isEmpty() && roomGroups.isEmpty()) {
                EmptyRooms()
            } else if (managementRooms.isEmpty()) {
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text("กลุ่มนี้ยังไม่มีห้อง", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    roomGroups.filter { selectedManagementGroup == "ALL" || selectedManagementGroup == it.id }.forEach { group ->
                        val groupRooms = managementRooms.filter { it.groupId == group.id }
                        item(key = "group-${group.id}") {
                            RoomGroupHeader(
                                group = group,
                                roomCount = groupRooms.size,
                                expanded = group.id !in collapsedGroups,
                                onToggle = { collapsedGroups = if (group.id in collapsedGroups) collapsedGroups - group.id else collapsedGroups + group.id },
                                onRename = { groupEditor = GroupEditorState(group) },
                                onDelete = { groupToDelete = group },
                            )
                        }
                        if (group.id !in collapsedGroups) items(items = groupRooms, key = RoomRecord::id) { room ->
                            RoomCard(
                                room = room,
                                groupLabel = group.name,
                                onEdit = { editorRoom = room; viewModel.clearFormError(); showEditor = true },
                                onAssign = { assignmentRoom = room },
                                onDelete = {
                                    scope.launch {
                                        runCatching { repository.roomHasFinancialHistory(room.id) }
                                            .onSuccess { hasHistory -> if (hasHistory) roomDeletionBlocked = room else roomToDelete = room }
                                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "ตรวจสอบประวัติห้องไม่สำเร็จ") }
                                    }
                                },
                                onActiveChanged = { active -> viewModel.setRoomActive(room.id, active) },
                            )
                        }
                    }
                    val ungroupedRooms = managementRooms.filter { it.groupId == null || roomGroups.none { group -> group.id == it.groupId } }
                    if (selectedManagementGroup == "ALL" || selectedManagementGroup == "UNGROUPED") item(key = "group-ungrouped") {
                        UngroupedHeader(roomCount = ungroupedRooms.size, expanded = !ungroupedCollapsed, onToggle = { ungroupedCollapsed = !ungroupedCollapsed })
                    }
                    if (!ungroupedCollapsed && (selectedManagementGroup == "ALL" || selectedManagementGroup == "UNGROUPED")) items(items = ungroupedRooms, key = RoomRecord::id) { room ->
                        RoomCard(
                            room = room,
                            groupLabel = "ไม่จัดกลุ่ม",
                            onEdit = { editorRoom = room; viewModel.clearFormError(); showEditor = true },
                            onAssign = { assignmentRoom = room },
                            onDelete = {
                                scope.launch {
                                    runCatching { repository.roomHasFinancialHistory(room.id) }
                                        .onSuccess { hasHistory -> if (hasHistory) roomDeletionBlocked = room else roomToDelete = room }
                                        .onFailure { snackbarHostState.showSnackbar(it.message ?: "ตรวจสอบประวัติห้องไม่สำเร็จ") }
                                }
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

    groupEditor?.let { editor ->
        RoomGroupEditorDialog(
            group = editor.group,
            errorMessage = formError,
            onDismiss = { groupEditor = null; viewModel.clearFormError() },
            onSave = { name ->
                if (editor.group == null) viewModel.createRoomGroup(name) { groupEditor = null }
                else viewModel.renameRoomGroup(editor.group.id, name) { groupEditor = null }
            },
        )
    }
    groupToDelete?.let { group ->
        RoomGroupDeleteDialog(
            group = group,
            roomCount = rooms.count { it.groupId == group.id },
            onDismiss = { groupToDelete = null },
            onConfirm = { viewModel.deleteRoomGroup(group.id) { groupToDelete = null } },
        )
    }
    assignmentRoom?.let { room ->
        RoomGroupPickerDialog(
            room = room,
            groups = roomGroups,
            onDismiss = { assignmentRoom = null },
            onSelect = { groupId -> viewModel.assignRoomToGroup(room.id, groupId); assignmentRoom = null },
        )
    }
    roomToDelete?.let { room ->
        RoomDeleteConfirmDialog(
            room = room,
            onDismiss = { roomToDelete = null },
            onConfirm = {
                scope.launch {
                    when (repository.deleteRoomPermanently(room.id)) {
                        RoomDeletionResult.DELETED -> { roomToDelete = null; snackbarHostState.showSnackbar("ลบห้อง ${room.name} แล้ว") }
                        RoomDeletionResult.HAS_HISTORY -> { roomToDelete = null; roomDeletionBlocked = room }
                        RoomDeletionResult.NOT_FOUND -> { roomToDelete = null; snackbarHostState.showSnackbar("ไม่พบห้องนี้แล้ว") }
                    }
                }
            },
        )
    }
    roomDeletionBlocked?.let { room ->
        RoomDeleteBlockedDialog(room = room, onDismiss = { roomDeletionBlocked = null })
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
private fun RoomCard(
    room: RoomRecord,
    groupLabel: String,
    onEdit: () -> Unit,
    onAssign: () -> Unit,
    onDelete: () -> Unit,
    onActiveChanged: (Boolean) -> Unit,
) {
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
                    Text(groupLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAssign, modifier = Modifier.weight(1f)) { Text("ย้ายกลุ่ม") }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("ลบห้อง") }
            }
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

private data class GroupEditorState(val group: RoomGroup?)

@Composable
private fun RoomGroupHeader(
    group: RoomGroup,
    roomCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (expanded) "⌄" else "›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$roomCount ห้อง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRename) { Text("เปลี่ยนชื่อ") }
                TextButton(onClick = onDelete) { Text("ลบกลุ่ม", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun UngroupedHeader(roomCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "⌄" else "›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("ไม่จัดกลุ่ม", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$roomCount ห้อง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RoomGroupEditorDialog(group: RoomGroup?, errorMessage: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(group?.id) { mutableStateOf(group?.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "สร้างกลุ่มห้อง" else "เปลี่ยนชื่อกลุ่ม") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("ชื่อกลุ่ม") }, singleLine = true)
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("บันทึก") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}

@Composable
private fun RoomGroupDeleteDialog(group: RoomGroup, roomCount: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ลบกลุ่ม ${group.name}") },
        text = { Text("ห้อง $roomCount ห้องในกลุ่มนี้จะยังอยู่ในระบบ และจะย้ายไปอยู่ในส่วน “ไม่จัดกลุ่ม”") },
        confirmButton = { Button(onClick = onConfirm) { Text("ลบกลุ่ม") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}

@Composable
private fun RoomGroupPickerDialog(room: RoomRecord, groups: List<RoomGroup>, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ย้ายห้อง ${room.name}") },
        text = {
            Column {
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("ไม่จัดกลุ่ม", modifier = Modifier.fillMaxWidth()) }
                groups.forEach { group ->
                    TextButton(onClick = { onSelect(group.id) }, modifier = Modifier.fillMaxWidth()) { Text(group.name, modifier = Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss) { Text("ปิด") } },
    )
}

@Composable
private fun RoomDeleteConfirmDialog(room: RoomRecord, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ยืนยันลบห้อง ${room.name}") },
        text = { Text("การลบห้องนี้เป็นการลบถาวรและไม่สามารถเรียกคืนได้ ระบบตรวจแล้วว่าห้องนี้ไม่มีเลขมิเตอร์ บิล หรือประวัติการเงินที่อ้างอิงอยู่") },
        confirmButton = { Button(onClick = onConfirm, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("ลบถาวร") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}

@Composable
private fun RoomDeleteBlockedDialog(room: RoomRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ลบห้อง ${room.name} ไม่ได้") },
        text = { Text("ห้องนี้มีเลขมิเตอร์ บิล รายการรับชำระ หรือประวัติยกยอดที่เกี่ยวข้อง ระบบจะเก็บประวัติทั้งหมดไว้ กรุณาปิดใช้งานห้องแทน") },
        confirmButton = { Button(onClick = onDismiss) { Text("เข้าใจแล้ว") } },
    )
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
