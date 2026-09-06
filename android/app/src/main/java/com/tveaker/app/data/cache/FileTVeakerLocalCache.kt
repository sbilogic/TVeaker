package com.tveaker.app.data.cache

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tveaker.app.data.model.NowWatchingDto
import com.tveaker.app.data.model.RecommendationResponseDto
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UnwatchedEpisodesResponseDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Type

@JsonClass(generateAdapter = true)
data class NowWatchingCacheRecord(
    val hasRecord: Boolean = false,
    val data: NowWatchingDto? = null
)

class FileTVeakerLocalCache(
    private val cacheDir: File,
    moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TVeakerLocalCache {

    private val mutex = Mutex()

    private val showListType: Type = Types.newParameterizedType(List::class.java, ShowEstimateDto::class.java)
    private val showListAdapter: JsonAdapter<List<ShowEstimateDto>> = moshi.adapter(showListType)

    private val showsByStatusType: Type = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        showListType
    )
    private val showsByStatusAdapter: JsonAdapter<Map<String, List<ShowEstimateDto>>> = moshi.adapter(showsByStatusType)

    private val nowWatchingRecordAdapter: JsonAdapter<NowWatchingCacheRecord> =
        moshi.adapter(NowWatchingCacheRecord::class.java)

    private val unwatchedEpisodesAdapter: JsonAdapter<UnwatchedEpisodesResponseDto> =
        moshi.adapter(UnwatchedEpisodesResponseDto::class.java)

    private val recommendationsAdapter: JsonAdapter<RecommendationResponseDto> =
        moshi.adapter(RecommendationResponseDto::class.java)

    private val cachedShowsMap = LinkedHashMap<Int, ShowEstimateDto>()
    private var hasShowsCache: Boolean = false
    private var cachedNowWatching: NowWatchingDto? = null
    private var hasNowWatchingFlag: Boolean = false
    private val cachedUnwatched = mutableMapOf<Int, UnwatchedEpisodesResponseDto>()
    private var cachedRecommendations: RecommendationResponseDto? = null

    private val showsFile get() = File(cacheDir, "shows.json")
    private val showsByStatusFile get() = File(cacheDir, "shows_by_status.json")
    private val nowWatchingFile get() = File(cacheDir, "now_watching.json")
    private val episodesDir get() = File(cacheDir, "episodes")
    private val recommendationsFile get() = File(cacheDir, "recommendations.json")

    private fun writeAtomically(file: File, content: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(content)
        try {
            java.nio.file.Files.move(
                temp.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
            }
        }
    }

    private fun loadShowsFromDiskLocked() {
        val filesToTry = mutableListOf<File>()
        if (showsFile.exists()) filesToTry.add(showsFile)
        val temp = File(showsFile.parentFile, "${showsFile.name}.tmp")
        if (temp.exists() && !filesToTry.contains(temp)) filesToTry.add(temp)
        if (showsByStatusFile.exists() && !filesToTry.contains(showsByStatusFile)) filesToTry.add(showsByStatusFile)

        for (file in filesToTry) {
            try {
                val json = file.readText()
                if (file == showsByStatusFile) {
                    val parsed = showsByStatusAdapter.fromJson(json)
                    if (parsed != null) {
                        cachedShowsMap.clear()
                        parsed.values.flatten().forEach { cachedShowsMap[it.showId] = it }
                        hasShowsCache = true
                        return
                    }
                } else {
                    val parsed = showListAdapter.fromJson(json)
                    if (parsed != null) {
                        cachedShowsMap.clear()
                        parsed.forEach { cachedShowsMap[it.showId] = it }
                        hasShowsCache = true
                        return
                    }
                }
            } catch (_: Exception) {
                // Try next candidate file if this one was malformed or interrupted
            }
        }
    }

    private fun loadNowWatchingFromDiskLocked() {
        val filesToTry = mutableListOf<File>()
        if (nowWatchingFile.exists()) filesToTry.add(nowWatchingFile)
        val temp = File(nowWatchingFile.parentFile, "${nowWatchingFile.name}.tmp")
        if (temp.exists() && !filesToTry.contains(temp)) filesToTry.add(temp)

        for (file in filesToTry) {
            try {
                val json = file.readText()
                val record = nowWatchingRecordAdapter.fromJson(json)
                if (record != null) {
                    hasNowWatchingFlag = record.hasRecord
                    cachedNowWatching = record.data
                    return
                }
            } catch (_: Exception) {
                // Try next candidate file if this one was malformed or interrupted
            }
        }
    }

    private fun loadRecommendationsFromDiskLocked() {
        val filesToTry = mutableListOf<File>()
        if (recommendationsFile.exists()) filesToTry.add(recommendationsFile)
        val temp = File(recommendationsFile.parentFile, "${recommendationsFile.name}.tmp")
        if (temp.exists() && !filesToTry.contains(temp)) filesToTry.add(temp)

        for (file in filesToTry) {
            try {
                val json = file.readText()
                val parsed = recommendationsAdapter.fromJson(json)
                if (parsed != null) {
                    cachedRecommendations = parsed
                    return
                }
            } catch (_: Exception) {
                // Try next candidate file if this one was malformed or interrupted
            }
        }
    }

    override suspend fun getShows(status: String?): List<ShowEstimateDto>? = withContext(ioDispatcher) {
        mutex.withLock {
            if (!hasShowsCache) {
                loadShowsFromDiskLocked()
            }
            if (!hasShowsCache) return@withLock null
            val all = cachedShowsMap.values.toList()
            if (status.isNullOrBlank()) return@withLock all
            all.filter { it.status.equals(status, ignoreCase = true) }
        }
    }

    override suspend fun saveShows(
        shows: List<ShowEstimateDto>,
        status: String?,
        isPartial: Boolean
    ) = withContext(ioDispatcher) {
        mutex.withLock {
            if (!hasShowsCache) {
                loadShowsFromDiskLocked()
            }
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

            try {
                writeAtomically(showsFile, showListAdapter.toJson(cachedShowsMap.values.toList()))
            } catch (_: Exception) {
                // Best-effort disk write
            }
        }
    }

    override suspend fun getNowWatching(): NowWatchingDto? = withContext(ioDispatcher) {
        mutex.withLock {
            if (!hasNowWatchingFlag) {
                loadNowWatchingFromDiskLocked()
            }
            cachedNowWatching
        }
    }

    override suspend fun saveNowWatching(nowWatching: NowWatchingDto?) = withContext(ioDispatcher) {
        mutex.withLock {
            cachedNowWatching = nowWatching
            hasNowWatchingFlag = true
            try {
                val record = NowWatchingCacheRecord(hasRecord = true, data = nowWatching)
                writeAtomically(nowWatchingFile, nowWatchingRecordAdapter.toJson(record))
            } catch (_: Exception) {
                // Best-effort disk write
            }
        }
    }

    override suspend fun hasNowWatching(): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            if (!hasNowWatchingFlag) {
                loadNowWatchingFromDiskLocked()
            }
            hasNowWatchingFlag
        }
    }

    override suspend fun getUnwatchedEpisodes(showId: Int): UnwatchedEpisodesResponseDto? = withContext(ioDispatcher) {
        mutex.withLock {
            cachedUnwatched[showId]?.let { return@withLock it }
            val epFile = File(episodesDir, "$showId.json")
            val filesToTry = mutableListOf<File>()
            if (epFile.exists()) filesToTry.add(epFile)
            val temp = File(episodesDir, "${epFile.name}.tmp")
            if (temp.exists() && !filesToTry.contains(temp)) filesToTry.add(temp)

            for (file in filesToTry) {
                try {
                    val json = file.readText()
                    val parsed = unwatchedEpisodesAdapter.fromJson(json)
                    if (parsed != null) {
                        cachedUnwatched[showId] = parsed
                        return@withLock parsed
                    }
                } catch (_: Exception) {
                    // Try next candidate file if this one was malformed or interrupted
                }
            }
            null
        }
    }

    override suspend fun saveUnwatchedEpisodes(
        showId: Int,
        episodes: UnwatchedEpisodesResponseDto
    ) = withContext(ioDispatcher) {
        mutex.withLock {
            cachedUnwatched[showId] = episodes
            try {
                val epFile = File(episodesDir, "$showId.json")
                writeAtomically(epFile, unwatchedEpisodesAdapter.toJson(episodes))
            } catch (_: Exception) {
                // Best-effort disk write
            }
        }
    }

    override suspend fun getRecommendations(): RecommendationResponseDto? = withContext(ioDispatcher) {
        mutex.withLock {
            if (cachedRecommendations == null) {
                loadRecommendationsFromDiskLocked()
            }
            cachedRecommendations
        }
    }

    override suspend fun saveRecommendations(recommendations: RecommendationResponseDto) = withContext(ioDispatcher) {
        mutex.withLock {
            cachedRecommendations = recommendations
            try {
                writeAtomically(recommendationsFile, recommendationsAdapter.toJson(recommendations))
            } catch (_: Exception) {
                // Best-effort disk write
            }
        }
    }

    override suspend fun updateShow(show: ShowEstimateDto) = withContext(ioDispatcher) {
        mutex.withLock {
            if (!hasShowsCache) {
                loadShowsFromDiskLocked()
            }
            cachedShowsMap[show.showId] = show
            hasShowsCache = true
            try {
                writeAtomically(showsFile, showListAdapter.toJson(cachedShowsMap.values.toList()))
            } catch (_: Exception) {
                // Best-effort disk write
            }
        }
    }

    override suspend fun clear(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            cachedShowsMap.clear()
            hasShowsCache = false
            cachedNowWatching = null
            hasNowWatchingFlag = false
            cachedUnwatched.clear()
            cachedRecommendations = null
            try {
                cacheDir.deleteRecursively()
            } catch (_: Exception) {
                // Best-effort delete
            }
            Unit
        }
    }
}
