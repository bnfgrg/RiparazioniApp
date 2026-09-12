package it.officina.riparazioni.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Riparazione::class, Cliente::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun riparazioneDao(): RiparazioneDao
    abstract fun clienteDao(): ClienteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE riparazioni ADD COLUMN prezzoRiparazione TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE riparazioni ADD COLUMN dataPronto INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE riparazioni ADD COLUMN tempoLavoroMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE riparazioni ADD COLUMN timerAvviatoAl INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Crea tabella clienti
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS clienti (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        nome TEXT NOT NULL DEFAULT '',
                        cognome TEXT NOT NULL DEFAULT '',
                        telefono TEXT NOT NULL DEFAULT '',
                        indirizzo TEXT NOT NULL DEFAULT '',
                        note TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                // Aggiunge FK clienteId a riparazioni (nullable)
                db.execSQL("ALTER TABLE riparazioni ADD COLUMN clienteId INTEGER")
            }
        }

        fun getInstance(ctx: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext, AppDatabase::class.java, "riparazioni.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                 .build().also { INSTANCE = it }
            }
        }
    }
}
