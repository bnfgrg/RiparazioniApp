package it.officina.riparazioni.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.officina.riparazioni.ui.RiparazioneViewModel

data class ClienteInfo(val nome: String, val telefono: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnagraficaClienteScreen(
    vm: RiparazioneViewModel,
    fromRiparazione: Boolean = false,
    onIndietro: () -> Unit
) {
    val tutte by vm.tutte.collectAsStateWithLifecycle()

    // Clienti distinti: per ogni nome unico prendo l'ultima occorrenza (telefono più recente)
    val clientiDistinti: List<ClienteInfo> = remember(tutte) {
        tutte
            .filter { it.cliente.isNotBlank() }
            .groupBy { it.cliente.trim() }
            .map { (nome, rips) ->
                val telefono = rips.maxByOrNull { it.dataIngresso }?.telefono ?: ""
                ClienteInfo(nome, telefono)
            }
            .sortedBy { it.nome }
    }

    var mostraDialogNuovo by remember { mutableStateOf(false) }
    var nomeNuovo by remember { mutableStateOf("") }
    var telNuovo by remember { mutableStateOf("") }

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
                Icon(Icons.Default.Person, "Nuovo cliente")
            }
        }
    ) { padding ->
        if (clientiDistinti.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nessun cliente trovato", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Usa il pulsante + per aggiungerne uno",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(clientiDistinti, key = { it.nome }) { c ->
                    RigaCliente(
                        cliente = c,
                        selezionabile = fromRiparazione,
                        onClick = {
                            if (fromRiparazione) {
                                vm.segnalaClienteCreato(c.nome, c.telefono)
                                onIndietro()
                            }
                        }
                    )
                }
            }
        }
    }

    if (mostraDialogNuovo) {
        AlertDialog(
            onDismissRequest = { mostraDialogNuovo = false },
            title = { Text("Nuovo cliente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nomeNuovo, onValueChange = { nomeNuovo = it },
                        label = { Text("Nome") },
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
                        if (nomeNuovo.isNotBlank()) {
                            if (fromRiparazione) {
                                vm.segnalaClienteCreato(nomeNuovo.trim(), telNuovo.trim())
                                mostraDialogNuovo = false
                                onIndietro()
                            } else {
                                mostraDialogNuovo = false
                            }
                        }
                    },
                    enabled = nomeNuovo.isNotBlank()
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Conferma")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraDialogNuovo = false }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun RigaCliente(cliente: ClienteInfo, selezionabile: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = selezionabile, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cliente.nome.first().uppercaseChar().toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(cliente.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (cliente.telefono.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(cliente.telefono, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (selezionabile) {
            Icon(Icons.Default.ArrowBack, "Seleziona",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.surfaceVariant))
}
