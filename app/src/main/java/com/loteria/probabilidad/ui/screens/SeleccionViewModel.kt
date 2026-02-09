package com.loteria.probabilidad.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.data.repository.LoteriaRepository
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad
import com.loteria.probabilidad.domain.ml.MotorInteligencia
import com.loteria.probabilidad.domain.usecase.ObtenerCombinacionesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Información de predicción para mostrar en la pantalla de selección.
 */
data class PrediccionLoteria(
    val tipoLoteria: TipoLoteria,
    val mejorMetodo: MetodoCalculo,
    val tasaAcierto: Double,
    val numerosPredichos: List<Int>,
    val complementarioPredicho: Int? = null,
    val complementarioPredicho2: Int? = null,
    val proximoDiaSorteo: String,
    val cargando: Boolean = false,
    // Nuevo: último sorteo
    val ultimoSorteoNumeros: List<Int> = emptyList(),
    val ultimoSorteoFecha: String = "",
    val ultimoSorteoComplementario: Int? = null,
    val ultimoSorteoComplementario2: Int? = null,
    // Nuevo: mejor método en último sorteo
    val metodoMejorAcierto: MetodoCalculo? = null,
    val aciertosDelMejorMetodo: Int = 0,
    val numerosAcertadosMejorMetodo: List<Int> = emptyList()
)

/**
 * Info del último sorteo.
 */
private data class UltimoSorteoInfo(
    val numeros: List<Int>,
    val fecha: String,
    val comp1: Int?,
    val comp2: Int?
)

/**
 * ViewModel para la pantalla de selección de loterías.
 */
