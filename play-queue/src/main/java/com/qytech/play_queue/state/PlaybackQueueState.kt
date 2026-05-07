package com.qytech.play_queue.state

import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.data.PlaybackMode
import com.qytech.play_queue.data.PreparedPlaybackItem
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSongEntity

data class PlaybackQueueState<S : IQueueSongEntity, SEG : IQueueSegmentEntity>(
    val currentSong: PlayableSong<S, SEG>? = null,
    val isPlaying: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.Sequence,
    val preparedPrevious: PreparedPlaybackItem<S, SEG>? = null,
    val preparedNext: PreparedPlaybackItem<S, SEG>? = null,
    val revision: Long = 0L
)
