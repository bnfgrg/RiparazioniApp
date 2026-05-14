package it.officina.riparazioni.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import it.officina.riparazioni.data.Riparazione

object SmsUtil {

    /**
     * Apre l'app SMS predefinita del telefono con numero e testo precompilati.
     * L'utente tocca "Invia" per confermare. Non richiede permessi aggiuntivi
     * perché usa un Intent che delega all'app SMS di sistema.
     */
    fun apriSms(ctx: Context, numeroCrudo: String, messaggio: String) {
        val numero = numeroCrudo.replace(Regex("[\\s\\-\\.]"), "")
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$numero")
            putExtra("sms_body", messaggio)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /**
     * SMS inviato alla creazione della scheda: conferma presa in carico.
     */
    fun messaggioPresaInCarico(r: Riparazione): String {
        return "Gentile ${r.cliente}, la riparazione del suo" +
               " ${r.tipoDispositivo.label}" +
               (if (r.marcaModello.isNotEmpty()) " ${r.marcaModello}" else "") +
               " (rif. #${r.numeroProgressivo}) e' stata presa in carico." +
               " La avviseremo appena pronta. Grazie!\n— FERRAMENTA EMG"
    }

    /**
     * SMS inviato quando la riparazione e' pronta per il ritiro.
     */
    fun messaggioPredefinto(r: Riparazione): String {
        return "Gentile ${r.cliente}, la sua ${r.tipoDispositivo.label}" +
               (if (r.marcaModello.isNotEmpty()) " ${r.marcaModello}" else "") +
               " (rif. #${r.numeroProgressivo}) e' pronta per il ritiro. Grazie!\n— FERRAMENTA EMG"
        // Nota: apostrofo semplice perché SMS non garantisce UTF-8 su tutti i carrier
    }
}
