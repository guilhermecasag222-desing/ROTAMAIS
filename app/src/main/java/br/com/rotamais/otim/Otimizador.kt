package br.com.rotamais.otim

import br.com.rotamais.geo.Geo

/**
 * Motor de otimizacao do ROTA+.
 *
 * Pipeline:
 *   1. clusterizar()      -> DBSCAN geografico, descobre as "regioes"
 *   2. selecionarLote()   -> escolhe QUAIS entregas entram no lote (agrupamento primeiro)
 *   3. ordenar()          -> Nearest Neighbor + 2-opt, define a SEQUENCIA
 *   4. preferirComercio() -> desempate final por comercio, com teto de desvio
 *
 * Tudo em Kotlin puro. Para lote de 10 pontos roda em milissegundos.
 */
object Otimizador {

    data class Ponto(
        val id: Long,
        val lat: Double,
        val lon: Double,
        val comercio: Boolean
    )

    /** Indice 0 = origem (posicao atual). Indices 1..n = pontos[0..n-1]. */
    data class Matriz(val km: Array<DoubleArray>, val minutos: Array<DoubleArray>)

    // ------------------------------------------------------------- 0. Matriz

    fun matrizHaversine(
        origemLat: Double,
        origemLon: Double,
        pontos: List<Ponto>,
        fatorRodoviario: Double,
        velocidadeKmh: Double
    ): Matriz {
        val n = pontos.size + 1
        val lats = DoubleArray(n)
        val lons = DoubleArray(n)
        lats[0] = origemLat; lons[0] = origemLon
        pontos.forEachIndexed { i, p -> lats[i + 1] = p.lat; lons[i + 1] = p.lon }

        val km = Array(n) { DoubleArray(n) }
        val min = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val d = Geo.haversineKm(lats[i], lons[i], lats[j], lons[j]) * fatorRodoviario
                km[i][j] = d; km[j][i] = d
                val t = d / velocidadeKmh * 60.0
                min[i][j] = t; min[j][i] = t
            }
        }
        return Matriz(km, min)
    }

    // -------------------------------------------------------- 1. Clusterizar

    /**
     * DBSCAN sobre distancia em linha reta.
     * Pontos isolados (ruido) viram clusters de tamanho 1 -- eles nao somem,
     * so perdem prioridade porque a pontuacao considera a densidade.
     */
    fun clusterizar(pontos: List<Ponto>, raioKm: Double, minPontos: Int = 2): List<List<Int>> {
        val n = pontos.size
        if (n == 0) return emptyList()

        val NAO_VISITADO = -1
        val RUIDO = -2
        val rotulo = IntArray(n) { NAO_VISITADO }
        val clusters = mutableListOf<MutableList<Int>>()

        fun vizinhos(i: Int): List<Int> = (0 until n).filter {
            it != i && Geo.haversineKm(pontos[i].lat, pontos[i].lon, pontos[it].lat, pontos[it].lon) <= raioKm
        }

        var c = 0
        for (i in 0 until n) {
            if (rotulo[i] != NAO_VISITADO) continue
            val viz = vizinhos(i).toMutableList()
            if (viz.size + 1 < minPontos) { rotulo[i] = RUIDO; continue }

            val atual = mutableListOf<Int>()
            clusters.add(atual)
            rotulo[i] = c
            atual.add(i)

            var k = 0
            while (k < viz.size) {
                val q = viz[k]; k++
                if (rotulo[q] == RUIDO) { rotulo[q] = c; atual.add(q); continue }
                if (rotulo[q] != NAO_VISITADO) continue
                rotulo[q] = c
                atual.add(q)
                val vq = vizinhos(q)
                if (vq.size + 1 >= minPontos) {
                    for (x in vq) if (!viz.contains(x)) viz.add(x)
                }
            }
            c++
        }

        // Ruido remanescente vira cluster proprio.
        for (i in 0 until n) if (rotulo[i] == RUIDO) clusters.add(mutableListOf(i))
        return clusters.map { it.toList() }
    }

    // ------------------------------------------------------ 2. Selecionar lote

    /**
     * Escolhe quais entregas entram no proximo lote.
     *
     * Regra que evita "Sombrio -> Ararangua -> Sombrio -> Turvo":
     * apos consumir um cluster, a referencia de distancia passa a ser o CENTROIDE
     * desse cluster, nao a posicao original. Assim a rota avanca em uma direcao.
     *
     * @return indices dentro de [pontos]
     */
    fun selecionarLote(
        origemLat: Double,
        origemLon: Double,
        pontos: List<Ponto>,
        tamanhoLote: Int,
        raioClusterKm: Double
    ): List<Int> {
        if (pontos.size <= tamanhoLote) return pontos.indices.toList()

        val clusters = clusterizar(pontos, raioClusterKm).toMutableList()
        val lote = mutableListOf<Int>()

        var refLat = origemLat
        var refLon = origemLon

        while (lote.size < tamanhoLote && clusters.isNotEmpty()) {
            val melhor = clusters.maxByOrNull { pontuacao(it, pontos, refLat, refLon) } ?: break
            clusters.remove(melhor)

            val ordenado = melhor.sortedBy {
                Geo.haversineKm(refLat, refLon, pontos[it].lat, pontos[it].lon)
            }
            for (idx in ordenado) {
                if (lote.size >= tamanhoLote) break
                lote.add(idx)
            }

            val cent = Geo.centroide(melhor.map { pontos[it].lat to pontos[it].lon })
            refLat = cent.first
            refLon = cent.second
        }
        return lote
    }

    /**
     * Densidade dividida por distancia.
     * 8 entregas a 3 km  -> 8 / 4  = 2,00
     * 1 entrega  a 15 km -> 1 / 16 = 0,06
     * E por isso que uma entrega isolada longe nunca ganha de um bolsao proximo.
     */
    private fun pontuacao(cluster: List<Int>, pontos: List<Ponto>, refLat: Double, refLon: Double): Double {
        val cent = Geo.centroide(cluster.map { pontos[it].lat to pontos[it].lon })
        val dist = Geo.haversineKm(refLat, refLon, cent.first, cent.second)
        return cluster.size / (dist + 1.0)
    }

    // ------------------------------------------------------------ 3. Ordenar

    /**
     * Nearest Neighbor a partir da origem + 2-opt (caminho aberto: nao volta ao inicio).
     *
     * O comercio so desempata dentro de uma faixa apertada:
     * candidatos ate 125% + 150 m da melhor distancia. Um comercio 15 km fora
     * jamais entra nessa faixa, entao nunca causa desvio.
     *
     * @param indices indices dentro de [pontos]
     * @return mesma lista, reordenada
     */
    fun ordenar(
        matriz: Matriz,
        indices: List<Int>,
        pontos: List<Ponto>,
        preferirComercio: Boolean,
        toleranciaComercioKm: Double = 0.4
    ): List<Int> {
        if (indices.size <= 1) return indices

        val restantes = indices.toMutableList()
        val rota = mutableListOf<Int>()
        var atual = 0 // linha 0 da matriz = origem

        while (restantes.isNotEmpty()) {
            val melhorDist = restantes.minOf { matriz.km[atual][it + 1] }
            val faixa = melhorDist * 1.25 + 0.15
            val candidatos = restantes.filter { matriz.km[atual][it + 1] <= faixa }

            val escolhido = if (preferirComercio) {
                candidatos.sortedWith(
                    compareByDescending<Int> { pontos[it].comercio }
                        .thenBy { matriz.km[atual][it + 1] }
                ).first()
            } else {
                candidatos.minByOrNull { matriz.km[atual][it + 1] }!!
            }

            rota.add(escolhido)
            restantes.remove(escolhido)
            atual = escolhido + 1
        }

        var resultado = doisOpt(matriz, rota)
        if (preferirComercio) {
            resultado = preferirComercio(matriz, resultado, pontos, toleranciaComercioKm)
        }
        return resultado
    }

    /** Custo total em km do caminho origem -> rota (aberto, sem retorno). */
    fun custoKm(matriz: Matriz, rota: List<Int>): Double {
        var soma = 0.0
        var atual = 0
        for (r in rota) { soma += matriz.km[atual][r + 1]; atual = r + 1 }
        return soma
    }

    fun custoMinutos(matriz: Matriz, rota: List<Int>): Double {
        var soma = 0.0
        var atual = 0
        for (r in rota) { soma += matriz.minutos[atual][r + 1]; atual = r + 1 }
        return soma
    }

    /** 2-opt classico adaptado para caminho aberto. Mata zigue-zague. */
    fun doisOpt(matriz: Matriz, rotaInicial: List<Int>): List<Int> {
        var rota = rotaInicial.toMutableList()
        var custoAtual = custoKm(matriz, rota)
        var melhorou = true
        var voltas = 0

        while (melhorou && voltas < 80) {
            melhorou = false
            voltas++
            for (i in 0 until rota.size - 1) {
                for (j in i + 1 until rota.size) {
                    val nova = rota.toMutableList()
                    var a = i; var b = j
                    while (a < b) { val t = nova[a]; nova[a] = nova[b]; nova[b] = t; a++; b-- }
                    val novoCusto = custoKm(matriz, nova)
                    if (novoCusto < custoAtual - 1e-9) {
                        rota = nova
                        custoAtual = novoCusto
                        melhorou = true
                    }
                }
            }
        }
        return rota
    }

    // -------------------------------------------------- 4. Desempate comercio

    /**
     * Puxa comercios para frente apenas quando o custo total sobe menos que
     * [toleranciaKm]. Teto explicito -> comercio nunca vira desvio.
     */
    fun preferirComercio(
        matriz: Matriz,
        rota: List<Int>,
        pontos: List<Ponto>,
        toleranciaKm: Double
    ): List<Int> {
        val r = rota.toMutableList()
        var mudou = true
        var voltas = 0
        while (mudou && voltas < 20) {
            mudou = false
            voltas++
            for (i in 0 until r.size - 1) {
                if (pontos[r[i]].comercio) continue
                if (!pontos[r[i + 1]].comercio) continue
                val nova = r.toMutableList()
                val t = nova[i]; nova[i] = nova[i + 1]; nova[i + 1] = t
                if (custoKm(matriz, nova) <= custoKm(matriz, r) + toleranciaKm) {
                    r.clear(); r.addAll(nova)
                    mudou = true
                }
            }
        }
        return r
    }
}
