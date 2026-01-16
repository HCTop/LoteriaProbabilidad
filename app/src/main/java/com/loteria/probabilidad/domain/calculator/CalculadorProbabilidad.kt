package com.loteria.probabilidad.domain.calculator

import android.content.Context
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.domain.ml.MotorInteligencia
import com.loteria.probabilidad.domain.ml.ResumenIA
import kotlin.math.roundToInt

/**
 * Calculador de probabilidades con múltiples métodos de análisis.
 * 
 * Métodos implementados:
 * - IA_GENETICA: Algoritmo genético con APRENDIZAJE PERSISTENTE
 * - LAPLACE: Probabilidad teórica matemática pura
 * - FRECUENCIAS: Basado en histórico de apariciones
 * - NUMEROS_CALIENTES: Los más frecuentes recientemente
 * - NUMEROS_FRIOS: Los menos frecuentes (teoría del equilibrio)
 * - EQUILIBRIO_ESTADISTICO: Mezcla de calientes y fríos
 * - PROBABILIDAD_CONDICIONAL: Números que salen juntos
 * - DESVIACION_MEDIA: Números alejados de su frecuencia esperada
 * - ALEATORIO_PURO: Selección completamente aleatoria
 */
class CalculadorProbabilidad(private val context: Context? = null) {
    
    // Motor de IA con aprendizaje persistente
    private val motorIA = MotorInteligencia(context)
    
    /**
     * Hace que la IA aprenda de los resultados del backtesting.
     */
    fun aprenderDeBacktest(
        resultados: List<ResultadoBacktest>,
        historico: List<ResultadoPrimitiva>,
        tipoLoteria: String,
        sorteosProbados: Int
    ) {
        motorIA.aprenderDeBacktest(resultados, historico, tipoLoteria, sorteosProbados)
    }
    
    /**
     * Obtiene el resumen del estado de la IA.
     */
    fun obtenerResumenIA(): ResumenIA? = motorIA.obtenerResumenIA()

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
        
        // Obtener reintegros ordenados por frecuencia para variar entre combinaciones
        val reintegrosOrdenados = frecuenciasReintegros.entries
            .sortedByDescending { it.value }
            .map { it.key }
        
