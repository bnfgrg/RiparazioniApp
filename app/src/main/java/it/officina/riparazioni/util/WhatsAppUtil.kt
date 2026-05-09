package it.officina.riparazioni.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import it.officina.riparazioni.data.Riparazione

object WhatsAppUtil {

    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun isInstallato(ctx: Context): Boolean {
        val pm = ctx.packageManager
        return isPackageInstallato(pm, WHATSAPP_PACKAGE) ||
               isPackageInstallato(pm, WHATSAPP_BUSINESS_PACKAGE)
    }

    private fun isPackageInstallato(pm: PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun messaggioPredefinto(r: Riparazione): String {
        return "Gentile ${r.cliente}, la sua ${r.tipoDispositivo.label}" +
               (if (r.marcaModello.isNotEmpty()) " ${r.marcaModello}" else "") +
               " (rif. #${r.numeroProgressivo}) è pronta per il ritiro. Grazie!"
    }

    /**
     * Apre WhatsApp con numero e messaggio precompilati anche se il numero
     * NON è in rubrica. Usa ACTION_SENDTO con schema smsto: che WhatsApp
     * intercetta direttamente, bypassando il requisito del contatto salvato.
     *
     * Fallback: se WhatsApp Business è installato prova prima quello,
     * poi WhatsApp normale.
     */
    fun apriChat(ctx: Context, numeroCrudo: String, messaggio: String) {
        val numero = normalizzaNumero(numeroCrudo)

        // Metodo 1: Intent diretto al package WhatsApp con ACTION_SEND
        // Funziona anche con numeri non in rubrica
        val pm = ctx.packageManager
        val targetPackage = when {
            isPackageInstallato(pm, WHATSAPP_PACKAGE) -> WHATSAPP_PACKAGE
            isPackageInstallato(pm, WHATSAPP_BUSINESS_PACKAGE) -> WHATSAPP_BUSINESS_PACKAGE
            else -> null
        }

        if (targetPackage != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$numero&text=${Uri.encode(messaggio)}")
                    setPackage(targetPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                return
            } catch (_: Exception) { /* prova fallback */ }
        }

        // Fallback: apre il browser con wa.me (funziona se WhatsApp è installato
        // ma l'intent diretto fallisce per qualche motivo)
        try {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$numero?text=${Uri.encode(messaggio)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) { /* WhatsApp non raggiungibile */ }
    }

    fun normalizzaNumero(numero: String): String {
        val pulito = numero.replace(Regex("[\\s\\-\\.()+]"), "")
        return when {
            pulito.startsWith("39") && pulito.length >= 11 -> pulito
            pulito.startsWith("0") -> "39${pulito.substring(1)}"
            else -> "39$pulito"
        }
    }
}
