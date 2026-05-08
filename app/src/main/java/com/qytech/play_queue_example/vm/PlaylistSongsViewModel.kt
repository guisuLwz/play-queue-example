package com.qytech.play_queue_example.vm

import androidx.paging.map
import androidx.lifecycle.viewModelScope
import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.data.QueueAction
import com.qytech.play_queue_example.model.Playlist
import com.qytech.play_queue_example.model.Song
import com.qytech.play_queue_example.model.toModel
import com.qytech.play_queue_example.player.PlaybackQueueController
import com.qytech.play_queue_example.repository.PLAY_QUEUE_PAGE_SIZE
import com.qytech.play_queue_example.repository.PlayQueueRepository
import com.qytech.play_queue_example.repository.SourceRepository
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSongEntity
import com.qytech.play_queue_example.room.entity.queue.toUiModel
import com.qytech.play_queue_example.state.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistSongsViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val playQueueRepository: PlayQueueRepository,
    private val playbackQueueController: PlaybackQueueController
): BaseViewModel() {

    private val queryParams = MutableStateFlow<Playlist?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs = queryParams
        .filterNotNull()
        .flatMapLatest { playlist ->
        sourceRepository.songPager(playlist.id)
            .flow
            .map { pagingData ->
                pagingData.map { entity ->
                    entity.toModel()
                }
            }
    }

    val playbackState = playbackQueueController.state
        .map { state ->
            PlaybackUiState(
                currentPlayingSong = state.currentSong?.let { current ->
                    current.song.toUiModel(
                        globalPosition = current.globalPosition,
                        playlist = current.location.segment,
                        isPlaying = state.isPlaying
                    )
                },
                isPlaying = state.isPlaying,
                playbackMode = state.playbackMode,
                hasPrevious = playbackQueueController.hasPrevious(),
                hasNext = playbackQueueController.hasNext()
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaybackUiState()
        )

    fun onClick(playlist: Playlist, song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = playbackQueueController.state.value.currentSong
            val isCurrentSong = current != null &&
                    current.song.id == song.id.toString()
            if (isCurrentSong) {
                playbackQueueController.playOrToggle(current.globalPosition)
                return@launch
            }

            val result = playQueueRepository.playSegmentFromOffset(
                segment = QueueSegmentEntity(
                    id = playlist.id.toString(),
                    name = playlist.name,
                    coverUrl = null,
                    loadedCount = 0,
                    totalCount = playlist.totalCount,
                    pageSize = PLAY_QUEUE_PAGE_SIZE,
                    hasMore = true,
                    lastError = null,
                    type = "playlist"
                ),
                offsetInSegment = song.indexInSegment
            )
            playbackQueueController.applyQueueMutationResult(result)
            playQueueRepository.preloadQueueWindow(window = playQueueRepository.visibleWindow.value)
        }
    }

    fun onAction(song: Song, action: QueueAction) {
        viewModelScope.launch(Dispatchers.IO) {
            val queueSong = QueueSongEntity(
                id = song.id.toString(),
                segmentId = song.id.toString(),
                name = song.title,
                coverUrl = null,
                singerName = song.artist,
                durationMs = song.durationSeconds * 1000L,
                playUrl = null,
                sortOrderInSegment = 0
            )
            val result = when (action) {
                QueueAction.PlayNow -> playQueueRepository.playSongNow(queueSong)
                QueueAction.InsertNext -> playQueueRepository.insertSongToNext(
                    song = queueSong,
                    currentGlobalPosition = playbackQueueController.state.value.currentSong?.globalPosition
                )
                QueueAction.AppendToEnd -> playQueueRepository.addSongToTail(queueSong)
            }
            playbackQueueController.applyQueueMutationResult(result)
            playQueueRepository.preloadQueueWindow(window = playQueueRepository.visibleWindow.value)
        }
    }

    fun updateParams(playlist: Playlist) {
        queryParams.update { playlist }
    }
}
