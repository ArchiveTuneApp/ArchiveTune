/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException

object CanvasNetworkAccess {
    private val mutablePolicy = MutableStateFlow(CanvasPolicy())
    val policy = mutablePolicy.asStateFlow()
    private val calls = LinkedHashMap<Call, CanvasSource>()

    @Volatile
    internal var connectivity: () -> CanvasConnectivity = { CanvasConnectivity() }

    @Synchronized
    internal fun update(policy: CanvasPolicy) {
        mutablePolicy.value = policy
        calls.filterValues { source -> !policy.networkAllowed || !policy.configuration.source.accepts(source) }
            .keys.toList().forEach(Call::cancel)
    }

    fun check(source: CanvasSource? = null) {
        requireAllowed(policy.value.copy(connectivity = connectivity()), source)
    }

    fun checkTransfer(source: CanvasSource) {
        requireAllowed(policy.value, source)
    }

    private fun requireAllowed(current: CanvasPolicy, source: CanvasSource?) {
        if (!current.networkAllowed || (source != null && !current.configuration.source.accepts(source))) {
            throw IOException("Canvas network access is disabled by the current policy")
        }
    }

    fun intercept(chain: Interceptor.Chain, source: CanvasSource): Response {
        val call = chain.call()
        synchronized(this) {
            check(source)
            calls[call] = source
        }
        try {
            val response = chain.proceed(chain.request())
            val body = response.body
            if (body == null) {
                remove(call)
                return response
            }
            val guardedSource = object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    return try {
                        checkTransfer(source)
                        val count = super.read(sink, byteCount)
                        checkTransfer(source)
                        if (count == -1L) remove(call)
                        count
                    } catch (error: Exception) {
                        call.cancel()
                        remove(call)
                        throw error
                    }
                }

                override fun close() {
                    try {
                        super.close()
                    } finally {
                        remove(call)
                    }
                }
            }.buffer()
            return response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength(): Long = body.contentLength()
                override fun source(): BufferedSource = guardedSource
            }).build()
        } catch (error: Exception) {
            remove(call)
            throw error
        }
    }

    @Synchronized
    private fun remove(call: Call) {
        calls.remove(call)
    }
}
