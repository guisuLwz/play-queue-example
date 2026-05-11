package com.qytech.play_queue.playback

import com.qytech.play_queue.data.LocatedPosition
import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.data.QueueMutationResult
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSongEntity
import com.qytech.play_queue.playback.intf.PlayableQueueSource
import com.qytech.play_queue.playback.intf.PlaybackQueuePlayerDelegate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BasePlaybackQueueControllerPreparedTest {

    @Test
    fun play_now_prepares_next_before_prepare_play() = runBlocking {
        val source = FakePlayableQueueSource()
        val events = mutableListOf<String>()
        var controller: TestPlaybackQueueController? = null

        DelegateHolder.delegate = RecordingDelegate(
            events = events,
            onPreparePlay = {
                val state = requireNotNull(controller).state.value
                assertNotNull(state.preparedNext)
                assertEquals("song-1", state.preparedNext?.song?.song?.id)
            }
        )
        controller = TestPlaybackQueueController(source)

        controller.applyQueueMutation {
            QueueMutationResult(
                firstInsertedPosition = 0,
                autoPlayPosition = 0
            )
        }

        assertEquals(
            listOf(
                "autoNextBlocked",
                "preparedNext:song-1",
                "preparePlay:song-0",
                "autoNextReleased:song-1"
            ),
            events
        )
    }

    @Test
    fun play_from_offset_prepares_previous_and_next_before_prepare_play() = runBlocking {
        val source = FakePlayableQueueSource()
        val events = mutableListOf<String>()
        var controller: TestPlaybackQueueController? = null

        DelegateHolder.delegate = RecordingDelegate(
            events = events,
            onPreparePlay = {
                val state = requireNotNull(controller).state.value
                assertEquals("song-0", state.preparedPrevious?.song?.song?.id)
                assertEquals("song-2", state.preparedNext?.song?.song?.id)
            }
        )
        controller = TestPlaybackQueueController(source)

        controller.applyQueueMutation {
            QueueMutationResult(
                firstInsertedPosition = 0,
                autoPlayPosition = 1
            )
        }

        assertEquals(
            listOf(
                "autoNextBlocked",
                "preparedPrevious:song-0",
                "preparedNext:song-2",
                "preparePlay:song-1",
                "autoNextReleased:song-2"
            ),
            events
        )
    }

    @Test
    fun resume_play_refreshes_prepared_items_before_player_play() = runBlocking {
        val source = FakePlayableQueueSource()
        val events = mutableListOf<String>()
        var controller: TestPlaybackQueueController? = null

        DelegateHolder.delegate = RecordingDelegate(
            events = events,
            onPlay = {
                val state = requireNotNull(controller).state.value
                assertEquals("song-0", state.preparedPrevious?.song?.song?.id)
                assertEquals("song-2", state.preparedNext?.song?.song?.id)
            }
        )
        controller = TestPlaybackQueueController(source)

        controller.playAt(globalPosition = 1, shouldPlay = false)
        events.clear()

        controller.play()

        assertEquals(
            listOf(
                "preparedPrevious:song-0",
                "preparedNext:song-2",
                "play"
            ),
            events
        )
    }
}

private object DelegateHolder {
    lateinit var delegate: PlaybackQueuePlayerDelegate<TestSong, TestSegment>
}

private class TestPlaybackQueueController(
    source: PlayableQueueSource<TestSong, TestSegment>
) : BasePlaybackQueueController<TestSong, TestSegment>(
    queueSource = source
) {
    override fun getPlayerDelegate(): PlaybackQueuePlayerDelegate<TestSong, TestSegment> {
        return DelegateHolder.delegate
    }
}

