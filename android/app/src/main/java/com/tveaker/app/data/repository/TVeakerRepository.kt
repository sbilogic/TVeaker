package com.tveaker.app.data.repository

import android.content.SharedPreferences
import com.tveaker.app.data.api.GatewayUrl
import com.tveaker.app.data.api.TVeakerApiService
import com.tveaker.app.data.cache.InMemoryTVeakerLocalCache
import com.tveaker.app.data.cache.TVeakerLocalCache
import com.tveaker.app.data.model.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

open class TVeakerRepository(
    initialBaseUrl: String = TVeakerApiService.DEFAULT_BASE_URL,
    private val prefs: SharedPreferences? = null,
    val localCache: TVeakerLocalCache = InMemoryTVeakerLocalCache(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialApiService: TVeakerApiService? = null
) {
    private val _currentBaseUrl = MutableStateFlow(
        prefs?.getString("base_url", initialBaseUrl) ?: initialBaseUrl
    )
    val currentBaseUrl: StateFlow<String> = _currentBaseUrl.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    @Volatile
    private var apiService: TVeakerApiService = initialApiService ?: TVeakerApiService.create(_currentBaseUrl.value)

    fun updateBaseUrl(newBaseUrl: String) {
        val formatted = GatewayUrl.normalize(newBaseUrl)
        prefs?.edit()?.putString("base_url", formatted)?.apply()
        _currentBaseUrl.value = formatted
        apiService = TVeakerApiService.create(formatted)
        _isOffline.value = false
    }

    fun getApiService(): TVeakerApiService = apiService

    /** Executes against the explicit online gateway configured by the user. */
    private suspend fun <T> executeWithFallback(block: suspend (TVeakerApiService) -> T): Result<T> = withContext(ioDispatcher) {
        if (!GatewayUrl.isConfigured(_currentBaseUrl.value)) {
            _isOffline.value = true
            return@withContext Result.failure(
                IllegalStateException("No phone gateway is configured. Add the online HTTPS URL from TVeaker Settings.")
            )
        }
        val result = runCatching { block(apiService) }
        if (result.isSuccess) {
            _isOffline.value = false
        } else {
            val ex = result.exceptionOrNull()
            val isConnectivityError = ex is java.io.IOException ||
                ex is IllegalStateException ||
                (ex is retrofit2.HttpException && ex.code() in 500..599)
            if (isConnectivityError) {
                _isOffline.value = true
            }
        }
        result
    }

    suspend fun getHealth(): Result<HealthDto> = executeWithFallback { it.getHealth() }

    open suspend fun getShows(status: String? = null, limit: Int? = null): Result<List<ShowEstimateDto>> {
        val remoteResult = executeWithFallback { it.getShows(status, limit) }
        return if (remoteResult.isSuccess) {
            val shows = remoteResult.getOrThrow()
            val isPartial = limit != null && limit > 0
            localCache.saveShows(shows, status, isPartial)
            remoteResult
        } else {
            val cached = localCache.getShows(status)
            if (cached != null) {
                val limited = if (limit != null && limit > 0) cached.take(limit) else cached
                Result.success(limited)
            } else {
                remoteResult
            }
        }
    }

    open suspend fun updateShow(showId: Int, request: UpdateShowRequest): Result<ShowEstimateDto> {
        val result = executeWithFallback { it.updateShow(showId, request) }
        if (result.isSuccess) {
            localCache.updateShow(result.getOrThrow())
        }
        return result
    }

    open suspend fun getUnwatchedEpisodes(showId: Int): Result<UnwatchedEpisodesResponseDto> {
        val remoteResult = executeWithFallback { it.getUnwatchedEpisodes(showId) }
        return if (remoteResult.isSuccess) {
            val episodes = remoteResult.getOrThrow()
            localCache.saveUnwatchedEpisodes(showId, episodes)
            remoteResult
        } else {
            val cached = localCache.getUnwatchedEpisodes(showId)
            if (cached != null) {
                Result.success(cached)
            } else {
                remoteResult
            }
        }
    }

    open suspend fun getNowWatching(): Result<NowWatchingDto?> {
        val remoteResult = executeWithFallback { it.getNowWatching() }
        return if (remoteResult.isSuccess) {
            val nowWatching = remoteResult.getOrNull()
            localCache.saveNowWatching(nowWatching)
            remoteResult
        } else {
            if (localCache.hasNowWatching()) {
                Result.success(localCache.getNowWatching())
            } else {
                remoteResult
            }
        }
    }

    suspend fun selectNowWatching(episodeId: Int): Result<NowWatchingDto> {
        val result = executeWithFallback {
            it.selectNowWatching(NowWatchingSelectionRequest(episodeId))
        }
        if (result.isSuccess) {
            localCache.saveNowWatching(result.getOrThrow())
        }
        return result
    }

    suspend fun clearNowWatching(): Result<Unit> {
        val result = executeWithFallback {
            it.clearNowWatching()
            Unit
        }
        if (result.isSuccess) {
            localCache.saveNowWatching(null)
        }
        return result
    }

    suspend fun quickScrobble(showId: Int): Result<Map<String, Any>> = executeWithFallback {
        it.quickScrobble(showId)
    }

    suspend fun watchEpisode(showId: Int, episodeId: Int): Result<Map<String, Any>> = executeWithFallback {
        it.watchEpisode(showId, episodeId)
    }

    open suspend fun getRecommendations(
        timeBudgetMinutes: Int? = null,
        intent: String = "auto",
        limit: Int = 10
    ): Result<RecommendationResponseDto> {
        val remoteResult = executeWithFallback {
            it.getRecommendations(timeBudgetMinutes, intent, limit)
        }
        return if (remoteResult.isSuccess) {
            val recs = remoteResult.getOrThrow()
            localCache.saveRecommendations(recs)
            remoteResult
        } else {
            val cached = localCache.getRecommendations()
            if (cached != null) {
                val limited = if (limit > 0 && cached.items.size > limit) {
                    cached.copy(items = cached.items.take(limit))
                } else {
                    cached
                }
                Result.success(limited)
            } else {
                remoteResult
            }
        }
    }

    suspend fun submitFeedback(runId: Int, candidateId: String, action: String): Result<Unit> = executeWithFallback {
        it.submitFeedback(FeedbackRequest(runId, candidateId, action))
        Unit
    }

    suspend fun triggerSync(mode: String = "incremental"): Result<SyncReportDto> = executeWithFallback {
        it.triggerSync(SyncTriggerRequest(mode))
    }

    suspend fun hydrateMissingMetadata(): Result<MetadataHydrationDto> = executeWithFallback {
        it.hydrateMissingMetadata()
    }

    open suspend fun getAppVersion(): Result<AppVersionDto> = executeWithFallback {
        it.getAppVersion()
    }
}
