package br.com.rotamais.mapa

import kotlin.math.hypot

/**
 * Junta um print com zoom ao print geral.
 *
 * Onde os baloes ficam amontoados, o OCR le um ou dois e perde o resto. A saida
 * e um segundo print com zoom naquela regiao -- mas ele tem escala e
 * enquadramento proprios, entao as posicoes precisam ser trazidas para o
 * sistema de coordenadas do print geral.
 *
 * Ancora: numeros que aparecem nos DOIS prints. Com dois deles ja da para
 * calcular escala e deslocamento, porque um mapa so muda de zoom e de centro --
 * nao gira nem distorce. Sem ancora, o usuario aponta a regiao com um toque.
 */
object Juncao {

    data class Resultado(
        val marcadores: List<Marcador>,
        val adicionados: Int,
        val ancoras: List<Int>,
        val erro: String? = null
    )

    /** Junta usando os numeros presentes nos dois prints. */
    fun automatica(base: List<Marcador>, zoom: List<Marcador>, geracao: Int): Resultado {

        val porNumeroBase = base.associateBy { it.numero }
        val pares = zoom.mapNotNull { z ->
            porNumeroBase[z.numero]?.let { b -> b to z }
        }

        if (pares.size < 2) {
            return Resultado(base, 0, pares.map { it.first.numero },
                "Achei ${pares.size} numero(s) em comum entre os dois prints; preciso de 2. " +
                        "Toque no mapa geral onde fica essa regiao.")
        }

        // Centroides dos pontos em comum, nos dois sistemas de coordenadas.
        val pcx = pares.sumOf { it.first.x.toDouble() } / pares.size
        val pcy = pares.sumOf { it.first.y.toDouble() } / pares.size
        val qcx = pares.sumOf { it.second.x.toDouble() } / pares.size
        val qcy = pares.sumOf { it.second.y.toDouble() } / pares.size

        val espalhamentoBase = pares.sumOf {
            hypot(it.first.x - pcx, it.first.y - pcy)
        }
        val espalhamentoZoom = pares.sumOf {
            hypot(it.second.x - qcx, it.second.y - qcy)
        }
        if (espalhamentoZoom < 1e-6) {
            return Resultado(base, 0, pares.map { it.first.numero },
                "Os numeros em comum estao no mesmo ponto; nao da para calcular a escala.")
        }

        val escala = espalhamentoBase / espalhamentoZoom
        val deslocX = pcx - escala * qcx
        val deslocY = pcy - escala * qcy

        return aplicar(base, zoom, escala, deslocX, deslocY, pares.map { it.first.numero }, geracao)
    }

    /**
     * Junta apontando a regiao na mao. Usada quando faltam numeros em comum.
     * O aglomerado entra com um raio fixo, pequeno: dentro dele as distancias
     * sao curtas e o que importa e a ordem entre os vizinhos, nao a posicao exata.
     */
    fun porToque(
        base: List<Marcador>,
        zoom: List<Marcador>,
        alvoX: Float,
        alvoY: Float,
        larguraBase: Int,
        geracao: Int
    ): Resultado {
        if (zoom.isEmpty()) return Resultado(base, 0, emptyList(), "O print com zoom nao trouxe numero.")

        val qcx = zoom.sumOf { it.x.toDouble() } / zoom.size
        val qcy = zoom.sumOf { it.y.toDouble() } / zoom.size
        val espalhamento = zoom.sumOf { hypot(it.x - qcx, it.y - qcy) } / zoom.size

        val raioDesejado = larguraBase * 0.05
        val escala = if (espalhamento < 1e-6) 1.0 else raioDesejado / espalhamento

        val deslocX = alvoX - escala * qcx
        val deslocY = alvoY - escala * qcy

        return aplicar(base, zoom, escala, deslocX, deslocY, emptyList(), geracao)
    }

    private fun aplicar(
        base: List<Marcador>,
        zoom: List<Marcador>,
        escala: Double,
        deslocX: Double,
        deslocY: Double,
        ancoras: List<Int>,
        geracao: Int
    ): Resultado {
        val jaTem = base.map { it.numero }.toMutableSet()
        val combinado = base.toMutableList()
        var adicionados = 0

        for (z in zoom) {
            // Numero ja conhecido fica com a posicao do print geral, que enxerga
            // o mapa inteiro; o zoom so entra para trazer o que faltava.
            if (!jaTem.add(z.numero)) continue
            combinado += z.copy(
                x = (escala * z.x + deslocX).toFloat(),
                y = (escala * z.y + deslocY).toFloat(),
                geracao = geracao
            )
            adicionados++
        }

        return Resultado(combinado.sortedBy { it.numero }, adicionados, ancoras)
    }
}
