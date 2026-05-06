package com.checkdang.app.ui.bodymap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.PainRecord
import com.checkdang.app.data.repository.PainRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PainSaveState {
    object Idle : PainSaveState()
    object Loading : PainSaveState()
    object Success : PainSaveState()
    data class Error(val message: String) : PainSaveState()
}

class BodyMapViewModel : ViewModel() {

    private val repository = PainRepository()

    val painRecords: StateFlow<List<PainRecord>> = MockDataProvider.painRecordsFlow

    private val _saveState = MutableStateFlow<PainSaveState>(PainSaveState.Idle)
    val saveState: StateFlow<PainSaveState> = _saveState.asStateFlow()

    fun addPainRecord(record: PainRecord) {
        viewModelScope.launch {
            _saveState.value = PainSaveState.Loading
            runCatching { repository.savePainRecord(record) }
                .onSuccess { _saveState.value = PainSaveState.Success }
                .onFailure { _saveState.value = PainSaveState.Error(it.message ?: "저장 실패") }
        }
    }

    fun resetSaveState() {
        _saveState.value = PainSaveState.Idle
    }
}
