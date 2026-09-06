package com.tveaker.app

import com.tveaker.app.data.model.*
import com.tveaker.app.data.repository.TVeakerRepository
import com.tveaker.app.ui.viewmodel.DashboardViewModel
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateAndLoading() = runTest(testDispatcher) {
        val repo = object : TVeakerRepository() {
            override suspend fun getShows(status: String?, limit: Int?) = Result.success(emptyList<ShowEstimateDto>())
            override suspend fun getRecommendations(timeBudgetMinutes: Int?, intent: String, limit: Int) =
                Result.success(RecommendationResponseDto(runId = 1, items = emptyList()))
            override suspend fun getAppVersion() = Result.success(AppVersionDto(1, "test", ""))
        }
        val viewModel = DashboardViewModel(repo)

        // Initially loading is scheduled
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun testOfflineServesCachedShowsAndNowWatching() = runTest(testDispatcher) {
        val mockShow = ShowEstimateDto(
            showId = 1,
            traktId = 10,
            title = "Offline Cached Show",
            year = 2024,
            status = "watching",
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

        val mockNowWatching = NowWatchingDto(
            showId = 1,
            showTitle = "Offline Cached Show",
            episodeId = 105,
            seasonNumber = 1,
            episodeNumber = 5,
            episodeTitle = "Episode 5",
            runtimeMinutes = 50
        )

        val cache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache()
        cache.saveShows(listOf(mockShow))
        cache.saveNowWatching(mockNowWatching)

        // TVeakerRepository with unconfigured gateway is offline and will fall back to cache
        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue("ViewModel should indicate offline", state.isOffline)
        assertEquals(1, state.activeShows.size)
        assertEquals("Offline Cached Show", state.activeShows[0].title)
        assertEquals(mockNowWatching, state.nowWatching)
        org.junit.Assert.assertNull("No blocking error message when cache is served", state.errorMessage)
    }

    @Test
    fun testOfflineServesCachedUnwatchedEpisodes() = runTest(testDispatcher) {
        val mockUnwatched = UnwatchedEpisodesResponseDto(
            showId = 1,
            title = "Cached Show",
            year = 2024,
            posterUrl = null,
            backdropUrl = null,
            totalEpisodes = 10,
            watchedEpisodes = 4,
            remainingEpisodes = 1,
            unwatchedMinutes = 50,
            unwatchedEpisodes = listOf(
                UnwatchedEpisodeDto(
                    id = 105,
                    seasonNumber = 1,
                    episodeNumber = 5,
                    title = "Episode 5",
                    overview = "Details",
                    runtimeMinutes = 50,
                    firstAired = "2024-05-01"
                )
            )
        )

        val cache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache()
        cache.saveShows(listOf(
            ShowEstimateDto(
                showId = 1,
                traktId = 10,
                title = "Cached Show",
                year = 2024,
                status = "watching",
                statusSource = "trakt",
                includeSpecials = false,
                totalEpisodes = 10,
                airedEpisodes = 10,
                unairedEpisodes = 0,
                watchedEpisodes = 4,
                remainingEpisodes = 1,
                unwatchedMinutes = 50,
                completionPercent = 80f,
                episodesPerWeek = 1f,
                paceSource = "historical",
                estimatedFinishDate = null,
                daysToFinish = null,
                isCaughtUp = false,
                nextAirDate = null
            )
        ))
        cache.saveUnwatchedEpisodes(1, mockUnwatched)

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        viewModel.loadUnwatchedEpisodes(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEpisodesLoading)
        assertEquals(mockUnwatched, state.selectedShowUnwatched)
        assertTrue(state.isOffline)
    }

    @Test
    fun testOfflineWithNoCacheShowsError() = runTest(testDispatcher) {
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            ioDispatcher = testDispatcher
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isOffline)
        assertTrue(state.activeShows.isEmpty())
        org.junit.Assert.assertNotNull("Error message should be present when cache is empty", state.errorMessage)
    }

    @Test
    fun testOfflineWithNowWatchingAndNoShows_doesNotSetBlockingError() = runTest(testDispatcher) {
        val cache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache()
        val mockNowWatching = NowWatchingDto(
            showId = 42,
            showTitle = "Solo Now Watching",
            episodeId = 4201,
            seasonNumber = 1,
            episodeNumber = 1,
            episodeTitle = "Pilot",
            runtimeMinutes = 45
        )
        cache.saveNowWatching(mockNowWatching)

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue("Should be offline", state.isOffline)
        assertTrue("Active shows should be empty", state.activeShows.isEmpty())
        assertEquals("Solo Now Watching", state.nowWatching?.showTitle)
        org.junit.Assert.assertNull("Blocking error should be null when now-watching is cached", state.errorMessage)
    }

    @Test
    fun testOfflineWithNoShowsAndNullNowWatching_showsConnectionError() = runTest(testDispatcher) {
        val cache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache()
        // Simulate that user previously connected when nowWatching was null
        cache.saveNowWatching(null)

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue("Should be offline", state.isOffline)
        assertTrue("Active shows should be empty", state.activeShows.isEmpty())
        org.junit.Assert.assertNull("nowWatching should be null", state.nowWatching)
        org.junit.Assert.assertNotNull("Error message MUST be present when no shows are cached, even if nowWatching was cached as null", state.errorMessage)
    }

    @Test
    fun testSetHeroShow_updatesUiStateAndHeroSelection() = runTest(testDispatcher) {
        val showA = ShowEstimateDto(
            showId = 1, traktId = 10, title = "Show Alpha", year = 2023, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 10, airedEpisodes = 10,
            unairedEpisodes = 0, watchedEpisodes = 5, remainingEpisodes = 5, unwatchedMinutes = 250,
            completionPercent = 50f, episodesPerWeek = 2f, paceSource = "historical",
            estimatedFinishDate = "2026-10-01", daysToFinish = 14, isCaughtUp = false, nextAirDate = null
        )
        val showB = ShowEstimateDto(
            showId = 2, traktId = 20, title = "Show Beta", year = 2024, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 12, airedEpisodes = 12,
            unairedEpisodes = 0, watchedEpisodes = 2, remainingEpisodes = 10, unwatchedMinutes = 500,
            completionPercent = 16.6f, episodesPerWeek = 1f, paceSource = "historical",
            estimatedFinishDate = "2026-11-01", daysToFinish = 45, isCaughtUp = false, nextAirDate = null
        )

        val fakeApi = FakeTVeakerApiService().apply {
            showsList = listOf(showA, showB)
        }

        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        // Initially no explicit hero
        org.junit.Assert.assertNull(viewModel.uiState.value.selectedHeroShowId)

        // Select Show Beta (id = 2)
        viewModel.setHeroShow(2)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.selectedHeroShowId)
        assertEquals(2, viewModel.uiState.value.nowWatching?.showId)
    }

    @Test
    fun testHeroShowResolution_waterfallLogic() = runTest(testDispatcher) {
        val showA = ShowEstimateDto(
            showId = 1, traktId = 10, title = "Show Alpha", year = 2023, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 10, airedEpisodes = 10,
            unairedEpisodes = 0, watchedEpisodes = 8, remainingEpisodes = 2, unwatchedMinutes = 100,
            completionPercent = 80f, episodesPerWeek = 2f, paceSource = "historical",
            estimatedFinishDate = "2026-10-01", daysToFinish = 7, isCaughtUp = false, nextAirDate = null
        )
        val showB = ShowEstimateDto(
            showId = 2, traktId = 20, title = "Show Beta", year = 2024, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 10, airedEpisodes = 10,
            unairedEpisodes = 0, watchedEpisodes = 1, remainingEpisodes = 9, unwatchedMinutes = 450,
            completionPercent = 10f, episodesPerWeek = 1f, paceSource = "historical",
            estimatedFinishDate = "2026-11-01", daysToFinish = 45, isCaughtUp = false, nextAirDate = null
        )

        fun resolveFocusShow(state: com.tveaker.app.ui.viewmodel.DashboardUiState): ShowEstimateDto? {
            return state.activeShows.find { it.showId == state.selectedHeroShowId && it.remainingEpisodes > 0 }
                ?: state.activeShows.find { it.showId == state.nowWatching?.showId && it.remainingEpisodes > 0 }
                ?: state.activeShows.filter { it.remainingEpisodes > 0 }.minWithOrNull(
                    compareBy<ShowEstimateDto> { it.remainingEpisodes }.thenByDescending { it.completionPercent }
                )
                ?: state.activeShows.firstOrNull()
        }

        val fakeApi = FakeTVeakerApiService().apply {
            showsList = listOf(showA, showB)
        }
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        // 1. Without explicit selection or nowWatching, falls back to Show Alpha (fewest remaining episodes: 2 vs 9)
        var focus = resolveFocusShow(viewModel.uiState.value)
        assertEquals("Show Alpha", focus?.title)

        // 2. Explicit selection of Show Beta overrides fallback
        viewModel.setHeroShow(2)
        advanceUntilIdle()
        focus = resolveFocusShow(viewModel.uiState.value)
        assertEquals("Show Beta", focus?.title)
    }

    @Test
    fun testQuickScrobbleHero_callsRepositoryAndReloads() = runTest(testDispatcher) {
        val showA = ShowEstimateDto(
            showId = 5, traktId = 50, title = "Hero Show", year = 2024, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 10, airedEpisodes = 10,
            unairedEpisodes = 0, watchedEpisodes = 4, remainingEpisodes = 6, unwatchedMinutes = 300,
            completionPercent = 40f, episodesPerWeek = 2f, paceSource = "historical",
            estimatedFinishDate = "2026-10-01", daysToFinish = 21, isCaughtUp = false, nextAirDate = null
        )
        val fakeApi = FakeTVeakerApiService().apply {
            showsList = listOf(showA)
        }
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        viewModel.quickScrobble(5)
        advanceUntilIdle()

        assertEquals(5, fakeApi.lastScrobbledShowId)
    }

    @Test
    fun testEpisodeDrawer_loadSelectAndDismiss() = runTest(testDispatcher) {
        val unwatched = UnwatchedEpisodesResponseDto(
            showId = 42, title = "Episode Show", year = 2024, posterUrl = null, backdropUrl = null,
            totalEpisodes = 5, watchedEpisodes = 2, remainingEpisodes = 3, unwatchedMinutes = 150,
            unwatchedEpisodes = listOf(
                UnwatchedEpisodeDto(101, 1, 3, "Episode 3", "Details", 50, "2024-06-01"),
                UnwatchedEpisodeDto(102, 1, 4, "Episode 4", "Details", 50, "2024-06-08")
            )
        )
        val fakeApi = FakeTVeakerApiService().apply {
            unwatchedResponse = unwatched
        }
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        // 1. Load unwatched episodes
        viewModel.loadUnwatchedEpisodes(42)
        advanceUntilIdle()
        assertEquals(unwatched, viewModel.uiState.value.selectedShowUnwatched)

        // 2. Select now watching episode (101)
        viewModel.selectNowWatching(101)
        advanceUntilIdle()
        // Sheet dismissed upon selection
        org.junit.Assert.assertNull(viewModel.uiState.value.selectedShowUnwatched)
        assertEquals(101, viewModel.uiState.value.nowWatching?.episodeId)

        // 3. Load again and test dismissEpisodesSheet
        viewModel.loadUnwatchedEpisodes(42)
        advanceUntilIdle()
        org.junit.Assert.assertNotNull(viewModel.uiState.value.selectedShowUnwatched)
        viewModel.dismissEpisodesSheet()
        org.junit.Assert.assertNull(viewModel.uiState.value.selectedShowUnwatched)
    }

    @Test
    fun testEpisodeDrawer_markEpisodeWatched() = runTest(testDispatcher) {
        val fakeApi = FakeTVeakerApiService()
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        viewModel.markEpisodeWatched(showId = 7, episodeId = 701)
        advanceUntilIdle()

        assertEquals(7 to 701, fakeApi.lastWatchedEpisodeId)
    }

    @Test
    fun testSubmitFeedback_removesRecommendationFromState() = runTest(testDispatcher) {
        val breakdown = RecommendationRankingBreakdownDto("cand_1", 0.9f, 0.8f, 0.9f, 0.5f, 1f, 1f, listOf("Drama"))
        val recItem = RecommendationItemDto(
            candidateId = "cand_1", mediaType = "show", mediaItemId = 10, traktId = 100,
            title = "Recommended Series", year = 2024, overview = "Great show",
            genres = listOf("Drama", "Sci-Fi"), runtimeMinutes = 45, score = 0.92f,
            explanation = "Because you like sci-fi", breakdown = breakdown
        )
        val fakeApi = FakeTVeakerApiService().apply {
            recommendationsResponse = RecommendationResponseDto(runId = 99, items = listOf(recItem))
        }
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recommendations.size)
        assertEquals("cand_1", viewModel.uiState.value.recommendations[0].candidateId)

        // Submit feedback "accepted"
        viewModel.submitFeedback(runId = 99, candidateId = "cand_1", action = "accepted")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recommendations.isEmpty())
        assertEquals("accepted", fakeApi.lastFeedback?.action)
        assertEquals("cand_1", fakeApi.lastFeedback?.candidateId)
    }

    @Test
    fun testQueueDerivation_filtersOutHeroShow() = runTest(testDispatcher) {
        val shows = (1..6).map { id ->
            ShowEstimateDto(
                showId = id, traktId = id * 10, title = "Show #$id", year = 2024, status = "watching",
                statusSource = "trakt", includeSpecials = false, totalEpisodes = 10, airedEpisodes = 10,
                unairedEpisodes = 0, watchedEpisodes = 2, remainingEpisodes = 8, unwatchedMinutes = 400,
                completionPercent = 20f, episodesPerWeek = 1f, paceSource = "historical",
                estimatedFinishDate = null, daysToFinish = null, isCaughtUp = false, nextAirDate = null
            )
        }
        val fakeApi = FakeTVeakerApiService().apply {
            showsList = shows
        }
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        // Set Show #2 as Hero
        viewModel.setHeroShow(2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val focusShow = state.activeShows.find { it.showId == state.selectedHeroShowId }
        assertEquals(2, focusShow?.showId)

        val queue = state.activeShows
            .filter { it.showId != focusShow?.showId }
            .take(4)

        // Queue must NOT contain Show #2 and must contain 4 items (shows 1, 3, 4, 5)
        assertEquals(4, queue.size)
        assertFalse(queue.any { it.showId == 2 })
        assertEquals(listOf(1, 3, 4, 5), queue.map { it.showId })
    }
}

