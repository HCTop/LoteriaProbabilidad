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
    );
    
    // Alias para facilitar uso
    val nombre: String get() = displayName
}

/**
 * Resultado de un test de backtesting
 * Adaptado para cada tipo de lotería con sus categorías específicas
 */
data class ResultadoBacktest(
    val metodo: MetodoCalculo,
    val sorteosProbados: Int,
    val aciertos0: Int,      // 0 números acertados
    val aciertos1: Int,      // 1 número acertado
    val aciertos2: Int,      // 2 números acertados
    val aciertos3: Int,      // 3 números acertados
    val aciertos4: Int,      // 4 números acertados
    val aciertos5: Int = 0,  // 5 números acertados (Primitiva/Bonoloto/Euromillones/Gordo)
    val aciertos6: Int = 0,  // 6 números acertados (Primitiva/Bonoloto)
    val aciertosComplementario: Int = 0,  // +C (Primitiva/Bonoloto)
    val aciertosReintegro: Int = 0,       // +R (Primitiva/Bonoloto/Nacional)
    val aciertosEstrella1: Int = 0,       // +E1 (Euromillones)
    val aciertosEstrella2: Int = 0,       // +E2 (Euromillones - ambas estrellas)
    val aciertosClave: Int = 0,           // +K (Gordo de la Primitiva)
    val puntuacionTotal: Double,  // Score ponderado
    val mejorAcierto: Int,   // Máximo de números acertados en un sorteo
    val promedioAciertos: Double,
    val tipoLoteria: String = ""  // Para saber qué campos mostrar
)

/**
 * Métodos de cálculo de probabilidad disponibles.
 */
