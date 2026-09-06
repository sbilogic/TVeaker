package com.tveaker.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tveaker.app.BuildConfig
import com.tveaker.app.TVeakerApplication
import com.tveaker.app.data.model.AppVersionDto
import com.tveaker.app.data.model.HealthDto
import com.tveaker.app.data.api.GatewayUrl
import com.tveaker.app.data.repository.TVeakerRepository
import com.tveaker.app.ui.update.UpdateDownloadState
import com.tveaker.app.ui.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val isLoading: Boolean = false,
    val health: HealthDto? = null,
    val baseUrl: String = GatewayUrl.UNCONFIGURED_BASE_URL,
    val syncMessage: String? = null,
    val isSyncing: Boolean = false,
    val isHydratingMetadata: Boolean = false,
    val metadataMessage: String? = null,
    val errorMessage: String? = null,
    // OTA Update state
    val isCheckingUpdate: Boolean = false,
    val lastCheckedTime: String? = null,
    val serverVersionInfo: AppVersionDto? = null,
    val isNewUpdateAvailable: Boolean = false,
    val updateMessage: String? = null,
    val downloadProgress: Float? = null,
    val downloadBytesProgress: String? = null,
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
        val formatted = try {
            GatewayUrl.normalize(newUrl)
        } catch (error: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(errorMessage = error.message)
            return
        }
        repository.updateBaseUrl(formatted)
        _uiState.value = _uiState.value.copy(baseUrl = formatted, errorMessage = null)
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
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val result = repository.getAppVersion()
            if (result.isSuccess) {
                val versionInfo = result.getOrNull()
                if (versionInfo != null) {
                    val isNewer = versionInfo.versionCode > BuildConfig.VERSION_CODE
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        lastCheckedTime = timeStr,
                        serverVersionInfo = versionInfo,
                        isNewUpdateAvailable = isNewer,
                        updateMessage = if (isNewer) {
                            "⚡ New version v${versionInfo.versionName} (b${versionInfo.versionCode}) available!"
                        } else {
                            "✓ TVeaker is up to date (b${BuildConfig.VERSION_CODE})"
                        }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        lastCheckedTime = timeStr,
                        updateMessage = "Invalid update response from server."
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    lastCheckedTime = timeStr,
                    updateMessage = "Could not reach update server at ${_uiState.value.baseUrl}"
                )
            }
        }
    }

    fun startDownloadUpdate(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadProgress = 0.01f, downloadBytesProgress = "Starting...", errorMessage = null)
            val updateManager = UpdateManager(context)
            updateManager.downloadApk(repository.getApiService()).collect { state ->
                when (state) {
                    is UpdateDownloadState.Downloading -> {
                        val downloadedMb = String.format(Locale.US, "%.1f", state.bytesDownloaded / 1048576f)
                        val totalMb = if (state.totalBytes > 0) String.format(Locale.US, "%.1f", state.totalBytes / 1048576f) else "?"
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = state.progress,
                            downloadBytesProgress = "$downloadedMb MB / $totalMb MB"
                        )
                    }
                    is UpdateDownloadState.ReadyToInstall -> {
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = null,
                            downloadBytesProgress = null,
                            readyToInstallApk = state.apkFile,
                            updateMessage = "Download complete. Opening system installer..."
                        )
                        installUpdate(context, state.apkFile)
                    }
                    is UpdateDownloadState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = null,
                            downloadBytesProgress = null,
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
            } else {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Sync failed"
                )
            }
        }
    }

    fun hydrateMissingMetadata() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isHydratingMetadata = true,
                metadataMessage = null,
                errorMessage = null
            )
            val result = repository.hydrateMissingMetadata()
            _uiState.value = if (result.isSuccess) {
                _uiState.value.copy(
                    isHydratingMetadata = false,
                    metadataMessage = "Metadata refresh queued for ${result.getOrNull()?.limit ?: 75} items."
                )
            } else {
                _uiState.value.copy(
                    isHydratingMetadata = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Metadata refresh failed"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null)
    }
}
