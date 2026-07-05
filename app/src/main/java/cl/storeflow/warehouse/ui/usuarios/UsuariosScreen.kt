package cl.storeflow.warehouse.ui.usuarios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import cl.storeflow.warehouse.ui.components.BackButton
import cl.storeflow.warehouse.ui.theme.StoreFlowTheme
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.domain.model.Rol
import cl.storeflow.warehouse.domain.model.Usuario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuariosScreen(
    onVolver: () -> Unit,
    viewModel: UsuariosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val operando by viewModel.operando.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarDialogRegistrar by remember { mutableStateOf(false) }
    var usuarioAEliminar by remember { mutableStateOf<Usuario?>(null) }
    var usuarioCambiarRol by remember { mutableStateOf<Usuario?>(null) }
    val primario = StoreFlowTheme.coloresExtendidos.paleta.primario

    LaunchedEffect(Unit) {
        viewModel.mensaje.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuarios") },
                navigationIcon = { BackButton(onClick = onVolver) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!operando) mostrarDialogRegistrar = true },
                containerColor = primario,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Registrar usuario")
            }
        }
    ) { padding ->
        when (val state = uiState) {
            is UsuariosUiState.Cargando -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UsuariosUiState.Listo -> {
                if (state.usuarios.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No hay usuarios registrados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val adminCount = state.usuarios.count { it.esAdmin() }
                    LazyColumn(contentPadding = padding) {
                        items(state.usuarios, key = { it.id }) { usuario ->
                            val esSelf = usuario.id == state.usuarioActualId
                            val puedeEliminar = !esSelf
                            val puedeCambiarRol = !esSelf && !(usuario.esAdmin() && adminCount <= 1)
                            Box(modifier = Modifier.animateItem()) {
                                UsuarioItem(
                                    usuario = usuario,
                                    esSelf = esSelf,
                                    puedeEliminar = puedeEliminar,
                                    puedeCambiarRol = puedeCambiarRol,
                                    onEliminar = { usuarioAEliminar = usuario },
                                    onCambiarRol = { usuarioCambiarRol = usuario }
                                )
                            }
                        }
                    }
                }
            }
            is UsuariosUiState.Error -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.mensaje, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (mostrarDialogRegistrar) {
        DialogRegistrarUsuario(
            operando = operando,
            onConfirmar = { email, password, nombre ->
                viewModel.registrar(email, password, nombre)
                mostrarDialogRegistrar = false
            },
            onCancelar = { mostrarDialogRegistrar = false }
        )
    }

    usuarioAEliminar?.let { usuario ->
        AlertDialog(
            onDismissRequest = { usuarioAEliminar = null },
            title = { Text("Eliminar usuario") },
            text = {
                Text("¿Eliminar a ${usuario.nombre.ifBlank { usuario.email }}? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.eliminar(usuario)
                        usuarioAEliminar = null
                    },
                    enabled = !operando
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { usuarioAEliminar = null }) { Text("Cancelar") }
            }
        )
    }

    usuarioCambiarRol?.let { usuario ->
        val nuevoRol = if (usuario.esAdmin()) Rol.OPERADOR else Rol.ADMIN
        AlertDialog(
            onDismissRequest = { usuarioCambiarRol = null },
            title = { Text("Cambiar rol") },
            text = {
                Text(
                    "¿Cambiar el rol de ${usuario.nombre.ifBlank { usuario.email }} " +
                    "de ${usuario.rol.toDisplayName()} a ${nuevoRol.toDisplayName()}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cambiarRol(usuario, nuevoRol)
                        usuarioCambiarRol = null
                    },
                    enabled = !operando
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { usuarioCambiarRol = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun UsuarioItem(
    usuario: Usuario,
    esSelf: Boolean,
    puedeEliminar: Boolean,
    puedeCambiarRol: Boolean,
    onEliminar: () -> Unit,
    onCambiarRol: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }
    val mostrarMenu = puedeEliminar || puedeCambiarRol

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(usuario.nombre.ifBlank { "(sin nombre)" })
                if (esSelf) {
                    Spacer(Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Tú", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        },
        supportingContent = { Text(usuario.email, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = usuario.rol.toDisplayName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (usuario.esAdmin())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mostrarMenu) {
                    Box {
                        IconButton(onClick = { expandedMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                        }
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false }
                        ) {
                            if (puedeCambiarRol) {
                                DropdownMenuItem(
                                    text = { Text("Cambiar rol") },
                                    onClick = { expandedMenu = false; onCambiarRol() }
                                )
                            }
                            if (puedeEliminar) {
                                DropdownMenuItem(
                                    text = {
                                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = { expandedMenu = false; onEliminar() }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
    HorizontalDivider()
}

@Composable
private fun DialogRegistrarUsuario(
    operando: Boolean,
    onConfirmar: (email: String, password: String, nombre: String) -> Unit,
    onCancelar: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Registrar usuario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "El usuario será registrado como Operador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(email.trim(), password, nombre.trim()) },
                enabled = nombre.isNotBlank() && email.isNotBlank() && password.isNotBlank() && !operando
            ) { Text("Registrar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}

private fun Rol.toDisplayName() = when (this) {
    Rol.ADMIN -> "Administrador"
    Rol.OPERADOR -> "Operador"
}
