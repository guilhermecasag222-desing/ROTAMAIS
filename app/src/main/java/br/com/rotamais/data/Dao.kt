package br.com.rotamais.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntregaDao {

    @Query("SELECT * FROM entregas ORDER BY capturadaEm ASC, id ASC")
    fun observarTodas(): Flow<List<Entrega>>

    @Query("SELECT * FROM entregas ORDER BY capturadaEm ASC, id ASC")
    suspend fun todas(): List<Entrega>

    @Query("SELECT * FROM entregas WHERE status = :st ORDER BY capturadaEm ASC, id ASC")
    suspend fun porStatus(st: StatusEntrega): List<Entrega>

    @Query("SELECT * FROM entregas WHERE lat IS NULL AND status = :st")
    suspend fun semCoordenada(st: StatusEntrega): List<Entrega>

    @Insert
    suspend fun inserir(e: Entrega): Long

    @Insert
    suspend fun inserirVarias(lista: List<Entrega>)

    @Update
    suspend fun atualizar(e: Entrega)

    @Delete
    suspend fun apagar(e: Entrega)

    @Query("DELETE FROM entregas")
    suspend fun apagarTudo()

    @Query("DELETE FROM entregas WHERE status = :st")
    suspend fun apagarPorStatus(st: StatusEntrega)

    @Query("UPDATE entregas SET ordemNoLote = NULL")
    suspend fun limparLote()

    @Query("UPDATE entregas SET ordemNoLote = :ordem WHERE id = :id")
    suspend fun definirOrdem(id: Long, ordem: Int)

    @Query("SELECT * FROM entregas WHERE status = :st AND concluidaEm >= :desde ORDER BY concluidaEm ASC")
    suspend fun concluidasDesde(desde: Long, st: StatusEntrega): List<Entrega>
}

@Dao
interface RotaDao {

    @Query("SELECT * FROM rotas ORDER BY fimEm DESC")
    fun observarTodas(): Flow<List<RotaHistorico>>

    @Insert
    suspend fun inserir(r: RotaHistorico)

    @Query("DELETE FROM rotas")
    suspend fun apagarTudo()
}
