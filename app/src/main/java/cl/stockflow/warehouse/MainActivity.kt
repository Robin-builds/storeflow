package cl.stockflow.warehouse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cl.stockflow.warehouse.ui.auth.AuthUiState
import cl.stockflow.warehouse.ui.auth.AuthViewModel
import cl.stockflow.warehouse.ui.auth.LoginScreen
import cl.stockflow.warehouse.ui.auth.RegistroScreen
import cl.stockflow.warehouse.ui.dashboard.DashboardScreen
import cl.stockflow.warehouse.ui.theme.StockFlowTheme
import dagger.hilt.android.AndroidEntryPoint

private object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val DASHBOARD = "dashboard"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StockFlowTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val uiState by authViewModel.uiState.collectAsState()

                    LaunchedEffect(uiState) {
                        when (uiState) {
                            is AuthUiState.Autenticado -> {
                                navController.navigate(Rutas.DASHBOARD) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                            is AuthUiState.Idle -> {
                                val destino_actual = navController.currentDestination?.route
                                if (destino_actual == Rutas.DASHBOARD) {
                                    navController.navigate(Rutas.LOGIN) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Rutas.LOGIN
                    ) {
                        composable(Rutas.LOGIN) {
                            LoginScreen(
                                uiState = uiState,
                                onLogin = authViewModel::login,
                                onIrARegistro = { navController.navigate(Rutas.REGISTRO) },
                                onLimpiarError = authViewModel::limpiarError
                            )
                        }
                        composable(Rutas.REGISTRO) {
                            RegistroScreen(
                                uiState = uiState,
                                onRegistrar = authViewModel::registrar,
                                onIrALogin = { navController.popBackStack() },
                                onLimpiarError = authViewModel::limpiarError
                            )
                        }
                        composable(Rutas.DASHBOARD) {
                            DashboardScreen(onLogout = authViewModel::logout)
                        }
                    }
                }
            }
        }
    }
}
