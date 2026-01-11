package com.loteria.probabilidad.domain.usecase

import com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.data.repository.LoteriaRepository
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad

/**
 * Caso de uso para obtener las combinaciones más probables de una lotería.
 */
class ObtenerCombinacionesUseCase(
    private val repository: LoteriaRepository,
    private val calculador: CalculadorProbabilidad
) {

    /**
     * Ejecuta el análisis y devuelve las combinaciones sugeridas.
     * @param tipoLoteria Tipo de lotería a analizar
     * @param numCombinaciones Número de combinaciones a generar
     * @param rangoFechas Rango de fechas para filtrar el histórico (opcional)
     * @param metodo Método de cálculo de probabilidad a usar
     */
    suspend fun ejecutar(
        tipoLoteria: TipoLoteria,
        numCombinaciones: Int = 5,
        rangoFechas: RangoFechas = RangoFechas.TODO,
        metodo: MetodoCalculo = MetodoCalculo.FRECUENCIAS
    ): AnalisisProbabilidad {
        // Ahora es una llamada suspend para permitir la descarga de internet
        val historicoCompleto = repository.obtenerHistorico(tipoLoteria)
        
        // Filtrar por fechas
        val historicoFiltrado = when (tipoLoteria) {
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> {
                @Suppress("UNCHECKED_CAST")
                LoteriaLocalDataSource.filtrarPorFechas(
                    historicoCompleto as List<ResultadoPrimitiva>,
                    rangoFechas
                )
            }
            TipoLoteria.EUROMILLONES -> {
                @Suppress("UNCHECKED_CAST")
                LoteriaLocalDataSource.filtrarPorFechas(
                    historicoCompleto as List<ResultadoEuromillones>,
                    rangoFechas
                )
            }
            TipoLoteria.GORDO_PRIMITIVA -> {
                @Suppress("UNCHECKED_CAST")
                LoteriaLocalDataSource.filtrarPorFechas(
                    historicoCompleto as List<ResultadoGordoPrimitiva>,
                    rangoFechas
                )
            }
            TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NINO -> {
                @Suppress("UNCHECKED_CAST")
                LoteriaLocalDataSource.filtrarPorFechas(
                    historicoCompleto as List<ResultadoNacional>,
                    rangoFechas
                )
            }
            TipoLoteria.NAVIDAD -> {
                @Suppress("UNCHECKED_CAST")
                LoteriaLocalDataSource.filtrarPorFechas(
                    historicoCompleto as List<ResultadoNavidad>,
                    rangoFechas
                )
            }
        }
        
        // Usar el calculador unificado
        return calculador.analizar(
            tipoLoteria = tipoLoteria,
            historico = historicoFiltrado,
            metodo = metodo,
            numCombinaciones = numCombinaciones
        ).copy(
            fechaDesde = rangoFechas.desde,
            fechaHasta = rangoFechas.hasta
        )
    }
}
