package com.qytech.play_queue.repository

import com.davidarvelo.fractionalindexing.FractionalIndexing
import com.qytech.play_queue.data.PageKey
import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.data.PositionKey
import com.qytech.play_queue.data.RepositorySnapshot
import com.qytech.play_queue.data.SegmentWindowRange
import com.qytech.play_queue.domain.BaseQueuePositionMapper
import com.qytech.play_queue.domain.IGlobalPositionMapper
import com.qytech.play_queue.local.BasePlayQueueDao
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSegmentPageEntity
import com.qytech.play_queue.local.IQueueSegmentRefEntity
import com.qytech.play_queue.local.IQueueSongEntity
import com.qytech.play_queue.playback.intf.PlayableQueueSource
import com.qytech.play_queue.remote.BaseMusicApi
import com.qytech.play_queue.remote.INetworkPage
import com.qytech.play_queue.remote.INetworkSegment
import com.qytech.play_queue.remote.INetworkSong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class BaseQueueMusicRepository<
        QUERY,
        S : IQueueSongEntity,
        NET_S : INetworkSong,
        SEG : IQueueSegmentEntity,
        NET_SEG : INetworkSegment,
        SEG_PAGE : IQueueSegmentPageEntity,
        NET_SEG_PAGE : INetworkPage<NET_S, NET_SEG>,
        SEG_REF: IQueueSegmentRefEntity,
        MAPPER : BaseQueuePositionMapper<SEG, SEG_REF>>(
    private val dao: BasePlayQueueDao<QUERY, S, SEG, SEG_PAGE, SEG_REF>,
    private val api: BaseMusicApi<NET_S, NET_SEG, NET_SEG_PAGE>,
    private val pageSize: Int = 50
) : PlayableQueueSource<S, SEG> {

    init {
        require(pageSize in 1..50) {
            "pageSize must be in range 1..50, but was $pageSize"
        }
    }

    private val _visibleWindow = MutableStateFlow(0..120)
    val visibleWindow = _visibleWindow.asStateFlow()

    private val loadMutex = Mutex()
    private val queueMutex = Mutex()
    private val loadingPageKeys = MutableStateFlow<Set<PageKey>>(emptySet())

    fun resetVisibleWindow() {
        _visibleWindow.update {
            0..120
        }
    }

    fun updateVisibleWindow(range: IntRange) {
        _visibleWindow.update {
            range
        }
    }

    /**
     * 播放队列窗口变化，拿到ui层所需要的内容
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeQueueWindow(window: IntRange): Flow<RepositorySnapshot<S, SEG, SEG_PAGE, SEG_REF>> {
        return combine(
            dao.observeSegments(),
            dao.observeRefs(),
            loadingPageKeys
        ) { segments, queueRefs, loadingKeys ->
            Triple(segments, queueRefs, loadingKeys)
        }.flatMapLatest { (segments, queueRefs, loadingKeys) ->
            val mapper = createQueuePositionMapper(segments, queueRefs)
            observeMappedWindow(
                segments = mapper.segments,
                mapper = mapper,
                window = window,
                loadingKeys = loadingKeys
            )
        }
    }

    private fun observeMappedWindow(
        segments: List<SEG>,
        mapper: MAPPER,
        window: IntRange,
        loadingKeys: Set<PageKey>
    ): Flow<RepositorySnapshot<S, SEG, SEG_PAGE, SEG_REF>> {
        if (mapper.totalSize <= 0) {
            return flowOf(RepositorySnapshot.empty(window))
        }

        val ranges = mapper.rangesFor(window)

        return combine(
            dao.observeSongsInWindow(buildSongsWindowQuery(ranges)),
            dao.observePagesInWindow(buildPagesWindowQuery(ranges))
        ) { songs, pages ->
            RepositorySnapshot(
                positionMapper = mapper,
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

    suspend fun setPlayQueueFirst(segment: SEG) {
        queueMutex.withLock {
            if (segment.logicalLength() <= 0) return@withLock

            dao.refreshPlayQueue(
                segments = listOf(segment),
                refs = listOf(createQueueRef(
                    segmentId = segment.id,
                    startOffsetInSegment = 0,
                    length = segment.logicalLength()
                ))
            )
        }
    }


    suspend fun addSegmentToTail(segment: SEG) {
        queueMutex.withLock {
            if (segment.logicalLength() <= 0) return@withLock

            val dbSegment = dao.getSegment(segment.id)
            if (dbSegment == null) {
                dao.upsertSegment(segment)
            }
            // 队列中已经存在了
            if (dao.getRefsBySegmentId(segment.id).isNotEmpty()) return@withLock

//            val allSegments = dao.getSegments()
//            val allRefs = dao.getRefs()
//            val queueWasEmpty = allRefs.isEmpty()
//            val currentTotalSize = createQueuePositionMapper(allSegments, allRefs).totalSize

            dao.upsertRef(createQueueRef(
                segmentId = segment.id,
                startOffsetInSegment = 0,
                length = segment.logicalLength()
            ))
        }
    }

    suspend fun insertSegmentToNext(
        segment: SEG,
        currentGlobalPosition: Int?
    ) {
        queueMutex.withLock {
            if (segment.logicalLength() <= 0) return@withLock

            val dbSegment = dao.getSegment(segment.id)
            if (dbSegment == null) {
                dao.upsertSegment(segment)
            }
            // 队列中已经存在了
            if (dao.getRefsBySegmentId(segment.id).isNotEmpty()) return@withLock

            val allSegments = dao.getSegments()
            val allRefs = dao.getRefs()
            val queueWasEmpty = allRefs.isEmpty()
            val currentTotalSize = createQueuePositionMapper(allSegments, allRefs).totalSize
            val insertPosition = if (queueWasEmpty) {
                0
            } else if (currentGlobalPosition == null) {
                0
            } else {
                (currentGlobalPosition + 1).coerceIn(0, currentTotalSize)
            }
            val insertedRef = createQueueRef(
                segmentId = segment.id,
                startOffsetInSegment = 0,
                length = segment.logicalLength()
            )
            val newAllRefs = insertQueueRefAtPosition(
                refs = allRefs,
                position = insertPosition,
                insertedRef = insertedRef
            )
            val sortIndexes = FractionalIndexing.generateNFractionalIndicesBetween(null, null, newAllRefs.size)
            dao.refreshRefs(
                newAllRefs.mapIndexed { index, it ->
                    it.copyTo(
                        sortIndex = sortIndexes[index]
                    )
                }
            )
        }
    }

    private fun insertQueueRefAtPosition(
        refs: List<SEG_REF>,
        position: Int,
        insertedRef: SEG_REF
    ): List<SEG_REF> {
        if (refs.isEmpty()) return listOf(insertedRef)

        val nextRefs = mutableListOf<SEG_REF>()
        var cursor = 0 // cursor 表示当前遍历到的 ref 在整个队列里的起始位置
        refs.forEachIndexed { index, ref ->
            val endExclusive = cursor + ref.length
            when {
                position <= cursor -> {
                    nextRefs += insertedRef
                    nextRefs += refs.drop(index)
                    return nextRefs
                }

                position < endExclusive -> {
                    val beforeLength = position - cursor
                    val afterLength = endExclusive - position
                    if (beforeLength > 0) {
                        nextRefs += createQueueRef(
                            segmentId = ref.segmentId,
                            startOffsetInSegment = ref.startOffsetInSegment,
                            length = beforeLength
                        )
                    }
                    nextRefs += insertedRef
                    if (afterLength > 0) {
                        nextRefs += createQueueRef(
                            segmentId = ref.segmentId,
                            startOffsetInSegment = ref.startOffsetInSegment + beforeLength,
                            length = afterLength
                        )
                    }
                    nextRefs += refs.drop(index + 1)
                    return nextRefs
                }

                else -> {
                    nextRefs += ref
                    cursor = endExclusive
                }
            }
        }
        nextRefs += insertedRef
        return nextRefs
    }

//    suspend fun getSegmentFirstSortIndex(): String {
//        return FractionalIndexing.generateFractionalIndexBetween(
//            null,
//            dao.getSegmentFirstSortIndex()
//        )
//    }
//
//    suspend fun getSegmentLastSortIndex(): String {
//        return FractionalIndexing.generateFractionalIndexBetween(
//            dao.getSegmentLastSortIndex(),
//            null
//        )
//    }
//
//    suspend fun getSegmentPreviousSortIndex(curSortIndex: String): String {
//        return FractionalIndexing.generateFractionalIndexBetween(dao.getSegmentPreviousSortIndex(curSortIndex), curSortIndex)
//    }
//
//    suspend fun getSegmentNextSortIndex(curSortIndex: String): String {
//        return FractionalIndexing.generateFractionalIndexBetween(curSortIndex, dao.getSegmentNextSortIndex(curSortIndex))
//    }

    suspend fun removeQueueSegment(segmentId: String) {
        dao.removeQueueSegment(segmentId)
    }

    // preloadWindow：根据当前内存窗口预加载它覆盖到的所有页。
    suspend fun preloadQueueWindow(window: IntRange) {
        preloadMappedWindow(
            mapper = createQueuePositionMapper(dao.getSegments(), dao.getRefs()),
            window = window
        )
    }

    private suspend fun preloadMappedWindow(
        mapper: IGlobalPositionMapper<SEG>,
        window: IntRange
    ) {
        val pageKeys = mapper.rangesFor(window)
            .flatMap { range ->
                (range.firstPage..range.lastPage).map { page ->
                    PageKey(segmentId = range.segment.id, page = page)
                }
            }
            .distinct()

        pageKeys.forEach { key ->
            loadPage(key.segmentId, key.page, forceRetry = false)
        }
    }

    /**
     * 用户点击错误行时，强制重试某个歌单某一页。
     */
    suspend fun retry(playlistId: String, page: Int) {
        // forceRetry=true 表示即使这页有错误记录，也允许重新请求。
        loadPage(playlistId, page, forceRetry = true)
    }

    /**
     * 获取播放队列的歌曲总数
     */
    override suspend fun totalSize(): Int {
        return createQueuePositionMapper(dao.getSegments(), dao.getRefs()).totalSize
    }

    /**
     * 点击播放队列的歌曲播放
     */
    override suspend fun getPlayableSongAt(
        globalPosition: Int,
        forceRetry: Boolean
    ): PlayableSong<S, SEG>? {
//        ensurePlaylists()
        val initialMapper = createQueuePositionMapper(dao.getSegments(), dao.getRefs())
        val initialLocation = initialMapper.locate(globalPosition) ?: return null

        loadPage(
            segmentId = initialLocation.segment.id,
            page = initialLocation.page,
            forceRetry = forceRetry
        )

        val refreshedMapper = createQueuePositionMapper(dao.getSegments(), dao.getRefs())
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

    /**
     * globalPosition是给看见的播放队列使用的
     */
    override suspend fun preloadPlaybackAround(
        globalPosition: Int,
        lookBehindPages: Int,
        lookAheadPages: Int
    ) {
//        ensurePlaylists()
        val mapper = createQueuePositionMapper(dao.getSegments(), dao.getRefs())
        val location = mapper.locate(globalPosition) ?: return
        val pageSize = location.segment.pageSize.coerceAtLeast(1)
        val start = (globalPosition - pageSize * lookBehindPages.coerceAtLeast(0))
            .coerceAtLeast(0)
        val end = (globalPosition + pageSize * lookAheadPages.coerceAtLeast(0))
            .coerceAtMost((mapper.totalSize - 1).coerceAtLeast(0))

        preloadQueueWindow(start..end)
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
                song.toQueueSongEntity(segmentId)
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

    protected abstract fun createQueueRef(
        segmentId: String,
        startOffsetInSegment: Int,
        length: Int
    ): SEG_REF

    protected abstract fun SEG_REF.copyTo(
        segmentId: String = this.segmentId,
        startOffsetInSegment: Int = this.startOffsetInSegment,
        length: Int = this.length,
        sortIndex: String = this.sortIndex
    ): SEG_REF

    private fun SEG.logicalLength(): Int {
        return totalCount ?: (loadedCount + pageSize)
    }

    protected abstract fun createQueuePositionMapper(segments: List<SEG>, queueRefs: List<SEG_REF>): MAPPER

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

    protected abstract fun NET_S.toQueueSongEntity(segmentId: String): S

    protected abstract fun NET_SEG.toQueueSegmentEntity(
        loadedCount: Int,
        hasMore: Boolean,
        lastError: String?,
        sortIndex: String
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