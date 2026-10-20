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
        private const val URL_GITHUB =
            "https://raw.githubusercontent.com/HCTop/LoteriaProbabilidad/master/datos/botes.json"

        /** Código de juego en la API oficial (los 4 comprobados en oct-2026). */
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
    suspend fun obtener(tipo: TipoLoteria, forzar: Boolean = false): Bote? = withContext(Dispatchers.IO) {
        val cache = enCache(tipo)
        if (!forzar && cache != null && System.currentTimeMillis() - cache.obtenidoMs < CACHE_MS) {
            return@withContext cache
        }
        // 1º la web oficial; 2º (solo Primitiva) primitiva.org.es; 3º el botes.json que
        // GitHub guarda cada noche (la web oficial a veces rechaza las peticiones del móvil)
        val nuevo = intentar(tipo) { descargar(tipo) }
            ?: (if (tipo == TipoLoteria.PRIMITIVA) intentar(tipo) { descargarRespaldoPrimitiva() } else null)
            ?: intentar(tipo) { descargarDeGitHub(tipo) }
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

    private inline fun intentar(tipo: TipoLoteria, bloque: () -> Bote?): Bote? = try {
        bloque()
    } catch (e: Exception) {
        android.util.Log.w("BoteOficial", "No se pudo leer el bote de $tipo: ${e.message}")
        null
    }

    private fun leerUrl(url: String, accept: String): String? {
        val conexion = URL(url).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 8_000
            conexion.readTimeout = 15_000
            // Algunas webs rechazan peticiones sin cabeceras de navegador
            conexion.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile")
            conexion.setRequestProperty("Accept", accept)
            conexion.setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
            if (conexion.responseCode != 200) return null
            return conexion.inputStream.bufferedReader().readText()
        } finally {
            conexion.disconnect()
        }
    }

    /**
     * Respaldo para La Primitiva: primitiva.org.es/bote muestra
     * "Lunes, 19 de octubre de 2026 Bote 38.000.000 €". Se quitan las etiquetas HTML
     * y se busca ese patrón. Si no aparece tal cual, no se inventa nada (null).
     */
    private fun descargarRespaldoPrimitiva(): Bote? {
        val html = leerUrl("https://www.primitiva.org.es/bote/", "text/html") ?: return null
        val texto = html.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&euro;", "€")
            .replace(Regex("\\s+"), " ")
        val m = Regex("Bote ([0-9]{1,3}(?:\\.[0-9]{3})+) ?€").find(texto) ?: return null
        val importe = m.groupValues[1].replace(".", "").toDoubleOrNull() ?: return null
        if (importe <= 0) return null
        // Fecha justo antes: "19 de octubre de 2026"; si no se encuentra, hoy
        val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        val previo = texto.substring(0, m.range.first).takeLast(80)
        val f = Regex("([0-9]{1,2}) de ([a-záéíóú]+) de ([0-9]{4})").findAll(previo).lastOrNull()
        val fecha = f?.let {
            val mes = meses.indexOf(it.groupValues[2].lowercase()) + 1
            if (mes > 0) "%s-%02d-%02d".format(it.groupValues[3], mes, it.groupValues[1].toInt()) else null
        } ?: java.time.LocalDate.now().toString()
        return Bote(fecha, importe, System.currentTimeMillis())
    }

    /**
     * Respaldo para las 4 loterías: datos/botes.json del repositorio, que escribe cada noche
     * la tarea de GitHub (scripts/actualizar_datos.py → guardar_botes) leyendo la API oficial.
     * Formato: {"botes": {"BONOLOTO": {"fecha": "2026-10-20", "importe": 2800000.0}, ...}}
     */
    private fun descargarDeGitHub(tipo: TipoLoteria): Bote? {
        val texto = leerUrl(URL_GITHUB, "application/json") ?: return null
        val obj = org.json.JSONObject(texto).optJSONObject("botes")?.optJSONObject(tipo.name) ?: return null
        val fecha = obj.optString("fecha")
        val importe = obj.optDouble("importe", -1.0)
        if (fecha.length != 10 || importe <= 0) return null
        if (fecha < java.time.LocalDate.now().toString()) return null   // bote de un sorteo ya pasado
        return Bote(fecha, importe, System.currentTimeMillis())
    }

    private fun descargar(tipo: TipoLoteria): Bote? {
        val gameId = GAME_ID[tipo] ?: return null
        val conexion = URL("$URL_BASE?game_id=$gameId&num=2").openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 8_000
            conexion.readTimeout = 15_000
            // La web rechaza a veces peticiones sin cabeceras de navegador
            conexion.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile")
            conexion.setRequestProperty("Accept", "application/json, text/plain, */*")
            conexion.setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
            conexion.setRequestProperty("Referer", "https://www.loteriasyapuestas.es/es/resultados")
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
                // La API puede devolver primero un sorteo ya celebrado (pasa con El Gordo):
                // ese bote no es el del próximo sorteo
                if (fecha < java.time.LocalDate.now().toString()) continue
                return Bote(fecha, importe, System.currentTimeMillis())
            }
            return null
        } finally {
            conexion.disconnect()
        }
    }
}
