package it.officina.riparazioni.util

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.util.Log

object ContactUtil {

    /**
     * Aggiunge il contatto alla rubrica Android se non esiste già un contatto
     * con lo stesso numero di telefono.
     * Operazione eseguita in modo silenzioso (nessun dialog, nessuna UI).
     * Richiede permesso WRITE_CONTACTS.
     */
    fun aggiungiSeNonEsiste(ctx: Context, nome: String, telefono: String) {
        if (nome.isBlank() || telefono.isBlank()) return
        val numeroNorm = WhatsAppUtil.normalizzaNumero(telefono)
        if (esisteNellaRubrica(ctx, numeroNorm)) return

        try {
            val ops = arrayListOf(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build(),

                // Nome
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        nome
                    )
                    .build(),

                // Numero di telefono
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        "+$numeroNorm"
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .build()
            )

            ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Log.d("ContactUtil", "Contatto aggiunto: $nome ($numeroNorm)")
        } catch (e: Exception) {
            Log.e("ContactUtil", "Errore aggiunta contatto", e)
        }
    }

    /**
     * Controlla se esiste già un contatto con quel numero (confronto sulle cifre finali).
     */
    private fun esisteNellaRubrica(ctx: Context, numero: String): Boolean {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val cursor = ctx.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val n = it.getString(0)?.replace(Regex("[^0-9]"), "") ?: continue
                // Confronto sulle ultime 9 cifre per gestire varianti di prefisso
                if (n.length >= 9 && numero.length >= 9 &&
                    n.takeLast(9) == numero.takeLast(9)) {
                    return true
                }
            }
        }
        return false
    }
}
