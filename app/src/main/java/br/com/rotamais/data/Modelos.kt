package br.com.rotamais.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class TipoLocal { COMERCIO, RESIDENCIAL, DESCONHECIDO }
enum class StatusEntrega { PENDENTE, CONCLUIDA, PULADA }

@Entity(tableName = "entregas")
data class Entrega(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Numero/codigo que aparece no Envio Logistics. */
    val codigo: String,
    val enderecoBruto: String,
    val enderecoNormalizado: String? = null,
    val bairro: String? = null,
    val cidade: String? = null,
    val cep: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val tipo: TipoLocal = TipoLocal.DESCONHECIDO,
    val status: StatusEntrega = StatusEntrega.PENDENTE,
    /** Posicao dentro do lote otimizado atual (1..N). Null = fora do lote. */
    val ordemNoLote: Int? = null,
    val capturadaEm: Long = System.currentTimeMillis(),
    val concluidaEm: Long? = null,
    /** Km reais percorridos desde a parada anterior (gravado ao concluir). */
    val kmPercorrido: Double = 0.0,
    /** Minutos gastos desde a parada anterior (gravado ao concluir). */
    val minutosGastos: Int = 0,
    val erroGeo: String? = null
)

val Entrega.temCoordenada: Boolean get() = lat != null && lon != null

@Entity(tableName = "rotas")
data class RotaHistorico(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inicioEm: Long,
    val fimEm: Long,
    val regiao: String,
    val qtdEntregas: Int,
    val km: Double,
    val minutos: Int,
    val litros: Double,
    val custo: Double
)

class Conversores {
    @TypeConverter fun tipoParaTexto(v: TipoLocal): String = v.name
    @TypeConverter fun textoParaTipo(v: String): TipoLocal =
        runCatching { TipoLocal.valueOf(v) }.getOrDefault(TipoLocal.DESCONHECIDO)

    @TypeConverter fun statusParaTexto(v: StatusEntrega): String = v.name
    @TypeConverter fun textoParaStatus(v: String): StatusEntrega =
        runCatching { StatusEntrega.valueOf(v) }.getOrDefault(StatusEntrega.PENDENTE)
}
