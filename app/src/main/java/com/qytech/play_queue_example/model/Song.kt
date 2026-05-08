package com.qytech.play_queue_example.model

import com.qytech.play_queue_example.room.entity.source.SongSourceEntity
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: Long,
    val playlistId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val indexInSegment: Int,
)

fun SongSourceEntity.toModel(): Song {
    return Song(
        id = id,
        playlistId = playlistId,
        title = songName,
        artist = singerName,
        album = album,
        durationSeconds = durationSeconds,
        indexInSegment = indexInSegment,
    )
}
