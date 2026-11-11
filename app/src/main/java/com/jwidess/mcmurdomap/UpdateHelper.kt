package com.jwidess.mcmurdomap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class Available(val version: String, val releaseUrl: String) : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

object UpdateHelper {

    private val semverRegex = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")

    fun extractSemVer(raw: String): Triple<Int, Int, Int>? {
        val match = semverRegex.find(raw) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: 0
        val minor = match.groupValues[2].toIntOrNull() ?: 0
        val patch = match.groupValues.getOrNull(3)?.ifEmpty { null }?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    fun formatVersion(raw: String): String {
        val parsed = extractSemVer(raw) ?: return raw
        return "v${parsed.first}.${parsed.second}.${parsed.third}"
    }

    fun isVersionNewer(latest: String, current: String): Boolean {
        val latestVer = extractSemVer(latest) ?: return false
        val currentVer = extractSemVer(current) ?: return false

        if (latestVer.first > currentVer.first) return true
        if (latestVer.first < currentVer.first) return false

        if (latestVer.second > currentVer.second) return true
        if (latestVer.second < currentVer.second) return false

        return latestVer.third > currentVer.third
    }

    suspend fun checkForUpdate(currentVersion: String): UpdateState = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/jwidess/mcmurdo-map/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "McMurdoStationMap-Android")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val code = conn.responseCode
            if (code == 404) {
                return@withContext UpdateState.Error("No releases published on GitHub yet.")
            }
            if (code != 200) {
                return@withContext UpdateState.Error("GitHub returned HTTP $code.")
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val rawTagName = json.optString("tag_name", "").trim()
            val releaseUrl = json.optString("html_url", "https://github.com/jwidess/mcmurdo-map/releases/latest")
                .ifBlank { "https://github.com/jwidess/mcmurdo-map/releases/latest" }

            val displayVersion = formatVersion(rawTagName)

            if (rawTagName.isNotEmpty() && isVersionNewer(rawTagName, currentVersion)) {
                UpdateState.Available(
                    version = displayVersion,
                    releaseUrl = releaseUrl
                )
            } else {
                UpdateState.UpToDate(version = displayVersion)
            }
        } catch (e: Exception) {
            UpdateState.Error("Unable to reach GitHub. Check internet connection.")
        }
    }
}
