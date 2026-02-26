package com.loteria.probabilidad.domain.calculator

import android.content.Context
import com.loteria.probabilidad.data.model.*
import com.loteria.probabilidad.domain.ml.MotorInteligencia
import com.loteria.probabilidad.domain.ml.ResumenIA
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Calculador de probabilidades con múltiples métodos de análisis.
 * 
 * Métodos implementados (8):
 * - METODO_ABUELO: Chi², Fourier, Bayes, Markov, Entropía
 * - ENSEMBLE_VOTING: 8 estrategias votan por consenso
 * - ALTA_CONFIANZA: 7 señales, solo números con alto consenso
 * - IA_GENETICA: Algoritmo genético con APRENDIZAJE PERSISTENTE
 * - RACHAS_MIX: Calientes + fríos + normales
 * - FRECUENCIAS: Basado en histórico de apariciones
 * - NUMEROS_FRIOS: Los menos frecuentes (teoría del equilibrio)
 * - ALEATORIO_PURO: Selección completamente aleatoria (baseline)
 */
class CalculadorProbabilidad(private val context: Context? = null) {

    // Motor de IA con aprendizaje persistente
    private val motorIA = MotorInteligencia(context)

    /** Random determinista: mismos datos → mismos resultados */
    private var rnd: Random = Random(0)

    private fun inicializarSemilla(tipoLoteria: String, historico: List<*>) {
        val pesosHash = motorIA.getPesosCaracteristicas().entries.sumOf {
            (it.key.hashCode().toLong() * 31 + (it.value * 1000000).toLong())
        }
        val entrenamientos = motorIA.getTotalEntrenamientos(tipoLoteria).toLong()
        // Incluir pesos del Abuelo para que cambien las combinaciones tras entrenamiento abuelo
        val pesosAbueloHash = motorIA.getPesosAbuelo(tipoLoteria).entries.sumOf {
            (it.key.hashCode().toLong() * 31 + (it.value * 1000000).toLong())
        }
        val entrenamientosAbuelo = motorIA.getEntrenamientosAbuelo(tipoLoteria).toLong()
        val hash = tipoLoteria.hashCode().toLong() * 31 + historico.size.toLong() * 17 +
            (historico.lastOrNull()?.hashCode()?.toLong() ?: 0L) +
            pesosHash + entrenamientos * 7 +
            pesosAbueloHash * 13 + entrenamientosAbuelo * 11
        rnd = Random(hash)
    }

    private fun <T> List<T>.randomDet(): T = this.random(rnd)
    private fun IntRange.randomDet(): Int = this.random(rnd)
    
    /**
     * Hace que la IA aprenda de los resultados del backtesting.
     */
    fun aprenderDeBacktest(
        resultados: List<ResultadoBacktest>,
        historico: List<ResultadoPrimitiva>,
        tipoLoteria: String,
        sorteosProbados: Int
    ) {
        motorIA.aprenderDeBacktest(resultados, historico, tipoLoteria, sorteosProbados)
    }
    
    /**
     * Obtiene el resumen del estado de la IA.
     */
    fun obtenerResumenIA(): ResumenIA? = motorIA.obtenerResumenIA()

    /**
     * Analiza el histórico según el método seleccionado.
     */
    fun analizar(
        tipoLoteria: TipoLoteria,
        historico: List<ResultadoSorteo>,
        metodo: MetodoCalculo,
        numCombinaciones: Int = 5
    ): AnalisisProbabilidad {
        // Forzar recarga de pesos desde SharedPreferences (pueden haber cambiado por entrenamiento)
        motorIA.recargarMemoria(tipoLoteria.name)
        inicializarSemilla(tipoLoteria.name, historico)
        return when (tipoLoteria) {
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> {
                @Suppress("UNCHECKED_CAST")
                analizarPrimitivaBonoloto(
                    historico as List<ResultadoPrimitiva>,
                    tipoLoteria,
                    metodo,
                    numCombinaciones
                )
            }
            TipoLoteria.EUROMILLONES -> {
                @Suppress("UNCHECKED_CAST")
                analizarEuromillones(
                    historico as List<ResultadoEuromillones>,
                    metodo,
                    numCombinaciones
                )
            }
            TipoLoteria.GORDO_PRIMITIVA -> {
                @Suppress("UNCHECKED_CAST")
                analizarGordoPrimitiva(
                    historico as List<ResultadoGordoPrimitiva>,
                    metodo,
                    numCombinaciones
                )
            }
            TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NINO -> {
                @Suppress("UNCHECKED_CAST")
                analizarNacional(
                    historico as List<ResultadoNacional>,
                    tipoLoteria,
                    metodo,
                    numCombinaciones
                )
            }
            TipoLoteria.NAVIDAD -> {
                @Suppress("UNCHECKED_CAST")
                analizarNavidad(
                    historico as List<ResultadoNavidad>,
                    metodo,
                    numCombinaciones
                )
            }
        }
    }

    // ==================== PRIMITIVA / BONOLOTO ====================

    private fun analizarPrimitivaBonoloto(
        historico: List<ResultadoPrimitiva>,
        tipoLoteria: TipoLoteria,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(tipoLoteria, metodo)
        }

        val maxNumero = 49
        val cantidadNumeros = 6
        
        // Calcular frecuencias
        val frecuenciasNumeros = contarFrecuencias(historico.flatMap { it.numeros }, 1..maxNumero)
        val frecuenciasReintegros = contarFrecuencias(historico.map { it.reintegro }, 0..9)

        // MEJORA 10: Predicción inteligente de reintegros
        val reintegrosPredichos = if (historico.isNotEmpty()) {
            motorIA.predecirReintegros(historico, numCombinaciones).map { it.numero }
        } else {
            (0..9).shuffled(rnd).take(numCombinaciones)
        }
        
