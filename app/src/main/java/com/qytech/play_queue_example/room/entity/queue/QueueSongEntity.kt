package com.qytech.play_queue_example.room.entity.queue

import androidx.room.Entity
import androidx.room.Index
import com.qytech.play_queue.local.IQueueSongEntity
import com.qytech.play_queue_example.model.QueueSong

@Entity(
    tableName = "queue_songs",
    primaryKeys = ["segmentId", "id"],
    indices = [Index(value = ["segmentId", "sortOrderInSegment"])]
)
data class QueueSongEntity(
    override val id: String,
    override val segmentId: String,
    override val name: String,
    override val coverUrl: String?,
    override val artist: String,
    override val durationMs: Long,
    override val playUrl: String?,
    override val sortOrderInSegment: Int
) : IQueueSongEntity

fun QueueSongEntity.toUiModel(
    globalPosition: Int,
    playlist: QueueSegmentEntity,
    isPlaying: Boolean
) = QueueSong(
    globalPosition = globalPosition,
    songId = id,
    segmentId = segmentId,
    segmentName = playlist.name,
    name = name,
    artist = artist,
    durationText = durationMs.toDurationText(),
    isPlaying = isPlaying
)

private fun Long.toDurationText(): String {
    val minute = this / 1000 / 60
    val second = this / 1000 % 60
    return "$minute:${second.toString().padStart(2, '0')}"
}