package com.pondktpprint.roomledger.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pondktpprint.roomledger.domain.RoomInputResult
import com.pondktpprint.roomledger.domain.RoomInputValidator
import com.pondktpprint.roomledger.domain.RoomRecord
import com.pondktpprint.roomledger.domain.RoomGroup
import com.pondktpprint.roomledger.domain.RoomRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoomLedgerViewModel(
    private val repository: RoomRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val rooms: StateFlow<List<RoomRecord>> = repository.observeRooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val roomGroups: StateFlow<List<RoomGroup>> = repository.observeRoomGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    fun clearFormError() {
        _formError.value = null
    }

    fun saveRoom(
        id: String?,
        name: String,
        rent: String,
        water: String,
        electricity: String,
        onSaved: () -> Unit,
    ) {
        when (val result = RoomInputValidator.validate(name, rent, water, electricity)) {
            is RoomInputResult.Invalid -> _formError.value = result.message
            is RoomInputResult.Valid -> viewModelScope.launch {
                val existing = id?.let { roomId -> rooms.value.firstOrNull { it.id == roomId } }
                val now = nowMillis()
                runCatching {
                    repository.saveRoom(
                        RoomRecord(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = result.input.name,
                            monthlyRentSatang = result.input.monthlyRentSatang,
                            waterPricePerUnitSatang = result.input.waterPricePerUnitSatang,
                            electricityPricePerUnitSatang = result.input.electricityPricePerUnitSatang,
                            isActive = existing?.isActive ?: true,
                            createdAtMillis = existing?.createdAtMillis ?: now,
                            updatedAtMillis = now,
                            groupId = existing?.groupId,
                        ),
                    )
                }.onSuccess {
                    _formError.value = null
                    onSaved()
                }.onFailure {
                    _formError.value = "บันทึกข้อมูลไม่สำเร็จ กรุณาลองอีกครั้ง"
                }
            }
        }
    }

    fun setRoomActive(id: String, isActive: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setRoomActive(id, isActive, nowMillis()) }
                .onFailure { _formError.value = "เปลี่ยนสถานะห้องไม่สำเร็จ กรุณาลองอีกครั้ง" }
        }
    }

    fun createRoomGroup(name: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.createRoomGroup(name, nowMillis()) }
                .onSuccess { _formError.value = null; onSaved() }
                .onFailure { _formError.value = it.message ?: "สร้างกลุ่มไม่สำเร็จ" }
        }
    }

    fun renameRoomGroup(id: String, name: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.renameRoomGroup(id, name, nowMillis()) }
                .onSuccess { _formError.value = null; onSaved() }
                .onFailure { _formError.value = it.message ?: "เปลี่ยนชื่อกลุ่มไม่สำเร็จ" }
        }
    }

    fun deleteRoomGroup(id: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.deleteRoomGroup(id, nowMillis()) }
                .onSuccess { _formError.value = null; onDeleted() }
                .onFailure { _formError.value = it.message ?: "ลบกลุ่มไม่สำเร็จ" }
        }
    }

    fun assignRoomToGroup(roomId: String, groupId: String?) {
        viewModelScope.launch {
            runCatching { repository.assignRoomToGroup(roomId, groupId, nowMillis()) }
                .onFailure { _formError.value = it.message ?: "ย้ายกลุ่มห้องไม่สำเร็จ" }
        }
    }

    class Factory(private val repository: RoomRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RoomLedgerViewModel::class.java))
            return RoomLedgerViewModel(repository) as T
        }
    }
}
