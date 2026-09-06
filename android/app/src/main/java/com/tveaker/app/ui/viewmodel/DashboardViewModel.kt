package com.tveaker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tveaker.app.BuildConfig
import com.tveaker.app.TVeakerApplication
import com.tveaker.app.data.model.AppVersionDto
import com.tveaker.app.data.model.NowWatchingDto
import com.tveaker.app.data.model.RecommendationItemDto
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UnwatchedEpisodesResponseDto
import com.tveaker.app.data.repository.TVeakerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val activeShows: List<ShowEstimateDto> = emptyList(),
    val recommendations: List<RecommendationItemDto> = emptyList(),
    val runId: Int? = null,
    val errorMessage: String? = null,
    val isSyncing: Boolean = false,
    val selectedShowUnwatched: UnwatchedEpisodesResponseDto? = null,
    val nowWatching: NowWatchingDto? = null,
    val isEpisodesLoading: Boolean = false,
    val currentServerUrl: String = "",
    val serverVersionInfo: AppVersionDto? = null,
    val isNewUpdateAvailable: Boolean = false,
    val isOffline: Boolean = false,
    val selectedHeroShowId: Int? = null
)

class DashboardViewModel(
    private val repository: TVeakerRepository = TVeakerApplication.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            currentServerUrl = repository.currentBaseUrl.value,
            isOffline = repository.isOffline.value,
            selectedHeroShowId = repository.selectedHeroShowId.value
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.isOffline.collectLatest { isOffline ->
                _uiState.value = _uiState.value.copy(isOffline = isOffline)
            }
        }
        viewModelScope.launch {
            repository.selectedHeroShowId.collectLatest { heroShowId ->
                _uiState.value = _uiState.value.copy(selectedHeroShowId = heroShowId)
            }
        }
        loadDashboardData()
        viewModelScope.launch {
            repository.currentBaseUrl.drop(1).collectLatest { newUrl ->
                _uiState.value = _uiState.value.copy(currentServerUrl = newUrl)
                loadDashboardData()
            }
        }
    }

    fun setHeroShow(showId: Int) {
        viewModelScope.launch {
            val result = repository.selectNowWatchingShow(showId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    nowWatching = result.getOrNull(),
                    selectedHeroShowId = showId
                )
            }
        }
    }

    fun setServerUrl(newUrl: String) {
        repository.updateBaseUrl(newUrl)
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Prioritize the content that makes Home useful. The local service can
            // serialize expensive reads, so starting recommendations at the same
            // time makes the first visible screen wait on both jobs.
            val showsResult = repository.getShows("watching", limit = 12)
            if (showsResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    activeShows = showsResult.getOrNull() ?: emptyList(),
                    errorMessage = null,
                    isOffline = repository.isOffline.value
                )
            }

            val (recResult, versionResult, nowWatchingResult) = coroutineScope {
                val recommendations = async { repository.getRecommendations(limit = 6) }
                val version = async { repository.getAppVersion() }
                val nowWatching = async { repository.getNowWatching() }
                Triple(recommendations.await(), version.await(), nowWatching.await())
            }

            val versionInfo = versionResult.getOrNull()
            val hasUpdate = versionInfo != null && versionInfo.versionCode > BuildConfig.VERSION_CODE

            val hasCachedNowWatching = nowWatchingResult.isSuccess && nowWatchingResult.getOrNull() != null
            val hasCachedOrRemoteData = showsResult.isSuccess || recResult.isSuccess || hasCachedNowWatching

            if (hasCachedOrRemoteData) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    activeShows = showsResult.getOrNull() ?: emptyList(),
                    recommendations = recResult.getOrNull()?.items ?: emptyList(),
                    runId = recResult.getOrNull()?.runId,
                    nowWatching = nowWatchingResult.getOrNull(),
                    errorMessage = null,
                    serverVersionInfo = versionInfo,
                    isNewUpdateAvailable = hasUpdate,
                    isOffline = repository.isOffline.value
                )
            } else {
                val error = showsResult.exceptionOrNull()?.message
                    ?: recResult.exceptionOrNull()?.message
                    ?: nowWatchingResult.exceptionOrNull()?.message
                    ?: "Cannot connect to server at ${repository.currentBaseUrl.value}"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error,
                    serverVersionInfo = versionInfo,
                    isNewUpdateAvailable = hasUpdate,
                    isOffline = repository.isOffline.value
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
                    selectedShowUnwatched = result.getOrNull(),
                    isOffline = repository.isOffline.value
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isEpisodesLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to load episodes",
                    isOffline = repository.isOffline.value
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

    fun selectNowWatching(episodeId: Int) {
        viewModelScope.launch {
            val result = repository.selectNowWatching(episodeId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    nowWatching = result.getOrNull(),
                    selectedShowUnwatched = null,
                    errorMessage = null
                )
                loadDashboardData()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Could not choose this episode"
                )
            }
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
