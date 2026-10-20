package com.loteria.probabilidad.domain.usecase

import android.content.Context
import com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.data.repository.LoteriaRepository
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad
import com.loteria.probabilidad.domain.ml.HistorialPredicciones
import com.loteria.probabilidad.domain.ml.LogAbuelo
import com.loteria.probabilidad.domain.ml.MemoriaIA
import com.loteria.probabilidad.domain.ml.MotorInteligencia
import kotlinx.coroutines.launch

/**
 * Punto ÚNICO para guardar y evaluar las predicciones de cada sorteo.
 *
 * Antes esta lógica vivía solo en ResultadosViewModel, así que:
 *  - Solo se guardaban predicciones si entrabas en la pantalla de esa lotería antes del sorteo.
 *  - Solo se evaluaban (aciertos) al volver a entrar en esa pantalla.
 *  - La pantalla de inicio enseñaba combinaciones que NUNCA se guardaban ni evaluaban.
 *
 * Ahora la pantalla de inicio y la de resultados usan esta misma clase:
 *  - Al abrir la app se evalúan los sorteos pendientes de TODAS las loterías
 *    y se guardan las predicciones del próximo sorteo de cada una.
 *  - Lo que ves en inicio es exactamente lo que se guarda y se evalúa después.
 *
 * Todas las operaciones van bajo un candado por lotería: si inicio y resultados
 * piden lo mismo a la vez, la segunda espera y reutiliza lo guardado en lugar
 * de generar (y sobrescribir) otra predicción distinta.
 */
