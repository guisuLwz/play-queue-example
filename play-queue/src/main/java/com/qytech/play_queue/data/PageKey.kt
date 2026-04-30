package com.qytech.play_queue.data

/**
 * PageKey 用来唯一定位某个歌单的某一页。
 */
data class PageKey(
    val segmentId: String,
    val segmentType: String = "",
    val page: Int
)