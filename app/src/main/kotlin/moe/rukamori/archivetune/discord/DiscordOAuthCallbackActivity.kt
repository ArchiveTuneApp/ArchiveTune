/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.discord

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class DiscordOAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let(DiscordAuthCoordinator::emit)
    }
}
