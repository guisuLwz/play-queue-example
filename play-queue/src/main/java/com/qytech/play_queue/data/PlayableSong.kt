package com.qytech.play_queue.data

import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSongEntity

data class PlayableSong<S : IQueueSongEntity, SEG : IQueueSegmentEntity>(
    val globalPosition: Int,
    val location: LocatedPosition<SEG>,
    val song: S
)