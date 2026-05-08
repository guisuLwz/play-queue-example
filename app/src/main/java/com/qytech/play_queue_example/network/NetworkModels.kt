package com.qytech.play_queue_example.network

import com.qytech.play_queue.remote.INetworkPage
import com.qytech.play_queue.remote.INetworkSegment
import com.qytech.play_queue.remote.INetworkSong

data class NetworkSegment(
    override val id: String,
    override val name: String,
    override val type: String,
    override val coverUrl: String?,
    override val totalCount: Int?
) : INetworkSegment

data class NetworkSong(
    override val id: String,
    override val name: String,
    override val coverUrl: String?,
    override val singerName: String,
    override val durationMs: Long,
    override val playUrl: String?,
    override val sortOrderInSegment: Int,
) : INetworkSong

data class NetworkPage(
    override val segment: NetworkSegment,
    override val page: Int,
    override val pageSize: Int,
    override val songs: List<NetworkSong>,
    override val hasMore: Boolean
) : INetworkPage<NetworkSong, NetworkSegment>
