package com.qytech.play_queue_example.model

import com.qytech.play_queue_example.room.entity.source.PlaylistSourceEntity
import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: Long,
    val title: String,
    val subtitle: String,
    val songCount: Int,
    val colorArgb: Int,
)

fun PlaylistSourceEntity.toModel(): Playlist {
    return Playlist(
        id = id,
        title = title,
        subtitle = subtitle,
        songCount = songCount,
        colorArgb = colorArgb,
    )
}