package br.com.rotamais.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HORA = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
private val DATA = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

fun hhmm(ts: Long): String = HORA.format(Date(ts))
fun dataHora(ts: Long): String = DATA.format(Date(ts))

fun duracao(minutos: Int): String {
    val h = minutos / 60
    val m = minutos % 60
    return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m}min"
}

fun reais(v: Double): String = "R$ %.2f".format(v).replace('.', ',')
fun km(v: Double): String = "%.1f km".format(v).replace('.', ',')

/** Botao grande, para usar com o carro parado e sem precisar mirar. */
@Composable
fun BotaoGrande(
    texto: String,
    modifier: Modifier = Modifier,
    cor: Color? = null,
    habilitado: Boolean = true,
    aoClicar: () -> Unit
) {
    Button(
        onClick = aoClicar,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = if (cor != null)
            ButtonDefaults.buttonColors(containerColor = cor, contentColor = Color(0xFF04150C))
        else ButtonDefaults.buttonColors()
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun Painel(titulo: String? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (titulo != null) {
                Text(
                    titulo.uppercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            conteudo()
        }
    }
}

@Composable
fun Linha(rotulo: String, valor: String, destaque: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rotulo, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            valor,
            style = if (destaque) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
