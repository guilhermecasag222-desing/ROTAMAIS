package br.com.rotamais.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
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
import br.com.rotamais.ocr.Confianca

@Composable
fun TelaEntregas(vm: MainViewModel) {

    val ui by vm.ui.collectAsState()

    val selecionarFotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris -> vm.importarImagens(uris) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        if (ui.carregando) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(ui.progresso ?: "processando...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
        }

        if (ui.revisao.isNotEmpty()) {
            Revisao(vm)
        } else {
            Captura(vm) {
                selecionarFotos.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Captura

@Composable
private fun Captura(vm: MainViewModel, aoEscolherFotos: () -> Unit) {

    val ui by vm.ui.collectAsState()
    val entregas by vm.entregas.collectAsState()
    var texto by remember { mutableStateOf("") }
    var mostrarManual by remember { mutableStateOf(false) }

    Text("Capturar entregas", style = MaterialTheme.typography.headlineMedium)

    Painel("Pelas fotos das etiquetas") {
        Text(
            "Fotografe a etiqueta de cada pacote com a camera normal do celular enquanto " +
                    "carrega o carro. Depois importe todas de uma vez: a leitura roda offline, " +
                    "no proprio aparelho. Pode empilhar varias etiquetas na mesma foto.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BotaoGrande("IMPORTAR FOTOS DAS ETIQUETAS", cor = Color(0xFF22D07A)) { aoEscolherFotos() }
    }

    ui.mensagem?.let {
        Text(it, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge)
    }

    TextButton(onClick = { mostrarManual = !mostrarManual }) {
        Text(if (mostrarManual) "esconder digitacao manual" else "digitar/colar enderecos")
    }

    if (mostrarManual) {
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Um endereco por linha") },
            minLines = 3,
            maxLines = 6
        )
        BotaoGrande("ADICIONAR") { vm.adicionarEnderecos(texto); texto = "" }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${entregas.size} entrega(s)", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { vm.geocodificarPendentes() }) { Text("geocodificar") }
        TextButton(onClick = { vm.limparConcluidas() }) { Text("limpar feitas") }
        TextButton(onClick = { vm.apagarTudo() }) {
            Text("apagar tudo", color = MaterialTheme.colorScheme.error)
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entregas, key = { it.id }) { e ->
            CartaoEntrega(e,
                aoAlternarTipo = { vm.alternarTipo(e) },
                aoApagar = { vm.apagarEntrega(e) },
                aoReabrir = { vm.reabrir(e) })
        }
    }
}

// ------------------------------------------------------------------ Revisao

@Composable
private fun Revisao(vm: MainViewModel) {

    val ui by vm.ui.collectAsState()
    val duvidosos = ui.revisao.count { it.confianca != Confianca.ALTA }

    Text("Conferir leitura", style = MaterialTheme.typography.headlineMedium)
    Text(
        "${ui.revisao.size} endereco(s) lido(s)" +
                if (duvidosos > 0) " - $duvidosos em vermelho/amarelo precisam de olhada" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotaoGrande("IMPORTAR", Modifier.weight(1f), cor = Color(0xFF22D07A)) {
            vm.confirmarRevisao()
        }
        BotaoGrande("DESCARTAR", Modifier.weight(1f)) { vm.descartarRevisao() }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ui.revisao, key = { it.id }) { item ->
            var mostrarBruto by remember(item.id) { mutableStateOf(false) }
            val cor = when (item.confianca) {
                Confianca.ALTA -> MaterialTheme.colorScheme.primary
                Confianca.MEDIA -> Color(0xFFE0B341)
                Confianca.BAIXA -> MaterialTheme.colorScheme.error
            }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.incluir, onCheckedChange = { vm.alternarRevisao(item.id) })
                        Text(
                            "${item.origem} - ${item.confianca.name.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cor
                        )
                    }
                    item.destinatario?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = item.texto,
                        onValueChange = { vm.editarRevisao(item.id, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Endereco") },
                        minLines = 1,
                        maxLines = 3
                    )
                    TextButton(onClick = { mostrarBruto = !mostrarBruto }) {
                        Text(if (mostrarBruto) "esconder texto lido" else "ver texto lido pelo OCR")
                    }
                    if (mostrarBruto) {
                        Text(
                            item.textoBruto,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Cartao

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
