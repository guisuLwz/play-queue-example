package com.qytech.play_queue.repository

import com.qytech.play_queue.data.SegmentWindowRange
import com.qytech.play_queue.domain.BaseQueuePositionMapper
import com.qytech.play_queue.local.BasePlayQueueDao
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSegmentPageEntity
import com.qytech.play_queue.local.IQueueSegmentRefEntity
import com.qytech.play_queue.local.IQueueSongEntity
import com.qytech.play_queue.remote.BaseMusicApi
import com.qytech.play_queue.remote.INetworkPage
import com.qytech.play_queue.remote.INetworkSegment
import com.qytech.play_queue.remote.INetworkSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseQueueMusicRepositoryDedupeTest {

    @Test
    fun insert_segment_to_next_deduplicates_refs_songs_and_cached_counts() = runBlocking {
        val dao = FakeQueueDao()
        val repository = createRepository(dao)

        repository.playSegmentNow(testSegment("A"))
        assertNotNull(repository.getPlayableSongAt(0))

        repository.insertSegmentToNext(testSegment("B"), currentGlobalPosition = 0)
        assertNotNull(repository.getPlayableSongAt(1))

        assertDuplicateAndBrokenPlaylistState(repository, dao)
    }

    @Test
    fun add_segment_to_tail_deduplicates_refs_songs_and_cached_counts() = runBlocking {
        val dao = FakeQueueDao()
        val repository = createRepository(dao)

        repository.playSegmentNow(testSegment("A"))
        assertNotNull(repository.getPlayableSongAt(0))

        repository.addSegmentToTail(testSegment("B"))
        assertNotNull(repository.getPlayableSongAt(PAGE_SIZE))

        assertDuplicateAndBrokenPlaylistState(repository, dao)
    }

    @Test
    fun insert_current_segment_to_next_keeps_sanitized_playlist_once() = runBlocking {
        val dao = FakeQueueDao()
        val repository = createRepository(dao)

        repository.playSegmentNow(testSegment("B"))
        assertNotNull(repository.getPlayableSongAt(0))

        repository.insertSegmentToNext(testSegment("B"), currentGlobalPosition = 0)

        assertCurrentBrokenPlaylistState(repository, dao)
    }

    @Test
    fun add_current_segment_to_tail_keeps_sanitized_playlist_once() = runBlocking {
        val dao = FakeQueueDao()
        val repository = createRepository(dao)

        repository.playSegmentNow(testSegment("B"))
        assertNotNull(repository.getPlayableSongAt(0))

        repository.addSegmentToTail(testSegment("B"))

        assertCurrentBrokenPlaylistState(repository, dao)
    }

    @Test
    fun insert_current_song_to_next_keeps_one_song() = runBlocking {
        val dao = FakeQueueDao()
        val repository = createRepository(dao)
        val song = testSong()

        repository.playSongNow(song)
        repository.insertSongToNext(song, currentGlobalPosition = 0)

        assertSingleSongQueue(repository, dao)
    }

    @Test
    fun add_current_song_to_tail_keeps_one_song() = runBlocking {
        val dao = FakeQueueDao()
        val repository = createRepository(dao)
        val song = testSong()

        repository.playSongNow(song)
        repository.addSongToTail(song)

        assertSingleSongQueue(repository, dao)
    }

    private suspend fun assertDuplicateAndBrokenPlaylistState(
        repository: TestQueueRepository,
        dao: FakeQueueDao
    ) {
        val refs = dao.getRefs()

        assertEquals(36, repository.totalSize())
        assertEquals(36, refs.sumOf { it.length })

        assertFalse(refs.containsPosition("A", 0))
        assertFalse(refs.containsPosition("A", 10))
        assertFalse(refs.containsPosition("A", 15))
        assertFalse(refs.containsPosition("B", 2))
        assertTrue(refs.containsPosition("B", BROKEN_OFFSET))

        assertEquals(17, dao.getSegment("A")?.loadedCount)
        assertEquals(18, dao.getSegment("B")?.loadedCount)
        assertEquals(35, listOf("A", "B").sumOf { dao.getSegment(it)?.loadedCount ?: 0 })

        assertEquals(20, dao.getPage("A", 1)?.cachedCount)
        assertEquals(18, dao.getPage("B", 1)?.cachedCount)

        assertNull(dao.getSongAtPosition("B", 2))
        assertNull(dao.getSongAtPosition("B", BROKEN_OFFSET))

        assertEquals(35, dao.allSongs().size)
        assertEquals(
            TestSong(
                id = SHARED_C,
                segmentId = "B",
                name = "B-8",
                singerName = "artist",
                sortOrderInSegment = 8
            ),
            dao.getSongsById(SHARED_C).single()
        )
    }

    private suspend fun assertCurrentBrokenPlaylistState(
        repository: TestQueueRepository,
        dao: FakeQueueDao
    ) {
        val refs = dao.getRefs()

        assertEquals(19, repository.totalSize())
        assertEquals(19, refs.sumOf { it.length })
        assertTrue(refs.all { ref -> ref.segmentId == "B" })
        assertFalse(refs.containsPosition("B", 2))
        assertTrue(refs.containsPosition("B", BROKEN_OFFSET))

        assertEquals(18, dao.getSegment("B")?.loadedCount)
        assertEquals(18, dao.getPage("B", 1)?.cachedCount)
        assertEquals(18, dao.allSongs().size)
        assertNull(dao.getSongAtPosition("B", 2))
        assertNull(dao.getSongAtPosition("B", BROKEN_OFFSET))
        assertEquals("B", dao.getSongsById(SHARED_C).single().segmentId)
        assertEquals(8, dao.getSongsById(SHARED_C).single().sortOrderInSegment)
    }

    private suspend fun assertSingleSongQueue(
        repository: TestQueueRepository,
        dao: FakeQueueDao
    ) {
        val refs = dao.getRefs()
        val song = dao.getSongsById(SOLO_SONG_ID).single()

        assertEquals(1, repository.totalSize())
        assertEquals(1, refs.size)
        assertEquals(SOLO_SONG_ID, refs.single().segmentId)
        assertEquals(0, refs.single().startOffsetInSegment)
        assertEquals(1, refs.single().length)
        assertEquals(1, dao.allSongs().size)
        assertEquals(SOLO_SONG_ID, song.segmentId)
        assertEquals(0, song.sortOrderInSegment)
        assertEquals(1, dao.getSegment(SOLO_SONG_ID)?.loadedCount)
        assertEquals(1, dao.getPage(SOLO_SONG_ID, 1)?.cachedCount)
    }

    private fun createRepository(dao: FakeQueueDao): TestQueueRepository {
        return TestQueueRepository(
            dao = dao,
            api = FakeMusicApi(
                pages = mapOf(
                    "A" to playlistASongs(),
                    "B" to playlistBSongs()
                )
            )
        )
    }
}

