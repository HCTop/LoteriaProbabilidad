package com.loteria.probabilidad.domain.ml

import com.loteria.probabilidad.data.model.ScorePopularidad
import com.loteria.probabilidad.data.model.TipoLoteria

/**
 * Pilar 2: Anti-Popularidad.
 * Calcula score de unicidad 0-100 para cada combinación.
 * A mayor score, menos probable que otros jugadores la hayan elegido.
 */
object AntiPopularidad {

    // Secuencias famosas que la gente suele jugar
    private val secuenciasFamosas = listOf(
        listOf(1, 2, 3, 4, 5, 6),
        listOf(7, 14, 21, 28, 35, 42),
        listOf(3, 6, 9, 12, 15, 18),
        listOf(5, 10, 15, 20, 25, 30),
        listOf(1, 11, 21, 31, 41),
        listOf(2, 12, 22, 32, 42),
        listOf(4, 8, 15, 16, 23, 42), // Lost
        listOf(1, 7, 13, 19, 25, 31)
    )

    // Números de dígitos famosos
    private val digitosFamosos = setOf(
        "00000", "00001", "00013", "11111", "12345",
        "22222", "33333", "44444", "55555", "66666",
        "77777", "88888", "99999", "13013", "54321",
        "00007", "00100", "01000", "10000"
    )

    /**
     * Calcula score anti-popularidad para una combinación de lotería de bolas.
     */
    fun calcularScoreCombinacion(
        combinacion: List<Int>,
        tipoLoteria: TipoLoteria
    ): ScorePopularidad {
        var score = 100
        val penalizaciones = mutableListOf<String>()
        val k = combinacion.size

        // 1. Penalización cumpleaños (números ≤ 31)
        val numCumple = combinacion.count { it <= 31 }
        val penCumple = ((numCumple.toDouble() / k) * 25).toInt().coerceAtMost(25)
        if (penCumple > 5) {
            score -= penCumple
            penalizaciones.add("Cumpleaños ($numCumple/$k ≤31): -$penCumple")
        }

        // 2. Penalización múltiplos de 5
        val numMult5 = combinacion.count { it % 5 == 0 && it > 0 }
        val penMult5 = ((numMult5.toDouble() / k) * 15).toInt().coerceAtMost(15)
        if (penMult5 > 3) {
            score -= penMult5
            penalizaciones.add("Múltiplos de 5 ($numMult5): -$penMult5")
        }

        // 3. Penalización múltiplos de 10
        val numMult10 = combinacion.count { it % 10 == 0 && it > 0 }
        val penMult10 = ((numMult10.toDouble() / k) * 10).toInt().coerceAtMost(10)
        if (penMult10 > 2) {
            score -= penMult10
            penalizaciones.add("Múltiplos de 10 ($numMult10): -$penMult10")
        }

        // 4. Secuencias famosas
        val sorted = combinacion.sorted()
        for (seq in secuenciasFamosas) {
            val overlap = sorted.count { it in seq }
            if (overlap >= minOf(k, seq.size) - 1) {
                score -= 20
                penalizaciones.add("Secuencia famosa detectada: -20")
                break
            }
        }

        // 5. Todos consecutivos
        val diffs = sorted.zipWithNext { a, b -> b - a }
        if (diffs.isNotEmpty() && diffs.all { it == 1 }) {
            score -= 10
            penalizaciones.add("Todos consecutivos: -10")
        }

        // 6. Todos números bajos (<25)
        if (combinacion.all { it < 25 }) {
            score -= 10
            penalizaciones.add("Todos números bajos (<25): -10")
        }

        // 7. Patrones visuales en boleto (diagonal, fila, columna)
        if (tienePatronVisual(sorted, tipoLoteria.maxNumero)) {
            score -= 15
            penalizaciones.add("Patrón visual en boleto: -15")
        }

        val estimacion = when {
            score >= 80 -> "Muy único - pocos jugadores"
            score >= 60 -> "Bastante único"
            score >= 40 -> "Popularidad media"
            score >= 20 -> "Bastante popular"
            else -> "Muy popular - muchos jugadores"
        }

        return ScorePopularidad(
            combinacion = combinacion,
            scoreUnicidad = score.coerceIn(0, 100),
            penalizaciones = penalizaciones,
            estimacionJugadores = estimacion
        )
    }

