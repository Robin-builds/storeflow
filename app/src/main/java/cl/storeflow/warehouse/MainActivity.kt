package cl.storeflow.warehouse

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

private val slideEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300))
}
private val slideExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(tween(200))
}
private val slidePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(tween(300))
}
private val slidePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(200))
}
private val fadeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(400))
}
private val fadeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(300))
}

private object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val DASHBOARD = "dashboard"
    const val PRODUCTOS = "productos"
    const val PRODUCTOS_PATTERN = "productos?busqueda={busqueda}"
    fun productosConBusqueda(query: String) = "productos?busqueda=${Uri.encode(query)}"
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
            val paletaSeleccionada by temaViewModel.paletaSeleccionada.collectAsState()
            val oscuridadSeleccionada by temaViewModel.oscuridadSeleccionada.collectAsState()

            StoreFlowTheme(
                paleta = paletaSeleccionada.paleta,
                oscuridad = oscuridadSeleccionada.oscuridad
            ) {
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
                        composable(
                            Rutas.LOGIN,
                            enterTransition = fadeEnter,
                            exitTransition = fadeExit,
                            popEnterTransition = fadeEnter,
                            popExitTransition = fadeExit
                        ) {
                            LoginScreen(
                                uiState = uiState,
                                onLogin = authViewModel::login,
                                onIrARegistro = { navController.navigate(Rutas.REGISTRO) },
                                onLimpiarError = authViewModel::limpiarError
                            )
                        }
                        composable(
                            Rutas.REGISTRO,
                            enterTransition = slideEnter,
                            exitTransition = slideExit,
                            popEnterTransition = slidePopEnter,
                            popExitTransition = slidePopExit
                        ) {
                            RegistroScreen(
                                uiState = uiState,
                                onRegistrar = authViewModel::registrar,
                                onIrALogin = { navController.popBackStack() },
                                onLimpiarError = authViewModel::limpiarError
                            )
                        }
                        composable(
                            Rutas.DASHBOARD,
                            enterTransition = fadeEnter,
                            exitTransition = fadeExit,
                            popEnterTransition = fadeEnter,
                            popExitTransition = fadeExit
                        ) {
                            DashboardScreen(
                                onIrAConfiguracion = { navController.navigate(Rutas.CONFIGURACION) },
                                onIrAProductos = { navController.navigate(Rutas.PRODUCTOS) },
                                onIrAAlerta = { navController.navigate(Rutas.ALERTAS) },
                                onIrABodegas = { navController.navigate(Rutas.BODEGAS) },
                                onIrAAtributos = { navController.navigate(Rutas.ATRIBUTOS) },
                                onIrAUsuarios = { navController.navigate(Rutas.USUARIOS) },
                                onIrAProductosConBusqueda = { query ->
                                    navController.navigate(Rutas.productosConBusqueda(query))
                                }
                            )
                        }
                        composable(
                            Rutas.CONFIGURACION,
                            enterTransition = slideEnter,
                            exitTransition = slideExit,
                            popEnterTransition = slidePopEnter,
                            popExitTransition = slidePopExit
                        ) {
                            ConfiguracionScreen(
                                paletaSeleccionada = paletaSeleccionada,
                                oscuridadSeleccionada = oscuridadSeleccionada,
                                onSetPaleta = temaViewModel::cambiarPaleta,
                                onSetOscuridad = temaViewModel::cambiarOscuridad,
                                onVolver = { navController.popBackStack() },
                                onLogout = authViewModel::logout,
                                onIrAReportarError = { navController.navigate(Rutas.REPORTAR_ERROR) }
                            )
                        }
                        composable(
                            Rutas.REPORTAR_ERROR,
                            enterTransition = slideEnter,
                            exitTransition = slideExit,
                            popEnterTransition = slidePopEnter,
                            popExitTransition = slidePopExit
                        ) {
                            ReportarErrorScreen(onVolver = { navController.popBackStack() })
                        }
                        composable(
                            Rutas.ALERTAS,
                            enterTransition = slideEnter,
                            exitTransition = slideExit,
                            popEnterTransition = slidePopEnter,
                            popExitTransition = slidePopExit
                        ) {
                            AlertasScreen(
                                onVolver = { navController.popBackStack() },
                                onVerMovimientos = { productoId ->
                                    navController.navigate(Rutas.movimientos(productoId))
                                }
                            )
                        }
                        composable(
                            Rutas.PRODUCTOS_PATTERN,
                            arguments = listOf(navArgument("busqueda") {
                                type = NavType.StringType
                                defaultValue = ""
                            }),
                            enterTransition = slideEnter,
                            exitTransition = slideExit,
                            popEnterTransition = slidePopEnter,
                            popExitTransition = slidePopExit
                        ) {
                            ProductosListScreen(
                                onVolver = { navController.popBackStack() },
                                onVerMovimientos = { productoId ->
                                    navController.navigate(Rutas.movimientos(productoId))
                                }
                            )
                        }
                        composable(
                            Rutas.BODEGAS,
                            enterTransition = slideEnter,
                            exitTransition = slideExit,
                            popEnterTransition = slidePopEnter,
                            popExitTransition = slidePopExit
                        ) {
                            BodegasScreen(
                                onVolver = { navController.popBackStack() },
                                onBodegaCambiada = {
                                    navController.navigate(Rutas.DASHBOARD) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            Rutas.ATRIBUTOS,
                            enterTransition = slideEnter,
                            exitTransition = slideExit,
                            popEnterTransition = slidePopEnter,
                            popExitTransition = slidePopExit
                        ) {
                            AtributosScreen(onVolver = { navController.popBackStack() })
                        }
                        composable(
                            Rutas.USUARIOS,
                            enterTransition = slideEnter,
                            exitTransition = slideExit,
                            popEnterTransition = slidePopEnter,
                            popExitTransition = slidePopExit
                        ) {
                            UsuariosScreen(onVolver = { navController.popBackStack() })
                        }
                        composable(
                            Rutas.MOVIMIENTOS,
                            enterTransition = slideEnter,
                            exitTransition = slideExit,
                            popEnterTransition = slidePopEnter,
                            popExitTransition = slidePopExit
                        ) {
                            MovimientosScreen(onVolver = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
