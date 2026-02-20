package com.loteria.probabilidad.domain.ml

import com.loteria.probabilidad.data.model.*

/**
 * Pilar 3: Criterio de Kelly y Gestión de Bankroll.
 *
 * Modelo de retorno correcto para loterías españolas:
 * - La Primitiva destina el 55% de la recaudación a premios
 * - Los premios variables (Especial, 1ª-4ª) son porcentajes del fondo
 * - Los premios fijos (5ª=8€, Reintegro=1€) salen del fondo primero
 * - EV por apuesta = tasaRetorno × precio + boteAcumulado/numApuestas
 *
 * Consecuencia matemática: sin bote acumulado, el retorno es EXACTAMENTE
 * el 55% de lo apostado. El bote acumulado es lo único que puede mejorar el EV.
 */
object GestionBankroll {

    /**
     * Estructura de premios REAL de cada lotería española.
     *
     * Primitiva (SELAE):
     * - Especial (6+R): 20% del fondo variable + bote acumulado
     * - 1ª (6): 40% del fondo variable
     * - 2ª (5+C): 6% del fondo variable
     * - 3ª (5): 13% del fondo variable
     * - 4ª (4): 21% del fondo variable
     * - 5ª (3): 8€ fijos
     * - Reintegro: 1€ (devuelve la apuesta)
     */
    fun obtenerEstructuraPremios(tipoLoteria: TipoLoteria): EstructuraPremios {
        return when (tipoLoteria) {
            TipoLoteria.PRIMITIVA -> EstructuraPremios(
                tipoLoteria = tipoLoteria,
                precioBoleto = 1.0,
                tasaRetorno = 0.55,
                categorias = listOf(
                    // Categoría Especial: 6 aciertos + reintegro
                    // P = P(6/49) × P(R) = 1/13.983.816 × 1/10
                    CategoriaPremio(
                        "Especial (6+R)", 6, true, 0.0,
                        1.0 / 13_983_816 * 1.0 / 10,
                        porcentajeFondo = 0.20
                    ),
                    // 1ª: 6 aciertos SIN reintegro
                    // P = 1/13.983.816 × 9/10
                    CategoriaPremio(
                        "1ª (6 aciertos)", 6, false, 0.0,
                        1.0 / 13_983_816 * 9.0 / 10,
                        porcentajeFondo = 0.40
                    ),
                    // 2ª: 5 + complementario
                    CategoriaPremio(
                        "2ª (5+C)", 5, true, 0.0,
                        6.0 / 13_983_816,
                        porcentajeFondo = 0.06
                    ),
                    // 3ª: 5 aciertos
                    CategoriaPremio(
                        "3ª (5 aciertos)", 5, false, 0.0,
                        252.0 / 13_983_816,
                        porcentajeFondo = 0.13
                    ),
                    // 4ª: 4 aciertos
                    CategoriaPremio(
                        "4ª (4 aciertos)", 4, false, 0.0,
                        13_545.0 / 13_983_816,
                        porcentajeFondo = 0.21
                    ),
                    // 5ª: 3 aciertos → 8€ fijos
                    CategoriaPremio(
                        "5ª (3 aciertos)", 3, false, 8.0,
                        246_820.0 / 13_983_816
                    ),
                    // Reintegro: devuelve la apuesta (1€)
                    // P = 1/10 (independiente del sorteo principal)
                    CategoriaPremio(
                        "Reintegro", 0, false, 1.0,
                        1.0 / 10
                    )
                )
            )

            TipoLoteria.BONOLOTO -> EstructuraPremios(
                tipoLoteria = tipoLoteria,
                precioBoleto = 0.50,
                tasaRetorno = 0.55,
                categorias = listOf(
                    CategoriaPremio(
                        "1ª (6 aciertos)", 6, false, 0.0,
                        1.0 / 13_983_816,
                        porcentajeFondo = 0.40
                    ),
                    CategoriaPremio(
                        "2ª (5+C)", 5, true, 0.0,
                        6.0 / 13_983_816,
                        porcentajeFondo = 0.10
                    ),
                    CategoriaPremio(
                        "3ª (5 aciertos)", 5, false, 0.0,
                        252.0 / 13_983_816,
                        porcentajeFondo = 0.16
                    ),
                    CategoriaPremio(
                        "4ª (4 aciertos)", 4, false, 0.0,
                        13_545.0 / 13_983_816,
                        porcentajeFondo = 0.34
                    ),
                    CategoriaPremio(
                        "5ª (3 aciertos)", 3, false, 4.0,
                        246_820.0 / 13_983_816
                    ),
                    CategoriaPremio(
                        "Reintegro", 0, false, 0.50,
                        1.0 / 10
                    )
                )
            )

            TipoLoteria.EUROMILLONES -> EstructuraPremios(
                tipoLoteria = tipoLoteria,
                precioBoleto = 2.50,
                tasaRetorno = 0.50,
                categorias = listOf(
                    CategoriaPremio(
                        "1ª (5+2★)", 5, true, 0.0,
                        1.0 / 139_838_160,
                        porcentajeFondo = 0.42
                    ),
                    CategoriaPremio(
                        "2ª (5+1★)", 5, false, 0.0,
                        10.0 / 139_838_160,
                        porcentajeFondo = 0.08
                    ),
                    CategoriaPremio(
                        "3ª (5)", 5, false, 0.0,
                        22.0 / 139_838_160,
                        porcentajeFondo = 0.04
                    ),
                    CategoriaPremio(
                        "4ª (4+2★)", 4, true, 0.0,
                        220.0 / 139_838_160,
                        porcentajeFondo = 0.02
                    ),
                    CategoriaPremio(
                        "5ª (4+1★)", 4, false, 0.0,
                        2_200.0 / 139_838_160,
                        porcentajeFondo = 0.03
                    ),
                    CategoriaPremio(
                        "6ª (3+2★)", 3, true, 0.0,
                        4_840.0 / 139_838_160,
                        porcentajeFondo = 0.02
                    ),
                    CategoriaPremio(
                        "7ª (4)", 4, false, 0.0,
                        4_840.0 / 139_838_160,
                        porcentajeFondo = 0.03
                    ),
                    CategoriaPremio(
                        "8ª (2+2★)", 2, true, 15.0,
                        48_400.0 / 139_838_160
                    ),
                    CategoriaPremio(
                        "9ª (3+1★)", 3, false, 12.0,
                        48_400.0 / 139_838_160
                    ),
                    CategoriaPremio(
                        "10ª (3)", 3, false, 10.0,
                        106_480.0 / 139_838_160
                    ),
                    CategoriaPremio(
                        "11ª (1+2★)", 1, true, 8.0,
                        290_400.0 / 139_838_160
                    ),
                    CategoriaPremio(
                        "12ª (2+1★)", 2, false, 4.0,
                        484_000.0 / 139_838_160
                    )
                )
            )

            TipoLoteria.GORDO_PRIMITIVA -> EstructuraPremios(
                tipoLoteria = tipoLoteria,
                precioBoleto = 1.50,
                tasaRetorno = 0.55,
                categorias = listOf(
                    CategoriaPremio(
                        "Especial (5+Clave)", 5, true, 0.0,
                        1.0 / 31_625_100,
                        porcentajeFondo = 0.35
                    ),
                    CategoriaPremio(
                        "1ª (5)", 5, false, 0.0,
                        9.0 / 31_625_100,
                        porcentajeFondo = 0.15
                    ),
                    CategoriaPremio(
                        "2ª (4+Clave)", 4, true, 0.0,
                        250.0 / 31_625_100,
                        porcentajeFondo = 0.10
                    ),
                    CategoriaPremio(
                        "3ª (4)", 4, false, 0.0,
                        2_250.0 / 31_625_100,
                        porcentajeFondo = 0.15
                    ),
                    CategoriaPremio(
                        "4ª (3+Clave)", 3, true, 0.0,
                        5_000.0 / 31_625_100,
                        porcentajeFondo = 0.10
                    ),
                    CategoriaPremio(
                        "5ª (3)", 3, false, 6.0,
                        45_000.0 / 31_625_100
                    ),
                    CategoriaPremio(
                        "6ª (2+Clave)", 2, true, 3.0,
                        45_000.0 / 31_625_100
                    )
                )
            )

            TipoLoteria.LOTERIA_NACIONAL -> EstructuraPremios(
                tipoLoteria = tipoLoteria,
                precioBoleto = 20.0,
                tasaRetorno = 0.70,
                categorias = listOf(
                    CategoriaPremio("1er Premio", 5, false, 300_000.0, 1.0 / 100_000),
                    CategoriaPremio("2do Premio", 5, false, 125_000.0, 1.0 / 100_000),
                    CategoriaPremio("3er Premio", 5, false, 50_000.0, 1.0 / 100_000),
                    CategoriaPremio("4tos Premios", 4, false, 5_000.0, 20.0 / 100_000),
                    CategoriaPremio("5tos Premios", 4, false, 1_000.0, 100.0 / 100_000),
                    CategoriaPremio("Aproximaciones", 4, false, 2_000.0, 4.0 / 100_000),
                    CategoriaPremio("Centenas", 3, false, 100.0, 198.0 / 100_000),
                    CategoriaPremio("Terminaciones", 2, false, 60.0, 999.0 / 100_000),
                    CategoriaPremio("Reintegro", 1, false, 20.0, 10_000.0 / 100_000)
                )
            )

            TipoLoteria.NAVIDAD -> EstructuraPremios(
                tipoLoteria = tipoLoteria,
                precioBoleto = 20.0,
                tasaRetorno = 0.70,
                categorias = listOf(
                    CategoriaPremio("El Gordo", 5, false, 400_000.0, 1.0 / 100_000),
                    CategoriaPremio("2do Premio", 5, false, 125_000.0, 1.0 / 100_000),
                    CategoriaPremio("3er Premio", 5, false, 50_000.0, 1.0 / 100_000),
                    CategoriaPremio("4tos Premios", 5, false, 20_000.0, 2.0 / 100_000),
                    CategoriaPremio("5tos Premios", 5, false, 6_000.0, 8.0 / 100_000),
                    CategoriaPremio("Pedrea", 5, false, 1_000.0, 1_794.0 / 100_000),
                    CategoriaPremio("Aproximaciones", 4, false, 2_000.0, 4.0 / 100_000),
                    CategoriaPremio("Centenas", 3, false, 100.0, 198.0 / 100_000),
                    CategoriaPremio("Terminaciones", 2, false, 60.0, 999.0 / 100_000),
                    CategoriaPremio("Reintegro", 1, false, 20.0, 10_000.0 / 100_000)
                )
            )

            TipoLoteria.NINO -> EstructuraPremios(
                tipoLoteria = tipoLoteria,
                precioBoleto = 20.0,
                tasaRetorno = 0.70,
                categorias = listOf(
                    CategoriaPremio("1er Premio", 5, false, 200_000.0, 1.0 / 100_000),
                    CategoriaPremio("2do Premio", 5, false, 75_000.0, 1.0 / 100_000),
                    CategoriaPremio("3er Premio", 5, false, 25_000.0, 1.0 / 100_000),
                    CategoriaPremio("Aproximaciones", 4, false, 1_200.0, 4.0 / 100_000),
                    CategoriaPremio("Centenas", 3, false, 100.0, 198.0 / 100_000),
                    CategoriaPremio("Terminaciones", 2, false, 60.0, 999.0 / 100_000),
                    CategoriaPremio("Reintegro", 1, false, 20.0, 10_000.0 / 100_000)
                )
            )
        }
    }

