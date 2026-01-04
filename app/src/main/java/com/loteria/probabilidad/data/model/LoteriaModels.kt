package com.loteria.probabilidad.data.model

/**
 * Tipos de lotería disponibles en la aplicación.
 */
enum class TipoLoteria(
    val displayName: String,
    val descripcion: String,
    val archivoCSV: String,
    val diasSorteo: String,
    val maxNumero: Int,
    val cantidadNumeros: Int
) {
    PRIMITIVA(
        displayName = "La Primitiva",
        descripcion = "6 números (1-49) + Complementario + Reintegro",
        archivoCSV = "historico_primitiva.csv",
        diasSorteo = "Lunes, Jueves y Sábado",
        maxNumero = 49,
        cantidadNumeros = 6
    ),
    BONOLOTO(
        displayName = "Bonoloto",
        descripcion = "6 números (1-49) + Complementario + Reintegro",
        archivoCSV = "historico_bonoloto.csv",
        diasSorteo = "Lunes a Sábado",
        maxNumero = 49,
        cantidadNumeros = 6
    ),
    EUROMILLONES(
        displayName = "Euromillones",
        descripcion = "5 números (1-50) + 2 Estrellas (1-12)",
        archivoCSV = "historico_euromillones.csv",
        diasSorteo = "Martes y Viernes",
        maxNumero = 50,
        cantidadNumeros = 5
    ),
    GORDO_PRIMITIVA(
        displayName = "El Gordo de la Primitiva",
        descripcion = "5 números (1-54) + Número Clave (0-9)",
        archivoCSV = "historico_gordo_primitiva.csv",
        diasSorteo = "Domingo",
        maxNumero = 54,
        cantidadNumeros = 5
    ),
    LOTERIA_NACIONAL(
        displayName = "Lotería Nacional",
        descripcion = "Números de 5 cifras (00000-99999)",
        archivoCSV = "historico_loteria_nacional.csv",
        diasSorteo = "Jueves y Sábado",
        maxNumero = 99999,
        cantidadNumeros = 1
    ),
    NAVIDAD(
        displayName = "El Gordo de Navidad",
        descripcion = "Sorteo del 22 de Diciembre",
        archivoCSV = "historico_navidad.csv",
        diasSorteo = "22 de Diciembre",
        maxNumero = 99999,
        cantidadNumeros = 1
    ),
    NINO(
        displayName = "El Niño",
        descripcion = "Sorteo del 6 de Enero",
        archivoCSV = "historico_nino.csv",
        diasSorteo = "6 de Enero",
        maxNumero = 99999,
        cantidadNumeros = 1
    )
}

/**
 * Métodos de cálculo de probabilidad disponibles.
 */
enum class MetodoCalculo(
    val displayName: String,
    val descripcion: String,
    val explicacionCorta: String
) {
    LAPLACE(
        displayName = "Regla de Laplace",
        descripcion = "Probabilidad teórica: casos favorables / casos posibles. " +
                "Todos los números tienen exactamente la misma probabilidad matemática.",
        explicacionCorta = "P(A) = casos favorables / casos posibles"
    ),
    FRECUENCIAS(
        displayName = "Análisis de Frecuencias",
        descripcion = "Basado en el histórico de sorteos. Prioriza los números que " +
                "han salido más veces estadísticamente.",
        explicacionCorta = "Números más frecuentes en el histórico"
    ),
    NUMEROS_CALIENTES(
        displayName = "Números Calientes",
        descripcion = "Selecciona los números que más han salido en los últimos sorteos. " +
                "Teoría de las rachas: si un número sale mucho, seguirá saliendo.",
        explicacionCorta = "Números con más apariciones recientes"
    ),
    NUMEROS_FRIOS(
        displayName = "Números Fríos",
        descripcion = "Selecciona los números que menos han salido recientemente. " +
                "Teoría del equilibrio: les 'toca' salir pronto.",
        explicacionCorta = "Números con menos apariciones recientes"
    ),
    EQUILIBRIO_ESTADISTICO(
        displayName = "Equilibrio Estadístico",
        descripcion = "Combina números calientes y fríos buscando un balance. " +
                "Mezcla de diferentes estrategias para diversificar.",
        explicacionCorta = "Mezcla de números calientes y fríos"
    ),
    PROBABILIDAD_CONDICIONAL(
        displayName = "Probabilidad Condicional",
        descripcion = "Analiza qué números tienden a salir juntos. " +
                "Busca patrones de combinaciones que se repiten.",
        explicacionCorta = "Números que suelen salir juntos"
    ),
    DESVIACION_MEDIA(
        displayName = "Desviación de la Media",
        descripcion = "Identifica números que están por encima o debajo de su " +
                "frecuencia esperada según la ley de los grandes números.",
        explicacionCorta = "Números alejados de su frecuencia esperada"
    ),
    ALEATORIO_PURO(
        displayName = "Aleatorio Puro",
        descripcion = "Genera combinaciones completamente aleatorias. " +
                "Matemáticamente, tan válido como cualquier otro método.",
        explicacionCorta = "Selección totalmente al azar"
    )
}

