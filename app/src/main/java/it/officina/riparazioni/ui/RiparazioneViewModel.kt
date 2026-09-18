package it.officina.riparazioni.ui

import androidx.lifecycle.*
import it.officina.riparazioni.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FiltroStato {
    TUTTI, ATTESA, LAVORAZIONE, PRONTI, CONSEGNATI;
    fun matches(s: StatoRiparazione) = when (this) {
        TUTTI -> true; ATTESA -> s == StatoRiparazione.IN_ATTESA
        LAVORAZIONE -> s == StatoRiparazione.IN_LAVORAZIONE
        PRONTI -> s == StatoRiparazione.PRONTO; CONSEGNATI -> s == StatoRiparazione.CONSEGNATO
    }
}

class RiparazioneViewModel(private val repo: RiparazioneRepository) : ViewModel() {

    // ─── CANALE: cliente appena creato da associare alla riparazione ────────────
    // Emette l'id del cliente quando viene creato da dentro il flusso nuova riparazione
    private val _clienteAppenaCreato = kotlinx.coroutines.flow.MutableSharedFlow<Long>(replay = 1)
    val clienteAppenaCreato: kotlinx.coroutines.flow.SharedFlow<Long> = _clienteAppenaCreato

    fun segnalaClienteCreato(id: Long) {
        viewModelScope.launch { _clienteAppenaCreato.emit(id) }
    }
    fun resetClienteCreato() {
        _clienteAppenaCreato.resetReplayCache()
    }

    // ─── STATO EDITING (sopravvive alla distruzione Activity) ───────────────────
    private val _ripInEditing = MutableStateFlow<Riparazione?>(null)
    val ripInEditing: StateFlow<Riparazione?> = _ripInEditing
    fun iniziaEditing(r: Riparazione) { _ripInEditing.value = r }
    fun aggiornaEditing(r: Riparazione) { _ripInEditing.value = r }
    fun terminaEditing() { _ripInEditing.value = null }

    // ─── FILTRI LISTA ────────────────────────────────────────────────────────────
    private val _query  = MutableStateFlow("")
    private val _filtro = MutableStateFlow(FiltroStato.TUTTI)
    val query:  StateFlow<String>      = _query
    val filtro: StateFlow<FiltroStato> = _filtro
    fun setQuery(q: String)       { _query.value = q }
    fun setFiltro(f: FiltroStato) { _filtro.value = f }

