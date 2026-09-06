package com.tveaker.app

import com.tveaker.app.data.model.*
import com.tveaker.app.data.repository.TVeakerRepository
import com.tveaker.app.ui.viewmodel.DashboardViewModel
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
}
