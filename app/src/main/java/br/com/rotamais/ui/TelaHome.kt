package br.com.rotamais.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.rotamais.BuildConfig
import br.com.rotamais.atualiza.Atualizador
import br.com.rotamais.atualiza.VersaoDisponivel
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

        AvisoAtualizacao()

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

        Text(
            "versao ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Avisa quando saiu versao nova e leva direto ao APK. Checa uma vez por abertura. */
@Composable
private fun AvisoAtualizacao() {

    val ctx = LocalContext.current
    var nova by remember { mutableStateOf<VersaoDisponivel?>(null) }

    LaunchedEffect(Unit) {
        nova = Atualizador.verificar(BuildConfig.VERSION_CODE)
    }

    val v = nova ?: return

    Painel("Atualizacao disponivel") {
        Text(
            "Saiu a ${v.nome}. Voce esta na ${BuildConfig.VERSION_NAME}.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (v.notas.isNotBlank()) {
            Text(v.notas, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BotaoGrande("BAIXAR ATUALIZACAO", cor = Color(0xFF4FA8FF)) {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(v.urlApk))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        Text(
            "O Android baixa o arquivo; toque na notificacao para instalar por cima.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
