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
}

/**
 * Composable principal de navegación.
 */
@Composable
fun LoteriaProbabilidadApp() {
    val navController = rememberNavController()
    
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
                onCambiarMetodo = { viewModel.cambiarMetodo(it) }
            )
        }
    }
}