open class FakeTVeakerApiService : com.tveaker.app.data.api.TVeakerApiService {
    var showsList: List<ShowEstimateDto> = emptyList()
    var unwatchedResponse: UnwatchedEpisodesResponseDto? = null
    var nowWatchingItem: NowWatchingDto? = null
    var recommendationsResponse: RecommendationResponseDto = RecommendationResponseDto(runId = 1, items = emptyList())
    var appVersionDto: AppVersionDto = AppVersionDto(1, "1.0.0", "")
    var lastScrobbledShowId: Int? = null
    var lastWatchedEpisodeId: Pair<Int, Int>? = null
    var lastFeedback: FeedbackRequest? = null

    override suspend fun getHealth(): HealthDto = HealthDto("ok", "2026-09-06", true, true, "user", null)
    override suspend fun getShows(status: String?, limit: Int?): List<ShowEstimateDto> = showsList
    override suspend fun updateShow(showId: Int, request: UpdateShowRequest): ShowEstimateDto =
        showsList.first { it.showId == showId }
    override suspend fun getUnwatchedEpisodes(showId: Int): UnwatchedEpisodesResponseDto =
        unwatchedResponse ?: UnwatchedEpisodesResponseDto(showId, "Show", 2024, null, null, emptyList(), 10, 10, 0, 5, 5, 200, null, emptyList())
    override suspend fun getNowWatching(): NowWatchingDto? = nowWatchingItem
    override suspend fun selectNowWatching(request: NowWatchingSelectionRequest): NowWatchingDto {
        val show = showsList.find { it.showId == request.showId }
        val now = NowWatchingDto(
            showId = request.showId ?: show?.showId ?: 1,
            showTitle = show?.title ?: "Selected Show",
            episodeId = request.episodeId ?: 101,
            seasonNumber = 1,
            episodeNumber = 1,
            episodeTitle = "Episode 1",
            runtimeMinutes = 45
        )
        nowWatchingItem = now
        return now
    }
    override suspend fun clearNowWatching() { nowWatchingItem = null }
    override suspend fun quickScrobble(showId: Int): Map<String, Any> {
        lastScrobbledShowId = showId
        return mapOf("success" to true)
    }
    override suspend fun watchEpisode(showId: Int, episodeId: Int): Map<String, Any> {
        lastWatchedEpisodeId = showId to episodeId
        return mapOf("success" to true)
    }
    override suspend fun getRecommendations(timeBudgetMinutes: Int?, intent: String, limit: Int): RecommendationResponseDto =
        recommendationsResponse
    override suspend fun submitFeedback(request: FeedbackRequest): Map<String, Any> {
        lastFeedback = request
        return mapOf("success" to true)
    }
    override suspend fun triggerSync(request: SyncTriggerRequest): SyncReportDto =
        SyncReportDto("ok")
    override suspend fun hydrateMissingMetadata(): MetadataHydrationDto =
        MetadataHydrationDto("ok", 0)
    override suspend fun getAppVersion(): AppVersionDto = appVersionDto
    override suspend fun downloadApk(): okhttp3.ResponseBody = "".toResponseBody(null)
}
