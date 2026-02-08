package com.loteria.probabilidad.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loteria.probabilidad.data.model.AnalisisProbabilidad
import com.loteria.probabilidad.data.model.OpcionRangoFechas
import com.loteria.probabilidad.data.model.TipoLoteria
import com.loteria.probabilidad.domain.ml.HistorialPredicciones
import com.loteria.probabilidad.domain.ml.MotorInteligencia.PrediccionComplementarioDia
import com.loteria.probabilidad.ui.components.*
import kotlin.math.roundToInt

// Función de extensión para redondear doubles
private fun Double.roundTo(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

/**
 * Estado de la pantalla de resultados.
 */
sealed class ResultadosUiState {
    data object Loading : ResultadosUiState()
    data class Success(val analisis: AnalisisProbabilidad) : ResultadosUiState()
    data class Error(val mensaje: String) : ResultadosUiState()
}

/**
 * Pantalla que muestra los resultados del análisis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaResultados(
    tipoLoteria: TipoLoteria,
    uiState: ResultadosUiState,
    rangoFechasSeleccionado: OpcionRangoFechas,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onCambiarRangoFechas: (OpcionRangoFechas) -> Unit,
    onBacktestClick: () -> Unit = {},
    // MEJORA 11: Mejores números para hoy
    mejoresNumerosHoy: List<Pair<Int, Double>> = emptyList(),
    diaSemanaActual: String = "",
    // MEJORA 13: Estadísticas de predicciones
    estadisticasPredicciones: Map<String, Any> = emptyMap(),
    // MEJORA 11B: Predicción de complementario para próximo sorteo
    prediccionComplementario: PrediccionComplementarioDia? = null,
    prediccionEstrellas: List<PrediccionComplementarioDia> = emptyList(),
    // Historial de evaluaciones y ranking de métodos
    historialEvaluado: List<HistorialPredicciones.EntradaHistorial> = emptyList(),
    rankingMetodos: List<Triple<String, Double, Int>> = emptyList(),
    prediccionesInfo: String = "",
    modifier: Modifier = Modifier
) {
    var mostrarSelectorFechas by remember { mutableStateOf(false) }

    // Backtesting disponible para todas las loterías
    val soportaBacktest = true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = tipoLoteria.displayName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "📅 ${tipoLoteria.diasSorteo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    // Botón de Backtesting (solo si lo soporta)
                    if (soportaBacktest) {
                        IconButton(onClick = onBacktestClick) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = "Backtesting"
                            )
                        }
                    }
                    // Botón de filtro de fechas
                    IconButton(onClick = { mostrarSelectorFechas = true }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Filtrar por fecha"
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is ResultadosUiState.Loading -> {
                    LoadingIndicator(
                        mensaje = "Analizando histórico de ${tipoLoteria.displayName}..."
                    )
                }
                
                is ResultadosUiState.Error -> {
                    ErrorMessage(
                        mensaje = uiState.mensaje,
                        onRetry = onRefresh
                    )
                }
                
                is ResultadosUiState.Success -> {
                    ResultadosContent(
                        analisis = uiState.analisis,
                        tipoLoteria = tipoLoteria,
                        rangoSeleccionado = rangoFechasSeleccionado,
                        mejoresNumerosHoy = mejoresNumerosHoy,
                        diaSemanaActual = diaSemanaActual,
                        estadisticasPredicciones = estadisticasPredicciones,
                        prediccionComplementario = prediccionComplementario,
                        prediccionEstrellas = prediccionEstrellas,
                        historialEvaluado = historialEvaluado,
                        rankingMetodos = rankingMetodos,
                        prediccionesInfo = prediccionesInfo
                    )
                }
            }
        }
    }

    // Diálogo de selección de rango de fechas
    if (mostrarSelectorFechas) {
        SelectorRangoFechasDialog(
            rangoActual = rangoFechasSeleccionado,
            onSeleccionar = { opcion ->
                onCambiarRangoFechas(opcion)
                mostrarSelectorFechas = false
            },
            onDismiss = { mostrarSelectorFechas = false }
        )
    }

}

@Composable
private fun SelectorRangoFechasDialog(
    rangoActual: OpcionRangoFechas,
    onSeleccionar: (OpcionRangoFechas) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "📅 Seleccionar período",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OpcionRangoFechas.entries.forEach { opcion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeleccionar(opcion) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = opcion == rangoActual,
                            onClick = { onSeleccionar(opcion) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = opcion.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun ResultadosContent(
    analisis: AnalisisProbabilidad,
    tipoLoteria: TipoLoteria,
    rangoSeleccionado: OpcionRangoFechas,
    mejoresNumerosHoy: List<Pair<Int, Double>> = emptyList(),
    diaSemanaActual: String = "",
    estadisticasPredicciones: Map<String, Any> = emptyMap(),
    prediccionComplementario: PrediccionComplementarioDia? = null,
    prediccionEstrellas: List<PrediccionComplementarioDia> = emptyList(),
    historialEvaluado: List<HistorialPredicciones.EntradaHistorial> = emptyList(),
    rankingMetodos: List<Triple<String, Double, Int>> = emptyList(),
    prediccionesInfo: String = "",
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Información del análisis con rango de fechas
        item {
            InfoCard(
                analisis = analisis,
                rangoSeleccionado = rangoSeleccionado,
                prediccionesInfo = prediccionesInfo
            )
        }

        // Tarjeta de evaluación y ranking de métodos
        if (historialEvaluado.isNotEmpty()) {
            item {
                EvaluacionCard(
                    historialEvaluado = historialEvaluado,
                    rankingMetodos = rankingMetodos
                )
            }
        }

        // MEJORA 11: Mejores números/dígitos para hoy
        if (mejoresNumerosHoy.isNotEmpty()) {
            item {
                if (tipoLoteria in listOf(TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO)) {
                    // Para loterías de 5 dígitos, mostrar dígitos más frecuentes por posición
                    MejoresDigitosHoyCard(
                        digitosHoy = mejoresNumerosHoy,
                        diaSemana = diaSemanaActual
                    )
                } else {
                    MejoresNumerosHoyCard(
                        numerosHoy = mejoresNumerosHoy,
                        diaSemana = diaSemanaActual
                    )
                }
            }
        }

        // MEJORA 11B: Predicción de complementario para próximo sorteo
        if (prediccionComplementario != null || prediccionEstrellas.isNotEmpty()) {
            item {
                val tipoComp = when (tipoLoteria) {
                    TipoLoteria.PRIMITIVA, TipoLoteria.BONOLOTO -> "reintegro"
                    TipoLoteria.EUROMILLONES -> "estrellas"
                    TipoLoteria.GORDO_PRIMITIVA -> "clave"
                    else -> "reintegro"
                }

                val rachaTexto: (PrediccionComplementarioDia) -> String = { pred ->
                    when (pred.racha.name) {
                        "MUY_CALIENTE" -> "🔥"
                        "CALIENTE" -> "♨️"
                        "MUY_FRIO" -> "🥶"
                        "FRIO" -> "❄️"
                        else -> "➖"
                    }
                }

                if (tipoLoteria == TipoLoteria.EUROMILLONES && prediccionEstrellas.isNotEmpty()) {
                    PrediccionComplementarioCard(
                        tipoComplementario = tipoComp,
                        numero = null,
                        numeros = prediccionEstrellas.map { it.numero },
                        diaSorteo = prediccionEstrellas.first().diaSorteo,
                        porcentaje = prediccionEstrellas.first().porcentaje,
                        frecuenciaEnDia = prediccionEstrellas.sumOf { it.frecuenciaEnDia },
                        totalSorteosEnDia = prediccionEstrellas.first().totalSorteosEnDia,
                        racha = prediccionEstrellas.joinToString(" ") { rachaTexto(it) }
                    )
                } else if (prediccionComplementario != null) {
                    PrediccionComplementarioCard(
                        tipoComplementario = tipoComp,
                        numero = prediccionComplementario.numero,
                        diaSorteo = prediccionComplementario.diaSorteo,
                        porcentaje = prediccionComplementario.porcentaje,
                        frecuenciaEnDia = prediccionComplementario.frecuenciaEnDia,
                        totalSorteosEnDia = prediccionComplementario.totalSorteosEnDia,
                        racha = rachaTexto(prediccionComplementario)
                    )
                }
            }
        }

        // Título de combinaciones
        item {
            Text(
                text = "Combinaciones sugeridas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Lista de combinaciones
        if (analisis.combinacionesSugeridas.isEmpty()) {
            item {
                EmptyStateCard()
            }
        } else {
            itemsIndexed(analisis.combinacionesSugeridas) { index, combinacion ->
                CombinacionCard(
                    combinacion = combinacion,
                    indice = index,
                    tipoLoteria = tipoLoteria
                )
            }
        }

        // Probabilidad teórica (Laplace)
        if (analisis.probabilidadTeorica.isNotEmpty()) {
            item {
                ProbabilidadTeoricaCard(probabilidad = analisis.probabilidadTeorica)
            }
        }

        // MEJORA 13: Historial de predicciones
        if (estadisticasPredicciones.isNotEmpty()) {
            item {
                HistorialPrediccionesCard(
                    totalPredicciones = (estadisticasPredicciones["totalPredicciones"] as? Int) ?: 0,
                    prediccionesEvaluadas = (estadisticasPredicciones["prediccionesEvaluadas"] as? Int) ?: 0,
                    promedioAciertos = (estadisticasPredicciones["promedioAciertos"] as? Double) ?: 0.0,
                    mejorAcierto = (estadisticasPredicciones["mejorAcierto"] as? Int) ?: 0,
                    porcentajeConAciertos = (estadisticasPredicciones["porcentajeConAciertos"] as? Double) ?: 0.0,
                    onVerHistorial = { /* TODO: Navegar al historial completo */ }
                )
            }
        }

        // Estadísticas de números
        item {
            Spacer(modifier = Modifier.height(8.dp))
            EstadisticasCard(analisis = analisis, tipoLoteria = tipoLoteria)
        }

        // Disclaimer final
        item {
            DisclaimerCard()
        }

        // Espacio final
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoCard(
    analisis: AnalisisProbabilidad,
    rangoSeleccionado: OpcionRangoFechas,
    prediccionesInfo: String = "",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📊 Todos los Métodos (1 por cada uno)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (prediccionesInfo.isNotEmpty()) {
                Text(
                    text = prediccionesInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sorteos analizados: ${analisis.totalSorteos}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = "Período: ${rangoSeleccionado.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            // Mostrar fecha del último sorteo (actualización de datos)
            analisis.fechaUltimoSorteo?.let { fecha ->
                Text(
                    text = "📅 Datos hasta: $fecha",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (analisis.fechaDesde != null || analisis.fechaHasta != null) {
                val rangoTexto = buildString {
                    analisis.fechaDesde?.let { append("Desde: $it") }
                    if (analisis.fechaDesde != null && analisis.fechaHasta != null) append(" | ")
                    analisis.fechaHasta?.let { append("Hasta: $it") }
                }
                if (rangoTexto.isNotEmpty()) {
                    Text(
                        text = rangoTexto,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbabilidadTeoricaCard(
    probabilidad: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📐",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Probabilidad teórica (Laplace)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = probabilidad,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun EstadisticasCard(
    analisis: AnalisisProbabilidad,
    tipoLoteria: TipoLoteria,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Estadísticas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Números más frecuentes
            if (analisis.numerosMasFrequentes.isNotEmpty()) {
                val labelNumeros = when (tipoLoteria) {
                    TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO -> 
                        "Terminaciones más frecuentes"
                    else -> "Números más frecuentes"
                }
                
                Text(
                    text = labelNumeros,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    analisis.numerosMasFrequentes.take(5).forEach { estadistica ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BolaNumerica(
                                numero = estadistica.numero,
                                tipo = TipoBola.PRINCIPAL
                            )
                            Text(
                                text = "${estadistica.porcentaje}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Complementarios más frecuentes
            if (analisis.complementariosMasFrequentes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                val labelComplementarios = when (tipoLoteria) {
                    TipoLoteria.EUROMILLONES -> "Estrellas más frecuentes"
                    TipoLoteria.GORDO_PRIMITIVA -> "Números clave más frecuentes"
                    else -> "Reintegros más frecuentes"
                }
                
                Text(
                    text = labelComplementarios,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tipoBola = when (tipoLoteria) {
                        TipoLoteria.EUROMILLONES -> TipoBola.ESTRELLA
                        else -> TipoBola.REINTEGRO
                    }
                    
                    analisis.complementariosMasFrequentes.take(5).forEach { estadistica ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BolaNumerica(
                                numero = estadistica.numero,
                                tipo = tipoBola
                            )
                            Text(
                                text = "${estadistica.porcentaje}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // ==================== ANÁLISIS POR POSICIÓN DE DÍGITOS ====================
            // Solo para loterías de 5 dígitos (Nacional, Navidad, Niño)
            if (analisis.analisisPorPosicion.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "📊 Frecuencia de Dígitos por Posición",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Análisis de cada dígito (0-9) en cada posición del número",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                analisis.analisisPorPosicion.forEach { (posicion, digitos) ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = posicion,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Mostrar los 3 dígitos más frecuentes para esta posición
                            digitos.take(3).forEach { (digito, porcentaje) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$digito",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${porcentaje.roundTo(1)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // Mostrar los 2 dígitos menos frecuentes (fríos)
                            Text(
                                text = "Fríos: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            digitos.takeLast(2).reversed().forEach { (digito, porcentaje) ->
                                Text(
                                    text = "$digito(${porcentaje.roundTo(1)}%) ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6699CC)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Número "óptimo" basado en dígitos más frecuentes
                val numeroOptimo = analisis.analisisPorPosicion.values
                    .mapNotNull { it.firstOrNull()?.first }
                    .joinToString("")
                    
                if (numeroOptimo.length == 5) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "🎯 Número Estadísticamente Óptimo",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Dígito más frecuente en cada posición",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                text = numeroOptimo,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No hay datos históricos disponibles",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ejecuta el script de descarga de datos para obtener el histórico",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EvaluacionCard(
    historialEvaluado: List<HistorialPredicciones.EntradaHistorial>,
    rankingMetodos: List<Triple<String, Double, Int>>,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Rendimiento de Predicciones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Último sorteo evaluado
            val ultimo = historialEvaluado.firstOrNull()
            if (ultimo != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (ultimo.mejorAciertos >= 3)
                            Color(0xFF2E7D32).copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Ultimo sorteo: ${ultimo.fechaSorteo}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Mejor: ${ultimo.mejorMetodo} (${ultimo.mejorAciertos} aciertos)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Mostrar todos los resultados del último sorteo
                        ultimo.evaluaciones
                            .sortedByDescending { it.aciertosNumeros }
                            .take(5)
                            .forEach { eval ->
                                val emoji = when (eval.aciertosNumeros) {
                                    0 -> "  "
                                    1 -> "  "
                                    2 -> "  "
                                    3 -> "  "
                                    else -> "  "
                                }
                                Text(
                                    text = "$emoji${eval.metodo}: ${eval.aciertosNumeros} aciertos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (eval.aciertosNumeros >= 3)
                                        Color(0xFF2E7D32)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                    }
                }
            }

            // Ranking histórico de métodos
            if (rankingMetodos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ranking historico (${historialEvaluado.size} sorteos)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                rankingMetodos.take(5).forEachIndexed { index, (metodo, promedio, mejor) ->
                    val medalla = when (index) {
                        0 -> "1."
                        1 -> "2."
                        2 -> "3."
                        else -> "${index + 1}."
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$medalla $metodo",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            text = "${"%.1f".format(promedio)} media | $mejor max",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

