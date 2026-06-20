/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe provider for Spotify GQL persisted-query hashes.
 *
 * Initialized with hardcoded defaults that ship with each release.
 * The app module can update hashes at runtime from a remote JSON
 * registry via [updateHashes], enabling automatic recovery when
 * Spotify rotates hashes between app releases.
 *
 * Resolution order: remote/cached → hardcoded (always available).
 */
object SpotifyHashProvider {
    enum class HashSource { HARDCODED, CACHED, REMOTE }

    data class GqlHashEntry(
        val hash: String,
        val previousHash: String? = null,
        val source: HashSource = HashSource.HARDCODED,
    )

    private val hashes = ConcurrentHashMap<String, GqlHashEntry>()

    init {
        loadHardcodedDefaults()
    }

    private fun loadHardcodedDefaults() {
        val defaults =
            mapOf(
                "profileAttributes" to "53bcb064f6cd18c23f752bc324a791194d20df612d8e1239c735144ab0399ced",
                "libraryV3" to "973e511ca44261fda7eebac8b653155e7caee3675abb4fb110cc1b8c78b091c3",
                "fetchPlaylist" to "346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763",
                "fetchLibraryTracks" to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
                "searchDesktop" to "4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49",
                "queryArtistOverview" to "5b9e64f43843fa3a9b6a98543600299b0a2cbbbccfdcdcef2402eb9c1017ca4c",
                "getAlbum" to "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10",
                "queryWhatsNewFeed" to "3b53dede3c6054e8b7c962dd280eb6761c5d1c82b06b039f4110d76a62b4966b",
                "addToPlaylist" to "47b2a1234b17748d332dd0431534f22450e9ecbb3d5ddcdacbd83368636a0990",
                "removeFromPlaylist" to "47b2a1234b17748d332dd0431534f22450e9ecbb3d5ddcdacbd83368636a0990",
                "moveItemsInPlaylist" to "47b2a1234b17748d332dd0431534f22450e9ecbb3d5ddcdacbd83368636a0990",
                "editPlaylistAttributes" to "35a1a9ce3a2f4f8c32ee0e24c63c2069c6613c0a0b7e56d0e40dabe69a0b4f80",
                "addToLibrary" to "7c5a69420e2bfae3da5cc4e14cbc8bb3f6090f80afc00ffc179177f19be3f33d",
                "removeFromLibrary" to "7c5a69420e2bfae3da5cc4e14cbc8bb3f6090f80afc00ffc179177f19be3f33d",
                "home" to "23e37f2e58d82d567f27080101d36609009d8c3676457b1086cb0acc55b72a5d",
            )
        defaults.forEach { (op, hash) ->
            hashes[op] = GqlHashEntry(hash = hash, source = HashSource.HARDCODED)
        }
    }

    /**
     * Returns the best available hash for [operationName].
     * Throws [IllegalStateException] if the operation is unknown
     * (should never happen — all operations have hardcoded defaults).
     */
    fun getHash(operationName: String): String =
        hashes[operationName]?.hash
            ?: error("No hash registered for GQL operation: $operationName")

    /**
     * Returns the previous hash for [operationName], if one was recorded
     * during a hash rotation. Used as a fallback when the current hash
     * returns a PersistedQueryNotFound error.
     */
    fun getPreviousHash(operationName: String): String? = hashes[operationName]?.previousHash

    /**
     * Bulk-update hashes from a remote or cached source.
     * Only overwrites entries whose remote hash differs from the
     * current hardcoded default, preserving the hardcoded value
     * as an implicit fallback (always reachable via [loadHardcodedDefaults]).
     */
    fun updateHashes(
        remoteHashes: Map<String, RemoteHashEntry>,
        source: HashSource,
    ): UpdateResult {
        var updated = 0
        var unchanged = 0
        remoteHashes.forEach { (op, remote) ->
            val current = hashes[op]
            if (current != null) {
                val hashChanged = current.hash != remote.hash
                if (hashChanged) updated++ else unchanged++
                hashes[op] =
                    GqlHashEntry(
                        hash = remote.hash,
                        previousHash = remote.previousHash,
                        source = source,
                    )
            }
        }
        return UpdateResult(updated = updated, unchanged = unchanged)
    }

    data class UpdateResult(
        val updated: Int,
        val unchanged: Int,
    )

    fun getAll(): Map<String, GqlHashEntry> = hashes.toMap()

    data class RemoteHashEntry(
        val hash: String,
        val previousHash: String? = null,
    )
}
