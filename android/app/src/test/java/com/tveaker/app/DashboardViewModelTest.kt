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
        val repo = TVeakerRepository()
        val viewModel = DashboardViewModel(repo)

        // Initially loading is scheduled
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
