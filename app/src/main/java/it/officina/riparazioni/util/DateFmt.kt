package it.officina.riparazioni.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFmt {
    private val short    = SimpleDateFormat("dd/MM", Locale.ITALIAN)
    private val full     = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)
    private val datetime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN)

    fun short(t: Long): String    = short.format(Date(t))
    fun full(t: Long): String     = full.format(Date(t))
    fun datetime(t: Long): String = datetime.format(Date(t))

    /** Formatta millisecondi come "Xh Ymin" oppure "Ymin" oppure "< 1 min" */
    fun durata(ms: Long): String {
        if (ms <= 0) return "0 min"
        val totMin = ms / 60_000
        if (totMin < 1) return "< 1 min"
        val h = totMin / 60
        val m = totMin % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}min"
            h > 0           -> "${h}h"
            else            -> "${m}min"
        }
    }
}
