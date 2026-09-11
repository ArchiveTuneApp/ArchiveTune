/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.podcast

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.pages.PodcastPage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository
    @Inject
    constructor() {
        suspend fun loadPodcast(browseId: String): Result<PodcastPage> =
            withContext(Dispatchers.IO) {
                try {
                    YouTube.podcast(browseId).also { result -> result.rethrowCancellation() }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    Result.failure(throwable)
                }
            }

        suspend fun loadContinuation(continuation: String): Result<PodcastPage.Continuation> =
            withContext(Dispatchers.IO) {
                try {
                    YouTube.podcastContinuation(continuation).also { result -> result.rethrowCancellation() }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    Result.failure(throwable)
                }
            }
    }

private fun Result<*>.rethrowCancellation() {
    val failure = exceptionOrNull()
    if (failure is CancellationException) throw failure
}
