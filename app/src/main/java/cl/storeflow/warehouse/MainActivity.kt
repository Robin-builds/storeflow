package cl.storeflow.warehouse

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
import cl.storeflow.warehouse.ui.auth.AuthUiState
import cl.storeflow.warehouse.ui.auth.AuthViewModel
import cl.storeflow.warehouse.ui.auth.LoginScreen
import cl.storeflow.warehouse.ui.auth.RegistroScreen
import cl.storeflow.warehouse.ui.alertas.AlertasScreen
import cl.storeflow.warehouse.ui.atributos.AtributosScreen
import cl.storeflow.warehouse.ui.bodegas.BodegasScreen
import cl.storeflow.warehouse.ui.configuracion.ConfiguracionScreen
import cl.storeflow.warehouse.ui.dashboard.DashboardScreen
import cl.storeflow.warehouse.ui.usuarios.UsuariosScreen
import cl.storeflow.warehouse.ui.movimientos.MovimientosScreen
import cl.storeflow.warehouse.ui.productos.ProductosListScreen
import cl.storeflow.warehouse.ui.reportar.ReportarErrorScreen
import cl.storeflow.warehouse.ui.theme.StoreFlowTheme
import cl.storeflow.warehouse.ui.theme.TemaViewModel
import dagger.hilt.android.AndroidEntryPoint

private object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val DASHBOARD = "dashboard"
    const val PRODUCTOS = "productos"
    const val ALERTAS = "alertas"
    const val BODEGAS = "bodegas"
    const val ATRIBUTOS = "atributos"
    const val USUARIOS = "usuarios"
    const val CONFIGURACION = "configuracion"
    const val MOVIMIENTOS = "movimientos/{productoId}"
    const val REPORTAR_ERROR = "reportar_error"
    fun movimientos(productoId: String) = "movimientos/$productoId"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val temaViewModel: TemaViewModel = hiltViewModel()
            val tema by temaViewModel.tema.collectAsState()

            StoreFlowTheme(tema = tema) {
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
                            is AuthUiState.SesionCerrada -> {
                                navController.navigate(Rutas.LOGIN) {
                                    popUpTo(0) { inclusive = true }
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
                            DashboardScreen(
                                onIrAConfiguracion = { navController.navigate(Rutas.CONFIGURACION) },
                                onIrAProductos = { navController.navigate(Rutas.PRODUCTOS) },
                                onIrAAlerta = { navController.navigate(Rutas.ALERTAS) },
                                onIrABodegas = { navController.navigate(Rutas.BODEGAS) },
                                onIrAAtributos = { navController.navigate(Rutas.ATRIBUTOS) },
                                onIrAUsuarios = { navController.navigate(Rutas.USUARIOS) }
                            )
                        }
                        composable(Rutas.CONFIGURACION) {
                            ConfiguracionScreen(
                                tema = tema,
                                onSetTema = temaViewModel::setTema,
                                onVolver = { navController.popBackStack() },
                                onLogout = authViewModel::logout,
                                onIrAReportarError = { navController.navigate(Rutas.REPORTAR_ERROR) }
                            )
                        }
                        composable(Rutas.REPORTAR_ERROR) {
                            ReportarErrorScreen(onVolver = { navController.popBackStack() })
                        }
                        composable(Rutas.ALERTAS) {
                            AlertasScreen(
                                onVolver = { navController.popBackStack() },
                                onVerMovimientos = { productoId ->
                                    navController.navigate(Rutas.movimientos(productoId))
                                }
                            )
                        }
                        composable(Rutas.PRODUCTOS) {
                            ProductosListScreen(
                                onVolver = { navController.popBackStack() },
                                onVerMovimientos = { productoId ->
                                    navController.navigate(Rutas.movimientos(productoId))
                                }
                            )
                        }
                        composable(Rutas.BODEGAS) {
                            BodegasScreen(
                                onVolver = { navController.popBackStack() },
                                onBodegaCambiada = {
                                    navController.navigate(Rutas.DASHBOARD) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Rutas.ATRIBUTOS) {
                            AtributosScreen(onVolver = { navController.popBackStack() })
                        }
                        composable(Rutas.USUARIOS) {
                            UsuariosScreen(onVolver = { navController.popBackStack() })
                        }
                        composable(Rutas.MOVIMIENTOS) {
                            MovimientosScreen(onVolver = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
