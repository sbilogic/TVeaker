package com.tveaker.app.data.repository

import com.tveaker.app.data.api.TVeakerApiService
import com.tveaker.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TVeakerRepository(
    private var apiService: TVeakerApiService = TVeakerApiService.create()
) {
    fun updateBaseUrl(newBaseUrl: String) {
        val formatted = if (newBaseUrl.endsWith("/")) newBaseUrl else "$newBaseUrl/"
        apiService = TVeakerApiService.create(formatted)
    }

    suspend fun getHealth(): Result<HealthDto> = withContext(Dispatchers.IO) {
        runCatching { apiService.getHealth() }
    }

    suspend fun getShows(status: String? = null): Result<List<ShowEstimateDto>> = withContext(Dispatchers.IO) {
        runCatching { apiService.getShows(status) }
    }

    suspend fun updateShow(showId: Int, request: UpdateShowRequest): Result<ShowEstimateDto> = withContext(Dispatchers.IO) {
        runCatching { apiService.updateShow(showId, request) }
    }

    suspend fun getRecommendations(
        timeBudgetMinutes: Int? = null,
        intent: String = "auto",
        limit: Int = 10
    ): Result<RecommendationResponseDto> = withContext(Dispatchers.IO) {
        runCatching { apiService.getRecommendations(timeBudgetMinutes, intent, limit) }
    }

    suspend fun submitFeedback(runId: Int, candidateId: String, action: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.submitFeedback(FeedbackRequest(runId, candidateId, action))
            Unit
        }
    }

    suspend fun triggerSync(mode: String = "incremental"): Result<SyncReportDto> = withContext(Dispatchers.IO) {
        runCatching { apiService.triggerSync(SyncTriggerRequest(mode)) }
    }
}
