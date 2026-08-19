package br.com.rotamais.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp

@Composable
fun TelaHistorico(vm: MainViewModel) {

    val rotas by vm.historico.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Historico", style = MaterialTheme.typography.headlineMedium)

        if (rotas.isEmpty()) {
            Text(
                "Nenhuma rota salva ainda. Ao terminar o dia, use FINALIZAR ROTA na aba Consumo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        val totalEntregas = rotas.sumOf { it.qtdEntregas }
        val totalKm = rotas.sumOf { it.km }
        val totalCusto = rotas.sumOf { it.custo }

        Painel("Acumulado") {
            Linha("Rotas", "${rotas.size}")
            Linha("Entregas", "$totalEntregas")
            Linha("Km", km(totalKm))
            Linha("Combustivel", reais(totalCusto))
            if (totalEntregas > 0) {
                Linha("Custo por entrega", reais(totalCusto / totalEntregas), destaque = true)
                Linha("Km por entrega", km(totalKm / totalEntregas))
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rotas, key = { it.id }) { r ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${dataHora(r.fimEm)} - ${r.regiao}",
                            style = MaterialTheme.typography.titleMedium)
                        Linha("Entregas", "${r.qtdEntregas}")
                        Linha("Distancia", km(r.km))
                        Linha("Tempo", duracao(r.minutos))
                        Linha("Combustivel", reais(r.custo))
                        if (r.qtdEntregas > 0) {
                            Linha("Custo/entrega", reais(r.custo / r.qtdEntregas))
                            Linha("Km/entrega", km(r.km / r.qtdEntregas))
                            Linha("Min/entrega",
                                "%.1f".format(r.minutos.toDouble() / r.qtdEntregas).replace('.', ','))
                        }
                    }
                }
            }
        }
    }
}
