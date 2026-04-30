package com.qytech.play_queue_example.room.entity.source

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_playlists")
data class PlaylistSourceEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val subtitle: String,
    val totalCount: Int,
    val colorArgb: Int,
) {
}