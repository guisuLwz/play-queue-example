package com.qytech.play_queue.local

/**
 * 片段 room 的实例接口，用于存储 playlist、album等歌曲列表
 * 设：id为主键
 */
interface IQueueSegmentEntity {
    val id: String
    val type: String
    val name: String
    val coverUrl: String?
    val totalCount: Int?
    val loadedCount: Int
    val pageSize: Int
    val hasMore: Boolean
    val lastError: String?
    val sortIndex: String
}

/**
 * 歌曲 room 的实例接口，用于存储 song
 * 设：segmentId 和 id 为主键
 * 设：segmentId 和 sortOrder 为索引
 */
interface IQueueSongEntity {
    val id: String
    val segmentId: String
    val name: String
    val coverUrl: String?
    val artist: String
    val durationMs: Long
    val playUrl: String?
    /**
     * 在片段中的实际index，从 0 开始
     */
    val sortOrderInSegment: Int
}

/**
 * 片段每页记录 room 的实例接口，用于存储每个片段的每一页信息
 * 设：segmentId 和 page 为主键
 * 设：segmentId 和 page 为索引
 */
interface IQueueSegmentPageEntity {
    val segmentId: String

    /**
     * 当前片段的第几页，从 1 开始
     */
    val page: Int

    /**
     * true 表示这一页已经完整写入 songs 表
     */
    val isCached: Boolean

    /**
     * 这一页加载失败时的错误信息；null 表示没有错误
     */
    val error: String?
}