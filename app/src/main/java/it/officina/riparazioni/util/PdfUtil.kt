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
    fun generaEtichetta(ctx: Context, r: Riparazione): File {
        val w = 297; val h = 420
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create())
        val canvas = page.canvas

        val bold = Paint().apply { isAntiAlias = true; textSize = 22f; isFakeBoldText = true }
        val gray = Paint().apply { isAntiAlias = true; textSize = 11f; color = android.graphics.Color.GRAY }
        val norm = Paint().apply { isAntiAlias = true; textSize = 16f }
        val big  = Paint().apply { isAntiAlias = true; textSize = 28f; isFakeBoldText = true }
        val bord = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }

        canvas.drawRect(15f, 15f, (w-15).toFloat(), (h-15).toFloat(), bord)
        canvas.drawText("RIPARAZIONE", 30f, 50f, bold)
        canvas.drawText("#${r.numeroProgressivo}", 30f, 90f, big)

        var y = 140f
        fun riga(label: String, value: String) {
            canvas.drawText(label.uppercase(), 30f, y, gray); y += 18f
            canvas.drawText(value.ifEmpty { "—" }, 30f, y, norm); y += 28f
        }
        riga("Cliente", r.cliente)
        if (r.telefono.isNotEmpty()) riga("Telefono", r.telefono)
        riga("Dispositivo", "${r.tipoDispositivo.label} — ${r.marcaModello}")
        if (r.problema.isNotEmpty()) riga("Problema", r.problema.take(80))
        riga("Data ingresso", DateFmt.full(r.dataIngresso))

        canvas.drawText("STATO: ${r.stato.label.uppercase()}", 30f, (h-50).toFloat(), norm)
        doc.finishPage(page)

        val outDir = File(ctx.filesDir, "pdf").apply { if (!exists()) mkdirs() }
        val file = File(outDir, "etichetta_${r.numeroProgressivo}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

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
