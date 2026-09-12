package it.officina.riparazioni.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clienti")
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String = "",
    val cognome: String = "",
    val telefono: String = "",
    val indirizzo: String = "",
    val note: String = ""
) {
    val nomeCompleto: String get() = "$nome $cognome".trim()
}
