/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

internal interface NetworkEvents<T> {
    fun onCapabilitiesChanged(
        network: T,
        isValidatedInternet: Boolean,
    )

    fun onLost(network: T)
}

internal class ValidatedNetworkRegistry<T>(
    initialValidatedNetworks: Set<T> = emptySet(),
) {
    private val validatedNetworks = initialValidatedNetworks.toMutableSet()

    fun isOnline(): Boolean = validatedNetworks.isNotEmpty()

    fun onCapabilitiesChanged(
        network: T,
        isValidatedInternet: Boolean,
    ): Boolean {
        if (isValidatedInternet) {
            validatedNetworks += network
        } else {
            validatedNetworks -= network
        }
        return isOnline()
    }

    fun onLost(network: T): Boolean {
        validatedNetworks -= network
        return isOnline()
    }
}

internal fun <T> validatedNetworkStatusFlow(
    initialValidatedNetworks: Set<T> = emptySet(),
    register: (NetworkEvents<T>) -> AutoCloseable,
): Flow<Boolean> =
    callbackFlow {
        val registry = ValidatedNetworkRegistry(initialValidatedNetworks)
        trySend(registry.isOnline())

        val handle =
            register(
                object : NetworkEvents<T> {
                    override fun onCapabilitiesChanged(
                        network: T,
                        isValidatedInternet: Boolean,
                    ) {
                        trySend(registry.onCapabilitiesChanged(network, isValidatedInternet))
                    }

                    override fun onLost(network: T) {
                        trySend(registry.onLost(network))
                    }
                },
            )

        awaitClose {
            handle.close()
        }
    }.distinctUntilChanged()

@Singleton
class NetworkMonitor
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val connectivityManager =
            requireNotNull(context.getSystemService(ConnectivityManager::class.java))

        val isOnline: Flow<Boolean> =
            validatedNetworkStatusFlow(initialValidatedNetworks = currentValidatedNetworks()) { events ->
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            events.onCapabilitiesChanged(
                                network = network,
                                isValidatedInternet = networkCapabilities.isValidatedInternet(),
                            )
                        }

                        override fun onLost(network: Network) {
                            events.onLost(network)
                        }
                    }

                val request =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()

                connectivityManager.registerNetworkCallback(request, callback)

                AutoCloseable {
                    runCatching {
                        connectivityManager.unregisterNetworkCallback(callback)
                    }
                }
            }

        private fun currentValidatedNetworks(): Set<Network> {
            val activeNetwork = connectivityManager.activeNetwork ?: return emptySet()
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            return if (capabilities.isValidatedInternet()) {
                setOf(activeNetwork)
            } else {
                emptySet()
            }
        }
    }

private fun NetworkCapabilities?.isValidatedInternet(): Boolean =
    this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        this.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
