package com.shunsoco.trainlivemap.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiClientFactory {
    /**
     * One shared JSON configuration is used by Retrofit and the on-device
     * snapshot cache, ensuring cached responses decode exactly like live ones.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
    }

    fun create(
        baseUrl: String,
        json: Json = this.json,
    ): TrainLiveMapApi = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(baseUrl))
        .addConverterFactory(
            json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()),
        )
        .build()
        .create(TrainLiveMapApi::class.java)

    private fun normalizeBaseUrl(baseUrl: String): String {
        val normalized = baseUrl.trim()
        require(normalized.isNotEmpty()) { "API base URL must not be blank" }
        return if (normalized.endsWith('/')) normalized else "$normalized/"
    }

    private const val JSON_MEDIA_TYPE = "application/json"
}
