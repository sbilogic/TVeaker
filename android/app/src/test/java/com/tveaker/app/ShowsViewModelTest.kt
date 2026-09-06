package com.tveaker.app

import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UpdateShowRequest
import com.tveaker.app.data.repository.TVeakerRepository
import com.tveaker.app.ui.viewmodel.ShowsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShowsViewModelTest {

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
    fun toggleSpecialsSendsTheExplicitPreference() = runTest(testDispatcher) {
        var request: UpdateShowRequest? = null
        val repo = object : TVeakerRepository(initialBaseUrl = "https://tveaker.example/") {
            override suspend fun getShows(
                status: String?,
                limit: Int?,
            ): Result<List<ShowEstimateDto>> = Result.success(emptyList())

            override suspend fun updateShow(
                showId: Int,
                update: UpdateShowRequest,
            ): Result<ShowEstimateDto> {
                request = update
                return Result.failure(IllegalStateException("Test response is unused"))
            }
        }
        val viewModel = ShowsViewModel(repo)
        advanceUntilIdle()

        viewModel.toggleSpecials(showId = 42, currentValue = false)
        advanceUntilIdle()

        assertEquals(true, request?.includeSpecials)
    }

    @Test
    fun testOfflineServesCachedShows() = runTest(testDispatcher) {
        val mockShow1 = ShowEstimateDto(
            showId = 1,
            traktId = 10,
            title = "Cached Show 1",
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

        val mockShow2 = ShowEstimateDto(
            showId = 2,
            traktId = 20,
            title = "Cached Show 2",
            year = 2023,
            status = "completed",
            statusSource = "trakt",
            includeSpecials = false,
            totalEpisodes = 8,
            airedEpisodes = 8,
            unairedEpisodes = 0,
            watchedEpisodes = 8,
            remainingEpisodes = 0,
            unwatchedMinutes = 0,
            completionPercent = 100f,
            episodesPerWeek = 0f,
            paceSource = "historical",
            estimatedFinishDate = null,
            daysToFinish = 0,
            isCaughtUp = true,
            nextAirDate = null
        )

        val cache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache()
        cache.saveShows(listOf(mockShow1, mockShow2))

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )
        val viewModel = ShowsViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        org.junit.Assert.assertFalse(state.isLoading)
        org.junit.Assert.assertTrue("ViewModel should indicate offline", state.isOffline)
        assertEquals(2, state.shows.size)
        org.junit.Assert.assertNull("No blocking error message when cache is served", state.errorMessage)
    }

    @Test
    fun testOfflineStatusFilter() = runTest(testDispatcher) {
        val mockShow1 = ShowEstimateDto(
            showId = 1,
            traktId = 10,
            title = "Watching Show",
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
            estimatedFinishDate = null,
            daysToFinish = 21,
            isCaughtUp = false,
            nextAirDate = null
        )

        val mockShow2 = ShowEstimateDto(
            showId = 2,
            traktId = 20,
            title = "Completed Show",
            year = 2023,
            status = "completed",
            statusSource = "trakt",
            includeSpecials = false,
            totalEpisodes = 8,
            airedEpisodes = 8,
            unairedEpisodes = 0,
            watchedEpisodes = 8,
            remainingEpisodes = 0,
            unwatchedMinutes = 0,
            completionPercent = 100f,
            episodesPerWeek = 0f,
            paceSource = "historical",
            estimatedFinishDate = null,
            daysToFinish = 0,
            isCaughtUp = true,
            nextAirDate = null
        )

        val cache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache()
        cache.saveShows(listOf(mockShow1, mockShow2))

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )
        val viewModel = ShowsViewModel(repo)
        advanceUntilIdle()

        viewModel.setStatusFilter("watching")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.shows.size)
        assertEquals("Watching Show", state.shows[0].title)
        org.junit.Assert.assertTrue(state.isOffline)
    }

    @Test
    fun testOfflineServesCachedUnwatchedEpisodes() = runTest(testDispatcher) {
        val mockUnwatched = com.tveaker.app.data.model.UnwatchedEpisodesResponseDto(
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
                com.tveaker.app.data.model.UnwatchedEpisodeDto(
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
        val viewModel = ShowsViewModel(repo)
        advanceUntilIdle()

        viewModel.loadUnwatchedEpisodes(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        org.junit.Assert.assertFalse(state.isEpisodesLoading)
        assertEquals(mockUnwatched, state.selectedShowUnwatched)
        org.junit.Assert.assertTrue(state.isOffline)
    }

    @Test
    fun testOfflineWithNoCacheShowsError() = runTest(testDispatcher) {
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            ioDispatcher = testDispatcher
        )
        val viewModel = ShowsViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        org.junit.Assert.assertFalse(state.isLoading)
        org.junit.Assert.assertTrue(state.isOffline)
        org.junit.Assert.assertTrue(state.shows.isEmpty())
        org.junit.Assert.assertNotNull("Error message should be present when cache is empty", state.errorMessage)
    }
}
