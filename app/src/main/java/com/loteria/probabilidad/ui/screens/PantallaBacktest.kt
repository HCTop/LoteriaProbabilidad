package com.loteria.probabilidad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
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
import com.loteria.probabilidad.domain.ml.ResumenIA
import com.loteria.probabilidad.domain.ml.BacktestPersistencia
import com.loteria.probabilidad.service.AprendizajeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val calculador = remember { CalculadorProbabilidad(context) }
    val memoriaIA = remember { MemoriaIA(context) }
    val persistencia = remember { BacktestPersistencia(context) }
    
    // Calcular tamaño del histórico de forma segura - NUNCA puede ser negativo
    val tamanoHistorico = maxOf(0, when (tipoLoteria) {
        TipoLoteria.PRIMITIVA -> historicoPrimitiva.size
        TipoLoteria.BONOLOTO -> historicoBonoloto.size
        TipoLoteria.EUROMILLONES -> historicoEuromillones.size
        TipoLoteria.GORDO_PRIMITIVA -> historicoGordo.size
        TipoLoteria.LOTERIA_NACIONAL -> historicoNacional.size
        TipoLoteria.NAVIDAD -> historicoNavidad.size
        TipoLoteria.NINO -> historicoNino.size
    })
    
    // ============ CÁLCULO 100% SEGURO PARA SLIDER ============
    // REGLA: minDias SIEMPRE <= maxDias para evitar crash en Slider
    // Si no hay datos suficientes, ambos serán iguales (slider deshabilitado)
    val minDias: Int
    val maxDias: Int
    
    if (tamanoHistorico < 5) {
        // Datos insuficientes - usar valores fijos seguros
        minDias = 2
        maxDias = 2
    } else {
        // Datos suficientes - calcular rango normal
        minDias =10
        maxDias = maxOf(minDias, minOf(tamanoHistorico - 2, 500))
    }
    
    // Valor inicial: SIEMPRE dentro del rango [minDias, maxDias]
    val valorInicial = maxOf(minDias.toFloat(), minOf((maxDias * 0.2f), maxDias.toFloat()))
    // =========================================================
    
    // Cargar resultados anteriores
    var resultados by remember { 
        mutableStateOf(persistencia.obtenerResultados(tipoLoteria.name))
    }
    var diasAtras by remember { mutableStateOf(valorInicial) }
    var ejecutando by remember { mutableStateOf(false) }
    var ejecutado by remember { mutableStateOf(resultados.isNotEmpty()) }
    var aprendiendo by remember { mutableStateOf(false) }
    var resumenIA by remember { mutableStateOf(memoriaIA.obtenerResumenIA(tipoLoteria.name)) }
    val scope = rememberCoroutineScope()
    
    // Fecha del último entrenamiento
    val fechaUltimoEntrenamiento = remember { persistencia.obtenerFechaUltimoEntrenamiento(tipoLoteria.name) }
    
    // Sistema de LOGS persistente
    var logs by remember { mutableStateOf(persistencia.obtenerLogs(tipoLoteria.name)) }
    
    fun addLog(mensaje: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val nuevoLog = "[$timestamp] $mensaje"
        logs = logs + nuevoLog
        // Guardar en persistencia
        persistencia.agregarLog(tipoLoteria.name, mensaje)
    }
    
    // Estado ANTES del aprendizaje (para comparar)
    var entrenamientosAntes by remember { mutableStateOf(0) }
    var mejorPuntuacionAntes by remember { mutableStateOf(0.0) }
    var pesosAntes by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("🧪 Backtesting - ${tipoLoteria.displayName}")
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
           /* // Explicación
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "¿Qué es el Backtesting?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Simula cómo habrían funcionado los diferentes métodos de predicción " +
                            "en sorteos REALES del pasado. Retrocedemos N sorteos, generamos predicciones " +
                            "con el histórico de ese momento, y comparamos con el resultado real.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "⚠️ Recuerda: Ningún método puede predecir la lotería. " +
                            "Esto es solo análisis estadístico.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }*/
            
            // Configuración
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Configuración",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Sorteos a probar: ${diasAtras.toInt()}")
                        
                        // Solo mostrar slider si hay rango válido
                        if (maxDias > minDias) {
                            Slider(
                                value = diasAtras,
                                onValueChange = { diasAtras = it },
                                valueRange = minDias.toFloat()..maxDias.toFloat(),
                                steps = ((maxDias - minDias) / 5).coerceAtLeast(0),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                "⚠️ Datos insuficientes (solo $tamanoHistorico sorteos)",
                                color = Color(0xFFFF6600),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Text(
                            "Más sorteos = resultado más fiable pero más lento",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                // NO borrar logs - se acumulan durante el día
                                addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                addLog("🚀 Iniciando backtesting para ${tipoLoteria.displayName}")
                                addLog("📊 Sorteos a probar: ${diasAtras.toInt()}")
                                
                                // Guardar estado ANTES (específico de esta lotería)
                                entrenamientosAntes = memoriaIA.obtenerTotalEntrenamientos(tipoLoteria.name)
                                mejorPuntuacionAntes = memoriaIA.obtenerMejorPuntuacion(tipoLoteria.name)
                                pesosAntes = memoriaIA.obtenerPesosCaracteristicas(tipoLoteria.name)
                                addLog("📝 Estado ANTES - Entrenamientos: $entrenamientosAntes, Mejor: $mejorPuntuacionAntes")
                                
                                ejecutando = true
                                scope.launch {
                                    // 1. Ejecutar backtesting
                                    addLog("⏳ Ejecutando backtesting...")
                                    resultados = withContext(Dispatchers.Default) {
                                        when (tipoLoteria) {
                                            TipoLoteria.PRIMITIVA -> 
                                                calculador.ejecutarBacktestPrimitiva(historicoPrimitiva, diasAtras.toInt())
                                            TipoLoteria.BONOLOTO -> 
                                                calculador.ejecutarBacktestPrimitiva(historicoBonoloto, diasAtras.toInt())
                                            TipoLoteria.EUROMILLONES -> 
                                                calculador.ejecutarBacktestEuromillones(historicoEuromillones, diasAtras.toInt())
                                            TipoLoteria.GORDO_PRIMITIVA -> 
                                                calculador.ejecutarBacktestGordo(historicoGordo, diasAtras.toInt())
                                            TipoLoteria.LOTERIA_NACIONAL -> 
                                                calculador.ejecutarBacktestNacional(historicoNacional, diasAtras.toInt())
                                            TipoLoteria.NAVIDAD -> 
                                                calculador.ejecutarBacktestNavidad(historicoNavidad, diasAtras.toInt())
                                            TipoLoteria.NINO -> 
                                                calculador.ejecutarBacktestNacional(historicoNino, diasAtras.toInt())
                                        }
                                    }
                                    ejecutando = false
                                    ejecutado = true
                                    addLog("✅ Backtesting completado - ${resultados.size} métodos evaluados")
                                    
                                    // Guardar resultados en persistencia
                                    persistencia.guardarResultados(tipoLoteria.name, resultados)
                                    
                                    // Mostrar resultado de IA
                                    val resultadoIA = resultados.find { it.metodo == MetodoCalculo.IA_GENETICA }
                                    if (resultadoIA != null) {
                                        val totalConAciertos = resultadoIA.aciertos1 + resultadoIA.aciertos2 + 
                                                               resultadoIA.aciertos3 + resultadoIA.aciertos4
                                        addLog("🤖 IA_GENETICA: $totalConAciertos sorteos con aciertos, mejor=${resultadoIA.mejorAcierto}, ${resultadoIA.puntuacionTotal} pts")
                                    }
                                    val mejorMetodo = resultados.maxByOrNull { it.puntuacionTotal }
                                    if (mejorMetodo != null) {
                                        addLog("🏆 Mejor método: ${mejorMetodo.metodo.name} con ${mejorMetodo.puntuacionTotal} pts")
                                    }
                                    
                                    // 2. APRENDER de los resultados (TODAS las loterías)
                                    aprendiendo = true
                                    addLog("🧠 Iniciando aprendizaje...")
                                    
                                    withContext(Dispatchers.Default) {
                                        // Convertir histórico al formato común para aprendizaje
                                        val historicoComun: List<ResultadoPrimitiva> = when (tipoLoteria) {
                                            TipoLoteria.PRIMITIVA -> historicoPrimitiva
                                            TipoLoteria.BONOLOTO -> historicoBonoloto
                                            TipoLoteria.EUROMILLONES -> historicoEuromillones.map { 
                                                ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) 
                                            }
                                            TipoLoteria.GORDO_PRIMITIVA -> historicoGordo.map { 
                                                ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) 
                                            }
                                            // Para Nacional/Navidad/Niño: usar TERMINACIONES como números
                                            // Esto permite que la IA aprenda patrones de terminaciones (0-99)
                                            TipoLoteria.LOTERIA_NACIONAL -> historicoNacional.map { nac ->
                                                val num = nac.primerPremio.filter { it.isDigit() }.takeLast(5).padStart(5, '0')
                                                val terminaciones = listOf(
                                                    num.takeLast(2).toIntOrNull() ?: 0,        // Terminación 2 cifras (0-99)
                                                    (num.takeLast(1).toIntOrNull() ?: 0) + 1,  // Última cifra (1-10)
                                                    ((num.takeLast(2).toIntOrNull() ?: 0) / 10) + 11, // Decena (11-20)
                                                    (num.takeLast(3).toIntOrNull()?.rem(100) ?: 0) + 21, // Centena mod (21-120)
                                                    (num.toIntOrNull()?.rem(50) ?: 0) + 1,    // Mod 50 (1-50)
                                                    nac.reintegros.firstOrNull()?.plus(41) ?: 41 // Reintegro (41-50)
                                                ).map { it.coerceIn(1, 99) }
                                                ResultadoPrimitiva(nac.fecha, terminaciones, 0, nac.reintegros.firstOrNull() ?: 0)
                                            }
                                            TipoLoteria.NAVIDAD -> historicoNavidad.map { nav ->
                                                val num = nav.gordo.filter { it.isDigit() }.takeLast(5).padStart(5, '0')
                                                val terminaciones = listOf(
                                                    num.takeLast(2).toIntOrNull() ?: 0,
                                                    (num.takeLast(1).toIntOrNull() ?: 0) + 1,
                                                    ((num.takeLast(2).toIntOrNull() ?: 0) / 10) + 11,
                                                    (num.takeLast(3).toIntOrNull()?.rem(100) ?: 0) + 21,
                                                    (num.toIntOrNull()?.rem(50) ?: 0) + 1,
                                                    nav.reintegros.firstOrNull()?.plus(41) ?: 41
                                                ).map { it.coerceIn(1, 99) }
                                                ResultadoPrimitiva(nav.fecha, terminaciones, 0, nav.reintegros.firstOrNull() ?: 0)
                                            }
                                            TipoLoteria.NINO -> historicoNino.map { nino ->
                                                val num = nino.primerPremio.filter { it.isDigit() }.takeLast(5).padStart(5, '0')
                                                val terminaciones = listOf(
                                                    num.takeLast(2).toIntOrNull() ?: 0,
                                                    (num.takeLast(1).toIntOrNull() ?: 0) + 1,
                                                    ((num.takeLast(2).toIntOrNull() ?: 0) / 10) + 11,
                                                    (num.takeLast(3).toIntOrNull()?.rem(100) ?: 0) + 21,
                                                    (num.toIntOrNull()?.rem(50) ?: 0) + 1,
                                                    nino.reintegros.firstOrNull()?.plus(41) ?: 41
                                                ).map { it.coerceIn(1, 99) }
                                                ResultadoPrimitiva(nino.fecha, terminaciones, 0, nino.reintegros.firstOrNull() ?: 0)
                                            }
                                        }
                                        addLog("📚 Histórico preparado: ${historicoComun.size} sorteos")
                                        
                                        if (historicoComun.isNotEmpty()) {
                                            calculador.aprenderDeBacktest(
                                                resultados = resultados,
                                                historico = historicoComun,
                                                tipoLoteria = tipoLoteria.name,
                                                sorteosProbados = diasAtras.toInt()
                                            )
                                            addLog("✅ Aprendizaje ejecutado para ${tipoLoteria.displayName}")
                                        } else {
                                            addLog("⚠️ Histórico vacío - No se puede aprender")
                                        }
                                    }
                                    
                                    // Verificar estado DESPUÉS (específico de esta lotería)
                                    resumenIA = memoriaIA.obtenerResumenIA(tipoLoteria.name)
                                    val entrenamientosDespues = memoriaIA.obtenerTotalEntrenamientos(tipoLoteria.name)
                                    val mejorPuntuacionDespues = memoriaIA.obtenerMejorPuntuacion(tipoLoteria.name)
                                    val pesosDespues = memoriaIA.obtenerPesosCaracteristicas(tipoLoteria.name)
                                    
                                    addLog("📝 Estado DESPUÉS - Entrenamientos: $entrenamientosDespues, Mejor: $mejorPuntuacionDespues")
                                    
                                    // Verificar si realmente cambió
                                    if (entrenamientosDespues > entrenamientosAntes) {
                                        addLog("✅ ¡APRENDIZAJE EXITOSO! Entrenamientos: $entrenamientosAntes → $entrenamientosDespues")
                                    } else {
                                        addLog("❌ ERROR: Contador no aumentó ($entrenamientosAntes → $entrenamientosDespues)")
                                    }
                                    
                                    // Mostrar cambios en pesos
                                    val cambiosPesos = pesosDespues.filter { (k, v) ->
                                        val antes = pesosAntes[k] ?: 0.0
                                        kotlin.math.abs(v - antes) > 0.001
                                    }
                                    if (cambiosPesos.isNotEmpty()) {
                                        addLog("📊 Pesos modificados:")
                                        cambiosPesos.forEach { (k, v) ->
                                            val antes = pesosAntes[k] ?: 0.0
                                            addLog("   $k: ${(antes*100).toInt()}% → ${(v*100).toInt()}%")
                                        }
                                    }
                                    
                                    aprendiendo = false
                                }
                            },
                            enabled = !ejecutando && !aprendiendo,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (ejecutando) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ejecutando backtesting...")
                            } else if (aprendiendo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🧠 Aprendiendo...")
                            } else {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ejecutar Backtesting + Aprender")
                            }
                        }
                        
                        // Sección de aprendizaje en segundo plano (TODAS las loterías)
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            "🌙 Aprendizaje en Segundo Plano",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Ejecuta múltiples iteraciones incluso con la pantalla apagada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            var iteraciones by remember { mutableStateOf(50f) }
                            // Verificar si hay servicio activo para ESTA lotería
                            var servicioActivoParaEsta by remember { mutableStateOf(AprendizajeService.isRunningFor(tipoLoteria.name)) }
                            var servicioActivoOtra by remember { mutableStateOf(AprendizajeService.isRunning && !AprendizajeService.isRunningFor(tipoLoteria.name)) }
                            var ultimoProgresoLogueado by remember { mutableStateOf(-1) }
                            
                            // Actualizar estado del servicio periódicamente
                            LaunchedEffect(Unit) {
                                while(true) {
                                    delay(2000) // Cada 2 segundos
                                    servicioActivoParaEsta = AprendizajeService.isRunningFor(tipoLoteria.name)
                                    servicioActivoOtra = AprendizajeService.isRunning && !servicioActivoParaEsta
                                    if (servicioActivoParaEsta) {
                                        val progresoActual = AprendizajeService.progreso
                                        // Solo logear si el progreso cambió en al menos 5%
                                        if (progresoActual >= ultimoProgresoLogueado + 5 || progresoActual == 100) {
                                            ultimoProgresoLogueado = progresoActual
                                            addLog("🔄 ${progresoActual}% - It. ${AprendizajeService.iteracionActual}/${AprendizajeService.totalIteraciones}")
                                        }
                                    } else {
                                        ultimoProgresoLogueado = -1
                                    }
                                }
                            }
                            
                            // Mostrar advertencia si hay servicio para OTRA lotería
                            if (servicioActivoOtra) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFFFE0B2)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("⚠️ ", fontSize = 20.sp)
                                        Column {
                                            Text(
                                                "Aprendizaje activo para ${AprendizajeService.tipoLoteriaActual}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                "Espera a que termine o detenlo",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            if (servicioActivoParaEsta) {
                                // Mostrar progreso del servicio PARA ESTA lotería
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "🧠 Aprendiendo ${tipoLoteria.displayName}...",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = AprendizajeService.progreso / 100f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Iteración ${AprendizajeService.iteracionActual} de ${AprendizajeService.totalIteraciones} (${AprendizajeService.progreso}%)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Lotería: ${AprendizajeService.tipoLoteriaActual}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                AprendizajeService.stopLearning(context)
                                                addLog("⏹️ Aprendizaje detenido manualmente")
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("⏹️ Detener")
                                        }
                                    }
                                }
                            } else if (!servicioActivoOtra) {
                                // Controles para iniciar
                                Text("Iteraciones: ${iteraciones.toInt()}")
                                Slider(
                                    value = iteraciones,
                                    onValueChange = { iteraciones = it },
                                    valueRange = 10f..500f,
                                    steps = 48,  // 10, 20, 30... hasta 500
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Button(
                                    onClick = {
                                        AprendizajeService.startLearning(
                                            context = context,
                                            tipoLoteria = tipoLoteria.name,
                                            sorteos = diasAtras.toInt(),
                                            iteraciones = iteraciones.toInt()
                                        )
                                        addLog("🚀 Iniciando ${iteraciones.toInt()} iteraciones en segundo plano")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Icon(Icons.Default.Schedule, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Iniciar ${iteraciones.toInt()} iteraciones (segundo plano)")
                                }
                                
                                Text(
                                    "💡 Puedes apagar la pantalla y el aprendizaje continuará",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                    }
                }
            }



          //-----------------------------------------------------------------------------------------------
            // ==================== PANEL DE DEBUG (SIEMPRE VISIBLE) ====================
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A2E)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = Color(0xFF00FF00),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "🔍 DEBUG LOG",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00FF00)
                                )
                                Text(
                                    " (${logs.size})",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(
                                onClick = {
                                    logs = listOf()
                                    persistencia.limpiarLogs(tipoLoteria.name)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Limpiar",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Mostrar fecha del último entrenamiento si existe
                        if (fechaUltimoEntrenamiento.isNotEmpty()) {
                            Text(
                                "📅 Último entrenamiento: $fechaUltimoEntrenamiento",
                                fontSize = 10.sp,
                                color = Color(0xFF888888),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Mostrar logs con SCROLL y altura fija
                        val scrollState = rememberScrollState()

                        // Auto-scroll al final cuando hay nuevos logs
                        LaunchedEffect(logs.size) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp) // Altura aumentada para ver más logs
                                .background(Color(0xFF0D0D1A), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            if (logs.isEmpty()) {
                                // Mensaje cuando no hay logs
                                Text(
                                    "Sin actividad registrada.\nEjecuta un backtesting para ver el log.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF666666),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                ) {
                                    logs.forEach { log ->
                                        val color = when {
                                            log.contains("✅") -> Color(0xFF00FF00)
                                            log.contains("❌") || log.contains("ERROR") -> Color(0xFFFF4444)
                                            log.contains("⚠️") -> Color(0xFFFFAA00)
                                            log.contains("🚀") || log.contains("🧠") -> Color(0xFF00AAFF)
                                            log.contains("📊") || log.contains("📝") -> Color(0xFFAAAAFF)
                                            log.contains("🤖") || log.contains("🏆") || log.contains("🔄") -> Color(0xFFFFD700)
                                            else -> Color(0xFFCCCCCC)
                                        }
                                        Text(
                                            text = log,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = color,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Resumen del estado actual de la memoria
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color(0xFF333333))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "📦 MEMORIA DE ${tipoLoteria.name}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00AAFF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Obtener estado específico de esta lotería
                        val estadoActual = memoriaIA.obtenerResumenIA(tipoLoteria.name)
                        Text(
                            "Nivel: ${estadoActual.nombreNivel}",
                            color = Color(0xFFCCCCCC),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Text(
                            "Entrenamientos: ${estadoActual.totalEntrenamientos}",
                            color = Color(0xFFCCCCCC),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Text(
                            "Mejor puntuación: ${estadoActual.mejorPuntuacion}",
                            color = Color(0xFFCCCCCC),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Text(
                            "Última actualización: ${estadoActual.ultimaActualizacion}",
                            color = Color(0xFFCCCCCC),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Pesos aprendidos para ${tipoLoteria.displayName}:",
                            color = Color(0xFF00AAFF),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        estadoActual.pesosCaracteristicas.entries
                            .sortedByDescending { it.value }
                            .forEach { (car, peso) ->
                                val barLength = (peso * 20).toInt()
                                val bar = "█".repeat(barLength) + "░".repeat(20 - barLength)
                                Text(
                                    "${car.padEnd(12)}: $bar ${(peso*100).toInt()}%",
                                    color = Color(0xFFAAFFAA),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }

                        // Botón para resetear memoria
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            OutlinedButton(
                                onClick = {
                                    memoriaIA.reiniciarMemoria(tipoLoteria.name)
                                    resumenIA = memoriaIA.obtenerResumenIA(tipoLoteria.name)
                                    addLog("🗑️ Memoria de ${tipoLoteria.name} reiniciada")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFF6666)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("🗑️ Resetear ${tipoLoteria.displayName}", fontSize = 10.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = {
                                    resumenIA = memoriaIA.obtenerResumenIA(tipoLoteria.name)
                                    addLog("🔄 Estado actualizado")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF66FF66)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("🔄 Refrescar", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }




            // Resultados
            if (ejecutado && resultados.isNotEmpty()) {
                item {
                    Text(
                        "📊 Resultados (ordenados por rendimiento)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(resultados) { resultado ->
                    ResultadoBacktestCard(
                        resultado = resultado,
                        posicion = resultados.indexOf(resultado) + 1
                    )
                }
                
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "📈 Leyenda de puntuación",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("• 1 acierto = 1 punto", style = MaterialTheme.typography.bodySmall)
                            Text("• 2 aciertos = 3 puntos", style = MaterialTheme.typography.bodySmall)
                            Text("• 3 aciertos = 10 puntos", style = MaterialTheme.typography.bodySmall)
                            Text("• 4+ aciertos = 50 puntos", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Puntuación = (puntos totales / combinaciones probadas) × 100",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            

        }
    }
}

@Composable
fun ResultadoBacktestCard(
    resultado: ResultadoBacktest,
    posicion: Int
) {
    val colorFondo = when (posicion) {
        1 -> Color(0xFFFFD700).copy(alpha = 0.2f) // Oro
        2 -> Color(0xFFC0C0C0).copy(alpha = 0.2f) // Plata
        3 -> Color(0xFFCD7F32).copy(alpha = 0.2f) // Bronce
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val medalla = when (posicion) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#$posicion"
    }
    
    // Calcular total según tipo de lotería
    val totalBasico = resultado.aciertos0 + resultado.aciertos1 + 
                      resultado.aciertos2 + resultado.aciertos3 + resultado.aciertos4
    val totalCombinaciones = if (resultado.aciertos5 > 0 || resultado.aciertos6 > 0) {
        totalBasico + resultado.aciertos5 + resultado.aciertos6
    } else {
        totalBasico
    }
    
    // Determinar tipo de lotería por los campos usados
    val esPrimitiva = resultado.tipoLoteria in listOf("PRIMITIVA", "BONOLOTO")
    val esEuromillones = resultado.tipoLoteria == "EUROMILLONES"
    val esGordo = resultado.tipoLoteria == "GORDO_PRIMITIVA"
    val esNacional = resultado.tipoLoteria in listOf("LOTERIA_NACIONAL", "NAVIDAD", "NINO")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorFondo)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = medalla,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = resultado.metodo.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "${resultado.puntuacionTotal} pts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (posicion) {
                        1 -> Color(0xFFB8860B)
                        2 -> Color(0xFF808080)
                        3 -> Color(0xFF8B4513)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Título explicativo
            Text(
                text = "Distribución de aciertos ($totalCombinaciones combinaciones probadas):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Primera fila: aciertos básicos (0-4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EstadisticaAciertoMejorada("0 ✗", resultado.aciertos0, totalCombinaciones, Color.Gray)
                EstadisticaAciertoMejorada("1 ✓", resultado.aciertos1, totalCombinaciones, Color(0xFF2196F3))
                EstadisticaAciertoMejorada("2 ✓", resultado.aciertos2, totalCombinaciones, Color(0xFF4CAF50))
                EstadisticaAciertoMejorada("3 ✓", resultado.aciertos3, totalCombinaciones, Color(0xFFFF9800))
                EstadisticaAciertoMejorada("4 ✓", resultado.aciertos4, totalCombinaciones, Color(0xFFF44336))
            }
            
            // Segunda fila: aciertos extendidos según tipo de lotería
            if (esPrimitiva && (resultado.aciertos5 > 0 || resultado.aciertos6 > 0 || 
                resultado.aciertosComplementario > 0 || resultado.aciertosReintegro > 0)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EstadisticaAciertoMejorada("5 ✓", resultado.aciertos5, totalCombinaciones, Color(0xFFE91E63))
                    EstadisticaAciertoMejorada("6 🎯", resultado.aciertos6, totalCombinaciones, Color(0xFF9C27B0))
                    EstadisticaAciertoMejorada("+C", resultado.aciertosComplementario, totalCombinaciones, Color(0xFF00BCD4))
                    EstadisticaAciertoMejorada("+R", resultado.aciertosReintegro, totalCombinaciones, Color(0xFF8BC34A))
                }
            }
            
            if (esEuromillones && (resultado.aciertos5 > 0 || 
                resultado.aciertosEstrella1 > 0 || resultado.aciertosEstrella2 > 0)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EstadisticaAciertoMejorada("5 🎯", resultado.aciertos5, totalCombinaciones, Color(0xFF9C27B0))
                    EstadisticaAciertoMejorada("+⭐", resultado.aciertosEstrella1, totalCombinaciones, Color(0xFFFFD700))
                    EstadisticaAciertoMejorada("+⭐⭐", resultado.aciertosEstrella2, totalCombinaciones, Color(0xFFFF9800))
                }
            }
            
            if (esGordo && (resultado.aciertos5 > 0 || resultado.aciertosClave > 0)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EstadisticaAciertoMejorada("5 🎯", resultado.aciertos5, totalCombinaciones, Color(0xFF9C27B0))
                    EstadisticaAciertoMejorada("+K", resultado.aciertosClave, totalCombinaciones, Color(0xFFFFD700))
                }
            }
            
            if (esNacional && resultado.aciertosReintegro > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    EstadisticaAciertoMejorada("+R", resultado.aciertosReintegro, totalCombinaciones, Color(0xFF8BC34A))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Divider()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Media: ${resultado.promedioAciertos} aciertos/comb.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Máximo: ${resultado.mejorAcierto} números",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun EstadisticaAciertoMejorada(
    label: String,
    cantidad: Int,
    total: Int,
    color: Color
) {
    val porcentaje = if (total > 0) (cantidad * 100.0 / total) else 0.0
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Número de aciertos
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = cantidad.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = "${String.format("%.0f", porcentaje)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
    }
}
