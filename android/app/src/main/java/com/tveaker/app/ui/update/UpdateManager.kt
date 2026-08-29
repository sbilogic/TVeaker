package com.tveaker.app.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tveaker.app.data.api.TVeakerApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class UpdateManager(private val context: Context) {

    fun downloadApk(apiService: TVeakerApiService): Flow<UpdateDownloadState> = flow {
        try {
            emit(UpdateDownloadState.Downloading(0f, 0, 0))
            val responseBody = apiService.downloadApk()
            val totalBytes = responseBody.contentLength()

            val apkDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val destinationFile = File(apkDir, "tveaker-update.apk")

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        val progress = if (totalBytes > 0) totalDownloaded.toFloat() / totalBytes else 0.5f
                        emit(UpdateDownloadState.Downloading(progress, totalDownloaded, totalBytes))
                    }
                    outputStream.flush()
                }
            }

            emit(UpdateDownloadState.ReadyToInstall(destinationFile))
        } catch (e: Exception) {
            emit(UpdateDownloadState.Error(e.localizedMessage ?: "Failed to download update."))
        }
    }.flowOn(Dispatchers.IO)

    fun installApk(apkFile: File): Result<Unit> {
        return runCatching {
            if (!apkFile.exists()) {
                throw IllegalStateException("APK file not found at ${apkFile.absolutePath}")
            }

            // Check Unknown Sources on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    throw IllegalStateException("Please enable 'Install unknown apps' for TVeaker in Settings, then tap install again.")
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        }
    }
}
