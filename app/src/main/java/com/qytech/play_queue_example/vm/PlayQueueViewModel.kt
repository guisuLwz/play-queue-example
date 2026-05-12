package com.qytech.play_queue_example.vm

import androidx.lifecycle.viewModelScope
import com.qytech.play_queue.data.PageKey
import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.data.PlaybackMode
import com.qytech.play_queue.data.PositionKey
import com.qytech.play_queue.data.RepositorySnapshot
import com.qytech.play_queue.model.QueueRow
import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.model.QueueSong
import com.qytech.play_queue_example.player.PlaybackQueueController
import com.qytech.play_queue_example.repository.PlayQueueRepository
import com.qytech.play_queue_example.repository.QueuePositionMapper
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentPageEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentRef
import com.qytech.play_queue_example.room.entity.queue.QueueSongEntity
import com.qytech.play_queue_example.room.entity.queue.toLoadState
import com.qytech.play_queue_example.room.entity.queue.toUiModel
import com.qytech.play_queue_example.state.PlayQueueUiState
import com.qytech.play_queue_example.state.QueueSegmentLoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayQueueViewModel @Inject constructor(
    private val repository: PlayQueueRepository,
    private val playbackController: PlaybackQueueController
) : BaseViewModel() {

    private val visibleWindow = repository.visibleWindow

    private val windowSnapshot = visibleWindow.flatMapLatest { window ->
        repository.observeQueueWindow(window)
    }

    val playQueueUiState = combine(
        windowSnapshot,
        playbackController.state,
        visibleWindow
    ) { snapshot, playbackState, window ->
        snapshot.toUiState(
            playingSong = playbackState.currentSong?.toUiModel(),
            playing = playbackState.isPlaying,
            mode = playbackState.playbackMode,
            window = window,
            hasPrevious = playbackController.hasPrevious(),
            hasNext = playbackController.hasNext()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayQueueUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.preloadQueueWindow(visibleWindow.value)
        }
    }

    fun onVisibleRangeChanged(first: Int, last: Int) {
        val buffer = 500
        val safeFirst = first.coerceAtLeast(0)
        val safeLast = last.coerceAtLeast(safeFirst)
        val window = (safeFirst - buffer).coerceAtLeast(0)..(safeLast + buffer)
        repository.updateVisibleWindow(window)

        viewModelScope.launch(Dispatchers.IO) {
            repository.preloadQueueWindow(window)
        }
    }

    fun onDeleteSegment(segmentId: String, segmentType: String = "playlist") {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.removeQueueSegment(segmentId, segmentType)
            playbackController.applyQueueRemovalResult(result)
            repository.preloadQueueWindow(visibleWindow.value)
        }
    }

    fun play(song: QueueSong) {
        viewModelScope.launch(Dispatchers.IO) {
            playbackController.playOrToggle(song.globalPosition)
        }
    }

    fun next() {
        viewModelScope.launch(Dispatchers.IO) {
            playbackController.next()
        }
    }

    private fun RepositorySnapshot<QueueSongEntity, QueueSegmentEntity, QueueSegmentPageEntity, QueueSegmentRef>.toUiState(
        playingSong: QueueSong?,
        playing: Boolean,
        mode: PlaybackMode,
        window: IntRange,
        hasPrevious: Boolean,
        hasNext: Boolean
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
            segmentStates = segments.map { it.toLoadState(loadingPageKeys, allPagesByKey) },
            currentPlayingSong = playingSong,
            isPlaying = playing,
            playbackMode = mode,
            visibleWindow = window,
            hasPrevious = hasPrevious,
            hasNext = hasNext
        )
    }

    private fun RepositorySnapshot<QueueSongEntity, QueueSegmentEntity, QueueSegmentPageEntity, QueueSegmentRef>.rowAt(
        globalPosition: Int,
        playingSongId: String?,
        playing: Boolean
    ): QueueRow? {
        val located = locate(globalPosition) ?: return null
        val segment = located.segment
        val page = located.page
        val pageKey = PageKey(segment.id, segment.type, page)
        val pageState = pagesByKey[pageKey]
        val song = songsByPositionKey[PositionKey(segment.id, segment.type, located.offsetInSegment)]

        if (song != null) {
            return QueueRow.SongRow(
                song.toUiModel(
                    globalPosition = globalPosition,
                    playlist = segment,
                    isCurrentPlayable = song.id == playingSongId,
                    isPlayingStatus = song.id == playingSongId && playing
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
                segmentType = segment.type,
                segmentName = segment.name,
                page = page,
                message = pageState.error ?: "加载失败"
            )
        }

        if (pageState?.isCached == true) {
            return QueueRow.ErrorRow(
                globalPosition = globalPosition,
                segmentId = segment.id,
                segmentType = segment.type,
                segmentName = segment.name,
                page = page,
                title = "${segment.name} 第${located.offsetInSegment + 1}首无法播放",
                message = "文件损坏无法播放",
                canRetry = false
            )
        }

        return QueueRow.PlaceholderRow(
            globalPosition = globalPosition,
            segmentId = segment.id,
            segmentType = segment.type,
            segmentName = segment.name,
            offsetInSegment = located.offsetInSegment,
            page = page,
            isPageLoading = pageKey in loadingPageKeys,
            isOutsideMemoryWindow = globalPosition !in window
        )
    }

    private fun PlayableSong<QueueSongEntity, QueueSegmentEntity>.toUiModel() = song.toUiModel(
        globalPosition = globalPosition,
        playlist = location.segment,
    )


    override fun onCleared() {
        super.onCleared()
        repository.resetVisibleWindow()
    }

}
