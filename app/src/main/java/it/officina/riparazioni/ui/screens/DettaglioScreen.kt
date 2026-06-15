package it.officina.riparazioni.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import it.officina.riparazioni.data.Riparazione
import it.officina.riparazioni.data.StatoRiparazione
import it.officina.riparazioni.data.TipoDispositivo
import it.officina.riparazioni.ui.RiparazioneViewModel
import it.officina.riparazioni.ui.components.ConfermaDialog
import it.officina.riparazioni.ui.theme.*
import it.officina.riparazioni.util.*
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

    var rip by remember { mutableStateOf<Riparazione?>(null) }
    var caricato by remember { mutableStateOf(false) }
    var mostraConfermaElimina by remember { mutableStateOf(false) }

    // Foto da ingrandire
    var fotoIngrandita by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(riparazioneId) {
        rip = if (riparazioneId == null || riparazioneId == 0L)
            Riparazione(numeroProgressivo = vm.nuovoProgressivo())
        else vm.byId(riparazioneId) ?: Riparazione(numeroProgressivo = vm.nuovoProgressivo())
        caricato = true
    }

    if (!caricato || rip == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Caricamento…") }
        return
    }

    val r = rip!!

    // Permesso camera
    var camPermGranted by remember { mutableStateOf(false) }
    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        camPermGranted = it
    }
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && pendingPhotoPath != null) rip = rip!!.copy(fotoPaths = rip!!.fotoPaths + pendingPhotoPath!!)
        pendingPhotoPath = null
    }
    LaunchedEffect(Unit) {
        camPermGranted = ctx.checkSelfPermission(android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    fun avviaFoto() {
        val file = PhotoUtil.newPhotoFile(ctx)
        pendingPhotoPath = file.absolutePath
        cameraLauncher.launch(PhotoUtil.uriFor(ctx, file))
    }

    // Timer live
    var tempoCorrente by remember { mutableStateOf(vm.tempoEffettivo(r)) }
    LaunchedEffect(r.timerAvviatoAl, r.tempoLavoroMs) {
        if (r.timerAvviatoAl != null) {
            while (true) {
                tempoCorrente = vm.tempoEffettivo(rip ?: r)
                kotlinx.coroutines.delay(30_000L) // aggiorna ogni 30s
            }
        } else {
            tempoCorrente = r.tempoLavoroMs
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(if (riparazioneId == null || riparazioneId == 0L) "Nuova riparazione"
                         else "Riparazione #${r.numeroProgressivo}")
                    if (r.stato == StatoRiparazione.IN_LAVORAZIONE && r.timerAvviatoAl != null) {
                        Text("⏱ ${DateFmt.durata(tempoCorrente)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorLavorazione)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onIndietro) { Icon(Icons.Default.ArrowBack, "Indietro") }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {

            // ─── FOTO ───────────────────────────────────────────────────────────
            if (r.fotoPaths.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
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
                        Text("Tocca per scattare foto", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(r.fotoPaths) { path ->
                        Box(modifier = Modifier.size(160.dp, 120.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)) {
                            AsyncImage(
                                model = path, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                                    // PUNTO 1: tap sulla foto → ingrandimento
                                    .clickable { fotoIngrandita = path }
                            )
                            // Pulsante rimozione
                            IconButton(
                                onClick = {
                                    rip = rip!!.copy(fotoPaths = rip!!.fotoPaths - path)
                                    try { java.io.File(path).delete() } catch (_: Exception) {}
                                },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                    .size(28.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) { Icon(Icons.Default.Close, "Rimuovi", tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    item {
                        Box(modifier = Modifier.size(160.dp, 120.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (camPermGranted) avviaFoto()
                                else camPermLauncher.launch(android.Manifest.permission.CAMERA)
                            },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.PhotoCamera, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ─── CLIENTE ────────────────────────────────────────────────────────
            EtichettaCampo("Cliente")
            OutlinedTextField(value = r.cliente, onValueChange = { rip = r.copy(cliente = it) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))
            EtichettaCampo("Telefono")
            OutlinedTextField(
                value = r.telefono, onValueChange = { rip = r.copy(telefono = it) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // ─── TIPO DISPOSITIVO ───────────────────────────────────────────────
            EtichettaCampo("Tipo dispositivo")
            TipoDispositivoDropdown(selected = r.tipoDispositivo, onSelect = { rip = r.copy(tipoDispositivo = it) })

            Spacer(Modifier.height(16.dp))
            EtichettaCampo("Marca e modello")
            OutlinedTextField(value = r.marcaModello, onValueChange = { rip = r.copy(marcaModello = it) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))
            EtichettaCampo("Problema riscontrato")
            OutlinedTextField(value = r.problema, onValueChange = { rip = r.copy(problema = it) },
                modifier = Modifier.fillMaxWidth().height(100.dp))

            Spacer(Modifier.height(16.dp))

            // ─── STATO CON TIMER ────────────────────────────────────────────────
            EtichettaCampo("Stato")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipColorato("In attesa", ColorAttesa, r.stato == StatoRiparazione.IN_ATTESA) {
                    rip = vm.applicaCambioStato(r, StatoRiparazione.IN_ATTESA)
                }
                ChipColorato("In lavorazione", ColorLavorazione, r.stato == StatoRiparazione.IN_LAVORAZIONE) {
                    rip = vm.applicaCambioStato(r, StatoRiparazione.IN_LAVORAZIONE)
                }
                ChipColorato("Pronto", ColorPronto, r.stato == StatoRiparazione.PRONTO) {
                    rip = vm.applicaCambioStato(r, StatoRiparazione.PRONTO)
                }
                ChipColorato("Consegnato", ColorConsegnato, r.stato == StatoRiparazione.CONSEGNATO) {
                    rip = vm.applicaCambioStato(r, StatoRiparazione.CONSEGNATO)
                }
            }

            // Mostra tempo lavoro accumulato
            if (tempoCorrente > 0 || r.stato == StatoRiparazione.IN_LAVORAZIONE) {
                Spacer(Modifier.height(8.dp))
                val label = if (r.timerAvviatoAl != null) "⏱ In lavorazione da: ${DateFmt.durata(tempoCorrente)}"
                            else "Tempo lavoro: ${DateFmt.durata(tempoCorrente)}"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (r.timerAvviatoAl != null) ColorLavorazione.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.timerAvviatoAl != null) ColorLavorazione else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            EtichettaCampo("Note interne")
            OutlinedTextField(value = r.noteInterne, onValueChange = { rip = r.copy(noteInterne = it) },
                modifier = Modifier.fillMaxWidth().height(80.dp))

            Spacer(Modifier.height(12.dp))
            EtichettaCampo("Prezzo riparazione (€)")
            OutlinedTextField(value = r.prezzoRiparazione, onValueChange = { rip = r.copy(prezzoRiparazione = it) },
                singleLine = true, placeholder = { Text("es. 45,00") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            // ─── DATE SOLA LETTURA ──────────────────────────────────────────────
            CampoDataLettura("Data ingresso", DateFmt.datetime(r.dataIngresso))
            if (r.dataPronto != null) { Spacer(Modifier.height(6.dp)); CampoDataLettura("Pronto il", DateFmt.datetime(r.dataPronto)) }
            if (r.dataConsegna != null) { Spacer(Modifier.height(6.dp)); CampoDataLettura("Consegnato il", DateFmt.datetime(r.dataConsegna)) }

            Spacer(Modifier.height(20.dp))

            // ─── AZIONI ─────────────────────────────────────────────────────────
            // SMS preso in carico (solo nuova scheda con telefono)
            if (r.telefono.isNotEmpty() && (riparazioneId == null || riparazioneId == 0L)) {
                OutlinedButton(onClick = { SmsUtil.apriSms(ctx, r.telefono, SmsUtil.messaggioPresaInCarico(r)) },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Sms, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp)); Text("Invia SMS preso in carico")
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(onClick = { vm.salva(r) { onIndietro() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Salva")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { scope.launch { vm.salva(r) { id -> val rec = r.copy(id = id); val pdf = PdfUtil.generaEtichetta(ctx, rec); PdfUtil.condividi(ctx, pdf) } } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp)); Text("Stampa etichetta")
            }

            // SMS notifica pronto
            if (r.telefono.isNotEmpty() && (r.stato == StatoRiparazione.PRONTO || r.stato == StatoRiparazione.CONSEGNATO)) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { SmsUtil.apriSms(ctx, r.telefono, SmsUtil.messaggioPredefinto(r)) },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Sms, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp)); Text("Notifica SMS pronto per il ritiro")
                }
            }

            // Elimina (solo schede esistenti)
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
                    Spacer(Modifier.size(8.dp)); Text("Elimina riparazione")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Dialog conferma eliminazione
    if (mostraConfermaElimina) {
        ConfermaDialog(
            titolo = "Eliminare la riparazione?",
            testo = "Verranno rimossi definitivamente il record e le foto associate. Operazione non annullabile.",
            sottoinfo = "${r.cliente}\n${r.tipoDispositivo.label} — ${r.marcaModello}\n#${r.numeroProgressivo}",
            confermaLabel = "Elimina",
            onConferma = { vm.elimina(r); mostraConfermaElimina = false; onIndietro() },
            onAnnulla = { mostraConfermaElimina = false }
        )
    }

    // PUNTO 1: Viewer foto a schermo intero
    if (fotoIngrandita != null) {
        Dialog(
            onDismissRequest = { fotoIngrandita = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black)
                    .clickable { fotoIngrandita = null },
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
                ) { Icon(Icons.Default.Close, "Chiudi", tint = Color.White) }
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

@Composable
private fun CampoDataLettura(label: String, valore: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        EtichettaCampo(label)
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 10.dp)
        ) { Text(valore, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun EtichettaCampo(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp), fontSize = 11.sp, letterSpacing = 0.5.sp)
}
