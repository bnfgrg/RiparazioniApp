package it.officina.riparazioni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.officina.riparazioni.ui.RiparazioneVMFactory
import it.officina.riparazioni.ui.RiparazioneViewModel
import it.officina.riparazioni.ui.screens.DettaglioScreen
import it.officina.riparazioni.ui.screens.ListaScreen
import it.officina.riparazioni.ui.theme.RiparazioniTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RiparazioneViewModel by viewModels {
        RiparazioneVMFactory((application as RiparazioniApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            RiparazioniTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "lista") {
                        composable("lista") {
                            ListaScreen(
                                vm = viewModel,
                                onApri = { id -> nav.navigate("dettaglio/$id") },
                                onNuova = { nav.navigate("dettaglio/0") }
                            )
                        }
                        composable("dettaglio/{id}") { back ->
                            val idStr = back.arguments?.getString("id")
                            val id = idStr?.toLongOrNull() ?: 0L
                            DettaglioScreen(
                                vm = viewModel,
                                riparazioneId = if (id == 0L) null else id,
                                onIndietro = { nav.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
