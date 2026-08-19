package br.com.rotamais

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.rotamais.geo.Localizacao
import br.com.rotamais.ui.MainViewModel
import br.com.rotamais.ui.RotaMaisTheme
import br.com.rotamais.ui.TelaConsumo
import br.com.rotamais.ui.TelaEntregas
import br.com.rotamais.ui.TelaHistorico
import br.com.rotamais.ui.TelaHome
import br.com.rotamais.ui.TelaRota
import br.com.rotamais.ui.TelaScanner

class MainActivity : ComponentActivity() {

    /** Fotos que chegaram por "Compartilhar" de outro app, esperando o OCR. */
    private val compartilhadas: SnapshotStateList<Uri> = mutableListOf<Uri>().toMutableStateList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receber(intent)
        setContent { RotaMaisTheme { App(compartilhadas) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receber(intent)
    }

    private fun receber(intent: Intent?) {
        if (intent == null) return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(extrair(intent, Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> extrairVarias(intent)
            else -> emptyList()
        }
        if (uris.isNotEmpty()) compartilhadas.addAll(uris)
    }

    @Suppress("DEPRECATION")
    private fun extrair(intent: Intent, chave: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(chave, Uri::class.java)
        else intent.getParcelableExtra(chave)

    @Suppress("DEPRECATION")
    private fun extrairVarias(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
}

private data class Aba(val rota: String, val titulo: String)

private val ABAS = listOf(
    Aba("home", "Inicio"),
    Aba("entregas", "Entregas"),
    Aba("rota", "Rota"),
    Aba("consumo", "Consumo"),
    Aba("historico", "Historico")
)

@Composable
fun App(compartilhadas: SnapshotStateList<Uri> = mutableListOf<Uri>().toMutableStateList()) {

    val vm: MainViewModel = viewModel()
    val nav = rememberNavController()
    val entradaAtual by nav.currentBackStackEntryAsState()
    val ctx = LocalContext.current

    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { concedidas ->
        if (concedidas.values.any { it }) vm.atualizarLocalizacao()
    }

    LaunchedEffect(Unit) {
        if (Localizacao.temPermissao(ctx)) {
            vm.atualizarLocalizacao()
        } else {
            pedirPermissao.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Fotos chegadas por "Compartilhar": manda para o OCR e abre a aba Entregas.
    LaunchedEffect(compartilhadas.size) {
        if (compartilhadas.isNotEmpty()) {
            val copia = compartilhadas.toList()
            compartilhadas.clear()
            vm.importarImagens(copia)
            nav.navigate("entregas") { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val destino = entradaAtual?.destination
                ABAS.forEach { aba ->
                    NavigationBarItem(
                        selected = destino?.hierarchy?.any { it.route == aba.rota } == true,
                        onClick = {
                            nav.navigate(aba.rota) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(aba.titulo.take(3).uppercase()) },
                        label = { Text(aba.titulo) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { TelaHome(vm) { destino -> nav.navigate(destino) } }
            composable("entregas") {
                TelaEntregas(vm) { nav.navigate("scanner") }
            }
            composable("scanner") {
                TelaScanner(vm) {
                    nav.navigate("entregas") { popUpTo("entregas") { inclusive = true } }
                }
            }
            composable("rota") { TelaRota(vm) }
            composable("consumo") { TelaConsumo(vm) }
            composable("historico") { TelaHistorico(vm) }
        }
    }
}
