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

    suspend fun importaTotale(righe: List<Riparazione>): Triple<Int, Int, Int> {
        repo.deleteAll(tutte.value); var ins = 0
        righe.forEach { repo.insert(it.copy(id = 0)); ins++ }
        return Triple(righe.size, ins, 0)
    }
    suspend fun importaAggiuntivaSovrascrivi(righe: List<Riparazione>): Triple<Int, Int, Int> {
        var nuovi = 0; var agg = 0
        righe.forEach { r ->
            val e = repo.byProgressivo(r.numeroProgressivo)
            if (e != null) { repo.update(r.copy(id = e.id)); agg++ } else { repo.insert(r.copy(id = 0)); nuovi++ }
        }
        return Triple(righe.size, nuovi, agg)
    }
    suspend fun importaAggiuntivaIgnora(righe: List<Riparazione>): Triple<Int, Int, Int> {
        var nuovi = 0; var salt = 0
        righe.forEach { r ->
            if (repo.byProgressivo(r.numeroProgressivo) != null) salt++
            else { repo.insert(r.copy(id = 0)); nuovi++ }
        }
        return Triple(righe.size, nuovi, salt)
    }
}

class RiparazioneVMFactory(private val repo: RiparazioneRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>) = RiparazioneViewModel(repo) as T
}
