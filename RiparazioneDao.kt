package it.officina.riparazioni.util

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoUtil {

    fun photosDir(ctx: Context): File {
        val dir = File(ctx.filesDir, "photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Crea un nuovo file vuoto per ricevere lo scatto della fotocamera. */
    fun newPhotoFile(ctx: Context): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALIAN).format(Date())
        return File(photosDir(ctx), "IMG_$ts.jpg")
    }

    fun authority(ctx: Context): String = "${ctx.packageName}.fileprovider"

    fun uriFor(ctx: Context, file: File) =
        FileProvider.getUriForFile(ctx, authority(ctx), file)
}
