package cl.stockflow.warehouse.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onLogin: (correo: String, contrasena: String) -> Unit,
    onIrARegistro: () -> Unit,
    onLimpiarError: () -> Unit
) {
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    val focusContrasena = remember { FocusRequester() }

    val cargando = uiState is AuthUiState.Cargando
    val error = (uiState as? AuthUiState.Error)?.mensaje

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Error) {
            correo = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "StockFlow",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ingresa a tu cuenta",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

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
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onNext = { focusContrasena.requestFocus() }
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
                imeAction = ImeAction.Done
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    if (correo.isNotBlank() && contrasena.isNotBlank()) {
                        onLogin(correo, contrasena)
                    }
                }
            ),
            singleLine = true,
            enabled = !cargando,
            isError = error != null,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusContrasena)
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
            onClick = { onLogin(correo, contrasena) },
            enabled = correo.isNotBlank() && contrasena.isNotBlank() && !cargando,
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
                Text("Ingresar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onIrARegistro,
            enabled = !cargando
        ) {
            Text("¿No tienes cuenta? Registrar empresa")
        }
    }
}
