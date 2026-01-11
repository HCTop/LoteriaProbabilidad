package com.loteria.probabilidad.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.loteria.probabilidad.MainActivity
import com.loteria.probabilidad.R
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad
import com.loteria.probabilidad.domain.ml.MemoriaIA
import kotlinx.coroutines.*

/**
 * Servicio que ejecuta el aprendizaje de IA en segundo plano.
 * Permite que el backtesting continúe incluso con la pantalla apagada.
 */
class AprendizajeService : Service() {
    
    companion object {
        const val CHANNEL_ID = "aprendizaje_ia_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.loteria.probabilidad.START_LEARNING"
        const val ACTION_STOP = "com.loteria.probabilidad.STOP_LEARNING"
        
        const val EXTRA_TIPO_LOTERIA = "tipo_loteria"
        const val EXTRA_SORTEOS = "sorteos"
        const val EXTRA_ITERACIONES = "iteraciones"
        const val EXTRA_OPEN_BACKTESTING = "open_backtesting"
        
        // Estado compartido para la UI
        var isRunning = false
        var progreso = 0
        var iteracionActual = 0
        var totalIteraciones = 0
        var ultimoLog = ""
        var entrenamientosCompletados = 0
        var tipoLoteriaActual = ""  // Lotería que se está procesando AHORA
        var tipoLoteriaIniciada = "" // Lotería que inició el proceso (para verificación)
        
        // Verificar si hay un servicio corriendo para una lotería específica
        fun isRunningFor(tipoLoteria: String): Boolean {
            return isRunning && tipoLoteriaActual == tipoLoteria
        }
        
        fun startLearning(
            context: Context,
            tipoLoteria: String,
            sorteos: Int,
            iteraciones: Int
        ) {
            // IMPORTANTE: Guardar la lotería que inició el proceso
            tipoLoteriaActual = tipoLoteria
            tipoLoteriaIniciada = tipoLoteria
            val intent = Intent(context, AprendizajeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TIPO_LOTERIA, tipoLoteria)
                putExtra(EXTRA_SORTEOS, sorteos)
                putExtra(EXTRA_ITERACIONES, iteraciones)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopLearning(context: Context) {
            val intent = Intent(context, AprendizajeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val tipoLoteria = intent.getStringExtra(EXTRA_TIPO_LOTERIA) ?: "PRIMITIVA"
                val sorteos = intent.getIntExtra(EXTRA_SORTEOS, 100)
                val iteraciones = intent.getIntExtra(EXTRA_ITERACIONES, 10)
                
                startForeground(NOTIFICATION_ID, createNotification("Iniciando aprendizaje...", 0))
                acquireWakeLock()
                startLearningProcess(tipoLoteria, sorteos, iteraciones)
            }
            ACTION_STOP -> {
                stopLearningProcess()
            }
        }
        return START_NOT_STICKY
    }
    
    private fun startLearningProcess(tipoLoteria: String, sorteos: Int, iteraciones: Int) {
        isRunning = true
        progreso = 0
        iteracionActual = 0
        totalIteraciones = iteraciones
        entrenamientosCompletados = 0
        
        job = scope.launch {
            try {
                val calculador = CalculadorProbabilidad(this@AprendizajeService)
                val memoriaIA = MemoriaIA(this@AprendizajeService)
                
                // Cargar histórico UNA SOLA VEZ (optimización)
                val dataSource = com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource(this@AprendizajeService)
                
                // Pre-cargar datos según tipo
                val historicoPrecargado: Any = when (tipoLoteria) {
                    "PRIMITIVA" -> dataSource.leerHistoricoPrimitiva(TipoLoteria.PRIMITIVA)
                    "BONOLOTO" -> dataSource.leerHistoricoPrimitiva(TipoLoteria.BONOLOTO)
                    "EUROMILLONES" -> dataSource.leerHistoricoEuromillones()
                    "GORDO_PRIMITIVA" -> dataSource.leerHistoricoGordoPrimitiva()
                    "LOTERIA_NACIONAL" -> dataSource.leerHistoricoNacional(TipoLoteria.LOTERIA_NACIONAL)
                    "NAVIDAD" -> dataSource.leerHistoricoNavidad()
                    "NINO" -> dataSource.leerHistoricoNacional(TipoLoteria.NINO)
                    else -> emptyList<Any>()
                }
                
                for (i in 1..iteraciones) {
                    if (!isRunning) break
                    
                    iteracionActual = i
                    progreso = ((i.toFloat() / iteraciones) * 100).toInt()
                    ultimoLog = "Iteración $i de $iteraciones para $tipoLoteria"
                    
                    // Actualizar notificación cada 5 iteraciones para no sobrecargar
                    if (i % 5 == 1 || i == iteraciones) {
                        updateNotification("Aprendiendo $tipoLoteria: $i/$iteraciones", progreso)
                    }
                    
                    // Ejecutar backtesting según tipo (usando datos precargados)
                    val resultados = when (tipoLoteria) {
                        "PRIMITIVA" -> {
                            @Suppress("UNCHECKED_CAST")
                            val historico = historicoPrecargado as List<ResultadoPrimitiva>
                            val results = calculador.ejecutarBacktestPrimitiva(historico, sorteos)
                            calculador.aprenderDeBacktest(results, historico, tipoLoteria, sorteos)
                            results
                        }
                        "BONOLOTO" -> {
                            @Suppress("UNCHECKED_CAST")
                            val historico = historicoPrecargado as List<ResultadoPrimitiva>
                            val results = calculador.ejecutarBacktestPrimitiva(historico, sorteos)
                            calculador.aprenderDeBacktest(results, historico, tipoLoteria, sorteos)
                            results
                        }
                        "EUROMILLONES" -> {
                            @Suppress("UNCHECKED_CAST")
                            val historico = historicoPrecargado as List<ResultadoEuromillones>
                            val results = calculador.ejecutarBacktestEuromillones(historico, sorteos)
                            val historicoComun = historico.map { euro -> ResultadoPrimitiva(euro.fecha, euro.numeros, 0, 0) }
                            calculador.aprenderDeBacktest(results, historicoComun, tipoLoteria, sorteos)
                            results
                        }
                        "GORDO_PRIMITIVA" -> {
                            @Suppress("UNCHECKED_CAST")
                            val historico = historicoPrecargado as List<ResultadoGordoPrimitiva>
                            val results = calculador.ejecutarBacktestGordo(historico, sorteos)
                            val historicoComun = historico.map { gordo -> ResultadoPrimitiva(gordo.fecha, gordo.numeros, 0, 0) }
                            calculador.aprenderDeBacktest(results, historicoComun, tipoLoteria, sorteos)
                            results
                        }
                        "LOTERIA_NACIONAL" -> {
                            @Suppress("UNCHECKED_CAST")
                            val historico = historicoPrecargado as List<ResultadoNacional>
                            val results = calculador.ejecutarBacktestNacional(historico, sorteos)
                            // Convertir a formato para aprendizaje usando TERMINACIONES
                            val historicoComun = historico.map { nac -> 
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
                            calculador.aprenderDeBacktest(results, historicoComun, tipoLoteria, sorteos)
                            results
                        }
                        "NAVIDAD" -> {
                            @Suppress("UNCHECKED_CAST")
                            val historico = historicoPrecargado as List<ResultadoNavidad>
                            val results = calculador.ejecutarBacktestNavidad(historico, sorteos)
                            val historicoComun = historico.map { nav -> 
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
                            calculador.aprenderDeBacktest(results, historicoComun, tipoLoteria, sorteos)
                            results
                        }
                        "NINO" -> {
                            @Suppress("UNCHECKED_CAST")
                            val historico = historicoPrecargado as List<ResultadoNacional>
                            val results = calculador.ejecutarBacktestNacional(historico, sorteos, "NINO")
                            val historicoComun = historico.map { nino -> 
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
                            calculador.aprenderDeBacktest(results, historicoComun, tipoLoteria, sorteos)
                            results
                        }
                        else -> emptyList()
                    }
                    
                    if (resultados.isNotEmpty()) {
                        entrenamientosCompletados++
                        val resumen = memoriaIA.obtenerResumenIA(tipoLoteria)
                        ultimoLog = "✅ It.$i - ${resumen.nombreNivel}"
                    }
                    
                    // Pausa mínima para no bloquear (50ms en lugar de 300ms = 6x más rápido)
                    delay(50)
                }
                
                ultimoLog = "🎉 ¡Completado! $entrenamientosCompletados iteraciones"
                updateNotification("✅ Aprendizaje completado", 100)
                
            } catch (e: Exception) {
                ultimoLog = "❌ Error: ${e.message}"
                updateNotification("❌ Error", progreso)
            } finally {
                delay(3000) // Mostrar resultado final por 3 segundos
                stopLearningProcess()
            }
        }
    }
    
    private fun stopLearningProcess() {
        isRunning = false
        job?.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aprendizaje de IA",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones del proceso de aprendizaje de IA"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(text: String, progress: Int): Notification {
        val stopIntent = Intent(this, AprendizajeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Título con porcentaje visible
        val titulo = if (progress in 1..99) {
            "🧠 Aprendizaje IA: $progress%"
        } else if (progress >= 100) {
            "✅ Aprendizaje completado"
        } else {
            "🧠 Aprendizaje de IA"
        }
        
        // Subtítulo con más info
        val subtitulo = if (iteracionActual > 0 && totalIteraciones > 0) {
            "It. $iteracionActual/$totalIteraciones - $text"
        } else {
            text
        }
        
        // Intent para abrir la app en backtesting al clicar
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_BACKTESTING, true)
            putExtra(EXTRA_TIPO_LOTERIA, tipoLoteriaActual)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titulo)
            .setContentText(subtitulo)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(progress < 100)
            .setProgress(100, progress, false)
            .setContentIntent(openPendingIntent)  // Al clicar abre backtesting
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }
    
    private fun updateNotification(text: String, progress: Int) {
        val notification = createNotification(text, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LoteriaProbabilidad::AprendizajeWakeLock"
        ).apply {
            acquire(60 * 60 * 1000L) // Máximo 1 hora
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        job?.cancel()
        releaseWakeLock()
        scope.cancel()
    }
}