private const val PAGE_SIZE = 20
private const val BROKEN_OFFSET = 7
private const val SHARED_C = "shared-c"
private const val SHARED_D = "shared-d"
private const val SHARED_F = "shared-f"
private const val SOLO_SONG_ID = "solo-song"

private fun testSegment(id: String) = TestSegment(
    id = id,
    name = id,
    totalCount = PAGE_SIZE
)

private fun playlistASongs(): List<TestNetworkSong> {
    return (0 until PAGE_SIZE).map { offset ->
        TestNetworkSong(
            id = when (offset) {
                0 -> SHARED_C
                10 -> SHARED_D
                15 -> SHARED_F
                else -> "a-$offset"
            },
            name = "A-$offset",
            sortOrderInSegment = offset
        )
    }
}

private fun playlistBSongs(): List<TestNetworkSong> {
    return (0 until PAGE_SIZE)
        .filterNot { offset -> offset == BROKEN_OFFSET }
        .map { offset ->
            TestNetworkSong(
                id = when (offset) {
                    2 -> SHARED_C
                    5 -> SHARED_D
                    8 -> SHARED_C
                    10 -> SHARED_F
                    else -> "b-$offset"
                },
                name = "B-$offset",
                sortOrderInSegment = offset
            )
        }
}

private fun testSong() = TestSong(
    id = SOLO_SONG_ID,
    segmentId = "source",
    name = "Solo Song",
    singerName = "artist",
    sortOrderInSegment = 3
)

