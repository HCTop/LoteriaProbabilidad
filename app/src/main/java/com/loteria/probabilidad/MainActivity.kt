package com.loteria.probabilidad

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.loteria.probabilidad.service.AprendizajeService
import com.loteria.probabilidad.ui.LoteriaProbabilidadApp
import com.loteria.probabilidad.ui.theme.LoteriaProbabilidadTheme

/**
 * Actividad principal de la aplicación.
 */
class MainActivity : ComponentActivity() {
    
    // Estado para navegar directamente a backtesting
    private val navegarABacktesting = mutableStateOf(false)
    private val tipoLoteriaBacktesting = mutableStateOf<String?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Verificar si viene de la notificación
        handleIntent(intent)
        
        setContent {
            LoteriaProbabilidadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoteriaProbabilidadApp(
                        navegarABacktesting = navegarABacktesting.value,
                        tipoLoteriaBacktesting = tipoLoteriaBacktesting.value,
                        onBacktestingNavegado = { 
                            navegarABacktesting.value = false
                            tipoLoteriaBacktesting.value = null
                        }
                    )
                }
            }
        }
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
