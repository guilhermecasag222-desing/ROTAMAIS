package br.com.rotamais.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tema escuro fixo: alto contraste, legivel no sol e sem consumir bateria em OLED.
private val Esquema = darkColorScheme(
    primary = Color(0xFF22D07A),
    onPrimary = Color(0xFF04150C),
    secondary = Color(0xFF4FA8FF),
    onSecondary = Color(0xFF041020),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF2F5F7),
    surface = Color(0xFF171C24),
    onSurface = Color(0xFFF2F5F7),
    surfaceVariant = Color(0xFF232B36),
    onSurfaceVariant = Color(0xFFC3CCD8),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0505),
    outline = Color(0xFF3A4552)
)

private val Tipografia = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun RotaMaisTheme(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, typography = Tipografia, content = conteudo)
}