class GestorPredicciones(
    private val repository: LoteriaRepository,
    private val obtenerCombinacionesUseCase: ObtenerCombinacionesUseCase,
    private val historialPredicciones: HistorialPredicciones,
    private val memoriaIA: MemoriaIA
) {

    data class Resultado(val analisis: AnalisisProbabilidad, val info: String)

    /** Lotería, sorteos hechos, total y cuándo empezó (para calcular lo que falta). */
    data class EstadoSimulacion(val tipo: TipoLoteria, val hechos: Int, val total: Int, val inicioMs: Long) {
        /** Segundos que faltan aprox., o null si aún no hay base para calcularlo. */
        fun segundosRestantes(): Long? {
            if (hechos < 3) return null
            val porSorteo = (System.currentTimeMillis() - inicioMs) / hechos
            return porSorteo * (total - hechos) / 1000
        }
    }

    companion object {
        private val candados = java.util.concurrent.ConcurrentHashMap<TipoLoteria, Any>()
        private fun candado(tipo: TipoLoteria): Any = candados.getOrPut(tipo) { Any() }

        // ── Simulación de sorteos pasados (puede durar minutos) ──
        private val _simulacion = kotlinx.coroutines.flow.MutableStateFlow<EstadoSimulacion?>(null)
        /** Simulación en marcha (o null). La pantalla lo observa aunque se haya cerrado y vuelto a abrir. */
        val simulacion: kotlinx.coroutines.flow.StateFlow<EstadoSimulacion?> = _simulacion
        private val enMarcha = java.util.concurrent.atomic.AtomicBoolean(false)
        private val parar = java.util.concurrent.atomic.AtomicBoolean(false)
        private val ambitoSimulacion = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
        )
        /**
         * Sorteos pasados que se simulan (solo la 1ª vez, en segundo plano) para decidir qué
         * método acierta más. Con menos, el "mejor método" es pura suerte.
         */
        const val OBJETIVO_SIMULACION = 1000

        /** Para la simulación en curso; lo ya simulado se queda guardado. */
        fun pararSimulacion() = parar.set(true)

        /** Loterías de bolas: las únicas que se guardan y evalúan automáticamente. */
        val LOTERIAS_EVALUABLES = listOf(
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO,
            TipoLoteria.EUROMILLONES, TipoLoteria.GORDO_PRIMITIVA
        )

        /**
         * MIGRACIÓN DE UNA SOLA VEZ (oct-2026): al pasar de 7 a 11 métodos, se borran los sorteos
         * simulados y las combinaciones guardadas del próximo sorteo, para que todo se rehaga
         * con los 11 métodos. Las evaluaciones REALES se conservan. Se marca con un flag y no
         * vuelve a ejecutarse. Para repetirla en desarrollo: subir VERSION_METODOS.
         */
        private const val VERSION_METODOS = 2
        /** Subir para rehacer solo las predicciones pendientes del próximo sorteo. */
        private const val VERSION_REINTEGRO = 2

        fun migrarSiHaceFalta(context: Context) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("gestor_predicciones", Context.MODE_PRIVATE)
            val historial = HistorialPredicciones(app)
            // Reintegro por método (v1): rehacer SOLO las predicciones del próximo sorteo
            // (aún no se ha celebrado, así que no hay look-ahead). El historial se conserva.
            val versionComp = prefs.getInt("version_reintegro", 0)
            if (versionComp < 1) {
                for (tipo in listOf(TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO)) {
                    historial.eliminarPredicciones(tipo.name, historial.proximoSorteo(tipo.name).toString())
                }
            }
            // v2: estrellas (Euromillones) y clave (Gordo) por método + Perfil típico de 5 números.
            // Sus simulados se hicieron con estrellas/clave al azar: se borran y
            // SimulacionHistoricaWorker los rehace solo. Los reales se conservan.
            if (versionComp < 2) {
                for (tipo in listOf(TipoLoteria.EUROMILLONES, TipoLoteria.GORDO_PRIMITIVA)) {
                    historial.borrarSimulados(tipo.name)
                    historial.eliminarPredicciones(tipo.name, historial.proximoSorteo(tipo.name).toString())
                }
            }
            if (versionComp < VERSION_REINTEGRO) {
                prefs.edit().putInt("version_reintegro", VERSION_REINTEGRO).apply()
            }
            if (prefs.getInt("version_metodos", 1) >= VERSION_METODOS) return
            for (tipo in LOTERIAS_EVALUABLES) {
                historial.borrarSimulados(tipo.name)
                historial.eliminarPredicciones(tipo.name, historial.proximoSorteo(tipo.name).toString())
            }
            prefs.edit().putInt("version_metodos", VERSION_METODOS).apply()
            android.util.Log.i("GestorPredicciones", "Migración a $VERSION_METODOS: historial simulado y predicciones rehechos")
        }

        fun crear(context: Context): GestorPredicciones {
            val app = context.applicationContext
            val repository = LoteriaRepository(LoteriaLocalDataSource(app))
            return GestorPredicciones(
                repository,
                ObtenerCombinacionesUseCase(repository, CalculadorProbabilidad(app)),
                HistorialPredicciones(app),
                MemoriaIA(app)
            )
        }
    }

    /** Evalúa contra los resultados reales los sorteos pendientes. Devuelve cuántos se evaluaron. */
    fun evaluarPendientes(tipoLoteria: TipoLoteria): Int =
        synchronized(candado(tipoLoteria)) { evaluarPrediccionesPendientes(tipoLoteria) }

    /** Devuelve las predicciones guardadas del próximo sorteo o las genera y guarda. */
    fun obtenerOGenerar(tipoLoteria: TipoLoteria, rangoFechas: RangoFechas = RangoFechas.TODO): Resultado =
        synchronized(candado(tipoLoteria)) { obtenerOGenerarInterno(tipoLoteria, rangoFechas) }

    /** Borra las predicciones guardadas del próximo sorteo y genera otras nuevas. */
    fun regenerar(tipoLoteria: TipoLoteria, rangoFechas: RangoFechas = RangoFechas.TODO): Resultado =
        synchronized(candado(tipoLoteria)) {
            val fecha = historialPredicciones.proximoSorteo(tipoLoteria.name).toString()
            historialPredicciones.eliminarPredicciones(tipoLoteria.name, fecha)
            obtenerOGenerarInterno(tipoLoteria, rangoFechas)
        }

    /**
     * Rellena el historial con los últimos [cuantos] sorteos SIMULADOS (walk-forward):
     * para cada sorteo pasado se generan las combinaciones de todos los métodos usando
     * SOLO los sorteos anteriores a su fecha, y se comparan con el resultado real.
     *
     * - Las entradas quedan marcadas como simuladas (la UI lo indica).
     * - No se toca el aprendizaje de la IA (sería entrenar dos veces con lo mismo).
     * - Se salta los sorteos que ya tienen evaluación real, o simulada con todos los métodos.
     * - Guarda por tandas de 25 y solo bloquea la lotería mientras simula cada sorteo.
     *
     * @param progreso (hechos, total) para ir mostrando el avance
     * @return número de sorteos añadidos
     */
    fun rellenarHistorialSimulado(
        tipoLoteria: TipoLoteria,
        cuantos: Int = OBJETIVO_SIMULACION,
        limiteMs: Long = Long.MAX_VALUE,
        progreso: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean {
        if (tipoLoteria !in LOTERIAS_EVALUABLES) return true
        // (fecha, números, complementarios) del más antiguo al más reciente
        val sorteos = synchronized(candado(tipoLoteria)) { sorteosRecientes(tipoLoteria, cuantos) }.reversed()
        // Qué hay ya guardado (se lee una vez): reales → no se tocan; simulados completos → se saltan
        val existentes = historialPredicciones.cargarHistorial(tipoLoteria.name).associateBy { it.fechaSorteo }
        val nombres = obtenerCombinacionesUseCase.nombresMetodos
        val tanda = mutableListOf<Triple<String, List<HistorialPredicciones.ResultadoEvaluacion>, Boolean>>()
        var anadidos = 0
        var completo = true

        fun guardarTanda() {
            if (tanda.isEmpty()) return
            synchronized(candado(tipoLoteria)) {
                historialPredicciones.guardarEvaluacionesSimuladas(tipoLoteria.name, tanda.toList())
            }
            anadidos += tanda.size
            tanda.clear()
        }

        for ((i, sorteo) in sorteos.withIndex()) {
            // Parar a petición o al agotar el tiempo (lo hecho queda guardado; se sigue otro día)
            if (parar.get() || System.currentTimeMillis() > limiteMs) { completo = false; break }
            progreso(i, sorteos.size)
            val (fecha, numeros, complementarios) = sorteo
            val existente = existentes[fecha]
            val incompleta = existente != null && existente.simulado &&
                !existente.evaluaciones.map { it.metodo }.containsAll(nombres)
            if (existente != null && !incompleta) continue
            try {
                // El candado solo durante ESTE sorteo (~1 s): así la pantalla de inicio y la
                // sincronización diaria no se quedan esperando a que acaben 1.000 sorteos.
                val evaluaciones = synchronized(candado(tipoLoteria)) {
                    val hasta = java.time.LocalDate.parse(fecha).minusDays(1).toString()
                    val analisis = obtenerCombinacionesUseCase.ejecutarTodosMetodos(
                        tipoLoteria = tipoLoteria,
                        rangoFechas = RangoFechas(null, hasta) // solo el pasado de ese sorteo
                    )
                    historialPredicciones.calcularEvaluaciones(
                        analisis.combinacionesSugeridas, numeros, complementarios
                    )
                }
                tanda.add(Triple(fecha, evaluaciones, incompleta))
                if (tanda.size >= 25) guardarTanda()
            } catch (e: Exception) {
                android.util.Log.e("GestorPredicciones", "Simulación $fecha falló: ${e.message}", e)
            }
        }
        guardarTanda()
        if (completo) progreso(sorteos.size, sorteos.size)
        android.util.Log.i("GestorPredicciones", "Simulación $tipoLoteria: $anadidos nuevos, completa=$completo")
        return completo
    }

    /**
     * Simula hasta [limiteMs] (o hasta acabar) mostrando el progreso en [simulacion].
     * La usa SimulacionHistoricaWorker. Devuelve true si ya están simulados los [cuantos].
     */
    fun simularConLimite(tipoLoteria: TipoLoteria, cuantos: Int, limiteMs: Long): Boolean {
        if (!enMarcha.compareAndSet(false, true)) return false
        parar.set(false)
        val inicio = System.currentTimeMillis()
        return try {
            rellenarHistorialSimulado(tipoLoteria, cuantos, limiteMs) { hechos, total ->
                _simulacion.value = EstadoSimulacion(tipoLoteria, hechos, total, inicio)
            }
        } finally {
            _simulacion.value = null
            enMarcha.set(false)
        }
    }

    /**
     * Lanza la simulación en segundo plano (no depende de la pantalla: si sales y vuelves,
     * sigue y se ve por dónde va). Solo una a la vez. El estado está en [simulacion].
     */
    fun lanzarSimulacion(tipoLoteria: TipoLoteria, cuantos: Int) {
        if (!enMarcha.compareAndSet(false, true)) return
        parar.set(false)
        val inicio = System.currentTimeMillis()
        _simulacion.value = EstadoSimulacion(tipoLoteria, 0, cuantos, inicio)
        ambitoSimulacion.launch {
            try {
                rellenarHistorialSimulado(tipoLoteria, cuantos, Long.MAX_VALUE) { hechos, total ->
                    _simulacion.value = EstadoSimulacion(tipoLoteria, hechos, total, inicio)
                }
            } catch (e: Exception) {
                android.util.Log.e("GestorPredicciones", "Simulación falló: ${e.message}", e)
            } finally {
                _simulacion.value = null
                enMarcha.set(false)
            }
        }
    }

    private fun sorteosRecientes(tipo: TipoLoteria, n: Int): List<Triple<String, List<Int>, List<Int>>> =
        when (tipo) {
            TipoLoteria.PRIMITIVA -> repository.obtenerHistoricoPrimitiva().take(n)
                .map { Triple(it.fecha, it.numeros, listOf(it.reintegro)) }
            TipoLoteria.BONOLOTO -> repository.obtenerHistoricoBonoloto().take(n)
                .map { Triple(it.fecha, it.numeros, listOf(it.reintegro)) }
            TipoLoteria.EUROMILLONES -> repository.obtenerHistoricoEuromillones().take(n)
                .map { Triple(it.fecha, it.numeros, it.estrellas) }
            TipoLoteria.GORDO_PRIMITIVA -> repository.obtenerHistoricoGordoPrimitiva().take(n)
                .map { Triple(it.fecha, it.numeros, listOf(it.numeroClave)) }
            else -> emptyList()
        }

    /**
     * Evalúa todo lo pendiente y deja guardadas las predicciones del próximo sorteo
     * de todas las loterías de bolas. Pensado para lanzarse al abrir la app.
     */
    fun sincronizarTodas() {
        for (tipo in LOTERIAS_EVALUABLES) {
            try {
                evaluarPendientes(tipo)
                obtenerOGenerar(tipo)
            } catch (e: Exception) {
                android.util.Log.e("GestorPredicciones", "Error sincronizando $tipo: ${e.message}", e)
            }
        }
    }

    /**
     * Evalúa predicciones pendientes contra los resultados reales del histórico.
     * Recorre los últimos 10 sorteos del histórico buscando predicciones guardadas sin evaluar.
     * Además, actualiza los pesos del Método del Abuelo si hay evaluaciones nuevas.
     */
    private fun evaluarPrediccionesPendientes(tipoLoteria: TipoLoteria): Int {
        if (!historialPredicciones.soportaEvaluacion(tipoLoteria.name)) return 0

        LogAbuelo.gradiente("Evaluador", 0.0,
            "Buscando predicciones pendientes para ${tipoLoteria.name}...")
        var evaluacionesEncontradas = 0

        try {
            when (tipoLoteria) {
                TipoLoteria.PRIMITIVA -> {
                    for (sorteo in repository.obtenerHistoricoPrimitiva().take(10)) {
                        val evaluaciones = historialPredicciones.evaluarPredicciones(
                            tipoLoteria.name, sorteo.fecha,
                            sorteo.numeros, listOf(sorteo.reintegro)
                        )
                        if (evaluaciones != null) {
                            evaluacionesEncontradas++
                            actualizarAprendizajeAbuelo(tipoLoteria.name, evaluaciones)
                            actualizarAprendizajeGeneral(tipoLoteria.name, evaluaciones, sorteo.numeros, 6, 49, sorteo.fecha)
                        }
                    }
                }
                TipoLoteria.BONOLOTO -> {
                    for (sorteo in repository.obtenerHistoricoBonoloto().take(10)) {
                        val evaluaciones = historialPredicciones.evaluarPredicciones(
                            tipoLoteria.name, sorteo.fecha,
                            sorteo.numeros, listOf(sorteo.reintegro)
                        )
                        if (evaluaciones != null) {
                            evaluacionesEncontradas++
                            actualizarAprendizajeAbuelo(tipoLoteria.name, evaluaciones)
                            actualizarAprendizajeGeneral(tipoLoteria.name, evaluaciones, sorteo.numeros, 6, 49, sorteo.fecha)
                        }
                    }
                }
                TipoLoteria.EUROMILLONES -> {
                    for (sorteo in repository.obtenerHistoricoEuromillones().take(10)) {
                        val evaluaciones = historialPredicciones.evaluarPredicciones(
                            tipoLoteria.name, sorteo.fecha,
                            sorteo.numeros, sorteo.estrellas
                        )
                        if (evaluaciones != null) {
                            evaluacionesEncontradas++
                            actualizarAprendizajeAbuelo(tipoLoteria.name, evaluaciones)
                            actualizarAprendizajeGeneral(tipoLoteria.name, evaluaciones, sorteo.numeros, 5, 50, sorteo.fecha)
                        }
                    }
                }
                TipoLoteria.GORDO_PRIMITIVA -> {
                    for (sorteo in repository.obtenerHistoricoGordoPrimitiva().take(10)) {
                        val evaluaciones = historialPredicciones.evaluarPredicciones(
                            tipoLoteria.name, sorteo.fecha,
                            sorteo.numeros, listOf(sorteo.numeroClave)
                        )
                        if (evaluaciones != null) {
                            evaluacionesEncontradas++
                            actualizarAprendizajeAbuelo(tipoLoteria.name, evaluaciones)
                            actualizarAprendizajeGeneral(tipoLoteria.name, evaluaciones, sorteo.numeros, 5, 54, sorteo.fecha)
                        }
                    }
                }
                else -> { /* Nacional, Navidad, Niño no se evalúan por bolas */ }
            }

            if (evaluacionesEncontradas == 0) {
                LogAbuelo.gradiente("Evaluador", 0.0,
                    "No hay predicciones nuevas pendientes para ${tipoLoteria.name} (ya evaluadas o sin predicciones)")
            } else {
                LogAbuelo.gradiente("Evaluador", 1.0,
                    "$evaluacionesEncontradas sorteos evaluados para ${tipoLoteria.name}")
            }
        } catch (e: Exception) {
            android.util.Log.e("GestorPredicciones", "Error evaluando predicciones: ${e.message}", e)
        }
        return evaluacionesEncontradas
    }

    /**
     * Actualiza los pesos de aprendizaje del Método del Abuelo basándose en la evaluación.
     * Busca la evaluación del Abuelo y usa sus aciertos como señal de aprendizaje.
     */
    private fun actualizarAprendizajeAbuelo(
        tipoLoteria: String,
        evaluaciones: List<HistorialPredicciones.ResultadoEvaluacion>
    ) {
        try {
            // Buscar la evaluación del Método del Abuelo
            val evalAbuelo = evaluaciones.find { it.metodo.contains("Abuelo") } ?: return

            val aciertos = evalAbuelo.aciertosNumeros
            val pesosActuales = memoriaIA.obtenerPesosAbuelo(tipoLoteria)
            val mediaAciertos = evaluaciones.map { it.aciertosNumeros }.average()

            // Señal directa: cuánto mejor/peor que la media
            val signal = (aciertos.toDouble() - mediaAciertos) / maxOf(mediaAciertos, 1.0)
            val uniforme = 1.0 / memoriaIA.ALGORITMOS_ABUELO.size

            val contribuciones = mutableMapOf<String, Double>()
            for (alg in memoriaIA.ALGORITMOS_ABUELO) {
                val peso = pesosActuales[alg] ?: uniforme
                // Amplificar/reducir la DESVIACIÓN del uniforme según el signal.
                // signal > 0 → los dominantes ganan más → diferenciación
                // signal < 0 → todos se mueven hacia uniforme → exploración
                // Gradiente real: (peso - uniforme) * signal ≠ 0 cuando pesos difieren entre algoritmos
                contribuciones[alg] = uniforme + (peso - uniforme) * (1.0 + signal)
            }

            LogAbuelo.gradiente("Abuelo", signal,
                "aciertos=$aciertos, media=${"%.2f".format(mediaAciertos)}")

            memoriaIA.actualizarPesosAbuelo(contribuciones, aciertos, tipoLoteria)
        } catch (e: Exception) {
            android.util.Log.e("GestorPredicciones", "Error actualizando aprendizaje Abuelo: ${e.message}", e)
        }
    }

    /**
     * Actualiza TODOS los sistemas de aprendizaje después de una evaluación:
     * 1. Pesos de características (desde resultado real)
     * 2. Pesos del ensemble (rendimiento por estrategia)
     * 3. Números exitosos
     * 4. Log resumen completo
     */
    private fun actualizarAprendizajeGeneral(
        tipoLoteria: String,
        evaluaciones: List<HistorialPredicciones.ResultadoEvaluacion>,
        numerosReales: List<Int>,
        cantidadNumeros: Int,
        maxNumero: Int,
        fechaSorteo: String
    ) {
        try {
            // 1. Actualizar pesos de características desde resultado real
            // Usar el mejor resultado como señal
            val mejorAciertos = evaluaciones.maxOf { it.aciertosNumeros }
            memoriaIA.actualizarPesosDesdeResultadoReal(mejorAciertos, cantidadNumeros, maxNumero, tipoLoteria)

            // 2. Mapear evaluaciones a EstrategiaPrediccion y registrar rendimiento
            val aciertosEstrategia = mutableMapOf<MotorInteligencia.EstrategiaPrediccion, Int>()
            for (eval in evaluaciones) {
                val estrategia = mapearMetodoAEstrategia(eval.metodo) ?: continue
                aciertosEstrategia[estrategia] = eval.aciertosNumeros
            }
            if (aciertosEstrategia.isNotEmpty()) {
                memoriaIA.registrarRendimientoEstrategias(aciertosEstrategia, tipoLoteria)
            }

            // 3. Registrar números exitosos con los aciertos del mejor método
            memoriaIA.registrarNumerosExitosos(numerosReales, mejorAciertos, tipoLoteria)

            // 4. Log de evaluaciones individuales
            for (eval in evaluaciones) {
                LogAbuelo.evaluacion(eval.metodo, eval.numerosPredichos, numerosReales, eval.aciertosNumeros)
            }

            // 5. Log resumen
            val evalsParaResumen = evaluaciones.map { eval ->
                Triple(eval.metodo, eval.aciertosNumeros, eval.numerosPredichos.filter { it in numerosReales })
            }
            LogAbuelo.resumen(
                tipoLoteria = tipoLoteria,
                fechaSorteo = fechaSorteo,
                numerosReales = numerosReales,
                evaluaciones = evalsParaResumen,
                pesosAbuelo = memoriaIA.obtenerPesosAbuelo(tipoLoteria),
                pesosCaracteristicas = memoriaIA.obtenerPesosCaracteristicas(tipoLoteria),
                entrenamientos = memoriaIA.obtenerEntrenamientosAbuelo(tipoLoteria)
            )
        } catch (e: Exception) {
            android.util.Log.e("GestorPredicciones", "Error en aprendizaje general: ${e.message}", e)
        }
    }

    /**
     * Mapea el nombre del método (de la predicción) a la EstrategiaPrediccion del ensemble.
     */
    private fun mapearMetodoAEstrategia(metodo: String): MotorInteligencia.EstrategiaPrediccion? {
        return when {
            // El Ensemble es la votación de todas: no es una estrategia en sí (antes sumaba al Genético)
            metodo.contains("Ensemble") || metodo.contains("🗳️") -> null
            metodo.contains("Calientes") -> MotorInteligencia.EstrategiaPrediccion.TENDENCIA
            metodo.contains("Perfil típico") -> null // método fijo, sin pesos que aprender
            metodo.contains("Debidos") -> MotorInteligencia.EstrategiaPrediccion.CICLOS
            metodo.contains("Alta Confianza") || metodo.contains("🎯") -> MotorInteligencia.EstrategiaPrediccion.ALTA_CONFIANZA
            metodo.contains("Rachas") || metodo.contains("🔥") -> MotorInteligencia.EstrategiaPrediccion.RACHAS_MIX
            metodo.contains("AG") || metodo.contains("🧬") -> MotorInteligencia.EstrategiaPrediccion.GENETICO
            metodo.contains("IA") || metodo.contains("🤖") -> MotorInteligencia.EstrategiaPrediccion.GENETICO
            metodo.contains("Frecuencias") || metodo.contains("Análisis") -> MotorInteligencia.EstrategiaPrediccion.FRECUENCIA
            metodo.contains("Fríos") -> MotorInteligencia.EstrategiaPrediccion.EQUILIBRIO
            // El aleatorio es el control: no debe mover pesos (antes sumaba a Tendencia)
            metodo.contains("Aleatorio") -> null
            metodo.contains("Abuelo") || metodo.contains("🔮") -> null // El Abuelo tiene su propio sistema
            else -> null
        }
    }

    private fun obtenerOGenerarInterno(
        tipoLoteria: TipoLoteria,
        rangoFechas: RangoFechas
    ): Resultado {
        val proximoSorteo = historialPredicciones.proximoSorteo(tipoLoteria.name)
        val fechaSorteo = proximoSorteo.toString()

        // Intentar cargar predicciones guardadas
        val prediccionesGuardadas = historialPredicciones.cargarPredicciones(tipoLoteria.name, fechaSorteo)

        if (prediccionesGuardadas != null && prediccionesGuardadas.isNotEmpty()) {
            // Detectar predicciones antiguas con reintegros todos iguales (bug pre-fix)
            val necesitaRegeneracion = when (tipoLoteria) {
                TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO, TipoLoteria.GORDO_PRIMITIVA -> {
                    val comps = prediccionesGuardadas.mapNotNull { it.complementarios.firstOrNull() }
                    comps.size >= 5 && comps.distinct().size <= 1
                }
                TipoLoteria.EUROMILLONES -> {
                    val comps = prediccionesGuardadas.map { it.complementarios }
                    comps.size >= 5 && comps.distinct().size <= 1
                }
                else -> false
            }

            if (necesitaRegeneracion) {
                historialPredicciones.eliminarPredicciones(tipoLoteria.name, fechaSorteo)
            } else {
                val fechaGen = historialPredicciones.fechaGeneracion(tipoLoteria.name, fechaSorteo) ?: "?"
                // Estadísticas frescas con un método rápido; las combinaciones son las guardadas
                val analisisBase = obtenerCombinacionesUseCase.ejecutar(
                    tipoLoteria = tipoLoteria,
                    numCombinaciones = 1,
                    rangoFechas = rangoFechas,
                    metodo = MetodoCalculo.ALEATORIO_PURO
                )
                return Resultado(
                    analisisBase.copy(
                        combinacionesSugeridas = prediccionesGuardadas,
                        fechaDesde = rangoFechas.desde,
                        fechaHasta = rangoFechas.hasta
                    ),
                    "Predicciones para sorteo del $fechaSorteo (generadas el $fechaGen)"
                )
            }
        }

        // Sin predicciones guardadas (o inválidas): generar con todos los métodos y guardar
        val analisis = obtenerCombinacionesUseCase.ejecutarTodosMetodos(
            tipoLoteria = tipoLoteria,
            rangoFechas = rangoFechas
        )
        // Nacional/Navidad/Niño no se evalúan por bolas, pero guardarlas no molesta
        historialPredicciones.guardarPredicciones(
            tipoLoteria.name, fechaSorteo, analisis.combinacionesSugeridas
        )
        return Resultado(analisis, "Predicciones nuevas para sorteo del $fechaSorteo")
    }
}
