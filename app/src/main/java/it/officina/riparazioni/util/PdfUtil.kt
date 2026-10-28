package it.officina.riparazioni.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import it.officina.riparazioni.data.Riparazione
import java.io.File
import java.io.FileOutputStream

object PdfUtil {

    /**
     * Genera un PDF formato A6 (105x148 mm) con i dati essenziali della riparazione.
     * Ritorna il file generato.
     */
    fun generaEtichetta(ctx: Context, r: Riparazione): File {
        // A6 in punti (1 pollice = 72 punti). 105mm = 297pt, 148mm = 420pt
        val w = 297
        val h = 420

        val doc = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(w, h, 1).create()
        val page = doc.startPage(info)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 22f
            isFakeBoldText = true
        }
        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = 11f
            color = android.graphics.Color.GRAY
        }
        val valuePaint = Paint().apply {
            isAntiAlias = true
            textSize = 16f
        }
        val numPaint = Paint().apply {
            isAntiAlias = true
            textSize = 28f
            isFakeBoldText = true
        }
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = android.graphics.Color.BLACK
        }

        // Cornice
        canvas.drawRect(15f, 15f, (w - 15).toFloat(), (h - 15).toFloat(), borderPaint)

        // Titolo
        canvas.drawText("RIPARAZIONE", 30f, 50f, titlePaint)

        // Numero progressivo grande
        canvas.drawText("#${r.numeroProgressivo}", 30f, 90f, numPaint)

        // Larghezza massima testo (margini 30px + 30px su 297px totali)
        val maxTextWidth = (w - 60).toFloat()
        val maxY = (h - 70).toFloat() // lascia spazio per stato in basso

        /**
         * Disegna testo con word-wrap automatico.
         * Se il testo non entra verticalmente, tronca con "…".
         * Ritorna la nuova y dopo il testo.
         */
        fun drawWrappedText(text: String, paint: Paint, startY: Float): Float {
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = ""
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) <= maxTextWidth) {
                    currentLine = candidate
                } else {
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine)

            var cy = startY
            val lineHeight = paint.textSize * 1.3f
            for ((i, line) in lines.withIndex()) {
                if (cy + lineHeight > maxY) {
                    // Troncamento: sostituisci l'ultima riga con "…"
                    if (i > 0) {
                        val prev = lines[i - 1]
                        var truncated = prev
                        while (paint.measureText("$truncated…") > maxTextWidth && truncated.isNotEmpty()) {
                            truncated = truncated.dropLast(1)
                        }
                        // Ridisegna la riga precedente troncata (sovrascrivi)
                        canvas.drawText("$truncated…", 30f, cy - lineHeight, paint)
                    }
                    break
                }
                canvas.drawText(line, 30f, cy, paint)
                cy += lineHeight
            }
            return cy
        }

        var y = 140f
        fun riga(label: String, value: String) {
            if (y >= maxY) return
            canvas.drawText(label.uppercase(), 30f, y, labelPaint)
            y += 18f
            y = drawWrappedText(value.ifEmpty { "—" }, valuePaint, y)
            y += 10f
        }

        riga("Cliente", r.cliente)
        if (r.telefono.isNotEmpty()) riga("Telefono", r.telefono)
        riga("Dispositivo", "${r.tipoDispositivo.label} — ${r.marcaModello}")
        if (r.problema.isNotEmpty()) riga("Problema", r.problema)
        riga("Data ingresso", DateFmt.full(r.dataIngresso))

        // Stato in basso, evidenziato
        val statoY = (h - 50).toFloat()
        canvas.drawText("STATO: ${r.stato.label.uppercase()}", 30f, statoY, valuePaint)

        doc.finishPage(page)

        val outDir = File(ctx.filesDir, "pdf").apply { if (!exists()) mkdirs() }
        val file = File(outDir, "etichetta_${r.numeroProgressivo}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        return file
    }

    /** Apre il chooser di sistema per condividere/stampare il PDF. */
    fun condividi(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Condividi etichetta").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
