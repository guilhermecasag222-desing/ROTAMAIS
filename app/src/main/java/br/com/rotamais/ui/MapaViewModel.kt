package br.com.rotamais.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.rotamais.data.Prefs
import br.com.rotamais.mapa.LeitorMapa
import br.com.rotamais.mapa.Marcador
import br.com.rotamais.mapa.ResultadoSequencia
import br.com.rotamais.mapa.SequenciaMapa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MapaState(
    val carregando: Boolean = false,
    val bitmap: Bitmap? = null,
    val marcadores: List<Marcador> = emptyList(),
    val descartados: Int = 0,
    val origemX: Float? = null,
    val origemY: Float? = null,
    val resultado: ResultadoSequencia? = null,
    val mensagem: String? = null
)

class MapaViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    private val _estado = MutableStateFlow(MapaState())
    val estado: StateFlow<MapaState> = _estado

    fun carregarPrint(uri: Uri) {
        viewModelScope.launch {
            _estado.value = MapaState(carregando = true)
            val leitura = LeitorMapa.ler(getApplication(), uri)
            if (leitura == null) {
                _estado.value = MapaState(mensagem = "Nao consegui abrir a imagem.")
                return@launch
            }
            _estado.value = MapaState(
                bitmap = leitura.bitmap,
                marcadores = leitura.marcadores,
                descartados = leitura.descartados,
                mensagem = leitura.aviso
                    ?: "${leitura.marcadores.size} parada(s) encontrada(s). " +
                    "Toque no mapa onde voce esta agora."
            )
        }
    }

    /** Um toque marca de onde a rota comeca; o proximo recalcula dali. */
    fun definirOrigem(x: Float, y: Float) {
        _estado.value = _estado.value.copy(origemX = x, origemY = y, resultado = null)
        calcular()
    }

    /** Toque em cima de um numero liga/desliga aquela parada. */
    fun alternarMarcador(numero: Int) {
        _estado.value = _estado.value.copy(
            marcadores = _estado.value.marcadores.map {
                if (it.numero == numero) it.copy(ativo = !it.ativo) else it
            }
        )
        if (_estado.value.origemX != null) calcular()
    }

    fun calcular() {
        val e = _estado.value
        val bmp = e.bitmap ?: return
        val ox = e.origemX ?: return
        val oy = e.origemY ?: return

        val r = SequenciaMapa.calcular(
            marcadores = e.marcadores,
            origemX = ox,
            origemY = oy,
            larguraImagemPx = bmp.width,
            larguraMapaKm = prefs.larguraMapaKm,
            velocidadeKmh = prefs.velocidadeKmh,
            tempoParadaMin = prefs.tempoParadaMin,
            raioClusterKm = prefs.raioClusterKm,
            tamanhoLote = prefs.tamanhoLote
        )
        _estado.value = e.copy(
            resultado = r,
            mensagem = if (r == null) "Nenhuma parada ativa para calcular." else null
        )
    }

    /** Conclui a parada e recalcula a partir dela: a origem passa a ser onde voce esta. */
    fun concluirPrimeira() {
        val e = _estado.value
        val primeira = e.resultado?.paradas?.firstOrNull() ?: return
        _estado.value = e.copy(
            marcadores = e.marcadores.map {
                if (it.numero == primeira.marcador.numero) it.copy(ativo = false) else it
            },
            origemX = primeira.marcador.x,
            origemY = primeira.marcador.y
        )
        calcular()
    }

    fun limpar() { _estado.value = MapaState() }

    fun definirLarguraMapaKm(km: Double) {
        prefs.larguraMapaKm = km
        calcular()
    }
}