    val tutte: StateFlow<List<Riparazione>> = repo.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtrate: StateFlow<List<Riparazione>> =
        combine(repo.all(), _query, _filtro) { lista, q, f ->
            lista.filter { r ->
                f.matches(r.stato) && (q.isEmpty() ||
                    r.cliente.contains(q, ignoreCase = true) ||
                    r.marcaModello.contains(q, ignoreCase = true) ||
                    r.numeroProgressivo.contains(q, ignoreCase = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── CLIENTI ────────────────────────────────────────────────────────────────
    val clienti: StateFlow<List<Cliente>> = repo.allClienti()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _queryClienti = MutableStateFlow("")
    val queryClienti: StateFlow<String> = _queryClienti
    fun setQueryClienti(q: String) { _queryClienti.value = q }

    val clientiFiltrati: StateFlow<List<Cliente>> =
        combine(repo.allClienti(), _queryClienti) { lista, q ->
            if (q.isEmpty()) lista
            else lista.filter {
                it.nome.contains(q, ignoreCase = true) ||
                it.cognome.contains(q, ignoreCase = true) ||
                it.telefono.contains(q, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun riparazioniByCliente(id: Long): Flow<List<Riparazione>> = repo.byClienteId(id)

    suspend fun clienteById(id: Long) = repo.clienteById(id)

    fun salvaCliente(c: Cliente, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (c.id == 0L) repo.insertCliente(c) else { repo.updateCliente(c); c.id }
            onDone(id)
        }
    }

    fun eliminaCliente(c: Cliente) { viewModelScope.launch { repo.deleteCliente(c) } }

    // ─── RIPARAZIONI ────────────────────────────────────────────────────────────
    suspend fun byId(id: Long) = repo.byId(id)
    suspend fun nuovoProgressivo() = repo.generaProgressivo()

    fun applicaCambioStato(r: Riparazione, nuovoStato: StatoRiparazione): Riparazione {
        val now = System.currentTimeMillis()
        return when (nuovoStato) {
            StatoRiparazione.IN_LAVORAZIONE ->
                if (r.timerAvviatoAl == null) r.copy(stato = nuovoStato, timerAvviatoAl = now)
                else r.copy(stato = nuovoStato)
            StatoRiparazione.PRONTO -> {
                val extra = if (r.timerAvviatoAl != null) now - r.timerAvviatoAl else 0L
                r.copy(stato = nuovoStato, timerAvviatoAl = null,
                    tempoLavoroMs = r.tempoLavoroMs + extra, dataPronto = r.dataPronto ?: now, dataConsegna = null)
            }
            StatoRiparazione.CONSEGNATO -> {
                val extra = if (r.timerAvviatoAl != null) now - r.timerAvviatoAl else 0L
                r.copy(stato = nuovoStato, timerAvviatoAl = null,
                    tempoLavoroMs = r.tempoLavoroMs + extra, dataPronto = r.dataPronto ?: now, dataConsegna = r.dataConsegna ?: now)
            }
            StatoRiparazione.IN_ATTESA -> {
                val extra = if (r.timerAvviatoAl != null) now - r.timerAvviatoAl else 0L
                r.copy(stato = nuovoStato, timerAvviatoAl = null, tempoLavoroMs = r.tempoLavoroMs + extra)
            }
        }
    }

    fun tempoEffettivo(r: Riparazione): Long {
        val extra = if (r.timerAvviatoAl != null) System.currentTimeMillis() - r.timerAvviatoAl else 0L
        return r.tempoLavoroMs + extra
    }

    fun salva(r: Riparazione, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (r.id == 0L) repo.insert(r) else { repo.update(r); r.id }
            onDone(id)
        }
    }

    fun elimina(r: Riparazione) { viewModelScope.launch { repo.delete(r) } }
    fun eliminaMultiple(rips: List<Riparazione>) { viewModelScope.launch { repo.deleteMany(rips) } }
    fun eliminaTutte(onDone: () -> Unit = {}) { viewModelScope.launch { repo.deleteAll(tutte.value); onDone() } }
    fun eliminaFiltrate(onDone: () -> Unit = {}) { viewModelScope.launch { repo.deleteMany(filtrate.value); onDone() } }

    /**
     * Import totale: cancella tutto e reimporta clienti + riparazioni.
     * Ricostruisce il mapping oldId -> newId per i clienti, poi aggiorna i clienteId delle riparazioni.
     */
    suspend fun importaTotale(data: it.officina.riparazioni.util.ExportUtil.ImportData): Triple<Int, Int, Int> {
        repo.deleteAll(tutte.value)
        // Prima importa clienti e costruisce la mappa vecchio-id -> nuovo-id
        val idMap = mutableMapOf<Long, Long>()
        data.clienti.forEach { c ->
            val oldId = c.id
            val newId = repo.insertCliente(c.copy(id = 0))
            if (oldId > 0) idMap[oldId] = newId
        }
        // Poi importa riparazioni aggiornando il clienteId
        var ins = 0
        data.riparazioni.forEach { r ->
            val nuovoClienteId = r.clienteId?.let { idMap[it] }
            repo.insert(r.copy(id = 0, clienteId = nuovoClienteId)); ins++
        }
        return Triple(data.riparazioni.size, ins, 0)
    }

    suspend fun importaAggiuntivaSovrascrivi(data: it.officina.riparazioni.util.ExportUtil.ImportData): Triple<Int, Int, Int> {
        // Importa clienti nuovi (non sovrascrive esistenti per telefono)
        val idMap = mutableMapOf<Long, Long>()
        data.clienti.forEach { c ->
            val oldId = c.id
            val newId = repo.insertCliente(c.copy(id = 0))
            if (oldId > 0) idMap[oldId] = newId
        }
        var nuovi = 0; var agg = 0
        data.riparazioni.forEach { r ->
            val nuovoClienteId = r.clienteId?.let { idMap[it] } ?: r.clienteId
            val e = repo.byProgressivo(r.numeroProgressivo)
            if (e != null) { repo.update(r.copy(id = e.id, clienteId = nuovoClienteId)); agg++ }
            else { repo.insert(r.copy(id = 0, clienteId = nuovoClienteId)); nuovi++ }
        }
        return Triple(data.riparazioni.size, nuovi, agg)
    }

    suspend fun importaAggiuntivaIgnora(data: it.officina.riparazioni.util.ExportUtil.ImportData): Triple<Int, Int, Int> {
        val idMap = mutableMapOf<Long, Long>()
        data.clienti.forEach { c ->
            val oldId = c.id
            val newId = repo.insertCliente(c.copy(id = 0))
            if (oldId > 0) idMap[oldId] = newId
        }
        var nuovi = 0; var salt = 0
        data.riparazioni.forEach { r ->
            val nuovoClienteId = r.clienteId?.let { idMap[it] } ?: r.clienteId
            if (repo.byProgressivo(r.numeroProgressivo) != null) salt++
            else { repo.insert(r.copy(id = 0, clienteId = nuovoClienteId)); nuovi++ }
        }
        return Triple(data.riparazioni.size, nuovi, salt)
    }

    // Versioni retrocompat con solo lista riparazioni
    suspend fun importaTotale(righe: List<it.officina.riparazioni.data.Riparazione>): Triple<Int, Int, Int> =
        importaTotale(it.officina.riparazioni.util.ExportUtil.ImportData(righe, emptyList()))
    suspend fun importaAggiuntivaSovrascrivi(righe: List<it.officina.riparazioni.data.Riparazione>): Triple<Int, Int, Int> =
        importaAggiuntivaSovrascrivi(it.officina.riparazioni.util.ExportUtil.ImportData(righe, emptyList()))
    suspend fun importaAggiuntivaIgnora(righe: List<it.officina.riparazioni.data.Riparazione>): Triple<Int, Int, Int> =
        importaAggiuntivaIgnora(it.officina.riparazioni.util.ExportUtil.ImportData(righe, emptyList()))
}

class RiparazioneVMFactory(private val repo: RiparazioneRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>) = RiparazioneViewModel(repo) as T
}
