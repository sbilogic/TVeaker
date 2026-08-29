package com.tveaker.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tveaker.app.BuildConfig
import com.tveaker.app.TVeakerApplication
import com.tveaker.app.data.model.AppVersionDto
import com.tveaker.app.data.model.HealthDto
import com.tveaker.app.data.repository.TVeakerRepository
import com.tveaker.app.ui.update.UpdateDownloadState
import com.tveaker.app.ui.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val isLoading: Boolean = false,
    val health: HealthDto? = null,
    val baseUrl: String = "http://192.168.1.33:8000/",
    val syncMessage: String? = null,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    // OTA Update state
    val isCheckingUpdate: Boolean = false,
    val serverVersionInfo: AppVersionDto? = null,
    val isNewUpdateAvailable: Boolean = false,
    val updateMessage: String? = null,
    val downloadProgress: Float? = null,
    val readyToInstallApk: File? = null,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val currentVersionName: String = BuildConfig.VERSION_NAME
)

class SettingsViewModel(
    private val repository: TVeakerRepository = TVeakerApplication.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(baseUrl = repository.currentBaseUrl.value)
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        checkHealth()
        checkForUpdates()
    }

    fun setBaseUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        val formatted = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        repository.updateBaseUrl(formatted)
        _uiState.value = _uiState.value.copy(baseUrl = formatted)
        checkHealth()
        checkForUpdates()
    }

    fun checkHealth() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.getHealth()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    health = result.getOrNull()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to connect to TVeaker backend"
                )
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdate = true, updateMessage = null)
            val result = repository.getAppVersion()
            if (result.isSuccess) {
                val versionInfo = result.getOrNull()
                if (versionInfo != null) {
                    val isNewer = versionInfo.versionCode > BuildConfig.VERSION_CODE
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        serverVersionInfo = versionInfo,
                        isNewUpdateAvailable = isNewer,
                        updateMessage = if (isNewer) {
                            "New Version: v${versionInfo.versionName} (Build ${versionInfo.versionCode})"
                        } else {
                            "Server has build v${versionInfo.versionName} (Build ${versionInfo.versionCode})"
                        }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateMessage = "Invalid update response from server."
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    updateMessage = "Could not check updates: ${result.exceptionOrNull()?.message ?: "Network error"}"
                )
            }
        }
    }

    fun startDownloadUpdate(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadProgress = 0.01f, errorMessage = null)
            val updateManager = UpdateManager(context)
            updateManager.downloadApk(repository.getApiService()).collect { state ->
                when (state) {
                    is UpdateDownloadState.Downloading -> {
                        _uiState.value = _uiState.value.copy(downloadProgress = state.progress)
                    }
                    is UpdateDownloadState.ReadyToInstall -> {
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = null,
                            readyToInstallApk = state.apkFile,
                            updateMessage = "Download complete. Tap Install."
                        )
                        installUpdate(context, state.apkFile)
                    }
                    is UpdateDownloadState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = null,
                            errorMessage = state.message
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    fun installUpdate(context: Context, apkFile: File? = null) {
        val fileToInstall = apkFile ?: _uiState.value.readyToInstallApk ?: return
        val updateManager = UpdateManager(context)
        val result = updateManager.installApk(fileToInstall)
        if (result.isFailure) {
            _uiState.value = _uiState.value.copy(
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to trigger installer."
            )
        }
    }

    fun triggerSync(mode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null)
            val result = repository.triggerSync(mode)
            if (result.isSuccess) {
                val rep = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "Sync completed: ${rep?.status} (Fetched: ${rep?.fetched?.values?.sum() ?: 0} items)"
                )
                checkHealth()
            } else {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Sync failed"
                )
            }
        }
    }
}
