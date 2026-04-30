package com.qytech.play_queue_example.room.entity.queue

import androidx.room.Entity
import androidx.room.Index
import com.qytech.play_queue.local.IQueueSongEntity

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