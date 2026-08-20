package br.com.rotamais.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.rotamais.data.Prefs
import br.com.rotamais.mapa.Diagnostico
import br.com.rotamais.mapa.Juncao
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
    val uri: Uri? = null,
    val marcadores: List<Marcador> = emptyList(),
    val diagnostico: Diagnostico = Diagnostico(),
    val semFiltros: Boolean = false,
    val origemX: Float? = null,
    val origemY: Float? = null,
    val resultado: ResultadoSequencia? = null,
    /** Quantas paradas a tela do app de entregas diz que existem. */
    val paradasReais: Int? = null,
    /** Quantos pacotes estao no carro (uma parada pode ter varios). */
    val pacotes: Int? = null,
    /** Leitura de um print com zoom esperando o usuario apontar a regiao. */
    val zoomPendente: List<Marcador>? = null,
    val mensagem: String? = null
) {
    /** Quantas paradas o OCR deixou passar, se o total real foi informado. */
    val faltando: Int?
        get() = paradasReais?.let { it - marcadores.count { m -> m.ativo } }
}

class MapaViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    private val _estado = MutableStateFlow(MapaState())
    val estado: StateFlow<MapaState> = _estado

    fun carregarPrint(uri: Uri, semFiltros: Boolean = false) {
        viewModelScope.launch {
            _estado.value = MapaState(carregando = true, uri = uri, semFiltros = semFiltros)
            val leitura = LeitorMapa.ler(getApplication(), uri, semFiltros)
            if (leitura == null) {
                _estado.value = MapaState(mensagem = "Nao consegui abrir a imagem.")
                return@launch
            }
            _estado.value = MapaState(
                bitmap = leitura.bitmap,
                uri = uri,
                semFiltros = semFiltros,
                marcadores = leitura.marcadores,
                diagnostico = leitura.diagnostico,
                // Comeca ja com uma sequencia pronta, do canto de baixo da imagem.
                // Um toque muda de onde a rota parte.
                origemX = leitura.bitmap.width / 2f,
                origemY = leitura.bitmap.height * 0.92f,
                mensagem = leitura.aviso
            )
            calcular()
        }
    }

    /** Rele a mesma imagem sem nenhum filtro, quando a leitura normal traz pouca coisa. */
    fun relerSemFiltros() {
        val uri = _estado.value.uri ?: return
        carregarPrint(uri, semFiltros = true)
    }

    /**
     * Um toque no mapa. Enquanto houver print com zoom esperando, o toque aponta
     * a regiao dele; fora disso, marca de onde a rota comeca.
     */
    fun definirOrigem(x: Float, y: Float) {
        val atual = _estado.value
        val pendente = atual.zoomPendente
        if (pendente != null) {
            val largura = atual.bitmap?.width ?: return
            val geracao = atual.marcadores.maxOfOrNull { it.geracao }?.plus(1) ?: 1
            val r = Juncao.porToque(atual.marcadores, pendente, x, y, largura, geracao)
            _estado.value = atual.copy(
                marcadores = r.marcadores,
                zoomPendente = null,
                mensagem = r.erro ?: "Juntei ${r.adicionados} parada(s) nessa regiao."
            )
            calcular()
            return
        }
        _estado.value = atual.copy(origemX = x, origemY = y, resultado = null)
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
            // 0 = todas as paradas do print. O pedido e a sequencia inteira,
            // nao um lote de dez.
            tamanhoLote = 0
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

    // ------------------------------------------------- Print com zoom

    /**
     * Le um segundo print, com zoom na regiao amontoada, e junta ao geral.
     * Se nao houver numeros em comum suficientes, guarda a leitura e pede que o
     * usuario aponte a regiao com um toque.
     */
    fun adicionarPrintZoom(uri: Uri) {
        viewModelScope.launch {
            val atual = _estado.value
            val base = atual.bitmap
            if (base == null) {
                _estado.value = atual.copy(mensagem = "Carregue primeiro o print geral.")
                return@launch
            }
            _estado.value = atual.copy(carregando = true)

            val leitura = LeitorMapa.ler(getApplication(), uri, atual.semFiltros)
            if (leitura == null || leitura.marcadores.isEmpty()) {
                _estado.value = atual.copy(
                    carregando = false,
                    mensagem = "Nao consegui ler numero nenhum nesse print com zoom."
                )
                return@launch
            }

            val geracao = atual.marcadores.maxOfOrNull { it.geracao }?.plus(1) ?: 1
            val r = Juncao.automatica(atual.marcadores, leitura.marcadores, geracao)

            if (r.erro != null) {
                // Sem ancora: espera o toque que aponta a regiao.
                _estado.value = atual.copy(
                    carregando = false,
                    zoomPendente = leitura.marcadores,
                    mensagem = r.erro
                )
                return@launch
            }

            _estado.value = atual.copy(
                carregando = false,
                marcadores = r.marcadores,
                zoomPendente = null,
                mensagem = "Juntei ${r.adicionados} parada(s) nova(s), usando " +
                        "${r.ancoras.joinToString(" e ")} como referencia."
            )
            calcular()
        }
    }

    fun cancelarZoomPendente() {
        _estado.value = _estado.value.copy(zoomPendente = null, mensagem = null)
    }

    fun definirParadasReais(n: Int?) { _estado.value = _estado.value.copy(paradasReais = n) }

    fun definirPacotes(n: Int?) { _estado.value = _estado.value.copy(pacotes = n) }

    fun limpar() { _estado.value = MapaState() }

    fun definirLarguraMapaKm(km: Double) {
        prefs.larguraMapaKm = km
        calcular()
    }
}
