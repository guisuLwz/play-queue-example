package com.qytech.play_queue.data

import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSongEntity

data class PreparedPlaybackItem<S : IQueueSongEntity, SEG : IQueueSegmentEntity>(
    val position: Int,
    val song: PlayableSong<S, SEG>
)