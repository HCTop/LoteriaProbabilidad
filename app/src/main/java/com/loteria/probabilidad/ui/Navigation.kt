package com.loteria.probabilidad.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.loteria.probabilidad.data.model.TipoLoteria
import com.loteria.probabilidad.ui.screens.*

/**
 * Rutas de navegación de la aplicación.
 */
sealed class Screen(val route: String) {
    data object Seleccion : Screen("seleccion")
    data object Resultados : Screen("resultados/{tipoLoteria}") {
        fun createRoute(tipoLoteria: TipoLoteria) = "resultados/${tipoLoteria.name}"
    }
    data object Backtest : Screen("backtest/{tipoLoteria}") {
        fun createRoute(tipoLoteria: TipoLoteria) = "backtest/${tipoLoteria.name}"
    }
}

/**
 * Composable principal de navegación.
 */
@Composable
fun LoteriaProbabilidadApp(
    navegarABacktesting: Boolean = false,
    tipoLoteriaBacktesting: String? = null,
    onBacktestingNavegado: () -> Unit = {}
) {
    val navController = rememberNavController()
    
    // Navegación automática a backtesting desde notificación
    LaunchedEffect(navegarABacktesting, tipoLoteriaBacktesting) {
        if (navegarABacktesting && tipoLoteriaBacktesting != null) {
            try {
                val tipoLoteria = TipoLoteria.valueOf(tipoLoteriaBacktesting)
                // Ir a resultados primero y luego a backtesting
                navController.navigate(Screen.Resultados.createRoute(tipoLoteria)) {
                    popUpTo(Screen.Seleccion.route)
                }
                navController.navigate(Screen.Backtest.createRoute(tipoLoteria))
                onBacktestingNavegado()
            } catch (e: Exception) {
                // Si falla, ignorar
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Seleccion.route
    ) {
        // Pantalla de selección de lotería
        composable(Screen.Seleccion.route) {
            PantallaSeleccionLoteria(
                onLoteriaSeleccionada = { tipoLoteria ->
                    navController.navigate(Screen.Resultados.createRoute(tipoLoteria))
                }
            )
        }
        
        // Pantalla de resultados
        composable(
            route = Screen.Resultados.route,
            arguments = listOf(
                navArgument("tipoLoteria") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tipoLoteriaName = backStackEntry.arguments?.getString("tipoLoteria")
            val tipoLoteria = tipoLoteriaName?.let { 
                TipoLoteria.valueOf(it) 
            } ?: TipoLoteria.PRIMITIVA
            
            val context = LocalContext.current
            val viewModel: ResultadosViewModel = viewModel(
                factory = ResultadosViewModel.Factory(context)
            )
            
            // Cargar resultados cuando se muestra la pantalla
            LaunchedEffect(tipoLoteria) {
                viewModel.cargarResultados(tipoLoteria)
            }
            
            val uiState by viewModel.uiState.collectAsState()
            val rangoFechas by viewModel.rangoFechasSeleccionado.collectAsState()
            val metodo by viewModel.metodoSeleccionado.collectAsState()
            
            PantallaResultados(
                tipoLoteria = tipoLoteria,
                uiState = uiState,
                rangoFechasSeleccionado = rangoFechas,
                metodoSeleccionado = metodo,
                onBackClick = { navController.popBackStack() },
                onRefresh = { viewModel.regenerarResultados() },
                onCambiarRangoFechas = { viewModel.cambiarRangoFechas(it) },
                onCambiarMetodo = { viewModel.cambiarMetodo(it) },
                onBacktestClick = { 
                    navController.navigate(Screen.Backtest.createRoute(tipoLoteria))
                }
            )
        }
        
        // Pantalla de Backtesting
        composable(
            route = Screen.Backtest.route,
            arguments = listOf(
                navArgument("tipoLoteria") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tipoLoteriaName = backStackEntry.arguments?.getString("tipoLoteria")
            val tipoLoteria = tipoLoteriaName?.let { 
                TipoLoteria.valueOf(it) 
            } ?: TipoLoteria.PRIMITIVA
            
            val context = LocalContext.current
            val viewModel: ResultadosViewModel = viewModel(
                factory = ResultadosViewModel.Factory(context)
            )
            
            // Cargar datos
            LaunchedEffect(tipoLoteria) {
                viewModel.cargarResultados(tipoLoteria)
            }
            
            val uiState by viewModel.uiState.collectAsState()
            
            // Extraer históricos del ViewModel
            val historicoPrimitiva = remember(uiState) { viewModel.getHistoricoPrimitiva() }
            val historicoBonoloto = remember(uiState) { viewModel.getHistoricoBonoloto() }
            val historicoEuromillones = remember(uiState) { viewModel.getHistoricoEuromillones() }
            val historicoGordo = remember(uiState) { viewModel.getHistoricoGordo() }
            val historicoNacional = remember(uiState) { viewModel.getHistoricoNacional() }
            val historicoNavidad = remember(uiState) { viewModel.getHistoricoNavidad() }
            val historicoNino = remember(uiState) { viewModel.getHistoricoNino() }
            
            PantallaBacktest(
                tipoLoteria = tipoLoteria,
                historicoPrimitiva = historicoPrimitiva,
                historicoBonoloto = historicoBonoloto,
                historicoEuromillones = historicoEuromillones,
                historicoGordo = historicoGordo,
                historicoNacional = historicoNacional,
                historicoNavidad = historicoNavidad,
                historicoNino = historicoNino,
                onVolver = { navController.popBackStack() }
            )
        }
    }
}

// Helper class para múltiples valores (ya no se usa pero se mantiene por compatibilidad)
data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
