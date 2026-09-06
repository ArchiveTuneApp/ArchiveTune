package moe.rukamori.archivetune.utils

import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageResult
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.repository.PlaylistCoverRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class PlaylistCoverInterceptor
    @Inject
    constructor(
        private val repository: PlaylistCoverRepository,
    ) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val result = chain.proceed()
            if (result !is ErrorResult || !request.networkCachePolicy.readEnabled) return result

            val statusCode = (result.throwable as? HttpException)?.response?.code ?: return result
            if (statusCode != 400 && statusCode != 401 && statusCode != 403 && statusCode != 404 && statusCode != 410) {
                return result
            }

            val sourceUrl =
                when (val data = request.data) {
                    is String -> data
                    is android.net.Uri -> data.toString()
                    is coil3.Uri -> data.toString()
                    else -> return result
                }
            val url = sourceUrl.toHttpUrlOrNull() ?: return result
            if (url.host != "ytimg.com" && !url.host.endsWith(".ytimg.com")) return result
            val path = url.pathSegments
            if (path.size != 3 || path[0] != "pl_c" || path[1].isBlank()) return result

            return try {
                val refreshedUrl = repository.refreshRemoteCover(path[1], sourceUrl)
                if (refreshedUrl == sourceUrl) return result

                val refreshedRequest =
                    request.newBuilder()
                        .data(refreshedUrl)
                        .memoryCacheKey(refreshedUrl)
                        .diskCacheKey(refreshedUrl)
                        .build()
                chain.withRequest(refreshedRequest).proceed()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                ErrorResult(
                    image = result.image,
                    request = request,
                    throwable = exception,
                )
            }
        }
    }
