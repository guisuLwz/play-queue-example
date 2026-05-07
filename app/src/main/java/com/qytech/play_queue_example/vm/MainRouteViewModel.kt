package com.qytech.play_queue_example.vm

import androidx.lifecycle.viewModelScope
import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue_example.base.BaseViewModel
import com.qytech.play_queue_example.player.PlaybackQueueController
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSongEntity
import com.qytech.play_queue_example.room.entity.queue.toUiModel
import com.qytech.play_queue_example.state.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainRouteViewModel @Inject constructor(
    private val playbackQueueController: PlaybackQueueController
): BaseViewModel() {

    val playbackState = playbackQueueController.state
        .map { state ->
            PlaybackUiState(
                currentPlayingSong = state.currentSong?.toUiModel(state.isPlaying),
                isPlaying = state.isPlaying,
                playbackMode = state.playbackMode,
                hasPrevious = playbackQueueController.hasPrevious(),
                hasNext = playbackQueueController.hasNext()
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackUiState()
        )

    fun onTogglePlay() {
        viewModelScope.launch {
            playbackQueueController.togglePlayPause()
        }
    }

    fun onPrevious() {
        viewModelScope.launch {
            playbackQueueController.previous()
        }
    }

    fun onNext() {
        viewModelScope.launch {
            playbackQueueController.next()
        }
    }

    fun onPlaybackModeClick() {
        viewModelScope.launch {
            playbackQueueController.cyclePlaybackMode()
        }
    }

    private fun PlayableSong<QueueSongEntity, QueueSegmentEntity>.toUiModel(
        isPlaying: Boolean
    ) = song.toUiModel(
        globalPosition = globalPosition,
        playlist = location.segment,
        isPlaying = isPlaying
    )
}
