package com.loteria.probabilidad.domain.calculator

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * "Forma típica" de una combinación ganadora, para cualquier lotería de bolas.
 * Sale de combinatoria pura (y el historial la confirma); no hace más probable acertar,
 * solo describe cómo suele ser lo que sale. La usan el método 🧩 Perfil típico y la
 * comprobación "Forma de la combinación" de la tarjeta Jugada.
 *
 *  - 6/49 (Primitiva, Bonoloto): suma 120-180, 2-4 impares, 2-4 bajos (1-24),
 *    exactamente una pareja de seguidos (≈ la mitad de los sorteos tiene alguna), 3-5 decenas.
 *  - 5 números (Euromillones 5/50, El Gordo 5/54): suma media ± 1 desviación, 2-3 impares,
 *    2-3 bajos, SIN números seguidos (lo más común, ≈ 65 %), 3-5 decenas.
 */
object FormaTipica {

    /** Rango "normal" de la suma: media ± 1 desviación típica (≈ 2 de cada 3 sorteos). */
    fun rangoSuma(maxNumero: Int, k: Int): IntRange {
        if (maxNumero == 49 && k == 6) return 120..180
        val media = k * (maxNumero + 1) / 2.0
        val sd = sqrt(k * (maxNumero * maxNumero - 1) / 12.0 * (maxNumero - k) / (maxNumero - 1.0))
        fun a5(x: Double) = ((x / 5.0).roundToInt() * 5)
        return a5(media - sd)..a5(media + sd)
    }

    fun rangoImpares(k: Int): IntRange = if (k == 6) 2..4 else 2..3
    fun rangoBajos(k: Int): IntRange = if (k == 6) 2..4 else 2..3
    /** Último número "bajo": 24 en 6/49 (como el estudio); la mitad en las demás. */
    fun limiteBajo(maxNumero: Int): Int = if (maxNumero == 49) 24 else maxNumero / 2
    /** Parejas de seguidos que se buscan: 1 en 6/49, ninguna con 5 números. */
    fun seguidosObjetivo(k: Int): Int = if (k == 6) 1 else 0

    fun seguidos(c: List<Int>): Int = c.sorted().zipWithNext().count { (a, b) -> b == a + 1 }
    fun decenas(c: List<Int>): Int = c.map { it / 10 }.distinct().size

    fun cumple(c: List<Int>, maxNumero: Int): Boolean {
        val k = c.size
        return c.sum() in rangoSuma(maxNumero, k) &&
            c.count { it % 2 == 1 } in rangoImpares(k) &&
            c.count { it <= limiteBajo(maxNumero) } in rangoBajos(k) &&
            seguidos(c) == seguidosObjetivo(k) &&
            decenas(c) in 3..5
    }
}
