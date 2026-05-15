package it.officina.riparazioni.data

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.Calendar

class RiparazioneRepository(private val dao: RiparazioneDao) {

    fun all(): Flow<List<Riparazione>> = dao.getAll()
    fun byCliente(cliente: String): Flow<List<Riparazione>> = dao.getByCliente(cliente)
    fun clientiDistinti(): Flow<List<String>> = dao.getClientiDistinti()

    suspend fun byId(id: Long): Riparazione? = dao.getById(id)

    /**
     * Genera prossimo numero progressivo nel formato YYYY-NNNN.
     * Conta le riparazioni dell'anno corrente e somma 1.
     */
    suspend fun generaProgressivo(): String {
        val anno = Calendar.getInstance().get(Calendar.YEAR)
        val prefix = "$anno-"
        val n = dao.countByPrefix(prefix) + 1
        return "$prefix${"%04d".format(n)}"
    }

    suspend fun insert(r: Riparazione): Long = dao.insert(r)
    suspend fun update(r: Riparazione) = dao.update(r)

    /**
     * Elimina una riparazione e i file foto associati.
     */
    suspend fun delete(r: Riparazione) {
        eliminaFoto(r.fotoPaths)
        dao.delete(r)
    }

    /**
     * Elimina piu' riparazioni e tutte le foto associate.
     */
    suspend fun deleteMany(rips: List<Riparazione>) {
        rips.forEach { eliminaFoto(it.fotoPaths) }
        dao.deleteByIds(rips.map { it.id })
    }

    /**
     * Cancella TUTTE le riparazioni e tutte le foto. Operazione irreversibile.
     */
    suspend fun deleteAll(tutteLeRiparazioni: List<Riparazione>) {
        tutteLeRiparazioni.forEach { eliminaFoto(it.fotoPaths) }
        dao.deleteAll()
    }

    private fun eliminaFoto(paths: List<String>) {
        paths.forEach { p ->
            try {
                val f = File(p)
                if (f.exists()) f.delete()
            } catch (_: Exception) { /* best effort */ }
        }
    }
}
