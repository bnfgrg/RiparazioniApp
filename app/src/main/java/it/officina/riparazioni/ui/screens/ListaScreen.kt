package it.officina.riparazioni.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import it.officina.riparazioni.BuildConfig
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.officina.riparazioni.R
import it.officina.riparazioni.data.Riparazione
import it.officina.riparazioni.ui.FiltroStato
import it.officina.riparazioni.ui.RiparazioneViewModel
import it.officina.riparazioni.ui.components.ChipFiltro
import it.officina.riparazioni.ui.components.ConfermaDialog
import it.officina.riparazioni.ui.components.PallinoStato
import it.officina.riparazioni.ui.theme.ColorAttesa
import it.officina.riparazioni.ui.theme.ColorConsegnato
import it.officina.riparazioni.ui.theme.ColorDanger
import it.officina.riparazioni.ui.theme.ColorDangerBg
import it.officina.riparazioni.ui.theme.ColorLavorazione
import it.officina.riparazioni.ui.theme.ColorPronto
import it.officina.riparazioni.util.DateFmt
import it.officina.riparazioni.util.ExportUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListaScreen(
    vm: RiparazioneViewModel,
    onApri: (Long) -> Unit,
    onNuova: () -> Unit
) {
    val items by vm.filtrate.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filtro by vm.filtro.collectAsStateWithLifecycle()
    val tutte by vm.tutte.collectAsStateWithLifecycle()

    var selezione by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var mostraDialog by remember { mutableStateOf(false) }

    // Menu principale
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mostraMenu by remember { mutableStateOf(false) }
    var mostraDialogCancellaAll by remember { mutableStateOf(false) }
    var mostraDialogCancellaConferma by remember { mutableStateOf(false) }
    var mostraDialogPathFoto by remember { mutableStateOf(false) }
    var mostraDialogEliminaFiltrati by remember { mutableStateOf(false) }

    val inSelezione = selezione.isNotEmpty()
    val haFiltriAttivi = query.isNotEmpty() || filtro != FiltroStato.TUTTI

    Scaffold(
        topBar = {
            if (inSelezione) {
                TopAppBar(
                    title = { Text(stringResource(R.string.n_selezionate, selezione.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selezione = emptySet() }) {
                            Icon(Icons.Default.Close, "Esci selezione")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            selezione = items.map { it.id }.toSet()
                        }) { Text(stringResource(R.string.seleziona_tutto)) }
                        IconButton(onClick = { mostraDialog = true }) {
                            Icon(Icons.Default.Delete, "Elimina", tint = ColorDanger)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ColorDangerBg
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.lista_titolo))
                            Text(
                                "v${BuildConfig.VERSION_NAME}  build ${BuildConfig.VERSION_CODE}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Pulsante menu
                        IconButton(onClick = { mostraMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Menu funzioni")
                        }
                        DropdownMenu(
                            expanded = mostraMenu,
                            onDismissRequest = { mostraMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Esporta schede (CSV)") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                onClick = {
                                    mostraMenu = false
                                    scope.launch {
                                        val file = ExportUtil.esportaCsv(ctx, tutte)
                                        ExportUtil.condividiCsv(ctx, file)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cancella tutte le schede") },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = ColorDanger) },
                                onClick = {
                                    mostraMenu = false
                                    mostraDialogCancellaAll = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Dove sono salvate le foto") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                                onClick = {
                                    mostraMenu = false
                                    mostraDialogPathFoto = true
                                }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!inSelezione) {
                FloatingActionButton(
                    onClick = onNuova,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.nuova_riparazione))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            if (!inSelezione) {
                // Barra di ricerca
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.setQuery(it) },
                    placeholder = { Text(stringResource(R.string.cerca_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Chip di filtro
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val countAttesa = tutte.count { it.stato.name == "IN_ATTESA" }
                    val countLav = tutte.count { it.stato.name == "IN_LAVORAZIONE" }
                    val countPronto = tutte.count { it.stato.name == "PRONTO" }
                    val countConseg = tutte.count { it.stato.name == "CONSEGNATO" }

                    ChipFiltro("Tutti · ${tutte.size}",
                        MaterialTheme.colorScheme.primary,
                        filtro == FiltroStato.TUTTI,
                        { vm.setFiltro(FiltroStato.TUTTI) })
                    ChipFiltro("In attesa · $countAttesa", ColorAttesa,
                        filtro == FiltroStato.ATTESA,
                        { vm.setFiltro(FiltroStato.ATTESA) })
                    ChipFiltro("In lavoraz. · $countLav", ColorLavorazione,
                        filtro == FiltroStato.LAVORAZIONE,
                        { vm.setFiltro(FiltroStato.LAVORAZIONE) })
                    ChipFiltro("Pronti · $countPronto", ColorPronto,
                        filtro == FiltroStato.PRONTI,
                        { vm.setFiltro(FiltroStato.PRONTI) })
                    ChipFiltro("Consegnati · $countConseg", ColorConsegnato,
                        filtro == FiltroStato.CONSEGNATI,
                        { vm.setFiltro(FiltroStato.CONSEGNATI) })
                }
                Spacer(Modifier.height(4.dp))

                // Banner risultati filtrati + pulsante elimina filtrati
                if (haFiltriAttivi && items.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${items.size} risultat${if (items.size == 1) "o" else "i"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { mostraDialogEliminaFiltrati = true },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = ColorDanger
                            )
                        ) {
                            Icon(Icons.Default.Delete, null,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Elimina risultati",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.lista_vuota),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.lista_vuota_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(items, key = { it.id }) { r ->
                        CardRiparazione(
                            r = r,
                            inSelezione = inSelezione,
                            selezionata = selezione.contains(r.id),
                            onTap = {
                                if (inSelezione) {
                                    selezione = if (selezione.contains(r.id))
                                        selezione - r.id else selezione + r.id
                                } else onApri(r.id)
                            },
                            onLongTap = {
                                if (!inSelezione) selezione = setOf(r.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (mostraDialog) {
        val daEliminare = items.filter { selezione.contains(it.id) }
        ConfermaDialog(
            titolo = stringResource(R.string.conferma_elimina_multi_titolo, daEliminare.size),
            testo = stringResource(R.string.conferma_elimina_multi_testo),
            sottoinfo = daEliminare.take(5).joinToString("\n") {
                "• ${it.cliente} — ${it.marcaModello}"
            } + if (daEliminare.size > 5) "\n…e altre ${daEliminare.size - 5}" else "",
            confermaLabel = stringResource(R.string.elimina),
            onConferma = {
                vm.eliminaMultiple(daEliminare)
                selezione = emptySet()
                mostraDialog = false
            },
            onAnnulla = { mostraDialog = false }
        )
    }

    // === DIALOG: elimina risultati filtrati ===
    if (mostraDialogEliminaFiltrati) {
        val descFiltro = buildString {
            if (query.isNotEmpty()) append("cliente/modello: "$query"")
            if (query.isNotEmpty() && filtro != FiltroStato.TUTTI) append(" · ")
            if (filtro != FiltroStato.TUTTI) append("stato: ${filtro.name.lowercase().replace("_", " ")}")
        }
        AlertDialog(
            onDismissRequest = { mostraDialogEliminaFiltrati = false },
            title = { Text("Elimina ${items.size} riparazioni?") },
            text = {
                Column {
                    Text(
                        "Verranno eliminate definitivamente le ${items.size} riparazioni " +
                        "attualmente visibili ($descFiltro), insieme alle foto associate.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Operazione non annullabile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorDanger
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.eliminaFiltrate()
                        mostraDialogEliminaFiltrati = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = ColorDanger
                    )
                ) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { mostraDialogEliminaFiltrati = false }) { Text("Annulla") }
            }
        )
    }

    // === DIALOG 1: prima conferma cancella tutto ===
    if (mostraDialogCancellaAll) {
        AlertDialog(
            onDismissRequest = { mostraDialogCancellaAll = false },
            title = { Text("Cancella tutte le schede") },
            text = {
                Text(
                    "Questa operazione eliminerà definitivamente TUTTE le " +
                    "${tutte.size} schede e tutte le foto associate.\n\n" +
                    "I dati non saranno recuperabili in nessun modo.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostraDialogCancellaAll = false
                        mostraDialogCancellaConferma = true
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = ColorDanger
                    )
                ) { Text("Continua") }
            },
            dismissButton = {
                TextButton(onClick = { mostraDialogCancellaAll = false }) { Text("Annulla") }
            }
        )
    }

    // === DIALOG 2: seconda conferma cancella tutto ===
    if (mostraDialogCancellaConferma) {
        AlertDialog(
            onDismissRequest = { mostraDialogCancellaConferma = false },
            title = { Text("Sei assolutamente sicuro?") },
            text = {
                Text(
                    "Ultima possibilità: premi ELIMINA TUTTO per confermare.\n\n" +
                    "Tutti i dati e le foto saranno persi per sempre.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.eliminaTutte()
                        mostraDialogCancellaConferma = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = ColorDanger
                    )
                ) { Text("ELIMINA TUTTO") }
            },
            dismissButton = {
                TextButton(onClick = { mostraDialogCancellaConferma = false }) { Text("Annulla") }
            }
        )
    }

    // === DIALOG: path cartella foto ===
    if (mostraDialogPathFoto) {
        val pathFoto = ExportUtil.pathFoto(ctx)
        AlertDialog(
            onDismissRequest = { mostraDialogPathFoto = false },
            title = { Text("Cartella foto") },
            text = {
                Column {
                    Text(
                        "Le foto sono salvate nella cartella interna dell'app:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            pathFoto,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Per accedere usa un file manager con accesso alla memoria interna " +
                        "(es. Files by Google con 'Mostra file interni' abilitato nelle impostazioni).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { mostraDialogPathFoto = false }) { Text("OK") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRiparazione(
    r: Riparazione,
    inSelezione: Boolean,
    selezionata: Boolean,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    val ctx = LocalContext.current
    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selezionata) ColorDangerBg
            else MaterialTheme.colorScheme.background
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongTap
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (inSelezione) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (selezionata) ColorDanger else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selezionata) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
            }

            // Foto miniatura
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (r.fotoPaths.isNotEmpty()) {
                    AsyncImage(
                        model = r.fotoPaths.first(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("📷", fontSize = 18.sp)
                }
            }

            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    r.cliente.ifEmpty { "Senza nome" },
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    r.marcaModello.ifEmpty { r.tipoDispositivo.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "#${r.numeroProgressivo} · ${DateFmt.short(r.dataIngresso)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PallinoStato(r.stato)
        }
        // separatore
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}
