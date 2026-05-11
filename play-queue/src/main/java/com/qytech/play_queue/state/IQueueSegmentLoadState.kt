package com.qytech.play_queue.state

/**
 * PlaylistLoadState 表示“一个歌单在界面上展示出来的加载状态”。
 * 注意：它不是数据库表，也不是网络返回，它是给 UI 用的状态模型。
 */
interface IQueueSegmentLoadState {
    val segmentId: String
    val name: String
    val totalCount: Int?
    val cachedCount: Int
    val pageSize: Int
    val isLoading: Boolean
    val error: String?
    val hasMore: Boolean
    val isFullyCached: Boolean
    val hasPageError: Boolean
}