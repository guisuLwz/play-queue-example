package com.qytech.play_queue_example.network

import android.util.Log
import com.qytech.play_queue.remote.BaseMusicApi
import com.qytech.play_queue.remote.INetworkPage
import com.qytech.play_queue_example.room.dao.PlayQueueDao
import com.qytech.play_queue_example.room.dao.SourceDao
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log
import kotlin.math.min

@Singleton
class PlayQueueMusicApi @Inject constructor(
    private val sourceDao: SourceDao
): BaseMusicApi<NetworkSong, NetworkSegment, NetworkPage>() {

    override suspend fun fetchSongs(
        segmentId: String,
        segmentType: String,
        page: Int,
        pageSize: Int
    ): INetworkPage<NetworkSong, NetworkSegment> {
        delay(180)
        val segment = sourceDao.getPlaylistById(segmentId.trim().toLong()) ?: error("歌单不存在：$segmentId")
        val key = "$segmentId-$page"
        val attempt = attemptCounts.getOrDefault(key, 0) + 1
        attemptCounts[key] = attempt

        val from = (page - 1) * pageSize
        val to = min(from + pageSize, segment.totalCount)
        val songs = if (from >= segment.totalCount) {
            emptyList<NetworkSong>()
        } else {
            sourceDao.getSongsByPlaylistId(segmentId, from, pageSize)
                .map { entity ->
                    val number = entity.indexInSegment + 1
                    NetworkSong(
                        id = entity.id.toString(),
                        name = "${segment.name} Track $number",
                        coverUrl = null,
                        artist = entity.artist,
                        durationMs = entity.durationSeconds * 1000L,
                        playUrl = "https://example.com/${segment}/${number}.mp3",
                        sortOrderInSegment = entity.indexInSegment
                    )
                }
        }

        return NetworkPage(
            segment = NetworkSegment(
                id = segmentId,
                name = segment.name,
                type = segmentType,
                coverUrl = null,
                totalCount = segment.totalCount
            ),
            page = page,
            pageSize = pageSize,
            songs = songs,
            hasMore = to < segment.totalCount
        )
    }
}
