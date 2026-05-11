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

fun QueueSegmentEntity.toLoadState(
    loadingKeys: Set<PageKey>,
    pagesByKey: Map<PageKey, QueueSegmentPageEntity>
): QueueSegmentLoadState {
    val expectedPageCount = expectedPageCount()
    val expectedPages = expectedPageCount?.let { count ->
        (1..count).map { page -> pagesByKey[PageKey(segmentId = id, page = page)] }
    }.orEmpty()
    val hasPageError = expectedPages.any { page -> page?.error != null }
    val isFullyCached = expectedPageCount != null &&
            expectedPages.size == expectedPageCount &&
            expectedPages.all { page -> page?.isCached == true && page.error == null }

    return QueueSegmentLoadState(
        segmentId = id,
        name = name,
        totalCount = totalCount,
        cachedCount = loadedCount,
        pageSize = pageSize,
        isLoading = loadingKeys.any { it.segmentId == id },
        error = lastError,
        hasMore = hasMore,
        isFullyCached = isFullyCached,
        hasPageError = hasPageError
    )
}

private fun QueueSegmentEntity.expectedPageCount(): Int? {
    val count = totalCount ?: return null
    if (count <= 0) return 0
    return (count + pageSize - 1) / pageSize
}
