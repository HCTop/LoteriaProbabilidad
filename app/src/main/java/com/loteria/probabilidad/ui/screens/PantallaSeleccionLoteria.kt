package com.loteria.probabilidad.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.io.FileOutputStream
import java.net.URL

/**
 * Pantalla principal con la selección de loterías.
 */
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
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lotería Probabilidad",
                            fontWeight = FontWeight.Bold
                        )
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Título y descripción
            Text(
                text = "Selecciona una lotería",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Obtén las 5 combinaciones más probables basadas en el análisis histórico",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Disclaimer
            DisclaimerCard()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sección: Loterías de números
            Text(
                text = "Loterías de números",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.PRIMITIVA,
                onClick = { onLoteriaSeleccionada(TipoLoteria.PRIMITIVA) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.BONOLOTO,
                onClick = { onLoteriaSeleccionada(TipoLoteria.BONOLOTO) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.EUROMILLONES,
                onClick = { onLoteriaSeleccionada(TipoLoteria.EUROMILLONES) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.GORDO_PRIMITIVA,
                onClick = { onLoteriaSeleccionada(TipoLoteria.GORDO_PRIMITIVA) }
            )
            
            // Sección: Sorteos especiales
            Text(
                text = "Sorteos especiales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.LOTERIA_NACIONAL,
                onClick = { onLoteriaSeleccionada(TipoLoteria.LOTERIA_NACIONAL) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.NAVIDAD,
                onClick = { onLoteriaSeleccionada(TipoLoteria.NAVIDAD) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.NINO,
                onClick = { onLoteriaSeleccionada(TipoLoteria.NINO) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Botón de descarga forzada de CSV
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "📊 Datos Históricos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        "Los datos se cargan automáticamente desde los recursos de la app. " +
                        "Si tienes problemas, puedes verificar los datos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    // Mostrar mensaje de estado
                    mensajeDescarga?.let { mensaje ->
                        Text(
                            mensaje,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (mensaje.contains("✅")) MaterialTheme.colorScheme.primary 
                                   else if (mensaje.contains("❌")) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                descargando = true
                                mensajeDescarga = "🔄 Verificando datos..."
                                
                                withContext(Dispatchers.IO) {
                                    try {
                                        // Verificar que los CSV existen en resources
                                        val csvFiles = listOf(
                                            "historico_primitiva.csv",
                                            "historico_bonoloto.csv",
                                            "historico_euromillones.csv",
                                            "historico_gordo_primitiva.csv",
                                            "historico_loteria_nacional.csv",
                                            "historico_navidad.csv",
                                            "historico_nino.csv"
                                        )
                                        
                                        var totalLineas = 0
                                        var archivosOk = 0
                                        
                                        for (csvFile in csvFiles) {
                                            try {
                                                val resourceName = csvFile.replace(".csv", "")
                                                val resId = context.resources.getIdentifier(
                                                    resourceName, "raw", context.packageName
                                                )
                                                if (resId != 0) {
                                                    val inputStream = context.resources.openRawResource(resId)
                                                    val lines = inputStream.bufferedReader().readLines().size
                                                    inputStream.close()
                                                    totalLineas += lines
                                                    archivosOk++
                                                }
                                            } catch (e: Exception) {
                                                // Archivo no encontrado
                                            }
                                        }
                                        
                                        withContext(Dispatchers.Main) {
                                            mensajeDescarga = if (archivosOk == csvFiles.size) {
                                                "✅ $archivosOk archivos verificados, $totalLineas sorteos totales"
                                            } else {
                                                "⚠️ $archivosOk/${csvFiles.size} archivos, $totalLineas sorteos"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            mensajeDescarga = "❌ Error: ${e.message}"
                                        }
                                    }
                                }
                                
                                descargando = false
                            }
                        },
                        enabled = !descargando,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (descargando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verificando...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verificar datos históricos")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
