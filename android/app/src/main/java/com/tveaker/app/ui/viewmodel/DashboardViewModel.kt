package com.tveaker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tveaker.app.TVeakerApplication
import com.tveaker.app.data.model.RecommendationItemDto
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UnwatchedEpisodesResponseDto
import com.tveaker.app.data.repository.TVeakerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val activeShows: List<ShowEstimateDto> = emptyList(),
    val recommendations: List<RecommendationItemDto> = emptyList(),
    val runId: Int? = null,
    val errorMessage: String? = null,
    val isSyncing: Boolean = false,
    val selectedShowUnwatched: UnwatchedEpisodesResponseDto? = null,
    val isEpisodesLoading: Boolean = false,
    val currentServerUrl: String = ""
)

class DashboardViewModel(
    private val repository: TVeakerRepository = TVeakerApplication.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DashboardUiState(currentServerUrl = repository.currentBaseUrl.value)
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
        viewModelScope.launch {
            repository.currentBaseUrl.collectLatest { newUrl ->
                _uiState.value = _uiState.value.copy(currentServerUrl = newUrl)
                loadDashboardData()
            }
        }
    }

    fun setServerUrl(newUrl: String) {
        repository.updateBaseUrl(newUrl)
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val showsResult = repository.getShows("watching")
            val recResult = repository.getRecommendations(limit = 6)

            if (showsResult.isSuccess || recResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    activeShows = showsResult.getOrNull() ?: emptyList(),
                    recommendations = recResult.getOrNull()?.items ?: emptyList(),
                    runId = recResult.getOrNull()?.runId,
                    errorMessage = null
                )
            } else {
                val error = showsResult.exceptionOrNull()?.message
                    ?: recResult.exceptionOrNull()?.message
                    ?: "Cannot connect to server at ${repository.currentBaseUrl.value}"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error
                )
            }
        }
    }

    fun loadUnwatchedEpisodes(showId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isEpisodesLoading = true)
            val result = repository.getUnwatchedEpisodes(showId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isEpisodesLoading = false,
                    selectedShowUnwatched = result.getOrNull()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isEpisodesLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to load episodes"
                )
            }
        }
    }

    fun dismissEpisodesSheet() {
        _uiState.value = _uiState.value.copy(selectedShowUnwatched = null)
    }

    fun markEpisodeWatched(showId: Int, episodeId: Int) {
        viewModelScope.launch {
            repository.watchEpisode(showId, episodeId)
            loadUnwatchedEpisodes(showId)
            loadDashboardData()
        }
    }

    fun quickScrobble(showId: Int) {
        viewModelScope.launch {
            repository.quickScrobble(showId)
            loadDashboardData()
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

    fun submitFeedback(runId: Int, candidateId: String, action: String) {
        viewModelScope.launch {
            repository.submitFeedback(runId, candidateId, action)
            _uiState.value = _uiState.value.copy(
                recommendations = _uiState.value.recommendations.filter { it.candidateId != candidateId }
            )
        }
    }
}
