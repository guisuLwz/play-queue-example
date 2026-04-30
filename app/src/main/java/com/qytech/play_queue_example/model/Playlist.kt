package com.qytech.play_queue_example.model

import com.qytech.play_queue_example.room.entity.source.PlaylistSourceEntity
import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val subtitle: String,
    val totalCount: Int,
    val colorArgb: Int,
)

fun PlaylistSourceEntity.toModel(): Playlist {
    return Playlist(
        id = id,
        name = name,
        subtitle = subtitle,
        totalCount = totalCount,
        colorArgb = colorArgb,
    )
}