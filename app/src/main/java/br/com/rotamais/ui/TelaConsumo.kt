package br.com.rotamais.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import br.com.rotamais.data.StatusEntrega

@Composable
fun TelaConsumo(vm: MainViewModel) {

    val p = vm.prefs
    val entregas by vm.entregas.collectAsState()
    val ui by vm.ui.collectAsState()

    var veiculo by remember { mutableStateOf(p.veiculo) }
    var consumo by remember { mutableStateOf(fmt(p.consumoKmL)) }
    var preco by remember { mutableStateOf(fmt(p.precoLitro)) }
    var tempoParada by remember { mutableStateOf(fmt(p.tempoParadaMin)) }
    var velocidade by remember { mutableStateOf(fmt(p.velocidadeKmh)) }
    var fator by remember { mutableStateOf(fmt(p.fatorRodoviario)) }
    var lote by remember { mutableStateOf(p.tamanhoLote.toString()) }
    var raio by remember { mutableStateOf(fmt(p.raioClusterKm)) }
    var metaH by remember { mutableStateOf(fmt(p.metaMinutos / 60.0)) }
    var regiao by remember { mutableStateOf(p.regiaoPadrao) }
    var odometro by remember { mutableStateOf(fmt(p.odometroInicial)) }
    var abastecimento by remember { mutableStateOf(fmt(p.abastecimentoValor)) }
    var comercio by remember { mutableStateOf(p.preferirComercio) }
    var osrm by remember { mutableStateOf(p.usarOsrm) }
    var salvo by remember { mutableStateOf(false) }

    val concluidas = entregas.filter { it.status == StatusEntrega.CONCLUIDA }
    val kmRodados = concluidas.sumOf { it.kmPercorrido }
    val litros = if (p.consumoKmL > 0) kmRodados / p.consumoKmL else 0.0
    val custo = litros * p.precoLitro
    val medioReal = vm.tempoMedioRealPorParada()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Consumo e ajustes", style = MaterialTheme.typography.headlineMedium)

        Painel("Veiculo") {
            Campo("Veiculo", veiculo, KeyboardType.Text) { veiculo = it }
            Campo("Consumo (km/L)", consumo) { consumo = it }
            Campo("Preco da gasolina (R$/L)", preco) { preco = it }
        }

        Painel("Abastecimento desta rota") {
            Campo("Odometro inicial (km)", odometro) { odometro = it }
            Campo("Valor abastecido (R$)", abastecimento) { abastecimento = it }
            val litrosAbast = if (p.precoLitro > 0) p.abastecimentoValor / p.precoLitro else 0.0
            Linha("Litros abastecidos", "${"%.2f".format(litrosAbast).replace('.', ',')} L")
            Linha("Autonomia estimada", km(litrosAbast * p.consumoKmL))
        }

        Painel("Rota atual (medido)") {
            Linha("Entregas concluidas", "${concluidas.size}")
            Linha("Km rodados", km(kmRodados))
            Linha("Combustivel", "${"%.2f".format(litros).replace('.', ',')} L")
            Linha("Custo", reais(custo), destaque = true)
            if (concluidas.isNotEmpty()) {
                Linha("Custo por entrega", reais(custo / concluidas.size))
                Linha("Km por entrega", km(kmRodados / concluidas.size))
            }
            if (medioReal != null) {
                Text(
                    "Seu tempo medio real e ${"%.1f".format(medioReal).replace('.', ',')} min por entrega. " +
                            "Toque para adotar esse valor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                BotaoGrande("USAR ${"%.1f".format(medioReal).replace('.', ',')} MIN/PARADA") {
                    p.tempoParadaMin = medioReal
                    tempoParada = fmt(medioReal)
                }
            }
        }

        Painel("Tempo e meta") {
            Campo("Tempo medio por parada (min)", tempoParada) { tempoParada = it }
            Campo("Velocidade media (km/h)", velocidade) { velocidade = it }
            Campo("Fator rodoviario (linha reta -> estrada)", fator) { fator = it }
            Campo("Meta da rota (horas)", metaH) { metaH = it }
        }

        Painel("Otimizacao") {
            Campo("Entregas por lote", lote) { lote = it }
            Campo("Raio de agrupamento (km)", raio) { raio = it }
            Campo("Regiao padrao (colada no endereco)", regiao, KeyboardType.Text) { regiao = it }
            Alternar("Priorizar comercio no desempate", comercio) { comercio = it }
            Alternar("Usar OSRM (distancia real, precisa de internet)", osrm) { osrm = it }
        }

        BotaoGrande("SALVAR AJUSTES", cor = Color(0xFF22D07A)) {
            p.veiculo = veiculo
            p.consumoKmL = num(consumo, p.consumoKmL)
            p.precoLitro = num(preco, p.precoLitro)
            p.tempoParadaMin = num(tempoParada, p.tempoParadaMin)
            p.velocidadeKmh = num(velocidade, p.velocidadeKmh)
            p.fatorRodoviario = num(fator, p.fatorRodoviario)
            p.tamanhoLote = lote.toIntOrNull()?.coerceIn(1, 40) ?: p.tamanhoLote
            p.raioClusterKm = num(raio, p.raioClusterKm)
            p.metaMinutos = (num(metaH, p.metaMinutos / 60.0) * 60).toInt()
            p.regiaoPadrao = regiao
            p.odometroInicial = num(odometro, p.odometroInicial)
            p.abastecimentoValor = num(abastecimento, p.abastecimentoValor)
            p.preferirComercio = comercio
            p.usarOsrm = osrm
            salvo = true
            vm.otimizar()
        }
        if (salvo) Text("Ajustes salvos e rota recalculada.",
            color = MaterialTheme.colorScheme.primary)

        BotaoGrande("FINALIZAR ROTA E SALVAR NO HISTORICO") {
            vm.finalizarRota(regiao)
        }
        ui.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun Campo(
    rotulo: String,
    valor: String,
    tipo: KeyboardType = KeyboardType.Decimal,
    aoMudar: (String) -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = tipo),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Alternar(rotulo: String, valor: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rotulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = aoMudar)
    }
}

private fun fmt(v: Double): String = "%.2f".format(v).replace('.', ',').trimEnd('0').trimEnd(',')
private fun num(s: String, padrao: Double): Double =
    s.replace(',', '.').toDoubleOrNull() ?: padrao
