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
        // ═══════════════════════════════════════════════════════════════════════════
        // MEJORA 5: Parámetros del Optimizador Adam
        // ═══════════════════════════════════════════════════════════════════════════
        private const val ADAM_LEARNING_RATE = 0.005  // α reducido: señal de características es ruido (~2% max)
        private const val ADAM_LEARNING_RATE_ABUELO = 0.03 // α para Abuelo (más agresivo)
        private const val ADAM_BETA1 = 0.9            // β₁ - Decay rate para momentum
        private const val ADAM_BETA2 = 0.999          // β₂ - Decay rate para velocity
        private const val ADAM_EPSILON = 1e-8         // ε - Evita división por cero

        // Parámetros de regularización
        private const val MIN_PESO = 0.03
        private const val MAX_PESO = 0.40
        private const val L2_REGULARIZATION = 0.001   // Regularización L2 para evitar pesos extremos

        // Factor de decay para scores de números/pares exitosos (no para Adam)
        private const val DECAY_FACTOR = 0.98

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

        /**
         * Pesos calibrados por backtest sobre 300 sorteos históricos.
         * Backtest midió la posición media de ganadores para cada señal:
         *   tendencia +2.0%, frecuencia +0.6%, gap -2.0% (contraproducente)
         *   ciclos/balance/paridad: ruido (confirmado por backtest de métodos)
         * Señales débiles → diferencias modestas respecto a uniforme (0.10).
         */
        fun pesosCaracteristicasDefault(): Map<String, Double> = mapOf(
            "frecuencia"    to 0.14,  // señal real (+0.6% backtest)
            "tendencia"     to 0.15,  // señal más fuerte (+2.0% backtest)
            "gap"           to 0.07,  // contraproducente (-2.0% backtest) — reducido
            "patrones"      to 0.10,  // sin datos directos — neutral
            "balance"       to 0.09,  // ruido (método equilibrio fallaba)
            "ciclos"        to 0.07,  // ruido (método ciclos fallaba) — reducido
            "consecutivos"  to 0.10,  // sin datos directos — neutral
            "suma"          to 0.10,  // sin datos directos — neutral
            "paridad"       to 0.09,  // levemente negativo (-1.6% backtest)
            "decenas"       to 0.09   // levemente negativo (-0.5% backtest)
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
    
    /**
     * MEJORA 5: Actualiza los pesos usando el Optimizador Adam.
     *
     * Adam (Adaptive Moment Estimation) combina:
     * - Momentum: promedio móvil de los gradientes (m)
     * - RMSprop: promedio móvil de los gradientes² (v)
     * - Bias correction: corrección para los primeros pasos
     *
     * Ventajas sobre SGD simple:
     * - Converge más rápido
     * - Maneja mejor gradientes ruidosos
     * - Learning rate adaptativo por parámetro
     */
    fun actualizarPesos(
        contribuciones: Map<String, Double>,
        puntuacionTotal: Double,
        mejorPuntuacionHistorica: Double,
        tipoLoteria: String
    ) {
        val pesosActuales = obtenerPesosCaracteristicas(tipoLoteria).toMutableMap()

        // Cargar estados de Adam (momentum y velocity)
        val momentum = obtenerMomentum(tipoLoteria)
        val velocity = obtenerVelocity(tipoLoteria)

        // Obtener el paso actual (t) para bias correction
        val t = obtenerTotalEntrenamientos(tipoLoteria) + 1

        // Factor de éxito basado en rendimiento
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

            // Calcular gradiente (dirección de mejora)
            val gradiente = (contribNorm - pesoActual) * exito

            // Añadir regularización L2 (penaliza pesos grandes)
            val gradienteConL2 = gradiente - L2_REGULARIZATION * pesoActual

            // ═══════════════════════════════════════════════════════════════════
            // ALGORITMO ADAM
            // ═══════════════════════════════════════════════════════════════════

            // Actualizar momentum (promedio móvil exponencial del gradiente)
            // m_t = β₁ * m_{t-1} + (1 - β₁) * g_t
            val mPrev = momentum[car] ?: 0.0
            val mNew = ADAM_BETA1 * mPrev + (1 - ADAM_BETA1) * gradienteConL2
            momentum[car] = mNew

            // Actualizar velocity (promedio móvil exponencial del gradiente²)
            // v_t = β₂ * v_{t-1} + (1 - β₂) * g_t²
            val vPrev = velocity[car] ?: 0.0
            val vNew = ADAM_BETA2 * vPrev + (1 - ADAM_BETA2) * gradienteConL2 * gradienteConL2
            velocity[car] = vNew

            // Bias correction (importante en los primeros pasos)
            // m̂_t = m_t / (1 - β₁^t)
            // v̂_t = v_t / (1 - β₂^t)
            val mHat = mNew / (1 - Math.pow(ADAM_BETA1, t.toDouble()))
            val vHat = vNew / (1 - Math.pow(ADAM_BETA2, t.toDouble()))

            // Actualizar peso
            // θ_t = θ_{t-1} + α * m̂_t / (√v̂_t + ε)
            val delta = ADAM_LEARNING_RATE * mHat / (Math.sqrt(vHat) + ADAM_EPSILON)
            val nuevoPeso = (pesoActual + delta).coerceIn(MIN_PESO, MAX_PESO)

            pesosActuales[car] = nuevoPeso
        }

        // Normalizar pesos para que sumen 1.0
        val suma = pesosActuales.values.sum()
        if (suma > 0) {
            pesosActuales.forEach { (k, v) -> pesosActuales[k] = v / suma }
        }

        // Guardar todo
        guardarPesosCaracteristicas(pesosActuales, tipoLoteria)
        guardarMomentum(momentum, tipoLoteria)
        guardarVelocity(velocity, tipoLoteria)

        // Actualizar mejor puntuación si mejoramos
        if (puntuacionTotal > obtenerMejorPuntuacion(tipoLoteria)) {
            guardarMejorPuntuacion(puntuacionTotal, tipoLoteria)
        }

        // Incrementar contador de entrenamientos
        incrementarEntrenamientos(tipoLoteria)

        // Guardar timestamp
        guardarUltimaActualizacion(tipoLoteria)
    }

    // ==================== APRENDIZAJE BASADO EN RESULTADOS REALES ====================

    /**
     * Actualiza los pesos de características usando aciertos REALES como señal.
     * A diferencia de actualizarPesos() que usa fitness genético, este método
     * aprende directamente de cuántos números acertó la predicción.
     */
    fun actualizarPesosDesdeResultadoReal(
        aciertos: Int,
        cantidadNumeros: Int,
        maxNumero: Int,
        tipoLoteria: String
    ) {
        val pesosActuales = obtenerPesosCaracteristicas(tipoLoteria).toMutableMap()
        val pesosAntes = pesosActuales.toMap()

        val momentum = obtenerMomentum(tipoLoteria)
        val velocity = obtenerVelocity(tipoLoteria)
        val t = obtenerTotalEntrenamientos(tipoLoteria) + 1

        // Aciertos esperados al azar: cantidadNumeros² / maxNumero
        val esperado = cantidadNumeros.toDouble() * cantidadNumeros / maxNumero
        // Signal normalizado a ~[-1, +1]
        val signal = ((aciertos - esperado) / cantidadNumeros).coerceIn(-1.0, 1.0)

        LogAbuelo.gradiente("PesosReales", signal,
            "aciertos=$aciertos, esperado=${"%.2f".format(esperado)}")

        val calibrado = pesosCaracteristicasDefault()

        for (car in CARACTERISTICAS) {
            val pesoActual = pesosActuales[car] ?: (1.0 / CARACTERISTICAS.size)
            val pesoObjetivo = calibrado[car] ?: (1.0 / CARACTERISTICAS.size)

            // Bug anterior: (pesoActual - uniforme) * signal → siempre 0 cuando pesos son uniformes
            // Fix: si va bien → acercarse a calibrado; si va mal → acercarse a uniforme (explorar)
            val uniforme = 1.0 / CARACTERISTICAS.size
            val gradiente = if (signal > 0) {
                (pesoObjetivo - pesoActual) * signal * 0.5  // pull hacia calibrado
            } else {
                (uniforme - pesoActual) * kotlin.math.abs(signal) * 0.2  // pull hacia uniforme
            }

            val gradienteConL2 = gradiente - L2_REGULARIZATION * pesoActual

            // Adam update
            val mPrev = momentum[car] ?: 0.0
            val mNew = ADAM_BETA1 * mPrev + (1 - ADAM_BETA1) * gradienteConL2
            momentum[car] = mNew

            val vPrev = velocity[car] ?: 0.0
            val vNew = ADAM_BETA2 * vPrev + (1 - ADAM_BETA2) * gradienteConL2 * gradienteConL2
            velocity[car] = vNew

            val mHat = mNew / (1 - Math.pow(ADAM_BETA1, t.toDouble()))
            val vHat = vNew / (1 - Math.pow(ADAM_BETA2, t.toDouble()))

            val delta = ADAM_LEARNING_RATE * mHat / (Math.sqrt(vHat) + ADAM_EPSILON)
            pesosActuales[car] = (pesoActual + delta).coerceIn(MIN_PESO, MAX_PESO)
        }

        // Normalizar
        val suma = pesosActuales.values.sum()
        if (suma > 0) {
            pesosActuales.forEach { (k, v) -> pesosActuales[k] = v / suma }
        }

        guardarPesosCaracteristicas(pesosActuales, tipoLoteria)
        guardarMomentum(momentum, tipoLoteria)
        guardarVelocity(velocity, tipoLoteria)

        LogAbuelo.aprendizaje("Características", pesosAntes, pesosActuales)
    }

    // ==================== ESTADOS DE ADAM (POR LOTERÍA) ====================

    /**
     * MEJORA 5: Obtiene el estado de momentum para Adam.
     */
    private fun obtenerMomentum(tipoLoteria: String): MutableMap<String, Double> {
        val key = "adam_momentum_$tipoLoteria"
        val json = prefs.getString(key, null) ?: return mutableMapOf()

        return try {
            val jsonObj = JSONObject(json)
            CARACTERISTICAS.associateWith { car ->
                jsonObj.optDouble(car, 0.0)
            }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    /**
     * MEJORA 5: Guarda el estado de momentum para Adam.
     */
    private fun guardarMomentum(momentum: Map<String, Double>, tipoLoteria: String) {
        val key = "adam_momentum_$tipoLoteria"
        val json = JSONObject()
        momentum.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(key, json.toString()).apply()
    }

    /**
     * MEJORA 5: Obtiene el estado de velocity para Adam.
     */
    private fun obtenerVelocity(tipoLoteria: String): MutableMap<String, Double> {
        val key = "adam_velocity_$tipoLoteria"
        val json = prefs.getString(key, null) ?: return mutableMapOf()

        return try {
            val jsonObj = JSONObject(json)
            CARACTERISTICAS.associateWith { car ->
                jsonObj.optDouble(car, 0.0)
            }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    /**
     * MEJORA 5: Guarda el estado de velocity para Adam.
     */
    private fun guardarVelocity(velocity: Map<String, Double>, tipoLoteria: String) {
        val key = "adam_velocity_$tipoLoteria"
        val json = JSONObject()
        velocity.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(key, json.toString()).apply()
    }
    
    private fun inicializarPesosDefault(tipoLoteria: String): Map<String, Double> {
        val pesos = pesosCaracteristicasDefault()
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
    /**
     * Reinicia la memoria de una lotería específica o toda la memoria.
     *
     * MEJORA 5: También reinicia los estados del optimizador Adam.
     */
    fun reiniciarMemoria(tipoLoteria: String? = null) {
        if (tipoLoteria != null) {
            // Reiniciar solo esa lotería (incluyendo estados de Adam)
            prefs.edit()
                .remove("pesos_$tipoLoteria")
                .remove("numeros_exitosos_$tipoLoteria")
                .remove("pares_exitosos_$tipoLoteria")
                .remove("entrenamientos_$tipoLoteria")
                .remove("mejor_punt_$tipoLoteria")
                .remove("ultima_act_$tipoLoteria")
                .remove("adam_momentum_$tipoLoteria")   // MEJORA 5
                .remove("adam_velocity_$tipoLoteria")   // MEJORA 5
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
        return ConfiguracionGenetica(tamanoPool = 12)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PESOS DEL MÉTODO DEL ABUELO (POR LOTERÍA)
    // Los 5 algoritmos matemáticos aprenden qué peso darle a cada uno
    // ═══════════════════════════════════════════════════════════════════════════════

    val ALGORITMOS_ABUELO = listOf(
        "chiCuadrado", "fourier", "bayesiano", "markov", "entropia"
    )

    /**
     * Obtiene los pesos aprendidos para los algoritmos del Método del Abuelo.
     * Cada algoritmo (Chi², Fourier, Bayes, Markov, Entropía) tiene un peso
     * que indica su fiabilidad para cada tipo de lotería.
     */
    fun obtenerPesosAbuelo(tipoLoteria: String): Map<String, Double> {
        val key = "pesos_abuelo_$tipoLoteria"
        val json = prefs.getString(key, null)

        return if (json != null) {
            try {
                val jsonObj = JSONObject(json)
                val pesos = ALGORITMOS_ABUELO.associateWith { alg ->
                    jsonObj.optDouble(alg, 1.0 / ALGORITMOS_ABUELO.size)
                }
                // Migración: si todos los pesos son iguales (uniformes del sistema antiguo),
                // reinicializar con priors no uniformes para permitir aprendizaje diferencial.
                val uniforme = 1.0 / ALGORITMOS_ABUELO.size
                val sonUniformes = pesos.values.all { kotlin.math.abs(it - uniforme) < 0.001 }
                if (sonUniformes) inicializarPesosAbueloDefault(tipoLoteria) else pesos
            } catch (e: Exception) {
                inicializarPesosAbueloDefault(tipoLoteria)
            }
        } else {
            inicializarPesosAbueloDefault(tipoLoteria)
        }
    }

    private fun inicializarPesosAbueloDefault(tipoLoteria: String): Map<String, Double> {
        // Pesos iniciales basados en fiabilidad empírica para loterías:
        // Chi² detecta sesgos reales en bolas → más fiable
        // Bayesiano: modelo probabilístico robusto → muy fiable
        // Fourier: periodicidades en lotería → moderado
        // Markov: dependencias secuenciales → menor fiabilidad en sorteos aleatorios
        // Entropía: concentración estadística → menor señal útil
        val pesos = mapOf(
            "chiCuadrado" to 0.30,
            "bayesiano"   to 0.25,
            "fourier"     to 0.20,
            "markov"      to 0.15,
            "entropia"    to 0.10
        )
        guardarPesosAbuelo(pesos, tipoLoteria)
        return pesos
    }

    private fun guardarPesosAbuelo(pesos: Map<String, Double>, tipoLoteria: String) {
        val key = "pesos_abuelo_$tipoLoteria"
        val json = JSONObject()
        pesos.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(key, json.toString()).apply()
    }

    /**
     * Actualiza los pesos del Abuelo usando Adam Optimizer.
     *
     * @param contribuciones Mapa de algoritmo → contribución al resultado.
     *   La contribución se calcula como: cuántos números del top-10 de cada algoritmo
     *   aparecieron en el resultado real.
     * @param aciertosTotal Número total de aciertos del Método del Abuelo en este sorteo.
     * @param tipoLoteria Tipo de lotería.
     */
    fun actualizarPesosAbuelo(
        contribuciones: Map<String, Double>,
        aciertosTotal: Int,
        tipoLoteria: String
    ) {
        val pesosAntes = obtenerPesosAbuelo(tipoLoteria)
        val pesosActuales = pesosAntes.toMutableMap()

        // Cargar estados de Adam específicos para Abuelo
        val momentum = obtenerMomentumAbuelo(tipoLoteria)
        val velocity = obtenerVelocityAbuelo(tipoLoteria)
        val t = (prefs.getInt("abuelo_train_count_$tipoLoteria", 0) + 1)

        val totalContrib = contribuciones.values.sum().coerceAtLeast(0.001)

        for (alg in ALGORITMOS_ABUELO) {
            val pesoActual = pesosActuales[alg] ?: (1.0 / ALGORITMOS_ABUELO.size)
            val contrib = contribuciones[alg] ?: 0.0
            val contribNorm = contrib / totalContrib

            // Gradiente: mover peso hacia la contribución normalizada
            val gradiente = (contribNorm - pesoActual)
            val gradienteConL2 = gradiente - L2_REGULARIZATION * pesoActual

            // Adam update
            val mPrev = momentum[alg] ?: 0.0
            val mNew = ADAM_BETA1 * mPrev + (1 - ADAM_BETA1) * gradienteConL2
            momentum[alg] = mNew

            val vPrev = velocity[alg] ?: 0.0
            val vNew = ADAM_BETA2 * vPrev + (1 - ADAM_BETA2) * gradienteConL2 * gradienteConL2
            velocity[alg] = vNew

            val mHat = mNew / (1 - Math.pow(ADAM_BETA1, t.toDouble()))
            val vHat = vNew / (1 - Math.pow(ADAM_BETA2, t.toDouble()))

            val delta = ADAM_LEARNING_RATE_ABUELO * mHat / (Math.sqrt(vHat) + ADAM_EPSILON)
            pesosActuales[alg] = (pesoActual + delta).coerceIn(0.05, 0.50)
        }

        // Normalizar
        val suma = pesosActuales.values.sum()
        if (suma > 0) {
            pesosActuales.forEach { (k, v) -> pesosActuales[k] = v / suma }
        }

        guardarPesosAbuelo(pesosActuales, tipoLoteria)
        guardarMomentumAbuelo(momentum, tipoLoteria)
        guardarVelocityAbuelo(velocity, tipoLoteria)
        prefs.edit().putInt("abuelo_train_count_$tipoLoteria", t).apply()

        LogAbuelo.aprendizaje("Abuelo", pesosAntes, pesosActuales)
    }

    /**
     * Obtiene el total de entrenamientos del Abuelo para una lotería.
     */
    fun obtenerEntrenamientosAbuelo(tipoLoteria: String): Int {
        return prefs.getInt("abuelo_train_count_$tipoLoteria", 0)
    }

    private fun obtenerMomentumAbuelo(tipoLoteria: String): MutableMap<String, Double> {
        val json = prefs.getString("adam_m_abuelo_$tipoLoteria", null) ?: return mutableMapOf()
        return try {
            val jsonObj = JSONObject(json)
            ALGORITMOS_ABUELO.associateWith { jsonObj.optDouble(it, 0.0) }.toMutableMap()
        } catch (e: Exception) { mutableMapOf() }
    }

    private fun guardarMomentumAbuelo(m: Map<String, Double>, tipoLoteria: String) {
        val json = JSONObject()
        m.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString("adam_m_abuelo_$tipoLoteria", json.toString()).apply()
    }

    private fun obtenerVelocityAbuelo(tipoLoteria: String): MutableMap<String, Double> {
        val json = prefs.getString("adam_v_abuelo_$tipoLoteria", null) ?: return mutableMapOf()
        return try {
            val jsonObj = JSONObject(json)
            ALGORITMOS_ABUELO.associateWith { jsonObj.optDouble(it, 0.0) }.toMutableMap()
        } catch (e: Exception) { mutableMapOf() }
    }

    private fun guardarVelocityAbuelo(v: Map<String, Double>, tipoLoteria: String) {
        val json = JSONObject()
        v.forEach { (k, v2) -> json.put(k, v2) }
        prefs.edit().putString("adam_v_abuelo_$tipoLoteria", json.toString()).apply()
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 9: FUNCIONES PARA ENSEMBLE VOTING
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Lista de estrategias disponibles (debe coincidir con EstrategiaPrediccion en MotorInteligencia).
     */
    private val ESTRATEGIAS = listOf(
        "GENETICO",
        "ALTA_CONFIANZA",
        "RACHAS_MIX",
        "EQUILIBRIO",
        "CICLOS",
        "CORRELACIONES",
        "FRECUENCIA",
        "TENDENCIA"
    )

    /**
     * MEJORA 9: Obtiene los pesos aprendidos para cada estrategia.
     *
     * Los pesos se ajustan con el tiempo según el rendimiento de cada estrategia.
     * Una estrategia que acierta más obtiene más peso.
     *
     * @return Mapa de estrategia -> peso, o null si no hay datos aprendidos
     */
    fun obtenerPesosEstrategias(tipoLoteria: String): Map<MotorInteligencia.EstrategiaPrediccion, Double>? {
        val key = "pesos_estrategias_$tipoLoteria"
        val json = prefs.getString(key, null) ?: return null

        return try {
            val jsonObj = JSONObject(json)
            MotorInteligencia.EstrategiaPrediccion.values().associateWith { estrategia ->
                jsonObj.optDouble(estrategia.name, 1.0)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * MEJORA 9: Guarda los pesos de las estrategias.
     */
    private fun guardarPesosEstrategias(
        pesos: Map<MotorInteligencia.EstrategiaPrediccion, Double>,
        tipoLoteria: String
    ) {
        val key = "pesos_estrategias_$tipoLoteria"
        val jsonObj = JSONObject()

        pesos.forEach { (estrategia, peso) ->
            jsonObj.put(estrategia.name, peso)
        }

        prefs.edit().putString(key, jsonObj.toString()).apply()
    }

    /**
     * MEJORA 9: Registra el rendimiento de cada estrategia para meta-aprendizaje.
     *
     * Después de cada sorteo real, se llama a esta función con los aciertos
     * de cada estrategia. Los pesos se actualizan para dar más peso a
     * las estrategias que aciertan más.
     *
     * @param aciertosEstrategia Mapa de estrategia -> número de aciertos
     * @param tipoLoteria Tipo de lotería
     */
    fun registrarRendimientoEstrategias(
        aciertosEstrategia: Map<MotorInteligencia.EstrategiaPrediccion, Int>,
        tipoLoteria: String
    ) = procesarRendimientoEstrategias(
        aciertosEstrategia.mapValues { it.value.toDouble() }, tipoLoteria
    )

    /**
     * Igual que [registrarRendimientoEstrategias] pero acepta aciertos medios en Double
     * (p. ej. la media de un lote de 10 iteraciones de AprendizajeContinuo).
     * @JvmName evita el choque de firmas por borrado de tipos (Map<_, Int> vs Map<_, Double>).
     */
    @JvmName("registrarRendimientoEstrategiasMedias")
    fun registrarRendimientoEstrategias(
        aciertosEstrategia: Map<MotorInteligencia.EstrategiaPrediccion, Double>,
        tipoLoteria: String
    ) = procesarRendimientoEstrategias(aciertosEstrategia, tipoLoteria)

    private fun procesarRendimientoEstrategias(
        aciertosEstrategia: Map<MotorInteligencia.EstrategiaPrediccion, Double>,
        tipoLoteria: String
    ) {
        // Guardar el sorteo actual en el historial ANTES de calcular promedios
        registrarHistorialEstrategias(aciertosEstrategia, tipoLoteria)

        // Ventana deslizante: promediar los últimos 20 sorteos para reducir ruido ~20x
        // Con 3 sorteos/semana converge en ~7 semanas en vez de ~33 semanas
        val promediosVentana = calcularPromediosVentana(tipoLoteria, ventana = 20)

        // Obtener pesos actuales o inicializar desde los calibrados por backtest.
        // CRÍTICO: no usar 1.0 uniforme — tiraría los defaults calibrados (TENDENCIA=1.6, CICLOS=0.2)
        val pesosActuales = obtenerPesosEstrategias(tipoLoteria)?.toMutableMap()
            ?: MotorInteligencia.pesosEnsembleDefault().toMutableMap()

        // Usar promedios de ventana si hay suficientes datos; si no, el sorteo individual
        val aciertosEfectivos: Map<MotorInteligencia.EstrategiaPrediccion, Double> =
            if (promediosVentana.size == aciertosEstrategia.size) promediosVentana
            else aciertosEstrategia

        val promedioGlobal = aciertosEfectivos.values.average()

        // Tasa conservadora: con ventana de 20, la señal ya es 20x más limpia
        val learningRate = 0.10
        aciertosEfectivos.forEach { (estrategia, media) ->
            val pesoActual = pesosActuales[estrategia] ?: 1.0
            val diferencia = (media - promedioGlobal) / 6.0
            val nuevoPeso = pesoActual + (diferencia * learningRate)
            pesosActuales[estrategia] = nuevoPeso.coerceIn(0.3, 2.0)
            LogAbuelo.ensemble(estrategia.name, media.toInt(), pesoActual, pesosActuales[estrategia]!!)
        }

        // Normalizar para que el promedio sea 1.0
        val promedioPesos = pesosActuales.values.average()
        if (promedioPesos > 0) {
            pesosActuales.forEach { (estrategia, peso) ->
                pesosActuales[estrategia] = peso / promedioPesos
            }
        }

        guardarPesosEstrategias(pesosActuales, tipoLoteria)
    }

    /**
     * Calcula el promedio de aciertos por estrategia sobre los últimos [ventana] sorteos.
     * Devuelve mapa vacío si no hay suficientes datos.
     */
    private fun calcularPromediosVentana(
        tipoLoteria: String,
        ventana: Int
    ): Map<MotorInteligencia.EstrategiaPrediccion, Double> {
        val key = "historial_estrategias_$tipoLoteria"
        val historialJson = prefs.getString(key, "[]") ?: "[]"

        return try {
            val historial = org.json.JSONArray(historialJson)
            val n = historial.length()
            val inicio = maxOf(0, n - ventana)

            val resultado = mutableMapOf<MotorInteligencia.EstrategiaPrediccion, Double>()
            for (estrategia in MotorInteligencia.EstrategiaPrediccion.values()) {
                val valores = (inicio until n).mapNotNull { i ->
                    val reg = historial.getJSONObject(i)
                    if (reg.has(estrategia.name)) reg.getDouble(estrategia.name) else null
                }
                if (valores.isNotEmpty()) resultado[estrategia] = valores.average()
            }
            resultado
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * MEJORA 9: Registra el historial de rendimiento de estrategias.
     */
    private fun registrarHistorialEstrategias(
        aciertos: Map<MotorInteligencia.EstrategiaPrediccion, Double>,
        tipoLoteria: String
    ) {
        val key = "historial_estrategias_$tipoLoteria"
        val historialJson = prefs.getString(key, "[]") ?: "[]"

        try {
            val historial = org.json.JSONArray(historialJson)

            // Limitar a últimos 100 registros
            while (historial.length() >= 100) {
                historial.remove(0)
            }

            // Añadir nuevo registro
            val registro = JSONObject()
            registro.put("fecha", System.currentTimeMillis())
            aciertos.forEach { (estrategia, numAciertos) ->
                registro.put(estrategia.name, numAciertos)
            }
            historial.put(registro)

            prefs.edit().putString(key, historial.toString()).apply()
        } catch (e: Exception) {
            // Ignorar errores de JSON
        }
    }

    /**
     * MEJORA 9: Obtiene estadísticas de rendimiento de las estrategias.
     *
     * @return Mapa de estrategia -> (promedio de aciertos, mejor acierto)
     */
    fun obtenerEstadisticasEstrategias(tipoLoteria: String): Map<String, Pair<Double, Int>> {
        val key = "historial_estrategias_$tipoLoteria"
        val historialJson = prefs.getString(key, "[]") ?: "[]"

        val estadisticas = mutableMapOf<String, Pair<Double, Int>>()

        try {
            val historial = org.json.JSONArray(historialJson)

            for (estrategia in ESTRATEGIAS) {
                val aciertosLista = mutableListOf<Int>()

                for (i in 0 until historial.length()) {
                    val registro = historial.getJSONObject(i)
                    if (registro.has(estrategia)) {
                        aciertosLista.add(registro.getInt(estrategia))
                    }
                }

                if (aciertosLista.isNotEmpty()) {
                    val promedio = aciertosLista.average()
                    val mejor = aciertosLista.maxOrNull() ?: 0
                    estadisticas[estrategia] = Pair(promedio, mejor)
                }
            }
        } catch (e: Exception) {
            // Ignorar errores
        }

        return estadisticas
    }

    /**
     * MEJORA 9: Obtiene la mejor estrategia para una lotería específica.
     */
    fun obtenerMejorEstrategia(tipoLoteria: String): String? {
        val pesos = obtenerPesosEstrategias(tipoLoteria) ?: return null
        return pesos.maxByOrNull { it.value }?.key?.name
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MEJORA 13: HISTORIAL DE PREDICCIONES
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Guarda una predicción en el historial.
     */
    fun guardarPrediccion(
        fecha: String,
        tipoLoteria: String,
        metodo: String,
        combinacion: List<Int>,
        complementarios: List<Int>
    ) {
        val key = "historial_predicciones_$tipoLoteria"
        val historialJson = prefs.getString(key, "[]") ?: "[]"

        try {
            val historial = org.json.JSONArray(historialJson)

            val registro = JSONObject().apply {
                put("fecha", fecha)
                put("metodo", metodo)
                put("combinacion", org.json.JSONArray(combinacion))
                put("complementarios", org.json.JSONArray(complementarios))
                put("resultadoReal", org.json.JSONArray())
                put("complementariosReales", org.json.JSONArray())
                put("aciertos", -1)  // -1 = pendiente
                put("aciertosComplementarios", -1)
            }

            historial.put(registro)

            // Limitar a últimas 100 predicciones
            while (historial.length() > 100) {
                historial.remove(0)
            }

            prefs.edit().putString(key, historial.toString()).apply()
        } catch (e: Exception) {
            // Ignorar errores
        }
    }

    /**
     * Actualiza una predicción con el resultado real.
     */
    fun actualizarPrediccionConResultado(
        fecha: String,
        tipoLoteria: String,
        resultadoReal: List<Int>,
        complementariosReales: List<Int>
    ) {
        val key = "historial_predicciones_$tipoLoteria"
        val historialJson = prefs.getString(key, "[]") ?: "[]"

        try {
            val historial = org.json.JSONArray(historialJson)
            val nuevoHistorial = org.json.JSONArray()

            for (i in 0 until historial.length()) {
                val registro = historial.getJSONObject(i)

                // Si es la fecha correcta y aún no tiene resultado
                if (registro.getString("fecha") == fecha && registro.getInt("aciertos") == -1) {
                    // Obtener combinación predicha
                    val combJson = registro.getJSONArray("combinacion")
                    val combinacion = (0 until combJson.length()).map { combJson.getInt(it) }

                    val compJson = registro.getJSONArray("complementarios")
                    val complementarios = (0 until compJson.length()).map { compJson.getInt(it) }

                    // Calcular aciertos
                    val aciertos = combinacion.count { it in resultadoReal }
                    val aciertosComp = complementarios.count { it in complementariosReales }

                    // Actualizar registro
                    registro.put("resultadoReal", org.json.JSONArray(resultadoReal))
                    registro.put("complementariosReales", org.json.JSONArray(complementariosReales))
                    registro.put("aciertos", aciertos)
                    registro.put("aciertosComplementarios", aciertosComp)
                }

                nuevoHistorial.put(registro)
            }

            prefs.edit().putString(key, nuevoHistorial.toString()).apply()
        } catch (e: Exception) {
            // Ignorar errores
        }
    }

    /**
     * Obtiene el historial de predicciones.
     */
    fun obtenerHistorialPredicciones(
        tipoLoteria: String,
        limite: Int
    ): List<MotorInteligencia.RegistroPrediccion> {
        val key = "historial_predicciones_$tipoLoteria"
        val historialJson = prefs.getString(key, "[]") ?: "[]"

        return try {
            val historial = org.json.JSONArray(historialJson)
            val lista = mutableListOf<MotorInteligencia.RegistroPrediccion>()

            // Iterar desde el más reciente
            for (i in (historial.length() - 1) downTo maxOf(0, historial.length() - limite)) {
                val registro = historial.getJSONObject(i)

                val combJson = registro.getJSONArray("combinacion")
                val combinacion = (0 until combJson.length()).map { combJson.getInt(it) }

                val compJson = registro.getJSONArray("complementarios")
                val complementarios = (0 until compJson.length()).map { compJson.getInt(it) }

                val resJson = registro.getJSONArray("resultadoReal")
                val resultadoReal = if (resJson.length() > 0) {
                    (0 until resJson.length()).map { resJson.getInt(it) }
                } else null

                val compRealesJson = registro.getJSONArray("complementariosReales")
                val complementariosReales = if (compRealesJson.length() > 0) {
                    (0 until compRealesJson.length()).map { compRealesJson.getInt(it) }
                } else null

                val aciertos = registro.getInt("aciertos")
                val aciertosComp = registro.getInt("aciertosComplementarios")

                lista.add(MotorInteligencia.RegistroPrediccion(
                    fecha = registro.getString("fecha"),
                    tipoLoteria = tipoLoteria,
                    metodo = registro.getString("metodo"),
                    combinacion = combinacion,
                    complementarios = complementarios,
                    resultadoReal = resultadoReal,
                    complementariosReales = complementariosReales,
                    aciertos = if (aciertos >= 0) aciertos else null,
                    aciertosComplementarios = if (aciertosComp >= 0) aciertosComp else null
                ))
            }

            lista
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Configuración del algoritmo genético MEJORADO.
 *
 * Mejoras implementadas:
 * - Mutación adaptativa: decrece conforme avanzan las generaciones
 * - Población dinámica: se ajusta según tamaño del histórico
 * - Crossover uniforme: mejor mezcla de genes
 */
data class ConfiguracionGenetica(
    val poblacionBase: Int = 500,           // Población base (se ajusta dinámicamente)
    val generaciones: Int = 60,             // Más generaciones para mejor convergencia
    val tasaMutacionInicial: Double = 0.20, // Mutación inicial alta (exploración)
    val tasaMutacionFinal: Double = 0.05,   // Mutación final baja (explotación)
    val tasaCruce: Double = 0.75,           // Tasa de crossover aumentada
    val elitismo: Double = 0.08,            // Elitismo reducido para más diversidad
    val tamanoPool: Int = 10,               // Cuántos números top considerar de cada categoría (5-25)
    val usarCrossoverUniforme: Boolean = true,  // Usar crossover uniforme en lugar de punto simple
    val torneoSize: Int = 4                 // Tamaño del torneo para selección
) {
    // Para compatibilidad con código existente
    val poblacion: Int get() = poblacionBase
    val tasaMutacion: Double get() = tasaMutacionInicial

    companion object {
        const val POBLACION_MIN = 300
        const val POBLACION_MAX = 1000
    }

    /**
     * Calcula la población dinámica basada en el tamaño del histórico.
     * Más datos históricos = población más grande para explorar mejor el espacio.
     */
    fun calcularPoblacionDinamica(historicoSize: Int): Int {
        val factor = 1.5
        val poblacionCalculada = (poblacionBase + (historicoSize * factor)).toInt()
        return poblacionCalculada.coerceIn(POBLACION_MIN, POBLACION_MAX)
    }

    /**
     * Calcula la tasa de mutación adaptativa según la generación actual.
     * Decrece linealmente de tasaMutacionInicial a tasaMutacionFinal.
     */
    fun calcularTasaMutacionAdaptativa(generacionActual: Int): Double {
        val progreso = generacionActual.toDouble() / generaciones.coerceAtLeast(1)
        return tasaMutacionInicial - (tasaMutacionInicial - tasaMutacionFinal) * progreso
    }
}

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
