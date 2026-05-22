package com.neurogarden.app.data.repository

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WeatherSnapshot(
    val weatherType: String,
    val temperature: Float,
    val humidity: Int,
    val city: String,
    val updatedAt: Long,
    val source: String
) {
    fun eventLabel(): String =
        "$weatherType ${"%.0f".format(temperature)}C 湿度$humidity% $city source=$source"

    fun displayText(): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(updatedAt))
        return "$city / $weatherType / ${"%.0f".format(temperature)}C / 湿度$humidity% / $source / $time"
    }

    companion object {
        fun mock(now: Long = System.currentTimeMillis()) = WeatherSnapshot(
            weatherType = "多云",
            temperature = 24f,
            humidity = 58,
            city = "本地模拟",
            updatedAt = now,
            source = "Mock"
        )
    }
}

class WeatherRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(): WeatherSnapshot {
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        if (updatedAt == 0L) return WeatherSnapshot.mock()
        return WeatherSnapshot(
            weatherType = prefs.getString(KEY_TYPE, null) ?: "多云",
            temperature = prefs.getFloat(KEY_TEMPERATURE, 24f),
            humidity = prefs.getInt(KEY_HUMIDITY, 58),
            city = prefs.getString(KEY_CITY, null) ?: "本地模拟",
            updatedAt = updatedAt,
            source = prefs.getString(KEY_SOURCE, null) ?: "Mock"
        )
    }

    suspend fun refresh(city: String = ""): WeatherSnapshot {
        val real = runCatching { fetchFromWttr(city) }.getOrNull()
        val snapshot = real ?: WeatherSnapshot.mock()
        save(snapshot)
        return snapshot
    }

    private suspend fun fetchFromWttr(city: String): WeatherSnapshot = withContext(Dispatchers.IO) {
        val target = if (city.isBlank()) "" else city.trim()
        val encodedCity = java.net.URLEncoder.encode(target, "UTF-8")
        val url = URL("https://wttr.in/$encodedCity?format=%C|%t|%h|%l")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 1_500
            readTimeout = 1_500
            requestMethod = "GET"
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            val parts = body.split("|").map { it.trim() }
            require(parts.size >= 4) { "Unexpected weather response" }
            WeatherSnapshot(
                weatherType = parts[0].ifBlank { "未知" },
                temperature = parts[1].filter { it.isDigit() || it == '-' || it == '.' }.toFloatOrNull() ?: 24f,
                humidity = parts[2].filter { it.isDigit() }.toIntOrNull() ?: 58,
                city = parts[3].ifBlank { "当前位置" },
                updatedAt = System.currentTimeMillis(),
                source = "Real"
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun save(snapshot: WeatherSnapshot) {
        prefs.edit()
            .putString(KEY_TYPE, snapshot.weatherType)
            .putFloat(KEY_TEMPERATURE, snapshot.temperature)
            .putInt(KEY_HUMIDITY, snapshot.humidity)
            .putString(KEY_CITY, snapshot.city)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAt)
            .putString(KEY_SOURCE, snapshot.source)
            .apply()
    }

    private companion object {
        const val PREFS = "weather_snapshot"
        const val KEY_TYPE = "weather_type"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_HUMIDITY = "humidity"
        const val KEY_CITY = "city"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_SOURCE = "source"
    }
}
