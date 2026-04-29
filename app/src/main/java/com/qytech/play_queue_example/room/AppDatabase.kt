package com.qytech.play_queue_example.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.qytech.play_queue_example.room.dao.PlayQueueDao
import com.qytech.play_queue_example.room.dao.SourceDao
import com.qytech.play_queue_example.room.entity.source.PlaylistSourceEntity
import com.qytech.play_queue_example.room.entity.source.SongSourceEntity

@Database(
    entities = [
        PlaylistSourceEntity::class,
        SongSourceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase: RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun playQueueDao(): PlayQueueDao
}