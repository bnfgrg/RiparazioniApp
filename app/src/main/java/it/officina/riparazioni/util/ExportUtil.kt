package it.officina.riparazioni.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import it.officina.riparazioni.data.Riparazione
import it.officina.riparazioni.data.StatoRiparazione
import it.officina.riparazioni.data.TipoDispositivo
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

object ExportUtil {

    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN)
    private val dFmt  = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)

    // ─── EXPORT ────────────────────────────────────────────────────────────────

    fun esportaCsv(ctx: Context, riparazioni: List<Riparazione>): File {
        val outDir = File(ctx.filesDir, "export").apply { if (!exists()) mkdirs() }
        val timestamp = DateFmt.datetime(System.currentTimeMillis())
            .replace("/", "-").replace(":", "-").replace(" ", "_")
        val file = File(outDir, "riparazioni_$timestamp.csv")

        FileWriter(file, Charsets.UTF_8).use { w ->
            w.appendLine(
                "Numero;Cliente;Telefono;Tipo;Marca/Modello;Problema;" +
                "Note;Prezzo;Stato;Data Ingresso;Data Pronto;Data Consegna;Tempo Lavoro (ms)"
            )
            riparazioni.forEach { r ->
                w.appendLine(
                    listOf(
                        r.numeroProgressivo, r.cliente, r.telefono,
                        r.tipoDispositivo.label, r.marcaModello,
                        r.problema.replace("\n", " "),
                        r.noteInterne.replace("\n", " "),
                        r.prezzoRiparazione, r.stato.label,
                        DateFmt.full(r.dataIngresso),
                        r.dataPronto?.let { DateFmt.datetime(it) } ?: "",
                        r.dataConsegna?.let { DateFmt.datetime(it) } ?: "",
                        r.tempoLavoroMs.toString()
                    ).joinToString(";") { campo -> "\"${campo.replace("\"", "\"\"")}\"" }
                )
            }
        }
        return file
    }

    fun condividiCsv(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Esportazione riparazioni")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Condividi esportazione").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ─── IMPORT ────────────────────────────────────────────────────────────────

    fun leggiCsv(ctx: Context, uri: Uri): List<Riparazione> {
        val righe = mutableListOf<Riparazione>()
        ctx.contentResolver.openInputStream(uri)?.use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val header = reader.readLine() ?: throw IllegalArgumentException("File CSV vuoto")
            if (!header.startsWith("Numero")) throw IllegalArgumentException("Intestazione CSV non valida")
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val campi = parseCsvLine(line)
                if (campi.size < 12) return@forEachLine
                try {
                    righe.add(Riparazione(
                        id = 0,
                        numeroProgressivo = campi[0],
                        cliente = campi[1],
                        telefono = campi[2],
                        tipoDispositivo = TipoDispositivo.values().firstOrNull { it.label == campi[3] } ?: TipoDispositivo.ALTRO,
                        marcaModello = campi[4],
                        problema = campi[5],
                        noteInterne = campi[6],
                        prezzoRiparazione = campi[7],
                        stato = StatoRiparazione.values().firstOrNull { it.label == campi[8] } ?: StatoRiparazione.IN_ATTESA,
                        dataIngresso = parseData(campi[9]) ?: System.currentTimeMillis(),
                        dataPronto = parseData(campi[10]),
                        dataConsegna = parseData(campi[11]),
                        tempoLavoroMs = if (campi.size > 12) campi[12].toLongOrNull() ?: 0L else 0L,
                        timerAvviatoAl = null,
                        fotoPaths = emptyList()
                    ))
                } catch (_: Exception) { /* riga malformata: salta */ }
            }
        } ?: throw IllegalArgumentException("Impossibile aprire il file")
        return righe
    }

    // ─── CARTELLA FOTO (modifica 3) ─────────────────────────────────────────────

    fun pathFoto(ctx: Context): String = File(ctx.filesDir, "photos").absolutePath

    /** Apre la galleria/file manager sulla cartella foto */
    fun apriCartellaFoto(ctx: Context) {
        // Tenta di aprire con il file manager (Intent ACTION_VIEW su cartella)
        val dir = File(ctx.filesDir, "photos").apply { if (!exists()) mkdirs() }
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: apre la galleria di sistema per la selezione immagini
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            } catch (_: Exception) { /* nessun gestore disponibile */ }
        }
    }

    // ─── UTILITY PRIVATI ────────────────────────────────────────────────────────

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            if (line[i] == '"') {
                val sb = StringBuilder(); i++
                while (i < line.length) {
                    if (line[i] == '"' && i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i += 2 }
                    else if (line[i] == '"') { i++; break }
                    else sb.append(line[i++])
                }
                result.add(sb.toString())
                if (i < line.length && line[i] == ';') i++
            } else {
                val end = line.indexOf(';', i).takeIf { it >= 0 } ?: line.length
                result.add(line.substring(i, end)); i = end + 1
            }
        }
        return result
    }

    private fun parseData(s: String): Long? {
        if (s.isBlank()) return null
        return try { dtFmt.parse(s)?.time }
        catch (_: Exception) {
            try { dFmt.parse(s)?.time } catch (_: Exception) { null }
        }
    }
}
