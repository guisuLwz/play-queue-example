package com.qytech.play_queue.data

import com.qytech.play_queue.local.IQueueSegmentEntity

/**
 * LocatedPosition 表示“一个全局位置定位后的结果”。
 */
data class LocatedPosition<SEG: IQueueSegmentEntity>(
    val segment: SEG,
    val startGlobalPosition: Int,
    val offsetInSegment: Int
) {
    // page：根据 offsetInPlaylist 和 pageSize 算出这是第几页。
    // 例如 pageSize=100，offset=0..99 是第 1 页，100..199 是第 2 页。
    /**
     * 是歌单的第几页
     */
    val page: Int get() = offsetInSegment / segment.pageSize + 1
}