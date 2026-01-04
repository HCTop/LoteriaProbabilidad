package com.loteria.probabilidad.domain.calculator

import com.loteria.probabilidad.data.model.*
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Calculador de probabilidades con múltiples métodos de análisis.
 * 
 * Métodos implementados:
 * - LAPLACE: Probabilidad teórica matemática pura
 * - FRECUENCIAS: Basado en histórico de apariciones
 * - NUMEROS_CALIENTES: Los más frecuentes recientemente
 * - NUMEROS_FRIOS: Los menos frecuentes (teoría del equilibrio)
 * - EQUILIBRIO_ESTADISTICO: Mezcla de calientes y fríos
 * - PROBABILIDAD_CONDICIONAL: Números que salen juntos
 * - DESVIACION_MEDIA: Números alejados de su frecuencia esperada
 * - ALEATORIO_PURO: Selección completamente aleatoria
 */
class CalculadorProbabilidad {

    /**
     * Analiza el histórico según el método seleccionado.
     */
    fun analizar(
        tipoLoteria: TipoLoteria,
        historico: List<ResultadoSorteo>,
        metodo: MetodoCalculo,
        numCombinaciones: Int = 5
    ): AnalisisProbabilidad {
        return when (tipoLoteria) {
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> {
                @Suppress("UNCHECKED_CAST")
                analizarPrimitivaBonoloto(
                    historico as List<ResultadoPrimitiva>,
                    tipoLoteria,
                    metodo,
                    numCombinaciones
                )
            }
            TipoLoteria.EUROMILLONES -> {
                @Suppress("UNCHECKED_CAST")
                analizarEuromillones(
                    historico as List<ResultadoEuromillones>,
                    metodo,
                    numCombinaciones
                )
            }
            TipoLoteria.GORDO_PRIMITIVA -> {
                @Suppress("UNCHECKED_CAST")
                analizarGordoPrimitiva(
                    historico as List<ResultadoGordoPrimitiva>,
                    metodo,
                    numCombinaciones
                )
            }
            TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NINO -> {
                @Suppress("UNCHECKED_CAST")
                analizarNacional(
                    historico as List<ResultadoNacional>,
                    tipoLoteria,
                    metodo,
                    numCombinaciones
                )
            }
            TipoLoteria.NAVIDAD -> {
                @Suppress("UNCHECKED_CAST")
                analizarNavidad(
                    historico as List<ResultadoNavidad>,
                    metodo,
                    numCombinaciones
                )
            }
        }
    }

    // ==================== PRIMITIVA / BONOLOTO ====================

    private fun analizarPrimitivaBonoloto(
        historico: List<ResultadoPrimitiva>,
        tipoLoteria: TipoLoteria,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.LAPLACE && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(tipoLoteria, metodo)
        }

        val maxNumero = 49
        val cantidadNumeros = 6
        
        // Calcular frecuencias
        val frecuenciasNumeros = contarFrecuencias(historico.flatMap { it.numeros }, 1..maxNumero)
        val frecuenciasReintegros = contarFrecuencias(historico.map { it.reintegro }, 0..9)
        
