package com.qytech.play_queue.playback.intf

import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSongEntity

interface PlaybackQueuePlayerDelegate<S : IQueueSongEntity, SEG : IQueueSegmentEntity> {

    suspend fun onPreparedPrevious(
        hasPrepared: Boolean,
        previous: PlayableSong<S, SEG>
    )

    suspend fun onPreparedNext(
        hasPrepared: Boolean,
        next: PlayableSong<S, SEG>
    )

    suspend fun onPreparePlay(
        current: PlayableSong<S, SEG>
    )

    suspend fun onAutoPreparedPrevious()

    suspend fun play()

    suspend fun pause()
}