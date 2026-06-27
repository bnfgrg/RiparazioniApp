package it.officina.riparazioni.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.officina.riparazioni.data.Riparazione
import it.officina.riparazioni.data.RiparazioneRepository
import it.officina.riparazioni.data.StatoRiparazione
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FiltroStato {
    TUTTI, ATTESA, LAVORAZIONE, PRONTI, CONSEGNATI;

    fun matches(s: StatoRiparazione): Boolean = when (this) {
        TUTTI -> true
        ATTESA -> s == StatoRiparazione.IN_ATTESA
        LAVORAZIONE -> s == StatoRiparazione.IN_LAVORAZIONE
        PRONTI -> s == StatoRiparazione.PRONTO
        CONSEGNATI -> s == StatoRiparazione.CONSEGNATO
    }
}

class RiparazioneViewModel(
    private val repo: RiparazioneRepository
) : ViewModel() {

    // ─── STATO EDITING (sopravvive alla distruzione dell'Activity) ─────────────
    // Mantiene la riparazione in fase di modifica/creazione.
    // MutableStateFlow nel ViewModel non viene azzerato da Android quando
    // l'app va in background o lo schermo si spegne.
    private val _ripInEditing = MutableStateFlow<Riparazione?>(null)
    val ripInEditing: StateFlow<Riparazione?> = _ripInEditing

    fun iniziaEditing(r: Riparazione) { _ripInEditing.value = r }
    fun aggiornaEditing(r: Riparazione) { _ripInEditing.value = r }
    fun terminaEditing() { _ripInEditing.value = null }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filtro = MutableStateFlow(FiltroStato.TUTTI)
    val filtro: StateFlow<FiltroStato> = _filtro

    val tutte: StateFlow<List<Riparazione>> = repo.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtrate: StateFlow<List<Riparazione>> = combine(repo.all(), _query, _filtro) { lista, q, f ->
        lista.filter { r ->
            f.matches(r.stato) && (
                q.isEmpty() ||
                r.cliente.contains(q, ignoreCase = true) ||
                r.marcaModello.contains(q, ignoreCase = true) ||
                r.numeroProgressivo.contains(q, ignoreCase = true)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setFiltro(f: FiltroStato) { _filtro.value = f }

    suspend fun byId(id: Long) = repo.byId(id)
    suspend fun nuovoProgressivo(): String = repo.generaProgressivo()

    // ─── TIMER LAVORO (modifica 2) ──────────────────────────────────────────────

    /**
     * Applica la logica timer al cambio stato:
     * - IN_LAVORAZIONE: avvia il timer
     * - tutti gli altri: ferma il timer e accumula il tempo
     */
    fun applicaCambioStato(r: Riparazione, nuovoStato: StatoRiparazione): Riparazione {
        val now = System.currentTimeMillis()
        return when (nuovoStato) {
            StatoRiparazione.IN_LAVORAZIONE -> {
                if (r.timerAvviatoAl == null) r.copy(stato = nuovoStato, timerAvviatoAl = now)
                else r.copy(stato = nuovoStato)
            }
            StatoRiparazione.PRONTO -> {
                val extra = if (r.timerAvviatoAl != null) now - r.timerAvviatoAl else 0L
                r.copy(stato = nuovoStato, timerAvviatoAl = null,
                    tempoLavoroMs = r.tempoLavoroMs + extra,
                    dataPronto = r.dataPronto ?: now, dataConsegna = null)
            }
            StatoRiparazione.CONSEGNATO -> {
                val extra = if (r.timerAvviatoAl != null) now - r.timerAvviatoAl else 0L
                r.copy(stato = nuovoStato, timerAvviatoAl = null,
                    tempoLavoroMs = r.tempoLavoroMs + extra,
                    dataPronto = r.dataPronto ?: now,
                    dataConsegna = r.dataConsegna ?: now)
            }
            StatoRiparazione.IN_ATTESA -> {
                val extra = if (r.timerAvviatoAl != null) now - r.timerAvviatoAl else 0L
                r.copy(stato = nuovoStato, timerAvviatoAl = null,
                    tempoLavoroMs = r.tempoLavoroMs + extra)
            }
        }
    }

    /** Tempo effettivo corrente includendo sessione in corso */
    fun tempoEffettivo(r: Riparazione): Long {
        val extra = if (r.timerAvviatoAl != null) System.currentTimeMillis() - r.timerAvviatoAl else 0L
        return r.tempoLavoroMs + extra
    }

    // ─── CRUD ───────────────────────────────────────────────────────────────────

    fun salva(r: Riparazione, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (r.id == 0L) repo.insert(r) else { repo.update(r); r.id }
            onDone(id)
        }
    }

    fun elimina(r: Riparazione) { viewModelScope.launch { repo.delete(r) } }

    fun eliminaMultiple(rips: List<Riparazione>) { viewModelScope.launch { repo.deleteMany(rips) } }

    fun eliminaTutte(onDone: () -> Unit = {}) {
        viewModelScope.launch { repo.deleteAll(tutte.value); onDone() }
    }

    fun eliminaFiltrate(onDone: () -> Unit = {}) {
        viewModelScope.launch { repo.deleteMany(filtrate.value); onDone() }
    }

    // ─── IMPORT CSV (modifica 4) ─────────────────────────────────────────────────

    /** Importazione totale: cancella tutto e reimporta */
    suspend fun importaTotale(righe: List<Riparazione>): Triple<Int, Int, Int> {
        repo.deleteAll(tutte.value)
        var ins = 0
        righe.forEach { r -> repo.insert(r.copy(id = 0)); ins++ }
        return Triple(righe.size, ins, 0)
    }

    /** Aggiuntiva: sovrascrive i duplicati per numero progressivo */
    suspend fun importaAggiuntivaSovrascrivi(righe: List<Riparazione>): Triple<Int, Int, Int> {
        var nuovi = 0; var aggiornati = 0
        righe.forEach { r ->
            val esistente = repo.byProgressivo(r.numeroProgressivo)
            if (esistente != null) { repo.update(r.copy(id = esistente.id)); aggiornati++ }
            else { repo.insert(r.copy(id = 0)); nuovi++ }
        }
        return Triple(righe.size, nuovi, aggiornati)
    }

    /** Aggiuntiva: salta i duplicati, crea nuovi record con ID diverso */
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
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RiparazioneViewModel(repo) as T
}
