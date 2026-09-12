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
import it.officina.riparazioni.ui.screens.*
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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "lista") {

                        // Lista riparazioni
                        composable("lista") {
                            ListaScreen(
                                vm = viewModel,
                                onApri = { nav.navigate("dettaglio/$it") },
                                onNuova = { nav.navigate("dettaglio/0") },
                                onApriAnagrafica = { nav.navigate("anagrafica") }
                            )
                        }

                        // Dettaglio/nuova riparazione
                        composable("dettaglio/{id}") { back ->
                            val id = back.arguments?.getString("id")?.toLongOrNull() ?: 0L
                            DettaglioScreen(
                                vm = viewModel,
                                riparazioneId = if (id == 0L) null else id,
                                onIndietro = { nav.popBackStack() },
                                onNuovoCliente = {
                                    // Vai a nuovo cliente e torna
                                    nav.navigate("anagrafica/cliente/0")
                                }
                            )
                        }

                        // Lista clienti
                        composable("anagrafica") {
                            AnagraficaListaScreen(
                                vm = viewModel,
                                onApriCliente = { nav.navigate("anagrafica/cliente/$it") },
                                onNuovoCliente = { nav.navigate("anagrafica/cliente/0") },
                                onIndietro = { nav.popBackStack() }
                            )
                        }

                        // Dettaglio/nuovo cliente
                        composable("anagrafica/cliente/{id}") { back ->
                            val id = back.arguments?.getString("id")?.toLongOrNull() ?: 0L
                            AnagraficaDettaglioScreen(
                                vm = viewModel,
                                clienteId = if (id == 0L) null else id,
                                onIndietro = { nav.popBackStack() },
                                onApriRiparazione = { nav.navigate("dettaglio/$it") }
                            )
                        }
                    }
                }
            }
        }
    }
}