        // Generar combinaciones según el método
        val combinaciones = when (metodo) {
            MetodoCalculo.LAPLACE -> generarLaplace(maxNumero, cantidadNumeros, numCombinaciones)
            MetodoCalculo.FRECUENCIAS -> generarPorFrecuencias(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.NUMEROS_CALIENTES -> generarNumerosCalientes(historico, cantidadNumeros, numCombinaciones, maxNumero)
            MetodoCalculo.NUMEROS_FRIOS -> generarNumerosFrios(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> generarEquilibrio(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> generarCondicional(historico, cantidadNumeros, numCombinaciones, maxNumero)
            MetodoCalculo.DESVIACION_MEDIA -> generarDesviacionMedia(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size, maxNumero)
            MetodoCalculo.ALEATORIO_PURO -> generarAleatorio(maxNumero, cantidadNumeros, numCombinaciones)
        }.map { combinacion ->
            // Añadir reintegro
            val reintegro = frecuenciasReintegros.entries.sortedByDescending { it.value }.first().key
            combinacion.copy(
                complementarios = listOf(reintegro),
                explicacion = "${combinacion.explicacion} | Reintegro: $reintegro"
            )
        }

        // Calcular probabilidad teórica (Laplace)
        val probabilidadTeorica = calcularProbabilidadLaplace(maxNumero, cantidadNumeros)

        return AnalisisProbabilidad(
            tipoLoteria = tipoLoteria,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasReintegros, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica
        )
    }

    // ==================== EUROMILLONES ====================

    private fun analizarEuromillones(
        historico: List<ResultadoEuromillones>,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.LAPLACE && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(TipoLoteria.EUROMILLONES, metodo)
        }

        val maxNumero = 50
        val cantidadNumeros = 5
        val maxEstrella = 12
        
        val frecuenciasNumeros = contarFrecuencias(historico.flatMap { it.numeros }, 1..maxNumero)
        val frecuenciasEstrellas = contarFrecuencias(historico.flatMap { it.estrellas }, 1..maxEstrella)
        
        val combinaciones = when (metodo) {
            MetodoCalculo.LAPLACE -> generarLaplace(maxNumero, cantidadNumeros, numCombinaciones)
            MetodoCalculo.FRECUENCIAS -> generarPorFrecuencias(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.NUMEROS_CALIENTES -> generarNumerosCalientesEuro(historico, numCombinaciones)
            MetodoCalculo.NUMEROS_FRIOS -> generarNumerosFrios(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> generarEquilibrio(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> generarCondicionalEuro(historico, numCombinaciones)
            MetodoCalculo.DESVIACION_MEDIA -> generarDesviacionMedia(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size, maxNumero)
            MetodoCalculo.ALEATORIO_PURO -> generarAleatorio(maxNumero, cantidadNumeros, numCombinaciones)
        }.map { combinacion ->
            // Añadir estrellas
            val estrellasTop = frecuenciasEstrellas.entries.sortedByDescending { it.value }.take(4).map { it.key }.shuffled().take(2).sorted()
            combinacion.copy(
                complementarios = estrellasTop,
                explicacion = "${combinacion.explicacion} | Estrellas: ${estrellasTop.joinToString(", ")}"
            )
        }

        // Probabilidad Euromillones: C(50,5) * C(12,2) = 139,838,160
        val probabilidadTeorica = "1 entre 139.838.160 (≈0.000000715%)"

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.EUROMILLONES,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasEstrellas, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica
        )
    }

    // ==================== EL GORDO DE LA PRIMITIVA ====================

    private fun analizarGordoPrimitiva(
        historico: List<ResultadoGordoPrimitiva>,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.LAPLACE && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(TipoLoteria.GORDO_PRIMITIVA, metodo)
        }

        val maxNumero = 54
        val cantidadNumeros = 5
        
        val frecuenciasNumeros = contarFrecuencias(historico.flatMap { it.numeros }, 1..maxNumero)
        val frecuenciasClave = contarFrecuencias(historico.map { it.numeroClave }, 0..9)
        
        val combinaciones = when (metodo) {
            MetodoCalculo.LAPLACE -> generarLaplace(maxNumero, cantidadNumeros, numCombinaciones)
            MetodoCalculo.FRECUENCIAS -> generarPorFrecuencias(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.NUMEROS_CALIENTES -> generarNumerosCalientesGordo(historico, numCombinaciones)
            MetodoCalculo.NUMEROS_FRIOS -> generarNumerosFrios(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> generarEquilibrio(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> generarCondicionalGordo(historico, numCombinaciones)
            MetodoCalculo.DESVIACION_MEDIA -> generarDesviacionMedia(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size, maxNumero)
            MetodoCalculo.ALEATORIO_PURO -> generarAleatorio(maxNumero, cantidadNumeros, numCombinaciones)
        }.map { combinacion ->
            val numeroClave = frecuenciasClave.entries.sortedByDescending { it.value }.first().key
            combinacion.copy(
                complementarios = listOf(numeroClave),
                explicacion = "${combinacion.explicacion} | Nº Clave: $numeroClave"
            )
        }

        // Probabilidad El Gordo: C(54,5) * 10 = 31,625,100
        val probabilidadTeorica = "1 entre 31.625.100 (≈0.00000316%)"

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.GORDO_PRIMITIVA,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasClave, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica
        )
    }

    // ==================== LOTERÍA NACIONAL / EL NIÑO ====================

    private fun analizarNacional(
        historico: List<ResultadoNacional>,
        tipoLoteria: TipoLoteria,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.LAPLACE && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(tipoLoteria, metodo)
        }

        // Analizar terminaciones (últimas 2 cifras)
        val terminaciones = historico.mapNotNull { it.primerPremio.takeLast(2).toIntOrNull() }
        val frecuenciasTerminaciones = contarFrecuencias(terminaciones, 0..99)
        val frecuenciasReintegros = contarFrecuencias(historico.flatMap { it.reintegros }, 0..9)

        val combinaciones = when (metodo) {
            MetodoCalculo.LAPLACE, MetodoCalculo.ALEATORIO_PURO -> {
                (0 until numCombinaciones).map {
                    val numero = (0..99999).random()
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = 0.001,
                        explicacion = "Número: ${numero.toString().padStart(5, '0')}"
                    )
                }
            }
            else -> {
                val terminacionesTop = frecuenciasTerminaciones.entries.sortedByDescending { it.value }.take(numCombinaciones)
                terminacionesTop.map { (terminacion, frecuencia) ->
                    val prefijo = (0..999).random()
                    val numero = prefijo * 100 + terminacion
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = frecuencia.toDouble() / historico.size * 100,
                        explicacion = "Número: ${numero.toString().padStart(5, '0')} | Terminación frecuente: ${terminacion.toString().padStart(2, '0')}"
                    )
                }
            }
        }

        val probabilidadTeorica = "1 entre 100.000 (0.001%)"

        return AnalisisProbabilidad(
            tipoLoteria = tipoLoteria,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasReintegros, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica
        )
    }

    // ==================== NAVIDAD ====================

    private fun analizarNavidad(
        historico: List<ResultadoNavidad>,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.LAPLACE && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(TipoLoteria.NAVIDAD, metodo)
        }

        val terminaciones = historico.mapNotNull { it.gordo.takeLast(2).toIntOrNull() }
        val frecuenciasTerminaciones = contarFrecuencias(terminaciones, 0..99)
        val frecuenciasReintegros = contarFrecuencias(historico.flatMap { it.reintegros }, 0..9)

        val combinaciones = when (metodo) {
            MetodoCalculo.LAPLACE, MetodoCalculo.ALEATORIO_PURO -> {
                (0 until numCombinaciones).map {
                    val numero = (0..99999).random()
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = 0.001,
                        explicacion = "Décimo: ${numero.toString().padStart(5, '0')}"
                    )
                }
            }
            else -> {
                val terminacionesTop = frecuenciasTerminaciones.entries.sortedByDescending { it.value }.take(numCombinaciones)
                terminacionesTop.map { (terminacion, frecuencia) ->
                    val prefijo = (0..999).random()
                    val numero = prefijo * 100 + terminacion
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = frecuencia.toDouble() / historico.size * 100,
                        explicacion = "Décimo: ${numero.toString().padStart(5, '0')} | Terminación: ${terminacion.toString().padStart(2, '0')}"
                    )
                }
            }
        }

        val probabilidadTeorica = "1 entre 100.000 (0.001%) para El Gordo"

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.NAVIDAD,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasReintegros, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica
        )
    }

    // ==================== MÉTODOS DE GENERACIÓN ====================

    /**
     * LAPLACE: Todos los números tienen la misma probabilidad.
     * Genera combinaciones aleatorias puras pero muestra la probabilidad teórica.
     */
    private fun generarLaplace(maxNumero: Int, cantidad: Int, numCombinaciones: Int): List<CombinacionSugerida> {
        val probabilidad = calcularProbabilidadLaplaceNumero(maxNumero, cantidad)
        
        return (0 until numCombinaciones).map {
            val numeros = (1..maxNumero).shuffled().take(cantidad).sorted()
            CombinacionSugerida(
                numeros = numeros,
                probabilidadRelativa = probabilidad,
                explicacion = "Laplace: P = ${"%.10f".format(probabilidad / 100)}%"
            )
        }
    }

    /**
     * FRECUENCIAS: Prioriza números que han salido más veces.
     */
    private fun generarPorFrecuencias(
        frecuencias: Map<Int, Int>,
        cantidad: Int,
        numCombinaciones: Int,
        totalSorteos: Int
    ): List<CombinacionSugerida> {
        val numerosOrdenados = frecuencias.entries.sortedByDescending { it.value }.map { it.key }
        
        return (0 until numCombinaciones).map { index ->
            val offset = index * 2
            val numerosSeleccionados = numerosOrdenados
                .drop(offset)
                .take(20)
                .shuffled()
                .take(cantidad)
                .sorted()
            
            val puntuacion = numerosSeleccionados.sumOf { frecuencias.getOrDefault(it, 0) }
                .toDouble() / (totalSorteos * cantidad) * 100

            CombinacionSugerida(
                numeros = numerosSeleccionados,
                probabilidadRelativa = puntuacion.roundTo(2),
                explicacion = "Frecuencia histórica: ${puntuacion.roundTo(1)}%"
            )
        }
    }

    /**
     * NÚMEROS CALIENTES: Los más frecuentes en los últimos N sorteos.
     */
    private fun generarNumerosCalientes(
        historico: List<ResultadoPrimitiva>,
        cantidad: Int,
        numCombinaciones: Int,
        maxNumero: Int,
        ultimosSorteos: Int = 50
    ): List<CombinacionSugerida> {
        val recientes = historico.take(ultimosSorteos)
        val frecuenciasRecientes = contarFrecuencias(recientes.flatMap { it.numeros }, 1..maxNumero)
        
        return generarPorFrecuencias(frecuenciasRecientes, cantidad, numCombinaciones, recientes.size).map {
            it.copy(explicacion = "Calientes (últimos $ultimosSorteos sorteos)")
        }
    }

    private fun generarNumerosCalientesEuro(
        historico: List<ResultadoEuromillones>,
        numCombinaciones: Int,
        ultimosSorteos: Int = 50
    ): List<CombinacionSugerida> {
        val recientes = historico.take(ultimosSorteos)
        val frecuenciasRecientes = contarFrecuencias(recientes.flatMap { it.numeros }, 1..50)
        return generarPorFrecuencias(frecuenciasRecientes, 5, numCombinaciones, recientes.size).map {
            it.copy(explicacion = "Calientes (últimos $ultimosSorteos sorteos)")
        }
    }

    private fun generarNumerosCalientesGordo(
        historico: List<ResultadoGordoPrimitiva>,
        numCombinaciones: Int,
        ultimosSorteos: Int = 50
    ): List<CombinacionSugerida> {
        val recientes = historico.take(ultimosSorteos)
        val frecuenciasRecientes = contarFrecuencias(recientes.flatMap { it.numeros }, 1..54)
        return generarPorFrecuencias(frecuenciasRecientes, 5, numCombinaciones, recientes.size).map {
            it.copy(explicacion = "Calientes (últimos $ultimosSorteos sorteos)")
        }
    }

    /**
     * NÚMEROS FRÍOS: Los menos frecuentes (teoría de que "les toca").
     */
    private fun generarNumerosFrios(
        frecuencias: Map<Int, Int>,
        cantidad: Int,
        numCombinaciones: Int,
        totalSorteos: Int
    ): List<CombinacionSugerida> {
        // Ordenar de menor a mayor frecuencia
        val numerosOrdenados = frecuencias.entries.sortedBy { it.value }.map { it.key }
        
        return (0 until numCombinaciones).map { index ->
            val offset = index * 2
            val numerosSeleccionados = numerosOrdenados
                .drop(offset)
                .take(20)
                .shuffled()
                .take(cantidad)
                .sorted()

            CombinacionSugerida(
                numeros = numerosSeleccionados,
                probabilidadRelativa = 50.0, // Puntuación neutral
                explicacion = "Números fríos: poco frecuentes, 'les toca'"
            )
        }
    }

    /**
     * EQUILIBRIO: Mezcla de números calientes y fríos.
     */
    private fun generarEquilibrio(
        frecuencias: Map<Int, Int>,
        cantidad: Int,
        numCombinaciones: Int,
        totalSorteos: Int
    ): List<CombinacionSugerida> {
        val ordenados = frecuencias.entries.sortedByDescending { it.value }.map { it.key }
        val calientes = ordenados.take(ordenados.size / 2)
        val frios = ordenados.drop(ordenados.size / 2)
        
        return (0 until numCombinaciones).map {
            val mitadCalientes = cantidad / 2
            val mitadFrios = cantidad - mitadCalientes
            
            val numerosCalientes = calientes.shuffled().take(mitadCalientes)
            val numerosFrios = frios.shuffled().take(mitadFrios)
            val numeros = (numerosCalientes + numerosFrios).sorted()

            CombinacionSugerida(
                numeros = numeros,
                probabilidadRelativa = 50.0,
                explicacion = "Equilibrio: $mitadCalientes calientes + $mitadFrios fríos"
            )
        }
    }

    /**
     * PROBABILIDAD CONDICIONAL: Números que suelen salir juntos.
     */
    private fun generarCondicional(
        historico: List<ResultadoPrimitiva>,
        cantidad: Int,
        numCombinaciones: Int,
        maxNumero: Int
    ): List<CombinacionSugerida> {
        // Analizar pares de números que salen juntos
        val paresFrecuentes = mutableMapOf<Pair<Int, Int>, Int>()
        
        historico.forEach { sorteo ->
            val nums = sorteo.numeros.sorted()
            for (i in nums.indices) {
                for (j in i + 1 until nums.size) {
                    val par = Pair(nums[i], nums[j])
                    paresFrecuentes[par] = paresFrecuentes.getOrDefault(par, 0) + 1
                }
            }
        }
        
        val mejoresPares = paresFrecuentes.entries.sortedByDescending { it.value }.take(30)
        
        return (0 until numCombinaciones).map { index ->
            val numerosBase = mutableSetOf<Int>()
            
            // Tomar números de los pares más frecuentes
            mejoresPares.drop(index * 3).take(5).forEach { (par, _) ->
                numerosBase.add(par.first)
                numerosBase.add(par.second)
            }
            
            // Completar si faltan
            while (numerosBase.size < cantidad) {
                numerosBase.add((1..maxNumero).random())
            }
            
            val numeros = numerosBase.take(cantidad).sorted()

            CombinacionSugerida(
                numeros = numeros,
                probabilidadRelativa = 55.0,
                explicacion = "Condicional: números que suelen salir juntos"
            )
        }
    }

    private fun generarCondicionalEuro(
        historico: List<ResultadoEuromillones>,
        numCombinaciones: Int
    ): List<CombinacionSugerida> {
        val paresFrecuentes = mutableMapOf<Pair<Int, Int>, Int>()
        historico.forEach { sorteo ->
            val nums = sorteo.numeros.sorted()
            for (i in nums.indices) {
                for (j in i + 1 until nums.size) {
                    val par = Pair(nums[i], nums[j])
                    paresFrecuentes[par] = paresFrecuentes.getOrDefault(par, 0) + 1
                }
            }
        }
        
        return (0 until numCombinaciones).map { index ->
            val numerosBase = mutableSetOf<Int>()
            paresFrecuentes.entries.sortedByDescending { it.value }.drop(index * 2).take(4).forEach {
                numerosBase.add(it.key.first)
                numerosBase.add(it.key.second)
            }
            while (numerosBase.size < 5) numerosBase.add((1..50).random())
            
            CombinacionSugerida(
                numeros = numerosBase.take(5).sorted(),
                probabilidadRelativa = 55.0,
                explicacion = "Condicional: números que suelen salir juntos"
            )
        }
    }

    private fun generarCondicionalGordo(
        historico: List<ResultadoGordoPrimitiva>,
        numCombinaciones: Int
    ): List<CombinacionSugerida> {
        val paresFrecuentes = mutableMapOf<Pair<Int, Int>, Int>()
        historico.forEach { sorteo ->
            val nums = sorteo.numeros.sorted()
            for (i in nums.indices) {
                for (j in i + 1 until nums.size) {
                    paresFrecuentes[Pair(nums[i], nums[j])] = 
                        paresFrecuentes.getOrDefault(Pair(nums[i], nums[j]), 0) + 1
                }
            }
        }
        
        return (0 until numCombinaciones).map { index ->
            val numerosBase = mutableSetOf<Int>()
            paresFrecuentes.entries.sortedByDescending { it.value }.drop(index * 2).take(4).forEach {
                numerosBase.add(it.key.first)
                numerosBase.add(it.key.second)
            }
            while (numerosBase.size < 5) numerosBase.add((1..54).random())
            
            CombinacionSugerida(
                numeros = numerosBase.take(5).sorted(),
                probabilidadRelativa = 55.0,
                explicacion = "Condicional: números que suelen salir juntos"
            )
        }
    }

    /**
     * DESVIACIÓN DE LA MEDIA: Números alejados de su frecuencia esperada.
     */
    private fun generarDesviacionMedia(
        frecuencias: Map<Int, Int>,
        cantidad: Int,
        numCombinaciones: Int,
        totalSorteos: Int,
        maxNumero: Int
    ): List<CombinacionSugerida> {
        // Frecuencia esperada según Laplace
        val frecuenciaEsperada = (totalSorteos * cantidad).toDouble() / maxNumero
        
        // Calcular desviación de cada número
        val desviaciones = frecuencias.map { (numero, freq) ->
            numero to (freq - frecuenciaEsperada)
        }.sortedByDescending { kotlin.math.abs(it.second) }
        
        // Tomar los más desviados (por debajo de la media = "les toca")
        val porDebajoMedia = desviaciones.filter { it.second < 0 }.map { it.first }
        
        return (0 until numCombinaciones).map { index ->
            val numeros = porDebajoMedia.drop(index * 2).take(cantidad * 2).shuffled().take(cantidad).sorted()

            CombinacionSugerida(
                numeros = numeros.ifEmpty { (1..maxNumero).shuffled().take(cantidad).sorted() },
                probabilidadRelativa = 50.0,
                explicacion = "Desviación media: por debajo de frecuencia esperada"
            )
        }
    }

    /**
     * ALEATORIO PURO: Completamente al azar.
     */
    private fun generarAleatorio(maxNumero: Int, cantidad: Int, numCombinaciones: Int): List<CombinacionSugerida> {
        return (0 until numCombinaciones).map {
            val numeros = (1..maxNumero).shuffled().take(cantidad).sorted()
            CombinacionSugerida(
                numeros = numeros,
                probabilidadRelativa = 50.0,
                explicacion = "Aleatorio puro: tan válido como cualquier otro"
            )
        }
    }

    // ==================== UTILIDADES ====================

    private fun contarFrecuencias(numeros: List<Int>, rango: IntRange): Map<Int, Int> {
        val frecuencias = rango.associateWith { 0 }.toMutableMap()
        numeros.forEach { numero ->
            if (numero in rango) {
                frecuencias[numero] = frecuencias.getOrDefault(numero, 0) + 1
            }
        }
        return frecuencias
    }

    private fun obtenerTopNumeros(
        frecuencias: Map<Int, Int>,
        cantidad: Int,
        totalSorteos: Int,
        menosFrecuentes: Boolean = false
    ): List<EstadisticaNumero> {
        return frecuencias.entries
            .sortedBy { if (menosFrecuentes) it.value else -it.value }
            .take(cantidad)
            .map { (numero, apariciones) ->
                EstadisticaNumero(
                    numero = numero,
                    apariciones = apariciones,
                    porcentaje = if (totalSorteos > 0) {
                        (apariciones.toDouble() / totalSorteos * 100).roundTo(2)
                    } else 0.0
                )
            }
    }

    private fun calcularProbabilidadLaplace(n: Int, r: Int): String {
        // C(n,r) = n! / (r! * (n-r)!)
        val combinaciones = factorial(n) / (factorial(r) * factorial(n - r))
        val probabilidad = 1.0 / combinaciones.toDouble()
        return "1 entre ${formatearNumero(combinaciones)} (${formatearPorcentaje(probabilidad * 100)})"
    }

    private fun calcularProbabilidadLaplaceNumero(n: Int, r: Int): Double {
        val combinaciones = factorial(n) / (factorial(r) * factorial(n - r))
        return 1.0 / combinaciones.toDouble() * 100
    }

    private fun factorial(n: Int): Long {
        if (n <= 1) return 1
        var result = 1L
        for (i in 2..n) {
            result *= i
        }
        return result
    }

    private fun formatearNumero(n: Long): String {
        return String.format("%,d", n).replace(",", ".")
    }

    private fun formatearPorcentaje(p: Double): String {
        return if (p < 0.0001) {
            "≈${String.format("%.8f", p)}%"
        } else {
            "${String.format("%.4f", p)}%"
        }
    }

    private fun crearAnalisisVacio(tipoLoteria: TipoLoteria, metodo: MetodoCalculo): AnalisisProbabilidad {
        return AnalisisProbabilidad(
            tipoLoteria = tipoLoteria,
            metodoCalculo = metodo,
            totalSorteos = 0,
            combinacionesSugeridas = emptyList(),
            numerosMasFrequentes = emptyList(),
            numerosMenosFrequentes = emptyList(),
            probabilidadTeorica = "Sin datos históricos"
        )
    }

    private fun Double.roundTo(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return (this * multiplier).roundToInt() / multiplier
    }
}
