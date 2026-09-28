package com.aetherweb.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ChatMessageEntity::class, CallLogEntity::class], version = 1, exportSchema = false)
abstract class MeshChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile
        private var INSTANCE: MeshChatDatabase? = null

        fun getDatabase(context: Context): MeshChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeshChatDatabase::class.java,
                    "mesh_chat_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
