package com.loteria.probabilidad.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loteria.probabilidad.data.model.AnalisisProbabilidad
import com.loteria.probabilidad.data.model.MetodoCalculo
import com.loteria.probabilidad.data.model.OpcionRangoFechas
import com.loteria.probabilidad.data.model.TipoLoteria
import com.loteria.probabilidad.ui.components.*

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
    metodoSeleccionado: MetodoCalculo,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onCambiarRangoFechas: (OpcionRangoFechas) -> Unit,
    onCambiarMetodo: (MetodoCalculo) -> Unit,
    modifier: Modifier = Modifier
) {
    var mostrarSelectorFechas by remember { mutableStateOf(false) }
    var mostrarSelectorMetodo by remember { mutableStateOf(false) }

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
                    // Botón de método de cálculo
                    IconButton(onClick = { mostrarSelectorMetodo = true }) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Método de cálculo"
                        )
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
                        metodoSeleccionado = metodoSeleccionado
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

    // Diálogo de selección de método de cálculo
    if (mostrarSelectorMetodo) {
        SelectorMetodoDialog(
            metodoActual = metodoSeleccionado,
            onSeleccionar = { metodo ->
                onCambiarMetodo(metodo)
                mostrarSelectorMetodo = false
            },
            onDismiss = { mostrarSelectorMetodo = false }
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
private fun SelectorMetodoDialog(
    metodoActual: MetodoCalculo,
    onSeleccionar: (MetodoCalculo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🧮 Método de cálculo",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                MetodoCalculo.entries.forEach { metodo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSeleccionar(metodo) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (metodo == metodoActual) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = metodo == metodoActual,
                                onClick = { onSeleccionar(metodo) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = metodo.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = metodo.explicacionCorta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
    metodoSeleccionado: MetodoCalculo,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Información del análisis con rango de fechas y método
        item {
            InfoCard(
                analisis = analisis, 
                rangoSeleccionado = rangoSeleccionado,
                metodoSeleccionado = metodoSeleccionado
            )
        }
        
        // Tarjeta del método actual
        item {
            MetodoInfoCard(metodo = metodoSeleccionado)
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
    metodoSeleccionado: MetodoCalculo,
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
                text = "📊 ${metodoSeleccionado.displayName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
private fun MetodoInfoCard(
    metodo: MetodoCalculo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "🧮 ${metodo.explicacionCorta}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = metodo.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
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
