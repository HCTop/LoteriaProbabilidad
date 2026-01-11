package com.loteria.probabilidad.domain.ml

import android.content.Context
import android.content.SharedPreferences
import com.loteria.probabilidad.data.model.MetodoCalculo
import com.loteria.probabilidad.data.model.ResultadoBacktest
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gestiona la persistencia de logs y resultados de backtesting.
 * Los logs se mantienen hasta el día siguiente.
 * Los resultados se mantienen hasta el próximo entrenamiento.
 */
class BacktestPersistencia(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "backtest_persistencia", Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_LOGS_PREFIX = "logs_"
        private const val KEY_LOGS_DATE_PREFIX = "logs_date_"
        private const val KEY_RESULTADOS_PREFIX = "resultados_"
        private const val KEY_ULTIMO_ENTRENAMIENTO_PREFIX = "ultimo_entrenamiento_"
        private const val MAX_LOGS = 500  // Máximo de logs a guardar
    }
    
    // ==================== LOGS ====================
    
    /**
     * Guarda un nuevo log para una lotería específica.
     */
    fun agregarLog(tipoLoteria: String, mensaje: String) {
        val logs = obtenerLogs(tipoLoteria).toMutableList()
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logConTimestamp = "[$timestamp] $mensaje"
        
        logs.add(logConTimestamp)
        
        // Limitar cantidad de logs
        val logsLimitados = if (logs.size > MAX_LOGS) {
            logs.takeLast(MAX_LOGS)
        } else {
            logs
        }
        
        guardarLogs(tipoLoteria, logsLimitados)
    }
    
    /**
     * Obtiene todos los logs de una lotería.
     * Limpia automáticamente si son del día anterior.
     */
    fun obtenerLogs(tipoLoteria: String): List<String> {
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val fechaLogs = prefs.getString(KEY_LOGS_DATE_PREFIX + tipoLoteria, "") ?: ""
        
        // Si los logs son de un día anterior, limpiarlos
        if (fechaLogs.isNotEmpty() && fechaLogs != hoy) {
            limpiarLogs(tipoLoteria)
            return emptyList()
        }
        
        val logsJson = prefs.getString(KEY_LOGS_PREFIX + tipoLoteria, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(logsJson)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Guarda la lista de logs.
     */
    private fun guardarLogs(tipoLoteria: String, logs: List<String>) {
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val jsonArray = JSONArray(logs)
        
        prefs.edit()
            .putString(KEY_LOGS_PREFIX + tipoLoteria, jsonArray.toString())
            .putString(KEY_LOGS_DATE_PREFIX + tipoLoteria, hoy)
            .apply()
    }
    
    /**
     * Limpia los logs de una lotería.
     */
    fun limpiarLogs(tipoLoteria: String) {
        prefs.edit()
            .remove(KEY_LOGS_PREFIX + tipoLoteria)
            .remove(KEY_LOGS_DATE_PREFIX + tipoLoteria)
            .apply()
    }
    
    // ==================== RESULTADOS ====================
    
    /**
     * Guarda los resultados del último backtesting.
     */
    fun guardarResultados(tipoLoteria: String, resultados: List<ResultadoBacktest>) {
        val jsonArray = JSONArray()
        
        for (resultado in resultados) {
            val json = JSONObject().apply {
                put("metodo", resultado.metodo.name)
                put("sorteosProbados", resultado.sorteosProbados)
                put("aciertos0", resultado.aciertos0)
                put("aciertos1", resultado.aciertos1)
                put("aciertos2", resultado.aciertos2)
                put("aciertos3", resultado.aciertos3)
                put("aciertos4", resultado.aciertos4)
                put("aciertos5", resultado.aciertos5)
                put("aciertos6", resultado.aciertos6)
                put("aciertosComplementario", resultado.aciertosComplementario)
                put("aciertosReintegro", resultado.aciertosReintegro)
                put("aciertosEstrella1", resultado.aciertosEstrella1)
                put("aciertosEstrella2", resultado.aciertosEstrella2)
                put("aciertosClave", resultado.aciertosClave)
                put("puntuacionTotal", resultado.puntuacionTotal)
                put("mejorAcierto", resultado.mejorAcierto)
                put("promedioAciertos", resultado.promedioAciertos)
                put("tipoLoteria", resultado.tipoLoteria)
            }
            jsonArray.put(json)
        }
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        
        prefs.edit()
            .putString(KEY_RESULTADOS_PREFIX + tipoLoteria, jsonArray.toString())
            .putString(KEY_ULTIMO_ENTRENAMIENTO_PREFIX + tipoLoteria, timestamp)
            .apply()
    }
    
    /**
     * Obtiene los resultados del último backtesting.
     */
    fun obtenerResultados(tipoLoteria: String): List<ResultadoBacktest> {
        val resultadosJson = prefs.getString(KEY_RESULTADOS_PREFIX + tipoLoteria, "[]") ?: "[]"
        
        return try {
            val jsonArray = JSONArray(resultadosJson)
            (0 until jsonArray.length()).map { i ->
                val json = jsonArray.getJSONObject(i)
                ResultadoBacktest(
                    metodo = MetodoCalculo.valueOf(json.getString("metodo")),
                    sorteosProbados = json.getInt("sorteosProbados"),
                    aciertos0 = json.getInt("aciertos0"),
                    aciertos1 = json.getInt("aciertos1"),
                    aciertos2 = json.getInt("aciertos2"),
                    aciertos3 = json.getInt("aciertos3"),
                    aciertos4 = json.getInt("aciertos4"),
                    aciertos5 = json.optInt("aciertos5", 0),
                    aciertos6 = json.optInt("aciertos6", 0),
                    aciertosComplementario = json.optInt("aciertosComplementario", 0),
                    aciertosReintegro = json.optInt("aciertosReintegro", 0),
                    aciertosEstrella1 = json.optInt("aciertosEstrella1", 0),
                    aciertosEstrella2 = json.optInt("aciertosEstrella2", 0),
                    aciertosClave = json.optInt("aciertosClave", 0),
                    puntuacionTotal = json.getDouble("puntuacionTotal"),
                    mejorAcierto = json.getInt("mejorAcierto"),
                    promedioAciertos = json.getDouble("promedioAciertos"),
                    tipoLoteria = json.optString("tipoLoteria", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Obtiene la fecha del último entrenamiento.
     */
    fun obtenerFechaUltimoEntrenamiento(tipoLoteria: String): String {
        return prefs.getString(KEY_ULTIMO_ENTRENAMIENTO_PREFIX + tipoLoteria, "") ?: ""
    }
    
    /**
     * Limpia los resultados de una lotería.
     */
    fun limpiarResultados(tipoLoteria: String) {
        prefs.edit()
            .remove(KEY_RESULTADOS_PREFIX + tipoLoteria)
            .remove(KEY_ULTIMO_ENTRENAMIENTO_PREFIX + tipoLoteria)
            .apply()
    }
}
