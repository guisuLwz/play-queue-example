package com.qytech.play_queue.data

import com.qytech.play_queue.local.IQueueSegmentEntity

/**
 * SegmentWindowRange 表示“当前内存窗口落在某个歌单内的那一段”。
 */
data class SegmentWindowRange<SEG: IQueueSegmentEntity>(
    val segment: SEG,
    val globalStart: Int,
    val globalEnd: Int,
    val offsetStart: Int,
    val offsetEnd: Int
) {
    val firstPage: Int get() = offsetStart / segment.pageSize + 1
    val lastPage: Int get() = offsetEnd / segment.pageSize + 1
}