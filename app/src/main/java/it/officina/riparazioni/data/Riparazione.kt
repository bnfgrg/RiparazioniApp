package it.officina.riparazioni.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StatoRiparazione(val label: String) {
    IN_ATTESA("In attesa"),
    IN_LAVORAZIONE("In lavorazione"),
    PRONTO("Pronto"),
    CONSEGNATO("Consegnato")
}

enum class TipoDispositivo(val label: String) {
    MOTOSEGA("Motosega"),
    DECESPUGLIATORE("Decespugliatore"),
    RASAERBA("Rasaerba (Tagliaerba)"),
    MOTOZAPPA("Motozappa"),
    MOTOCOLTIVATORE("Motocoltivatore"),
    TAGLIASIEPI("Tagliasiepi"),
    MOTOFALCIATRICE("Motofalciatrice"),
    SOFFIATORE_ASPIRATORE("Soffiatore / Aspiratore"),
    BIOTRITURATORE("Biotrituratore"),
    ABBACCHIATORE("Abbacchiatore (Scuotitore olive)"),
    ATOMIZZATORE("Atomizzatore / Irroratore a motore"),
    MOTOPOMPA("Motopompa"),
    POTATORE_TELESCOPICO("Potatore telescopico"),
    MOTO_CARRIOLA("Moto-carriola (Mini-transporter)"),
    GENERATORE("Generatore di corrente"),
    COMPRESSORE("Compressore"),
    ALTRO("Altro")
}

@Entity(tableName = "riparazioni")
data class Riparazione(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val numeroProgressivo: String = "",
    val cliente: String = "",
    val telefono: String = "",
    val tipoDispositivo: TipoDispositivo = TipoDispositivo.MOTOSEGA,
    val marcaModello: String = "",
    val problema: String = "",
    val noteInterne: String = "",
    val prezzoRiparazione: String = "",          // testo libero es. "45,00"
    val stato: StatoRiparazione = StatoRiparazione.IN_ATTESA,
    val dataIngresso: Long = System.currentTimeMillis(),
    val dataPronto: Long? = null,                // timestamp cambio stato -> PRONTO
    val dataConsegna: Long? = null,              // timestamp cambio stato -> CONSEGNATO
    val fotoPaths: List<String> = emptyList()
)
