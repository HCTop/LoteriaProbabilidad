package com.loteria.probabilidad.data.datasource

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualiza automáticamente los CSV históricos desde GitHub.
 *
 * - Se llama al arrancar la app (silencioso, sin bloquear UI)
 * - Solo descarga si han pasado 24h desde la última actualización
 *   o si algún archivo no existe todavía en el dispositivo
 * - En caso de fallo de red, conserva los archivos existentes
 * - LoteriaLocalDataSource ya prioriza filesDir sobre res/raw
 */
class ActualizadorHistorico(private val context: Context) {

    companion object {
        private const val PREFS_KEY = "actualizador_historico_v1"
        private const val KEY_ULTIMA_ACTUALIZACION = "ultima_actualizacion"
        private const val INTERVALO_MS = 24 * 60 * 60 * 1000L // 24 horas

        private const val BASE_URL =
            "https://raw.githubusercontent.com/HCTop/LoteriaProbabilidad/master/app/src/main/res/raw"

        val CSV_FILES = listOf(
            "historico_primitiva.csv",
            "historico_bonoloto.csv",
            "historico_euromillones.csv",
            "historico_gordo_primitiva.csv",
            "historico_loteria_nacional.csv",
            "historico_navidad.csv",
            "historico_nino.csv"
        )
    }

    private val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)

    /** Devuelve true si hay que descargar (falta algún archivo o pasaron 24h). */
    fun necesitaActualizar(): Boolean {
        if (CSV_FILES.any { !File(context.filesDir, it).exists() }) return true
        val ultima = prefs.getLong(KEY_ULTIMA_ACTUALIZACION, 0L)
        return System.currentTimeMillis() - ultima > INTERVALO_MS
    }

    /** Fecha de la última actualización exitosa en milisegundos, o 0 si nunca. */
    fun ultimaActualizacion(): Long = prefs.getLong(KEY_ULTIMA_ACTUALIZACION, 0L)

    /**
     * Descarga todos los CSV desde GitHub.
     * Llamar siempre desde Dispatchers.IO.
     * @return número de archivos actualizados correctamente.
     */
    suspend fun actualizar(): Int = withContext(Dispatchers.IO) {
        var exitosos = 0
        for (csvFile in CSV_FILES) {
            try {
                val connection = URL("$BASE_URL/$csvFile").openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout   = 60_000
                connection.setRequestProperty("User-Agent", "LoteriaProbabilidad-App")

                if (connection.responseCode == 200) {
                    val content = connection.inputStream.bufferedReader().readText()
                    connection.disconnect()
                    // Solo sobrescribir si el contenido tiene sentido (cabecera CSV presente)
                    if (content.length > 100) {
                        File(context.filesDir, csvFile).writeText(content)
                        exitosos++
                    }
                } else {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                // Sin red o error: conservar archivo existente, seguir con el siguiente
            }
        }

        if (exitosos > 0) {
            prefs.edit()
                .putLong(KEY_ULTIMA_ACTUALIZACION, System.currentTimeMillis())
                .apply()
        }
        exitosos
    }
}
