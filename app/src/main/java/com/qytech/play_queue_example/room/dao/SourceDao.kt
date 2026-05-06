package com.qytech.play_queue_example.room.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qytech.play_queue_example.room.entity.source.PlaylistSourceEntity
import com.qytech.play_queue_example.room.entity.source.SongSourceEntity

@Dao
interface SourceDao {

    @Query(
        """
        SELECT id, name, subtitle, totalCount, colorArgb
        FROM source_playlists
        ORDER BY id
        """,
    )
    fun getPlaylistPagingSource(): PagingSource<Int, PlaylistSourceEntity>

    @Query("SELECT COUNT(*) FROM source_playlists")
    suspend fun getPlaylistCount(): Int

    @Query("SELECT * FROM source_playlists")
    suspend fun getPlaylists(): List<PlaylistSourceEntity>

    @Query("SELECT * FROM source_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(playlists: List<PlaylistSourceEntity>)

    @Query("SELECT * FROM source_songs WHERE playlistId = :playlistId ORDER BY indexInSegment ASC")
    fun getSongPagingSource(playlistId: Long): PagingSource<Int, SongSourceEntity>

    @Query("SELECT COUNT(*) FROM source_songs")
    suspend fun getSongCount(): Int

    @Query("SELECT * FROM source_songs WHERE playlistId = :playlistId ORDER BY indexInSegment ASC LIMIT :pageSize OFFSET :offset")
    suspend fun getSongsByPlaylistId(playlistId: String, offset: Int, pageSize: Int): List<SongSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<SongSourceEntity>)
}
