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

    companion object {
        private val candados = java.util.concurrent.ConcurrentHashMap<TipoLoteria, Any>()
        private fun candado(tipo: TipoLoteria): Any = candados.getOrPut(tipo) { Any() }

        /** Loterías de bolas: las únicas que se guardan y evalúan automáticamente. */
        val LOTERIAS_EVALUABLES = listOf(
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO,
            TipoLoteria.EUROMILLONES, TipoLoteria.GORDO_PRIMITIVA
        )

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
     * - Se salta los sorteos que ya tienen evaluación (real o simulada).
     *
     * @param progreso (hechos, total) para ir mostrando el avance
     * @return número de sorteos añadidos
     */
    fun rellenarHistorialSimulado(
        tipoLoteria: TipoLoteria,
        cuantos: Int = 10,
        progreso: (Int, Int) -> Unit = { _, _ -> }
    ): Int = synchronized(candado(tipoLoteria)) {
        if (tipoLoteria !in LOTERIAS_EVALUABLES) return@synchronized 0

        // (fecha, números, complementarios) del más antiguo al más reciente
        val sorteos = sorteosRecientes(tipoLoteria, cuantos).reversed()
        var anadidos = 0
        sorteos.forEachIndexed { i, (fecha, numeros, complementarios) ->
            progreso(i, sorteos.size)
            if (historialPredicciones.tieneEntradaHistorial(tipoLoteria.name, fecha)) return@forEachIndexed
            try {
                val hasta = java.time.LocalDate.parse(fecha).minusDays(1).toString()
                val analisis = obtenerCombinacionesUseCase.ejecutarTodosMetodos(
                    tipoLoteria = tipoLoteria,
                    rangoFechas = RangoFechas(null, hasta) // solo el pasado de ese sorteo
                )
                val evaluaciones = historialPredicciones.calcularEvaluaciones(
                    analisis.combinacionesSugeridas, numeros, complementarios
                )
                historialPredicciones.guardarEvaluacionSimulada(tipoLoteria.name, fecha, evaluaciones)
                anadidos++
            } catch (e: Exception) {
                android.util.Log.e("GestorPredicciones", "Simulación $fecha falló: ${e.message}", e)
            }
        }
        progreso(sorteos.size, sorteos.size)
        anadidos
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
