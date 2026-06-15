package it.officina.riparazioni.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromStato(s: StatoRiparazione): String = s.name
    @TypeConverter fun toStato(s: String): StatoRiparazione = StatoRiparazione.valueOf(s)
    @TypeConverter fun fromTipo(t: TipoDispositivo): String = t.name
    @TypeConverter fun toTipo(t: String): TipoDispositivo = TipoDispositivo.valueOf(t)
    @TypeConverter fun fromList(list: List<String>): String = list.joinToString("|||")
    @TypeConverter fun toList(s: String): List<String> = if (s.isEmpty()) emptyList() else s.split("|||")
}
