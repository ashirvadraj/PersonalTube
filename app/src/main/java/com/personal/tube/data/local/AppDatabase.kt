package com.personal.tube.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.personal.tube.data.local.daos.BookmarkDao
import com.personal.tube.data.local.daos.DownloadDao
import com.personal.tube.data.local.daos.HistoryDao
import com.personal.tube.data.local.daos.SubscriptionDao
import com.personal.tube.data.local.entities.BookmarkEntity
import com.personal.tube.data.local.entities.DownloadEntity
import com.personal.tube.data.local.entities.SubscriptionEntity
import com.personal.tube.data.local.entities.WatchHistoryEntity

@Database(
    entities = [
        WatchHistoryEntity::class,
        SubscriptionEntity::class,
        BookmarkEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "personal_tube_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
