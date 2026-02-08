package com.loteria.probabilidad.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.data.repository.LoteriaRepository
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad
import com.loteria.probabilidad.domain.ml.HistorialPredicciones
import com.loteria.probabilidad.domain.ml.MemoriaIA
import com.loteria.probabilidad.domain.ml.MotorInteligencia
import com.loteria.probabilidad.domain.usecase.ObtenerCombinacionesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel para la pantalla de resultados.
 *
 * FLUJO DE PREDICCIONES PERSISTENTES:
 * 1. Al cargar una lotería → cargar histórico
 * 2. Evaluar predicciones pendientes contra resultados reales
 * 3. Comprobar si hay predicciones guardadas para el próximo sorteo
 * 4. Si SÍ → mostrar las guardadas (NO regenerar)
 * 5. Si NO → generar nuevas con todos los métodos, guardarlas, mostrarlas
 * 6. "Regenerar" → borra las guardadas y genera nuevas
 */
class ResultadosViewModel(
    private val obtenerCombinacionesUseCase: ObtenerCombinacionesUseCase,
    private val repository: LoteriaRepository,
    private val motorIA: MotorInteligencia,
    private val historialPredicciones: HistorialPredicciones,
    private val memoriaIA: MemoriaIA
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResultadosUiState>(ResultadosUiState.Loading)
    val uiState: StateFlow<ResultadosUiState> = _uiState.asStateFlow()

    private val _rangoFechasSeleccionado = MutableStateFlow(OpcionRangoFechas.TODO_HISTORICO)
    val rangoFechasSeleccionado: StateFlow<OpcionRangoFechas> = _rangoFechasSeleccionado.asStateFlow()

    // MEJORA 11: Mejores números para hoy
    private val _mejoresNumerosHoy = MutableStateFlow<List<Pair<Int, Double>>>(emptyList())
    val mejoresNumerosHoy: StateFlow<List<Pair<Int, Double>>> = _mejoresNumerosHoy.asStateFlow()

    private val _diaSemanaActual = MutableStateFlow("")
    val diaSemanaActual: StateFlow<String> = _diaSemanaActual.asStateFlow()

    // MEJORA 13: Estadísticas de predicciones
    private val _estadisticasPredicciones = MutableStateFlow<Map<String, Any>>(emptyMap())
    val estadisticasPredicciones: StateFlow<Map<String, Any>> = _estadisticasPredicciones.asStateFlow()

    // MEJORA 11B: Predicción de complementario para próximo sorteo
    private val _prediccionComplementario = MutableStateFlow<MotorInteligencia.PrediccionComplementarioDia?>(null)
    val prediccionComplementario: StateFlow<MotorInteligencia.PrediccionComplementarioDia?> = _prediccionComplementario.asStateFlow()

    // Para estrellas (Euromillones tiene 2)
    private val _prediccionEstrellas = MutableStateFlow<List<MotorInteligencia.PrediccionComplementarioDia>>(emptyList())
    val prediccionEstrellas: StateFlow<List<MotorInteligencia.PrediccionComplementarioDia>> = _prediccionEstrellas.asStateFlow()

    // Historial de evaluaciones (últimos sorteos evaluados con aciertos)
    private val _historialEvaluado = MutableStateFlow<List<HistorialPredicciones.EntradaHistorial>>(emptyList())
    val historialEvaluado: StateFlow<List<HistorialPredicciones.EntradaHistorial>> = _historialEvaluado.asStateFlow()

    // Ranking de métodos por rendimiento
    private val _rankingMetodos = MutableStateFlow<List<Triple<String, Double, Int>>>(emptyList())
    val rankingMetodos: StateFlow<List<Triple<String, Double, Int>>> = _rankingMetodos.asStateFlow()

    // Info de predicciones guardadas
    private val _prediccionesInfo = MutableStateFlow("")
    val prediccionesInfo: StateFlow<String> = _prediccionesInfo.asStateFlow()

    private var tipoLoteriaActual: TipoLoteria? = null

    // Históricos cacheados para backtesting
    private var _historicoPrimitiva: List<ResultadoPrimitiva> = emptyList()
    private var _historicoBonoloto: List<ResultadoPrimitiva> = emptyList()
    private var _historicoEuromillones: List<ResultadoEuromillones> = emptyList()
    private var _historicoGordo: List<ResultadoGordoPrimitiva> = emptyList()
    private var _historicoNacional: List<ResultadoNacional> = emptyList()
    private var _historicoNavidad: List<ResultadoNavidad> = emptyList()
    private var _historicoNino: List<ResultadoNacional> = emptyList()

    /**
     * Carga los resultados para un tipo de lotería.
     * Flujo secuencial: cargar histórico → evaluar pendientes → obtener/generar predicciones.
     */
    fun cargarResultados(tipoLoteria: TipoLoteria) {
        tipoLoteriaActual = tipoLoteria
        _uiState.value = ResultadosUiState.Loading

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // ═══ PASO 1: Cargar datos históricos ═══
                    android.util.Log.d("ResultadosVM", "Cargando datos para $tipoLoteria")
                    cargarHistorico(tipoLoteria)

                    // ═══ PASO 2: Evaluar predicciones pendientes contra resultados reales ═══
                    evaluarPrediccionesPendientes(tipoLoteria)

                    // ═══ PASO 3: Cargar datos adicionales ═══
                    cargarDatosAdicionales(tipoLoteria)

                    // ═══ PASO 4: Cargar historial de evaluaciones y ranking ═══
                    _historialEvaluado.value = historialPredicciones.cargarHistorial(tipoLoteria.name)
                    _rankingMetodos.value = historialPredicciones.obtenerRankingMetodos(tipoLoteria.name)

                    // ═══ PASO 5: Obtener o generar predicciones para el próximo sorteo ═══
                    val analisis = obtenerOGenerarPredicciones(
                        tipoLoteria, _rangoFechasSeleccionado.value.rango
                    )
                    _uiState.value = ResultadosUiState.Success(analisis)

                    // Limpiar predicciones muy antiguas
                    historialPredicciones.limpiarPrediccionesAntiguas(tipoLoteria.name)
                }
            } catch (e: Exception) {
                android.util.Log.e("ResultadosVM", "Error cargando datos: ${e.message}", e)
                _uiState.value = ResultadosUiState.Error(
                    "Error al analizar los datos: ${e.message}"
                )
            }
        }
    }

    /**
     * Carga el histórico según el tipo de lotería.
     */
    private fun cargarHistorico(tipoLoteria: TipoLoteria) {
        when (tipoLoteria) {
            TipoLoteria.PRIMITIVA -> {
                _historicoPrimitiva = repository.obtenerHistoricoPrimitiva()
                android.util.Log.d("ResultadosVM", "Historico Primitiva: ${_historicoPrimitiva.size} sorteos")
            }
            TipoLoteria.BONOLOTO -> {
                _historicoBonoloto = repository.obtenerHistoricoBonoloto()
                android.util.Log.d("ResultadosVM", "Historico Bonoloto: ${_historicoBonoloto.size} sorteos")
            }
            TipoLoteria.EUROMILLONES -> {
                _historicoEuromillones = repository.obtenerHistoricoEuromillones()
                android.util.Log.d("ResultadosVM", "Historico Euromillones: ${_historicoEuromillones.size} sorteos")
            }
            TipoLoteria.GORDO_PRIMITIVA -> {
                _historicoGordo = repository.obtenerHistoricoGordoPrimitiva()
                android.util.Log.d("ResultadosVM", "Historico Gordo: ${_historicoGordo.size} sorteos")
            }
            TipoLoteria.LOTERIA_NACIONAL -> {
                _historicoNacional = repository.obtenerHistoricoNacional()
                android.util.Log.d("ResultadosVM", "Historico Nacional: ${_historicoNacional.size} sorteos")
            }
            TipoLoteria.NAVIDAD -> {
                _historicoNavidad = repository.obtenerHistoricoNavidad()
                android.util.Log.d("ResultadosVM", "Historico Navidad: ${_historicoNavidad.size} sorteos")
            }
            TipoLoteria.NINO -> {
                _historicoNino = repository.obtenerHistoricoNino()
                android.util.Log.d("ResultadosVM", "Historico Niño: ${_historicoNino.size} sorteos")
            }
        }
    }

    /**
     * Evalúa predicciones pendientes contra los resultados reales del histórico.
     * Recorre los últimos 10 sorteos del histórico buscando predicciones guardadas sin evaluar.
     * Además, actualiza los pesos del Método del Abuelo si hay evaluaciones nuevas.
     */
    private fun evaluarPrediccionesPendientes(tipoLoteria: TipoLoteria) {
        if (!historialPredicciones.soportaEvaluacion(tipoLoteria.name)) return

        try {
            when (tipoLoteria) {
                TipoLoteria.PRIMITIVA -> {
                    for (sorteo in _historicoPrimitiva.take(10)) {
                        val evaluaciones = historialPredicciones.evaluarPredicciones(
                            tipoLoteria.name, sorteo.fecha,
                            sorteo.numeros, listOf(sorteo.reintegro)
                        )
                        if (evaluaciones != null) {
                            actualizarAprendizajeAbuelo(tipoLoteria.name, evaluaciones)
                            android.util.Log.d("ResultadosVM",
                                "Evaluado ${sorteo.fecha}: mejor=${evaluaciones.maxOf { it.aciertosNumeros }} aciertos")
                        }
                    }
                }
                TipoLoteria.BONOLOTO -> {
                    for (sorteo in _historicoBonoloto.take(10)) {
                        val evaluaciones = historialPredicciones.evaluarPredicciones(
                            tipoLoteria.name, sorteo.fecha,
                            sorteo.numeros, listOf(sorteo.reintegro)
                        )
                        if (evaluaciones != null) {
                            actualizarAprendizajeAbuelo(tipoLoteria.name, evaluaciones)
                        }
                    }
                }
                TipoLoteria.EUROMILLONES -> {
                    for (sorteo in _historicoEuromillones.take(10)) {
                        val evaluaciones = historialPredicciones.evaluarPredicciones(
                            tipoLoteria.name, sorteo.fecha,
                            sorteo.numeros, sorteo.estrellas
                        )
                        if (evaluaciones != null) {
                            actualizarAprendizajeAbuelo(tipoLoteria.name, evaluaciones)
                        }
                    }
                }
                TipoLoteria.GORDO_PRIMITIVA -> {
                    for (sorteo in _historicoGordo.take(10)) {
                        val evaluaciones = historialPredicciones.evaluarPredicciones(
                            tipoLoteria.name, sorteo.fecha,
                            sorteo.numeros, listOf(sorteo.numeroClave)
                        )
                        if (evaluaciones != null) {
                            actualizarAprendizajeAbuelo(tipoLoteria.name, evaluaciones)
                        }
                    }
                }
                else -> { /* Nacional, Navidad, Niño no se evalúan por bolas */ }
            }
        } catch (e: Exception) {
            android.util.Log.e("ResultadosVM", "Error evaluando predicciones: ${e.message}", e)
        }
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

            // Calcular contribución: para cada algoritmo, ver cuántos de sus números acertaron
            // Como no tenemos desglose por algoritmo, usamos la evaluación general como señal
            val aciertos = evalAbuelo.aciertosNumeros
            val pesosActuales = memoriaIA.obtenerPesosAbuelo(tipoLoteria)

            // Si el Abuelo acertó más que la media de todos los métodos, reforzar pesos actuales
            val mediaAciertos = evaluaciones.map { it.aciertosNumeros }.average()

            val contribuciones = pesosActuales.toMutableMap()
            if (aciertos > mediaAciertos) {
                // Reforzar: los pesos actuales funcionaron bien, mantenerlos
                // (las contribuciones iguales a los pesos = gradiente ~0 = sin cambio grande)
            } else if (aciertos < mediaAciertos) {
                // Necesita ajuste: perturbar los pesos para explorar
                // Dar más peso a los algoritmos proporcionalmente a 1/peso (los menos usados)
                val sumInverso = pesosActuales.values.sumOf { 1.0 / it.coerceAtLeast(0.01) }
                for (alg in memoriaIA.ALGORITMOS_ABUELO) {
                    val pesoActual = pesosActuales[alg] ?: 0.2
                    contribuciones[alg] = (1.0 / pesoActual.coerceAtLeast(0.01)) / sumInverso
                }
            }

            memoriaIA.actualizarPesosAbuelo(contribuciones, aciertos, tipoLoteria)
            android.util.Log.d("ResultadosVM",
                "Abuelo aprendizaje: aciertos=$aciertos, media=${"%.1f".format(mediaAciertos)}, " +
                "pesos=${memoriaIA.obtenerPesosAbuelo(tipoLoteria).map { "${it.key}=${"%.3f".format(it.value)}" }}")
        } catch (e: Exception) {
            android.util.Log.e("ResultadosVM", "Error actualizando aprendizaje Abuelo: ${e.message}", e)
        }
    }

    /**
     * Carga datos adicionales (mejores números, complementarios, estadísticas).
     */
    private fun cargarDatosAdicionales(tipoLoteria: TipoLoteria) {
        when (tipoLoteria) {
            TipoLoteria.PRIMITIVA -> {
                cargarMejoresNumerosHoy(_historicoPrimitiva, 49, tipoLoteria.name)
                cargarPrediccionComplementario(tipoLoteria)
            }
            TipoLoteria.BONOLOTO -> {
                cargarMejoresNumerosHoy(_historicoBonoloto, 49, tipoLoteria.name)
                cargarPrediccionComplementario(tipoLoteria)
            }
            TipoLoteria.EUROMILLONES -> {
                val convertido = _historicoEuromillones.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }
                cargarMejoresNumerosHoy(convertido, 50, tipoLoteria.name)
                cargarPrediccionComplementario(tipoLoteria)
            }
            TipoLoteria.GORDO_PRIMITIVA -> {
                val convertido = _historicoGordo.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }
                cargarMejoresNumerosHoy(convertido, 54, tipoLoteria.name)
                cargarPrediccionComplementario(tipoLoteria)
            }
            TipoLoteria.LOTERIA_NACIONAL -> {
                cargarMejoresDigitosParaNacional(_historicoNacional, tipoLoteria.name)
            }
            TipoLoteria.NAVIDAD -> {
                cargarMejoresDigitosParaNavidad(_historicoNavidad)
            }
            TipoLoteria.NINO -> {
                cargarMejoresDigitosParaNacional(_historicoNino, tipoLoteria.name)
            }
        }
        cargarEstadisticasPredicciones(tipoLoteria.name)
    }

    /**
     * Obtiene predicciones guardadas o genera nuevas.
     * - Si hay predicciones guardadas para el próximo sorteo → las usa
     * - Si no → genera nuevas con todos los métodos y las guarda
     */
    private fun obtenerOGenerarPredicciones(
        tipoLoteria: TipoLoteria,
        rangoFechas: RangoFechas
    ): AnalisisProbabilidad {
        val proximoSorteo = historialPredicciones.proximoSorteo(tipoLoteria.name)
        val fechaSorteo = proximoSorteo.toString()

        // Intentar cargar predicciones guardadas
        val prediccionesGuardadas = historialPredicciones.cargarPredicciones(tipoLoteria.name, fechaSorteo)

        if (prediccionesGuardadas != null) {
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
                android.util.Log.d("ResultadosVM",
                    "Predicciones con complementarios duplicados detectadas, forzando regeneración")
                historialPredicciones.eliminarPredicciones(tipoLoteria.name, fechaSorteo)
                // Caer al else para regenerar
            } else {
                android.util.Log.d("ResultadosVM",
                    "Usando predicciones guardadas para $fechaSorteo (${prediccionesGuardadas.size} combinaciones)")

                val fechaGen = historialPredicciones.fechaGeneracion(tipoLoteria.name, fechaSorteo) ?: "?"
                _prediccionesInfo.value = "Predicciones para sorteo del $fechaSorteo (generadas el $fechaGen)"

                // Obtener estadísticas frescas con un método rápido (LAPLACE)
                val analisisBase = obtenerCombinacionesUseCase.ejecutar(
                    tipoLoteria = tipoLoteria,
                    numCombinaciones = 1,
                    rangoFechas = rangoFechas,
                    metodo = MetodoCalculo.LAPLACE
                )
                // Reemplazar las combinaciones con las guardadas
                return analisisBase.copy(
                    combinacionesSugeridas = prediccionesGuardadas,
                    fechaDesde = rangoFechas.desde,
                    fechaHasta = rangoFechas.hasta
                )
            }
        }

        // Sin predicciones guardadas o necesita regeneración
        android.util.Log.d("ResultadosVM",
            "Generando predicciones nuevas para $fechaSorteo")

        _prediccionesInfo.value = "Predicciones nuevas para sorteo del $fechaSorteo"

        // Generar con todos los métodos
        val analisis = obtenerCombinacionesUseCase.ejecutarTodosMetodos(
            tipoLoteria = tipoLoteria,
            rangoFechas = rangoFechas
        )

        // Guardar las predicciones
        historialPredicciones.guardarPredicciones(
            tipoLoteria.name, fechaSorteo, analisis.combinacionesSugeridas
        )

        return analisis
    }

    // ==================== MEJORA 11: Mejores números para hoy ====================

    private fun cargarMejoresNumerosHoy(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        tipoLoteria: String
    ) {
        if (historico.isEmpty()) {
            _mejoresNumerosHoy.value = emptyList()
            return
        }
        try {
            val diasSorteoPorLoteria = mapOf(
                "PRIMITIVA" to listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.SATURDAY),
                "BONOLOTO" to listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY),
                "EUROMILLONES" to listOf(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.FRIDAY),
                "GORDO_PRIMITIVA" to listOf(java.time.DayOfWeek.SUNDAY),
                "LOTERIA_NACIONAL" to listOf(java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.SATURDAY)
            )
            val hoy = java.time.LocalDate.now()
            val diasSorteo = diasSorteoPorLoteria[tipoLoteria]
            val diasSemana = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
            val proximoSorteo = if (diasSorteo != null) {
                (0L..7L).map { hoy.plusDays(it) }.first { it.dayOfWeek in diasSorteo }
            } else hoy
            _diaSemanaActual.value = diasSemana[proximoSorteo.dayOfWeek.value - 1]
            _mejoresNumerosHoy.value = motorIA.obtenerMejoresNumerosParaDia(
                historico, maxNumero, proximoSorteo.dayOfWeek.value, 8
            )
        } catch (e: Exception) {
            _mejoresNumerosHoy.value = emptyList()
        }
    }

    private fun cargarMejoresDigitosParaNacional(historico: List<ResultadoNacional>, tipoLoteria: String) {
        if (historico.isEmpty()) { _mejoresNumerosHoy.value = emptyList(); return }
        try {
            val diasSorteoPorLoteria = mapOf(
                "LOTERIA_NACIONAL" to listOf(java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.SATURDAY),
                "NINO" to listOf(java.time.DayOfWeek.MONDAY)
            )
            val hoy = java.time.LocalDate.now()
            val diasSorteo = diasSorteoPorLoteria[tipoLoteria]
            val diasSemana = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
            val proximoSorteo = if (diasSorteo != null && diasSorteo.isNotEmpty()) {
                (0L..7L).map { hoy.plusDays(it) }.first { it.dayOfWeek in diasSorteo }
            } else hoy
            _diaSemanaActual.value = diasSemana[proximoSorteo.dayOfWeek.value - 1]
            val frecuenciaPorPosicion = Array(5) { mutableMapOf<Int, Int>() }
            historico.forEach { sorteo ->
                val numero = sorteo.primerPremio.filter { it.isDigit() }.padStart(5, '0')
                numero.forEachIndexed { pos, digito ->
                    if (pos < 5) {
                        val d = digito.digitToInt()
                        frecuenciaPorPosicion[pos][d] = (frecuenciaPorPosicion[pos][d] ?: 0) + 1
                    }
                }
            }
            _mejoresNumerosHoy.value = frecuenciaPorPosicion.mapIndexed { pos, freqs ->
                val total = freqs.values.sum().toDouble()
                val mejor = freqs.maxByOrNull { it.value }
                if (mejor != null && total > 0) Pair(mejor.key, mejor.value / total) else Pair(pos, 0.1)
            }
        } catch (e: Exception) {
            _mejoresNumerosHoy.value = emptyList()
        }
    }

    private fun cargarMejoresDigitosParaNavidad(historico: List<ResultadoNavidad>) {
        if (historico.isEmpty()) { _mejoresNumerosHoy.value = emptyList(); return }
        try {
            _diaSemanaActual.value = "22 Dic"
            val frecuenciaPorPosicion = Array(5) { mutableMapOf<Int, Int>() }
            historico.forEach { sorteo ->
                val numero = sorteo.gordo.filter { it.isDigit() }.padStart(5, '0')
                numero.forEachIndexed { pos, digito ->
                    if (pos < 5) {
                        val d = digito.digitToInt()
                        frecuenciaPorPosicion[pos][d] = (frecuenciaPorPosicion[pos][d] ?: 0) + 1
                    }
                }
            }
            _mejoresNumerosHoy.value = frecuenciaPorPosicion.mapIndexed { pos, freqs ->
                val total = freqs.values.sum().toDouble()
                val mejor = freqs.maxByOrNull { it.value }
                if (mejor != null && total > 0) Pair(mejor.key, mejor.value / total) else Pair(pos, 0.1)
            }
        } catch (e: Exception) {
            _mejoresNumerosHoy.value = emptyList()
        }
    }

    private fun cargarEstadisticasPredicciones(tipoLoteria: String) {
        try {
            _estadisticasPredicciones.value = motorIA.calcularEstadisticasPredicciones(tipoLoteria)
        } catch (e: Exception) {
            _estadisticasPredicciones.value = emptyMap()
        }
    }

    private fun cargarPrediccionComplementario(tipoLoteria: TipoLoteria) {
        try {
            when (tipoLoteria) {
                TipoLoteria.PRIMITIVA -> {
                    _prediccionComplementario.value = motorIA.predecirReintegroParaProximoSorteo(_historicoPrimitiva, tipoLoteria.name)
                    _prediccionEstrellas.value = emptyList()
                }
                TipoLoteria.BONOLOTO -> {
                    _prediccionComplementario.value = motorIA.predecirReintegroParaProximoSorteo(_historicoBonoloto, tipoLoteria.name)
                    _prediccionEstrellas.value = emptyList()
                }
                TipoLoteria.EUROMILLONES -> {
                    _prediccionComplementario.value = null
                    _prediccionEstrellas.value = motorIA.predecirEstrellasParaProximoSorteo(_historicoEuromillones)
                }
                TipoLoteria.GORDO_PRIMITIVA -> {
                    _prediccionComplementario.value = motorIA.predecirClaveParaProximoSorteo(_historicoGordo)
                    _prediccionEstrellas.value = emptyList()
                }
                else -> {
                    _prediccionComplementario.value = null
                    _prediccionEstrellas.value = emptyList()
                }
            }
        } catch (e: Exception) {
            _prediccionComplementario.value = null
            _prediccionEstrellas.value = emptyList()
        }
    }

    // ==================== ACCIONES DEL USUARIO ====================

    fun analizarRareza(combinacion: List<Int>, maxNumero: Int): MotorInteligencia.AnalisisRareza {
        return motorIA.analizarRarezaCombinacion(combinacion, maxNumero)
    }

    fun cambiarRangoFechas(opcion: OpcionRangoFechas) {
        _rangoFechasSeleccionado.value = opcion
        // Cambiar rango solo actualiza estadísticas, NO regenera predicciones
        // Las predicciones guardadas se mantienen
        tipoLoteriaActual?.let { tipo ->
            viewModelScope.launch {
                try {
                    val analisis = withContext(Dispatchers.IO) {
                        obtenerOGenerarPredicciones(tipo, opcion.rango)
                    }
                    _uiState.value = ResultadosUiState.Success(analisis)
                } catch (e: Exception) {
                    _uiState.value = ResultadosUiState.Error("Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Regenera las predicciones FORZANDO nuevas (borra las guardadas).
     */
    fun regenerarResultados() {
        tipoLoteriaActual?.let { tipo ->
            val proximoSorteo = historialPredicciones.proximoSorteo(tipo.name)
            // Borrar predicciones guardadas para forzar regeneración
            historialPredicciones.eliminarPredicciones(tipo.name, proximoSorteo.toString())
            android.util.Log.d("ResultadosVM", "Forzando regeneración para $proximoSorteo")

            _uiState.value = ResultadosUiState.Loading
            viewModelScope.launch {
                try {
                    val analisis = withContext(Dispatchers.IO) {
                        obtenerOGenerarPredicciones(tipo, _rangoFechasSeleccionado.value.rango)
                    }
                    _uiState.value = ResultadosUiState.Success(analisis)
                } catch (e: Exception) {
                    _uiState.value = ResultadosUiState.Error("Error: ${e.message}")
                }
            }
        }
    }

    // Getters para históricos (para backtesting)
    fun getHistoricoPrimitiva(): List<ResultadoPrimitiva> = _historicoPrimitiva
    fun getHistoricoBonoloto(): List<ResultadoPrimitiva> = _historicoBonoloto
    fun getHistoricoEuromillones(): List<ResultadoEuromillones> = _historicoEuromillones
    fun getHistoricoGordo(): List<ResultadoGordoPrimitiva> = _historicoGordo
    fun getHistoricoNacional(): List<ResultadoNacional> = _historicoNacional
    fun getHistoricoNavidad(): List<ResultadoNavidad> = _historicoNavidad
    fun getHistoricoNino(): List<ResultadoNacional> = _historicoNino

    /**
     * Factory para crear el ViewModel con dependencias.
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dataSource = LoteriaLocalDataSource(context)
            val repository = LoteriaRepository(dataSource)
            val calculador = CalculadorProbabilidad(context)
            val useCase = ObtenerCombinacionesUseCase(repository, calculador)
            val motorIA = MotorInteligencia(context)
            val historialPredicciones = HistorialPredicciones(context)
            val memoriaIA = MemoriaIA(context)

            return ResultadosViewModel(useCase, repository, motorIA, historialPredicciones, memoriaIA) as T
        }
    }
}
