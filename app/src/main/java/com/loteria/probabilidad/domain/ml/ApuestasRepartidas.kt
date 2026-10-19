package com.loteria.probabilidad.domain.ml

import com.loteria.probabilidad.data.model.ScorePopularidad
import com.loteria.probabilidad.data.model.TipoLoteria
import kotlin.random.Random

/**
 * "Varias apuestas": cómo jugar N apuestas de la mejor forma posible.
 *
 * Sustituye a la antigua "Fórmula del Abuelo" en las loterías de bolas. Su sistema de
 * cobertura (17 números, hasta 15 boletos) prometía una garantía que no cumplía (solo
 * cubría el 43 % de los tríos) y, comprobado por simulación, BAJABA la probabilidad de
 * cobrar algún premio frente a jugar las mismas apuestas al azar (19,7 % vs 24,8 % con 15).
 *
 * Lo que sí funciona, y es lo que hace esta clase:
 *  1. REPARTIR: que las apuestas compartan los menos números posible. Con 15 apuestas de
 *     Primitiva sube la probabilidad de cobrar algo al ~26 % (máximo posible ≈ 1-(1-p)^N).
 *  2. ANTI-POPULARES: evitar fechas, múltiplos, patrones y secuencias famosas. No cambia la
 *     probabilidad de acertar, pero si aciertas repartes el premio con menos gente.
 *  3. No repetir combinaciones que ya han salido (no cambia la probabilidad; es por gusto).
 *
 * Determinista: la misma semilla (lotería + sorteo + N) da siempre las mismas apuestas.
 */
object ApuestasRepartidas {

    data class Resultado(
        val apuestas: List<ScorePopularidad>,
        /** Estrellas (Euromillones) o clave (Gordo) de cada apuesta; vacío si no aplica. */
        val extras: List<List<Int>>,
        /** Probabilidad (0-1) de que al menos una apuesta acierte [minAciertos] o más números. */
        val probAlgunPremio: Double,
        val minAciertos: Int,
        val coste: Double
    )

    private const val INTENTOS_POR_APUESTA = 600

    fun generar(
        tipo: TipoLoteria,
        numApuestas: Int,
        semilla: Long,
        yaSalidas: Set<Set<Int>> = emptySet()
    ): Resultado {
        val maxNum = tipo.maxNumero
        val k = tipo.cantidadNumeros
        val rnd = Random(semilla)
        val usos = IntArray(maxNum + 1)
        val elegidas = mutableListOf<ScorePopularidad>()

        repeat(numApuestas) {
            var mejor: ScorePopularidad? = null
            var mejorPuntos = Double.NEGATIVE_INFINITY
            repeat(INTENTOS_POR_APUESTA) {
                val cand = (1..maxNum).shuffled(rnd).take(k).sorted()
                if (cand.toSet() in yaSalidas) return@repeat
                if (elegidas.any { it.combinacion == cand }) return@repeat
                val score = AntiPopularidad.calcularScoreCombinacion(cand, tipo)
                // Reparto: penalizar números ya usados y, sobre todo, compartir 2+ números
                // con otra apuesta (eso es lo que hace que dos apuestas "acierten a la vez")
                val reutilizados = cand.sumOf { usos[it] }
                val solapeMax = elegidas.maxOfOrNull { (it.combinacion intersect cand.toSet()).size } ?: 0
                val puntos = score.scoreUnicidad -
                    8.0 * reutilizados -
                    (if (solapeMax >= 2) 40.0 * (solapeMax - 1) else 0.0)
                if (puntos > mejorPuntos) {
                    mejorPuntos = puntos
                    mejor = score
                }
            }
            mejor?.let { m ->
                elegidas.add(m)
                m.combinacion.forEach { usos[it]++ }
            }
        }

        val extras = when (tipo) {
            // Euromillones: repartir parejas de estrellas distintas
            TipoLoteria.EUROMILLONES -> {
                val estrellas = (1..12).shuffled(rnd)
                elegidas.indices.map { i ->
                    val a = estrellas[(2 * i) % 12]
                    val b = estrellas[(2 * i + 1) % 12]
                    listOf(a, b).sorted()
                }
            }
            // Gordo: repartir el número clave 0-9
            TipoLoteria.GORDO_PRIMITIVA -> {
                val claves = (0..9).shuffled(rnd)
                elegidas.indices.map { i -> listOf(claves[i % 10]) }
            }
            // Primitiva/Bonoloto: el reintegro lo asigna la máquina al validar
            else -> emptyList()
        }

        val minAciertos = if (k == 6) 3 else 2
        return Resultado(
            apuestas = elegidas,
            extras = extras,
            probAlgunPremio = probabilidadAlgunPremio(elegidas.map { it.combinacion }, maxNum, k, minAciertos, rnd),
            minAciertos = minAciertos,
            coste = elegidas.size * tipo.precioApuesta()
        )
    }

    /** Simulación de 20.000 sorteos: % de veces que al menos una apuesta acierta minAciertos+. */
    private fun probabilidadAlgunPremio(
        apuestas: List<List<Int>>,
        maxNum: Int,
        k: Int,
        minAciertos: Int,
        rnd: Random
    ): Double {
        if (apuestas.isEmpty()) return 0.0
        val sets = apuestas.map { it.toSet() }
        val n = 20_000
        var conPremio = 0
        repeat(n) {
            val sorteo = (1..maxNum).shuffled(rnd).take(k).toSet()
            if (sets.any { (it intersect sorteo).size >= minAciertos }) conPremio++
        }
        return conPremio.toDouble() / n
    }

    private fun TipoLoteria.precioApuesta(): Double = when (this) {
        TipoLoteria.PRIMITIVA -> 1.0
        TipoLoteria.BONOLOTO -> 0.5
        TipoLoteria.EUROMILLONES -> 2.5
        TipoLoteria.GORDO_PRIMITIVA -> 1.5
        else -> 1.0
    }
}
