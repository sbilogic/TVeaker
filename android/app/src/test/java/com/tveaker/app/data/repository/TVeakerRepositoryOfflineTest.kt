package com.tveaker.app.data.repository

import com.tveaker.app.data.api.TVeakerApiService
import com.tveaker.app.data.cache.FileTVeakerLocalCache
import com.tveaker.app.data.cache.InMemoryTVeakerLocalCache
import com.tveaker.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TVeakerRepositoryOfflineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()

    @org.junit.Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @org.junit.After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun sampleShow(id: Int, title: String, status: String = "watching") = ShowEstimateDto(
        showId = id,
        traktId = id * 10,
        title = title,
        year = 2024,
        status = status,
        statusSource = "trakt",
        includeSpecials = false,
        totalEpisodes = 10,
        airedEpisodes = 10,
        unairedEpisodes = 0,
        watchedEpisodes = 4,
        remainingEpisodes = 6,
        unwatchedMinutes = 300,
        completionPercent = 40f,
        episodesPerWeek = 2f,
        paceSource = "historical",
        estimatedFinishDate = "2026-10-01",
        daysToFinish = 21,
        isCaughtUp = false,
        nextAirDate = null
    )

    private fun sampleNowWatching(showId: Int = 1) = NowWatchingDto(
        showId = showId,
        showTitle = "Show $showId",
        episodeId = showId * 100 + 1,
        seasonNumber = 1,
        episodeNumber = 5,
        episodeTitle = "Episode 5",
        runtimeMinutes = 50
    )

    private fun sampleUnwatched(showId: Int = 1) = UnwatchedEpisodesResponseDto(
        showId = showId,
        title = "Show $showId",
        year = 2024,
        posterUrl = null,
        backdropUrl = null,
        totalEpisodes = 10,
        watchedEpisodes = 4,
        remainingEpisodes = 6,
        unwatchedMinutes = 300,
        unwatchedEpisodes = listOf(
            UnwatchedEpisodeDto(
                id = showId * 100 + 5,
                seasonNumber = 1,
                episodeNumber = 5,
                title = "Episode 5",
                overview = "Overview",
                runtimeMinutes = 50,
                firstAired = "2024-05-01"
            )
        )
    )

    @Test
    fun testOfflineFallback_showsServedFromCache() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        val cachedShows = listOf(
            sampleShow(1, "Cached Show 1", "watching"),
            sampleShow(2, "Cached Show 2", "completed")
        )
        cache.saveShows(cachedShows)

        // Gateway unconfigured / offline repo
        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )

        val result = repo.getShows()
        assertTrue("Should succeed by serving cache", result.isSuccess)
        val shows = result.getOrNull()
        assertNotNull(shows)
        assertEquals(2, shows!!.size)
        assertEquals("Cached Show 1", shows[0].title)
        assertTrue("Repository should be marked offline", repo.isOffline.value)
    }

    @Test
    fun testOfflineFallback_statusAndLimitFiltering() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        val cachedShows = listOf(
            sampleShow(1, "Show 1", "watching"),
            sampleShow(2, "Show 2", "watching"),
            sampleShow(3, "Show 3", "completed")
        )
        cache.saveShows(cachedShows)

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )

        // Test status filtering offline
        val watchingResult = repo.getShows(status = "watching")
        assertTrue(watchingResult.isSuccess)
        assertEquals(2, watchingResult.getOrNull()?.size)

        // Test limit filtering offline
        val limitedResult = repo.getShows(status = "watching", limit = 1)
        assertTrue(limitedResult.isSuccess)
        assertEquals(1, limitedResult.getOrNull()?.size)
    }

    @Test
    fun testOfflineFallback_nowWatchingServedFromCache() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        val nowWatching = sampleNowWatching(42)
        cache.saveNowWatching(nowWatching)

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )

        val result = repo.getNowWatching()
        assertTrue("Now watching should succeed from cache", result.isSuccess)
        val dto = result.getOrNull()
        assertNotNull(dto)
        assertEquals(42, dto?.showId)
        assertTrue(repo.isOffline.value)
    }

    @Test
    fun testOfflineFallback_unwatchedEpisodesServedFromCache() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        cache.saveUnwatchedEpisodes(7, sampleUnwatched(7))

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )

        val result = repo.getUnwatchedEpisodes(7)
        assertTrue("Unwatched episodes should succeed from cache", result.isSuccess)
        assertEquals(7, result.getOrNull()?.showId)

        // Non-cached showId should fail when offline
        val uncachedResult = repo.getUnwatchedEpisodes(99)
        assertTrue("Uncached show should fail when offline", uncachedResult.isFailure)
    }

    @Test
    fun testOffline_whenCacheIsEmpty_returnsFailure() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )

        val showsResult = repo.getShows()
        assertTrue("Empty cache should return failure on network failure", showsResult.isFailure)

        val nowWatchingResult = repo.getNowWatching()
        assertTrue("Empty cache should return failure on network failure", nowWatchingResult.isFailure)
    }

    @Test
    fun testDiskCachePersistenceAcrossRepoRestarts() = runTest(testDispatcher) {
        val dir = tempFolder.root
        val cache1 = FileTVeakerLocalCache(dir, ioDispatcher = testDispatcher)

        cache1.saveShows(listOf(sampleShow(100, "Disk Show", "watching")))
        cache1.saveNowWatching(sampleNowWatching(100))
        cache1.saveUnwatchedEpisodes(100, sampleUnwatched(100))

        // Simulate app kill and restart with a fresh repo instance
        val cache2 = FileTVeakerLocalCache(dir, ioDispatcher = testDispatcher)
        val repo = TVeakerRepository(
            localCache = cache2,
            ioDispatcher = testDispatcher
        )

        // Offline query should succeed from persisted disk cache
        val showsResult = repo.getShows()
        assertTrue(showsResult.isSuccess)
        assertEquals("Disk Show", showsResult.getOrNull()?.firstOrNull()?.title)

        val nowWatchingResult = repo.getNowWatching()
        assertTrue(nowWatchingResult.isSuccess)
        assertEquals(100, nowWatchingResult.getOrNull()?.showId)

        val unwatchedResult = repo.getUnwatchedEpisodes(100)
        assertTrue(unwatchedResult.isSuccess)
        assertEquals(100, unwatchedResult.getOrNull()?.showId)
    }

    @Test
    fun testTransparentRefresh_updatesCacheAndClearsOffline() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        // Start with stale cache
        cache.saveShows(listOf(sampleShow(1, "Old Title", "watching")))

        val freshShows = listOf(
            sampleShow(1, "Refreshed Title", "watching"),
            sampleShow(2, "New Show", "watching")
        )

        val mockApi = object : MockTVeakerApiService() {
            override suspend fun getShows(status: String?, limit: Int?): List<ShowEstimateDto> = freshShows
        }

        val repo = TVeakerRepository(
            initialBaseUrl = "https://online.tveaker.example/",
            localCache = cache,
            ioDispatcher = testDispatcher,
            initialApiService = mockApi
        )

        val result = repo.getShows()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        assertFalse("Repository should not be marked offline on success", repo.isOffline.value)

        // Local cache must now contain the refreshed shows
        val cached = cache.getShows()
        assertNotNull(cached)
        assertEquals(2, cached!!.size)
        assertEquals("Refreshed Title", cached[0].title)
    }

    @Test
    fun testCorruptedCacheDiskFiles_handledGracefully() = runTest(testDispatcher) {
        val dir = tempFolder.root
        // Write corrupted JSON directly into cache files
        java.io.File(dir, "shows.json").writeText("{invalid-json-content: corrupt")
        java.io.File(dir, "now_watching.json").writeText("invalid-content")
        java.io.File(dir, "episodes").mkdirs()
        java.io.File(dir, "episodes/1.json").writeText("corrupted")

        val cache = FileTVeakerLocalCache(dir, ioDispatcher = testDispatcher)
        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )

        // Querying with corrupted cache when offline must not crash or throw unhandled exceptions
        val showsResult = repo.getShows()
        assertTrue("Corrupted cache should be treated as cache miss without crashing", showsResult.isFailure)

        val nowWatchingResult = repo.getNowWatching()
        assertTrue("Corrupted now watching should be treated as cache miss without crashing", nowWatchingResult.isFailure)

        val unwatchedResult = repo.getUnwatchedEpisodes(1)
        assertTrue("Corrupted episode file should be treated as cache miss without crashing", unwatchedResult.isFailure)
    }

    @Test
    fun testOfflineFallback_nowWatchingWithoutShows() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        cache.saveNowWatching(sampleNowWatching(99))

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )

        val showsResult = repo.getShows()
        assertTrue("Shows should fail since none were cached", showsResult.isFailure)

        val nowWatchingResult = repo.getNowWatching()
        assertTrue("Now watching should succeed from cache", nowWatchingResult.isSuccess)
        assertEquals(99, nowWatchingResult.getOrNull()?.showId)
    }

    @Test
    fun testOfflineFallback_recommendationsServedFromCache() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        val recs = RecommendationResponseDto(
            runId = 12,
            items = listOf(
                RecommendationItemDto(
                    candidateId = "rec_1",
                    mediaType = "show",
                    mediaItemId = 55,
                    traktId = 550,
                    title = "Offline Rec",
                    year = 2024,
                    overview = "Offline overview",
                    genres = listOf("Comedy"),
                    runtimeMinutes = 30,
                    score = 0.88f,
                    explanation = "Offline recommended",
                    breakdown = RecommendationRankingBreakdownDto(
                        candidateId = "rec_1",
                        finalScore = 0.88f,
                        contentScore = 0.85f,
                        sourceScore = 0.8f,
                        progressScore = 0.7f,
                        budgetScore = 0.9f,
                        intentScore = 0.8f,
                        matchedGenres = listOf("Comedy")
                    )
                )
            )
        )
        cache.saveRecommendations(recs)

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )

        val result = repo.getRecommendations(limit = 10)
        assertTrue("Recommendations should succeed from cache", result.isSuccess)
        assertEquals(1, result.getOrNull()?.items?.size)
        assertEquals("Offline Rec", result.getOrNull()?.items?.firstOrNull()?.title)
    }

    @Test
    fun testStatusChange_reflectedInOfflineQueries() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        val show = sampleShow(1, "Dynamic Show", "watching")
        cache.saveShows(listOf(show))

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )

        // Offline: initial query
        val watching1 = repo.getShows(status = "watching")
        assertEquals(1, watching1.getOrNull()?.size)
        val paused1 = repo.getShows(status = "paused")
        assertEquals(0, paused1.getOrNull()?.size)

        // Update show to paused
        cache.updateShow(show.copy(status = "paused"))

        val watching2 = repo.getShows(status = "watching")
        assertEquals(0, watching2.getOrNull()?.size)
        val paused2 = repo.getShows(status = "paused")
        assertEquals(1, paused2.getOrNull()?.size)
        assertEquals(1, paused2.getOrNull()?.firstOrNull()?.showId)
    }

    @Test
    fun testNonNetworkHttpError_doesNotSetOffline() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        val response = retrofit2.Response.error<Any>(404, "Not Found".toByteArray().toResponseBody(null))
        val notFoundEx = retrofit2.HttpException(response)

        val mockApi = object : MockTVeakerApiService() {
            override suspend fun getShows(status: String?, limit: Int?): List<ShowEstimateDto> {
                throw notFoundEx
            }
        }

        val repo = TVeakerRepository(
            initialBaseUrl = "https://online.tveaker.example/",
            localCache = cache,
            ioDispatcher = testDispatcher,
            initialApiService = mockApi
        )

        val result = repo.getShows()
        assertTrue(result.isFailure)
        assertFalse("HTTP 404 should not mark repository as offline", repo.isOffline.value)
    }

    @Test
    fun testCloudflareGateway521Error_marksAsOfflineAndServesCache() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        cache.saveShows(listOf(sampleShow(1, "Cloudflare Cached Show")))
        val response = retrofit2.Response.error<Any>(521, "Web Server is Down".toByteArray().toResponseBody(null))
        val cfError = retrofit2.HttpException(response)

        val mockApi = object : MockTVeakerApiService() {
            override suspend fun getShows(status: String?, limit: Int?): List<ShowEstimateDto> {
                throw cfError
            }
        }

        val repo = TVeakerRepository(
            initialBaseUrl = "https://tveaker.example.com/",
            localCache = cache,
            ioDispatcher = testDispatcher,
            initialApiService = mockApi
        )

        val result = repo.getShows()
        assertTrue(result.isSuccess)
        assertEquals("Cloudflare Cached Show", result.getOrNull()?.firstOrNull()?.title)
        assertTrue("Cloudflare 521 error should mark repository as offline", repo.isOffline.value)
    }

    @Test
    fun testCloudflareGateway524Timeout_marksAsOfflineAndServesCache() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        cache.saveShows(listOf(sampleShow(2, "Cloudflare 524 Cached Show")))
        val response = retrofit2.Response.error<Any>(524, "A timeout occurred".toByteArray().toResponseBody(null))
        val cfTimeoutError = retrofit2.HttpException(response)

        val mockApi = object : MockTVeakerApiService() {
            override suspend fun getShows(status: String?, limit: Int?): List<ShowEstimateDto> {
                throw cfTimeoutError
            }
        }

        val repo = TVeakerRepository(
            initialBaseUrl = "https://tveaker.example.com/",
            localCache = cache,
            ioDispatcher = testDispatcher,
            initialApiService = mockApi
        )

        val result = repo.getShows()
        assertTrue(result.isSuccess)
        assertEquals("Cloudflare 524 Cached Show", result.getOrNull()?.firstOrNull()?.title)
        assertTrue("Cloudflare 524 timeout error should mark repository as offline", repo.isOffline.value)
    }

    @Test
    fun testPartialShowsQuery_doesNotPurgeUnrelatedShows() = runTest(testDispatcher) {
        val cache = InMemoryTVeakerLocalCache()
        cache.saveShows(listOf(
            sampleShow(1, "Show 1", "watching"),
            sampleShow(2, "Show 2", "watching"),
            sampleShow(3, "Show 3", "completed")
        ))

        val partial = listOf(sampleShow(1, "Show 1 Refreshed", "watching"))
        val mockApi = object : MockTVeakerApiService() {
            override suspend fun getShows(status: String?, limit: Int?): List<ShowEstimateDto> {
                return partial
            }
        }

        val repo = TVeakerRepository(
            initialBaseUrl = "https://online.tveaker.example/",
            localCache = cache,
            ioDispatcher = testDispatcher,
            initialApiService = mockApi
        )

        // Simulate limited dashboard query (limit = 1)
        val result = repo.getShows(status = "watching", limit = 1)
        assertTrue(result.isSuccess)

        // Cached shows must still have all watching and completed shows
        val allWatching = cache.getShows("watching")
        assertEquals(2, allWatching?.size)
        val completed = cache.getShows("completed")
        assertEquals(1, completed?.size)
    }
}

