package com.loteria.probabilidad.domain.ml

import android.content.Context
import com.loteria.probabilidad.data.model.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Motor de IA con APRENDIZAJE PERSISTENTE.
 * 
 * La IA recuerda qué funciona y qué no entre sesiones.
 * Mejora con cada backtesting ejecutado.
 */
class MotorInteligencia(private val context: Context? = null) {
    
    private val memoria: MemoriaIA? = context?.let { MemoriaIA(it) }
    private var pesosCaracteristicas: MutableMap<String, Double> = mutableMapOf()
    private var config = ConfiguracionGenetica()
    private val contribuciones = mutableMapOf<String, Double>()
    private var tipoLoteriaActual: String = "PRIMITIVA"
    
    init { cargarMemoria("PRIMITIVA") }
    
    private fun cargarMemoria(tipoLoteria: String) {
        tipoLoteriaActual = tipoLoteria
        pesosCaracteristicas = (memoria?.obtenerPesosCaracteristicas(tipoLoteria) 
            ?: MemoriaIA.CARACTERISTICAS.associateWith { 1.0 / MemoriaIA.CARACTERISTICAS.size }).toMutableMap()
        config = memoria?.obtenerConfiguracionGenetica() ?: ConfiguracionGenetica()
    }
    
    fun obtenerResumenIA(tipoLoteria: String = "PRIMITIVA"): ResumenIA? = memoria?.obtenerResumenIA(tipoLoteria)

    fun getContribuciones(): Map<String, Double> = contribuciones.toMap()
    
