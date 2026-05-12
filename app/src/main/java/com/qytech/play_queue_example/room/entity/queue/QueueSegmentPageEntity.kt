package com.qytech.play_queue_example.room.entity.queue

import androidx.room.Entity
import androidx.room.Index
import com.qytech.play_queue.local.IQueueSegmentPageEntity

@Entity(
    tableName = "queue_segment_pages",
    primaryKeys = ["segmentId", "page"],
    indices = [Index(value = ["segmentId", "page"])]
)
data class QueueSegmentPageEntity(
    override val segmentId: String,
    override val segmentType: String,
    override val page: Int,
    override val isCached: Boolean,
    override val cachedCount: Int,
    override val error: String?
) : IQueueSegmentPageEntity
