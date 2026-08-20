package br.com.rotamais.mapa

import br.com.rotamais.otim.Otimizador

data class ParadaSequencia(
    val ordem: Int,
    val marcador: Marcador,
    val kmDoAnterior: Double
)

data class ResultadoSequencia(
    val paradas: List<ParadaSequencia>,
    val kmTotal: Double,
    val minutosTotal: Int,
    val kmNaOrdemDoApp: Double,
    val minutosNaOrdemDoApp: Int
)

/**
 * Transforma os baloes do print numa ordem de execucao.
 *
 * Os pixels viram coordenadas geograficas sinteticas (equador, onde 1 grau de
 * longitude mede o mesmo que 1 grau de latitude). Assim o motor de otimizacao
 * ja existente -- clusterizacao, vizinho mais proximo, 2-opt -- roda sem
 * nenhuma alteracao, e a geometria da imagem e preservada.
 */
object SequenciaMapa {

    private const val KM_POR_GRAU = 111.32

    fun calcular(
        marcadores: List<Marcador>,
        origemX: Float,
        origemY: Float,
        larguraImagemPx: Int,
        larguraMapaKm: Double,
        velocidadeKmh: Double,
        tempoParadaMin: Double,
        raioClusterKm: Double,
        tamanhoLote: Int
    ): ResultadoSequencia? {

        val ativos = marcadores.filter { it.ativo }
        if (ativos.isEmpty() || larguraImagemPx <= 0) return null

        val kmPorPixel = larguraMapaKm / larguraImagemPx

        fun lat(y: Float) = -(y * kmPorPixel) / KM_POR_GRAU
        fun lon(x: Float) = (x * kmPorPixel) / KM_POR_GRAU

        val pontos = ativos.mapIndexed { i, m ->
            Otimizador.Ponto(
                id = i.toLong(),
                lat = lat(m.y),
                lon = lon(m.x),
                comercio = false
            )
        }

        val oLat = lat(origemY)
        val oLon = lon(origemX)

        // Fator 1.0: a distancia entre pixels ja e "em linha reta no mapa".
        // Aplicar fator rodoviario aqui distorceria sem ganho, porque ele afeta
        // todos os pares igualmente e nao muda a ordem escolhida.
        val matriz = Otimizador.matrizHaversine(oLat, oLon, pontos, 1.0, velocidadeKmh)

        val quantos = if (tamanhoLote <= 0) pontos.size else minOf(tamanhoLote, pontos.size)
        val selecionados = Otimizador.selecionarLote(
            oLat, oLon, pontos, quantos, raioClusterKm
        )
        val ordenados = Otimizador.ordenar(matriz, selecionados, pontos, preferirComercio = false)

        val paradas = mutableListOf<ParadaSequencia>()
        var anterior = 0
        var km = 0.0
        ordenados.forEachIndexed { pos, idx ->
            val trecho = matriz.km[anterior][idx + 1]
            km += trecho
            anterior = idx + 1
            paradas += ParadaSequencia(pos + 1, ativos[idx], trecho)
        }

        val minutos = (Otimizador.custoMinutos(matriz, ordenados) +
                ordenados.size * tempoParadaMin).toInt()

        // Comparacao com a ordem que o app de entregas ja sugere (numero crescente).
        val ordemApp = selecionados.sortedBy { ativos[it].numero }
        val kmApp = Otimizador.custoKm(matriz, ordemApp)
        val minApp = (Otimizador.custoMinutos(matriz, ordemApp) +
                ordemApp.size * tempoParadaMin).toInt()

        return ResultadoSequencia(paradas, km, minutos, kmApp, minApp)
    }
}
