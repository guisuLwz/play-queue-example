package com.qytech.play_queue.remote

abstract class BaseMusicApi<S: INetworkSong, SEG: INetworkSegment, SEG_PAGE: INetworkPage<S, SEG>> {

    /**
     * key: segmentId-page
     */
    protected val attemptCounts = mutableMapOf<String, Int>()

    abstract suspend fun fetchSongs(
        segmentId: String,
        segmentType: String,
        page: Int,
        pageSize: Int
    ): INetworkPage<S, SEG>
}