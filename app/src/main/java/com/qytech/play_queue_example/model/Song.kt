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
)

fun SongSourceEntity.toModel(): Song {
    return Song(
        id = id,
        playlistId = playlistId,
        title = songName,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
    )
}
