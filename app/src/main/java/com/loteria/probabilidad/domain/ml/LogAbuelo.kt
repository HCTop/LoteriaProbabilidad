package com.loteria.probabilidad.domain.ml

import android.util.Log

/**
 * Sistema de logging centralizado "Diario del Abuelo".
 * Filtrar en Logcat por tag "ABUELO" para ver todo.
 */
object LogAbuelo {
    private const val TAG = "ABUELO"

    fun prediccion(metodo: String, numeros: List<Int>, confianza: Double) {
        Log.d(TAG, "PREDICCIÓN [$metodo]: ${numeros.sorted()} (confianza: ${"%.1f".format(confianza * 100)}%)")
    }

    fun evaluacion(metodo: String, predichos: List<Int>, reales: List<Int>, aciertos: Int) {
        val acertados = predichos.filter { it in reales }
        val estrellas = "★".repeat(aciertos)
        Log.d(TAG, "EVAL [$metodo]: $aciertos aciertos $acertados $estrellas")
    }

    fun aprendizaje(sistema: String, pesoAntes: Map<String, Double>, pesoDespues: Map<String, Double>) {
        val cambios = pesoDespues.map { (k, v) ->
            val antes = pesoAntes[k] ?: 0.0
            "$k=${"%.3f".format(antes)}→${"%.3f".format(v)}"
        }.joinToString(", ")
        Log.d(TAG, "APRENDE [$sistema]: $cambios")
    }

    fun algoritmo(nombre: String, resultado: String, significativo: Boolean) {
        val marca = if (significativo) "✓" else "✗"
        Log.d(TAG, "ALG [$nombre] $marca: $resultado")
    }

    fun mezcla(estadistico: Double, aprendido: Double, entrenamientos: Int) {
        Log.d(TAG, "MEZCLA: ${"%.0f".format(estadistico * 100)}% estadístico + ${"%.0f".format(aprendido * 100)}% aprendido ($entrenamientos entrenamientos)")
    }

    fun ensemble(estrategia: String, aciertos: Int, pesoAntes: Double, pesoDespues: Double) {
        Log.d(TAG, "ENSEMBLE [$estrategia]: $aciertos aciertos, peso ${"%.3f".format(pesoAntes)}→${"%.3f".format(pesoDespues)}")
    }

    fun resumen(
        tipoLoteria: String,
        fechaSorteo: String,
        numerosReales: List<Int>,
        evaluaciones: List<Triple<String, Int, List<Int>>>,
        pesosAbuelo: Map<String, Double>?,
        pesosCaracteristicas: Map<String, Double>?,
        entrenamientos: Int
    ) {
        Log.d(TAG, "══════════════════════════════════════════")
        Log.d(TAG, "RESUMEN EVALUACIÓN - $tipoLoteria")
        Log.d(TAG, "Sorteo: $fechaSorteo → Resultado: $numerosReales")
        Log.d(TAG, "──────────────────────────────────────────")
        for ((metodo, aciertos, acertados) in evaluaciones.sortedByDescending { it.second }) {
            Log.d(TAG, "$metodo: $aciertos aciertos $acertados ${"★".repeat(aciertos)}")
        }
        Log.d(TAG, "──────────────────────────────────────────")
        Log.d(TAG, "APRENDIZAJE:")
        if (pesosAbuelo != null) {
            Log.d(TAG, "  Abuelo pesos: ${pesosAbuelo.map { "${it.key}=${"%.3f".format(it.value)}" }.joinToString(", ")}")
        }
        if (pesosCaracteristicas != null) {
            Log.d(TAG, "  Características: ${pesosCaracteristicas.entries.take(5).joinToString(", ") { "${it.key}=${"%.3f".format(it.value)}" }}")
        }
        val mezclaAprendido = 0.1 + 0.75 / (1.0 + Math.exp(-(entrenamientos - 50.0) / 30.0))
        Log.d(TAG, "  Mezcla actual: ${"%.0f".format(mezclaAprendido * 100)}% aprendido ($entrenamientos entrenamientos)")
        Log.d(TAG, "══════════════════════════════════════════")
    }

    fun gradiente(sistema: String, signal: Double, detalles: String) {
        Log.d(TAG, "GRADIENTE [$sistema]: signal=${"%.3f".format(signal)}, $detalles")
    }
}
