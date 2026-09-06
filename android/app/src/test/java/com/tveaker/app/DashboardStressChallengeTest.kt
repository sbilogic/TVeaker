package com.tveaker.app

import com.tveaker.app.data.model.*
import com.tveaker.app.data.repository.TVeakerRepository
import com.tveaker.app.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardStressChallengeTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createShow(id: Int, remaining: Int, watched: Int = 2, total: Int = 10, percent: Float = 20f) =
        ShowEstimateDto(
            showId = id, traktId = id * 10, title = "Show #$id", year = 2024, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = total, airedEpisodes = total,
            unairedEpisodes = 0, watchedEpisodes = watched, remainingEpisodes = remaining,
            unwatchedMinutes = remaining * 45, completionPercent = percent, episodesPerWeek = 1f,
            paceSource = "historical", estimatedFinishDate = "2026-10-01", daysToFinish = 14,
            isCaughtUp = remaining == 0, nextAirDate = null
        )

    private fun resolveFocusShow(state: com.tveaker.app.ui.viewmodel.DashboardUiState): ShowEstimateDto? {
        return state.activeShows.find { it.showId == state.selectedHeroShowId && it.remainingEpisodes > 0 }
            ?: state.activeShows.find { it.showId == state.nowWatching?.showId && it.remainingEpisodes > 0 }
            ?: state.activeShows.filter { it.remainingEpisodes > 0 }.minWithOrNull(
                compareBy<ShowEstimateDto> { it.remainingEpisodes }.thenByDescending { it.completionPercent }
            )
            ?: state.activeShows.firstOrNull()
    }

    @Test
    fun testRapidHeroSwitching_concurrencyStress() = runTest(testDispatcher) {
        val shows = (1..10).map { createShow(it, remaining = 5) }
        val fakeApi = FakeTVeakerApiService().apply { showsList = shows }
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        // Rapidly switch hero show across 10 different IDs in rapid succession
        for (i in 1..10) {
            viewModel.setHeroShow(i)
        }
        advanceUntilIdle()

        // Final hero show must be Show #10
        assertEquals(10, viewModel.uiState.value.selectedHeroShowId)
        val focus = resolveFocusShow(viewModel.uiState.value)
        assertEquals(10, focus?.showId)

        // Queue must exclude Show #10 and take 4 items
        val queue = viewModel.uiState.value.activeShows.filter { it.showId != focus?.showId }.take(4)
        assertEquals(4, queue.size)
        assertFalse(queue.any { it.showId == 10 })
    }

    @Test
    fun testHeroWaterfall_whenAllShowsCompleted_gracefullyFallsBackToFirstShow() = runTest(testDispatcher) {
        val shows = listOf(
            createShow(1, remaining = 0, watched = 10, percent = 100f),
            createShow(2, remaining = 0, watched = 12, percent = 100f)
        )
        val fakeApi = FakeTVeakerApiService().apply { showsList = shows }
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        val focus = resolveFocusShow(viewModel.uiState.value)
        assertNotNull("Focus show should not be null even if all completed", focus)
        assertEquals(1, focus?.showId)
    }

    @Test
    fun testRapidScrobbleAndReloadStress() = runTest(testDispatcher) {
        val shows = listOf(
            createShow(1, remaining = 4),
            createShow(2, remaining = 8)
        )
        val fakeApi = FakeTVeakerApiService().apply { showsList = shows }
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        // Rapid quick scrobbles
        viewModel.quickScrobble(1)
        viewModel.quickScrobble(2)
        viewModel.quickScrobble(1)
        advanceUntilIdle()

        assertEquals(1, fakeApi.lastScrobbledShowId)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testEpisodeDrawerIdempotentDismissal() = runTest(testDispatcher) {
        val fakeApi = FakeTVeakerApiService()
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            initialBaseUrl = "http://127.0.0.1:8000",
            ioDispatcher = testDispatcher,
            initialApiService = fakeApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        // Dismiss when already null
        viewModel.dismissEpisodesSheet()
        assertNull(viewModel.uiState.value.selectedShowUnwatched)

        // Load then dismiss repeatedly
        viewModel.loadUnwatchedEpisodes(1)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.selectedShowUnwatched)

        viewModel.dismissEpisodesSheet()
        assertNull(viewModel.uiState.value.selectedShowUnwatched)
        viewModel.dismissEpisodesSheet()
        assertNull(viewModel.uiState.value.selectedShowUnwatched)
    }

    @Test
    fun testOfflineResilience_withNetworkLossDuringDashboardLoad() = runTest(testDispatcher) {
        val cache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache()
        cache.saveShows(listOf(createShow(5, remaining = 3)))
        cache.saveNowWatching(NowWatchingDto(5, "Cached Show", 501, 1, 1, "Ep 1", 45))

        val failingApi = object : FakeTVeakerApiService() {
            override suspend fun getShows(status: String?, limit: Int?): List<ShowEstimateDto> {
                throw java.io.IOException("Connection reset by peer")
            }
            override suspend fun getRecommendations(timeBudgetMinutes: Int?, intent: String, limit: Int): RecommendationResponseDto {
                throw java.io.IOException("Connection reset by peer")
            }
            override suspend fun getNowWatching(): NowWatchingDto? {
                throw java.io.IOException("Connection reset by peer")
            }
            override suspend fun getAppVersion(): AppVersionDto {
                throw java.io.IOException("Connection reset by peer")
            }
        }

        val repo = TVeakerRepository(
            initialBaseUrl = "http://127.0.0.1:8000",
            localCache = cache,
            ioDispatcher = testDispatcher,
            initialApiService = failingApi
        )
        val viewModel = DashboardViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Repository must detect offline", state.isOffline)
        assertEquals(1, state.activeShows.size)
        assertEquals(5, state.activeShows[0].showId)
        assertNotNull(state.nowWatching)
        assertNull("No blocking error screen when cache is available", state.errorMessage)
    }
}
