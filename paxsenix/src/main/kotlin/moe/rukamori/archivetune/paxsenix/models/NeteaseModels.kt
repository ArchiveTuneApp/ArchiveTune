/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.paxsenix.models

import kotlinx.serialization.Serializable

@Serializable
data class NeteaseSearchResponse(
    val result: NeteaseSearchResult? = null,
)

@Serializable
data class NeteaseSearchResult(
    val songs: List<NeteaseSong> = emptyList(),
)

@Serializable
data class NeteaseSong(
    val id: Long = 0,
    val name: String? = null,
    val artists: List<NeteaseArtist> = emptyList(),
    val duration: Int = 0,
)

@Serializable
data class NeteaseArtist(
    val name: String,
)

@Serializable
data class NeteaseLyricsResponse(
    val lrc: NeteaseLrc? = null,
)

@Serializable
data class NeteaseLrc(
    val lyric: String? = null,
)