    /**
     * Calcula score anti-popularidad para loterías de dígitos (Nacional/Navidad/Niño).
     */
    fun calcularScoreDigitos(
        numero: String
    ): ScorePopularidad {
        var score = 100
        val penalizaciones = mutableListOf<String>()
        val num = numero.padStart(5, '0')

        // 1. Números redondos
        when {
            num.endsWith("000") -> {
                score -= 15
                penalizaciones.add("Termina en 000: -15")
            }
            num.endsWith("00") -> {
                score -= 10
                penalizaciones.add("Termina en 00: -10")
            }
            num.endsWith("0") -> {
                score -= 5
                penalizaciones.add("Termina en 0: -5")
            }
        }

        // 2. Repetitivos
        if (num.toSet().size == 1) {
            score -= 20
            penalizaciones.add("Todos dígitos iguales: -20")
        } else if (num == "12345" || num == "54321" || num == "13579" || num == "02468") {
            score -= 20
            penalizaciones.add("Secuencia obvia: -20")
        }

        // 3. Parece fecha (DDMM_)
        val dd = num.substring(0, 2).toIntOrNull() ?: 0
        val mm = num.substring(2, 4).toIntOrNull() ?: 0
        if (dd in 1..31 && mm in 1..12) {
            score -= 10
            penalizaciones.add("Parece fecha (${dd}/${mm}): -10")
        }

        // 4. Números famosos
        if (num in digitosFamosos) {
            score -= 15
            penalizaciones.add("Número famoso: -15")
        }

        // 5. Palíndromo (12321, etc.)
        if (num == num.reversed()) {
            score -= 10
            penalizaciones.add("Palíndromo: -10")
        }

        val estimacion = when {
            score >= 80 -> "Muy único"
            score >= 60 -> "Bastante único"
            score >= 40 -> "Popularidad media"
            else -> "Bastante popular"
        }

        return ScorePopularidad(
            combinacion = num.map { it.digitToInt() },
            scoreUnicidad = score.coerceIn(0, 100),
            penalizaciones = penalizaciones,
            estimacionJugadores = estimacion
        )
    }

    /**
     * Detecta si los números forman un patrón visual en el boleto
     * (diagonal, fila completa, columna).
     * Usa el grid real del boleto: 7 columnas para Primitiva/Bonoloto (49 nums),
     * 10 para Euromillones (50), etc.
     */
    private fun tienePatronVisual(sorted: List<Int>, maxNumero: Int): Boolean {
        // Grid real según el boleto de cada lotería
        val cols = when {
            maxNumero <= 49 -> 7   // Primitiva/Bonoloto: 7×7
            maxNumero <= 50 -> 10  // Euromillones: 5×10
            maxNumero <= 54 -> 9   // Gordo: 6×9
            else -> 10             // Fallback
        }
        val columnas = sorted.map { (it - 1) % cols }
        val filas = sorted.map { (it - 1) / cols }

        // Todos en la misma columna
        if (columnas.toSet().size == 1 && sorted.size >= 4) return true

        // Todos en la misma fila
        if (filas.toSet().size == 1 && sorted.size >= 4) return true

        // Diagonal perfecta
        if (sorted.size >= 4) {
            val diff = sorted.zipWithNext { a, b -> b - a }
            if (diff.toSet().size == 1 && diff.first() == cols + 1) return true
            if (diff.toSet().size == 1 && diff.first() == cols - 1) return true
        }

        return false
    }
}
