package it.officina.riparazioni.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import it.officina.riparazioni.R
import it.officina.riparazioni.data.Riparazione
import it.officina.riparazioni.data.StatoRiparazione
import it.officina.riparazioni.data.TipoDispositivo
import it.officina.riparazioni.ui.RiparazioneViewModel
import it.officina.riparazioni.ui.components.ConfermaDialog
import it.officina.riparazioni.ui.theme.ColorAttesa
import it.officina.riparazioni.ui.theme.ColorConsegnato
import it.officina.riparazioni.ui.theme.ColorDanger
import it.officina.riparazioni.ui.theme.ColorLavorazione
import it.officina.riparazioni.ui.theme.ColorPronto
import it.officina.riparazioni.util.DateFmt
import it.officina.riparazioni.util.PdfUtil
import it.officina.riparazioni.util.SmsUtil
import it.officina.riparazioni.util.PhotoUtil
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DettaglioScreen(
    vm: RiparazioneViewModel,
    riparazioneId: Long?,
    onIndietro: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Stato editing dal ViewModel: sopravvive alla distruzione dell'Activity
    val ripEditing by vm.ripInEditing.collectAsStateWithLifecycle()
    var mostraConfermaElimina by remember { mutableStateOf(false) }
    // MODIFICA 1: stato foto da ingrandire
    var fotoIngrandita by remember { mutableStateOf<String?>(null) }

    // Carica la riparazione solo se il ViewModel non ha già uno stato in editing
    // per questo stesso id (es. utente era già qui prima di andare in background)
    LaunchedEffect(riparazioneId) {
        val editing = vm.ripInEditing.value
        val idCorrente = editing?.id ?: -99L
        val idAtteso = riparazioneId ?: 0L
        // Ricarica solo se l'editing corrente non è per questa scheda
        val isSteady = if (riparazioneId == null || riparazioneId == 0L)
            editing != null && editing.id == 0L
        else editing != null && editing.id == riparazioneId
        if (!isSteady) {
            val r = if (riparazioneId == null || riparazioneId == 0L) {
                Riparazione(numeroProgressivo = vm.nuovoProgressivo())
            } else {
                vm.byId(riparazioneId) ?: Riparazione(numeroProgressivo = vm.nuovoProgressivo())
            }
            vm.iniziaEditing(r)
        }
    }

    if (ripEditing == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Caricamento…") }
        return
    }

    val r = ripEditing!!
    // Alias per aggiornare lo stato: ogni modifica va al ViewModel
    fun aggiorna(nuovo: Riparazione) { vm.aggiornaEditing(nuovo) }

    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var camPermGranted by remember { mutableStateOf(false) }

    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        camPermGranted = granted
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoPath != null) aggiorna(r.copy(fotoPaths = r.fotoPaths + pendingPhotoPath!!))
        pendingPhotoPath = null
    }

    LaunchedEffect(Unit) {
        camPermGranted = ctx.checkSelfPermission(android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun avviaFoto() {
        val file = PhotoUtil.newPhotoFile(ctx)
        pendingPhotoPath = file.absolutePath
        val uri: Uri = PhotoUtil.uriFor(ctx, file)
        cameraLauncher.launch(uri)
    }

    // MODIFICA 2: timer live
    var tempoCorrente by remember { mutableStateOf(vm.tempoEffettivo(r)) }
    LaunchedEffect(r.timerAvviatoAl, r.tempoLavoroMs) {
        if (r.timerAvviatoAl != null) {
            while (true) {
                tempoCorrente = vm.tempoEffettivo(vm.ripInEditing.value ?: r)
                kotlinx.coroutines.delay(30_000L)
            }
        } else {
            tempoCorrente = r.tempoLavoroMs
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (riparazioneId == null || riparazioneId == 0L)
                                stringResource(R.string.dettaglio_titolo_nuova)
                            else "Riparazione #${r.numeroProgressivo}"
                        )
                        // MODIFICA 2: mostra timer live nella toolbar quando in lavorazione
                        if (r.stato == StatoRiparazione.IN_LAVORAZIONE && r.timerAvviatoAl != null) {
                            Text(
                                "⏱ ${DateFmt.durata(tempoCorrente)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorLavorazione
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.terminaEditing(); onIndietro() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.indietro))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // === FOTO ===
            if (r.fotoPaths.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            if (camPermGranted) avviaFoto()
                            else camPermLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.scatta_foto), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(r.fotoPaths) { path ->
                        Box(
                            modifier = Modifier
                                .size(160.dp, 120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    // MODIFICA 1: tap sulla thumbnail → ingrandimento
                                    .clickable { fotoIngrandita = path }
                            )
                            IconButton(
                                onClick = {
                                    aggiorna(r.copy(fotoPaths = r.fotoPaths - path))
                                    try { java.io.File(path).delete() } catch (_: Exception) {}
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd).padding(4.dp)
                                    .size(28.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Close, "Rimuovi", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .size(160.dp, 120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (camPermGranted) avviaFoto()
                                    else camPermLauncher.launch(android.Manifest.permission.CAMERA)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            EtichettaCampo(stringResource(R.string.cliente))
            OutlinedTextField(value = r.cliente, onValueChange = { aggiorna(r.copy(cliente = it)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))

            EtichettaCampo(stringResource(R.string.telefono))
            OutlinedTextField(
                value = r.telefono, onValueChange = { aggiorna(r.copy(telefono = it)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            if (r.telefono.isNotEmpty() && (riparazioneId == null || riparazioneId == 0L)) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { SmsUtil.apriSms(ctx, r.telefono, SmsUtil.messaggioPresaInCarico(r)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sms, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp)); Text("Invia SMS preso in carico")
                }
            }

            Spacer(Modifier.height(16.dp))

            EtichettaCampo(stringResource(R.string.tipo_dispositivo))
            TipoDispositivoDropdown(selected = r.tipoDispositivo, onSelect = { aggiorna(r.copy(tipoDispositivo = it)) })

            Spacer(Modifier.height(16.dp))

            EtichettaCampo(stringResource(R.string.marca_modello))
            OutlinedTextField(value = r.marcaModello, onValueChange = { aggiorna(r.copy(marcaModello = it)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))

            EtichettaCampo(stringResource(R.string.problema))
            OutlinedTextField(value = r.problema, onValueChange = { aggiorna(r.copy(problema = it)) },
                modifier = Modifier.fillMaxWidth().height(100.dp))

            Spacer(Modifier.height(16.dp))

            // === STATO CON TIMER (modifica 2) ===
            EtichettaCampo(stringResource(R.string.stato))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipColorato("In attesa", ColorAttesa, r.stato == StatoRiparazione.IN_ATTESA) {
                    aggiorna(vm.applicaCambioStato(r, StatoRiparazione.IN_ATTESA))
                }
                ChipColorato("In lavorazione", ColorLavorazione, r.stato == StatoRiparazione.IN_LAVORAZIONE) {
                    aggiorna(vm.applicaCambioStato(r, StatoRiparazione.IN_LAVORAZIONE))
                }
                ChipColorato("Pronto", ColorPronto, r.stato == StatoRiparazione.PRONTO) {
                    aggiorna(vm.applicaCambioStato(r, StatoRiparazione.PRONTO))
                }
                ChipColorato("Consegnato", ColorConsegnato, r.stato == StatoRiparazione.CONSEGNATO) {
                    aggiorna(vm.applicaCambioStato(r, StatoRiparazione.CONSEGNATO))
                }
            }

            // MODIFICA 2: badge tempo lavoro
            if (tempoCorrente > 0 || r.stato == StatoRiparazione.IN_LAVORAZIONE) {
                Spacer(Modifier.height(8.dp))
                val timerLabel = if (r.timerAvviatoAl != null)
                    "⏱ In lavorazione da: ${DateFmt.durata(tempoCorrente)}"
                else "Tempo lavoro totale: ${DateFmt.durata(tempoCorrente)}"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (r.timerAvviatoAl != null) ColorLavorazione.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        timerLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.timerAvviatoAl != null) ColorLavorazione
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            EtichettaCampo(stringResource(R.string.note_interne))
            OutlinedTextField(value = r.noteInterne, onValueChange = { aggiorna(r.copy(noteInterne = it)) },
                modifier = Modifier.fillMaxWidth().height(80.dp))

            Spacer(Modifier.height(12.dp))

            EtichettaCampo("Prezzo riparazione (€)")
            OutlinedTextField(value = r.prezzoRiparazione, onValueChange = { aggiorna(r.copy(prezzoRiparazione = it)) },
                singleLine = true, placeholder = { Text("es. 45,00") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            // === DATE (sola lettura) ===
            CampoDataLettura("Data ingresso", DateFmt.datetime(r.dataIngresso))
            if (r.dataPronto != null) { Spacer(Modifier.height(6.dp)); CampoDataLettura("Pronto il", DateFmt.datetime(r.dataPronto)) }
            if (r.dataConsegna != null) { Spacer(Modifier.height(6.dp)); CampoDataLettura("Consegnato il", DateFmt.datetime(r.dataConsegna)) }

            Spacer(Modifier.height(20.dp))

            // === AZIONI ===
            if (r.telefono.isNotEmpty() && (riparazioneId == null || riparazioneId == 0L)) {
                OutlinedButton(
                    onClick = { SmsUtil.apriSms(ctx, r.telefono, SmsUtil.messaggioPresaInCarico(r)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sms, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp)); Text("Invia SMS preso in carico")
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(onClick = { vm.salva(r) { vm.terminaEditing(); onIndietro() } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.salva))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        vm.salva(r) { id ->
                            val rec = r.copy(id = id)
                            val pdf = PdfUtil.generaEtichetta(ctx, rec)
                            PdfUtil.condividi(ctx, pdf)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.stampa_etichetta))
            }

            if (r.telefono.isNotEmpty() && (r.stato == StatoRiparazione.PRONTO || r.stato == StatoRiparazione.CONSEGNATO)) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { SmsUtil.apriSms(ctx, r.telefono, SmsUtil.messaggioPredefinto(r)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sms, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp)); Text("Notifica SMS pronto per il ritiro")
                }
            }

            if (riparazioneId != null && riparazioneId != 0L) {
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { mostraConfermaElimina = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.elimina_riparazione))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (mostraConfermaElimina) {
        ConfermaDialog(
            titolo = stringResource(R.string.conferma_elimina_titolo),
            testo = stringResource(R.string.conferma_elimina_testo),
            sottoinfo = "${r.cliente}\n${r.tipoDispositivo.label} — ${r.marcaModello}\n#${r.numeroProgressivo}",
            confermaLabel = stringResource(R.string.elimina),
            onConferma = { vm.elimina(r); vm.terminaEditing(); mostraConfermaElimina = false; onIndietro() },
            onAnnulla = { mostraConfermaElimina = false }
        )
    }

    // MODIFICA 1: viewer foto fullscreen
    if (fotoIngrandita != null) {
        Dialog(
            onDismissRequest = { fotoIngrandita = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black).clickable { fotoIngrandita = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fotoIngrandita,
                    contentDescription = "Foto ingrandita",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
                IconButton(
                    onClick = { fotoIngrandita = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                        .size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Close, "Chiudi", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipoDispositivoDropdown(selected: TipoDispositivo, onSelect: (TipoDispositivo) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected.label, onValueChange = {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TipoDispositivo.values().forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { onSelect(t); expanded = false })
            }
        }
    }
}

@Composable
private fun CampoDataLettura(label: String, valore: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        EtichettaCampo(label)
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(valore, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EtichettaCampo(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp), fontSize = 11.sp, letterSpacing = 0.5.sp)
}

@Composable
private fun ChipColorato(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
