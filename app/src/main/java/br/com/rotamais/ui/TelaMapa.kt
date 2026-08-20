package br.com.rotamais.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs

/**
 * Um print da tela do app de entregas vira a ordem de execucao das paradas.
 * Sem endereco, sem foto de etiqueta: os numeros ja estao no mapa.
 */
@Composable
fun TelaMapa(vmPrincipal: MainViewModel) {

    val vm: MapaViewModel = viewModel()
    val e by vm.estado.collectAsState()

    val escolherPrint = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.carregarPrint(uri) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text("Rota pelo mapa", style = MaterialTheme.typography.headlineMedium)

        if (e.bitmap == null) {
            Painel("Como usar") {
                Text(
                    "1. No Envio Logistics, deixe o mapa mostrando as paradas.\n" +
                            "2. Tire um print da tela.\n" +
                            "3. Toque abaixo e escolha esse print.\n" +
                            "4. Toque no mapa onde voce esta agora.\n\n" +
                            "O ROTA+ le os numeros dos baloes e devolve a ordem.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            BotaoGrande("ESCOLHER PRINT DO MAPA", cor = Color(0xFF22D07A)) {
                escolherPrint.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }

        if (e.carregando) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Lendo os numeros do mapa...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
        }

        e.mensagem?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary)
        }

        val bmp = e.bitmap
        if (bmp != null) {
            // A sequencia vem antes da imagem: e a resposta que o entregador
            // precisa ler de relance, com o carro parado.
            e.resultado?.let { r ->
                Painel("Faca nesta ordem") {
                    Text(
                        r.paradas.joinToString("  →  ") { "${it.marcador.numero}" },
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    r.paradas.firstOrNull()?.let { p ->
                        BotaoGrande("ENTREGUEI A ${p.marcador.numero} - PROXIMA",
                            cor = Color(0xFF22D07A)) { vm.concluirPrimeira() }
                    }
                }
            }

            MapaInterativo(e, vm, bmp.width, bmp.height)

            Text(
                "Toque num numero para tirar essa parada da conta. " +
                        "Toque em qualquer outro ponto para mudar de onde voce esta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Painel("Leitura") {
                Linha("Paradas usadas", "${e.marcadores.count { it.ativo }}", destaque = true)
                Linha("Numeros lidos na imagem", "${e.diagnostico.lidos}")
                if (e.diagnostico.descartados > 0) {
                    Linha("Descartados", "${e.diagnostico.descartados}")
                    Text(
                        "por area ${e.diagnostico.foraDaArea} - " +
                                "tamanho ${e.diagnostico.tamanhoErrado} - " +
                                "cor ${e.diagnostico.semCor} - " +
                                "repetidos ${e.diagnostico.repetidos}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!e.semFiltros) {
                    Text(
                        "Faltou parada? Leia sem nenhum filtro e desligue na mao o que " +
                                "nao for entrega.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BotaoGrande("LER TUDO, SEM FILTRO", cor = Color(0xFF4FA8FF)) {
                        vm.relerSemFiltros()
                    }
                } else {
                    Text("Lendo sem filtro: todo numero da imagem virou parada.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }

            val r = e.resultado
            if (r != null) {
                Painel("Estimativas") {
                    Linha("Paradas", "${r.paradas.size}")
                    Linha("Distancia estimada", km(r.kmTotal))
                    Linha("Tempo estimado", duracao(r.minutosTotal), destaque = true)

                    val litros = if (vm.prefs.consumoKmL > 0) r.kmTotal / vm.prefs.consumoKmL else 0.0
                    Linha("Combustivel", reais(litros * vm.prefs.precoLitro))
                }

                Painel("Comparativo (ESTIMATIVA)") {
                    Linha("Ordem 1, 2, 3... do app",
                        "${km(r.kmNaOrdemDoApp)} / ${duracao(r.minutosNaOrdemDoApp)}")
                    Linha("Ordem sugerida aqui",
                        "${km(r.kmTotal)} / ${duracao(r.minutosTotal)}")
                    val economiaKm = r.kmNaOrdemDoApp - r.kmTotal
                    val economiaMin = r.minutosNaOrdemDoApp - r.minutosTotal
                    Linha("Diferenca", "${km(economiaKm)} / ${duracao(economiaMin)}", destaque = true)
                    Text(
                        "Numeros estimados a partir da escala do mapa. A ordem nao depende " +
                                "dessa escala; so os quilometros dependem.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            }

            Painel("Escala do mapa") {
                Text(
                    "Quantos km cabem na largura do print? Usado so para estimar km, " +
                            "tempo e combustivel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5.0, 12.0, 25.0, 50.0).forEach { v ->
                        BotaoGrande("${v.toInt()}", Modifier.weight(1f),
                            cor = if (abs(vm.prefs.larguraMapaKm - v) < 0.1)
                                Color(0xFF22D07A) else null) {
                            vm.definirLarguraMapaKm(v)
                        }
                    }
                }
            }

            BotaoGrande("OUTRO PRINT") {
                vm.limpar()
                escolherPrint.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }
}

@Composable
private fun MapaInterativo(e: MapaState, vm: MapaViewModel, larguraPx: Int, alturaPx: Int) {

    val bmp = e.bitmap ?: return
    val proporcao = larguraPx.toFloat() / alturaPx.toFloat()

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(proporcao)
            .pointerInput(larguraPx, alturaPx, e.marcadores) {
                detectTapGestures { toque ->
                    val escala = larguraPx / size.width.toFloat()
                    val x = toque.x * escala
                    val y = toque.y * escala

                    // Toque perto de um numero alterna aquela parada.
                    val alvo = e.marcadores.minByOrNull {
                        (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y)
                    }
                    val distancia = alvo?.let {
                        kotlin.math.hypot((it.x - x).toDouble(), (it.y - y).toDouble())
                    } ?: Double.MAX_VALUE

                    if (alvo != null && distancia < larguraPx * 0.035) {
                        vm.alternarMarcador(alvo.numero)
                    } else {
                        vm.definirOrigem(x, y)
                    }
                }
            }
    ) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "print do mapa de entregas",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(Modifier.fillMaxSize()) {
            val escala = size.width / larguraPx.toFloat()
            fun ponto(x: Float, y: Float) = Offset(x * escala, y * escala)

            // Caminho na ordem sugerida.
            e.resultado?.paradas?.let { paradas ->
                var anterior = e.origemX?.let { ox ->
                    e.origemY?.let { oy -> ponto(ox, oy) }
                }
                paradas.forEach { p ->
                    val atual = ponto(p.marcador.x, p.marcador.y)
                    if (anterior != null) {
                        drawLine(
                            color = Color(0xFF22D07A),
                            start = anterior!!,
                            end = atual,
                            strokeWidth = 6f
                        )
                    }
                    anterior = atual
                }
            }

            // Paradas desligadas: circulo vermelho por cima.
            e.marcadores.filter { !it.ativo }.forEach {
                drawCircle(
                    color = Color(0xCCFF6B6B),
                    radius = size.width * 0.022f,
                    center = ponto(it.x, it.y)
                )
            }

            // Onde voce esta.
            val ox = e.origemX
            val oy = e.origemY
            if (ox != null && oy != null) {
                drawCircle(Color.White, size.width * 0.030f, ponto(ox, oy))
                drawCircle(Color(0xFF4FA8FF), size.width * 0.022f, ponto(ox, oy))
            }
        }

        if (e.origemX == null && e.marcadores.isNotEmpty()) {
            Text(
                "toque onde voce esta",
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
