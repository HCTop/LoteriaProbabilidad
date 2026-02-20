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
import com.loteria.probabilidad.domain.ml.HistorialPredicciones
import com.loteria.probabilidad.domain.ml.MatematicasAbuelo
import com.loteria.probabilidad.domain.ml.MemoriaIA
import com.loteria.probabilidad.domain.ml.MotorInteligencia
import kotlinx.coroutines.*

/**
 * Servicio de aprendizaje en segundo plano v4.
 * Soporta dos modos:
 * - Backtesting clásico (ACTION_START): iteraciones x sorteos x 9 métodos
 * - Entrenamiento rápido IA (ACTION_START_RAPIDO): entrena pesos de Abuelo/Ensemble/Genético
 */
class AprendizajeService : Service() {

    companion object {
        const val CHANNEL_ID = "aprendizaje_ia_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.loteria.probabilidad.START_LEARNING"
        const val ACTION_START_RAPIDO = "com.loteria.probabilidad.START_RAPIDO"
        const val ACTION_STOP = "com.loteria.probabilidad.STOP_LEARNING"

        const val EXTRA_TIPO_LOTERIA = "tipo_loteria"
        const val EXTRA_SORTEOS = "sorteos"
        const val EXTRA_ITERACIONES = "iteraciones"
        const val EXTRA_OPEN_BACKTESTING = "open_backtesting"
        const val EXTRA_ENTRENAR_ABUELO = "entrenar_abuelo"
        const val EXTRA_ENTRENAR_ENSEMBLE = "entrenar_ensemble"
        const val EXTRA_ENTRENAR_GENETICO = "entrenar_genetico"

        @Volatile var isRunning = false
        @Volatile var progreso = 0
        @Volatile var iteracionActual = 0
        @Volatile var totalIteraciones = 0
        @Volatile var ultimoLog = ""
        @Volatile var entrenamientosCompletados = 0
        @Volatile var tipoLoteriaActual = ""
        @Volatile var mejorPuntuacion = 0.0
        @Volatile var ultimaActualizacion = System.currentTimeMillis()
        @Volatile var modoRapido = false

        // Contadores de combinaciones
        @Volatile var combinacionActual = 0
        @Volatile var totalCombinaciones = 0
        @Volatile var metodoActual = ""

        // Logs del entrenamiento rápido (la UI los lee y vacía)
        val logsRapido = mutableListOf<String>()

        fun agregarLogRapido(msg: String) {
            synchronized(logsRapido) {
                logsRapido.add(msg)
                while (logsRapido.size > 100) logsRapido.removeAt(0)
            }
        }

        fun obtenerLogsRapido(): List<String> {
            synchronized(logsRapido) {
                val copia = logsRapido.toList()
                logsRapido.clear()
                return copia
            }
        }

        // Lista de últimos métodos procesados (para el log)
        val metodosRecientes = mutableListOf<String>()

        fun agregarMetodoProcesado(metodo: String) {
            synchronized(metodosRecientes) {
                if (metodosRecientes.lastOrNull() != metodo) {
                    metodosRecientes.add(metodo)
                    while (metodosRecientes.size > 20) {
                        metodosRecientes.removeAt(0)
                    }
                }
            }
        }

        fun obtenerMetodosRecientes(): List<String> {
            synchronized(metodosRecientes) {
                val copia = metodosRecientes.toList()
                metodosRecientes.clear()
                return copia
            }
        }

        fun isRunningFor(tipoLoteria: String): Boolean {
            return isRunning && tipoLoteriaActual == tipoLoteria
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

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
            tipoLoteriaActual = tipoLoteria
            mejorPuntuacion = 0.0
            progreso = 0
            iteracionActual = 0
            totalIteraciones = iteraciones
            entrenamientosCompletados = 0
            ultimoLog = ""
            modoRapido = false

            combinacionActual = 0
            totalCombinaciones = 9 * sorteos * iteraciones
            metodoActual = ""
            synchronized(metodosRecientes) { metodosRecientes.clear() }

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

        fun startQuickTraining(
            context: Context, tipoLoteria: String, numSorteos: Int,
            entrenarAbuelo: Boolean, entrenarEnsemble: Boolean, entrenarGenetico: Boolean
        ) {
            tipoLoteriaActual = tipoLoteria
            mejorPuntuacion = 0.0
            progreso = 0
            iteracionActual = 0
            totalIteraciones = numSorteos
            entrenamientosCompletados = 0
            ultimoLog = ""
            modoRapido = true
            combinacionActual = 0
            totalCombinaciones = numSorteos
            metodoActual = ""
            synchronized(logsRapido) { logsRapido.clear() }

            val intent = Intent(context, AprendizajeService::class.java).apply {
                action = ACTION_START_RAPIDO
                putExtra(EXTRA_TIPO_LOTERIA, tipoLoteria)
                putExtra(EXTRA_SORTEOS, numSorteos)
                putExtra(EXTRA_ENTRENAR_ABUELO, entrenarAbuelo)
                putExtra(EXTRA_ENTRENAR_ENSEMBLE, entrenarEnsemble)
                putExtra(EXTRA_ENTRENAR_GENETICO, entrenarGenetico)
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
            ACTION_START_RAPIDO -> {
                val tipoLoteria = intent.getStringExtra(EXTRA_TIPO_LOTERIA) ?: "PRIMITIVA"
                val numSorteos = intent.getIntExtra(EXTRA_SORTEOS, 150)
                val entrenarAbuelo = intent.getBooleanExtra(EXTRA_ENTRENAR_ABUELO, true)
                val entrenarEnsemble = intent.getBooleanExtra(EXTRA_ENTRENAR_ENSEMBLE, true)
                val entrenarGenetico = intent.getBooleanExtra(EXTRA_ENTRENAR_GENETICO, true)

                startForeground(NOTIFICATION_ID, createNotification("Preparando entrenamiento rápido...", 0))
                acquireWakeLock()
                startQuickTrainingProcess(tipoLoteria, numSorteos, entrenarAbuelo, entrenarEnsemble, entrenarGenetico)
            }
            ACTION_STOP -> stopLearningProcess()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    // ==================== BACKTESTING CLÁSICO ====================

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

                // 1ª iteración evalúa todos (8 métodos), las demás solo 3 que aprenden
                val numMetodosTodos = MetodoCalculo.values().size
                val numMetodosAprendizaje = 3
                val combsPrimeraIt = sorteos * numMetodosTodos * 5
                val combsRestoIt = sorteos * numMetodosAprendizaje * 5
                val totalCombsEstimadas = combsPrimeraIt + combsRestoIt * (iteraciones - 1).coerceAtLeast(0)
                calculador.onProgresoBacktest = { metodo, comb, total ->
                    metodoActual = metodo
                    agregarMetodoProcesado(metodo)
                    val combsAnteriores = if (iteracionActual <= 1) 0
                        else combsPrimeraIt + combsRestoIt * (iteracionActual - 2)
                    combinacionActual = combsAnteriores + comb
                    totalCombinaciones = totalCombsEstimadas
                }

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

                val mejoresResultadosPorMetodo = mutableMapOf<MetodoCalculo, ResultadoBacktest>()

                // Métodos que realmente aprenden - solo estos se re-evalúan en iteraciones >1
                val metodosQueAprenden = arrayOf(
                    MetodoCalculo.METODO_ABUELO,
                    MetodoCalculo.ENSEMBLE_VOTING,
                    MetodoCalculo.IA_GENETICA
                )

                // Preparar aprendizaje del Abuelo por algoritmo (como el rápido)
                val matematicas = MatematicasAbuelo()
                val (maxNumero, cantidadNumeros) = when (tipoLoteria) {
                    "EUROMILLONES" -> Pair(50, 5)
                    "GORDO_PRIMITIVA" -> Pair(54, 5)
                    else -> Pair(49, 6)
                }
                val historicoPrim: List<ResultadoPrimitiva>? = when (tipoLoteria) {
                    "PRIMITIVA", "BONOLOTO" -> historicoPrecargado as? List<ResultadoPrimitiva>
                    "EUROMILLONES" -> (historicoPrecargado as? List<ResultadoEuromillones>)?.map {
                        ResultadoPrimitiva(it.fecha, it.numeros, 0, 0)
                    }
                    "GORDO_PRIMITIVA" -> (historicoPrecargado as? List<ResultadoGordoPrimitiva>)?.map {
                        ResultadoPrimitiva(it.fecha, it.numeros, 0, 0)
                    }
                    else -> null
                }

                for (i in 1..iteraciones) {
                    if (!isRunning) break

                    iteracionActual = i
                    progreso = ((i.toFloat() / iteraciones) * 100).toInt()
                    ultimaActualizacion = System.currentTimeMillis()

                    // 1ª iteración: todos los métodos (para ranking). Siguientes: solo los que aprenden.
                    val metodos = if (i == 1) MetodoCalculo.values() else metodosQueAprenden
                    val resultados = ejecutarIteracion(tipoLoteria, calculador, memoriaIA, historicoPrecargado, sorteos, metodos)

                    for (resultado in resultados) {
                        val mejorPrevio = mejoresResultadosPorMetodo[resultado.metodo]
                        if (mejorPrevio == null || resultado.puntuacionTotal > mejorPrevio.puntuacionTotal) {
                            mejoresResultadosPorMetodo[resultado.metodo] = resultado
                        }
                    }

                    // ═══ NUEVO: Aprendizaje del Abuelo por algoritmo (como el rápido) ═══
                    if (historicoPrim != null && historicoPrim.size > sorteos + 100) {
                        for (s in 0 until sorteos.coerceAtMost(10)) {
                            val sorteo = historicoPrim[s]
                            val historicoAnterior = historicoPrim.subList(
                                s + 1, (s + 500).coerceAtMost(historicoPrim.size)
                            )
                            if (historicoAnterior.size < 100) continue

                            val numerosReales = sorteo.numeros.toSet()

                            // Evaluar cada algoritmo individualmente
                            val (_, chiResultados) = matematicas.testChiCuadradoGlobal(historicoAnterior, maxNumero, cantidadNumeros)
                            val bayesianos = matematicas.inferenciaBayesiana(historicoAnterior, maxNumero, cantidadNumeros)
                            val fourier = matematicas.analizarFourier(historicoAnterior, maxNumero)
                            val markov = matematicas.analizarMarkov(historicoAnterior, maxNumero)
                            val entropia = matematicas.calcularEntropia(historicoAnterior, maxNumero)

                            val topChi = chiResultados.filter { it.sesgo > 0 }.sortedByDescending { it.sesgo }.take(10).map { it.numero }
                            val topBayes = bayesianos.entries.sortedByDescending { it.value.posteriorMedia }.take(10).map { it.key }
                            val topFourier = fourier.entries.sortedByDescending { entry ->
                                val comp = entry.value
                                if (comp.confianzaPeriodicidad > 0.3) {
                                    (1.0 - (comp.prediccionProximaSalida.toDouble() / comp.periodoDominante.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)) * comp.confianzaPeriodicidad
                                } else 0.0
                            }.take(10).map { it.key }
                            val topMarkov = markov.entries.sortedByDescending { it.value.prediccionProximoSorteo }.take(10).map { it.key }
                            val topEntropia = entropia.numerosConcentrados.take(10)

                            val contribuciones = mapOf(
                                "chiCuadrado" to topChi.count { it in numerosReales }.toDouble(),
                                "bayesiano" to topBayes.count { it in numerosReales }.toDouble(),
                                "fourier" to topFourier.count { it in numerosReales }.toDouble(),
                                "markov" to topMarkov.count { it in numerosReales }.toDouble(),
                                "entropia" to topEntropia.count { it in numerosReales }.toDouble()
                            )
                            memoriaIA.actualizarPesosAbuelo(contribuciones, contribuciones.values.sum().toInt(), tipoLoteria)
                        }
                    }

                    val mejorGlobal = mejoresResultadosPorMetodo.values.maxOfOrNull { it.puntuacionTotal } ?: 0.0
                    mejorPuntuacion = mejorGlobal

                    entrenamientosCompletados++
                    ultimoLog = "It. $i: Mejor=${String.format("%.1f", mejorPuntuacion)}"

                    updateNotification("$tipoLoteria: $i/$iteraciones", progreso)

                    yield()
                }

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
        memoriaIA: MemoriaIA, historicoPrecargado: Any, sorteos: Int,
        metodosAEvaluar: Array<MetodoCalculo> = MetodoCalculo.values()
    ): List<ResultadoBacktest> = withContext(Dispatchers.Default) {
        when (tipoLoteria) {
            "PRIMITIVA", "BONOLOTO" -> {
                val historico = historicoPrecargado as List<ResultadoPrimitiva>
                val results = calculador.ejecutarBacktestPrimitiva(historico, sorteos, tipoLoteria, metodosAEvaluar)
                calculador.aprenderDeBacktest(results, historico, tipoLoteria, sorteos)
                results
            }
            "EUROMILLONES" -> {
                val historico = historicoPrecargado as List<ResultadoEuromillones>
                val results = calculador.ejecutarBacktestEuromillones(historico, sorteos, metodosAEvaluar)
                calculador.aprenderDeBacktest(results, historico.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }, tipoLoteria, sorteos)
                results
            }
            "GORDO_PRIMITIVA" -> {
                val historico = historicoPrecargado as List<ResultadoGordoPrimitiva>
                val results = calculador.ejecutarBacktestGordo(historico, sorteos, metodosAEvaluar)
                calculador.aprenderDeBacktest(results, historico.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }, tipoLoteria, sorteos)
                results
            }
            "LOTERIA_NACIONAL" -> {
                val historico = historicoPrecargado as List<ResultadoNacional>
                val results = calculador.ejecutarBacktestNacional(historico, sorteos, tipoLoteria, metodosAEvaluar)
                calculador.aprenderDeBacktest(results, historico.map { convertirNacionalATerminaciones(it) }, tipoLoteria, sorteos)
                results
            }
            "NAVIDAD" -> {
                val historico = historicoPrecargado as List<ResultadoNavidad>
                val results = calculador.ejecutarBacktestNavidad(historico, sorteos, metodosAEvaluar)
                calculador.aprenderDeBacktest(results, historico.map {
                    val gordo = it.gordo.filter { c -> c.isDigit() }.takeLast(5).padStart(5, '0')
                    convertirNumeroATerminaciones(gordo, it.fecha, it.reintegros)
                }, tipoLoteria, sorteos)
                results
            }
            "NINO" -> {
                val historico = historicoPrecargado as List<ResultadoNacional>
                val results = calculador.ejecutarBacktestNacional(historico, sorteos, tipoLoteria, metodosAEvaluar)
                calculador.aprenderDeBacktest(results, historico.map { convertirNacionalATerminaciones(it) }, tipoLoteria, sorteos)
                results
            }
            else -> emptyList()
        }
    }

    // ==================== ENTRENAMIENTO RÁPIDO IA ====================

    private fun startQuickTrainingProcess(
        tipoLoteria: String, numSorteos: Int,
        entrenarAbuelo: Boolean, entrenarEnsemble: Boolean, entrenarGenetico: Boolean
    ) {
        job?.cancel()

        isRunning = true
        modoRapido = true
        progreso = 0
        iteracionActual = 0
        entrenamientosCompletados = 0
        tipoLoteriaActual = tipoLoteria

        val metodos = mutableListOf<String>()
        if (entrenarAbuelo) metodos.add("Abuelo")
        if (entrenarEnsemble) metodos.add("Ensemble")
        if (entrenarGenetico) metodos.add("Genético")
        agregarLogRapido("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        agregarLogRapido("🧠 ENTRENAMIENTO RÁPIDO IA")
        agregarLogRapido("   Sorteos: $numSorteos | Métodos: ${metodos.joinToString(", ")}")
        agregarLogRapido("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        job = serviceScope.launch {
            try {
                val memoriaIA = MemoriaIA(this@AprendizajeService)
                val motorIA = MotorInteligencia(this@AprendizajeService)
                val matematicas = MatematicasAbuelo()
                val dataSource = com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource(this@AprendizajeService)
                val historialPredicciones = HistorialPredicciones(this@AprendizajeService)

                updateNotification("Cargando $tipoLoteria...", 0)

                val (historicoPrim, maxNumero, cantidadNumeros) = when (tipoLoteria) {
                    "PRIMITIVA" -> Triple(dataSource.leerHistoricoPrimitiva(TipoLoteria.PRIMITIVA), 49, 6)
                    "BONOLOTO" -> Triple(dataSource.leerHistoricoPrimitiva(TipoLoteria.BONOLOTO), 49, 6)
                    "EUROMILLONES" -> {
                        val h = dataSource.leerHistoricoEuromillones()
                        Triple(h.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }, 50, 5)
                    }
                    "GORDO_PRIMITIVA" -> {
                        val h = dataSource.leerHistoricoGordoPrimitiva()
                        Triple(h.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }, 54, 5)
                    }
                    else -> {
                        agregarLogRapido("❌ No disponible para esta lotería")
                        ultimoLog = "No disponible"
                        progreso = 100
                        updateNotification("❌ No disponible", 100)
                        delay(2000)
                        finishService()
                        return@launch
                    }
                }

                if (historicoPrim.size < 150) {
                    agregarLogRapido("❌ Histórico insuficiente (mín 150, tiene ${historicoPrim.size})")
                    ultimoLog = "Histórico insuficiente"
                    progreso = 100
                    updateNotification("❌ Histórico insuficiente", 100)
                    delay(2000)
                    finishService()
                    return@launch
                }

                val efectivo = numSorteos.coerceAtMost(historicoPrim.size - 100)
                totalIteraciones = efectivo
                val sorteosAEvaluar = historicoPrim.take(efectivo).reversed()

                var entrenamientos = 0
                val distAbuelo = mutableMapOf<Int, Int>()
                val distEnsemble = mutableMapOf<Int, Int>()
                val distGenetico = mutableMapOf<Int, Int>()

                for ((idx, sorteo) in sorteosAEvaluar.withIndex()) {
                    if (!isRunning) break

                    iteracionActual = idx + 1
                    progreso = ((idx + 1).toFloat() / efectivo * 100).toInt()
                    ultimaActualizacion = System.currentTimeMillis()

                    val pos = historicoPrim.indexOf(sorteo)
                    val historicoAnterior = historicoPrim.subList(
                        pos + 1, (pos + 500).coerceAtMost(historicoPrim.size)
                    )
                    if (historicoAnterior.size < 100) continue

                    val numerosReales = sorteo.numeros.toSet()

                    // ══════ ABUELO ══════
                    if (entrenarAbuelo) {
                        val (_, chiResultados) = matematicas.testChiCuadradoGlobal(historicoAnterior, maxNumero, cantidadNumeros)
                        val bayesianos = matematicas.inferenciaBayesiana(historicoAnterior, maxNumero, cantidadNumeros)
                        val fourier = matematicas.analizarFourier(historicoAnterior, maxNumero)
                        val markov = matematicas.analizarMarkov(historicoAnterior, maxNumero)
                        val entropia = matematicas.calcularEntropia(historicoAnterior, maxNumero)

                        val topChi = chiResultados.filter { it.sesgo > 0 }.sortedByDescending { it.sesgo }.take(10).map { it.numero }
                        val topBayes = bayesianos.entries.sortedByDescending { it.value.posteriorMedia }.take(10).map { it.key }
                        val topFourier = if (fourier.isNotEmpty()) {
                            fourier.entries.sortedByDescending { entry ->
                                val comp = entry.value
                                if (comp.confianzaPeriodicidad > 0.3) {
                                    (1.0 - (comp.prediccionProximaSalida.toDouble() / comp.periodoDominante.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)) * comp.confianzaPeriodicidad
                                } else 0.0
                            }.take(10).map { it.key }
                        } else (1..maxNumero).take(10)
                        val topMarkov = if (markov.isNotEmpty()) markov.entries.sortedByDescending { it.value.prediccionProximoSorteo }.take(10).map { it.key }
                        else (1..maxNumero).take(10)
                        val topEntropia = entropia.numerosConcentrados.take(10)

                        val contribuciones = mapOf(
                            "chiCuadrado" to topChi.count { it in numerosReales }.toDouble(),
                            "bayesiano" to topBayes.count { it in numerosReales }.toDouble(),
                            "fourier" to topFourier.count { it in numerosReales }.toDouble(),
                            "markov" to topMarkov.count { it in numerosReales }.toDouble(),
                            "entropia" to topEntropia.count { it in numerosReales }.toDouble()
                        )

                        val pesosActuales = memoriaIA.obtenerPesosAbuelo(tipoLoteria)
                        val tops = mapOf("chiCuadrado" to topChi, "bayesiano" to topBayes, "fourier" to topFourier, "markov" to topMarkov, "entropia" to topEntropia)
                        val scoresPorNumero = (1..maxNumero).associateWith { num ->
                            var score = 0.0
                            for ((alg, topList) in tops) {
                                val posicion = topList.indexOf(num)
                                if (posicion >= 0) score += (10 - posicion) * (pesosActuales[alg] ?: 0.2)
                            }
                            score
                        }
                        val prediccion = scoresPorNumero.entries.sortedByDescending { it.value }.take(cantidadNumeros).map { it.key }
                        val aciertosAbuelo = prediccion.count { it in numerosReales }
                        distAbuelo[aciertosAbuelo] = (distAbuelo[aciertosAbuelo] ?: 0) + 1
                        memoriaIA.actualizarPesosAbuelo(contribuciones, aciertosAbuelo, tipoLoteria)
                    }

                    // ══════ ENSEMBLE ══════
                    if (entrenarEnsemble) {
                        try {
                            val resultado = motorIA.ejecutarEnsembleVoting(historicoAnterior, maxNumero, cantidadNumeros, tipoLoteria)
                            motorIA.registrarResultadoEnsemble(sorteo.numeros, resultado, tipoLoteria)
                            val aciertosEns = resultado.combinacionGanadora.count { it in numerosReales }
                            distEnsemble[aciertosEns] = (distEnsemble[aciertosEns] ?: 0) + 1
                        } catch (_: Exception) { }
                    }

                    // ══════ GENÉTICO ══════
                    if (entrenarGenetico) {
                        try {
                            motorIA.generarCombinacionesInteligentes(historicoAnterior, maxNumero, cantidadNumeros, 1, tipoLoteria)
                            val contribuciones = motorIA.getContribuciones()
                            val mejorPunt = memoriaIA.obtenerMejorPuntuacion(tipoLoteria)
                            val combinacion = motorIA.generarCombinacionesInteligentes(historicoAnterior, maxNumero, cantidadNumeros, 1, tipoLoteria)
                                .firstOrNull()?.numeros ?: emptyList()
                            val aciertosGen = combinacion.count { it in numerosReales }
                            val puntuacion = aciertosGen.toDouble()
                            distGenetico[aciertosGen] = (distGenetico[aciertosGen] ?: 0) + 1
                            memoriaIA.actualizarPesos(contribuciones, puntuacion, mejorPunt, tipoLoteria)
                        } catch (_: Exception) { }
                    }

                    entrenamientos++
                    entrenamientosCompletados = entrenamientos

                    // Log cada 25 sorteos
                    if ((idx + 1) % 25 == 0) {
                        val parts = mutableListOf<String>()
                        if (entrenarAbuelo) {
                            val pesosNow = memoriaIA.obtenerPesosAbuelo(tipoLoteria)
                            parts.add("Abuelo[${pesosNow.entries.joinToString(" ") { "${it.key.take(3)}=${"%.0f".format(it.value * 100)}%" }}]")
                        }
                        if (entrenarEnsemble) parts.add("Ens✓")
                        if (entrenarGenetico) parts.add("Gen✓")
                        agregarLogRapido("   📊 ${idx + 1}/$efectivo - ${parts.joinToString(" | ")}")
                    }

                    updateNotification("Entrenando $tipoLoteria: ${idx + 1}/$efectivo", progreso)
                    yield()
                }

                if (!isRunning) {
                    agregarLogRapido("⏹️ Detenido por el usuario")
                    ultimoLog = "⏹️ Detenido"
                    finishService()
                    return@launch
                }

                // ── Resumen final ──
                if (entrenarAbuelo) {
                    val pesosFinales = memoriaIA.obtenerPesosAbuelo(tipoLoteria)
                    val resumen = pesosFinales.entries.joinToString(", ") { "${it.key}=${"%.0f".format(it.value * 100)}%" }
                    val distStr = distAbuelo.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}ac:${it.value}x" }
                    agregarLogRapido("   🔬 Abuelo: $distStr")
                    agregarLogRapido("   Pesos: $resumen")
                }
                if (entrenarEnsemble) {
                    val distStr = distEnsemble.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}ac:${it.value}x" }
                    agregarLogRapido("   🗳️ Ensemble: $distStr")
                }
                if (entrenarGenetico) {
                    val distStr = distGenetico.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}ac:${it.value}x" }
                    agregarLogRapido("   🧬 Genético: $distStr")
                }