private fun List<TestRef>.containsPosition(segmentId: String, offset: Int): Boolean {
    return any { ref ->
        ref.segmentId == segmentId &&
                offset >= ref.startOffsetInSegment &&
                offset < ref.startOffsetInSegment + ref.length
    }
}

private data class TestSegment(
    override val id: String,
    override val type: String = "playlist",
    override val name: String,
    override val coverUrl: String? = null,
    override val totalCount: Int?,
    override val loadedCount: Int = 0,
    override val pageSize: Int = PAGE_SIZE,
    override val hasMore: Boolean = false,
    override val lastError: String? = null,
    override val sortIndex: String = ""
) : IQueueSegmentEntity

private data class TestSong(
    override val id: String,
    override val segmentId: String,
    override val name: String,
    override val coverUrl: String? = null,
    override val singerName: String,
    override val durationMs: Long = 180_000,
    override val playUrl: String? = null,
    override val sortOrderInSegment: Int
) : IQueueSongEntity

private data class TestPage(
    override val segmentId: String,
    override val page: Int,
    override val isCached: Boolean,
    override val cachedCount: Int,
    override val error: String?
) : IQueueSegmentPageEntity

private data class TestRef(
    override val id: Long = 0,
    override val segmentId: String,
    override val startOffsetInSegment: Int,
    override val length: Int,
    override val sortIndex: String = ""
) : IQueueSegmentRefEntity

private data class TestNetworkSegment(
    override val id: String,
    override val name: String,
    override val type: String = "playlist",
    override val coverUrl: String? = null,
    override val totalCount: Int? = PAGE_SIZE
) : INetworkSegment

private data class TestNetworkSong(
    override val id: String,
    override val name: String,
    override val coverUrl: String? = null,
    override val singerName: String = "artist",
    override val durationMs: Long = 180_000,
    override val playUrl: String? = null,
    override val sortOrderInSegment: Int
) : INetworkSong

private data class TestNetworkPage(
    override val segment: TestNetworkSegment,
    override val page: Int,
    override val pageSize: Int,
    override val songs: List<TestNetworkSong>,
    override val hasMore: Boolean
) : INetworkPage<TestNetworkSong, TestNetworkSegment>

private class TestPositionMapper(
    queueRefs: List<TestRef>,
    segmentsById: Map<String, TestSegment>
) : BaseQueuePositionMapper<TestSegment, TestRef>(queueRefs, segmentsById)

private class FakeMusicApi(
    private val pages: Map<String, List<TestNetworkSong>>
) : BaseMusicApi<TestNetworkSong, TestNetworkSegment, TestNetworkPage>() {
    override suspend fun fetchSongs(
        segmentId: String,
        segmentType: String,
        page: Int,
        pageSize: Int
    ): INetworkPage<TestNetworkSong, TestNetworkSegment> {
        val songs = if (page == 1) pages[segmentId].orEmpty() else emptyList()
        return TestNetworkPage(
            segment = TestNetworkSegment(
                id = segmentId,
                name = segmentId,
                type = segmentType,
                totalCount = PAGE_SIZE
            ),
            page = page,
            pageSize = pageSize,
            songs = songs,
            hasMore = false
        )
    }
}

