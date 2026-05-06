package cl.storeflow.warehouse.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun RegistroScreen(
    uiState: AuthUiState,
    onRegistrar: (nombre_empresa: String, rubro: String, correo: String, contrasena: String) -> Unit,
    onIrALogin: () -> Unit,
    onLimpiarError: () -> Unit
) {
    var nombre_empresa by remember { mutableStateOf("") }
    var rubro by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var confirmar_contrasena by remember { mutableStateOf("") }

    val cargando = uiState is AuthUiState.Cargando
    val error = (uiState as? AuthUiState.Error)?.mensaje

    val contrasenaNoCoincide = confirmar_contrasena.isNotEmpty() && contrasena != confirmar_contrasena
    val contrasenaMuyCorta = contrasena.isNotEmpty() && contrasena.length < 8
    val formularioValido = nombre_empresa.isNotBlank() &&
            rubro.isNotBlank() &&
            correo.isNotBlank() &&
            contrasena.length >= 8 &&
            contrasena == confirmar_contrasena

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Registrar empresa",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Crea tu cuenta en StoreFlow",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = nombre_empresa,
            onValueChange = {
                nombre_empresa = it
                if (uiState is AuthUiState.Error) onLimpiarError()
            },
            label = { Text("Nombre de la empresa") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            enabled = !cargando,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = rubro,
            onValueChange = {
                rubro = it
                if (uiState is AuthUiState.Error) onLimpiarError()
            },
            label = { Text("Rubro (ej: Restaurante, Ferretería)") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            enabled = !cargando,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = correo,
            onValueChange = {
                correo = it
                if (uiState is AuthUiState.Error) onLimpiarError()
            },
            label = { Text("Correo electrónico") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            enabled = !cargando,
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = contrasena,
            onValueChange = {
                contrasena = it
                if (uiState is AuthUiState.Error) onLimpiarError()
            },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            enabled = !cargando,
            isError = contrasenaMuyCorta,
            supportingText = if (contrasenaMuyCorta) {
                { Text("Contraseña debe tener mínimo 8 caracteres") }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmar_contrasena,
            onValueChange = { confirmar_contrasena = it },
            label = { Text("Confirmar contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { if (formularioValido) onRegistrar(nombre_empresa, rubro, correo, contrasena) }
            ),
            singleLine = true,
            enabled = !cargando,
            isError = contrasenaNoCoincide,
            supportingText = if (contrasenaNoCoincide) {
                { Text("Las contraseñas no coinciden") }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onRegistrar(nombre_empresa, rubro, correo, contrasena) },
            enabled = formularioValido && !cargando,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (cargando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Crear cuenta")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onIrALogin,
            enabled = !cargando
        ) {
            Text("¿Ya tienes cuenta? Ingresar")
        }
    }
}
