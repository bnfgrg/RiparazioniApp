package it.officina.riparazioni.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Riparazione::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun riparazioneDao(): RiparazioneDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Migrazione da v1 (prima release) a v2 (aggiunta colonne)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE riparazioni ADD COLUMN prezzoRiparazione TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE riparazioni ADD COLUMN dataPronto INTEGER")
            }
        }

        fun getInstance(ctx: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "riparazioni.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
            }
        }
    }
}
