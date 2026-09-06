package com.tveaker.app.data.cache

import com.tveaker.app.data.model.NowWatchingDto
import com.tveaker.app.data.model.RecommendationResponseDto
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UnwatchedEpisodesResponseDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryTVeakerLocalCache : TVeakerLocalCache {
    private val mutex = Mutex()
    private val cachedShowsMap = LinkedHashMap<Int, ShowEstimateDto>()
    private var hasShowsCache: Boolean = false
    private var cachedNowWatching: NowWatchingDto? = null
    private var hasNowWatchingFlag: Boolean = false
    private val cachedUnwatched = mutableMapOf<Int, UnwatchedEpisodesResponseDto>()
    private var cachedRecommendations: RecommendationResponseDto? = null

    override suspend fun getShows(status: String?): List<ShowEstimateDto>? = mutex.withLock {
        if (!hasShowsCache) return null
        val all = cachedShowsMap.values.toList()
        if (status.isNullOrBlank()) return all
        all.filter { it.status.equals(status, ignoreCase = true) }
    }

    override suspend fun saveShows(
        shows: List<ShowEstimateDto>,
        status: String?,
        isPartial: Boolean
    ) = mutex.withLock {
        if (!isPartial) {
            if (status.isNullOrBlank()) {
                cachedShowsMap.clear()
                shows.forEach { cachedShowsMap[it.showId] = it }
            } else {
                val currentShowsInStatus = cachedShowsMap.values
                    .filter { it.status.equals(status, ignoreCase = true) }
                    .map { it.showId }
                    .toSet()
                val newShowIds = shows.map { it.showId }.toSet()
                val removedShowIds = currentShowsInStatus - newShowIds
                removedShowIds.forEach { cachedShowsMap.remove(it) }

                shows.forEach { cachedShowsMap[it.showId] = it }
            }
        } else {
            shows.forEach { cachedShowsMap[it.showId] = it }
        }
        hasShowsCache = true
    }

    override suspend fun getNowWatching(): NowWatchingDto? = mutex.withLock {
        cachedNowWatching
    }

    override suspend fun saveNowWatching(nowWatching: NowWatchingDto?) = mutex.withLock {
        cachedNowWatching = nowWatching
        hasNowWatchingFlag = true
    }

    override suspend fun hasNowWatching(): Boolean = mutex.withLock {
        hasNowWatchingFlag
    }

    override suspend fun getUnwatchedEpisodes(showId: Int): UnwatchedEpisodesResponseDto? = mutex.withLock {
        cachedUnwatched[showId]
    }

    override suspend fun saveUnwatchedEpisodes(
        showId: Int,
        episodes: UnwatchedEpisodesResponseDto
    ) = mutex.withLock {
        cachedUnwatched[showId] = episodes
    }

    override suspend fun getRecommendations(): RecommendationResponseDto? = mutex.withLock {
        cachedRecommendations
    }

    override suspend fun saveRecommendations(recommendations: RecommendationResponseDto) = mutex.withLock {
        cachedRecommendations = recommendations
    }

    override suspend fun updateShow(show: ShowEstimateDto) = mutex.withLock {
        cachedShowsMap[show.showId] = show
        hasShowsCache = true
    }

    override suspend fun clear() = mutex.withLock {
        cachedShowsMap.clear()
        hasShowsCache = false
        cachedNowWatching = null
        hasNowWatchingFlag = false
        cachedUnwatched.clear()
        cachedRecommendations = null
    }
}