                // Persistir resultados como ResultadoBacktest para que la UI los muestre
                val backtestPersistencia = com.loteria.probabilidad.domain.ml.BacktestPersistencia(this@AprendizajeService)
                val resultadosBacktest = mutableListOf<ResultadoBacktest>()

                fun distToBacktest(dist: Map<Int, Int>, metodo: MetodoCalculo): ResultadoBacktest {
                    val total = dist.values.sum().coerceAtLeast(1)
                    val mejor = dist.keys.maxOrNull() ?: 0
                    val promedio = dist.entries.sumOf { it.key * it.value }.toDouble() / total
                    return ResultadoBacktest(
                        metodo = metodo,
                        sorteosProbados = total,
                        aciertos0 = dist[0] ?: 0,
                        aciertos1 = dist[1] ?: 0,
                        aciertos2 = dist[2] ?: 0,
                        aciertos3 = dist[3] ?: 0,
                        aciertos4 = dist[4] ?: 0,
                        aciertos5 = dist[5] ?: 0,
                        aciertos6 = dist[6] ?: 0,
                        puntuacionTotal = promedio * 10,
                        mejorAcierto = mejor,
                        promedioAciertos = promedio,
                        tipoLoteria = tipoLoteria
                    )
                }

                if (entrenarAbuelo && distAbuelo.isNotEmpty()) {
                    resultadosBacktest.add(distToBacktest(distAbuelo, MetodoCalculo.METODO_ABUELO))
                }
                if (entrenarEnsemble && distEnsemble.isNotEmpty()) {
                    resultadosBacktest.add(distToBacktest(distEnsemble, MetodoCalculo.ENSEMBLE_VOTING))
                }
                if (entrenarGenetico && distGenetico.isNotEmpty()) {
                    resultadosBacktest.add(distToBacktest(distGenetico, MetodoCalculo.IA_GENETICA))
                }

