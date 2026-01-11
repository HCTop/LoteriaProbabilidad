package com.loteria.probabilidad.data.datasource

import android.content.Context
import com.loteria.probabilidad.data.model.*
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

/**
 * DataSource para leer los datos históricos de loterías desde archivos CSV.
 * Prioriza archivos descargados de GitHub sobre los recursos embebidos.
 */
class LoteriaLocalDataSource(private val context: Context) {

    /**
     * Obtiene un BufferedReader para el archivo CSV.
     * Prioriza archivos descargados sobre recursos embebidos.
     */
    private fun getReaderForCSV(archivoCSV: String): BufferedReader? {
        // 1. Primero intentar desde archivos descargados
        val downloadedFile = File(context.filesDir, archivoCSV)
        if (downloadedFile.exists()) {
            try {
                return BufferedReader(FileReader(downloadedFile))
            } catch (e: Exception) {
                // Si falla, intentar con recursos
            }
        }
        
        // 2. Fallback a recursos embebidos
        val resourceName = archivoCSV.replace(".csv", "")
        val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        if (resourceId != 0) {
            return BufferedReader(InputStreamReader(context.resources.openRawResource(resourceId)))
        }
        
        return null
    }

    /**
     * Lee el histórico de La Primitiva o Bonoloto desde CSV.
     */
    fun leerHistoricoPrimitiva(tipoLoteria: TipoLoteria): List<ResultadoPrimitiva> {
        val resultados = mutableListOf<ResultadoPrimitiva>()
        
        try {
            val reader = getReaderForCSV(tipoLoteria.archivoCSV) ?: return resultados
            
            reader.use { br ->
                // Saltar cabecera
                br.readLine()
                
                var linea = br.readLine()
                while (linea != null) {
                    val campos = linea.split(",")
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
                    linea = br.readLine()
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
            val reader = getReaderForCSV(TipoLoteria.EUROMILLONES.archivoCSV) ?: return resultados
            
            reader.use { br ->
                br.readLine() // Saltar cabecera
                
                var linea = br.readLine()
                while (linea != null) {
                    val campos = linea.split(",")
                    if (campos.size >= 7) {
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
                                    if (campos.size > 7) campos[7].trim().toInt() else 1
                                )
                            )
                            resultados.add(resultado)
                        } catch (e: NumberFormatException) {
                            // Ignorar líneas con formato incorrecto
                        }
                    }
                    linea = br.readLine()
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
            val reader = getReaderForCSV(TipoLoteria.GORDO_PRIMITIVA.archivoCSV) ?: return resultados
            
            reader.use { br ->
                br.readLine() // Saltar cabecera
                
                var linea = br.readLine()
                while (linea != null) {
                    val campos = linea.split(",")
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
                    linea = br.readLine()
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
            val reader = getReaderForCSV(tipoLoteria.archivoCSV) ?: return resultados
            
            reader.use { br ->
                br.readLine() // Saltar cabecera
                
                var linea = br.readLine()
                while (linea != null) {
                    val campos = linea.split(",")
                    if (campos.size >= 3) {
                        try {
                            val reintegros = if (campos.size >= 7) {
                                listOf(
                                    campos[3].trim().toIntOrNull() ?: 0,
                                    campos[4].trim().toIntOrNull() ?: 0,
                                    campos[5].trim().toIntOrNull() ?: 0,
                                    campos[6].trim().toIntOrNull() ?: 0
                                )
                            } else {
                                listOf(0, 0, 0, 0)
                            }
                            
                            val resultado = ResultadoNacional(
                                fecha = campos[0].trim(),
                                primerPremio = campos[1].trim(),
                                segundoPremio = campos[2].trim(),
                                reintegros = reintegros
                            )
                            resultados.add(resultado)
                        } catch (e: NumberFormatException) {
                            // Ignorar líneas con formato incorrecto
                        }
                    }
                    linea = br.readLine()
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
            val reader = getReaderForCSV(TipoLoteria.NAVIDAD.archivoCSV) ?: return resultados
            
            reader.use { br ->
                br.readLine() // Saltar cabecera
                
                var linea = br.readLine()
                while (linea != null) {
                    val campos = linea.split(",")
                    if (campos.size >= 4) {
                        try {
                            val reintegros = if (campos.size >= 8) {
                                listOf(
                                    campos[4].trim().toIntOrNull() ?: 0,
                                    campos[5].trim().toIntOrNull() ?: 0,
                                    campos[6].trim().toIntOrNull() ?: 0,
                                    campos[7].trim().toIntOrNull() ?: 0
                                )
                            } else {
                                listOf(0, 0, 0, 0)
                            }
                            
                            val resultado = ResultadoNavidad(
                                fecha = campos[0].trim(),
                                gordo = campos[1].trim(),
                                segundo = campos[2].trim(),
                                tercero = campos[3].trim(),
                                reintegros = reintegros
                            )
                            resultados.add(resultado)
                        } catch (e: NumberFormatException) {
                            // Ignorar líneas con formato incorrecto
                        }
                    }
                    linea = br.readLine()
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

    companion object {
        /**
         * Filtra resultados por rango de fechas.
         * Las fechas deben estar en formato YYYY-MM-DD.
         */
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