open class MockTVeakerApiService : TVeakerApiService {
    override suspend fun getHealth(): HealthDto = throw UnsupportedOperationException()
    override suspend fun getShows(status: String?, limit: Int?): List<ShowEstimateDto> = emptyList()
    override suspend fun updateShow(showId: Int, request: UpdateShowRequest): ShowEstimateDto = throw UnsupportedOperationException()
    override suspend fun getUnwatchedEpisodes(showId: Int): UnwatchedEpisodesResponseDto = throw UnsupportedOperationException()
    override suspend fun getNowWatching(): NowWatchingDto? = null
    override suspend fun selectNowWatching(request: NowWatchingSelectionRequest): NowWatchingDto = throw UnsupportedOperationException()
    override suspend fun clearNowWatching() {}
    override suspend fun quickScrobble(showId: Int): Map<String, Any> = emptyMap()
    override suspend fun watchEpisode(showId: Int, episodeId: Int): Map<String, Any> = emptyMap()
    override suspend fun getRecommendations(timeBudgetMinutes: Int?, intent: String, limit: Int): RecommendationResponseDto =
        RecommendationResponseDto(runId = 1, items = emptyList())
    override suspend fun submitFeedback(request: FeedbackRequest): Map<String, Any> = emptyMap()
    override suspend fun triggerSync(request: SyncTriggerRequest): SyncReportDto = throw UnsupportedOperationException()
    override suspend fun hydrateMissingMetadata(): MetadataHydrationDto = throw UnsupportedOperationException()
    override suspend fun getAppVersion(): AppVersionDto = AppVersionDto(1, "1.0", "")
    override suspend fun downloadApk(): okhttp3.ResponseBody = throw UnsupportedOperationException()
}
