package br.com.rotamais.mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs

/** Um balao numerado do mapa do app de entregas. */
data class Marcador(
    val numero: Int,
    /** Centro do balao, em pixels da imagem. */
    val x: Float,
    val y: Float,
    val corFundo: Int,
    val ativo: Boolean = true
)

data class LeituraMapa(
    val bitmap: Bitmap,
    val marcadores: List<Marcador>,
    val diagnostico: Diagnostico,
    val aviso: String? = null
)

/** Quantos numeros cairam em cada filtro. Serve para achar o filtro apertado demais. */
data class Diagnostico(
    val lidos: Int = 0,
    val foraDaArea: Int = 0,
    val tamanhoErrado: Int = 0,
    val semCor: Int = 0,
    val repetidos: Int = 0
) {
    val descartados: Int get() = foraDaArea + tamanhoErrado + semCor + repetidos

    fun resumo(): String = "lidos $lidos - area $foraDaArea - tamanho $tamanhoErrado - " +
            "cor $semCor - repetidos $repetidos"
}

/**
 * Le um print da tela do app de entregas e extrai os baloes numerados com a
 * posicao de cada um na imagem.
 *
 * A sacada: para decidir a ORDEM das paradas nao e preciso saber latitude e
 * longitude. Distancia entre pixels de um mapa e proporcional a distancia real
 * na escala de uma cidade, entao a sequencia sai certa so com a geometria da
 * imagem. Coordenada real so faria falta para navegar, nao para ordenar.
 */
object LeitorMapa {

    private val reconhecedor by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private const val LARGURA_MAX = 2400

    /**
     * Faixas ignoradas por padrao. Generosas de proposito: a imagem pode ser um print
     * (mapa ocupa quase tudo) ou uma foto da tela do celular (mapa deslocado e torto).
     * Melhor deixar passar lixo, que o usuario desliga com um toque, do que comer parada.
     */
    private const val TOPO_IGNORADO = 0.04f
    private const val RODAPE_IGNORADO = 0.94f

    /**
     * Os baloes do app de entregas sao pastel -- verde claro, rosa claro, bege.
     * Corte alto aqui derruba quase todas as paradas; so serve para descartar
     * fundo branco puro, como o escudo de rodovia.
     */
    private const val SATURACAO_MINIMA = 0.05f

    /**
     * @param semFiltros le tudo que for numero, sem descartar nada. Usado quando a
     *        leitura normal traz poucas paradas.
     */
    suspend fun ler(ctx: Context, uri: Uri, semFiltros: Boolean = false): LeituraMapa? =
        withContext(Dispatchers.IO) {
        val bitmap = carregarReduzido(ctx, uri) ?: return@withContext null

        val texto = try {
            val entrada = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine<Text?> { cont ->
                reconhecedor.process(entrada)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        } catch (e: Exception) {
            null
        } ?: return@withContext LeituraMapa(
            bitmap, emptyList(), Diagnostico(), "Nao consegui ler a imagem."
        )

        val candidatos = mutableListOf<Pair<Int, Rect>>()
        for (bloco in texto.textBlocks) {
            for (linha in bloco.lines) {
                for (elemento in linha.elements) {
                    val t = elemento.text.trim()
                    if (!Regex("""^\d{1,3}$""").matches(t)) continue
                    val n = t.toIntOrNull() ?: continue
                    if (n < 1 || n > 999) continue
                    val caixa = elemento.boundingBox ?: continue
                    candidatos += n to caixa
                }
            }
        }
        if (candidatos.isEmpty()) {
            return@withContext LeituraMapa(bitmap, emptyList(), Diagnostico(),
                "Nao achei numero nenhum nesta imagem.")
        }

        var foraDaArea = 0
        var tamanhoErrado = 0
        var semCor = 0
        var repetidos = 0

        // 1. Cabecalho e cartao de detalhe: nao sao baloes.
        val alturaImg = bitmap.height.toFloat()
        val naArea = if (semFiltros) candidatos else candidatos.filter { (_, c) ->
            val cy = c.exactCenterY() / alturaImg
            val dentro = cy in TOPO_IGNORADO..RODAPE_IGNORADO
            if (!dentro) foraDaArea++
            dentro
        }

        // 2. Altura muito fora da mediana. Faixa larga porque foto de tela tem
        //    perspectiva e os numeros nao saem todos do mesmo tamanho.
        val alturas = naArea.map { it.second.height() }.sorted()
        val mediana = if (alturas.isEmpty()) 0 else alturas[alturas.size / 2]
        val noTamanho = if (semFiltros) naArea else naArea.filter { (_, c) ->
            val ok = mediana == 0 || (c.height() >= mediana * 0.40 && c.height() <= mediana * 2.6)
            if (!ok) tamanhoErrado++
            ok
        }

        // 3. Fundo branco puro: escudo de rodovia, numero solto do mapa.
        val coloridos = mutableListOf<Marcador>()
        for ((n, c) in noTamanho) {
            val cor = corDoFundo(bitmap, c)
            if (!semFiltros) {
                val hsv = FloatArray(3)
                Color.colorToHSV(cor, hsv)
                val quaseBranco = hsv[1] < SATURACAO_MINIMA && hsv[2] > 0.88f
                if (quaseBranco) { semCor++; continue }
            }
            coloridos += Marcador(n, c.exactCenterX(), c.exactCenterY(), cor)
        }

        // 4. Mesmo numero lido duas vezes quase no mesmo ponto.
        val proximidade = bitmap.width * 0.03f
        val finais = mutableListOf<Marcador>()
        for (m in coloridos) {
            val repetido = finais.any {
                it.numero == m.numero &&
                        abs(it.x - m.x) < proximidade && abs(it.y - m.y) < proximidade
            }
            if (repetido) repetidos++ else finais += m
        }

        val diag = Diagnostico(candidatos.size, foraDaArea, tamanhoErrado, semCor, repetidos)

        LeituraMapa(
            bitmap = bitmap,
            marcadores = finais.sortedBy { it.numero },
            diagnostico = diag,
            aviso = if (finais.isEmpty())
                "Li ${candidatos.size} numero(s), mas nenhum passou nos filtros."
            else null
        )
    }

    /**
     * Cor do balao: amostra um anel logo em volta do numero. Dentro da caixa esta
     * o texto; um pouco fora, o preenchimento do balao.
     */
    private fun corDoFundo(bmp: Bitmap, caixa: Rect): Int {
        val raio = (caixa.height() * 0.75f).toInt().coerceAtLeast(4)
        val cx = caixa.exactCenterX().toInt()
        val cy = caixa.exactCenterY().toInt()
        val pontos = listOf(
            cx - raio to cy, cx + raio to cy,
            cx to cy - raio, cx to cy + raio,
            cx - raio to cy - raio, cx + raio to cy + raio
        )
        var r = 0; var g = 0; var b = 0; var n = 0
        for ((px, py) in pontos) {
            if (px < 0 || py < 0 || px >= bmp.width || py >= bmp.height) continue
            val cor = bmp.getPixel(px, py)
            r += Color.red(cor); g += Color.green(cor); b += Color.blue(cor); n++
        }
        if (n == 0) return Color.WHITE
        return Color.rgb(r / n, g / n, b / n)
    }

    private fun carregarReduzido(ctx: Context, uri: Uri): Bitmap? {
        return try {
            val medir = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, medir)
            }
            var amostra = 1
            while (medir.outWidth / amostra > LARGURA_MAX) amostra *= 2
            val opcoes = BitmapFactory.Options().apply {
                inSampleSize = amostra
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opcoes)
            }
        } catch (e: Exception) {
            null
        }
    }
}
