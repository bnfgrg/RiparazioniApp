package it.officina.riparazioni.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.officina.riparazioni.ui.RiparazioneViewModel
import it.officina.riparazioni.util.ExportUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnagraficaClienteScreen(
    vm: RiparazioneViewModel,
    fromRiparazione: Boolean = false,
    onIndietro: () -> Unit
) {
    val tutte by vm.tutte.collectAsStateWithLifecycle()
    val clientiExtra by vm.clientiExtra.collectAsStateWithLifecycle()

    // Clienti derivati dalle riparazioni (nome → telefono più recente)
    val clientiDaRip: List<ExportUtil.ClienteInfo> = remember(tutte) {
        tutte
            .filter { it.cliente.isNotBlank() }
            .groupBy { it.cliente.trim() }
            .map { (nome, rips) ->
                val telefono = rips.maxByOrNull { it.dataIngresso }?.telefono ?: ""
                ExportUtil.ClienteInfo(nome, telefono)
            }
    }

    // Lista unificata: clienti da riparazioni + clienti extra (senza duplicati)
    val clientiDistinti: List<ExportUtil.ClienteInfo> = remember(clientiDaRip, clientiExtra) {
        val nomiDaRip = clientiDaRip.map { it.nome.trim().lowercase() }.toSet()
        val extra = clientiExtra.filter { it.nome.trim().lowercase() !in nomiDaRip }
        (clientiDaRip + extra).sortedBy { it.nome }
    }

    // Stato dialogs
    var mostraDialogNuovo by remember { mutableStateOf(false) }
    var nomeNuovo by remember { mutableStateOf("") }
    var telNuovo by remember { mutableStateOf("") }

    var clienteDaModificare by remember { mutableStateOf<ExportUtil.ClienteInfo?>(null) }
    var nomeModifica by remember { mutableStateOf("") }
    var telModifica by remember { mutableStateOf("") }

    var clienteDaEliminare by remember { mutableStateOf<ExportUtil.ClienteInfo?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (fromRiparazione) "Seleziona cliente" else "Rubrica clienti") },
                navigationIcon = {
                    IconButton(onClick = onIndietro) {
                        Icon(Icons.Default.ArrowBack, "Indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { nomeNuovo = ""; telNuovo = ""; mostraDialogNuovo = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, "Nuovo cliente")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (clientiDistinti.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Nessun cliente in rubrica",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Usa il pulsante + per aggiungere un cliente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(clientiDistinti, key = { it.nome }) { c ->
                    val isDismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                clienteDaEliminare = c
                                false // non dismissare subito: aspetta conferma
                            } else false
                        }
                    )
                    SwipeToDismissBox(
                        state = isDismissState,
                        backgroundContent = {
                            val color by animateColorAsState(
                                targetValue = if (isDismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                                    MaterialTheme.colorScheme.errorContainer
                                else Color.Transparent,
                                label = "swipe_color"
                            )
                            Box(
                                Modifier.fillMaxSize().background(color).padding(end = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, "Elimina",
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true
                    ) {
                        RigaCliente(
                            cliente = c,
                            fromRiparazione = fromRiparazione,
                            onSeleziona = {
                                vm.segnalaClienteCreato(c.nome, c.telefono)
                                onIndietro()
                            },
                            onModifica = {
                                nomeModifica = c.nome
                                telModifica = c.telefono
                                clienteDaModificare = c
                            }
                        )
                    }
                }
            }
        }
    }

    // ─── Dialog: Nuovo cliente ───────────────────────────────────────────────────
    if (mostraDialogNuovo) {
        AlertDialog(
            onDismissRequest = { mostraDialogNuovo = false },
            title = { Text("Nuovo cliente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nomeNuovo, onValueChange = { nomeNuovo = it },
                        label = { Text("Nome *") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = telNuovo, onValueChange = { telNuovo = it },
                        label = { Text("Telefono") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nome = nomeNuovo.trim()
                        val tel = telNuovo.trim()
                        if (nome.isNotBlank()) {
                            if (fromRiparazione) {
                                // Seleziona subito e torna alla riparazione
                                vm.segnalaClienteCreato(nome, tel)
                                mostraDialogNuovo = false
                                onIndietro()
                            } else {
                                // Salva in rubrica standalone
                                vm.aggiungiClienteExtra(nome, tel)
                                mostraDialogNuovo = false
                                scope.launch { snackbar.showSnackbar("Cliente \"$nome\" aggiunto alla rubrica") }
                            }
                        }
                    },
                    enabled = nomeNuovo.isNotBlank()
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (fromRiparazione) "Seleziona" else "Aggiungi")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraDialogNuovo = false }) { Text("Annulla") }
            }
        )
    }

    // ─── Dialog: Modifica cliente ────────────────────────────────────────────────
    clienteDaModificare?.let { c ->
        AlertDialog(
            onDismissRequest = { clienteDaModificare = null },
            title = { Text("Modifica cliente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nomeModifica, onValueChange = { nomeModifica = it },
                        label = { Text("Nome *") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = telModifica, onValueChange = { telModifica = it },
                        label = { Text("Telefono") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nuovoNome = nomeModifica.trim()
                        val nuovoTel = telModifica.trim()
                        if (nuovoNome.isNotBlank()) {
                            // Se il cliente proviene da riparazioni non possiamo modificarlo
                            // direttamente nel DB — l'aggiornamento si fa solo sui clientiExtra.
                            // Per i clienti da riparazioni mostriamo un messaggio informativo.
                            val isDaRip = tutte.any { it.cliente.trim() == c.nome }
                            if (isDaRip) {
                                // Aggiorna comunque il record extra (o crea uno override)
                                vm.aggiungiClienteExtra(nuovoNome, nuovoTel)
                                if (nuovoNome != c.nome) vm.rimuoviClienteExtra(c.nome)
                                scope.launch {
                                    snackbar.showSnackbar("Nota: il nome nelle riparazioni esistenti non cambia")
                                }
                            } else {
                                vm.aggiornaClienteExtra(c.nome, nuovoNome, nuovoTel)
                            }
                            clienteDaModificare = null
                        }
                    },
                    enabled = nomeModifica.isNotBlank()
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { clienteDaModificare = null }) { Text("Annulla") }
            }
        )
    }

    // ─── Dialog: Conferma eliminazione ──────────────────────────────────────────
    clienteDaEliminare?.let { c ->
        AlertDialog(
            onDismissRequest = { clienteDaEliminare = null },
            title = { Text("Elimina cliente") },
            text = {
                val isDaRip = tutte.any { it.cliente.trim() == c.nome }
                if (isDaRip) {
                    Text("\"${c.nome}\" è associato a delle riparazioni e non può essere eliminato dalla rubrica. Puoi eliminare le riparazioni associate dalla schermata principale.")
                } else {
                    Text("Eliminare \"${c.nome}\" dalla rubrica?")
                }
            },
            confirmButton = {
                val isDaRip = tutte.any { it.cliente.trim() == c.nome }
                if (!isDaRip) {
                    TextButton(
                        onClick = {
                            vm.rimuoviClienteExtra(c.nome)
                            clienteDaEliminare = null
                        }
                    ) {
                        Text("Elimina", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { clienteDaEliminare = null }) { Text("Chiudi") }
            }
        )
    }
}

@Composable
private fun RigaCliente(
    cliente: ExportUtil.ClienteInfo,
    fromRiparazione: Boolean,
    onSeleziona: () -> Unit,
    onModifica: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = if (fromRiparazione) onSeleziona else onModifica)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar iniziale
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cliente.nome.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }

        // Nome e telefono
        Column(modifier = Modifier.weight(1f)) {
            Text(
                cliente.nome,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (cliente.telefono.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Phone, null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        cliente.telefono,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Icona azione a destra
        if (fromRiparazione) {
            Icon(
                Icons.Default.ArrowForward, "Seleziona",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            IconButton(onClick = onModifica, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Edit, "Modifica",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(0.5.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}
