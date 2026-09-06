package com.tveaker.app.ui.update

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tveaker.app.BuildConfig
import com.tveaker.app.TVeakerApplication
import java.io.File

/** Checks, downloads, validates, and opens every newer APK without a manual app action. */
@Composable
fun AutoUpdateEffect() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val repository = TVeakerApplication.instance.repository
    val updateManager = remember(context) { UpdateManager(context.applicationContext) }
    var pendingApk by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        val release = repository.getAppVersion().getOrNull() ?: return@LaunchedEffect
        if (release.versionCode <= BuildConfig.VERSION_CODE) return@LaunchedEffect

        Toast.makeText(
            context,
            "Downloading TVeaker ${release.versionName} update…",
            Toast.LENGTH_SHORT
        ).show()

        updateManager.downloadApk(repository.getApiService()).collect { state ->
            when (state) {
                is UpdateDownloadState.ReadyToInstall -> {
                    pendingApk = state.apkFile
                    val result = updateManager.installApk(state.apkFile)
                    if (result.isSuccess) pendingApk = null
                }
                is UpdateDownloadState.Error -> Toast.makeText(
                    context,
                    "Update failed: ${state.message}",
                    Toast.LENGTH_LONG
                ).show()
                else -> Unit
            }
        }
    }

    DisposableEffect(activity, pendingApk) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                pendingApk?.let { apk ->
                    if (updateManager.installApk(apk).isSuccess) pendingApk = null
                }
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
}
