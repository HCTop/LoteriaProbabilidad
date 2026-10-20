package com.loteria.probabilidad.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.loteria.probabilidad.data.model.TipoLoteria

/**
 * Identidad visual de cada lotería (un sitio para todo: antes estaba copiada en varios archivos).
 * El color se usa como acento (icono, franja), no de fondo de tarjetas enteras.
 */
fun colorLoteria(tipo: TipoLoteria): Color = when (tipo) {
    TipoLoteria.PRIMITIVA -> Color(0xFF2F6FDE)
    TipoLoteria.BONOLOTO -> Color(0xFF2E9D57)
    TipoLoteria.EUROMILLONES -> Color(0xFFC99A1E)
    TipoLoteria.GORDO_PRIMITIVA -> Color(0xFFE5772E)
    TipoLoteria.LOTERIA_NACIONAL -> Color(0xFF8E44C2)
    TipoLoteria.NAVIDAD -> Color(0xFFD13C3C)
    TipoLoteria.NINO -> Color(0xFF14918A)
}

fun iconoLoteria(tipo: TipoLoteria): ImageVector = when (tipo) {
    TipoLoteria.PRIMITIVA -> Icons.Default.Casino
    TipoLoteria.BONOLOTO -> Icons.Default.Loyalty
    TipoLoteria.EUROMILLONES -> Icons.Default.Euro
    TipoLoteria.GORDO_PRIMITIVA -> Icons.Default.EmojiEvents
    TipoLoteria.LOTERIA_NACIONAL -> Icons.Default.ConfirmationNumber
    TipoLoteria.NAVIDAD -> Icons.Default.Celebration
    TipoLoteria.NINO -> Icons.Default.ChildCare
}

/**
 * Tarjeta estándar de la app (Material 3): esquinas grandes, fondo por tono y borde fino,
 * sin sombras pesadas. El recorte va ANTES del modifier para que el efecto al tocar
 * (clickable) respete las esquinas redondeadas.
 */
@androidx.compose.runtime.Composable
fun TarjetaApp(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val forma = androidx.compose.material3.MaterialTheme.shapes.large
    androidx.compose.material3.Card(
        modifier = androidx.compose.ui.Modifier
            .clip(forma)
            .then(modifier),
        shape = forma,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        content = content
    )
}
