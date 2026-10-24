package it.officina.riparazioni

import android.app.Application
import it.officina.riparazioni.data.AppDatabase
import it.officina.riparazioni.data.RiparazioneRepository

class RiparazioniApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { RiparazioneRepository(database.riparazioneDao()) }
}
