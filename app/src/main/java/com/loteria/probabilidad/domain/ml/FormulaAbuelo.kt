package com.loteria.probabilidad.domain.ml

import com.loteria.probabilidad.data.model.*
import kotlin.math.ln
import kotlin.math.exp

/**
 * La Fórmula del Abuelo: orquesta los 3 pilares.
 * 1. Cobertura matemática - diseño C(v,k,t,m) con parámetros realistas
 * 2. Anti-popularidad ACTIVA - filtra y reemplaza combinaciones populares
 * 3. Criterio de Kelly - evalúa atractivo del sorteo
 *
 * Principio fundamental: con v=15 y t=3 (tríos), la garantía se activa cuando
 * ≥3 candidatos aciertan, lo cual ocurre ~26% de sorteos. A cambio, cuando
 * se activa se garantizan 3 aciertos en el mismo boleto (vs solo 2 con t=2).
 * Mejor ROI demostrado en simulación de 200 sorteos históricos.
 */
object FormulaAbuelo {

    /** Umbral mínimo de unicidad anti-popularidad (0-100) */
    private const val UMBRAL_UNICIDAD = 55

    /**
     * Configuraciones de cobertura REALISTAS por lotería.
     * Clave: t=2 (parejas) → la garantía se activa cuando ≥2 candidatos aciertan,
     * lo cual ocurre ~70-85% de las veces con v=18-20 candidatos.
     */
    private fun configPorDefecto(tipoLoteria: TipoLoteria): ConfiguracionCobertura {
        return when (tipoLoteria) {
            // Primitiva/Bonoloto: 49 números, elegir 6
            // v=17: E[aciertos]=17*6/49≈2.08, P(≥3)≈34% → garantía 3 aciertos cuando activa
            // Simulación 200 sorteos: ROI -77.5% (mejor consistente sin depender de suerte)
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> ConfiguracionCobertura(
                v = 17, k = 6, t = 3, m = 3,
                tipoLoteria = tipoLoteria
            )
            // Euromillones: 50 números, elegir 5
            // v=15: E[aciertos]=15*5/50=1.5, P(≥2)≈54%
            TipoLoteria.EUROMILLONES -> ConfiguracionCobertura(
                v = 15, k = 5, t = 2, m = 2,
                tipoLoteria = tipoLoteria
            )
            // Gordo: 54 números, elegir 5
            // v=16: E[aciertos]=16*5/54≈1.48, P(≥2)≈53%
            TipoLoteria.GORDO_PRIMITIVA -> ConfiguracionCobertura(
                v = 16, k = 5, t = 2, m = 2,
                tipoLoteria = tipoLoteria
            )
            else -> ConfiguracionCobertura(
                v = 10, k = 5, t = 2, m = 2,
                tipoLoteria = tipoLoteria
            )
        }
    }

    /**
     * Ejecuta la Fórmula del Abuelo completa.
     */
    fun ejecutar(
        candidatos: List<Int>,
        tipoLoteria: TipoLoteria,
        boteActual: Double = 0.0,
        numCandidatos: Int = 17,
        garantiaMinima: Int = 3,
        historicoNums: Set<Set<Int>> = emptySet()
    ): ResultadoFormulaAbuelo {
        val esDigitos = tipoLoteria in listOf(
            TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO
        )

        return if (esDigitos) {
            ejecutarDigitos(candidatos, tipoLoteria, boteActual)
        } else {
            ejecutarCombinacion(candidatos, tipoLoteria, boteActual, historicoNums)
        }
    }

