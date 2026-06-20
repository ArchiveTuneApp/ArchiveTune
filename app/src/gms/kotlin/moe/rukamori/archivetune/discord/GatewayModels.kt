/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.discord

import org.json.JSONObject

data class GatewaySessionState(
    val sessionId: String,
    val seq: Int,
    val resumeGatewayUrl: String,
)

data class GatewayReadyEvent(
    val user: GatewayReadyUser,
    val sessionId: String,
    val resumeGatewayUrl: String,
) {
    companion object {
        fun fromJson(obj: JSONObject): GatewayReadyEvent {
            val userObj = obj.getJSONObject("user")
            return GatewayReadyEvent(
                user =
                    GatewayReadyUser(
                        id = userObj.getString("id"),
                        username = userObj.getString("username"),
                        globalName = userObj.optString("global_name", null),
                    ),
                sessionId = obj.getString("session_id"),
                resumeGatewayUrl = obj.getString("resume_gateway_url"),
            )
        }
    }
}

data class GatewayReadyUser(
    val id: String,
    val username: String,
    val globalName: String? = null,
)

data class GatewayCloseInfo(
    val code: Int,
    val reason: String,
    val resumable: Boolean,
    val session: GatewaySessionState?,
)
