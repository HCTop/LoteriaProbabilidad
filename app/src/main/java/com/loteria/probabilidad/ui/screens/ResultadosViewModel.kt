package com.loteria.probabilidad.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.data.repository.LoteriaRepository
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad
import com.loteria.probabilidad.domain.usecase.ObtenerCombinacionesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel para la pantalla de resultados.
 */
class ResultadosViewModel(
    private val obtenerCombinacionesUseCase: ObtenerCombinacionesUseCase,
    private val repository: LoteriaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResultadosUiState>(ResultadosUiState.Loading)
    val uiState: StateFlow<ResultadosUiState> = _uiState.asStateFlow()

    private val _rangoFechasSeleccionado = MutableStateFlow(OpcionRangoFechas.TODO_HISTORICO)
    val rangoFechasSeleccionado: StateFlow<OpcionRangoFechas> = _rangoFechasSeleccionado.asStateFlow()

    private val _metodoSeleccionado = MutableStateFlow(MetodoCalculo.IA_GENETICA)
    val metodoSeleccionado: StateFlow<MetodoCalculo> = _metodoSeleccionado.asStateFlow()

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
     */
    fun cargarResultados(tipoLoteria: TipoLoteria) {
        tipoLoteriaActual = tipoLoteria
        
        // Cargar históricos para backtesting
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (tipoLoteria) {
                    TipoLoteria.PRIMITIVA -> {
                        _historicoPrimitiva = repository.obtenerHistoricoPrimitiva()
                    }
                    TipoLoteria.BONOLOTO -> {
                        _historicoBonoloto = repository.obtenerHistoricoBonoloto()
                    }
                    TipoLoteria.EUROMILLONES -> {
                        _historicoEuromillones = repository.obtenerHistoricoEuromillones()
                    }
                    TipoLoteria.GORDO_PRIMITIVA -> {
                        _historicoGordo = repository.obtenerHistoricoGordoPrimitiva()
                    }
                    TipoLoteria.LOTERIA_NACIONAL -> {
                        _historicoNacional = repository.obtenerHistoricoNacional()
                    }
                    TipoLoteria.NAVIDAD -> {
                        _historicoNavidad = repository.obtenerHistoricoNavidad()
                    }
                    TipoLoteria.NINO -> {
                        _historicoNino = repository.obtenerHistoricoNino()
                    }
                }
            }
        }
        
        cargarConParametros(
            tipoLoteria,
            _rangoFechasSeleccionado.value.rango,
            _metodoSeleccionado.value
        )
    }

    /**
     * Cambia el rango de fechas y recarga los resultados.
     */
    fun cambiarRangoFechas(opcion: OpcionRangoFechas) {
        _rangoFechasSeleccionado.value = opcion
        tipoLoteriaActual?.let {
            cargarConParametros(it, opcion.rango, _metodoSeleccionado.value)
        }
    }

    /**
     * Cambia el método de cálculo y recarga los resultados.
     */
    fun cambiarMetodo(metodo: MetodoCalculo) {
        _metodoSeleccionado.value = metodo
        tipoLoteriaActual?.let {
            cargarConParametros(it, _rangoFechasSeleccionado.value.rango, metodo)
        }
    }

    /**
     * Carga los resultados con parámetros específicos.
     */
    private fun cargarConParametros(
        tipoLoteria: TipoLoteria,
        rangoFechas: RangoFechas,
        metodo: MetodoCalculo
    ) {
        _uiState.value = ResultadosUiState.Loading
        
        viewModelScope.launch {
            try {
                val analisis = withContext(Dispatchers.IO) {
                    obtenerCombinacionesUseCase.ejecutar(
                        tipoLoteria = tipoLoteria,
                        numCombinaciones = 5,
                        rangoFechas = rangoFechas,
                        metodo = metodo
                    )
                }
                _uiState.value = ResultadosUiState.Success(analisis)
            } catch (e: Exception) {
                _uiState.value = ResultadosUiState.Error(
                    "Error al analizar los datos: ${e.message}"
                )
            }
        }
    }

    /**
     * Regenera los resultados con los mismos parámetros.
     */
    fun regenerarResultados() {
        tipoLoteriaActual?.let { 
            cargarConParametros(
                it,
                _rangoFechasSeleccionado.value.rango,
                _metodoSeleccionado.value
            )
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
            val calculador = CalculadorProbabilidad()
            val useCase = ObtenerCombinacionesUseCase(repository, calculador)
            
            return ResultadosViewModel(useCase, repository) as T
        }
    }
}
