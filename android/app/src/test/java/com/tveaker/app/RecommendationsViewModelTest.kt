package com.tveaker.app

import com.tveaker.app.data.model.RecommendationResponseDto
import com.tveaker.app.data.repository.TVeakerRepository
import com.tveaker.app.ui.viewmodel.RecommendationsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationsViewModelTest {

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
    fun testIntentAndBudgetUpdate() = runTest(testDispatcher) {
        val repo = object : TVeakerRepository() {
            override suspend fun getRecommendations(timeBudgetMinutes: Int?, intent: String, limit: Int) =
                Result.success(RecommendationResponseDto(runId = 1, items = emptyList()))
        }
        val viewModel = RecommendationsViewModel(repo)

        viewModel.setBudget(45)
        assertEquals(45, viewModel.uiState.value.selectedBudget)

        viewModel.setIntent("movie")
        assertEquals("movie", viewModel.uiState.value.selectedIntent)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun testOfflineServesCachedRecommendations() = runTest(testDispatcher) {
        val cache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache()
        val mockItem = com.tveaker.app.data.model.RecommendationItemDto(
            candidateId = "rec_99",
            mediaType = "show",
            mediaItemId = 99,
            traktId = 990,
            title = "Cached Recommendation",
            year = 2024,
            overview = "Top rated drama",
            genres = listOf("Drama"),
            runtimeMinutes = 45,
            score = 0.99f,
            explanation = "Top rated",
            breakdown = com.tveaker.app.data.model.RecommendationRankingBreakdownDto(
                candidateId = "rec_99",
                finalScore = 0.99f,
                contentScore = 0.9f,
                sourceScore = 0.8f,
                progressScore = 0.7f,
                budgetScore = 0.9f,
                intentScore = 0.85f,
                matchedGenres = listOf("Drama")
            )
        )
        cache.saveRecommendations(
            RecommendationResponseDto(
                runId = 1,
                items = listOf(mockItem)
            )
        )

        val repo = TVeakerRepository(
            localCache = cache,
            ioDispatcher = testDispatcher
        )
        val viewModel = RecommendationsViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        org.junit.Assert.assertTrue("Recommendations ViewModel should reflect offline state", state.isOffline)
        assertEquals(1, state.items.size)
        assertEquals("Cached Recommendation", state.items[0].title)
        org.junit.Assert.assertNull("No error message when cached recommendations are served", state.errorMessage)
    }

    @Test
    fun testOfflineWithNoCacheShowsError() = runTest(testDispatcher) {
        val repo = TVeakerRepository(
            localCache = com.tveaker.app.data.cache.InMemoryTVeakerLocalCache(),
            ioDispatcher = testDispatcher
        )
        val viewModel = RecommendationsViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        org.junit.Assert.assertTrue(state.isOffline)
        org.junit.Assert.assertTrue(state.items.isEmpty())
        org.junit.Assert.assertNotNull("Error message should be present when cache is empty", state.errorMessage)
    }
}
