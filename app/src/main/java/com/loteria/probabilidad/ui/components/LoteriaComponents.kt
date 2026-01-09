package com.loteria.probabilidad.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loteria.probabilidad.data.model.CombinacionSugerida
import com.loteria.probabilidad.data.model.TipoLoteria

/**
 * Botón de lotería con gradiente y diseño atractivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoteriaButton(
    tipoLoteria: TipoLoteria,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val (icon, gradientColors) = when (tipoLoteria) {
        TipoLoteria.PRIMITIVA -> Icons.Default.Casino to listOf(Color(0xFF1976D2), Color(0xFF42A5F5))
        TipoLoteria.BONOLOTO -> Icons.Default.Loyalty to listOf(Color(0xFF388E3C), Color(0xFF66BB6A))
        TipoLoteria.EUROMILLONES -> Icons.Default.Euro to listOf(Color(0xFFD4AF37), Color(0xFFFFE082))
        TipoLoteria.GORDO_PRIMITIVA -> Icons.Default.EmojiEvents to listOf(Color(0xFFF57C00), Color(0xFFFFB74D))
        TipoLoteria.LOTERIA_NACIONAL -> Icons.Default.ConfirmationNumber to listOf(Color(0xFF7B1FA2), Color(0xFFBA68C8))
        TipoLoteria.NAVIDAD -> Icons.Default.Celebration to listOf(Color(0xFFC62828), Color(0xFFEF5350))
        TipoLoteria.NINO -> Icons.Default.ChildCare to listOf(Color(0xFF00796B), Color(0xFF4DB6AC))
    }

    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(95.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = tipoLoteria.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = tipoLoteria.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tipoLoteria.diasSorteo,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bola de lotería animada.
 */
@Composable
fun BolaNumerica(
    numero: Int,
    tipo: TipoBola = TipoBola.PRINCIPAL,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, borderColor) = when (tipo) {
        TipoBola.PRINCIPAL -> Triple(
            Color(0xFFD4AF37),
            Color.White,
            Color(0xFFB8860B)
        )
        TipoBola.COMPLEMENTARIO -> Triple(
            Color(0xFF7B1FA2),
            Color.White,
            Color(0xFF4A148C)
        )
        TipoBola.ESTRELLA -> Triple(
            Color(0xFF1976D2),
            Color.White,
            Color(0xFF0D47A1)
        )
        TipoBola.REINTEGRO -> Triple(
            Color(0xFF388E3C),
            Color.White,
            Color(0xFF1B5E20)
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(if (tipo == TipoBola.PRINCIPAL) 48.dp else 40.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        backgroundColor,
                        borderColor
                    )
                )
            )
            .border(
                width = 2.dp,
                color = borderColor,
                shape = CircleShape
            )
    ) {
        Text(
            text = numero.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

enum class TipoBola {
    PRINCIPAL,
    COMPLEMENTARIO,
    ESTRELLA,
    REINTEGRO
}

/**
 * Tarjeta de combinación sugerida.
 */
@Composable
fun CombinacionCard(
    combinacion: CombinacionSugerida,
    indice: Int,
    tipoLoteria: TipoLoteria,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Encabezado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Combinación ${indice + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Badge de probabilidad relativa (formateado a 2 decimales máximo)
                val probabilidadFormateada = if (combinacion.probabilidadRelativa < 0.01) {
                    String.format("%.4f", combinacion.probabilidadRelativa)
                } else {
                    String.format("%.2f", combinacion.probabilidadRelativa)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$probabilidadFormateada%",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Números principales
            Text(
                text = when (tipoLoteria) {
                    TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO -> "Número:"
                    else -> "Números:"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Mostrar números según tipo de lotería
            when (tipoLoteria) {
                TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO -> {
                    // Mostrar número de 5 cifras
                    Text(
                        text = combinacion.numeros.firstOrNull()?.toString()?.padStart(5, '0') ?: "00000",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    // Mostrar bolas numéricas
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        combinacion.numeros.forEach { numero ->
                            BolaNumerica(
                                numero = numero,
                                tipo = TipoBola.PRINCIPAL
                            )
                        }
                    }
                }
            }
            
            // Complementarios (estrellas, reintegro, etc.)
            if (combinacion.complementarios.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                val labelComplementario = when (tipoLoteria) {
                    TipoLoteria.EUROMILLONES -> "Estrellas:"
                    TipoLoteria.GORDO_PRIMITIVA -> "Número Clave:"
                    else -> "Reintegro:"
                }
                
                Text(
                    text = labelComplementario,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tipoBola = when (tipoLoteria) {
                        TipoLoteria.EUROMILLONES -> TipoBola.ESTRELLA
                        else -> TipoBola.REINTEGRO
                    }
                    
                    combinacion.complementarios.forEach { numero ->
                        BolaNumerica(
                            numero = numero,
                            tipo = tipoBola
                        )
                    }
                }
            }
            
            // Explicación adicional
            if (combinacion.explicacion.isNotEmpty() && 
                tipoLoteria in listOf(TipoLoteria.LOTERIA_NACIONAL, TipoLoteria.NAVIDAD, TipoLoteria.NINO)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = combinacion.explicacion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Indicador de carga.
 */
@Composable
fun LoadingIndicator(
    mensaje: String = "Analizando histórico...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Mensaje de error.
 */
@Composable
fun ErrorMessage(
    mensaje: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

/**
 * Disclaimer legal.
 */
@Composable
fun DisclaimerCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Los sorteos de lotería son eventos aleatorios. Este análisis se basa en frecuencias históricas y NO garantiza resultados futuros. Juega con responsabilidad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
