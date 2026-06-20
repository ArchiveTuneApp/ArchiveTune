/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.putLegacyPoToken
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.resetAuthWebViewSession

const val LOGIN_ROUTE = "login"
const val LOGIN_URL_ARGUMENT = "url"

fun buildLoginRoute(startUrl: String? = null): String {
    val resolvedUrl = startUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return LOGIN_ROUTE
    return "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT=${Uri.encode(resolvedUrl)}"
}

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

private val YOUTUBE_COOKIE_URLS =
    listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
    )

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
    startUrl: String? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")

    var hasNavigated by remember { mutableStateOf(false) }

    var webView: WebView? = null

    AndroidView(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                val cookieManager = CookieManager.getInstance()
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            val isYouTubePage = url?.contains("youtube.com", ignoreCase = true) == true
                            if (isYouTubePage) {
                                loadUrl(
                                    "javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var v=c.get('VISITOR_DATA');if(v){Android.onRetrieveVisitorData(v);return}}var y=window.yt&&window.yt.config_;if(y&&y.VISITOR_DATA){Android.onRetrieveVisitorData(y.VISITOR_DATA);return}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"VISITOR_DATA\":\"([^\"]+)\"/);if(m){Android.onRetrieveVisitorData(m[1]);return}}}catch(e){}})())",
                                )
                                loadUrl(
                                    "javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var d=c.get('DATASYNC_ID');if(d){Android.onRetrieveDataSyncId(d);return}}var y=window.yt&&window.yt.config_;if(y&&y.DATASYNC_ID){Android.onRetrieveDataSyncId(y.DATASYNC_ID);return}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"DATASYNC_ID\":\"([^\"]+)\"/);if(m){Android.onRetrieveDataSyncId(m[1]);return}}}catch(e){}})())",
                                )
                                loadUrl(
                                    "javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var t=c.get('PO_TOKEN');if(t){Android.onRetrievePoToken(t);return}}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"PO_TOKEN\":\"([^\"]+)\"/);if(m){Android.onRetrievePoToken(m[1]);return}}}catch(e){}})())",
                                )
                            }

                            val mergedCookie = mergeYouTubeCookies(cookieManager, url)
                            if (!mergedCookie.isNullOrBlank()) {
                                innerTubeCookie = mergedCookie
                                coroutineScope.launch {
                                    YouTube
                                        .accountInfo()
                                        .onSuccess {
                                            accountName = it.name
                                            accountEmail = it.email.orEmpty()
                                            accountChannelHandle = it.channelHandle.orEmpty()

                                            if (!hasNavigated) {
                                                hasNavigated = true
                                                Toast.makeText(context, R.string.login_success, Toast.LENGTH_SHORT).show()
                                                navController.navigateUp()
                                            }
                                        }.onFailure {
                                            reportException(it)
                                        }
                                }
                            }
                        }
                    }
                settings.apply {
                    javaScriptEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onRetrieveVisitorData(newVisitorData: String?) {
                            if (!newVisitorData.isNullOrBlank()) {
                                visitorData = newVisitorData
                            }
                        }

                        @JavascriptInterface
                        fun onRetrieveDataSyncId(newDataSyncId: String?) {
                            if (!newDataSyncId.isNullOrBlank()) {
                                dataSyncId = newDataSyncId
                            }
                        }

                        @JavascriptInterface
                        fun onRetrievePoToken(newPoToken: String?) {
                            if (!newPoToken.isNullOrBlank()) {
                                PreferenceStore.launchEdit(context.dataStore) {
                                    putLegacyPoToken(newPoToken)
                                }
                            }
                        }
                    },
                    "Android",
                )
                webView = this
                resetAuthWebViewSession(context, this, clearCookies = true) {
                    loadUrl(startUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_LOGIN_URL)
                }
            }
        },
    )

    TopAppBar(
        title = { Text(stringResource(R.string.login)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

private fun mergeYouTubeCookies(
    cookieManager: CookieManager,
    currentUrl: String? = null,
): String? {
    val cookieParts = linkedMapOf<String, String>()
    val candidateUrls = linkedSetOf<String>()

    currentUrl.toYouTubeCookieOrigin()?.let(candidateUrls::add)
    candidateUrls.addAll(YOUTUBE_COOKIE_URLS)

    cookieManager.flush()

    candidateUrls.forEach { url ->
        cookieManager
            .getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) return@forEach

                val key = part.substring(0, separatorIndex).trim()
                val value = part.substring(separatorIndex + 1).trim()
                if (key.isNotEmpty()) {
                    cookieParts[key] = value
                }
            }
    }

    return cookieParts
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}

private fun String?.toYouTubeCookieOrigin(): String? {
    val parsed = this?.let(Uri::parse) ?: return null
    val host = parsed.host?.lowercase() ?: return null
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null

    val scheme =
        parsed.scheme
            ?.takeIf { it.equals("https", ignoreCase = true) || it.equals("http", ignoreCase = true) }
            ?: "https"

    return "$scheme://$host"
}
