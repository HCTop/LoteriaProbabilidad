package com.loteria.probabilidad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad
import com.loteria.probabilidad.domain.ml.MemoriaIA
import com.loteria.probabilidad.domain.ml.BacktestPersistencia
import com.loteria.probabilidad.service.AprendizajeService
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBacktest(
    tipoLoteria: TipoLoteria,
    historicoPrimitiva: List<ResultadoPrimitiva> = emptyList(),
    historicoBonoloto: List<ResultadoPrimitiva> = emptyList(),
    historicoEuromillones: List<ResultadoEuromillones> = emptyList(),
    historicoGordo: List<ResultadoGordoPrimitiva> = emptyList(),
    historicoNacional: List<ResultadoNacional> = emptyList(),
    historicoNavidad: List<ResultadoNavidad> = emptyList(),
    historicoNino: List<ResultadoNacional> = emptyList(),
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    val memoriaIA = remember { MemoriaIA(context) }
    val persistencia = remember { BacktestPersistencia(context) }
    
    // Tamaño del histórico
    val tamanoHistorico = maxOf(0, when (tipoLoteria) {
        TipoLoteria.PRIMITIVA -> historicoPrimitiva.size
        TipoLoteria.BONOLOTO -> historicoBonoloto.size
        TipoLoteria.EUROMILLONES -> historicoEuromillones.size
        TipoLoteria.GORDO_PRIMITIVA -> historicoGordo.size
        TipoLoteria.LOTERIA_NACIONAL -> historicoNacional.size
        TipoLoteria.NAVIDAD -> historicoNavidad.size
        TipoLoteria.NINO -> historicoNino.size
    })
    
    // Cálculo seguro para slider
    val minDias = 2
    val maxDias = if (tamanoHistorico < 5) 2 else maxOf(minDias, minOf(tamanoHistorico - 2, 500))
    val valorInicial = maxOf(minDias.toFloat(), minOf((maxDias * 0.2f), maxDias.toFloat()))
    
    // Estados
    var resultados by remember { mutableStateOf(persistencia.obtenerResultados(tipoLoteria.name)) }
    var diasAtras by remember { mutableStateOf(valorInicial) }
    var iteraciones by remember { mutableStateOf(50f) }
    var resumenIA by remember { mutableStateOf(memoriaIA.obtenerResumenIA(tipoLoteria.name)) }
    
    // Estado del servicio
    var servicioActivo by remember { mutableStateOf(AprendizajeService.isRunning) }
    var servicioParaEsta by remember { mutableStateOf(AprendizajeService.isRunningFor(tipoLoteria.name)) }
    var ultimoProgreso by remember { mutableStateOf(-1) }
    
    // Optimización de batería
    var bateriaOptimizada by remember { mutableStateOf(!AprendizajeService.isIgnoringBatteryOptimizations(context)) }
    
    // LOGS
    var logs by remember { mutableStateOf(persistencia.obtenerLogs(tipoLoteria.name)) }
    val logListState = rememberLazyListState()
    var ultimoMetodoLogueado by remember { mutableStateOf("") }
    var ultimaCombLogueada by remember { mutableStateOf(0) }
    
    fun addLog(mensaje: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs = logs + "[$timestamp] $mensaje"
        persistencia.agregarLog(tipoLoteria.name, mensaje)
    }
    
    // Actualizar estado del servicio
    LaunchedEffect(Unit) {
        while(true) {
            delay(1000)
            val nuevoParaEsta = AprendizajeService.isRunningFor(tipoLoteria.name)
            
            if (servicioParaEsta && !nuevoParaEsta) {
                addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                addLog("✅ APRENDIZAJE COMPLETADO")
                addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                
                // Recargar estado de la IA
                resumenIA = memoriaIA.obtenerResumenIA(tipoLoteria.name)
                resultados = persistencia.obtenerResultados(tipoLoteria.name)
                
                addLog("📈 RESULTADOS DEL APRENDIZAJE:")
                addLog("   • Nuevo nivel: ${resumenIA.nombreNivel}")
                addLog("   • Total entrenamientos: ${resumenIA.totalEntrenamientos}")
                addLog("   • Mejor puntuación: ${"%.2f".format(resumenIA.mejorPuntuacion)}")
                
                // Mostrar lo que ha aprendido
                if (resumenIA.pesosCaracteristicas.isNotEmpty()) {
                    addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    addLog("🧠 PESOS APRENDIDOS:")
                    resumenIA.pesosCaracteristicas.entries
                        .sortedByDescending { it.value }
                        .take(5)
                        .forEach { (nombre, peso) ->
                            addLog("   • $nombre: ${(peso * 100).toInt()}%")
                        }
                }
                
                if (resultados.isNotEmpty()) {
                    addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    addLog("🏆 MEJORES MÉTODOS:")
                    resultados.take(3).forEachIndexed { idx, r ->
                        addLog("   ${idx + 1}. ${r.metodo.displayName}: ${"%.2f".format(r.puntuacionTotal)} pts")
                    }
                }
                addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
            
            servicioActivo = AprendizajeService.isRunning
            servicioParaEsta = nuevoParaEsta
            bateriaOptimizada = !AprendizajeService.isIgnoringBatteryOptimizations(context)
            
            if (servicioParaEsta) {
                val progreso = AprendizajeService.progreso
                val iteracion = AprendizajeService.iteracionActual
                val total = AprendizajeService.totalIteraciones
                val mejorPunt = AprendizajeService.mejorPuntuacion
                val metodoActual = AprendizajeService.metodoActual
                val combActual = AprendizajeService.combinacionActual
                val combTotal = AprendizajeService.totalCombinaciones
                
                // Loguear progreso general cada 10%
                val debeLoguearProgreso = when {
                    progreso == 100 && ultimoProgreso == 100 -> false
                    progreso == 100 -> true
                    progreso >= ultimoProgreso + 10 -> true
                    else -> false
                }
                
                if (debeLoguearProgreso) {
                    ultimoProgreso = progreso
                    val logMsg = StringBuilder("🔄 $progreso% - It. $iteracion/$total")
                    if (mejorPunt > 0) {
                        logMsg.append(" | Mejor: ${"%.1f".format(mejorPunt)}")
                    }
                    addLog(logMsg.toString())
                }
                
                // Mostrar info de combinaciones si hay datos
                if (metodoActual.isNotEmpty() && combTotal > 0) {
                    // Loguear cuando cambie el método o cada 250 combinaciones
                    val cambioMetodo = metodoActual != ultimoMetodoLogueado
                    val avance250 = combActual >= ultimaCombLogueada + 250
                    
                    if (cambioMetodo || avance250) {
                        ultimoMetodoLogueado = metodoActual
                        ultimaCombLogueada = combActual
                        addLog("   📊 $metodoActual | Comb: $combActual/$combTotal")
                    }
                }
            } else {
                ultimoProgreso = -1
            }
        }
    }
    
    // Auto-scroll del log
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) logListState.animateScrollToItem(logs.size - 1)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("🧪 Backtesting - ${tipoLoteria.displayName}")
                        Text("$tamanoHistorico sorteos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==================== SECCIÓN 1: CONFIGURACIÓN ====================
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚙️ Configuración", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Sorteos a probar
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("📊 Sorteos a probar:")
                            Text("${diasAtras.toInt()}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        
                        if (maxDias > minDias) {
                            Slider(value = diasAtras, onValueChange = { diasAtras = it }, valueRange = minDias.toFloat()..maxDias.toFloat(), steps = ((maxDias - minDias) / 5).coerceAtLeast(0), modifier = Modifier.fillMaxWidth())
                        } else {
                            Text("⚠️ Datos insuficientes", color = Color(0xFFFF6600), style = MaterialTheme.typography.bodySmall)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Iteraciones
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("🧠 Iteraciones:")
                            Text("${iteraciones.toInt()}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                        
                        Slider(value = iteraciones, onValueChange = { iteraciones = it }, valueRange = 10f..300f, steps = 28, modifier = Modifier.fillMaxWidth())
                        
                        Text("Cada iteración ejecuta backtesting + aprendizaje", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // ========== AVISO BATERÍA ==========
                        if (bateriaOptimizada) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("⚠️ Optimización de batería activa", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                    Text("El proceso puede pausarse con la pantalla apagada.", fontSize = 12.sp, color = Color(0xFF5D4037))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = { AprendizajeService.requestIgnoreBatteryOptimizations(context) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("🔋 Desactivar para esta app")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        // ========== BOTÓN UNIFICADO ==========
                        if (servicioActivo) {
                            Card(colors = CardDefaults.cardColors(containerColor = if (servicioParaEsta) MaterialTheme.colorScheme.primaryContainer else Color(0xFFFFE0B2))) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (servicioParaEsta) {
                                        Text("🧠 Aprendiendo ${tipoLoteria.displayName}...", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(progress = AprendizajeService.progreso / 100f, modifier = Modifier.fillMaxWidth())
                                        Text("It. ${AprendizajeService.iteracionActual}/${AprendizajeService.totalIteraciones} (${AprendizajeService.progreso}%)", style = MaterialTheme.typography.bodySmall)
                                        if (AprendizajeService.mejorPuntuacion > 0) {
                                            Text("📈 Mejor puntuación: ${"%.2f".format(AprendizajeService.mejorPuntuacion)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                                        }
                                    } else {
                                        Text("⚠️ Aprendizaje activo: ${AprendizajeService.tipoLoteriaActual}", fontWeight = FontWeight.Bold)
                                        Text("Espera o detén el proceso actual", style = MaterialTheme.typography.bodySmall)
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { AprendizajeService.stopLearning(context); addLog("⏹️ Detenido") }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Stop, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("⏹️ Detener")
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    // Calcular combinaciones totales: 9 métodos x sorteos x iteraciones
                                    val totalCombinaciones = 9 * diasAtras.toInt() * iteraciones.toInt()
                                    
                                    addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    addLog("🚀 INICIANDO APRENDIZAJE")
                                    addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    addLog("📊 Lotería: ${tipoLoteria.displayName}")
                                    addLog("📊 Histórico disponible: $tamanoHistorico sorteos")
                                    addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    addLog("⚙️ CONFIGURACIÓN:")
                                    addLog("   • Iteraciones: ${iteraciones.toInt()}")
                                    addLog("   • Sorteos por iteración: ${diasAtras.toInt()}")
                                    addLog("   • Métodos de cálculo: 9")
                                    addLog("   • Combinaciones totales: $totalCombinaciones")
                                    addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    addLog("📝 Estado actual de la IA:")
                                    addLog("   • Nivel: ${resumenIA.nombreNivel}")
                                    addLog("   • Entrenamientos previos: ${resumenIA.totalEntrenamientos}")
                                    addLog("   • Mejor puntuación: ${"%.2f".format(resumenIA.mejorPuntuacion)}")
                                    addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    
                                    // Resetear contadores de log
                                    ultimoProgreso = -1
                                    ultimoMetodoLogueado = ""
                                    ultimaCombLogueada = 0
                                    
                                    AprendizajeService.startLearning(context, tipoLoteria.name, diasAtras.toInt(), iteraciones.toInt())
                                    addLog("✅ Servicio iniciado - Puedes cerrar la app")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = maxDias > minDias
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("▶️ Iniciar Backtesting + Aprendizaje")
                            }
                            
                            Text("💡 Funciona en segundo plano con la app cerrada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
            }
            
            // ==================== SECCIÓN 2: MEMORIA IA ====================
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E).copy(alpha = 0.1f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("🧠 MEMORIA IA", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { resumenIA = memoriaIA.obtenerResumenIA(tipoLoteria.name) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            }
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Nivel", fontSize = 10.sp, color = Color.Gray)
                                Text(resumenIA.nombreNivel, fontWeight = FontWeight.Bold, color = when(resumenIA.nombreNivel) { "Experto" -> Color(0xFFFFD700); "Avanzado" -> Color(0xFF4CAF50); "Intermedio" -> Color(0xFF2196F3); else -> Color.Gray })
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Entrenamientos", fontSize = 10.sp, color = Color.Gray)
                                Text("${resumenIA.totalEntrenamientos}", fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Mejor punt.", fontSize = 10.sp, color = Color.Gray)
                                Text("${"%.1f".format(resumenIA.mejorPuntuacion)}", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        if (resumenIA.pesosCaracteristicas.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Pesos aprendidos:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            resumenIA.pesosCaracteristicas.entries.sortedByDescending { it.value }.take(5).forEach { (nombre, peso) ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(nombre, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    LinearProgressIndicator(progress = peso.toFloat().coerceIn(0f, 1f), modifier = Modifier.width(80.dp).height(6.dp), color = Color(0xFF4CAF50))
                                    Text("${(peso * 100).toInt()}%", fontSize = 10.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { memoriaIA.reiniciarMemoria(tipoLoteria.name); resumenIA = memoriaIA.obtenerResumenIA(tipoLoteria.name); addLog("🗑️ Memoria reseteada") }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                            Text("🗑️ Resetear", fontSize = 12.sp)
                        }
                    }
                }
            }
            
            // ==================== SECCIÓN 3: LOG ====================
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D1A))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BugReport, null, tint = Color(0xFF00FF00), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📋 LOG", fontWeight = FontWeight.Bold, color = Color(0xFF00FF00))
                                Text(" (${logs.size})", fontSize = 10.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { logs = listOf(); persistencia.limpiarLogs(tipoLoteria.name) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF0A0A15), RoundedCornerShape(4.dp)).padding(8.dp)) {
                            if (logs.isEmpty()) {
                                Text("Inicia un backtesting para ver actividad", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center)
                            } else {
                                LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                                    items(logs) { log ->
                                        val color = when {
                                            log.contains("ERROR") || log.contains("❌") -> Color(0xFFFF5252)
                                            log.contains("✅") -> Color(0xFF4CAF50)
                                            log.contains("⚠️") -> Color(0xFFFFB74D)
                                            log.contains("🚀") -> Color(0xFF64B5F6)
                                            log.contains("🧠") -> Color(0xFFE040FB)
                                            log.contains("📊") -> Color(0xFF00BCD4)
                                            log.contains("🔄") -> Color(0xFFFFEB3B)
                                            log.contains("━") -> Color(0xFF444444)
                                            else -> Color(0xFFCCCCCC)
                                        }
                                        Text(log, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color, lineHeight = 14.sp, modifier = Modifier.padding(vertical = 1.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // ==================== SECCIÓN 4: RESULTADOS ====================
            if (resultados.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("📊 Resultados del Backtesting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Ordenados por puntuación", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                items(resultados) { resultado ->
                    ResultadoBacktestCard(resultado = resultado, posicion = resultados.indexOf(resultado) + 1, tipoLoteria = tipoLoteria)
                }
                
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("📈 Leyenda de puntuación", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val leyenda = when (tipoLoteria) {
                                TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO -> "1 cifra=2pt • 2 cifras=15pt • 3 cifras=100pt • 4 cifras=500pt • 5 cifras=2000pt • Reintegro=5pt"
                                else -> "1 acierto = 1pt • 2 = 3pt • 3 = 10pt • 4 = 50pt • 5+ = 500pt"
                            }
                            Text(leyenda, fontSize = 11.sp)
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(60.dp)) }
        }
    }
}

@Composable
fun ResultadoBacktestCard(resultado: ResultadoBacktest, posicion: Int, tipoLoteria: TipoLoteria) {
    val colorPosicion = when (posicion) { 1 -> Color(0xFFFFD700); 2 -> Color(0xFFC0C0C0); 3 -> Color(0xFFCD7F32); else -> MaterialTheme.colorScheme.primary }
    
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (posicion <= 3) colorPosicion.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#$posicion", fontWeight = FontWeight.Bold, color = colorPosicion, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(resultado.metodo.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                Text("${"%.2f".format(resultado.puntuacionTotal)} pts", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Mostrar aciertos con formato según tipo de lotería
            when (tipoLoteria) {
                TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO -> {
                    // Loterías de 5 cifras - MOSTRAR TODAS LAS CIFRAS
                    Column {
                        // Primera fila: cifras acertadas
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            AciertoChipConTick("0", resultado.aciertos0, Color.Gray, false)
                            AciertoChipConTick("1", resultado.aciertos1, Color(0xFF9E9E9E), resultado.aciertos1 > 0)
                            AciertoChipConTick("2", resultado.aciertos2, Color(0xFF8BC34A), resultado.aciertos2 > 0)
                            AciertoChipConTick("3", resultado.aciertos3, Color(0xFF4CAF50), resultado.aciertos3 > 0)
                            AciertoChipConTick("4", resultado.aciertos4, Color(0xFFFF9800), resultado.aciertos4 > 0)
                            AciertoChipConTick("5", resultado.aciertos5, Color(0xFFE91E63), resultado.aciertos5 > 0)
                        }
                        // Segunda fila: reintegro
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            AciertoChipConTick("🔄 Reintegro", resultado.aciertosReintegro, Color(0xFF2196F3), resultado.aciertosReintegro > 0)
                        }
                    }
                }
                TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> {
                    // Loterías de 6 números
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        AciertoChipConTick("0", resultado.aciertos0, Color.Gray, false)
                        AciertoChipConTick("1", resultado.aciertos1, Color(0xFF9E9E9E), resultado.aciertos1 > 0)
                        AciertoChipConTick("2", resultado.aciertos2, Color(0xFF8BC34A), resultado.aciertos2 > 0)
                        AciertoChipConTick("3", resultado.aciertos3, Color(0xFF4CAF50), resultado.aciertos3 > 0)
                        AciertoChipConTick("4", resultado.aciertos4, Color(0xFFFF9800), resultado.aciertos4 > 0)
                        AciertoChipConTick("5+", resultado.aciertos5, Color(0xFFE91E63), resultado.aciertos5 > 0)
                    }
                }
                TipoLoteria.EUROMILLONES -> {
                    // Euromillones: 5 números + 2 estrellas
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            AciertoChipConTick("0", resultado.aciertos0, Color.Gray, false)
                            AciertoChipConTick("1", resultado.aciertos1, Color(0xFF9E9E9E), resultado.aciertos1 > 0)
                            AciertoChipConTick("2", resultado.aciertos2, Color(0xFF8BC34A), resultado.aciertos2 > 0)
                            AciertoChipConTick("3", resultado.aciertos3, Color(0xFF4CAF50), resultado.aciertos3 > 0)
                            AciertoChipConTick("4", resultado.aciertos4, Color(0xFFFF9800), resultado.aciertos4 > 0)
                            AciertoChipConTick("5", resultado.aciertos5, Color(0xFFE91E63), resultado.aciertos5 > 0)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text("⭐", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            AciertoChipConTick("1⭐", resultado.aciertosEstrella1, Color(0xFFFFD700), resultado.aciertosEstrella1 > 0)
                            Spacer(modifier = Modifier.width(8.dp))
                            AciertoChipConTick("2⭐", resultado.aciertosEstrella2, Color(0xFFFF5722), resultado.aciertosEstrella2 > 0)
                        }
                    }
                }
                TipoLoteria.GORDO_PRIMITIVA -> {
                    // Gordo: 5 números + clave
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        AciertoChipConTick("0", resultado.aciertos0, Color.Gray, false)
                        AciertoChipConTick("1", resultado.aciertos1, Color(0xFF9E9E9E), resultado.aciertos1 > 0)
                        AciertoChipConTick("2", resultado.aciertos2, Color(0xFF8BC34A), resultado.aciertos2 > 0)
                        AciertoChipConTick("3", resultado.aciertos3, Color(0xFF4CAF50), resultado.aciertos3 > 0)
                        AciertoChipConTick("4", resultado.aciertos4, Color(0xFFFF9800), resultado.aciertos4 > 0)
                        AciertoChipConTick("5", resultado.aciertos5, Color(0xFFE91E63), resultado.aciertos5 > 0)
                    }
                }
            }
            
            Text("Mejor: ${resultado.mejorAcierto} | Promedio: ${resultado.promedioAciertos}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun AciertoChipConTick(label: String, count: Int, color: Color, mostrarTick: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = Color.Gray)
        Surface(color = if (count > 0) color.copy(alpha = 0.2f) else Color.Transparent, shape = RoundedCornerShape(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                if (mostrarTick) {
                    Text("✓", fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text("$count", fontSize = 12.sp, fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal, color = if (count > 0) color else Color.Gray)
            }
        }
    }
}
