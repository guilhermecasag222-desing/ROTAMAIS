package br.com.rotamais.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import br.com.rotamais.captura.ServicoLeitura
import br.com.rotamais.data.Entrega
import br.com.rotamais.data.StatusEntrega
import br.com.rotamais.data.TipoLocal
import br.com.rotamais.ocr.Confianca

@Composable
fun TelaEntregas(vm: MainViewModel, aoAbrirScanner: () -> Unit = {}) {

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
            Revisao(vm, aoAbrirScanner)
        } else {
            Captura(
                vm = vm,
                aoEscolherFotos = {
                    selecionarFotos.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                aoAbrirScanner = aoAbrirScanner
            )
        }
    }
}

// ------------------------------------------------------------------ Captura

@Composable
private fun Captura(
    vm: MainViewModel,
    aoEscolherFotos: () -> Unit,
    aoAbrirScanner: () -> Unit
) {

    val ui by vm.ui.collectAsState()
    val entregas by vm.entregas.collectAsState()
    var texto by remember { mutableStateOf("") }
    var mostrarManual by remember { mutableStateOf(false) }

    Text("Capturar entregas", style = MaterialTheme.typography.headlineMedium)

    LeituraAutomatica(vm)

    Painel("Scanner") {
        Text(
            "Passe o celular por cima dos pacotes, como um leitor de supermercado. " +
                    "O app le as etiquetas sozinho, conta na tela e ignora repetidas. " +
                    "Sem foto, sem toque, funciona offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BotaoGrande("ABRIR SCANNER", cor = Color(0xFF22D07A)) { aoAbrirScanner() }
    }

    Painel("Ou por fotos ja tiradas") {
        Text(
            "Se preferir fotografar antes, importe as fotos de uma vez. " +
                    "Varias etiquetas na mesma foto viram varias entregas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BotaoGrande("IMPORTAR FOTOS") { aoEscolherFotos() }
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

// -------------------------------------------------------- Leitura automatica

/**
 * Captura sem esforco: o servico de acessibilidade le a tela do app de entregas
 * enquanto o entregador o usa normalmente. Nada de foto, nada de toque extra.
 */
@Composable
private fun LeituraAutomatica(vm: MainViewModel) {

    val ctx = LocalContext.current
    val capturas by vm.capturas.collectAsState()
    var ativo by remember { mutableStateOf(ServicoLeitura.ativo(ctx)) }
    var alvo by remember { mutableStateOf(vm.prefs.pacoteAlvo) }

    // Reconfere ao voltar das configuracoes do Android.
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) {
                ativo = ServicoLeitura.ativo(ctx)
                alvo = vm.prefs.pacoteAlvo
            }
        }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }

    Painel("Leitura automatica da tela") {
        Text(
            if (ativo) "Ligada. Abra o Envio Logistics e use normalmente: o ROTA+ vai " +
                    "pescando os enderecos que aparecerem."
            else "Desligada. Com ela ligada voce nao precisa fotografar nem escanear nada: " +
                    "o ROTA+ le os enderecos da tela do app de entregas enquanto voce o usa.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (ativo) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!ativo) {
            BotaoGrande("LIGAR LEITURA AUTOMATICA", cor = Color(0xFF4FA8FF)) {
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Text(
                "Vai abrir as configuracoes do Android. Procure ROTA+ na lista e ative.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Linha("Telas capturadas", "${capturas.size}", destaque = true)

            BotaoGrande(
                if (capturas.isEmpty()) "NADA CAPTURADO AINDA"
                else "CONFERIR ${capturas.size} CAPTURA(S)",
                cor = if (capturas.isEmpty()) null else Color(0xFF22D07A),
                habilitado = capturas.isNotEmpty()
            ) { vm.processarCapturas() }

            Text(
                if (alvo.isBlank()) "Lendo qualquer app."
                else "Lendo so: $alvo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    val p = vm.fixarAppAlvo()
                    alvo = p
                }) { Text("so o ultimo app usado") }
                TextButton(onClick = { vm.liberarAppAlvo(); alvo = "" }) { Text("qualquer app") }
                TextButton(onClick = { vm.limparCapturas() }) { Text("limpar") }
            }
        }
    }
}

// ------------------------------------------------------------------ Revisao

@Composable
private fun Revisao(vm: MainViewModel, aoAbrirScanner: () -> Unit) {

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
        BotaoGrande("LER MAIS", Modifier.weight(1f)) { aoAbrirScanner() }
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
