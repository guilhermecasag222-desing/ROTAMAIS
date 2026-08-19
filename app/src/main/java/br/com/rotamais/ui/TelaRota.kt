package br.com.rotamais.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.rotamais.data.TipoLocal
import br.com.rotamais.geo.Localizacao

@Composable
fun TelaRota(vm: MainViewModel) {

    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current

    val agora = System.currentTimeMillis()
    val previsao = agora + ui.minutosLote * 60_000L
    val inicio = vm.prefs.inicioRotaEm.takeIf { it > 0L } ?: agora
    val decorridoMin = ((agora - inicio) / 60000L).toInt()
    val totalPrevistoMin = decorridoMin + ui.minutosLote
    val meta = vm.prefs.metaMinutos
    val delta = totalPrevistoMin - meta

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Lote atual", style = MaterialTheme.typography.headlineMedium)

        Painel("Resumo do lote") {
            Linha("Paradas", "${ui.lote.size}", destaque = true)
            Linha("Distancia", km(ui.kmLote))
            Linha("Tempo do lote", duracao(ui.minutosLote))
            Linha("Previsao de termino", hhmm(previsao), destaque = true)
            Linha("Combustivel", "${"%.2f".format(ui.litrosLote).replace('.', ',')} L")
            Linha("Custo", reais(ui.custoLote))
        }

        Painel("Meta da rota") {
            Linha("Inicio", hhmm(inicio))
            Linha("Meta", duracao(meta))
            Linha("Previsto no total", duracao(totalPrevistoMin))
            Text(
                when {
                    delta <= -15 -> "RITMO BOM - ${duracao(-delta)} abaixo da meta"
                    delta <= 0 -> "DENTRO DA META"
                    else -> "${duracao(delta)} ACIMA DA META"
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (delta <= 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }

        if (ui.kmSemOtimizar > 0) {
            Painel("Comparativo (ESTIMATIVA)") {
                Linha("Ordem de captura", "${km(ui.kmSemOtimizar)} / ${duracao(ui.minutosSemOtimizar)}")
                Linha("Ordem otimizada", "${km(ui.kmLote)} / ${duracao(ui.minutosLote)}")
                val kmEcon = ui.kmSemOtimizar - ui.kmLote
                val minEcon = ui.minutosSemOtimizar - ui.minutosLote
                val litros = if (vm.prefs.consumoKmL > 0) kmEcon / vm.prefs.consumoKmL else 0.0
                Linha(
                    "Economia estimada",
                    "${km(kmEcon)} / ${duracao(minEcon)} / ${reais(litros * vm.prefs.precoLitro)}",
                    destaque = true
                )
                Text(
                    "Numeros estimados, nao medidos. So o odometro confirma o resultado real.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (ui.lote.isEmpty()) {
            Text("Nenhum lote calculado. Atualize a localizacao e toque em OTIMIZAR.",
                style = MaterialTheme.typography.bodyLarge)
            BotaoGrande("OTIMIZAR AGORA", cor = Color(0xFF22D07A)) { vm.otimizar() }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ui.lote, key = { it.entrega.id }) { p ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (p.ordem == 1) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${p.ordem}.  #${p.entrega.codigo}" +
                                    if (p.entrega.tipo == TipoLocal.COMERCIO) "   [COMERCIO]" else "",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (p.ordem == 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(p.entrega.enderecoNormalizado ?: p.entrega.enderecoBruto,
                            style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${km(p.kmDoAnterior)} do ponto anterior - chega em ~${duracao(p.minutosAcumulados)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BotaoGrande("NAVEGAR", Modifier.weight(1f)) {
                                val lat = p.entrega.lat
                                val lon = p.entrega.lon
                                if (lat != null && lon != null) {
                                    Localizacao.abrirNavegacao(ctx, lat, lon, p.entrega.enderecoBruto)
                                }
                            }
                            BotaoGrande("ENTREGUE", Modifier.weight(1f), cor = Color(0xFF22D07A)) {
                                vm.concluir(p)
                            }
                        }
                    }
                }
            }
        }
    }
}
