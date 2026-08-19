package br.com.rotamais.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {

    private const val RAIO_TERRA_KM = 6371.0088

    /** Distancia em linha reta, em km. */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * RAIO_TERRA_KM * asin(sqrt(a).coerceIn(0.0, 1.0))
    }

    fun centroide(pontos: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (pontos.isEmpty()) return 0.0 to 0.0
        return pontos.sumOf { it.first } / pontos.size to pontos.sumOf { it.second } / pontos.size
    }
}