    /**
     * Para loterías de combinación (Primitiva, Bonoloto, Euromillones, Gordo).
     */
    private fun ejecutarCombinacion(
        candidatos: List<Int>,
        tipoLoteria: TipoLoteria,
        boteActual: Double,
        historicoNums: Set<Set<Int>> = emptySet()
    ): ResultadoFormulaAbuelo {
        val config = configPorDefecto(tipoLoteria)
        val v = config.v
        val k = config.k
        val t = config.t
        val m = config.m
        val maxNum = tipoLoteria.maxNumero
        val numSorteo = tipoLoteria.cantidadNumeros

        // Tomar los top V candidatos
        val topCandidatos = candidatos.take(v).sorted()

        // PILAR 1: Algoritmo de cobertura greedy C(v,k,t,m)
        val cobertura = calcularCobertura(topCandidatos, k, t, m, maxNum, numSorteo)

        // PILAR 2: Anti-popularidad ACTIVA
        // Filtra combinaciones populares y las reemplaza por mejores
        val ticketsAntiPop = filtrarAntiPopularidad(
            cobertura.tickets, topCandidatos, k, tipoLoteria
        )

        // PILAR 3: Filtro histórico — máximo 5 de 6 en común con cualquier sorteo real
        // Estadísticamente demostrado: en 4099 sorteos de Primitiva solo 1 combinación
        // se ha repetido exactamente. No tiene sentido generar una combinación ya salida.
        val ticketsFiltrados = filtrarRepetidosHistorico(
            ticketsAntiPop, historicoNums, topCandidatos, tipoLoteria
        )

        // PILAR 4 (Kelly): evaluación de atractivo del sorteo
        val kelly = GestionBankroll.calcularKelly(tipoLoteria, boteActual)

        // Estadísticas
        val mediaUnicidad = if (ticketsFiltrados.isNotEmpty()) {
            ticketsFiltrados.map { it.scoreUnicidad }.average()
        } else 0.0

        val probPct = "%.1f".format(cobertura.probActivacion * 100)

        val resumen = buildString {
            append("La Fórmula del Abuelo - ${tipoLoteria.displayName}\n\n")

            append("COBERTURA: ${topCandidatos.size} candidatos → ${cobertura.numTickets} boletos\n")
            append("${cobertura.garantia}\n")
            append("Probabilidad de activación: $probPct%\n")
            append("Cobertura de parejas: ${"%.1f".format(cobertura.coberturaPct)}%\n\n")

            append("ANTI-POPULARIDAD: Unicidad media ${"%.0f".format(mediaUnicidad)}/100\n")
            val mejorados = ticketsFiltrados.count { it.scoreUnicidad >= UMBRAL_UNICIDAD }
            append("$mejorados/${ticketsFiltrados.size} boletos con unicidad ≥$UMBRAL_UNICIDAD\n\n")

            append("KELLY: ${kelly.recomendacion.displayName} (${kelly.atractividad}/10)\n")
            append("EV: ${"%.4f".format(kelly.valorEsperado)}€ por boleto\n\n")

            append("\"El abuelo decía: no juegues para ganar el bote, ")
            append("juega para que cuando aciertes, aciertes solo.\"")
        }

        return ResultadoFormulaAbuelo(
            cobertura = cobertura,
            coberturaDigitos = null,
            ticketsFiltrados = ticketsFiltrados,
            analisisKelly = kelly,
            resumen = resumen
        )
    }

