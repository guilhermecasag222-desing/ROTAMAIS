package br.com.rotamais.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.rotamais.data.StatusEntrega

@Composable
fun TelaHome(vm: MainViewModel, irPara: (String) -> Unit) {

    val ui by vm.ui.collectAsState()
    val entregas by vm.entregas.collectAsState()

    val pendentes = entregas.count { it.status == StatusEntrega.PENDENTE }
    val concluidas = entregas.count { it.status == StatusEntrega.CONCLUIDA }
    val semCoord = entregas.count { it.status == StatusEntrega.PENDENTE && it.lat == null }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ROTA+", style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary)
        Text("otimizador de rotas de entrega",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (ui.carregando) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(ui.progresso ?: "processando...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary)
        }

        Painel("Minha posicao") {
            Text(ui.origemRotulo, style = MaterialTheme.typography.titleMedium)
            BotaoGrande("ATUALIZAR LOCALIZACAO") { vm.atualizarLocalizacao() }
        }

        Painel("Entregas") {
            Linha("Total capturadas", "${entregas.size}")
            Linha("Pendentes", "$pendentes", destaque = true)
            Linha("Concluidas", "$concluidas")
            if (semCoord > 0) {
                Text("$semCoord sem coordenada - geocodifique na aba Entregas",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (ui.lote.isNotEmpty()) {
            Painel("Lote atual") {
                Linha("Paradas", "${ui.lote.size}")
                Linha("Distancia", km(ui.kmLote))
                Linha("Tempo estimado", duracao(ui.minutosLote), destaque = true)
                Linha("Combustivel", "${"%.2f".format(ui.litrosLote).replace('.', ',')} L")
                Linha("Custo estimado", reais(ui.custoLote))
                Text("base: ${ui.fonteDistancia}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        BotaoGrande("ROTA PELO PRINT DO MAPA", cor = Color(0xFF22D07A)) { irPara("mapa") }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotaoGrande("ENTREGAS", Modifier.weight(1f)) { irPara("entregas") }
            BotaoGrande("OTIMIZAR\nROTA", Modifier.weight(1f)) {
                vm.otimizar(); irPara("rota")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotaoGrande("CONSUMO", Modifier.weight(1f)) { irPara("consumo") }
            BotaoGrande("HISTORICO", Modifier.weight(1f)) { irPara("historico") }
        }

        ui.mensagem?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}
