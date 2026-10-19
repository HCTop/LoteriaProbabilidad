package com.loteria.probabilidad

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.loteria.probabilidad.service.AprendizajeContinuo
import com.loteria.probabilidad.service.AprendizajeService
import com.loteria.probabilidad.service.SincronizacionDiariaWorker
import com.loteria.probabilidad.ui.LoteriaProbabilidadApp
import com.loteria.probabilidad.ui.screens.PantallaLogin
import com.loteria.probabilidad.ui.theme.LoteriaProbabilidadTheme

/**
 * Actividad principal de la aplicación.
 */
class MainActivity : ComponentActivity() {
    
    // Estado para navegar directamente a backtesting
    private val navegarABacktesting = mutableStateOf(false)
    private val tipoLoteriaBacktesting = mutableStateOf<String?>(null)
    
    // Estado de autenticación
    private var estaAutenticado by mutableStateOf(false)

    // Aprendizaje continuo mientras el app está visible
    // lazy: en el constructor de la Activity todavía no hay Context adjunto
    private val aprendizajeContinuo by lazy { AprendizajeContinuo(applicationContext) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Verificar si viene de la notificación
        handleIntent(intent)
        
        // Verificar autenticación previa
        val prefs = getSharedPreferences("loteria_auth", Context.MODE_PRIVATE)
        estaAutenticado = prefs.getBoolean("autenticado", false)

        // Tarea diaria: descarga resultados, evalúa aciertos y guarda las predicciones
        // del próximo sorteo aunque no se abra la app (no se duplica si ya existe)
        SincronizacionDiariaWorker.programar(applicationContext)

        // Una sola vez: rehacer simulados y predicciones con los 11 métodos
        com.loteria.probabilidad.domain.usecase.GestorPredicciones.migrarSiHaceFalta(applicationContext)

        // Analizar en segundo plano los últimos 1.000 sorteos de cada lotería (solo la 1ª vez es
        // largo; luego solo comprueba). De ahí sale el método que más acierta y su jugada.
        // Va DESPUÉS de la migración, que puede borrar simulados antiguos.
        com.loteria.probabilidad.service.SimulacionHistoricaWorker.programar(applicationContext)

        // La descarga de históricos la hace ahora la pantalla de inicio (SeleccionViewModel)
        // ANTES de evaluar aciertos; aquí solo se duplicaba la descarga en paralelo.
        
        setContent {
            LoteriaProbabilidadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (estaAutenticado) {
                        LoteriaProbabilidadApp(
                            navegarABacktesting = navegarABacktesting.value,
                            tipoLoteriaBacktesting = tipoLoteriaBacktesting.value,
                            onBacktestingNavegado = {
                                navegarABacktesting.value = false
                                tipoLoteriaBacktesting.value = null
                            }
                        )
                    } else {
                        PantallaLogin(
                            onLoginExitoso = {
                                estaAutenticado = true
                                aprendizajeContinuo.iniciar(lifecycleScope)
                            }
                        )
                    }
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Iniciar aprendizaje continuo cuando el app es visible
        if (estaAutenticado) {
            aprendizajeContinuo.iniciar(lifecycleScope)
        }
    }

    override fun onStop() {
        super.onStop()
        // Detener cuando el app pasa a segundo plano
        aprendizajeContinuo.detener()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AprendizajeService.EXTRA_OPEN_BACKTESTING, false) == true) {
            navegarABacktesting.value = true
            tipoLoteriaBacktesting.value = intent.getStringExtra(AprendizajeService.EXTRA_TIPO_LOTERIA)
        }
    }
}
