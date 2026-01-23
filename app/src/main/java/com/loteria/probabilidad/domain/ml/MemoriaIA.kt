package com.loteria.probabilidad.domain.ml

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Sistema de Memoria Persistente para la IA de Lotería.
 * 
 * IMPORTANTE: Cada tipo de lotería tiene su PROPIA memoria separada.
 * El aprendizaje de Euromillones NO afecta a Primitiva y viceversa.
 * 
 * CARACTERÍSTICAS:
 * - Persistencia entre sesiones (SharedPreferences)
 * - Pesos adaptativos por característica POR LOTERÍA
 * - Historial de rendimiento por método POR LOTERÍA
 * - Patrones de números exitosos POR LOTERÍA
 * - Decay temporal (información reciente pesa más)
 */
class MemoriaIA(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "ia_memoria_loteria_v3", Context.MODE_PRIVATE
    )
    
    companion object {
        // Parámetros de aprendizaje
        private const val LEARNING_RATE = 0.15
        private const val DECAY_FACTOR = 0.98
        private const val MIN_PESO = 0.05
        private const val MAX_PESO = 0.50
        
        // Características que la IA aprende a ponderar
        val CARACTERISTICAS = listOf(
            "frecuencia",
            "gap",
            "tendencia",
            "patrones",
            "balance",
            "ciclos",
            "consecutivos",
            "suma",
            "paridad",
            "decenas"
        )
    }
    
    // ==================== PESOS DE CARACTERÍSTICAS (POR LOTERÍA) ====================
    
    fun obtenerPesosCaracteristicas(tipoLoteria: String = "GLOBAL"): Map<String, Double> {
        val key = "pesos_$tipoLoteria"
        val json = prefs.getString(key, null)
        
        return if (json != null) {
            try {
                val jsonObj = JSONObject(json)
                CARACTERISTICAS.associateWith { car ->
                    jsonObj.optDouble(car, 1.0 / CARACTERISTICAS.size)
                }
            } catch (e: Exception) {
                inicializarPesosDefault(tipoLoteria)
            }
        } else {
            inicializarPesosDefault(tipoLoteria)
        }
    }
    
    fun actualizarPesos(
        contribuciones: Map<String, Double>,
        puntuacionTotal: Double,
        mejorPuntuacionHistorica: Double,
        tipoLoteria: String
    ) {
        val pesosActuales = obtenerPesosCaracteristicas(tipoLoteria).toMutableMap()
        
        val exito = if (mejorPuntuacionHistorica > 0) {
            (puntuacionTotal / mejorPuntuacionHistorica).coerceIn(0.5, 2.0)
        } else {
            1.0
        }
        
        val totalContrib = contribuciones.values.sum().coerceAtLeast(0.001)
        
        for (car in CARACTERISTICAS) {
            val pesoActual = pesosActuales[car] ?: (1.0 / CARACTERISTICAS.size)
            val contrib = contribuciones[car] ?: 0.0
            val contribNorm = contrib / totalContrib
            
            val gradiente = (contribNorm - pesoActual) * exito
            val delta = LEARNING_RATE * gradiente
            val nuevoPeso = (pesoActual * DECAY_FACTOR + delta * (1 - DECAY_FACTOR))
                .coerceIn(MIN_PESO, MAX_PESO)
            
            pesosActuales[car] = nuevoPeso
        }
        
        // Normalizar
        val suma = pesosActuales.values.sum()
        pesosActuales.forEach { (k, v) -> pesosActuales[k] = v / suma }
        
        // Guardar
        guardarPesosCaracteristicas(pesosActuales, tipoLoteria)
        
        // Actualizar mejor puntuación
        if (puntuacionTotal > obtenerMejorPuntuacion(tipoLoteria)) {
            guardarMejorPuntuacion(puntuacionTotal, tipoLoteria)
        }
        
        // Incrementar entrenamientos
        incrementarEntrenamientos(tipoLoteria)
        
        // Guardar timestamp
        guardarUltimaActualizacion(tipoLoteria)
    }
    
    private fun inicializarPesosDefault(tipoLoteria: String): Map<String, Double> {
        val pesoInicial = 1.0 / CARACTERISTICAS.size
        val pesos = CARACTERISTICAS.associateWith { pesoInicial }
        guardarPesosCaracteristicas(pesos, tipoLoteria)
        return pesos
    }
    
    private fun guardarPesosCaracteristicas(pesos: Map<String, Double>, tipoLoteria: String) {
        val key = "pesos_$tipoLoteria"
        val json = JSONObject()
        pesos.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(key, json.toString()).apply()
    }
    
    // ==================== NÚMEROS EXITOSOS (POR LOTERÍA) ====================
    
    fun registrarNumerosExitosos(numeros: List<Int>, aciertos: Int, tipoLoteria: String) {
        val key = "numeros_exitosos_$tipoLoteria"
        val json = prefs.getString(key, "{}")
        val jsonObj = try { JSONObject(json) } catch (e: Exception) { JSONObject() }
        
        val peso = when (aciertos) {
            1 -> 1.0
            2 -> 3.0
            3 -> 10.0
            4 -> 30.0
            5 -> 100.0
            6 -> 500.0
            else -> 0.5
        }
        
        for (numero in numeros) {
            val scoreActual = jsonObj.optDouble(numero.toString(), 0.0)
            jsonObj.put(numero.toString(), scoreActual * DECAY_FACTOR + peso)
        }
        
        prefs.edit().putString(key, jsonObj.toString()).apply()
    }
    
    fun obtenerScoreNumeros(tipoLoteria: String, maxNumero: Int): Map<Int, Double> {
        val key = "numeros_exitosos_$tipoLoteria"
        val json = prefs.getString(key, "{}")
        val jsonObj = try { JSONObject(json) } catch (e: Exception) { JSONObject() }
        
        return (1..maxNumero).associateWith { numero ->
            jsonObj.optDouble(numero.toString(), 0.0)
        }
    }
    
    // ==================== PARES EXITOSOS (POR LOTERÍA) ====================
    
    fun registrarParExitoso(num1: Int, num2: Int, tipoLoteria: String) {
        val key = "pares_exitosos_$tipoLoteria"
        val parKey = "${minOf(num1, num2)}-${maxOf(num1, num2)}"
        
        val json = prefs.getString(key, "{}")
        val jsonObj = try { JSONObject(json) } catch (e: Exception) { JSONObject() }
        
        val scoreActual = jsonObj.optDouble(parKey, 0.0)
        jsonObj.put(parKey, scoreActual * DECAY_FACTOR + 1.0)
        
        prefs.edit().putString(key, jsonObj.toString()).apply()
    }
    
    fun obtenerParesExitosos(tipoLoteria: String, top: Int = 50): List<Pair<Pair<Int, Int>, Double>> {
        val key = "pares_exitosos_$tipoLoteria"
        val json = prefs.getString(key, "{}")
        val jsonObj = try { JSONObject(json) } catch (e: Exception) { JSONObject() }
        
        val pares = mutableListOf<Pair<Pair<Int, Int>, Double>>()
        
        jsonObj.keys().forEach { parKey ->
            val parts = parKey.split("-")
            if (parts.size == 2) {
                val num1 = parts[0].toIntOrNull() ?: return@forEach
                val num2 = parts[1].toIntOrNull() ?: return@forEach
                val score = jsonObj.optDouble(parKey, 0.0)
                pares.add(Pair(Pair(num1, num2), score))
            }
        }
        
        return pares.sortedByDescending { it.second }.take(top)
    }
    
    // ==================== ESTADÍSTICAS (POR LOTERÍA) ====================
    
    fun obtenerTotalEntrenamientos(tipoLoteria: String = "GLOBAL"): Int {
        return prefs.getInt("entrenamientos_$tipoLoteria", 0)
    }
    
    private fun incrementarEntrenamientos(tipoLoteria: String) {
        val actual = obtenerTotalEntrenamientos(tipoLoteria)
        prefs.edit().putInt("entrenamientos_$tipoLoteria", actual + 1).apply()
    }
    
    fun obtenerMejorPuntuacion(tipoLoteria: String = "GLOBAL"): Double {
        return prefs.getString("mejor_punt_$tipoLoteria", "0.0")?.toDoubleOrNull() ?: 0.0
    }
    
    private fun guardarMejorPuntuacion(puntuacion: Double, tipoLoteria: String) {
        prefs.edit().putString("mejor_punt_$tipoLoteria", puntuacion.toString()).apply()
    }
    
    fun obtenerUltimaActualizacion(tipoLoteria: String = "GLOBAL"): String {
        return prefs.getString("ultima_act_$tipoLoteria", "Nunca") ?: "Nunca"
    }
    
    private fun guardarUltimaActualizacion(tipoLoteria: String) {
        val timestamp = java.text.SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        prefs.edit().putString("ultima_act_$tipoLoteria", timestamp).apply()
    }
    
    fun obtenerNivelInteligencia(tipoLoteria: String = "GLOBAL"): Int {
        val entrenamientos = obtenerTotalEntrenamientos(tipoLoteria)
        val mejorPuntuacion = obtenerMejorPuntuacion(tipoLoteria)
        
        // Nivel basado en entrenamientos Y calidad de resultados
        val nivelPorEntrenamientos = when {
            entrenamientos < 50 -> 1      // Novato: < 50 entrenamientos
            entrenamientos < 200 -> 2     // Aprendiz: 50-199
            entrenamientos < 500 -> 3     // Intermedio: 200-499
            entrenamientos < 1000 -> 4    // Avanzado: 500-999
            entrenamientos < 2000 -> 5    // Experto: 1000-1999
            else -> 6                     // Maestro: 2000+
        }
        
        // Bonus por buena puntuación (si tiene aciertos de 4+ números)
        val bonusPorCalidad = when {
            mejorPuntuacion >= 500 -> 1   // Ha conseguido 5+ aciertos
            mejorPuntuacion >= 200 -> 0   // Ha conseguido 4 aciertos
            else -> -1                    // Solo 3 o menos aciertos
        }
        
        return (nivelPorEntrenamientos + bonusPorCalidad).coerceIn(1, 6)
    }
    
    fun obtenerNombreNivel(tipoLoteria: String = "GLOBAL"): String {
        return when (obtenerNivelInteligencia(tipoLoteria)) {
            1 -> "🌱 Novato"
            2 -> "📚 Aprendiz"
            3 -> "🎯 Intermedio"
            4 -> "⚡ Avanzado"
            5 -> "🏆 Experto"
            6 -> "👑 Maestro"
            else -> "🌱 Novato"
        }
    }
    
    /**
     * Obtiene el resumen del estado de la IA para una lotería específica.
     */
    fun obtenerResumenIA(tipoLoteria: String = "GLOBAL"): ResumenIA {
        val pesos = obtenerPesosCaracteristicas(tipoLoteria)
        val topCaracteristicas = pesos.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
        
        return ResumenIA(
            nivelInteligencia = obtenerNivelInteligencia(tipoLoteria),
            nombreNivel = obtenerNombreNivel(tipoLoteria),
            totalEntrenamientos = obtenerTotalEntrenamientos(tipoLoteria),
            mejorPuntuacion = obtenerMejorPuntuacion(tipoLoteria),
            ultimaActualizacion = obtenerUltimaActualizacion(tipoLoteria),
            topCaracteristicas = topCaracteristicas,
            pesosCaracteristicas = pesos,
            tipoLoteria = tipoLoteria
        )
    }
    
    /**
     * Reinicia la memoria de una lotería específica.
     */
    fun reiniciarMemoria(tipoLoteria: String? = null) {
        if (tipoLoteria != null) {
            // Reiniciar solo esa lotería
            prefs.edit()
                .remove("pesos_$tipoLoteria")
                .remove("numeros_exitosos_$tipoLoteria")
                .remove("pares_exitosos_$tipoLoteria")
                .remove("entrenamientos_$tipoLoteria")
                .remove("mejor_punt_$tipoLoteria")
                .remove("ultima_act_$tipoLoteria")
                .apply()
        } else {
            // Reiniciar TODO
            prefs.edit().clear().apply()
        }
    }
    
    /**
     * Obtiene la configuración genética (global).
     */
    fun obtenerConfiguracionGenetica(): ConfiguracionGenetica {
        val tamPool = prefs.getInt("config_tamano_pool", 10)
        return ConfiguracionGenetica(tamanoPool = tamPool)
    }
    
    /**
     * Guarda el tamaño del pool de números.
     */
    fun guardarTamanoPool(tamano: Int) {
        prefs.edit().putInt("config_tamano_pool", tamano.coerceIn(5, 25)).apply()
    }
    
    /**
     * Obtiene el tamaño del pool actual.
     */
    fun obtenerTamanoPool(): Int {
        return prefs.getInt("config_tamano_pool", 10)
    }
}

/**
 * Configuración del algoritmo genético.
 */
data class ConfiguracionGenetica(
    val poblacion: Int = 500,
    val generaciones: Int = 50,
    val tasaMutacion: Double = 0.15,
    val tasaCruce: Double = 0.7,
    val elitismo: Double = 0.1,
    val tamanoPool: Int = 10  // Cuántos números top considerar de cada categoría (5-20)
)

/**
 * Resumen del estado de la IA.
 */
data class ResumenIA(
    val nivelInteligencia: Int,
    val nombreNivel: String,
    val totalEntrenamientos: Int,
    val mejorPuntuacion: Double,
    val ultimaActualizacion: String,
    val topCaracteristicas: List<String>,
    val pesosCaracteristicas: Map<String, Double>,
    val tipoLoteria: String = "GLOBAL"
)
