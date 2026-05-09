package com.qytech.play_queue_example.player

import com.qytech.play_queue.data.PlayableSong
import com.qytech.play_queue.playback.BasePlaybackQueueController
import com.qytech.play_queue.playback.intf.PlaybackQueuePlayerDelegate
import com.qytech.play_queue_example.repository.PlayQueueRepository
import com.qytech.play_queue_example.room.entity.queue.QueueSegmentEntity
import com.qytech.play_queue_example.room.entity.queue.QueueSongEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueController @Inject constructor(
    private val playQueueRepository: PlayQueueRepository
): BasePlaybackQueueController<QueueSongEntity, QueueSegmentEntity>(
    queueSource = playQueueRepository,
) {
    override fun getPlayerDelegate() = object : PlaybackQueuePlayerDelegate<QueueSongEntity, QueueSegmentEntity> {
        override suspend fun onPreparedPrevious(
            hasPrepared: Boolean,
            previous: PlayableSong<QueueSongEntity, QueueSegmentEntity>
        ) {

        }

        override suspend fun onPreparedNext(
            hasPrepared: Boolean,
            next: PlayableSong<QueueSongEntity, QueueSegmentEntity>
        ) {

        }

        override suspend fun onPreparePlay(
            current: PlayableSong<QueueSongEntity, QueueSegmentEntity>
        ) {

        }

        override suspend fun onAutoPreparedPrevious() {

        }

        override suspend fun onAutoNextBlocked() {

        }

        override suspend fun onAutoNextReleased(
            next: PlayableSong<QueueSongEntity, QueueSegmentEntity>?
        ) {

        }

        override suspend fun play() {

        }

        override suspend fun pause() {

        }
    }

}
