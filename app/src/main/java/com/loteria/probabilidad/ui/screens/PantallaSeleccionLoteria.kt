package com.loteria.probabilidad.ui.screens

import android.app.Activity
import android.content.Intent
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loteria.probabilidad.data.model.TipoLoteria
import com.loteria.probabilidad.ui.components.DisclaimerCard
import com.loteria.probabilidad.ui.components.LoteriaButton
import com.loteria.probabilidad.ui.components.LoteriaButtonConPrediccion
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

    // ViewModel para predicciones
    val viewModel: SeleccionViewModel = viewModel(
        factory = SeleccionViewModel.Factory(context)
    )
    val predicciones by viewModel.predicciones.collectAsState()
    val cargandoPredicciones by viewModel.cargando.collectAsState()

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

            // Helper para crear botones con predicción
            @Composable
            fun BotonLoteria(tipo: TipoLoteria) {
                val pred = predicciones[tipo]
                LoteriaButtonConPrediccion(
                    tipoLoteria = tipo,
                    onClick = { onLoteriaSeleccionada(tipo) },
                    numerosPredichos = pred?.numerosPredichos ?: emptyList(),
                    mejorMetodo = pred?.mejorMetodo?.displayName ?: "",
                    tasaAcierto = pred?.tasaAcierto ?: 0.0,
                    proximoDia = pred?.proximoDiaSorteo ?: "",
                    complementario = pred?.complementarioPredicho,
                    complementario2 = pred?.complementarioPredicho2,
                    cargando = cargandoPredicciones,
                    ultimoSorteoNumeros = pred?.ultimoSorteoNumeros ?: emptyList(),
                    ultimoSorteoFecha = pred?.ultimoSorteoFecha ?: "",
                    ultimoSorteoComp1 = pred?.ultimoSorteoComplementario,
                    ultimoSorteoComp2 = pred?.ultimoSorteoComplementario2,
                    metodoMejorAcierto = pred?.metodoMejorAcierto?.displayName ?: "",
                    aciertosDelMejorMetodo = pred?.aciertosDelMejorMetodo ?: 0,
                    numerosAcertados = pred?.numerosAcertadosMejorMetodo ?: emptyList()
                )
            }

            // PRIMITIVA
            BotonLoteria(TipoLoteria.PRIMITIVA)

            // BONOLOTO
            BotonLoteria(TipoLoteria.BONOLOTO)

            // EUROMILLONES
            BotonLoteria(TipoLoteria.EUROMILLONES)

            // GORDO PRIMITIVA
            BotonLoteria(TipoLoteria.GORDO_PRIMITIVA)

            // Sección: Sorteos especiales
            Text(
                text = "Sorteos especiales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )

            // LOTERIA NACIONAL
            BotonLoteria(TipoLoteria.LOTERIA_NACIONAL)

            // NAVIDAD
            BotonLoteria(TipoLoteria.NAVIDAD)

            // NIÑO
            BotonLoteria(TipoLoteria.NINO)
            
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
                                mensajeDescarga = "🔄 Conectando a GitHub (HCTop)..."
                                
                                val baseUrl = "https://raw.githubusercontent.com/HCTop/LoteriaProbabilidad/master/app/src/main/res/raw"
                                
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
                                var descargadosGitHub = 0
                                var totalLineas = 0
                                var ultimaFecha = ""
                                var primeraFecha = ""
                                var usandoLocal = false
                                
                                withContext(Dispatchers.IO) {
                                    var errores = mutableListOf<String>()
                                    
                                    for (csvFile in csvFiles) {
                                        withContext(Dispatchers.Main) {
                                            mensajeDescarga = "⬇️ $csvFile..."
                                        }
                                        
                                        var content: String? = null
                                        var desdeGitHub = false
                                        
                                        // Intentar descargar desde GitHub
                                        try {
                                            val urlStr = "$baseUrl/$csvFile"
                                            val url = URL(urlStr)
                                            val connection = url.openConnection() as java.net.HttpURLConnection
                                            connection.connectTimeout = 15000
                                            connection.readTimeout = 60000
                                            connection.setRequestProperty("User-Agent", "LoteriaProbabilidad-App")
                                            connection.setRequestProperty("Accept", "text/plain,text/csv,*/*")
                                            
                                            val responseCode = connection.responseCode
                                            if (responseCode == 200) {
                                                val inputStream = connection.inputStream
                                                content = inputStream.bufferedReader().readText()
                                                inputStream.close()
                                                desdeGitHub = true
                                                
                                                // Guardar en archivos internos
                                                val outputFile = File(context.filesDir, csvFile)
                                                outputFile.writeText(content)
                                            } else {
                                                errores.add("$csvFile: HTTP $responseCode")
                                            }
                                            connection.disconnect()
                                            
                                        } catch (e: Exception) {
                                            errores.add("$csvFile: ${e.message?.take(50) ?: "Error"}")
                                            
                                            // Fallback: Intentar leer archivo ya descargado previamente
                                            val existingFile = File(context.filesDir, csvFile)
                                            if (existingFile.exists()) {
                                                try {
                                                    content = existingFile.readText()
                                                    desdeGitHub = false  // Ya estaba descargado
                                                } catch (_: Exception) {}
                                            }
                                            
                                            // Fallback final: recurso local
                                            if (content == null) {
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
                                        }
                                        
                                        // Contar líneas y extraer fechas
                                        content?.let { txt ->
                                            val lines = txt.lines()
                                            totalLineas += lines.size - 1
                                            descargados++
                                            if (desdeGitHub) descargadosGitHub++
                                            
                                            // Extraer fechas de Primitiva
                                            if (csvFile == "historico_primitiva.csv" && lines.size > 1) {
                                                ultimaFecha = lines[1].split(",").firstOrNull() ?: ""
                                                primeraFecha = lines.filter { it.isNotBlank() }.lastOrNull()?.split(",")?.firstOrNull() ?: ""
                                            }
                                        }
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        val fuente = when {
                                            descargadosGitHub == csvFiles.size -> " (GitHub ✓)"
                                            descargadosGitHub > 0 -> " (GitHub parcial)"
                                            usandoLocal -> " (local)"
                                            else -> ""
                                        }
                                        mensajeDescarga = if (descargados == csvFiles.size) {
                                            "✅ $descargados archivos$fuente\n📊 $totalLineas sorteos\n📅 $primeraFecha → $ultimaFecha"
                                        } else if (descargados > 0) {
                                            "⚠️ $descargados/${csvFiles.size} archivos$fuente\n📊 $totalLineas sorteos"
                                        } else {
                                            "❌ Sin conexión\n${errores.take(2).joinToString("\n")}"
                                        }

                                        // Si se descargaron archivos, recargar las predicciones
                                        if (descargados > 0) {
                                            mensajeDescarga = mensajeDescarga + "\n🔄 Recalculando predicciones..."
                                        }
                                    }
                                }

                                // Recargar predicciones con los nuevos datos
                                if (descargados > 0) {
                                    viewModel.cargarPredicciones()
                                    withContext(Dispatchers.Main) {
                                        mensajeDescarga = mensajeDescarga?.replace("\n🔄 Recalculando predicciones...", "\n✨ Predicciones actualizadas")
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
            
            // Botón de cerrar sesión
            TextButton(
                onClick = {
                    // Cerrar sesión
                    val prefs = context.getSharedPreferences("loteria_auth", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("autenticado", false).apply()
                    
                    // Reiniciar la actividad para volver al login
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    (context as? Activity)?.finish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "🔒 Cerrar sesión",
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
