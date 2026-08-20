package br.com.rotamais.atualiza

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class VersaoDisponivel(
    val codigo: Int,
    val nome: String,
    val urlApk: String,
    val notas: String
)

/**
 * Verifica se saiu versao nova olhando as Releases publicas do repositorio.
 * Sem chave, sem servidor proprio, sem custo.
 *
 * O app nao instala sozinho de proposito: pedir REQUEST_INSTALL_PACKAGES
 * reacenderia o alarme do Play Protect, que ja barrou uma versao deste app.
 * Aqui so abrimos o link do APK; o Android cuida do resto.
 */
object Atualizador {

    private const val API =
        "https://api.github.com/repos/guilhermecasag222-desing/ROTAMAIS/releases/latest"

    suspend fun verificar(codigoAtual: Int): VersaoDisponivel? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "ROTAmais")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode !in 200..299) { conn.disconnect(); return@withContext null }
            val corpo = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()

            val o = JSONObject(corpo)
            val tag = o.optString("tag_name")
            val codigo = tag.removePrefix("v").toIntOrNull() ?: return@withContext null
            if (codigo <= codigoAtual) return@withContext null

            val assets = o.optJSONArray("assets") ?: return@withContext null
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("browser_download_url")
                    break
                }
            }
            val apk = url ?: return@withContext null

            VersaoDisponivel(
                codigo = codigo,
                nome = o.optString("name").ifBlank { tag },
                urlApk = apk,
                notas = o.optString("body").take(400)
            )
        } catch (e: Exception) {
            null
        }
    }
}
