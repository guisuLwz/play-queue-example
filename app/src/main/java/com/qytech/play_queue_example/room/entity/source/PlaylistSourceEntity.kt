package com.qytech.play_queue_example.room.entity.source

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_playlists")
data class PlaylistSourceEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val subtitle: String,
    val songCount: Int,
    val colorArgb: Int,
) {
}