package com.qytech.play_queue.local

import kotlinx.coroutines.flow.Flow

interface BasePlayQueueDao<
        QUERY,
        S: IQueueSongEntity,
        SEG: IQueueSegmentEntity,
        SEG_PAGE: IQueueSegmentPageEntity> {

    /**
     * 观察歌单表。
     * 例如：‘@’Query("SELECT * FROM segments ORDER BY id")
     */
    fun observeSegments(): Flow<List<SEG>>

    /**
     * 观察“当前窗口内”的歌曲。注意：不是查 songs 全表。
     * observedEntities 告诉 Room：只要 SongEntity 对应的 songs 表变化，就重新执行这个 RawQuery。
     * QUERY -> SupportSQLiteQuery 动态查询
     * 例如：’@‘RawQuery(observedEntities = [SongEntity::class])
     */
    fun observeSongsInWindow(query: QUERY): Flow<List<S>>

    /**
     * 观察“当前窗口内”的页状态。UI 需要知道窗口里的页是成功、失败还是正在加载。
     */
    fun observePagesInWindow(query: QUERY): Flow<List<SEG_PAGE>>

    /**
     * 一次性读取所有歌单。Repository 做业务判断时用，不是给 UI 长期观察用。
     * 例如：'@'Query("SELECT * FROM segments ORDER BY id")
     */
    suspend fun getSegments(): List<SEG>

    /**
     * 一次性读取某个歌单某一页的状态，用于判断是否需要请求网络。
     * 例如：'@'Query("SELECT * FROM segment_pages WHERE segmentId = :segmentId AND page = :page LIMIT 1")
     */
    suspend fun getPage(segmentId: String, page: Int): SEG_PAGE?

    suspend fun getSongAtPosition(segmentId: String, sortOrderInSegment: Int): S?

    /**
     * 统计某个歌单当前已经缓存了多少首歌，用来更新 PlaylistEntity.loadedCount。
     * 例如：'@'Query("SELECT COUNT(*) FROM songs WHERE segmentId = :segmentId")
     */
    suspend fun countCachedSongs(segmentId: String): Int

    /**
     * 批量插入或更新歌单。
     * 例如：'@'Insert(onConflict = OnConflictStrategy.REPLACE)
     */
    suspend fun upsertSegments(segments: List<SEG>)

    /**
     * 插入或更新单个歌单。
     * 例如：'@'Insert(onConflict = OnConflictStrategy.REPLACE)
     */
    suspend fun upsertSegment(segment: SEG)

    /**
     * 批量插入或更新歌曲。
     * 例如：'@'Insert(onConflict = OnConflictStrategy.REPLACE)
     */
    suspend fun upsertSongs(songs: List<S>)

    /**
     * 插入或更新某一页的状态。
     * 例如：'@'Insert(onConflict = OnConflictStrategy.REPLACE)
     */
    suspend fun upsertPage(page: SEG_PAGE)

    /**
     * cachePage 用事务保存“一页请求成功后的所有数据”。
     * 1. upsertSegment
     * 2. upsertSongs
     * 3. upsertPage
     * 例如：'@'Transaction
     */
    suspend fun cachePage(
        segment: SEG,
        page: SEG_PAGE,
        songs: List<S>
    )
}
