package com.loteria.probabilidad.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loteria.probabilidad.data.model.TipoLoteria
import com.loteria.probabilidad.ui.components.DisclaimerCard
import com.loteria.probabilidad.ui.components.LoteriaButton

/**
 * Pantalla principal con la selección de loterías.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaSeleccionLoteria(
    onLoteriaSeleccionada: (TipoLoteria) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lotería Probabilidad",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Título y descripción
            Text(
                text = "Selecciona una lotería",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Obtén las 5 combinaciones más probables basadas en el análisis histórico",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Disclaimer
            DisclaimerCard()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sección: Loterías de números
            Text(
                text = "Loterías de números",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.PRIMITIVA,
                onClick = { onLoteriaSeleccionada(TipoLoteria.PRIMITIVA) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.BONOLOTO,
                onClick = { onLoteriaSeleccionada(TipoLoteria.BONOLOTO) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.EUROMILLONES,
                onClick = { onLoteriaSeleccionada(TipoLoteria.EUROMILLONES) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.GORDO_PRIMITIVA,
                onClick = { onLoteriaSeleccionada(TipoLoteria.GORDO_PRIMITIVA) }
            )
            
            // Sección: Sorteos especiales
            Text(
                text = "Sorteos especiales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.LOTERIA_NACIONAL,
                onClick = { onLoteriaSeleccionada(TipoLoteria.LOTERIA_NACIONAL) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.NAVIDAD,
                onClick = { onLoteriaSeleccionada(TipoLoteria.NAVIDAD) }
            )
            
            LoteriaButton(
                tipoLoteria = TipoLoteria.NINO,
                onClick = { onLoteriaSeleccionada(TipoLoteria.NINO) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
