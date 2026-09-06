package com.tveaker.app.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tveaker.app.data.api.TVeakerApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val apkDir = File(baseDir, "updates").apply { mkdirs() }
            val destinationFile = File(apkDir, "tveaker-update.apk")

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(16384)
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

            // Ensure destination file is readable by the PackageInstaller process
            destinationFile.setReadable(true, false)
            validateDownloadedApk(destinationFile)

            emit(UpdateDownloadState.ReadyToInstall(destinationFile))
        } catch (e: Exception) {
            emit(UpdateDownloadState.Error(e.localizedMessage ?: "Failed to download update."))
        }
    }.flowOn(Dispatchers.IO)

    @Suppress("DEPRECATION")
    private fun validateDownloadedApk(apkFile: File) {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        } ?: throw IllegalStateException("Downloaded update is not a valid APK.")

        if (packageInfo.packageName != context.packageName) {
            throw IllegalStateException("Downloaded APK belongs to a different application.")
        }

        val downloadedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        val installedInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installedInfo.longVersionCode
        } else {
            installedInfo.versionCode.toLong()
        }
        if (downloadedVersion <= installedVersion) {
            throw IllegalStateException("Downloaded APK is not newer than the installed app.")
        }
    }

    fun installApk(apkFile: File): Result<Unit> {
        return runCatching {
            if (!apkFile.exists()) {
                throw IllegalStateException("APK file not found at ${apkFile.absolutePath}")
            }

            // Check Unknown Sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    try {
                        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                    } catch (e: ActivityNotFoundException) {
                        val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallbackIntent)
                    }
                    throw IllegalStateException("Please enable 'Install unknown apps' permission for TVeaker in Android Settings, then tap Install again.")
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
