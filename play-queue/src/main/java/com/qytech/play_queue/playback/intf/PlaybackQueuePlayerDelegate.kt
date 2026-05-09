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

    /**
     * Called before a queue mutation that may replace the prepared next item.
     * The player should hold auto-advance to the old prepared next until onAutoNextReleased.
     */
    suspend fun onAutoNextBlocked()

    /**
     * Called after the queue mutation and next preparation finish.
     * If playback reached the end while blocked, the player should continue with this next item.
     */
    suspend fun onAutoNextReleased(
        next: PlayableSong<S, SEG>?
    )

    suspend fun play()

    suspend fun pause()
}
