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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.rotamais.data.Entrega
import br.com.rotamais.data.StatusEntrega
import br.com.rotamais.data.TipoLocal

@Composable
fun TelaEntregas(vm: MainViewModel) {

    val ui by vm.ui.collectAsState()
    val entregas by vm.entregas.collectAsState()
    var texto by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Capturar entregas", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Cole uma entrega por linha. Formatos aceitos:\n" +
                    "Rua X, 123, Centro, Ararangua\n" +
                    "22 | Rua X, 123, Centro, Ararangua\n" +
                    "22 | Mercado Sao Jose | -28.9351,-49.4917",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enderecos") },
            minLines = 4,
            maxLines = 8
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotaoGrande("ADICIONAR", Modifier.weight(1f)) {
                vm.adicionarEnderecos(texto); texto = ""
            }
            BotaoGrande("GEOCODIFICAR", Modifier.weight(1f), cor = Color(0xFF4FA8FF)) {
                vm.geocodificarPendentes()
            }
        }

        ui.progresso?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium)
        }
        ui.mensagem?.let {
            Text(it, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${entregas.size} entrega(s)", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { vm.limparConcluidas() }) { Text("limpar concluidas") }
            TextButton(onClick = { vm.apagarTudo() }) {
                Text("apagar tudo", color = MaterialTheme.colorScheme.error)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entregas, key = { it.id }) { e ->
                CartaoEntrega(e, aoAlternarTipo = { vm.alternarTipo(e) },
                    aoApagar = { vm.apagarEntrega(e) },
                    aoReabrir = { vm.reabrir(e) })
            }
        }
    }
}

@Composable
private fun CartaoEntrega(
    e: Entrega,
    aoAlternarTipo: () -> Unit,
    aoApagar: () -> Unit,
    aoReabrir: () -> Unit
) {
    val corStatus = when {
        e.status == StatusEntrega.CONCLUIDA -> MaterialTheme.colorScheme.primary
        e.lat == null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("#${e.codigo}  ${e.enderecoBruto}",
                style = MaterialTheme.typography.titleMedium, color = corStatus)
            Text(
                listOfNotNull(e.bairro, e.cidade, e.cep).joinToString(" - ")
                    .ifBlank { e.enderecoNormalizado ?: "sem detalhes" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (e.lat != null && e.lon != null) {
                Text("%.5f, %.5f".format(e.lat, e.lon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.erroGeo?.let {
                Text("! $it - corrija o texto ou informe lat,lon",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = aoAlternarTipo) {
                    Text(
                        when (e.tipo) {
                            TipoLocal.COMERCIO -> "COMERCIO"
                            TipoLocal.RESIDENCIAL -> "RESIDENCIAL"
                            TipoLocal.DESCONHECIDO -> "TIPO?"
                        }
                    )
                }
                if (e.status == StatusEntrega.CONCLUIDA) {
                    TextButton(onClick = aoReabrir) { Text("reabrir") }
                }
                TextButton(onClick = aoApagar) {
                    Text("apagar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
