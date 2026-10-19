package com.loteria.probabilidad.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.loteria.probabilidad.data.datasource.ActualizadorHistorico
import com.loteria.probabilidad.domain.usecase.GestorPredicciones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Tarea diaria en segundo plano (WorkManager), aunque no se abra la app:
 *  1. Descarga los CSV nuevos desde GitHub.
 *  2. Evalúa los sorteos pendientes (aciertos de cada método).
 *  3. Genera y GUARDA las predicciones del próximo sorteo de cada lotería de bolas.
 *
 * Así no quedan huecos en el historial los días que no se abre la app.
 * Se programa por la mañana: los sorteos son por la noche y hay margen de sobra.
 * Android decide el momento exacto (puede retrasarla si hay poca batería o no hay red).
 */
class SincronizacionDiariaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext

            // 1. Resultados nuevos (si falla la red se sigue con lo que haya)
            try {
                val actualizador = ActualizadorHistorico(app)
                if (actualizador.necesitaActualizar()) actualizador.actualizar()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Sin actualización de históricos: ${e.message}")
            }

            // 2 y 3. Evaluar pendientes + guardar predicciones del próximo sorteo
            GestorPredicciones.crear(app).sincronizarTodas()

            android.util.Log.i(TAG, "Sincronización diaria completada")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Sincronización diaria fallida: ${e.message}", e)
            // Reintentar más tarde (WorkManager aplica espera exponencial)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "SincronizacionDiaria"
        private const val NOMBRE_TRABAJO = "sincronizacion_diaria_predicciones"

        /** Hora objetivo de la ejecución diaria (por la mañana, antes de los sorteos). */
        private const val HORA_OBJETIVO = 9

        /**
         * Programa la tarea diaria. Se puede llamar en cada arranque: KEEP hace que,
         * si ya está programada, no se duplique ni se reinicie el reloj.
         */
        fun programar(context: Context) {
            val restricciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val peticion = PeriodicWorkRequestBuilder<SincronizacionDiariaWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(minutosHastaProximaEjecucion(), TimeUnit.MINUTES)
                .setConstraints(restricciones)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                NOMBRE_TRABAJO,
                ExistingPeriodicWorkPolicy.KEEP,
                peticion
            )
        }

        /** Minutos que faltan hasta las HORA_OBJETIVO:00 de hoy o, si ya pasó, de mañana. */
        private fun minutosHastaProximaEjecucion(): Long {
            val ahora = LocalDateTime.now()
            var objetivo = ahora.toLocalDate().atTime(HORA_OBJETIVO, 0)
            if (!objetivo.isAfter(ahora)) objetivo = objetivo.plusDays(1)
            return Duration.between(ahora, objetivo).toMinutes()
        }
    }
}
