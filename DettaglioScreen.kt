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
}
