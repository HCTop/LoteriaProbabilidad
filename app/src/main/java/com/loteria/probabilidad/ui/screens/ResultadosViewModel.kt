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
import com.loteria.probabilidad.domain.ml.LogAbuelo
import com.loteria.probabilidad.domain.ml.MemoriaIA
import com.loteria.probabilidad.domain.ml.MotorInteligencia
import com.loteria.probabilidad.domain.usecase.GestorPredicciones
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
    private val memoriaIA: MemoriaIA,
    private val gestorPredicciones: GestorPredicciones
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

    // Relleno del historial con sorteos pasados simulados: (hechos, total) o null si no está en marcha
    private val _progresoRelleno = MutableStateFlow<Pair<Int, Int>?>(null)
    val progresoRelleno: StateFlow<Pair<Int, Int>?> = _progresoRelleno.asStateFlow()

    // Info de predicciones guardadas
    private val _prediccionesInfo = MutableStateFlow("")
    val prediccionesInfo: StateFlow<String> = _prediccionesInfo.asStateFlow()

    // Fórmula del Abuelo
    private val _formulaAbuelo = MutableStateFlow<ResultadoFormulaAbuelo?>(null)
    val formulaAbuelo: StateFlow<ResultadoFormulaAbuelo?> = _formulaAbuelo.asStateFlow()

    private val _boteActual = MutableStateFlow(0.0)
    val boteActual: StateFlow<Double> = _boteActual.asStateFlow()

    private val _formulaAbueloCargando = MutableStateFlow(false)
    val formulaAbueloCargando: StateFlow<Boolean> = _formulaAbueloCargando.asStateFlow()

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
     * Evalúa predicciones pendientes contra los resultados reales.
     * La lógica (y el aprendizaje asociado) vive en GestorPredicciones para que
     * la pantalla de inicio y esta usen exactamente lo mismo.
     */
    private fun evaluarPrediccionesPendientes(tipoLoteria: TipoLoteria) {
        gestorPredicciones.evaluarPendientes(tipoLoteria)
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
     * Obtiene predicciones guardadas o genera nuevas (ver GestorPredicciones).
     */
    private fun obtenerOGenerarPredicciones(
        tipoLoteria: TipoLoteria,
        rangoFechas: RangoFechas
    ): AnalisisProbabilidad {
        val resultado = gestorPredicciones.obtenerOGenerar(tipoLoteria, rangoFechas)
        _prediccionesInfo.value = resultado.info
        return resultado.analisis
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
            val diasSemana = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
            val proximoSorteo = historialPredicciones.proximoSorteo(tipoLoteria)
            _diaSemanaActual.value = diasSemana[proximoSorteo.dayOfWeek.value - 1]
            val topCount = when (tipoLoteria) {
                "EUROMILLONES", "GORDO_PRIMITIVA" -> 5
                else -> 6
            }
            _mejoresNumerosHoy.value = motorIA.obtenerMejoresNumerosParaDia(
                historico, maxNumero, proximoSorteo.dayOfWeek.value, topCount
            )
        } catch (e: Exception) {
            _mejoresNumerosHoy.value = emptyList()
        }
    }

    private fun cargarMejoresDigitosParaNacional(historico: List<ResultadoNacional>, tipoLoteria: String) {
        if (historico.isEmpty()) { _mejoresNumerosHoy.value = emptyList(); return }
        try {
            val diasSemana = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
            val proximoSorteo = historialPredicciones.proximoSorteo(tipoLoteria)
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

    // ==================== FÓRMULA DEL ABUELO ====================

    fun actualizarBote(bote: Double) {
        _boteActual.value = bote
    }

    fun ejecutarFormulaAbuelo() {
        val tipo = tipoLoteriaActual ?: return
        _formulaAbueloCargando.value = true
        viewModelScope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) {
                    obtenerCombinacionesUseCase.ejecutarFormulaAbuelo(
                        tipoLoteria = tipo,
                        rangoFechas = _rangoFechasSeleccionado.value.rango,
                        boteActual = _boteActual.value
                    )
                }
                _formulaAbuelo.value = resultado
            } catch (e: Exception) {
                android.util.Log.e("ResultadosVM", "Error en Fórmula del Abuelo: ${e.message}", e)
            } finally {
                _formulaAbueloCargando.value = false
            }
        }
    }

    // ==================== ACCIONES DEL USUARIO ====================

    /**
     * Rellena el historial con los últimos [cuantos] sorteos simulados (walk-forward),
     * para no tener que esperar semanas a que haya sorteos evaluados.
     */
    fun rellenarHistorial(cuantos: Int = 10) {
        val tipo = tipoLoteriaActual ?: return
        if (_progresoRelleno.value != null) return
        _progresoRelleno.value = 0 to cuantos
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    gestorPredicciones.rellenarHistorialSimulado(tipo, cuantos) { hechos, total ->
                        _progresoRelleno.value = hechos to total
                    }
                    _historialEvaluado.value = historialPredicciones.cargarHistorial(tipo.name)
                    _rankingMetodos.value = historialPredicciones.obtenerRankingMetodos(tipo.name)
                }
            } catch (e: Exception) {
                android.util.Log.e("ResultadosVM", "Error rellenando historial: ${e.message}", e)
            } finally {
                _progresoRelleno.value = null
            }
        }
    }

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
            _uiState.value = ResultadosUiState.Loading
            viewModelScope.launch {
                try {
                    val analisis = withContext(Dispatchers.IO) {
                        // Borrar + regenerar bajo el mismo candado (evita carreras con la pantalla de inicio)
                        val resultado = gestorPredicciones.regenerar(tipo, _rangoFechasSeleccionado.value.rango)
                        _prediccionesInfo.value = resultado.info
                        resultado.analisis
                    }
                    _uiState.value = ResultadosUiState.Success(analisis)
                    // Actualizar complementarios y estadísticas
                    withContext(Dispatchers.IO) {
                        cargarPrediccionComplementario(tipo)
                        cargarEstadisticasPredicciones(tipo.name)
                        _historialEvaluado.value = historialPredicciones.cargarHistorial(tipo.name)
                        _rankingMetodos.value = historialPredicciones.obtenerRankingMetodos(tipo.name)
                    }
                } catch (e: Exception) {
                    _uiState.value = ResultadosUiState.Error("Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Recarga ligera: solo regenera predicciones si el caché fue invalidado (ej. tras entrenamiento).
     * No recarga el histórico completo.
     */
    fun recargarSiNecesario() {
        // Si la carga inicial sigue en marcha todavía no hay caché: sin este guard,
        // el primer ON_RESUME lanzaba una SEGUNDA generación en paralelo.
        if (_uiState.value is ResultadosUiState.Loading) return
        tipoLoteriaActual?.let { tipo ->
            val proximoSorteo = historialPredicciones.proximoSorteo(tipo.name)
            val fechaSorteo = proximoSorteo.toString()
            val tieneCache = historialPredicciones.tienePrediccionesGuardadas(tipo.name, fechaSorteo)

            if (!tieneCache) {
                // El caché fue invalidado (por entrenamiento) → regenerar
                android.util.Log.d("ResultadosVM", "Caché invalidado, regenerando predicciones")
                regenerarResultados()
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
    /**
     * Usa applicationContext: el ViewModel sobrevive a la Activity (rotaciones),
     * y guardar el Context de la Activity la dejaría filtrada en memoria.
     */
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val context: Context = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dataSource = LoteriaLocalDataSource(context)
            val repository = LoteriaRepository(dataSource)
            val calculador = CalculadorProbabilidad(context)
            val useCase = ObtenerCombinacionesUseCase(repository, calculador)
            val motorIA = MotorInteligencia(context)
            val historialPredicciones = HistorialPredicciones(context)
            val memoriaIA = MemoriaIA(context)

            val gestor = GestorPredicciones(repository, useCase, historialPredicciones, memoriaIA)
            return ResultadosViewModel(useCase, repository, motorIA, historialPredicciones, memoriaIA, gestor) as T
        }
    }
}
