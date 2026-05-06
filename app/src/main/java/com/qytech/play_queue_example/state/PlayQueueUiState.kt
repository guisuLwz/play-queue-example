package com.qytech.play_queue_example.state

import com.qytech.play_queue.data.PlaybackMode
import com.qytech.play_queue.model.QueueRow
import com.qytech.play_queue.state.IPlayQueueUiState
import com.qytech.play_queue_example.model.QueueSong

data class PlayQueueUiState(
    override val totalCount: Int = 0,
    override val rowsByPosition: Map<Int, QueueRow> = emptyMap(),
    override val segmentStates: List<QueueSegmentLoadState> = emptyList(),
    override val currentPlayingSong: QueueSong? = null,
    override val isPlaying: Boolean = false,
    override val playbackMode: PlaybackMode = PlaybackMode.Sequence,
    override val visibleWindow: IntRange = 0..0,
    override val hasPrevious: Boolean = true,
    override val hasNext: Boolean = true
): IPlayQueueUiState<QueueSong, QueueSegmentLoadState>