        // Generar combinaciones según el método
        val combinacionesBase = when (metodo) {
            MetodoCalculo.IA_GENETICA -> motorIA.generarCombinacionesInteligentes(
                historico, maxNumero, cantidadNumeros, numCombinaciones, tipoLoteria.name
            )
            MetodoCalculo.LAPLACE -> generarLaplace(maxNumero, cantidadNumeros, numCombinaciones)
            MetodoCalculo.FRECUENCIAS -> generarPorFrecuencias(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.NUMEROS_CALIENTES -> generarNumerosCalientes(historico, cantidadNumeros, numCombinaciones, maxNumero)
            MetodoCalculo.NUMEROS_FRIOS -> generarNumerosFrios(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> generarEquilibrio(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> generarCondicional(historico, cantidadNumeros, numCombinaciones, maxNumero)
            MetodoCalculo.DESVIACION_MEDIA -> generarDesviacionMedia(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size, maxNumero)
            MetodoCalculo.ALEATORIO_PURO -> generarAleatorio(maxNumero, cantidadNumeros, numCombinaciones)
        }
        
        // Añadir reintegro DIFERENTE a cada combinación
        val combinaciones = combinacionesBase.mapIndexed { index, combinacion ->
            val reintegro = reintegrosOrdenados[index % reintegrosOrdenados.size]
            combinacion.copy(
                complementarios = listOf(reintegro),
                explicacion = "${combinacion.explicacion} | Reintegro: $reintegro"
            )
        }

        // Calcular probabilidad teórica (Laplace)
        val probabilidadTeorica = calcularProbabilidadLaplace(maxNumero, cantidadNumeros)
        
        // Obtener fecha del último sorteo
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha

        return AnalisisProbabilidad(
            tipoLoteria = tipoLoteria,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasReintegros, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo
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
        
        // Obtener estrellas ordenadas por frecuencia
        val estrellasOrdenadas = frecuenciasEstrellas.entries
            .sortedByDescending { it.value }
            .map { it.key }
        
        val combinacionesBase = when (metodo) {
            MetodoCalculo.IA_GENETICA -> motorIA.generarCombinacionesInteligenteEuro(historico, numCombinaciones)
            MetodoCalculo.LAPLACE -> generarLaplace(maxNumero, cantidadNumeros, numCombinaciones)
            MetodoCalculo.FRECUENCIAS -> generarPorFrecuencias(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.NUMEROS_CALIENTES -> generarNumerosCalientesEuro(historico, numCombinaciones)
            MetodoCalculo.NUMEROS_FRIOS -> generarNumerosFrios(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> generarEquilibrio(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> generarCondicionalEuro(historico, numCombinaciones)
            MetodoCalculo.DESVIACION_MEDIA -> generarDesviacionMedia(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size, maxNumero)
            MetodoCalculo.ALEATORIO_PURO -> generarAleatorio(maxNumero, cantidadNumeros, numCombinaciones)
        }
        
        // Añadir estrellas DIFERENTES a cada combinación
        val combinaciones = combinacionesBase.mapIndexed { index, combinacion ->
            // Rotar las estrellas para cada combinación
            val offset = index * 2
            val estrella1 = estrellasOrdenadas[offset % estrellasOrdenadas.size]
            val estrella2 = estrellasOrdenadas[(offset + 1) % estrellasOrdenadas.size]
            val estrellas = listOf(estrella1, estrella2).distinct().sorted()
            
            // Si son iguales, tomar la siguiente
            val estrellasFinales = if (estrellas.size == 1) {
                listOf(estrella1, estrellasOrdenadas[(offset + 2) % estrellasOrdenadas.size]).sorted()
            } else {
                estrellas
            }
            
            combinacion.copy(
                complementarios = estrellasFinales,
                explicacion = "${combinacion.explicacion} | Estrellas: ${estrellasFinales.joinToString(", ")}"
            )
        }

        // Probabilidad Euromillones: C(50,5) * C(12,2) = 139,838,160
        val probabilidadTeorica = "1 entre 139.838.160 (≈0.0000000715%)"
        
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha
        
        // IMPORTANTE: Para estrellas, dividir por total de estrellas (2 por sorteo), no por sorteos
        val totalEstrellas = historico.size * 2

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.EUROMILLONES,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasEstrellas, 5, totalEstrellas),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo
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
        
        // Obtener números clave ordenados por frecuencia
        val clavesOrdenadas = frecuenciasClave.entries
            .sortedByDescending { it.value }
            .map { it.key }
        
        val combinacionesBase = when (metodo) {
            MetodoCalculo.IA_GENETICA -> motorIA.generarCombinacionesInteligenteGordo(historico, numCombinaciones)
            MetodoCalculo.LAPLACE -> generarLaplace(maxNumero, cantidadNumeros, numCombinaciones)
            MetodoCalculo.FRECUENCIAS -> generarPorFrecuencias(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.NUMEROS_CALIENTES -> generarNumerosCalientesGordo(historico, numCombinaciones)
            MetodoCalculo.NUMEROS_FRIOS -> generarNumerosFrios(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> generarEquilibrio(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> generarCondicionalGordo(historico, numCombinaciones)
            MetodoCalculo.DESVIACION_MEDIA -> generarDesviacionMedia(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size, maxNumero)
            MetodoCalculo.ALEATORIO_PURO -> generarAleatorio(maxNumero, cantidadNumeros, numCombinaciones)
        }
        
        // Añadir número clave DIFERENTE a cada combinación
        val combinaciones = combinacionesBase.mapIndexed { index, combinacion ->
            val numeroClave = clavesOrdenadas[index % clavesOrdenadas.size]
            combinacion.copy(
                complementarios = listOf(numeroClave),
                explicacion = "${combinacion.explicacion} | Nº Clave: $numeroClave"
            )
        }

        // Probabilidad El Gordo: C(54,5) * 10 = 31,625,100
        val probabilidadTeorica = "1 entre 31.625.100 (≈0.00000316%)"
        
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.GORDO_PRIMITIVA,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasClave, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo
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

        // ==================== ANÁLISIS DE FRECUENCIAS POR POSICIÓN ====================
        // Cada posición del número de 5 dígitos se analiza independientemente (0-9)
        // Posición 0: Decenas de millar, Posición 1: Millares, Posición 2: Centenas, 
        // Posición 3: Decenas, Posición 4: Unidades
        
        val frecuenciasPorPosicion = Array(5) { mutableMapOf<Int, Int>() }
        val frecuenciasRecientesPorPosicion = Array(5) { mutableMapOf<Int, Int>() }
        
        // Inicializar con ceros
        for (pos in 0..4) {
            for (digito in 0..9) {
                frecuenciasPorPosicion[pos][digito] = 0
                frecuenciasRecientesPorPosicion[pos][digito] = 0
            }
        }
        
        // Contar frecuencias de cada dígito en cada posición
        historico.forEach { resultado ->
            val numero = resultado.primerPremio.padStart(5, '0')
            numero.forEachIndexed { posicion, char ->
                val digito = char.digitToIntOrNull() ?: return@forEachIndexed
                frecuenciasPorPosicion[posicion][digito] = 
                    (frecuenciasPorPosicion[posicion][digito] ?: 0) + 1
            }
        }
        
        // Contar frecuencias recientes (últimos 50 sorteos)
        historico.take(50).forEach { resultado ->
            val numero = resultado.primerPremio.padStart(5, '0')
            numero.forEachIndexed { posicion, char ->
                val digito = char.digitToIntOrNull() ?: return@forEachIndexed
                frecuenciasRecientesPorPosicion[posicion][digito] = 
                    (frecuenciasRecientesPorPosicion[posicion][digito] ?: 0) + 1
            }
        }
        
        // Análisis de terminaciones (últimas 2 cifras) para compatibilidad
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
                        explicacion = "🎲 Aleatorio: ${numero.toString().padStart(5, '0')}"
                    )
                }
            }
            MetodoCalculo.FRECUENCIAS -> {
                // Usar el dígito MÁS FRECUENTE en cada posición
                generarNumerosOptimos(
                    frecuenciasPorPosicion, 
                    numCombinaciones, 
                    historico.size,
                    "📊 Frecuencias",
                    seleccionarMasFrecuentes = true
                )
            }
            MetodoCalculo.NUMEROS_CALIENTES -> {
                // Usar dígitos más frecuentes en los últimos 50 sorteos
                generarNumerosOptimos(
                    frecuenciasRecientesPorPosicion, 
                    numCombinaciones, 
                    minOf(50, historico.size),
                    "🔥 Calientes",
                    seleccionarMasFrecuentes = true
                )
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                // Usar dígitos MENOS frecuentes (teoría del equilibrio)
                generarNumerosOptimos(
                    frecuenciasPorPosicion, 
                    numCombinaciones, 
                    historico.size,
                    "❄️ Fríos",
                    seleccionarMasFrecuentes = false
                )
            }
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> {
                // Mezcla: 3 posiciones frecuentes, 2 posiciones frías
                generarNumerosMixtos(
                    frecuenciasPorPosicion,
                    numCombinaciones,
                    historico.size
                )
            }
            MetodoCalculo.DESVIACION_MEDIA -> {
                // Dígitos más alejados de la media esperada (10%)
                generarNumerosDesviacion(
                    frecuenciasPorPosicion,
                    numCombinaciones,
                    historico.size
                )
            }
            else -> {
                // Por defecto: usar terminaciones + dígitos frecuentes
                val terminacionesOrdenadas = frecuenciasTerminaciones.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    
                (0 until numCombinaciones).map { index ->
                    val terminacion = terminacionesOrdenadas.getOrElse(index) { (0..99).random() }
                    val frecuencia = frecuenciasTerminaciones[terminacion] ?: 0
                    
                    // Generar prefijo usando dígitos frecuentes
                    val dig0 = obtenerDigitoTop(frecuenciasPorPosicion[0], index)
                    val dig1 = obtenerDigitoTop(frecuenciasPorPosicion[1], index)
                    val dig2 = obtenerDigitoTop(frecuenciasPorPosicion[2], index)
                    val prefijo = dig0 * 100 + dig1 * 10 + dig2
                    
                    val numero = prefijo * 100 + terminacion
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = if (historico.isNotEmpty()) {
                            (frecuencia.toDouble() / historico.size * 100).roundTo(2)
                        } else 0.001,
                        explicacion = "🎯 Número: ${numero.toString().padStart(5, '0')} | Term: ${terminacion.toString().padStart(2, '0')} (${frecuencia}x)"
                    )
                }
            }
        }

        val probabilidadTeorica = "1 entre 100.000 (0.001%)"
        
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha

        return AnalisisProbabilidad(
            tipoLoteria = tipoLoteria,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasReintegros, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo,
            analisisPorPosicion = crearAnalisisPorPosicion(frecuenciasPorPosicion, historico.size)
        )
    }
    
    // ==================== FUNCIONES AUXILIARES PARA ANÁLISIS DE DÍGITOS ====================
    
    /**
     * Genera números usando los dígitos más/menos frecuentes por posición.
     */
    private fun generarNumerosOptimos(
        frecuenciasPorPosicion: Array<MutableMap<Int, Int>>,
        numCombinaciones: Int,
        totalSorteos: Int,
        prefijo: String,
        seleccionarMasFrecuentes: Boolean
    ): List<CombinacionSugerida> {
        val combinaciones = mutableListOf<CombinacionSugerida>()
        val numerosGenerados = mutableSetOf<Int>()
        
        repeat(numCombinaciones) { index ->
            var numero: Int
            var intentos = 0
            
            do {
                val digitos = (0..4).map { pos ->
                    obtenerDigitoRanked(frecuenciasPorPosicion[pos], index + intentos, seleccionarMasFrecuentes)
                }
                numero = digitos[0] * 10000 + digitos[1] * 1000 + digitos[2] * 100 + digitos[3] * 10 + digitos[4]
                intentos++
            } while (numero in numerosGenerados && intentos < 20)
            
            numerosGenerados.add(numero)
            
            // Calcular puntuación basada en frecuencias
            val puntuacion = calcularPuntuacionNumero(numero, frecuenciasPorPosicion, totalSorteos)
            
            combinaciones.add(CombinacionSugerida(
                numeros = listOf(numero),
                probabilidadRelativa = puntuacion,
                explicacion = "$prefijo: ${numero.toString().padStart(5, '0')} | Score: ${puntuacion.roundTo(1)}%"
            ))
        }
        
        return combinaciones
    }
    
    /**
     * Genera números mixtos: algunas posiciones frecuentes, otras frías.
     */
    private fun generarNumerosMixtos(
        frecuenciasPorPosicion: Array<MutableMap<Int, Int>>,
        numCombinaciones: Int,
        totalSorteos: Int
    ): List<CombinacionSugerida> {
        val combinaciones = mutableListOf<CombinacionSugerida>()
        val numerosGenerados = mutableSetOf<Int>()
        
        repeat(numCombinaciones) { index ->
            var numero: Int
            var intentos = 0
            
            do {
                // Alternar: posiciones 0,2,4 frecuentes, posiciones 1,3 frías
                val digitos = (0..4).map { pos ->
                    val usarFrecuente = (pos + index) % 2 == 0
                    obtenerDigitoRanked(frecuenciasPorPosicion[pos], index + intentos, usarFrecuente)
                }
                numero = digitos[0] * 10000 + digitos[1] * 1000 + digitos[2] * 100 + digitos[3] * 10 + digitos[4]
                intentos++
            } while (numero in numerosGenerados && intentos < 20)
            
            numerosGenerados.add(numero)
            val puntuacion = calcularPuntuacionNumero(numero, frecuenciasPorPosicion, totalSorteos)
            
            combinaciones.add(CombinacionSugerida(
                numeros = listOf(numero),
                probabilidadRelativa = puntuacion,
                explicacion = "⚖️ Equilibrio: ${numero.toString().padStart(5, '0')} | Score: ${puntuacion.roundTo(1)}%"
            ))
        }
        
        return combinaciones
    }
    
    /**
     * Genera números basándose en la desviación de la media esperada.
     */
    private fun generarNumerosDesviacion(
        frecuenciasPorPosicion: Array<MutableMap<Int, Int>>,
        numCombinaciones: Int,
        totalSorteos: Int
    ): List<CombinacionSugerida> {
        val mediaEsperada = totalSorteos / 10.0 // Cada dígito debería aparecer 10% de las veces
        
        // Para cada posición, calcular desviación de cada dígito
        val desviacionesPorPosicion = Array(5) { pos ->
            frecuenciasPorPosicion[pos].map { (digito, freq) ->
                digito to kotlin.math.abs(freq - mediaEsperada)
            }.sortedByDescending { it.second }
        }
        
        val combinaciones = mutableListOf<CombinacionSugerida>()
        val numerosGenerados = mutableSetOf<Int>()
        
        repeat(numCombinaciones) { index ->
            var numero: Int
            var intentos = 0
            
            do {
                val digitos = (0..4).map { pos ->
                    desviacionesPorPosicion[pos].getOrNull(index + intentos)?.first ?: (0..9).random()
                }
                numero = digitos[0] * 10000 + digitos[1] * 1000 + digitos[2] * 100 + digitos[3] * 10 + digitos[4]
                intentos++
            } while (numero in numerosGenerados && intentos < 20)
            
            numerosGenerados.add(numero)
            val puntuacion = calcularPuntuacionNumero(numero, frecuenciasPorPosicion, totalSorteos)
            
            combinaciones.add(CombinacionSugerida(
                numeros = listOf(numero),
                probabilidadRelativa = puntuacion,
                explicacion = "📈 Desviación: ${numero.toString().padStart(5, '0')} | Score: ${puntuacion.roundTo(1)}%"
            ))
        }
        
        return combinaciones
    }
    
    /**
     * Obtiene el dígito en el ranking especificado.
     */
    private fun obtenerDigitoRanked(
        frecuencias: Map<Int, Int>, 
        rank: Int, 
        masFrecuente: Boolean
    ): Int {
        val ordenados = if (masFrecuente) {
            frecuencias.entries.sortedByDescending { it.value }
        } else {
            frecuencias.entries.sortedBy { it.value }
        }
        return ordenados.getOrNull(rank % 10)?.key ?: (0..9).random()
    }
    
    /**
     * Obtiene el dígito top para una posición.
     */
    private fun obtenerDigitoTop(frecuencias: Map<Int, Int>, offset: Int): Int {
        val ordenados = frecuencias.entries.sortedByDescending { it.value }
        return ordenados.getOrNull(offset % 10)?.key ?: (0..9).random()
    }
    
    /**
     * Calcula la puntuación de un número basándose en las frecuencias históricas.
     */
    private fun calcularPuntuacionNumero(
        numero: Int,
        frecuenciasPorPosicion: Array<MutableMap<Int, Int>>,
        totalSorteos: Int
    ): Double {
        if (totalSorteos == 0) return 0.0
        
        val numeroStr = numero.toString().padStart(5, '0')
        var puntuacionTotal = 0.0
        
        numeroStr.forEachIndexed { pos, char ->
            val digito = char.digitToIntOrNull() ?: return@forEachIndexed
            val frecuencia = frecuenciasPorPosicion[pos][digito] ?: 0
            puntuacionTotal += (frecuencia.toDouble() / totalSorteos) * 100
        }
        
        return puntuacionTotal / 5 // Promedio de las 5 posiciones
    }
    
    /**
     * Crea el análisis detallado por posición para mostrar en UI.
     */
    private fun crearAnalisisPorPosicion(
        frecuenciasPorPosicion: Array<MutableMap<Int, Int>>,
        totalSorteos: Int
    ): Map<String, List<Pair<Int, Double>>> {
        val nombresPosicion = listOf(
            "Decenas de millar (1ª)",
            "Millares (2ª)",
            "Centenas (3ª)",
            "Decenas (4ª)",
            "Unidades (5ª)"
        )
        
        return nombresPosicion.mapIndexed { pos, nombre ->
            nombre to frecuenciasPorPosicion[pos].entries
                .sortedByDescending { it.value }
                .map { (digito, freq) ->
                    digito to if (totalSorteos > 0) (freq.toDouble() / totalSorteos * 100) else 0.0
                }
        }.toMap()
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

        // ==================== ANÁLISIS DE FRECUENCIAS POR POSICIÓN ====================
        val frecuenciasPorPosicion = Array(5) { mutableMapOf<Int, Int>() }
        val frecuenciasRecientesPorPosicion = Array(5) { mutableMapOf<Int, Int>() }
        
        // Inicializar con ceros
        for (pos in 0..4) {
            for (digito in 0..9) {
                frecuenciasPorPosicion[pos][digito] = 0
                frecuenciasRecientesPorPosicion[pos][digito] = 0
            }
        }
        
        // Contar frecuencias de cada dígito en cada posición (El Gordo)
        historico.forEach { resultado ->
            val numero = resultado.gordo.padStart(5, '0')
            numero.forEachIndexed { posicion, char ->
                val digito = char.digitToIntOrNull() ?: return@forEachIndexed
                frecuenciasPorPosicion[posicion][digito] = 
                    (frecuenciasPorPosicion[posicion][digito] ?: 0) + 1
            }
        }
        
        // Frecuencias recientes (últimos 20 sorteos para Navidad)
        historico.take(20).forEach { resultado ->
            val numero = resultado.gordo.padStart(5, '0')
            numero.forEachIndexed { posicion, char ->
                val digito = char.digitToIntOrNull() ?: return@forEachIndexed
                frecuenciasRecientesPorPosicion[posicion][digito] = 
                    (frecuenciasRecientesPorPosicion[posicion][digito] ?: 0) + 1
            }
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
                        explicacion = "🎲 Aleatorio: ${numero.toString().padStart(5, '0')}"
                    )
                }
            }
            MetodoCalculo.FRECUENCIAS -> {
                generarNumerosOptimos(
                    frecuenciasPorPosicion, 
                    numCombinaciones, 
                    historico.size,
                    "📊 Frecuencias",
                    seleccionarMasFrecuentes = true
                )
            }
            MetodoCalculo.NUMEROS_CALIENTES -> {
                generarNumerosOptimos(
                    frecuenciasRecientesPorPosicion, 
                    numCombinaciones, 
                    minOf(20, historico.size),
                    "🔥 Calientes",
                    seleccionarMasFrecuentes = true
                )
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                generarNumerosOptimos(
                    frecuenciasPorPosicion, 
                    numCombinaciones, 
                    historico.size,
                    "❄️ Fríos",
                    seleccionarMasFrecuentes = false
                )
            }
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> {
                generarNumerosMixtos(
                    frecuenciasPorPosicion,
                    numCombinaciones,
                    historico.size
                )
            }
            MetodoCalculo.DESVIACION_MEDIA -> {
                generarNumerosDesviacion(
                    frecuenciasPorPosicion,
                    numCombinaciones,
                    historico.size
                )
            }
            else -> {
                val terminacionesOrdenadas = frecuenciasTerminaciones.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    
                (0 until numCombinaciones).map { index ->
                    val terminacion = terminacionesOrdenadas.getOrElse(index) { (0..99).random() }
                    val frecuencia = frecuenciasTerminaciones[terminacion] ?: 0
                    
                    val dig0 = obtenerDigitoTop(frecuenciasPorPosicion[0], index)
                    val dig1 = obtenerDigitoTop(frecuenciasPorPosicion[1], index)
                    val dig2 = obtenerDigitoTop(frecuenciasPorPosicion[2], index)
                    val prefijo = dig0 * 100 + dig1 * 10 + dig2
                    
                    val numero = prefijo * 100 + terminacion
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = if (historico.isNotEmpty()) {
                            (frecuencia.toDouble() / historico.size * 100).roundTo(2)
                        } else 0.001,
                        explicacion = "🎄 Décimo: ${numero.toString().padStart(5, '0')} | Term: ${terminacion.toString().padStart(2, '0')} (${frecuencia}x)"
                    )
                }
            }
        }

