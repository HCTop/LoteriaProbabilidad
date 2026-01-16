package com.loteria.probabilidad.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

/**
 * Pantalla de login para proteger el acceso a la app.
 * Las credenciales están hasheadas con SHA-256.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLogin(
    onLoginExitoso: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("loteria_auth", Context.MODE_PRIVATE)
    
    // Verificar si ya está autenticado
    LaunchedEffect(Unit) {
        if (prefs.getBoolean("autenticado", false)) {
            onLoginExitoso()
        }
    }
    
    var usuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var mostrarContrasena by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var intentos by remember { mutableStateOf(0) }
    
    // ════════════════════════════════════════════════════════════════
    // CREDENCIALES DE ACCESO
    // Las credenciales están hasheadas con SHA-256 por seguridad.
    // Para cambiarlas, modifica las constantes abajo y recompila.
    // ════════════════════════════════════════════════════════════════
    
    // Credenciales actuales:
    // Usuario: HCTop
    // Contraseña: Loteria2026!
    val USUARIO_HASH = hashSHA256("HCTop")
    val CONTRASENA_HASH = hashSHA256("Loteria2026!")
    
    Scaffold { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Icon(
                imageVector = Icons.Default.Casino,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Lotería Probabilidad",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Acceso restringido",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Campo de usuario
            OutlinedTextField(
                value = usuario,
                onValueChange = { 
                    usuario = it
                    error = null
                },
                label = { Text("Usuario") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = error != null
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Campo de contraseña
            OutlinedTextField(
                value = contrasena,
                onValueChange = { 
                    contrasena = it
                    error = null
                },
                label = { Text("Contraseña") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { mostrarContrasena = !mostrarContrasena }) {
                        Icon(
                            imageVector = if (mostrarContrasena) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (mostrarContrasena) "Ocultar" else "Mostrar"
                        )
                    }
                },
                visualTransformation = if (mostrarContrasena) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = error != null
            )
            
            // Mensaje de error
            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Botón de login
            Button(
                onClick = {
                    val usuarioHash = hashSHA256(usuario)
                    val contrasenaHash = hashSHA256(contrasena)
                    
                    if (usuarioHash == USUARIO_HASH && contrasenaHash == CONTRASENA_HASH) {
                        // Login exitoso - guardar sesión
                        prefs.edit().putBoolean("autenticado", true).apply()
                        onLoginExitoso()
                    } else {
                        intentos++
                        error = when {
                            intentos >= 5 -> "Demasiados intentos. Espera un momento."
                            intentos >= 3 -> "Credenciales incorrectas ($intentos/5)"
                            else -> "Usuario o contraseña incorrectos"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = usuario.isNotBlank() && contrasena.isNotBlank() && intentos < 5
            ) {
                Text("Entrar", style = MaterialTheme.typography.titleMedium)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Información
            Text(
                text = "🔒 Acceso solo para usuarios autorizados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Genera hash SHA-256 de un string.
 */
private fun hashSHA256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Cierra la sesión del usuario.
 */
fun cerrarSesion(context: Context) {
    val prefs = context.getSharedPreferences("loteria_auth", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("autenticado", false).apply()
}
