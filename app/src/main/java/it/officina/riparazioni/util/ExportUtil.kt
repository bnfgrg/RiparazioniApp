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

    // ─── EXPORT ────────────────────────────────────────────────────────────────

    fun esportaCsv(ctx: Context, riparazioni: List<Riparazione>): File {
        val ts = DateFmt.datetime(System.currentTimeMillis())
            .replace("/", "-").replace(":", "-").replace(" ", "_")
        val outDir = File(ctx.filesDir, "export").apply { if (!exists()) mkdirs() }
        val file = File(outDir, "riparazioni_$ts.csv")

        FileWriter(file, Charsets.UTF_8).use { w ->
            w.appendLine(HEADER)
            riparazioni.forEach { r ->
                w.appendLine(rigaCsv(r))
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

    sealed class ImportResult {
        data class Success(val totale: Int, val nuovi: Int, val aggiornati: Int, val saltati: Int) : ImportResult()
        data class Error(val msg: String) : ImportResult()
    }

    enum class ModalitaImport { TOTALE, AGGIUNTIVA_SOVRASCRIVI, AGGIUNTIVA_NUOVI }

    /**
     * Legge il CSV dall'URI e restituisce la lista di Riparazione parsate.
     * Lancia eccezione se il file non è valido.
     */
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
                    righe.add(
                        Riparazione(
                            id = 0, // verrà gestito in base alla modalità
                            numeroProgressivo = campi[0],
                            cliente = campi[1],
                            telefono = campi[2],
                            tipoDispositivo = TipoDispositivo.values()
                                .firstOrNull { it.label == campi[3] } ?: TipoDispositivo.ALTRO,
                            marcaModello = campi[4],
                            problema = campi[5],
                            noteInterne = campi[6],
                            prezzoRiparazione = campi[7],
                            stato = StatoRiparazione.values()
                                .firstOrNull { it.label == campi[8] } ?: StatoRiparazione.IN_ATTESA,
                            dataIngresso = parseData(campi[9]) ?: System.currentTimeMillis(),
                            dataPronto = parseData(campi[10]),
                            dataConsegna = parseData(campi[11]),
                            tempoLavoroMs = if (campi.size > 12) campi[12].toLongOrNull() ?: 0L else 0L,
                            timerAvviatoAl = null,
                            fotoPaths = emptyList()
                        )
                    )
                } catch (_: Exception) { /* riga malformata: salta */ }
            }
        } ?: throw IllegalArgumentException("Impossibile aprire il file")
        return righe
    }

    // ─── INTERNO ───────────────────────────────────────────────────────────────

    private const val HEADER =
        "Numero;Cliente;Telefono;Tipo;Marca/Modello;Problema;Note;Prezzo;Stato;" +
        "Data Ingresso;Data Pronto;Data Consegna;Tempo Lavoro (ms)"

    private fun rigaCsv(r: Riparazione): String = listOf(
        r.numeroProgressivo, r.cliente, r.telefono,
        r.tipoDispositivo.label, r.marcaModello,
        r.problema.replace("\n", " "), r.noteInterne.replace("\n", " "),
        r.prezzoRiparazione, r.stato.label,
        DateFmt.full(r.dataIngresso),
        r.dataPronto?.let { DateFmt.datetime(it) } ?: "",
        r.dataConsegna?.let { DateFmt.datetime(it) } ?: "",
        r.tempoLavoroMs.toString()
    ).joinToString(";") { campo -> "\"${campo.replace("\"", "\"\"")}\"" }

    /** Parser CSV semplice che gestisce campi quotati con ; interno */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            if (line[i] == '"') {
                val sb = StringBuilder()
                i++ // salta "
                while (i < line.length) {
                    if (line[i] == '"' && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i += 2
                    } else if (line[i] == '"') {
                        i++; break
                    } else {
                        sb.append(line[i++])
                    }
                }
                result.add(sb.toString())
                if (i < line.length && line[i] == ';') i++
            } else {
                val end = line.indexOf(';', i).takeIf { it >= 0 } ?: line.length
                result.add(line.substring(i, end))
                i = end + 1
            }
        }
        return result
    }

    private fun parseData(s: String): Long? {
        if (s.isBlank()) return null
        return try {
            dtFmt.parse(s)?.time
        } catch (_: Exception) {
            try {
                SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN).parse(s)?.time
            } catch (_: Exception) { null }
        }
    }

    fun pathFoto(ctx: Context): String = File(ctx.filesDir, "photos").absolutePath

    /** Apre la cartella foto nel file manager/galleria di sistema */
    fun apriCartellaFoto(ctx: Context) {
        val dir = File(ctx.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dir)
        // Prova prima con ACTION_VIEW sulla cartella
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: apre il file manager generico
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { ctx.startActivity(fallback) } catch (_: Exception) {}
        }
    }
}
