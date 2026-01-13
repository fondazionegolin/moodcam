package com.moodcam.preset

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for storing custom film presets.
 */
@Database(
    entities = [FilmPreset::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(PresetConverters::class)
abstract class PresetDatabase : RoomDatabase() {
    
    abstract fun presetDao(): PresetDao
    
    companion object {
        private const val DATABASE_NAME = "moodcam_presets.db"
        
        @Volatile
        private var INSTANCE: PresetDatabase? = null
        
        fun getInstance(context: Context): PresetDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): PresetDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PresetDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*MIGRATIONS)
                .build()
        }
        
        // Migration definitions for future versions
        private val MIGRATIONS = arrayOf<Migration>(
            // Add migrations here as needed, e.g.:
            // MIGRATION_1_2
        )
        
        // Example migration template for future use:
        // private val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(database: SupportSQLiteDatabase) {
        //         database.execSQL("ALTER TABLE presets ADD COLUMN newField TEXT DEFAULT ''")
        //     }
        // }
    }
}
