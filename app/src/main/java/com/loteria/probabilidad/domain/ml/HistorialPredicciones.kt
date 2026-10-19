package com.loteria.probabilidad.domain.ml

import android.content.Context
import android.content.SharedPreferences
import com.loteria.probabilidad.data.model.CombinacionSugerida
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Sistema de persistencia de predicciones.
 *
 * Guarda las predicciones generadas para cada sorteo y las evalúa
 * contra los resultados reales cuando están disponibles.
 * Mantiene un historial de los últimos 20 sorteos evaluados.
 *
 * FLUJO:
 * 1. Al abrir la app → comprobar si hay predicciones guardadas para el próximo sorteo
 * 2. Si SÍ → mostrarlas (no regenerar)
 * 3. Si NO → generar nuevas, guardarlas, mostrarlas
 * 4. Cuando se actualiza el histórico → evaluar predicciones pendientes vs resultados reales
 * 5. Guardar evaluación en historial (últimos 20 sorteos)
 */
class HistorialPredicciones(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "historial_predicciones_v1", Context.MODE_PRIVATE
    )

    companion object {
        /** Hasta 1.000 simulados (botón "Simular") + margen para los reales. */
        private const val MAX_HISTORIAL = 1100

        private val DIAS_SORTEO = mapOf(
            "PRIMITIVA" to listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
            "BONOLOTO" to listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
            ),
            "EUROMILLONES" to listOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
            "GORDO_PRIMITIVA" to listOf(DayOfWeek.SUNDAY),
            "LOTERIA_NACIONAL" to listOf(DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
            "NAVIDAD" to emptyList(),
            "NINO" to emptyList()
        )
    }

    // ==================== CÁLCULO DE FECHAS ====================

    /**
     * Calcula la fecha del próximo sorteo para una lotería.
     * Si hoy es día de sorteo, devuelve hoy.
     */
    fun proximoSorteo(tipoLoteria: String): LocalDate {
        val hoy = LocalDate.now()
        // Sorteos anuales con fecha fija (antes devolvían "hoy")
        when (tipoLoteria) {
            "NAVIDAD" -> return proximaFechaAnual(hoy, 12, 22)
            "NINO" -> return proximaFechaAnual(hoy, 1, 6)
        }
        val diasSorteo = DIAS_SORTEO[tipoLoteria]
        if (diasSorteo.isNullOrEmpty()) return hoy
        // Si hoy es día de sorteo pero ya pasaron las 22:00, el sorteo ya ocurrió
        val horaLimite = LocalTime.of(22, 0)
        val sorteoHoyYaPaso = hoy.dayOfWeek in diasSorteo && LocalTime.now() >= horaLimite
        val inicio = if (sorteoHoyYaPaso) 1L else 0L
        return (inicio..7L).map { hoy.plusDays(it) }
            .first { it.dayOfWeek in diasSorteo }
    }

    /** Próxima ocurrencia (hoy incluido) de un día/mes fijo: 22-dic Navidad, 6-ene Niño. */
    private fun proximaFechaAnual(hoy: LocalDate, mes: Int, dia: Int): LocalDate {
        val esteAno = LocalDate.of(hoy.year, mes, dia)
        return if (hoy.isAfter(esteAno)) esteAno.plusYears(1) else esteAno
    }

    /**
     * Comprueba si una lotería soporta evaluación automática.
     * Las loterías de 5 dígitos (Nacional, Navidad, Niño) no se evalúan por bolas.
     */
    fun soportaEvaluacion(tipoLoteria: String): Boolean {
        return tipoLoteria in listOf("PRIMITIVA", "BONOLOTO", "EUROMILLONES", "GORDO_PRIMITIVA")
    }

    // ==================== PREDICCIONES ====================

    /**
     * Comprueba si hay predicciones guardadas para un sorteo.
     */
    fun tienePrediccionesGuardadas(tipoLoteria: String, fechaSorteo: String): Boolean {
        return prefs.contains("pred_${tipoLoteria}_$fechaSorteo")
    }

    /**
     * Guarda las predicciones para un sorteo.
     */
    fun guardarPredicciones(
        tipoLoteria: String,
        fechaSorteo: String,
        combinaciones: List<CombinacionSugerida>
    ) {
        val jsonArray = JSONArray()
        for (comb in combinaciones) {
            jsonArray.put(JSONObject().apply {
                put("numeros", JSONArray(comb.numeros))
                put("complementarios", JSONArray(comb.complementarios))
                put("probabilidad", comb.probabilidadRelativa)
                put("explicacion", comb.explicacion)
            })
        }
        prefs.edit()
            .putString("pred_${tipoLoteria}_$fechaSorteo", jsonArray.toString())
            .putString("fecha_gen_${tipoLoteria}_$fechaSorteo", LocalDate.now().toString())
            .apply()
    }

    /**
     * Carga las predicciones guardadas para un sorteo.
     * @return Lista de combinaciones o null si no hay predicciones guardadas.
     */
    fun cargarPredicciones(tipoLoteria: String, fechaSorteo: String): List<CombinacionSugerida>? {
        val json = prefs.getString("pred_${tipoLoteria}_$fechaSorteo", null) ?: return null
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                CombinacionSugerida(
                    numeros = obj.getJSONArray("numeros").toIntList(),
                    complementarios = obj.getJSONArray("complementarios").toIntList(),
                    probabilidadRelativa = obj.getDouble("probabilidad"),
                    explicacion = obj.getString("explicacion")
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Elimina las predicciones para un sorteo (para forzar regeneración).
     */
    fun eliminarPredicciones(tipoLoteria: String, fechaSorteo: String) {
        prefs.edit()
            .remove("pred_${tipoLoteria}_$fechaSorteo")
            .remove("fecha_gen_${tipoLoteria}_$fechaSorteo")
            .apply()
    }

    /**
     * Obtiene la fecha en que se generaron las predicciones para un sorteo.
     */
    fun fechaGeneracion(tipoLoteria: String, fechaSorteo: String): String? {
        return prefs.getString("fecha_gen_${tipoLoteria}_$fechaSorteo", null)
    }

    // ==================== EVALUACIÓN ====================

    /**
     * Evalúa las predicciones guardadas contra el resultado real de un sorteo.
     * Solo evalúa si hay predicciones guardadas Y aún no se han evaluado.
     *
     * @return Lista de evaluaciones por método, o null si ya estaba evaluado o no había predicciones.
     */
    fun evaluarPredicciones(
        tipoLoteria: String,
        fechaSorteo: String,
        numerosReales: List<Int>,
        complementariosReales: List<Int> = emptyList()
    ): List<ResultadoEvaluacion>? {
        if (!soportaEvaluacion(tipoLoteria)) return null
        if (prefs.contains("eval_${tipoLoteria}_$fechaSorteo")) return null

        val predicciones = cargarPredicciones(tipoLoteria, fechaSorteo) ?: return null

        val evaluaciones = calcularEvaluaciones(predicciones, numerosReales, complementariosReales)

        // Guardar evaluación en historial
        guardarEvaluacion(tipoLoteria, fechaSorteo, evaluaciones)

        // Marcar como evaluado
        prefs.edit().putString("eval_${tipoLoteria}_$fechaSorteo", "done").apply()

        return evaluaciones
    }

    /**
     * Compara una lista de combinaciones con el resultado real de un sorteo.
     * El nombre del método sale de la primera línea de la explicación.
     */
    fun calcularEvaluaciones(
        predicciones: List<CombinacionSugerida>,
        numerosReales: List<Int>,
        complementariosReales: List<Int> = emptyList()
    ): List<ResultadoEvaluacion> {
        return predicciones.map { pred ->
            val aciertosNumeros = pred.numeros.count { it in numerosReales }
            val aciertosComplementarios = pred.complementarios.count { it in complementariosReales }

            // Extraer nombre del método de la explicación (formato: "🔮📐 Método del Abuelo\n...")
            val metodo = pred.explicacion.split("\n").firstOrNull()?.trim() ?: "Desconocido"

            ResultadoEvaluacion(
                metodo = metodo,
                numerosPredichos = pred.numeros,
                complementariosPredichos = pred.complementarios,
                aciertosNumeros = aciertosNumeros,
                aciertosComplementarios = aciertosComplementarios,
                numerosReales = numerosReales
            )
        }
    }

    /**
     * Guarda una evaluación SIMULADA de un sorteo pasado: predicciones generadas solo con
     * los sorteos anteriores a esa fecha (walk-forward). Sirve para tener historial desde
     * el primer día. No toca el aprendizaje. Si ese sorteo ya tiene evaluación real, no hace nada.
     */
    fun guardarEvaluacionSimulada(
        tipoLoteria: String,
        fechaSorteo: String,
        evaluaciones: List<ResultadoEvaluacion>,
        reemplazarSimulada: Boolean = false
    ) = guardarEvaluacion(tipoLoteria, fechaSorteo, evaluaciones, simulado = true, reemplazarSimulada = reemplazarSimulada)

    /** Entrada del historial (real o simulada) de ese sorteo, o null. */
    fun entradaHistorial(tipoLoteria: String, fechaSorteo: String): EntradaHistorial? =
        cargarHistorialInterno(tipoLoteria).firstOrNull { it.fechaSorteo == fechaSorteo }

    /** ¿Hay ya una entrada (real o simulada) para ese sorteo? */
    fun tieneEntradaHistorial(tipoLoteria: String, fechaSorteo: String): Boolean =
        cargarHistorialInterno(tipoLoteria).any { it.fechaSorteo == fechaSorteo }

    /**
     * Comprueba si un sorteo ya fue evaluado.
     */
    fun estaEvaluado(tipoLoteria: String, fechaSorteo: String): Boolean {
        return prefs.contains("eval_${tipoLoteria}_$fechaSorteo")
    }

    /**
     * Guarda la evaluación en el historial.
     */
    private fun guardarEvaluacion(
        tipoLoteria: String,
        fechaSorteo: String,
        evaluaciones: List<ResultadoEvaluacion>,
        simulado: Boolean = false,
        reemplazarSimulada: Boolean = false
    ) {
        val historial = cargarHistorialInterno(tipoLoteria).toMutableList()

        // No duplicar. Una evaluación REAL sí sustituye a una simulada del mismo sorteo,
        // y una simulada solo sustituye a otra simulada si se pide (le faltaban métodos).
        // Una real nunca se sustituye.
        val existente = historial.firstOrNull { it.fechaSorteo == fechaSorteo }
        if (existente != null) {
            if (!existente.simulado || (simulado && !reemplazarSimulada)) return
            historial.remove(existente)
        }

        val mejorEval = evaluaciones.maxByOrNull { it.aciertosNumeros }
        historial.add(EntradaHistorial(
            fechaSorteo = fechaSorteo,
            evaluaciones = evaluaciones,
            mejorMetodo = mejorEval?.metodo ?: "",
            mejorAciertos = mejorEval?.aciertosNumeros ?: 0,
            simulado = simulado
        ))
        // Siempre del más reciente al más antiguo (el relleno simulado mete sorteos viejos)
        historial.sortByDescending { it.fechaSorteo }

        // Mantener solo los últimos MAX_HISTORIAL
        while (historial.size > MAX_HISTORIAL) historial.removeAt(historial.lastIndex)

        escribirHistorial(tipoLoteria, historial)
    }

    /**
     * Guarda de una vez una tanda de sorteos SIMULADOS (fecha, evaluaciones, reemplazar).
     * Leer y escribir el historial entero por cada sorteo sería muy lento con 1.000.
     * Mismas reglas que [guardarEvaluacion]: una real nunca se sustituye y una simulada
     * solo si reemplazar = true.
     */
    fun guardarEvaluacionesSimuladas(
        tipoLoteria: String,
        tanda: List<Triple<String, List<ResultadoEvaluacion>, Boolean>>
    ) {
        if (tanda.isEmpty()) return
        val historial = cargarHistorialInterno(tipoLoteria).toMutableList()
        for ((fecha, evaluaciones, reemplazar) in tanda) {
            val existente = historial.firstOrNull { it.fechaSorteo == fecha }
            if (existente != null) {
                if (!existente.simulado || !reemplazar) continue
                historial.remove(existente)
            }
            val mejorEval = evaluaciones.maxByOrNull { it.aciertosNumeros }
            historial.add(EntradaHistorial(
                fechaSorteo = fecha,
                evaluaciones = evaluaciones,
                mejorMetodo = mejorEval?.metodo ?: "",
                mejorAciertos = mejorEval?.aciertosNumeros ?: 0,
                simulado = true
            ))
        }
        historial.sortByDescending { it.fechaSorteo }
        while (historial.size > MAX_HISTORIAL) historial.removeAt(historial.lastIndex)
        escribirHistorial(tipoLoteria, historial)
    }

    /** Borra del historial las entradas simuladas (las evaluaciones reales se conservan). */
    fun borrarSimulados(tipoLoteria: String) {
        escribirHistorial(tipoLoteria, cargarHistorialInterno(tipoLoteria).filter { !it.simulado })
    }

    private fun escribirHistorial(tipoLoteria: String, historial: List<EntradaHistorial>) {
        // Serializar y guardar
        val jsonArray = JSONArray()
        for (entrada in historial) {
            jsonArray.put(JSONObject().apply {
                put("fechaSorteo", entrada.fechaSorteo)
                put("mejorMetodo", entrada.mejorMetodo)
                put("mejorAciertos", entrada.mejorAciertos)
                put("simulado", entrada.simulado)
                put("evaluaciones", JSONArray().apply {
                    for (eval in entrada.evaluaciones) {
                        put(JSONObject().apply {
                            put("metodo", eval.metodo)
                            put("numeros", JSONArray(eval.numerosPredichos))
                            put("complementarios", JSONArray(eval.complementariosPredichos))
                            put("aciertosNum", eval.aciertosNumeros)
                            put("aciertosComp", eval.aciertosComplementarios)
                            put("reales", JSONArray(eval.numerosReales))
                        })
                    }
                })
            })
        }

        prefs.edit()
            .putString("historial_$tipoLoteria", jsonArray.toString())
            .apply()
    }

    // ==================== HISTORIAL ====================

    /**
     * Carga el historial de evaluaciones para una lotería.
     */
    fun cargarHistorial(tipoLoteria: String): List<EntradaHistorial> = cargarHistorialInterno(tipoLoteria)

    private fun cargarHistorialInterno(tipoLoteria: String): List<EntradaHistorial> {
        val json = prefs.getString("historial_$tipoLoteria", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                val evalsArr = obj.getJSONArray("evaluaciones")
                val evaluaciones = (0 until evalsArr.length()).map { j ->
                    val evalObj = evalsArr.getJSONObject(j)
                    ResultadoEvaluacion(
                        metodo = evalObj.getString("metodo"),
                        numerosPredichos = evalObj.getJSONArray("numeros").toIntList(),
                        complementariosPredichos = evalObj.getJSONArray("complementarios").toIntList(),
                        aciertosNumeros = evalObj.getInt("aciertosNum"),
                        aciertosComplementarios = evalObj.getInt("aciertosComp"),
                        numerosReales = evalObj.getJSONArray("reales").toIntList()
                    )
                }
                EntradaHistorial(
                    fechaSorteo = obj.getString("fechaSorteo"),
                    evaluaciones = evaluaciones,
                    mejorMetodo = obj.getString("mejorMetodo"),
                    mejorAciertos = obj.getInt("mejorAciertos"),
                    simulado = obj.optBoolean("simulado", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Obtiene el mejor método según el historial de evaluaciones.
     * @return Par de (nombre del método, promedio de aciertos) o null si no hay historial.
     */
    fun obtenerMejorMetodoHistorico(tipoLoteria: String): Pair<String, Double>? {
        val historial = cargarHistorial(tipoLoteria)
        if (historial.isEmpty()) return null

        val scores = mutableMapOf<String, MutableList<Int>>()
        for (entrada in historial) {
            for (eval in entrada.evaluaciones) {
                scores.getOrPut(eval.metodo) { mutableListOf() }.add(eval.aciertosNumeros)
            }
        }

        return scores.maxByOrNull { it.value.average() }
            ?.let { it.key to it.value.average() }
    }

    /**
     * Obtiene un ranking completo de todos los métodos por rendimiento histórico.
     * @return Lista de (nombre método, promedio aciertos, mejor acierto) ordenado de mejor a peor.
     */
    /** Cuántos sorteos recientes cuentan para elegir el mejor método (lo elige el usuario en 🏆). */
    fun ventanaRanking(tipoLoteria: String): Int = prefs.getInt("ventana_ranking_$tipoLoteria", 1000)

    fun guardarVentanaRanking(tipoLoteria: String, sorteos: Int) {
        prefs.edit().putInt("ventana_ranking_$tipoLoteria", sorteos).apply()
    }

    /**
     * Método que más acierta en los últimos [ventanaRanking] sorteos del historial.
     * MISMO criterio que la tarjeta 🏆: más premios (≥ [minPremio] aciertos), luego mayor
     * máximo, luego mejor media. Devuelve (método, media) o null si no hay historial.
     */
    fun metodoLider(tipoLoteria: String, minPremio: Int): Pair<String, Double>? {
        val historial = cargarHistorial(tipoLoteria).take(ventanaRanking(tipoLoteria))
        val aciertos = mutableMapOf<String, MutableList<Int>>()
        for (entrada in historial) for (eval in entrada.evaluaciones) {
            aciertos.getOrPut(eval.metodo) { mutableListOf() }.add(eval.aciertosNumeros)
        }
        return aciertos.entries.sortedWith(
            compareByDescending<Map.Entry<String, MutableList<Int>>> { e -> e.value.count { it >= minPremio } }
                .thenByDescending { e -> e.value.max() }
                .thenByDescending { e -> e.value.average() }
        ).firstOrNull()?.let { it.key to it.value.average() }
    }

    fun obtenerRankingMetodos(tipoLoteria: String): List<Triple<String, Double, Int>> {
        val historial = cargarHistorial(tipoLoteria)
        if (historial.isEmpty()) return emptyList()

        val scores = mutableMapOf<String, MutableList<Int>>()
        for (entrada in historial) {
            for (eval in entrada.evaluaciones) {
                scores.getOrPut(eval.metodo) { mutableListOf() }.add(eval.aciertosNumeros)
            }
        }

        return scores.map { (metodo, aciertos) ->
            Triple(metodo, aciertos.average(), aciertos.max())
        }.sortedByDescending { it.second }
    }

    /**
     * Limpia predicciones antiguas (más de 60 días).
     */
    fun limpiarPrediccionesAntiguas(tipoLoteria: String) {
        val hoy = LocalDate.now()
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("pred_${tipoLoteria}_") }
            .forEach { key ->
                val fecha = key.removePrefix("pred_${tipoLoteria}_")
                try {
                    if (LocalDate.parse(fecha).isBefore(hoy.minusDays(60))) {
                        editor.remove(key)
                        editor.remove("fecha_gen_${tipoLoteria}_$fecha")
                        editor.remove("eval_${tipoLoteria}_$fecha")
                    }
                } catch (_: Exception) { }
            }
        editor.apply()
    }

    // ==================== UTILIDADES ====================

    private fun JSONArray.toIntList(): List<Int> =
        (0 until length()).map { getInt(it) }

    // ==================== DATA CLASSES ====================

    data class ResultadoEvaluacion(
        val metodo: String,
        val numerosPredichos: List<Int>,
        val complementariosPredichos: List<Int>,
        val aciertosNumeros: Int,
        val aciertosComplementarios: Int,
        val numerosReales: List<Int>
    )

    data class EntradaHistorial(
        val fechaSorteo: String,
        val evaluaciones: List<ResultadoEvaluacion>,
        val mejorMetodo: String,
        val mejorAciertos: Int,
        /** true = reconstruido a posteriori con walk-forward, no guardado antes del sorteo */
        val simulado: Boolean = false
    )
}
