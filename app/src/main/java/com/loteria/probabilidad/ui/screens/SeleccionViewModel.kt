package com.loteria.probabilidad.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.loteria.probabilidad.data.datasource.ActualizadorHistorico
import com.loteria.probabilidad.data.datasource.BoteOficial
import com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.data.repository.LoteriaRepository
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad
import com.loteria.probabilidad.domain.ml.GestionBankroll
import com.loteria.probabilidad.domain.ml.HistorialPredicciones
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
    val numerosAcertadosMejorMetodo: List<Int> = emptyList(),
    // Semáforo del bote (null si no se conoce el bote)
    val semaforoTexto: String? = null,
    val semaforoColor: Long? = null
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
    private val obtenerCombinacionesUseCase: ObtenerCombinacionesUseCase,
    private val historialPredicciones: HistorialPredicciones,
    private val gestorPredicciones: GestorPredicciones,
    private val actualizador: ActualizadorHistorico,
    private val boteOficial: BoteOficial
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
                    // Traer los resultados nuevos ANTES de evaluar: si no, el sorteo de ayer
                    // no estaba en el CSV y sus aciertos no aparecían hasta otro día
                    try {
                        if (actualizador.necesitaActualizar()) actualizador.actualizar()
                    } catch (e: Exception) {
                        android.util.Log.w("SeleccionVM", "Sin actualización de históricos: ${e.message}")
                    }

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
        val proximoSorteo = when {
            tipo == TipoLoteria.NAVIDAD -> proximaFechaAnual(hoy, 12, 22)
            tipo == TipoLoteria.NINO -> proximaFechaAnual(hoy, 1, 6)
            diasSorteo.isNotEmpty() -> (0L..7L).map { hoy.plusDays(it) }
                .first { it.dayOfWeek.value in diasSorteo }
            else -> hoy
        }
        val proximoDiaNombre = if (tipo == TipoLoteria.NAVIDAD || tipo == TipoLoteria.NINO) {
            "${diasSemana[proximoSorteo.dayOfWeek.value - 1]} ${proximoSorteo.dayOfMonth}/${proximoSorteo.monthValue}"
        } else {
            diasSemana[proximoSorteo.dayOfWeek.value - 1]
        }

        // Obtener último sorteo
        val (ultimoNumeros, ultimoFecha, ultimoComp1, ultimoComp2) = obtenerUltimoSorteo(tipo)

        val esDeBolas = tipo in GestorPredicciones.LOTERIAS_EVALUABLES

        val metodoMejor: MetodoCalculo?
        val aciertos: Int
        val numerosAcertados: List<Int>
        val mejorMetodo: MetodoCalculo
        val tasaAcierto: Double
        val numerosPredichos: List<Int>
        val comp1: Int?
        val comp2: Int?

        if (esDeBolas) {
            // 1. Evaluar sorteos pendientes con lo que se GUARDÓ antes de cada sorteo
            gestorPredicciones.evaluarPendientes(tipo)

            // 2. Predicciones del próximo sorteo: las guardadas o se generan y se guardan.
            //    Lo que se enseña aquí es exactamente lo que luego se evalúa.
            val combinaciones = gestorPredicciones.obtenerOGenerar(tipo).analisis.combinacionesSugeridas

            // 3. "Mejor método del último sorteo" = evaluación REAL de lo guardado
            val historial = historialPredicciones.cargarHistorial(tipo.name)
            val mejorEvalUltimo = historial.firstOrNull { it.fechaSorteo == ultimoFecha }
                ?.evaluaciones?.maxByOrNull { it.aciertosNumeros }
            metodoMejor = mejorEvalUltimo?.let { metodoPorNombre(it.metodo) }
            aciertos = mejorEvalUltimo?.aciertosNumeros ?: 0
            numerosAcertados = mejorEvalUltimo?.numerosPredichos?.filter { it in ultimoNumeros } ?: emptyList()

            // 4. Método a mostrar: el mejor del ranking histórico (con al menos 3 sorteos),
            //    si no el mejor del último sorteo, y si no Ensemble
            val ranking = historialPredicciones.obtenerRankingMetodos(tipo.name)
            val mejorDelRanking = if (historial.size >= 3) ranking.firstOrNull() else null
            mejorMetodo = mejorDelRanking?.let { metodoPorNombre(it.first) }
                ?: metodoMejor ?: MetodoCalculo.ENSEMBLE_VOTING

            val cantidad = if (tipo == TipoLoteria.PRIMITIVA || tipo == TipoLoteria.BONOLOTO) 6 else 5
            val promedio = ranking.firstOrNull { metodoPorNombre(it.first) == mejorMetodo }?.second ?: 0.0
            tasaAcierto = promedio * 100 / cantidad

            val combinacion = combinaciones.firstOrNull { nombreMetodo(it) == mejorMetodo.displayName }
                ?: combinaciones.firstOrNull()
            numerosPredichos = combinacion?.numeros ?: emptyList()
            // Complementarios de la MISMA combinación guardada (antes salían de otro cálculo)
            comp1 = combinacion?.complementarios?.getOrNull(0)
            comp2 = combinacion?.complementarios?.getOrNull(1)
        } else {
            // Nacional / Navidad / Niño: sin evaluación por bolas
            metodoMejor = null
            aciertos = 0
            numerosAcertados = emptyList()
            mejorMetodo = MetodoCalculo.ENSEMBLE_VOTING
            tasaAcierto = 0.0

            val analisis = obtenerCombinacionesUseCase.ejecutar(
                tipoLoteria = tipo,
                numCombinaciones = 1,
                rangoFechas = RangoFechas(null, null),
                metodo = mejorMetodo
            )
            val combinacion = analisis.combinacionesSugeridas.firstOrNull()?.numeros ?: emptyList()
            numerosPredichos = when {
                combinacion.isEmpty() -> emptyList()
                // Número de 5 cifras → dígitos individuales
                combinacion.first() > 9 -> combinacion.first().toString().padStart(5, '0').map { it.digitToInt() }
                else -> combinacion.take(5)
            }
            comp1 = null
            comp2 = null
        }

        // Semáforo: ¿merece la pena jugar con el bote actual?
        var semaforoTexto: String? = null
        var semaforoColor: Long? = null
        if (esDeBolas) {
            try {
                boteOficial.obtener(tipo)?.let { bote ->
                    val kelly = GestionBankroll.calcularKelly(tipo, bote.importe)
                    val millones = bote.importe / 1_000_000
                    val boteTexto = if (millones >= 1)
                        (if (millones % 1.0 == 0.0) "%.0f" else "%.1f").format(millones) + " M€"
                    else "%,.0f €".format(bote.importe).replace(',', '.')
                    semaforoTexto = "Bote $boteTexto · ${kelly.recomendacion.displayName}"
                    semaforoColor = kelly.recomendacion.color
                }
            } catch (e: Exception) {
                android.util.Log.w("SeleccionVM", "Sin bote para $tipo: ${e.message}")
            }
        }

        return PrediccionLoteria(
            tipoLoteria = tipo,
            semaforoTexto = semaforoTexto,
            semaforoColor = semaforoColor,
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

    /** Nombre del método de una combinación guardada (primera línea de la explicación). */
    private fun nombreMetodo(c: CombinacionSugerida): String =
        c.explicacion.lineSequence().firstOrNull()?.trim() ?: ""

    private fun metodoPorNombre(nombre: String): MetodoCalculo? =
        MetodoCalculo.entries.firstOrNull { it.displayName == nombre.trim() }

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

    private fun proximaFechaAnual(hoy: java.time.LocalDate, mes: Int, dia: Int): java.time.LocalDate {
        val esteAno = java.time.LocalDate.of(hoy.year, mes, dia)
        return if (hoy.isAfter(esteAno)) esteAno.plusYears(1) else esteAno
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
            val historial = HistorialPredicciones(context)
            val gestor = GestorPredicciones(repository, useCase, historial, MemoriaIA(context))

            return SeleccionViewModel(
                repository, motorIA, useCase, historial, gestor, ActualizadorHistorico(context),
                BoteOficial(context)
            ) as T
        }
    }
}
