/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.discord

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object DiscordSocialPresenceClient {
    private const val TAG = "DiscordSocialPresenceClient"

    private val mutex = Mutex()
    private var gateway: GatewayClient? = null
    private var scope: CoroutineScope? = null
    private var activeToken: String? = null

    val isStarted: Boolean
        get() = activeToken != null

    suspend fun updatePresence(
        accessToken: String,
        activity: DiscordPresenceActivity,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val token = accessToken.trim()
                if (token.isBlank()) {
                    return@withLock Result.failure(IllegalArgumentException("Discord access token is missing"))
                }

                val connectResult = ensureConnected(token)
                if (connectResult.isFailure) return@withLock connectResult

                val presenceJson = buildPresencePayload(token, activity)
                val sent = gateway?.sendPresenceUpdate(presenceJson) ?: false
                if (!sent) {
                    return@withLock Result.failure(Exception("Failed to send presence update"))
                }
                Result.success(Unit)
            }
        }

    suspend fun clearPresence(accessToken: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var g = gateway
                if (g == null && !accessToken.isNullOrBlank()) {
                    ensureConnected(accessToken)
                    g = gateway
                }
                g ?: return@withLock Result.failure(Exception("Not connected"))
                val empty =
                    JSONObject().apply {
                        put("activities", JSONArray())
                        put("afk", false)
                        put("since", JSONObject.NULL)
                        put("status", "online")
                    }
                g.sendPresenceUpdate(empty)
                Result.success(Unit)
            }
        }

    suspend fun close(): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                gateway?.disconnect()
                gateway = null
                scope?.cancel()
                scope = null
                activeToken = null
                DiscordAssetRegistrar.clearCache()
                Result.success(Unit)
            }
        }

    private fun ensureConnected(token: String): Result<Unit> {
        if (activeToken == token && gateway != null) return Result.success(Unit)

        gateway?.disconnect()
        gateway = null
        scope?.cancel()
        scope = null

        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val newGateway = GatewayClient()

        return runCatching {
            runBlocking {
                newGateway.connect(token)
            }
            scope = newScope
            gateway = newGateway
            activeToken = token
        }
    }

    private suspend fun buildPresencePayload(
        token: String,
        activity: DiscordPresenceActivity,
    ): JSONObject {
        val (resolvedLarge, resolvedSmall) =
            DiscordAssetRegistrar.resolveImages(
                accessToken = token,
                largeImage = activity.assets.largeImage,
                smallImage = activity.assets.smallImage,
            )

        val activityJson = JSONObject()

        activityJson.put("name", activity.name ?: "ArchiveTune")
        activityJson.put("type", activity.type.nativeValue)

        activity.details?.let { activityJson.put("details", it) }
        activity.state?.let { activityJson.put("state", it) }

        activityJson.put("application_id", activity.applicationId)

        val timestamps = JSONObject()
        activity.timestamps.startEpochSeconds?.let {
            timestamps.put("start", it * 1000L)
        }
        activity.timestamps.endEpochSeconds?.let {
            timestamps.put("end", it * 1000L)
        }
        if (timestamps.length() > 0) {
            activityJson.put("timestamps", timestamps)
        }

        val assets = JSONObject()
        resolvedLarge?.let { assets.put("large_image", it) }
        activity.assets.largeText?.let { assets.put("large_text", it) }
        resolvedSmall?.let { assets.put("small_image", it) }
        activity.assets.smallText?.let { assets.put("small_text", it) }
        if (assets.length() > 0) {
            activityJson.put("assets", assets)
        }

        if (activity.buttons.isNotEmpty()) {
            val buttonsArray = JSONArray()
            val metadataUrls = JSONArray()
            for (button in activity.buttons.take(2)) {
                buttonsArray.put(button.label)
                metadataUrls.put(button.url)
            }
            activityJson.put("buttons", buttonsArray)
            val metadata = JSONObject()
            metadata.put("button_urls", metadataUrls)
            activityJson.put("metadata", metadata)
        }

        activityJson.put("platform", "android")

        val activities = JSONArray()
        activities.put(activityJson)

        val payload = JSONObject()
        payload.put("activities", activities)
        payload.put("afk", false)
        payload.put("since", JSONObject.NULL)
        payload.put(
            "status",
            when (activity.onlineStatus) {
                DiscordOnlineStatus.Idle -> "idle"
                DiscordOnlineStatus.Dnd -> "dnd"
                else -> "online"
            },
        )

        return payload
    }
}
