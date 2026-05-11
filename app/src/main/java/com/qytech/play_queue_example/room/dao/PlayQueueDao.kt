// data.local 包：DAO 属于本地数据库访问层。
package com.qytech.play_queue_example.room.dao

// Dao 注解告诉 Room：这个接口里定义的是数据库操作方法。
import androidx.room.Dao
// Insert 注解用于插入数据。
import androidx.room.Insert
// OnConflictStrategy.REPLACE 表示主键冲突时用新数据覆盖旧数据。
import androidx.room.OnConflictStrategy
// Query 注解用于写固定 SQL。
import androidx.room.Query
// RawQuery 用于动态 SQL。本项目窗口范围是运行时才知道的，所以需要它。
import androidx.room.RawQuery
// Transaction 表示这个函数里的多个数据库操作要放在同一个事务里执行。
import androidx.room.Transaction
// SupportSQLiteQuery 是 RawQuery 接收的 SQL 对象类型。
import androidx.sqlite.db.SupportSQLiteQuery
import com.qytech.play_queue.local.BasePlayQueueDao
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentPageEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentRef
import com.qytech.play_queue_example.room.entity.queue.QueueSongEntity
// Flow 表示持续观察数据库变化。表变了，Room 会自动重新发射数据。
import kotlinx.coroutines.flow.Flow