    /**
     * Calcula el EV y análisis de Kelly usando el modelo correcto de retorno.
     *
     * Para loterías con premios por porcentaje (Primitiva, Bonoloto, etc.):
     * - EV_base = tasaRetorno × precio (ej: 55% × 1€ = 0.55€)
     * - EV_con_bote = EV_base + boteAcumulado × P(ganar bote)
     *
     * Esto es matemáticamente exacto: el 55% de la recaudación vuelve como premios,
     * así que tu retorno esperado por euro es exactamente 0.55€ (sin bote).
     * El bote acumulado es lo ÚNICO que puede mejorar el EV.
     */
    fun calcularKelly(
        tipoLoteria: TipoLoteria,
        boteActual: Double = 0.0
    ): AnalisisKelly {
        val estructura = obtenerEstructuraPremios(tipoLoteria)
        val precio = estructura.precioBoleto
        val categorias = estructura.categorias

        // Separar categorías fijas y por porcentaje
        val catFijas = categorias.filter { it.porcentajeFondo == 0.0 }
        val catPorcentaje = categorias.filter { it.porcentajeFondo > 0.0 }

        // EV de premios fijos
        val evFijos = catFijas.sumOf { it.premioMedio * it.probabilidad }

        // EV de premios por porcentaje
        // Fondo variable por apuesta = tasaRetorno × precio - evFijos
        // (los fijos salen del fondo primero, el resto se reparte por porcentajes)
        val fondoVariablePorApuesta = if (catPorcentaje.isNotEmpty()) {
            (estructura.tasaRetorno * precio - evFijos).coerceAtLeast(0.0)
        } else 0.0

        val evPorcentaje = catPorcentaje.sumOf { it.porcentajeFondo * fondoVariablePorApuesta }

        // EV base (sin bote acumulado)
        val evBase = evFijos + evPorcentaje

        // EV del bote acumulado (se suma a la categoría especial/primera)
        val evBote = if (boteActual > 0 && catPorcentaje.isNotEmpty()) {
            // El bote va al ganador de la categoría más alta (Especial o 1ª)
            val catBote = catPorcentaje.first()
            boteActual * catBote.probabilidad
        } else 0.0

        val evTotal = evBase + evBote
        val evNeto = evTotal - precio
        val evPorEuro = evNeto / precio
        val retornoPct = (evTotal / precio) * 100

        // Probabilidad de ganar algo
        val pGanar = categorias.sumOf { it.probabilidad }.coerceAtMost(1.0)

        // Kelly generalizado: f* = (μ-1)/σ²
        val mediaRetorno = evTotal / precio
        val varianzaRetorno = calcularVarianza(categorias, fondoVariablePorApuesta, precio, pGanar, boteActual)
        val kelly = if (varianzaRetorno > 0 && mediaRetorno > 1.0) {
            ((mediaRetorno - 1.0) / varianzaRetorno).coerceIn(0.0, 1.0)
        } else 0.0

        // Clasificación
        val (recomendacion, atractividad) = when {
            evPorEuro > 0 -> RecomendacionJuego.MUY_FAVORABLE to 10
            evPorEuro > -0.3 -> RecomendacionJuego.FAVORABLE to 7
            evPorEuro > -0.5 -> RecomendacionJuego.MODERADO to 5
            evPorEuro > -0.7 -> RecomendacionJuego.MINIMO to 3
            else -> RecomendacionJuego.NO_JUGAR to 1
        }

        // Desglose detallado
        val explicacion = buildString {
            append("DESGLOSE DE RETORNO (apuesta ${"%.2f".format(precio)}€):\n")

            // Premios por porcentaje
            for (cat in catPorcentaje) {
                val evCat = cat.porcentajeFondo * fondoVariablePorApuesta
                val pctRetorno = if (evTotal > 0) (evCat / evTotal * 100) else 0.0
                val premioEstimado = if (cat.probabilidad > 0) evCat / cat.probabilidad else 0.0
                append("  ${cat.nombre}: ${"%.4f".format(evCat)}€")
                append(" (${"%.1f".format(pctRetorno)}%)")
                append(" → ~${"%.0f".format(premioEstimado)}€/ganador\n")
            }

            // Premios fijos
            for (cat in catFijas) {
                val evCat = cat.premioMedio * cat.probabilidad
                val pctRetorno = if (evTotal > 0) (evCat / evTotal * 100) else 0.0
                val prob1en = if (cat.probabilidad > 0) (1.0 / cat.probabilidad).toLong() else 0L
                append("  ${cat.nombre}: ${"%.4f".format(evCat)}€")
                append(" (${"%.1f".format(pctRetorno)}%)")
                append(" → ${"%.2f".format(cat.premioMedio)}€, 1/$prob1en\n")
            }

            append("\nRetorno base: ${"%.4f".format(evBase)}€ → ${"%.1f".format(retornoPct)}% de ${"%.2f".format(precio)}€\n")

            if (boteActual > 0) {
                append("Bote acumulado: ${"%.0f".format(boteActual)}€ → +${"%.6f".format(evBote)}€ al EV\n")
                append("Retorno con bote: ${"%.1f".format((evTotal / precio) * 100)}%\n")
            }

            append("EV neto: ${"%.4f".format(evNeto)}€ (${"%.1f".format(evPorEuro * 100)}%)\n")
            append("P(ganar algo): ${"%.2f".format(pGanar * 100)}%\n")

            if (kelly > 0) {
                append("Kelly: ${"%.6f".format(kelly * 100)}% del bankroll")
            } else {
                append("Kelly: 0% (EV negativo → apostar 0 es óptimo)")
            }
        }

        return AnalisisKelly(
            tipoLoteria = tipoLoteria,
            boteActual = boteActual,
            valorEsperado = evNeto,
            fraccionKelly = kelly,
            recomendacion = recomendacion,
            atractividad = atractividad,
            explicacion = explicacion
        )
    }

    /**
     * Calcula varianza del retorno para Kelly.
     */
    private fun calcularVarianza(
        categorias: List<CategoriaPremio>,
        fondoVariable: Double,
        precio: Double,
        pGanar: Double,
        boteActual: Double
    ): Double {
        val mediaRetorno = categorias.sumOf { cat ->
            val premio = if (cat.porcentajeFondo > 0.0) {
                val base = cat.porcentajeFondo * fondoVariable
                if (cat == categorias.first { it.porcentajeFondo > 0.0 } && boteActual > 0) {
                    base + boteActual * cat.probabilidad
                } else base
            } else {
                cat.premioMedio * cat.probabilidad
            }
            premio
        } / precio

        return categorias.sumOf { cat ->
            val retorno = if (cat.porcentajeFondo > 0.0) {
                val premioGanador = if (cat.probabilidad > 0) {
                    cat.porcentajeFondo * fondoVariable / cat.probabilidad
                } else 0.0
                premioGanador / precio
            } else {
                cat.premioMedio / precio
            }
            cat.probabilidad * (retorno - mediaRetorno) * (retorno - mediaRetorno)
        } + (1.0 - pGanar) * (0.0 - mediaRetorno) * (0.0 - mediaRetorno)
    }
}
