/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sponsorblock

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.SponsorBlockApiUrlKey
import moe.rukamori.archivetune.constants.SponsorBlockCategoriesKey
import moe.rukamori.archivetune.constants.SponsorBlockEnabledKey
import moe.rukamori.archivetune.utils.dataStore
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

internal class SponsorBlockApiException(
    val statusCode: Int,
) : Exception("SponsorBlock request failed with HTTP $statusCode")

@Singleton
class SponsorBlockRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val client =
            HttpClient(OkHttp) {
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                    connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                }
                engine {
                    config {
                        retryOnConnectionFailure(true)
                    }
                }
            }

        private val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        private val responseCache =
            object : LinkedHashMap<SponsorBlockRequestKey, List<SponsorBlockRawSegment>>(
                MAX_CACHE_ENTRIES + 1,
                0.75f,
                true,
            ) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<SponsorBlockRequestKey, List<SponsorBlockRawSegment>>,
                ): Boolean = size > MAX_CACHE_ENTRIES
            }

        fun observeSettings(): Flow<SponsorBlockSettings> =
            context.dataStore.data
                .map(::settingsFromPreferences)
                .distinctUntilChanged()

        suspend fun setEnabled(enabled: Boolean) {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { preferences ->
                    preferences[SponsorBlockEnabledKey] = enabled
                }
            }
        }

        suspend fun setCategories(categories: Set<SponsorBlockCategory>) {
            val storedCategories = categories.mapTo(mutableSetOf(), SponsorBlockCategory::apiValue)
            withContext(Dispatchers.IO) {
                context.dataStore.edit { preferences ->
                    preferences[SponsorBlockCategoriesKey] = storedCategories
                }
            }
        }

        suspend fun setApiUrl(apiUrl: String) {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { preferences ->
                    preferences[SponsorBlockApiUrlKey] = apiUrl
                }
            }
        }

        internal suspend fun fetchSegments(
            videoId: String,
            categories: List<SponsorBlockCategory>,
            apiUrl: String,
        ): List<SponsorBlockRawSegment> =
            withContext(Dispatchers.IO) {
                val categoryValues = categories.map(SponsorBlockCategory::apiValue)
                val requestKey = SponsorBlockRequestKey(apiUrl, videoId, categoryValues)
                getCached(requestKey)?.let { return@withContext it }

                val hashPrefix = videoId.sha256Prefix()
                val response =
                    client.get("$apiUrl/api/skipSegments/$hashPrefix") {
                        parameter("categories", json.encodeToString(categoryValues))
                        parameter("actionTypes", SKIP_ACTION_TYPES_JSON)
                        parameter("service", YOUTUBE_SERVICE)
                        header(HttpHeaders.UserAgent, "ArchiveTune/${BuildConfig.VERSION_NAME}")
                    }

                val segments =
                    when {
                        response.status == HttpStatusCode.NotFound -> emptyList()
                        response.status.value !in 200..299 ->
                            throw SponsorBlockApiException(response.status.value)
                        else ->
                            json
                                .decodeFromString<List<SponsorBlockVideoResponse>>(response.bodyAsText())
                                .firstOrNull { it.videoId == videoId }
                                ?.segments
                                .orEmpty()
                                .map { segment ->
                                    SponsorBlockRawSegment(
                                        segment = segment.segment,
                                        category = segment.category,
                                        actionType = segment.actionType,
                                    )
                                }
                    }

                putCached(requestKey, segments)
                segments
            }

        private fun settingsFromPreferences(preferences: Preferences): SponsorBlockSettings {
            val storedCategoryValues = preferences[SponsorBlockCategoriesKey]
            val categories =
                if (storedCategoryValues == null) {
                    SponsorBlockSettings.Default.categories
                } else {
                    storedCategoryValues.mapNotNullTo(linkedSetOf(), SponsorBlockCategory::fromApiValue)
                }
            val apiUrl =
                normalizeSponsorBlockApiUrl(preferences[SponsorBlockApiUrlKey].orEmpty())
                    ?: DEFAULT_SPONSOR_BLOCK_API_URL

            return SponsorBlockSettings(
                enabled = preferences[SponsorBlockEnabledKey] ?: SponsorBlockSettings.Default.enabled,
                categories = categories,
                apiUrl = apiUrl,
            )
        }

        @Synchronized
        private fun getCached(
            key: SponsorBlockRequestKey,
        ): List<SponsorBlockRawSegment>? = responseCache[key]

        @Synchronized
        private fun putCached(
            key: SponsorBlockRequestKey,
            segments: List<SponsorBlockRawSegment>,
        ) {
            responseCache[key] = segments
        }

        private fun String.sha256Prefix(): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
            return buildString(HASH_PREFIX_LENGTH) {
                for (index in 0 until HASH_PREFIX_LENGTH / 2) {
                    val value = digest[index].toInt() and 0xFF
                    append(HEX_DIGITS[value ushr 4])
                    append(HEX_DIGITS[value and 0x0F])
                }
            }
        }

        private data class SponsorBlockRequestKey(
            val apiUrl: String,
            val videoId: String,
            val categories: List<String>,
        )

        private companion object {
            const val REQUEST_TIMEOUT_MILLIS = 10_000L
            const val CONNECT_TIMEOUT_MILLIS = 8_000L
            const val MAX_CACHE_ENTRIES = 64
            const val HASH_PREFIX_LENGTH = 4
            const val SKIP_ACTION_TYPES_JSON = "[\"skip\"]"
            const val YOUTUBE_SERVICE = "YouTube"
            const val HEX_DIGITS = "0123456789abcdef"
        }
    }
