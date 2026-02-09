package com.loteria.probabilidad.domain.ml

import com.loteria.probabilidad.data.model.ResultadoPrimitiva
import kotlin.math.*
import kotlin.random.Random

/**
 * MATEMÁTICAS AVANZADAS DEL ABUELO
 *
 * "De todas las probabilidades siempre hay factores que hacen que las cosas
 * surjan más veces... la clave no es predecir el azar, sino encontrar dónde
 * el azar deja de serlo."
 *
 * Un verdadero matemático no intenta adivinar números al azar.
 * Busca ANOMALÍAS ESTADÍSTICAS REALES usando tests rigurosos:
 *
 * 1. TEST CHI-CUADRADO: Detecta si ciertos números aparecen significativamente
 *    más de lo esperado por puro azar (sesgos en bolas/máquinas físicas)
 * 2. ANÁLISIS DE FOURIER: Encuentra periodicidades genuinas en los datos
 * 3. INFERENCIA BAYESIANA: Actualiza probabilidades con cada sorteo observado
 * 4. CADENAS DE MARKOV: Analiza transiciones entre sorteos consecutivos
 * 5. ANÁLISIS DE ENTROPÍA: Detecta ventanas de baja aleatoriedad
 * 6. DISEÑOS DE COBERTURA: Maximiza probabilidad de aciertos parciales
 * 7. VALIDACIÓN MONTE CARLO: Compara contra aleatorio puro
 */
class MatematicasAbuelo {

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. TEST CHI-CUADRADO: DETECCIÓN DE SESGOS REALES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resultado del test Chi-Cuadrado para un número.
     */
    data class ResultadoChiCuadrado(
        val numero: Int,
        val frecuenciaObservada: Int,
        val frecuenciaEsperada: Double,
        val chiCuadrado: Double,        // Estadístico χ²
        val pValor: Double,             // p-valor aproximado
        val esSignificativo: Boolean,   // p < 0.05
        val esMuySignificativo: Boolean,// p < 0.01
        val sesgo: Double               // (observado - esperado) / esperado
    )

    /**
     * Test Chi-Cuadrado de bondad de ajuste.
     *
     * Hipótesis nula: Todos los números tienen la misma probabilidad.
     * Si χ² > valor crítico, rechazamos H0 → existe sesgo real.
     *
     * Para 48 grados de libertad (49 números - 1):
     * - p=0.05 → χ² crítico ≈ 65.17
     * - p=0.01 → χ² crítico ≈ 73.68
     * - p=0.001 → χ² crítico ≈ 82.72
     */
    fun testChiCuadradoGlobal(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int
    ): Pair<Double, List<ResultadoChiCuadrado>> {
        val totalNumeros = historico.size * cantidadNumeros
        val frecuenciaEsperada = totalNumeros.toDouble() / maxNumero

        val frecuencias = IntArray(maxNumero + 1)
        historico.forEach { sorteo ->
            sorteo.numeros.forEach { num ->
                if (num in 1..maxNumero) frecuencias[num]++
            }
        }

        var chiCuadradoTotal = 0.0
        val resultados = (1..maxNumero).map { num ->
            val observado = frecuencias[num]
            val chi = (observado - frecuenciaEsperada).pow(2) / frecuenciaEsperada
            chiCuadradoTotal += chi

            val sesgo = (observado - frecuenciaEsperada) / frecuenciaEsperada
            val pValor = aproximarPValorChi(chi, 1)

            ResultadoChiCuadrado(
                numero = num,
                frecuenciaObservada = observado,
                frecuenciaEsperada = frecuenciaEsperada,
                chiCuadrado = chi,
                pValor = pValor,
                esSignificativo = pValor < 0.05,
                esMuySignificativo = pValor < 0.01,
                sesgo = sesgo
            )
        }

        return Pair(chiCuadradoTotal, resultados)
    }

    /**
     * Test Chi-Cuadrado por VENTANAS TEMPORALES.
     * Detecta si el sesgo ha cambiado con el tiempo (máquina nueva, bolas nuevas, etc.)
     */
    fun testChiCuadradoPorVentana(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        ventana: Int = 200
    ): List<Pair<Int, List<ResultadoChiCuadrado>>> {
        val resultados = mutableListOf<Pair<Int, List<ResultadoChiCuadrado>>>()

        var inicio = 0
        while (inicio + ventana <= historico.size) {
            val subHistorico = historico.subList(inicio, inicio + ventana)
            val (_, chiResultados) = testChiCuadradoGlobal(subHistorico, maxNumero, cantidadNumeros)
            resultados.add(Pair(inicio, chiResultados))
            inicio += ventana / 2 // Ventanas solapadas para mayor resolución
        }

        return resultados
    }

