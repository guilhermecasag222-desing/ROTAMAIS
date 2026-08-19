package br.com.rotamais.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Matriz de distancia/tempo por estrada usando o servidor publico do OSRM.
 * Sem chave, sem custo. Nao tem SLA -> so e usado quando o usuario liga o toggle,
 * e qualquer falha cai de volta no calculo Haversine.
 *
 * Limite pratico do servidor demo: ~100 coordenadas por chamada de /table.
 */
object Osrm {

    private const val BASE = "https://router.project-osrm.org/table/v1/driving/"

    data class MatrizOsrm(val km: Array<DoubleArray>, val minutos: Array<DoubleArray>)

    /**
     * @param coords lista (lat, lon). O indice 0 deve ser a origem.
     * @return null se offline, se estourar limite ou se o servidor recusar.
     */
    suspend fun matriz(coords: List<Pair<Double, Double>>): MatrizOsrm? = withContext(Dispatchers.IO) {
        if (coords.size < 2 || coords.size > 90) return@withContext null
        try {
            val pares = coords.joinToString(";") { "${it.second},${it.first}" }
            val url = URL("$BASE$pares?annotations=distance,duration")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "ROTAmais/0.1 (uso pessoal)")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@withContext null
            }
            val corpo = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()

            val o = JSONObject(corpo)
            if (o.optString("code") != "Ok") return@withContext null

            val dist = o.getJSONArray("distances")
            val dur = o.getJSONArray("durations")
            val n = coords.size
            val km = Array(n) { DoubleArray(n) }
            val min = Array(n) { DoubleArray(n) }
            for (i in 0 until n) {
                val linhaD = dist.getJSONArray(i)
                val linhaT = dur.getJSONArray(i)
                for (j in 0 until n) {
                    km[i][j] = if (linhaD.isNull(j)) Double.MAX_VALUE / 4 else linhaD.getDouble(j) / 1000.0
                    min[i][j] = if (linhaT.isNull(j)) Double.MAX_VALUE / 4 else linhaT.getDouble(j) / 60.0
                }
            }
            MatrizOsrm(km, min)
        } catch (e: Exception) {
            null
        }
    }
}