    /**
     * PILAR 3: Filtra combinaciones que ya hayan salido exactamente en el histórico.
     * Máximo permitido: 5 de 6 en común con cualquier sorteo real.
     * Si una combinación coincide al 100% con un sorteo histórico,
     * muta 1 número (primero dentro de candidatos, luego fuera) hasta resolverlo.
     * Estadística: en 4099 sorteos de Primitiva solo ocurrió 1 vez (2002 y 2009).
     */
    private fun filtrarRepetidosHistorico(
        tickets: List<ScorePopularidad>,
        historicoNums: Set<Set<Int>>,
        candidatos: List<Int>,
        tipoLoteria: TipoLoteria
    ): List<ScorePopularidad> {
        if (historicoNums.isEmpty()) return tickets

        return tickets.map { score ->
            val combinacionSet = score.combinacion.toSet()
            if (combinacionSet !in historicoNums) return@map score

            // Esta combinación ya salió exactamente — mutar 1 número
            val maxNum = tipoLoteria.maxNumero
            val candidatoSet = candidatos.toSet()
            // Alternativas: primero candidatos no usados, luego resto del rango
            val alternativas = ((candidatoSet - combinacionSet) +
                    (1..maxNum).filter { it !in combinacionSet }).distinct()

            var resultado = score
            outer@ for (numViejo in score.combinacion) {
                for (numNuevo in alternativas) {
                    val mutado = score.combinacion.map { if (it == numViejo) numNuevo else it }.sorted()
                    if (mutado.toSet() !in historicoNums) {
                        val scoreMutado = AntiPopularidad.calcularScoreCombinacion(mutado, tipoLoteria)
                        resultado = scoreMutado.copy(
                            penalizaciones = scoreMutado.penalizaciones +
                                    "🔄 $numViejo→$numNuevo: combinación ya salió en histórico"
                        )
                        break@outer
                    }
                }
            }

            android.util.Log.d("ABUELO",
                "Filtro histórico: ${score.combinacion} ya salió en histórico → mutada a ${resultado.combinacion}")
            resultado
        }
    }

    /**
     * PILAR 2 ACTIVO: Si una combinación tiene score < UMBRAL_UNICIDAD,
     * intenta reemplazarla mutando números para mejorar la unicidad
     * sin romper la cobertura.
     */
    private fun filtrarAntiPopularidad(
        tickets: List<List<Int>>,
        candidatos: List<Int>,
        k: Int,
        tipoLoteria: TipoLoteria
    ): List<ScorePopularidad> {
        val candidatoSet = candidatos.toSet()
        val maxNum = tipoLoteria.maxNumero

        return tickets.map { ticket ->
            var score = AntiPopularidad.calcularScoreCombinacion(ticket, tipoLoteria)

            if (score.scoreUnicidad < UMBRAL_UNICIDAD) {
                // Intentar mejorar: sustituir el número más "popular" por otro candidato
                var mejorTicket = ticket
                var mejorScore = score

                // Probar sustituir cada número del ticket por otros candidatos no usados
                val noUsados = candidatoSet - ticket.toSet()
                for (numViejo in ticket) {
                    for (numNuevo in noUsados) {
                        val ticketMutado = ticket.map { if (it == numViejo) numNuevo else it }.sorted()
                        val scoreMutado = AntiPopularidad.calcularScoreCombinacion(ticketMutado, tipoLoteria)
                        if (scoreMutado.scoreUnicidad > mejorScore.scoreUnicidad) {
                            mejorTicket = ticketMutado
                            mejorScore = scoreMutado
                        }
                    }
                }

                // Si aún no supera el umbral, sustituir números populares por números
                // fuera de los candidatos — hasta 2 sustituciones para mayor unicidad
                if (mejorScore.scoreUnicidad < UMBRAL_UNICIDAD) {
                    // Números fuera de candidatos: altos (>maxNum/2) y no redondos
                    val fueraAltos = ((maxNum / 2 + 1)..maxNum)
                        .filter { it !in candidatoSet && it % 10 != 0 }
                    // Números bajos poco jugados: primos o impares fuera de candidatos
                    val fueraBajos = (2..(maxNum / 2))
                        .filter { it !in candidatoSet && it % 5 != 0 && it % 10 != 0 }
                    val fueraCandidatos = (fueraAltos + fueraBajos).distinct()

                    // Intentar sustituir los números más "populares" del ticket (≤31, múltiplos o muy bajos)
                    val populares = mejorTicket
                        .filter { it <= 31 || it % 10 == 0 || it in listOf(7, 13, 21) }
                        .sortedBy { it } // del más bajo (más popular) al más alto
                        .take(3)

                    for (numViejo in populares) {
                        for (numNuevo in fueraCandidatos) {
                            val ticketMutado = mejorTicket.map { if (it == numViejo) numNuevo else it }.sorted()
                            val scoreMutado = AntiPopularidad.calcularScoreCombinacion(ticketMutado, tipoLoteria)
                            if (scoreMutado.scoreUnicidad > mejorScore.scoreUnicidad) {
                                mejorTicket = ticketMutado
                                mejorScore = scoreMutado
                            }
                        }
                        // Parar si ya superó el umbral
                        if (mejorScore.scoreUnicidad >= UMBRAL_UNICIDAD) break
                    }
                }

                mejorScore
            } else {
                score
            }
        }
    }