        // Generar combinaciones según el método
        val combinacionesBase = when (metodo) {
            MetodoCalculo.ENSEMBLE_VOTING -> {
                // MEJORA 9: Sistema de votación con 8 estrategias
                val resultado = motorIA.ejecutarEnsembleVoting(historico, maxNumero, cantidadNumeros, tipoLoteria.name)
                val combinaciones = mutableListOf(motorIA.generarCombinacionEnsemble(historico, maxNumero, cantidadNumeros, tipoLoteria.name))
                // Añadir alternativas si hay
                resultado.combinacionesAlternativas.take(numCombinaciones - 1).forEach { alt ->
                    combinaciones.add(CombinacionSugerida(
                        numeros = alt,
                        probabilidadRelativa = resultado.confianzaGlobal * 90,
                        explicacion = "🗳️ Alternativa Ensemble"
                    ))
                }
                // Completar con genético si faltan
                while (combinaciones.size < numCombinaciones) {
                    combinaciones.addAll(motorIA.generarCombinacionesInteligentes(
                        historico, maxNumero, cantidadNumeros, 1, tipoLoteria.name
                    ))
                }
                combinaciones.take(numCombinaciones)
            }
            MetodoCalculo.ALTA_CONFIANZA -> {
                // MEJORA 7: Sistema de 7 señales coherentes de alta confianza
                val prediccion = motorIA.generarPrediccionAltaConfianza(historico, maxNumero, cantidadNumeros, tipoLoteria.name)
                val combinaciones = mutableListOf(CombinacionSugerida(
                    numeros = prediccion.combinacionPrincipal,
                    probabilidadRelativa = prediccion.confianzaGlobal * 100,
                    explicacion = "🎯 Alta Confianza | ${prediccion.numerosConAltoConsenso.size} números con ≥4/7 señales"
                ))
                prediccion.combinacionesAlternativas.take(numCombinaciones - 1).forEach { alt ->
                    combinaciones.add(CombinacionSugerida(
                        numeros = alt,
                        probabilidadRelativa = prediccion.confianzaGlobal * 85,
                        explicacion = "🎯 Alternativa Alta Confianza"
                    ))
                }
                while (combinaciones.size < numCombinaciones) {
                    combinaciones.addAll(motorIA.generarCombinacionesInteligentes(
                        historico, maxNumero, cantidadNumeros, 1, tipoLoteria.name
                    ))
                }
                combinaciones.take(numCombinaciones)
            }
            MetodoCalculo.RACHAS_MIX -> {
                // MEJORA 8: Mezcla de números calientes y fríos por rachas
                val combinaciones = (0 until numCombinaciones).map {
                    motorIA.generarCombinacionMixta(historico, maxNumero, cantidadNumeros, tipoLoteria.name)
                }
                combinaciones
            }
            MetodoCalculo.IA_GENETICA -> motorIA.generarCombinacionesInteligentes(
                historico, maxNumero, cantidadNumeros, numCombinaciones, tipoLoteria.name
            )
            MetodoCalculo.FRECUENCIAS -> generarPorFrecuencias(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.NUMEROS_FRIOS -> generarNumerosFrios(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.ALEATORIO_PURO -> generarAleatorio(maxNumero, cantidadNumeros, numCombinaciones)
            MetodoCalculo.METODO_ABUELO -> {
                // 🔮 MÉTODO DEL ABUELO: Sistema de convergencias
                val resultado = motorIA.ejecutarMetodoAbuelo(historico, maxNumero, cantidadNumeros, tipoLoteria.name)
                val combinaciones = mutableListOf(CombinacionSugerida(
                    numeros = resultado.combinacionPrincipal,
                    probabilidadRelativa = resultado.confianza * 100,
                    explicacion = "🔮 ${resultado.explicacion}"
                ))
                // Añadir alternativas
                resultado.combinacionesAlternativas.forEachIndexed { idx, alt ->
                    combinaciones.add(CombinacionSugerida(
                        numeros = alt,
                        probabilidadRelativa = resultado.confianza * 90 - (idx * 5),
                        explicacion = "🔮 Alternativa ${idx + 1} | ${resultado.sabiduria}"
                    ))
                }
                // Completar si faltan
                while (combinaciones.size < numCombinaciones) {
                    combinaciones.add(motorIA.generarCombinacionMetodoAbuelo(
                        historico, maxNumero, cantidadNumeros, tipoLoteria.name
                    ))
                }
                combinaciones.take(numCombinaciones)
            }
        }

        // MEJORA 10: Añadir reintegro inteligente DIFERENTE a cada combinación
        val combinaciones = combinacionesBase.mapIndexed { index, combinacion ->
            val reintegro = reintegrosPredichos.getOrElse(index % reintegrosPredichos.size) {
                (0..9).randomDet()
            }
            combinacion.copy(
                complementarios = listOf(reintegro),
                explicacion = "${combinacion.explicacion} | 🎲R:$reintegro"
            )
        }

        // Calcular probabilidad teórica (Laplace)
        val probabilidadTeorica = calcularProbabilidadLaplace(maxNumero, cantidadNumeros)
        
        // Obtener fecha del último sorteo
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha

        return AnalisisProbabilidad(
            tipoLoteria = tipoLoteria,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasReintegros, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo
        )
    }

    // ==================== EUROMILLONES ====================

    private fun analizarEuromillones(
        historico: List<ResultadoEuromillones>,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(TipoLoteria.EUROMILLONES, metodo)
        }

        val maxNumero = 50
        val cantidadNumeros = 5
        val maxEstrella = 12
        
        val frecuenciasNumeros = contarFrecuencias(historico.flatMap { it.numeros }, 1..maxNumero)
        val frecuenciasEstrellas = contarFrecuencias(historico.flatMap { it.estrellas }, 1..maxEstrella)

        // MEJORA 10: Predicción inteligente de estrellas
        val estrellasPredichas = if (historico.isNotEmpty()) {
            motorIA.predecirEstrellas(historico, numCombinaciones * 2).map { it.numero }
        } else {
            (1..12).shuffled(rnd).take(numCombinaciones * 2)
        }

        // Convertir a ResultadoPrimitiva para usar las funciones del motor
        val historicoConvertido = historico.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }

        val combinacionesBase = when (metodo) {
            MetodoCalculo.ENSEMBLE_VOTING -> {
                val resultado = motorIA.ejecutarEnsembleVoting(historicoConvertido, maxNumero, cantidadNumeros, "EURO")
                val combinaciones = mutableListOf(motorIA.generarCombinacionEnsemble(historicoConvertido, maxNumero, cantidadNumeros, "EURO"))
                resultado.combinacionesAlternativas.take(numCombinaciones - 1).forEach { alt ->
                    combinaciones.add(CombinacionSugerida(numeros = alt, probabilidadRelativa = resultado.confianzaGlobal * 90, explicacion = "🗳️ Alternativa Ensemble"))
                }
                while (combinaciones.size < numCombinaciones) { combinaciones.addAll(motorIA.generarCombinacionesInteligenteEuro(historico, 1)) }
                combinaciones.take(numCombinaciones)
            }
            MetodoCalculo.ALTA_CONFIANZA -> {
                val prediccion = motorIA.generarPrediccionAltaConfianza(historicoConvertido, maxNumero, cantidadNumeros, "EURO")
                val combinaciones = mutableListOf(CombinacionSugerida(numeros = prediccion.combinacionPrincipal, probabilidadRelativa = prediccion.confianzaGlobal * 100, explicacion = "🎯 Alta Confianza"))
                prediccion.combinacionesAlternativas.take(numCombinaciones - 1).forEach { alt -> combinaciones.add(CombinacionSugerida(numeros = alt, probabilidadRelativa = prediccion.confianzaGlobal * 85, explicacion = "🎯 Alternativa")) }
                while (combinaciones.size < numCombinaciones) { combinaciones.addAll(motorIA.generarCombinacionesInteligenteEuro(historico, 1)) }
                combinaciones.take(numCombinaciones)
            }
            MetodoCalculo.RACHAS_MIX -> {
                (0 until numCombinaciones).map { motorIA.generarCombinacionMixta(historicoConvertido, maxNumero, cantidadNumeros, "EURO") }
            }
            MetodoCalculo.IA_GENETICA -> motorIA.generarCombinacionesInteligenteEuro(historico, numCombinaciones)
            MetodoCalculo.FRECUENCIAS -> generarPorFrecuencias(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.NUMEROS_FRIOS -> generarNumerosFrios(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.ALEATORIO_PURO -> generarAleatorio(maxNumero, cantidadNumeros, numCombinaciones)
            MetodoCalculo.METODO_ABUELO -> {
                // 🔮 MÉTODO DEL ABUELO: Sistema de convergencias
                val resultado = motorIA.ejecutarMetodoAbuelo(historicoConvertido, maxNumero, cantidadNumeros, "EURO")
                val combinaciones = mutableListOf(CombinacionSugerida(
                    numeros = resultado.combinacionPrincipal,
                    probabilidadRelativa = resultado.confianza * 100,
                    explicacion = "🔮 ${resultado.explicacion}"
                ))
                resultado.combinacionesAlternativas.forEachIndexed { idx, alt ->
                    combinaciones.add(CombinacionSugerida(
                        numeros = alt,
                        probabilidadRelativa = resultado.confianza * 90 - (idx * 5),
                        explicacion = "🔮 Alternativa ${idx + 1} | ${resultado.sabiduria}"
                    ))
                }
                while (combinaciones.size < numCombinaciones) {
                    combinaciones.add(motorIA.generarCombinacionMetodoAbuelo(historicoConvertido, maxNumero, cantidadNumeros, "EURO"))
                }
                combinaciones.take(numCombinaciones)
            }
        }

        // MEJORA 10: Añadir estrellas inteligentes DIFERENTES a cada combinación
        val combinaciones = combinacionesBase.mapIndexed { index, combinacion ->
            // Usar estrellas predichas con rotación
            val offset = index * 2
            val estrella1 = estrellasPredichas.getOrElse(offset % estrellasPredichas.size) { (1..12).randomDet() }
            val estrella2 = estrellasPredichas.getOrElse((offset + 1) % estrellasPredichas.size) { (1..12).filter { it != estrella1 }.randomDet() }
            val estrellas = listOf(estrella1, estrella2).distinct().sorted()

            // Si son iguales, tomar la siguiente disponible
            val estrellasFinales = if (estrellas.size == 1) {
                val siguiente = estrellasPredichas.getOrElse((offset + 2) % estrellasPredichas.size) { (1..12).filter { it != estrella1 }.randomDet() }
                listOf(estrella1, siguiente).sorted()
            } else {
                estrellas
            }

            combinacion.copy(
                complementarios = estrellasFinales,
                explicacion = "${combinacion.explicacion} | ⭐${estrellasFinales.joinToString(",")}"
            )
        }

        // Probabilidad Euromillones: C(50,5) * C(12,2) = 139,838,160
        val probabilidadTeorica = "1 entre 139.838.160 (≈0.0000000715%)"
        
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha
        
        // IMPORTANTE: Para estrellas, dividir por total de estrellas (2 por sorteo), no por sorteos
        val totalEstrellas = historico.size * 2

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.EUROMILLONES,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasEstrellas, 5, totalEstrellas),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo
        )
    }

    // ==================== EL GORDO DE LA PRIMITIVA ====================

    private fun analizarGordoPrimitiva(
        historico: List<ResultadoGordoPrimitiva>,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(TipoLoteria.GORDO_PRIMITIVA, metodo)
        }

        val maxNumero = 54
        val cantidadNumeros = 5
        
        val frecuenciasNumeros = contarFrecuencias(historico.flatMap { it.numeros }, 1..maxNumero)
        val frecuenciasClave = contarFrecuencias(historico.map { it.numeroClave }, 0..9)

        // MEJORA 10: Predicción inteligente de número clave
        val clavesPredichas = if (historico.isNotEmpty()) {
            motorIA.predecirNumeroClave(historico, numCombinaciones).map { it.numero }
        } else {
            (0..9).shuffled(rnd).take(numCombinaciones)
        }

        // Convertir a ResultadoPrimitiva para usar las funciones del motor
        val historicoConvertido = historico.map { ResultadoPrimitiva(it.fecha, it.numeros, 0, 0) }

        val combinacionesBase = when (metodo) {
            MetodoCalculo.ENSEMBLE_VOTING -> {
                val resultado = motorIA.ejecutarEnsembleVoting(historicoConvertido, maxNumero, cantidadNumeros, "GORDO")
                val combinaciones = mutableListOf(motorIA.generarCombinacionEnsemble(historicoConvertido, maxNumero, cantidadNumeros, "GORDO"))
                resultado.combinacionesAlternativas.take(numCombinaciones - 1).forEach { alt ->
                    combinaciones.add(CombinacionSugerida(numeros = alt, probabilidadRelativa = resultado.confianzaGlobal * 90, explicacion = "🗳️ Alternativa Ensemble"))
                }
                while (combinaciones.size < numCombinaciones) { combinaciones.addAll(motorIA.generarCombinacionesInteligenteGordo(historico, 1)) }
                combinaciones.take(numCombinaciones)
            }
            MetodoCalculo.ALTA_CONFIANZA -> {
                val prediccion = motorIA.generarPrediccionAltaConfianza(historicoConvertido, maxNumero, cantidadNumeros, "GORDO")
                val combinaciones = mutableListOf(CombinacionSugerida(numeros = prediccion.combinacionPrincipal, probabilidadRelativa = prediccion.confianzaGlobal * 100, explicacion = "🎯 Alta Confianza"))
                prediccion.combinacionesAlternativas.take(numCombinaciones - 1).forEach { alt -> combinaciones.add(CombinacionSugerida(numeros = alt, probabilidadRelativa = prediccion.confianzaGlobal * 85, explicacion = "🎯 Alternativa")) }
                while (combinaciones.size < numCombinaciones) { combinaciones.addAll(motorIA.generarCombinacionesInteligenteGordo(historico, 1)) }
                combinaciones.take(numCombinaciones)
            }
            MetodoCalculo.RACHAS_MIX -> {
                (0 until numCombinaciones).map { motorIA.generarCombinacionMixta(historicoConvertido, maxNumero, cantidadNumeros, "GORDO") }
            }
            MetodoCalculo.IA_GENETICA -> motorIA.generarCombinacionesInteligenteGordo(historico, numCombinaciones)
            MetodoCalculo.FRECUENCIAS -> generarPorFrecuencias(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.NUMEROS_FRIOS -> generarNumerosFrios(frecuenciasNumeros, cantidadNumeros, numCombinaciones, historico.size)
            MetodoCalculo.ALEATORIO_PURO -> generarAleatorio(maxNumero, cantidadNumeros, numCombinaciones)
            MetodoCalculo.METODO_ABUELO -> {
                // 🔮 MÉTODO DEL ABUELO: Sistema de convergencias
                val resultado = motorIA.ejecutarMetodoAbuelo(historicoConvertido, maxNumero, cantidadNumeros, "GORDO")
                val combinaciones = mutableListOf(CombinacionSugerida(
                    numeros = resultado.combinacionPrincipal,
                    probabilidadRelativa = resultado.confianza * 100,
                    explicacion = "🔮 ${resultado.explicacion}"
                ))
                resultado.combinacionesAlternativas.forEachIndexed { idx, alt ->
                    combinaciones.add(CombinacionSugerida(
                        numeros = alt,
                        probabilidadRelativa = resultado.confianza * 90 - (idx * 5),
                        explicacion = "🔮 Alternativa ${idx + 1} | ${resultado.sabiduria}"
                    ))
                }
                while (combinaciones.size < numCombinaciones) {
                    combinaciones.add(motorIA.generarCombinacionMetodoAbuelo(historicoConvertido, maxNumero, cantidadNumeros, "GORDO"))
                }
                combinaciones.take(numCombinaciones)
            }
        }

        // MEJORA 10: Añadir número clave inteligente DIFERENTE a cada combinación
        val combinaciones = combinacionesBase.mapIndexed { index, combinacion ->
            val numeroClave = clavesPredichas.getOrElse(index % clavesPredichas.size) {
                (0..9).randomDet()
            }
            combinacion.copy(
                complementarios = listOf(numeroClave),
                explicacion = "${combinacion.explicacion} | 🔑K:$numeroClave"
            )
        }

        // Probabilidad El Gordo: C(54,5) * 10 = 31,625,100
        val probabilidadTeorica = "1 entre 31.625.100 (≈0.00000316%)"
        
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.GORDO_PRIMITIVA,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasNumeros, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasClave, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo
        )
    }

    // ==================== LOTERÍA NACIONAL / EL NIÑO ====================

    private fun analizarNacional(
        historico: List<ResultadoNacional>,
        tipoLoteria: TipoLoteria,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(tipoLoteria, metodo)
        }

        // ==================== ANÁLISIS DE FRECUENCIAS POR POSICIÓN ====================
        // Cada posición del número de 5 dígitos se analiza independientemente (0-9)
        // Posición 0: Decenas de millar, Posición 1: Millares, Posición 2: Centenas, 
        // Posición 3: Decenas, Posición 4: Unidades
        
        val frecuenciasPorPosicion = Array(5) { mutableMapOf<Int, Int>() }

        // Inicializar con ceros
        for (pos in 0..4) {
            for (digito in 0..9) {
                frecuenciasPorPosicion[pos][digito] = 0
            }
        }

        // Contar frecuencias de cada dígito en cada posición
        historico.forEach { resultado ->
            val numero = resultado.primerPremio.padStart(5, '0')
            numero.forEachIndexed { posicion, char ->
                val digito = char.digitToIntOrNull() ?: return@forEachIndexed
                frecuenciasPorPosicion[posicion][digito] =
                    (frecuenciasPorPosicion[posicion][digito] ?: 0) + 1
            }
        }

        // Análisis de terminaciones (últimas 2 cifras) para compatibilidad
        val terminaciones = historico.mapNotNull { it.primerPremio.takeLast(2).toIntOrNull() }
        val frecuenciasTerminaciones = contarFrecuencias(terminaciones, 0..99)
        val frecuenciasReintegros = contarFrecuencias(historico.flatMap { it.reintegros }, 0..9)

        val combinaciones = when (metodo) {
            MetodoCalculo.ALEATORIO_PURO -> {
                (0 until numCombinaciones).map {
                    val numero = (0..99999).randomDet()
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = 0.001,
                        explicacion = "🎲 Aleatorio: ${numero.toString().padStart(5, '0')}"
                    )
                }
            }
            MetodoCalculo.FRECUENCIAS -> {
                // Usar el dígito MÁS FRECUENTE en cada posición
                generarNumerosOptimos(
                    frecuenciasPorPosicion,
                    numCombinaciones,
                    historico.size,
                    "📊 Frecuencias",
                    seleccionarMasFrecuentes = true
                )
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                // Usar dígitos MENOS frecuentes (teoría del equilibrio)
                generarNumerosOptimos(
                    frecuenciasPorPosicion,
                    numCombinaciones,
                    historico.size,
                    "❄️ Fríos",
                    seleccionarMasFrecuentes = false
                )
            }
            else -> {
                // Por defecto: usar terminaciones + dígitos frecuentes
                val terminacionesOrdenadas = frecuenciasTerminaciones.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    
                (0 until numCombinaciones).map { index ->
                    val terminacion = terminacionesOrdenadas.getOrElse(index) { (0..99).randomDet() }
                    val frecuencia = frecuenciasTerminaciones[terminacion] ?: 0
                    
                    // Generar prefijo usando dígitos frecuentes
                    val dig0 = obtenerDigitoTop(frecuenciasPorPosicion[0], index)
                    val dig1 = obtenerDigitoTop(frecuenciasPorPosicion[1], index)
                    val dig2 = obtenerDigitoTop(frecuenciasPorPosicion[2], index)
                    val prefijo = dig0 * 100 + dig1 * 10 + dig2
                    
                    val numero = prefijo * 100 + terminacion
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = if (historico.isNotEmpty()) {
                            (frecuencia.toDouble() / historico.size * 100).roundTo(2)
                        } else 0.001,
                        explicacion = "🎯 Número: ${numero.toString().padStart(5, '0')} | Term: ${terminacion.toString().padStart(2, '0')} (${frecuencia}x)"
                    )
                }
            }
        }

        val probabilidadTeorica = "1 entre 100.000 (0.001%)"
        
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha

        return AnalisisProbabilidad(
            tipoLoteria = tipoLoteria,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasReintegros, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo,
            analisisPorPosicion = crearAnalisisPorPosicion(frecuenciasPorPosicion, historico.size)
        )
    }
    
    // ==================== FUNCIONES AUXILIARES PARA ANÁLISIS DE DÍGITOS ====================
    
    /**
     * Genera números usando los dígitos más/menos frecuentes por posición.
     */
    private fun generarNumerosOptimos(
        frecuenciasPorPosicion: Array<MutableMap<Int, Int>>,
        numCombinaciones: Int,
        totalSorteos: Int,
        prefijo: String,
        seleccionarMasFrecuentes: Boolean
    ): List<CombinacionSugerida> {
        val combinaciones = mutableListOf<CombinacionSugerida>()
        val numerosGenerados = mutableSetOf<Int>()
        
        repeat(numCombinaciones) { index ->
            var numero: Int
            var intentos = 0
            
            do {
                val digitos = (0..4).map { pos ->
                    obtenerDigitoRanked(frecuenciasPorPosicion[pos], index + intentos, seleccionarMasFrecuentes)
                }
                numero = digitos[0] * 10000 + digitos[1] * 1000 + digitos[2] * 100 + digitos[3] * 10 + digitos[4]
                intentos++
            } while (numero in numerosGenerados && intentos < 20)
            
            numerosGenerados.add(numero)
            
            // Calcular puntuación basada en frecuencias
            val puntuacion = calcularPuntuacionNumero(numero, frecuenciasPorPosicion, totalSorteos)
            
            combinaciones.add(CombinacionSugerida(
                numeros = listOf(numero),
                probabilidadRelativa = puntuacion,
                explicacion = "$prefijo: ${numero.toString().padStart(5, '0')} | Score: ${puntuacion.roundTo(1)}%"
            ))
        }
        
        return combinaciones
    }
    
    /**
     * Obtiene el dígito en el ranking especificado.
     */
    private fun obtenerDigitoRanked(
        frecuencias: Map<Int, Int>, 
        rank: Int, 
        masFrecuente: Boolean
    ): Int {
        val ordenados = if (masFrecuente) {
            frecuencias.entries.sortedByDescending { it.value }
        } else {
            frecuencias.entries.sortedBy { it.value }
        }
        return ordenados.getOrNull(rank % 10)?.key ?: (0..9).randomDet()
    }
    
    /**
     * Obtiene el dígito top para una posición.
     */
    private fun obtenerDigitoTop(frecuencias: Map<Int, Int>, offset: Int): Int {
        val ordenados = frecuencias.entries.sortedByDescending { it.value }
        return ordenados.getOrNull(offset % 10)?.key ?: (0..9).randomDet()
    }
    
    /**
     * Calcula la puntuación de un número basándose en las frecuencias históricas.
     */
    private fun calcularPuntuacionNumero(
        numero: Int,
        frecuenciasPorPosicion: Array<MutableMap<Int, Int>>,
        totalSorteos: Int
    ): Double {
        if (totalSorteos == 0) return 0.0
        
        val numeroStr = numero.toString().padStart(5, '0')
        var puntuacionTotal = 0.0
        
        numeroStr.forEachIndexed { pos, char ->
            val digito = char.digitToIntOrNull() ?: return@forEachIndexed
            val frecuencia = frecuenciasPorPosicion[pos][digito] ?: 0
            puntuacionTotal += (frecuencia.toDouble() / totalSorteos) * 100
        }
        
        return puntuacionTotal / 5 // Promedio de las 5 posiciones
    }
    
    /**
     * Crea el análisis detallado por posición para mostrar en UI.
     */
    private fun crearAnalisisPorPosicion(
        frecuenciasPorPosicion: Array<MutableMap<Int, Int>>,
        totalSorteos: Int
    ): Map<String, List<Pair<Int, Double>>> {
        val nombresPosicion = listOf(
            "Decenas de millar (1ª)",
            "Millares (2ª)",
            "Centenas (3ª)",
            "Decenas (4ª)",
            "Unidades (5ª)"
        )
        
        return nombresPosicion.mapIndexed { pos, nombre ->
            nombre to frecuenciasPorPosicion[pos].entries
                .sortedByDescending { it.value }
                .map { (digito, freq) ->
                    digito to if (totalSorteos > 0) (freq.toDouble() / totalSorteos * 100) else 0.0
                }
        }.toMap()
    }

    // ==================== NAVIDAD ====================

    private fun analizarNavidad(
        historico: List<ResultadoNavidad>,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): AnalisisProbabilidad {
        if (historico.isEmpty() && metodo != MetodoCalculo.ALEATORIO_PURO) {
            return crearAnalisisVacio(TipoLoteria.NAVIDAD, metodo)
        }

        // ==================== ANÁLISIS DE FRECUENCIAS POR POSICIÓN ====================
        val frecuenciasPorPosicion = Array(5) { mutableMapOf<Int, Int>() }

        // Inicializar con ceros
        for (pos in 0..4) {
            for (digito in 0..9) {
                frecuenciasPorPosicion[pos][digito] = 0
            }
        }

        // Contar frecuencias de cada dígito en cada posición (El Gordo)
        historico.forEach { resultado ->
            val numero = resultado.gordo.padStart(5, '0')
            numero.forEachIndexed { posicion, char ->
                val digito = char.digitToIntOrNull() ?: return@forEachIndexed
                frecuenciasPorPosicion[posicion][digito] =
                    (frecuenciasPorPosicion[posicion][digito] ?: 0) + 1
            }
        }

        val terminaciones = historico.mapNotNull { it.gordo.takeLast(2).toIntOrNull() }
        val frecuenciasTerminaciones = contarFrecuencias(terminaciones, 0..99)
        val frecuenciasReintegros = contarFrecuencias(historico.flatMap { it.reintegros }, 0..9)

        val combinaciones = when (metodo) {
            MetodoCalculo.ALEATORIO_PURO -> {
                (0 until numCombinaciones).map {
                    val numero = (0..99999).randomDet()
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = 0.001,
                        explicacion = "🎲 Aleatorio: ${numero.toString().padStart(5, '0')}"
                    )
                }
            }
            MetodoCalculo.FRECUENCIAS -> {
                generarNumerosOptimos(
                    frecuenciasPorPosicion,
                    numCombinaciones,
                    historico.size,
                    "📊 Frecuencias",
                    seleccionarMasFrecuentes = true
                )
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                generarNumerosOptimos(
                    frecuenciasPorPosicion,
                    numCombinaciones,
                    historico.size,
                    "❄️ Fríos",
                    seleccionarMasFrecuentes = false
                )
            }
            else -> {
                val terminacionesOrdenadas = frecuenciasTerminaciones.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    
                (0 until numCombinaciones).map { index ->
                    val terminacion = terminacionesOrdenadas.getOrElse(index) { (0..99).randomDet() }
                    val frecuencia = frecuenciasTerminaciones[terminacion] ?: 0
                    
                    val dig0 = obtenerDigitoTop(frecuenciasPorPosicion[0], index)
                    val dig1 = obtenerDigitoTop(frecuenciasPorPosicion[1], index)
                    val dig2 = obtenerDigitoTop(frecuenciasPorPosicion[2], index)
                    val prefijo = dig0 * 100 + dig1 * 10 + dig2
                    
                    val numero = prefijo * 100 + terminacion
                    CombinacionSugerida(
                        numeros = listOf(numero),
                        probabilidadRelativa = if (historico.isNotEmpty()) {
                            (frecuencia.toDouble() / historico.size * 100).roundTo(2)
                        } else 0.001,
                        explicacion = "🎄 Décimo: ${numero.toString().padStart(5, '0')} | Term: ${terminacion.toString().padStart(2, '0')} (${frecuencia}x)"
                    )
                }
            }
        }

        val probabilidadTeorica = "1 entre 100.000 (0.001%) para El Gordo"
        
        val fechaUltimoSorteo = historico.maxByOrNull { it.fecha }?.fecha

        return AnalisisProbabilidad(
            tipoLoteria = TipoLoteria.NAVIDAD,
            metodoCalculo = metodo,
            totalSorteos = historico.size,
            combinacionesSugeridas = combinaciones,
            numerosMasFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size),
            numerosMenosFrequentes = obtenerTopNumeros(frecuenciasTerminaciones, 10, historico.size, menosFrecuentes = true),
            complementariosMasFrequentes = obtenerTopNumeros(frecuenciasReintegros, 5, historico.size),
            probabilidadTeorica = probabilidadTeorica,
            fechaUltimoSorteo = fechaUltimoSorteo,
            analisisPorPosicion = crearAnalisisPorPosicion(frecuenciasPorPosicion, historico.size)
        )
    }

    // ==================== MÉTODOS DE GENERACIÓN ====================

    /**
     * FRECUENCIAS: Prioriza números que han salido más veces.
     */
    private fun generarPorFrecuencias(
        frecuencias: Map<Int, Int>,
        cantidad: Int,
        numCombinaciones: Int,
        totalSorteos: Int
    ): List<CombinacionSugerida> {
        val numerosOrdenados = frecuencias.entries.sortedByDescending { it.value }.map { it.key }
        
        return (0 until numCombinaciones).map { index ->
            val offset = index * 2
            val numerosSeleccionados = numerosOrdenados
                .drop(offset)
                .take(cantidad)
                .sorted()

            val puntuacion = numerosSeleccionados.sumOf { frecuencias.getOrDefault(it, 0) }
                .toDouble() / (totalSorteos * cantidad) * 100

            CombinacionSugerida(
                numeros = numerosSeleccionados,
                probabilidadRelativa = puntuacion.roundTo(2),
                explicacion = "Frecuencia histórica: ${puntuacion.roundTo(1)}%"
            )
        }
    }

    /**
     * NÚMEROS FRÍOS: Los menos frecuentes (teoría de que "les toca").
     */
    private fun generarNumerosFrios(
        frecuencias: Map<Int, Int>,
        cantidad: Int,
        numCombinaciones: Int,
        totalSorteos: Int
    ): List<CombinacionSugerida> {
        // Ordenar de menor a mayor frecuencia
        val numerosOrdenados = frecuencias.entries.sortedBy { it.value }.map { it.key }
        
        return (0 until numCombinaciones).map { index ->
            val offset = index * 2
            val numerosSeleccionados = numerosOrdenados
                .drop(offset)
                .take(cantidad)
                .sorted()

            CombinacionSugerida(
                numeros = numerosSeleccionados,
                probabilidadRelativa = 50.0, // Puntuación neutral
                explicacion = "Números fríos: poco frecuentes, 'les toca'"
            )
        }
    }

    /**
     * ALEATORIO PURO: Completamente al azar.
     */
    private fun generarAleatorio(maxNumero: Int, cantidad: Int, numCombinaciones: Int): List<CombinacionSugerida> {
        return (0 until numCombinaciones).map {
            val numeros = (1..maxNumero).shuffled(rnd).take(cantidad).sorted()
            CombinacionSugerida(
                numeros = numeros,
                probabilidadRelativa = 50.0,
                explicacion = "Aleatorio puro: tan válido como cualquier otro"
            )
        }
    }

    // ==================== UTILIDADES ====================

    private fun contarFrecuencias(numeros: List<Int>, rango: IntRange): Map<Int, Int> {
        val frecuencias = rango.associateWith { 0 }.toMutableMap()
        numeros.forEach { numero ->
            if (numero in rango) {
                frecuencias[numero] = frecuencias.getOrDefault(numero, 0) + 1
            }
        }
        return frecuencias
    }

    private fun obtenerTopNumeros(
        frecuencias: Map<Int, Int>,
        cantidad: Int,
        totalSorteos: Int,
        menosFrecuentes: Boolean = false
    ): List<EstadisticaNumero> {
        return frecuencias.entries
            .sortedBy { if (menosFrecuentes) it.value else -it.value }
            .take(cantidad)
            .map { (numero, apariciones) ->
                EstadisticaNumero(
                    numero = numero,
                    apariciones = apariciones,
                    porcentaje = if (totalSorteos > 0) {
                        (apariciones.toDouble() / totalSorteos * 100).roundTo(2)
                    } else 0.0
                )
            }
    }

    private fun calcularProbabilidadLaplace(n: Int, r: Int): String {
        // C(n,r) = n! / (r! * (n-r)!)
        // Usamos BigInteger para evitar overflow
        val combinaciones = calcularCombinaciones(n, r)
        val probabilidad = 1.0 / combinaciones.toDouble()
        return "1 entre ${formatearNumeroBig(combinaciones)} (${formatearPorcentaje(probabilidad * 100)})"
    }

    /**
     * Calcula C(n,r) usando BigInteger para evitar overflow.
     * C(n,r) = n! / (r! * (n-r)!)
     * 
     * Optimización: C(n,r) = [n * (n-1) * ... * (n-r+1)] / [r * (r-1) * ... * 1]
     */
    private fun calcularCombinaciones(n: Int, r: Int): java.math.BigInteger {
        if (r > n) return java.math.BigInteger.ZERO
        if (r == 0 || r == n) return java.math.BigInteger.ONE
        
        // Usar el menor de r y (n-r) para optimizar
        val k = if (r > n - r) n - r else r
        
        var resultado = java.math.BigInteger.ONE
        for (i in 0 until k) {
            resultado = resultado.multiply(java.math.BigInteger.valueOf((n - i).toLong()))
            resultado = resultado.divide(java.math.BigInteger.valueOf((i + 1).toLong()))
        }
        return resultado
    }

    private fun formatearNumeroBig(n: java.math.BigInteger): String {
        // Formatear con puntos como separadores de miles
        val str = n.toString()
        val reversed = str.reversed()
        val formatted = reversed.chunked(3).joinToString(".").reversed()
        return formatted
    }

    private fun formatearPorcentaje(p: Double): String {
        return if (p < 0.0001) {
            "≈${String.format("%.8f", p)}%"
        } else {
            "${String.format("%.4f", p)}%"
        }
    }

    private fun crearAnalisisVacio(tipoLoteria: TipoLoteria, metodo: MetodoCalculo): AnalisisProbabilidad {
        return AnalisisProbabilidad(
            tipoLoteria = tipoLoteria,
            metodoCalculo = metodo,
            totalSorteos = 0,
            combinacionesSugeridas = emptyList(),
            numerosMasFrequentes = emptyList(),
            numerosMenosFrequentes = emptyList(),
            probabilidadTeorica = "Sin datos históricos"
        )
    }

    private fun Double.roundTo(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return (this * multiplier).roundToInt() / multiplier
    }
    
    // ==================== SISTEMA DE BACKTESTING ====================
    
    /**
     * Ejecuta backtesting para Primitiva/Bonoloto.
     * Retrocede N sorteos en el histórico y prueba cada método.
     * 
     * @param historico Lista completa de sorteos
     * @param diasAtras Número de sorteos a retroceder para probar
     * @return Lista de resultados de backtesting por método
     */
    /**
     * Callback para reportar progreso durante el backtesting
     */
    var onProgresoBacktest: ((metodo: String, combinacion: Int, total: Int) -> Unit)? = null
    
    fun ejecutarBacktestPrimitiva(
        historico: List<ResultadoPrimitiva>,
        diasAtras: Int = 10,
        tipoLoteria: String = "PRIMITIVA",
        metodosAEvaluar: Array<MetodoCalculo> = MetodoCalculo.values()
    ): List<ResultadoBacktest> {
        if (historico.size <= diasAtras) return emptyList()

        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = metodosAEvaluar
        val totalCombsPorMetodo = diasAtras * 5
        val totalCombs = metodos.size * totalCombsPorMetodo
        var combinacionGlobal = 0
        
        // OPTIMIZACIÓN: Precalcular frecuencias una sola vez
        val maxNumero = if (tipoLoteria == "BONOLOTO") 49 else 49
        val frecuenciasGlobales = contarFrecuencias(historico.flatMap { it.numeros }, 1..maxNumero)
        
        // Probar cada método
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            
            var aciertos0 = 0
            var aciertos1 = 0
            var aciertos2 = 0
            var aciertos3 = 0
            var aciertos4 = 0
            var aciertos5 = 0
            var aciertos6 = 0
            var aciertosComplementario = 0
            var aciertosReintegro = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            // Para cada sorteo de prueba
            for (i in 0 until diasAtras) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.isEmpty()) continue
                
                // OPTIMIZACIÓN: Generar combinaciones directamente sin crear AnalisisProbabilidad completo
                val combinaciones = generarCombinacionesRapido(
                    frecuenciasGlobales, metodo, 6, 5, historicoHastaMomento
                )
                
                val sorteoReal = historico[i]
                val numerosReales = sorteoReal.numeros.toSet()
                val complementarioReal = sorteoReal.complementario
                
                for (numeros in combinaciones) {
                    combinacionGlobal++
                    // Actualizar progreso cada 50 combinaciones
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val numerosPredichos = numeros.toSet()
                    val aciertosEnCombinacion = numerosPredichos.intersect(numerosReales).size
                    
                    when (aciertosEnCombinacion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        5 -> aciertos5++
                        6 -> aciertos6++
                    }
                    
                    if (aciertosEnCombinacion >= 5 && complementarioReal in numerosPredichos) {
                        aciertosComplementario++
                    }
                    
                    if (aciertosEnCombinacion > mejorAcierto) {
                        mejorAcierto = aciertosEnCombinacion
                    }
                    totalAciertos += aciertosEnCombinacion
                }
            }
            
            val totalCombinaciones = diasAtras * 5
            val puntuacion = (aciertos1 * 1.0 + aciertos2 * 3.0 + aciertos3 * 10.0 + 
                             aciertos4 * 50.0 + aciertos5 * 200.0 + aciertos6 * 1000.0 +
                             aciertosComplementario * 100.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasAtras,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertos6 = aciertos6,
                aciertosComplementario = aciertosComplementario,
                aciertosReintegro = aciertosReintegro,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = tipoLoteria
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    /**
     * Genera combinaciones de forma rápida sin crear objetos pesados
     */
    private fun generarCombinacionesRapido(
        frecuencias: Map<Int, Int>,
        metodo: MetodoCalculo,
        cantidadNumeros: Int,
        numCombinaciones: Int,
        historico: List<ResultadoPrimitiva>
    ): List<List<Int>> {
        val maxNumero = 49
        val totalSorteos = historico.size.coerceAtLeast(1)
        
        return when (metodo) {
            MetodoCalculo.FRECUENCIAS -> {
                // Top números más frecuentes
                val topNums = frecuencias.entries.sortedByDescending { it.value }.take(20).map { it.key }
                (0 until numCombinaciones).map { i ->
                    topNums.drop(i).take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                // Números menos frecuentes
                val frios = frecuencias.entries.sortedBy { it.value }.take(20).map { it.key }
                (0 until numCombinaciones).map { i ->
                    frios.drop(i).take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.ALEATORIO_PURO -> {
                (0 until numCombinaciones).map {
                    (1..maxNumero).shuffled(rnd).take(cantidadNumeros).sorted()
                }
            }
            MetodoCalculo.IA_GENETICA -> {
                // Versión simplificada del algoritmo genético
                motorIA.generarCombinacionesInteligentes(historico, 49, cantidadNumeros, numCombinaciones).map { it.numeros }
            }
            MetodoCalculo.ENSEMBLE_VOTING -> {
                val resultado = motorIA.ejecutarEnsembleVoting(historico, 49, cantidadNumeros, "PRIMITIVA")
                listOf(resultado.combinacionGanadora) + resultado.combinacionesAlternativas.take(numCombinaciones - 1)
            }
            MetodoCalculo.ALTA_CONFIANZA -> {
                val prediccion = motorIA.generarPrediccionAltaConfianza(historico, 49, cantidadNumeros, "PRIMITIVA")
                listOf(prediccion.combinacionPrincipal) + prediccion.combinacionesAlternativas.take(numCombinaciones - 1)
            }
            MetodoCalculo.RACHAS_MIX -> {
                (0 until numCombinaciones).map { motorIA.generarCombinacionMixta(historico, 49, cantidadNumeros, "PRIMITIVA").numeros }
            }
            MetodoCalculo.METODO_ABUELO -> {
                // 🔮 Método del Abuelo: Sistema de convergencias
                val resultado = motorIA.ejecutarMetodoAbuelo(historico, 49, cantidadNumeros, "PRIMITIVA")
                listOf(resultado.combinacionPrincipal) + resultado.combinacionesAlternativas.take(numCombinaciones - 1)
            }
        }
    }

    /**
     * Ejecuta backtesting para Euromillones.
     */
    fun ejecutarBacktestEuromillones(
        historico: List<ResultadoEuromillones>,
        diasAtras: Int = 10,
        metodosAEvaluar: Array<MetodoCalculo> = MetodoCalculo.values()
    ): List<ResultadoBacktest> {
        if (historico.size <= diasAtras) return emptyList()

        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = metodosAEvaluar
        val totalCombs = metodos.size * diasAtras * 5
        var combinacionGlobal = 0
        
        // Precalcular frecuencias
        val frecuenciasNumeros = contarFrecuencias(historico.flatMap { it.numeros }, 1..50)
        val frecuenciasEstrellas = contarFrecuencias(historico.flatMap { it.estrellas }, 1..12)
        
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            
            var aciertos0 = 0
            var aciertos1 = 0
            var aciertos2 = 0
            var aciertos3 = 0
            var aciertos4 = 0
            var aciertos5 = 0
            var aciertosEstrella1 = 0
            var aciertosEstrella2 = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasAtras) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.isEmpty()) continue
                
                // Generar combinaciones rápido
                val combinaciones = generarCombinacionesEuroRapido(frecuenciasNumeros, frecuenciasEstrellas, metodo, 5)
                val sorteoReal = historico[i]
                val numerosReales = sorteoReal.numeros.toSet()
                val estrellasReales = sorteoReal.estrellas.toSet()
                
                for ((numeros, estrellas) in combinaciones) {
                    combinacionGlobal++
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val aciertosEnCombinacion = numeros.toSet().intersect(numerosReales).size
                    val aciertosEstrellas = estrellas.toSet().intersect(estrellasReales).size
                    
                    when (aciertosEnCombinacion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        5 -> aciertos5++
                    }
                    
                    if (aciertosEstrellas >= 1) aciertosEstrella1++
                    if (aciertosEstrellas >= 2) aciertosEstrella2++
                    
                    if (aciertosEnCombinacion > mejorAcierto) mejorAcierto = aciertosEnCombinacion
                    totalAciertos += aciertosEnCombinacion
                }
            }
            
            val totalCombinaciones = diasAtras * 5
            val puntuacion = (aciertos1 * 1.0 + aciertos2 * 3.0 + aciertos3 * 10.0 + 
                             aciertos4 * 50.0 + aciertos5 * 500.0 +
                             aciertosEstrella1 * 10.0 + aciertosEstrella2 * 50.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasAtras,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertosEstrella1 = aciertosEstrella1,
                aciertosEstrella2 = aciertosEstrella2,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = "EUROMILLONES"
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    private fun generarCombinacionesEuroRapido(
        frecNums: Map<Int, Int>,
        frecEstrellas: Map<Int, Int>,
        metodo: MetodoCalculo,
        numCombinaciones: Int
    ): List<Pair<List<Int>, List<Int>>> {
        val topNums = frecNums.entries.sortedByDescending { it.value }.map { it.key }
        val topEstrellas = frecEstrellas.entries.sortedByDescending { it.value }.map { it.key }
        
        return (0 until numCombinaciones).map { i ->
            val nums = when (metodo) {
                MetodoCalculo.FRECUENCIAS -> topNums.drop(i).take(5)
                MetodoCalculo.NUMEROS_FRIOS -> topNums.reversed().drop(i).take(5)
                MetodoCalculo.ALEATORIO_PURO -> (1..50).shuffled(rnd).take(5)
                else -> topNums.drop(i).take(5)
            }.sorted()
            
            val estrellas = listOf(
                topEstrellas[(i * 2) % topEstrellas.size],
                topEstrellas[(i * 2 + 1) % topEstrellas.size]
            ).distinct().sorted()
            
            Pair(nums, estrellas)
        }
    }
    
    /**
     * Ejecuta backtesting para El Gordo de la Primitiva.
     */
    fun ejecutarBacktestGordo(
        historico: List<ResultadoGordoPrimitiva>,
        diasAtras: Int = 10,
        metodosAEvaluar: Array<MetodoCalculo> = MetodoCalculo.values()
    ): List<ResultadoBacktest> {
        if (historico.size <= diasAtras) return emptyList()

        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = metodosAEvaluar
        val totalCombs = metodos.size * diasAtras * 5
        var combinacionGlobal = 0
        
        // Precalcular frecuencias
        val frecuencias = contarFrecuencias(historico.flatMap { it.numeros }, 1..54)
        
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            
            var aciertos0 = 0
            var aciertos1 = 0
            var aciertos2 = 0
            var aciertos3 = 0
            var aciertos4 = 0
            var aciertos5 = 0
            var aciertosClave = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasAtras) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.isEmpty()) continue
                
                // Generar combinaciones rápido
                val topNums = frecuencias.entries.sortedByDescending { it.value }.map { it.key }
                val combinaciones = when (metodo) {
                    MetodoCalculo.FRECUENCIAS -> (0 until 5).map { j -> topNums.drop(j).take(5).sorted() }
                    MetodoCalculo.NUMEROS_FRIOS -> (0 until 5).map { j -> topNums.reversed().drop(j).take(5).sorted() }
                    MetodoCalculo.ALEATORIO_PURO -> (0 until 5).map { (1..54).shuffled(rnd).take(5).sorted() }
                    else -> (0 until 5).map { j -> topNums.drop(j).take(5).sorted() }
                }
                
                val sorteoReal = historico[i]
                val numerosReales = sorteoReal.numeros.toSet()
                
                for (numeros in combinaciones) {
                    combinacionGlobal++
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val aciertosEnCombinacion = numeros.toSet().intersect(numerosReales).size
                    
                    when (aciertosEnCombinacion) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        5 -> aciertos5++
                    }
                    
                    if (aciertosEnCombinacion > mejorAcierto) mejorAcierto = aciertosEnCombinacion
                    totalAciertos += aciertosEnCombinacion
                }
            }
            
            val totalCombinaciones = diasAtras * 5
            val puntuacion = (aciertos1 * 1.0 + aciertos2 * 3.0 + aciertos3 * 10.0 + 
                             aciertos4 * 50.0 + aciertos5 * 300.0 + aciertosClave * 30.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasAtras,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertosClave = aciertosClave,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = "GORDO_PRIMITIVA"
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    /**
     * Ejecuta backtesting para Lotería Nacional / El Niño.
     * Compara terminaciones de 2 dígitos del primer premio.
     */
    fun ejecutarBacktestNacional(
        historico: List<ResultadoNacional>,
        diasAtras: Int = 10,
        tipoLoteria: String = "LOTERIA_NACIONAL",
        metodosAEvaluar: Array<MetodoCalculo> = MetodoCalculo.values()
    ): List<ResultadoBacktest> {
        val diasEfectivos = diasAtras.coerceAtMost(historico.size - 2).coerceAtLeast(1)
        if (historico.size < 3) return emptyList()

        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = metodosAEvaluar
        val totalCombs = metodos.size * diasEfectivos * 5
        var combinacionGlobal = 0
        
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            
            var aciertos0 = 0  // 0 cifras
            var aciertos1 = 0  // 1 cifra (última)
            var aciertos2 = 0  // 2 cifras (terminación)
            var aciertos3 = 0  // 3 cifras
            var aciertos4 = 0  // 4 cifras
            var aciertos5 = 0  // 5 cifras (número completo)
            var aciertosReintegro = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasEfectivos) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.size < 3) continue
                
                val sorteoReal = historico[i]
                val numeroReal = sorteoReal.primerPremio.filter { it.isDigit() }.takeLast(5).padStart(5, '0')
                val reintegrosReales = sorteoReal.reintegros.toSet()
                
                // Generar predicciones según MÉTODO
                val predicciones = generarPrediccionesNacional(historicoHastaMomento, metodo)
                
                for (prediccion in predicciones.take(5)) {
                    combinacionGlobal++
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val numeroPred = prediccion.toString().padStart(5, '0').takeLast(5)
                    
                    // Contar cifras coincidentes desde el final
                    val cifrasCoincidentes = contarCifrasCoincidentes(numeroPred, numeroReal)
                    
                    // Comprobar reintegro (última cifra de la predicción)
                    val ultimaCifraPred = numeroPred.last().digitToIntOrNull() ?: -1
                    if (ultimaCifraPred in reintegrosReales) aciertosReintegro++
                    
                    when (cifrasCoincidentes) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        else -> aciertos5++
                    }
                    
                    if (cifrasCoincidentes > mejorAcierto) mejorAcierto = cifrasCoincidentes
                    totalAciertos += cifrasCoincidentes
                }
            }
            
            val totalCombinaciones = (diasEfectivos * 5).coerceAtLeast(1)
            // Puntuación: 1 cifra=2, 2 cifras=15, 3 cifras=100, 4 cifras=500, 5 cifras=2000, reintegro=5
            val puntuacion = (aciertos1 * 2.0 + aciertos2 * 15.0 + aciertos3 * 100.0 + 
                             aciertos4 * 500.0 + aciertos5 * 2000.0 + aciertosReintegro * 5.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasEfectivos,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertosReintegro = aciertosReintegro,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = tipoLoteria
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    /**
     * Cuenta cuántas cifras coinciden desde el final entre dos números.
     * Ej: "12345" y "00045" -> 2 cifras (45)
     *     "12345" y "12345" -> 5 cifras
     *     "12345" y "99999" -> 0 cifras
     */
    private fun contarCifrasCoincidentes(pred: String, real: String): Int {
        val predPadded = pred.takeLast(5).padStart(5, '0')
        val realPadded = real.takeLast(5).padStart(5, '0')
        
        var cifras = 0
        for (i in 4 downTo 0) {
            if (predPadded[i] == realPadded[i]) {
                cifras++
            } else {
                break  // Si una cifra no coincide, dejar de contar
            }
        }
        return cifras
    }
    
    /**
     * Genera predicciones de terminaciones para Nacional/Navidad/Niño según método
     */
    private fun generarPrediccionesNacional(
        historico: List<ResultadoNacional>,
        metodo: MetodoCalculo
    ): List<Int> {
        val terminaciones = historico.mapNotNull { 
            it.primerPremio.filter { c -> c.isDigit() }.takeLast(2).toIntOrNull() 
        }
        if (terminaciones.isEmpty()) return (0..99).shuffled(rnd).take(5)
        
        return when (metodo) {
            MetodoCalculo.IA_GENETICA -> {
                // Combinar múltiples estrategias con pesos
                val porFrec = terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.take(4).map { it.key }
                val porRecientes = terminaciones.take(3)
                val noSalieron = (0..99).filter { it !in terminaciones }.shuffled(rnd).take(3)
                (porFrec + porRecientes + noSalieron).distinct().take(10)
            }
            MetodoCalculo.FRECUENCIAS -> {
                // Terminaciones más frecuentes
                terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(10).map { it.key }
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                // Terminaciones con mayor gap (más tiempo sin salir)
                val ultimaAparicion = mutableMapOf<Int, Int>()
                terminaciones.forEachIndexed { idx, term -> ultimaAparicion[term] = idx }
                (0..99).filter { ultimaAparicion[it] != null }
                    .sortedBy { ultimaAparicion[it] ?: 0 }
                    .take(10)
            }
            MetodoCalculo.ALEATORIO_PURO -> {
                // Completamente aleatorio
                (0..99).shuffled(rnd).take(10)
            }
            MetodoCalculo.ENSEMBLE_VOTING, MetodoCalculo.ALTA_CONFIANZA, MetodoCalculo.RACHAS_MIX, MetodoCalculo.METODO_ABUELO -> {
                // Para loterías de 5 dígitos, usar frecuencias como fallback
                terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(10).map { it.key }
            }
        }
    }

    /**
     * Ejecuta backtesting para Lotería de Navidad.
     * Compara terminaciones del Gordo usando diferentes estrategias por método.
     */
    fun ejecutarBacktestNavidad(
        historico: List<ResultadoNavidad>,
        diasAtras: Int = 10,
        metodosAEvaluar: Array<MetodoCalculo> = MetodoCalculo.values()
    ): List<ResultadoBacktest> {
        val diasEfectivos = diasAtras.coerceAtMost(historico.size - 2).coerceAtLeast(1)
        if (historico.size < 3) return emptyList()

        val resultados = mutableListOf<ResultadoBacktest>()
        val metodos = metodosAEvaluar
        val totalCombs = metodos.size * diasEfectivos * 5
        var combinacionGlobal = 0
        
        for (metodo in metodos) {
            onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
            var aciertos0 = 0  // 0 cifras
            var aciertos1 = 0  // 1 cifra (última)
            var aciertos2 = 0  // 2 cifras (terminación)
            var aciertos3 = 0  // 3 cifras
            var aciertos4 = 0  // 4 cifras
            var aciertos5 = 0  // 5 cifras (número completo)
            var aciertosReintegro = 0
            var mejorAcierto = 0
            var totalAciertos = 0
            
            for (i in 0 until diasEfectivos) {
                val historicoHastaMomento = historico.drop(i + 1)
                if (historicoHastaMomento.size < 3) continue
                
                val sorteoReal = historico[i]
                val gordoReal = sorteoReal.gordo.filter { it.isDigit() }.takeLast(5).padStart(5, '0')
                val reintegrosReales = sorteoReal.reintegros.toSet()
                
                // Generar predicciones según MÉTODO
                val predicciones = generarPrediccionesNavidad(historicoHastaMomento, metodo)
                
                for (prediccion in predicciones.take(5)) {
                    combinacionGlobal++
                    if (combinacionGlobal % 25 == 0) {
                        onProgresoBacktest?.invoke(metodo.displayName, combinacionGlobal, totalCombs)
                    }
                    val numeroPred = prediccion.toString().padStart(5, '0').takeLast(5)
                    
                    // Contar cifras coincidentes desde el final
                    val cifrasCoincidentes = contarCifrasCoincidentes(numeroPred, gordoReal)
                    
                    // Comprobar reintegro (última cifra de la predicción)
                    val ultimaCifraPred = numeroPred.last().digitToIntOrNull() ?: -1
                    if (ultimaCifraPred in reintegrosReales) aciertosReintegro++
                    
                    when (cifrasCoincidentes) {
                        0 -> aciertos0++
                        1 -> aciertos1++
                        2 -> aciertos2++
                        3 -> aciertos3++
                        4 -> aciertos4++
                        else -> aciertos5++
                    }
                    
                    if (cifrasCoincidentes > mejorAcierto) mejorAcierto = cifrasCoincidentes
                    totalAciertos += cifrasCoincidentes
                }
            }
            
            val totalCombinaciones = (diasEfectivos * 5).coerceAtLeast(1)
            // Puntuación: 1 cifra=2, 2 cifras=15, 3 cifras=100, 4 cifras=500, 5 cifras=2000, reintegro=5
            val puntuacion = (aciertos1 * 2.0 + aciertos2 * 15.0 + aciertos3 * 100.0 + 
                             aciertos4 * 500.0 + aciertos5 * 2000.0 + aciertosReintegro * 5.0) / totalCombinaciones * 100
            
            resultados.add(ResultadoBacktest(
                metodo = metodo,
                sorteosProbados = diasEfectivos,
                aciertos0 = aciertos0,
                aciertos1 = aciertos1,
                aciertos2 = aciertos2,
                aciertos3 = aciertos3,
                aciertos4 = aciertos4,
                aciertos5 = aciertos5,
                aciertosReintegro = aciertosReintegro,
                puntuacionTotal = puntuacion.roundTo(2),
                mejorAcierto = mejorAcierto,
                promedioAciertos = (totalAciertos.toDouble() / totalCombinaciones).roundTo(2),
                tipoLoteria = "NAVIDAD"
            ))
        }
        
        return resultados.sortedByDescending { it.puntuacionTotal }
    }
    
    /**
     * Genera predicciones de terminaciones para Navidad según método
     */
    private fun generarPrediccionesNavidad(
        historico: List<ResultadoNavidad>,
        metodo: MetodoCalculo
    ): List<Int> {
        val terminaciones = historico.mapNotNull { 
            it.gordo.filter { c -> c.isDigit() }.takeLast(2).toIntOrNull() 
        }
        if (terminaciones.isEmpty()) return (0..99).shuffled(rnd).take(5)
        
        return when (metodo) {
            MetodoCalculo.IA_GENETICA -> {
                // Combinar múltiples estrategias
                val porFrec = terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.take(4).map { it.key }
                val porRecientes = terminaciones.take(3)
                val porDecenas = terminaciones.map { it / 10 }.distinct().take(3).map { it * 10 + (0..9).randomDet() }
                (porFrec + porRecientes + porDecenas).distinct().take(10)
            }
            MetodoCalculo.FRECUENCIAS -> {
                terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(10).map { it.key }
            }
            MetodoCalculo.NUMEROS_FRIOS -> {
                val ultimaAparicion = mutableMapOf<Int, Int>()
                terminaciones.forEachIndexed { idx, term -> ultimaAparicion[term] = idx }
                (0..99).filter { ultimaAparicion[it] != null }
                    .sortedBy { ultimaAparicion[it] ?: 0 }
                    .take(10)
            }
            MetodoCalculo.ALEATORIO_PURO -> {
                (0..99).shuffled(rnd).take(10)
            }
            MetodoCalculo.ENSEMBLE_VOTING, MetodoCalculo.ALTA_CONFIANZA, MetodoCalculo.RACHAS_MIX, MetodoCalculo.METODO_ABUELO -> {
                // Para loterías de 5 dígitos, usar frecuencias como fallback
                terminaciones.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(10).map { it.key }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FÓRMULA DEL ABUELO: Cobertura + Anti-Popularidad + Kelly
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Ejecuta la Fórmula del Abuelo con candidatos ya seleccionados por votación.
     * Los candidatos vienen del UseCase que ejecuta los 8 métodos y los pondera.
     */
    fun ejecutarFormulaAbueloConCandidatos(
        candidatos: List<Int>,
        tipoLoteria: TipoLoteria,
        historico: List<ResultadoSorteo>,
        boteActual: Double = 0.0,
        garantiaMinima: Int = 3
    ): ResultadoFormulaAbuelo {
        motorIA.recargarMemoria(tipoLoteria.name)
        inicializarSemilla(tipoLoteria.name, historico)

        // Extraer el conjunto de combinaciones históricas para el filtro de repetidos
        val historicoNums: Set<Set<Int>> = when (tipoLoteria) {
            TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> {
                @Suppress("UNCHECKED_CAST")
                (historico as List<ResultadoPrimitiva>).map { it.numeros.toSet() }.toSet()
            }
            TipoLoteria.EUROMILLONES -> {
                @Suppress("UNCHECKED_CAST")
                (historico as List<ResultadoEuromillones>).map { it.numeros.toSet() }.toSet()
            }
            TipoLoteria.GORDO_PRIMITIVA -> {
                @Suppress("UNCHECKED_CAST")
                (historico as List<ResultadoGordoPrimitiva>).map { it.numeros.toSet() }.toSet()
            }
            else -> emptySet()
        }

        return com.loteria.probabilidad.domain.ml.FormulaAbuelo.ejecutar(
            candidatos = candidatos,
            tipoLoteria = tipoLoteria,
            boteActual = boteActual,
            numCandidatos = candidatos.size,
            garantiaMinima = garantiaMinima,
            historicoNums = historicoNums
        )
    }
}
