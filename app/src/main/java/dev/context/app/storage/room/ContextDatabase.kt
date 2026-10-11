package dev.context.app.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AutomationEntity::class,
        LocationEntity::class,
        ExecutionLogEntity::class,
        VariableEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ContextDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao
    abstract fun locationDao(): LocationDao
    abstract fun logDao(): LogDao
    abstract fun variableDao(): VariableDao

    companion object {
        @Volatile
        private var instance: ContextDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE locations ADD COLUMN cellIds TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE automations ADD COLUMN useGpsForLocation INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): ContextDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ContextDatabase::class.java,
                    "context.db",
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}