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
        
        // Si no hay suficientes, completar
        while (combinacionesGeneradas.size < numCombinaciones) {
            val nueva = (1..maxNumero).shuffled(rnd).take(cantidadNumeros).sorted()
            if (combinacionesVistas.add(nueva.toSet())) {
                combinacionesGeneradas.add(nueva)
            }
        }
        
        // Info para mostrar
        val infoComposicion = "G:${(pGap*100).toInt()}% F:${(pFrec*100).toInt()}% T:${(pTend*100).toInt()}%"
        
        return combinacionesGeneradas.mapIndexed { i, nums ->
            CombinacionSugerida(
                numeros = nums,
                probabilidadRelativa = ((pGap + pFrec + pTend) * 100).roundTo(1),
                explicacion = "🤖 $nombreNivel | $infoComposicion | Exp:$totalEntrenamientos"
            )
        }
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
    
    // ==================== ALGORITMO GENÉTICO ====================
    
    data class Individuo(val genes: List<Int>, var fitness: Double = 0.0)
    
    data class Caracteristicas(
        val frecuencias: Map<Int, Int>,
        val gaps: Map<Int, Int>,
        val tendencia: Map<Int, Int>,
        val pares: Map<Pair<Int, Int>, Int>,
        val numerosExitosos: Map<Int, Double>,
        val paresExitosos: List<Pair<Pair<Int, Int>, Double>>,
        val maxNumero: Int
    )
    
    private fun crearPoblacionInicial(car: Caracteristicas, maxNum: Int, cant: Int): MutableList<Individuo> {
        val pob = mutableListOf<Individuo>()
        val n = config.poblacion / 6
        
        // Obtener diferentes pools de números
        val topFrec = car.frecuencias.entries.sortedByDescending { it.value }.take(25).map { it.key }
        val topGap = car.gaps.entries.sortedByDescending { it.value }.take(25).map { it.key }
        val topTend = car.tendencia.entries.sortedByDescending { it.value }.take(25).map { it.key }
        val bottomFrec = car.frecuencias.entries.sortedBy { it.value }.take(15).map { it.key }
        
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
        // Mixto: frecuentes + atrasados
        repeat(n) {
            val mix = (topFrec.shuffled().take(cant/2) + topGap.shuffled().take(cant - cant/2)).distinct()
            val completar = if (mix.size < cant) (1..maxNum).filter { it !in mix }.shuffled().take(cant - mix.size) else emptyList()
            pob.add(Individuo((mix + completar).take(cant)))
        }
        // Mixto: frecuentes + fríos (contrarios)
        repeat(n) {
            val mix = (topFrec.shuffled().take(cant - 2) + bottomFrec.shuffled().take(2)).distinct()
            val completar = if (mix.size < cant) (1..maxNum).filter { it !in mix }.shuffled().take(cant - mix.size) else emptyList()
            pob.add(Individuo((mix + completar).take(cant)))
        }
        // Aleatorio puro
        while (pob.size < config.poblacion) {
            pob.add(Individuo((1..maxNum).shuffled().take(cant)))
        }
        
        return pob
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VERSIÓN VARIADA: Usa Random con semilla para generar resultados diferentes
    // ═══════════════════════════════════════════════════════════════════════════
    private fun crearPoblacionInicialVariada(car: Caracteristicas, maxNum: Int, cant: Int, rnd: Random): MutableList<Individuo> {
        val pob = mutableListOf<Individuo>()
        val n = config.poblacion / 6
        
        // Obtener diferentes pools de números con rotación basada en Random
        val rotacion = rnd.nextInt(10)
        val topFrec = car.frecuencias.entries.sortedByDescending { it.value }.drop(rotacion).take(25).map { it.key }
        val topGap = car.gaps.entries.sortedByDescending { it.value }.drop(rotacion).take(25).map { it.key }
        val topTend = car.tendencia.entries.sortedByDescending { it.value }.drop(rotacion).take(25).map { it.key }
        val bottomFrec = car.frecuencias.entries.sortedBy { it.value }.take(20).map { it.key }
        
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
        // Mixto: frecuentes + atrasados
        repeat(n) {
            val mix = (topFrec.shuffled(rnd).take(cant/2) + topGap.shuffled(rnd).take(cant - cant/2)).distinct()
            val completar = if (mix.size < cant) (1..maxNum).filter { it !in mix }.shuffled(rnd).take(cant - mix.size) else emptyList()
            pob.add(Individuo((mix + completar).take(cant)))
        }
        // Mixto: frecuentes + fríos (contrarios)
        repeat(n) {
            val mix = (topFrec.shuffled(rnd).take(cant - 2) + bottomFrec.shuffled(rnd).take(2)).distinct()
            val completar = if (mix.size < cant) (1..maxNum).filter { it !in mix }.shuffled(rnd).take(cant - mix.size) else emptyList()
            pob.add(Individuo((mix + completar).take(cant)))
        }
        // Aleatorio puro (más cantidad)
        while (pob.size < config.poblacion) {
            pob.add(Individuo((1..maxNum).shuffled(rnd).take(cant)))
        }
        
        return pob
    }
    
    private fun evolucionarGeneracion(pob: MutableList<Individuo>, car: Caracteristicas, maxNum: Int, cant: Int): MutableList<Individuo> {
        val nueva = mutableListOf<Individuo>()
        val elite = (config.poblacion * config.elitismo).toInt()
        nueva.addAll(pob.sortedByDescending { it.fitness }.take(elite))
        
        while (nueva.size < config.poblacion) {
            val p1 = pob.shuffled().take(3).maxByOrNull { it.fitness }!!
            val p2 = pob.shuffled().take(3).maxByOrNull { it.fitness }!!
            
            var hijo = if (Random.nextDouble() < config.tasaCruce) {
                val genes = (p1.genes + p2.genes).distinct().shuffled().take(cant)
                val completar = if (genes.size < cant) (1..maxNum).filter { it !in genes }.shuffled().take(cant - genes.size) else emptyList()
                Individuo(genes + completar)
            } else {
                Individuo(p1.genes.toList())
            }
            
            if (Random.nextDouble() < config.tasaMutacion) {
                val genes = hijo.genes.toMutableList()
                val idx = Random.nextInt(genes.size)
                genes[idx] = (1..maxNum).filter { it !in genes }.random()
                hijo = Individuo(genes)
            }
            
            evaluarFitness(hijo, car)
            nueva.add(hijo)
        }
        return nueva
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VERSIÓN VARIADA: Evolución con más aleatoriedad y Random controlado
    // ═══════════════════════════════════════════════════════════════════════════
    private fun evolucionarGeneracionVariada(pob: MutableList<Individuo>, car: Caracteristicas, maxNum: Int, cant: Int, rnd: Random): MutableList<Individuo> {
        val nueva = mutableListOf<Individuo>()
        // Menos elitismo para más variación (15% en lugar del normal)
        val elite = (config.poblacion * 0.15).toInt()
        nueva.addAll(pob.shuffled(rnd).sortedByDescending { it.fitness }.take(elite))
        
        // Tasa de cruce y mutación más alta para más variación
        val tasaCruceAlta = 0.85
        val tasaMutacionAlta = 0.25
        
        while (nueva.size < config.poblacion) {
            val p1 = pob.shuffled(rnd).take(4).maxByOrNull { it.fitness }!!
            val p2 = pob.shuffled(rnd).take(4).maxByOrNull { it.fitness }!!
            
            var hijo = if (rnd.nextDouble() < tasaCruceAlta) {
                val genes = (p1.genes + p2.genes).distinct().shuffled(rnd).take(cant)
                val completar = if (genes.size < cant) (1..maxNum).filter { it !in genes }.shuffled(rnd).take(cant - genes.size) else emptyList()
                Individuo(genes + completar)
            } else {
                Individuo(if (rnd.nextBoolean()) p1.genes.toList() else p2.genes.toList())
            }
            
            // Mutación más agresiva
            if (rnd.nextDouble() < tasaMutacionAlta) {
                val genes = hijo.genes.toMutableList()
                // Mutar 1 o 2 genes
                val numMutaciones = if (rnd.nextDouble() < 0.3) 2 else 1
                repeat(numMutaciones) {
                    val idx = rnd.nextInt(genes.size)
                    val disponibles = (1..maxNum).filter { it !in genes }
                    if (disponibles.isNotEmpty()) {
                        genes[idx] = disponibles.random(rnd)
                    }
                }
                hijo = Individuo(genes)
            }
            
            evaluarFitness(hijo, car)
            nueva.add(hijo)
        }
        return nueva
    }
    
    private fun evaluarFitness(ind: Individuo, car: Caracteristicas) {
        val nums = ind.genes.sorted()
        
        val scoreFrec = calcScore(nums, car.frecuencias)
        val scoreGap = calcScoreGap(nums, car.gaps)
        val scoreTend = calcScore(nums, car.tendencia)
        val scoreMem = calcScoreMem(nums, car.numerosExitosos)
        val scoreBal = calcScoreBalance(nums, car.maxNumero)
        
        contribuciones["frecuencia"] = (contribuciones["frecuencia"] ?: 0.0) + scoreFrec
        contribuciones["gap"] = (contribuciones["gap"] ?: 0.0) + scoreGap
        contribuciones["tendencia"] = (contribuciones["tendencia"] ?: 0.0) + scoreTend
        contribuciones["balance"] = (contribuciones["balance"] ?: 0.0) + scoreBal
        
        ind.fitness = (
            scoreFrec * (pesosCaracteristicas["frecuencia"] ?: 0.15) +
            scoreGap * (pesosCaracteristicas["gap"] ?: 0.15) +
            scoreTend * (pesosCaracteristicas["tendencia"] ?: 0.15) +
            scoreMem * 0.25 +
            scoreBal * (pesosCaracteristicas["balance"] ?: 0.15)
        )
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
        
        return Caracteristicas(
            frecuencias = frec,
            gaps = gaps,
            tendencia = tend,
            pares = pares,
            numerosExitosos = memoria?.obtenerScoreNumeros(tipo, maxNum) ?: emptyMap(),
            paresExitosos = memoria?.obtenerParesExitosos(tipo) ?: emptyList(),
            maxNumero = maxNum
        )
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
}
