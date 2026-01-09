package com.loteria.probabilidad.data.repository

import com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource
import com.loteria.probabilidad.data.model.*

/**
 * Repositorio que proporciona acceso a los datos históricos de loterías.
 */
class LoteriaRepository(private val localDataSource: LoteriaLocalDataSource) {

    /**
     * Obtiene el histórico según el tipo de lotería.
     */
    fun obtenerHistorico(tipoLoteria: TipoLoteria): List<ResultadoSorteo> {
        return when (tipoLoteria) {
            TipoLoteria.PRIMITIVA,
            TipoLoteria.BONOLOTO -> localDataSource.leerHistoricoPrimitiva(tipoLoteria)
            
            TipoLoteria.EUROMILLONES -> localDataSource.leerHistoricoEuromillones()
            
            TipoLoteria.GORDO_PRIMITIVA -> localDataSource.leerHistoricoGordoPrimitiva()
            
            TipoLoteria.LOTERIA_NACIONAL,
            TipoLoteria.NINO -> localDataSource.leerHistoricoNacional(tipoLoteria)
            
            TipoLoteria.NAVIDAD -> localDataSource.leerHistoricoNavidad()
        }
    }

    /**
     * Obtiene el número total de sorteos en el histórico.
     */
    fun contarSorteos(tipoLoteria: TipoLoteria): Int {
        return obtenerHistorico(tipoLoteria).size
    }
    
    // Métodos específicos para backtesting
    
    fun obtenerHistoricoPrimitiva(): List<ResultadoPrimitiva> {
        return localDataSource.leerHistoricoPrimitiva(TipoLoteria.PRIMITIVA)
    }
    
    fun obtenerHistoricoBonoloto(): List<ResultadoPrimitiva> {
        return localDataSource.leerHistoricoPrimitiva(TipoLoteria.BONOLOTO)
    }
    
    fun obtenerHistoricoEuromillones(): List<ResultadoEuromillones> {
        return localDataSource.leerHistoricoEuromillones()
    }
    
    fun obtenerHistoricoGordoPrimitiva(): List<ResultadoGordoPrimitiva> {
        return localDataSource.leerHistoricoGordoPrimitiva()
    }
    
    fun obtenerHistoricoNacional(): List<ResultadoNacional> {
        return localDataSource.leerHistoricoNacional(TipoLoteria.LOTERIA_NACIONAL)
    }
    
    fun obtenerHistoricoNavidad(): List<ResultadoNavidad> {
        return localDataSource.leerHistoricoNavidad()
    }
    
    fun obtenerHistoricoNino(): List<ResultadoNacional> {
        return localDataSource.leerHistoricoNacional(TipoLoteria.NINO)
    }
}
