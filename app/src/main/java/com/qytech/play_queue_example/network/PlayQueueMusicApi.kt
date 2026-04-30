package com.qytech.play_queue_example.network

import com.qytech.play_queue.remote.BaseMusicApi
import com.qytech.play_queue.remote.INetworkPage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayQueueMusicApi @Inject constructor(

): BaseMusicApi<NetworkSong, NetworkSegment, NetworkPage>() {

    override suspend fun fetchSongs(
        segmentId: String,
        page: Int,
        pageSize: Int
    ): INetworkPage<NetworkSong, NetworkSegment> {

        return NetworkPage(
            segment = NetworkSegment(
                id = "",
                name = "",
                type = "",
                coverUrl = null,
                totalCount = 0
            ),
            page = page,
            pageSize = pageSize,
            songs = emptyList(),
            hasMore = false
        )
    }
}