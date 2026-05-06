package com.qytech.play_queue_example.room.entity.queue

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.qytech.play_queue.data.PageKey
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue_example.state.QueueSegmentLoadState

@Entity(tableName = "queue_segments")
data class QueueSegmentEntity(
    @PrimaryKey
    override val id: String,
    override val type: String,
    override val name: String,
    override val coverUrl: String?,
    override val totalCount: Int?,
    override val loadedCount: Int,
    override val pageSize: Int,
    override val hasMore: Boolean,
    override val lastError: String?,
    override val sortIndex: String = ""
) : IQueueSegmentEntity

fun QueueSegmentEntity.toLoadState(loadingKeys: Set<PageKey>) = QueueSegmentLoadState(
    segmentId = id,
    name = name,
    totalCount = totalCount,
    cachedCount = loadedCount,
    pageSize = pageSize,
    isLoading = loadingKeys.any { it.segmentId == id },
    error = lastError,
    hasMore = hasMore
)