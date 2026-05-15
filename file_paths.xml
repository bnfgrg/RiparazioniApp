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

    fun salva(r: Riparazione, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (r.id == 0L) repo.insert(r) else { repo.update(r); r.id }
            onDone(id)
        }
    }

    fun elimina(r: Riparazione) {
        viewModelScope.launch { repo.delete(r) }
    }

    fun eliminaMultiple(rips: List<Riparazione>) {
        viewModelScope.launch { repo.deleteMany(rips) }
    }

    fun eliminaTutte(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteAll(tutte.value)
            onDone()
        }
    }

    /**
     * Elimina solo le riparazioni attualmente visibili (filtrate per query+stato).
     */
    fun eliminaFiltrate(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteMany(filtrate.value)
            onDone()
        }
    }
}

class RiparazioneVMFactory(private val repo: RiparazioneRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RiparazioneViewModel(repo) as T
    }
}
