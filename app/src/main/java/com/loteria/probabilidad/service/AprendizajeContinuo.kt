package com.loteria.probabilidad.service

import android.content.Context
import com.loteria.probabilidad.data.datasource.LoteriaLocalDataSource
import com.loteria.probabilidad.data.model.ResultadoPrimitiva
import com.loteria.probabilidad.data.model.TipoLoteria
import com.loteria.probabilidad.domain.ml.MemoriaIA
import com.loteria.probabilidad.domain.ml.MotorInteligencia
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * Aprendizaje continuo mientras el app está abierto.
 *
 * Ejecuta un bucle de mini walk-forward sobre el histórico:
 *   1. Toma un sorteo aleatorio del histórico (índice i)
 *   2. Puntúa cada voter del Ensemble usando solo history[0..i-1]
 *   3. Compara con el sorteo real [i]
 *   4. Acumula 10 iteraciones y actualiza pesos del Ensemble vía MemoriaIA
 *
 * Con 4000+ sorteos históricos disponibles, aprende sin esperar sorteos reales.
 * No usa algoritmo genético — cada voter se evalúa con reglas ligeras (~300ms/iter).
 * Se detiene automáticamente si AprendizajeService está activo (evita conflictos).
 */
class AprendizajeContinuo(private val context: Context) {

    private var job: Job? = null

    @Volatile var iteracionesCompletadas: Int = 0
        private set

    companion object {
        private const val BATCH_SIZE  = 10    // Actualizar pesos cada N iteraciones
        private const val DELAY_MS    = 300L  // Pausa entre iteraciones (bajo consumo)
        private const val MIN_HISTORIA = 50   // Sorteos mínimos de contexto previo
        private const val MAX_N        = 49
        private const val LOTERIA      = "PRIMITIVA"
    }

    /**
     * Inicia el bucle de aprendizaje dentro del [scope] dado.
     * Se cancela automáticamente cuando el scope se cancela (ej. onStop de Activity).
     */
    fun iniciar(scope: CoroutineScope) {
        if (job?.isActive == true) return
        iteracionesCompletadas = 0

        job = scope.launch(Dispatchers.Default) {
            // No competir con el AprendizajeService si está corriendo
            if (AprendizajeService.isRunning) return@launch

            val memoriaIA = MemoriaIA(context)
            val historico: List<ResultadoPrimitiva> =
                LoteriaLocalDataSource(context).leerHistoricoPrimitiva(TipoLoteria.PRIMITIVA)

            if (historico.size < MIN_HISTORIA + 10) return@launch

            val rnd = Random(System.currentTimeMillis())
            val batch = mutableListOf<Map<MotorInteligencia.EstrategiaPrediccion, Int>>()

            while (isActive && !AprendizajeService.isRunning) {
                try {
                    val i    = rnd.nextInt(MIN_HISTORIA, historico.size)
                    val hist = historico.subList(0, i)
                    val real = historico[i].numeros.toSet()

                    // Puntuar cada voter del Ensemble (sin algoritmo genético)
                    batch.add(mapOf(
                        MotorInteligencia.EstrategiaPrediccion.FRECUENCIA     to top6Frec(hist).overlap(real),
                        MotorInteligencia.EstrategiaPrediccion.TENDENCIA      to top6Frec(hist, 12).overlap(real),
                        MotorInteligencia.EstrategiaPrediccion.GENETICO       to top6Frec(hist, 6).overlap(real),
                        MotorInteligencia.EstrategiaPrediccion.ALTA_CONFIANZA to top6Consenso(hist).overlap(real),
                        MotorInteligencia.EstrategiaPrediccion.RACHAS_MIX    to top6RachasMix(hist).overlap(real),
                        MotorInteligencia.EstrategiaPrediccion.EQUILIBRIO     to top6Equilibrio(hist).overlap(real),
                        MotorInteligencia.EstrategiaPrediccion.CICLOS         to top6Gap(hist).overlap(real),
                        MotorInteligencia.EstrategiaPrediccion.CORRELACIONES  to top6Frec(hist, 20).overlap(real)
                    ))
                    iteracionesCompletadas++

                    // Cada BATCH_SIZE iteraciones, promediar y guardar pesos
                    if (batch.size >= BATCH_SIZE) {
                        val promedios = MotorInteligencia.EstrategiaPrediccion.values()
                            .associateWith { e -> batch.map { it[e] ?: 0 }.average().toInt() }
                        withContext(Dispatchers.IO) {
                            memoriaIA.registrarRendimientoEstrategias(promedios, LOTERIA)
                        }
                        batch.clear()
                    }

                    delay(DELAY_MS)

                } catch (_: CancellationException) {
                    break
                } catch (_: Exception) {
                    delay(1000)
                }
            }
        }
    }

    fun detener() {
        job?.cancel()
        job = null
    }

    val estaActivo: Boolean get() = job?.isActive == true

    // ─── Voter functions (sin algoritmo genético, rápidas) ───────────────────

    /** Top 6 por frecuencia (histórica o ventana reciente). */
    private fun top6Frec(hist: List<ResultadoPrimitiva>, ventana: Int? = null): Set<Int> {
        val data = if (ventana != null) hist.takeLast(ventana) else hist
        return (1..MAX_N).sortedByDescending { n -> data.count { n in it.numeros } }.take(6).toSet()
    }

    /** Consenso de ventanas 5/12/30: números en top-12 de al menos 2 de 3 ventanas. */
    private fun top6Consenso(hist: List<ResultadoPrimitiva>): Set<Int> {
        val topPorVentana = listOf(5, 12, 30).map { v ->
            val rec = hist.takeLast(v)
            (1..MAX_N).sortedByDescending { n -> rec.count { n in it.numeros } }.take(12).toSet()
        }
        return (1..MAX_N).sortedByDescending { n -> topPorVentana.count { n in it } }.take(6).toSet()
    }

    /** 3 más calientes en últimos 5 sorteos + 3 más frecuentes históricos. */
    private fun top6RachasMix(hist: List<ResultadoPrimitiva>): Set<Int> {
        val cal = top6Frec(hist, 5)
        val frec = top6Frec(hist)
        return (cal.take(3) + frec.filter { it !in cal }.take(3)).toSet()
    }

    /** 3 frecuentes entre 1-24 + 3 frecuentes entre 25-49 (equilibrio alto/bajo). */
    private fun top6Equilibrio(hist: List<ResultadoPrimitiva>): Set<Int> {
        val frec = { n: Int -> hist.count { n in it.numeros } }
        return ((1..24).sortedByDescending(frec).take(3) +
                (25..MAX_N).sortedByDescending(frec).take(3)).toSet()
    }

    /** Top 6 más "debidos": mayor número de sorteos sin aparecer. */
    private fun top6Gap(hist: List<ResultadoPrimitiva>): Set<Int> {
        val n = hist.size
        return (1..MAX_N)
            .sortedByDescending { x -> n - 1 - hist.indexOfLast { x in it.numeros } }
            .take(6).toSet()
    }

    private fun Set<Int>.overlap(real: Set<Int>) = intersect(real).size
}
