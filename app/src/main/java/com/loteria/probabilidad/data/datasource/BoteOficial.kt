package com.loteria.probabilidad.data.datasource

import android.content.Context
import com.loteria.probabilidad.data.model.TipoLoteria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lee el bote del próximo sorteo de la web oficial de Loterías y Apuestas del Estado.
 *
 * Endpoint público (el mismo que usa su web):
 *   https://www.loteriasyapuestas.es/servicios/proximosv3?game_id=LAPR&num=2
 * Devuelve un array JSON con los próximos sorteos; el bote va en "premio_bote"
 * (texto, en euros, o null si aún no se ha anunciado).
 *
 * - Guarda el último bote en caché (SharedPreferences) y solo vuelve a pedirlo cada 3 h.
 * - Si no hay red, devuelve el último bote guardado (si es del mismo sorteo o posterior a hoy).
 * - Regla del proyecto "no inventar datos": si la respuesta no es de la lotería pedida
 *   o no trae bote, devuelve null y el usuario puede escribirlo a mano.
 */
class BoteOficial(context: Context) {

    data class Bote(
        val fechaSorteo: String,   // YYYY-MM-DD
        val importe: Double,       // euros
        val obtenidoMs: Long
    )

    companion object {
        private const val PREFS = "bote_oficial_v1"
        private const val CACHE_MS = 3 * 60 * 60 * 1000L
        private const val URL_BASE = "https://www.loteriasyapuestas.es/servicios/proximosv3"

        /** Código de juego en la API oficial. Solo LAPR está comprobado (oct-2026). */
        private val GAME_ID = mapOf(
            TipoLoteria.PRIMITIVA to "LAPR",
            TipoLoteria.BONOLOTO to "BONO",
            TipoLoteria.EUROMILLONES to "EMIL",
            TipoLoteria.GORDO_PRIMITIVA to "ELGR"
        )
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Último bote guardado para esa lotería (sin red), o null. */
    fun enCache(tipo: TipoLoteria): Bote? {
        val fecha = prefs.getString("fecha_${tipo.name}", null) ?: return null
        val importe = prefs.getFloat("importe_${tipo.name}", -1f).toDouble()
        val obtenido = prefs.getLong("obtenido_${tipo.name}", 0L)
        if (importe <= 0) return null
        // Un bote de un sorteo ya pasado no sirve
        if (fecha < java.time.LocalDate.now().toString()) return null
        return Bote(fecha, importe, obtenido)
    }

    /**
     * Bote del próximo sorteo: de la caché si es reciente; si no, de la web oficial;
     * si la web falla, el último guardado. Llamar desde una corrutina (usa Dispatchers.IO).
     */
    suspend fun obtener(tipo: TipoLoteria): Bote? = withContext(Dispatchers.IO) {
        val cache = enCache(tipo)
        if (cache != null && System.currentTimeMillis() - cache.obtenidoMs < CACHE_MS) {
            return@withContext cache
        }
        val nuevo = try {
            descargar(tipo)
        } catch (e: Exception) {
            android.util.Log.w("BoteOficial", "No se pudo leer el bote de $tipo: ${e.message}")
            null
        }
        if (nuevo != null) {
            prefs.edit()
                .putString("fecha_${tipo.name}", nuevo.fechaSorteo)
                .putFloat("importe_${tipo.name}", nuevo.importe.toFloat())
                .putLong("obtenido_${tipo.name}", nuevo.obtenidoMs)
                .apply()
            nuevo
        } else {
            cache
        }
    }

    private fun descargar(tipo: TipoLoteria): Bote? {
        val gameId = GAME_ID[tipo] ?: return null
        val conexion = URL("$URL_BASE?game_id=$gameId&num=2").openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 8_000
            conexion.readTimeout = 15_000
            // La web rechaza a veces peticiones sin cabeceras de navegador
            conexion.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile")
            conexion.setRequestProperty("Accept", "application/json")
            if (conexion.responseCode != 200) return null

            val texto = conexion.inputStream.bufferedReader().readText()
            val sorteos = JSONArray(texto)
            for (i in 0 until sorteos.length()) {
                val s = sorteos.getJSONObject(i)
                // Solo datos de la lotería pedida (si el código fuera otro, no inventar)
                if (s.optString("game_id") != gameId) continue
                if (s.isNull("premio_bote")) continue
                val importe = s.optString("premio_bote").toDoubleOrNull() ?: continue
                if (importe <= 0) continue
                val fecha = s.optString("fecha").take(10)          // "2026-10-19 00:00:00"
                if (fecha.length != 10) continue
                return Bote(fecha, importe, System.currentTimeMillis())
            }
            return null
        } finally {
            conexion.disconnect()
        }
    }
}
