package com.qytech.play_queue_example.room.entity.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.qytech.play_queue.local.IQueueSegmentRefEntity

@Entity(
    tableName = "queue_segment_refs",
    indices = [Index(value = ["segmentId"])]
)
data class QueueSegmentRef(
    @PrimaryKey(autoGenerate = true)
    override val id: Long = 0,
    override val segmentId: String,
    override val startOffsetInSegment: Int,
    override val length: Int,
    override val sortIndex: String = ""
): IQueueSegmentRefEntity {
}