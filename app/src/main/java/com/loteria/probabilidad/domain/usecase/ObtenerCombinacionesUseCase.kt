package com.loteria.probabilidad.domain.usecase

import com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.data.repository.LoteriaRepository
import com.loteria.probabilidad.domain.calculator.CalculadorProbabilidad
import kotlin.random.Random

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
        MetodoCalculo.MULTI_HORIZON,
        MetodoCalculo.ENSEMBLE_VOTING,
        MetodoCalculo.ALTA_CONFIANZA,
        MetodoCalculo.IA_GENETICA,
        MetodoCalculo.RACHAS_MIX,
        MetodoCalculo.FRECUENCIAS
        // NUMEROS_FRIOS y ALEATORIO_PURO eliminados: backtest demostró que
        // no superan el valor esperado teórico (0.735 ac/sorteo) — ruido puro
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
        // Semilla determinista: incluye hash de las combinaciones para que cambie tras entrenamiento
        val combHash = todasCombinaciones.sumOf { it.numeros.hashCode().toLong() }
        val semillaComp = tipoLoteria.name.hashCode().toLong() * 31 + historicoFiltrado.size.toLong() + combHash
        val rndComp = Random(semillaComp)
        val combinacionesFinales = when (tipoLoteria) {
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> {
                // Generar lista de 10 reintegros (0-9) barajados, reciclar cíclicamente
                val reintegrosBarajados = (0..9).shuffled(rndComp)
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
                val todasEstrellas = (1..12).shuffled(rndComp)
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
                val clavesBarajadas = (0..9).shuffled(rndComp)
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

    /**
     * Ejecuta la Fórmula del Abuelo (3 pilares: Cobertura + Anti-Popularidad + Kelly).
     *
     * Los candidatos se eligen por ESTADÍSTICA PURA del histórico real:
     * 1. Frecuencia: números que salen más de lo esperado estadísticamente
     * 2. Calientes: números con tendencia alcista en los últimos sorteos
     * 3. Debidos: números que llevan más sorteos sin salir de lo normal
     * 4. Se combinan los 3 criterios y se eligen los top V
     *
     * Sin algoritmos complejos. Solo datos reales.
     */
    fun ejecutarFormulaAbuelo(
        tipoLoteria: TipoLoteria,
        rangoFechas: RangoFechas = RangoFechas.TODO,
        boteActual: Double = 0.0,
        numCandidatos: Int = 17,
        garantiaMinima: Int = 3
    ): ResultadoFormulaAbuelo {
        val historicoCompleto = repository.obtenerHistorico(tipoLoteria)
        val historicoFiltrado = filtrarHistorico(historicoCompleto, tipoLoteria, rangoFechas)

        // Candidatos del histórico real
        val candidatos = obtenerCandidatosDelHistorico(
            tipoLoteria, historicoFiltrado, numCandidatos
        )

        val resultado = calculador.ejecutarFormulaAbueloConCandidatos(
            candidatos = candidatos,
            tipoLoteria = tipoLoteria,
            historico = historicoFiltrado,
            boteActual = boteActual,
            garantiaMinima = garantiaMinima
        )

        val (suplemento, porcentajes) = calcularSuplemento(tipoLoteria, historicoFiltrado)
        return resultado.copy(suplementoCandidatos = suplemento, suplementoPorcentajes = porcentajes)
    }

    /**
     * Calcula los números suplementarios con su % de aparición en el histórico:
     * - Primitiva/Bonoloto: los 10 reintegros (0-9) ciclando — estrategia C óptima por simulación
     * - Euromillones: top 4 estrellas individuales (1-12)
     * - Gordo: top 3 claves (0-9)
     * Devuelve (numeros, porcentajes) — 100% determinista, igual en todos los dispositivos.
     */
    private fun calcularSuplemento(
        tipoLoteria: TipoLoteria,
        historico: List<ResultadoSorteo>
    ): Pair<List<Int>, List<Double>> {
        return when (tipoLoteria) {
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> {
                @Suppress("UNCHECKED_CAST")
                val hist = historico as List<ResultadoPrimitiva>
                val total = hist.size.takeIf { it > 0 } ?: 1
                val freq = IntArray(10)
                hist.forEach { s -> if (s.reintegro in 0..9) freq[s.reintegro]++ }
                // Estrategia C: cubrir los 10 reintegros ciclando
                // Los 5 más frecuentes en posiciones pares (2 boletos c/u)
                // Los 5 menos frecuentes en posiciones impares (1 boleto c/u)
                val sortedByFreq = (0..9).sortedByDescending { freq[it] }
                val top5 = sortedByFreq.take(5)
                val bottom5 = sortedByFreq.drop(5)
                val todos = (0..4).flatMap { i -> listOf(top5[i], bottom5[i]) }
                val pct = todos.map { (freq[it].toDouble() / total) * 100 }
                Pair(todos, pct)
            }
            TipoLoteria.EUROMILLONES -> {
                @Suppress("UNCHECKED_CAST")
                val hist = historico as List<ResultadoEuromillones>
                val totalEstrellas = (hist.size * 2).takeIf { it > 0 } ?: 1
                val freq = IntArray(13)
                hist.forEach { s -> s.estrellas.forEach { e -> if (e in 1..12) freq[e]++ } }
                val top = (1..12).sortedByDescending { freq[it] }.take(4)
                val pct = top.map { (freq[it].toDouble() / totalEstrellas) * 100 }
                Pair(top, pct)
            }
            TipoLoteria.GORDO_PRIMITIVA -> {
                @Suppress("UNCHECKED_CAST")
                val hist = historico as List<ResultadoGordoPrimitiva>
                val total = hist.size.takeIf { it > 0 } ?: 1
                val freq = IntArray(10)
                hist.forEach { s -> if (s.numeroClave in 0..9) freq[s.numeroClave]++ }
                val top = (0..9).sortedByDescending { freq[it] }.take(3)
                val pct = top.map { (freq[it].toDouble() / total) * 100 }
                Pair(top, pct)
            }
            else -> Pair(emptyList(), emptyList())
        }
    }

    /**
     * Selección de candidatos basada ÚNICAMENTE en el histórico real de sorteos.
     *
     * Tres criterios estadísticos combinados:
     *
     * 1. FRECUENCIA (15%): Diferencia entre frecuencia observada y esperada.
     *    Chi-cuadrado simplificado: (observada - esperada)² / esperada
     *    Si observada > esperada → score positivo (sale más de lo normal)
     *
     * 2. CALIENTES (70%): Frecuencia en los últimos 12 sorteos vs la general.
     *    Si un número sale más en los últimos sorteos que en el total → está caliente.
     *
     * 3. DEBIDOS (15%): Sorteos desde la última aparición vs promedio de gap.
     *    Si lleva más sorteos sin salir que su promedio → está "debido".
     *
     * Los scores se normalizan 0-1 y se combinan con los pesos indicados.
     */
    private fun obtenerCandidatosDelHistorico(
        tipoLoteria: TipoLoteria,
        historico: List<ResultadoSorteo>,
        numCandidatos: Int
    ): List<Int> {
        // Extraer los números de cada sorteo según el tipo de lotería
        // El CSV viene del más reciente al más antiguo; lo invertimos para que
        // sorteos[0]=más antiguo, sorteos[last]=más reciente (orden cronológico).
        val sorteos = extraerNumerosDeSorteos(tipoLoteria, historico).reversed()
        if (sorteos.isEmpty()) return (1..numCandidatos).toList()

        val maxNum = tipoLoteria.maxNumero
        val numPorSorteo = tipoLoteria.cantidadNumeros
        val totalSorteos = sorteos.size

        // Para loterías de dígitos, delegar al método antiguo
        if (maxNum > 1000) {
            return extraerCandidatosDigitos(tipoLoteria, historico, numCandidatos)
        }

        // ═══ CRITERIO 1: FRECUENCIA ═══
        // Contar apariciones de cada número en todo el histórico
        val frecuencia = IntArray(maxNum + 1)
        for (sorteo in sorteos) {
            for (num in sorteo) {
                if (num in 1..maxNum) frecuencia[num]++
            }
        }

        val frecEsperada = totalSorteos.toDouble() * numPorSorteo / maxNum
        val scoreFrecuencia = DoubleArray(maxNum + 1)
        for (num in 1..maxNum) {
            val diff = frecuencia[num] - frecEsperada
            // Score positivo si sale más de lo esperado
            scoreFrecuencia[num] = diff / frecEsperada.coerceAtLeast(1.0)
        }

        // ═══ CRITERIO 2: CALIENTES (últimos N sorteos) ═══
        val ventanaCaliente = minOf(12, totalSorteos / 3).coerceAtLeast(8)
        val sorteosRecientes = sorteos.takeLast(ventanaCaliente)
        val frecReciente = IntArray(maxNum + 1)
        for (sorteo in sorteosRecientes) {
            for (num in sorteo) {
                if (num in 1..maxNum) frecReciente[num]++
            }
        }

        val frecEsperadaReciente = ventanaCaliente.toDouble() * numPorSorteo / maxNum
        val scoreCaliente = DoubleArray(maxNum + 1)
        for (num in 1..maxNum) {
            val diff = frecReciente[num] - frecEsperadaReciente
            scoreCaliente[num] = diff / frecEsperadaReciente.coerceAtLeast(1.0)
        }

        // ═══ CRITERIO 3: DEBIDOS (gap desde última aparición) ═══
        // Calcular el gap actual (sorteos desde última aparición)
        val ultimaAparicion = IntArray(maxNum + 1) { -1 }
        for ((index, sorteo) in sorteos.withIndex()) {
            for (num in sorteo) {
                if (num in 1..maxNum) ultimaAparicion[num] = index
            }
        }

        // Gap promedio de cada número
        val gapPromedio = DoubleArray(maxNum + 1)
        for (num in 1..maxNum) {
            gapPromedio[num] = if (frecuencia[num] > 1) {
                totalSorteos.toDouble() / frecuencia[num]
            } else {
                totalSorteos.toDouble() // nunca salió o solo una vez
            }
        }

        val scoreDebido = DoubleArray(maxNum + 1)
        for (num in 1..maxNum) {
            val gapActual = totalSorteos - 1 - ultimaAparicion[num]
            // Score positivo si lleva más sorteos sin salir que su promedio
            scoreDebido[num] = if (gapPromedio[num] > 0) {
                (gapActual - gapPromedio[num]) / gapPromedio[num]
            } else 0.0
        }

        // ═══ COMBINAR los 3 criterios ═══
        // Normalizar cada score a 0-1
        val normFrecuencia = normalizar(scoreFrecuencia, maxNum)
        val normCaliente = normalizar(scoreCaliente, maxNum)
        val normDebido = normalizar(scoreDebido, maxNum)

        // Ponderación: Frecuencia 15%, Calientes 70%, Debidos 15%
        val scoreFinal = DoubleArray(maxNum + 1)
        for (num in 1..maxNum) {
            scoreFinal[num] = 0.15 * normFrecuencia[num] +
                    0.70 * normCaliente[num] +
                    0.15 * normDebido[num]
        }

        // Seleccionar top V, asegurando equilibrio alto/bajo
        val candidatosOrdenados = (1..maxNum)
            .sortedByDescending { scoreFinal[it] }

        // DEBUG: Log para verificar contra script Python
        android.util.Log.d("CANDIDATOS_DEBUG", "=== VERIFICACION CANDIDATOS ===")
        android.util.Log.d("CANDIDATOS_DEBUG", "totalSorteos=$totalSorteos maxNum=$maxNum ventanaCaliente=$ventanaCaliente")
        android.util.Log.d("CANDIDATOS_DEBUG", "frecEsperada=$frecEsperada frecEspReciente=$frecEsperadaReciente")
        android.util.Log.d("CANDIDATOS_DEBUG", "Primer sorteo: ${sorteos.firstOrNull()}")
        android.util.Log.d("CANDIDATOS_DEBUG", "Ultimo sorteo: ${sorteos.lastOrNull()}")
        for (num in candidatosOrdenados.take(25)) {
            val gapAct = totalSorteos - 1 - ultimaAparicion[num]
            android.util.Log.d("CANDIDATOS_DEBUG",
                "#$num frec=${frecuencia[num]} frecRec=${frecReciente[num]} gap=$gapAct " +
                "gapProm=${"%.1f".format(gapPromedio[num])} " +
                "nF=${"%.4f".format(normFrecuencia[num])} " +
                "nC=${"%.4f".format(normCaliente[num])} " +
                "nD=${"%.4f".format(normDebido[num])} " +
                "TOTAL=${"%.4f".format(scoreFinal[num])}")
        }

        val mitad = maxNum / 2
        val candidatosBajos = candidatosOrdenados.filter { it <= mitad }
        val candidatosAltos = candidatosOrdenados.filter { it > mitad }

        // Alternar bajo-alto para equilibrar
        val resultado = mutableListOf<Int>()
        var iBajo = 0
        var iAlto = 0
        while (resultado.size < numCandidatos) {
            // Más o menos 50/50 pero priorizando por score
            if (iBajo < candidatosBajos.size && (iAlto >= candidatosAltos.size || iBajo <= iAlto)) {
                resultado.add(candidatosBajos[iBajo++])
            } else if (iAlto < candidatosAltos.size) {
                resultado.add(candidatosAltos[iAlto++])
            } else break
        }

        android.util.Log.d("CANDIDATOS_DEBUG", "Resultado final: ${resultado.take(numCandidatos).sorted()}")

        return resultado.take(numCandidatos)
    }

    /**
     * Normaliza un array de scores a rango 0-1.
     */
    private fun normalizar(scores: DoubleArray, maxNum: Int): DoubleArray {
        val valores = (1..maxNum).map { scores[it] }
        val minVal = valores.minOrNull() ?: 0.0
        val maxVal = valores.maxOrNull() ?: 1.0
        val rango = (maxVal - minVal).coerceAtLeast(0.001)

        val result = DoubleArray(scores.size)
        for (num in 1..maxNum) {
            result[num] = (scores[num] - minVal) / rango
        }
        return result
    }

    /**
     * Extrae la lista de números de cada sorteo del histórico.
     */
    private fun extraerNumerosDeSorteos(
        tipoLoteria: TipoLoteria,
        historico: List<ResultadoSorteo>
    ): List<List<Int>> {
        return when (tipoLoteria) {
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> {
                @Suppress("UNCHECKED_CAST")
                (historico as List<ResultadoPrimitiva>).map { it.numeros }
            }
            TipoLoteria.EUROMILLONES -> {
                @Suppress("UNCHECKED_CAST")
                (historico as List<ResultadoEuromillones>).map { it.numeros }
            }
            TipoLoteria.GORDO_PRIMITIVA -> {
                @Suppress("UNCHECKED_CAST")
                (historico as List<ResultadoGordoPrimitiva>).map { it.numeros }
            }
            else -> emptyList() // Dígitos se manejan aparte
        }
    }

    /**
     * Candidatos para loterías de dígitos (Nacional/Navidad/Niño).
     */
    private fun extraerCandidatosDigitos(
        tipoLoteria: TipoLoteria,
        historico: List<ResultadoSorteo>,
        numCandidatos: Int
    ): List<Int> {
        return when (tipoLoteria) {
            TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NINO -> {
                @Suppress("UNCHECKED_CAST")
                val hist = historico as List<ResultadoNacional>
                hist.take(50).map { sorteo ->
                    sorteo.primerPremio.filter { it.isDigit() }.toIntOrNull() ?: 0
                }.filter { it > 0 }.distinct().take(numCandidatos)
            }
            TipoLoteria.NAVIDAD -> {
                @Suppress("UNCHECKED_CAST")
                val hist = historico as List<ResultadoNavidad>
                hist.take(50).map { sorteo ->
                    sorteo.gordo.filter { it.isDigit() }.toIntOrNull() ?: 0
                }.filter { it > 0 }.distinct().take(numCandidatos)
            }
            else -> emptyList()
        }
    }
}