/**
 * Resultado de un sorteo histórico.
 */
sealed class ResultadoSorteo {
    abstract val fecha: String
}

/**
 * Resultado de sorteos tipo Primitiva/Bonoloto.
 */
data class ResultadoPrimitiva(
    override val fecha: String,
    val numeros: List<Int>,           // 6 números
    val complementario: Int,
    val reintegro: Int
) : ResultadoSorteo()

/**
 * Resultado de Euromillones.
 */
data class ResultadoEuromillones(
    override val fecha: String,
    val numeros: List<Int>,           // 5 números
    val estrellas: List<Int>          // 2 estrellas
) : ResultadoSorteo()

/**
 * Resultado de El Gordo de la Primitiva.
 */
data class ResultadoGordoPrimitiva(
    override val fecha: String,
    val numeros: List<Int>,           // 5 números
    val numeroClave: Int
) : ResultadoSorteo()

/**
 * Resultado de Lotería Nacional/El Niño.
 */
data class ResultadoNacional(
    override val fecha: String,
    val primerPremio: String,
    val segundoPremio: String,
    val reintegros: List<Int>
) : ResultadoSorteo()

/**
 * Resultado de El Gordo de Navidad.
 */
data class ResultadoNavidad(
    override val fecha: String,
    val gordo: String,
    val segundo: String,
    val tercero: String,
    val reintegros: List<Int>
) : ResultadoSorteo()

/**
 * Combinación sugerida por el calculador de probabilidades.
 */
data class CombinacionSugerida(
    val numeros: List<Int>,
    val complementarios: List<Int> = emptyList(),  // Estrellas, reintegro, etc.
    val probabilidadRelativa: Double,              // Puntuación basada en frecuencia
    val explicacion: String = ""
)

/**
 * Estadísticas de frecuencia de un número.
 */
data class EstadisticaNumero(
    val numero: Int,
    val apariciones: Int,
    val porcentaje: Double,
    val ultimaAparicion: String? = null
)

/**
 * Resultado del análisis de probabilidades.
 */
data class AnalisisProbabilidad(
    val tipoLoteria: TipoLoteria,
    val metodoCalculo: MetodoCalculo = MetodoCalculo.FRECUENCIAS,
    val totalSorteos: Int,
    val combinacionesSugeridas: List<CombinacionSugerida>,
    val numerosMasFrequentes: List<EstadisticaNumero>,
    val numerosMenosFrequentes: List<EstadisticaNumero>,
    val complementariosMasFrequentes: List<EstadisticaNumero> = emptyList(),
    val fechaDesde: String? = null,
    val fechaHasta: String? = null,
    val probabilidadTeorica: String = ""  // Para mostrar la probabilidad según Laplace
)

/**
 * Rango de fechas para filtrar el análisis.
 */
data class RangoFechas(
    val desde: String?,  // Formato: YYYY-MM-DD, null = desde el inicio
    val hasta: String?   // Formato: YYYY-MM-DD, null = hasta hoy
) {
    companion object {
        val TODO = RangoFechas(null, null)
        
        fun ultimosAnios(anios: Int): RangoFechas {
            val hoy = java.time.LocalDate.now()
            val desde = hoy.minusYears(anios.toLong())
            return RangoFechas(desde.toString(), hoy.toString())
        }
        
        fun anioEspecifico(anio: Int): RangoFechas {
            return RangoFechas("$anio-01-01", "$anio-12-31")
        }
        
        fun rangoAnios(desde: Int, hasta: Int): RangoFechas {
            return RangoFechas("$desde-01-01", "$hasta-12-31")
        }
    }
}

/**
 * Opciones predefinidas de rango de fechas.
 */
enum class OpcionRangoFechas(val displayName: String, val rango: RangoFechas) {
    TODO_HISTORICO("Todo el histórico", RangoFechas.TODO),
    ULTIMOS_5_ANIOS("Últimos 5 años", RangoFechas.ultimosAnios(5)),
    ULTIMOS_10_ANIOS("Últimos 10 años", RangoFechas.ultimosAnios(10)),
    ULTIMOS_20_ANIOS("Últimos 20 años", RangoFechas.ultimosAnios(20)),
    ULTIMO_ANIO("Último año", RangoFechas.ultimosAnios(1)),
    ANIO_2024("Año 2024", RangoFechas.anioEspecifico(2024)),
    ANIO_2023("Año 2023", RangoFechas.anioEspecifico(2023)),
    DESDE_2020("Desde 2020", RangoFechas("2020-01-01", null)),
    DESDE_2010("Desde 2010", RangoFechas("2010-01-01", null)),
    DESDE_2000("Desde 2000", RangoFechas("2000-01-01", null))
}
