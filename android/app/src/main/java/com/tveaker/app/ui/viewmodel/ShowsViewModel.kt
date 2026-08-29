package com.tveaker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UpdateShowRequest
import com.tveaker.app.data.repository.TVeakerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShowsUiState(
    val isLoading: Boolean = false,
    val shows: List<ShowEstimateDto> = emptyList(),
    val selectedStatus: String? = null,
    val errorMessage: String? = null
)

class ShowsViewModel(
    private val repository: TVeakerRepository = TVeakerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShowsUiState())
    val uiState: StateFlow<ShowsUiState> = _uiState.asStateFlow()

    init {
        loadShows()
    }

    fun setStatusFilter(status: String?) {
        _uiState.value = _uiState.value.copy(selectedStatus = status)
        loadShows()
    }

    fun loadShows() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.getShows(_uiState.value.selectedStatus)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    shows = result.getOrNull() ?: emptyList()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to load shows"
                )
            }
        }
    }

    fun updateShowStatus(showId: Int, status: String) {
        viewModelScope.launch {
            repository.updateShow(showId, UpdateShowRequest(status = status))
            loadShows()
        }
    }

    fun updateShowPace(showId: Int, pace: Float?) {
        viewModelScope.launch {
            repository.updateShow(showId, UpdateShowRequest(manualEpisodesPerWeek = pace))
            loadShows()
        }
    }

    fun toggleSpecials(showId: Int, currentValue: Boolean) {
        viewModelScope.launch {
            repository.updateShow(showId, UpdateShowRequest(includeSpecials = !currentValue))
            loadShows()
        }
    }
}
