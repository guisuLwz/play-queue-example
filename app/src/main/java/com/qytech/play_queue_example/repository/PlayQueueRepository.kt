package com.qytech.play_queue_example.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.qytech.play_queue.data.SegmentWindowRange
import com.qytech.play_queue.repository.BaseQueueMusicRepository
import com.qytech.play_queue_example.network.NetworkPage
import com.qytech.play_queue_example.network.NetworkSegment
import com.qytech.play_queue_example.network.NetworkSong
import com.qytech.play_queue_example.network.PlayQueueMusicApi
import com.qytech.play_queue_example.room.dao.PlayQueueDao
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentPageEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentRef
import com.qytech.play_queue_example.room.entity.queue.QueueSongEntity
import javax.inject.Inject
import javax.inject.Singleton

const val PLAY_QUEUE_PAGE_SIZE = 50

@Singleton
class PlayQueueRepository @Inject constructor(
    private val dao: PlayQueueDao,
    private val api: PlayQueueMusicApi
) : BaseQueueMusicRepository<
        SupportSQLiteQuery,
        QueueSongEntity,
        NetworkSong,
        QueueSegmentEntity,
        NetworkSegment,
        QueueSegmentPageEntity,
        NetworkPage,
        QueueSegmentRef,
        QueuePositionMapper>(
    dao = dao,
    api = api,
    pageSize = PLAY_QUEUE_PAGE_SIZE
) {
    override fun createQueuePositionMapper(
        segments: List<QueueSegmentEntity>,
        queueRefs: List<QueueSegmentRef>
    ): QueuePositionMapper {
        return QueuePositionMapper(queueRefs, segments.associateBy { it.id })
    }

    override fun createSegmentPageEntity(
        segmentId: String,
        page: Int,
        isCached: Boolean,
        cachedCount: Int,
        error: String?
    ): QueueSegmentPageEntity {
        return QueueSegmentPageEntity(
            segmentId = segmentId,
            page = page,
            isCached = isCached,
            cachedCount = cachedCount,
            error = error
        )
    }

    override fun QueueSegmentEntity.copyTo(
        name: String,
        coverUrl: String?,
        totalCount: Int?,
        loadedCount: Int,
        hasMore: Boolean,
        lastError: String?
    ): QueueSegmentEntity {
        return this.copy(
            name = name,
            coverUrl = coverUrl,
            totalCount = totalCount,
            loadedCount = loadedCount,
            hasMore = hasMore,
            lastError = lastError,
        )
    }

    override fun NetworkSong.toQueueSongEntity(segmentId: String): QueueSongEntity {
        return QueueSongEntity(
            segmentId = segmentId,
            id = id,
            name = name,
            singerName = singerName,
            durationMs = durationMs,
            playUrl = playUrl,
            sortOrderInSegment = sortOrderInSegment,
            coverUrl = coverUrl
        )
    }

    override fun QueueSongEntity.copyTo(
        id: String,
        segmentId: String,
        name: String,
        coverUrl: String?,
        singerName: String,
        durationMs: Long,
        playUrl: String?,
        sortOrderInSegment: Int
    ): QueueSongEntity {
        return this.copy(
            id = id,
            segmentId = segmentId,
            name = name,
            coverUrl = coverUrl,
            singerName = singerName,
            durationMs = durationMs,
            playUrl = playUrl,
            sortOrderInSegment = sortOrderInSegment
        )
    }

    override fun QueueSongEntity.toSingleSongSegment(): QueueSegmentEntity {
        return QueueSegmentEntity(
            id = id,
            type = "song",
            name = name,
            coverUrl = coverUrl,
            totalCount = 1,
            loadedCount = 1,
            pageSize = 1,
            hasMore = false,
            lastError = null
        )
    }

    override fun NetworkSegment.toQueueSegmentEntity(
        loadedCount: Int,
        hasMore: Boolean,
        lastError: String?,
        sortIndex: String
    ): QueueSegmentEntity {
        return QueueSegmentEntity(
            id = id,
            name = name,
            type = type,
            coverUrl = coverUrl,
            totalCount = totalCount,
            loadedCount = loadedCount,
            pageSize = PLAY_QUEUE_PAGE_SIZE,
            hasMore = hasMore,
            lastError = lastError,
            sortIndex = sortIndex
        )
    }

    override fun buildSongsWindowQuery(ranges: List<SegmentWindowRange<QueueSegmentEntity>>): SupportSQLiteQuery {
        return buildWindowQuery(
            // 查询 songs 表。
            select = "SELECT * FROM queue_songs",
            // 排序方式：先按歌单，再按歌单内位置。
            orderBy = "ORDER BY segmentId, sortOrderInSegment",
            // 当前窗口拆出来的多个歌单范围。
            ranges = ranges,
            // false 表示范围字段用 sortOrder。
            pageRange = false
        )
    }

    override fun buildPagesWindowQuery(ranges: List<SegmentWindowRange<QueueSegmentEntity>>): SupportSQLiteQuery {
        return buildWindowQuery(
            // 查询 playlist_pages 表。
            select = "SELECT * FROM queue_segment_pages",
            // 排序方式：先按歌单，再按页码。
            orderBy = "ORDER BY segmentId, page",
            // 当前窗口拆出来的多个歌单范围。
            ranges = ranges,
            // true 表示范围字段用 page。
            pageRange = true
        )
    }

    override fun buildWindowQuery(
        select: String,
        orderBy: String,
        ranges: List<SegmentWindowRange<QueueSegmentEntity>>,
        pageRange: Boolean
    ): SupportSQLiteQuery {
        // 没有任何范围时，WHERE 0 表示永远查不到数据。
        if (ranges.isEmpty()) {
            return SimpleSQLiteQuery("$select WHERE 0 $orderBy")
        }

        // args 保存 SQL 里的 ? 参数，避免把值直接拼进 SQL。
        val args = mutableListOf<Any>()
        // where 把多个歌单窗口片段用 OR 连接。
        val where = ranges.joinToString(separator = " OR ") { range ->
            // 每个片段都先放 playlistId。
            args += range.segment.id
            if (pageRange) {
                // 页状态查询：参数是 firstPage 和 lastPage。
                args += range.firstPage
                args += range.lastPage
                // SQL 片段：某歌单的 page 在窗口覆盖页范围内。
                "(segmentId = ? AND page BETWEEN ? AND ?)"
            } else {
                // 歌曲查询：参数是 offsetStart 和 offsetEnd。
                args += range.offsetStart
                args += range.offsetEnd
                // SQL 片段：某歌单的 sortOrder 在窗口覆盖偏移范围内。
                "(segmentId = ? AND sortOrderInSegment BETWEEN ? AND ?)"
            }
        }

        // 返回 Room 可执行的动态 SQL。
        return SimpleSQLiteQuery("$select WHERE $where $orderBy", args.toTypedArray())
    }

    override fun createQueueRef(
        segmentId: String,
        startOffsetInSegment: Int,
        length: Int
    ): QueueSegmentRef {
        return QueueSegmentRef(
            segmentId = segmentId,
            startOffsetInSegment = startOffsetInSegment,
            length = length
        )
    }

    override fun QueueSegmentRef.copyTo(
        segmentId: String,
        startOffsetInSegment: Int,
        length: Int,
        sortIndex: String
    ): QueueSegmentRef {
        return this.copy(
            segmentId = segmentId,
            startOffsetInSegment = startOffsetInSegment,
            length = length,
            sortIndex = sortIndex
        )
    }
}
