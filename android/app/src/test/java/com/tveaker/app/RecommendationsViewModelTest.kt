package com.tveaker.app

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
        val repo = TVeakerRepository()
        val viewModel = RecommendationsViewModel(repo)

        viewModel.setBudget(45)
        assertEquals(45, viewModel.uiState.value.selectedBudget)

        viewModel.setIntent("movie")
        assertEquals("movie", viewModel.uiState.value.selectedIntent)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
