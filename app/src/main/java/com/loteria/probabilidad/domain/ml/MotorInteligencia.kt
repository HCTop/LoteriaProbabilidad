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
        
        contribuciones.clear()
        val car = extraerCaracteristicas(historico, maxNumero, tipoLoteria)
        var poblacion = crearPoblacionInicial(car, maxNumero, cantidadNumeros)
        
        poblacion.forEach { evaluarFitness(it, car) }
        
        repeat(config.generaciones) { gen ->
            poblacion = evolucionarGeneracion(poblacion, car, maxNumero, cantidadNumeros)
        }
        
        val nombreNivel = memoria?.obtenerNombreNivel(tipoLoteria) ?: "🌱 Novato"
        val entrenamientos = memoria?.obtenerTotalEntrenamientos(tipoLoteria) ?: 0
        
        // Añadir VARIACIÓN al fitness para que cada regeneración sea diferente
        // Esto evita que siempre salgan las mismas combinaciones "óptimas"
        val variacionFactor = 0.15 // 15% de variación aleatoria
        poblacion.forEach { ind ->
            val variacion = (Random.nextDouble() - 0.5) * 2 * variacionFactor * ind.fitness
            ind.fitness = (ind.fitness + variacion).coerceAtLeast(0.0)
        }
        
        // Seleccionar combinaciones DIVERSAS (no repetir números idénticos)
        val ordenadas = poblacion.sortedByDescending { it.fitness }
        val seleccionadas = mutableListOf<Individuo>()
        val combinacionesVistas = mutableSetOf<Set<Int>>()
        
        for (ind in ordenadas) {
            val numSet = ind.genes.toSet()
            // Solo añadir si es suficientemente diferente (al menos 2 números distintos)
            val esDiferente = combinacionesVistas.all { vista ->
                (numSet - vista).size >= 2 || (vista - numSet).size >= 2
            }
            if (esDiferente || combinacionesVistas.isEmpty()) {
                seleccionadas.add(ind)
                combinacionesVistas.add(numSet)
            }
            if (seleccionadas.size >= numCombinaciones) break
        }
        
        // Si no hay suficientes diversas, generar aleatorias para completar
        while (seleccionadas.size < numCombinaciones) {
            val nuevaComb = (1..maxNumero).shuffled().take(cantidadNumeros)
            val numSet = nuevaComb.toSet()
            if (combinacionesVistas.none { it == numSet }) {
                seleccionadas.add(Individuo(nuevaComb, 0.3))
                combinacionesVistas.add(numSet)
            }
        }
        
        return seleccionadas.mapIndexed { i, ind ->
            val top2 = listOf("Freq", "Gap", "Tend", "Mem").take(2).joinToString("+")
            CombinacionSugerida(
                numeros = ind.genes.sorted(),
                probabilidadRelativa = (ind.fitness * 100).roundTo(1),
                explicacion = "🤖 $nombreNivel #${i+1} | Score:${(ind.fitness*100).roundTo(1)} | $top2 | Exp:$entrenamientos"
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
