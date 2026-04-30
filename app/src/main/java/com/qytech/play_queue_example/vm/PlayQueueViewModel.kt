package com.qytech.play_queue_example.vm

import androidx.lifecycle.viewModelScope
import com.qytech.play_queue.data.PageKey
import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.data.PlaybackMode
import com.qytech.play_queue.data.PositionKey
import com.qytech.play_queue.data.RepositorySnapshot
import com.qytech.play_queue.model.QueueRow
import com.qytech.play_queue.playback.PlaybackQueueController
import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.model.QueueSong
import com.qytech.play_queue_example.network.PlayQueueMusicApi
import com.qytech.play_queue_example.repository.PlayQueueRepository
import com.qytech.play_queue_example.room.dao.PlayQueueDao
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentPageEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSongEntity
import com.qytech.play_queue_example.state.PlayQueueUiState
import com.qytech.play_queue_example.state.QueueSegmentLoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayQueueViewModel @Inject constructor(
    private val repository: PlayQueueRepository
) : BaseViewModel() {

    private val playbackController = PlaybackQueueController(
        queueSource = repository,
        onPreparedPrevious = { playableSong -> preparePreviousMediaItem(playableSong) },
        onPreparedNext = { playableSong -> prepareNextMediaItem(playableSong) },
        onPreparePlay = { }
    )

    private val visibleWindow = MutableStateFlow(0..120)

    private val windowSnapshot = visibleWindow.flatMapLatest { window ->
        repository.observeWindow(window)
    }

    val uiState = combine(
        windowSnapshot,
        playbackController.state,
        visibleWindow
    ) { snapshot, playbackState, window ->
        snapshot.toUiState(
            playingSong = playbackState.currentSong?.toUiModel(
                isPlaying = playbackState.isPlaying
            ),
            playing = playbackState.isPlaying,
            mode = playbackState.playbackMode,
            window = window
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayQueueUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.preloadWindow(visibleWindow.value)
        }
    }

    fun onVisibleRangeChanged(first: Int, last: Int) {
        val buffer = 500
        val safeFirst = first.coerceAtLeast(0)
        val safeLast = last.coerceAtLeast(safeFirst)
        val window = (safeFirst - buffer).coerceAtLeast(0)..(safeLast + buffer)
        visibleWindow.value = window

        viewModelScope.launch(Dispatchers.IO) {
            repository.preloadWindow(window)
        }
    }

    fun play(song: QueueSong) {
        viewModelScope.launch(Dispatchers.IO) {
            playbackController.playOrToggle(song.globalPosition)
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch(Dispatchers.IO) {
            playbackController.togglePlayPause()
        }
    }

    fun previous() {
        viewModelScope.launch(Dispatchers.IO) {
            playbackController.previous()
        }
    }

    fun next() {
        viewModelScope.launch(Dispatchers.IO) {
            playbackController.next()
        }
    }

    fun cyclePlaybackMode() {
        viewModelScope.launch(Dispatchers.IO) {
            playbackController.cyclePlaybackMode()
        }
    }

    fun retry(segmentId: String, page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.retry(segmentId, page)
        }
    }

    private fun preparePreviousMediaItem(song: PlayableSong<QueueSongEntity, QueueSegmentEntity>) {

    }

    private fun prepareNextMediaItem(song: PlayableSong<QueueSongEntity, QueueSegmentEntity>) {
        // Real player integration can prepare the next media item here.
    }

    private fun RepositorySnapshot<QueueSongEntity, QueueSegmentEntity, QueueSegmentPageEntity>.toUiState(
        playingSong: QueueSong?,
        playing: Boolean,
        mode: PlaybackMode,
        window: IntRange
    ): PlayQueueUiState {
        val rowsByPosition = window
            .asSequence()
            .filter { it in 0 until totalSize }
            .mapNotNull { globalPosition ->
                val row =
                    rowAt(globalPosition, playingSong?.songId, playing) ?: return@mapNotNull null
                globalPosition to row
            }
            .toMap()

        return PlayQueueUiState(
            totalCount = totalSize,
            rowsByPosition = rowsByPosition,
            segmentStates = segments.map { it.toLoadState(loadingPageKeys) },
            currentPlayingSong = playingSong,
            isPlaying = playing,
            playbackMode = mode,
            visibleWindow = window
        )
    }

    private fun RepositorySnapshot<QueueSongEntity, QueueSegmentEntity, QueueSegmentPageEntity>.rowAt(
        globalPosition: Int,
        playingSongId: String?,
        playing: Boolean
    ): QueueRow? {
        val located = locate(globalPosition) ?: return null
        val segment = located.segment
        val page = located.page
        val pageKey = PageKey(segment.id, "", page)
        val pageState = pagesByKey[pageKey]
        val song = songsByPositionKey[PositionKey(segment.id, "", located.offsetInSegment)]

        if (song != null) {
            return QueueRow.SongRow(
                song.toUiModel(
                    globalPosition = globalPosition,
                    playlist = segment,
                    isPlaying = song.id == playingSongId && playing
                )
            )
        }

        val firstOffsetOfPage = (page - 1) * segment.pageSize
        val shouldShowErrorHere =
            pageState?.error != null && located.offsetInSegment == firstOffsetOfPage
        if (shouldShowErrorHere) {
            return QueueRow.ErrorRow(
                globalPosition = globalPosition,
                segmentId = segment.id,
                segmentName = segment.name,
                page = page,
                message = pageState.error ?: "加载失败"
            )
        }

        return QueueRow.PlaceholderRow(
            globalPosition = globalPosition,
            segmentId = segment.id,
            segmentName = segment.name,
            offsetInSegment = located.offsetInSegment,
            page = page,
            isPageLoading = pageKey in loadingPageKeys,
            isOutsideMemoryWindow = globalPosition !in window
        )
    }

    private fun PlayableSong<QueueSongEntity, QueueSegmentEntity>.toUiModel(
        isPlaying: Boolean
    ) = song.toUiModel(
        globalPosition = globalPosition,
        playlist = location.segment,
        isPlaying = isPlaying
    )

    private fun QueueSongEntity.toUiModel(
        globalPosition: Int,
        playlist: QueueSegmentEntity,
        isPlaying: Boolean
    ) = QueueSong(
        globalPosition = globalPosition,
        songId = id,
        segmentId = segmentId,
        segmentName = playlist.name,
        name = name,
        artist = artist,
        durationText = durationMs.toDurationText(),
        isPlaying = isPlaying
    )

    private fun QueueSegmentEntity.toLoadState(loadingKeys: Set<PageKey>) = QueueSegmentLoadState(
        segmentId = id,
        name = name,
        totalCount = totalCount,
        cachedCount = loadedCount,
        pageSize = pageSize,
        isLoading = loadingKeys.any { it.segmentId == id },
        error = lastError,
        hasMore = hasMore
    )

    private fun Long.toDurationText(): String {
        val minute = this / 1000 / 60
        val second = this / 1000 % 60
        return "$minute:${second.toString().padStart(2, '0')}"
    }

}