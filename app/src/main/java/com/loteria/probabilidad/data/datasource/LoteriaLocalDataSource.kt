package com.loteria.probabilidad.data.datasource

import android.content.Context
import com.loteria.probabilidad.data.model.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * DataSource para leer los datos históricos de loterías desde archivos CSV.
 */
class LoteriaLocalDataSource(private val context: Context) {

    /**
     * Lee el histórico de La Primitiva o Bonoloto desde CSV.
     */
    fun leerHistoricoPrimitiva(tipoLoteria: TipoLoteria): List<ResultadoPrimitiva> {
        val resultados = mutableListOf<ResultadoPrimitiva>()
        
        try {
            val resourceId = getResourceId(tipoLoteria.archivoCSV)
            if (resourceId == 0) return resultados
            
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    // Saltar cabecera
                    reader.readLine()
                    
                    var linea: String?
                    while (reader.readLine().also { linea = it } != null) {
                        linea?.let { line ->
                            val campos = line.split(",")
                            if (campos.size >= 9) {
                                try {
                                    val resultado = ResultadoPrimitiva(
                                        fecha = campos[0].trim(),
                                        numeros = listOf(
                                            campos[1].trim().toInt(),
                                            campos[2].trim().toInt(),
                                            campos[3].trim().toInt(),
                                            campos[4].trim().toInt(),
                                            campos[5].trim().toInt(),
                                            campos[6].trim().toInt()
                                        ),
                                        complementario = campos[7].trim().toInt(),
                                        reintegro = campos[8].trim().toInt()
                                    )
                                    resultados.add(resultado)
                                } catch (e: NumberFormatException) {
                                    // Ignorar líneas con formato incorrecto
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return resultados
    }

    /**
     * Lee el histórico de Euromillones desde CSV.
     */
    fun leerHistoricoEuromillones(): List<ResultadoEuromillones> {
        val resultados = mutableListOf<ResultadoEuromillones>()
        
        try {
            val resourceId = getResourceId(TipoLoteria.EUROMILLONES.archivoCSV)
            if (resourceId == 0) return resultados
            
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Saltar cabecera
                    
                    var linea: String?
                    while (reader.readLine().also { linea = it } != null) {
                        linea?.let { line ->
                            val campos = line.split(",")
                            if (campos.size >= 8) {
                                try {
                                    val resultado = ResultadoEuromillones(
                                        fecha = campos[0].trim(),
                                        numeros = listOf(
                                            campos[1].trim().toInt(),
                                            campos[2].trim().toInt(),
                                            campos[3].trim().toInt(),
                                            campos[4].trim().toInt(),
                                            campos[5].trim().toInt()
                                        ),
                                        estrellas = listOf(
                                            campos[6].trim().toInt(),
                                            campos[7].trim().toInt()
                                        )
                                    )
                                    resultados.add(resultado)
                                } catch (e: NumberFormatException) {
                                    // Ignorar líneas con formato incorrecto
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return resultados
    }

    /**
     * Lee el histórico de El Gordo de la Primitiva desde CSV.
     */
    fun leerHistoricoGordoPrimitiva(): List<ResultadoGordoPrimitiva> {
        val resultados = mutableListOf<ResultadoGordoPrimitiva>()
        
        try {
            val resourceId = getResourceId(TipoLoteria.GORDO_PRIMITIVA.archivoCSV)
            if (resourceId == 0) return resultados
            
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Saltar cabecera
                    
                    var linea: String?
                    while (reader.readLine().also { linea = it } != null) {
                        linea?.let { line ->
                            val campos = line.split(",")
                            if (campos.size >= 7) {
                                try {
                                    val resultado = ResultadoGordoPrimitiva(
                                        fecha = campos[0].trim(),
                                        numeros = listOf(
                                            campos[1].trim().toInt(),
                                            campos[2].trim().toInt(),
                                            campos[3].trim().toInt(),
                                            campos[4].trim().toInt(),
                                            campos[5].trim().toInt()
                                        ),
                                        numeroClave = campos[6].trim().toInt()
                                    )
                                    resultados.add(resultado)
                                } catch (e: NumberFormatException) {
                                    // Ignorar líneas con formato incorrecto
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return resultados
    }

    /**
     * Lee el histórico de Lotería Nacional o El Niño desde CSV.
     */
    fun leerHistoricoNacional(tipoLoteria: TipoLoteria): List<ResultadoNacional> {
        val resultados = mutableListOf<ResultadoNacional>()
        
        try {
            val resourceId = getResourceId(tipoLoteria.archivoCSV)
            if (resourceId == 0) return resultados
            
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Saltar cabecera
                    
                    var linea: String?
                    while (reader.readLine().also { linea = it } != null) {
                        linea?.let { line ->
                            val campos = line.split(",")
                            if (campos.size >= 7) {
                                try {
                                    val resultado = ResultadoNacional(
                                        fecha = campos[0].trim(),
                                        primerPremio = campos[1].trim(),
                                        segundoPremio = campos[2].trim(),
                                        reintegros = listOf(
                                            campos[3].trim().toInt(),
                                            campos[4].trim().toInt(),
                                            campos[5].trim().toInt(),
                                            campos[6].trim().toInt()
                                        )
                                    )
                                    resultados.add(resultado)
                                } catch (e: NumberFormatException) {
                                    // Ignorar líneas con formato incorrecto
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return resultados
    }

    /**
     * Lee el histórico de El Gordo de Navidad desde CSV.
     */
    fun leerHistoricoNavidad(): List<ResultadoNavidad> {
        val resultados = mutableListOf<ResultadoNavidad>()
        
        try {
            val resourceId = getResourceId(TipoLoteria.NAVIDAD.archivoCSV)
            if (resourceId == 0) return resultados
            
            context.resources.openRawResource(resourceId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Saltar cabecera
                    
                    var linea: String?
                    while (reader.readLine().also { linea = it } != null) {
                        linea?.let { line ->
                            val campos = line.split(",")
                            if (campos.size >= 8) {
                                try {
                                    val resultado = ResultadoNavidad(
                                        fecha = campos[0].trim(),
                                        gordo = campos[1].trim(),
                                        segundo = campos[2].trim(),
                                        tercero = campos[3].trim(),
                                        reintegros = listOf(
                                            campos[4].trim().toInt(),
                                            campos[5].trim().toInt(),
                                            campos[6].trim().toInt(),
                                            campos[7].trim().toInt()
                                        )
                                    )
                                    resultados.add(resultado)
                                } catch (e: NumberFormatException) {
                                    // Ignorar líneas con formato incorrecto
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return resultados
    }

    /**
     * Obtiene el ID del recurso raw a partir del nombre del archivo.
     */
    private fun getResourceId(nombreArchivo: String): Int {
        val nombreRecurso = nombreArchivo.replace(".csv", "")
        return context.resources.getIdentifier(
            nombreRecurso,
            "raw",
            context.packageName
        )
    }

    /**
     * Filtra resultados por rango de fechas.
     * Las fechas deben estar en formato YYYY-MM-DD.
     */
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
