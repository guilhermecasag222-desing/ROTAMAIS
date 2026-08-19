package br.com.rotamais.ocr

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Leitura continua das etiquetas: o entregador passa o celular por cima dos pacotes
 * e o app vai reconhecendo sozinho, sem foto e sem toque.
 *
 * Um frame por vez (KEEP_ONLY_LATEST) e no maximo um a cada [INTERVALO_MS] --
 * passar todo frame pelo OCR fritaria a CPU sem ler nada a mais.
 */
class Scanner(
    private val ctx: Context,
    private val aoLerTexto: (String) -> Unit
) {

    private val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val executor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var ultimoProcessado = 0L

    companion object {
        private const val INTERVALO_MS = 350L
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun iniciar(dono: LifecycleOwner, preview: PreviewView) {
        val futuro = ProcessCameraProvider.getInstance(ctx)
        futuro.addListener({
            val p = futuro.get()
            provider = p

            val visor = Preview.Builder().build().also {
                it.setSurfaceProvider(preview.surfaceProvider)
            }

            val analise = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analise.setAnalyzer(executor) { frame ->
                val agora = System.currentTimeMillis()
                val imagem = frame.image
                if (imagem == null || agora - ultimoProcessado < INTERVALO_MS) {
                    frame.close()
                    return@setAnalyzer
                }
                ultimoProcessado = agora
                val entrada = InputImage.fromMediaImage(imagem, frame.imageInfo.rotationDegrees)
                reconhecedor.process(entrada)
                    .addOnSuccessListener { r -> if (r.text.isNotBlank()) aoLerTexto(r.text) }
                    .addOnCompleteListener { frame.close() }
            }

            try {
                p.unbindAll()
                p.bindToLifecycle(dono, CameraSelector.DEFAULT_BACK_CAMERA, visor, analise)
            } catch (e: Exception) {
                // Camera ocupada por outro app ou indisponivel: a tela avisa e o
                // usuario continua pelo caminho das fotos.
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun parar() {
        try { provider?.unbindAll() } catch (e: Exception) { }
        executor.shutdown()
    }
}
