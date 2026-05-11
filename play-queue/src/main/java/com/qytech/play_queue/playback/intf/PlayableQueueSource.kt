package com.qytech.play_queue.playback.intf

import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.local.IQueueSegmentEntity
import com.qytech.play_queue.local.IQueueSongEntity

interface PlayableQueueSource<S : IQueueSongEntity, SEG : IQueueSegmentEntity> {
    fun setCurrentPlayableSong(song: PlayableSong<S, SEG>?) = Unit

    suspend fun totalSize(): Int

    suspend fun getPlayableSongAt(
        globalPosition: Int,
        forceRetry: Boolean = false
    ): PlayableSong<S, SEG>?

    suspend fun getSongGlobalPosition(songId: String): Int?

    suspend fun getSongSegmentId(songId: String): String?

    suspend fun findPreviousPlayableSong(
        globalPosition: Int?,
        wrap: Boolean = false
    ): PlayableSong<S, SEG>?

    suspend fun findNextPlayableSong(
        globalPosition: Int?,
        wrap: Boolean = false
    ): PlayableSong<S, SEG>?

    suspend fun preloadPlaybackAround(
        globalPosition: Int,
        lookBehindPages: Int = 1,
        lookAheadPages: Int = 4
    )
}
