package it.officina.riparazioni.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoUtil {
    fun photosDir(ctx: Context): File = File(ctx.filesDir, "photos").also { it.mkdirs() }
    fun newPhotoFile(ctx: Context): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALIAN).format(Date())
        return File(photosDir(ctx), "IMG_$ts.jpg")
    }
    fun uriFor(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}
