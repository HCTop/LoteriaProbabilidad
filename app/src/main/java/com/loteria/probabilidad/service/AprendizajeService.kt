package com.loteria.probabilidad.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.loteria.probabilidad.MainActivity
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad
import com.loteria.probabilidad.domain.ml.MemoriaIA
import kotlinx.coroutines.*

/**
 * Servicio de aprendizaje en segundo plano v3.
 * - Solicita desactivar optimización de batería
 * - WakeLock agresivo
 * - Notificación de máxima prioridad
 * - Continúa al cerrar la app
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
        
        @Volatile var isRunning = false
        @Volatile var progreso = 0
        @Volatile var iteracionActual = 0
        @Volatile var totalIteraciones = 0
        @Volatile var ultimoLog = ""
        @Volatile var entrenamientosCompletados = 0
        @Volatile var tipoLoteriaActual = ""
        @Volatile var mejorPuntuacion = 0.0
        @Volatile var ultimaActualizacion = System.currentTimeMillis()
        
        fun isRunningFor(tipoLoteria: String): Boolean {
            return isRunning && tipoLoteriaActual == tipoLoteria
        }
        
        /** Verifica si la app está exenta de optimización de batería */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        
        /** Solicita al usuario desactivar la optimización de batería */
        fun requestIgnoreBatteryOptimizations(context: Context) {
            if (!isIgnoringBatteryOptimizations(context)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (_: Exception) { }
                }
            }
        }
        
        fun startLearning(context: Context, tipoLoteria: String, sorteos: Int, iteraciones: Int) {
            // Resetear todas las variables al iniciar nuevo aprendizaje
            tipoLoteriaActual = tipoLoteria
            mejorPuntuacion = 0.0
            progreso = 0
            iteracionActual = 0
            totalIteraciones = iteraciones
            entrenamientosCompletados = 0
            ultimoLog = ""
            
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
            isRunning = false
            context.startService(Intent(context, AprendizajeService::class.java).apply { action = ACTION_STOP })
        }
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var job: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val tipoLoteria = intent.getStringExtra(EXTRA_TIPO_LOTERIA) ?: "PRIMITIVA"
                val sorteos = intent.getIntExtra(EXTRA_SORTEOS, 50)
                val iteraciones = intent.getIntExtra(EXTRA_ITERACIONES, 30)
                
                startForeground(NOTIFICATION_ID, createNotification("Preparando...", 0))
                acquireWakeLock()
                startLearningProcess(tipoLoteria, sorteos, iteraciones)
            }
            ACTION_STOP -> stopLearningProcess()
        }
        return START_STICKY
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // El servicio continúa cuando el usuario cierra la app
    }
    
    private fun startLearningProcess(tipoLoteria: String, sorteos: Int, iteraciones: Int) {
        job?.cancel()
        
        isRunning = true
        progreso = 0
        iteracionActual = 0
        totalIteraciones = iteraciones
        entrenamientosCompletados = 0
        mejorPuntuacion = 0.0
        tipoLoteriaActual = tipoLoteria
        
        job = serviceScope.launch {
            try {
                val calculador = CalculadorProbabilidad(this@AprendizajeService)
                val memoriaIA = MemoriaIA(this@AprendizajeService)
                val dataSource = com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource(this@AprendizajeService)
                val persistencia = com.loteria.probabilidad.domain.ml.BacktestPersistencia(this@AprendizajeService)
                
                updateNotification("Cargando $tipoLoteria...", 0)
                
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
                
                // Mapa para acumular los MEJORES resultados por método de TODAS las iteraciones
                val mejoresResultadosPorMetodo = mutableMapOf<MetodoCalculo, ResultadoBacktest>()
                
                for (i in 1..iteraciones) {
                    if (!isRunning) break
                    
                    iteracionActual = i
                    progreso = ((i.toFloat() / iteraciones) * 100).toInt()
                    ultimaActualizacion = System.currentTimeMillis()
                    
                    val resultados = ejecutarIteracion(tipoLoteria, calculador, memoriaIA, historicoPrecargado, sorteos)
                    
                    // Actualizar mejores resultados por método
                    for (resultado in resultados) {
                        val mejorPrevio = mejoresResultadosPorMetodo[resultado.metodo]
                        if (mejorPrevio == null || resultado.puntuacionTotal > mejorPrevio.puntuacionTotal) {
                            mejoresResultadosPorMetodo[resultado.metodo] = resultado
                        }
                    }
                    
                    // Actualizar mejor puntuación global
                    val mejorActualIteracion = resultados.maxOfOrNull { it.puntuacionTotal } ?: 0.0
                    val mejorGlobal = mejoresResultadosPorMetodo.values.maxOfOrNull { it.puntuacionTotal } ?: 0.0
                    mejorPuntuacion = mejorGlobal
                    
                    entrenamientosCompletados++
                    ultimoLog = "It. $i: Mejor=${String.format("%.1f", mejorPuntuacion)}"
                    
                    updateNotification("$tipoLoteria: $i/$iteraciones", progreso)
                    
                    yield()
                }
                
                // Al finalizar, guardar los MEJORES resultados acumulados de todas las iteraciones
                if (isRunning && mejoresResultadosPorMetodo.isNotEmpty()) {
                    val mejoresOrdenados = mejoresResultadosPorMetodo.values
                        .sortedByDescending { it.puntuacionTotal }
                    persistencia.guardarResultados(tipoLoteria, mejoresOrdenados)
                    
                    progreso = 100
                    ultimoLog = "✅ Completado: $iteraciones iteraciones"
                    updateNotification("✅ $tipoLoteria: Completado", 100)
                    delay(3000)
                }
                
            } catch (e: CancellationException) {
                ultimoLog = "⏹️ Detenido"
            } catch (e: Exception) {
                ultimoLog = "❌ Error: ${e.message}"
            } finally {
                finishService()
            }
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private suspend fun ejecutarIteracion(
        tipoLoteria: String, calculador: CalculadorProbabilidad, 
        memoriaIA: MemoriaIA, historicoPrecargado: Any, sorteos: Int
    ): List<ResultadoBacktest> = withContext(Dispatchers.Default) {
        when (tipoLoteria) {
            "PRIMITIVA", "BONOLOTO" -> {
                val historico = historicoPrecargado as List<ResultadoPrimitiva>
                val results = calculador.ejecutarBacktestPrimitiva(historico, sorteos)
                calculador.aprenderDeBacktest(results, historico, tipoLoteria, sorteos)
                results
            }
            "EUROMILLONES" -> {
                val historico = historicoPrecargado as List<ResultadoEuromillones>
                val results = calculador.ejecutarBacktestEuromillones(historico, sorteos)
                calculador.aprenderDeBacktest(results, historico.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }, tipoLoteria, sorteos)
                results
            }
            "GORDO_PRIMITIVA" -> {
                val historico = historicoPrecargado as List<ResultadoGordoPrimitiva>
                val results = calculador.ejecutarBacktestGordo(historico, sorteos)
                calculador.aprenderDeBacktest(results, historico.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }, tipoLoteria, sorteos)
                results
            }
            "LOTERIA_NACIONAL" -> {
                val historico = historicoPrecargado as List<ResultadoNacional>
                val results = calculador.ejecutarBacktestNacional(historico, sorteos)
                calculador.aprenderDeBacktest(results, historico.map { convertirNacionalATerminaciones(it) }, tipoLoteria, sorteos)
                results
            }
            "NAVIDAD" -> {
                val historico = historicoPrecargado as List<ResultadoNavidad>
                val results = calculador.ejecutarBacktestNavidad(historico, sorteos)
                calculador.aprenderDeBacktest(results, historico.map { 
                    val gordo = it.gordo.filter { c -> c.isDigit() }.takeLast(5).padStart(5, '0')
                    convertirNumeroATerminaciones(gordo, it.fecha, it.reintegros)
                }, tipoLoteria, sorteos)
                results
            }
            "NINO" -> {
                val historico = historicoPrecargado as List<ResultadoNacional>
                val results = calculador.ejecutarBacktestNacional(historico, sorteos)
                calculador.aprenderDeBacktest(results, historico.map { convertirNacionalATerminaciones(it) }, tipoLoteria, sorteos)
                results
            }
            else -> emptyList()
        }
    }
    
    private fun convertirNacionalATerminaciones(nac: ResultadoNacional): ResultadoPrimitiva {
        val num = nac.primerPremio.filter { it.isDigit() }.takeLast(5).padStart(5, '0')
        return convertirNumeroATerminaciones(num, nac.fecha, nac.reintegros)
    }
    
    private fun convertirNumeroATerminaciones(numero: String, fecha: String, reintegros: List<Int>): ResultadoPrimitiva {
        val term2 = numero.takeLast(2).toIntOrNull() ?: 0
        val term1 = (numero.takeLast(1).toIntOrNull() ?: 0) + 1
        val decena = ((numero.takeLast(2).toIntOrNull() ?: 0) / 10) + 11
        val centena = (numero.takeLast(3).toIntOrNull()?.rem(100) ?: 0) + 21
        val millar = (numero.takeLast(4).toIntOrNull()?.rem(50) ?: 0) + 1
        val reint = reintegros.firstOrNull()?.plus(41) ?: 41
        return ResultadoPrimitiva(fecha, listOf(term2, term1, decena, centena, millar, reint).map { it.coerceIn(1, 49) }, 0, reintegros.firstOrNull() ?: 0)
    }
    
    private fun stopLearningProcess() {
        isRunning = false
        job?.cancel()
        finishService()
    }
    
    private fun finishService() {
        isRunning = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Aprendizaje IA", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Proceso de aprendizaje en segundo plano"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(text: String, progress: Int): Notification {
        val stopPendingIntent = PendingIntent.getService(this, 0, 
            Intent(this, AprendizajeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        val openPendingIntent = PendingIntent.getActivity(this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_BACKTESTING, true)
                putExtra(EXTRA_TIPO_LOTERIA, tipoLoteriaActual)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        val titulo = when {
            progress >= 100 -> "✅ Completado"
            progress > 0 -> "🧠 $progress% - It. $iteracionActual/$totalIteraciones"
            else -> "🧠 Iniciando..."
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titulo)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(progress < 100)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar", stopPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
    
    private fun updateNotification(text: String, progress: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(text, progress))
    }
    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LoteriaProbabilidad::IA")
                .apply { setReferenceCounted(false); acquire() }
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        job?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
    }
}