    /**
     * Números con sesgo CONSISTENTE a través de múltiples ventanas.
     * Un sesgo que persiste en el tiempo es más confiable que uno puntual.
     */
    fun numerosConSesgoConsistente(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int
    ): Map<Int, Double> {
        val ventanas = testChiCuadradoPorVentana(historico, maxNumero, cantidadNumeros)
        if (ventanas.isEmpty()) return emptyMap()

        val sesgosAcumulados = mutableMapOf<Int, MutableList<Double>>()

        ventanas.forEach { (_, resultados) ->
            resultados.forEach { res ->
                sesgosAcumulados.getOrPut(res.numero) { mutableListOf() }.add(res.sesgo)
            }
        }

        // Score = media del sesgo * consistencia (inversa de la varianza)
        return sesgosAcumulados.mapValues { (_, sesgos) ->
            val media = sesgos.average()
            val varianza = sesgos.map { (it - media).pow(2) }.average()
            val consistencia = 1.0 / (1.0 + varianza)

            // Solo valorar sesgos positivos (más frecuentes que lo esperado)
            if (media > 0) media * consistencia else 0.0
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. ANÁLISIS DE FOURIER: PERIODICIDADES GENUINAS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Componente de Fourier para un número.
     */
    data class ComponenteFourier(
        val numero: Int,
        val frecuenciaDominante: Double,  // Frecuencia en ciclos/sorteo
        val periodoDominante: Double,     // Período en sorteos
        val amplitud: Double,             // Fuerza de la periodicidad
        val fase: Double,                 // Fase actual del ciclo
        val prediccionProximaSalida: Int, // En cuántos sorteos debería salir
        val confianzaPeriodicidad: Double // 0-1, si la periodicidad es real
    )

    /**
     * Transformada Discreta de Fourier (DFT) para detectar periodicidades.
     *
     * Convierte la serie temporal de apariciones de cada número en el
     * dominio de frecuencias para encontrar ciclos genuinos.
     */
    fun analizarFourier(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        maxPeriodo: Int = 100
    ): Map<Int, ComponenteFourier> {
        val n = historico.size
        if (n < 60) return emptyMap()

        return (1..maxNumero).associateWith { numero ->
            // Crear señal binaria: 1 si aparece, 0 si no
            val signal = DoubleArray(n) { idx ->
                if (numero in historico[idx].numeros) 1.0 else 0.0
            }

            // Restar media para centrar la señal
            val media = signal.average()
            for (i in signal.indices) signal[i] -= media

            // DFT para las frecuencias de interés (períodos de 3 a maxPeriodo)
            var mejorAmplitud = 0.0
            var mejorFrecuencia = 0.0
            var mejorFase = 0.0

            val periodoMin = 3
            for (periodo in periodoMin..minOf(maxPeriodo, n / 3)) {
                val freq = 1.0 / periodo
                var cosSum = 0.0
                var sinSum = 0.0

                for (t in signal.indices) {
                    val angulo = 2.0 * PI * freq * t
                    cosSum += signal[t] * cos(angulo)
                    sinSum += signal[t] * sin(angulo)
                }

                val amplitud = 2.0 * sqrt(cosSum.pow(2) + sinSum.pow(2)) / n

                if (amplitud > mejorAmplitud) {
                    mejorAmplitud = amplitud
                    mejorFrecuencia = freq
                    mejorFase = atan2(sinSum, cosSum)
                }
            }

            // Calcular predicción de próxima aparición
            val periodoDominante = if (mejorFrecuencia > 0) 1.0 / mejorFrecuencia else n.toDouble()

            // Fase actual: dónde estamos en el ciclo
            val faseActual = (2.0 * PI * mejorFrecuencia * (n - 1) + mejorFase) % (2.0 * PI)

            // Predecir cuántos sorteos hasta el próximo pico
            val fasePico = PI / 2.0 // El pico del seno
            val diferenciaFase = ((fasePico - faseActual) % (2.0 * PI) + 2.0 * PI) % (2.0 * PI)
            val sorteoHastaPico = (diferenciaFase / (2.0 * PI * mejorFrecuencia)).roundToInt()
                .coerceIn(0, maxPeriodo)

            // Confianza en la periodicidad basada en la amplitud relativa
            // Comparar con ruido esperado (1/sqrt(n) para señal aleatoria)
            val ruidoEsperado = 1.0 / sqrt(n.toDouble())
            val confianza = (mejorAmplitud / (ruidoEsperado * 3)).coerceIn(0.0, 1.0)

            ComponenteFourier(
                numero = numero,
                frecuenciaDominante = mejorFrecuencia,
                periodoDominante = periodoDominante,
                amplitud = mejorAmplitud,
                fase = faseActual,
                prediccionProximaSalida = sorteoHastaPico,
                confianzaPeriodicidad = confianza
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. INFERENCIA BAYESIANA: PROBABILIDADES ACTUALIZADAS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resultado bayesiano para un número.
     */
    data class PosteriorBayesiano(
        val numero: Int,
        val priorAlpha: Double,     // Parámetro α de la distribución Beta
        val priorBeta: Double,      // Parámetro β de la distribución Beta
        val posteriorMedia: Double, // Media posterior = α / (α + β)
        val posteriorVarianza: Double,
        val intervaloCredibilidad95: Pair<Double, Double>,
        val factorBayes: Double,    // Evidencia a favor vs uniforme
        val esFavorecido: Boolean   // Posterior > prior uniforme
    )

    /**
     * Inferencia Bayesiana usando distribución Beta-Binomial.
     *
     * Prior: Beta(1, 1) = Uniforme (sin información previa)
     * Likelihood: Binomial (aparece o no en cada sorteo)
     * Posterior: Beta(1 + éxitos, 1 + fracasos)
     *
     * La belleza de este enfoque es que cada sorteo actualiza las creencias.
     * Después de miles de sorteos, las probabilidades convergen al verdadero sesgo.
     */
    fun inferenciaBayesiana(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int
    ): Map<Int, PosteriorBayesiano> {
        val n = historico.size
        val pUniforme = cantidadNumeros.toDouble() / maxNumero

        return (1..maxNumero).associateWith { numero ->
            val exitos = historico.count { numero in it.numeros }
            val fracasos = n - exitos

            // Prior Beta(1, 1) → Posterior Beta(1 + éxitos, 1 + fracasos)
            val alpha = 1.0 + exitos
            val beta = 1.0 + fracasos

            val posteriorMedia = alpha / (alpha + beta)
            val posteriorVarianza = (alpha * beta) / ((alpha + beta).pow(2) * (alpha + beta + 1))

            // Intervalo de credibilidad al 95% (aproximación normal para n grande)
            val stdDev = sqrt(posteriorVarianza)
            val intervalo = Pair(
                (posteriorMedia - 1.96 * stdDev).coerceAtLeast(0.0),
                (posteriorMedia + 1.96 * stdDev).coerceAtMost(1.0)
            )

            // Factor de Bayes: ¿cuánto más probable es que este número esté sesgado
            // vs que sea uniforme?
            val logBF = exitos * ln(posteriorMedia / pUniforme) +
                        fracasos * ln((1 - posteriorMedia) / (1 - pUniforme))
            val factorBayes = exp(logBF.coerceIn(-50.0, 50.0))

            PosteriorBayesiano(
                numero = numero,
                priorAlpha = alpha,
                priorBeta = beta,
                posteriorMedia = posteriorMedia,
                posteriorVarianza = posteriorVarianza,
                intervaloCredibilidad95 = intervalo,
                factorBayes = factorBayes,
                esFavorecido = posteriorMedia > pUniforme
            )
        }
    }

    /**
     * Bayesiano con decaimiento temporal: más peso a los sorteos recientes.
     * Simula el efecto de cambios en la máquina/bolas a lo largo del tiempo.
     */
    fun bayesianoTemporal(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        decaimiento: Double = 0.995 // Cada sorteo antiguo pierde 0.5% de peso
    ): Map<Int, Double> {
        return (1..maxNumero).associateWith { numero ->
            var alphaWeighted = 1.0
            var betaWeighted = 1.0

            historico.forEachIndexed { idx, sorteo ->
                val peso = decaimiento.pow(idx.toDouble()) // Más reciente = más peso
                if (numero in sorteo.numeros) {
                    alphaWeighted += peso
                } else {
                    betaWeighted += peso
                }
            }

            alphaWeighted / (alphaWeighted + betaWeighted)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. CADENAS DE MARKOV: TRANSICIONES ENTRE SORTEOS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resultado Markov para un número.
     */
    data class TransicionMarkov(
        val numero: Int,
        val probDespuesDeAparecer: Double,    // P(sale | salió en el anterior)
        val probDespuesDeNoAparecer: Double,  // P(sale | NO salió en el anterior)
        val estadoActual: Boolean,            // Si salió en el último sorteo
        val prediccionProximoSorteo: Double,  // Probabilidad para el próximo
        val esMarkovSignificativo: Boolean    // Si hay diferencia real entre transiciones
    )

    /**
     * Análisis de Cadenas de Markov de primer orden.
     *
     * Para cada número, calcula las probabilidades de transición:
     * - P(sale | salió antes) → ¿Hay "momentum"?
     * - P(sale | no salió antes) → ¿Hay "compensación"?
     *
     * Si ambas son iguales ≈ el proceso es sin memoria (como debería ser).
     * Si difieren significativamente → hay un patrón explotable.
     */
    fun analizarMarkov(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int
    ): Map<Int, TransicionMarkov> {
        if (historico.size < 50) return emptyMap()

        return (1..maxNumero).associateWith { numero ->
            var conteo11 = 0 // Sale → Sale
            var conteo10 = 0 // Sale → No sale
            var conteo01 = 0 // No sale → Sale
            var conteo00 = 0 // No sale → No sale

            for (i in 0 until historico.size - 1) {
                val actual = numero in historico[i].numeros
                val siguiente = numero in historico[i + 1].numeros

                when {
                    actual && siguiente -> conteo11++
                    actual && !siguiente -> conteo10++
                    !actual && siguiente -> conteo01++
                    else -> conteo00++
                }
            }

            val totalDespuesSale = conteo11 + conteo10
            val totalDespuesNoSale = conteo01 + conteo00

            val probDespuesSale = if (totalDespuesSale > 0) {
                conteo11.toDouble() / totalDespuesSale
            } else 0.0

            val probDespuesNoSale = if (totalDespuesNoSale > 0) {
                conteo01.toDouble() / totalDespuesNoSale
            } else 0.0

            // Test de significancia: ¿la diferencia es real?
            val diferencia = abs(probDespuesSale - probDespuesNoSale)
            // Error estándar aproximado
            val se = sqrt(
                (probDespuesSale * (1 - probDespuesSale)) / totalDespuesSale.coerceAtLeast(1) +
                (probDespuesNoSale * (1 - probDespuesNoSale)) / totalDespuesNoSale.coerceAtLeast(1)
            )
            val zScore = if (se > 0) diferencia / se else 0.0
            val esSignificativo = zScore > 1.96 // p < 0.05

            // Estado actual y predicción
            val estadoActual = numero in historico[0].numeros
            val prediccion = if (estadoActual) probDespuesSale else probDespuesNoSale

            TransicionMarkov(
                numero = numero,
                probDespuesDeAparecer = probDespuesSale,
                probDespuesDeNoAparecer = probDespuesNoSale,
                estadoActual = estadoActual,
                prediccionProximoSorteo = prediccion,
                esMarkovSignificativo = esSignificativo
            )
        }
    }

    /**
     * Markov de SEGUNDO ORDEN: considera los dos sorteos anteriores.
     * P(sale | estado anterior 1, estado anterior 2)
     */
    fun markovSegundoOrden(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int
    ): Map<Int, Double> {
        if (historico.size < 100) return emptyMap()

        return (1..maxNumero).associateWith { numero ->
            // Estado: (hace 2 sorteos, hace 1 sorteo)
            val transiciones = mutableMapOf<Pair<Boolean, Boolean>, MutableList<Boolean>>()

            for (i in 0 until historico.size - 2) {
                val hace2 = numero in historico[i + 2].numeros
                val hace1 = numero in historico[i + 1].numeros
                val ahora = numero in historico[i].numeros

                val estado = Pair(hace2, hace1)
                transiciones.getOrPut(estado) { mutableListOf() }.add(ahora)
            }

            // Estado actual
            val estadoActual = Pair(
                numero in historico[1].numeros,
                numero in historico[0].numeros
            )

            val transicionesDelEstado = transiciones[estadoActual] ?: return@associateWith 0.0
            if (transicionesDelEstado.isEmpty()) return@associateWith 0.0

            transicionesDelEstado.count { it }.toDouble() / transicionesDelEstado.size
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. ANÁLISIS DE ENTROPÍA: VENTANAS DE BAJA ALEATORIEDAD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resultado de análisis de entropía.
     */
    data class AnalisisEntropia(
        val entropiaActual: Double,        // Entropía de Shannon actual
        val entropiaMaxima: Double,        // Entropía máxima teórica (log2(maxNum))
        val ratioEntropia: Double,         // actual / máxima (1.0 = máximamente aleatorio)
        val esVentanaBajaEntropia: Boolean,// Si la entropía es inusualmente baja
        val numerosConcentrados: List<Int>,// Números que concentran más probabilidad
        val redundancia: Double            // 1 - ratio (qué tan predecible es)
    )

    /**
     * Entropía de Shannon para la distribución de números.
     *
     * H = -Σ p(x) * log2(p(x))
     *
     * Entropía alta = muy aleatorio = difícil de predecir
     * Entropía baja = concentrado = hay oportunidad
     *
     * Si la entropía cae significativamente, indica que el sistema
     * se ha vuelto más predecible temporalmente.
     */
    fun calcularEntropia(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        ventana: Int = 50
    ): AnalisisEntropia {
        val sorteosRecientes = historico.take(ventana)
        val totalNumeros = sorteosRecientes.sumOf { it.numeros.size }

        val frecuencias = IntArray(maxNumero + 1)
        sorteosRecientes.forEach { sorteo ->
            sorteo.numeros.forEach { num ->
                if (num in 1..maxNumero) frecuencias[num]++
            }
        }

        var entropia = 0.0
        val numerosConcentrados = mutableListOf<Int>()
        val umbralConcentracion = 1.5 / maxNumero // 1.5x la probabilidad uniforme

        for (num in 1..maxNumero) {
            val p = frecuencias[num].toDouble() / totalNumeros
            if (p > 0) {
                entropia -= p * ln(p) / ln(2.0) // log base 2
            }
            if (p > umbralConcentracion) {
                numerosConcentrados.add(num)
            }
        }

        val entropiaMaxima = ln(maxNumero.toDouble()) / ln(2.0)
        val ratio = entropia / entropiaMaxima

        // Comparar con la entropía de otras ventanas para detectar anomalía
        val entropiasHistoricas = mutableListOf<Double>()
        for (i in ventana until minOf(historico.size, ventana * 10) step ventana) {
            val subHistorico = historico.subList(i, minOf(i + ventana, historico.size))
            val totalSub = subHistorico.sumOf { it.numeros.size }
            val freqSub = IntArray(maxNumero + 1)
            subHistorico.forEach { s -> s.numeros.forEach { n -> if (n in 1..maxNumero) freqSub[n]++ } }

            var eSub = 0.0
            for (num in 1..maxNumero) {
                val p = freqSub[num].toDouble() / totalSub.coerceAtLeast(1)
                if (p > 0) eSub -= p * ln(p) / ln(2.0)
            }
            entropiasHistoricas.add(eSub)
        }

        val mediaHistorica = if (entropiasHistoricas.isNotEmpty()) entropiasHistoricas.average() else entropia
        val stdHistorica = if (entropiasHistoricas.size > 1) {
            sqrt(entropiasHistoricas.map { (it - mediaHistorica).pow(2) }.average())
        } else 0.1

        val esBajaEntropia = entropia < mediaHistorica - 1.5 * stdHistorica

        return AnalisisEntropia(
            entropiaActual = entropia,
            entropiaMaxima = entropiaMaxima,
            ratioEntropia = ratio,
            esVentanaBajaEntropia = esBajaEntropia,
            numerosConcentrados = numerosConcentrados.sortedByDescending { frecuencias[it] },
            redundancia = 1.0 - ratio
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 6. DISEÑOS DE COBERTURA: MAXIMIZAR ACIERTOS PARCIALES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Optimiza combinaciones para maximizar la probabilidad de aciertos parciales (3+, 4+).
     *
     * En vez de intentar acertar los 6, busca CUBRIR el mayor espacio posible
     * de números probables, maximizando P(al menos 3 aciertos).
     *
     * Usa un algoritmo greedy de cobertura máxima.
     */
    fun disenoCobertura(
        numerosOrdenados: List<Pair<Int, Double>>, // (número, score) ordenados por score
        cantidadNumeros: Int,
        numCombinaciones: Int,
        maxNumero: Int
    ): List<List<Int>> {
        val topNumeros = numerosOrdenados.take(cantidadNumeros * numCombinaciones)
            .map { it.first }

        if (topNumeros.size < cantidadNumeros) {
            return listOf(topNumeros.take(cantidadNumeros))
        }

        val combinaciones = mutableListOf<List<Int>>()
        val numerosUsados = mutableMapOf<Int, Int>() // número -> veces usado

        repeat(numCombinaciones) {
            val combinacion = mutableListOf<Int>()
            val candidatos = topNumeros.toMutableList()

            // Priorizar números poco usados en combinaciones anteriores
            candidatos.sortBy { numerosUsados.getOrDefault(it, 0) }

            while (combinacion.size < cantidadNumeros && candidatos.isNotEmpty()) {
                val num = candidatos.removeAt(0)
                if (num !in combinacion) {
                    combinacion.add(num)
                    numerosUsados[num] = (numerosUsados[num] ?: 0) + 1
                }
            }

            // Completar si es necesario
            while (combinacion.size < cantidadNumeros) {
                val disponible = (1..maxNumero).filter { it !in combinacion }.randomOrNull() ?: break
                combinacion.add(disponible)
            }

            combinaciones.add(combinacion.sorted())
        }

        return combinaciones
    }

    /**
     * Cobertura con restricción de diversidad.
     * Garantiza que entre todas las combinaciones se cubran al menos
     * K números distintos del pool de favoritos.
     */
    fun coberturaConDiversidad(
        scoresNumeros: Map<Int, Double>,
        cantidadNumeros: Int,
        numCombinaciones: Int,
        maxNumero: Int,
        minSolapamiento: Int = 2,
        maxSolapamiento: Int = 3
    ): List<List<Int>> {
        val ordenados = scoresNumeros.entries
            .sortedByDescending { it.value }
            .map { it.key }

        val combinaciones = mutableListOf<List<Int>>()

        // Primera combinación: los mejores directamente
        val primera = ordenados.take(cantidadNumeros)
        combinaciones.add(primera)

        // Siguientes: rotar manteniendo un núcleo compartido
        for (i in 1 until numCombinaciones) {
            val nucleo = primera.take(minSolapamiento)
            val offset = i * (cantidadNumeros - minSolapamiento)
            val nuevos = ordenados
                .filter { it !in nucleo }
                .drop(offset % (ordenados.size - minSolapamiento))
                .take(cantidadNumeros - minSolapamiento)

            val combinacion = (nucleo + nuevos).distinct().take(cantidadNumeros)

            // Completar si faltan
            val completa = if (combinacion.size < cantidadNumeros) {
                val extras = (1..maxNumero).filter { it !in combinacion }
                    .shuffled().take(cantidadNumeros - combinacion.size)
                combinacion + extras
            } else combinacion

            combinaciones.add(completa.sorted())
        }

        return combinaciones
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 7. VALIDACIÓN MONTE CARLO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resultado de validación Monte Carlo.
     */
    data class ResultadoMonteCarlo(
        val aciertosMetodo: Double,     // Promedio de aciertos del método
        val aciertosAleatorio: Double,  // Promedio de aciertos aleatorios
        val mejora: Double,             // (método - aleatorio) / aleatorio * 100
        val esMejorQueAleatorio: Boolean,
        val significanciaEstadistica: Double, // p-valor del test
        val intervaloConfianza: Pair<Double, Double>
    )

    /**
     * Compara una estrategia de selección contra aleatorio puro.
     *
     * Ejecuta N simulaciones comparando:
     * - Combinaciones generadas por el método
     * - Combinaciones puramente aleatorias
     *
     * Retorna si el método es significativamente mejor que el azar.
     */
    fun validacionMonteCarlo(
        historico: List<ResultadoPrimitiva>,
        numerosPreferidos: List<Int>,
        maxNumero: Int,
        cantidadNumeros: Int,
        simulaciones: Int = 500,
        ventanaValidacion: Int = 50
    ): ResultadoMonteCarlo {
        if (historico.size < ventanaValidacion + 50) {
            return ResultadoMonteCarlo(0.0, 0.0, 0.0, false, 1.0, Pair(0.0, 0.0))
        }

        val rnd = Random(42) // Semilla fija para reproducibilidad
        val sorteosPrueba = historico.take(ventanaValidacion)
        val aciertosMetodo = mutableListOf<Int>()
        val aciertosAleatorio = mutableListOf<Int>()

        repeat(simulaciones) {
            // Generar combinación del método (selección ponderada de los preferidos)
            val combinacionMetodo = if (numerosPreferidos.size >= cantidadNumeros) {
                numerosPreferidos.shuffled(rnd).take(cantidadNumeros)
            } else {
                numerosPreferidos + (1..maxNumero).filter { it !in numerosPreferidos }
                    .shuffled(rnd).take(cantidadNumeros - numerosPreferidos.size)
            }

            // Generar combinación aleatoria
            val combinacionAleatoria = (1..maxNumero).shuffled(rnd).take(cantidadNumeros)

            // Evaluar contra sorteos reales
            val sorteoReal = sorteosPrueba[rnd.nextInt(sorteosPrueba.size)]
            val numerosReales = sorteoReal.numeros.toSet()

            aciertosMetodo.add(combinacionMetodo.count { it in numerosReales })
            aciertosAleatorio.add(combinacionAleatoria.count { it in numerosReales })
        }

        val mediaMetodo = aciertosMetodo.average()
        val mediaAleatorio = aciertosAleatorio.average()

        // Test t de Student para diferencia de medias
        val varianzaMetodo = aciertosMetodo.map { (it - mediaMetodo).pow(2) }.average()
        val varianzaAleatorio = aciertosAleatorio.map { (it - mediaAleatorio).pow(2) }.average()

        val se = sqrt(varianzaMetodo / simulaciones + varianzaAleatorio / simulaciones)
        val tStat = if (se > 0) (mediaMetodo - mediaAleatorio) / se else 0.0
        val pValor = aproximarPValorT(abs(tStat), simulaciones * 2 - 2)

        val mejora = if (mediaAleatorio > 0) {
            (mediaMetodo - mediaAleatorio) / mediaAleatorio * 100
        } else 0.0

        val stdMetodo = sqrt(varianzaMetodo)
        val intervalo = Pair(
            mediaMetodo - 1.96 * stdMetodo / sqrt(simulaciones.toDouble()),
            mediaMetodo + 1.96 * stdMetodo / sqrt(simulaciones.toDouble())
        )

        return ResultadoMonteCarlo(
            aciertosMetodo = mediaMetodo,
            aciertosAleatorio = mediaAleatorio,
            mejora = mejora,
            esMejorQueAleatorio = pValor < 0.05 && mediaMetodo > mediaAleatorio,
            significanciaEstadistica = pValor,
            intervaloConfianza = intervalo
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 8. CONVERGENCIA MULTI-DIMENSIONAL DEL ABUELO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Score final de convergencia multi-dimensional.
     */
    data class ConvergenciaFinal(
        val numero: Int,
        val scoreTotal: Double,          // Score combinado (0-100)
        val chiCuadrado: Double,         // Sesgo estadístico real
        val bayesiano: Double,           // Probabilidad posterior
        val fourier: Double,             // Score periódico
        val markov: Double,              // Predicción Markov
        val entropia: Double,            // Contribución entrópica
        val convergenciaClasica: Double, // Score del método clásico
        val factoresActivos: Int,        // Cuántos factores son positivos
        val explicacion: String
    )

    /**
     * EL MÉTODO DEFINITIVO DEL ABUELO.
     *
     * Combina TODOS los análisis matemáticos en un único score ponderado
     * por la calidad estadística de cada uno.
     *
     * Solo da peso significativo a factores ESTADÍSTICAMENTE VALIDADOS.
     * Si un factor no es significativo (p > 0.05), su peso se reduce.
     */
    fun calcularConvergenciaDefinitiva(
        historico: List<ResultadoPrimitiva>,
        maxNumero: Int,
        cantidadNumeros: Int,
        pesosAprendidos: Map<String, Double> = emptyMap()
    ): List<ConvergenciaFinal> {
        // 1. Chi-Cuadrado: sesgos reales
        val (chiTotal, chiResultados) = testChiCuadradoGlobal(historico, maxNumero, cantidadNumeros)
        val sesgosConsistentes = numerosConSesgoConsistente(historico, maxNumero, cantidadNumeros)

        // 2. Bayesiano
        val bayesianos = inferenciaBayesiana(historico, maxNumero, cantidadNumeros)
        val bayesianoTemporal = bayesianoTemporal(historico, maxNumero, cantidadNumeros)

        // 3. Fourier
        val fourier = analizarFourier(historico, maxNumero)

        // 4. Markov
        val markov1 = analizarMarkov(historico, maxNumero)
        val markov2 = markovSegundoOrden(historico, maxNumero)

        // 5. Entropía
        val entropia = calcularEntropia(historico, maxNumero)

        // Determinar pesos basados en significancia estadística
        val haySesgosReales = chiResultados.count { it.esSignificativo } > maxNumero * 0.1
        val hayPatronesMarkov = markov1.count { it.value.esMarkovSignificativo } > maxNumero * 0.05
        val hayPeriodicidades = fourier.count { (it.value.confianzaPeriodicidad > 0.5) } > maxNumero * 0.05
        val hayBajaEntropia = entropia.esVentanaBajaEntropia

        // Pesos adaptativos según lo que sea estadísticamente significativo
        var pesoChi = if (haySesgosReales) 0.30 else 0.10
        var pesoBayes = 0.20 // Siempre útil
        var pesoFourier = if (hayPeriodicidades) 0.20 else 0.05
        var pesoMarkov = if (hayPatronesMarkov) 0.15 else 0.05
        var pesoEntropia = if (hayBajaEntropia) 0.15 else 0.05

        // Integrar pesos aprendidos si existen (mezcla 50/50 con pesos estadísticos)
        if (pesosAprendidos.isNotEmpty()) {
            val mezcla = 0.5 // 50% estadístico + 50% aprendido
            pesoChi = pesoChi * (1 - mezcla) + (pesosAprendidos["chiCuadrado"] ?: 0.2) * mezcla
            pesoBayes = pesoBayes * (1 - mezcla) + (pesosAprendidos["bayesiano"] ?: 0.2) * mezcla
            pesoFourier = pesoFourier * (1 - mezcla) + (pesosAprendidos["fourier"] ?: 0.2) * mezcla
            pesoMarkov = pesoMarkov * (1 - mezcla) + (pesosAprendidos["markov"] ?: 0.2) * mezcla
            pesoEntropia = pesoEntropia * (1 - mezcla) + (pesosAprendidos["entropia"] ?: 0.2) * mezcla
        }

        // Normalizar pesos
        val sumaPesos = pesoChi + pesoBayes + pesoFourier + pesoMarkov + pesoEntropia
        val pChi = pesoChi / sumaPesos
        val pBayes = pesoBayes / sumaPesos
        val pFourier = pesoFourier / sumaPesos
        val pMarkov = pesoMarkov / sumaPesos
        val pEntropia = pesoEntropia / sumaPesos

        // Calcular score para cada número
        return (1..maxNumero).map { num ->
            // Chi-Cuadrado score
            val chiRes = chiResultados.find { it.numero == num }
            val scoreChi = if (chiRes != null && chiRes.sesgo > 0) {
                // Combinar sesgo puntual con sesgo consistente
                val sesgoPuntual = chiRes.sesgo.coerceIn(0.0, 1.0)
                val sesgoConsistente = sesgosConsistentes[num] ?: 0.0
                ((sesgoPuntual * 0.4 + sesgoConsistente * 0.6) * 100).coerceIn(0.0, 100.0)
            } else 0.0

            // Bayesiano score
            val bayesRes = bayesianos[num]
            val bayesTempRes = bayesianoTemporal[num] ?: 0.0
            val pUniforme = cantidadNumeros.toDouble() / maxNumero
            val scoreBayes = if (bayesRes != null) {
                val desviacion = (bayesRes.posteriorMedia - pUniforme) / pUniforme
                val desvTemporal = (bayesTempRes - pUniforme) / pUniforme
                val scoreCombinado = (desviacion * 0.5 + desvTemporal * 0.5)
                (scoreCombinado * 500 + 50).coerceIn(0.0, 100.0) // Centrado en 50
            } else 50.0

            // Fourier score
            val fourierRes = fourier[num]
            val scoreFourier = if (fourierRes != null && fourierRes.confianzaPeriodicidad > 0.3) {
                // Score alto si está a punto de alcanzar su pico periódico
                val proximidad = 1.0 - (fourierRes.prediccionProximaSalida.toDouble() /
                    fourierRes.periodoDominante).coerceIn(0.0, 1.0)
                (proximidad * fourierRes.confianzaPeriodicidad * 100).coerceIn(0.0, 100.0)
            } else 50.0

            // Markov score
            val markovRes = markov1[num]
            val markov2Res = markov2[num] ?: 0.0
            val scoreMarkov = if (markovRes != null) {
                val prediccion = markovRes.prediccionProximoSorteo
                val prediccion2 = markov2Res
                ((prediccion * 0.6 + prediccion2 * 0.4) / pUniforme * 50).coerceIn(0.0, 100.0)
            } else 50.0

            // Entropía score
            val scoreEntropia = if (num in entropia.numerosConcentrados) {
                val posicion = entropia.numerosConcentrados.indexOf(num)
                (100.0 - posicion * 5).coerceIn(50.0, 100.0)
            } else 40.0

            // Score total ponderado
            val scoreTotal = scoreChi * pChi +
                            scoreBayes * pBayes +
                            scoreFourier * pFourier +
                            scoreMarkov * pMarkov +
                            scoreEntropia * pEntropia

            // Contar factores activos (por encima del umbral neutro de 55)
            var factoresActivos = 0
            if (scoreChi > 55) factoresActivos++
            if (scoreBayes > 55) factoresActivos++
            if (scoreFourier > 55) factoresActivos++
            if (scoreMarkov > 55) factoresActivos++
            if (scoreEntropia > 55) factoresActivos++

            // Generar explicación
            val explicacionParts = mutableListOf<String>()
            if (scoreChi > 60) explicacionParts.add("χ²↑")
            if (scoreBayes > 60) explicacionParts.add("Bay↑")
            if (scoreFourier > 60) explicacionParts.add("Fou↑")
            if (scoreMarkov > 60) explicacionParts.add("Mkv↑")
            if (scoreEntropia > 60) explicacionParts.add("Ent↑")

            ConvergenciaFinal(
                numero = num,
                scoreTotal = scoreTotal,
                chiCuadrado = scoreChi,
                bayesiano = scoreBayes,
                fourier = scoreFourier,
                markov = scoreMarkov,
                entropia = scoreEntropia,
                convergenciaClasica = 0.0, // Se llenará después si hay convergencia clásica
                factoresActivos = factoresActivos,
                explicacion = if (explicacionParts.isNotEmpty()) explicacionParts.joinToString(" ") else "–"
            )
        }.sortedByDescending { it.scoreTotal }
    }

    /**
     * Construye la combinación óptima final del Abuelo.
     * Respeta restricciones de balance y rango de sumas.
     */
    fun construirCombinacionOptima(
        convergencias: List<ConvergenciaFinal>,
        cantidadNumeros: Int,
        maxNumero: Int,
        historico: List<ResultadoPrimitiva>
    ): List<Int> {
        // Calcular rango de sumas óptimo
        val sumas = historico.map { it.numeros.take(cantidadNumeros).sum() }
        val sumaMedia = sumas.average()
        val sumaStd = sqrt(sumas.map { (it - sumaMedia).pow(2) }.average())
        val sumaMin = (sumaMedia - sumaStd).toInt()
        val sumaMax = (sumaMedia + sumaStd).toInt()

        val mitad = maxNumero / 2
        val seleccionados = mutableListOf<Int>()
        val candidatos = convergencias.toMutableList()

        // Fase 1: Añadir números con mayor convergencia respetando balance
        while (seleccionados.size < cantidadNumeros && candidatos.isNotEmpty()) {
            val mejor = candidatos.removeAt(0)
            val num = mejor.numero

            // Verificar balance pares/impares
            val paresActuales = seleccionados.count { it % 2 == 0 }
            val imparesActuales = seleccionados.size - paresActuales
            val maxPermitido = (cantidadNumeros + 1) / 2 + 1

            if (num % 2 == 0 && paresActuales >= maxPermitido) continue
            if (num % 2 != 0 && imparesActuales >= maxPermitido) continue

            // Verificar balance altos/bajos
            val bajosActuales = seleccionados.count { it <= mitad }
            val altosActuales = seleccionados.size - bajosActuales
            if (num <= mitad && bajosActuales >= maxPermitido) continue
            if (num > mitad && altosActuales >= maxPermitido) continue

            seleccionados.add(num)
        }

        // Fase 2: Verificar suma y ajustar si necesario
        var intentos = 0
        while (intentos < 30 && seleccionados.size == cantidadNumeros) {
            val suma = seleccionados.sum()
            if (suma in sumaMin..sumaMax) break

            if (suma < sumaMin) {
                // Reemplazar el menor por uno mayor
                val menor = seleccionados.minOrNull() ?: break
                val reemplazo = convergencias
                    .filter { it.numero > menor && it.numero !in seleccionados }
                    .firstOrNull()?.numero
                if (reemplazo != null) {
                    seleccionados.remove(menor)
                    seleccionados.add(reemplazo)
                } else break
            } else {
                val mayor = seleccionados.maxOrNull() ?: break
                val reemplazo = convergencias
                    .filter { it.numero < mayor && it.numero !in seleccionados }
                    .firstOrNull()?.numero
                if (reemplazo != null) {
                    seleccionados.remove(mayor)
                    seleccionados.add(reemplazo)
                } else break
            }
            intentos++
        }

        // Completar si faltan
        while (seleccionados.size < cantidadNumeros) {
            val disponible = (1..maxNumero).filter { it !in seleccionados }.randomOrNull() ?: break
            seleccionados.add(disponible)
        }

        return seleccionados.sorted()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FUNCIONES AUXILIARES MATEMÁTICAS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Aproximación del p-valor para distribución Chi-Cuadrado.
     * Usa la aproximación de Wilson-Hilferty.
     */
    private fun aproximarPValorChi(chiCuadrado: Double, gl: Int): Double {
        if (chiCuadrado <= 0 || gl <= 0) return 1.0

        // Transformación Wilson-Hilferty a normal estándar
        val k = gl.toDouble()
        val z = ((chiCuadrado / k).pow(1.0 / 3) - (1 - 2.0 / (9 * k))) / sqrt(2.0 / (9 * k))

        // Aproximación del p-valor de la normal estándar (cola superior)
        return aproximarPValorNormal(z)
    }

    /**
     * Aproximación del p-valor para test t.
     * Para gl > 30, la distribución t se aproxima a la normal.
     */
    private fun aproximarPValorT(tStat: Double, gl: Int): Double {
        // Para gl grandes, t ≈ normal
        if (gl > 30) return aproximarPValorNormal(tStat) * 2 // Test bilateral

        // Para gl pequeños, usar aproximación más conservadora
        val z = tStat * (1 - 1.0 / (4 * gl))
        return aproximarPValorNormal(abs(z)) * 2
    }

    /**
     * Aproximación del p-valor de la normal estándar (cola superior).
     * Usa la aproximación de Abramowitz & Stegun.
     */
    private fun aproximarPValorNormal(z: Double): Double {
        if (z < 0) return 1.0 - aproximarPValorNormal(-z)
        if (z > 8) return 0.0

        val b0 = 0.2316419
        val b1 = 0.319381530
        val b2 = -0.356563782
        val b3 = 1.781477937
        val b4 = -1.821255978
        val b5 = 1.330274429

        val t = 1.0 / (1.0 + b0 * z)
        val phi = exp(-z * z / 2.0) / sqrt(2.0 * PI)

        val pValor = phi * (b1 * t + b2 * t.pow(2) + b3 * t.pow(3) + b4 * t.pow(4) + b5 * t.pow(5))
        return pValor.coerceIn(0.0, 1.0)
    }

    /**
     * Genera un resumen legible del análisis matemático.
     */
    fun generarResumenAnalisis(
        chiTotal: Double,
        haySesgos: Boolean,
        hayPeriodicidades: Boolean,
        hayMarkov: Boolean,
        bajaEntropia: Boolean,
        mejoraMonteCarlo: Double
    ): String {
        val sb = StringBuilder()
        sb.append("📐 ANÁLISIS MATEMÁTICO DEL ABUELO\n\n")

        if (haySesgos) {
            sb.append("✅ χ²=${"%.1f".format(chiTotal)}: Detectados sesgos estadísticamente significativos\n")
        } else {
            sb.append("⚠️ χ²=${"%.1f".format(chiTotal)}: Sin sesgos significativos detectados\n")
        }

        if (hayPeriodicidades) {
            sb.append("✅ Fourier: Periodicidades genuinas encontradas\n")
        }

        if (hayMarkov) {
            sb.append("✅ Markov: Patrones de transición significativos\n")
        }

        if (bajaEntropia) {
            sb.append("✅ Entropía baja: Ventana favorable para predicción\n")
        }

        sb.append("\n📊 Mejora vs aleatorio: ${"%.1f".format(mejoraMonteCarlo)}%")

        return sb.toString()
    }
}
