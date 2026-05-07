package com.qytech.play_queue.playback.intf

import com.qytech.play_queue.data.QueueMutationResult
import com.qytech.play_queue.local.IQueueSongEntity

interface SongQueueActionTarget<S : IQueueSongEntity> {

    suspend fun playSongNow(song: S): QueueMutationResult

    suspend fun insertSongToNext(song: S, currentGlobalPosition: Int?): QueueMutationResult

    suspend fun addSongToTail(song: S): QueueMutationResult

}