// MusicDao 是所有音乐相关数据库读写入口。
@Dao
interface PlayQueueDao : BasePlayQueueDao<
        SupportSQLiteQuery,
        QueueSongEntity,
        QueueSegmentEntity,
        QueueSegmentPageEntity,
        QueueSegmentRef> {

    @Query("SELECT * FROM queue_segments ORDER BY id")
    override fun observeSegments(): Flow<List<QueueSegmentEntity>>

    @RawQuery(observedEntities = [QueueSongEntity::class])
    override fun observeSongsInWindow(query: SupportSQLiteQuery): Flow<List<QueueSongEntity>>

    @RawQuery(observedEntities = [QueueSegmentPageEntity::class])
    override fun observePagesInWindow(query: SupportSQLiteQuery): Flow<List<QueueSegmentPageEntity>>

    @Query("SELECT * FROM queue_segment_pages")
    override fun observeAllPages(): Flow<List<QueueSegmentPageEntity>>

    @Query("SELECT * FROM queue_segment_refs ORDER BY sortIndex ASC")
    override fun observeRefs(): Flow<List<QueueSegmentRef>>

    @Query("SELECT * FROM queue_segments ORDER BY id")
    override suspend fun getSegments(): List<QueueSegmentEntity>

    @Query("SELECT * FROM queue_segments WHERE id = :segmentId")
    override suspend fun getSegment(segmentId: String): QueueSegmentEntity?

    @Query("SELECT sortIndex FROM queue_segments WHERE id = :segmentId")
    override suspend fun getSegmentSortIndex(segmentId: String): String?

    @Query("SELECT sortIndex FROM queue_segments WHERE sortIndex < :curSortIndex ORDER BY sortIndex DESC LIMIT 1")
    override suspend fun getSegmentPreviousSortIndex(curSortIndex: String): String?

    @Query("SELECT sortIndex FROM queue_segments WHERE sortIndex > :curSortIndex ORDER BY sortIndex ASC LIMIT 1")
    override suspend fun getSegmentNextSortIndex(curSortIndex: String): String?

    @Query("SELECT sortIndex FROM queue_segments ORDER BY sortIndex ASC LIMIT 1")
    override suspend fun getSegmentFirstSortIndex(): String?

    @Query("SELECT sortIndex FROM queue_segments ORDER BY sortIndex DESC LIMIT 1")
    override suspend fun getSegmentLastSortIndex(): String?

    @Query("SELECT * FROM queue_segment_pages WHERE segmentId = :segmentId AND page = :page LIMIT 1")
    override suspend fun getPage(segmentId: String, page: Int): QueueSegmentPageEntity?

    @Query("SELECT * FROM queue_songs WHERE segmentId = :segmentId AND sortOrderInSegment = :sortOrderInSegment LIMIT 1")
    override suspend fun getSongAtPosition(
        segmentId: String,
        sortOrderInSegment: Int
    ): QueueSongEntity?

    @Query("SELECT * FROM queue_songs WHERE id = :songId")
    override suspend fun getSongsById(songId: String): List<QueueSongEntity>

    @Query("SELECT * FROM queue_songs WHERE id IN (:songIds)")
    override suspend fun getSongsByIds(songIds: List<String>): List<QueueSongEntity>

    @Query("SELECT * FROM queue_songs WHERE segmentId = :segmentId")
    override suspend fun getSongsBySegmentId(segmentId: String): List<QueueSongEntity>

    @Query("SELECT * FROM queue_segment_refs ORDER BY sortIndex ASC")
    override suspend fun getRefs(): List<QueueSegmentRef>

    @Query("SELECT * FROM queue_segment_refs WHERE segmentId = :segmentId ORDER BY sortIndex ASC")
    override suspend fun getRefsBySegmentId(segmentId: String): List<QueueSegmentRef>

    @Query(
        """
        SELECT COALESCE(SUM(cachedCount), 0)
        FROM queue_segment_pages
        WHERE segmentId = :segmentId
            AND isCached = 1
        """
    )
    override suspend fun countCachedSongs(segmentId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsertSegments(segments: List<QueueSegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsertSegment(segment: QueueSegmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsertSongs(songs: List<QueueSongEntity>)

    @Transaction
    override suspend fun refreshPlayQueue(segments: List<QueueSegmentEntity>, refs: List<QueueSegmentRef>) {
        clearSegments()
        clearSegmentRefs()
        clearSegmentPages()
        clearSongs()
        upsertSegments(segments)
        upsertRefs(refs)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsertPage(page: QueueSegmentPageEntity)

    @Transaction
    override suspend fun cachePage(
        // playlist：更新后的歌单状态。
        segment: QueueSegmentEntity,
        // page：这一页的缓存状态。
        page: QueueSegmentPageEntity,
        // songs：这一页包含的歌曲。
        songs: List<QueueSongEntity>
    ) {
        // 先写歌单状态。
        upsertSegment(segment)
        // 再写这一页的歌曲。
        upsertSongs(songs)
        // 最后标记这一页已经缓存成功。
        upsertPage(page)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsertRef(ref: QueueSegmentRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsertRefs(ref: List<QueueSegmentRef>)

    @Transaction
    override suspend fun refreshRefs(refs: List<QueueSegmentRef>) {
        clearSegmentRefs()
        upsertRefs(refs)
    }

    @Query("DELETE FROM queue_segments WHERE id = :segmentId")
    override suspend fun deleteSegmentById(segmentId: String)

    @Query("DELETE FROM queue_songs WHERE segmentId = :segmentId")
    override suspend fun deleteSongsBySegmentId(segmentId: String)

    @Query("DELETE FROM queue_segment_pages WHERE segmentId = :segmentId")
    override suspend fun deleteSegmentPageBySegmentId(segmentId: String)

    @Query("DELETE FROM queue_segment_refs WHERE segmentId = :segmentId")
    suspend fun deleteSegmentRefBySegmentId(segmentId: String)

    @Transaction
    override suspend fun removeQueueSegment(segmentId: String) {
        deleteSongsBySegmentId(segmentId)
        deleteSegmentPageBySegmentId(segmentId)
        deleteSegmentRefBySegmentId(segmentId)
        deleteSegmentById(segmentId)
    }

    @Query("DELETE FROM queue_segments")
    override suspend fun clearSegments()

    @Query("DELETE FROM queue_segment_pages")
    override suspend fun clearSegmentPages()

    @Query("DELETE FROM queue_segment_refs")
    override suspend fun clearSegmentRefs()

    @Query("DELETE FROM queue_songs")
    override suspend fun clearSongs()

    @Transaction
    override suspend fun clearPlayQueue() {
        clearSegments()
        clearSegmentPages()
        clearSegmentRefs()
        clearSongs()
    }


}
