package com.qytech.play_queue_example.state

import androidx.compose.runtime.Immutable
import com.qytech.play_queue.data.PlaybackMode
import com.qytech.play_queue_example.model.QueueSong

@Immutable
data class PlaybackUiState(
    val currentPlayingSong: QueueSong? = null,
    val isPlaying: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.Sequence,
    val hasPrevious: Boolean = true,
    val hasNext: Boolean = true
) {
}