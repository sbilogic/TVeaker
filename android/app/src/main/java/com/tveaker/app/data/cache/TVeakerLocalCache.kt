package com.tveaker.app.data.cache

import com.tveaker.app.data.model.NowWatchingDto
import com.tveaker.app.data.model.RecommendationResponseDto
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UnwatchedEpisodesResponseDto

interface TVeakerLocalCache {
    suspend fun getShows(status: String? = null): List<ShowEstimateDto>?
    suspend fun saveShows(shows: List<ShowEstimateDto>, status: String? = null, isPartial: Boolean = (status != null))

    suspend fun getNowWatching(): NowWatchingDto?
    suspend fun saveNowWatching(nowWatching: NowWatchingDto?)
    suspend fun hasNowWatching(): Boolean

    suspend fun getUnwatchedEpisodes(showId: Int): UnwatchedEpisodesResponseDto?
    suspend fun saveUnwatchedEpisodes(showId: Int, episodes: UnwatchedEpisodesResponseDto)

    suspend fun getRecommendations(): RecommendationResponseDto?
    suspend fun saveRecommendations(recommendations: RecommendationResponseDto)

    suspend fun updateShow(show: ShowEstimateDto)
    suspend fun clear()
}
