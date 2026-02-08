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
            // Extraer nombre del método de la primera línea de explicación
            val partes = combinacion.explicacion.split("\n", limit = 2)
            val nombreMetodo = partes.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: "Combinación ${indice + 1}"
            val explicacionResto = partes.getOrNull(1)?.trim() ?: ""

            // Encabezado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = nombreMetodo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
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
            
            // Explicación adicional (para todas las loterías)
            if (explicacionResto.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = explicacionResto,
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

// ═══════════════════════════════════════════════════════════════════════════════
// MEJORA 11: TARJETA DE MEJORES NÚMEROS PARA HOY
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Tarjeta que muestra los mejores números para el día de hoy.
 */
@Composable
fun MejoresNumerosHoyCard(
    numerosHoy: List<Pair<Int, Double>>,
    diaSemana: String,
    modifier: Modifier = Modifier
) {
    if (numerosHoy.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mejores números para próximo sorteo ($diaSemana)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Basado en patrones temporales históricos:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                numerosHoy.take(8).forEach { (numero, tendencia) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BolaNumerica(
                            numero = numero,
                            tipo = TipoBola.PRINCIPAL
                        )
                        Text(
                            text = "${String.format("%.0f", tendencia * 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TARJETA DE MEJORES DÍGITOS PARA LOTERÍAS DE 5 DÍGITOS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Tarjeta que muestra los dígitos más frecuentes por posición para Nacional/Navidad/Niño.
 */
@Composable
fun MejoresDigitosHoyCard(
    digitosHoy: List<Pair<Int, Double>>,
    diaSemana: String,
    modifier: Modifier = Modifier
) {
    if (digitosHoy.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎰",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dígitos sugeridos para próximo sorteo ($diaSemana)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Basado en frecuencia histórica por posición:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mostrar los 5 dígitos sugeridos (uno por posición)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val posiciones = listOf("1ª", "2ª", "3ª", "4ª", "5ª")
                digitosHoy.take(5).forEachIndexed { index, (digito, frecuencia) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = posiciones.getOrElse(index) { "" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$digito",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Text(
                            text = "${String.format("%.0f", frecuencia * 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mostrar el número completo sugerido
            val numeroSugerido = digitosHoy.take(5).joinToString("") { it.first.toString() }
            if (numeroSugerido.length == 5) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
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
                        Text(
                            text = "🎯 Número sugerido:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = numeroSugerido,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MEJORA 12: TARJETA DE ALERTA DE COMBINACIÓN RARA
// ═══════════════════════════════════════════════════════════════════════════════
// MEJORA 11B: TARJETA DE PREDICCIÓN DE COMPLEMENTARIO PARA PRÓXIMO SORTEO
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Tarjeta que muestra la predicción de reintegro/estrella/clave para el próximo sorteo.
 */
@Composable
fun PrediccionComplementarioCard(
    tipoComplementario: String, // "reintegro", "estrellas", "clave"
    numero: Int?,
    numeros: List<Int> = emptyList(), // Para estrellas (2)
    diaSorteo: String,
    porcentaje: Double,
    frecuenciaEnDia: Int,
    totalSorteosEnDia: Int,
    racha: String,
    modifier: Modifier = Modifier
) {
    if (numero == null && numeros.isEmpty()) return

    val (emoji, titulo, colorFondo) = when (tipoComplementario) {
        "reintegro" -> Triple("🎲", "Mejor Reintegro", Color(0xFF4CAF50))
        "estrellas" -> Triple("⭐", "Mejores Estrellas", Color(0xFFFFD700))
        "clave" -> Triple("🔑", "Mejor Nº Clave", Color(0xFFFF9800))
        else -> Triple("🎯", "Predicción", Color(0xFF2196F3))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorFondo.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Encabezado
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "$titulo para $diaSorteo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Basado en $totalSorteosEnDia sorteos de $diaSorteo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Número(s) predicho(s)
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (numeros.isNotEmpty()) {
                    // Para estrellas
                    numeros.forEach { num ->
                        BolaNumerica(
                            numero = num,
                            tipo = TipoBola.ESTRELLA
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                } else if (numero != null) {
                    // Para reintegro o clave
                    BolaNumerica(
                        numero = numero,
                        tipo = TipoBola.REINTEGRO
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Estadísticas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${String.format("%.1f", porcentaje)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorFondo
                    )
                    Text(
                        text = "Frecuencia",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$frecuenciaEnDia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorFondo
                    )
                    Text(
                        text = "Apariciones",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = racha,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Estado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MEJORA 12: TARJETA DE ALERTA DE COMBINACIÓN RARA
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Tarjeta de alerta cuando una combinación es estadísticamente rara.
 */
@Composable
fun AlertaRarezaCard(
    esRara: Boolean,
    scoreRareza: Double,
    alertas: List<String>,
    sugerencias: List<Int>?,
    modifier: Modifier = Modifier
) {
    if (!esRara || alertas.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (scoreRareza > 50)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (scoreRareza > 50) "⚠️" else "ℹ️",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (scoreRareza > 50) "Combinación muy rara" else "Combinación poco común",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (scoreRareza > 50)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            alertas.forEach { alerta ->
                Text(
                    text = alerta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scoreRareza > 50)
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                )
            }

            if (!sugerencias.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Considera cambiar por: ${sugerencias.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (scoreRareza > 50)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MEJORA 13: TARJETA DE HISTORIAL DE PREDICCIONES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Tarjeta que muestra el resumen del historial de predicciones.
 */
@Composable
fun HistorialPrediccionesCard(
    totalPredicciones: Int,
    prediccionesEvaluadas: Int,
    promedioAciertos: Double,
    mejorAcierto: Int,
    porcentajeConAciertos: Double,
    onVerHistorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📊",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Historial de Predicciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (totalPredicciones > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EstadisticaMini(
                        valor = "$totalPredicciones",
                        etiqueta = "Total"
                    )
                    EstadisticaMini(
                        valor = "$prediccionesEvaluadas",
                        etiqueta = "Evaluadas"
                    )
                    EstadisticaMini(
                        valor = String.format("%.1f", promedioAciertos),
                        etiqueta = "Prom. Aciertos"
                    )
                    EstadisticaMini(
                        valor = "$mejorAcierto",
                        etiqueta = "Mejor"
                    )
                }

                if (prediccionesEvaluadas > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✅ ${String.format("%.0f", porcentajeConAciertos)}% con al menos 1 acierto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aún no hay predicciones guardadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun EstadisticaMini(
    valor: String,
    etiqueta: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = valor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MEJORA 10: INFO DE PREDICCIÓN INTELIGENTE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Badge que muestra el tipo de predicción inteligente.
 */
@Composable
fun PrediccionInteligenteBadge(
    tipo: String, // "reintegro", "estrellas", "clave"
    score: Double,
    modifier: Modifier = Modifier
) {
    val (emoji, label) = when (tipo) {
        "reintegro" -> "🎲" to "IA"
        "estrellas" -> "⭐" to "IA"
        "clave" -> "🔑" to "IA"
        else -> "🤖" to "IA"
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$emoji$label",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BOTÓN DE LOTERÍA CON PREDICCIÓN
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Botón de lotería con predicción del próximo sorteo mostrada debajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoteriaButtonConPrediccion(
    tipoLoteria: TipoLoteria,
    onClick: () -> Unit,
    numerosPredichos: List<Int>,
    mejorMetodo: String,
    tasaAcierto: Double,
    proximoDia: String,
    complementario: Int? = null,
    complementario2: Int? = null,
    cargando: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Nuevo: último sorteo
    ultimoSorteoNumeros: List<Int> = emptyList(),
    ultimoSorteoFecha: String = "",
    ultimoSorteoComp1: Int? = null,
    ultimoSorteoComp2: Int? = null,
    // Nuevo: mejor método en último sorteo
    metodoMejorAcierto: String = "",
    aciertosDelMejorMetodo: Int = 0,
    numerosAcertados: List<Int> = emptyList()
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

    Column(modifier = modifier.fillMaxWidth()) {
        // Botón principal
        ElevatedCard(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
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
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = tipoLoteria.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tipoLoteria.diasSorteo,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        // Card de predicción debajo
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            if (cargando) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Calculando predicción...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (numerosPredichos.isNotEmpty() || ultimoSorteoNumeros.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // === ÚLTIMO SORTEO ===
                    if (ultimoSorteoNumeros.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏆",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Último ($ultimoSorteoFecha):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            ultimoSorteoNumeros.take(6).forEach { numero ->
                                BolaNumerica(
                                    numero = numero,
                                    tipo = TipoBola.PRINCIPAL,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                            if (ultimoSorteoComp1 != null) {
                                Text("+", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                BolaNumerica(
                                    numero = ultimoSorteoComp1,
                                    tipo = if (tipoLoteria == TipoLoteria.EUROMILLONES) TipoBola.ESTRELLA else TipoBola.REINTEGRO,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (ultimoSorteoComp2 != null) {
                                    BolaNumerica(
                                        numero = ultimoSorteoComp2,
                                        tipo = TipoBola.ESTRELLA,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // === MEJOR MÉTODO EN ÚLTIMO SORTEO ===
                        if (aciertosDelMejorMetodo > 0 && metodoMejorAcierto.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✅",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$metodoMejorAcierto acertó $aciertosDelMejorMetodo:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4CAF50)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                numerosAcertados.forEach { numero ->
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$numero",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // === PREDICCIÓN PRÓXIMO SORTEO ===
                    if (numerosPredichos.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🎯",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Próximo ($proximoDia):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = mejorMetodo,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            numerosPredichos.take(6).forEach { numero ->
                                BolaNumerica(
                                    numero = numero,
                                    tipo = TipoBola.PRINCIPAL,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            if (complementario != null) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                BolaNumerica(
                                    numero = complementario,
                                    tipo = if (tipoLoteria == TipoLoteria.EUROMILLONES) TipoBola.ESTRELLA else TipoBola.REINTEGRO,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (complementario2 != null) {
                                    BolaNumerica(
                                        numero = complementario2,
                                        tipo = TipoBola.ESTRELLA,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Sin predicción disponible
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Toca para ver predicciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
