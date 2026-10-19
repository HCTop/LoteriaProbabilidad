package com.loteria.probabilidad.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.loteria.probabilidad.domain.usecase.GestorPredicciones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Simula en segundo plano los últimos [GestorPredicciones.OBJETIVO_SIMULACION] sorteos de cada
 * lotería de bolas (walk-forward: cada sorteo solo con los anteriores). Con eso la app decide
 * SOLA qué método acierta más, y la "Jugada para el próximo sorteo" es la de ese método.
 *
 * - La primera vez es largo, así que va por tramos de ~8 min (Android corta a los 10):
 *   guarda lo hecho y pide a WorkManager que lo retome (retry). No repite lo ya simulado.
 * - Una vez completo, cada arranque solo comprueba que no falte nada (segundos) y termina.
 *   Si se añade un método nuevo, rehace los simulados a los que les falta.
 */
class SimulacionHistoricaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val gestor = GestorPredicciones.crear(applicationContext)
        val limite = System.currentTimeMillis() + TRAMO_MS
        for (tipo in GestorPredicciones.LOTERIAS_EVALUABLES) {
            val completo = try {
                gestor.simularConLimite(tipo, GestorPredicciones.OBJETIVO_SIMULACION, limite)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Simulación de $tipo fallida: ${e.message}", e)
                false
            }
            if (!completo) {
                android.util.Log.i(TAG, "Tramo terminado en $tipo; se retoma más tarde")
                return@withContext Result.retry()
            }
        }
        android.util.Log.i(TAG, "Simulación histórica completa")
        Result.success()
    }

    companion object {
        private const val TAG = "SimulacionHistorica"
        private const val NOMBRE_TRABAJO = "simulacion_historica"
        private const val TRAMO_MS = 8 * 60 * 1000L

        /**
         * Se llama en cada arranque. KEEP: si ya está en marcha o pendiente, no se duplica;
         * si terminó, se vuelve a lanzar y solo comprueba (rápido) que esté todo.
         */
        fun programar(context: Context) {
            val peticion = OneTimeWorkRequestBuilder<SimulacionHistoricaWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NOMBRE_TRABAJO, ExistingWorkPolicy.KEEP, peticion)
        }
    }
}
