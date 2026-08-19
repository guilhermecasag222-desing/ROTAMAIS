package br.com.rotamais

import android.Manifest
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
import androidx.compose.ui.Modifier
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RotaMaisTheme { App() } }
    }
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
fun App() {
    val vm: MainViewModel = viewModel()
    val nav = rememberNavController()
    val entradaAtual by nav.currentBackStackEntryAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current

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
            composable("entregas") { TelaEntregas(vm) }
            composable("rota") { TelaRota(vm) }
            composable("consumo") { TelaConsumo(vm) }
            composable("historico") { TelaHistorico(vm) }
        }
    }
}
