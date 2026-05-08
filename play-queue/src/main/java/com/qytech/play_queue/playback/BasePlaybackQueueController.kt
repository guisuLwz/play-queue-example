package com.qytech.play_queue.playback

import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.data.PlaybackMode
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSongEntity
import com.qytech.play_queue.state.PlaybackQueueState
import com.qytech.play_queue.data.PreparedPlaybackItem
import com.qytech.play_queue.data.QueueMutationResult
import com.qytech.play_queue.data.QueueRemovalResult
import com.qytech.play_queue.playback.intf.PlayableQueueSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

abstract class BasePlaybackQueueController<S : IQueueSongEntity, SEG : IQueueSegmentEntity>(
    private val queueSource: PlayableQueueSource<S, SEG>,
    private val maxShuffleHistory: Int = DEFAULT_MAX_SHUFFLE_HISTORY,
    private val onPreparedPrevious: suspend (PlayableSong<S, SEG>) -> Unit = {},
    private val onPreparedNext: suspend (PlayableSong<S, SEG>) -> Unit = {},
    private val onPreparePlay: suspend (PlayableSong<S, SEG>) -> Unit,
    private val onAutoPreparedPrevious: suspend () -> Unit,
) {
    private val operationMutex = Mutex()
    private val shuffleBackStack = ArrayDeque<Int>()
    private val _state = MutableStateFlow(PlaybackQueueState<S, SEG>())

    val state: StateFlow<PlaybackQueueState<S, SEG>> = _state.asStateFlow()

    suspend fun autoPlayNext(songId: String) {
        return operationMutex.withLock {
            val globalPosition = queueSource.getSongGlobalPosition(songId) ?: return@withLock
            if (songId == _state.value.currentSong?.song?.id) return@withLock
            val playableSong = queueSource.getPlayableSongAt(globalPosition)
            _state.value = _state.value.copy(
                currentSong = playableSong,
                isPlaying = true
            )

//            preparePreviousIfNeededLocked(globalPosition)
            onAutoPreparedPrevious()
            prepareNextIfNeededLocked(globalPosition)
        }
    }

    suspend fun playOrToggle(globalPosition: Int): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            val current = _state.value.currentSong
            if (current?.globalPosition == globalPosition) {
                val nextPlaying = !_state.value.isPlaying
                setPlayingLocked(nextPlaying)
                if (nextPlaying) {
                    queueSource.preloadPlaybackAround(globalPosition)
                    preparePreviousIfNeededLocked(globalPosition)
                    prepareNextIfNeededLocked(globalPosition)
                }
                current
            } else {
                playAtLocked(globalPosition, shouldPlay = true)
            }
        }
    }

    suspend fun playAt(
        globalPosition: Int,
        shouldPlay: Boolean = true
    ): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            playAtLocked(globalPosition, shouldPlay)
        }
    }

    suspend fun togglePlayPause(): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            val current = _state.value.currentSong
            if (current == null) {
                playAtLocked(globalPosition = 0, shouldPlay = true)
            } else {
                val nextPlaying = !_state.value.isPlaying
                setPlayingLocked(nextPlaying)
                if (nextPlaying) {
                    queueSource.preloadPlaybackAround(current.globalPosition)
                    preparePreviousIfNeededLocked(current.globalPosition)
                    prepareNextIfNeededLocked(current.globalPosition)
                }
                current
            }
        }
    }

    suspend fun play(): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            val current = _state.value.currentSong
            if (current == null) {
                null
            } else {
                val nextPlaying = !_state.value.isPlaying
                if (!nextPlaying) return null
                setPlayingLocked(true)
                queueSource.preloadPlaybackAround(current.globalPosition)
                preparePreviousIfNeededLocked(current.globalPosition)
                prepareNextIfNeededLocked(current.globalPosition)
                current
            }
        }
    }

    suspend fun pause(): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            val current = _state.value.currentSong
            if (current == null) {
                null
            } else {
                val nextPlaying = !_state.value.isPlaying
                if (nextPlaying) return null
                setPlayingLocked(false)
                queueSource.preloadPlaybackAround(current.globalPosition)
                preparePreviousIfNeededLocked(current.globalPosition)
                prepareNextIfNeededLocked(current.globalPosition)
                current
            }
        }
    }

    suspend fun previous(): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            val playableSong = previousPlayableSongLocked() ?: return@withLock null
            playResolvedSongLocked(
                playableSong = playableSong,
//                shouldPlay = _state.value.isPlaying || _state.value.currentSong == null 当前不播放状态不会被强行改变
                shouldPlay = true // 强行播放
            )
        }
    }

    suspend fun next(): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            val playableSong = nextPlayableSongLocked() ?: return@withLock null
            playResolvedSongLocked(
                playableSong = playableSong,
//                shouldPlay = _state.value.isPlaying || _state.value.currentSong == null
                shouldPlay = true // 强行播放
            )
        }
    }

    suspend fun cyclePlaybackMode(): PlaybackMode {
        return operationMutex.withLock {
            val nextMode = when (_state.value.playbackMode) {
                PlaybackMode.Sequence -> PlaybackMode.RepeatAll
                PlaybackMode.RepeatAll -> PlaybackMode.RepeatOne
                PlaybackMode.RepeatOne -> PlaybackMode.Shuffle
                PlaybackMode.Shuffle -> PlaybackMode.Sequence
            }

            setPlaybackModeLocked(nextMode)
            nextMode
        }
    }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        operationMutex.withLock {
            setPlaybackModeLocked(mode)
        }
    }

    suspend fun hasPrevious(): Boolean {
        return operationMutex.withLock {
            val total = queueSource.totalSize()
            if (total <= 0) return@withLock false
            previousCandidatePositionLocked(total, _state.value.currentSong?.globalPosition) != null
        }
    }

    suspend fun hasNext(): Boolean {
        return operationMutex.withLock {
            val total = queueSource.totalSize()
            nextCandidatePositionLocked(total, _state.value.currentSong?.globalPosition) != null
        }
    }

    suspend fun refreshPrepared() {
        operationMutex.withLock {
            val currentPosition = _state.value.currentSong?.globalPosition
            if (currentPosition == null) {
                clearPreparedPreviousLocked()
                clearPreparedNextLocked()
                return@withLock
            }

            queueSource.preloadPlaybackAround(currentPosition)
            preparePreviousIfNeededLocked(currentPosition)
            prepareNextIfNeededLocked(currentPosition)
        }
    }

    suspend fun applyQueueMutationResult(result: QueueMutationResult) {
        val autoPlayPosition = result.autoPlayPosition
        if (autoPlayPosition != null) {
            playAt(autoPlayPosition, shouldPlay = true)
        } else if (result.inserted) {
            refreshPrepared()
        }
    }

    suspend fun applyQueueRemovalResult(result: QueueRemovalResult) {
        operationMutex.withLock {
            val current = _state.value.currentSong
            val total = queueSource.totalSize()

            if (total <= 0) {
                clearPlaybackLocked(incrementRevision = true)
                return@withLock
            }

            if (current == null) {
                _state.value = _state.value.copy(
                    preparedPrevious = null,
                    preparedNext = null,
                    revision = _state.value.revision + 1
                )
                return@withLock
            }

            val currentWasRemoved = current.location.segment.id == result.removedSegmentId ||
                    result.contains(current.globalPosition)
            if (currentWasRemoved) {
                clearPlaybackLocked(incrementRevision = true)
                return@withLock
            }

            val adjustedPosition = (
                    current.globalPosition - result.removedCountBefore(current.globalPosition)
                    ).coerceIn(0, total - 1)
            val refreshedCurrent = queueSource.getPlayableSongAt(adjustedPosition)
            if (refreshedCurrent == null ||
                refreshedCurrent.song.id != current.song.id ||
                refreshedCurrent.location.segment.id != current.location.segment.id
            ) {
                clearPlaybackLocked(incrementRevision = true)
                return@withLock
            }

            _state.value = _state.value.copy(
                currentSong = refreshedCurrent,
                revision = _state.value.revision + 1
            )
            queueSource.preloadPlaybackAround(adjustedPosition)
            preparePreviousIfNeededLocked(adjustedPosition)
            prepareNextIfNeededLocked(adjustedPosition)
        }
    }

    private suspend fun setPlaybackModeLocked(mode: PlaybackMode) {
        _state.value = _state.value.copy(playbackMode = mode)
        val currentPosition = _state.value.currentSong?.globalPosition
        if (currentPosition == null) {
            clearPreparedPreviousLocked()
            clearPreparedNextLocked()
            return
        }
        preparePreviousIfNeededLocked(currentPosition)
        prepareNextIfNeededLocked(currentPosition)
    }

    private suspend fun playAtLocked(
        globalPosition: Int,
        shouldPlay: Boolean
    ): PlayableSong<S, SEG>? {
        val playableSong = queueSource.getPlayableSongAt(globalPosition) ?: return null
        return playResolvedSongLocked(playableSong, shouldPlay)
    }

    private suspend fun playResolvedSongLocked(
        playableSong: PlayableSong<S, SEG>,
        shouldPlay: Boolean
    ): PlayableSong<S, SEG> {
        _state.value = _state.value.copy(
            currentSong = playableSong,
            isPlaying = shouldPlay
        )
        queueSource.preloadPlaybackAround(playableSong.globalPosition)
        if (shouldPlay) onPreparePlay(playableSong)
        preparePreviousIfNeededLocked(playableSong.globalPosition)
        prepareNextIfNeededLocked(playableSong.globalPosition)
        return playableSong
    }

    private fun setPlayingLocked(playing: Boolean) {
        _state.value = _state.value.copy(isPlaying = playing)
    }

    private fun clearPlaybackLocked(incrementRevision: Boolean = false) {
        shuffleBackStack.clear()
        val currentState = _state.value
        _state.value = currentState.copy(
            currentSong = null,
            isPlaying = false,
            preparedPrevious = null,
            preparedNext = null,
            revision = if (incrementRevision) currentState.revision + 1 else currentState.revision
        )
    }

    private suspend fun nextPlayableSongLocked(): PlayableSong<S, SEG>? {
        val total = queueSource.totalSize()
        if (total <= 0) return null
        val current = _state.value.currentSong?.globalPosition

        val playableSong = when (_state.value.playbackMode) {
            PlaybackMode.Sequence -> queueSource.findNextPlayableSong(current, wrap = false)
            PlaybackMode.RepeatAll -> queueSource.findNextPlayableSong(current, wrap = true)
            PlaybackMode.RepeatOne -> queueSource.getPlayableSongAt(current ?: 0)
                ?: queueSource.findNextPlayableSong(current, wrap = true)

            PlaybackMode.Shuffle -> {
                val randomStart = randomPosition(total, current)
                queueSource.findNextPlayableSong(randomStart - 1, wrap = true)
            }
        }

        if (playableSong != null && _state.value.playbackMode == PlaybackMode.Shuffle && current != null) {
            pushShuffleHistory(current)
        }
        clearPreparedNextLocked()
        return playableSong
    }

    private suspend fun previousPlayableSongLocked(): PlayableSong<S, SEG>? {
        val total = queueSource.totalSize()
        if (total <= 0) return null
        val current = _state.value.currentSong?.globalPosition

        return when (_state.value.playbackMode) {
            PlaybackMode.Sequence -> queueSource.findPreviousPlayableSong(current, wrap = false)
            PlaybackMode.RepeatAll -> queueSource.findPreviousPlayableSong(current, wrap = true)
            PlaybackMode.RepeatOne -> queueSource.getPlayableSongAt(current ?: 0)
                ?: queueSource.findPreviousPlayableSong(current, wrap = true)

            PlaybackMode.Shuffle -> {
                while (true) {
                    val historyPosition = popShuffleHistory() ?: break
                    queueSource.getPlayableSongAt(historyPosition)?.let { return it }
                }
                queueSource.findPreviousPlayableSong(current, wrap = true)
            }
        }
    }

    private suspend fun nextPositionLocked(): Int? {
        val total = queueSource.totalSize()
        if (total <= 0) return null
        val current = _state.value.currentSong?.globalPosition

        val next = nextCandidatePositionLocked(total, current) ?: return null
        if (_state.value.playbackMode == PlaybackMode.Shuffle && current != null) {
            pushShuffleHistory(current)
        }
        clearPreparedNextLocked()
        return next
    }

    private suspend fun previousPositionLocked(): Int? {
        val total = queueSource.totalSize()
        if (total <= 0) return null
        val current = _state.value.currentSong?.globalPosition

        return when (_state.value.playbackMode) {
            PlaybackMode.Sequence -> {
                val previous = (current ?: total) - 1
                if (previous in 0 until total) previous else null
            }

            PlaybackMode.RepeatAll -> {
                val previous = (current ?: total) - 1
                if (previous >= 0) previous else total - 1
            }

            PlaybackMode.RepeatOne -> current ?: 0

            PlaybackMode.Shuffle -> popShuffleHistory()
                ?: current?.let { (it - 1 + total) % total }
                ?: 0
        }
    }

    private fun randomPosition(total: Int, current: Int?): Int {
        if (total <= 1) return 0
        var candidate = Random.nextInt(total)
        if (candidate == current) {
            candidate = (candidate + 1) % total
        }
        return candidate
    }

    private suspend fun preparePreviousIfNeededLocked(current: Int?) {
        val total = queueSource.totalSize()
        if (total <= 0) {
            clearPreparedPreviousLocked()
            return
        }

        val previous = previousCandidatePositionLocked(total, current) ?: run {
            clearPreparedPreviousLocked()
            return
        }

        val playableSong = queueSource.getPlayableSongAt(previous) ?: run {
            clearPreparedPreviousLocked()
            return
        }

        val prepared = PreparedPlaybackItem(
            position = previous,
            song = playableSong
        )

        _state.value = _state.value.copy(preparedPrevious = prepared)

        queueSource.preloadPlaybackAround(
            globalPosition = previous,
            lookBehindPages = 1,
            lookAheadPages = 0
        )

        onPreparedPrevious(playableSong)
    }

    /**
     * 只计算候选上一首，不消费随机历史
     */
    private fun previousCandidatePositionLocked(
        total: Int,
        current: Int?
    ): Int? {
        return when (_state.value.playbackMode) {
            PlaybackMode.Sequence -> {
                val previous = (current ?: total) - 1
                if (previous in 0 until total) previous else null
            }

            PlaybackMode.RepeatAll -> {
                val previous = (current ?: total) - 1
                if (previous >= 0) previous else total - 1
            }

            PlaybackMode.RepeatOne -> current ?: 0

            PlaybackMode.Shuffle -> {
                shuffleBackStack.lastOrNull()
                    ?: current?.let {
                        (it - 1 + total) % total
                    } ?: 0
            }
        }
    }

    private fun nextCandidatePositionLocked(
        total: Int,
        current: Int?
    ): Int? {
        if (total <= 0) return null

        return when (_state.value.playbackMode) {
            PlaybackMode.Sequence -> {
                val next = (current ?: -1) + 1
                if (next in 0 until total) next else null
            }

            PlaybackMode.RepeatAll -> {
                val next = (current ?: -1) + 1
                if (next < total) next else 0
            }

            PlaybackMode.RepeatOne -> current ?: 0

            PlaybackMode.Shuffle -> {
                _state.value.preparedNext?.position
                    ?.takeIf { it in 0 until total }
                    ?.takeIf { total <= 1 || it != current }
                    ?: randomPosition(total, current)
            }
        }
    }

    private fun nextCandidatePositionForPreparationLocked(
        total: Int,
        current: Int?
    ): Int? {
        if (total <= 0) return null

        return when (_state.value.playbackMode) {
            PlaybackMode.Sequence -> {
                val next = (current ?: -1) + 1
                if (next in 0 until total) next else null
            }

            PlaybackMode.RepeatAll -> {
                val next = (current ?: -1) + 1
                if (next < total) next else 0
            }

            PlaybackMode.RepeatOne -> current ?: 0

            PlaybackMode.Shuffle -> randomPosition(total, current)
        }
    }

    private suspend fun prepareNextIfNeededLocked(current: Int?) {
        val total = queueSource.totalSize()
        if (total <= 0) {
            clearPreparedNextLocked()
            return
        }

        val next = nextCandidatePositionForPreparationLocked(total, current) ?: run {
            clearPreparedNextLocked()
            return
        }
        val playableSong = queueSource.getPlayableSongAt(next) ?: run {
            clearPreparedNextLocked()
            return
        }

        val prepared = PreparedPlaybackItem(
            position = next,
            song = playableSong
        )
        _state.value = _state.value.copy(preparedNext = prepared)

        queueSource.preloadPlaybackAround(
            globalPosition = next,
            lookBehindPages = 0,
            lookAheadPages = 1
        )
        onPreparedNext(playableSong)
    }

    private fun clearPreparedPreviousLocked() {
        _state.value = _state.value.copy(preparedPrevious = null)
    }

    private fun clearPreparedNextLocked() {
        _state.value = _state.value.copy(preparedNext = null)
    }

    private fun pushShuffleHistory(position: Int) {
        if (shuffleBackStack.lastOrNull() == position) return
        shuffleBackStack.addLast(position)
        while (shuffleBackStack.size > maxShuffleHistory) {
            shuffleBackStack.removeFirst()
        }
    }

    private fun popShuffleHistory(): Int? {
        return if (shuffleBackStack.isEmpty()) null else shuffleBackStack.removeLast()
    }

    companion object {
        const val DEFAULT_MAX_SHUFFLE_HISTORY: Int = 10_000
    }
}