private class TestQueueRepository(
    private val dao: FakeQueueDao,
    api: FakeMusicApi
) : BaseQueueMusicRepository<
        Unit,
        TestSong,
        TestNetworkSong,
        TestSegment,
        TestNetworkSegment,
        TestPage,
        TestNetworkPage,
        TestRef,
        TestPositionMapper>(
    dao = dao,
    api = api,
    pageSize = PAGE_SIZE
) {
    override fun createQueueRef(
        segmentId: String,
        startOffsetInSegment: Int,
        length: Int
    ): TestRef {
        return TestRef(
            segmentId = segmentId,
            startOffsetInSegment = startOffsetInSegment,
            length = length
        )
    }

    override fun TestRef.copyTo(
        segmentId: String,
        startOffsetInSegment: Int,
        length: Int,
        sortIndex: String
    ): TestRef {
        return copy(
            segmentId = segmentId,
            startOffsetInSegment = startOffsetInSegment,
            length = length,
            sortIndex = sortIndex
        )
    }

    override fun createQueuePositionMapper(
        segments: List<TestSegment>,
        queueRefs: List<TestRef>
    ): TestPositionMapper {
        return TestPositionMapper(queueRefs, segments.associateBy { it.id })
    }

    override fun createSegmentPageEntity(
        segmentId: String,
        page: Int,
        isCached: Boolean,
        cachedCount: Int,
        error: String?
    ): TestPage {
        return TestPage(
            segmentId = segmentId,
            page = page,
            isCached = isCached,
            cachedCount = cachedCount,
            error = error
        )
    }

    override fun TestSegment.copyTo(
        name: String,
        coverUrl: String?,
        totalCount: Int?,
        loadedCount: Int,
        hasMore: Boolean,
        lastError: String?
    ): TestSegment {
        return copy(
            name = name,
            coverUrl = coverUrl,
            totalCount = totalCount,
            loadedCount = loadedCount,
            hasMore = hasMore,
            lastError = lastError
        )
    }

    override fun TestNetworkSong.toQueueSongEntity(segmentId: String): TestSong {
        return TestSong(
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

    override fun TestSong.copyTo(
        id: String,
        segmentId: String,
        name: String,
        coverUrl: String?,
        artist: String,
        durationMs: Long,
        playUrl: String?,
        sortOrderInSegment: Int
    ): TestSong {
        return copy(
            id = id,
            segmentId = segmentId,
            name = name,
            coverUrl = coverUrl,
            singerName = artist,
            durationMs = durationMs,
            playUrl = playUrl,
            sortOrderInSegment = sortOrderInSegment
        )
    }

    override fun TestSong.toSingleSongSegment(): TestSegment {
        return TestSegment(
            id = id,
            type = "song",
            name = name,
            coverUrl = coverUrl,
            totalCount = 1,
            loadedCount = 1,
            pageSize = 1,
            hasMore = false
        )
    }

    override fun TestNetworkSegment.toQueueSegmentEntity(
        loadedCount: Int,
        hasMore: Boolean,
        lastError: String?,
        sortIndex: String
    ): TestSegment {
        return TestSegment(
            id = id,
            type = type,
            name = name,
            coverUrl = coverUrl,
            totalCount = totalCount,
            loadedCount = loadedCount,
            pageSize = PAGE_SIZE,
            hasMore = hasMore,
            lastError = lastError,
            sortIndex = sortIndex
        )
    }

    override fun buildSongsWindowQuery(ranges: List<SegmentWindowRange<TestSegment>>) = Unit

    override fun buildPagesWindowQuery(ranges: List<SegmentWindowRange<TestSegment>>) = Unit

    override fun buildWindowQuery(
        select: String,
        orderBy: String,
        ranges: List<SegmentWindowRange<TestSegment>>,
        pageRange: Boolean
    ) = Unit
}

private class FakeQueueDao : BasePlayQueueDao<Unit, TestSong, TestSegment, TestPage, TestRef> {
    private val segments = linkedMapOf<String, TestSegment>()
    private val songs = linkedMapOf<String, TestSong>()
    private val pages = linkedMapOf<Pair<String, Int>, TestPage>()
    private val refs = mutableListOf<TestRef>()
    private var nextRefId = 1L

    fun allSongs(): List<TestSong> = songs.values.toList()

    override fun observeSegments(): Flow<List<TestSegment>> = flowOf(getSegmentsBlocking())

    override fun observeSongsInWindow(query: Unit): Flow<List<TestSong>> = flowOf(songs.values.toList())

    override fun observePagesInWindow(query: Unit): Flow<List<TestPage>> = flowOf(pages.values.toList())

    override fun observeRefs(): Flow<List<TestRef>> = flowOf(refs.toList())

    override suspend fun getSegments(): List<TestSegment> = getSegmentsBlocking()

    override suspend fun getSegment(segmentId: String): TestSegment? = segments[segmentId]

    override suspend fun getSegmentLastSortIndex(): String? = segments.values.maxOfOrNull { it.sortIndex }

    override suspend fun getSegmentFirstSortIndex(): String? = segments.values.minOfOrNull { it.sortIndex }

    override suspend fun getSegmentSortIndex(segmentId: String): String? = segments[segmentId]?.sortIndex

    override suspend fun getSegmentPreviousSortIndex(curSortIndex: String): String? {
        return segments.values.map { it.sortIndex }.filter { it < curSortIndex }.maxOrNull()
    }

    override suspend fun getSegmentNextSortIndex(curSortIndex: String): String? {
        return segments.values.map { it.sortIndex }.filter { it > curSortIndex }.minOrNull()
    }

    override suspend fun getPage(segmentId: String, page: Int): TestPage? = pages[segmentId to page]

    override suspend fun getSongAtPosition(segmentId: String, sortOrderInSegment: Int): TestSong? {
        return songs.values.firstOrNull { song ->
            song.segmentId == segmentId && song.sortOrderInSegment == sortOrderInSegment
        }
    }

    override suspend fun getSongsById(songId: String): List<TestSong> {
        return songs[songId]?.let { listOf(it) }.orEmpty()
    }

    override suspend fun getSongsByIds(songIds: List<String>): List<TestSong> {
        return songIds.distinct().mapNotNull { songId -> songs[songId] }
    }

    override suspend fun getSongsBySegmentId(segmentId: String): List<TestSong> {
        return songs.values.filter { song -> song.segmentId == segmentId }
    }

    override suspend fun getRefs(): List<TestRef> = refs.toList()

    override suspend fun getRefsBySegmentId(segmentId: String): List<TestRef> {
        return refs.filter { ref -> ref.segmentId == segmentId }
    }

    override suspend fun countCachedSongs(segmentId: String): Int {
        return pages.values
            .filter { page -> page.segmentId == segmentId && page.isCached }
            .sumOf { page -> page.cachedCount }
    }

    override suspend fun upsertSegments(segments: List<TestSegment>) {
        segments.forEach { segment -> this.segments[segment.id] = segment }
    }

    override suspend fun upsertSegment(segment: TestSegment) {
        segments[segment.id] = segment
    }

    override suspend fun upsertSongs(songs: List<TestSong>) {
        songs.forEach { song -> this.songs[song.id] = song }
    }

    override suspend fun refreshPlayQueue(segments: List<TestSegment>, refs: List<TestRef>) {
        clearPlayQueue()
        upsertSegments(segments)
        upsertRefs(refs)
    }

    override suspend fun upsertPage(page: TestPage) {
        pages[page.segmentId to page.page] = page
    }

    override suspend fun cachePage(segment: TestSegment, page: TestPage, songs: List<TestSong>) {
        upsertSegment(segment)
        upsertSongs(songs)
        upsertPage(page)
    }

    override suspend fun upsertRef(ref: TestRef) {
        val saved = if (ref.id == 0L) ref.copy(id = nextRefId++) else ref
        refs.removeAll { existing -> existing.id == saved.id }
        refs += saved
    }

    override suspend fun upsertRefs(ref: List<TestRef>) {
        ref.forEach { item -> upsertRef(item) }
    }

    override suspend fun refreshRefs(refs: List<TestRef>) {
        this.refs.clear()
        upsertRefs(refs)
    }

    override suspend fun deleteSegmentById(segmentId: String) {
        segments.remove(segmentId)
    }

    override suspend fun deleteSongsBySegmentId(segmentId: String) {
        songs.values.removeAll { song -> song.segmentId == segmentId }
    }

    override suspend fun deleteSegmentPageBySegmentId(segmentId: String) {
        pages.keys.removeAll { (id, _) -> id == segmentId }
    }

    override suspend fun removeQueueSegment(segmentId: String) {
        deleteSongsBySegmentId(segmentId)
        deleteSegmentPageBySegmentId(segmentId)
        refs.removeAll { ref -> ref.segmentId == segmentId }
        deleteSegmentById(segmentId)
    }

    override suspend fun clearSegments() {
        segments.clear()
    }

    override suspend fun clearSegmentPages() {
        pages.clear()
    }

    override suspend fun clearSegmentRefs() {
        refs.clear()
    }

    override suspend fun clearSongs() {
        songs.clear()
    }

    override suspend fun clearPlayQueue() {
        clearSegments()
        clearSegmentPages()
        clearSegmentRefs()
        clearSongs()
    }

    private fun getSegmentsBlocking(): List<TestSegment> {
        return segments.values.sortedBy { segment -> segment.id }
    }
}
