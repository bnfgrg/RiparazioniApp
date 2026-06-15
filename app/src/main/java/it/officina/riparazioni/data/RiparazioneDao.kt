package it.officina.riparazioni.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RiparazioneDao {
    @Query("SELECT * FROM riparazioni ORDER BY dataIngresso DESC")
    fun getAll(): Flow<List<Riparazione>>

    @Query("SELECT * FROM riparazioni WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Riparazione?

    @Query("SELECT * FROM riparazioni WHERE cliente = :cliente ORDER BY dataIngresso DESC")
    fun getByCliente(cliente: String): Flow<List<Riparazione>>

    @Query("SELECT COUNT(*) FROM riparazioni WHERE numeroProgressivo LIKE :prefix || '%'")
    suspend fun countByPrefix(prefix: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: Riparazione): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(r: Riparazione): Long

    @Update
    suspend fun update(r: Riparazione)

    @Delete
    suspend fun delete(r: Riparazione)

    @Query("DELETE FROM riparazioni WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM riparazioni")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT cliente FROM riparazioni ORDER BY cliente")
    fun getClientiDistinti(): Flow<List<String>>

    @Query("SELECT * FROM riparazioni WHERE numeroProgressivo = :num LIMIT 1")
    suspend fun getByProgressivo(num: String): Riparazione?
}
