package it.officina.riparazioni.data

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.Calendar

class RiparazioneRepository(private val dao: RiparazioneDao) {

    fun all(): Flow<List<Riparazione>> = dao.getAll()
    fun byCliente(c: String): Flow<List<Riparazione>> = dao.getByCliente(c)
    fun clientiDistinti(): Flow<List<String>> = dao.getClientiDistinti()
    suspend fun byId(id: Long): Riparazione? = dao.getById(id)
    suspend fun byProgressivo(num: String): Riparazione? = dao.getByProgressivo(num)

    suspend fun generaProgressivo(): String {
        val anno = Calendar.getInstance().get(Calendar.YEAR)
        val n = dao.countByPrefix("$anno-") + 1
        return "$anno-${"%04d".format(n)}"
    }

    suspend fun insert(r: Riparazione): Long = dao.insert(r)
    suspend fun insertIgnore(r: Riparazione): Long = dao.insertIgnore(r)
    suspend fun update(r: Riparazione) = dao.update(r)

    suspend fun delete(r: Riparazione) {
        eliminaFoto(r.fotoPaths); dao.delete(r)
    }

    suspend fun deleteMany(rips: List<Riparazione>) {
        rips.forEach { eliminaFoto(it.fotoPaths) }; dao.deleteByIds(rips.map { it.id })
    }

    suspend fun deleteAll(tutte: List<Riparazione>) {
        tutte.forEach { eliminaFoto(it.fotoPaths) }; dao.deleteAll()
    }

    private fun eliminaFoto(paths: List<String>) {
        paths.forEach { try { File(it).takeIf { f -> f.exists() }?.delete() } catch (_: Exception) {} }
    }
}
