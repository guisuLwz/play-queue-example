package com.qytech.play_queue.data

import com.qytech.play_queue.domain.BaseQueuePositionMapper
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSegmentPageEntity
import com.qytech.play_queue.local.IQueueSegmentRefEntity
import com.qytech.play_queue.local.IQueueSongEntity

/**
 * RepositorySnapshot 是 Repository 给 ViewModel 的“一次窗口快照”。
 */
data class RepositorySnapshot<S : IQueueSongEntity, SEG : IQueueSegmentEntity, SEG_PAGE : IQueueSegmentPageEntity, SEG_REF: IQueueSegmentRefEntity>(
    val positionMapper: BaseQueuePositionMapper<SEG, SEG_REF>?,
    val segments: List<SEG>,
    val totalSize: Int,
    val window: IntRange,
    val ranges: List<SegmentWindowRange<SEG>>,
    val songsByPositionKey: Map<PositionKey, S>,
    val pagesByKey: Map<PageKey, SEG_PAGE>,
    val allPagesByKey: Map<PageKey, SEG_PAGE>,
    val loadingPageKeys: Set<PageKey>
) {
    fun locate(globalPosition: Int) = positionMapper?.locate(globalPosition)

    // companion object 类似 Java 的 static 区域。
    companion object {
        // empty：创建空快照，初始没有歌单时使用。
        fun <S : IQueueSongEntity, SEG : IQueueSegmentEntity, SEG_PAGE : IQueueSegmentPageEntity, SEG_REF: IQueueSegmentRefEntity> empty(window: IntRange) =
            RepositorySnapshot<S, SEG, SEG_PAGE, SEG_REF>(
                positionMapper = null,
                segments = emptyList(),
                totalSize = 0,
                window = window,
                ranges = emptyList(),
                songsByPositionKey = emptyMap(),
                pagesByKey = emptyMap(),
                allPagesByKey = emptyMap(),
                loadingPageKeys = emptySet()
            )

    }
}
