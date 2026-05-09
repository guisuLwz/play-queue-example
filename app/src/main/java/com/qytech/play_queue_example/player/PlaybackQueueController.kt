package com.qytech.play_queue_example.player

import com.qytech.play_queue.playback.BasePlaybackQueueController
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
    onPreparedPrevious = { hasPrepared, prev ->

    },
    onPreparedNext = { hasPrepared, next ->

    },
    onPreparePlay = {

    },
    onAutoPreparedPrevious = {

    }
) {

}