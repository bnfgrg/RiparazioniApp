package it.officina.riparazioni.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.officina.riparazioni.data.StatoRiparazione
import it.officina.riparazioni.ui.theme.*

fun coloreStato(s: StatoRiparazione): Color = when (s) {
    StatoRiparazione.IN_ATTESA     -> ColorAttesa
    StatoRiparazione.IN_LAVORAZIONE -> ColorLavorazione
    StatoRiparazione.PRONTO        -> ColorPronto
    StatoRiparazione.CONSEGNATO    -> ColorConsegnato
}

@Composable
fun PallinoStato(stato: StatoRiparazione, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(12.dp).clip(CircleShape).background(coloreStato(stato)))
}

@Composable
fun ChipFiltro(label: String, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
}

@Composable
fun ChipSelezione(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConfermaDialog(
    titolo: String, testo: String, sottoinfo: String? = null,
    confermaLabel: String = "Elimina", onConferma: () -> Unit, onAnnulla: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text(titolo) },
        text = {
            Column {
                Text(testo, style = MaterialTheme.typography.bodyMedium)
                if (!sottoinfo.isNullOrEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(sottoinfo, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConferma, colors = ButtonDefaults.textButtonColors(contentColor = ColorDanger)) {
                Text(confermaLabel)
            }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}
