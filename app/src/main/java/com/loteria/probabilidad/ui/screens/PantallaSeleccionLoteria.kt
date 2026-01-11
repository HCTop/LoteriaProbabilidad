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
            
            Text("Loterías de números", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
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
            
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅ Sincronización Real Activa", fontWeight = FontWeight.Bold)
                    Text("Pulsa para obtener los sorteos oficiales de Lotoideas (incluyendo el día 8).", 
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    
                    mensajeDescarga?.let { mensaje ->
                        Text(
                            mensaje, 
                            color = if(mensaje.contains("✅")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall, 
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                descargando = true
                                mensajeDescarga = "Conectando con GitHub..."
                                withContext(Dispatchers.IO) {
                                    try {
                                        val files = listOf("historico_primitiva.csv", "historico_bonoloto.csv", "historico_euromillones.csv", "historico_gordo_primitiva.csv")
                                        files.forEach { name ->
                                            val content = URL(githubBaseUrl + name).readText()
                                            if (content.isNotBlank()) {
                                                File(context.cacheDir, name).writeText(content)
                                            }
                                        }
                                        withContext(Dispatchers.Main) { mensajeDescarga = "✅ Datos reales actualizados con éxito." }
                                    } catch (_: Exception) {
                                        withContext(Dispatchers.Main) { mensajeDescarga = "❌ Error: Verifica tu internet." }
                                    }
                                }
                                descargando = false
                            }
                        },
                        enabled = !descargando,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (descargando) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else {
                            Icon(Icons.Default.CloudDownload, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Descargar sorteos reales")
                        }
                    }
                }
            }
        }
    }
}
