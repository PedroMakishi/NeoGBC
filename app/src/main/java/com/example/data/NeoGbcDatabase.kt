package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RomEntity::class,
        SaveStateEntity::class,
        CheatEntity::class,
        ScannedDirectoryEntity::class,
        ControlLayoutEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NeoGbcDatabase : RoomDatabase() {
    abstract fun dao(): NeoGbcDao

    companion object {
        @Volatile
        private var INSTANCE: NeoGbcDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `control_layouts`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `control_layouts` (
                        `profileId` TEXT NOT NULL,
                        `dpadX` REAL NOT NULL,
                        `dpadY` REAL NOT NULL,
                        `dpadScale` REAL NOT NULL,
                        `dpadOpacity` REAL NOT NULL,
                        `abX` REAL NOT NULL,
                        `abY` REAL NOT NULL,
                        `abScale` REAL NOT NULL,
                        `abOpacity` REAL NOT NULL,
                        `startSelectX` REAL NOT NULL,
                        `startSelectY` REAL NOT NULL,
                        `startSelectScale` REAL NOT NULL,
                        `startSelectOpacity` REAL NOT NULL,
                        `turboX` REAL NOT NULL,
                        `turboY` REAL NOT NULL,
                        `turboScale` REAL NOT NULL,
                        `turboOpacity` REAL NOT NULL,
                        `hapticEnabled` INTEGER NOT NULL,
                        `hapticIntensity` REAL NOT NULL,
                        `dpadDeadzone` REAL NOT NULL,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): NeoGbcDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NeoGbcDatabase::class.java,
                    "neogbc_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
