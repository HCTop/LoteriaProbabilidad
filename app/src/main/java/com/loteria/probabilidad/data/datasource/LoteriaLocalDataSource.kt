package com.loteria.probabilidad.data.datasource

import android.annotation.SuppressLint
import android.content.Context
import com.loteria.probabilidad.data.model.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * DataSource optimizado para lectura fiel de históricos.
 */
class LoteriaLocalDataSource(private val context: Context) {

    fun leerHistoricoPrimitiva(tipoLoteria: TipoLoteria): List<ResultadoPrimitiva> {
        val resultados = mutableListOf<ResultadoPrimitiva>()
        try {
            val resourceId = getResourceId(tipoLoteria.archivoCSV)
            if (resourceId == 0) return resultados
            
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val lineas = reader.readLines().filter { it.isNotBlank() }
                    lineas.drop(1).forEach { line ->
                        val campos = line.split(",")
                        if (campos.size >= 9) {
                            try {
                                resultados.add(ResultadoPrimitiva(
                                    fecha = campos[0].trim(),
                                    numeros = (1..6).map { campos[it].trim().toInt() },
                                    complementario = campos[7].trim().toInt(),
                                    reintegro = campos[8].trim().toInt()
                                ))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        // Devolvemos la lista invertida: LOS MÁS RECIENTES PRIMERO
        return resultados.reversed()
    }

    fun leerHistoricoEuromillones(): List<ResultadoEuromillones> {
        val resultados = mutableListOf<ResultadoEuromillones>()
        try {
            val resourceId = getResourceId(TipoLoteria.EUROMILLONES.archivoCSV)
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLines().filter { it.isNotBlank() }.drop(1).forEach { line ->
                        val campos = line.split(",")
                        if (campos.size >= 8) {
                            try {
                                resultados.add(ResultadoEuromillones(
                                    fecha = campos[0].trim(),
                                    numeros = (1..5).map { campos[it].trim().toInt() },
                                    estrellas = (6..7).map { campos[it].trim().toInt() }
                                ))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return resultados.reversed()
    }

    fun leerHistoricoGordoPrimitiva(): List<ResultadoGordoPrimitiva> {
        val resultados = mutableListOf<ResultadoGordoPrimitiva>()
        try {
            val resourceId = getResourceId(TipoLoteria.GORDO_PRIMITIVA.archivoCSV)
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLines().filter { it.isNotBlank() }.drop(1).forEach { line ->
                        val campos = line.split(",")
                        if (campos.size >= 7) {
                            try {
                                resultados.add(ResultadoGordoPrimitiva(
                                    fecha = campos[0].trim(),
                                    numeros = (1..5).map { campos[it].trim().toInt() },
                                    numeroClave = campos[6].trim().toInt()
                                ))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return resultados.reversed()
    }

    fun leerHistoricoNacional(tipoLoteria: TipoLoteria): List<ResultadoNacional> {
        val resultados = mutableListOf<ResultadoNacional>()
        try {
            val resourceId = getResourceId(tipoLoteria.archivoCSV)
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLines().filter { it.isNotBlank() }.drop(1).forEach { line ->
                        val campos = line.split(",")
                        if (campos.size >= 7) {
                            try {
                                resultados.add(ResultadoNacional(
                                    fecha = campos[0].trim(),
                                    primerPremio = campos[1].trim(),
                                    segundoPremio = campos[2].trim(),
                                    reintegros = (3..6).map { campos[it].trim().toInt() }
                                ))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return resultados.reversed()
    }

    fun leerHistoricoNavidad(): List<ResultadoNavidad> {
        val resultados = mutableListOf<ResultadoNavidad>()
        try {
            val resourceId = getResourceId(TipoLoteria.NAVIDAD.archivoCSV)
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLines().filter { it.isNotBlank() }.drop(1).forEach { line ->
                        val campos = line.split(",")
                        if (campos.size >= 8) {
                            try {
                                resultados.add(ResultadoNavidad(
                                    fecha = campos[0].trim(),
                                    gordo = campos[1].trim(),
                                    segundo = campos[2].trim(),
                                    tercero = campos[3].trim(),
                                    reintegros = (4..7).map { campos[it].trim().toInt() }
                                ))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return resultados.reversed()
    }

    @SuppressLint("DiscouragedApi")
    private fun getResourceId(nombreArchivo: String): Int {
        val nombreRecurso = nombreArchivo.replace(".csv", "")
        return context.resources.getIdentifier(nombreRecurso, "raw", context.packageName)
    }

    companion object {
        fun <T : ResultadoSorteo> filtrarPorFechas(resultados: List<T>, rangoFechas: RangoFechas): List<T> {
            val desde = rangoFechas.desde
            val hasta = rangoFechas.hasta
            return resultados.filter { 
                val f = it.fecha
                (desde == null || f >= desde) && (hasta == null || f <= hasta)
            }
        }
    }
}
