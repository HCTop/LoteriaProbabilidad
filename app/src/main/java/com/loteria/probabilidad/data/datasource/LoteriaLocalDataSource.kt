package com.loteria.probabilidad.data.datasource

import android.annotation.SuppressLint
import android.content.Context
import com.loteria.probabilidad.data.model.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DataSource con capacidad de ACTUALIZACIÓN AUTOMÁTICA desde GitHub.
 */
class LoteriaLocalDataSource(private val context: Context) {

    private val GITHUB_USER = "HCTop"
    private val REPO_NAME = "LoteriaProbabilidad"
    private val BASE_URL = "https://raw.githubusercontent.com/$GITHUB_USER/$REPO_NAME/master/app/src/main/res/raw/"

    /**
     * Obtiene las líneas de un CSV, priorizando la descarga de GitHub.
     */
    private suspend fun obtenerLineasActualizadas(nombreArchivo: String): List<String> = withContext(Dispatchers.IO) {
        val archivoCache = File(context.cacheDir, nombreArchivo)
        
        try {
            // 1. Intentar descargar de GitHub
            val contenido = URL(BASE_URL + nombreArchivo).readText()
            // 2. Guardar en caché interna para uso offline futuro
            archivoCache.writeText(contenido)
            contenido.lines()
        } catch (e: Exception) {
            // 3. Si falla (sin internet), usar caché o archivo local de fábrica
            if (archivoCache.exists()) {
                archivoCache.readLines()
            } else {
                leerDesdeRecursosRaw(nombreArchivo)
            }
        }
    }

    private fun leerDesdeRecursosRaw(nombreArchivo: String): List<String> {
        val resourceId = getResourceId(nombreArchivo)
        if (resourceId == 0) return emptyList()
        return try {
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readLines()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Lee el histórico de La Primitiva o Bonoloto.
     */
    suspend fun leerHistoricoPrimitiva(tipoLoteria: TipoLoteria): List<ResultadoPrimitiva> {
        val resultados = mutableListOf<ResultadoPrimitiva>()
        val lineas = obtenerLineasActualizadas(tipoLoteria.archivoCSV)
        
        if (lineas.isNotEmpty()) {
            lineas.drop(1).filter { it.isNotBlank() }.forEach { line ->
                val campos = line.split(",").map { it.trim() }
                if (campos.size >= 9) {
                    try {
                        resultados.add(ResultadoPrimitiva(
                            fecha = campos[0],
                            numeros = listOf(
                                campos[1].toInt(), campos[2].toInt(), campos[3].toInt(),
                                campos[4].toInt(), campos[5].toInt(), campos[6].toInt()
                            ),
                            complementario = campos[7].toInt(),
                            reintegro = campos[8].toInt()
                        ))
                    } catch (_: Exception) {}
                }
            }
        }
        return resultados.reversed()
    }

    /**
     * Lee el histórico de Euromillones.
     */
    suspend fun leerHistoricoEuromillones(): List<ResultadoEuromillones> {
        val resultados = mutableListOf<ResultadoEuromillones>()
        val lineas = obtenerLineasActualizadas(TipoLoteria.EUROMILLONES.archivoCSV)
        
        lineas.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val campos = line.split(",").map { it.trim() }
            if (campos.size >= 8) {
                try {
                    resultados.add(ResultadoEuromillones(
                        fecha = campos[0],
                        numeros = (1..5).map { campos[it].toInt() },
                        estrellas = listOf(campos[6].toInt(), campos[7].toInt())
                    ))
                } catch (_: Exception) {}
            }
        }
        return resultados.reversed()
    }

    /**
     * Lee el histórico de El Gordo de la Primitiva.
     */
    suspend fun leerHistoricoGordoPrimitiva(): List<ResultadoGordoPrimitiva> {
        val resultados = mutableListOf<ResultadoGordoPrimitiva>()
        val lineas = obtenerLineasActualizadas(TipoLoteria.GORDO_PRIMITIVA.archivoCSV)
        
        lineas.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val campos = line.split(",").map { it.trim() }
            if (campos.size >= 7) {
                try {
                    resultados.add(ResultadoGordoPrimitiva(
                        fecha = campos[0],
                        numeros = (1..5).map { campos[it].toInt() },
                        numeroClave = campos[6].toInt()
                    ))
                } catch (_: Exception) {}
            }
        }
        return resultados.reversed()
    }

    /**
     * Lee el histórico de Lotería Nacional o El Niño.
     */
    suspend fun leerHistoricoNacional(tipoLoteria: TipoLoteria): List<ResultadoNacional> {
        val resultados = mutableListOf<ResultadoNacional>()
        val lineas = obtenerLineasActualizadas(tipoLoteria.archivoCSV)
        
        lineas.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val campos = line.split(",").map { it.trim() }
            if (campos.size >= 7) {
                try {
                    resultados.add(ResultadoNacional(
                        fecha = campos[0],
                        primerPremio = campos[1],
                        segundoPremio = campos[2],
                        reintegros = (3..6).map { campos[it].toInt() }
                    ))
                } catch (_: Exception) {}
            }
        }
        return resultados.reversed()
    }

    /**
     * Lee el histórico de El Gordo de Navidad.
     */
    suspend fun leerHistoricoNavidad(): List<ResultadoNavidad> {
        val resultados = mutableListOf<ResultadoNavidad>()
        val lineas = obtenerLineasActualizadas(TipoLoteria.NAVIDAD.archivoCSV)
        
        lineas.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val campos = line.split(",").map { it.trim() }
            if (campos.size >= 8) {
                try {
                    resultados.add(ResultadoNavidad(
                        fecha = campos[0],
                        gordo = campos[1],
                        segundo = campos[2],
                        tercero = campos[3],
                        reintegros = (4..7).map { campos[it].toInt() }
                    ))
                } catch (_: Exception) {}
            }
        }
        return resultados.reversed()
    }

    @SuppressLint("DiscouragedApi")
    private fun getResourceId(nombreArchivo: String): Int {
        val nombreRecurso = nombreArchivo.replace(".csv", "")
        return context.resources.getIdentifier(
            nombreRecurso,
            "raw",
            context.packageName
        )
    }

    companion object {
        fun <T : ResultadoSorteo> filtrarPorFechas(
            resultados: List<T>,
            rangoFechas: RangoFechas
        ): List<T> {
            val desde = rangoFechas.desde
            val hasta = rangoFechas.hasta
            
            return resultados.filter { resultado ->
                val fecha = resultado.fecha
                val cumpleDesde = desde == null || fecha >= desde
                val cumpleHasta = hasta == null || fecha <= hasta
                cumpleDesde && cumpleHasta
            }
        }
    }
}