                if (resultadosBacktest.isNotEmpty()) {
                    // Combinar con resultados existentes (reemplazar métodos entrenados, mantener los demás)
                    val existentes = backtestPersistencia.obtenerResultados(tipoLoteria)
                    val metodosEntrenados = resultadosBacktest.map { it.metodo }.toSet()
                    val combinados = existentes.filter { it.metodo !in metodosEntrenados } + resultadosBacktest
                    backtestPersistencia.guardarResultados(tipoLoteria, combinados.sortedByDescending { it.puntuacionTotal })
                    agregarLogRapido("📊 Resultados guardados: ${resultadosBacktest.size} métodos")
                }

                // Invalidar predicciones cacheadas
                val proximoSorteo = historialPredicciones.proximoSorteo(tipoLoteria)
                historialPredicciones.eliminarPredicciones(tipoLoteria, proximoSorteo.toString())
                agregarLogRapido("🔄 Predicciones invalidadas - se regenerarán con los nuevos pesos")

                agregarLogRapido("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                agregarLogRapido("✅ ENTRENAMIENTO COMPLETADO: $entrenamientos sorteos")
                agregarLogRapido("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                progreso = 100
                ultimoLog = "✅ $entrenamientos sorteos entrenados"
                updateNotification("✅ $tipoLoteria: Entrenamiento completado", 100)
                delay(3000)

            } catch (e: CancellationException) {
                agregarLogRapido("⏹️ Detenido")
                ultimoLog = "⏹️ Detenido"
            } catch (e: Exception) {
                agregarLogRapido("❌ Error: ${e.message}")
                ultimoLog = "❌ Error: ${e.message}"
            } finally {
                finishService()
            }
        }
    }

    // ==================== UTILIDADES ====================

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

        val prefix = if (modoRapido) "🧬" else "🧠"
        val titulo = when {
            progress >= 100 -> "✅ Completado"
            progress > 0 -> if (modoRapido) "$prefix $progress% - $iteracionActual/$totalIteraciones sorteos"
                            else "$prefix $progress% - It. $iteracionActual/$totalIteraciones"
            else -> "$prefix Iniciando..."
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
