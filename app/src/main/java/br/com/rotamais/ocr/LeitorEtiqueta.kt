package br.com.rotamais.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import br.com.rotamais.data.TipoLocal
import br.com.rotamais.geo.Geocodificador
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Resultado de UMA etiqueta encontrada numa foto.
 * [textoBruto] e sempre preenchido: quando o parser erra, e ele que permite corrigir
 * na tela de revisao (e me mandar para eu calibrar o parser).
 */
data class EnderecoLido(
    val endereco: String,
    val cep: String?,
    val cidade: String?,
    val destinatario: String?,
    val tipo: TipoLocal,
    val confianca: Confianca,
    val textoBruto: String,
    val origem: String
)

enum class Confianca { ALTA, MEDIA, BAIXA }

object LeitorEtiqueta {

    private val reconhecedor by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private const val LARGURA_MAX = 2200

    /** Texto cru da imagem, sem interpretacao. */
    suspend fun lerTexto(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = carregarReduzido(ctx, uri) ?: return@withContext null
        try {
            val entrada = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine<String?> { cont ->
                reconhecedor.process(entrada)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Le a imagem e devolve todas as etiquetas encontradas nela. */
    suspend fun ler(ctx: Context, uri: Uri, rotulo: String): List<EnderecoLido> {
        val texto = lerTexto(ctx, uri) ?: return emptyList()
        val achados = interpretar(texto, rotulo)
        return achados.ifEmpty {
            // Nada reconhecido: devolve o texto cru para revisao manual em vez de descartar.
            listOf(
                EnderecoLido(
                    endereco = texto.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                    cep = null, cidade = null, destinatario = null,
                    tipo = TipoLocal.DESCONHECIDO,
                    confianca = Confianca.BAIXA,
                    textoBruto = texto,
                    origem = rotulo
                )
            )
        }
    }

    // ------------------------------------------------------------- Bitmap

    /**
     * Reduz a foto antes do OCR. Foto de 12 MP em celular modesto derruba o app;
     * 2200 px de largura ainda le etiqueta com folga.
     */
    private fun carregarReduzido(ctx: Context, uri: Uri): Bitmap? {
        return try {
            val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opcoes)
            }
            var amostra = 1
            while (opcoes.outWidth / amostra > LARGURA_MAX) amostra *= 2

            val finais = BitmapFactory.Options().apply {
                inSampleSize = amostra
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, finais)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------- Parser

    private val RE_CEP = Regex("""\b(\d{5})[-.\s]?(\d{3})\b""")

    private val RE_LOGRADOURO = Regex(
        """(?i)^\s*(r\.?|rua|av\.?|avenida|trav\.?|travessa|rod\.?|rodovia|estr\.?|estrada|""" +
                """servid[aã]o|alameda|al\.?|pra[cç]a|linha|beco|via|marginal|sc[-\s]?\d+|br[-\s]?\d+)\b"""
    )

    private val RE_UF = Regex("""(?i)\b(SC|RS|PR|SP|RJ|MG|BA|PE|CE|GO|MT|MS|DF|ES|PA|AM)\b""")

    /** Linhas que a etiqueta traz mas nao interessam para o endereco de destino. */
    private val RUIDO = listOf(
        "remetente", "destinat", "mercado livre", "mercadolivre", "meli", "envios",
        "nota fiscal", "danfe", "chave de acesso", "pedido", "volume", "peso",
        "codigo de barras", "rastreamento", "cpf", "cnpj do", "declaracao"
    )

    /**
     * Varre o texto procurando blocos de endereco. Cada logradouro encontrado abre um
     * bloco, que fecha no CEP seguinte ou no proximo logradouro -- assim uma foto com
     * varias etiquetas empilhadas vira varias entregas.
     */
    fun interpretar(texto: String, rotulo: String): List<EnderecoLido> {
        val linhas = texto.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (linhas.isEmpty()) return emptyList()

        val resultados = mutableListOf<EnderecoLido>()
        var i = 0
        while (i < linhas.size) {
            if (!RE_LOGRADOURO.containsMatchIn(linhas[i])) { i++; continue }

            val bloco = mutableListOf(linhas[i])
            var cep: String? = RE_CEP.find(linhas[i])?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }
            var j = i + 1
            while (j < linhas.size && bloco.size < 5) {
                val l = linhas[j]
                if (RE_LOGRADOURO.containsMatchIn(l)) break
                if (ehRuido(l)) { j++; continue }
                bloco.add(l)
                val achado = RE_CEP.find(l)
                if (achado != null) {
                    cep = "${achado.groupValues[1]}-${achado.groupValues[2]}"
                    j++
                    break
                }
                j++
            }

            // Destinatario: linha imediatamente acima do logradouro, se nao for ruido.
            val destinatario = linhas.getOrNull(i - 1)
                ?.takeIf { !ehRuido(it) && !RE_CEP.containsMatchIn(it) && it.length in 3..60 }

            val enderecoCompleto = bloco.joinToString(", ")
            val cidade = extrairCidade(bloco, cep)

            val confianca = when {
                cep != null && cidade != null -> Confianca.ALTA
                cep != null || cidade != null -> Confianca.MEDIA
                else -> Confianca.BAIXA
            }

            resultados += EnderecoLido(
                endereco = enderecoCompleto,
                cep = cep,
                cidade = cidade,
                destinatario = destinatario,
                tipo = Geocodificador.classificar(
                    listOfNotNull(destinatario, enderecoCompleto).joinToString(" ")
                ),
                confianca = confianca,
                textoBruto = texto,
                origem = rotulo
            )
            i = maxOf(j, i + 1)
        }
        return resultados
    }

    private fun ehRuido(linha: String): Boolean {
        val l = linha.lowercase()
        if (l.length < 3) return true
        if (RUIDO.any { l.contains(it) }) return true
        // Linha so de digitos longos: codigo de rastreio, nao endereco.
        if (Regex("""^[\d\s.\-]{10,}$""").matches(linha)) return true
        return false
    }

    /** Cidade: pedaco antes da UF, na linha que traz o CEP ou a UF. */
    private fun extrairCidade(bloco: List<String>, cep: String?): String? {
        for (l in bloco.reversed()) {
            val uf = RE_UF.find(l) ?: continue
            val antes = l.substring(0, uf.range.first)
                .replace(RE_CEP, "")
                .trim().trim(',', '-', '/', '.')
                .substringAfterLast(',')
                .trim()
            if (antes.length in 3..40) return "$antes, ${uf.value.uppercase()}"
        }
        return null
    }
}