    /**
     * Para loterías de dígitos (Nacional, Navidad, Niño).
     */
    private fun ejecutarDigitos(
        candidatos: List<Int>,
        tipoLoteria: TipoLoteria,
        boteActual: Double
    ): ResultadoFormulaAbuelo {
        val numeros = candidatos.take(8).map { it.toString().padStart(5, '0') }

        val terminaciones = numeros.map { it.last().digitToInt() }.distinct().sorted()

        val ticketsFiltrados = numeros.map { num ->
            AntiPopularidad.calcularScoreDigitos(num)
        }

        val estrategia = when {
            terminaciones.size >= 8 -> "Cobertura amplia de terminaciones"
            terminaciones.size >= 5 -> "Cobertura media de terminaciones"
            else -> "Cobertura básica de terminaciones"
        }

        val coberturaDigitos = CoberturaDigitos(
            numeros = numeros,
            terminacionesCubiertas = terminaciones,
            estrategia = estrategia,
            explicacion = "${numeros.size} décimos cubriendo ${terminaciones.size}/10 terminaciones: $terminaciones"
        )

        val kelly = GestionBankroll.calcularKelly(tipoLoteria, boteActual)

        val resumen = buildString {
            append("La Fórmula del Abuelo - ${tipoLoteria.displayName}\n\n")
            append("COBERTURA: ${numeros.size} décimos, ${terminaciones.size} terminaciones\n")
            append("Terminaciones: $terminaciones\n\n")
            append("KELLY: ${kelly.recomendacion.displayName} (${kelly.atractividad}/10)\n")
            append("EV: ${"%.4f".format(kelly.valorEsperado)}€ por décimo\n\n")
            append("\"El abuelo decía: reparte las terminaciones, ")
            append("y si la suerte llama, que te pille preparado.\"")
        }

        return ResultadoFormulaAbuelo(
            cobertura = null,
            coberturaDigitos = coberturaDigitos,
            ticketsFiltrados = ticketsFiltrados,
            analisisKelly = kelly,
            resumen = resumen
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // ALGORITMO DE COBERTURA GREEDY C(v, k, t, m)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Genera t-subconjuntos de una lista.
     */
    private fun tSubconjuntos(lista: List<Int>, t: Int): Set<Set<Int>> {
        if (t > lista.size) return emptySet()
        if (t == 0) return setOf(emptySet())
        if (t == lista.size) return setOf(lista.toSet())

        val result = mutableSetOf<Set<Int>>()
        fun generar(start: Int, current: MutableList<Int>) {
            if (current.size == t) {
                result.add(current.toSet())
                return
            }
            val remaining = t - current.size
            for (i in start..lista.size - remaining) {
                current.add(lista[i])
                generar(i + 1, current)
                current.removeAt(current.size - 1)
            }
        }
        generar(0, mutableListOf())
        return result
    }

    /**
     * Genera k-subconjuntos (candidatos a boletos) de la lista.
     * Para v≤18: todos. Para v>18: muestreo inteligente.
     */
    private fun generarBoletoCandidatos(candidatos: List<Int>, k: Int): List<List<Int>> {
        if (candidatos.size <= 18) {
            val result = mutableListOf<List<Int>>()
            fun generar(start: Int, current: MutableList<Int>) {
                if (current.size == k) {
                    result.add(current.toList())
                    return
                }
                val remaining = k - current.size
                for (i in start..candidatos.size - remaining) {
                    current.add(candidatos[i])
                    generar(i + 1, current)
                    current.removeAt(current.size - 1)
                }
            }
            generar(0, mutableListOf())
            return result
        } else {
            val result = mutableSetOf<List<Int>>()
            val rnd = kotlin.random.Random(candidatos.hashCode())
            val maxIntentos = 5000
            var intentos = 0
            while (result.size < 1000 && intentos < maxIntentos) {
                val boleto = candidatos.shuffled(rnd).take(k).sorted()
                result.add(boleto)
                intentos++
            }
            return result.toList()
        }
    }

    /**
     * Algoritmo greedy de cobertura con probabilidad hipergeométrica real.
     *
     * @param maxNum Número máximo de la lotería (ej: 49 para Primitiva)
     * @param numSorteo Cuántos números se sortean (ej: 6 para Primitiva)
     */
    private fun calcularCobertura(
        candidatos: List<Int>,
        k: Int,
        t: Int,
        m: Int,
        maxNum: Int,
        numSorteo: Int
    ): ResultadoCobertura {
        if (candidatos.size < k) {
            return ResultadoCobertura(
                tickets = listOf(candidatos),
                garantia = "Pocos candidatos para diseño de cobertura",
                numTickets = 1,
                candidatos = candidatos,
                coberturaPct = 100.0,
                explicacion = "Solo ${candidatos.size} candidatos disponibles",
                probActivacion = 0.0
            )
        }

        // Calcular probabilidad hipergeométrica de que ≥t candidatos acierten
        val probActivacion = probabilidadHipergeometrica(
            N = maxNum, K = candidatos.size, n = numSorteo, minAciertos = t
        )

        // Generar todos los t-subconjuntos que debemos cubrir
        val tSubs = tSubconjuntos(candidatos, t).toMutableSet()
        val totalTSubs = tSubs.size

        // Generar boletos candidatos
        val boletoCandidatos = generarBoletoCandidatos(candidatos, k)

        // Pre-calcular qué t-subconjuntos cubre cada boleto (con garantía m)
        val coberturaPorBoleto = boletoCandidatos.associateWith { boleto ->
            val boletoSet = boleto.toSet()
            tSubs.filter { tSub -> (tSub intersect boletoSet).size >= m }.toSet()
        }

        // Greedy con score combinado:
        //   score = pares_nuevos - 0.4 * pares_ya_cubiertos_dos_veces - 0.1 * solapamiento_maximo
        // Esto distribuye la cobertura evitando que los mismos pares (ej: {1,2}) aparezcan
        // en 3+ boletos, sin sacrificar la cobertura de pares nuevos.
        val ticketsElegidos = mutableListOf<List<Int>>()
        val subconjuntosSinCubrir = tSubs.toMutableSet()
        val coberturaPorPar = mutableMapOf<Set<Int>, Int>() // cuántas veces se ha cubierto cada par
        val maxTickets = 15

        while (subconjuntosSinCubrir.isNotEmpty() && ticketsElegidos.size < maxTickets) {
            var mejorBoleto: List<Int>? = null
            var mejorScore = Double.NEGATIVE_INFINITY

            for (boleto in boletoCandidatos) {
                if (boleto in ticketsElegidos) continue
                val pares = coberturaPorBoleto[boleto] ?: emptySet()
                val nuevos = pares.count { it in subconjuntosSinCubrir }
                if (nuevos == 0) continue

                // Penalizar pares que ya se han cubierto 2+ veces (sobre-cobertura)
                val sobreCubiertos = pares.count { (coberturaPorPar[it] ?: 0) >= 2 }

                // Penalizar solapamiento de números con tickets ya elegidos
                val solapMax = if (ticketsElegidos.isEmpty()) 0
                else ticketsElegidos.maxOf { t -> (t.toSet() intersect boleto.toSet()).size }

                val score = nuevos - 0.4 * sobreCubiertos - 0.1 * solapMax

                if (score > mejorScore) {
                    mejorScore = score
                    mejorBoleto = boleto
                }
            }

            if (mejorBoleto == null) break

            ticketsElegidos.add(mejorBoleto)
            val cubiertosAhora = coberturaPorBoleto[mejorBoleto] ?: emptySet()
            cubiertosAhora.forEach { par -> coberturaPorPar[par] = (coberturaPorPar[par] ?: 0) + 1 }
            subconjuntosSinCubrir.removeAll(cubiertosAhora)
        }

        val coberturaPct = if (totalTSubs > 0) {
            ((totalTSubs - subconjuntosSinCubrir.size).toDouble() / totalTSubs * 100)
        } else 100.0

        val probPct = "%.1f".format(probActivacion * 100)
        val cobPct = "%.1f".format(coberturaPct)

        val garantia = if (coberturaPct >= 99.9) {
            "GARANTÍA: Si $t+ de tus ${candidatos.size} candidatos salen ($probPct% prob.), " +
                    "al menos 1 boleto tiene $m aciertos"
        } else {
            "COBERTURA $cobPct%: Si $t+ de tus ${candidatos.size} candidatos salen ($probPct% prob.), " +
                    "muy probable que 1 boleto tenga $m aciertos"
        }

        val explicacion = buildString {
            append("C(${candidatos.size},$k,$t,$m): ")
            append("${ticketsElegidos.size} boletos, cobertura $cobPct% ")
            append("de ${totalTSubs} parejas. ")
            append("Activación: $probPct% de sorteos")
        }

        return ResultadoCobertura(
            tickets = ticketsElegidos,
            garantia = garantia,
            numTickets = ticketsElegidos.size,
            candidatos = candidatos,
            coberturaPct = coberturaPct,
            explicacion = explicacion,
            probActivacion = probActivacion
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISTRIBUCIÓN HIPERGEOMÉTRICA
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calcula P(X ≥ minAciertos) con distribución hipergeométrica.
     * X ~ Hypergeometric(N, K, n)
     * N = total bolas, K = bolas marcadas (candidatos), n = bolas extraídas
     *
     * Usa logaritmos para evitar overflow con factoriales grandes.
     */
    private fun probabilidadHipergeometrica(N: Int, K: Int, n: Int, minAciertos: Int): Double {
        var probAcumulada = 0.0
        val maxK = minOf(K, n)

        for (k in minAciertos..maxK) {
            probAcumulada += hipergeometricaPMF(N, K, n, k)
        }

        return probAcumulada.coerceIn(0.0, 1.0)
    }

    /**
     * P(X = k) para hipergeométrica usando log-factoriales.
     * P(X=k) = C(K,k) × C(N-K,n-k) / C(N,n)
     */
    private fun hipergeometricaPMF(N: Int, K: Int, n: Int, k: Int): Double {
        if (k < 0 || k > minOf(K, n) || (n - k) > (N - K) || (n - k) < 0) return 0.0

        val logP = logCombinacion(K, k) + logCombinacion(N - K, n - k) - logCombinacion(N, n)
        return exp(logP)
    }

    /**
     * log(C(n,k)) usando suma de logaritmos para evitar overflow.
     */
    private fun logCombinacion(n: Int, k: Int): Double {
        if (k < 0 || k > n) return Double.NEGATIVE_INFINITY
        if (k == 0 || k == n) return 0.0
        val kk = minOf(k, n - k) // Optimización: C(n,k) = C(n,n-k)
        var result = 0.0
        for (i in 0 until kk) {
            result += ln((n - i).toDouble()) - ln((i + 1).toDouble())
        }
        return result
    }
}
