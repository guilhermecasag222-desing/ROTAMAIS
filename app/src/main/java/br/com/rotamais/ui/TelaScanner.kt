package br.com.rotamais.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import br.com.rotamais.ocr.Scanner

/**
 * Passa o celular por cima dos pacotes e o app vai lendo. Sem foto, sem toque:
 * a etiqueta entra na fila assim que e reconhecida, e repetida e ignorada.
 */
@Composable
fun TelaScanner(vm: MainViewModel, aoSair: () -> Unit) {

    val ctx = LocalContext.current
    val dono = LocalLifecycleOwner.current
    val haptico = LocalHapticFeedback.current
    val ui by vm.ui.collectAsState()

    var temPermissao by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val pedir = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida -> temPermissao = concedida }

    LaunchedEffect(Unit) {
        if (!temPermissao) pedir.launch(Manifest.permission.CAMERA)
    }

    // Vibra a cada etiqueta nova, para dar retorno sem precisar olhar a tela.
    LaunchedEffect(ui.revisao.size) {
        if (ui.revisao.isNotEmpty()) haptico.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    if (!temPermissao) {
        Column(Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Preciso da camera", style = MaterialTheme.typography.headlineMedium)
            Text(
                "O scanner le as etiquetas direto pela camera. Se preferir nao dar a " +
                        "permissao, da para importar fotos das etiquetas na aba Entregas.",
                style = MaterialTheme.typography.bodyLarge
            )
            BotaoGrande("PERMITIR CAMERA") { pedir.launch(Manifest.permission.CAMERA) }
            BotaoGrande("VOLTAR") { aoSair() }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { contexto ->
                val visor = PreviewView(contexto).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val scanner = Scanner(contexto) { texto -> vm.processarLeituraScanner(texto) }
                scanner.iniciar(dono, visor)
                visor.tag = scanner
                visor
            },
            onRelease = { visor -> (visor.tag as? Scanner)?.parar() }
        )

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color(0xCC0E1116)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("${ui.revisao.size}",
                style = MaterialTheme.typography.displaySmall,
                color = Color(0xFF22D07A))
            Text("etiquetas lidas",
                style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(
                "Passe o celular devagar por cima dos pacotes, uns 25 cm de distancia. " +
                        "Repetida nao conta duas vezes.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC3CCD8),
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xCC0E1116)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BotaoGrande("PRONTO - CONFERIR ${ui.revisao.size} LEITURA(S)",
                cor = Color(0xFF22D07A)) { aoSair() }
        }
    }
}
