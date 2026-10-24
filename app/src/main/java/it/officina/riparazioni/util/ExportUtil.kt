package it.officina.riparazioni.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import it.officina.riparazioni.data.Riparazione
import it.officina.riparazioni.data.StatoChiamata
import it.officina.riparazioni.data.StatoRiparazione
import it.officina.riparazioni.data.TipoDispositivo
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
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

    data class ImportData(
        val riparazioni: List<Riparazione>,
        val clienti: List<ClienteInfo>
    )

    data class ClienteInfo(val nome: String, val telefono: String)

    // ─── EXPORT ZIP ────────────────────────────────────────────────────────────

    fun esportaZip(ctx: Context, riparazioni: List<Riparazione>): File {
        val outDir = File(ctx.filesDir, "export").apply { if (!exists()) mkdirs() }
        val timestamp = DateFmt.datetime(System.currentTimeMillis())
            .replace("/", "-").replace(":", "-").replace(" ", "_")
        val zipFile = File(outDir, "backup_riparazioni_$timestamp.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("riparazioni.csv"))
            val ripCsv = buildString {
                appendLine(
                    "Numero;Cliente;Telefono;Tipo;Marca/Modello;Problema;" +
                    "Note;Prezzo;Stato;Data Ingresso;Data Pronto;Data Consegna;" +
                    "Tempo Lavoro (ms);StatoChiamata"
                )
                riparazioni.forEach { r ->
                    appendLine(
                        listOf(
                            r.numeroProgressivo, r.cliente, r.telefono,
                            r.tipoDispositivo.label, r.marcaModello,
                            r.problema.replace("\n", " "),
                            r.noteInterne.replace("\n", " "),
                            r.prezzoRiparazione, r.stato.label,
                            DateFmt.full(r.dataIngresso),
                            r.dataPronto?.let { DateFmt.datetime(it) } ?: "",
                            r.dataConsegna?.let { DateFmt.datetime(it) } ?: "",
                            r.tempoLavoroMs.toString(),
                            r.statoChiamata.name
                        ).joinToString(";") { campo -> "\"${campo.replace("\"", "\"\"")}\"" }
                    )
                }
            }
            zos.write(ripCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("clienti.csv"))
            val clientiDistinti = riparazioni
                .map { ClienteInfo(it.cliente, it.telefono) }
                .distinctBy { it.nome }
                .filter { it.nome.isNotBlank() }
            val clientiCsv = buildString {
                appendLine("Nome;Telefono")
                clientiDistinti.forEach { c ->
                    appendLine(
                        listOf(c.nome, c.telefono)
                            .joinToString(";") { campo -> "\"${campo.replace("\"", "\"\"")}\"" }
                    )
                }
            }
            zos.write(clientiCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return zipFile
    }

    fun condividi(ctx: Context, file: File) {
        val mime = if (file.name.endsWith(".zip")) "application/zip" else "text/csv"
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Backup riparazioni")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Condividi backup").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun condividiCsv(ctx: Context, file: File) = condividi(ctx, file)

    fun esportaCsv(ctx: Context, riparazioni: List<Riparazione>): File = esportaZip(ctx, riparazioni)

    // ─── IMPORT ────────────────────────────────────────────────────────────────

    fun leggi(ctx: Context, uri: Uri): ImportData {
        val fileName = ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: ""
        return if (fileName.endsWith(".zip", ignoreCase = true)) leggiZip(ctx, uri)
        else ImportData(leggiCsv(ctx, uri), emptyList())
    }

    fun leggiCsv(ctx: Context, uri: Uri): List<Riparazione> {
        val righe = mutableListOf<Riparazione>()
        ctx.contentResolver.openInputStream(uri)?.use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val header = reader.readLine() ?: throw IllegalArgumentException("File CSV vuoto")
            if (!header.startsWith("Numero")) throw IllegalArgumentException("Intestazione CSV non valida")
            val hasStatoChiamata = header.contains("StatoChiamata")
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val campi = parseCsvLine(line)
                if (campi.size < 12) return@forEachLine
                try {
                    righe.add(Riparazione(
                        id = 0,
                        numeroProgressivo = campi[0], cliente = campi[1], telefono = campi[2],
                        tipoDispositivo = TipoDispositivo.values().firstOrNull { it.label == campi[3] } ?: TipoDispositivo.ALTRO,
                        marcaModello = campi[4], problema = campi[5], noteInterne = campi[6],
                        prezzoRiparazione = campi[7],
                        stato = StatoRiparazione.values().firstOrNull { it.label == campi[8] } ?: StatoRiparazione.IN_ATTESA,
                        dataIngresso = parseData(campi[9]) ?: System.currentTimeMillis(),
                        dataPronto = parseData(campi[10]), dataConsegna = parseData(campi[11]),
                        tempoLavoroMs = if (campi.size > 12) campi[12].toLongOrNull() ?: 0L else 0L,
                        timerAvviatoAl = null, fotoPaths = emptyList(),
                        statoChiamata = if (hasStatoChiamata && campi.size > 13) {
                            try { StatoChiamata.valueOf(campi[13]) } catch (_: Exception) { StatoChiamata.NON_CHIAMATO }
                        } else StatoChiamata.NON_CHIAMATO
                    ))
                } catch (_: Exception) {}
            }
        } ?: throw IllegalArgumentException("Impossibile aprire il file")
        return righe
    }

    private fun leggiZip(ctx: Context, uri: Uri): ImportData {
        var riparazioni = listOf<Riparazione>()
        val clienti = mutableListOf<ClienteInfo>()
        ctx.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val content = zis.readBytes().toString(Charsets.UTF_8)
                    when (entry.name) {
                        "riparazioni.csv" -> riparazioni = parseCsvRiparazioniStr(content)
                        "clienti.csv"     -> clienti.addAll(parseCsvClientiStr(content))
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalArgumentException("Impossibile aprire il file ZIP")
        return ImportData(riparazioni, clienti)
    }

    private fun parseCsvRiparazioniStr(content: String): List<Riparazione> {
        val righe = mutableListOf<Riparazione>()
        val lines = content.lines()
        if (lines.isEmpty()) return righe
        val header = lines[0]
        val hasStatoChiamata = header.contains("StatoChiamata")
        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val campi = parseCsvLine(line)
            if (campi.size < 12) return@forEach
            try {
                righe.add(Riparazione(
                    id = 0,
                    numeroProgressivo = campi[0], cliente = campi[1], telefono = campi[2],
                    tipoDispositivo = TipoDispositivo.values().firstOrNull { it.label == campi[3] } ?: TipoDispositivo.ALTRO,
                    marcaModello = campi[4], problema = campi[5], noteInterne = campi[6],
                    prezzoRiparazione = campi[7],
                    stato = StatoRiparazione.values().firstOrNull { it.label == campi[8] } ?: StatoRiparazione.IN_ATTESA,
                    dataIngresso = parseData(campi[9]) ?: System.currentTimeMillis(),
                    dataPronto = parseData(campi[10]), dataConsegna = parseData(campi[11]),
                    tempoLavoroMs = if (campi.size > 12) campi[12].toLongOrNull() ?: 0L else 0L,
                    timerAvviatoAl = null, fotoPaths = emptyList(),
                    statoChiamata = if (hasStatoChiamata && campi.size > 13) {
                        try { StatoChiamata.valueOf(campi[13]) } catch (_: Exception) { StatoChiamata.NON_CHIAMATO }
                    } else StatoChiamata.NON_CHIAMATO
                ))
            } catch (_: Exception) {}
        }
        return righe
    }

    private fun parseCsvClientiStr(content: String): List<ClienteInfo> {
        val result = mutableListOf<ClienteInfo>()
        val lines = content.lines()
        if (lines.size < 2) return result
        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val campi = parseCsvLine(line)
            if (campi.size >= 2) result.add(ClienteInfo(nome = campi[0], telefono = campi[1]))
            else if (campi.size == 1 && campi[0].isNotBlank()) result.add(ClienteInfo(nome = campi[0], telefono = ""))
        }
        return result
    }

    // ─── CARTELLA FOTO ───────────────────────────────────────────────────────────

    fun pathFoto(ctx: Context): String = File(ctx.filesDir, "photos").absolutePath

    fun apriCartellaFoto(ctx: Context) {
        val dir = File(ctx.filesDir, "photos").apply { if (!exists()) mkdirs() }
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            } catch (_: Exception) {}
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
        catch (_: Exception) { try { dFmt.parse(s)?.time } catch (_: Exception) { null } }
    }
}
