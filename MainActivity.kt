package it.officina.riparazioni.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import it.officina.riparazioni.data.Riparazione
import java.io.File
import java.io.FileWriter

object ExportUtil {

    /**
     * Genera un file CSV con tutti i dati delle riparazioni (escluse le foto).
     * Restituisce il File generato.
     */
    fun esportaCsv(ctx: Context, riparazioni: List<Riparazione>): File {
        val outDir = File(ctx.filesDir, "export").apply { if (!exists()) mkdirs() }
        val timestamp = DateFmt.datetime(System.currentTimeMillis())
            .replace("/", "-").replace(":", "-").replace(" ", "_")
        val file = File(outDir, "riparazioni_$timestamp.csv")

        FileWriter(file, Charsets.UTF_8).use { w ->
            // Header
            w.appendLine(
                "Numero;Cliente;Telefono;Tipo;Marca/Modello;Problema;" +
                "Note;Prezzo;Stato;Data Ingresso;Data Pronto;Data Consegna"
            )
            riparazioni.forEach { r ->
                w.appendLine(
                    listOf(
                        r.numeroProgressivo,
                        r.cliente,
                        r.telefono,
                        r.tipoDispositivo.label,
                        r.marcaModello,
                        r.problema.replace("\n", " "),
                        r.noteInterne.replace("\n", " "),
                        r.prezzoRiparazione,
                        r.stato.label,
                        DateFmt.full(r.dataIngresso),
                        r.dataPronto?.let { DateFmt.datetime(it) } ?: "",
                        r.dataConsegna?.let { DateFmt.datetime(it) } ?: ""
                    ).joinToString(";") { campo -> "\"${campo.replace("\"", "\"\"")}\"" }
                )
            }
        }
        return file
    }

    /**
     * Apre il chooser di sistema per condividere il CSV (mail, WhatsApp, ecc.)
     */
    fun condividiCsv(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Esportazione riparazioni")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            Intent.createChooser(intent, "Condividi esportazione").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /**
     * Restituisce il path della cartella foto, leggibile dall'utente.
     */
    fun pathFoto(ctx: Context): String = File(ctx.filesDir, "photos").absolutePath
}
