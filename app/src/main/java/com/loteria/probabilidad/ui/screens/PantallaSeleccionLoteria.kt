package com.loteria.probabilidad.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loteria.probabilidad.data.model.TipoLoteria
import com.loteria.probabilidad.ui.components.DisclaimerCard
import com.loteria.probabilidad.ui.components.LoteriaButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaSeleccionLoteria(
    onLoteriaSeleccionada: (TipoLoteria) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var descargando by remember { mutableStateOf(false) }
    var mensajeDescarga by remember { mutableStateOf<String?>(null) }
    
    // URL base de GitHub donde el bot sube los CSV reales
    val githubBaseUrl = "https://raw.githubusercontent.com/HCTop/LoteriaProbabilidad/master/app/src/main/res/raw/"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Casino, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lotería Probabilidad", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DisclaimerCard()
            
            Text("Loterías disponibles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            // Grid de Loterías
            TipoLoteria.values().forEach { loteria ->
                LoteriaButton(
                    tipoLoteria = loteria,
                    onClick = { onLoteriaSeleccionada(loteria) }
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))

            // Panel de Sincronización Real
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✅ Sincronización Real Activa", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Descarga los últimos sorteos reales de Lotoideas y Google Sheets.", 
                        style = MaterialTheme.typography.bodySmall, 
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    mensajeDescarga?.let { mensaje ->
                        Text(
                            mensaje, 
                            color = if(mensaje.contains("✅")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall, 
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                descargando = true
                                mensajeDescarga = "Sincronizando con el servidor..."
                                withContext(Dispatchers.IO) {
                                    try {
                                        // Todos los archivos generados por el script de Python
                                        val files = listOf(
                                            "historico_primitiva.csv", 
                                            "historico_bonoloto.csv", 
                                            "historico_euromillones.csv", 
                                            "historico_gordo_primitiva.csv",
                                            "historico_loteria_nacional.csv",
                                            "historico_navidad.csv",
                                            "historico_nino.csv"
                                        )
                                        
                                        var exitos = 0
                                        files.forEach { name ->
                                            try {
                                                val content = URL(githubBaseUrl + name).readText()
                                                if (content.length > 50) { // Validar que no sea un 404 o archivo vacío
                                                    File(context.cacheDir, name).writeText(content)
                                                    exitos++
                                                }
                                            } catch (e: Exception) {
                                                println("Error descargando $name: ${e.message}")
                                            }
                                        }
                                        
                                        withContext(Dispatchers.Main) { 
                                            if (exitos > 0) mensajeDescarga = "✅ $exitos loterías actualizadas correctamente."
                                            else mensajeDescarga = "❌ Error: No se pudo obtener datos. Prueba más tarde."
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { mensajeDescarga = "❌ Error de conexión. Revisa tu internet." }
                                    }
                                }
                                descargando = false
                            }
                        },
                        enabled = !descargando,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (descargando) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.CloudDownload, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Actualizar datos reales ahora")
                        }
                    }
                }
            }
        }
    }
}
