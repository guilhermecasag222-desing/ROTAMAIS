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
    val descartados: Int,
    val aviso: String? = null
)

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

    private const val LARGURA_MAX = 1600

    /** Faixas de tela ocupadas por barra de status, cabecalho e cartao inferior. */
    private const val TOPO_IGNORADO = 0.11f
    private const val RODAPE_IGNORADO = 0.82f

    /** Abaixo disso o fundo e branco/cinza: rotulo de rodovia ou nome de rua, nao balao. */
    private const val SATURACAO_MINIMA = 0.12f

    suspend fun ler(ctx: Context, uri: Uri): LeituraMapa? = withContext(Dispatchers.IO) {
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
        } ?: return@withContext LeituraMapa(bitmap, emptyList(), 0, "Nao consegui ler a imagem.")

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
            return@withContext LeituraMapa(bitmap, emptyList(), 0,
                "Nao achei numeros de parada nesta imagem.")
        }

        var descartados = 0

        // 1. Fora da area do mapa: cabecalho e cartao de detalhe nao sao baloes.
        val alturaImg = bitmap.height.toFloat()
        val naArea = candidatos.filter { (_, c) ->
            val cy = c.exactCenterY() / alturaImg
            val dentro = cy in TOPO_IGNORADO..RODAPE_IGNORADO
            if (!dentro) descartados++
            dentro
        }

        // 2. Tamanho fora do padrao: numeros de balao tem altura parecida entre si.
        val alturas = naArea.map { it.second.height() }.sorted()
        val mediana = if (alturas.isEmpty()) 0 else alturas[alturas.size / 2]
        val noTamanho = naArea.filter { (_, c) ->
            val ok = mediana == 0 || (c.height() >= mediana * 0.55 && c.height() <= mediana * 1.9)
            if (!ok) descartados++
            ok
        }

        // 3. Fundo sem cor: escudo de rodovia (BR-101), numero de rua, rotulo do mapa.
        val coloridos = mutableListOf<Marcador>()
        for ((n, c) in noTamanho) {
            val cor = corDoFundo(bitmap, c)
            val hsv = FloatArray(3)
            Color.colorToHSV(cor, hsv)
            if (hsv[1] < SATURACAO_MINIMA) { descartados++; continue }
            coloridos += Marcador(n, c.exactCenterX(), c.exactCenterY(), cor)
        }

        // 4. Mesmo numero lido duas vezes quase no mesmo ponto: fica o primeiro.
        val finais = mutableListOf<Marcador>()
        for (m in coloridos) {
            val repetido = finais.any {
                it.numero == m.numero && abs(it.x - m.x) < 40 && abs(it.y - m.y) < 40
            }
            if (repetido) descartados++ else finais += m
        }

        LeituraMapa(
            bitmap = bitmap,
            marcadores = finais.sortedBy { it.numero },
            descartados = descartados,
            aviso = if (finais.isEmpty())
                "Li numeros, mas nenhum parecia balao de parada. Tente um print com mais zoom."
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