class SeleccionViewModel(
    private val repository: LoteriaRepository,
    private val motorIA: MotorInteligencia,
    private val obtenerCombinacionesUseCase: ObtenerCombinacionesUseCase
) : ViewModel() {

    private val _predicciones = MutableStateFlow<Map<TipoLoteria, PrediccionLoteria>>(emptyMap())
    val predicciones: StateFlow<Map<TipoLoteria, PrediccionLoteria>> = _predicciones.asStateFlow()

    private val _cargando = MutableStateFlow(true)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    init {
        cargarPredicciones()
    }

    fun cargarPredicciones() {
        viewModelScope.launch {
            _cargando.value = true

            try {
                withContext(Dispatchers.IO) {
                    val prediccionesMap = mutableMapOf<TipoLoteria, PrediccionLoteria>()

                    for (tipo in TipoLoteria.entries) {
                        try {
                            val prediccion = calcularPrediccionParaLoteria(tipo)
                            prediccionesMap[tipo] = prediccion
                        } catch (e: Exception) {
                            android.util.Log.e("SeleccionVM", "Error para $tipo: ${e.message}")
                        }
                    }

                    _predicciones.value = prediccionesMap
                }
            } catch (e: Exception) {
                android.util.Log.e("SeleccionVM", "Error general: ${e.message}")
            } finally {
                _cargando.value = false
            }
        }
    }

    private suspend fun calcularPrediccionParaLoteria(tipo: TipoLoteria): PrediccionLoteria {
        val diasSemana = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val hoy = java.time.LocalDate.now()

        // Próximo día de sorteo
        val diasSorteo = obtenerDiasSorteo(tipo)
        val proximoSorteo = if (diasSorteo.isNotEmpty()) {
            (0L..7L).map { hoy.plusDays(it) }
                .first { it.dayOfWeek.value in diasSorteo }
        } else {
            hoy
        }
        val proximoDiaNombre = diasSemana[proximoSorteo.dayOfWeek.value - 1]

        // Obtener último sorteo
        val (ultimoNumeros, ultimoFecha, ultimoComp1, ultimoComp2) = obtenerUltimoSorteo(tipo)

        // Evaluar qué método habría acertado más en el último sorteo
        val (metodoMejor, aciertos, numerosAcertados) = evaluarMejorMetodoEnUltimoSorteo(tipo, ultimoNumeros)

        // Usar el método que mejor funcionó, o ENSEMBLE_VOTING por defecto
        val mejorMetodo = metodoMejor ?: MetodoCalculo.ENSEMBLE_VOTING

        // Obtener tasa de acierto del historial si existe
        val stats = motorIA.calcularEstadisticasPredicciones(tipo.name)
        val tasaAcierto = ((stats["promedioAciertos"] as? Double) ?: 0.0) * 100 / 6

        // Obtener predicción para el próximo sorteo
        val analisis = obtenerCombinacionesUseCase.ejecutar(
            tipoLoteria = tipo,
            numCombinaciones = 1,
            rangoFechas = RangoFechas(null, null),
            metodo = mejorMetodo
        )

        // Para Nacional/Navidad/Niño, convertir el número a dígitos individuales
        val numerosPredichos = if (tipo in listOf(TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO)) {
            // El análisis devuelve números de 5 dígitos, los convertimos a dígitos individuales
            val combinacion = analisis.combinacionesSugeridas.firstOrNull()?.numeros ?: emptyList()
            if (combinacion.isNotEmpty()) {
                // Si el primer número es grande (>9), es un número de 5 dígitos
                val primerNumero = combinacion.first()
                if (primerNumero > 9) {
                    primerNumero.toString().padStart(5, '0').map { it.digitToInt() }
                } else {
                    // Ya son dígitos individuales
                    combinacion.take(5)
                }
            } else emptyList()
        } else {
            analisis.combinacionesSugeridas.firstOrNull()?.numeros ?: emptyList()
        }

        // Complementarios predichos
        val (comp1, comp2) = obtenerComplementarioPredicho(tipo)

        return PrediccionLoteria(
            tipoLoteria = tipo,
            mejorMetodo = mejorMetodo,
            tasaAcierto = tasaAcierto,
            numerosPredichos = numerosPredichos,
            complementarioPredicho = comp1,
            complementarioPredicho2 = comp2,
            proximoDiaSorteo = proximoDiaNombre,
            ultimoSorteoNumeros = ultimoNumeros,
            ultimoSorteoFecha = ultimoFecha,
            ultimoSorteoComplementario = ultimoComp1,
            ultimoSorteoComplementario2 = ultimoComp2,
            metodoMejorAcierto = metodoMejor,
            aciertosDelMejorMetodo = aciertos,
            numerosAcertadosMejorMetodo = numerosAcertados
        )
    }

    /**
     * Obtiene el último sorteo de una lotería.
     */
    private fun obtenerUltimoSorteo(tipo: TipoLoteria): UltimoSorteoInfo {
        return try {
            when (tipo) {
                TipoLoteria.PRIMITIVA -> {
                    val historico = repository.obtenerHistoricoPrimitiva()
                    if (historico.isNotEmpty()) {
                        val ultimo = historico.first()
                        UltimoSorteoInfo(ultimo.numeros, ultimo.fecha, ultimo.reintegro, null)
                    } else UltimoSorteoInfo(emptyList(), "", null, null)
                }
                TipoLoteria.BONOLOTO -> {
                    val historico = repository.obtenerHistoricoBonoloto()
                    if (historico.isNotEmpty()) {
                        val ultimo = historico.first()
                        UltimoSorteoInfo(ultimo.numeros, ultimo.fecha, ultimo.reintegro, null)
                    } else UltimoSorteoInfo(emptyList(), "", null, null)
                }
                TipoLoteria.EUROMILLONES -> {
                    val historico = repository.obtenerHistoricoEuromillones()
                    if (historico.isNotEmpty()) {
                        val ultimo = historico.first()
                        val estrella1 = ultimo.estrellas.getOrNull(0)
                        val estrella2 = ultimo.estrellas.getOrNull(1)
                        UltimoSorteoInfo(ultimo.numeros, ultimo.fecha, estrella1, estrella2)
                    } else UltimoSorteoInfo(emptyList(), "", null, null)
                }
                TipoLoteria.GORDO_PRIMITIVA -> {
                    val historico = repository.obtenerHistoricoGordoPrimitiva()
                    if (historico.isNotEmpty()) {
                        val ultimo = historico.first()
                        UltimoSorteoInfo(ultimo.numeros, ultimo.fecha, ultimo.numeroClave, null)
                    } else UltimoSorteoInfo(emptyList(), "", null, null)
                }
                TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NINO -> {
                    val historico = if (tipo == TipoLoteria.LOTERIA_NACIONAL)
                        repository.obtenerHistoricoNacional()
                    else
                        repository.obtenerHistoricoNino()
                    if (historico.isNotEmpty()) {
                        val ultimo = historico.first()
                        // Convertir número de 5 dígitos a lista
                        val numeros = ultimo.primerPremio.filter { c -> c.isDigit() }.map { c -> c.digitToInt() }
                        UltimoSorteoInfo(numeros, ultimo.fecha, ultimo.reintegros.firstOrNull(), null)
                    } else UltimoSorteoInfo(emptyList(), "", null, null)
                }
                TipoLoteria.NAVIDAD -> {
                    val historico = repository.obtenerHistoricoNavidad()
                    if (historico.isNotEmpty()) {
                        val ultimo = historico.first()
                        val numeros = ultimo.gordo.filter { c -> c.isDigit() }.map { c -> c.digitToInt() }
                        UltimoSorteoInfo(numeros, ultimo.fecha, null, null)
                    } else UltimoSorteoInfo(emptyList(), "", null, null)
                }
            }
        } catch (e: Exception) {
            UltimoSorteoInfo(emptyList(), "", null, null)
        }
    }

    /**
     * Evalúa qué método habría acertado más números en el último sorteo.
     */
    private fun evaluarMejorMetodoEnUltimoSorteo(
        tipo: TipoLoteria,
        ultimoSorteoNumeros: List<Int>
    ): Triple<MetodoCalculo?, Int, List<Int>> {
        if (ultimoSorteoNumeros.isEmpty()) return Triple(null, 0, emptyList())

        // Para Nacional/Navidad/Niño no hacemos esta evaluación (son dígitos, no números)
        if (tipo in listOf(TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO)) {
            return Triple(null, 0, emptyList())
        }

        var mejorMetodo: MetodoCalculo? = null
        var maxAciertos = 0
        var numerosAcertados = emptyList<Int>()

        val metodosAEvaluar = listOf(
            MetodoCalculo.ENSEMBLE_VOTING,
            MetodoCalculo.ALTA_CONFIANZA,
            MetodoCalculo.IA_GENETICA,
            MetodoCalculo.RACHAS_MIX,
            MetodoCalculo.FRECUENCIAS
        )

        val ultimoSet = ultimoSorteoNumeros.toSet()

        for (metodo in metodosAEvaluar) {
            try {
                val analisis = obtenerCombinacionesUseCase.ejecutar(
                    tipoLoteria = tipo,
                    numCombinaciones = 1,
                    rangoFechas = RangoFechas(null, null),
                    metodo = metodo
                )

                val prediccion = analisis.combinacionesSugeridas.firstOrNull()?.numeros ?: continue
                val acertados = prediccion.filter { it in ultimoSet }
                val aciertos = acertados.size

                if (aciertos > maxAciertos) {
                    maxAciertos = aciertos
                    mejorMetodo = metodo
                    numerosAcertados = acertados
                }
            } catch (e: Exception) {
                continue
            }
        }

        return Triple(mejorMetodo, maxAciertos, numerosAcertados)
    }

    private fun obtenerComplementarioPredicho(tipo: TipoLoteria): Pair<Int?, Int?> {
        return try {
            when (tipo) {
                TipoLoteria.PRIMITIVA -> {
                    val historico = repository.obtenerHistoricoPrimitiva()
                    val pred = motorIA.predecirReintegroParaProximoSorteo(historico, tipo.name)
                    Pair(pred?.numero, null)
                }
                TipoLoteria.BONOLOTO -> {
                    val historico = repository.obtenerHistoricoBonoloto()
                    val pred = motorIA.predecirReintegroParaProximoSorteo(historico, tipo.name)
                    Pair(pred?.numero, null)
                }
                TipoLoteria.EUROMILLONES -> {
                    val historico = repository.obtenerHistoricoEuromillones()
                    val estrellas = motorIA.predecirEstrellasParaProximoSorteo(historico)
                    if (estrellas.size >= 2) {
                        Pair(estrellas[0].numero, estrellas[1].numero)
                    } else if (estrellas.isNotEmpty()) {
                        Pair(estrellas[0].numero, null)
                    } else {
                        Pair(null, null)
                    }
                }
                TipoLoteria.GORDO_PRIMITIVA -> {
                    val historico = repository.obtenerHistoricoGordoPrimitiva()
                    val pred = motorIA.predecirClaveParaProximoSorteo(historico)
                    Pair(pred?.numero, null)
                }
                else -> Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun obtenerDiasSorteo(tipo: TipoLoteria): List<Int> {
        return when (tipo) {
            TipoLoteria.PRIMITIVA -> listOf(1, 4, 6)
            TipoLoteria.BONOLOTO -> listOf(1, 2, 3, 4, 5, 6)
            TipoLoteria.EUROMILLONES -> listOf(2, 5)
            TipoLoteria.GORDO_PRIMITIVA -> listOf(7)
            TipoLoteria.LOTERIA_NACIONAL -> listOf(4, 6)
            TipoLoteria.NAVIDAD -> listOf()
            TipoLoteria.NINO -> listOf()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dataSource = LoteriaLocalDataSource(context)
            val repository = LoteriaRepository(dataSource)
            val calculador = CalculadorProbabilidad(context)
            val useCase = ObtenerCombinacionesUseCase(repository, calculador)
            val motorIA = MotorInteligencia(context)

            return SeleccionViewModel(repository, motorIA, useCase) as T
        }
    }
}
