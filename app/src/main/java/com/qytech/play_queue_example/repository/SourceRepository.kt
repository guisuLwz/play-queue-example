package com.qytech.play_queue_example.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.qytech.play_queue_example.global.SourceData.albums
import com.qytech.play_queue_example.global.SourceData.artists
import com.qytech.play_queue_example.global.SourceData.palettes
import com.qytech.play_queue_example.global.SourceData.playlistMoods
import com.qytech.play_queue_example.room.dao.SourceDao
import com.qytech.play_queue_example.room.entity.source.PlaylistSourceEntity
import com.qytech.play_queue_example.room.entity.source.SongSourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRepository @Inject constructor(
    private val sourceDao: SourceDao
) {

    suspend fun initPlaylists() {
        if (getPlaylistsCount() > 0) {
            return
        }
        sourceDao.upsertPlaylists(getPlaylists())
    }

    suspend fun getPlaylistsCount(): Int {
        return sourceDao.getPlaylistCount()
    }

    suspend fun initSongs() {
        if (getPlaylistsCount() == 0 || getSongsCount() > 0) {
            return
        }
        val playlists = sourceDao.getPlaylists()

        var totalCount = 0

        for (playlist in playlists) {
            totalCount += coroutineScope {
                val totalCount = playlist.totalCount
                val pageSize = 500
                val parallelism = 6
                val totalPages = (totalCount + pageSize - 1) / pageSize

                var count = 0

                (1..totalPages)
                    .chunked(parallelism)
                    .forEach { pageBatch ->
                        pageBatch.map { page ->
                            async(Dispatchers.IO) {
                                val from = (page - 1) * pageSize
                                val limit = minOf(pageSize, totalCount - from)
                                val end = from + limit

                                if (end > totalCount) return@async

                                val list = getSongs(playlist, from, end)
                                count += end - from
                                sourceDao.upsertSongs(list)
                            }
                        }.awaitAll()
                        Log.d("lwz", "${playlist.name} loaded count: $count")
                    }
                count
            }
            Log.d("lwz", "totalCount: $totalCount")
        }
    }

    suspend fun getSongsCount(): Int {
        return sourceDao.getSongCount()
    }

    fun playlistPager(): Pager<Int, PlaylistSourceEntity> {
        return Pager(
            config = PagingConfig(
                pageSize = PLAYLIST_PAGE_SIZE,
                initialLoadSize = PLAYLIST_PAGE_SIZE * 2,
                prefetchDistance = 6,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { sourceDao.getPlaylistPagingSource() },
        )
    }

    fun songPager(playlistId: Long): Pager<Int, SongSourceEntity> {
        return Pager(
            config = PagingConfig(
                pageSize = SONG_PAGE_SIZE,
                initialLoadSize = SONG_PAGE_SIZE * 2,
                prefetchDistance = SONG_PAGE_SIZE,
                enablePlaceholders = true,
            ),
            pagingSourceFactory = { sourceDao.getSongPagingSource(playlistId) },
        )
    }

    private fun getPlaylists(): List<PlaylistSourceEntity> {
        return List(PLAYLIST_COUNT) { index ->
            val displayIndex = index + 1
            PlaylistSourceEntity(
                id = displayIndex.toLong(),
                name = "${playlistMoods[index % playlistMoods.size]} $displayIndex",
                subtitle = if (index in 0 until MILLION_ROW_PLAYLIST_COUNT) {
                    "Large playlist seeded by metadata only"
                } else {
                    "Paged playlist backed by database rows"
                },
                totalCount = if (index in 0 until MILLION_ROW_PLAYLIST_COUNT) {
                    MILLION_ROW_PLAYLIST_SIZE
                } else {
                    42 + (index % 9) * 7
                },
                colorArgb = palettes[index % palettes.size],
            )
        }
    }

    private fun getSongs(
        playlist: PlaylistSourceEntity,
        from: Int,
        end: Int
    ): List<SongSourceEntity> {
        return List(end - from) { index ->
            val number = from + index + 1
            SongSourceEntity(
                id = playlist.id * 10_000_000 + number,
                playlistId = playlist.id,
                songName = "Track ${number.toString().padStart(2, '0')} - ${playlist.name}",
                artist = artists[(index + playlist.id.toInt()) % artists.size],
                album = albums[(index / 3 + playlist.id.toInt()) % albums.size],
                durationSeconds = 154 + ((index * 17 + playlist.id.toInt()) % 132),
            )
        }
    }

    companion object {
        private const val PLAYLIST_COUNT = 96
        private const val PLAYLIST_PAGE_SIZE = 12
        private const val SONG_PAGE_SIZE = 50
        private const val QUEUE_PAGE_SIZE = 50
        internal const val MAX_LOAD_SIZE = 250
        private const val PLAYBACK_PRELOAD_BEHIND = 20
        private const val PLAYBACK_PRELOAD_AHEAD = 100
        private const val MILLION_ROW_PLAYLIST_COUNT = 3
        private const val MILLION_ROW_PLAYLIST_SIZE = 350_000
        private const val SONG_ID_STRIDE = 10_000_000L
    }

}