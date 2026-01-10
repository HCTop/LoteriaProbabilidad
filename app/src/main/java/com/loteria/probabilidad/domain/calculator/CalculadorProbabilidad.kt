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

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.EUROMILLONES,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasEstrellas, 5, historico.size),
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
    fun ejecutarBacktestPrimitiva(
        historico: List<ResultadoPrimitiva>,
        diasAtras: Int = 10,
        tipoLoteria: String = "PRIMITIVA"
    ): List<ResultadoBacktest> {
        if (historico.size <= diasAtras) return emptyList()
        
        val resultados = mutableListOf<ResultadoBacktest>()
        
        // Probar cada método
        for (metodo in MetodoCalculo.values()) {
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
                // Histórico "del pasado" (excluyendo los sorteos futuros)
                val historicoHastaMomento = historico.drop(i + 1)
                
                if (historicoHastaMomento.isEmpty()) continue
                
                // Generar predicciones con ese histórico
                val analisis = analizarPrimitivaBonoloto(
                    historico = historicoHastaMomento,
                    tipoLoteria = if (tipoLoteria == "BONOLOTO") TipoLoteria.BONOLOTO else TipoLoteria.PRIMITIVA,
                    metodo = metodo,
                    numCombinaciones = 5
                )
                
                // Sorteo real que ocurrió
                val sorteoReal = historico[i]
                val numerosReales = sorteoReal.numeros.toSet()
                val complementarioReal = sorteoReal.complementario
                val reintegroReal = sorteoReal.reintegro
                
                // Contar aciertos de cada combinación sugerida
                for (combinacion in analisis.combinacionesSugeridas) {
                    val numerosPredichos = combinacion.numeros.toSet()
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
                    
                    // Verificar complementario (si acertó 5 y tiene el complementario)
                    if (aciertosEnCombinacion >= 5 && complementarioReal in numerosPredichos) {
                        aciertosComplementario++
                    }
                    
                    // El reintegro es un número extra (0-9), difícil de predecir
                    // Se cuenta si la combinación es buena (3+ aciertos)
                    if (aciertosEnCombinacion >= 3) {
                        // Simulamos que podría haber acertado el reintegro
                        // (en la realidad el usuario elige el reintegro)
                    }
                    
                    if (aciertosEnCombinacion > mejorAcierto) {
                        mejorAcierto = aciertosEnCombinacion
                    }
                    totalAciertos += aciertosEnCombinacion
                }
            }
            
            val totalCombinaciones = diasAtras * 5
            // Puntuación mejorada considerando 5 y 6 aciertos
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
        
        // Ordenar por puntuación (mejor primero)
        return resultados.sortedByDescending { it.puntuacionTotal }
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
        
        for (metodo in MetodoCalculo.values()) {
            var aciertos0 = 0
            var aciertos1 = 0
            var aciertos2 = 0
            var aciertos3 = 0
            var aciertos4 = 0
            var aciertos5 = 0
            var aciertosEstrella1 = 0  // Al menos 1 estrella
            var aciertosEstrella2 = 0  // Las 2 estrellas
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasAtras) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.isEmpty()) continue
                
                val analisis = analizarEuromillones(historicoHastaMomento, metodo, 5)
                val sorteoReal = historico[i]
                val numerosReales = sorteoReal.numeros.toSet()
                val estrellasReales = sorteoReal.estrellas.toSet()
                
                for (combinacion in analisis.combinacionesSugeridas) {
                    val numerosPredichos = combinacion.numeros.take(5).toSet()
                    val estrellasPredichas = if (combinacion.numeros.size > 5) {
                        combinacion.numeros.drop(5).toSet()
                    } else emptySet()
                    
                    val aciertosEnCombinacion = numerosPredichos.intersect(numerosReales).size
                    val aciertosEstrellas = estrellasPredichas.intersect(estrellasReales).size
                    
                    when (aciertosEnCombinacion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        5 -> aciertos5++
                    }
                    
                    // Contar aciertos de estrellas
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
    
    /**
     * Ejecuta backtesting para El Gordo de la Primitiva.
     */
    fun ejecutarBacktestGordo(
        historico: List<ResultadoGordoPrimitiva>,
        diasAtras: Int = 10
    ): List<ResultadoBacktest> {
        if (historico.size <= diasAtras) return emptyList()
        
        val resultados = mutableListOf<ResultadoBacktest>()
        
        for (metodo in MetodoCalculo.values()) {
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
                
                val analisis = analizarGordoPrimitiva(historicoHastaMomento, metodo, 5)
                val sorteoReal = historico[i]
                val numerosReales = sorteoReal.numeros.toSet()
                val claveReal = sorteoReal.numeroClave
                
                for (combinacion in analisis.combinacionesSugeridas) {
                    val numerosPredichos = combinacion.numeros.take(5).toSet()
                    val aciertosEnCombinacion = numerosPredichos.intersect(numerosReales).size
                    
                    when (aciertosEnCombinacion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        5 -> aciertos5++
                    }
                    
                    // Verificar si también acertó el número clave
                    // (el número clave suele ser el último en la combinación si se incluye)
                    if (combinacion.numeros.size > 5 && combinacion.numeros.last() == claveReal) {
                        aciertosClave++
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
        if (historico.size <= diasAtras) return emptyList()
        
        val resultados = mutableListOf<ResultadoBacktest>()
        
        for (metodo in MetodoCalculo.values()) {
            var aciertos0 = 0  // Sin coincidencia
            var aciertos1 = 0  // Última cifra
            var aciertos2 = 0  // 2 últimas cifras (terminación)
            var aciertos3 = 0  // 3 últimas cifras
            var aciertos4 = 0  // 4+ cifras o premio completo
            var aciertosReintegro = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasAtras) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.isEmpty()) continue
                
                val sorteoReal = historico[i]
                val numeroReal = sorteoReal.primerPremio.filter { it.isDigit() }.toIntOrNull() ?: 0
                val terminacionReal = numeroReal % 100  // 2 últimas cifras
                val reintegrosReales = sorteoReal.reintegros.toSet()
                
                // Generar predicciones con el histórico de ese momento
                val terminaciones = historicoHastaMomento.mapNotNull { 
                    it.primerPremio.filter { c -> c.isDigit() }.toIntOrNull()?.rem(100)
                }
                val frecuencias = terminaciones.groupingBy { it }.eachCount()
                val mejoresTerminaciones = frecuencias.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { it.key }
                
                for (terminacionPredicha in mejoresTerminaciones) {
                    // Contar cuántas cifras coinciden desde la derecha
                    val aciertosEnPrediccion = when {
                        terminacionPredicha == terminacionReal -> 2  // Terminación completa
                        terminacionPredicha % 10 == numeroReal % 10 -> 1  // Última cifra
                        else -> 0
                    }
                    
                    // Verificar reintegro (última cifra del número)
                    val ultimaCifraPredicha = terminacionPredicha % 10
                    if (ultimaCifraPredicha in reintegrosReales) {
                        aciertosReintegro++
                    }
                    
                    when (aciertosEnPrediccion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        else -> aciertos4++
                    }
                    
                    if (aciertosEnPrediccion > mejorAcierto) mejorAcierto = aciertosEnPrediccion
                    totalAciertos += aciertosEnPrediccion
                }
            }
            
            val totalCombinaciones = diasAtras * 5
            // Para Nacional, el scoring premia más las terminaciones acertadas
            val puntuacion = (aciertos1 * 2.0 + aciertos2 * 15.0 + aciertos3 * 50.0 + aciertos4 * 100.0 +
                             aciertosReintegro * 5.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasAtras,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
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
     * Ejecuta backtesting para Lotería de Navidad.
     * Compara terminaciones del Gordo.
     */
    fun ejecutarBacktestNavidad(
        historico: List<ResultadoNavidad>,
        diasAtras: Int = 10
    ): List<ResultadoBacktest> {
        if (historico.size <= diasAtras) return emptyList()
        
        val resultados = mutableListOf<ResultadoBacktest>()
        
        for (metodo in MetodoCalculo.values()) {
            var aciertos0 = 0  // Sin coincidencia
            var aciertos1 = 0  // Última cifra
            var aciertos2 = 0  // 2 últimas cifras (terminación)
            var aciertos3 = 0  // 3 últimas cifras
            var aciertos4 = 0  // 4+ cifras
            var aciertosReintegro = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasAtras) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.isEmpty()) continue
                
                val sorteoReal = historico[i]
                val gordoReal = sorteoReal.gordo.toIntOrNull() ?: 0
                val terminacionReal = gordoReal % 100  // 2 últimas cifras
                val reintegrosReales = sorteoReal.reintegros.toSet()
                
                // Generar predicciones con el histórico de ese momento
                val terminaciones = historicoHastaMomento.mapNotNull { it.gordo.takeLast(2).toIntOrNull() }
                val frecuencias = terminaciones.groupingBy { it }.eachCount()
                val mejoresTerminaciones = frecuencias.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { it.key }
                
                for (terminacionPredicha in mejoresTerminaciones) {
                    val aciertosEnPrediccion = when {
                        terminacionPredicha == terminacionReal -> 2  // Terminación completa
                        terminacionPredicha % 10 == gordoReal % 10 -> 1  // Última cifra
                        else -> 0
                    }
                    
                    // Verificar reintegro
                    val ultimaCifraPredicha = terminacionPredicha % 10
                    if (ultimaCifraPredicha in reintegrosReales) {
                        aciertosReintegro++
                    }
                    
                    when (aciertosEnPrediccion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        else -> aciertos4++
                    }
                    
                    if (aciertosEnPrediccion > mejorAcierto) mejorAcierto = aciertosEnPrediccion
                    totalAciertos += aciertosEnPrediccion
                }
            }
            
            val totalCombinaciones = diasAtras * 5
            val puntuacion = (aciertos1 * 2.0 + aciertos2 * 15.0 + aciertos3 * 50.0 + aciertos4 * 100.0 +
                             aciertosReintegro * 5.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasAtras,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertosReintegro = aciertosReintegro,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = "NAVIDAD"
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
}