enum class MetodoCalculo(
    val displayName: String,
    val descripcion: String,
    val explicacionCorta: String
) {
    ENSEMBLE_VOTING(
        displayName = "🗳️ Ensemble Voting",
        descripcion = "Sistema de votación que combina 8 estrategias diferentes: " +
                "IA genética, alta confianza, rachas, equilibrio, ciclos, correlaciones, " +
                "frecuencia y tendencia. Cada estrategia vota por sus números favoritos " +
                "y se seleccionan los de mayor consenso.",
        explicacionCorta = "8 estrategias votan → máximo consenso"
    ),
    ALTA_CONFIANZA(
        displayName = "🎯 Alta Confianza",
        descripcion = "Sistema de 7 señales coherentes: ciclos, tendencia reciente, " +
                "EMA, compañeros activos, próximos por ciclo, rachas y balance. " +
                "Solo sugiere números con alto consenso (≥4/7 señales positivas).",
        explicacionCorta = "7 señales → solo números con alto consenso"
    ),
    RACHAS_MIX(
        displayName = "🔥❄️ Mix Rachas",
        descripcion = "Combina números calientes (en racha positiva) con números fríos " +
                "(debidos por no salir). Estrategia: 2-3 calientes + 1-2 fríos + normales. " +
                "Detecta rachas en los últimos 20 sorteos.",
        explicacionCorta = "Mezcla de números calientes y debidos"
    ),
    IA_GENETICA(
        displayName = "🤖 IA Genética",
        descripcion = "Sistema de Inteligencia Artificial que usa algoritmos genéticos con 500 individuos " +
                "evolucionando durante 60 generaciones. Combina: análisis de frecuencias, gaps, tendencias, " +
                "patrones de pares, balance estructural, ciclos, correlaciones y rachas. " +
                "Los pesos se ajustan dinámicamente con optimizador Adam.",
        explicacionCorta = "Algoritmo genético + ensemble de 10 predictores"
    ),
    FRECUENCIAS(
        displayName = "Análisis de Frecuencias",
        descripcion = "Basado en el histórico de sorteos. Prioriza los números que " +
                "han salido más veces estadísticamente.",
        explicacionCorta = "Números más frecuentes en el histórico"
    ),
    NUMEROS_FRIOS(
        displayName = "Números Fríos",
        descripcion = "Selecciona los números que menos han salido recientemente. " +
                "Teoría del equilibrio: les 'toca' salir pronto.",
        explicacionCorta = "Números con menos apariciones recientes"
    ),
    ALEATORIO_PURO(
        displayName = "Aleatorio Puro",
        descripcion = "Genera combinaciones completamente aleatorias. " +
                "Matemáticamente, tan válido como cualquier otro método.",
        explicacionCorta = "Selección totalmente al azar"
    ),
    CALIENTES_50(
        displayName = "🌡️ Calientes 50",
        descripcion = "Los números que más han salido en los últimos 50 sorteos. " +
                "Fue el que más premios dio en La Primitiva en el laboratorio (sobre todo " +
                "desde 2018), aunque no de forma estadísticamente significativa: se mide aquí " +
                "hacia delante para ver si se mantiene.",
        explicacionCorta = "Los más salidos en los últimos 50 sorteos"
    ),
    DEBIDOS(
        displayName = "⏳ Debidos",
        descripcion = "Los números que más sorteos llevan sin salir ('les toca'). " +
                "Es la creencia contraria a Calientes; útil para comparar.",
        explicacionCorta = "Los que más tiempo llevan sin salir"
    ),
    MULTI_HORIZON(
        displayName = "🔭 Multi-Horizon",
        descripcion = "Consenso multi-horizonte: analiza frecuencias en 4 ventanas temporales " +
                "(5, 12, 30 y 60 sorteos). Selecciona números que aparecen consistentemente " +
                "en el top de TODAS las ventanas. (En el laboratorio con todo el histórico " +
                "no se distingue del azar: ver scripts/laboratorio_*.py.)",
        explicacionCorta = "Top en 4 ventanas temporales → consenso multi-horizonte"
    ),
    METODO_ABUELO(
        displayName = "🔮📐 Método del Abuelo",
        descripcion = "Sistema MATEMÁTICO AVANZADO que combina 7 algoritmos rigurosos: " +
                "Test Chi-Cuadrado (detecta sesgos reales en bolas/máquinas), " +
                "Análisis de Fourier (periodicidades genuinas), " +
                "Inferencia Bayesiana (probabilidades actualizadas con cada sorteo), " +
                "Cadenas de Markov (transiciones entre sorteos), " +
                "Análisis de Entropía (ventanas de baja aleatoriedad), " +
                "Diseños de Cobertura (maximiza aciertos parciales) y " +
                "Validación Monte Carlo (verifica mejora sobre el azar). " +
                "Solo da peso a factores ESTADÍSTICAMENTE SIGNIFICATIVOS (p<0.05).",
        explicacionCorta = "χ² + Fourier + Bayes + Markov + Entropía + Monte Carlo"
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
    val probabilidadTeorica: String = "",
    val fechaUltimoSorteo: String? = null,
    // Análisis de frecuencia por posición para loterías de 5 dígitos (Nacional, Navidad, Niño)
    // Map<NombrePosicion, List<Pair<Digito, Porcentaje>>>
    val analisisPorPosicion: Map<String, List<Pair<Int, Double>>> = emptyMap()
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

// ═══════════════════════════════════════════════════════════════════
// FÓRMULA DEL ABUELO - Modelos de datos
// ═══════════════════════════════════════════════════════════════════

/**
 * Configuración del diseño de cobertura C(v,k,t,m).
 * v=candidatos, k=números por boleto, t=tamaño subconjunto, m=garantía mínima.
 */
data class ConfiguracionCobertura(
    val v: Int,
    val k: Int,
    val t: Int,
    val m: Int,
    val tipoLoteria: TipoLoteria
)

/**
 * Resultado del algoritmo de cobertura para loterías de combinación.
 */
data class ResultadoCobertura(
    val tickets: List<List<Int>>,
    val garantia: String,
    val numTickets: Int,
    val candidatos: List<Int>,
    val coberturaPct: Double,
    val explicacion: String,
    val probActivacion: Double = 0.0
)

/**
 * Resultado de cobertura para loterías de dígitos (Nacional/Navidad/Niño).
 */
data class CoberturaDigitos(
    val numeros: List<String>,
    val terminacionesCubiertas: List<Int>,
    val estrategia: String,
    val explicacion: String
)

/**
 * Puntuación anti-popularidad de una combinación.
 */
data class ScorePopularidad(
    val combinacion: List<Int>,
    val scoreUnicidad: Int,
    val penalizaciones: List<String>,
    val estimacionJugadores: String
)

/**
 * Estructura de premios de una lotería.
 * tasaRetorno: fracción de la recaudación que va a premios (ej: 0.55 = 55%)
 */
data class EstructuraPremios(
    val tipoLoteria: TipoLoteria,
    val precioBoleto: Double,
    val categorias: List<CategoriaPremio>,
    val tasaRetorno: Double = 0.55
)

/**
 * Categoría de premio individual.
 * - Si porcentajeFondo > 0: es premio variable (% del fondo variable tras descontar fijos)
 * - Si porcentajeFondo == 0: es premio fijo (premioMedio contiene el importe fijo)
 */
data class CategoriaPremio(
    val nombre: String,
    val aciertos: Int,
    val complementario: Boolean,
    val premioMedio: Double,
    val probabilidad: Double,
    val porcentajeFondo: Double = 0.0
)

/**
 * Resultado del análisis de Kelly.
 */
data class AnalisisKelly(
    val tipoLoteria: TipoLoteria,
    val boteActual: Double,
    val valorEsperado: Double,
    val fraccionKelly: Double,
    val recomendacion: RecomendacionJuego,
    val atractividad: Int,
    val explicacion: String
)

/**
 * Nivel de recomendación de juego.
 */
enum class RecomendacionJuego(val displayName: String, val color: Long) {
    // Semáforo de 3 colores (MINIMO y MUY_FAVORABLE ya no se usan; se dejan por compatibilidad)
    // Respuestas a "¿Merece la pena jugar?"
    NO_JUGAR("No, mejor espera", 0xFFD32F2F),
    MINIMO("Mínimo", 0xFFF57C00),
    MODERADO("Sí, pero con poco", 0xFFF9A825),
    FAVORABLE("Sí, buen momento", 0xFF388E3C),
    MUY_FAVORABLE("Muy favorable", 0xFF388E3C)
}

/**
 * Resultado combinado de la Fórmula del Abuelo (3 pilares).
 */
data class ResultadoFormulaAbuelo(
    val cobertura: ResultadoCobertura?,
    val coberturaDigitos: CoberturaDigitos?,
    val ticketsFiltrados: List<ScorePopularidad>,
    val analisisKelly: AnalisisKelly,
    val resumen: String,
    val suplementoCandidatos: List<Int> = emptyList(),   // reintegro, estrellas, clave según lotería
    val suplementoPorcentajes: List<Double> = emptyList() // porcentaje histórico de cada suplemento
)
