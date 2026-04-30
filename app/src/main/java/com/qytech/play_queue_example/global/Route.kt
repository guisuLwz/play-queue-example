package com.qytech.play_queue_example.global

import androidx.navigation3.runtime.NavKey
import com.qytech.play_queue_example.model.Playlist
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute: NavKey

@Serializable
data object Playlists: AppRoute

@Serializable
data class PlaylistSongs(
    val playlist: Playlist
): AppRoute

@Serializable
data object PlayQueue: AppRoute
