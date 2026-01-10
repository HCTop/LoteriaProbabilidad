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
        var tipoLoteriaActual = ""  // Para saber qué lotería abrir
        
        fun startLearning(
            context: Context,
            tipoLoteria: String,
            sorteos: Int,
            iteraciones: Int
        ) {
            tipoLoteriaActual = tipoLoteria
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
                
                // Cargar histórico según tipo de lotería
                val dataSource = com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource(this@AprendizajeService)
                
                for (i in 1..iteraciones) {
                    if (!isRunning) break
                    
                    iteracionActual = i
                    progreso = ((i.toFloat() / iteraciones) * 100).toInt()
                    ultimoLog = "Iteración $i de $iteraciones para $tipoLoteria"
                    
                    updateNotification("Aprendiendo $tipoLoteria: $i/$iteraciones", progreso)
                    
                    // Ejecutar backtesting según tipo
                    val resultados = when (tipoLoteria) {
                        "PRIMITIVA" -> {
                            val historico = dataSource.leerHistoricoPrimitiva(TipoLoteria.PRIMITIVA)
                            val results = calculador.ejecutarBacktestPrimitiva(historico, sorteos)
                            calculador.aprenderDeBacktest(results, historico, tipoLoteria, sorteos)
                            results
                        }
                        "BONOLOTO" -> {
                            val historico = dataSource.leerHistoricoPrimitiva(TipoLoteria.BONOLOTO)
                            val results = calculador.ejecutarBacktestPrimitiva(historico, sorteos)
                            calculador.aprenderDeBacktest(results, historico, tipoLoteria, sorteos)
                            results
                        }
                        "EUROMILLONES" -> {
                            val historico = dataSource.leerHistoricoEuromillones()
                            val results = calculador.ejecutarBacktestEuromillones(historico, sorteos)
                            val historicoComun = historico.map { euro -> ResultadoPrimitiva(euro.fecha, euro.numeros, 0, 0) }
                            calculador.aprenderDeBacktest(results, historicoComun, tipoLoteria, sorteos)
                            results
                        }
                        "GORDO_PRIMITIVA" -> {
                            val historico = dataSource.leerHistoricoGordoPrimitiva()
                            val results = calculador.ejecutarBacktestGordo(historico, sorteos)
                            val historicoComun = historico.map { gordo -> ResultadoPrimitiva(gordo.fecha, gordo.numeros, 0, 0) }
                            calculador.aprenderDeBacktest(results, historicoComun, tipoLoteria, sorteos)
                            results
                        }
                        "LOTERIA_NACIONAL" -> {
                            val historico = dataSource.leerHistoricoNacional(TipoLoteria.LOTERIA_NACIONAL)
                            val results = calculador.ejecutarBacktestNacional(historico, sorteos)
                            // Convertir a dígitos para aprendizaje
                            val historicoComun = historico.map { nac -> 
                                val digitos = nac.primerPremio.padStart(5, '0').map { it.digitToInt() }
                                ResultadoPrimitiva(nac.fecha, digitos, 0, 0)
                            }
                            calculador.aprenderDeBacktest(results, historicoComun, tipoLoteria, sorteos)
                            results
                        }
                        "NAVIDAD" -> {
                            val historico = dataSource.leerHistoricoNavidad()
                            val results = calculador.ejecutarBacktestNavidad(historico, sorteos)
                            val historicoComun = historico.map { nav -> 
                                val digitos = nav.gordo.padStart(5, '0').map { it.digitToInt() }
                                ResultadoPrimitiva(nav.fecha, digitos, 0, 0)
                            }
                            calculador.aprenderDeBacktest(results, historicoComun, tipoLoteria, sorteos)
                            results
                        }
                        "NINO" -> {
                            val historico = dataSource.leerHistoricoNacional(TipoLoteria.NINO)
                            val results = calculador.ejecutarBacktestNacional(historico, sorteos)
                            val historicoComun = historico.map { nino -> 
                                val digitos = nino.primerPremio.padStart(5, '0').map { it.digitToInt() }
                                ResultadoPrimitiva(nino.fecha, digitos, 0, 0)
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
                    
                    // Pequeña pausa entre iteraciones
                    delay(300)
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
