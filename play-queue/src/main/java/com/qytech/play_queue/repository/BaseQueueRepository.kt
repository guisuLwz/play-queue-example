package com.qytech.play_queue.repository

import com.qytech.play_queue.data.PageKey
import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.data.PositionKey
import com.qytech.play_queue.data.RepositorySnapshot
import com.qytech.play_queue.data.SegmentWindowRange
import com.qytech.play_queue.domain.BaseGlobalPositionMapper
import com.qytech.play_queue.local.BaseMusicDao
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSegmentPageEntity
import com.qytech.play_queue.local.IQueueSongEntity
import com.qytech.play_queue.playback.intf.PlayableQueueSource
import com.qytech.play_queue.remote.BaseMusicApi
import com.qytech.play_queue.remote.INetworkPage
import com.qytech.play_queue.remote.INetworkSegment
import com.qytech.play_queue.remote.INetworkSong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class BaseMusicRepository<
        QUERY,
        S : IQueueSongEntity,
        NET_S : INetworkSong,
        SEG : IQueueSegmentEntity,
        NET_SEG : INetworkSegment,
        SEG_PAGE : IQueueSegmentPageEntity,
        NET_SEG_PAGE : INetworkPage<NET_S, NET_SEG>,
        MAPPER : BaseGlobalPositionMapper<SEG>>(
    private val dao: BaseMusicDao<QUERY, S, SEG, SEG_PAGE>,
    private val api: BaseMusicApi<NET_S, NET_SEG, NET_SEG_PAGE>,
    private val pageSize: Int = 50
) : PlayableQueueSource<S, SEG> {

    init {
        require(pageSize in 1..50) {
            "pageSize must be in range 1..50, but was $pageSize"
        }
    }

    private val loadMutex = Mutex()

    private val loadingPageKeys = MutableStateFlow<Set<PageKey>>(emptySet())

    /**
     * 播放队列窗口变化，拿到ui层所需要的内容
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWindow(window: IntRange): Flow<RepositorySnapshot<S, SEG, SEG_PAGE>> {
        return combine(
            dao.observeSegments(),
            loadingPageKeys
        ) { segments, loadingKeys ->
            segments to loadingKeys
        }.flatMapLatest { (segments, loadingKeys) ->
            if (segments.isEmpty()) {
                flowOf(RepositorySnapshot.empty(window))
            } else {
                val mapper = createGlobalPositionMapper(segments)
                val ranges = mapper.rangesFor(window)
                combine(
                    dao.observeSongsInWindow(buildSongsWindowQuery(ranges)),
                    dao.observePagesInWindow(buildPagesWindowQuery(ranges))
                ) { songs, pages ->
                    RepositorySnapshot<S, SEG, SEG_PAGE>(
                        positionMapper = createGlobalPositionMapper(segments),
                        segments = segments,
                        totalSize = mapper.totalSize,
                        window = window,
                        ranges = ranges,
                        songsByPositionKey = songs.associateBy {
                            PositionKey(
                                segmentId = it.segmentId,
                                sortOrderInSegment = it.sortOrderInSegment
                            )
                        },
                        pagesByKey = pages.associateBy {
                            PageKey(
                                segmentId = it.segmentId,
                                page = it.page
                            )
                        },
                        loadingPageKeys = loadingKeys
                    )
                }
            }
        }
    }

    suspend fun upsertSegment(segment: SEG) {
        dao.upsertSegment(segment)
    }

    suspend fun upsertSegments(segments: List<SEG>) {
        dao.upsertSegments(segments)
    }

    /**
     * 打开播放列表的第一次加载，加载播放列表第一页内容
     */
    suspend fun loadInitialPages() {

    }

    // preloadWindow：根据当前内存窗口预加载它覆盖到的所有页。
    suspend fun preloadWindow(window: IntRange) {
        val mapper = createGlobalPositionMapper(dao.getSegments())
        mapper.rangesFor(window).forEach { range ->
            // 这个 range 可能跨多个页，所以遍历 firstPage..lastPage。
            for (page in range.firstPage..range.lastPage) {
                // 加载对应页；已缓存/正在加载/失败未重试都会在 loadPage 内部拦住。
                loadPage(range.segment.id, page, forceRetry = false)
            }
        }
    }

    /**
     * retry：用户点击错误行时，强制重试某个歌单某一页。
     */
    suspend fun retry(playlistId: String, page: Int) {
        // forceRetry=true 表示即使这页有错误记录，也允许重新请求。
        loadPage(playlistId, page, forceRetry = true)
    }

    override suspend fun totalSize(): Int {
//        ensurePlaylists()
        return createGlobalPositionMapper(dao.getSegments()).totalSize
    }

    override suspend fun getPlayableSongAt(
        globalPosition: Int,
        forceRetry: Boolean
    ): PlayableSong<S, SEG>? {
//        ensurePlaylists()
        val initialMapper = createGlobalPositionMapper(dao.getSegments())
        val initialLocation = initialMapper.locate(globalPosition) ?: return null

        loadPage(
            segmentId = initialLocation.segment.id,
            page = initialLocation.page,
            forceRetry = forceRetry
        )

        val refreshedMapper = createGlobalPositionMapper(dao.getSegments())
        val location = refreshedMapper.locate(globalPosition) ?: return null
        val song = dao.getSongAtPosition(
            segmentId = location.segment.id,
            sortOrderInSegment = location.offsetInSegment
        ) ?: return null

        return PlayableSong(
            globalPosition = globalPosition,
            location = location,
            song = song
        )
    }

    override suspend fun preloadPlaybackAround(
        globalPosition: Int,
        lookBehindPages: Int,
        lookAheadPages: Int
    ) {
//        ensurePlaylists()
        val mapper = createGlobalPositionMapper(dao.getSegments())
        val location = mapper.locate(globalPosition) ?: return
        val pageSize = location.segment.pageSize.coerceAtLeast(1)
        val start = (globalPosition - pageSize * lookBehindPages.coerceAtLeast(0))
            .coerceAtLeast(0)
        val end = (globalPosition + pageSize * lookAheadPages.coerceAtLeast(0))
            .coerceAtMost((mapper.totalSize - 1).coerceAtLeast(0))

        preloadWindow(start..end)
    }

    // loadPage：加载某个歌单某一页。
    private suspend fun loadPage(segmentId: String, page: Int, forceRetry: Boolean) {
        val key = PageKey(segmentId = segmentId, page = page)
        loadMutex.withLock {
            val existing = dao.getPage(segmentId, page)
            if (key in loadingPageKeys.value) return
            if (existing?.isCached == true) return
            if (existing?.error != null && !forceRetry) return
            loadingPageKeys.value += key
        }

        try {
            val segment = dao.getSegments().firstOrNull { it.id == segmentId } ?: return
            val pageResult = api.fetchSongs(segmentId, page, segment.pageSize)
            val songs = pageResult.songs.map { song ->
                song.toSongEntity(segmentId)
            }
            // 先构造更新后的歌单，但 loadedCount 暂时保留旧值。
            val updatedPlaylist = segment.copyTo(
                // 标题以服务端返回为准。
                name = pageResult.segment.name,
                // 封面以服务端返回为准。
                coverUrl = pageResult.segment.coverUrl,
                // 总数以服务端返回为准。
                totalCount = pageResult.segment.totalCount,
                // 这里先不计算缓存数，等歌曲写入后再 COUNT(*)，避免并发时算错。
                loadedCount = segment.loadedCount,
                // 是否还有更多页。
                hasMore = pageResult.hasMore,
                // 成功后清掉歌单错误。
                lastError = null
            )
            // 用事务写入歌单、歌曲、页状态。
            dao.cachePage(
                // 更新后的歌单。
                segment = updatedPlaylist,
                // 标记这一页已经缓存成功。
                page = createSegmentPageEntity(
                    segmentId = segmentId,
                    page = page,
                    isCached = true,
                    error = null
                ),
                // 这一页歌曲。
                songs = songs
            )
            // 歌曲写入后，再统计真实缓存数量并回填。
            dao.upsertSegment(updatedPlaylist.copyTo(loadedCount = dao.countCachedSongs(segmentId)))
        } catch (error: Throwable) {
            // 请求或写库失败时，取错误信息。
            val message = error.message ?: "加载失败"
            // 记录这一页失败，UI 可以显示 ErrorRow。
            dao.upsertPage(
                createSegmentPageEntity(
                    segmentId = segmentId,
                    page = page,
                    isCached = false,
                    error = message
                )
            )
            // 同时把错误写到歌单上，顶部状态条也能看到。
            dao.getSegments().firstOrNull { it.id == segmentId }?.let { playlist ->
                dao.upsertSegment(playlist.copyTo(lastError = message))
            }
        } finally {
            // 无论成功失败，都要把这一页从正在加载集合中移除。
            loadMutex.withLock {
                loadingPageKeys.value -= key
            }
        }
    }

    protected abstract fun createGlobalPositionMapper(segments: List<SEG>): MAPPER

    protected abstract fun createSegmentPageEntity(
        segmentId: String,
        page: Int,
        isCached: Boolean,
        error: String?
    ): SEG_PAGE

    protected abstract fun SEG.copyTo(
        name: String = this.name,
        coverUrl: String? = this.coverUrl,
        totalCount: Int? = this.totalCount,
        loadedCount: Int = this.loadedCount,
        hasMore: Boolean = this.hasMore,
        lastError: String? = this.lastError
    ): SEG

    protected abstract fun NET_S.toSongEntity(segmentId: String): S

    protected abstract fun NET_SEG.toSegmentEntity(
        loadedCount: Int,
        hasMore: Boolean,
        lastError: String?
    ): SEG

    /**
     * buildSongsWindowQuery：构造“查窗口内歌曲”的 SQL。
     */
    protected abstract fun buildSongsWindowQuery(ranges: List<SegmentWindowRange<SEG>>): QUERY

    /**
     * buildPagesWindowQuery：构造“查窗口内页状态”的 SQL。
     */
    protected abstract fun buildPagesWindowQuery(ranges: List<SegmentWindowRange<SEG>>): QUERY

    /**
     * buildWindowQuery：根据 ranges 动态拼出 SQL。
     */
    protected abstract fun buildWindowQuery(
        select: String,
        orderBy: String,
        ranges: List<SegmentWindowRange<SEG>>,
        pageRange: Boolean
    ): QUERY
}