package it.officina.riparazioni.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClienteDao {

    @Query("SELECT * FROM clienti ORDER BY cognome, nome")
    fun getAll(): Flow<List<Cliente>>

    @Query("SELECT * FROM clienti WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Cliente?

    @Query("""SELECT * FROM clienti WHERE 
              nome LIKE '%' || :q || '%' OR 
              cognome LIKE '%' || :q || '%' OR 
              telefono LIKE '%' || :q || '%'
              ORDER BY cognome, nome""")
    fun search(q: String): Flow<List<Cliente>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: Cliente): Long

    @Update
    suspend fun update(c: Cliente)

    @Delete
    suspend fun delete(c: Cliente)
}
