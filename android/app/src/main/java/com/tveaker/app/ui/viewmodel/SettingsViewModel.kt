package com.tveaker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tveaker.app.data.model.HealthDto
import com.tveaker.app.data.repository.TVeakerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val health: HealthDto? = null,
    val baseUrl: String = "http://10.0.2.2:8000/",
    val syncMessage: String? = null,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val repository: TVeakerRepository = TVeakerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        checkHealth()
    }

    fun setBaseUrl(newUrl: String) {
        repository.updateBaseUrl(newUrl)
        _uiState.value = _uiState.value.copy(baseUrl = newUrl)
        checkHealth()
    }

    fun checkHealth() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.getHealth()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    health = result.getOrNull()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to connect to TVeaker backend"
                )
            }
        }
    }

    fun triggerSync(mode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null)
            val result = repository.triggerSync(mode)
            if (result.isSuccess) {
                val rep = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "Sync completed: ${rep?.status} (Fetched: ${rep?.fetched?.values?.sum() ?: 0} items)"
                )
                checkHealth()
            } else {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Sync failed"
                )
            }
        }
    }
}
