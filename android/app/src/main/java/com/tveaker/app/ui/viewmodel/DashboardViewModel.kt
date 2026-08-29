package com.tveaker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tveaker.app.data.model.RecommendationItemDto
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.repository.TVeakerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val activeShows: List<ShowEstimateDto> = emptyList(),
    val recommendations: List<RecommendationItemDto> = emptyList(),
    val runId: Int? = null,
    val errorMessage: String? = null,
    val isSyncing: Boolean = false
)

class DashboardViewModel(
    private val repository: TVeakerRepository = TVeakerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val showsResult = repository.getShows("watching")
            val recResult = repository.getRecommendations(limit = 6)

            if (showsResult.isSuccess && recResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    activeShows = showsResult.getOrNull() ?: emptyList(),
                    recommendations = recResult.getOrNull()?.items ?: emptyList(),
                    runId = recResult.getOrNull()?.runId
                )
            } else {
                val error = showsResult.exceptionOrNull()?.message
                    ?: recResult.exceptionOrNull()?.message
                    ?: "Failed to load dashboard data"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error
                )
            }
        }
    }

    fun triggerSync(mode: String = "incremental") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            repository.triggerSync(mode)
            _uiState.value = _uiState.value.copy(isSyncing = false)
            loadDashboardData()
        }
    }

    fun submitFeedback(candidateId: String, action: String) {
        val runId = _uiState.value.runId ?: return
        viewModelScope.launch {
            repository.submitFeedback(runId, candidateId, action)
            _uiState.value = _uiState.value.copy(
                recommendations = _uiState.value.recommendations.filter { it.candidateId != candidateId }
            )
        }
    }
}
