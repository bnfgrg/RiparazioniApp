package it.officina.riparazioni.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.officina.riparazioni.data.Cliente
import it.officina.riparazioni.data.Riparazione
import it.officina.riparazioni.ui.RiparazioneViewModel
import it.officina.riparazioni.ui.components.ConfermaDialog
import it.officina.riparazioni.ui.components.PallinoStato
import it.officina.riparazioni.ui.theme.ColorDanger
import it.officina.riparazioni.util.DateFmt

// ─── SCHERMATA LISTA CLIENTI ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnagraficaListaScreen(
    vm: RiparazioneViewModel,
    onApriCliente: (Long) -> Unit,
    onNuovoCliente: () -> Unit,
    onIndietro: () -> Unit
) {
    val clienti by vm.clientiFiltrati.collectAsStateWithLifecycle()
    val query   by vm.queryClienti.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anagrafica clienti") },
                navigationIcon = {
                    IconButton(onClick = onIndietro) { Icon(Icons.Default.ArrowBack, "Indietro") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNuovoCliente,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Default.Add, "Nuovo cliente") }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.setQueryClienti(it) },
                placeholder = { Text("Cerca per nome, cognome, telefono…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (clienti.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nessun cliente", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("Tocca + per aggiungerne uno", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(clienti, key = { it.id }) { c ->
                        CardCliente(c = c, onClick = { onApriCliente(c.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CardCliente(c: Cliente, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar iniziali
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                val iniziali = "${c.nome.firstOrNull() ?: ""}${c.cognome.firstOrNull() ?: ""}".uppercase()
                Text(iniziali, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(c.nomeCompleto.ifEmpty { "Senza nome" },
                    fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                if (c.telefono.isNotEmpty())
                    Text(c.telefono, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.indirizzo.isNotEmpty())
                    Text(c.indirizzo, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

// ─── SCHERMATA DETTAGLIO CLIENTE ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnagraficaDettaglioScreen(
    vm: RiparazioneViewModel,
    clienteId: Long?,   // null = nuovo
    onIndietro: () -> Unit,
    onApriRiparazione: (Long) -> Unit
) {
    var cliente by remember { mutableStateOf<Cliente?>(null) }
    var caricato by remember { mutableStateOf(false) }
    var mostraConfermaElimina by remember { mutableStateOf(false) }
    val riparazioni = remember { mutableStateOf<List<Riparazione>>(emptyList()) }

    LaunchedEffect(clienteId) {
        cliente = if (clienteId == null || clienteId == 0L) Cliente()
                  else vm.clienteById(clienteId) ?: Cliente()
        caricato = true
    }

    // Raccoglie le riparazioni del cliente
    val ripsFlow = remember(clienteId) {
        if (clienteId != null && clienteId != 0L) vm.riparazioniByCliente(clienteId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val rips by ripsFlow.collectAsStateWithLifecycle(emptyList())

    if (!caricato || cliente == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Caricamento…") }
        return
    }

    val c = cliente!!
    fun aggiorna(nuovo: Cliente) { cliente = nuovo }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (clienteId == null || clienteId == 0L) "Nuovo cliente" else c.nomeCompleto.ifEmpty { "Cliente" }) },
                navigationIcon = {
                    IconButton(onClick = onIndietro) { Icon(Icons.Default.ArrowBack, "Indietro") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // ─── DATI ANAGRAFICI ─────────────────────────────────────────────
            EtichettaCampoC("Nome")
            OutlinedTextField(value = c.nome, onValueChange = { aggiorna(c.copy(nome = it)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(10.dp))
            EtichettaCampoC("Cognome")
            OutlinedTextField(value = c.cognome, onValueChange = { aggiorna(c.copy(cognome = it)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(10.dp))
            EtichettaCampoC("Telefono")
            OutlinedTextField(value = c.telefono, onValueChange = { aggiorna(c.copy(telefono = it)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(10.dp))
            EtichettaCampoC("Indirizzo")
            OutlinedTextField(value = c.indirizzo, onValueChange = { aggiorna(c.copy(indirizzo = it)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(10.dp))
            EtichettaCampoC("Note")
            OutlinedTextField(value = c.note, onValueChange = { aggiorna(c.copy(note = it)) },
                modifier = Modifier.fillMaxWidth().height(80.dp))

            Spacer(Modifier.height(20.dp))

            // ─── SALVA ────────────────────────────────────────────────────────
            Button(
                onClick = { vm.salvaCliente(c) { onIndietro() } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva") }

            // ─── RIPARAZIONI ASSOCIATE ────────────────────────────────────────
            if (clienteId != null && clienteId != 0L && rips.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.height(16.dp))
                Text("Riparazioni associate (${rips.size})",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                rips.forEach { r ->
                    CardRiparazioneCompatta(r = r, onClick = { onApriRiparazione(r.id) })
                    Spacer(Modifier.height(6.dp))
                }
            }

            // ─── ELIMINA ──────────────────────────────────────────────────────
            if (clienteId != null && clienteId != 0L) {
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { mostraConfermaElimina = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp)); Text("Elimina cliente")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (mostraConfermaElimina) {
        ConfermaDialog(
            titolo = "Eliminare il cliente?",
            testo = "Il cliente verrà rimosso dall'anagrafica. Le riparazioni associate rimarranno ma perderanno il collegamento al cliente.",
            sottoinfo = c.nomeCompleto,
            confermaLabel = "Elimina",
            onConferma = { vm.eliminaCliente(c); mostraConfermaElimina = false; onIndietro() },
            onAnnulla = { mostraConfermaElimina = false }
        )
    }
}

@Composable
private fun CardRiparazioneCompatta(r: Riparazione, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("#${r.numeroProgressivo}  ${r.tipoDispositivo.label}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (r.marcaModello.isNotEmpty())
                    Text(r.marcaModello, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFmt.full(r.dataIngresso), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PallinoStato(r.stato)
        }
    }
}

@Composable
private fun EtichettaCampoC(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp))
}
