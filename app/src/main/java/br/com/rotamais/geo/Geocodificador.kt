package br.com.rotamais.geo

import android.content.Context
import android.location.Geocoder
import android.os.Build
import br.com.rotamais.data.TipoLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class ResultadoGeo(
    val lat: Double,
    val lon: Double,
    val enderecoNormalizado: String?,
    val bairro: String?,
    val cidade: String?,
    val cep: String?,
    val tipo: TipoLocal,
    val fonte: String
)

/**
 * Estrategia:
 *  1) Geocoder nativo do Android (instantaneo, sem chave, funciona offline em parte).
 *  2) Nominatim / OpenStreetMap (sem chave, 1 req/s, ainda classifica comercio pela categoria OSM).
 *
 * Nenhuma das duas cobra nada. Se as duas falharem, devolve null e o app
 * marca a entrega em vermelho para correcao manual.
 */
object Geocodificador {

    private const val USER_AGENT = "ROTAmais/0.1 (uso pessoal, entregas)"
    private const val INTERVALO_NOMINATIM_MS = 1100L

    private var ultimaChamadaNominatim = 0L

    suspend fun geocodificar(ctx: Context, enderecoBruto: String, regiaoPadrao: String): ResultadoGeo? {
        val consulta = montarConsulta(enderecoBruto, regiaoPadrao)
        viaAndroid(ctx, consulta, enderecoBruto)?.let { return it }
        return viaNominatim(consulta, enderecoBruto)
    }

    private fun montarConsulta(endereco: String, regiaoPadrao: String): String {
        val e = endereco.trim()
        val jaTemUf = Regex("""(?i)\b(SC|RS|PR|SP|Santa\s+Catarina|Brasil)\b""").containsMatchIn(e)
        return if (jaTemUf || regiaoPadrao.isBlank()) e else "$e, $regiaoPadrao"
    }

    // ---------------------------------------------------------------- Android

    @Suppress("DEPRECATION")
    private suspend fun viaAndroid(ctx: Context, consulta: String, bruto: String): ResultadoGeo? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            try {
                val g = Geocoder(ctx, Locale("pt", "BR"))
                val lista = g.getFromLocationName(consulta, 1)
                val a = lista?.firstOrNull() ?: return@withContext null
                ResultadoGeo(
                    lat = a.latitude,
                    lon = a.longitude,
                    enderecoNormalizado = a.getAddressLine(0),
                    bairro = a.subLocality,
                    cidade = a.locality ?: a.subAdminArea,
                    cep = a.postalCode,
                    tipo = classificarPorTexto(listOfNotNull(bruto, a.featureName).joinToString(" ")),
                    fonte = "android"
                )
            } catch (e: Exception) {
                null
            }
        }

    // ------------------------------------------------------------- Nominatim

    private suspend fun viaNominatim(consulta: String, bruto: String): ResultadoGeo? =
        withContext(Dispatchers.IO) {
            // Politica de uso do Nominatim: no maximo 1 requisicao por segundo.
            val espera = INTERVALO_NOMINATIM_MS - (System.currentTimeMillis() - ultimaChamadaNominatim)
            if (espera > 0) delay(espera)
            ultimaChamadaNominatim = System.currentTimeMillis()

            try {
                val url = URL(
                    "https://nominatim.openstreetmap.org/search" +
                            "?format=jsonv2&limit=1&countrycodes=br&addressdetails=1" +
                            "&q=" + URLEncoder.encode(consulta, "UTF-8")
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept-Language", "pt-BR")
                }
                if (conn.responseCode !in 200..299) {
                    conn.disconnect()
                    return@withContext null
                }
                val corpo = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                conn.disconnect()

                val arr = JSONArray(corpo)
                if (arr.length() == 0) return@withContext null
                val o = arr.getJSONObject(0)
                val end = o.optJSONObject("address")

                val categoria = o.optString("category", "")
                val tipoOsm = o.optString("type", "")

                ResultadoGeo(
                    lat = o.getString("lat").toDouble(),
                    lon = o.getString("lon").toDouble(),
                    enderecoNormalizado = o.optString("display_name").ifBlank { null },
                    bairro = end?.optString("suburb")?.ifBlank { null }
                        ?: end?.optString("neighbourhood")?.ifBlank { null },
                    cidade = end?.optString("city")?.ifBlank { null }
                        ?: end?.optString("town")?.ifBlank { null }
                        ?: end?.optString("village")?.ifBlank { null }
                        ?: end?.optString("municipality")?.ifBlank { null },
                    cep = end?.optString("postcode")?.ifBlank { null },
                    tipo = classificar(bruto, categoria, tipoOsm),
                    fonte = "nominatim"
                )
            } catch (e: Exception) {
                null
            }
        }

    // ---------------------------------------------------- Comercio x residencia

    private val PALAVRAS_COMERCIO = listOf(
        "mercado", "supermercado", "mercearia", "minimercado", "loja", "lojas", "comercial",
        "comercio", "ltda", "eireli", "mei ", "cnpj", "auto pecas", "autopecas", "auto centro",
        "oficina", "restaurante", "lanchonete", "padaria", "farmacia", "drogaria", "posto",
        "borracharia", "barbearia", "salao", "estetica", "academia", "papelaria", "petshop",
        "pet shop", "distribuidora", "atacado", "atacadao", "materiais de construcao",
        "deposito", "pizzaria", "sorveteria", "clinica", "consultorio", "escritorio",
        "imobiliaria", "agropecuaria", "serralheria", "marcenaria", "grafica", "otica",
        "joalheria", "boutique", "magazine", "industria", "transportadora", "supermercados",
        "conveniencia", "bazar", "confeccoes", "modas", "calcados", "informatica", "celular",
        "assistencia tecnica", "hotel", "pousada", "bar ", "cafe ", "s/a", "s.a."
    )

    private val CATEGORIAS_COMERCIO = setOf("shop", "amenity", "office", "craft", "tourism", "healthcare")

    private val TIPOS_RESIDENCIAIS = setOf("house", "residential", "apartments", "yes", "detached")

    fun classificar(textoBruto: String, categoriaOsm: String = "", tipoOsm: String = ""): TipoLocal {
        val porTexto = classificarPorTexto(textoBruto)
        if (porTexto == TipoLocal.COMERCIO) return TipoLocal.COMERCIO
        if (categoriaOsm.lowercase() in CATEGORIAS_COMERCIO) return TipoLocal.COMERCIO
        if (tipoOsm.lowercase() in TIPOS_RESIDENCIAIS) return TipoLocal.RESIDENCIAL
        return porTexto
    }

    private fun classificarPorTexto(texto: String): TipoLocal {
        val t = semAcento(texto.lowercase())
        return if (PALAVRAS_COMERCIO.any { t.contains(it) }) TipoLocal.COMERCIO
        else TipoLocal.DESCONHECIDO
    }

    private fun semAcento(s: String): String {
        val de = "áàâãäéèêëíìîïóòôõöúùûüçÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ"
        val para = "aaaaaeeeeiiiiooooouuuucAAAAAEEEEIIIIOOOOOUUUUC"
        val sb = StringBuilder(s.length)
        for (c in s) {
            val i = de.indexOf(c)
            sb.append(if (i >= 0) para[i] else c)
        }
        return sb.toString()
    }

    /** Detecta se o Geocoder nativo esta disponivel (util para avisar o usuario). */
    fun geocoderNativoDisponivel(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Geocoder.isPresent()
}
