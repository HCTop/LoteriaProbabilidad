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
     * Orden de métodos del más elaborado al menos elaborado.
     */
    private val metodosOrdenados = listOf(
        MetodoCalculo.METODO_ABUELO,
        MetodoCalculo.ENSEMBLE_VOTING,
        MetodoCalculo.ALTA_CONFIANZA,
        MetodoCalculo.IA_GENETICA,
        MetodoCalculo.RACHAS_MIX,
        MetodoCalculo.EQUILIBRIO_ESTADISTICO,
        MetodoCalculo.PROBABILIDAD_CONDICIONAL,
        MetodoCalculo.FRECUENCIAS,
        MetodoCalculo.NUMEROS_CALIENTES,
        MetodoCalculo.NUMEROS_FRIOS,
        MetodoCalculo.DESVIACION_MEDIA,
        MetodoCalculo.LAPLACE,
        MetodoCalculo.ALEATORIO_PURO
    )

    /**
     * Filtra el histórico por rango de fechas según el tipo de lotería.
     */
    private fun filtrarHistorico(
        historicoCompleto: List<ResultadoSorteo>,
        tipoLoteria: TipoLoteria,
        rangoFechas: RangoFechas
    ): List<ResultadoSorteo> {
        return when (tipoLoteria) {
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
    }

    /**
     * Ejecuta el análisis con un solo método y devuelve las combinaciones sugeridas.
     */
    fun ejecutar(
        tipoLoteria: TipoLoteria,
        numCombinaciones: Int = 5,
        rangoFechas: RangoFechas = RangoFechas.TODO,
        metodo: MetodoCalculo = MetodoCalculo.FRECUENCIAS
    ): AnalisisProbabilidad {
        val historicoCompleto = repository.obtenerHistorico(tipoLoteria)
        val historicoFiltrado = filtrarHistorico(historicoCompleto, tipoLoteria, rangoFechas)

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

    /**
     * Ejecuta TODOS los métodos (1 combinación de cada uno) ordenados del más elaborado al menos.
     * Obtiene el histórico una sola vez y lo reutiliza para todos los métodos.
     */
    fun ejecutarTodosMetodos(
        tipoLoteria: TipoLoteria,
        rangoFechas: RangoFechas = RangoFechas.TODO
    ): AnalisisProbabilidad {
        val historicoCompleto = repository.obtenerHistorico(tipoLoteria)
        val historicoFiltrado = filtrarHistorico(historicoCompleto, tipoLoteria, rangoFechas)

        val todasCombinaciones = mutableListOf<CombinacionSugerida>()
        var analisisBase: AnalisisProbabilidad? = null

        for (metodo in metodosOrdenados) {
            try {
                val analisis = calculador.analizar(
                    tipoLoteria = tipoLoteria,
                    historico = historicoFiltrado,
                    metodo = metodo,
                    numCombinaciones = 1
                )
                if (analisisBase == null) analisisBase = analisis

                analisis.combinacionesSugeridas.firstOrNull()?.let { combinacion ->
                    todasCombinaciones.add(
                        combinacion.copy(
                            explicacion = "${metodo.displayName}\n${combinacion.explicacion}"
                        )
                    )
                }
            } catch (e: Exception) {
                // Si un método falla, continuar con los demás
                android.util.Log.w("UseCase", "Método ${metodo.name} falló: ${e.message}")
            }
        }

        // Reasignar complementarios variados para que no todas las combinaciones
        // tengan el mismo reintegro/estrella/clave (el bug de siempre-0)
        val combinacionesFinales = when (tipoLoteria) {
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> {
                // Generar lista de 10 reintegros (0-9) barajados, reciclar cíclicamente
                val reintegrosBarajados = (0..9).shuffled()
                todasCombinaciones.mapIndexed { index, comb ->
                    val nuevoReintegro = reintegrosBarajados[index % reintegrosBarajados.size]
                    comb.copy(
                        complementarios = listOf(nuevoReintegro),
                        explicacion = comb.explicacion.replace(
                            Regex("\\| 🎲R:\\d+"), "| 🎲R:$nuevoReintegro"
                        )
                    )
                }
            }
            TipoLoteria.EUROMILLONES -> {
                // Generar pares de estrellas variados
                val todasEstrellas = (1..12).shuffled()
                todasCombinaciones.mapIndexed { index, comb ->
                    val offset = (index * 2) % todasEstrellas.size
                    val e1 = todasEstrellas[offset]
                    val e2 = todasEstrellas[(offset + 1) % todasEstrellas.size]
                    val estrellas = listOf(e1, e2).distinct().sorted().let {
                        if (it.size < 2) listOf(e1, (1..12).first { n -> n != e1 }).sorted() else it
                    }
                    comb.copy(
                        complementarios = estrellas,
                        explicacion = comb.explicacion.replace(
                            Regex("\\| ⭐[\\d,]+"), "| ⭐${estrellas.joinToString(",")}"
                        )
                    )
                }
            }
            TipoLoteria.GORDO_PRIMITIVA -> {
                val clavesBarajadas = (0..9).shuffled()
                todasCombinaciones.mapIndexed { index, comb ->
                    val nuevaClave = clavesBarajadas[index % clavesBarajadas.size]
                    comb.copy(
                        complementarios = listOf(nuevaClave),
                        explicacion = comb.explicacion.replace(
                            Regex("\\| 🔑K:\\d+"), "| 🔑K:$nuevaClave"
                        )
                    )
                }
            }
            else -> todasCombinaciones // Nacional, Navidad, Niño no tienen complementarios
        }

        return (analisisBase ?: throw Exception("No se pudieron generar combinaciones")).copy(
            combinacionesSugeridas = combinacionesFinales,
            fechaDesde = rangoFechas.desde,
            fechaHasta = rangoFechas.hasta
        )
    }
}
