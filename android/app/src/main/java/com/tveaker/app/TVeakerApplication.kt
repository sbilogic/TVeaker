package com.tveaker.app

import android.app.Application
import android.content.Context
import com.tveaker.app.data.repository.TVeakerRepository

class TVeakerApplication : Application() {

    lateinit var repository: TVeakerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val prefs = getSharedPreferences("tveaker_prefs", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("base_url", "http://192.168.1.33:8000/") ?: "http://192.168.1.33:8000/"
        repository = TVeakerRepository(initialBaseUrl = savedUrl, prefs = prefs)
    }

    companion object {
        lateinit var instance: TVeakerApplication
            private set
    }
}
