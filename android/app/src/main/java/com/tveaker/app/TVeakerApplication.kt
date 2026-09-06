package com.tveaker.app

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import com.tveaker.app.data.api.GatewayUrl
import com.tveaker.app.data.cache.FileTVeakerLocalCache
import com.tveaker.app.data.repository.TVeakerRepository

class TVeakerApplication : Application() {

    lateinit var repository: TVeakerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val prefs = getSharedPreferences("tveaker_prefs", Context.MODE_PRIVATE)
        val defaultUrl = GatewayUrl.defaultForDevice(isEmulator())
        val storedUrl = prefs.getString("base_url", null)
        val savedUrl = if (storedUrl == null || GatewayUrl.isLegacyLanUrl(storedUrl) || storedUrl == GatewayUrl.UNCONFIGURED_BASE_URL) {
            defaultUrl
        } else {
            storedUrl
        }
        if (savedUrl != storedUrl) {
            prefs.edit().putString("base_url", savedUrl).apply()
        }
        val cacheDir = File(filesDir, "tveaker_offline_cache")
        val localCache = FileTVeakerLocalCache(cacheDir)
        repository = TVeakerRepository(initialBaseUrl = savedUrl, prefs = prefs, localCache = localCache)
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.FINGERPRINT.contains("/emu", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.MODEL.startsWith("sdk_gphone", ignoreCase = true) ||
            Build.DEVICE.contains("emu", ignoreCase = true)

    companion object {
        lateinit var instance: TVeakerApplication
            private set
    }
}
