package com.tunnellight.stop_scroll.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScrollSessionEntity::class], version = 1, exportSchema = true)
abstract class StopScrollDatabase : RoomDatabase() {

    abstract fun sessions(): ScrollSessionDao

    companion object {
        fun build(context: Context): StopScrollDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                StopScrollDatabase::class.java,
                "stopscroll.db",
            ).build()
    }
}
