package com.qytech.play_queue.playback.intf

import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSongEntity

interface PlayableQueueSource<S : IQueueSongEntity, SEG : IQueueSegmentEntity> {
    suspend fun totalSize(): Int

    suspend fun getPlayableSongAt(
        globalPosition: Int,
        forceRetry: Boolean = false
    ): PlayableSong<S, SEG>?

    suspend fun preloadPlaybackAround(
        globalPosition: Int,
        lookBehindPages: Int = 1,
        lookAheadPages: Int = 4
    )
}