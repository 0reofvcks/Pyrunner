package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PythonScript::class, ScriptRun::class], version = 1, exportSchema = false)
abstract class ScriptDatabase : RoomDatabase() {
    abstract fun scriptDao(): ScriptDao

    companion object {
        @Volatile
        private var INSTANCE: ScriptDatabase? = null

        fun getDatabase(context: Context): ScriptDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScriptDatabase::class.java,
                    "pyrunner_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