private class RecordingDelegate(
    private val events: MutableList<String>,
    private val onPreparePlay: () -> Unit = {},
    private val onPlay: () -> Unit = {}
) : PlaybackQueuePlayerDelegate<TestSong, TestSegment> {
    override suspend fun onPreparedPrevious(
        hasPrepared: Boolean,
        previous: PlayableSong<TestSong, TestSegment>
    ) {
        events += "preparedPrevious:${previous.song.id}"
    }

    override suspend fun onPreparedNext(
        hasPrepared: Boolean,
        next: PlayableSong<TestSong, TestSegment>
    ) {
        events += "preparedNext:${next.song.id}"
    }

    override suspend fun onPreparePlay(current: PlayableSong<TestSong, TestSegment>) {
        events += "preparePlay:${current.song.id}"
        onPreparePlay()
    }

    override suspend fun onAutoPreparedPrevious() {
        events += "autoPreparedPrevious"
    }

    override suspend fun onAutoNextBlocked() {
        events += "autoNextBlocked"
    }

    override suspend fun onAutoNextReleased(next: PlayableSong<TestSong, TestSegment>?) {
        events += "autoNextReleased:${next?.song?.id}"
    }

    override suspend fun play() {
        events += "play"
        onPlay()
    }

    override suspend fun pause() {
        events += "pause"
    }
}

private class FakePlayableQueueSource : PlayableQueueSource<TestSong, TestSegment> {
    private val segment = TestSegment(id = "segment", totalCount = 3)
    private val songs = listOf(
        TestSong(id = "song-0", sortOrderInSegment = 0),
        TestSong(id = "song-1", sortOrderInSegment = 1),
        TestSong(id = "song-2", sortOrderInSegment = 2)
    )

    override suspend fun totalSize(): Int = songs.size

    override suspend fun getPlayableSongAt(
        globalPosition: Int,
        forceRetry: Boolean
    ): PlayableSong<TestSong, TestSegment>? {
        val song = songs.getOrNull(globalPosition) ?: return null
        return PlayableSong(
            globalPosition = globalPosition,
            location = LocatedPosition(
                segment = segment,
                startGlobalPosition = 0,
                offsetInSegment = globalPosition
            ),
            song = song
        )
    }

    override suspend fun getSongGlobalPosition(songId: String): Int? {
        return songs.indexOfFirst { song -> song.id == songId }
            .takeIf { index -> index >= 0 }
    }

    override suspend fun getSongSegmentId(songId: String): String? {
        return segment.id.takeIf { getSongGlobalPosition(songId) != null }
    }

    override suspend fun findPreviousPlayableSong(
        globalPosition: Int?,
        wrap: Boolean
    ): PlayableSong<TestSong, TestSegment>? {
        val start = ((globalPosition ?: songs.size) - 1).coerceAtMost(songs.lastIndex)
        if (start >= 0) return getPlayableSongAt(start)
        return if (wrap) getPlayableSongAt(songs.lastIndex) else null
    }

    override suspend fun findNextPlayableSong(
        globalPosition: Int?,
        wrap: Boolean
    ): PlayableSong<TestSong, TestSegment>? {
        val next = (globalPosition ?: -1) + 1
        if (next in songs.indices) return getPlayableSongAt(next)
        return if (wrap) getPlayableSongAt(0) else null
    }

    override suspend fun preloadPlaybackAround(
        globalPosition: Int,
        lookBehindPages: Int,
        lookAheadPages: Int
    ) = Unit
}

private data class TestSegment(
    override val id: String,
    override val type: String = "playlist",
    override val name: String = id,
    override val coverUrl: String? = null,
    override val totalCount: Int?,
    override val loadedCount: Int = totalCount ?: 0,
    override val pageSize: Int = 3,
    override val hasMore: Boolean = false,
    override val lastError: String? = null,
    override val sortIndex: String = "a0"
) : IQueueSegmentEntity

private data class TestSong(
    override val id: String,
    override val segmentId: String = "segment",
    override val name: String = id,
    override val coverUrl: String? = null,
    override val singerName: String = "artist",
    override val durationMs: Long = 1000L,
    override val playUrl: String? = null,
    override val sortOrderInSegment: Int
) : IQueueSongEntity
