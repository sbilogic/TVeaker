package com.tveaker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tveaker.app.data.model.RecommendationItemDto
import com.tveaker.app.data.repository.TVeakerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecommendationsUiState(
    val isLoading: Boolean = false,
    val items: List<RecommendationItemDto> = emptyList(),
    val runId: Int? = null,
    val selectedBudget: Int? = null,
    val selectedIntent: String = "auto",
    val errorMessage: String? = null
)

class RecommendationsViewModel(
    private val repository: TVeakerRepository = TVeakerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationsUiState())
    val uiState: StateFlow<RecommendationsUiState> = _uiState.asStateFlow()

    init {
        loadRecommendations()
    }

    fun setBudget(budget: Int?) {
        _uiState.value = _uiState.value.copy(selectedBudget = budget)
        loadRecommendations()
    }

    fun setIntent(intent: String) {
        _uiState.value = _uiState.value.copy(selectedIntent = intent)
        loadRecommendations()
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.getRecommendations(
                timeBudgetMinutes = _uiState.value.selectedBudget,
                intent = _uiState.value.selectedIntent,
                limit = 15
            )
            if (result.isSuccess) {
                val res = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = res?.items ?: emptyList(),
                    runId = res?.runId
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to load recommendations"
                )
            }
        }
    }

    fun submitFeedback(candidateId: String, action: String) {
        val runId = _uiState.value.runId ?: return
        viewModelScope.launch {
            repository.submitFeedback(runId, candidateId, action)
            _uiState.value = _uiState.value.copy(
                items = _uiState.value.items.filter { it.candidateId != candidateId }
            )
        }
    }
}