        val probabilidadTeorica = "1 entre 100.000 (0.001%) para El Gordo"
        
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.NAVIDAD,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasReintegros, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo,
            analisisPorPosicion = crearAnalisisPorPosicion(frecuenciasPorPosicion, historico.size)
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
        // Usamos BigInteger para evitar overflow
        val combinaciones = calcularCombinaciones(n, r)
        val probabilidad = 1.0 / combinaciones.toDouble()
        return "1 entre ${formatearNumeroBig(combinaciones)} (${formatearPorcentaje(probabilidad * 100)})"
    }

    private fun calcularProbabilidadLaplaceNumero(n: Int, r: Int): Double {
        val combinaciones = calcularCombinaciones(n, r)
        return 1.0 / combinaciones.toDouble() * 100
    }
    
    /**
     * Calcula C(n,r) usando BigInteger para evitar overflow.
     * C(n,r) = n! / (r! * (n-r)!)
     * 
     * Optimización: C(n,r) = [n * (n-1) * ... * (n-r+1)] / [r * (r-1) * ... * 1]
     */
    private fun calcularCombinaciones(n: Int, r: Int): java.math.BigInteger {
        if (r > n) return java.math.BigInteger.ZERO
        if (r == 0 || r == n) return java.math.BigInteger.ONE
        
        // Usar el menor de r y (n-r) para optimizar
        val k = if (r > n - r) n - r else r
        
        var resultado = java.math.BigInteger.ONE
        for (i in 0 until k) {
            resultado = resultado.multiply(java.math.BigInteger.valueOf((n - i).toLong()))
            resultado = resultado.divide(java.math.BigInteger.valueOf((i + 1).toLong()))
        }
        return resultado
    }

    private fun formatearNumeroBig(n: java.math.BigInteger): String {
        // Formatear con puntos como separadores de miles
        val str = n.toString()
        val reversed = str.reversed()
        val formatted = reversed.chunked(3).joinToString(".").reversed()
        return formatted
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
    
    // ==================== SISTEMA DE BACKTESTING ====================
    
    /**
     * Ejecuta backtesting para Primitiva/Bonoloto.
     * Retrocede N sorteos en el histórico y prueba cada método.
     * 
     * @param historico Lista completa de sorteos
     * @param diasAtras Número de sorteos a retroceder para probar
     * @return Lista de resultados de backtesting por método
     */
    /**
     * Callback para reportar progreso durante el backtesting
     */
    var onProgresoBacktest: ((metodo: String, combinacion: Int, total: Int) -> Unit)? = null
    
    fun ejecutarBacktestPrimitiva(
        historico: List<ResultadoPrimitiva>,
        diasAtras: Int = 10,
        tipoLoteria: String = "PRIMITIVA"
    ): List<ResultadoBacktest> {
        if (historico.size <= diasAtras) return emptyList()
        
        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = MetodoCalculo.values()
        val totalCombsPorMetodo = diasAtras * 5
        val totalCombs = metodos.size * totalCombsPorMetodo
        var combinacionGlobal = 0
        
        // OPTIMIZACIÓN: Precalcular frecuencias una sola vez
        val maxNumero = if (tipoLoteria == "BONOLOTO") 49 else 49
        val frecuenciasGlobales = contarFrecuencias(historico.flatMap { it.numeros }, 1..maxNumero)
        
        // Probar cada método
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            
            var aciertos0 = 0
            var aciertos1 = 0
            var aciertos2 = 0
            var aciertos3 = 0
            var aciertos4 = 0
            var aciertos5 = 0
            var aciertos6 = 0
            var aciertosComplementario = 0
            var aciertosReintegro = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            // Para cada sorteo de prueba
            for (i in 0 until diasAtras) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.isEmpty()) continue
                
                // OPTIMIZACIÓN: Generar combinaciones directamente sin crear AnalisisProbabilidad completo
                val combinaciones = generarCombinacionesRapido(
                    frecuenciasGlobales, metodo, 6, 5, historicoHastaMomento
                )
                
                val sorteoReal = historico[i]
                val numerosReales = sorteoReal.numeros.toSet()
                val complementarioReal = sorteoReal.complementario
                
                for (numeros in combinaciones) {
                    combinacionGlobal++
                    // Actualizar progreso cada 50 combinaciones
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val numerosPredichos = numeros.toSet()
                    val aciertosEnCombinacion = numerosPredichos.intersect(numerosReales).size
                    
                    when (aciertosEnCombinacion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        5 -> aciertos5++
                        6 -> aciertos6++
                    }
                    
                    if (aciertosEnCombinacion >= 5 && complementarioReal in numerosPredichos) {
                        aciertosComplementario++
                    }
                    
                    if (aciertosEnCombinacion > mejorAcierto) {
                        mejorAcierto = aciertosEnCombinacion
                    }
                    totalAciertos += aciertosEnCombinacion
                }
            }
            
            val totalCombinaciones = diasAtras * 5
            val puntuacion = (aciertos1 * 1.0 + aciertos2 * 3.0 + aciertos3 * 10.0 + 
                             aciertos4 * 50.0 + aciertos5 * 200.0 + aciertos6 * 1000.0 +
                             aciertosComplementario * 100.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasAtras,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertos6 = aciertos6,
                aciertosComplementario = aciertosComplementario,
                aciertosReintegro = aciertosReintegro,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = tipoLoteria
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    /**
     * Genera combinaciones de forma rápida sin crear objetos pesados
     */
    private fun generarCombinacionesRapido(
        frecuencias: Map<Int, Int>,
        metodo: MetodoCalculo,
        cantidadNumeros: Int,
        numCombinaciones: Int,
        historico: List<ResultadoPrimitiva>
    ): List<List<Int>> {
        val maxNumero = 49
        val totalSorteos = historico.size.coerceAtLeast(1)
        
        return when (metodo) {
            MetodoCalculo.FRECUENCIAS -> {
                // Top números más frecuentes
                val topNums = frecuencias.entries.sortedByDescending { it.value }.take(20).map { it.key }
                (0 until numCombinaciones).map { i ->
                    topNums.drop(i).take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.NUMEROS_CALIENTES -> {
                // Números de los últimos sorteos
                val recientes = historico.take(10).flatMap { it.numeros }.groupingBy { it }.eachCount()
                val calientes = recientes.entries.sortedByDescending { it.value }.take(20).map { it.key }
                (0 until numCombinaciones).map { i ->
                    calientes.drop(i).take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                // Números menos frecuentes
                val frios = frecuencias.entries.sortedBy { it.value }.take(20).map { it.key }
                (0 until numCombinaciones).map { i ->
                    frios.drop(i).take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> {
                // Mix de calientes y fríos
                val ordenados = frecuencias.entries.sortedByDescending { it.value }
                val calientes = ordenados.take(10).map { it.key }
                val frios = ordenados.takeLast(10).map { it.key }
                (0 until numCombinaciones).map { i ->
                    (calientes.take(3) + frios.take(3)).shuffled().take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.LAPLACE -> {
                // Distribución uniforme por rotación
                (0 until numCombinaciones).map { i ->
                    val offset = i * 7
                    (1..cantidadNumeros).map { j -> ((offset + j * 8) % maxNumero) + 1 }.sorted()
                }
            }
            MetodoCalculo.ALEATORIO_PURO -> {
                (0 until numCombinaciones).map {
                    (1..maxNumero).shuffled().take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.DESVIACION_MEDIA -> {
                val media = frecuencias.entries.sumOf { it.value } / frecuencias.size.coerceAtLeast(1)
                val cercanos = frecuencias.entries.sortedBy { kotlin.math.abs(it.value - media) }.take(20).map { it.key }
                (0 until numCombinaciones).map { i ->
                    cercanos.drop(i).take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> {
                // Basado en último sorteo
                val ultimo = historico.firstOrNull()?.numeros ?: listOf(1,2,3,4,5,6)
                val candidatos = (1..maxNumero).filter { it !in ultimo }
                (0 until numCombinaciones).map { i ->
                    candidatos.shuffled().take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.IA_GENETICA -> {
                // Versión simplificada del algoritmo genético
                motorIA.generarCombinacionesInteligentes(historico, 49, cantidadNumeros, numCombinaciones).map { it.numeros }
            }
        }
    }
    
    /**
     * Ejecuta backtesting para Euromillones.
     */
    fun ejecutarBacktestEuromillones(
        historico: List<ResultadoEuromillones>,
        diasAtras: Int = 10
    ): List<ResultadoBacktest> {
        if (historico.size <= diasAtras) return emptyList()
        
        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = MetodoCalculo.values()
        val totalCombs = metodos.size * diasAtras * 5
        var combinacionGlobal = 0
        
        // Precalcular frecuencias
        val frecuenciasNumeros = contarFrecuencias(historico.flatMap { it.numeros }, 1..50)
        val frecuenciasEstrellas = contarFrecuencias(historico.flatMap { it.estrellas }, 1..12)
        
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            
            var aciertos0 = 0
            var aciertos1 = 0
            var aciertos2 = 0
            var aciertos3 = 0
            var aciertos4 = 0
            var aciertos5 = 0
            var aciertosEstrella1 = 0
            var aciertosEstrella2 = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasAtras) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.isEmpty()) continue
                
                // Generar combinaciones rápido
                val combinaciones = generarCombinacionesEuroRapido(frecuenciasNumeros, frecuenciasEstrellas, metodo, 5)
                val sorteoReal = historico[i]
                val numerosReales = sorteoReal.numeros.toSet()
                val estrellasReales = sorteoReal.estrellas.toSet()
                
                for ((numeros, estrellas) in combinaciones) {
                    combinacionGlobal++
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val aciertosEnCombinacion = numeros.toSet().intersect(numerosReales).size
                    val aciertosEstrellas = estrellas.toSet().intersect(estrellasReales).size
                    
                    when (aciertosEnCombinacion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        5 -> aciertos5++
                    }
                    
                    if (aciertosEstrellas >= 1) aciertosEstrella1++
                    if (aciertosEstrellas >= 2) aciertosEstrella2++
                    
                    if (aciertosEnCombinacion > mejorAcierto) mejorAcierto = aciertosEnCombinacion
                    totalAciertos += aciertosEnCombinacion
                }
            }
            
            val totalCombinaciones = diasAtras * 5
            val puntuacion = (aciertos1 * 1.0 + aciertos2 * 3.0 + aciertos3 * 10.0 + 
                             aciertos4 * 50.0 + aciertos5 * 500.0 +
                             aciertosEstrella1 * 10.0 + aciertosEstrella2 * 50.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasAtras,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertosEstrella1 = aciertosEstrella1,
                aciertosEstrella2 = aciertosEstrella2,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = "EUROMILLONES"
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    private fun generarCombinacionesEuroRapido(
        frecNums: Map<Int, Int>,
        frecEstrellas: Map<Int, Int>,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): List<Pair<List<Int>, List<Int>>> {
        val topNums = frecNums.entries.sortedByDescending { it.value }.map { it.key }
        val topEstrellas = frecEstrellas.entries.sortedByDescending { it.value }.map { it.key }
        
        return (0 until numCombinaciones).map { i ->
            val nums = when (metodo) {
                MetodoCalculo.FRECUENCIAS -> topNums.drop(i).take(5)
                MetodoCalculo.NUMEROS_FRIOS -> topNums.reversed().drop(i).take(5)
                MetodoCalculo.LAPLACE -> (1..5).map { j -> ((i * 7 + j * 10) % 50) + 1 }
                MetodoCalculo.ALEATORIO_PURO -> (1..50).shuffled().take(5)
                else -> topNums.drop(i).take(5)
            }.sorted()
            
            val estrellas = listOf(
                topEstrellas[(i * 2) % topEstrellas.size],
                topEstrellas[(i * 2 + 1) % topEstrellas.size]
            ).distinct().sorted()
            
            Pair(nums, estrellas)
        }
    }
    
    /**
     * Ejecuta backtesting para El Gordo de la Primitiva.
     */
    fun ejecutarBacktestGordo(
        historico: List<ResultadoGordoPrimitiva>,
        diasAtras: Int = 10
    ): List<ResultadoBacktest> {
        if (historico.size <= diasAtras) return emptyList()
        
        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = MetodoCalculo.values()
        val totalCombs = metodos.size * diasAtras * 5
        var combinacionGlobal = 0
        
        // Precalcular frecuencias
        val frecuencias = contarFrecuencias(historico.flatMap { it.numeros }, 1..54)
        
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            
            var aciertos0 = 0
            var aciertos1 = 0
            var aciertos2 = 0
            var aciertos3 = 0
            var aciertos4 = 0
            var aciertos5 = 0
            var aciertosClave = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasAtras) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.isEmpty()) continue
                
                // Generar combinaciones rápido
                val topNums = frecuencias.entries.sortedByDescending { it.value }.map { it.key }
                val combinaciones = when (metodo) {
                    MetodoCalculo.FRECUENCIAS -> (0 until 5).map { j -> topNums.drop(j).take(5).sorted() }
                    MetodoCalculo.NUMEROS_FRIOS -> (0 until 5).map { j -> topNums.reversed().drop(j).take(5).sorted() }
                    MetodoCalculo.ALEATORIO_PURO -> (0 until 5).map { (1..54).shuffled().take(5).sorted() }
                    else -> (0 until 5).map { j -> topNums.drop(j).take(5).sorted() }
                }
                
                val sorteoReal = historico[i]
                val numerosReales = sorteoReal.numeros.toSet()
                
                for (numeros in combinaciones) {
                    combinacionGlobal++
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val aciertosEnCombinacion = numeros.toSet().intersect(numerosReales).size
                    
                    when (aciertosEnCombinacion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        5 -> aciertos5++
                    }
                    
                    if (aciertosEnCombinacion > mejorAcierto) mejorAcierto = aciertosEnCombinacion
                    totalAciertos += aciertosEnCombinacion
                }
            }
            
            val totalCombinaciones = diasAtras * 5
            val puntuacion = (aciertos1 * 1.0 + aciertos2 * 3.0 + aciertos3 * 10.0 + 
                             aciertos4 * 50.0 + aciertos5 * 300.0 + aciertosClave * 30.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasAtras,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertosClave = aciertosClave,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = "GORDO_PRIMITIVA"
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    /**
     * Ejecuta backtesting para Lotería Nacional / El Niño.
     * Compara terminaciones de 2 dígitos del primer premio.
     */
    fun ejecutarBacktestNacional(
        historico: List<ResultadoNacional>,
        diasAtras: Int = 10,
        tipoLoteria: String = "LOTERIA_NACIONAL"
    ): List<ResultadoBacktest> {
        val diasEfectivos = diasAtras.coerceAtMost(historico.size - 2).coerceAtLeast(1)
        if (historico.size < 3) return emptyList()
        
        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = MetodoCalculo.values()
        val totalCombs = metodos.size * diasEfectivos * 5
        var combinacionGlobal = 0
        
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            
            var aciertos0 = 0  // 0 cifras
            var aciertos1 = 0  // 1 cifra (última)
            var aciertos2 = 0  // 2 cifras (terminación)
            var aciertos3 = 0  // 3 cifras
            var aciertos4 = 0  // 4 cifras
            var aciertos5 = 0  // 5 cifras (número completo)
            var aciertosReintegro = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasEfectivos) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.size < 3) continue
                
                val sorteoReal = historico[i]
                val numeroReal = sorteoReal.primerPremio.filter { it.isDigit() }.takeLast(5).padStart(5, '0')
                val reintegrosReales = sorteoReal.reintegros.toSet()
                
                // Generar predicciones según MÉTODO
                val predicciones = generarPrediccionesNacional(historicoHastaMomento, metodo)
                
                for (prediccion in predicciones.take(5)) {
                    combinacionGlobal++
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val numeroPred = prediccion.toString().padStart(5, '0').takeLast(5)
                    
                    // Contar cifras coincidentes desde el final
                    val cifrasCoincidentes = contarCifrasCoincidentes(numeroPred, numeroReal)
                    
                    // Comprobar reintegro (última cifra de la predicción)
                    val ultimaCifraPred = numeroPred.last().digitToIntOrNull() ?: -1
                    if (ultimaCifraPred in reintegrosReales) aciertosReintegro++
                    
                    when (cifrasCoincidentes) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        else -> aciertos5++
                    }
                    
                    if (cifrasCoincidentes > mejorAcierto) mejorAcierto = cifrasCoincidentes
                    totalAciertos += cifrasCoincidentes
                }
            }
            
            val totalCombinaciones = (diasEfectivos * 5).coerceAtLeast(1)
            // Puntuación: 1 cifra=2, 2 cifras=15, 3 cifras=100, 4 cifras=500, 5 cifras=2000, reintegro=5
            val puntuacion = (aciertos1 * 2.0 + aciertos2 * 15.0 + aciertos3 * 100.0 + 
                             aciertos4 * 500.0 + aciertos5 * 2000.0 + aciertosReintegro * 5.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasEfectivos,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertosReintegro = aciertosReintegro,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = tipoLoteria
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    /**
     * Cuenta cuántas cifras coinciden desde el final entre dos números.
     * Ej: "12345" y "00045" -> 2 cifras (45)
     *     "12345" y "12345" -> 5 cifras
     *     "12345" y "99999" -> 0 cifras
     */
    private fun contarCifrasCoincidentes(pred: String, real: String): Int {
        val predPadded = pred.takeLast(5).padStart(5, '0')
        val realPadded = real.takeLast(5).padStart(5, '0')
        
        var cifras = 0
        for (i in 4 downTo 0) {
            if (predPadded[i] == realPadded[i]) {
                cifras++
            } else {
                break  // Si una cifra no coincide, dejar de contar
            }
        }
        return cifras
    }
    
    /**
     * Genera predicciones de terminaciones para Nacional/Navidad/Niño según método
     */
    private fun generarPrediccionesNacional(
        historico: List<ResultadoNacional>,
        metodo: MetodoCalculo
    ): List<Int> {
        val terminaciones = historico.mapNotNull { 
            it.primerPremio.filter { c -> c.isDigit() }.takeLast(2).toIntOrNull() 
        }
        if (terminaciones.isEmpty()) return (0..99).shuffled().take(5)
        
        return when (metodo) {
            MetodoCalculo.IA_GENETICA -> {
                // Combinar múltiples estrategias con pesos
                val porFrec = terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.take(4).map { it.key }
                val porRecientes = terminaciones.take(3)
                val noSalieron = (0..99).filter { it !in terminaciones }.shuffled().take(3)
                (porFrec + porRecientes + noSalieron).distinct().take(10)
            }
            MetodoCalculo.FRECUENCIAS -> {
                // Terminaciones más frecuentes
                terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(10).map { it.key }
            }
            MetodoCalculo.NUMEROS_CALIENTES -> {
                // Terminaciones más frecuentes en sorteos recientes
                val recientes = terminaciones.take(10)
                recientes.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .map { it.key }.take(10)
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                // Terminaciones con mayor gap (más tiempo sin salir)
                val ultimaAparicion = mutableMapOf<Int, Int>()
                terminaciones.forEachIndexed { idx, term -> ultimaAparicion[term] = idx }
                (0..99).filter { ultimaAparicion[it] != null }
                    .sortedBy { ultimaAparicion[it] ?: 0 }
                    .take(10)
            }
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> {
                // Balance entre frecuentes e infrecuentes
                val frecuencias = terminaciones.groupingBy { it }.eachCount()
                val frecuentes = frecuencias.entries.sortedByDescending { it.value }.take(5).map { it.key }
                val infrecuentes = (0..99).filter { it !in frecuencias.keys }.shuffled().take(5)
                frecuentes + infrecuentes
            }
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> {
                // Terminaciones consecutivas a las últimas
                val ultima = terminaciones.firstOrNull() ?: 50
                listOf(
                    (ultima + 1) % 100, (ultima - 1 + 100) % 100,
                    (ultima + 10) % 100, (ultima - 10 + 100) % 100,
                    (ultima + 11) % 100
                ) + terminaciones.take(5)
            }
            MetodoCalculo.DESVIACION_MEDIA -> {
                // Terminaciones cuya suma de dígitos sea similar a la media
                val sumaMedia = terminaciones.take(10).map { it / 10 + it % 10 }.average().toInt()
                (0..99).filter { (it / 10 + it % 10) in (sumaMedia - 2)..(sumaMedia + 2) }
                    .shuffled().take(10)
            }
            MetodoCalculo.LAPLACE -> {
                // Todos tienen la misma probabilidad, rotar por decenas
                val ultimaDecena = (terminaciones.firstOrNull() ?: 0) / 10
                val siguienteDecena = (ultimaDecena + 1) % 10
                (siguienteDecena * 10 until siguienteDecena * 10 + 10).toList()
            }
            MetodoCalculo.ALEATORIO_PURO -> {
                // Completamente aleatorio
                (0..99).shuffled().take(10)
            }
        }
    }
    
    /**
     * Ejecuta backtesting para Lotería de Navidad.
     * Compara terminaciones del Gordo usando diferentes estrategias por método.
     */
    fun ejecutarBacktestNavidad(
        historico: List<ResultadoNavidad>,
        diasAtras: Int = 10
    ): List<ResultadoBacktest> {
        val diasEfectivos = diasAtras.coerceAtMost(historico.size - 2).coerceAtLeast(1)
        if (historico.size < 3) return emptyList()
        
        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = MetodoCalculo.values()
        val totalCombs = metodos.size * diasEfectivos * 5
        var combinacionGlobal = 0
        
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            var aciertos0 = 0  // 0 cifras
            var aciertos1 = 0  // 1 cifra (última)
            var aciertos2 = 0  // 2 cifras (terminación)
            var aciertos3 = 0  // 3 cifras
            var aciertos4 = 0  // 4 cifras
            var aciertos5 = 0  // 5 cifras (número completo)
            var aciertosReintegro = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasEfectivos) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.size < 3) continue
                
                val sorteoReal = historico[i]
                val gordoReal = sorteoReal.gordo.filter { it.isDigit() }.takeLast(5).padStart(5, '0')
                val reintegrosReales = sorteoReal.reintegros.toSet()
                
                // Generar predicciones según MÉTODO
                val predicciones = generarPrediccionesNavidad(historicoHastaMomento, metodo)
                
                for (prediccion in predicciones.take(5)) {
                    combinacionGlobal++
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val numeroPred = prediccion.toString().padStart(5, '0').takeLast(5)
                    
                    // Contar cifras coincidentes desde el final
                    val cifrasCoincidentes = contarCifrasCoincidentes(numeroPred, gordoReal)
                    
                    // Comprobar reintegro (última cifra de la predicción)
                    val ultimaCifraPred = numeroPred.last().digitToIntOrNull() ?: -1
                    if (ultimaCifraPred in reintegrosReales) aciertosReintegro++
                    
                    when (cifrasCoincidentes) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        else -> aciertos5++
                    }
                    
                    if (cifrasCoincidentes > mejorAcierto) mejorAcierto = cifrasCoincidentes
                    totalAciertos += cifrasCoincidentes
                }
            }
            
            val totalCombinaciones = (diasEfectivos * 5).coerceAtLeast(1)
            // Puntuación: 1 cifra=2, 2 cifras=15, 3 cifras=100, 4 cifras=500, 5 cifras=2000, reintegro=5
            val puntuacion = (aciertos1 * 2.0 + aciertos2 * 15.0 + aciertos3 * 100.0 + 
                             aciertos4 * 500.0 + aciertos5 * 2000.0 + aciertosReintegro * 5.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasEfectivos,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertosReintegro = aciertosReintegro,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = "NAVIDAD"
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    /**
     * Genera predicciones de terminaciones para Navidad según método
     */
    private fun generarPrediccionesNavidad(
        historico: List<ResultadoNavidad>,
        metodo: MetodoCalculo
    ): List<Int> {
        val terminaciones = historico.mapNotNull { 
            it.gordo.filter { c -> c.isDigit() }.takeLast(2).toIntOrNull() 
        }
        if (terminaciones.isEmpty()) return (0..99).shuffled().take(5)
        
        return when (metodo) {
            MetodoCalculo.IA_GENETICA -> {
                // Combinar múltiples estrategias
                val porFrec = terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.take(4).map { it.key }
                val porRecientes = terminaciones.take(3)
                val porDecenas = terminaciones.map { it / 10 }.distinct().take(3).map { it * 10 + (0..9).random() }
                (porFrec + porRecientes + porDecenas).distinct().take(10)
            }
            MetodoCalculo.FRECUENCIAS -> {
                terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(10).map { it.key }
            }
            MetodoCalculo.NUMEROS_CALIENTES -> {
                val recientes = terminaciones.take(5)
                recientes.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .map { it.key }.take(10)
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                val ultimaAparicion = mutableMapOf<Int, Int>()
                terminaciones.forEachIndexed { idx, term -> ultimaAparicion[term] = idx }
                (0..99).filter { ultimaAparicion[it] != null }
                    .sortedBy { ultimaAparicion[it] ?: 0 }
                    .take(10)
            }
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> {
                val frecuencias = terminaciones.groupingBy { it }.eachCount()
                val frecuentes = frecuencias.entries.sortedByDescending { it.value }.take(5).map { it.key }
                val nunca = (0..99).filter { it !in frecuencias.keys }.shuffled().take(5)
                frecuentes + nunca
            }
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> {
                val ultima = terminaciones.firstOrNull() ?: 50
                listOf(
                    (ultima + 1) % 100, (ultima - 1 + 100) % 100,
                    (ultima + 10) % 100, (ultima - 10 + 100) % 100,
                    (ultima + 5) % 100, (ultima - 5 + 100) % 100
                ) + terminaciones.take(4)
            }
            MetodoCalculo.DESVIACION_MEDIA -> {
                val sumaMedia = terminaciones.take(10).map { it / 10 + it % 10 }.average().toInt()
                (0..99).filter { (it / 10 + it % 10) in (sumaMedia - 2)..(sumaMedia + 2) }
                    .shuffled().take(10)
            }
            MetodoCalculo.LAPLACE -> {
                // Decenas frecuentes
                val decenasFrecuentes = terminaciones.map { it / 10 }.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.take(3).map { it.key }
                decenasFrecuentes.flatMap { dec -> (dec * 10 until dec * 10 + 10).toList() }.shuffled().take(10)
            }
            MetodoCalculo.ALEATORIO_PURO -> {
                (0..99).shuffled().take(10)
            }
        }
    }
}
