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
import com.loteria.probabilidad.data.datasource.ActualizadorHistorico
import com.loteria.probabilidad.service.AprendizajeService
import com.loteria.probabilidad.ui.LoteriaProbabilidadApp
import com.loteria.probabilidad.ui.screens.PantallaLogin
import com.loteria.probabilidad.ui.theme.LoteriaProbabilidadTheme
import kotlinx.coroutines.launch

/**
 * Actividad principal de la aplicación.
 */
class MainActivity : ComponentActivity() {
    
    // Estado para navegar directamente a backtesting
    private val navegarABacktesting = mutableStateOf(false)
    private val tipoLoteriaBacktesting = mutableStateOf<String?>(null)
    
    // Estado de autenticación
    private var estaAutenticado by mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Verificar si viene de la notificación
        handleIntent(intent)
        
        // Verificar autenticación previa
        val prefs = getSharedPreferences("loteria_auth", Context.MODE_PRIVATE)
        estaAutenticado = prefs.getBoolean("autenticado", false)

        // Auto-actualizar históricos desde GitHub en background (silencioso, cada 24h)
        if (estaAutenticado) {
            actualizarHistoricosEnBackground()
        }
        
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
                                actualizarHistoricosEnBackground()
                            }
                        )
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun actualizarHistoricosEnBackground() {
        lifecycleScope.launch {
            val actualizador = ActualizadorHistorico(this@MainActivity)
            if (actualizador.necesitaActualizar()) {
                actualizador.actualizar()
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AprendizajeService.EXTRA_OPEN_BACKTESTING, false) == true) {
            navegarABacktesting.value = true
            tipoLoteriaBacktesting.value = intent.getStringExtra(AprendizajeService.EXTRA_TIPO_LOTERIA)
        }
    }
}
