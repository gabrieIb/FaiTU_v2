package com.menumanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menumanager.data.HouseholdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HouseholdViewModel(
    private val repository: HouseholdRepository
) : ViewModel() {

    private val _state = MutableStateFlow<HouseholdState>(HouseholdState.Loading)
    val state: StateFlow<HouseholdState> = _state.asStateFlow()

    init {
        checkHouseholdStatus()
    }

    private fun checkHouseholdStatus() {
        viewModelScope.launch {
            val householdId = repository.getHouseholdId()
            if (householdId != null) {
                val inviteCode = repository.getInviteCode()
                _state.value = HouseholdState.Ready(householdId, inviteCode)
            } else {
                _state.value = HouseholdState.NeedsSetup
            }
        }
    }

    fun createHousehold() {
        viewModelScope.launch {
            _state.value = HouseholdState.Creating
            try {
                val (householdId, inviteCode) = repository.createHousehold()
                _state.value = HouseholdState.Ready(householdId, inviteCode)
            } catch (e: Exception) {
                _state.value = HouseholdState.Error(e.message ?: "Errore durante la creazione")
            }
        }
    }

    fun joinHousehold(inviteCode: String) {
        viewModelScope.launch {
            _state.value = HouseholdState.Joining
            try {
                val householdId = repository.joinHousehold(inviteCode.trim().uppercase())
                val code = repository.getInviteCode()
                _state.value = HouseholdState.Ready(householdId, code)
            } catch (e: Exception) {
                _state.value = HouseholdState.Error(e.message ?: "Codice invito non valido")
            }
        }
    }

    fun overrideHouseholdId(householdId: String, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            val targetId = householdId.trim()
            runCatching {
                repository.overrideHouseholdId(targetId)
            }.onSuccess { inviteCode ->
                _state.value = HouseholdState.Ready(targetId, inviteCode)
                onResult(null)
            }.onFailure { error ->
                onResult(error)
            }
        }
    }

    fun dismissError() {
        _state.value = HouseholdState.NeedsSetup
    }
}

sealed class HouseholdState {
    object Loading : HouseholdState()
    object NeedsSetup : HouseholdState()
    object Creating : HouseholdState()
    object Joining : HouseholdState()
    data class Ready(val householdId: String, val inviteCode: String?) : HouseholdState()
    data class Error(val message: String) : HouseholdState()
}
