package com.tveaker.app.data.api

/**
 * Keeps physical devices from silently retrying stale LAN addresses.
 * An online HTTPS URL is configured once in Settings and then persisted.
 */
object GatewayUrl {
    const val UNCONFIGURED_BASE_URL = "https://tveaker.invalid/"
    private const val EMULATOR_BASE_URL = "http://10.0.2.2:8000/"
    private val legacyLanUrls = setOf(
        "http://192.168.1.33:8000/",
        "http://192.168.1.33:8000",
        "http://192.168.0.2:8000/",
        "http://192.168.0.2:8000"
    )

    fun defaultForDevice(isEmulator: Boolean): String =
        if (isEmulator) EMULATOR_BASE_URL else UNCONFIGURED_BASE_URL

    fun isConfigured(baseUrl: String): Boolean =
        baseUrl.trim().isNotEmpty() && !isLegacyLanUrl(baseUrl) && baseUrl != UNCONFIGURED_BASE_URL

    fun isLegacyLanUrl(baseUrl: String): Boolean = baseUrl.trim() in legacyLanUrls

    fun normalize(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            "Use a complete HTTPS URL from the TVeaker phone gateway."
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
