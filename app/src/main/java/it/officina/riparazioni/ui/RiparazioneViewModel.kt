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

    private val _query  = MutableStateFlow("")
    private val _filtro = MutableStateFlow(FiltroStato.TUTTI)
    val query:  StateFlow<String>      = _query
    val filtro: StateFlow<FiltroStato> = _filtro

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

    fun setQuery(q: String)      { _query.value = q }
    fun setFiltro(f: FiltroStato) { _filtro.value = f }

    suspend fun byId(id: Long) = repo.byId(id)
    suspend fun nuovoProgressivo() = repo.generaProgressivo()

    /**
     * Applica la logica del timer al cambio di stato.
     * - Passaggio a IN_LAVORAZIONE: avvia il timer (imposta timerAvviatoAl = now)
     * - Passaggio a qualsiasi altro stato: ferma il timer, accumula il tempo
     */
    fun applicaCambioStato(r: Riparazione, nuovoStato: StatoRiparazione): Riparazione {
        val now = System.currentTimeMillis()
        return when (nuovoStato) {
            StatoRiparazione.IN_LAVORAZIONE -> {
                // Avvia timer solo se non è già in corso
                if (r.timerAvviatoAl == null) r.copy(stato = nuovoStato, timerAvviatoAl = now)
                else r.copy(stato = nuovoStato)
            }
            StatoRiparazione.PRONTO -> {
                val extra = if (r.timerAvviatoAl != null) now - r.timerAvviatoAl else 0L
                r.copy(
                    stato = nuovoStato,
                    timerAvviatoAl = null,
                    tempoLavoroMs = r.tempoLavoroMs + extra,
                    dataPronto = r.dataPronto ?: now
                )
            }
            StatoRiparazione.CONSEGNATO -> {
                val extra = if (r.timerAvviatoAl != null) now - r.timerAvviatoAl else 0L
                r.copy(
                    stato = nuovoStato,
                    timerAvviatoAl = null,
                    tempoLavoroMs = r.tempoLavoroMs + extra,
                    dataPronto = r.dataPronto ?: now,
                    dataConsegna = r.dataConsegna ?: now
                )
            }
            StatoRiparazione.IN_ATTESA -> {
                val extra = if (r.timerAvviatoAl != null) now - r.timerAvviatoAl else 0L
                r.copy(
                    stato = nuovoStato,
                    timerAvviatoAl = null,
                    tempoLavoroMs = r.tempoLavoroMs + extra
                )
            }
        }
    }

    /** Tempo effettivo corrente includendo sessione in corso (se timer attivo) */
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

    // ─── IMPORT CSV ────────────────────────────────────────────────────────────

    suspend fun importaTotale(righe: List<Riparazione>): Triple<Int, Int, Int> {
        repo.deleteAll(tutte.value)
        var ins = 0
        righe.forEach { r -> repo.insert(r.copy(id = 0)); ins++ }
        return Triple(righe.size, ins, 0)
    }

    suspend fun importaAggiuntivaSovrascrivi(righe: List<Riparazione>): Triple<Int, Int, Int> {
        var nuovi = 0; var aggiornati = 0
        righe.forEach { r ->
            val esistente = repo.byProgressivo(r.numeroProgressivo)
            if (esistente != null) { repo.update(r.copy(id = esistente.id)); aggiornati++ }
            else { repo.insert(r.copy(id = 0)); nuovi++ }
        }
        return Triple(righe.size, nuovi, aggiornati)
    }

    suspend fun importaAggiuntivaIgnora(righe: List<Riparazione>): Triple<Int, Int, Int> {
        var nuovi = 0; var saltati = 0
        righe.forEach { r ->
            val esistente = repo.byProgressivo(r.numeroProgressivo)
            if (esistente != null) saltati++
            else { repo.insert(r.copy(id = 0)); nuovi++ }
        }
        return Triple(righe.size, nuovi, saltati)
    }
}

class RiparazioneVMFactory(private val repo: RiparazioneRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>) = RiparazioneViewModel(repo) as T
}
