package com.qytech.play_queue_example.room.entity.source

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_songs")
data class SongSourceEntity(
    @PrimaryKey
    val id: Long,
    val playlistId: Long,
    val songName: String = "",
    val singerName: String = "",
    val album: String = "",
    val durationSeconds: Int,
    val indexInSegment: Int
) {
}