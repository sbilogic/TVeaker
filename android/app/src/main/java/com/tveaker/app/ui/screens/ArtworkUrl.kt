package com.tveaker.app.ui.screens

import com.tveaker.app.TVeakerApplication
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun artworkUrl(remoteUrl: String?): String? {
    if (remoteUrl.isNullOrBlank()) return null
    val baseUrl = TVeakerApplication.instance.repository.currentBaseUrl.value.trimEnd('/')
    val encoded = URLEncoder.encode(remoteUrl, StandardCharsets.UTF_8.toString())
    return "$baseUrl/api/v1/artwork?url=$encoded"
}
