package com.qytech.play_queue.remote

interface INetworkSegment {
    val id: String
    val name: String
    val type: String
    val coverUrl: String?
    val totalCount: Int?
}

interface INetworkSong {
    val id: String
    val name: String
    val coverUrl: String?
    val artist: String
    val durationMs: Long
    val playUrl: String?
    val sortOrderInSegment: Int
}

interface INetworkPage<S: INetworkSong, SEG: INetworkSegment> {
    val segment: SEG
    val page: Int
    val pageSize: Int
    val songs: List<S>
    val hasMore: Boolean
}