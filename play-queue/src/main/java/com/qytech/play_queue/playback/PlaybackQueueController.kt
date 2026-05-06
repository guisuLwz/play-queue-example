package com.qytech.play_queue.playback

import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.data.PlaybackMode
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSongEntity
import com.qytech.play_queue.state.PlaybackQueueState
import com.qytech.play_queue.data.PreparedPlaybackItem
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
    private val onPreparePlay: suspend (PlayableSong<S, SEG>) -> Unit
) {
    private val operationMutex = Mutex()
    private val shuffleBackStack = ArrayDeque<Int>()
    private val _state = MutableStateFlow(PlaybackQueueState<S, SEG>())

    val state: StateFlow<PlaybackQueueState<S, SEG>> = _state.asStateFlow()

    suspend fun playOrToggle(globalPosition: Int): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            val current = _state.value.currentSong
            if (current?.globalPosition == globalPosition) {
                val nextPlaying = !_state.value.isPlaying
                setPlayingLocked(nextPlaying)
                if (nextPlaying) {
                    queueSource.preloadPlaybackAround(globalPosition)
                    preparePreviousIfNeededLocked(globalPosition)
                    prepareShuffleNextIfNeededLocked(globalPosition)
                }
                current
            } else {
                playAtLocked(globalPosition, shouldPlay = true)
            }
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
                    prepareShuffleNextIfNeededLocked(current.globalPosition)
                }
                current
            }
        }
    }

    suspend fun previous(): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            val target = previousPositionLocked() ?: return@withLock null
            playAtLocked(
                globalPosition = target,
//                shouldPlay = _state.value.isPlaying || _state.value.currentSong == null 当前不播放状态不会被强行改变
                shouldPlay = true // 强行播放
            )
        }
    }

    suspend fun next(): PlayableSong<S, SEG>? {
        return operationMutex.withLock {
            val target = nextPositionLocked() ?: return@withLock null
            playAtLocked(
                globalPosition = target,
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
        return previousPositionLocked() != null
    }

    suspend fun hasNext(): Boolean {
        return nextPositionLocked() != null
    }

    private suspend fun setPlaybackModeLocked(mode: PlaybackMode) {
        _state.value = _state.value.copy(playbackMode = mode)
        if (mode == PlaybackMode.Shuffle) {
            preparePreviousIfNeededLocked(_state.value.currentSong?.globalPosition)
            prepareShuffleNextIfNeededLocked(_state.value.currentSong?.globalPosition)
        } else {
            clearPreparedShuffleNextLocked()
        }
    }

    private suspend fun playAtLocked(
        globalPosition: Int,
        shouldPlay: Boolean
    ): PlayableSong<S, SEG>? {
        val playableSong = queueSource.getPlayableSongAt(globalPosition) ?: return null
        _state.value = _state.value.copy(
            currentSong = playableSong,
            isPlaying = shouldPlay
        )
        queueSource.preloadPlaybackAround(globalPosition)
        if (shouldPlay) onPreparePlay(playableSong)
        preparePreviousIfNeededLocked(globalPosition)
        prepareShuffleNextIfNeededLocked(globalPosition)
        return playableSong
    }

    private fun setPlayingLocked(playing: Boolean) {
        _state.value = _state.value.copy(isPlaying = playing)
    }

    private suspend fun nextPositionLocked(): Int? {
        val total = queueSource.totalSize()
        if (total <= 0) return null
        val current = _state.value.currentSong?.globalPosition

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
                if (current != null) pushShuffleHistory(current)
                consumePreparedShuffleNextLocked(total, current)
            }
        }
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

    private suspend fun prepareShuffleNextIfNeededLocked(current: Int?) {
        if (_state.value.playbackMode != PlaybackMode.Shuffle) {
            clearPreparedShuffleNextLocked()
            return
        }

        val total = queueSource.totalSize()
        if (total <= 0) {
            clearPreparedShuffleNextLocked()
            return
        }

        val next = randomPosition(total, current)
        val playableSong = queueSource.getPlayableSongAt(next) ?: run {
            clearPreparedShuffleNextLocked()
            return
        }

        if (_state.value.playbackMode != PlaybackMode.Shuffle) {
            clearPreparedShuffleNextLocked()
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

    private fun consumePreparedShuffleNextLocked(total: Int, current: Int?): Int {
        val prepared = _state.value.preparedNext?.position
            ?.takeIf { it in 0 until total }
            ?.takeIf { total <= 1 || it != current }

        clearPreparedShuffleNextLocked()
        return prepared ?: randomPosition(total, current)
    }

    private fun clearPreparedPreviousLocked() {
        _state.value = _state.value.copy(preparedPrevious = null)
    }

    private fun clearPreparedShuffleNextLocked() {
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
