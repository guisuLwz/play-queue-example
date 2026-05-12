package com.qytech.play_queue.data

/**
 * PositionKey 用来唯一定位某个歌单内部某个位置的歌曲。
 */
data class PositionKey(
    val segmentId: String,
    val segmentType: String,
    val sortOrderInSegment: Int
)