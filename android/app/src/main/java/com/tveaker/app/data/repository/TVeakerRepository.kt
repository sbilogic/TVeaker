package com.tveaker.app.data.repository

import android.content.SharedPreferences
import com.tveaker.app.data.api.TVeakerApiService
import com.tveaker.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class TVeakerRepository(
    initialBaseUrl: String = TVeakerApiService.DEFAULT_BASE_URL,
    private val prefs: SharedPreferences? = null
) {
    private val _currentBaseUrl = MutableStateFlow(
        prefs?.getString("base_url", initialBaseUrl) ?: initialBaseUrl
    )
    val currentBaseUrl: StateFlow<String> = _currentBaseUrl.asStateFlow()

    @Volatile
    private var apiService: TVeakerApiService = TVeakerApiService.create(_currentBaseUrl.value)

    fun updateBaseUrl(newBaseUrl: String) {
        val trimmed = newBaseUrl.trim()
        val formatted = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        prefs?.edit()?.putString("base_url", formatted)?.apply()
        _currentBaseUrl.value = formatted
        apiService = TVeakerApiService.create(formatted)
    }

    fun getApiService(): TVeakerApiService = apiService

    /**
     * Attempts an API call with auto-fallback to alternative LAN / Emulator host if connection fails.
     */
    private suspend fun <T> executeWithFallback(block: suspend (TVeakerApiService) -> T): Result<T> = withContext(Dispatchers.IO) {
        val primaryResult = runCatching { block(apiService) }
        if (primaryResult.isSuccess) {
            return@withContext primaryResult
        }

        // If primary call failed due to connection error, attempt fallback host
        val currentUrl = _currentBaseUrl.value
        val fallbackUrl = when {
            currentUrl.contains("10.0.2.2") -> currentUrl.replace("10.0.2.2", "192.168.1.33")
            currentUrl.contains("192.168.1.33") -> currentUrl.replace("192.168.1.33", "10.0.2.2")
            else -> null
        }

        if (fallbackUrl != null && fallbackUrl != currentUrl) {
            val fallbackService = TVeakerApiService.create(fallbackUrl)
            val fallbackResult = runCatching { block(fallbackService) }
            if (fallbackResult.isSuccess) {
                updateBaseUrl(fallbackUrl)
                return@withContext fallbackResult
            }
        }

        primaryResult
    }

    suspend fun getHealth(): Result<HealthDto> = executeWithFallback { it.getHealth() }

    suspend fun getShows(status: String? = null): Result<List<ShowEstimateDto>> = executeWithFallback {
        it.getShows(status)
    }

    suspend fun updateShow(showId: Int, request: UpdateShowRequest): Result<ShowEstimateDto> = executeWithFallback {
        it.updateShow(showId, request)
    }

    suspend fun getUnwatchedEpisodes(showId: Int): Result<UnwatchedEpisodesResponseDto> = executeWithFallback {
        it.getUnwatchedEpisodes(showId)
    }

    suspend fun quickScrobble(showId: Int): Result<Map<String, Any>> = executeWithFallback {
        it.quickScrobble(showId)
    }

    suspend fun watchEpisode(showId: Int, episodeId: Int): Result<Map<String, Any>> = executeWithFallback {
        it.watchEpisode(showId, episodeId)
    }

    suspend fun getRecommendations(
        timeBudgetMinutes: Int? = null,
        intent: String = "auto",
        limit: Int = 10
    ): Result<RecommendationResponseDto> = executeWithFallback {
        it.getRecommendations(timeBudgetMinutes, intent, limit)
    }

    suspend fun submitFeedback(runId: Int, candidateId: String, action: String): Result<Unit> = executeWithFallback {
        it.submitFeedback(FeedbackRequest(runId, candidateId, action))
        Unit
    }

    suspend fun triggerSync(mode: String = "incremental"): Result<SyncReportDto> = executeWithFallback {
        it.triggerSync(SyncTriggerRequest(mode))
    }

    suspend fun getAppVersion(): Result<AppVersionDto> = executeWithFallback {
        it.getAppVersion()
    }
}