    /**
     * Genera combinaciones usando IA con memoria.
     */
    fun generarCombinacionesInteligentes(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        numCombinaciones: Int,
        tipoLoteria: String = "PRIMITIVA"
    ): List<CombinacionSugerida> {
        // Cargar memoria específica para este tipo de lotería
        cargarMemoria(tipoLoteria)
        
        if (historico.size < 50) {
            return generarHibridoSimple(historico, maxNumero, cantidadNumeros, numCombinaciones)
        }
        
        // Semilla para variación en la selección dentro de cada pool
        val semilla = System.nanoTime()
        val rnd = Random(semilla)
        
        val car = extraerCaracteristicas(historico, maxNumero, tipoLoteria)
        val nombreNivel = memoria?.obtenerNombreNivel(tipoLoteria) ?: "🌱 Novato"
        val totalEntrenamientos = memoria?.obtenerTotalEntrenamientos(tipoLoteria) ?: 0
        
        // ═══════════════════════════════════════════════════════════════════
        // OBTENER PESOS APRENDIDOS
        // ═══════════════════════════════════════════════════════════════════
        val pesoGap = pesosCaracteristicas["gap"] ?: 0.20
        val pesoFrec = pesosCaracteristicas["frecuencia"] ?: 0.20
        val pesoTend = pesosCaracteristicas["tendencia"] ?: 0.20
        val pesoBal = pesosCaracteristicas["balance"] ?: 0.20
        val pesoPatrones = pesosCaracteristicas["patrones"] ?: 0.10
        
        // Normalizar pesos para que sumen 1.0
        val sumaPesos = pesoGap + pesoFrec + pesoTend + pesoBal + pesoPatrones
        val pGap = pesoGap / sumaPesos
        val pFrec = pesoFrec / sumaPesos
        val pTend = pesoTend / sumaPesos
        val pBal = pesoBal / sumaPesos
        val pPatrones = pesoPatrones / sumaPesos
        
        // ═══════════════════════════════════════════════════════════════════
        // CREAR POOLS DE NÚMEROS SEGÚN CADA CARACTERÍSTICA
        // El tamaño del pool determina cuán "selectivo" es el algoritmo
        // Pool pequeño (5-8) = muy selectivo, Pool grande (15-20) = más variado
        // ═══════════════════════════════════════════════════════════════════
        val tamPool = config.tamanoPool.coerceIn(5, 25)
        
        // Pool de números con mayor GAP (más tiempo sin salir)
        val poolGap = car.gaps.entries
            .sortedByDescending { it.value }
            .take(tamPool)
            .map { it.key }
        
        // Pool de números más FRECUENTES
        val poolFrec = car.frecuencias.entries
            .sortedByDescending { it.value }
            .take(tamPool)
            .map { it.key }
        
        // Pool de números con mejor TENDENCIA reciente
        val poolTend = car.tendencia.entries
            .sortedByDescending { it.value }
            .take(tamPool)
            .map { it.key }
        
        // Pool de números para BALANCE (mezcla de altos y bajos)
        val mitad = maxNumero / 2
        val poolBalBajos = (1..mitad).toList()
        val poolBalAltos = ((mitad + 1)..maxNumero).toList()
        
        // Pool de números con PATRONES exitosos (de la memoria)
        val poolPatrones = car.numerosExitosos.entries
            .sortedByDescending { it.value }
            .take(tamPool)
            .map { it.key }
        
        // ═══════════════════════════════════════════════════════════════════
        // CALCULAR CUÁNTOS NÚMEROS DE CADA POOL SEGÚN LOS PESOS
        // Ejemplo: Si Gap=30%, Frec=25%, Tend=20%, Bal=15%, Pat=10%
        // Para 6 números: Gap=2, Frec=1-2, Tend=1, Bal=1, Pat=0-1
        // ═══════════════════════════════════════════════════════════════════
        
        val combinacionesGeneradas = mutableListOf<List<Int>>()
        val combinacionesVistas = mutableSetOf<Set<Int>>()
        
        var intentos = 0
        while (combinacionesGeneradas.size < numCombinaciones && intentos < 200) {
            intentos++
            
            // Calcular cuántos de cada tipo (con pequeña variación)
            val variacion = rnd.nextDouble() * 0.1 - 0.05  // ±5% variación
            
            var numGap = ((pGap + variacion) * cantidadNumeros).roundToInt().coerceIn(0, cantidadNumeros)
            var numFrec = ((pFrec + variacion) * cantidadNumeros).roundToInt().coerceIn(0, cantidadNumeros - numGap)
            var numTend = ((pTend + variacion) * cantidadNumeros).roundToInt().coerceIn(0, cantidadNumeros - numGap - numFrec)
            var numBal = ((pBal + variacion) * cantidadNumeros).roundToInt().coerceIn(0, cantidadNumeros - numGap - numFrec - numTend)
            var numPat = cantidadNumeros - numGap - numFrec - numTend - numBal
            
            // Asegurar que sumamos exactamente cantidadNumeros
            val total = numGap + numFrec + numTend + numBal + numPat
            if (total < cantidadNumeros) {
                // Añadir al de mayor peso
                when {
                    pGap >= maxOf(pFrec, pTend, pBal, pPatrones) -> numGap += (cantidadNumeros - total)
                    pFrec >= maxOf(pTend, pBal, pPatrones) -> numFrec += (cantidadNumeros - total)
                    pTend >= maxOf(pBal, pPatrones) -> numTend += (cantidadNumeros - total)
                    pBal >= pPatrones -> numBal += (cantidadNumeros - total)
                    else -> numPat += (cantidadNumeros - total)
                }
            } else if (total > cantidadNumeros) {
                // Quitar del de menor peso
                val exceso = total - cantidadNumeros
                when {
                    numPat >= exceso -> numPat -= exceso
                    numBal >= exceso -> numBal -= exceso
                    numTend >= exceso -> numTend -= exceso
                    numFrec >= exceso -> numFrec -= exceso
                    else -> numGap -= exceso
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // SELECCIONAR NÚMEROS DE CADA POOL (shuffled para variación)
            // ═══════════════════════════════════════════════════════════════
            val numerosSeleccionados = mutableSetOf<Int>()
            
            // Del pool de GAP
            poolGap.shuffled(rnd)
                .filter { it !in numerosSeleccionados }
                .take(numGap)
                .forEach { numerosSeleccionados.add(it) }
            
            // Del pool de FRECUENCIA
            poolFrec.shuffled(rnd)
                .filter { it !in numerosSeleccionados }
                .take(numFrec)
                .forEach { numerosSeleccionados.add(it) }
            
            // Del pool de TENDENCIA
            poolTend.shuffled(rnd)
                .filter { it !in numerosSeleccionados }
                .take(numTend)
                .forEach { numerosSeleccionados.add(it) }
            
            // Del pool de BALANCE (alternando bajos y altos)
            val bajos = poolBalBajos.shuffled(rnd).filter { it !in numerosSeleccionados }
            val altos = poolBalAltos.shuffled(rnd).filter { it !in numerosSeleccionados }
            repeat(numBal) { i ->
                val num = if (i % 2 == 0 && bajos.size > i/2) bajos[i/2]
                         else if (altos.size > i/2) altos[i/2]
                         else (1..maxNumero).filter { it !in numerosSeleccionados }.randomOrNull()
                num?.let { numerosSeleccionados.add(it) }
            }
            
            // Del pool de PATRONES
            if (poolPatrones.isNotEmpty()) {
                poolPatrones.shuffled(rnd)
                    .filter { it !in numerosSeleccionados }
                    .take(numPat)
                    .forEach { numerosSeleccionados.add(it) }
            }
            
            // Completar si faltan (por solapamiento de pools)
            while (numerosSeleccionados.size < cantidadNumeros) {
                val disponibles = (1..maxNumero).filter { it !in numerosSeleccionados }
                if (disponibles.isNotEmpty()) {
                    numerosSeleccionados.add(disponibles.random(rnd))
                } else break
            }
            
            // Verificar diversidad con combinaciones anteriores
            val numSet = numerosSeleccionados.toSet()
            val esDiferente = combinacionesVistas.all { vista ->
                (numSet intersect vista).size <= 3
            }
            
            if (numSet.size == cantidadNumeros && (esDiferente || combinacionesVistas.isEmpty())) {
                combinacionesGeneradas.add(numerosSeleccionados.sorted())
                combinacionesVistas.add(numSet)
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // MEJORA: Si faltan combinaciones, usar algoritmo genético mejorado
        // en lugar de combinaciones aleatorias
        // ═══════════════════════════════════════════════════════════════════
        if (combinacionesGeneradas.size < numCombinaciones) {
            val faltantes = numCombinaciones - combinacionesGeneradas.size
            val combinacionesGeneticas = generarConAlgoritmoGeneticoMejorado(
                car = car,
                maxNumero = maxNumero,
                cantidadNumeros = cantidadNumeros,
                numCombinaciones = faltantes,
                combinacionesExistentes = combinacionesVistas,
                historicoSize = historico.size
            )

            combinacionesGeneticas.forEach { genes ->
                if (combinacionesVistas.add(genes.toSet())) {
                    combinacionesGeneradas.add(genes)
                }
            }
        }

        // Fallback final: si aún faltan, usar aleatorio
        while (combinacionesGeneradas.size < numCombinaciones) {
            val nueva = (1..maxNumero).shuffled(rnd).take(cantidadNumeros).sorted()
            if (combinacionesVistas.add(nueva.toSet())) {
                combinacionesGeneradas.add(nueva)
            }
        }

        // Info para mostrar
        val poblacionUsada = config.calcularPoblacionDinamica(historico.size)
        val infoComposicion = "G:${(pGap*100).toInt()}% F:${(pFrec*100).toInt()}% T:${(pTend*100).toInt()}%"
        val infoMejorado = "Pop:$poblacionUsada Gen:${config.generaciones}"

        return combinacionesGeneradas.mapIndexed { i, nums ->
            CombinacionSugerida(
                numeros = nums,
                probabilidadRelativa = ((pGap + pFrec + pTend) * 100).roundTo(1),
                explicacion = "🤖 $nombreNivel | $infoComposicion | $infoMejorado"
            )
        }
    }

    /**
     * Genera combinaciones usando el algoritmo genético MEJORADO.
     * Usa mutación adaptativa, crossover uniforme y población dinámica.
     */
    private fun generarConAlgoritmoGeneticoMejorado(
        car: Caracteristicas,
        maxNumero: Int,
        cantidadNumeros: Int,
        numCombinaciones: Int,
        combinacionesExistentes: Set<Set<Int>>,
        historicoSize: Int
    ): List<List<Int>> {
        // MEJORA 1: Población dinámica
        val poblacionDinamica = config.calcularPoblacionDinamica(historicoSize)

        // Crear población inicial
        var poblacion = crearPoblacionInicial(car, maxNumero, cantidadNumeros)
        while (poblacion.size < poblacionDinamica) {
            poblacion.add(Individuo((1..maxNumero).shuffled().take(cantidadNumeros)))
        }

        // Evaluar fitness inicial
        poblacion.forEach { evaluarFitness(it, car) }

        // MEJORA 2: Evolución con mutación adaptativa
        for (gen in 0 until config.generaciones) {
            poblacion = evolucionarGeneracion(
                pob = poblacion,
                car = car,
                maxNum = maxNumero,
                cant = cantidadNumeros,
                generacionActual = gen,
                poblacionObjetivo = poblacionDinamica
            )
        }

        // Seleccionar mejores combinaciones que no estén ya generadas
        return poblacion
            .sortedByDescending { it.fitness }
            .map { it.genes.sorted() }
            .filter { it.toSet() !in combinacionesExistentes }
            .distinctBy { it.toSet() }
            .take(numCombinaciones)
    }
    
    /**
     * CLAVE: Aprende del backtesting y guarda en memoria.
     */
    fun aprenderDeBacktest(
        resultados: List<ResultadoBacktest>,
        historico: List<ResultadoPrimitiva>,
        tipoLoteria: String,
        sorteosProbados: Int
    ) {
        if (memoria == null || resultados.isEmpty()) return
        
        val resultadoIA = resultados.find { it.metodo == MetodoCalculo.IA_GENETICA }
        val mejorResultado = resultados.maxByOrNull { it.puntuacionTotal }
        
        if (resultadoIA == null || mejorResultado == null) return
        
        // Si otro método fue mejor, aprender de él
        if (mejorResultado.metodo != MetodoCalculo.IA_GENETICA) {
            ajustarPesosSegunMetodo(mejorResultado.metodo)
        }
        
        // Actualizar memoria CON EL TIPO DE LOTERÍA ESPECÍFICO
        memoria.actualizarPesos(
            contribuciones.toMap(),
            resultadoIA.puntuacionTotal,
            memoria.obtenerMejorPuntuacion(tipoLoteria),
            tipoLoteria  // ← IMPORTANTE: cada lotería tiene su propia memoria
        )
        
        // Registrar números exitosos para esta lotería
        historico.take(sorteosProbados).forEach { sorteo ->
            memoria.registrarNumerosExitosos(sorteo.numeros, 1, tipoLoteria)
            for (i in sorteo.numeros.indices) {
                for (j in i + 1 until sorteo.numeros.size) {
                    memoria.registrarParExitoso(sorteo.numeros[i], sorteo.numeros[j], tipoLoteria)
                }
            }
        }
        
        // Recargar memoria para esta lotería
        cargarMemoria(tipoLoteria)
    }
    
    private fun ajustarPesosSegunMetodo(metodo: MetodoCalculo) {
        val clave = when (metodo) {
            MetodoCalculo.FRECUENCIAS -> "frecuencia"
            MetodoCalculo.NUMEROS_CALIENTES -> "tendencia"
            MetodoCalculo.NUMEROS_FRIOS -> "gap"
            MetodoCalculo.PROBABILIDAD_CONDICIONAL -> "patrones"
            MetodoCalculo.EQUILIBRIO_ESTADISTICO -> "balance"
            else -> return
        }
        contribuciones[clave] = (contribuciones[clave] ?: 0.0) + 0.5
    }
    
    // ==================== ALGORITMO GENÉTICO MEJORADO ====================

    data class Individuo(val genes: List<Int>, var fitness: Double = 0.0)

    /**
     * Ejecuta el algoritmo genético COMPLETO con todas las mejoras.
     *
     * @return Lista de las mejores combinaciones encontradas
     */
    fun ejecutarAlgoritmoGeneticoCompleto(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        numCombinaciones: Int,
        tipoLoteria: String = "PRIMITIVA"
    ): List<CombinacionSugerida> {
        cargarMemoria(tipoLoteria)

        if (historico.size < 30) {
            return generarHibridoSimple(historico, maxNumero, cantidadNumeros, numCombinaciones)
        }

        val car = extraerCaracteristicas(historico, maxNumero, tipoLoteria)

        // MEJORA 1: Población dinámica basada en histórico
        val poblacionDinamica = config.calcularPoblacionDinamica(historico.size)

        // Crear población inicial
        var poblacion = crearPoblacionInicial(car, maxNumero, cantidadNumeros)
        // Ajustar tamaño de población si es necesario
        while (poblacion.size < poblacionDinamica) {
            poblacion.add(Individuo((1..maxNumero).shuffled().take(cantidadNumeros)))
        }

        // Evaluar fitness inicial
        poblacion.forEach { evaluarFitness(it, car) }

        // MEJORA 2: Evolución con mutación adaptativa
        for (gen in 0 until config.generaciones) {
            poblacion = evolucionarGeneracion(
                pob = poblacion,
                car = car,
                maxNum = maxNumero,
                cant = cantidadNumeros,
                generacionActual = gen,
                poblacionObjetivo = poblacionDinamica
            )
        }

        // Seleccionar las mejores combinaciones únicas
        val mejores = poblacion
            .sortedByDescending { it.fitness }
            .distinctBy { it.genes.sorted().toSet() }
            .take(numCombinaciones)

        val nombreNivel = memoria?.obtenerNombreNivel(tipoLoteria) ?: "🌱 Novato"
        val totalEntrenamientos = memoria?.obtenerTotalEntrenamientos(tipoLoteria) ?: 0

        return mejores.mapIndexed { idx, ind ->
            CombinacionSugerida(
                numeros = ind.genes.sorted(),
                probabilidadRelativa = (ind.fitness * 100).coerceIn(0.0, 100.0),
                explicacion = "🧬 AG Mejorado $nombreNivel | Fit:${String.format("%.2f", ind.fitness)} | Gen:${config.generaciones} Pop:$poblacionDinamica"
            )
        }
    }
    
    /**
     * MEJORA 4: Perfil estadístico de una combinación.
     *
     * Describe las características distributivas de un conjunto de números.
     */
    data class PerfilCombinacion(
        val pares: Int,           // Cantidad de números pares
        val impares: Int,         // Cantidad de números impares
        val bajos: Int,           // Cantidad de números en mitad inferior
        val altos: Int,           // Cantidad de números en mitad superior
        val suma: Int,            // Suma total de los números
        val rango: Int,           // Diferencia entre máximo y mínimo
        val consecutivos: Int,    // Cantidad de pares de números consecutivos
        val decenas: Map<Int, Int> // Distribución por decenas (0-9, 10-19, etc.)
    ) {
        companion object {
            /**
             * Crea el perfil de una combinación de números.
             */
            fun desde(numeros: List<Int>, maxNumero: Int): PerfilCombinacion {
                val sorted = numeros.sorted()
                val mitad = maxNumero / 2

                return PerfilCombinacion(
                    pares = numeros.count { it % 2 == 0 },
                    impares = numeros.count { it % 2 != 0 },
                    bajos = numeros.count { it <= mitad },
                    altos = numeros.count { it > mitad },
                    suma = numeros.sum(),
                    rango = sorted.last() - sorted.first(),
                    consecutivos = sorted.zipWithNext().count { (a, b) -> b - a == 1 },
                    decenas = numeros.groupingBy { it / 10 }.eachCount()
                )
            }
        }
    }

    /**
     * MEJORA 4: Estadísticas de perfiles del histórico.
     *
     * Almacena los rangos típicos de cada característica distributiva.
     */
    data class EstadisticasPerfil(
        val sumaMedia: Double,
        val sumaDesviacion: Double,
        val sumaMin: Int,
        val sumaMax: Int,
        val rangoMedia: Double,
        val paresMedia: Double,          // Media de números pares por combinación
        val bajosMedia: Double,          // Media de números bajos por combinación
        val consecutivosMedia: Double,
        val perfilesFrecuentes: List<Pair<String, Int>>  // Patrones más comunes (ej: "3P-3I", "4B-2A")
    )

    /**
     * Características extraídas del histórico para el análisis.
     *
     * MEJORA 2: Análisis de series temporales (ciclos, EMA)
     * MEJORA 3: Análisis de correlaciones (matriz phi, tripletas, compañeros)
     * MEJORA 4: Análisis de distribución (perfil de combinaciones)
     * MEJORA 8: Análisis de rachas calientes/frías
     */
    data class Caracteristicas(
        val frecuencias: Map<Int, Int>,
        val gaps: Map<Int, Int>,
        val tendencia: Map<Int, Int>,
        val pares: Map<Pair<Int, Int>, Int>,
        val numerosExitosos: Map<Int, Double>,
        val paresExitosos: List<Pair<Pair<Int, Int>, Double>>,
        val maxNumero: Int,
        // MEJORA 2: Series temporales
        val ciclos: Map<Int, Double> = emptyMap(),
        val ema: Map<Int, Double> = emptyMap(),
        val proximosPorCiclo: List<Int> = emptyList(),
        val scoreCiclo: Map<Int, Double> = emptyMap(),
        // MEJORA 3: Correlaciones
        val correlaciones: Map<Pair<Int, Int>, Double> = emptyMap(),
        val tripletas: List<Triple<Int, Int, Int>> = emptyList(),
        val companeros: Map<Int, List<Int>> = emptyMap(),
        // MEJORA 4: Distribución
        val estadisticasPerfil: EstadisticasPerfil? = null,
        // MEJORA 8: Rachas calientes/frías
        val rachas: Map<Int, InfoRacha> = emptyMap()
    )
    
    /**
     * Crea la población inicial con diferentes estrategias.
     *
     * MEJORA 2: Añadido pool basado en ciclos (números "debidos").
     */
    private fun crearPoblacionInicial(car: Caracteristicas, maxNum: Int, cant: Int): MutableList<Individuo> {
        val pob = mutableListOf<Individuo>()
        val n = config.poblacion / 8  // Dividir entre 8 estrategias (MEJORA 3: añadido compañeros)

        // Obtener diferentes pools de números
        val topFrec = car.frecuencias.entries.sortedByDescending { it.value }.take(25).map { it.key }
        val topGap = car.gaps.entries.sortedByDescending { it.value }.take(25).map { it.key }
        val topTend = car.tendencia.entries.sortedByDescending { it.value }.take(25).map { it.key }
        val bottomFrec = car.frecuencias.entries.sortedBy { it.value }.take(15).map { it.key }

        // MEJORA 2: Pool de números "debidos" por ciclo
        val topCiclo = if (car.proximosPorCiclo.isNotEmpty()) {
            car.proximosPorCiclo.take(25)
        } else {
            car.scoreCiclo.entries.sortedByDescending { it.value }.take(25).map { it.key }
        }

        // MEJORA 2: Pool de números con mejor EMA (tendencia suavizada)
        val topEMA = car.ema.entries.sortedByDescending { it.value }.take(25).map { it.key }

        // Por frecuencia alta
        repeat(n) {
            pob.add(Individuo(topFrec.shuffled().take(cant)))
        }
        // Por gap (números atrasados)
        repeat(n) {
            pob.add(Individuo(topGap.shuffled().take(cant)))
        }
        // Por tendencia reciente
        repeat(n) {
            pob.add(Individuo(topTend.shuffled().take(cant)))
        }

        // MEJORA 2: Por ciclo (números "debidos")
        repeat(n) {
            if (topCiclo.size >= cant) {
                pob.add(Individuo(topCiclo.shuffled().take(cant)))
            } else {
                val mix = topCiclo + (1..maxNum).filter { it !in topCiclo }.shuffled().take(cant - topCiclo.size)
                pob.add(Individuo(mix.shuffled().take(cant)))
            }
        }

        // MEJORA 2: Mixto ciclo + frecuencia (combina "debidos" con frecuentes)
        repeat(n) {
            val deCiclo = topCiclo.shuffled().take(cant / 2)
            val deFrec = topFrec.filter { it !in deCiclo }.shuffled().take(cant - deCiclo.size)
            val mix = (deCiclo + deFrec).distinct()
            val completar = if (mix.size < cant) (1..maxNum).filter { it !in mix }.shuffled().take(cant - mix.size) else emptyList()
            pob.add(Individuo((mix + completar).take(cant)))
        }

        // Mixto: frecuentes + fríos (contrarios)
        repeat(n) {
            val mix = (topFrec.shuffled().take(cant - 2) + bottomFrec.shuffled().take(2)).distinct()
            val completar = if (mix.size < cant) (1..maxNum).filter { it !in mix }.shuffled().take(cant - mix.size) else emptyList()
            pob.add(Individuo((mix + completar).take(cant)))
        }

        // MEJORA 3: Basado en compañeros (números que salen juntos frecuentemente)
        repeat(n) {
            if (car.companeros.isNotEmpty()) {
                // Elegir un número semilla frecuente
                val semilla = topFrec.randomOrNull() ?: (1..maxNum).random()
                val misCompaneros = car.companeros[semilla] ?: emptyList()

                // Construir combinación: semilla + sus compañeros + frecuentes
                val nums = mutableSetOf(semilla)
                nums.addAll(misCompaneros.take(cant - 1))

                // Completar con frecuentes si faltan
                while (nums.size < cant) {
                    val candidato = topFrec.filter { it !in nums }.randomOrNull()
                        ?: (1..maxNum).filter { it !in nums }.random()
                    nums.add(candidato)
                }

                pob.add(Individuo(nums.toList()))
            } else {
                pob.add(Individuo(topFrec.shuffled().take(cant)))
            }
        }

        // Aleatorio puro
        while (pob.size < config.poblacion) {
            pob.add(Individuo((1..maxNum).shuffled().take(cant)))
        }

        return pob
    }
    
    /**
     * VERSIÓN VARIADA: Usa Random con semilla para generar resultados diferentes.
     *
     * MEJORA 2: Pool de ciclos.
     * MEJORA 3: Pool de compañeros.
     */
    private fun crearPoblacionInicialVariada(car: Caracteristicas, maxNum: Int, cant: Int, rnd: Random): MutableList<Individuo> {
        val pob = mutableListOf<Individuo>()
        val n = config.poblacion / 8  // Dividir entre 8 estrategias

        // Obtener diferentes pools de números con rotación basada en Random
        val rotacion = rnd.nextInt(10)
        val topFrec = car.frecuencias.entries.sortedByDescending { it.value }.drop(rotacion).take(25).map { it.key }
        val topGap = car.gaps.entries.sortedByDescending { it.value }.drop(rotacion).take(25).map { it.key }
        val topTend = car.tendencia.entries.sortedByDescending { it.value }.drop(rotacion).take(25).map { it.key }
        val bottomFrec = car.frecuencias.entries.sortedBy { it.value }.take(20).map { it.key }

        // MEJORA 2: Pool de números "debidos" por ciclo (con rotación)
        val topCiclo = if (car.proximosPorCiclo.isNotEmpty()) {
            car.proximosPorCiclo.drop(rotacion % car.proximosPorCiclo.size).take(25) +
            car.proximosPorCiclo.take(rotacion % car.proximosPorCiclo.size)
        } else {
            car.scoreCiclo.entries.sortedByDescending { it.value }.drop(rotacion).take(25).map { it.key }
        }

        // Por frecuencia alta (con variación)
        repeat(n) {
            pob.add(Individuo(topFrec.shuffled(rnd).take(cant)))
        }
        // Por gap (números atrasados)
        repeat(n) {
            pob.add(Individuo(topGap.shuffled(rnd).take(cant)))
        }
        // Por tendencia reciente
        repeat(n) {
            pob.add(Individuo(topTend.shuffled(rnd).take(cant)))
        }

        // MEJORA 2: Por ciclo (números "debidos")
        repeat(n) {
            if (topCiclo.size >= cant) {
                pob.add(Individuo(topCiclo.shuffled(rnd).take(cant)))
            } else {
                val mix = topCiclo + (1..maxNum).filter { it !in topCiclo }.shuffled(rnd).take(cant - topCiclo.size)
                pob.add(Individuo(mix.shuffled(rnd).take(cant)))
            }
        }

        // MEJORA 2: Mixto ciclo + frecuencia
        repeat(n) {
            val deCiclo = topCiclo.shuffled(rnd).take(cant / 2)
            val deFrec = topFrec.filter { it !in deCiclo }.shuffled(rnd).take(cant - deCiclo.size)
            val mix = (deCiclo + deFrec).distinct()
            val completar = if (mix.size < cant) (1..maxNum).filter { it !in mix }.shuffled(rnd).take(cant - mix.size) else emptyList()
            pob.add(Individuo((mix + completar).take(cant)))
        }

        // Mixto: frecuentes + fríos (contrarios)
        repeat(n) {
            val mix = (topFrec.shuffled(rnd).take(cant - 2) + bottomFrec.shuffled(rnd).take(2)).distinct()
            val completar = if (mix.size < cant) (1..maxNum).filter { it !in mix }.shuffled(rnd).take(cant - mix.size) else emptyList()
            pob.add(Individuo((mix + completar).take(cant)))
        }

        // MEJORA 3: Basado en compañeros
        repeat(n) {
            if (car.companeros.isNotEmpty()) {
                val semilla = topFrec.randomOrNull() ?: (1..maxNum).random(rnd)
                val misCompaneros = car.companeros[semilla] ?: emptyList()

                val nums = mutableSetOf(semilla)
                nums.addAll(misCompaneros.take(cant - 1))

                while (nums.size < cant) {
                    val candidato = topFrec.filter { it !in nums }.randomOrNull()
                        ?: (1..maxNum).filter { it !in nums }.random(rnd)
                    nums.add(candidato)
                }

                pob.add(Individuo(nums.toList()))
            } else {
                pob.add(Individuo(topFrec.shuffled(rnd).take(cant)))
            }
        }

        // Aleatorio puro
        while (pob.size < config.poblacion) {
            pob.add(Individuo((1..maxNum).shuffled(rnd).take(cant)))
        }

        return pob
    }

    /**
     * Evoluciona una generación usando algoritmo genético MEJORADO.
     *
     * Mejoras:
     * - Mutación adaptativa: decrece con las generaciones
     * - Crossover uniforme: mejor mezcla de genes
     * - Selección por torneo mejorada
     */
    private fun evolucionarGeneracion(
        pob: MutableList<Individuo>,
        car: Caracteristicas,
        maxNum: Int,
        cant: Int,
        generacionActual: Int = 0,
        poblacionObjetivo: Int = config.poblacion
    ): MutableList<Individuo> {
        val nueva = mutableListOf<Individuo>()
        val elite = (poblacionObjetivo * config.elitismo).toInt()
        nueva.addAll(pob.sortedByDescending { it.fitness }.take(elite))

        // Mutación adaptativa: alta al inicio (exploración), baja al final (explotación)
        val tasaMutacionActual = config.calcularTasaMutacionAdaptativa(generacionActual)

        while (nueva.size < poblacionObjetivo) {
            // Selección por torneo mejorada
            val p1 = seleccionTorneo(pob, config.torneoSize)
            val p2 = seleccionTorneo(pob, config.torneoSize)

            var hijo = if (Random.nextDouble() < config.tasaCruce) {
                if (config.usarCrossoverUniforme) {
                    // Crossover uniforme: cada gen se selecciona aleatoriamente de un padre
                    crossoverUniforme(p1, p2, maxNum, cant)
                } else {
                    // Crossover clásico: combinar genes de ambos padres
                    val genes = (p1.genes + p2.genes).distinct().shuffled().take(cant)
                    val completar = if (genes.size < cant) (1..maxNum).filter { it !in genes }.shuffled().take(cant - genes.size) else emptyList()
                    Individuo(genes + completar)
                }
            } else {
                Individuo(p1.genes.toList())
            }

            // Mutación con tasa adaptativa
            if (Random.nextDouble() < tasaMutacionActual) {
                hijo = mutarIndividuo(hijo, maxNum, tasaMutacionActual)
            }

            evaluarFitness(hijo, car)
            nueva.add(hijo)
        }
        return nueva
    }

    /**
     * Selección por torneo: selecciona el mejor de N individuos aleatorios.
     */
    private fun seleccionTorneo(pob: List<Individuo>, torneoSize: Int): Individuo {
        return pob.shuffled().take(torneoSize).maxByOrNull { it.fitness }!!
    }

    /**
     * Crossover uniforme: cada posición se hereda aleatoriamente de uno de los padres.
     * Produce mayor diversidad que el crossover de un punto.
     */
    private fun crossoverUniforme(p1: Individuo, p2: Individuo, maxNum: Int, cant: Int): Individuo {
        val genesHijo = mutableSetOf<Int>()
        val todosGenes = (p1.genes + p2.genes).distinct()

        // Para cada posición, seleccionar aleatoriamente de un padre
        for (i in 0 until cant) {
            val genP1 = p1.genes.getOrNull(i)
            val genP2 = p2.genes.getOrNull(i)

            val gen = when {
                genP1 != null && genP2 != null -> if (Random.nextBoolean()) genP1 else genP2
                genP1 != null -> genP1
                genP2 != null -> genP2
                else -> todosGenes.filter { it !in genesHijo }.randomOrNull() ?: (1..maxNum).filter { it !in genesHijo }.random()
            }

            if (gen !in genesHijo) {
                genesHijo.add(gen)
            }
        }

        // Completar si faltan genes (por duplicados)
        while (genesHijo.size < cant) {
            val disponibles = (1..maxNum).filter { it !in genesHijo }
            if (disponibles.isNotEmpty()) {
                genesHijo.add(disponibles.random())
            } else break
        }

        return Individuo(genesHijo.toList())
    }

    /**
     * Mutación mejorada: puede mutar múltiples genes según la tasa.
     */
    private fun mutarIndividuo(individuo: Individuo, maxNum: Int, tasaMutacion: Double): Individuo {
        val genes = individuo.genes.toMutableList()

        // Número de mutaciones basado en la tasa (mínimo 1 si se activa mutación)
        val numMutaciones = when {
            tasaMutacion > 0.15 -> if (Random.nextDouble() < 0.3) 2 else 1  // Alta tasa: a veces 2 mutaciones
            else -> 1  // Baja tasa: solo 1 mutación
        }

        repeat(numMutaciones) {
            val idx = Random.nextInt(genes.size)
            val disponibles = (1..maxNum).filter { it !in genes }
            if (disponibles.isNotEmpty()) {
                genes[idx] = disponibles.random()
            }
        }

        return Individuo(genes)
    }
    
    /**
     * VERSIÓN VARIADA MEJORADA: Evolución con aleatoriedad controlada y mutación adaptativa.
     *
     * Usa semilla Random para reproducibilidad y variación controlada.
     * Incluye las mismas mejoras que la versión estándar.
     */
    private fun evolucionarGeneracionVariada(
        pob: MutableList<Individuo>,
        car: Caracteristicas,
        maxNum: Int,
        cant: Int,
        rnd: Random,
        generacionActual: Int = 0,
        poblacionObjetivo: Int = config.poblacion
    ): MutableList<Individuo> {
        val nueva = mutableListOf<Individuo>()
        // Elitismo ligeramente mayor para variación pero manteniendo buenos individuos
        val elite = (poblacionObjetivo * 0.12).toInt()
        nueva.addAll(pob.shuffled(rnd).sortedByDescending { it.fitness }.take(elite))

        // Mutación adaptativa: empieza alta y decrece
        val tasaMutacionBase = config.calcularTasaMutacionAdaptativa(generacionActual)
        // Para la versión variada, añadimos un poco más de exploración
        val tasaMutacionVariada = (tasaMutacionBase * 1.2).coerceAtMost(0.30)
        val tasaCruceVariada = 0.80  // Ligeramente más alta para variación

        while (nueva.size < poblacionObjetivo) {
            // Selección por torneo con tamaño configurable
            val p1 = seleccionTorneoVariada(pob, config.torneoSize, rnd)
            val p2 = seleccionTorneoVariada(pob, config.torneoSize, rnd)

            var hijo = if (rnd.nextDouble() < tasaCruceVariada) {
                if (config.usarCrossoverUniforme) {
                    crossoverUniformeVariada(p1, p2, maxNum, cant, rnd)
                } else {
                    val genes = (p1.genes + p2.genes).distinct().shuffled(rnd).take(cant)
                    val completar = if (genes.size < cant) (1..maxNum).filter { it !in genes }.shuffled(rnd).take(cant - genes.size) else emptyList()
                    Individuo(genes + completar)
                }
            } else {
                Individuo(if (rnd.nextBoolean()) p1.genes.toList() else p2.genes.toList())
            }

            // Mutación adaptativa variada
            if (rnd.nextDouble() < tasaMutacionVariada) {
                hijo = mutarIndividuoVariado(hijo, maxNum, tasaMutacionVariada, rnd)
            }

            evaluarFitness(hijo, car)
            nueva.add(hijo)
        }
        return nueva
    }

    /**
     * Selección por torneo con Random controlado.
     */
    private fun seleccionTorneoVariada(pob: List<Individuo>, torneoSize: Int, rnd: Random): Individuo {
        return pob.shuffled(rnd).take(torneoSize).maxByOrNull { it.fitness }!!
    }

    /**
     * Crossover uniforme con Random controlado para reproducibilidad.
     */
    private fun crossoverUniformeVariada(p1: Individuo, p2: Individuo, maxNum: Int, cant: Int, rnd: Random): Individuo {
        val genesHijo = mutableSetOf<Int>()
        val todosGenes = (p1.genes + p2.genes).distinct()

        for (i in 0 until cant) {
            val genP1 = p1.genes.getOrNull(i)
            val genP2 = p2.genes.getOrNull(i)

            val gen = when {
                genP1 != null && genP2 != null -> if (rnd.nextBoolean()) genP1 else genP2
                genP1 != null -> genP1
                genP2 != null -> genP2
                else -> todosGenes.filter { it !in genesHijo }.randomOrNull() ?: (1..maxNum).filter { it !in genesHijo }.random(rnd)
            }

            if (gen !in genesHijo) {
                genesHijo.add(gen)
            }
        }

        while (genesHijo.size < cant) {
            val disponibles = (1..maxNum).filter { it !in genesHijo }
            if (disponibles.isNotEmpty()) {
                genesHijo.add(disponibles.random(rnd))
            } else break
        }

        return Individuo(genesHijo.toList())
    }

    /**
     * Mutación con Random controlado y número adaptativo de mutaciones.
     */
    private fun mutarIndividuoVariado(individuo: Individuo, maxNum: Int, tasaMutacion: Double, rnd: Random): Individuo {
        val genes = individuo.genes.toMutableList()

        // Más mutaciones cuando la tasa es alta (exploración)
        val numMutaciones = when {
            tasaMutacion > 0.20 -> if (rnd.nextDouble() < 0.4) 2 else 1
            tasaMutacion > 0.10 -> if (rnd.nextDouble() < 0.2) 2 else 1
            else -> 1
        }

        repeat(numMutaciones) {
            val idx = rnd.nextInt(genes.size)
            val disponibles = (1..maxNum).filter { it !in genes }
            if (disponibles.isNotEmpty()) {
                genes[idx] = disponibles.random(rnd)
            }
        }

        return Individuo(genes)
    }
    
    /**
     * Evalúa el fitness de un individuo basándose en múltiples características.
     *
     * MEJORA 2: Scoring de ciclos y EMA para series temporales.
     * MEJORA 3: Scoring de correlaciones y compañeros.
     * MEJORA 4: Scoring de distribución (perfil de combinación).
     * MEJORA 8: Scoring de rachas calientes/frías.
     */
    private fun evaluarFitness(ind: Individuo, car: Caracteristicas) {
        val nums = ind.genes.sorted()

        // MEJORA 4: Penalizar fuertemente combinaciones con perfil inválido
        if (!esPerfilValido(nums, car.estadisticasPerfil, car.maxNumero)) {
            ind.fitness = 0.01  // Fitness muy bajo pero no cero
            return
        }

        // Scores existentes
        val scoreFrec = calcScore(nums, car.frecuencias)
        val scoreGap = calcScoreGap(nums, car.gaps)
        val scoreTend = calcScore(nums, car.tendencia)
        val scoreMem = calcScoreMem(nums, car.numerosExitosos)
        val scoreBal = calcScoreBalance(nums, car.maxNumero)

        // MEJORA 2: Scores de series temporales
        val scoreCiclo = calcScoreCicloFitness(nums, car.scoreCiclo)
        val scoreEMA = calcScoreEMA(nums, car.ema)

        // MEJORA 3: Scores de correlaciones
        val scoreCorr = calcScoreCorrelacion(nums, car.correlaciones)
        val scoreComp = calcScoreCompaneros(nums, car.companeros)

        // MEJORA 4: Score de distribución
        val scoreDist = calcScoreDistribucion(nums, car.estadisticasPerfil, car.maxNumero)

        // MEJORA 8: Score de rachas calientes/frías
        val scoreRachas = calcScoreRachas(nums, car.rachas)

        // Registrar contribuciones para aprendizaje
        contribuciones["frecuencia"] = (contribuciones["frecuencia"] ?: 0.0) + scoreFrec
        contribuciones["gap"] = (contribuciones["gap"] ?: 0.0) + scoreGap
        contribuciones["tendencia"] = (contribuciones["tendencia"] ?: 0.0) + scoreTend
        contribuciones["balance"] = (contribuciones["balance"] ?: 0.0) + scoreBal
        contribuciones["ciclos"] = (contribuciones["ciclos"] ?: 0.0) + scoreCiclo
        contribuciones["patrones"] = (contribuciones["patrones"] ?: 0.0) + scoreCorr
        contribuciones["rachas"] = (contribuciones["rachas"] ?: 0.0) + scoreRachas

        // Calcular fitness ponderado
        val pesoCiclos = pesosCaracteristicas["ciclos"] ?: 0.06
        val pesoPatrones = pesosCaracteristicas["patrones"] ?: 0.06
        val pesoRachas = pesosCaracteristicas["rachas"] ?: 0.08

        ind.fitness = (
            scoreFrec * (pesosCaracteristicas["frecuencia"] ?: 0.11) +
            scoreGap * (pesosCaracteristicas["gap"] ?: 0.11) +
            scoreTend * (pesosCaracteristicas["tendencia"] ?: 0.11) +
            scoreMem * 0.10 +
            scoreBal * (pesosCaracteristicas["balance"] ?: 0.07) +
            scoreCiclo * pesoCiclos +       // MEJORA 2: ciclos
            scoreEMA * 0.05 +               // MEJORA 2: EMA
            scoreCorr * pesoPatrones +      // MEJORA 3: correlaciones
            scoreComp * 0.05 +              // MEJORA 3: compañeros
            scoreDist * 0.10 +              // MEJORA 4: distribución
            scoreRachas * pesoRachas        // MEJORA 8: rachas
        )
    }

    /**
     * MEJORA 2: Calcula el score de ciclos para una combinación.
     *
     * Premia combinaciones que incluyen números "debidos" (gap > ciclo promedio).
     */
    private fun calcScoreCicloFitness(nums: List<Int>, scoreCiclo: Map<Int, Double>): Double {
        if (scoreCiclo.isEmpty()) return 0.5

        // Promedio de scores de ciclo de los números seleccionados
        val avgScore = nums.mapNotNull { scoreCiclo[it] }.average()

        // Normalizar: score de 1.0 significa que están en su ciclo exacto
        // Premiamos ligeramente los que están "atrasados" (score > 1)
        return when {
            avgScore > 1.5 -> 0.9  // Muy atrasados - alta probabilidad
            avgScore > 1.0 -> 0.7  // Ligeramente atrasados - buena probabilidad
            avgScore > 0.7 -> 0.5  // Normal
            else -> 0.3  // Acaban de salir - baja probabilidad
        }
    }

    /**
     * MEJORA 2: Calcula el score EMA para una combinación.
     *
     * Premia combinaciones con buena tendencia reciente suavizada.
     */
    private fun calcScoreEMA(nums: List<Int>, ema: Map<Int, Double>): Double {
        if (ema.isEmpty()) return 0.5

        val max = ema.values.maxOrNull() ?: 1.0
        if (max == 0.0) return 0.5

        // Promedio normalizado de EMA de los números seleccionados
        return (nums.sumOf { (ema[it] ?: 0.0) / max } / nums.size).coerceIn(0.0, 1.0)
    }
    
    private fun calcScore(nums: List<Int>, map: Map<Int, Int>): Double {
        if (map.isEmpty()) return 0.5
        val max = map.values.maxOrNull()?.toDouble() ?: 1.0
        return (nums.sumOf { (map[it] ?: 0).toDouble() / max } / nums.size).coerceIn(0.0, 1.0)
    }
    
    private fun calcScoreGap(nums: List<Int>, gaps: Map<Int, Int>): Double {
        if (gaps.isEmpty()) return 0.5
        val g = nums.map { gaps[it] ?: 0 }.sorted()
        var s = 0.5
        if (g.take(2).any { it <= 3 }) s += 0.25
        if (g.takeLast(2).any { it >= 10 }) s += 0.25
        return s.coerceIn(0.0, 1.0)
    }
    
    private fun calcScoreMem(nums: List<Int>, mem: Map<Int, Double>): Double {
        if (mem.isEmpty() || mem.values.all { it == 0.0 }) return 0.0
        val max = mem.values.maxOrNull() ?: 1.0
        return (nums.sumOf { (mem[it] ?: 0.0) / max } / nums.size).coerceIn(0.0, 1.0)
    }
    
    private fun calcScoreBalance(nums: List<Int>, maxNum: Int): Double {
        val pares = nums.count { it % 2 == 0 }
        val ratio = pares.toDouble() / nums.size
        val s1 = (1.0 - abs(ratio - 0.5) * 2) * 0.5
        val bajos = nums.count { it <= maxNum / 2 }
        val s2 = (1.0 - abs(bajos.toDouble() / nums.size - 0.5) * 2) * 0.5
        return (s1 + s2).coerceIn(0.0, 1.0)
    }
    
    private fun extraerCaracteristicas(historico: List<ResultadoPrimitiva>, maxNum: Int, tipo: String): Caracteristicas {
        val frec = (1..maxNum).associateWith { n -> historico.count { n in it.numeros } }
        val gaps = (1..maxNum).associateWith { n -> historico.indexOfFirst { n in it.numeros }.let { if (it < 0) historico.size else it } }
        val tend = (1..maxNum).associateWith { n -> historico.take(30).count { n in it.numeros } }
        val pares = mutableMapOf<Pair<Int, Int>, Int>()
        historico.forEach { s ->
            val n = s.numeros.sorted()
            for (i in n.indices) for (j in i+1 until n.size) {
                val p = Pair(n[i], n[j])
                pares[p] = (pares[p] ?: 0) + 1
            }
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // MEJORA 2: ANÁLISIS DE SERIES TEMPORALES
        // ═══════════════════════════════════════════════════════════════════════════

        // Calcular ciclos (periodicidad promedio de cada número)
        val ciclos = calcularCiclosNumeros(historico, maxNum)

        // Calcular EMA (Media Móvil Exponencial) para tendencias suavizadas
        val ema = calcularEMA(historico, maxNum)

        // Calcular score de ciclo: qué tan "debido" está un número para salir
        val scoreCiclo = calcularScoreCiclo(ciclos, gaps, maxNum)

        // Obtener números que deberían salir pronto según su ciclo
        val proximosPorCiclo = scoreCiclo.entries
            .sortedByDescending { it.value }
            .take(15)
            .map { it.key }

        // ═══════════════════════════════════════════════════════════════════════════
        // MEJORA 3: ANÁLISIS DE CORRELACIONES
        // ═══════════════════════════════════════════════════════════════════════════

        // Calcular matriz de correlación (coeficiente phi)
        val correlaciones = calcularMatrizCorrelacion(historico, maxNum, frec)

        // Detectar tripletas frecuentes
        val tripletas = detectarTripletasFrecuentes(historico, maxNum)

        // Calcular números compañeros para cada número
        val companeros = calcularNumerosCompaneros(historico, maxNum)

        // ═══════════════════════════════════════════════════════════════════════════
        // MEJORA 4: ANÁLISIS DE DISTRIBUCIÓN
        // ═══════════════════════════════════════════════════════════════════════════

        // Calcular estadísticas de perfil del histórico
        val estadisticasPerfil = calcularEstadisticasPerfil(historico, maxNum)

        // ═══════════════════════════════════════════════════════════════════════════
        // MEJORA 8: ANÁLISIS DE RACHAS CALIENTES/FRÍAS
        // ═══════════════════════════════════════════════════════════════════════════

        // Detectar números en racha caliente o fría
        val rachas = detectarRachas(historico, maxNum)

        return Caracteristicas(
            frecuencias = frec,
            gaps = gaps,
            tendencia = tend,
            pares = pares,
            numerosExitosos = memoria?.obtenerScoreNumeros(tipo, maxNum) ?: emptyMap(),
            paresExitosos = memoria?.obtenerParesExitosos(tipo) ?: emptyList(),
            maxNumero = maxNum,
            ciclos = ciclos,
            ema = ema,
            proximosPorCiclo = proximosPorCiclo,
            scoreCiclo = scoreCiclo,
            correlaciones = correlaciones,
            tripletas = tripletas,
            companeros = companeros,
            estadisticasPerfil = estadisticasPerfil,
            rachas = rachas
        )
    }

    /**
     * MEJORA 2: Calcula el ciclo promedio de aparición de cada número.
     *
     * El ciclo es el intervalo promedio (en sorteos) entre apariciones consecutivas.
     * Un número con ciclo de 8 significa que aparece aproximadamente cada 8 sorteos.
     */
    private fun calcularCiclosNumeros(historico: List<ResultadoPrimitiva>, maxNum: Int): Map<Int, Double> {
        return (1..maxNum).associateWith { numero ->
            // Encontrar todas las posiciones donde aparece el número
            val apariciones = historico.mapIndexedNotNull { index, sorteo ->
                if (numero in sorteo.numeros) index else null
            }

            if (apariciones.size < 2) {
                // Si aparece 0 o 1 vez, usar el tamaño del histórico como ciclo estimado
                historico.size.toDouble()
            } else {
                // Calcular los gaps entre apariciones consecutivas
                val gapsEntreApariciones = apariciones.zipWithNext { a, b -> b - a }
                // El ciclo es el promedio de estos gaps
                gapsEntreApariciones.average()
            }
        }
    }

    /**
     * MEJORA 2: Calcula la Media Móvil Exponencial (EMA) de frecuencia.
     *
     * EMA da más peso a los sorteos recientes, permitiendo detectar
     * tendencias más rápidamente que la media simple.
     *
     * @param alpha Factor de suavizado (0.1-0.3). Mayor = más sensible a cambios recientes.
     */
    private fun calcularEMA(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int,
        alpha: Double = 0.2
    ): Map<Int, Double> {
        // Usar los últimos 50 sorteos para EMA (o todos si hay menos)
        val sorteosProcesar = historico.take(50)

        return (1..maxNum).associateWith { numero ->
            var ema = 0.0
            var inicializado = false

            // Procesar de más antiguo a más reciente (invertir lista)
            sorteosProcesar.reversed().forEach { sorteo ->
                val valor = if (numero in sorteo.numeros) 1.0 else 0.0
                if (!inicializado) {
                    ema = valor
                    inicializado = true
                } else {
                    // Fórmula EMA: EMA_t = α * valor_t + (1-α) * EMA_{t-1}
                    ema = alpha * valor + (1 - alpha) * ema
                }
            }

            ema
        }
    }

    /**
     * MEJORA 2: Calcula el score de ciclo para cada número.
     *
     * El score indica qué tan "debido" está un número para salir,
     * comparando su gap actual con su ciclo histórico.
     *
     * Score > 1.0 = el número ha superado su ciclo normal (debería salir pronto)
     * Score < 1.0 = el número aún no ha alcanzado su ciclo
     */
    private fun calcularScoreCiclo(
        ciclos: Map<Int, Double>,
        gaps: Map<Int, Int>,
        maxNum: Int
    ): Map<Int, Double> {
        return (1..maxNum).associateWith { numero ->
            val ciclo = ciclos[numero] ?: 10.0
            val gap = gaps[numero] ?: 0

            if (ciclo <= 0) {
                0.0
            } else {
                // Score = gap actual / ciclo promedio
                // Si score > 1, el número está "atrasado" respecto a su ciclo
                (gap.toDouble() / ciclo).coerceIn(0.0, 3.0)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 3: FUNCIONES DE ANÁLISIS DE CORRELACIONES
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * MEJORA 3: Calcula la matriz de correlación usando el coeficiente phi.
     *
     * El coeficiente phi mide la asociación entre dos variables binarias.
     * phi = (n11*n00 - n10*n01) / sqrt((n11+n10)*(n01+n00)*(n11+n01)*(n10+n00))
     *
     * donde:
     * - n11: ambos números aparecen
     * - n00: ninguno aparece
     * - n10: solo el primero aparece
     * - n01: solo el segundo aparece
     *
     * @return Mapa de pares con su coeficiente phi (solo pares con correlación significativa)
     */
    private fun calcularMatrizCorrelacion(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int,
        frecuencias: Map<Int, Int>
    ): Map<Pair<Int, Int>, Double> {
        val correlaciones = mutableMapOf<Pair<Int, Int>, Double>()
        val n = historico.size.toDouble()

        if (n < 30) return emptyMap()  // Necesitamos suficientes datos

        // Solo calcular para los 30 números más frecuentes (optimización)
        val numerosRelevantes = frecuencias.entries
            .sortedByDescending { it.value }
            .take(30)
            .map { it.key }

        for (i in numerosRelevantes.indices) {
            for (j in i + 1 until numerosRelevantes.size) {
                val num1 = numerosRelevantes[i]
                val num2 = numerosRelevantes[j]

                // Contar co-ocurrencias
                var n11 = 0  // Ambos aparecen
                var n10 = 0  // Solo num1
                var n01 = 0  // Solo num2
                var n00 = 0  // Ninguno

                historico.forEach { sorteo ->
                    val tiene1 = num1 in sorteo.numeros
                    val tiene2 = num2 in sorteo.numeros
                    when {
                        tiene1 && tiene2 -> n11++
                        tiene1 && !tiene2 -> n10++
                        !tiene1 && tiene2 -> n01++
                        else -> n00++
                    }
                }

                // Calcular coeficiente phi
                val numerador = (n11 * n00 - n10 * n01).toDouble()
                val denominador = sqrt(
                    ((n11 + n10) * (n01 + n00) * (n11 + n01) * (n10 + n00)).toDouble()
                )

                if (denominador > 0) {
                    val phi = numerador / denominador
                    // Solo guardar correlaciones significativas (|phi| > 0.1)
                    if (abs(phi) > 0.1) {
                        correlaciones[Pair(minOf(num1, num2), maxOf(num1, num2))] = phi
                    }
                }
            }
        }

        return correlaciones
    }

    /**
     * MEJORA 3: Detecta las tripletas de números más frecuentes.
     *
     * Analiza qué grupos de 3 números tienden a salir juntos.
     *
     * @return Lista de las 20 tripletas más frecuentes
     */
    private fun detectarTripletasFrecuentes(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int
    ): List<Triple<Int, Int, Int>> {
        if (historico.size < 50) return emptyList()

        val tripletasCount = mutableMapOf<Triple<Int, Int, Int>, Int>()

        // Contar todas las tripletas en el histórico
        historico.forEach { sorteo ->
            val nums = sorteo.numeros.sorted()
            // Generar todas las combinaciones de 3 números del sorteo
            for (i in nums.indices) {
                for (j in i + 1 until nums.size) {
                    for (k in j + 1 until nums.size) {
                        val tripleta = Triple(nums[i], nums[j], nums[k])
                        tripletasCount[tripleta] = (tripletasCount[tripleta] ?: 0) + 1
                    }
                }
            }
        }

        // Devolver las 20 más frecuentes (que aparezcan al menos 2 veces)
        return tripletasCount.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(20)
            .map { it.key }
    }

    /**
     * MEJORA 3: Calcula los "números compañeros" para cada número.
     *
     * Para cada número, encuentra los 5 números que más frecuentemente
     * aparecen junto a él en los sorteos.
     *
     * @return Mapa de número -> lista de sus 5 mejores compañeros
     */
    private fun calcularNumerosCompaneros(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int
    ): Map<Int, List<Int>> {
        if (historico.size < 30) return emptyMap()

        return (1..maxNum).associateWith { numero ->
            // Encontrar todos los sorteos donde aparece este número
            val sorteosConNumero = historico.filter { numero in it.numeros }

            if (sorteosConNumero.size < 5) {
                emptyList()
            } else {
                // Contar con qué otros números aparece
                val companeroCount = mutableMapOf<Int, Int>()

                sorteosConNumero.forEach { sorteo ->
                    sorteo.numeros.filter { it != numero }.forEach { companero ->
                        companeroCount[companero] = (companeroCount[companero] ?: 0) + 1
                    }
                }

                // Devolver los 5 más frecuentes
                companeroCount.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { it.key }
            }
        }
    }

    /**
     * MEJORA 3: Calcula el score de correlación para una combinación.
     *
     * Evalúa si los números de la combinación tienen buena correlación histórica.
     */
    private fun calcScoreCorrelacion(nums: List<Int>, correlaciones: Map<Pair<Int, Int>, Double>): Double {
        if (correlaciones.isEmpty()) return 0.5

        var totalCorr = 0.0
        var pares = 0

        // Evaluar correlación entre todos los pares de la combinación
        for (i in nums.indices) {
            for (j in i + 1 until nums.size) {
                val par = Pair(minOf(nums[i], nums[j]), maxOf(nums[i], nums[j]))
                correlaciones[par]?.let { phi ->
                    // Correlación positiva = buenos compañeros
                    totalCorr += phi
                    pares++
                }
            }
        }

        return if (pares > 0) {
            // Normalizar: transformar de [-1, 1] a [0, 1]
            ((totalCorr / pares + 1) / 2).coerceIn(0.0, 1.0)
        } else {
            0.5  // Sin datos de correlación
        }
    }

    /**
     * MEJORA 3: Calcula el score de compañeros para una combinación.
     *
     * Evalúa si los números incluyen compañeros frecuentes entre sí.
     */
    private fun calcScoreCompaneros(nums: List<Int>, companeros: Map<Int, List<Int>>): Double {
        if (companeros.isEmpty()) return 0.5

        var aciertos = 0
        var total = 0

        for (num in nums) {
            val misCompaneros = companeros[num] ?: continue
            total += misCompaneros.size

            // Contar cuántos de mis compañeros están en la combinación
            aciertos += nums.count { it != num && it in misCompaneros }
        }

        return if (total > 0) {
            (aciertos.toDouble() / total).coerceIn(0.0, 1.0)
        } else {
            0.5
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 4: FUNCIONES DE ANÁLISIS DE DISTRIBUCIÓN
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * MEJORA 4: Calcula las estadísticas de perfil del histórico.
     *
     * Analiza la distribución típica de combinaciones ganadoras:
     * - Suma media y desviación estándar
     * - Rango típico (max - min)
     * - Balance par/impar típico
     * - Balance bajos/altos típico
     */
    private fun calcularEstadisticasPerfil(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int
    ): EstadisticasPerfil? {
        if (historico.size < 30) return null

        val perfiles = historico.map { PerfilCombinacion.desde(it.numeros, maxNum) }

        // Calcular estadísticas de suma
        val sumas = perfiles.map { it.suma }
        val sumaMedia = sumas.average()
        val sumaDesviacion = sqrt(sumas.map { (it - sumaMedia).let { d -> d * d } }.average())

        // Calcular estadísticas de rango
        val rangos = perfiles.map { it.rango }
        val rangoMedia = rangos.average()

        // Calcular medias de balance
        val paresMedia = perfiles.map { it.pares }.average()
        val bajosMedia = perfiles.map { it.bajos }.average()
        val consecutivosMedia = perfiles.map { it.consecutivos }.average()

        // Calcular perfiles más frecuentes (patrones como "3P-3I", "4B-2A")
        val patronesPares = perfiles.groupingBy { "${it.pares}P-${it.impares}I" }.eachCount()
        val patronesBajos = perfiles.groupingBy { "${it.bajos}B-${it.altos}A" }.eachCount()

        val perfilesFrecuentes = (patronesPares.entries + patronesBajos.entries)
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }

        return EstadisticasPerfil(
            sumaMedia = sumaMedia,
            sumaDesviacion = sumaDesviacion,
            sumaMin = sumas.minOrNull() ?: 0,
            sumaMax = sumas.maxOrNull() ?: 0,
            rangoMedia = rangoMedia,
            paresMedia = paresMedia,
            bajosMedia = bajosMedia,
            consecutivosMedia = consecutivosMedia,
            perfilesFrecuentes = perfilesFrecuentes
        )
    }

    /**
     * MEJORA 4: Calcula el score de distribución para una combinación.
     *
     * Evalúa qué tan "típica" es la distribución de una combinación
     * comparada con el histórico de combinaciones ganadoras.
     *
     * @return Score de 0.0 a 1.0 (1.0 = distribución muy típica)
     */
    private fun calcScoreDistribucion(nums: List<Int>, stats: EstadisticasPerfil?, maxNum: Int): Double {
        if (stats == null) return 0.5

        val perfil = PerfilCombinacion.desde(nums, maxNum)
        var score = 0.0
        var factores = 0

        // Score de suma: qué tan cerca está de la media histórica
        val zScoreSuma = if (stats.sumaDesviacion > 0) {
            abs(perfil.suma - stats.sumaMedia) / stats.sumaDesviacion
        } else 0.0

        // Z-score < 1 = dentro de 1 desviación estándar (bueno)
        // Z-score > 2 = muy lejos de la media (malo)
        score += when {
            zScoreSuma < 0.5 -> 1.0    // Muy cerca de la media
            zScoreSuma < 1.0 -> 0.8    // Dentro de 1 std
            zScoreSuma < 1.5 -> 0.6    // Ligeramente fuera
            zScoreSuma < 2.0 -> 0.4    // Bastante fuera
            else -> 0.2                 // Muy atípico
        }
        factores++

        // Score de balance par/impar
        val difPares = abs(perfil.pares - stats.paresMedia)
        score += when {
            difPares < 0.5 -> 1.0
            difPares < 1.0 -> 0.8
            difPares < 1.5 -> 0.6
            difPares < 2.0 -> 0.4
            else -> 0.2
        }
        factores++

        // Score de balance bajos/altos
        val difBajos = abs(perfil.bajos - stats.bajosMedia)
        score += when {
            difBajos < 0.5 -> 1.0
            difBajos < 1.0 -> 0.8
            difBajos < 1.5 -> 0.6
            difBajos < 2.0 -> 0.4
            else -> 0.2
        }
        factores++

        // Score de consecutivos (penalizar muchos consecutivos)
        score += when {
            perfil.consecutivos == 0 -> 0.9           // Sin consecutivos: muy común
            perfil.consecutivos == 1 -> 0.7           // 1 par consecutivo: aceptable
            perfil.consecutivos == 2 -> 0.4           // 2 pares: poco común
            else -> 0.2                                // 3+ pares: muy raro
        }
        factores++

        // Score de rango (combinaciones con rango muy pequeño o muy grande son raras)
        val difRango = abs(perfil.rango - stats.rangoMedia)
        val rangoScore = 1.0 - (difRango / maxNum.toDouble()).coerceIn(0.0, 1.0)
        score += rangoScore
        factores++

        return (score / factores).coerceIn(0.0, 1.0)
    }

    /**
     * MEJORA 4: Verifica si una combinación tiene un perfil válido.
     *
     * Rechaza combinaciones con perfiles muy atípicos que casi nunca ocurren:
     * - Todos pares o todos impares
     * - Todos bajos o todos altos
     * - Suma muy extrema
     * - Demasiados consecutivos
     */
    private fun esPerfilValido(nums: List<Int>, stats: EstadisticasPerfil?, maxNum: Int): Boolean {
        if (stats == null) return true  // Sin datos, aceptar todo

        val perfil = PerfilCombinacion.desde(nums, maxNum)
        val cantNums = nums.size

        // Rechazar: todos pares o todos impares
        if (perfil.pares == 0 || perfil.impares == 0) return false

        // Rechazar: todos bajos o todos altos
        if (perfil.bajos == 0 || perfil.altos == 0) return false

        // Rechazar: suma muy extrema (fuera de 3 desviaciones estándar)
        if (stats.sumaDesviacion > 0) {
            val zScore = abs(perfil.suma - stats.sumaMedia) / stats.sumaDesviacion
            if (zScore > 3.0) return false
        }

        // Rechazar: demasiados consecutivos (más de la mitad de la combinación)
        if (perfil.consecutivos >= cantNums / 2) return false

        // Rechazar: rango muy pequeño (números muy agrupados)
        if (perfil.rango < cantNums * 2) return false

        return true
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 6: K-FOLD VALIDACIÓN CRUZADA TEMPORAL
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * MEJORA 6: Resultado de una validación K-Fold.
     */
    data class ResultadoKFold(
        val foldIndex: Int,
        val aciertosPromedio: Double,
        val mejorAcierto: Int,
        val puntuacionFold: Double,
        val tamanoEntrenamiento: Int,
        val tamanoValidacion: Int
    )

    /**
     * MEJORA 6: Resultado agregado de K-Fold completo.
     */
    data class ResultadoKFoldCompleto(
        val k: Int,
        val resultadosPorFold: List<ResultadoKFold>,
        val puntuacionMedia: Double,
        val puntuacionDesviacion: Double,
        val aciertosPromedioGlobal: Double,
        val mejorAciertoGlobal: Int,
        val esEstable: Boolean  // Baja varianza = modelo estable
    )

    /**
     * MEJORA 6: Divide el histórico en K folds temporales.
     *
     * A diferencia del K-Fold aleatorio, este mantiene el orden temporal:
     * - Fold 1: sorteos más antiguos
     * - Fold K: sorteos más recientes
     *
     * Esto es importante para series temporales donde el orden importa.
     *
     * @param historico Lista de sorteos ordenados del más reciente al más antiguo
     * @param k Número de folds (típicamente 5)
     * @return Lista de pares (entrenamiento, validación) para cada fold
     */
    fun dividirEnKFoldsTemporal(
        historico: List<ResultadoPrimitiva>,
        k: Int = 5
    ): List<Pair<List<ResultadoPrimitiva>, List<ResultadoPrimitiva>>> {
        if (historico.size < k * 10) {
            // No hay suficientes datos para K-Fold significativo
            return emptyList()
        }

        val foldSize = historico.size / k
        val folds = mutableListOf<Pair<List<ResultadoPrimitiva>, List<ResultadoPrimitiva>>>()

        for (i in 0 until k) {
            val validacionStart = i * foldSize
            val validacionEnd = if (i == k - 1) historico.size else (i + 1) * foldSize

            // Validación: el fold i
            val validacion = historico.subList(validacionStart, validacionEnd)

            // Entrenamiento: todos los demás folds
            val entrenamiento = historico.subList(0, validacionStart) +
                    historico.subList(validacionEnd, historico.size)

            folds.add(Pair(entrenamiento, validacion))
        }

        return folds
    }

    /**
     * MEJORA 6: Ejecuta validación K-Fold completa.
     *
     * Para cada fold:
     * 1. Extrae características del conjunto de entrenamiento
     * 2. Genera combinaciones usando el algoritmo genético
     * 3. Evalúa las combinaciones contra el conjunto de validación
     * 4. Registra aciertos
     *
     * @param historico Histórico completo de sorteos
     * @param maxNumero Número máximo (49 para Primitiva)
     * @param cantidadNumeros Números por combinación (6 para Primitiva)
     * @param k Número de folds
     * @param combinacionesPorFold Combinaciones a generar por fold
     * @param tipoLoteria Tipo de lotería para cargar memoria
     *
     * @return Resultado agregado de todos los folds
     */
    fun ejecutarKFoldValidation(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        k: Int = 5,
        combinacionesPorFold: Int = 10,
        tipoLoteria: String = "PRIMITIVA"
    ): ResultadoKFoldCompleto? {
        val folds = dividirEnKFoldsTemporal(historico, k)
        if (folds.isEmpty()) return null

        cargarMemoria(tipoLoteria)

        val resultadosFolds = mutableListOf<ResultadoKFold>()

        for ((foldIndex, fold) in folds.withIndex()) {
            val (entrenamiento, validacion) = fold

            // Extraer características SOLO del conjunto de entrenamiento
            val car = extraerCaracteristicas(entrenamiento, maxNumero, tipoLoteria)

            // Generar combinaciones usando el AG mejorado
            val poblacionDinamica = config.calcularPoblacionDinamica(entrenamiento.size)
            var poblacion = crearPoblacionInicial(car, maxNumero, cantidadNumeros)
            while (poblacion.size < poblacionDinamica) {
                poblacion.add(Individuo((1..maxNumero).shuffled().take(cantidadNumeros)))
            }
            poblacion.forEach { evaluarFitness(it, car) }

            // Evolucionar
            for (gen in 0 until config.generaciones) {
                poblacion = evolucionarGeneracion(
                    pob = poblacion,
                    car = car,
                    maxNum = maxNumero,
                    cant = cantidadNumeros,
                    generacionActual = gen,
                    poblacionObjetivo = poblacionDinamica
                )
            }

            // Seleccionar mejores combinaciones
            val mejoresCombinaciones = poblacion
                .sortedByDescending { it.fitness }
                .distinctBy { it.genes.sorted().toSet() }
                .take(combinacionesPorFold)
                .map { it.genes.sorted() }

            // Evaluar contra el conjunto de VALIDACIÓN
            var totalAciertos = 0
            var mejorAcierto = 0
            var puntuacionFold = 0.0

            for (sorteoReal in validacion) {
                val numerosReales = sorteoReal.numeros.toSet()

                for (combinacion in mejoresCombinaciones) {
                    val aciertos = combinacion.count { it in numerosReales }
                    totalAciertos += aciertos
                    if (aciertos > mejorAcierto) mejorAcierto = aciertos

                    // Puntuación ponderada por aciertos
                    puntuacionFold += when (aciertos) {
                        6 -> 1000.0
                        5 -> 100.0
                        4 -> 20.0
                        3 -> 5.0
                        2 -> 1.0
                        else -> 0.0
                    }
                }
            }

            val totalEvaluaciones = validacion.size * mejoresCombinaciones.size
            val aciertosPromedio = if (totalEvaluaciones > 0) {
                totalAciertos.toDouble() / totalEvaluaciones
            } else 0.0

            resultadosFolds.add(
                ResultadoKFold(
                    foldIndex = foldIndex,
                    aciertosPromedio = aciertosPromedio,
                    mejorAcierto = mejorAcierto,
                    puntuacionFold = puntuacionFold,
                    tamanoEntrenamiento = entrenamiento.size,
                    tamanoValidacion = validacion.size
                )
            )
        }

        // Calcular estadísticas agregadas
        val puntuaciones = resultadosFolds.map { it.puntuacionFold }
        val puntuacionMedia = puntuaciones.average()
        val puntuacionDesviacion = if (puntuaciones.size > 1) {
            sqrt(puntuaciones.map { (it - puntuacionMedia).let { d -> d * d } }.average())
        } else 0.0

        val aciertosPromedioGlobal = resultadosFolds.map { it.aciertosPromedio }.average()
        val mejorAciertoGlobal = resultadosFolds.maxOfOrNull { it.mejorAcierto } ?: 0

        // Un modelo es estable si la desviación es menor al 30% de la media
        val esEstable = puntuacionMedia > 0 && (puntuacionDesviacion / puntuacionMedia) < 0.30

        return ResultadoKFoldCompleto(
            k = k,
            resultadosPorFold = resultadosFolds,
            puntuacionMedia = puntuacionMedia,
            puntuacionDesviacion = puntuacionDesviacion,
            aciertosPromedioGlobal = aciertosPromedioGlobal,
            mejorAciertoGlobal = mejorAciertoGlobal,
            esEstable = esEstable
        )
    }

    /**
     * MEJORA 6: Versión simplificada de K-Fold para evaluación rápida.
     *
     * Usa solo 3 folds y menos combinaciones para una evaluación rápida.
     */
    fun ejecutarKFoldRapido(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        tipoLoteria: String = "PRIMITIVA"
    ): ResultadoKFoldCompleto? {
        return ejecutarKFoldValidation(
            historico = historico,
            maxNumero = maxNumero,
            cantidadNumeros = cantidadNumeros,
            k = 3,
            combinacionesPorFold = 5,
            tipoLoteria = tipoLoteria
        )
    }

    /**
     * MEJORA 6: Walk-Forward Validation (validación progresiva).
     *
     * Más realista que K-Fold para series temporales:
     * - Entrena con datos hasta el momento T
     * - Predice para T+1
     * - Avanza T y repite
     *
     * @param historico Histórico ordenado del más reciente al más antiguo
     * @param ventanaEntrenamiento Número de sorteos para entrenamiento
     * @param ventanaValidacion Número de sorteos para validación
     * @param pasos Cuántas veces avanzar la ventana
     */
    fun ejecutarWalkForwardValidation(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        ventanaEntrenamiento: Int = 100,
        ventanaValidacion: Int = 10,
        pasos: Int = 5,
        tipoLoteria: String = "PRIMITIVA"
    ): ResultadoKFoldCompleto? {
        if (historico.size < ventanaEntrenamiento + ventanaValidacion * pasos) {
            return null
        }

        cargarMemoria(tipoLoteria)
        val resultadosFolds = mutableListOf<ResultadoKFold>()

        for (paso in 0 until pasos) {
            val offset = paso * ventanaValidacion

            // Ventana de entrenamiento: datos "antiguos" desde el punto actual
            val entrenamientoStart = offset + ventanaValidacion
            val entrenamientoEnd = (entrenamientoStart + ventanaEntrenamiento).coerceAtMost(historico.size)
            val entrenamiento = historico.subList(entrenamientoStart, entrenamientoEnd)

            // Ventana de validación: datos "futuros" (más recientes que entrenamiento)
            val validacion = historico.subList(offset, offset + ventanaValidacion)

            if (entrenamiento.size < 50) continue

            // Extraer características del entrenamiento
            val car = extraerCaracteristicas(entrenamiento, maxNumero, tipoLoteria)

            // Generar y evaluar combinaciones
            val poblacion = crearPoblacionInicial(car, maxNumero, cantidadNumeros)
            poblacion.forEach { evaluarFitness(it, car) }

            for (gen in 0 until config.generaciones / 2) {  // Menos generaciones para velocidad
                evolucionarGeneracion(poblacion, car, maxNumero, cantidadNumeros, gen, config.poblacion)
            }

            val mejoresCombinaciones = poblacion
                .sortedByDescending { it.fitness }
                .take(5)
                .map { it.genes.sorted() }

            // Evaluar contra validación
            var totalAciertos = 0
            var mejorAcierto = 0
            var puntuacionFold = 0.0

            for (sorteoReal in validacion) {
                val numerosReales = sorteoReal.numeros.toSet()
                for (combinacion in mejoresCombinaciones) {
                    val aciertos = combinacion.count { it in numerosReales }
                    totalAciertos += aciertos
                    if (aciertos > mejorAcierto) mejorAcierto = aciertos
                    puntuacionFold += when (aciertos) {
                        6 -> 1000.0; 5 -> 100.0; 4 -> 20.0; 3 -> 5.0; 2 -> 1.0; else -> 0.0
                    }
                }
            }

            val totalEvaluaciones = validacion.size * mejoresCombinaciones.size
            resultadosFolds.add(
                ResultadoKFold(
                    foldIndex = paso,
                    aciertosPromedio = if (totalEvaluaciones > 0) totalAciertos.toDouble() / totalEvaluaciones else 0.0,
                    mejorAcierto = mejorAcierto,
                    puntuacionFold = puntuacionFold,
                    tamanoEntrenamiento = entrenamiento.size,
                    tamanoValidacion = validacion.size
                )
            )
        }

        if (resultadosFolds.isEmpty()) return null

        val puntuaciones = resultadosFolds.map { it.puntuacionFold }
        val puntuacionMedia = puntuaciones.average()
        val puntuacionDesviacion = if (puntuaciones.size > 1) {
            sqrt(puntuaciones.map { (it - puntuacionMedia).let { d -> d * d } }.average())
        } else 0.0

        return ResultadoKFoldCompleto(
            k = pasos,
            resultadosPorFold = resultadosFolds,
            puntuacionMedia = puntuacionMedia,
            puntuacionDesviacion = puntuacionDesviacion,
            aciertosPromedioGlobal = resultadosFolds.map { it.aciertosPromedio }.average(),
            mejorAciertoGlobal = resultadosFolds.maxOfOrNull { it.mejorAcierto } ?: 0,
            esEstable = puntuacionMedia > 0 && (puntuacionDesviacion / puntuacionMedia) < 0.30
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 7: SISTEMA DE PREDICCIÓN DE ALTA CONFIANZA
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Score detallado de un número para predicción.
     */
    data class ScoreNumero(
        val numero: Int,
        val scoreTotal: Double,           // Score combinado (0-100)
        val confianza: Double,            // Nivel de confianza (0-1)
        val senalesPositivas: Int,        // Cuántas señales apuntan a este número
        val senalesTotales: Int,          // Total de señales evaluadas
        val detalles: Map<String, Double> // Score por cada señal
    )

    /**
     * Resultado de predicción de alta confianza.
     */
    data class PrediccionAltaConfianza(
        val combinacionPrincipal: List<Int>,
        val confianzaGlobal: Double,
        val numerosConAltoConsenso: List<ScoreNumero>,
        val explicacion: String,
        val combinacionesAlternativas: List<List<Int>>
    )

    /**
     * MEJORA 7: Genera predicción de ALTA CONFIANZA para el próximo sorteo.
     *
     * Combina TODAS las señales disponibles:
     * 1. Desviación del equilibrio estadístico
     * 2. Ciclo vs Gap actual (¿está "debido"?)
     * 3. Tendencia EMA
     * 4. Frecuencia histórica
     * 5. Tendencia reciente
     * 6. Compañeros activos
     * 7. Memoria de éxitos
     * 8. Próximo por ciclo
     *
     * Solo sugiere números cuando hay ALTO CONSENSO entre señales.
     */
    fun generarPrediccionAltaConfianza(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        tipoLoteria: String = "PRIMITIVA"
    ): PrediccionAltaConfianza {
        cargarMemoria(tipoLoteria)

        val car = extraerCaracteristicas(historico, maxNumero, tipoLoteria)
        val scoresNumeros = mutableListOf<ScoreNumero>()

        // Frecuencia esperada en equilibrio perfecto
        val frecuenciaEsperada = (historico.size * cantidadNumeros).toDouble() / maxNumero
        val frecMax = car.frecuencias.values.maxOrNull() ?: 1
        val tendMax = car.tendencia.values.maxOrNull() ?: 1
        val emaMax = car.ema.values.maxOrNull() ?: 1.0
        val exitosMax = car.numerosExitosos.values.maxOrNull() ?: 1.0

        for (num in 1..maxNumero) {
            val detalles = mutableMapOf<String, Double>()
            var senalesPositivas = 0
            val senalesTotales = 7  // Reducido a 7 señales coherentes

            val frecuenciaReal = car.frecuencias[num] ?: 0
            val gap = car.gaps[num] ?: 0
            val tendenciaReciente = car.tendencia[num] ?: 0

            // SEÑAL 1: CICLO - ¿Está "debido" según su ciclo normal?
            // Si el gap actual > ciclo promedio, está debido
            val cicloPromedio = car.ciclos[num] ?: 8.0
            val ratioGapCiclo = if (cicloPromedio > 0) gap / cicloPromedio else 1.0
            val scoreCiclo = (ratioGapCiclo * 40).coerceIn(0.0, 100.0)
            detalles["ciclo"] = scoreCiclo
            if (ratioGapCiclo > 0.8) senalesPositivas++  // Umbral más bajo

            // SEÑAL 2: TENDENCIA RECIENTE - ¿Está activo últimamente?
            val scoreTend = ((tendenciaReciente.toDouble() / tendMax.coerceAtLeast(1)) * 100)
            detalles["tendencia"] = scoreTend
            if (scoreTend > 40) senalesPositivas++  // Umbral más bajo

            // SEÑAL 3: EMA - Tendencia suavizada
            val scoreEMA = if (emaMax > 0) ((car.ema[num] ?: 0.0) / emaMax * 100) else 50.0
            detalles["ema"] = scoreEMA
            if (scoreEMA > 40) senalesPositivas++

            // SEÑAL 4: COMPAÑEROS ACTIVOS - ¿Sus pares frecuentes están activos?
            val companeros = car.companeros[num] ?: emptyList()
            val companerosActivos = companeros.count { comp ->
                (car.tendencia[comp] ?: 0) > 0
            }
            val scoreCompaneros = if (companeros.isNotEmpty()) {
                (companerosActivos.toDouble() / companeros.size * 100)
            } else 50.0
            detalles["companeros"] = scoreCompaneros
            if (companerosActivos >= 2) senalesPositivas++

            // SEÑAL 5: EN LISTA DE PRÓXIMOS POR CICLO
            val posicionCiclo = car.proximosPorCiclo.indexOf(num)
            val scoreProximoCiclo = if (posicionCiclo >= 0 && posicionCiclo < 15) {
                100.0 - (posicionCiclo * 5)
            } else 30.0
            detalles["proximo_ciclo"] = scoreProximoCiclo
            if (posicionCiclo in 0..9) senalesPositivas++

            // SEÑAL 6: RACHA - ¿Está caliente o muy frío?
            val racha = car.rachas[num]
            val scoreRacha = when (racha?.tipoRacha) {
                TipoRacha.MUY_CALIENTE -> 90.0
                TipoRacha.CALIENTE -> 80.0
                TipoRacha.MUY_FRIO -> 75.0  // Muy debido
                TipoRacha.FRIO -> 60.0
                else -> 50.0
            }
            detalles["racha"] = scoreRacha
            if (racha != null && racha.tipoRacha != TipoRacha.NORMAL) {
                senalesPositivas++
            }

            // SEÑAL 7: BALANCE - ¿Aporta diversidad a la combinación?
            val esPar = num % 2 == 0
            val esBajo = num <= maxNumero / 2
            val decena = num / 10
            // Bonus si está en una decena diferente a la más frecuente
            val decenaScore = 50.0 + (if (decena !in listOf(0, 1, 2, 3)) 20.0 else 0.0)
            detalles["balance"] = decenaScore
            if (decena > 2) senalesPositivas++

            // SCORE TOTAL PONDERADO (7 señales coherentes)
            val scoreTotal =
                scoreCiclo * 0.25 +         // Ciclo: muy importante
                scoreTend * 0.20 +          // Tendencia reciente
                scoreEMA * 0.15 +           // EMA suavizado
                scoreCompaneros * 0.10 +    // Compañeros activos
                scoreProximoCiclo * 0.10 +  // Próximo por ciclo
                scoreRacha * 0.10 +         // Racha caliente/fría
                decenaScore * 0.10          // Balance/diversidad

            val confianza = senalesPositivas.toDouble() / senalesTotales

            scoresNumeros.add(ScoreNumero(num, scoreTotal, confianza, senalesPositivas, senalesTotales, detalles))
        }

        val numerosOrdenados = scoresNumeros.sortedByDescending { it.scoreTotal }
        // Reducir umbral: 4 de 7 señales es suficiente
        val altaConfianza = numerosOrdenados.filter { it.senalesPositivas >= 4 }

        // Seleccionar combinación óptima con balance
        val combinacionPrincipal = seleccionarCombinacionOptima(numerosOrdenados, cantidadNumeros, maxNumero, car.estadisticasPerfil)
        val alternativas = generarAlternativasConfianza(numerosOrdenados, cantidadNumeros, combinacionPrincipal)

        val confianzaGlobal = if (combinacionPrincipal.isNotEmpty()) {
            combinacionPrincipal.mapNotNull { num -> scoresNumeros.find { it.numero == num }?.confianza }.average()
        } else 0.0

        val explicacion = generarExplicacionPrediccion(combinacionPrincipal, scoresNumeros, altaConfianza.size)

        return PrediccionAltaConfianza(combinacionPrincipal, confianzaGlobal, altaConfianza.take(15), explicacion, alternativas)
    }

    private fun seleccionarCombinacionOptima(
        numerosOrdenados: List<ScoreNumero>,
        cantidad: Int,
        maxNumero: Int,
        stats: EstadisticasPerfil?
    ): List<Int> {
        val seleccionados = mutableListOf<Int>()
        val mitad = maxNumero / 2
        val candidatos = numerosOrdenados.toMutableList()

        while (seleccionados.size < cantidad && candidatos.isNotEmpty()) {
            val mejor = candidatos.removeAt(0)
            val num = mejor.numero

            val paresActuales = seleccionados.count { it % 2 == 0 }
            val bajosActuales = seleccionados.count { it <= mitad }

            // Evitar desequilibrios extremos
            if (seleccionados.size >= cantidad - 1) {
                if (paresActuales == 0 && num % 2 != 0) continue
                if (paresActuales == seleccionados.size && num % 2 == 0) continue
                if (bajosActuales == 0 && num > mitad) continue
                if (bajosActuales == seleccionados.size && num <= mitad) continue
            }

            seleccionados.add(num)
        }

        while (seleccionados.size < cantidad) {
            val disponible = (1..maxNumero).filter { it !in seleccionados }.randomOrNull() ?: break
            seleccionados.add(disponible)
        }

        return seleccionados.sorted()
    }

    private fun generarAlternativasConfianza(
        numerosOrdenados: List<ScoreNumero>,
        cantidad: Int,
        principal: List<Int>
    ): List<List<Int>> {
        val alternativas = mutableListOf<List<Int>>()

        repeat(3) { idx ->
            val alternativa = mutableListOf<Int>()
            val offset = (idx + 1) * 2  // Empezar desde diferentes posiciones

            numerosOrdenados.drop(offset).forEach { score ->
                if (alternativa.size < cantidad && score.numero !in alternativa) {
                    alternativa.add(score.numero)
                }
            }

            if (alternativa.size == cantidad) {
                alternativas.add(alternativa.sorted())
            }
        }

        return alternativas.distinctBy { it.toSet() }.filter { it.toSet() != principal.toSet() }
    }

    private fun generarExplicacionPrediccion(
        combinacion: List<Int>,
        scores: List<ScoreNumero>,
        numAltaConfianza: Int
    ): String {
        val sb = StringBuilder()
        sb.append("🎯 PREDICCIÓN ALTA CONFIANZA\n\n")
        sb.append("📊 $numAltaConfianza números con alto consenso (≥4/7 señales)\n\n")
        sb.append("🔢 Números seleccionados:\n")

        for (num in combinacion) {
            val score = scores.find { it.numero == num }
            if (score != null) {
                val rachaIcon = when {
                    (score.detalles["racha"] ?: 50.0) > 80 -> "🔥"
                    (score.detalles["racha"] ?: 50.0) > 70 -> "♨️"
                    (score.detalles["racha"] ?: 50.0) < 55 -> "❄️"
                    else -> ""
                }
                sb.append("  • $num$rachaIcon: ${String.format("%.1f", score.scoreTotal)}pts ")
                sb.append("(${score.senalesPositivas}/7 señales)\n")
            }
        }

        val topEquilibrio = scores.sortedByDescending { it.detalles["equilibrio"] ?: 0.0 }.take(5)
        sb.append("\n⚖️ Más debidos por equilibrio: ${topEquilibrio.map { it.numero }.joinToString(", ")}")

        val topCiclo = scores.sortedByDescending { it.detalles["ciclo"] ?: 0.0 }.take(5)
        sb.append("\n⏰ Más debidos por ciclo: ${topCiclo.map { it.numero }.joinToString(", ")}")

        val topRacha = scores.sortedByDescending { it.detalles["racha"] ?: 0.0 }.take(5)
        sb.append("\n🔥 Más calientes: ${topRacha.map { it.numero }.joinToString(", ")}")

        return sb.toString()
    }

    // Versiones para otras loterías
    fun generarCombinacionesInteligenteEuro(historico: List<ResultadoEuromillones>, n: Int): List<CombinacionSugerida> {
        return generarCombinacionesInteligentes(historico.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }, 50, 5, n, "EURO")
    }
    
    fun generarCombinacionesInteligenteGordo(historico: List<ResultadoGordoPrimitiva>, n: Int): List<CombinacionSugerida> {
        return generarCombinacionesInteligentes(historico.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }, 54, 5, n, "GORDO")
    }
    
    private fun generarHibridoSimple(historico: List<ResultadoPrimitiva>, maxNum: Int, cant: Int, n: Int): List<CombinacionSugerida> {
        val frec = (1..maxNum).associateWith { num -> historico.count { num in it.numeros } }
        val top = frec.entries.sortedByDescending { it.value }.take(25).map { it.key }
        val bottom = frec.entries.sortedBy { it.value }.take(15).map { it.key }
        
        val combinaciones = mutableListOf<CombinacionSugerida>()
        val vistas = mutableSetOf<Set<Int>>()
        
        repeat(n * 3) { // Intentar más veces para conseguir diversidad
            if (combinaciones.size >= n) return@repeat
            
            // Mezclar estrategias
            val numeros = when (it % 3) {
                0 -> top.shuffled().take(cant)
                1 -> (top.shuffled().take(cant - 1) + bottom.shuffled().take(1)).shuffled()
                else -> (1..maxNum).shuffled().take(cant)
            }.sorted()
            
            val numSet = numeros.toSet()
            if (numSet !in vistas) {
                vistas.add(numSet)
                combinaciones.add(CombinacionSugerida(
                    numeros = numeros,
                    probabilidadRelativa = 50.0 - combinaciones.size,
                    explicacion = "🤖 IA (datos limitados) #${combinaciones.size + 1}"
                ))
            }
        }
        
        return combinaciones.take(n)
    }
    
    private fun Double.roundTo(d: Int): Double {
        var m = 1.0
        repeat(d) { m *= 10 }
        return (this * m).roundToInt() / m
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 8: DETECCIÓN DE RACHAS CALIENTES/FRÍAS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * MEJORA 8: Información de racha de un número.
     *
     * Detecta si un número está en racha positiva (caliente) o negativa (frío).
     */
    data class InfoRacha(
        val numero: Int,
        val tipoRacha: TipoRacha,
        val longitudRacha: Int,         // Cuántos sorteos lleva la racha
        val intensidad: Double,         // Qué tan fuerte es la racha (0-1)
        val aparicionesRecientes: Int,  // Apariciones en últimos N sorteos
        val esperadoReciente: Double,   // Apariciones esperadas en últimos N sorteos
        val desviacionReciente: Double  // (real - esperado) / esperado
    )

    enum class TipoRacha {
        MUY_CALIENTE,  // Aparece mucho más de lo esperado (>50% más)
        CALIENTE,      // Aparece más de lo esperado (>25% más)
        NORMAL,        // Aparece cerca de lo esperado (±25%)
        FRIO,          // Aparece menos de lo esperado (>25% menos)
        MUY_FRIO       // Aparece mucho menos de lo esperado (>50% menos)
    }

    /**
     * MEJORA 8: Detecta rachas calientes y frías para todos los números.
     *
     * Analiza los últimos N sorteos y compara la frecuencia real con la esperada.
     * Un número "caliente" está apareciendo más de lo esperado.
     * Un número "frío" está apareciendo menos de lo esperado.
     *
     * @param historico Lista de sorteos (más reciente primero)
     * @param maxNum Número máximo de la lotería
     * @param ventanaAnalisis Cuántos sorteos recientes analizar (default: 20)
     * @param cantidadNumeros Números por sorteo (para calcular esperado)
     */
    fun detectarRachas(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int,
        ventanaAnalisis: Int = 20,
        cantidadNumeros: Int = 6
    ): Map<Int, InfoRacha> {
        if (historico.size < ventanaAnalisis) {
            return (1..maxNum).associateWith { num ->
                InfoRacha(num, TipoRacha.NORMAL, 0, 0.5, 0, 0.0, 0.0)
            }
        }

        val sorteosRecientes = historico.take(ventanaAnalisis)

        // Frecuencia esperada por número en la ventana de análisis
        val frecuenciaEsperada = (ventanaAnalisis * cantidadNumeros).toDouble() / maxNum

        return (1..maxNum).associateWith { numero ->
            // Contar apariciones en la ventana
            val apariciones = sorteosRecientes.count { numero in it.numeros }

            // Calcular desviación porcentual
            val desviacion = if (frecuenciaEsperada > 0) {
                (apariciones - frecuenciaEsperada) / frecuenciaEsperada
            } else 0.0

            // Determinar tipo de racha
            val tipo = when {
                desviacion > 0.50 -> TipoRacha.MUY_CALIENTE
                desviacion > 0.25 -> TipoRacha.CALIENTE
                desviacion < -0.50 -> TipoRacha.MUY_FRIO
                desviacion < -0.25 -> TipoRacha.FRIO
                else -> TipoRacha.NORMAL
            }

            // Calcular longitud de racha (sorteos consecutivos en tendencia)
            val longitudRacha = calcularLongitudRacha(historico, numero, tipo)

            // Intensidad: qué tan fuerte es la desviación (normalizada)
            val intensidad = abs(desviacion).coerceIn(0.0, 1.0)

            InfoRacha(
                numero = numero,
                tipoRacha = tipo,
                longitudRacha = longitudRacha,
                intensidad = intensidad,
                aparicionesRecientes = apariciones,
                esperadoReciente = frecuenciaEsperada,
                desviacionReciente = desviacion
            )
        }
    }

    /**
     * Calcula cuántos sorteos consecutivos el número ha mantenido su tendencia.
     */
    private fun calcularLongitudRacha(
        historico: List<ResultadoPrimitiva>,
        numero: Int,
        tipoActual: TipoRacha
    ): Int {
        if (tipoActual == TipoRacha.NORMAL) return 0

        val esCaliente = tipoActual == TipoRacha.CALIENTE || tipoActual == TipoRacha.MUY_CALIENTE
        var racha = 0
        var ventanaMovil = 5  // Analizar en mini-ventanas

        for (i in 0 until minOf(historico.size - ventanaMovil, 50) step ventanaMovil) {
            val miniVentana = historico.subList(i, i + ventanaMovil)
            val apariciones = miniVentana.count { numero in it.numeros }
            val esperado = ventanaMovil * 6.0 / 49  // Aproximado

            val tendenciaVentana = if (esperado > 0) {
                (apariciones - esperado) / esperado
            } else 0.0

            val sigueEnRacha = if (esCaliente) tendenciaVentana > 0.1 else tendenciaVentana < -0.1

            if (sigueEnRacha) {
                racha += ventanaMovil
            } else {
                break
            }
        }

        return racha
    }

    /**
     * MEJORA 8: Calcula el score de racha para una combinación.
     *
     * Estrategia mixta:
     * - Incluir algunos números calientes (momentum)
     * - Incluir algunos números fríos que están "debidos"
     * - Evitar extremos (todos calientes o todos fríos)
     */
    fun calcScoreRachas(nums: List<Int>, rachas: Map<Int, InfoRacha>): Double {
        if (rachas.isEmpty()) return 0.5

        var score = 0.0
        var calientes = 0
        var frios = 0

        for (num in nums) {
            val racha = rachas[num] ?: continue

            when (racha.tipoRacha) {
                TipoRacha.MUY_CALIENTE -> {
                    score += 0.8  // Buenos por momentum
                    calientes++
                }
                TipoRacha.CALIENTE -> {
                    score += 0.9  // Muy buenos (en racha pero no extremo)
                    calientes++
                }
                TipoRacha.NORMAL -> {
                    score += 0.5  // Neutro
                }
                TipoRacha.FRIO -> {
                    score += 0.7  // Buenos por estar "debidos"
                    frios++
                }
                TipoRacha.MUY_FRIO -> {
                    score += 0.6  // Arriesgados pero con potencial
                    frios++
                }
            }
        }

        // Bonificación por balance: mezcla de calientes y fríos es mejor
        val balanceBonus = if (calientes in 1..3 && frios in 1..3) 0.1 else 0.0

        // Penalización por extremos: todos calientes o todos fríos
        val extremoPenalty = when {
            calientes >= nums.size - 1 -> -0.15
            frios >= nums.size - 1 -> -0.10
            else -> 0.0
        }

        return ((score / nums.size) + balanceBonus + extremoPenalty).coerceIn(0.0, 1.0)
    }

    /**
     * MEJORA 8: Obtiene los números más calientes actualmente.
     */
    fun obtenerNumerosCalientes(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int,
        top: Int = 10
    ): List<InfoRacha> {
        val rachas = detectarRachas(historico, maxNum)
        return rachas.values
            .filter { it.tipoRacha == TipoRacha.CALIENTE || it.tipoRacha == TipoRacha.MUY_CALIENTE }
            .sortedByDescending { it.intensidad }
            .take(top)
    }

    /**
     * MEJORA 8: Obtiene los números más fríos actualmente.
     */
    fun obtenerNumerosFrios(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int,
        top: Int = 10
    ): List<InfoRacha> {
        val rachas = detectarRachas(historico, maxNum)
        return rachas.values
            .filter { it.tipoRacha == TipoRacha.FRIO || it.tipoRacha == TipoRacha.MUY_FRIO }
            .sortedByDescending { it.intensidad }
            .take(top)
    }

    /**
     * MEJORA 8: Genera una combinación balanceada de calientes y fríos.
     *
     * Estrategia:
     * - 2-3 números calientes (momentum)
     * - 1-2 números fríos (debidos)
     * - 1-2 números normales (equilibrio)
     */
    fun generarCombinacionMixta(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int,
        cantidad: Int = 6,
        tipoLoteria: String = "PRIMITIVA"
    ): CombinacionSugerida {
        val rachas = detectarRachas(historico, maxNum)
        val car = extraerCaracteristicas(historico, maxNum, tipoLoteria)

        val calientes = rachas.values
            .filter { it.tipoRacha == TipoRacha.CALIENTE || it.tipoRacha == TipoRacha.MUY_CALIENTE }
            .sortedByDescending { it.intensidad }
            .map { it.numero }

        val frios = rachas.values
            .filter { it.tipoRacha == TipoRacha.FRIO || it.tipoRacha == TipoRacha.MUY_FRIO }
            .sortedByDescending { it.intensidad }
            .map { it.numero }

        val normales = rachas.values
            .filter { it.tipoRacha == TipoRacha.NORMAL }
            .map { it.numero }
            .shuffled()

        val seleccionados = mutableSetOf<Int>()

        // Añadir 2-3 calientes
        val numCalientes = minOf(3, cantidad / 2, calientes.size)
        calientes.take(numCalientes).forEach { seleccionados.add(it) }

        // Añadir 1-2 fríos
        val numFrios = minOf(2, (cantidad - seleccionados.size) / 2, frios.size)
        frios.take(numFrios).forEach { seleccionados.add(it) }

        // Completar con normales
        normales.filter { it !in seleccionados }.take(cantidad - seleccionados.size)
            .forEach { seleccionados.add(it) }

        // Si faltan, añadir cualquier número disponible con buen score
        if (seleccionados.size < cantidad) {
            car.frecuencias.entries
                .sortedByDescending { it.value }
                .filter { it.key !in seleccionados }
                .take(cantidad - seleccionados.size)
                .forEach { seleccionados.add(it.key) }
        }

        // Validar perfil
        val combinacionFinal = if (esPerfilValido(seleccionados.toList(), car.estadisticasPerfil, maxNum)) {
            seleccionados.toList().sorted()
        } else {
            // Regenerar con más normales
            seleccionados.clear()
            calientes.take(2).forEach { seleccionados.add(it) }
            frios.take(1).forEach { seleccionados.add(it) }
            normales.filter { it !in seleccionados }.take(cantidad - seleccionados.size)
                .forEach { seleccionados.add(it) }

            while (seleccionados.size < cantidad) {
                val disponible = (1..maxNum).filter { it !in seleccionados }.randomOrNull() ?: break
                seleccionados.add(disponible)
            }
            seleccionados.toList().sorted()
        }

        val scoreRacha = calcScoreRachas(combinacionFinal, rachas)

        return CombinacionSugerida(
            numeros = combinacionFinal,
            probabilidadRelativa = (scoreRacha * 100).roundTo(1),
            explicacion = "🔥❄️ Mix Rachas | Cal:$numCalientes Frí:$numFrios | Score:${String.format("%.0f", scoreRacha * 100)}%"
        )
    }

    /**
     * MEJORA 8: Resumen de rachas para mostrar en UI.
     */
    data class ResumenRachas(
        val totalCalientes: Int,
        val totalFrios: Int,
        val top5Calientes: List<Int>,
        val top5Frios: List<Int>,
        val numeroMasCaliente: Int?,
        val numeroMasFrio: Int?,
        val tendenciaGeneral: String  // "Mercado caliente", "Mercado frío", "Equilibrado"
    )

    fun obtenerResumenRachas(
        historico: List<ResultadoPrimitiva>,
        maxNum: Int
    ): ResumenRachas {
        val rachas = detectarRachas(historico, maxNum)

        val calientes = rachas.values.filter {
            it.tipoRacha == TipoRacha.CALIENTE || it.tipoRacha == TipoRacha.MUY_CALIENTE
        }.sortedByDescending { it.intensidad }

        val frios = rachas.values.filter {
            it.tipoRacha == TipoRacha.FRIO || it.tipoRacha == TipoRacha.MUY_FRIO
        }.sortedByDescending { it.intensidad }

        val tendencia = when {
            calientes.size > frios.size * 1.5 -> "🔥 Mercado caliente"
            frios.size > calientes.size * 1.5 -> "❄️ Mercado frío"
            else -> "⚖️ Equilibrado"
        }

        return ResumenRachas(
            totalCalientes = calientes.size,
            totalFrios = frios.size,
            top5Calientes = calientes.take(5).map { it.numero },
            top5Frios = frios.take(5).map { it.numero },
            numeroMasCaliente = calientes.firstOrNull()?.numero,
            numeroMasFrio = frios.firstOrNull()?.numero,
            tendenciaGeneral = tendencia
        )
    }

    // Motor de matemáticas avanzadas del abuelo
    private val matematicasAbuelo = MatematicasAbuelo()

    // ═══════════════════════════════════════════════════════════════════════════════
    // 🔮 MÉTODO DEL ABUELO: SISTEMA DE CONVERGENCIAS MATEMÁTICAS AVANZADAS
    // ═══════════════════════════════════════════════════════════════════════════════
    //
    // "No se trata de adivinar el azar, sino de encontrar dónde el azar deja
    // de serlo. Las bolas no son perfectas, las máquinas tienen sesgos,
    // y las matemáticas nunca mienten."
    //
    // ALGORITMOS IMPLEMENTADOS:
    // 1. Test Chi-Cuadrado: detecta sesgos estadísticos reales
    // 2. Análisis de Fourier: periodicidades genuinas
    // 3. Inferencia Bayesiana: probabilidades actualizadas
    // 4. Cadenas de Markov: transiciones entre sorteos
    // 5. Análisis de Entropía: ventanas de baja aleatoriedad
    // 6. Diseños de Cobertura: maximizar aciertos parciales
    // 7. Validación Monte Carlo: verificación contra aleatorio
    //
    // ADEMÁS mantiene los factores clásicos:
    // - Ciclos temporales (día de semana, mes, estación)
    // - Intervalos personales de cada número
    // - Armonías entre números (pares que resuenan)
    // - El "rango dorado" de las sumas
    // - Balance perfecto (pares/impares, altos/bajos)
    // - Números maestros que trascienden el tiempo
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Resultado del análisis de convergencias del Método del Abuelo.
     */
    data class ConvergenciaAbuelo(
        val numero: Int,
        val scoreTotal: Double,              // Score total de convergencia (0-100)
        val factoresActivos: Int,            // Cuántos factores convergen
        val detalle: Map<String, Double>,    // Score por cada factor
        val esMaestro: Boolean,              // Si es un "número maestro"
        val estaDebido: Boolean,             // Si está "debido" por ciclo
        val momentoPerfecto: Boolean         // Si es el momento perfecto
    )

    /**
     * Resultado completo del Método del Abuelo.
     */
    data class ResultadoMetodoAbuelo(
        val combinacionPrincipal: List<Int>,
        val confianza: Double,
        val convergenciasDetalle: List<ConvergenciaAbuelo>,
        val sumaTotal: Int,
        val enRangoDorado: Boolean,
        val balancePerfecto: Boolean,
        val armoniasEncontradas: Int,
        val combinacionesAlternativas: List<List<Int>>,
        val explicacion: String,
        val sabiduria: String                // Mensaje de sabiduría del abuelo
    )

    /**
     * 🔮 MÉTODO DEL ABUELO: Sistema de convergencias matemáticas avanzadas.
     *
     * Combina análisis matemáticos rigurosos con factores clásicos:
     *
     * ANÁLISIS MATEMÁTICOS (peso dinámico según significancia):
     * 1. Test Chi-Cuadrado → detecta sesgos reales en bolas/máquinas
     * 2. Análisis de Fourier → periodicidades genuinas
     * 3. Inferencia Bayesiana → probabilidades actualizadas
     * 4. Cadenas de Markov → transiciones entre sorteos
     * 5. Análisis de Entropía → ventanas de baja aleatoriedad
     *
     * FACTORES CLÁSICOS:
     * 6. Ciclos temporales (día, mes)
     * 7. Intervalos personales de cada número
     * 8. Armonías (co-ocurrencias significativas)
     * 9. Rango dorado de sumas
     * 10. Números maestros + Momentum
     */
    fun ejecutarMetodoAbuelo(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        tipoLoteria: String = "PRIMITIVA"
    ): ResultadoMetodoAbuelo {
        cargarMemoria(tipoLoteria)

        if (historico.size < 100) {
            return generarMetodoAbueloSimple(historico, maxNumero, cantidadNumeros)
        }

        // ═══════════════════════════════════════════════════════════════════
        // FASE 1: ANÁLISIS MATEMÁTICO AVANZADO (Nuevos algoritmos del abuelo)
        // ═══════════════════════════════════════════════════════════════════

        // 1. Test Chi-Cuadrado: ¿hay sesgos reales?
        val (chiTotal, chiResultados) = matematicasAbuelo.testChiCuadradoGlobal(
            historico, maxNumero, cantidadNumeros
        )
        val sesgosConsistentes = matematicasAbuelo.numerosConSesgoConsistente(
            historico, maxNumero, cantidadNumeros
        )

        // 2. Inferencia Bayesiana: probabilidades actualizadas
        val bayesianos = matematicasAbuelo.inferenciaBayesiana(historico, maxNumero, cantidadNumeros)
        val bayesianoTemporal = matematicasAbuelo.bayesianoTemporal(historico, maxNumero, cantidadNumeros)

        // 3. Análisis de Fourier: periodicidades
        val fourier = matematicasAbuelo.analizarFourier(historico, maxNumero)

        // 4. Cadenas de Markov: transiciones
        val markov1 = matematicasAbuelo.analizarMarkov(historico, maxNumero)
        val markov2 = matematicasAbuelo.markovSegundoOrden(historico, maxNumero)

        // 5. Análisis de Entropía
        val entropia = matematicasAbuelo.calcularEntropia(historico, maxNumero)

        // ═══════════════════════════════════════════════════════════════════
        // FASE 2: ANÁLISIS CLÁSICO (Factores de convergencia originales)
        // ═══════════════════════════════════════════════════════════════════
        val hoy = java.time.LocalDate.now()
        val diaHoy = hoy.dayOfWeek.value
        val mesActual = hoy.monthValue

        val frecuenciaPorDia = analizarFrecuenciaPorDia(historico, maxNumero)
        val numerosDelDia = frecuenciaPorDia[diaHoy] ?: emptyMap()

        val intervalos = calcularIntervaloPersonal(historico, maxNumero)
        val numerosDebidos = intervalos.filter { (_, info) ->
            info.sorteosSinSalir >= info.intervaloPromedio * 1.3
        }.map { it.key }

        val armonias = calcularArmonias(historico, maxNumero)
        val (sumaMin, sumaMax, _) = calcularRangoDorado(historico, cantidadNumeros)
        val maestros = identificarNumerosMaestros(historico, maxNumero)

        val rachas = detectarRachas(historico, maxNumero)
        val enRacha = rachas.filter { (_, info) ->
            info.tipoRacha == TipoRacha.CALIENTE || info.tipoRacha == TipoRacha.MUY_CALIENTE
        }.map { it.key }

        val numerosDeMes = analizarFrecuenciaPorMes(historico, maxNumero)[mesActual] ?: emptyMap()

        // ═══════════════════════════════════════════════════════════════════
        // FASE 3: CALCULAR PESOS DINÁMICOS (según significancia estadística)
        // ═══════════════════════════════════════════════════════════════════
        val haySesgosReales = chiResultados.count { it.esSignificativo } > maxNumero * 0.08
        val hayPatronesMarkov = markov1.count { it.value.esMarkovSignificativo } > maxNumero * 0.05
        val hayPeriodicidades = fourier.count { it.value.confianzaPeriodicidad > 0.5 } > maxNumero * 0.05
        val hayBajaEntropia = entropia.esVentanaBajaEntropia

        // Cargar pesos aprendidos del Abuelo
        val pesosAprendidos = memoria?.obtenerPesosAbuelo(tipoLoteria) ?: emptyMap()

        // Pesos adaptativos: más peso a lo estadísticamente significativo
        val pesoAvanzado = if (haySesgosReales || hayPatronesMarkov || hayPeriodicidades) 0.60 else 0.35
        val pesoClasico = 1.0 - pesoAvanzado

        // ═══════════════════════════════════════════════════════════════════
        // FASE 4: CONVERGENCIA FINAL MULTI-DIMENSIONAL
        // ═══════════════════════════════════════════════════════════════════
        val pUniforme = cantidadNumeros.toDouble() / maxNumero

        val convergenciasFinales = (1..maxNumero).map { num ->
            // === SCORE AVANZADO ===
            val chiRes = chiResultados.find { it.numero == num }
            val scoreChi = if (chiRes != null && chiRes.sesgo > 0) {
                val sesgoPuntual = chiRes.sesgo.coerceIn(0.0, 1.0)
                val sesgoConsistente = sesgosConsistentes[num] ?: 0.0
                ((sesgoPuntual * 0.4 + sesgoConsistente * 0.6) * 100).coerceIn(0.0, 100.0)
            } else 0.0

            val bayesRes = bayesianos[num]
            val bayesTempRes = bayesianoTemporal[num] ?: 0.0
            val scoreBayes = if (bayesRes != null) {
                val desviacion = (bayesRes.posteriorMedia - pUniforme) / pUniforme
                val desvTemporal = (bayesTempRes - pUniforme) / pUniforme
                ((desviacion * 0.5 + desvTemporal * 0.5) * 500 + 50).coerceIn(0.0, 100.0)
            } else 50.0

            val fourierRes = fourier[num]
            val scoreFourier = if (fourierRes != null && fourierRes.confianzaPeriodicidad > 0.3) {
                val proximidad = 1.0 - (fourierRes.prediccionProximaSalida.toDouble() /
                    fourierRes.periodoDominante).coerceIn(0.0, 1.0)
                (proximidad * fourierRes.confianzaPeriodicidad * 100).coerceIn(0.0, 100.0)
            } else 50.0

            val markovRes = markov1[num]
            val markov2Res = markov2[num] ?: 0.0
            val scoreMarkov = if (markovRes != null) {
                val prediccion = markovRes.prediccionProximoSorteo
                val prediccion2 = markov2Res
                ((prediccion * 0.6 + prediccion2 * 0.4) / pUniforme * 50).coerceIn(0.0, 100.0)
            } else 50.0

            val scoreEntropia = if (num in entropia.numerosConcentrados) {
                val posicion = entropia.numerosConcentrados.indexOf(num)
                (100.0 - posicion * 5).coerceIn(50.0, 100.0)
            } else 40.0

            // Pesos base por significancia estadística
            var wChi = if (haySesgosReales) 0.30 else 0.10
            var wBayes = 0.25
            var wFourier = if (hayPeriodicidades) 0.20 else 0.05
            var wMarkov = if (hayPatronesMarkov) 0.15 else 0.05
            var wEntropia = if (hayBajaEntropia) 0.10 else 0.05

            // Integrar pesos aprendidos (mezcla 50/50 estadístico + aprendido)
            if (pesosAprendidos.isNotEmpty()) {
                val m = 0.5
                wChi = wChi * (1 - m) + (pesosAprendidos["chiCuadrado"] ?: 0.2) * m
                wBayes = wBayes * (1 - m) + (pesosAprendidos["bayesiano"] ?: 0.2) * m
                wFourier = wFourier * (1 - m) + (pesosAprendidos["fourier"] ?: 0.2) * m
                wMarkov = wMarkov * (1 - m) + (pesosAprendidos["markov"] ?: 0.2) * m
                wEntropia = wEntropia * (1 - m) + (pesosAprendidos["entropia"] ?: 0.2) * m
            }

            val scoreAvanzado = (
                scoreChi * wChi +
                scoreBayes * wBayes +
                scoreFourier * wFourier +
                scoreMarkov * wMarkov +
                scoreEntropia * wEntropia
            ) / (if (haySesgosReales) 1.0 else 0.5) // Normalizar

            // === SCORE CLÁSICO (convergencia original del abuelo) ===
            val convergenciaClasica = calcularConvergenciaNumero(
                numero = num,
                scoreDelDia = numerosDelDia[num] ?: 0.0,
                estaDebido = num in numerosDebidos,
                armoniasCon = armonias[num] ?: emptyList(),
                esMaestro = num in maestros,
                enRacha = num in enRacha,
                scoreMes = numerosDeMes[num] ?: 0.0,
                intervalo = intervalos[num]
            )

            // === SCORE FINAL COMBINADO ===
            val scoreFinal = scoreAvanzado * pesoAvanzado + convergenciaClasica.scoreTotal * pesoClasico

            // Contar factores activos
            var factoresActivos = convergenciaClasica.factoresActivos
            if (scoreChi > 55) factoresActivos++
            if (scoreBayes > 55) factoresActivos++
            if (scoreFourier > 55) factoresActivos++
            if (scoreMarkov > 55) factoresActivos++

            // Generar mini-explicación
            val tags = mutableListOf<String>()
            if (scoreChi > 60) tags.add("χ²")
            if (scoreBayes > 60) tags.add("Bay")
            if (scoreFourier > 60) tags.add("Fou")
            if (scoreMarkov > 60) tags.add("Mkv")
            if (convergenciaClasica.esMaestro) tags.add("M")
            if (convergenciaClasica.estaDebido) tags.add("D")
            if (num in enRacha) tags.add("🔥")

            ConvergenciaAbuelo(
                numero = num,
                scoreTotal = scoreFinal,
                factoresActivos = factoresActivos,
                detalle = convergenciaClasica.detalle + mapOf(
                    "chi" to scoreChi,
                    "bayes" to scoreBayes,
                    "fourier" to scoreFourier,
                    "markov" to scoreMarkov,
                    "entropia" to scoreEntropia
                ),
                esMaestro = num in maestros,
                estaDebido = num in numerosDebidos,
                momentoPerfecto = factoresActivos >= 5 && scoreFinal >= 55
            )
        }.sortedByDescending { it.scoreTotal }

        // ═══════════════════════════════════════════════════════════════════
        // FASE 5: CONSTRUIR COMBINACIÓN ÓPTIMA
        // ═══════════════════════════════════════════════════════════════════

        // Usar el constructor avanzado que respeta restricciones
        val convergenciasParaConstructor = convergenciasFinales.map { conv ->
            MatematicasAbuelo.ConvergenciaFinal(
                numero = conv.numero,
                scoreTotal = conv.scoreTotal,
                chiCuadrado = conv.detalle["chi"] ?: 0.0,
                bayesiano = conv.detalle["bayes"] ?: 50.0,
                fourier = conv.detalle["fourier"] ?: 50.0,
                markov = conv.detalle["markov"] ?: 50.0,
                entropia = conv.detalle["entropia"] ?: 40.0,
                convergenciaClasica = conv.detalle.values.sum() -
                    (conv.detalle["chi"] ?: 0.0) - (conv.detalle["bayes"] ?: 0.0) -
                    (conv.detalle["fourier"] ?: 0.0) - (conv.detalle["markov"] ?: 0.0) -
                    (conv.detalle["entropia"] ?: 0.0),
                factoresActivos = conv.factoresActivos,
                explicacion = ""
            )
        }

        val combinacion = matematicasAbuelo.construirCombinacionOptima(
            convergenciasParaConstructor, cantidadNumeros, maxNumero, historico
        )

        val suma = combinacion.sum()
        val enRangoDorado = suma in sumaMin..sumaMax
        val balancePerfecto = verificarBalancePerfecto(combinacion, maxNumero)

        // ═══════════════════════════════════════════════════════════════════
        // FASE 6: GENERAR ALTERNATIVAS CON COBERTURA ÓPTIMA
        // ═══════════════════════════════════════════════════════════════════
        val scoresParaCobertura = convergenciasFinales.map { it.numero to it.scoreTotal }
        val alternativasCobertura = matematicasAbuelo.coberturaConDiversidad(
            scoresNumeros = convergenciasFinales.associate { it.numero to it.scoreTotal },
            cantidadNumeros = cantidadNumeros,
            numCombinaciones = 5,
            maxNumero = maxNumero
        ).filter { it.toSet() != combinacion.toSet() }.take(4)

        // También las alternativas clásicas como respaldo
        val alternativasClasicas = generarAlternativasAbuelo(
            convergencias = convergenciasFinales,
            cantidadNumeros = cantidadNumeros,
            maxNumero = maxNumero,
            sumaMin = sumaMin,
            sumaMax = sumaMax,
            combinacionPrincipal = combinacion.toSet()
        )

        val alternativasFinales = (alternativasCobertura + alternativasClasicas)
            .distinctBy { it.toSet() }
            .filter { it.toSet() != combinacion.toSet() }
            .take(4)

        // ═══════════════════════════════════════════════════════════════════
        // FASE 7: VALIDACIÓN MONTE CARLO
        // ═══════════════════════════════════════════════════════════════════
        val topNumerosParaMC = convergenciasFinales.take(cantidadNumeros * 3).map { it.numero }
        val monteCarlo = matematicasAbuelo.validacionMonteCarlo(
            historico = historico,
            numerosPreferidos = topNumerosParaMC,
            maxNumero = maxNumero,
            cantidadNumeros = cantidadNumeros,
            simulaciones = 300,
            ventanaValidacion = minOf(30, historico.size / 4)
        )

        val convergenciasSeleccionadas = convergenciasFinales.filter { it.numero in combinacion }
        val confianza = convergenciasSeleccionadas.map { it.scoreTotal }.average() / 100.0

        // ═══════════════════════════════════════════════════════════════════
        // FASE 8: GENERAR SABIDURÍA Y EXPLICACIÓN
        // ═══════════════════════════════════════════════════════════════════
        val numSesgos = chiResultados.count { it.esSignificativo && it.sesgo > 0 }
        val numFourierPeaks = fourier.count { it.value.confianzaPeriodicidad > 0.5 }
        val numMarkovSig = markov1.count { it.value.esMarkovSignificativo }

        val explicacionDetallada = buildString {
            append("🔮📐 ")
            if (haySesgosReales) append("χ²:$numSesgos sesgos ")
            if (hayPeriodicidades) append("| Fou:$numFourierPeaks ciclos ")
            if (hayPatronesMarkov) append("| Mkv:$numMarkovSig trans ")
            if (hayBajaEntropia) append("| Ent↓ ")
            append("| Σ=$suma ${if(enRangoDorado) "✓" else ""} ")
            append("| ${convergenciasSeleccionadas.count { it.esMaestro }}M ")
            append("| MC:${"%.1f".format(monteCarlo.mejora)}%")
        }

        val sabiduriaFinal = generarSabiduriaAbueloAvanzada(
            convergenciasSeleccionadas, enRangoDorado, balancePerfecto, suma,
            haySesgosReales, hayPeriodicidades, hayPatronesMarkov, monteCarlo.mejora
        )

        return ResultadoMetodoAbuelo(
            combinacionPrincipal = combinacion,
            confianza = confianza,
            convergenciasDetalle = convergenciasSeleccionadas,
            sumaTotal = suma,
            enRangoDorado = enRangoDorado,
            balancePerfecto = balancePerfecto,
            armoniasEncontradas = contarArmoniasEnCombinacion(combinacion, armonias),
            combinacionesAlternativas = alternativasFinales,
            explicacion = explicacionDetallada,
            sabiduria = sabiduriaFinal
        )
    }

    /**
     * Genera sabiduría del abuelo con información de los análisis avanzados.
     */
    private fun generarSabiduriaAbueloAvanzada(
        convergencias: List<ConvergenciaAbuelo>,
        enRangoDorado: Boolean,
        balancePerfecto: Boolean,
        suma: Int,
        haySesgos: Boolean,
        hayPeriodicidades: Boolean,
        hayMarkov: Boolean,
        mejoraMC: Double
    ): String {
        val perfectos = convergencias.count { it.momentoPerfecto }
        val maestros = convergencias.count { it.esMaestro }

        return when {
            haySesgos && mejoraMC > 10 && perfectos >= 3 ->
                "🌟 Las matemáticas no mienten. χ² detecta sesgo real. Fourier confirma los ciclos. Este es EL momento."

            haySesgos && hayPeriodicidades ->
                "📐 Sesgos estadísticos confirmados por Chi-Cuadrado. Los ciclos de Fourier convergen. El abuelo sonríe."

            hayMarkov && haySesgos ->
                "🔗 Las cadenas de Markov revelan un patrón de transición. Combinado con sesgos reales, la probabilidad se inclina."

            perfectos >= 4 && enRangoDorado && balancePerfecto ->
                "🌟 Convergencia máxima: ${perfectos} factores alineados. Suma dorada. Balance perfecto. Es el momento."

            mejoraMC > 5 ->
                "📊 Monte Carlo confirma: ${"%.1f".format(mejoraMC)}% mejor que el azar. Las matemáticas están de tu lado."

            hayPeriodicidades && maestros >= 2 ->
                "🔮 Fourier detecta ciclos reales. Los números maestros resuenan. Paciencia y matemáticas."

            perfectos >= 3 ->
                "📚 ${perfectos} convergencias activas. Como decía el abuelo: 'cuando todo se alinea, hay que estar ahí'."

            maestros >= 3 ->
                "📚 Los números maestros trascienden el tiempo. Su consistencia habla."

            else ->
                "🎲 Las matemáticas buscan ventaja donde otros ven azar. Cada sorteo es una oportunidad."
        }
    }

    /**
     * Analiza frecuencia de aparición por día de la semana.
     */
    private fun analizarFrecuenciaPorDia(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int
    ): Map<Int, Map<Int, Double>> {
        val frecuenciaPorDia = mutableMapOf<Int, MutableMap<Int, Int>>()
        val conteoSorteosPorDia = mutableMapOf<Int, Int>()

        historico.forEach { sorteo ->
            try {
                val fecha = java.time.LocalDate.parse(sorteo.fecha)
                val dia = fecha.dayOfWeek.value

                conteoSorteosPorDia[dia] = (conteoSorteosPorDia[dia] ?: 0) + 1

                if (!frecuenciaPorDia.containsKey(dia)) {
                    frecuenciaPorDia[dia] = mutableMapOf()
                }

                sorteo.numeros.forEach { num ->
                    frecuenciaPorDia[dia]!![num] = (frecuenciaPorDia[dia]!![num] ?: 0) + 1
                }
            } catch (_: Exception) {}
        }

        // Normalizar por número de sorteos de cada día
        return frecuenciaPorDia.mapValues { (dia, freqs) ->
            val total = conteoSorteosPorDia[dia]?.toDouble() ?: 1.0
            freqs.mapValues { (_, count) -> count / total }
        }
    }

    /**
     * Analiza frecuencia por mes del año.
     */
    private fun analizarFrecuenciaPorMes(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int
    ): Map<Int, Map<Int, Double>> {
        val frecuenciaPorMes = mutableMapOf<Int, MutableMap<Int, Int>>()
        val conteoSorteosPorMes = mutableMapOf<Int, Int>()

        historico.forEach { sorteo ->
            try {
                val fecha = java.time.LocalDate.parse(sorteo.fecha)
                val mes = fecha.monthValue

                conteoSorteosPorMes[mes] = (conteoSorteosPorMes[mes] ?: 0) + 1

                if (!frecuenciaPorMes.containsKey(mes)) {
                    frecuenciaPorMes[mes] = mutableMapOf()
                }

                sorteo.numeros.forEach { num ->
                    frecuenciaPorMes[mes]!![num] = (frecuenciaPorMes[mes]!![num] ?: 0) + 1
                }
            } catch (_: Exception) {}
        }

        return frecuenciaPorMes.mapValues { (mes, freqs) ->
            val total = conteoSorteosPorMes[mes]?.toDouble() ?: 1.0
            freqs.mapValues { (_, count) -> count / total }
        }
    }

    /**
     * Información del intervalo personal de un número.
     */
    data class IntervaloPersonal(
        val intervaloPromedio: Double,   // Cada cuántos sorteos sale en promedio
        val sorteosSinSalir: Int,        // Hace cuántos sorteos que no sale
        val varianza: Double,            // Qué tan regular es
        val ultimaAparicion: Int         // Índice del último sorteo donde apareció
    )

    /**
     * Calcula el intervalo personal de cada número.
     */
    private fun calcularIntervaloPersonal(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int
    ): Map<Int, IntervaloPersonal> {
        val resultado = mutableMapOf<Int, IntervaloPersonal>()

        for (num in 1..maxNumero) {
            val apariciones = mutableListOf<Int>()

            historico.forEachIndexed { index, sorteo ->
                if (num in sorteo.numeros) {
                    apariciones.add(index)
                }
            }

            if (apariciones.size >= 2) {
                val intervalos = apariciones.zipWithNext { a, b -> b - a }
                val promedio = intervalos.average()
                val varianza = intervalos.map { (it - promedio).pow(2) }.average()
                val ultimaAparicion = apariciones.lastOrNull() ?: historico.size
                val sorteosSinSalir = historico.size - 1 - ultimaAparicion

                resultado[num] = IntervaloPersonal(
                    intervaloPromedio = promedio,
                    sorteosSinSalir = sorteosSinSalir,
                    varianza = varianza,
                    ultimaAparicion = ultimaAparicion
                )
            } else {
                resultado[num] = IntervaloPersonal(
                    intervaloPromedio = historico.size.toDouble(),
                    sorteosSinSalir = historico.size,
                    varianza = 0.0,
                    ultimaAparicion = -1
                )
            }
        }

        return resultado
    }

    /**
     * Calcula las armonías entre números (pares que aparecen juntos más de lo esperado).
     */
    private fun calcularArmonias(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int
    ): Map<Int, List<Int>> {
        val coApariciones = mutableMapOf<Pair<Int, Int>, Int>()
        val frecuenciaIndividual = mutableMapOf<Int, Int>()

        historico.forEach { sorteo ->
            sorteo.numeros.forEach { num ->
                frecuenciaIndividual[num] = (frecuenciaIndividual[num] ?: 0) + 1
            }

            for (i in sorteo.numeros.indices) {
                for (j in i + 1 until sorteo.numeros.size) {
                    val par = minOf(sorteo.numeros[i], sorteo.numeros[j]) to
                              maxOf(sorteo.numeros[i], sorteo.numeros[j])
                    coApariciones[par] = (coApariciones[par] ?: 0) + 1
                }
            }
        }

        // Calcular correlación esperada vs real
        val armonias = mutableMapOf<Int, MutableList<Int>>()
        val n = historico.size.toDouble()

        coApariciones.forEach { (par, count) ->
            val (a, b) = par
            val freqA = frecuenciaIndividual[a] ?: 0
            val freqB = frecuenciaIndividual[b] ?: 0

            // Esperado = P(A) * P(B) * n
            val esperado = (freqA / n) * (freqB / n) * n

            // Si aparecen juntos más de 1.5x lo esperado, son armónicos
            if (count > esperado * 1.5 && count >= 3) {
                armonias.getOrPut(a) { mutableListOf() }.add(b)
                armonias.getOrPut(b) { mutableListOf() }.add(a)
            }
        }

        return armonias
    }

    /**
     * Calcula el "rango dorado" de las sumas ganadoras.
     * Retorna (mínimo, máximo, centro) del rango donde caen más combinaciones ganadoras.
     */
    private fun calcularRangoDorado(
        historico: List<ResultadoPrimitiva>,
        cantidadNumeros: Int
    ): Triple<Int, Int, Int> {
        val sumas = historico.map { it.numeros.take(cantidadNumeros).sum() }

        if (sumas.isEmpty()) {
            val teorico = (1..49).sum() * cantidadNumeros / 49
            return Triple(teorico - 30, teorico + 30, teorico)
        }

        val sumaOrdenada = sumas.sorted()
        val percentil25 = sumaOrdenada[(sumas.size * 0.25).toInt()]
        val percentil75 = sumaOrdenada[(sumas.size * 0.75).toInt()]
        val mediana = sumaOrdenada[sumas.size / 2]

        return Triple(percentil25, percentil75, mediana)
    }

    /**
     * Identifica los "números maestros" - los que aparecen consistentemente
     * a través de diferentes períodos de tiempo.
     */
    private fun identificarNumerosMaestros(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int
    ): Set<Int> {
        if (historico.size < 200) return emptySet()

        // Dividir en 4 períodos
        val tamPeriodo = historico.size / 4
        val periodos = (0 until 4).map { i ->
            historico.subList(i * tamPeriodo, minOf((i + 1) * tamPeriodo, historico.size))
        }

        // Contar frecuencia en cada período
        val frecuenciaPorPeriodo = periodos.map { periodo ->
            val freq = mutableMapOf<Int, Int>()
            periodo.forEach { sorteo ->
                sorteo.numeros.forEach { num ->
                    freq[num] = (freq[num] ?: 0) + 1
                }
            }
            // Top 15 de cada período
            freq.entries.sortedByDescending { it.value }.take(15).map { it.key }.toSet()
        }

        // Números maestros: aparecen en top 15 de AL MENOS 3 períodos
        return (1..maxNumero).filter { num ->
            frecuenciaPorPeriodo.count { num in it } >= 3
        }.toSet()
    }

    /**
     * Calcula la convergencia total de un número.
     */
    private fun calcularConvergenciaNumero(
        numero: Int,
        scoreDelDia: Double,
        estaDebido: Boolean,
        armoniasCon: List<Int>,
        esMaestro: Boolean,
        enRacha: Boolean,
        scoreMes: Double,
        intervalo: IntervaloPersonal?
    ): ConvergenciaAbuelo {
        val detalle = mutableMapOf<String, Double>()
        var factoresActivos = 0

        // Factor 1: Día de la semana (0-20 puntos)
        val puntosDelDia = (scoreDelDia * 100).coerceIn(0.0, 20.0)
        detalle["dia"] = puntosDelDia
        if (puntosDelDia > 10) factoresActivos++

        // Factor 2: Está debido por ciclo (0-25 puntos)
        val puntosDebido = if (estaDebido) {
            val ratio = intervalo?.let {
                (it.sorteosSinSalir / it.intervaloPromedio).coerceIn(1.0, 2.5)
            } ?: 1.0
            (ratio * 10).coerceIn(0.0, 25.0)
        } else 0.0
        detalle["debido"] = puntosDebido
        if (puntosDebido > 12) factoresActivos++

        // Factor 3: Tiene armonías (0-15 puntos)
        val puntosArmonia = (armoniasCon.size * 3.0).coerceIn(0.0, 15.0)
        detalle["armonia"] = puntosArmonia
        if (armoniasCon.isNotEmpty()) factoresActivos++

        // Factor 4: Es número maestro (0-15 puntos)
        val puntosMaestro = if (esMaestro) 15.0 else 0.0
        detalle["maestro"] = puntosMaestro
        if (esMaestro) factoresActivos++

        // Factor 5: En racha positiva (0-15 puntos)
        val puntosRacha = if (enRacha) 15.0 else 0.0
        detalle["racha"] = puntosRacha
        if (enRacha) factoresActivos++

        // Factor 6: Mes favorable (0-10 puntos)
        val puntosMes = (scoreMes * 50).coerceIn(0.0, 10.0)
        detalle["mes"] = puntosMes
        if (puntosMes > 5) factoresActivos++

        val scoreTotal = detalle.values.sum()
        val momentoPerfecto = factoresActivos >= 4 && scoreTotal >= 50

        return ConvergenciaAbuelo(
            numero = numero,
            scoreTotal = scoreTotal,
            factoresActivos = factoresActivos,
            detalle = detalle,
            esMaestro = esMaestro,
            estaDebido = estaDebido,
            momentoPerfecto = momentoPerfecto
        )
    }

    /**
     * Construye la combinación óptima respetando el rango dorado y las armonías.
     */
    private fun construirCombinacionConvergente(
        convergencias: List<ConvergenciaAbuelo>,
        cantidadNumeros: Int,
        maxNumero: Int,
        sumaMin: Int,
        sumaMax: Int,
        armonias: Map<Int, List<Int>>
    ): List<Int> {
        val seleccionados = mutableListOf<Int>()
        val candidatos = convergencias.toMutableList()

        // Priorizar números en "momento perfecto"
        val perfectos = candidatos.filter { it.momentoPerfecto }
        perfectos.take(minOf(3, perfectos.size)).forEach {
            seleccionados.add(it.numero)
            candidatos.removeIf { c -> c.numero == it.numero }
        }

        // Añadir números armónicos con los ya seleccionados
        if (seleccionados.isNotEmpty()) {
            val armonicosDeSeleccionados = seleccionados.flatMap { armonias[it] ?: emptyList() }
                .distinct()
                .filter { it !in seleccionados }

            val candidatosArmonicos = candidatos.filter { it.numero in armonicosDeSeleccionados }
                .sortedByDescending { it.scoreTotal }
                .take(2)

            candidatosArmonicos.forEach {
                if (seleccionados.size < cantidadNumeros) {
                    seleccionados.add(it.numero)
                    candidatos.removeIf { c -> c.numero == it.numero }
                }
            }
        }

        // Completar con los de mayor convergencia
        while (seleccionados.size < cantidadNumeros && candidatos.isNotEmpty()) {
            val siguiente = candidatos.maxByOrNull { it.scoreTotal }
            if (siguiente != null) {
                seleccionados.add(siguiente.numero)
                candidatos.removeIf { it.numero == siguiente.numero }
            } else break
        }

        // Verificar suma y ajustar si es necesario
        var intentos = 0
        while (intentos < 50) {
            val suma = seleccionados.sum()
            when {
                suma in sumaMin..sumaMax -> break
                suma < sumaMin -> {
                    // Reemplazar el número más bajo por uno más alto
                    val minNum = seleccionados.minOrNull() ?: break
                    val reemplazo = candidatos
                        .filter { it.numero > minNum && it.numero !in seleccionados }
                        .maxByOrNull { it.scoreTotal }?.numero

                    if (reemplazo != null) {
                        seleccionados.remove(minNum)
                        seleccionados.add(reemplazo)
                    } else break
                }
                suma > sumaMax -> {
                    // Reemplazar el número más alto por uno más bajo
                    val maxNum = seleccionados.maxOrNull() ?: break
                    val reemplazo = candidatos
                        .filter { it.numero < maxNum && it.numero !in seleccionados }
                        .maxByOrNull { it.scoreTotal }?.numero

                    if (reemplazo != null) {
                        seleccionados.remove(maxNum)
                        seleccionados.add(reemplazo)
                    } else break
                }
            }
            intentos++
        }

        // Asegurar balance pares/impares
        val pares = seleccionados.count { it % 2 == 0 }
        val impares = seleccionados.size - pares

        if (pares == 0 || impares == 0) {
            // Desequilibrio total, intentar balancear
            val tipoReemplazar = if (pares == 0) { n: Int -> n % 2 != 0 } else { n: Int -> n % 2 == 0 }
            val numReemplazar = seleccionados.filter(tipoReemplazar).minByOrNull {
                convergencias.find { c -> c.numero == it }?.scoreTotal ?: 0.0
            }

            if (numReemplazar != null) {
                val tipoNuevo = if (pares == 0) { n: Int -> n % 2 == 0 } else { n: Int -> n % 2 != 0 }
                val nuevo = candidatos
                    .filter { tipoNuevo(it.numero) && it.numero !in seleccionados }
                    .maxByOrNull { it.scoreTotal }

                if (nuevo != null) {
                    seleccionados.remove(numReemplazar)
                    seleccionados.add(nuevo.numero)
                }
            }
        }

        return seleccionados.sorted()
    }

    /**
     * Verifica si la combinación tiene un balance "perfecto".
     */
    private fun verificarBalancePerfecto(combinacion: List<Int>, maxNumero: Int): Boolean {
        val pares = combinacion.count { it % 2 == 0 }
        val impares = combinacion.size - pares
        val mitad = maxNumero / 2
        val bajos = combinacion.count { it <= mitad }
        val altos = combinacion.size - bajos

        // Balance perfecto: diferencia máxima de 2 en ambas categorías
        return kotlin.math.abs(pares - impares) <= 2 && kotlin.math.abs(bajos - altos) <= 2
    }

    /**
     * Genera combinaciones alternativas variando los parámetros.
     */
    private fun generarAlternativasAbuelo(
        convergencias: List<ConvergenciaAbuelo>,
        cantidadNumeros: Int,
        maxNumero: Int,
        sumaMin: Int,
        sumaMax: Int,
        combinacionPrincipal: Set<Int>
    ): List<List<Int>> {
        val alternativas = mutableListOf<List<Int>>()
        val usados = mutableSetOf<Set<Int>>()
        usados.add(combinacionPrincipal)

        // Alternativa 1: Solo números en momento perfecto + maestros
        val perfectosYMaestros = convergencias
            .filter { it.momentoPerfecto || it.esMaestro }
            .map { it.numero }
            .shuffled()
            .take(cantidadNumeros)
        if (perfectosYMaestros.size == cantidadNumeros && usados.add(perfectosYMaestros.toSet())) {
            alternativas.add(perfectosYMaestros.sorted())
        }

        // Alternativa 2: Solo números debidos con alta convergencia
        val debidos = convergencias
            .filter { it.estaDebido && it.scoreTotal > 30 }
            .sortedByDescending { it.scoreTotal }
            .map { it.numero }
            .take(cantidadNumeros)
        if (debidos.size == cantidadNumeros && usados.add(debidos.toSet())) {
            alternativas.add(debidos.sorted())
        }

        // Alternativa 3: Mix de racha + maestros
        val rachaYMaestros = convergencias
            .filter { it.detalle["racha"]!! > 0 || it.esMaestro }
            .sortedByDescending { it.scoreTotal }
            .map { it.numero }
            .take(cantidadNumeros)
        if (rachaYMaestros.size == cantidadNumeros && usados.add(rachaYMaestros.toSet())) {
            alternativas.add(rachaYMaestros.sorted())
        }

        // Alternativa 4: Números del día/mes favorables
        val temporales = convergencias
            .filter { (it.detalle["dia"] ?: 0.0) > 8 || (it.detalle["mes"] ?: 0.0) > 5 }
            .sortedByDescending { it.scoreTotal }
            .map { it.numero }
            .take(cantidadNumeros)
        if (temporales.size == cantidadNumeros && usados.add(temporales.toSet())) {
            alternativas.add(temporales.sorted())
        }

        return alternativas.take(4)
    }

    /**
     * Cuenta cuántas armonías hay en una combinación.
     */
    private fun contarArmoniasEnCombinacion(
        combinacion: List<Int>,
        armonias: Map<Int, List<Int>>
    ): Int {
        var count = 0
        for (i in combinacion.indices) {
            for (j in i + 1 until combinacion.size) {
                val a = combinacion[i]
                val b = combinacion[j]
                if (armonias[a]?.contains(b) == true) {
                    count++
                }
            }
        }
        return count
    }

    /**
     * Genera un mensaje de sabiduría del abuelo basado en los resultados.
     */
    private fun generarSabiduriaAbuelo(
        convergencias: List<ConvergenciaAbuelo>,
        enRangoDorado: Boolean,
        balancePerfecto: Boolean,
        suma: Int
    ): String {
        val perfectos = convergencias.count { it.momentoPerfecto }
        val maestros = convergencias.count { it.esMaestro }
        val debidos = convergencias.count { it.estaDebido }

        return when {
            perfectos >= 4 && enRangoDorado && balancePerfecto ->
                "🌟 Los astros se alinean. Esta es una combinación de alta convergencia."

            perfectos >= 3 ->
                "🔮 Múltiples factores convergen. El momento es propicio."

            maestros >= 3 ->
                "📚 Los números maestros hablan. Escucha su sabiduría ancestral."

            debidos >= 4 ->
                "⏰ La paciencia tiene su recompensa. Estos números esperaban su momento."

            enRangoDorado && balancePerfecto ->
                "⚖️ Balance y armonía. El abuelo sonríe."

            else ->
                "🎲 Confía en los patrones. Nada es completamente aleatorio."
        }
    }

    /**
     * Versión simplificada para cuando no hay suficiente histórico.
     */
    private fun generarMetodoAbueloSimple(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int
    ): ResultadoMetodoAbuelo {
        val frecuencias = mutableMapOf<Int, Int>()
        historico.forEach { sorteo ->
            sorteo.numeros.forEach { num ->
                frecuencias[num] = (frecuencias[num] ?: 0) + 1
            }
        }

        val topNumeros = frecuencias.entries
            .sortedByDescending { it.value }
            .take(cantidadNumeros)
            .map { it.key }
            .sorted()

        val combinacion = if (topNumeros.size == cantidadNumeros) {
            topNumeros
        } else {
            (1..maxNumero).shuffled().take(cantidadNumeros).sorted()
        }

        return ResultadoMetodoAbuelo(
            combinacionPrincipal = combinacion,
            confianza = 0.3,
            convergenciasDetalle = emptyList(),
            sumaTotal = combinacion.sum(),
            enRangoDorado = false,
            balancePerfecto = verificarBalancePerfecto(combinacion, maxNumero),
            armoniasEncontradas = 0,
            combinacionesAlternativas = emptyList(),
            explicacion = "🔮 Método simplificado (histórico limitado)",
            sabiduria = "📚 El abuelo dice: necesitamos más historia para encontrar los patrones."
        )
    }

    /**
     * Genera una combinación usando el Método del Abuelo.
     * Función pública para usar desde CalculadorProbabilidad.
     */
    fun generarCombinacionMetodoAbuelo(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        tipoLoteria: String = "PRIMITIVA"
    ): CombinacionSugerida {
        val resultado = ejecutarMetodoAbuelo(historico, maxNumero, cantidadNumeros, tipoLoteria)

        return CombinacionSugerida(
            numeros = resultado.combinacionPrincipal,
            probabilidadRelativa = (resultado.confianza * 100).roundTo(1),
            explicacion = resultado.explicacion + " | " + resultado.sabiduria
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 9: SISTEMA DE ENSEMBLE VOTING (VOTACIÓN POR CONSENSO)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Estrategia de predicción disponible en el ensemble.
     */
    enum class EstrategiaPrediccion {
        GENETICO,           // Algoritmo genético mejorado
        ALTA_CONFIANZA,     // Sistema de 9 señales
        RACHAS_MIX,         // Mezcla calientes/fríos
        EQUILIBRIO,         // Números más "debidos" por equilibrio
        CICLOS,             // Números por ciclo/EMA
        CORRELACIONES,      // Números con buenos compañeros
        FRECUENCIA,         // Números más frecuentes
        TENDENCIA           // Números con mejor tendencia reciente
    }

    /**
     * Voto de una estrategia para un número.
     */
    data class VotoEstrategia(
        val estrategia: EstrategiaPrediccion,
        val numero: Int,
        val peso: Double,       // Peso del voto (basado en rendimiento histórico)
        val posicion: Int,      // Posición en el ranking de esa estrategia (1 = mejor)
        val score: Double       // Score interno de la estrategia
    )

    /**
     * Resultado de votación para un número.
     */
    data class ResultadoVotacion(
        val numero: Int,
        val votosTotal: Double,         // Suma ponderada de votos
        val numEstrategias: Int,        // Cuántas estrategias votaron por este número
        val posicionPromedio: Double,   // Posición promedio en los rankings
        val consenso: Double,           // 0-1, qué tan de acuerdo están las estrategias
        val votos: List<VotoEstrategia> // Detalle de cada voto
    )

    /**
     * Resultado del ensemble completo.
     */
    data class ResultadoEnsemble(
        val combinacionGanadora: List<Int>,
        val confianzaGlobal: Double,
        val votacionPorNumero: List<ResultadoVotacion>,
        val estrategiasUsadas: Int,
        val nivelConsenso: String,      // "Alto", "Medio", "Bajo"
        val combinacionesAlternativas: List<List<Int>>,
        val explicacion: String,
        val pesosEstrategias: Map<EstrategiaPrediccion, Double>
    )

    /**
     * MEJORA 9: Ejecuta el sistema de Ensemble Voting.
     *
     * Combina múltiples estrategias de predicción y usa votación ponderada
     * para encontrar los números con mayor consenso.
     *
     * @param historico Histórico de sorteos
     * @param maxNumero Número máximo de la lotería
     * @param cantidadNumeros Números por combinación
     * @param tipoLoteria Tipo de lotería
     * @return Resultado del ensemble con la mejor combinación
     */
    fun ejecutarEnsembleVoting(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        tipoLoteria: String = "PRIMITIVA"
    ): ResultadoEnsemble {
        cargarMemoria(tipoLoteria)

        val car = extraerCaracteristicas(historico, maxNumero, tipoLoteria)

        // Obtener pesos de estrategias (del aprendizaje o default)
        val pesosEstrategias = obtenerPesosEstrategias(tipoLoteria)

        // Recopilar votos de todas las estrategias
        val todosLosVotos = mutableListOf<VotoEstrategia>()

        // ESTRATEGIA 1: Algoritmo Genético
        todosLosVotos.addAll(
            obtenerVotosGenetico(historico, maxNumero, cantidadNumeros, tipoLoteria, pesosEstrategias)
        )

        // ESTRATEGIA 2: Alta Confianza
        todosLosVotos.addAll(
            obtenerVotosAltaConfianza(historico, maxNumero, cantidadNumeros, tipoLoteria, pesosEstrategias)
        )

        // ESTRATEGIA 3: Mix de Rachas
        todosLosVotos.addAll(
            obtenerVotosRachas(car, maxNumero, cantidadNumeros, pesosEstrategias)
        )

        // ESTRATEGIA 4: Equilibrio Estadístico
        todosLosVotos.addAll(
            obtenerVotosEquilibrio(car, historico.size, cantidadNumeros, maxNumero, pesosEstrategias)
        )

        // ESTRATEGIA 5: Ciclos
        todosLosVotos.addAll(
            obtenerVotosCiclos(car, maxNumero, pesosEstrategias)
        )

        // ESTRATEGIA 6: Correlaciones
        todosLosVotos.addAll(
            obtenerVotosCorrelaciones(car, maxNumero, pesosEstrategias)
        )

        // ESTRATEGIA 7: Frecuencia
        todosLosVotos.addAll(
            obtenerVotosFrecuencia(car, maxNumero, pesosEstrategias)
        )

        // ESTRATEGIA 8: Tendencia
        todosLosVotos.addAll(
            obtenerVotosTendencia(car, maxNumero, pesosEstrategias)
        )

        // Agregar votos por número
        val votacionPorNumero = agregarVotos(todosLosVotos, maxNumero)

        // Seleccionar la mejor combinación
        val combinacionGanadora = seleccionarCombinacionEnsemble(
            votacionPorNumero, cantidadNumeros, maxNumero, car.estadisticasPerfil
        )

        // Calcular confianza global
        val confianzaGlobal = calcularConfianzaEnsemble(combinacionGanadora, votacionPorNumero)

        // Determinar nivel de consenso
        val nivelConsenso = when {
            confianzaGlobal > 0.7 -> "🟢 Alto"
            confianzaGlobal > 0.4 -> "🟡 Medio"
            else -> "🔴 Bajo"
        }

        // Generar alternativas
        val alternativas = generarAlternativasEnsemble(votacionPorNumero, cantidadNumeros, combinacionGanadora)

        // Generar explicación
        val explicacion = generarExplicacionEnsemble(
            combinacionGanadora, votacionPorNumero, confianzaGlobal, nivelConsenso
        )

        return ResultadoEnsemble(
            combinacionGanadora = combinacionGanadora,
            confianzaGlobal = confianzaGlobal,
            votacionPorNumero = votacionPorNumero.sortedByDescending { it.votosTotal }.take(20),
            estrategiasUsadas = EstrategiaPrediccion.values().size,
            nivelConsenso = nivelConsenso,
            combinacionesAlternativas = alternativas,
            explicacion = explicacion,
            pesosEstrategias = pesosEstrategias
        )
    }

    /**
     * Obtiene los pesos de cada estrategia basados en rendimiento histórico.
     */
    private fun obtenerPesosEstrategias(tipoLoteria: String): Map<EstrategiaPrediccion, Double> {
        // Intentar obtener pesos aprendidos de la memoria
        val pesosAprendidos = memoria?.obtenerPesosEstrategias(tipoLoteria)

        return pesosAprendidos ?: mapOf(
            // Pesos por defecto (todos iguales inicialmente)
            EstrategiaPrediccion.GENETICO to 1.0,
            EstrategiaPrediccion.ALTA_CONFIANZA to 1.2,      // Ligeramente más peso
            EstrategiaPrediccion.RACHAS_MIX to 0.9,
            EstrategiaPrediccion.EQUILIBRIO to 1.1,
            EstrategiaPrediccion.CICLOS to 1.0,
            EstrategiaPrediccion.CORRELACIONES to 0.8,
            EstrategiaPrediccion.FRECUENCIA to 0.9,
            EstrategiaPrediccion.TENDENCIA to 1.0
        )
    }

    /**
     * Obtiene votos del algoritmo genético.
     */
    private fun obtenerVotosGenetico(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        tipoLoteria: String,
        pesos: Map<EstrategiaPrediccion, Double>
    ): List<VotoEstrategia> {
        val votos = mutableListOf<VotoEstrategia>()
        val peso = pesos[EstrategiaPrediccion.GENETICO] ?: 1.0

        try {
            val combinaciones = generarCombinacionesInteligentes(
                historico, maxNumero, cantidadNumeros, 3, tipoLoteria
            )

            // Contar frecuencia de cada número en las combinaciones
            val conteo = mutableMapOf<Int, Int>()
            combinaciones.forEach { comb ->
                comb.numeros.forEach { num ->
                    conteo[num] = (conteo[num] ?: 0) + 1
                }
            }

            // Convertir a votos
            conteo.entries.sortedByDescending { it.value }.forEachIndexed { idx, entry ->
                votos.add(VotoEstrategia(
                    estrategia = EstrategiaPrediccion.GENETICO,
                    numero = entry.key,
                    peso = peso * (entry.value.toDouble() / combinaciones.size),
                    posicion = idx + 1,
                    score = entry.value.toDouble()
                ))
            }
        } catch (e: Exception) {
            // Si falla, no añadir votos
        }

        return votos
    }

    /**
     * Obtiene votos del sistema de alta confianza.
     */
    private fun obtenerVotosAltaConfianza(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        tipoLoteria: String,
        pesos: Map<EstrategiaPrediccion, Double>
    ): List<VotoEstrategia> {
        val votos = mutableListOf<VotoEstrategia>()
        val peso = pesos[EstrategiaPrediccion.ALTA_CONFIANZA] ?: 1.0

        try {
            val prediccion = generarPrediccionAltaConfianza(historico, maxNumero, cantidadNumeros, tipoLoteria)

            // Votar por los números de alta confianza
            prediccion.numerosConAltoConsenso.forEachIndexed { idx, scoreNum ->
                votos.add(VotoEstrategia(
                    estrategia = EstrategiaPrediccion.ALTA_CONFIANZA,
                    numero = scoreNum.numero,
                    peso = peso * scoreNum.confianza,
                    posicion = idx + 1,
                    score = scoreNum.scoreTotal
                ))
            }

            // También votar por la combinación principal
            prediccion.combinacionPrincipal.forEachIndexed { idx, num ->
                if (votos.none { it.numero == num }) {
                    votos.add(VotoEstrategia(
                        estrategia = EstrategiaPrediccion.ALTA_CONFIANZA,
                        numero = num,
                        peso = peso * 0.8,
                        posicion = idx + 1,
                        score = 80.0
                    ))
                }
            }
        } catch (e: Exception) {
            // Si falla, no añadir votos
        }

        return votos
    }

    /**
     * Obtiene votos del sistema de rachas.
     */
    private fun obtenerVotosRachas(
        car: Caracteristicas,
        maxNumero: Int,
        cantidadNumeros: Int,
        pesos: Map<EstrategiaPrediccion, Double>
    ): List<VotoEstrategia> {
        val votos = mutableListOf<VotoEstrategia>()
        val peso = pesos[EstrategiaPrediccion.RACHAS_MIX] ?: 1.0

        val rachas = car.rachas
        if (rachas.isEmpty()) return votos

        // Votar por números calientes
        var posicion = 1
        rachas.values
            .filter { it.tipoRacha == TipoRacha.MUY_CALIENTE || it.tipoRacha == TipoRacha.CALIENTE }
            .sortedByDescending { it.intensidad }
            .take(cantidadNumeros)
            .forEach { racha ->
                votos.add(VotoEstrategia(
                    estrategia = EstrategiaPrediccion.RACHAS_MIX,
                    numero = racha.numero,
                    peso = peso * (0.8 + racha.intensidad * 0.2),
                    posicion = posicion++,
                    score = racha.intensidad * 100
                ))
            }

        // Votar por números muy fríos (debidos)
        rachas.values
            .filter { it.tipoRacha == TipoRacha.MUY_FRIO }
            .sortedByDescending { it.intensidad }
            .take(cantidadNumeros / 2)
            .forEach { racha ->
                votos.add(VotoEstrategia(
                    estrategia = EstrategiaPrediccion.RACHAS_MIX,
                    numero = racha.numero,
                    peso = peso * 0.7,
                    posicion = posicion++,
                    score = racha.intensidad * 70
                ))
            }

        return votos
    }

    /**
     * Obtiene votos por equilibrio estadístico.
     */
    private fun obtenerVotosEquilibrio(
        car: Caracteristicas,
        historicoSize: Int,
        cantidadNumeros: Int,
        maxNumero: Int,
        pesos: Map<EstrategiaPrediccion, Double>
    ): List<VotoEstrategia> {
        val votos = mutableListOf<VotoEstrategia>()
        val peso = pesos[EstrategiaPrediccion.EQUILIBRIO] ?: 1.0

        val frecuenciaEsperada = (historicoSize * cantidadNumeros).toDouble() / maxNumero

        // Calcular desviación de cada número
        val desviaciones = (1..maxNumero).map { num ->
            val frecReal = car.frecuencias[num] ?: 0
            val desviacion = (frecuenciaEsperada - frecReal) / frecuenciaEsperada.coerceAtLeast(1.0)
            num to desviacion
        }.sortedByDescending { it.second }

        // Votar por los más "debidos"
        desviaciones.take(cantidadNumeros * 2).forEachIndexed { idx, (num, desv) ->
            if (desv > 0) {  // Solo votar por números con déficit
                votos.add(VotoEstrategia(
                    estrategia = EstrategiaPrediccion.EQUILIBRIO,
                    numero = num,
                    peso = peso * (0.5 + desv.coerceIn(0.0, 0.5)),
                    posicion = idx + 1,
                    score = desv * 100
                ))
            }
        }

        return votos
    }

    /**
     * Obtiene votos por ciclos.
     */
    private fun obtenerVotosCiclos(
        car: Caracteristicas,
        maxNumero: Int,
        pesos: Map<EstrategiaPrediccion, Double>
    ): List<VotoEstrategia> {
        val votos = mutableListOf<VotoEstrategia>()
        val peso = pesos[EstrategiaPrediccion.CICLOS] ?: 1.0

        // Votar por números con alto score de ciclo (atrasados)
        car.scoreCiclo.entries
            .sortedByDescending { it.value }
            .take(15)
            .forEachIndexed { idx, (num, scoreCiclo) ->
                if (scoreCiclo > 0.8) {  // Solo si está al menos cerca de su ciclo
                    votos.add(VotoEstrategia(
                        estrategia = EstrategiaPrediccion.CICLOS,
                        numero = num,
                        peso = peso * (scoreCiclo / 3.0).coerceIn(0.3, 1.0),
                        posicion = idx + 1,
                        score = scoreCiclo * 33.33
                    ))
                }
            }

        // También votar por próximos por ciclo
        car.proximosPorCiclo.take(10).forEachIndexed { idx, num ->
            if (votos.none { it.numero == num }) {
                votos.add(VotoEstrategia(
                    estrategia = EstrategiaPrediccion.CICLOS,
                    numero = num,
                    peso = peso * 0.7,
                    posicion = idx + 10,
                    score = 70.0
                ))
            }
        }

        return votos
    }

    /**
     * Obtiene votos por correlaciones.
     */
    private fun obtenerVotosCorrelaciones(
        car: Caracteristicas,
        maxNumero: Int,
        pesos: Map<EstrategiaPrediccion, Double>
    ): List<VotoEstrategia> {
        val votos = mutableListOf<VotoEstrategia>()
        val peso = pesos[EstrategiaPrediccion.CORRELACIONES] ?: 1.0

        // Contar cuántos compañeros activos tiene cada número
        val scoreCompaneros = (1..maxNumero).map { num ->
            val companeros = car.companeros[num] ?: emptyList()
            val activos = companeros.count { comp ->
                (car.tendencia[comp] ?: 0) > (car.tendencia.values.average() * 0.8)
            }
            num to (activos.toDouble() / companeros.size.coerceAtLeast(1))
        }.sortedByDescending { it.second }

        scoreCompaneros.filter { it.second > 0.3 }.take(15).forEachIndexed { idx, (num, score) ->
            votos.add(VotoEstrategia(
                estrategia = EstrategiaPrediccion.CORRELACIONES,
                numero = num,
                peso = peso * score,
                posicion = idx + 1,
                score = score * 100
            ))
        }

        return votos
    }

    /**
     * Obtiene votos por frecuencia.
     */
    private fun obtenerVotosFrecuencia(
        car: Caracteristicas,
        maxNumero: Int,
        pesos: Map<EstrategiaPrediccion, Double>
    ): List<VotoEstrategia> {
        val votos = mutableListOf<VotoEstrategia>()
        val peso = pesos[EstrategiaPrediccion.FRECUENCIA] ?: 1.0
        val maxFrec = car.frecuencias.values.maxOrNull()?.toDouble() ?: 1.0

        car.frecuencias.entries
            .sortedByDescending { it.value }
            .take(15)
            .forEachIndexed { idx, (num, frec) ->
                votos.add(VotoEstrategia(
                    estrategia = EstrategiaPrediccion.FRECUENCIA,
                    numero = num,
                    peso = peso * (frec / maxFrec),
                    posicion = idx + 1,
                    score = (frec / maxFrec) * 100
                ))
            }

        return votos
    }

    /**
     * Obtiene votos por tendencia reciente.
     */
    private fun obtenerVotosTendencia(
        car: Caracteristicas,
        maxNumero: Int,
        pesos: Map<EstrategiaPrediccion, Double>
    ): List<VotoEstrategia> {
        val votos = mutableListOf<VotoEstrategia>()
        val peso = pesos[EstrategiaPrediccion.TENDENCIA] ?: 1.0
        val maxTend = car.tendencia.values.maxOrNull()?.toDouble() ?: 1.0

        car.tendencia.entries
            .sortedByDescending { it.value }
            .take(15)
            .forEachIndexed { idx, (num, tend) ->
                votos.add(VotoEstrategia(
                    estrategia = EstrategiaPrediccion.TENDENCIA,
                    numero = num,
                    peso = peso * (tend / maxTend),
                    posicion = idx + 1,
                    score = (tend / maxTend) * 100
                ))
            }

        return votos
    }

    /**
     * Agrega todos los votos por número.
     */
    private fun agregarVotos(votos: List<VotoEstrategia>, maxNumero: Int): List<ResultadoVotacion> {
        return (1..maxNumero).map { num ->
            val votosNumero = votos.filter { it.numero == num }

            val votosTotal = votosNumero.sumOf { it.peso }
            val numEstrategias = votosNumero.map { it.estrategia }.distinct().size
            val posicionPromedio = if (votosNumero.isNotEmpty()) {
                votosNumero.map { it.posicion }.average()
            } else Double.MAX_VALUE

            // Consenso: alto si muchas estrategias votan y con posiciones similares
            val consenso = if (numEstrategias > 0) {
                val factorEstrategias = numEstrategias.toDouble() / EstrategiaPrediccion.values().size
                val factorPosicion = if (posicionPromedio < 10) 1.0 else 0.5
                (factorEstrategias * 0.6 + factorPosicion * 0.4).coerceIn(0.0, 1.0)
            } else 0.0

            ResultadoVotacion(
                numero = num,
                votosTotal = votosTotal,
                numEstrategias = numEstrategias,
                posicionPromedio = posicionPromedio,
                consenso = consenso,
                votos = votosNumero
            )
        }.filter { it.votosTotal > 0 }
    }

    /**
     * Selecciona la mejor combinación basada en la votación.
     */
    private fun seleccionarCombinacionEnsemble(
        votacion: List<ResultadoVotacion>,
        cantidad: Int,
        maxNumero: Int,
        stats: EstadisticasPerfil?
    ): List<Int> {
        val ordenados = votacion.sortedByDescending { it.votosTotal * it.consenso }
        val seleccionados = mutableListOf<Int>()
        val mitad = maxNumero / 2

        for (resultado in ordenados) {
            if (seleccionados.size >= cantidad) break

            val num = resultado.numero
            val paresActuales = seleccionados.count { it % 2 == 0 }
            val bajosActuales = seleccionados.count { it <= mitad }

            // Verificar balance antes de añadir
            val seraDesequilibrado = when {
                seleccionados.size >= cantidad - 1 && paresActuales == 0 && num % 2 != 0 -> true
                seleccionados.size >= cantidad - 1 && bajosActuales == 0 && num > mitad -> true
                seleccionados.size >= cantidad - 1 && paresActuales == seleccionados.size && num % 2 == 0 -> true
                seleccionados.size >= cantidad - 1 && bajosActuales == seleccionados.size && num <= mitad -> true
                else -> false
            }

            if (!seraDesequilibrado) {
                seleccionados.add(num)
            }
        }

        // Completar si faltan
        while (seleccionados.size < cantidad) {
            val disponible = ordenados
                .map { it.numero }
                .filter { it !in seleccionados }
                .firstOrNull() ?: (1..maxNumero).filter { it !in seleccionados }.randomOrNull() ?: break
            seleccionados.add(disponible)
        }

        return seleccionados.sorted()
    }

    /**
     * Calcula la confianza global del ensemble.
     */
    private fun calcularConfianzaEnsemble(
        combinacion: List<Int>,
        votacion: List<ResultadoVotacion>
    ): Double {
        val consensos = combinacion.mapNotNull { num ->
            votacion.find { it.numero == num }?.consenso
        }

        return if (consensos.isNotEmpty()) {
            consensos.average()
        } else 0.0
    }

    /**
     * Genera combinaciones alternativas.
     */
    private fun generarAlternativasEnsemble(
        votacion: List<ResultadoVotacion>,
        cantidad: Int,
        principal: List<Int>
    ): List<List<Int>> {
        val ordenados = votacion.sortedByDescending { it.votosTotal }
        val alternativas = mutableListOf<List<Int>>()

        // Alternativa 1: Siguientes en el ranking
        val alt1 = ordenados
            .map { it.numero }
            .filter { it !in principal }
            .take(cantidad)
            .sorted()
        if (alt1.size == cantidad) alternativas.add(alt1)

        // Alternativa 2: Mayor consenso (aunque menos votos)
        val porConsenso = votacion.sortedByDescending { it.consenso }
        val alt2 = porConsenso
            .map { it.numero }
            .filter { it !in principal && it !in alt1 }
            .take(cantidad)
            .sorted()
        if (alt2.size == cantidad) alternativas.add(alt2)

        // Alternativa 3: Mix principal + siguientes
        val alt3 = (principal.take(cantidad / 2) +
            ordenados.map { it.numero }.filter { it !in principal }.take(cantidad / 2 + 1))
            .take(cantidad)
            .sorted()
        if (alt3.size == cantidad && alt3.toSet() != principal.toSet()) {
            alternativas.add(alt3)
        }

        return alternativas.distinctBy { it.toSet() }
    }

    /**
     * Genera la explicación del ensemble.
     */
    private fun generarExplicacionEnsemble(
        combinacion: List<Int>,
        votacion: List<ResultadoVotacion>,
        confianza: Double,
        nivelConsenso: String
    ): String {
        val sb = StringBuilder()
        sb.append("🗳️ ENSEMBLE VOTING - Consenso de 8 Estrategias\n\n")
        sb.append("$nivelConsenso Confianza: ${String.format("%.0f", confianza * 100)}%\n\n")
        sb.append("🔢 Combinación ganadora:\n")

        for (num in combinacion) {
            val resultado = votacion.find { it.numero == num }
            if (resultado != null) {
                val estrategiasStr = resultado.votos
                    .map { it.estrategia.name.take(3) }
                    .distinct()
                    .joinToString(",")
                sb.append("  • $num: ${String.format("%.1f", resultado.votosTotal)} votos ")
                sb.append("(${resultado.numEstrategias} estrategias: $estrategiasStr)\n")
            }
        }

        // Top 5 por votos
        val top5 = votacion.sortedByDescending { it.votosTotal }.take(5)
        sb.append("\n📊 Top 5 por votos: ${top5.map { "${it.numero}(${String.format("%.1f", it.votosTotal)})" }.joinToString(", ")}")

        // Top 5 por consenso
        val top5Consenso = votacion.sortedByDescending { it.consenso }.take(5)
        sb.append("\n🤝 Top 5 por consenso: ${top5Consenso.map { it.numero }.joinToString(", ")}")

        return sb.toString()
    }

    /**
     * MEJORA 9: Genera combinación usando ensemble (método simplificado).
     */
    fun generarCombinacionEnsemble(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        tipoLoteria: String = "PRIMITIVA"
    ): CombinacionSugerida {
        val resultado = ejecutarEnsembleVoting(historico, maxNumero, cantidadNumeros, tipoLoteria)

        return CombinacionSugerida(
            numeros = resultado.combinacionGanadora,
            probabilidadRelativa = (resultado.confianzaGlobal * 100).roundTo(1),
            explicacion = "🗳️ Ensemble ${resultado.nivelConsenso} | ${resultado.estrategiasUsadas} estrategias | Conf:${String.format("%.0f", resultado.confianzaGlobal * 100)}%"
        )
    }

    /**
     * MEJORA 9: Registrar resultado para meta-aprendizaje.
     *
     * Después de un sorteo real, actualiza los pesos de las estrategias
     * según cuáles acertaron más.
     */
    fun registrarResultadoEnsemble(
        resultadoReal: List<Int>,
        prediccionEnsemble: ResultadoEnsemble,
        tipoLoteria: String
    ) {
        if (memoria == null) return

        // Para cada estrategia, calcular cuántos aciertos tuvo
        val aciertosEstrategia = mutableMapOf<EstrategiaPrediccion, Int>()

        for (estrategia in EstrategiaPrediccion.values()) {
            val votosEstrategia = prediccionEnsemble.votacionPorNumero
                .flatMap { it.votos }
                .filter { it.estrategia == estrategia }
                .sortedByDescending { it.peso }
                .take(6)  // Top 6 de cada estrategia
                .map { it.numero }

            val aciertos = votosEstrategia.count { it in resultadoReal }
            aciertosEstrategia[estrategia] = aciertos
        }

        // Guardar para ajustar pesos en el futuro
        memoria.registrarRendimientoEstrategias(aciertosEstrategia, tipoLoteria)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 10: SISTEMA INTELIGENTE DE REINTEGROS/ESTRELLAS/CLAVES
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Score de un número complementario (reintegro, estrella, clave).
     */
    data class ScoreComplementario(
        val numero: Int,
        val scoreTotal: Double,
        val frecuencia: Int,
        val gap: Int,
        val tendenciaReciente: Int,
        val ciclo: Double,
        val scoreCiclo: Double,
        val racha: TipoRacha
    )

    /**
     * Predicción inteligente de reintegros para Primitiva/Bonoloto.
     *
     * Aplica las mismas técnicas de IA que para los números principales:
     * - Frecuencias
     * - Ciclos (cada cuántos sorteos sale cada reintegro)
     * - Tendencia reciente
     * - Rachas calientes/frías
     *
     * @param historico Histórico de sorteos
     * @param numReintegros Cuántos reintegros devolver (ordenados por score)
     * @return Lista de reintegros sugeridos con sus scores
     */
    fun predecirReintegros(
        historico: List<ResultadoPrimitiva>,
        numReintegros: Int = 5
    ): List<ScoreComplementario> {
        if (historico.isEmpty()) {
            return (0..9).shuffled().take(numReintegros).map { num ->
                ScoreComplementario(num, 50.0, 0, 0, 0, 10.0, 0.5, TipoRacha.NORMAL)
            }
        }

        val reintegros = historico.map { it.reintegro }
        return calcularScoresComplementarios(reintegros, 0..9, historico.size, numReintegros)
    }

    /**
     * Predicción inteligente de estrellas para Euromillones.
     *
     * @param historico Histórico de Euromillones
     * @param numEstrellas Cuántas estrellas devolver
     * @return Lista de estrellas sugeridas con sus scores
     */
    fun predecirEstrellas(
        historico: List<ResultadoEuromillones>,
        numEstrellas: Int = 4
    ): List<ScoreComplementario> {
        if (historico.isEmpty()) {
            return (1..12).shuffled().take(numEstrellas).map { num ->
                ScoreComplementario(num, 50.0, 0, 0, 0, 6.0, 0.5, TipoRacha.NORMAL)
            }
        }

        // Aplanar todas las estrellas en una lista
        val estrellas = historico.flatMap { it.estrellas }
        // Cada sorteo tiene 2 estrellas, ajustar para el cálculo de frecuencia esperada
        val totalSorteos = historico.size * 2  // 2 estrellas por sorteo
        return calcularScoresComplementarios(estrellas, 1..12, totalSorteos, numEstrellas, 2)
    }

    /**
     * Predicción inteligente de número clave para El Gordo de la Primitiva.
     *
     * @param historico Histórico del Gordo
     * @param numClaves Cuántas claves devolver
     * @return Lista de claves sugeridas con sus scores
     */
    fun predecirNumeroClave(
        historico: List<ResultadoGordoPrimitiva>,
        numClaves: Int = 5
    ): List<ScoreComplementario> {
        if (historico.isEmpty()) {
            return (0..9).shuffled().take(numClaves).map { num ->
                ScoreComplementario(num, 50.0, 0, 0, 0, 10.0, 0.5, TipoRacha.NORMAL)
            }
        }

        val claves = historico.map { it.numeroClave }
        return calcularScoresComplementarios(claves, 0..9, historico.size, numClaves)
    }

    /**
     * Cálculo genérico de scores para números complementarios.
     *
     * Aplica análisis de:
     * - Frecuencia histórica
     * - Gap (sorteos desde última aparición)
     * - Tendencia reciente (últimos 20 sorteos)
     * - Ciclo promedio de aparición
     * - Rachas calientes/frías
     *
     * @param numeros Lista de todos los números que han salido (en orden, más reciente primero)
     * @param rango Rango de números posibles (ej: 0..9 para reintegros)
     * @param totalSorteos Total de sorteos en el histórico
     * @param cantidad Cuántos devolver
     * @param multiplo Cuántos números de este tipo salen por sorteo (1 para reintegro, 2 para estrellas)
     */
    private fun calcularScoresComplementarios(
        numeros: List<Int>,
        rango: IntRange,
        totalSorteos: Int,
        cantidad: Int,
        multiplo: Int = 1
    ): List<ScoreComplementario> {
        val totalNumeros = rango.count()

        // Frecuencia de cada número
        val frecuencias = rango.associateWith { num ->
            numeros.count { it == num }
        }

        // Gap (sorteos desde última aparición) - para estrellas, cada 2 elementos es un sorteo
        val gaps = rango.associateWith { num ->
            val idx = numeros.indexOf(num)
            if (idx < 0) totalSorteos else idx / multiplo
        }

        // Tendencia reciente (últimos 20 sorteos)
        val ventanaReciente = 20 * multiplo
        val numerosRecientes = numeros.take(ventanaReciente)
        val tendenciaReciente = rango.associateWith { num ->
            numerosRecientes.count { it == num }
        }

        // Calcular ciclos (intervalo promedio entre apariciones)
        val ciclos = calcularCiclosComplementarios(numeros, rango, multiplo)

        // Calcular score de ciclo (qué tan "debido" está)
        val scoreCiclos = rango.associateWith { num ->
            val ciclo = ciclos[num] ?: 10.0
            val gap = gaps[num] ?: 0
            if (ciclo <= 0) 0.5 else (gap.toDouble() / ciclo).coerceIn(0.0, 3.0)
        }

        // Detectar rachas
        val rachas = detectarRachasComplementarios(numeros, rango, ventanaReciente / multiplo, multiplo, totalNumeros)

        // Frecuencia esperada por número
        val frecuenciaEsperada = (totalSorteos * multiplo).toDouble() / totalNumeros

        // Calcular scores finales
        val scores = rango.map { num ->
            val frec = frecuencias[num] ?: 0
            val gap = gaps[num] ?: 0
            val tend = tendenciaReciente[num] ?: 0
            val ciclo = ciclos[num] ?: 10.0
            val scoreCiclo = scoreCiclos[num] ?: 0.5
            val racha = rachas[num] ?: TipoRacha.NORMAL

            // Score combinado ponderado
            val frecMax = frecuencias.values.maxOrNull() ?: 1
            val tendMax = tendenciaReciente.values.maxOrNull() ?: 1

            val scoreFrec = if (frecMax > 0) (frec.toDouble() / frecMax) * 25 else 12.5
            val scoreTend = if (tendMax > 0) (tend.toDouble() / tendMax) * 25 else 12.5
            val scoreGap = (scoreCiclo / 3.0) * 25  // Normalizado 0-25
            val scoreRacha = when (racha) {
                TipoRacha.MUY_CALIENTE -> 22.0
                TipoRacha.CALIENTE -> 20.0
                TipoRacha.MUY_FRIO -> 18.0  // Debido
                TipoRacha.FRIO -> 15.0
                TipoRacha.NORMAL -> 12.5
            }

            val scoreTotal = scoreFrec + scoreTend + scoreGap + scoreRacha

            ScoreComplementario(
                numero = num,
                scoreTotal = scoreTotal,
                frecuencia = frec,
                gap = gap,
                tendenciaReciente = tend,
                ciclo = ciclo,
                scoreCiclo = scoreCiclo,
                racha = racha
            )
        }

        return scores.sortedByDescending { it.scoreTotal }.take(cantidad)
    }

    /**
     * Calcula ciclos de aparición para números complementarios.
     */
    private fun calcularCiclosComplementarios(
        numeros: List<Int>,
        rango: IntRange,
        multiplo: Int
    ): Map<Int, Double> {
        return rango.associateWith { numero ->
            // Encontrar todas las posiciones donde aparece (ajustando por multiplo)
            val posiciones = mutableListOf<Int>()
            var sorteoActual = 0

            for (i in numeros.indices step multiplo) {
                val numerosEnSorteo = numeros.subList(i, minOf(i + multiplo, numeros.size))
                if (numero in numerosEnSorteo) {
                    posiciones.add(sorteoActual)
                }
                sorteoActual++
            }

            if (posiciones.size < 2) {
                (numeros.size / multiplo).toDouble()  // Estimar como tamaño del histórico
            } else {
                val gaps = posiciones.zipWithNext { a, b -> b - a }
                gaps.average()
            }
        }
    }

    /**
     * Detecta rachas para números complementarios.
     */
    private fun detectarRachasComplementarios(
        numeros: List<Int>,
        rango: IntRange,
        ventana: Int,
        multiplo: Int,
        totalNumeros: Int
    ): Map<Int, TipoRacha> {
        val numerosRecientes = numeros.take(ventana * multiplo)
        val frecuenciaEsperada = (ventana * multiplo).toDouble() / totalNumeros

        return rango.associateWith { num ->
            val apariciones = numerosRecientes.count { it == num }
            val desviacion = if (frecuenciaEsperada > 0) {
                (apariciones - frecuenciaEsperada) / frecuenciaEsperada
            } else 0.0

            when {
                desviacion > 0.50 -> TipoRacha.MUY_CALIENTE
                desviacion > 0.25 -> TipoRacha.CALIENTE
                desviacion < -0.50 -> TipoRacha.MUY_FRIO
                desviacion < -0.25 -> TipoRacha.FRIO
                else -> TipoRacha.NORMAL
            }
        }
    }

    /**
     * Genera predicción de reintegro con explicación.
     */
    fun generarPrediccionReintegroConExplicacion(
        historico: List<ResultadoPrimitiva>
    ): Pair<Int, String> {
        val scores = predecirReintegros(historico, 3)
        val mejor = scores.first()

        val explicacion = buildString {
            append("🎲 Reintegro: ${mejor.numero}\n")
            append("  • Frecuencia: ${mejor.frecuencia} apariciones\n")
            append("  • Gap: ${mejor.gap} sorteos sin salir\n")
            append("  • Ciclo: cada ${String.format("%.1f", mejor.ciclo)} sorteos\n")
            append("  • Estado: ${rachaToEmoji(mejor.racha)}\n")
            append("  • Score: ${String.format("%.1f", mejor.scoreTotal)}/100")
        }

        return Pair(mejor.numero, explicacion)
    }

    /**
     * Genera predicción de estrellas con explicación.
     */
    fun generarPrediccionEstrellasConExplicacion(
        historico: List<ResultadoEuromillones>
    ): Pair<List<Int>, String> {
        val scores = predecirEstrellas(historico, 4)
        val top2 = scores.take(2)

        val explicacion = buildString {
            append("⭐ Estrellas: ${top2.map { it.numero }.joinToString(", ")}\n\n")
            for (estrella in top2) {
                append("  Estrella ${estrella.numero}:\n")
                append("    • Frecuencia: ${estrella.frecuencia}\n")
                append("    • Gap: ${estrella.gap} sorteos\n")
                append("    • ${rachaToEmoji(estrella.racha)}\n")
                append("    • Score: ${String.format("%.1f", estrella.scoreTotal)}/100\n")
            }
        }

        return Pair(top2.map { it.numero }, explicacion)
    }

    /**
     * Genera predicción de número clave con explicación.
     */
    fun generarPrediccionClaveConExplicacion(
        historico: List<ResultadoGordoPrimitiva>
    ): Pair<Int, String> {
        val scores = predecirNumeroClave(historico, 3)
        val mejor = scores.first()

        val explicacion = buildString {
            append("🔑 Número Clave: ${mejor.numero}\n")
            append("  • Frecuencia: ${mejor.frecuencia} apariciones\n")
            append("  • Gap: ${mejor.gap} sorteos sin salir\n")
            append("  • Ciclo: cada ${String.format("%.1f", mejor.ciclo)} sorteos\n")
            append("  • Estado: ${rachaToEmoji(mejor.racha)}\n")
            append("  • Score: ${String.format("%.1f", mejor.scoreTotal)}/100")
        }

        return Pair(mejor.numero, explicacion)
    }

    private fun rachaToEmoji(racha: TipoRacha): String = when (racha) {
        TipoRacha.MUY_CALIENTE -> "🔥 Muy caliente"
        TipoRacha.CALIENTE -> "♨️ Caliente"
        TipoRacha.NORMAL -> "➖ Normal"
        TipoRacha.FRIO -> "❄️ Frío"
        TipoRacha.MUY_FRIO -> "🥶 Muy frío (debido)"
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 11: ANÁLISIS DE PATRONES TEMPORALES
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Información de patrón temporal.
     */
    data class PatronTemporal(
        val numero: Int,
        val frecuenciaDia: Map<String, Int>,      // Frecuencia por día de la semana
        val frecuenciaMes: Map<Int, Int>,         // Frecuencia por mes
        val mejorDia: String?,                    // Día donde más sale
        val peorDia: String?,                     // Día donde menos sale
        val mejorMes: Int?,                       // Mes donde más sale
        val tendenciaDiaSemana: Double            // Score para el día actual
    )

    /**
     * Analiza patrones temporales de los números.
     *
     * Detecta si ciertos números tienden a salir más en ciertos días de la semana
     * o meses del año. Cada lotería tiene sus propios días de sorteo:
     *
     * - PRIMITIVA: Lunes, Jueves, Sábado
     * - BONOLOTO: Lunes a Sábado
     * - EUROMILLONES: Martes, Viernes
     * - GORDO_PRIMITIVA: Domingo
     * - LOTERIA_NACIONAL: Jueves, Sábado
     * - NAVIDAD: 22 de Diciembre
     * - NIÑO: 6 de Enero
     *
     * @param historico Histórico de sorteos
     * @param maxNumero Número máximo
     * @param diaSemanaActual Día de la semana actual (1=Lunes, 7=Domingo)
     * @param mesActual Mes actual (1-12)
     * @return Mapa de patrones por número
     */
    fun analizarPatronesTemporales(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        diaSemanaActual: Int,
        mesActual: Int
    ): Map<Int, PatronTemporal> {
        if (historico.isEmpty()) return emptyMap()

        val diasSemana = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

        // Parsear fechas y agrupar por día/mes
        val sorteosPorDia = mutableMapOf<String, MutableList<ResultadoPrimitiva>>()
        val sorteosPorMes = mutableMapOf<Int, MutableList<ResultadoPrimitiva>>()

        historico.forEach { sorteo ->
            try {
                val fecha = java.time.LocalDate.parse(sorteo.fecha)
                val diaSemana = diasSemana[fecha.dayOfWeek.value - 1]
                val mes = fecha.monthValue

                sorteosPorDia.getOrPut(diaSemana) { mutableListOf() }.add(sorteo)
                sorteosPorMes.getOrPut(mes) { mutableListOf() }.add(sorteo)
            } catch (e: Exception) {
                // Fecha inválida, ignorar
            }
        }

        return (1..maxNumero).associateWith { num ->
            // Frecuencia por día de la semana
            val frecDia = diasSemana.associateWith { dia ->
                sorteosPorDia[dia]?.count { num in it.numeros } ?: 0
            }

            // Frecuencia por mes
            val frecMes = (1..12).associateWith { mes ->
                sorteosPorMes[mes]?.count { num in it.numeros } ?: 0
            }

            // Mejor y peor día
            val diasConSorteos = frecDia.filter { sorteosPorDia[it.key]?.isNotEmpty() == true }
            val mejorDia = diasConSorteos.maxByOrNull { it.value }?.key
            val peorDia = diasConSorteos.minByOrNull { it.value }?.key

            // Mejor mes
            val mesesConSorteos = frecMes.filter { sorteosPorMes[it.key]?.isNotEmpty() == true }
            val mejorMes = mesesConSorteos.maxByOrNull { it.value }?.key

            // Score para el día actual
            val diaActualNombre = diasSemana.getOrNull(diaSemanaActual - 1) ?: "Lun"
            val frecDiaActual = frecDia[diaActualNombre] ?: 0
            val sorteosDiaActual = sorteosPorDia[diaActualNombre]?.size ?: 1
            val frecuenciaEsperada = (sorteosDiaActual * 6.0) / maxNumero
            val tendenciaDia = if (frecuenciaEsperada > 0) {
                frecDiaActual / frecuenciaEsperada
            } else 1.0

            PatronTemporal(
                numero = num,
                frecuenciaDia = frecDia,
                frecuenciaMes = frecMes,
                mejorDia = mejorDia,
                peorDia = peorDia,
                mejorMes = mejorMes,
                tendenciaDiaSemana = tendenciaDia
            )
        }
    }

    /**
     * Obtiene los mejores números para el día actual.
     *
     * @param historico Histórico de sorteos
     * @param maxNumero Número máximo
     * @param tipoLoteria Tipo de lotería (para determinar días de sorteo)
     * @param cantidad Cuántos números devolver
     * @return Lista de números que históricamente salen más en este día
     */
    fun obtenerMejoresNumerosParaHoy(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        tipoLoteria: String,
        cantidad: Int = 10
    ): List<Pair<Int, Double>> {
        val hoy = java.time.LocalDate.now()
        val diaSemana = hoy.dayOfWeek.value  // 1=Lunes, 7=Domingo
        val mes = hoy.monthValue

        val patrones = analizarPatronesTemporales(historico, maxNumero, diaSemana, mes)

        return patrones.values
            .sortedByDescending { it.tendenciaDiaSemana }
            .take(cantidad)
            .map { Pair(it.numero, it.tendenciaDiaSemana) }
    }

    /**
     * Obtiene los mejores números para un día específico de la semana.
     */
    fun obtenerMejoresNumerosParaDia(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        diaSemana: Int,  // 1=Lunes, 7=Domingo
        cantidad: Int = 10
    ): List<Pair<Int, Double>> {
        val patrones = analizarPatronesTemporales(historico, maxNumero, diaSemana, 1)

        return patrones.values
            .sortedByDescending { it.tendenciaDiaSemana }
            .take(cantidad)
            .map { Pair(it.numero, it.tendenciaDiaSemana) }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 12: DETECTOR DE COMBINACIONES "RARAS"
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Análisis de rareza de una combinación.
     */
    data class AnalisisRareza(
        val esRara: Boolean,
        val scoreRareza: Double,           // 0 = muy común, 100 = muy rara
        val alertas: List<String>,
        val sugerenciasCorreccion: List<Int>?
    )

    /**
     * Analiza si una combinación es estadísticamente "rara" o improbable.
     *
     * Detecta patrones que históricamente casi nunca ocurren:
     * - Todos pares o todos impares
     * - Todos altos o todos bajos
     * - Números consecutivos (1,2,3,4,5,6)
     * - Números en una sola decena
     * - Patrones aritméticos evidentes
     * - Suma muy alejada de la media
     *
     * @param combinacion Combinación a analizar
     * @param maxNumero Número máximo de la lotería
     * @param estadisticas Estadísticas del perfil (opcional)
     * @return Análisis de rareza
     */
    fun analizarRarezaCombinacion(
        combinacion: List<Int>,
        maxNumero: Int,
        estadisticas: EstadisticasPerfil? = null
    ): AnalisisRareza {
        val alertas = mutableListOf<String>()
        var scoreRareza = 0.0

        val nums = combinacion.sorted()
        val mitad = maxNumero / 2

        // 1. TODOS PARES O TODOS IMPARES
        val pares = nums.count { it % 2 == 0 }
        if (pares == 0) {
            alertas.add("⚠️ Todos impares - Muy raro (<2% histórico)")
            scoreRareza += 30
        } else if (pares == nums.size) {
            alertas.add("⚠️ Todos pares - Muy raro (<2% histórico)")
            scoreRareza += 30
        }

        // 2. TODOS ALTOS O TODOS BAJOS
        val bajos = nums.count { it <= mitad }
        if (bajos == 0) {
            alertas.add("⚠️ Todos números altos (>${mitad}) - Raro (<3%)")
            scoreRareza += 25
        } else if (bajos == nums.size) {
            alertas.add("⚠️ Todos números bajos (≤${mitad}) - Raro (<3%)")
            scoreRareza += 25
        }

        // 3. NÚMEROS CONSECUTIVOS
        val esConsecutiva = (0 until nums.size - 1).all { i ->
            nums[i + 1] - nums[i] == 1
        }
        if (esConsecutiva) {
            alertas.add("⚠️ Secuencia consecutiva - Extremadamente raro (<0.01%)")
            scoreRareza += 50
        }

        // 4. TODOS EN UNA DECENA
        val decenas = nums.map { it / 10 }.toSet()
        if (decenas.size == 1) {
            alertas.add("⚠️ Todos en la misma decena - Muy raro (<1%)")
            scoreRareza += 35
        } else if (decenas.size == 2 && nums.size >= 5) {
            alertas.add("⚠️ Solo 2 decenas cubiertas - Poco común (<5%)")
            scoreRareza += 15
        }

        // 5. PROGRESIÓN ARITMÉTICA
        if (nums.size >= 4) {
            val diffs = nums.zipWithNext { a, b -> b - a }
            if (diffs.toSet().size == 1) {
                alertas.add("⚠️ Progresión aritmética (${diffs.first()} entre números) - Muy raro")
                scoreRareza += 40
            }
        }

        // 6. SUMA MUY ALEJADA DE LA MEDIA
        val suma = nums.sum()
        val sumaEsperadaMin = (1..nums.size).sum() + (nums.size * 5)  // Aproximado
        val sumaEsperadaMax = ((maxNumero - nums.size + 1)..maxNumero).sum() - (nums.size * 5)
        val sumaMedia = (sumaEsperadaMin + sumaEsperadaMax) / 2

        if (estadisticas != null) {
            val desviacionSuma = abs(suma - estadisticas.sumaMedia) /
                                 (estadisticas.sumaDesviacion.coerceAtLeast(1.0))
            if (desviacionSuma > 3) {
                alertas.add("⚠️ Suma ($suma) muy alejada de la media (${estadisticas.sumaMedia.toInt()})")
                scoreRareza += (desviacionSuma * 10).coerceAtMost(30.0)
            }
        }

        // 7. NÚMEROS MUY CERCANOS ENTRE SÍ
        val gaps = nums.zipWithNext { a, b -> b - a }
        val gapMin = gaps.minOrNull() ?: 1
        val gapMax = gaps.maxOrNull() ?: 1
        if (gapMin <= 1 && gaps.count { it <= 1 } >= 3) {
            alertas.add("⚠️ Demasiados números consecutivos o cercanos")
            scoreRareza += 20
        }

        // 8. SPREAD MUY BAJO O MUY ALTO
        val spread = nums.last() - nums.first()
        val spreadEsperado = maxNumero * 0.7  // Spread típico ~70% del rango
        if (spread < maxNumero * 0.3) {
            alertas.add("⚠️ Números muy concentrados (spread: $spread)")
            scoreRareza += 20
        }

        // Generar sugerencias si es rara
        val sugerencias = if (scoreRareza >= 30) {
            generarSugerenciasCorreccion(nums, maxNumero)
        } else null

        return AnalisisRareza(
            esRara = scoreRareza >= 30,
            scoreRareza = scoreRareza.coerceAtMost(100.0),
            alertas = alertas,
            sugerenciasCorreccion = sugerencias
        )
    }

    /**
     * Genera sugerencias de números alternativos para corregir una combinación rara.
     */
    private fun generarSugerenciasCorreccion(
        combinacionActual: List<Int>,
        maxNumero: Int
    ): List<Int> {
        val nums = combinacionActual.toMutableSet()
        val mitad = maxNumero / 2
        val sugerencias = mutableListOf<Int>()

        // Sugerir un número par si todos son impares
        val pares = nums.count { it % 2 == 0 }
        if (pares == 0) {
            val parSugerido = (2..maxNumero step 2).filter { it !in nums }.randomOrNull()
            if (parSugerido != null) sugerencias.add(parSugerido)
        } else if (pares == nums.size) {
            val imparSugerido = (1..maxNumero step 2).filter { it !in nums }.randomOrNull()
            if (imparSugerido != null) sugerencias.add(imparSugerido)
        }

        // Sugerir un número bajo si todos son altos
        val bajos = nums.count { it <= mitad }
        if (bajos == 0) {
            val bajoSugerido = (1..mitad).filter { it !in nums }.randomOrNull()
            if (bajoSugerido != null) sugerencias.add(bajoSugerido)
        } else if (bajos == nums.size) {
            val altoSugerido = ((mitad + 1)..maxNumero).filter { it !in nums }.randomOrNull()
            if (altoSugerido != null) sugerencias.add(altoSugerido)
        }

        // Sugerir número de otra decena
        val decenas = nums.map { it / 10 }.toSet()
        val decenasFaltantes = (0..(maxNumero / 10)).filter { it !in decenas }
        if (decenasFaltantes.isNotEmpty()) {
            val decenaSugerida = decenasFaltantes.random()
            val numSugerido = ((decenaSugerida * 10).coerceAtLeast(1)..(decenaSugerida * 10 + 9).coerceAtMost(maxNumero))
                .filter { it !in nums }
                .randomOrNull()
            if (numSugerido != null) sugerencias.add(numSugerido)
        }

        return sugerencias.distinct().take(3)
    }

    /**
     * Valida y corrige una combinación si es necesario.
     *
     * @return Combinación corregida o la original si no era rara
     */
    fun validarYCorregirCombinacion(
        combinacion: List<Int>,
        maxNumero: Int,
        estadisticas: EstadisticasPerfil? = null
    ): List<Int> {
        val analisis = analizarRarezaCombinacion(combinacion, maxNumero, estadisticas)

        if (!analisis.esRara || analisis.sugerenciasCorreccion.isNullOrEmpty()) {
            return combinacion
        }

        // Reemplazar el número con peor balance
        val nums = combinacion.toMutableList()
        val mitad = maxNumero / 2
        val pares = nums.count { it % 2 == 0 }
        val bajos = nums.count { it <= mitad }

        // Encontrar el número a reemplazar
        val numReemplazar = when {
            pares == 0 -> nums.filter { it % 2 != 0 }.random()
            pares == nums.size -> nums.filter { it % 2 == 0 }.random()
            bajos == 0 -> nums.filter { it > mitad }.random()
            bajos == nums.size -> nums.filter { it <= mitad }.random()
            else -> nums.random()
        }

        val nuevoNum = analisis.sugerenciasCorreccion.first()
        nums.remove(numReemplazar)
        nums.add(nuevoNum)

        return nums.sorted()
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 11B: PREDICCIÓN DE COMPLEMENTARIOS POR DÍA DE SORTEO
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Días de sorteo por tipo de lotería.
     */
    private val diasSorteoPorLoteria = mapOf(
        "PRIMITIVA" to listOf(1, 4, 6),      // Lunes, Jueves, Sábado
        "BONOLOTO" to listOf(1, 2, 3, 4, 5, 6), // Lunes a Sábado
        "EUROMILLONES" to listOf(2, 5),      // Martes, Viernes
        "GORDO_PRIMITIVA" to listOf(7),      // Domingo
        "LOTERIA_NACIONAL" to listOf(4, 6),  // Jueves, Sábado
        "NAVIDAD" to listOf(-1),             // Especial (22 Dic)
        "NINO" to listOf(-1)                 // Especial (6 Ene)
    )

    private val nombresDias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    /**
     * Obtiene el próximo día de sorteo para una lotería.
     *
     * @param tipoLoteria Tipo de lotería
     * @return Par de (díaSemana 1-7, nombreDía)
     */
    fun obtenerProximoDiaSorteo(tipoLoteria: String): Pair<Int, String> {
        val diasSorteo = diasSorteoPorLoteria[tipoLoteria] ?: listOf(6) // Por defecto sábado

        if (diasSorteo.contains(-1)) {
            // Loterías especiales (Navidad, Niño)
            return Pair(-1, "Especial")
        }

        val hoy = java.time.LocalDate.now()
        val diaHoy = hoy.dayOfWeek.value // 1=Lunes, 7=Domingo

        // Buscar el próximo día de sorteo
        for (i in 0..7) {
            val diaBuscado = ((diaHoy - 1 + i) % 7) + 1
            if (diaBuscado in diasSorteo) {
                return Pair(diaBuscado, nombresDias[diaBuscado - 1])
            }
        }

        return Pair(diasSorteo.first(), nombresDias[diasSorteo.first() - 1])
    }

    /**
     * Predicción de reintegro para el próximo día de sorteo.
     */
    data class PrediccionComplementarioDia(
        val numero: Int,
        val diaSorteo: String,
        val score: Double,
        val frecuenciaEnDia: Int,
        val totalSorteosEnDia: Int,
        val porcentaje: Double,
        val racha: TipoRacha
    )

    /**
     * Predice el mejor reintegro para el próximo día de sorteo.
     *
     * @param historico Histórico de sorteos Primitiva/Bonoloto
     * @param tipoLoteria Tipo de lotería
     * @return Predicción del mejor reintegro para el próximo sorteo
     */
    fun predecirReintegroParaProximoSorteo(
        historico: List<ResultadoPrimitiva>,
        tipoLoteria: String
    ): PrediccionComplementarioDia? {
        if (historico.isEmpty()) return null

        val (diaSorteo, nombreDia) = obtenerProximoDiaSorteo(tipoLoteria)
        if (diaSorteo == -1) return null

        // Filtrar sorteos del día de la semana del próximo sorteo
        val sorteosDia = historico.filter { sorteo ->
            try {
                val fecha = java.time.LocalDate.parse(sorteo.fecha)
                fecha.dayOfWeek.value == diaSorteo
            } catch (e: Exception) {
                false
            }
        }

        if (sorteosDia.isEmpty()) {
            // Si no hay datos del día, usar predicción general
            val prediccionGeneral = predecirReintegros(historico, 1).firstOrNull()
            return prediccionGeneral?.let {
                PrediccionComplementarioDia(
                    numero = it.numero,
                    diaSorteo = nombreDia,
                    score = it.scoreTotal,
                    frecuenciaEnDia = it.frecuencia,
                    totalSorteosEnDia = historico.size,
                    porcentaje = (it.frecuencia.toDouble() / historico.size * 100),
                    racha = it.racha
                )
            }
        }

        // Analizar frecuencia de reintegros en ese día específico
        val frecuenciasPorReintegro = (0..9).associateWith { reintegro ->
            sorteosDia.count { it.reintegro == reintegro }
        }

        // Calcular tendencia reciente (últimos 20 sorteos de ese día)
        val sorteosDiaRecientes = sorteosDia.take(20)
        val tendenciaReciente = (0..9).associateWith { reintegro ->
            sorteosDiaRecientes.count { it.reintegro == reintegro }
        }

        // Calcular gaps
        val gaps = (0..9).associateWith { reintegro ->
            sorteosDia.indexOfFirst { it.reintegro == reintegro }.let { if (it < 0) sorteosDia.size else it }
        }

        // Detectar rachas
        val rachas = detectarRachasComplementarios(
            sorteosDia.map { it.reintegro },
            0..9,
            20,
            1,
            10
        )

        // Calcular score combinado
        val scores = (0..9).map { reintegro ->
            val frec = frecuenciasPorReintegro[reintegro] ?: 0
            val tend = tendenciaReciente[reintegro] ?: 0
            val gap = gaps[reintegro] ?: 0
            val racha = rachas[reintegro] ?: TipoRacha.NORMAL

            val frecMax = frecuenciasPorReintegro.values.maxOrNull() ?: 1
            val tendMax = tendenciaReciente.values.maxOrNull() ?: 1

            val scoreFrec = if (frecMax > 0) (frec.toDouble() / frecMax) * 30 else 15.0
            val scoreTend = if (tendMax > 0) (tend.toDouble() / tendMax) * 30 else 15.0
            val scoreGap = (gap.toDouble() / sorteosDia.size.coerceAtLeast(1) * 20).coerceAtMost(20.0)
            val scoreRacha = when (racha) {
                TipoRacha.MUY_CALIENTE -> 20.0
                TipoRacha.CALIENTE -> 17.0
                TipoRacha.MUY_FRIO -> 15.0
                TipoRacha.FRIO -> 12.0
                TipoRacha.NORMAL -> 10.0
            }

            Triple(reintegro, scoreFrec + scoreTend + scoreGap + scoreRacha, racha)
        }

        val mejor = scores.maxByOrNull { it.second } ?: return null
        val frecEnDia = frecuenciasPorReintegro[mejor.first] ?: 0

        return PrediccionComplementarioDia(
            numero = mejor.first,
            diaSorteo = nombreDia,
            score = mejor.second,
            frecuenciaEnDia = frecEnDia,
            totalSorteosEnDia = sorteosDia.size,
            porcentaje = (frecEnDia.toDouble() / sorteosDia.size * 100),
            racha = mejor.third
        )
    }

    /**
     * Predice las mejores estrellas para el próximo día de sorteo de Euromillones.
     */
    fun predecirEstrellasParaProximoSorteo(
        historico: List<ResultadoEuromillones>
    ): List<PrediccionComplementarioDia> {
        if (historico.isEmpty()) return emptyList()

        val (diaSorteo, nombreDia) = obtenerProximoDiaSorteo("EUROMILLONES")
        if (diaSorteo == -1) return emptyList()

        // Filtrar sorteos del día
        val sorteosDia = historico.filter { sorteo ->
            try {
                val fecha = java.time.LocalDate.parse(sorteo.fecha)
                fecha.dayOfWeek.value == diaSorteo
            } catch (e: Exception) {
                false
            }
        }

        if (sorteosDia.isEmpty()) {
            return predecirEstrellas(historico, 2).map {
                PrediccionComplementarioDia(
                    numero = it.numero,
                    diaSorteo = nombreDia,
                    score = it.scoreTotal,
                    frecuenciaEnDia = it.frecuencia,
                    totalSorteosEnDia = historico.size,
                    porcentaje = (it.frecuencia.toDouble() / (historico.size * 2) * 100),
                    racha = it.racha
                )
            }
        }

        // Analizar frecuencia de estrellas en ese día
        val todasEstrellas = sorteosDia.flatMap { it.estrellas }
        val frecuenciasPorEstrella = (1..12).associateWith { estrella ->
            todasEstrellas.count { it == estrella }
        }

        val rachas = detectarRachasComplementarios(todasEstrellas, 1..12, 40, 2, 12)

        val scores = (1..12).map { estrella ->
            val frec = frecuenciasPorEstrella[estrella] ?: 0
            val frecMax = frecuenciasPorEstrella.values.maxOrNull() ?: 1
            val racha = rachas[estrella] ?: TipoRacha.NORMAL

            val scoreFrec = if (frecMax > 0) (frec.toDouble() / frecMax) * 50 else 25.0
            val scoreRacha = when (racha) {
                TipoRacha.MUY_CALIENTE -> 50.0
                TipoRacha.CALIENTE -> 40.0
                TipoRacha.MUY_FRIO -> 35.0
                TipoRacha.FRIO -> 25.0
                TipoRacha.NORMAL -> 20.0
            }

            Triple(estrella, scoreFrec + scoreRacha, racha)
        }

        return scores.sortedByDescending { it.second }.take(2).map { (estrella, score, racha) ->
            val frecEnDia = frecuenciasPorEstrella[estrella] ?: 0
            PrediccionComplementarioDia(
                numero = estrella,
                diaSorteo = nombreDia,
                score = score,
                frecuenciaEnDia = frecEnDia,
                totalSorteosEnDia = sorteosDia.size,
                porcentaje = (frecEnDia.toDouble() / (sorteosDia.size * 2) * 100),
                racha = racha
            )
        }
    }

    /**
     * Predice el mejor número clave para el próximo día de sorteo del Gordo.
     */
    fun predecirClaveParaProximoSorteo(
        historico: List<ResultadoGordoPrimitiva>
    ): PrediccionComplementarioDia? {
        if (historico.isEmpty()) return null

        val (diaSorteo, nombreDia) = obtenerProximoDiaSorteo("GORDO_PRIMITIVA")
        if (diaSorteo == -1) return null

        // El Gordo es solo domingos, así que usamos todo el histórico
        val claves = historico.map { it.numeroClave }
        val frecuenciasPorClave = (0..9).associateWith { clave ->
            claves.count { it == clave }
        }

        val rachas = detectarRachasComplementarios(claves, 0..9, 20, 1, 10)

        val scores = (0..9).map { clave ->
            val frec = frecuenciasPorClave[clave] ?: 0
            val frecMax = frecuenciasPorClave.values.maxOrNull() ?: 1
            val racha = rachas[clave] ?: TipoRacha.NORMAL

            val scoreFrec = if (frecMax > 0) (frec.toDouble() / frecMax) * 50 else 25.0
            val scoreRacha = when (racha) {
                TipoRacha.MUY_CALIENTE -> 50.0
                TipoRacha.CALIENTE -> 40.0
                TipoRacha.MUY_FRIO -> 35.0
                TipoRacha.FRIO -> 25.0
                TipoRacha.NORMAL -> 20.0
            }

            Triple(clave, scoreFrec + scoreRacha, racha)
        }

        val mejor = scores.maxByOrNull { it.second } ?: return null
        val frecEnDia = frecuenciasPorClave[mejor.first] ?: 0

        return PrediccionComplementarioDia(
            numero = mejor.first,
            diaSorteo = nombreDia,
            score = mejor.second,
            frecuenciaEnDia = frecEnDia,
            totalSorteosEnDia = historico.size,
            porcentaje = (frecEnDia.toDouble() / historico.size * 100),
            racha = mejor.third
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 13: HISTORIAL DE PREDICCIONES
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Registro de una predicción.
     */
    data class RegistroPrediccion(
        val fecha: String,
        val tipoLoteria: String,
        val metodo: String,
        val combinacion: List<Int>,
        val complementarios: List<Int>,
        val resultadoReal: List<Int>?,         // null si aún no hay sorteo
        val complementariosReales: List<Int>?,
        val aciertos: Int?,
        val aciertosComplementarios: Int?
    )

    /**
     * Guarda una predicción en el historial.
     */
    fun guardarPrediccion(
        tipoLoteria: String,
        metodo: String,
        combinacion: List<Int>,
        complementarios: List<Int> = emptyList()
    ) {
        memoria?.guardarPrediccion(
            fecha = java.time.LocalDate.now().toString(),
            tipoLoteria = tipoLoteria,
            metodo = metodo,
            combinacion = combinacion,
            complementarios = complementarios
        )
    }

    /**
     * Actualiza una predicción con el resultado real.
     */
    fun actualizarPrediccionConResultado(
        fecha: String,
        tipoLoteria: String,
        resultadoReal: List<Int>,
        complementariosReales: List<Int> = emptyList()
    ) {
        memoria?.actualizarPrediccionConResultado(
            fecha = fecha,
            tipoLoteria = tipoLoteria,
            resultadoReal = resultadoReal,
            complementariosReales = complementariosReales
        )
    }

    /**
     * Obtiene el historial de predicciones.
     */
    fun obtenerHistorialPredicciones(
        tipoLoteria: String,
        limite: Int = 50
    ): List<RegistroPrediccion> {
        return memoria?.obtenerHistorialPredicciones(tipoLoteria, limite) ?: emptyList()
    }

    /**
     * Calcula estadísticas del historial de predicciones.
     */
    fun calcularEstadisticasPredicciones(tipoLoteria: String): Map<String, Any> {
        val historial = obtenerHistorialPredicciones(tipoLoteria, 100)
        val conResultado = historial.filter { it.resultadoReal != null }

        if (conResultado.isEmpty()) {
            return mapOf(
                "totalPredicciones" to historial.size,
                "prediccionesEvaluadas" to 0,
                "promedioAciertos" to 0.0,
                "mejorAcierto" to 0,
                "porcentajeConAciertos" to 0.0
            )
        }

        val aciertos = conResultado.mapNotNull { it.aciertos }
        val aciertosComp = conResultado.mapNotNull { it.aciertosComplementarios }

        return mapOf(
            "totalPredicciones" to historial.size,
            "prediccionesEvaluadas" to conResultado.size,
            "promedioAciertos" to if (aciertos.isNotEmpty()) aciertos.average() else 0.0,
            "mejorAcierto" to (aciertos.maxOrNull() ?: 0),
            "porcentajeConAciertos" to (aciertos.count { it > 0 }.toDouble() / conResultado.size * 100),
            "promedioAciertosComplementarios" to if (aciertosComp.isNotEmpty()) aciertosComp.average() else 0.0,
            "distribucionAciertos" to aciertos.groupingBy { it }.eachCount()
        )
    }
}
