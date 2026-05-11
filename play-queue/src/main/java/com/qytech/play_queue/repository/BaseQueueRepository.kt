package com.qytech.play_queue.repository

import com.qytech.play_queue.data.DeduplicatedQueueRefs
import com.qytech.play_queue.data.PageKey
import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.data.PositionKey
import com.qytech.play_queue.data.QueueMutationResult
import com.qytech.play_queue.data.QueueRemovalResult
import com.qytech.play_queue.data.RemovedGlobalRange
import com.qytech.play_queue.data.RepositorySnapshot
import com.qytech.play_queue.data.SegmentWindowRange
import com.qytech.play_queue.data.containsPosition
import com.qytech.play_queue.data.hasRemoval
import com.qytech.play_queue.data.length
import com.qytech.play_queue.data.removedCountBefore
import com.qytech.play_queue.domain.BaseQueuePositionMapper
import com.qytech.play_queue.domain.IGlobalPositionMapper
import com.qytech.play_queue.local.BasePlayQueueDao
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSegmentPageEntity
import com.qytech.play_queue.local.IQueueSegmentRefEntity
import com.qytech.play_queue.local.IQueueSongEntity
import com.qytech.play_queue.playback.intf.PlayableQueueSource
import com.qytech.play_queue.playback.intf.SegmentQueueActionTarget
import com.qytech.play_queue.playback.intf.SongQueueActionTarget
import com.qytech.play_queue.remote.BaseMusicApi
import com.qytech.play_queue.remote.INetworkPage
import com.qytech.play_queue.remote.INetworkSegment
import com.qytech.play_queue.remote.INetworkSong
import com.qytech.play_queue.util.FractionalIndexing
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
        SEG_REF : IQueueSegmentRefEntity,
        MAPPER : BaseQueuePositionMapper<SEG, SEG_REF>>(
    private val dao: BasePlayQueueDao<QUERY, S, SEG, SEG_PAGE, SEG_REF>,
    private val api: BaseMusicApi<NET_S, NET_SEG, NET_SEG_PAGE>,
    private val pageSize: Int = 50
) : PlayableQueueSource<S, SEG>, SegmentQueueActionTarget<SEG>, SongQueueActionTarget<S> {

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
    private val protectedPlayableSong = MutableStateFlow<ProtectedPlayableSong?>(null)

    override fun setCurrentPlayableSong(song: PlayableSong<S, SEG>?) {
        protectedPlayableSong.value = song?.let { playableSong ->
            ProtectedPlayableSong(
                songId = playableSong.song.id,
                positionKey = PositionKey(
                    segmentId = playableSong.location.segment.id,
                    sortOrderInSegment = playableSong.location.offsetInSegment
                )
            )
        }
    }

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
            dao.observeAllPages(),
            loadingPageKeys
        ) { segments, queueRefs, allPages, loadingKeys ->
            QueueObservation(
                segments = segments,
                queueRefs = queueRefs,
                allPages = allPages,
                loadingKeys = loadingKeys
            )
        }.flatMapLatest { observation ->
            val segments = observation.segments
            val queueRefs = observation.queueRefs
            val mapper = createQueuePositionMapper(segments, queueRefs)
            observeMappedWindow(
                segments = mapper.segments,
                mapper = mapper,
                window = window,
                allPages = observation.allPages,
                loadingKeys = observation.loadingKeys
            )
        }
    }

    private fun observeMappedWindow(
        segments: List<SEG>,
        mapper: MAPPER,
        window: IntRange,
        allPages: List<SEG_PAGE>,
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
                allPagesByKey = allPages.associateBy {
                    PageKey(
                        segmentId = it.segmentId,
                        page = it.page
                    )
                },
                loadingPageKeys = loadingKeys
            )
        }
    }

    /**
     * 现在播放片段
     */
    override suspend fun playSegmentNow(segment: SEG): QueueMutationResult {
        return queueMutex.withLock {
            if (segment.logicalLength() <= 0) return@withLock QueueMutationResult.Noop

            dao.refreshPlayQueue(
                segments = listOf(segment),
                refs = listOf(
                    createQueueRef(
                        segmentId = segment.id,
                        startOffsetInSegment = 0,
                        length = segment.logicalLength()
                    )
                )
            )

            return@withLock QueueMutationResult(
                firstInsertedPosition = 0,
                autoPlayPosition = 0
            )
        }
    }

    /**
     * 现在播放歌曲
     */
    override suspend fun playSongNow(song: S): QueueMutationResult {
        return queueMutex.withLock {
            val segment = song.toSingleSongSegment()
            // 将歌曲中的片段id改成自身的歌曲id，这样改是为了播放队列查找的时候使用
            val singleQueueSong = song.copyTo(
                segmentId = song.id,
                sortOrderInSegment = 0
            )

            dao.refreshPlayQueue(
                segments = listOf(segment),
                refs = listOf(
                    createQueueRef(
                        segmentId = segment.id,
                        startOffsetInSegment = 0,
                        length = 1
                    )
                )
            )

            dao.cachePage(
                segment = segment,
                songs = listOf(singleQueueSong),
                page = createSegmentPageEntity(
                    segmentId = segment.id, // 这里取的是song的id
                    page = 1,
                    isCached = true,
                    cachedCount = 1,
                    error = null
                )
            )

            QueueMutationResult(
                firstInsertedPosition = 0,
                autoPlayPosition = 0
            )
        }
    }

    /**
     * 点击片段中的某一首歌播放
     */
    override suspend fun playSegmentFromOffset(
        segment: SEG,
        offsetInSegment: Int
    ): QueueMutationResult {
        return queueMutex.withLock {
            val totalSize = segment.logicalLength()
            if (totalSize <= 0) return@withLock QueueMutationResult.Noop

            val autoPlayPosition = offsetInSegment.coerceIn(0, totalSize - 1)

            dao.refreshPlayQueue(
                segments = listOf(segment),
                refs = listOf(
                    createQueueRef(
                        segmentId = segment.id,
                        startOffsetInSegment = 0,
                        length = totalSize
                    )
                )
            )

            QueueMutationResult(
                firstInsertedPosition = 0,
                autoPlayPosition = autoPlayPosition
            )
        }
    }

    /**
     * 添加片段到队尾
     */
    override suspend fun addSegmentToTail(segment: SEG): QueueMutationResult {
        return queueMutex.withLock {
            if (segment.logicalLength() <= 0) return@withLock QueueMutationResult.Noop

            val queueSegment = upsertSegmentForQueueAction(segment)
            val allSegments = dao.getSegments()
            val allRefs = dao.getRefs()
            val queueWasEmpty = allRefs.isEmpty()
            val loadedSongs = dao.getSongsBySegmentId(queueSegment.id)
            val currentSegmentRefs = allRefs.filter { it.segmentId == queueSegment.id }
            val withoutSameSegment =
                removeExistingQueuePositionsBySegmentId(allRefs, queueSegment.id)
            val insertedRefs = currentSegmentRefs.ifEmpty {
                listOf(
                    createQueueRef(
                        segmentId = queueSegment.id,
                        startOffsetInSegment = 0,
                        length = queueSegment.logicalLength()
                    )
                )
            }
            val withInsertedSegment = withoutSameSegment.refs + insertedRefs
            val deduplicatedRefs = removeExistingQueuePositionsByLoadedSongs(
                refs = withInsertedSegment,
                loadedSongs = loadedSongs
            )
            val newAllRefs = deduplicatedRefs.refs

            refreshRefsWithStableOrder(newAllRefs)
            val firstInsertedPosition = firstGlobalPositionOfSegment(newAllRefs, queueSegment.id)
                ?: createQueuePositionMapper(allSegments, newAllRefs).totalSize

            QueueMutationResult(
                firstInsertedPosition = firstInsertedPosition,
                autoPlayPosition = firstInsertedPosition.takeIf { queueWasEmpty }
            )
        }
    }

    /**
     * 添加歌曲到队尾
     */
    override suspend fun addSongToTail(song: S): QueueMutationResult {
        return queueMutex.withLock {
            if (protectedPlayableSong.value?.songId == song.id) {
                return@withLock QueueMutationResult.Noop
            }

            val segment = song.toSingleSongSegment()
            val singleQueueSong = song.copyTo(
                segmentId = song.id,
                sortOrderInSegment = 0
            )

            val allSegments = dao.getSegments()
            val allRefs = dao.getRefs()
            val deduplicatedRefs = removeExistingQueuePositionsBySongId(allRefs, song.id)
            val queueWasEmpty = deduplicatedRefs.refs.isEmpty()
            val firstInsertedPosition =
                createQueuePositionMapper(allSegments, deduplicatedRefs.refs).totalSize
            val newAllRefs = deduplicatedRefs.refs + createQueueRef(
                segmentId = segment.id,
                startOffsetInSegment = 0,
                length = 1
            )

            refreshRefsWithStableOrder(newAllRefs)
            dao.cachePage(
                segment = segment,
                songs = listOf(singleQueueSong),
                page = createSegmentPageEntity(
                    segmentId = segment.id, // 这里取的是song的id
                    page = 1,
                    isCached = true,
                    cachedCount = 1,
                    error = null
                )
            )

            QueueMutationResult(
                firstInsertedPosition = firstInsertedPosition,
                autoPlayPosition = firstInsertedPosition.takeIf { queueWasEmpty }
            )
        }
    }

    /**
     * 将片段插入下一首播放
     */
    override suspend fun insertSegmentToNext(
        segment: SEG,
        currentGlobalPosition: Int?
    ): QueueMutationResult {
        return queueMutex.withLock {
            if (segment.logicalLength() <= 0) return@withLock QueueMutationResult.Noop

            val queueSegment = upsertSegmentForQueueAction(segment)
            val allSegments = dao.getSegments()
            val allRefs = dao.getRefs()
            val queueWasEmpty = allRefs.isEmpty()
            val loadedSongs = dao.getSongsBySegmentId(queueSegment.id)
            val currentSegmentRefs = allRefs.filter { it.segmentId == queueSegment.id }
            val withoutSameSegment =
                removeExistingQueuePositionsBySegmentId(allRefs, queueSegment.id)
            val currentTotalSize =
                createQueuePositionMapper(allSegments, withoutSameSegment.refs).totalSize
            val insertPosition = if (queueWasEmpty) {
                0
            } else if (currentGlobalPosition == null) {
                0
            } else {
                adjustedInsertPositionAfterRemoval(
                    currentGlobalPosition = currentGlobalPosition,
                    currentTotalSize = currentTotalSize,
                    removedGlobalRanges = withoutSameSegment.removedGlobalRanges
                )
            }
            val insertedRefs = currentSegmentRefs.ifEmpty {
                listOf(
                    createQueueRef(
                        segmentId = queueSegment.id,
                        startOffsetInSegment = 0,
                        length = queueSegment.logicalLength()
                    )
                )
            }
            val withInsertedSegment = insertQueueRefAtPosition(
                refs = withoutSameSegment.refs,
                position = insertPosition,
                insertedRefs = insertedRefs
            )
            val deduplicatedRefs = removeExistingQueuePositionsByLoadedSongs(
                refs = withInsertedSegment,
                loadedSongs = loadedSongs
            )
            val newAllRefs = deduplicatedRefs.refs

            refreshRefsWithStableOrder(newAllRefs)
            val firstInsertedPosition = firstGlobalPositionOfSegment(newAllRefs, queueSegment.id)
                ?: insertPosition.coerceIn(
                    0,
                    createQueuePositionMapper(allSegments, newAllRefs).totalSize
                )

            QueueMutationResult(
                firstInsertedPosition = firstInsertedPosition,
                autoPlayPosition = firstInsertedPosition.takeIf { queueWasEmpty }
            )
        }
    }

    /**
     * 将歌曲插入下一首播放
     */
    override suspend fun insertSongToNext(
        song: S,
        currentGlobalPosition: Int?
    ): QueueMutationResult {
        return queueMutex.withLock {
            if (protectedPlayableSong.value?.songId == song.id) {
                return@withLock QueueMutationResult.Noop
            }

            val segment = song.toSingleSongSegment()
            val singleQueueSong = song.copyTo(
                segmentId = song.id,
                sortOrderInSegment = 0
            )

            val allSegments = dao.getSegments()
            val allRefs = dao.getRefs()
            val deduplicatedRefs = removeExistingQueuePositionsBySongId(allRefs, song.id)
            val queueWasEmpty = deduplicatedRefs.refs.isEmpty()
            val currentTotalSize =
                createQueuePositionMapper(allSegments, deduplicatedRefs.refs).totalSize
            val insertPosition = if (queueWasEmpty) {
                0
            } else if (currentGlobalPosition == null) {
                0
            } else {
                adjustedInsertPositionAfterRemoval(
                    currentGlobalPosition = currentGlobalPosition,
                    currentTotalSize = currentTotalSize,
                    removedGlobalRanges = deduplicatedRefs.removedGlobalRanges
                )
            }
            val insertedRef = createQueueRef(
                segmentId = segment.id,
                startOffsetInSegment = 0,
                length = 1
            )
            val newAllRefs = insertQueueRefAtPosition(
                refs = deduplicatedRefs.refs,
                position = insertPosition,
                insertedRef = insertedRef
            )

            refreshRefsWithStableOrder(newAllRefs)
            dao.cachePage(
                segment = segment,
                songs = listOf(singleQueueSong),
                page = createSegmentPageEntity(
                    segmentId = segment.id, // 这里取的是song的id
                    page = 1,
                    isCached = true,
                    cachedCount = 1,
                    error = null
                )
            )

            QueueMutationResult(
                firstInsertedPosition = insertPosition,
                autoPlayPosition = insertPosition.takeIf { queueWasEmpty }
            )
        }
    }

    private fun insertQueueRefAtPosition(
        refs: List<SEG_REF>,
        position: Int,
        insertedRef: SEG_REF
    ): List<SEG_REF> {
        return insertQueueRefAtPosition(
            refs = refs,
            position = position,
            insertedRefs = listOf(insertedRef)
        )
    }

    private fun insertQueueRefAtPosition(
        refs: List<SEG_REF>,
        position: Int,
        insertedRefs: List<SEG_REF>
    ): List<SEG_REF> {
        if (insertedRefs.isEmpty()) return refs
        if (refs.isEmpty()) return insertedRefs

        val nextRefs = mutableListOf<SEG_REF>()
        var cursor = 0 // cursor 表示当前遍历到的 ref 在整个队列里的起始位置
        refs.forEachIndexed { index, ref ->
            val endExclusive = cursor + ref.length
            when {
                position <= cursor -> {
                    nextRefs += insertedRefs
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
                    nextRefs += insertedRefs
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
        nextRefs += insertedRefs
        return nextRefs
    }

    private fun removeExistingQueuePositionsBySegmentId(
        refs: List<SEG_REF>,
        segmentId: String
    ): DeduplicatedQueueRefs<SEG_REF> {
        if (refs.isEmpty()) {
            return DeduplicatedQueueRefs(
                refs = refs,
                removedGlobalRanges = emptyList()
            )
        }

        val nextRefs = mutableListOf<SEG_REF>()
        val removedGlobalRanges = mutableListOf<RemovedGlobalRange>()
        var cursor = 0

        refs.forEach { ref ->
            val endExclusive = cursor + ref.length
            if (ref.segmentId == segmentId) {
                removedGlobalRanges += RemovedGlobalRange(cursor, endExclusive)
            } else {
                nextRefs += ref
            }
            cursor = endExclusive
        }

        return DeduplicatedQueueRefs(
            refs = nextRefs,
            removedGlobalRanges = removedGlobalRanges
        )
    }

    private suspend fun removeExistingQueuePositionsBySongId(
        refs: List<SEG_REF>,
        songId: String
    ): DeduplicatedQueueRefs<SEG_REF> {
        return removeExistingQueuePositionsByLoadedSongs(
            refs = refs,
            loadedSongs = dao.getSongsById(songId),
            positionsToKeep = emptySet(),
            extraSongIds = setOf(songId)
        )
    }

    private suspend fun removeExistingQueuePositionsByLoadedSongs(
        refs: List<SEG_REF>,
        loadedSongs: List<S>
    ): DeduplicatedQueueRefs<SEG_REF> {
        val plan = loadedSongs.toDedupPlan()
        return removeExistingQueuePositionsByLoadedSongs(
            refs = refs,
            loadedSongs = loadedSongs,
            positionsToKeep = plan.positionsToKeep,
            extraSongIds = emptySet(),
            loadedOffsetsToRemoveBySegment = plan.offsetsToRemoveBySegment
        )
    }

    private suspend fun removeExistingQueuePositionsByLoadedSongs(
        refs: List<SEG_REF>,
        loadedSongs: List<S>,
        positionsToKeep: Set<PositionKey>,
        extraSongIds: Set<String>,
        loadedOffsetsToRemoveBySegment: Map<String, Set<Int>> = emptyMap()
    ): DeduplicatedQueueRefs<SEG_REF> {
        if (refs.isEmpty() || (loadedSongs.isEmpty() && extraSongIds.isEmpty())) {
            return DeduplicatedQueueRefs(
                refs = refs,
                removedGlobalRanges = emptyList()
            )
        }

        val songIds = (loadedSongs.map { it.id } + extraSongIds).toSet()
        val cachedOffsetsBySegment = dao.getSongsByIds(songIds.toList())
            .asSequence()
            .filter { song ->
                PositionKey(
                    segmentId = song.segmentId,
                    sortOrderInSegment = song.sortOrderInSegment
                ) !in positionsToKeep
            }
            .groupBy(
                keySelector = { it.segmentId },
                valueTransform = { it.sortOrderInSegment }
            )
            .mapValues { (_, offsets) -> offsets.toSet() }

        val nextRefs = mutableListOf<SEG_REF>()
        val removedGlobalRanges = mutableListOf<RemovedGlobalRange>()
        var cursor = 0

        refs.forEach { ref ->
            val refStart = ref.startOffsetInSegment
            val refEndExclusive = ref.startOffsetInSegment + ref.length
            val offsetsToRemove = buildSet {
                cachedOffsetsBySegment[ref.segmentId]
                    ?.asSequence()
                    ?.filter { offset -> offset in refStart until refEndExclusive }
                    ?.forEach { offset -> add(offset) }
                loadedOffsetsToRemoveBySegment[ref.segmentId]
                    ?.asSequence()
                    ?.filter { offset -> offset in refStart until refEndExclusive }
                    ?.forEach { offset -> add(offset) }
                if (ref.segmentId in songIds) {
                    (refStart until refEndExclusive).forEach { offset -> add(offset) }
                }
            }.asSequence()
                .filter { offset ->
                    PositionKey(
                        segmentId = ref.segmentId,
                        sortOrderInSegment = offset
                    ) !in positionsToKeep
                }
                .sorted()
                .toList()

            if (offsetsToRemove.isEmpty()) {
                nextRefs += ref
            } else {
                removedGlobalRanges += offsetsToRemove.toGlobalRanges(
                    cursor = cursor,
                    refStart = refStart
                )
                nextRefs += splitRefExcludingOffsets(ref, offsetsToRemove)
            }

            cursor += ref.length
        }

        return DeduplicatedQueueRefs(
            refs = nextRefs,
            removedGlobalRanges = removedGlobalRanges
                .filter { it.length > 0 }
                .sortedWith(compareBy<RemovedGlobalRange> { it.startInclusive }.thenBy { it.endExclusive })
        )
    }

    private fun List<S>.toDedupPlan(
        protectedSong: ProtectedPlayableSong? = protectedPlayableSong.value
    ): SongPositionDedupPlan {
        val hasProtectedDuplicate = protectedSong != null &&
                any { song -> song.id == protectedSong.songId }
        val latestBySongId = LinkedHashMap<String, S>()
        forEach { song ->
            if (
                protectedSong != null &&
                song.id == protectedSong.songId &&
                song.positionKey() != protectedSong.positionKey
            ) {
                return@forEach
            }
            val current = latestBySongId[song.id]
            if (current == null || song.sortOrderInSegment >= current.sortOrderInSegment) {
                latestBySongId[song.id] = song
            }
        }

        val positionsToCache = latestBySongId.values.toList().toPositionKeys()
        val positionsToKeep = buildSet {
            addAll(positionsToCache)
            if (protectedSong != null && hasProtectedDuplicate) {
                add(protectedSong.positionKey)
            }
        }
        val offsetsToRemoveBySegment = asSequence()
            .filter { song ->
                song.positionKey() !in positionsToCache
            }
            .groupBy(
                keySelector = { song -> song.segmentId },
                valueTransform = { song -> song.sortOrderInSegment }
            )
            .mapValues { (_, offsets) -> offsets.toSet() }

        return SongPositionDedupPlan(
            positionsToKeep = positionsToKeep,
            positionsToCache = positionsToCache,
            offsetsToRemoveBySegment = offsetsToRemoveBySegment
        )
    }

    private fun splitRefExcludingOffsets(
        ref: SEG_REF,
        offsetsToRemove: List<Int>
    ): List<SEG_REF> {
        val nextRefs = mutableListOf<SEG_REF>()
        val refEndExclusive = ref.startOffsetInSegment + ref.length
        var nextStart = ref.startOffsetInSegment

        offsetsToRemove.distinct().sorted().forEach { offset ->
            if (offset < nextStart || offset >= refEndExclusive) return@forEach
            if (nextStart < offset) {
                nextRefs += createQueueRef(
                    segmentId = ref.segmentId,
                    startOffsetInSegment = nextStart,
                    length = offset - nextStart
                )
            }
            nextStart = offset + 1
        }

        if (nextStart < refEndExclusive) {
            nextRefs += createQueueRef(
                segmentId = ref.segmentId,
                startOffsetInSegment = nextStart,
                length = refEndExclusive - nextStart
            )
        }

        return nextRefs
    }

    private fun List<Int>.toGlobalRanges(
        cursor: Int,
        refStart: Int
    ): List<RemovedGlobalRange> {
        if (isEmpty()) return emptyList()

        val ranges = mutableListOf<RemovedGlobalRange>()
        var rangeStart = first()
        var previous = first()

        drop(1).forEach { offset ->
            if (offset == previous + 1) {
                previous = offset
            } else {
                ranges += RemovedGlobalRange(
                    startInclusive = cursor + (rangeStart - refStart),
                    endExclusive = cursor + (previous - refStart) + 1
                )
                rangeStart = offset
                previous = offset
            }
        }

        ranges += RemovedGlobalRange(
            startInclusive = cursor + (rangeStart - refStart),
            endExclusive = cursor + (previous - refStart) + 1
        )

        return ranges
    }

    private fun List<S>.toPositionKeys(): Set<PositionKey> {
        return map { song -> song.positionKey() }.toSet()
    }

    private fun S.positionKey(): PositionKey {
        return PositionKey(
            segmentId = segmentId,
            sortOrderInSegment = sortOrderInSegment
        )
    }

    private data class SongPositionDedupPlan(
        val positionsToKeep: Set<PositionKey>,
        val positionsToCache: Set<PositionKey>,
        val offsetsToRemoveBySegment: Map<String, Set<Int>>
    )

    private data class ProtectedPlayableSong(
        val songId: String,
        val positionKey: PositionKey
    )

    private fun adjustedInsertPositionAfterRemoval(
        currentGlobalPosition: Int,
        currentTotalSize: Int,
        removedGlobalRanges: List<RemovedGlobalRange>
    ): Int {
        val adjustedCurrentPosition = (
                currentGlobalPosition - removedGlobalRanges.removedCountBefore(currentGlobalPosition)
                ).coerceIn(0, currentTotalSize)
        return if (removedGlobalRanges.containsPosition(currentGlobalPosition)) {
            adjustedCurrentPosition
        } else {
            (adjustedCurrentPosition + 1).coerceIn(0, currentTotalSize)
        }
    }

    private fun firstGlobalPositionOfSegment(
        refs: List<SEG_REF>,
        segmentId: String
    ): Int? {
        var cursor = 0
        refs.forEach { ref ->
            if (ref.segmentId == segmentId) return cursor
            cursor += ref.length
        }
        return null
    }

    private suspend fun upsertSegmentForQueueAction(segment: SEG): SEG {
        val existingSegment = dao.getSegment(segment.id)
        val queueSegment = existingSegment?.copyTo(
            name = segment.name,
            coverUrl = segment.coverUrl ?: existingSegment.coverUrl,
            totalCount = segment.totalCount ?: existingSegment.totalCount,
            loadedCount = existingSegment.loadedCount,
            hasMore = existingSegment.hasMore,
            lastError = existingSegment.lastError
        ) ?: segment

        dao.upsertSegment(queueSegment)
        return queueSegment
    }

    private suspend fun deduplicateQueueByLoadedSongs(
        songs: List<S>,
        plan: SongPositionDedupPlan = songs.toDedupPlan()
    ) {
        if (songs.isEmpty()) return

        queueMutex.withLock {
            val refs = dao.getRefs()
            val deduplicatedRefs = removeExistingQueuePositionsByLoadedSongs(
                refs = refs,
                loadedSongs = songs,
                positionsToKeep = plan.positionsToKeep,
                extraSongIds = emptySet(),
                loadedOffsetsToRemoveBySegment = plan.offsetsToRemoveBySegment
            )
            if (deduplicatedRefs.removedGlobalRanges.hasRemoval()) {
                refreshRefsWithStableOrder(deduplicatedRefs.refs)
            }
        }
    }

    private suspend fun refreshRefsWithStableOrder(refs: List<SEG_REF>) {
        if (refs.isEmpty()) {
            dao.refreshRefs(emptyList())
            refreshLoadedCountsForActiveRefs(emptyList())
            return
        }
        val sortIndexes =
            FractionalIndexing.generateNFractionalIndicesBetween(null, null, refs.size)
        val orderedRefs = refs.mapIndexed { index, ref ->
            ref.copyTo(sortIndex = sortIndexes[index])
        }
        dao.refreshRefs(orderedRefs)
        refreshLoadedCountsForActiveRefs(orderedRefs)
    }

    private suspend fun refreshLoadedCountsForActiveRefs() {
        refreshLoadedCountsForActiveRefs(dao.getRefs())
    }

    private suspend fun refreshLoadedCountsForActiveRefs(refs: List<SEG_REF>) {
        val refsBySegment = refs.groupBy { it.segmentId }
        if (refsBySegment.isEmpty()) return

        val segmentsById = dao.getSegments().associateBy { it.id }
        refsBySegment.forEach { (segmentId, segmentRefs) ->
            val segment = segmentsById[segmentId] ?: return@forEach
            val loadedCount = dao.getSongsBySegmentId(segmentId)
                .asSequence()
                .map { it.sortOrderInSegment }
                .distinct()
                .count { offset ->
                    segmentRefs.any { ref ->
                        offset >= ref.startOffsetInSegment &&
                                offset < ref.startOffsetInSegment + ref.length
                    }
                }

            if (segment.loadedCount != loadedCount) {
                dao.upsertSegment(segment.copyTo(loadedCount = loadedCount))
            }
        }
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

    suspend fun removeQueueSegment(segmentId: String): QueueRemovalResult {
        return queueMutex.withLock {
            val refs = dao.getRefs()
            val removedRanges = mutableListOf<RemovedGlobalRange>()
            var cursor = 0

            refs.forEach { ref ->
                val endExclusive = cursor + ref.length
                if (ref.segmentId == segmentId) {
                    removedRanges += RemovedGlobalRange(
                        startInclusive = cursor,
                        endExclusive = endExclusive
                    )
                }
                cursor = endExclusive
            }

            dao.removeQueueSegment(segmentId)

            if (removedRanges.isEmpty()) {
                QueueRemovalResult.none(segmentId)
            } else {
                QueueRemovalResult(
                    removedSegmentId = segmentId,
                    removedRanges = removedRanges
                )
            }
        }
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
            loadPage(
                segmentId = key.segmentId,
                segmentType = key.segmentType,
                page = key.page,
                forceRetry = false
            )
        }
    }

    /**
     * 用户点击错误行时，强制重试某个歌单某一页。
     */
    suspend fun retry(segmentId: String, segmentType: String, page: Int) {
        // forceRetry=true 表示即使这页有错误记录，也允许重新请求。
        loadPage(segmentId, segmentType, page, forceRetry = true)
    }

    /**
     * 获取播放队列的歌曲总数
     */
    override suspend fun totalSize(): Int {
        return createQueuePositionMapper(dao.getSegments(), dao.getRefs()).totalSize
    }

    override suspend fun getSongGlobalPosition(songId: String): Int? {
        return queueMutex.withLock {
            findSongQueueLocationLocked(songId)?.globalPosition
        }
    }

    override suspend fun getSongSegmentId(songId: String): String? {
        return queueMutex.withLock {
            findSongQueueLocationLocked(songId)?.segmentId
        }
    }

    override suspend fun findPreviousPlayableSong(
        globalPosition: Int?,
        wrap: Boolean
    ): PlayableSong<S, SEG>? {
        val total = totalSize()
        if (total <= 0) return null

        val start = ((globalPosition ?: total) - 1).coerceAtMost(total - 1)
        findPlayableSongBackward(start, 0)?.let { return it }

        if (!wrap || globalPosition == null) return null
        return findPlayableSongBackward(total - 1, globalPosition.coerceIn(0, total - 1))
    }

    override suspend fun findNextPlayableSong(
        globalPosition: Int?,
        wrap: Boolean
    ): PlayableSong<S, SEG>? {
        val total = totalSize()
        if (total <= 0) return null

        val start = ((globalPosition ?: -1) + 1).coerceAtLeast(0)
        findPlayableSongForward(start, total - 1)?.let { return it }

        if (!wrap || globalPosition == null) return null
        return findPlayableSongForward(0, globalPosition.coerceIn(0, total - 1))
    }

    private suspend fun findPlayableSongBackward(
        start: Int,
        endInclusive: Int
    ): PlayableSong<S, SEG>? {
        if (start < endInclusive) return null
        for (position in start downTo endInclusive) {
            getPlayableSongAt(position)?.let { return it }
        }
        return null
    }

    private suspend fun findPlayableSongForward(
        start: Int,
        endInclusive: Int
    ): PlayableSong<S, SEG>? {
        if (start > endInclusive) return null
        for (position in start..endInclusive) {
            getPlayableSongAt(position)?.let { return it }
        }
        return null
    }

    private suspend fun findSongQueueLocationLocked(songId: String): SongQueueLocation? {
        val mapper = createQueuePositionMapper(dao.getSegments(), dao.getRefs())
        if (mapper.totalSize <= 0) return null

        val cachedOffsetsBySegment = dao.getSongsById(songId)
            .groupBy(
                keySelector = { song -> song.segmentId },
                valueTransform = { song -> song.sortOrderInSegment }
            )
            .mapValues { (_, offsets) -> offsets.toSet() }

        mapper.rangesFor(0..<mapper.totalSize).forEach { range ->
            val offsetRange = range.offsetStart..range.offsetEnd
            if (range.segment.id == songId && 0 in offsetRange) {
                return SongQueueLocation(
                    globalPosition = range.globalStart - range.offsetStart,
                    segmentId = range.segment.id
                )
            }

            val matchingOffset = cachedOffsetsBySegment[range.segment.id]
                ?.asSequence()
                ?.filter { offset -> offset in offsetRange }
                ?.minOrNull()
                ?: return@forEach

            return SongQueueLocation(
                globalPosition = range.globalStart + (matchingOffset - range.offsetStart),
                segmentId = range.segment.id
            )
        }

        return null
    }

    private data class SongQueueLocation(
        val globalPosition: Int,
        val segmentId: String
    )

    /**
     * 通过 globalPosition 获取 playableSong 对象
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
            segmentType = initialLocation.segment.type,
            page = initialLocation.page,
            forceRetry = forceRetry,
            preferredPositionKey = PositionKey(
                segmentId = initialLocation.segment.id,
                sortOrderInSegment = initialLocation.offsetInSegment
            )
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
     * 预加载页数
     * @param lookBehindPages 当前globalPosition的前几页
     * @param lookAheadPages 当前globalPosition的后几页
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
    private suspend fun loadPage(
        segmentId: String,
        segmentType: String,
        page: Int,
        forceRetry: Boolean,
        preferredPositionKey: PositionKey? = null
    ) {
        val key = PageKey(segmentId = segmentId, segmentType = segmentType, page = page)
        loadMutex.withLock {
            val existing = dao.getPage(segmentId, page)
            if (key in loadingPageKeys.value) return
            if (existing?.isCached == true) return
            if (existing?.error != null && !forceRetry) return
            loadingPageKeys.value += key
        }

        try {
            val segment = dao.getSegments().firstOrNull { it.id == segmentId } ?: return
            val pageResult = api.fetchSongs(segmentId, segmentType, page, segment.pageSize)
            val songs = pageResult.songs.map { song ->
                song.toQueueSongEntity(segmentId)
            }
            val preferredProtectedSong = preferredPositionKey?.let { key ->
                songs.firstOrNull { song -> song.positionKey() == key }?.let { song ->
                    ProtectedPlayableSong(
                        songId = song.id,
                        positionKey = key
                    )
                }
            }
            val protectedSong = protectedPlayableSong.value ?: preferredProtectedSong
            val dedupPlan = songs.toDedupPlan(protectedSong)
            val songsToCache = songs.filter { song ->
                song.positionKey() in dedupPlan.positionsToCache
            }
            val pageCachedCount = songsToCache.size
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
            deduplicateQueueByLoadedSongs(songs, dedupPlan)
            // 用事务写入歌单、歌曲、页状态。歌曲以 id 为唯一键时，这一步会保留最新片段里的歌曲归属。
            dao.cachePage(
                // 更新后的歌单。
                segment = updatedPlaylist,
                // 标记这一页已经缓存成功。
                page = createSegmentPageEntity(
                    segmentId = segmentId,
                    page = page,
                    isCached = true,
                    cachedCount = pageCachedCount,
                    error = null
                ),
                // 这一页歌曲。
                songs = songsToCache
            )
            // 页状态写入后，再统计缓存数量并回填。
            refreshLoadedCountsForActiveRefs()
        } catch (error: Throwable) {
            // 请求或写库失败时，取错误信息。
            val message = error.message ?: "加载失败"
            // 记录这一页失败，UI 可以显示 ErrorRow。
            dao.upsertPage(
                createSegmentPageEntity(
                    segmentId = segmentId,
                    page = page,
                    isCached = false,
                    cachedCount = 0,
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

    protected abstract fun createQueuePositionMapper(
        segments: List<SEG>,
        queueRefs: List<SEG_REF>
    ): MAPPER

    protected abstract fun createSegmentPageEntity(
        segmentId: String,
        page: Int,
        isCached: Boolean,
        cachedCount: Int,
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

    protected abstract fun S.copyTo(
        id: String = this.id,
        segmentId: String = this.segmentId,
        name: String = this.name,
        coverUrl: String? = this.coverUrl,
        singerName: String = this.singerName,
        durationMs: Long = this.durationMs,
        playUrl: String? = this.playUrl,
        sortOrderInSegment: Int = this.sortOrderInSegment
    ): S

    protected abstract fun S.toSingleSongSegment(): SEG

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

private data class QueueObservation<SEG, SEG_PAGE, SEG_REF>(
    val segments: List<SEG>,
    val queueRefs: List<SEG_REF>,
    val allPages: List<SEG_PAGE>,
    val loadingKeys: Set<PageKey>
)
