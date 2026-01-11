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
            
            // Botón de actualización de CSV desde GitHub
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
                        "Pulsa para descargar los últimos datos desde GitHub. " +
                        "Se actualizan automáticamente cada noche.",
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
                                   else if (mensaje.contains("⬇️")) MaterialTheme.colorScheme.tertiary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // Botón único para descargar y verificar
                    Button(
                        onClick = {
                            scope.launch {
                                descargando = true
                                mensajeDescarga = "🔄 Conectando..."
                                
                                // URL base de GitHub - CAMBIAR POR TU USUARIO
                                val baseUrl = "https://raw.githubusercontent.com/TU_USUARIO/LoteriaProbabilidad/main/app/src/main/res/raw"
                                
                                val csvFiles = listOf(
                                    "historico_primitiva.csv",
                                    "historico_bonoloto.csv",
                                    "historico_euromillones.csv",
                                    "historico_gordo_primitiva.csv",
                                    "historico_loteria_nacional.csv",
                                    "historico_navidad.csv",
                                    "historico_nino.csv"
                                )
                                
                                var descargados = 0
                                var totalLineas = 0
                                var ultimaFecha = ""
                                var primeraFecha = ""
                                var usandoLocal = false
                                
                                withContext(Dispatchers.IO) {
                                    for (csvFile in csvFiles) {
                                        withContext(Dispatchers.Main) {
                                            mensajeDescarga = "⬇️ $csvFile..."
                                        }
                                        
                                        var content: String? = null
                                        
                                        // Intentar descargar desde GitHub
                                        try {
                                            val url = URL("$baseUrl/$csvFile")
                                            val connection = url.openConnection()
                                            connection.connectTimeout = 10000
                                            connection.readTimeout = 30000
                                            connection.setRequestProperty("User-Agent", "LoteriaProbabilidad-App")
                                            
                                            val inputStream = connection.getInputStream()
                                            content = inputStream.bufferedReader().readText()
                                            inputStream.close()
                                            
                                            // Guardar en archivos internos
                                            val outputFile = File(context.filesDir, csvFile)
                                            outputFile.writeText(content)
                                            
                                        } catch (e: Exception) {
                                            // Fallback a recurso local
                                            usandoLocal = true
                                            try {
                                                val resourceName = csvFile.replace(".csv", "")
                                                val resId = context.resources.getIdentifier(
                                                    resourceName, "raw", context.packageName
                                                )
                                                if (resId != 0) {
                                                    val localStream = context.resources.openRawResource(resId)
                                                    content = localStream.bufferedReader().readText()
                                                    localStream.close()
                                                }
                                            } catch (localError: Exception) {
                                                // Ignorar
                                            }
                                        }
                                        
                                        // Contar líneas y extraer fechas
                                        content?.let { txt ->
                                            val lines = txt.lines()
                                            totalLineas += lines.size - 1
                                            descargados++
                                            
                                            // Extraer fechas de Primitiva
                                            if (csvFile == "historico_primitiva.csv" && lines.size > 1) {
                                                ultimaFecha = lines[1].split(",").firstOrNull() ?: ""
                                                primeraFecha = lines.filter { it.isNotBlank() }.lastOrNull()?.split(",")?.firstOrNull() ?: ""
                                            }
                                        }
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        val fuente = if (usandoLocal) " (local)" else " (GitHub)"
                                        mensajeDescarga = if (descargados == csvFiles.size) {
                                            "✅ $descargados archivos$fuente\n📊 $totalLineas sorteos\n📅 $primeraFecha → $ultimaFecha"
                                        } else if (descargados > 0) {
                                            "⚠️ $descargados/${csvFiles.size} archivos\n📊 $totalLineas sorteos"
                                        } else {
                                            "❌ Sin conexión\nComprueba tu red"
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
                            Text("Descargando...")
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🔄 Actualizar datos históricos")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
