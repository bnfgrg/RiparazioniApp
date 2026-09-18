package it.officina.riparazioni.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import it.officina.riparazioni.data.Cliente
import it.officina.riparazioni.data.Riparazione
import it.officina.riparazioni.data.StatoChiamata
import it.officina.riparazioni.data.StatoRiparazione
import it.officina.riparazioni.data.TipoDispositivo
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ExportUtil {

    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN)
    private val dFmt  = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)

    // ─── EXPORT ZIP (riparazioni + clienti) ────────────────────────────────────

    /**
     * Esporta un file ZIP contenente due CSV:
     *  - riparazioni.csv  (con colonna clienteId per collegare al cliente)
     *  - clienti.csv
     */
    fun esportaZip(ctx: Context, riparazioni: List<Riparazione>, clienti: List<Cliente>): File {
        val outDir = File(ctx.filesDir, "export").apply { if (!exists()) mkdirs() }
        val ts = DateFmt.datetime(System.currentTimeMillis())
            .replace("/", "-").replace(":", "-").replace(" ", "_")
        val zipFile = File(outDir, "backup_$ts.zip")

        ZipOutputStream(zipFile.outputStream()).use { zos ->
            // CSV riparazioni
            zos.putNextEntry(ZipEntry("riparazioni.csv"))
            zos.write(csvRiparazioni(riparazioni).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            // CSV clienti
            zos.putNextEntry(ZipEntry("clienti.csv"))
            zos.write(csvClienti(clienti).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return zipFile
    }

    /** Mantiene anche esportazione solo-CSV per retrocompatibilità */
    fun esportaCsv(ctx: Context, riparazioni: List<Riparazione>): File {
        val outDir = File(ctx.filesDir, "export").apply { if (!exists()) mkdirs() }
        val ts = DateFmt.datetime(System.currentTimeMillis())
            .replace("/", "-").replace(":", "-").replace(" ", "_")
        val file = File(outDir, "riparazioni_$ts.csv")
        FileWriter(file, Charsets.UTF_8).use { it.write(csvRiparazioni(riparazioni)) }
        return file
    }

    private fun csvRiparazioni(riparazioni: List<Riparazione>): String {
        val sb = StringBuilder()
        sb.appendLine("Numero;ClienteId;Cliente;Telefono;Tipo;Marca/Modello;Problema;" +
            "Note;Prezzo;Stato;StatoChiamata;Data Ingresso;Data Pronto;Data Consegna;Tempo Lavoro (ms)")
        riparazioni.forEach { r ->
            sb.appendLine(listOf(
                r.numeroProgressivo,
                r.clienteId?.toString() ?: "",
                r.cliente, r.telefono,
                r.tipoDispositivo.label, r.marcaModello,
                r.problema.replace("\n", " "), r.noteInterne.replace("\n", " "),
                r.prezzoRiparazione, r.stato.label,
                r.statoChiamata.name,
                DateFmt.full(r.dataIngresso),
                r.dataPronto?.let { DateFmt.datetime(it) } ?: "",
                r.dataConsegna?.let { DateFmt.datetime(it) } ?: "",
                r.tempoLavoroMs.toString()
            ).joinToString(";") { campo -> "\"${campo.replace("\"", "\"\"")}\"" })
        }
        return sb.toString()
    }

    private fun csvClienti(clienti: List<Cliente>): String {
        val sb = StringBuilder()
        sb.appendLine("Id;Nome;Cognome;Telefono;Indirizzo;Note")
        clienti.forEach { c ->
            sb.appendLine(listOf(
                c.id.toString(), c.nome, c.cognome, c.telefono,
                c.indirizzo.replace("\n", " "), c.note.replace("\n", " ")
            ).joinToString(";") { campo -> "\"${campo.replace("\"", "\"\"")}\"" })
        }
        return sb.toString()
    }

    fun condividi(ctx: Context, file: File, mimeType: String = "application/zip") {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Backup Riparazioni EMG")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Condividi backup").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // Alias per compatibilità con chiamate esistenti
    fun condividiCsv(ctx: Context, file: File) = condividi(ctx, file, "text/csv")

    // ─── IMPORT ────────────────────────────────────────────────────────────────

    data class ImportData(
        val riparazioni: List<Riparazione>,
        val clienti: List<Cliente>
    )

    /**
     * Legge un file ZIP (con clienti.csv + riparazioni.csv)
     * oppure un CSV singolo (solo riparazioni, retrocompatibilità).
     */
    fun leggi(ctx: Context, uri: Uri): ImportData {
        val nome = nomeFile(ctx, uri)?.lowercase() ?: ""
        return if (nome.endsWith(".zip")) leggiZip(ctx, uri)
               else ImportData(leggiCsvRiparazioni(ctx, uri), emptyList())
    }

    private fun leggiZip(ctx: Context, uri: Uri): ImportData {
        var riparazioni = emptyList<Riparazione>()
        var clienti     = emptyList<Cliente>()
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val testo = zis.readBytes().toString(Charsets.UTF_8)
                    when (entry.name) {
                        "riparazioni.csv" -> riparazioni = parseCsvRiparazioni(testo)
                        "clienti.csv"     -> clienti     = parseCsvClienti(testo)
                    }
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalArgumentException("Impossibile aprire il file")
        return ImportData(riparazioni, clienti)
    }

    private fun leggiCsvRiparazioni(ctx: Context, uri: Uri): List<Riparazione> {
        val testo = ctx.contentResolver.openInputStream(uri)
            ?.readBytes()?.toString(Charsets.UTF_8)
            ?: throw IllegalArgumentException("Impossibile aprire il file")
        return parseCsvRiparazioni(testo)
    }

    // Mantieni alias per chiamate vecchie
    fun leggiCsv(ctx: Context, uri: Uri): List<Riparazione> = leggi(ctx, uri).riparazioni

    private fun parseCsvRiparazioni(testo: String): List<Riparazione> {
        val righe = testo.lines().filter { it.isNotBlank() }
        if (righe.isEmpty()) throw IllegalArgumentException("File CSV vuoto")
        val header = righe.first()
        if (!header.startsWith("Numero")) throw IllegalArgumentException("Intestazione CSV non valida")
        return righe.drop(1).mapNotNull { line ->
            runCatching {
                val f = parseCsvLine(line)
                if (f.size < 12) return@mapNotNull null
                // Supporta sia formato vecchio (senza ClienteId e StatoChiamata)
                // che formato nuovo (con entrambi)
                val hasClienteId  = header.contains("ClienteId")
                val hasStatoChiam = header.contains("StatoChiamata")
                var idx = 0
                val numero       = f[idx++]
                val clienteId    = if (hasClienteId) f[idx++].toLongOrNull() else null
                val cliente      = f[idx++]
                val telefono     = f[idx++]
                val tipo         = TipoDispositivo.values().firstOrNull { it.label == f[idx++] } ?: TipoDispositivo.ALTRO
                val marcaModello = f[idx++]
                val problema     = f[idx++]
                val note         = f[idx++]
                val prezzo       = f[idx++]
                val stato        = StatoRiparazione.values().firstOrNull { it.label == f[idx++] } ?: StatoRiparazione.IN_ATTESA
                val statoChiam   = if (hasStatoChiam) { try { StatoChiamata.valueOf(f[idx++]) } catch (_: Exception) { StatoChiamata.NON_CHIAMATO } } else StatoChiamata.NON_CHIAMATO
                val dataIngresso = parseData(f[idx++]) ?: System.currentTimeMillis()
                val dataPronto   = parseData(f.getOrElse(idx++) { "" })
                val dataConsegna = parseData(f.getOrElse(idx++) { "" })
                val tempoMs      = f.getOrElse(idx) { "0" }.toLongOrNull() ?: 0L
                Riparazione(id = 0, numeroProgressivo = numero, clienteId = clienteId,
                    cliente = cliente, telefono = telefono, tipoDispositivo = tipo,
                    marcaModello = marcaModello, problema = problema, noteInterne = note,
                    prezzoRiparazione = prezzo, stato = stato, statoChiamata = statoChiam,
                    dataIngresso = dataIngresso, dataPronto = dataPronto, dataConsegna = dataConsegna,
                    tempoLavoroMs = tempoMs, timerAvviatoAl = null, fotoPaths = emptyList())
            }.getOrNull()
        }
    }

    private fun parseCsvClienti(testo: String): List<Cliente> {
        val righe = testo.lines().filter { it.isNotBlank() }
        if (righe.size < 2) return emptyList()
        return righe.drop(1).mapNotNull { line ->
            runCatching {
                val f = parseCsvLine(line)
                if (f.size < 6) return@mapNotNull null
                Cliente(id = f[0].toLongOrNull() ?: 0L,
                    nome = f[1], cognome = f[2], telefono = f[3],
                    indirizzo = f[4], note = f[5])
            }.getOrNull()
        }
    }

    // ─── PERCORSO FOTO ─────────────────────────────────────────────────────────
    fun pathFoto(ctx: Context): String = File(ctx.filesDir, "photos").absolutePath

    fun apriCartellaFoto(ctx: Context) {
        val dir = File(ctx.filesDir, "photos").apply { if (!exists()) mkdirs() }
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dir)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            try { ctx.startActivity(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
            catch (_: Exception) {}
        }
    }

    // ─── UTILITY ───────────────────────────────────────────────────────────────
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>(); var i = 0
        while (i < line.length) {
            if (line[i] == '"') {
                val sb = StringBuilder(); i++
                while (i < line.length) {
                    if (line[i] == '"' && i + 1 < line.length && line[i+1] == '"') { sb.append('"'); i += 2 }
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
        return try { dtFmt.parse(s)?.time } catch (_: Exception) {
            try { dFmt.parse(s)?.time } catch (_: Exception) { null }
        }
    }

    private fun nomeFile(ctx: Context, uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
