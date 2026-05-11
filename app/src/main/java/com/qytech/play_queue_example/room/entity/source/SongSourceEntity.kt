package com.qytech.play_queue_example.room.entity.source

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "source_songs",
    primaryKeys = ["playlistId", "indexInSegment"],
    indices = [Index(value = ["id"])]
)
data class SongSourceEntity(
    val id: Long,
    val playlistId: Long,
    val songName: String = "",
    val singerName: String = "",
    val album: String = "",
    val durationSeconds: Int,
    val indexInSegment: Int
) {
}
