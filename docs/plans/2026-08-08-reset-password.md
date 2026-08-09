# Cambio y Reseteo de Contraseña Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Permitir que cualquier usuario logueado cambie su propia contraseña, y que un ADMIN resetee la contraseña de otro usuario de su misma empresa.

**Architecture:** Dos flujos independientes sobre Supabase Auth (GoTrue), sin tocar schema/RLS de `public`. Auto-servicio es 100% cliente (`gotrue.modifyUser`). Reseteo por ADMIN requiere `service_role`, vía una Edge Function nueva (mismo patrón que `registrar-usuario-empresa`).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, supabase-kt 1.4.6 (gotrue-kt), Ktor client, Deno Edge Function (TypeScript), JUnit4 + mockk + kotlinx-coroutines-test.

**Platform:** Android

**Spec:** `docs/designs/2026-08-08-reset-password-design.md`

**Nota de testing:** los métodos existentes que tocan `HttpClient(Android)` inline o el
singleton `supabaseClient` (`AuthRepository.login`, `.registrar`, `.registrarUsuarioEnEmpresa`)
no tienen unit test en este repo — no son inyectables sin refactor, y ese refactor está
fuera de alcance de esta feature. Los métodos nuevos (`cambiarPassword`,
`resetearPasswordUsuario`) siguen la misma convención: sin unit test propio. Lo que sí es
testeable e importante testear son los ViewModels (dependen de `AuthRepository` inyectado,
mockeable con mockk) — ahí va el esfuerzo de TDD de este plan.

---

### Task 1: `AuthRepository.cambiarPassword` (auto-servicio)

**Files:**
- Modify: `app/src/main/java/cl/storeflow/warehouse/data/repository/AuthRepository.kt:300-305` (agregar método antes del cierre de la clase, después de `obtenerRolActual`)

- [ ] **Step 1: Agregar el método `cambiarPassword`**

En `AuthRepository.kt`, agregar justo antes del `}` de cierre de la clase (después de `obtenerRolActual`):

```kotlin
    suspend fun cambiarPassword(actual: String, nueva: String): Result<Unit> {
        val sesion = authSessionDao.obtenerSesion()
            ?: return Result.failure(Exception("Sin sesión activa"))
        return try {
            Timber.d("AUTH: verificando contraseña actual para cambio")
            supabaseClient.gotrue.loginWith(Email) {
                email = sesion.correo
                password = actual
            }
            Timber.d("AUTH: contraseña actual verificada, aplicando cambio")
            supabaseClient.gotrue.modifyUser {
                password = nueva
            }
            Timber.d("AUTH: contraseña cambiada OK")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "AUTH: error cambiando password")
            val mensaje = if (e.message?.contains("Invalid login credentials") == true)
                "Contraseña actual incorrecta"
            else "Error al cambiar contraseña: ${e.message}"
            Result.failure(Exception(mensaje))
        }
    }
```

No requiere imports nuevos — `gotrue`, `Email`, `Timber` ya están importados en el archivo.

- [ ] **Step 2: Verificar que compila**

Run: `gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cl/storeflow/warehouse/data/repository/AuthRepository.kt
git commit -m "feat: agrega AuthRepository.cambiarPassword para auto-servicio"
```

---

### Task 2: `ConfiguracionViewModel` (nuevo) con TDD

**Files:**
- Create: `app/src/main/java/cl/storeflow/warehouse/ui/configuracion/ConfiguracionViewModel.kt`
- Test: `app/src/test/java/cl/storeflow/warehouse/ui/configuracion/ConfiguracionViewModelTest.kt`

- [ ] **Step 1: Escribir el test (falla porque la clase no existe)**

Crear `app/src/test/java/cl/storeflow/warehouse/ui/configuracion/ConfiguracionViewModelTest.kt`:

```kotlin
package cl.storeflow.warehouse.ui.configuracion

import cl.storeflow.warehouse.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConfiguracionViewModelTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: ConfiguracionViewModel

    @Before
    fun setUp() {
        authRepository = mockk()
        viewModel = ConfiguracionViewModel(authRepository)
    }

    @Test
    fun `estado inicial es Idle`() = runTest {
        assertEquals(ConfiguracionUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `cambiarPassword exitoso emite mensaje y vuelve a Idle`() = runTest {
        coEvery { authRepository.cambiarPassword("actual123", "nueva12345") } returns Result.success(Unit)

        val mensajeDeferred = kotlinx.coroutines.async { viewModel.mensaje.first() }
        viewModel.cambiarPassword("actual123", "nueva12345")

        assertEquals("Contraseña actualizada", mensajeDeferred.await())
        assertEquals(ConfiguracionUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `cambiarPassword fallido emite mensaje de error y vuelve a Idle`() = runTest {
        coEvery { authRepository.cambiarPassword("mala", "nueva12345") } returns
            Result.failure(Exception("Contraseña actual incorrecta"))

        val mensajeDeferred = kotlinx.coroutines.async { viewModel.mensaje.first() }
        viewModel.cambiarPassword("mala", "nueva12345")

        assertEquals("Contraseña actual incorrecta", mensajeDeferred.await())
        assertEquals(ConfiguracionUiState.Idle, viewModel.uiState.value)
    }
}
```

- [ ] **Step 2: Correr el test para confirmar que falla**

Run: `gradlew.bat testDebugUnitTest --tests "cl.storeflow.warehouse.ui.configuracion.ConfiguracionViewModelTest"`
Expected: FAIL (no se resuelve `ConfiguracionViewModel`, la clase no existe)

- [ ] **Step 3: Crear `ConfiguracionViewModel.kt`**

```kotlin
package cl.storeflow.warehouse.ui.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConfiguracionUiState {
    object Idle : ConfiguracionUiState()
    object Operando : ConfiguracionUiState()
}

@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConfiguracionUiState>(ConfiguracionUiState.Idle)
    val uiState: StateFlow<ConfiguracionUiState> = _uiState.asStateFlow()

    private val _mensaje = MutableSharedFlow<String>()
    val mensaje: SharedFlow<String> = _mensaje.asSharedFlow()

    fun cambiarPassword(actual: String, nueva: String) {
        viewModelScope.launch {
            _uiState.value = ConfiguracionUiState.Operando
            authRepository.cambiarPassword(actual, nueva)
                .onSuccess { _mensaje.emit("Contraseña actualizada") }
                .onFailure { _mensaje.emit(it.message ?: "Error al cambiar contraseña") }
            _uiState.value = ConfiguracionUiState.Idle
        }
    }
}
```

- [ ] **Step 4: Correr el test para confirmar que pasa**

Run: `gradlew.bat testDebugUnitTest --tests "cl.storeflow.warehouse.ui.configuracion.ConfiguracionViewModelTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cl/storeflow/warehouse/ui/configuracion/ConfiguracionViewModel.kt app/src/test/java/cl/storeflow/warehouse/ui/configuracion/ConfiguracionViewModelTest.kt
git commit -m "feat: agrega ConfiguracionViewModel para cambio de contraseña"
```

---

### Task 3: UI de auto-servicio en `ConfiguracionScreen`

**Files:**
- Modify: `app/src/main/java/cl/storeflow/warehouse/ui/configuracion/ConfiguracionScreen.kt`

- [ ] **Step 1: Agregar imports necesarios**

Al inicio de `ConfiguracionScreen.kt`, junto a los imports existentes, agregar:

```kotlin
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
```

- [ ] **Step 2: Agregar el parámetro `viewModel` y el snackbar de mensajes**

Reemplazar la firma de la función (líneas 34-42) y las primeras líneas del cuerpo (línea 43):

```kotlin
fun ConfiguracionScreen(
    paletaSeleccionada: PaletaId,
    oscuridadSeleccionada: OscuridadId,
    onSetPaleta: (PaletaId) -> Unit,
    onSetOscuridad: (OscuridadId) -> Unit,
    onVolver: () -> Unit,
    onLogout: () -> Unit,
    onIrAReportarError: () -> Unit,
    viewModel: ConfiguracionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarDialogCambiarPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.mensaje.collect { snackbarHostState.showSnackbar(it) }
    }
```

- [ ] **Step 3: Agregar `snackbarHost` al `Scaffold` y la sección "Cuenta"**

En el `Scaffold` (línea 45), agregar el parámetro `snackbarHost`:

```kotlin
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
```

Justo antes de `// --- Soporte ---` (línea 64), agregar la nueva sección:

```kotlin
            // --- Cuenta ---
            SeccionLabel("Cuenta")
            Card(modifier = Modifier.fillMaxWidth()) {
                ConfigFilaItem(
                    icon = Icons.Filled.Lock,
                    titulo = "Cambiar contraseña",
                    subtitulo = "Actualiza tu contraseña de acceso",
                    onClick = { mostrarDialogCambiarPassword = true }
                )
            }

```

- [ ] **Step 4: Agregar el diálogo al final del `Composable`, antes del `}` de cierre**

Justo antes del `}` final de la función `ConfiguracionScreen` (después del cierre del `Scaffold`), agregar:

```kotlin

    if (mostrarDialogCambiarPassword) {
        DialogCambiarPassword(
            operando = uiState is ConfiguracionUiState.Operando,
            onConfirmar = { actual, nueva ->
                viewModel.cambiarPassword(actual, nueva)
                mostrarDialogCambiarPassword = false
            },
            onCancelar = { mostrarDialogCambiarPassword = false }
        )
    }
```

- [ ] **Step 5: Agregar el composable `DialogCambiarPassword`**

Al final del archivo, después de `OscuridadCard` (después de la línea 312), agregar:

```kotlin

@Composable
private fun DialogCambiarPassword(
    operando: Boolean,
    onConfirmar: (actual: String, nueva: String) -> Unit,
    onCancelar: () -> Unit
) {
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }

    val nuevaValida = nueva.length >= 8
    val coincide = nueva == confirmar
    val puedeConfirmar = actual.isNotBlank() && nuevaValida && coincide && nueva != actual && !operando

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Cambiar contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = actual,
                    onValueChange = { actual = it },
                    label = { Text("Contraseña actual *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nueva,
                    onValueChange = { nueva = it },
                    label = { Text("Nueva contraseña *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = nueva.isNotEmpty() && !nuevaValida,
                    supportingText = {
                        if (nueva.isNotEmpty() && !nuevaValida) Text("Mínimo 8 caracteres")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmar,
                    onValueChange = { confirmar = it },
                    label = { Text("Confirmar nueva contraseña *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = confirmar.isNotEmpty() && !coincide,
                    supportingText = {
                        if (confirmar.isNotEmpty() && !coincide) Text("No coincide")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(actual, nueva) },
                enabled = puedeConfirmar
            ) { Text("Cambiar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 6: Verificar que compila**

Run: `gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/cl/storeflow/warehouse/ui/configuracion/ConfiguracionScreen.kt
git commit -m "feat: agrega dialogo de cambio de contrasena en Configuracion"
```

---

### Task 4: Build de verificación (checkpoint)

- [ ] **Step 1: Compilar el debug APK completo**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Correr toda la suite de unit tests**

Run: `gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures (los 51 tests preexistentes + los 3 nuevos de `ConfiguracionViewModelTest`, salvo el bug preexistente ya documentado en `ProductoAtributosFormTest` que no se toca en este plan)

---

### Task 5: Edge Function `resetear-password-usuario`

**Files:**
- Create: `supabase/functions/resetear-password-usuario/index.ts`

- [ ] **Step 1: Crear la función completa**

```typescript
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      return json({ error: 'No autorizado' }, 401)
    }

    // Cliente con JWT del ADMIN para verificar identidad y rol
    const supabaseUser = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      { global: { headers: { Authorization: authHeader } } }
    )

    const { data: { user }, error: userError } = await supabaseUser.auth.getUser()
    if (userError || !user) {
      return json({ error: 'Token inválido' }, 401)
    }

    // Verificar que el llamante sea ADMIN
    const { data: adminData, error: adminError } = await supabaseUser
      .from('usuarios')
      .select('rol, empresa_id')
      .eq('id', user.id)
      .single()

    if (adminError || !adminData) {
      return json({ error: 'Usuario sin perfil en la empresa' }, 403)
    }
    if (adminData.rol !== 'ADMIN') {
      return json({ error: 'Se requiere rol ADMIN para esta operación' }, 403)
    }

    const empresaId: string = adminData.empresa_id

    const { user_id, password } = await req.json()
    if (!user_id || !password) {
      return json({ error: 'user_id y password son requeridos' }, 400)
    }
    if (password.length < 8) {
      return json({ error: 'La contraseña debe tener mínimo 8 caracteres' }, 400)
    }

    // Cliente con service role para Admin API
    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
      { auth: { autoRefreshToken: false, persistSession: false } }
    )

    // Verificar que el usuario objetivo pertenece a la misma empresa del ADMIN
    const { data: targetData, error: targetError } = await supabaseAdmin
      .from('usuarios')
      .select('empresa_id')
      .eq('id', user_id)
      .single()

    if (targetError || !targetData) {
      return json({ error: 'Usuario objetivo no encontrado' }, 404)
    }
    if (targetData.empresa_id !== empresaId) {
      return json({ error: 'Usuario no pertenece a tu empresa' }, 403)
    }

    const { error: updateError } = await supabaseAdmin.auth.admin.updateUserById(
      user_id,
      { password }
    )

    if (updateError) {
      return json({ error: `Error al restablecer contraseña: ${updateError.message}` }, 500)
    }

    return json({ success: true }, 200)

  } catch (error) {
    return json({ error: `Error interno: ${error.message}` }, 500)
  }
})

function json(body: object, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' }
  })
}
```

- [ ] **Step 2: Commit**

```bash
git add supabase/functions/resetear-password-usuario/index.ts
git commit -m "feat: agrega edge function resetear-password-usuario"
```

(El deploy de esta función al proyecto de Supabase se hace fuera del build de Android — ver Task 8.)

---

### Task 6: `AuthRepository.resetearPasswordUsuario` + `UsuariosViewModel.resetearPassword` con TDD

**Files:**
- Modify: `app/src/main/java/cl/storeflow/warehouse/data/repository/AuthRepository.kt` (agregar método después de `registrarUsuarioEnEmpresa`, antes de `logout`)
- Modify: `app/src/main/java/cl/storeflow/warehouse/ui/usuarios/UsuariosViewModel.kt`
- Test: `app/src/test/java/cl/storeflow/warehouse/ui/usuarios/UsuariosViewModelTest.kt`

- [ ] **Step 1: Escribir el test de `UsuariosViewModel.resetearPassword` (falla, el método no existe)**

Crear `app/src/test/java/cl/storeflow/warehouse/ui/usuarios/UsuariosViewModelTest.kt`:

```kotlin
package cl.storeflow.warehouse.ui.usuarios

import cl.storeflow.warehouse.data.repository.AuthRepository
import cl.storeflow.warehouse.data.repository.UsuarioRepository
import cl.storeflow.warehouse.domain.model.Rol
import cl.storeflow.warehouse.domain.model.Usuario
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UsuariosViewModelTest {

    private lateinit var usuarioRepository: UsuarioRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: UsuariosViewModel

    private val testUsuario = Usuario(
        id = "op-1", nombre = "Operador Uno", email = "op1@empresa.cl",
        rol = Rol.OPERADOR, empresaId = "emp-1"
    )

    @Before
    fun setUp() {
        usuarioRepository = mockk()
        authRepository = mockk()
        coEvery { usuarioRepository.obtenerUsuarioActual() } returns testUsuario
        coEvery { usuarioRepository.observarUsuariosDeEmpresa() } returns flowOf(listOf(testUsuario))
        viewModel = UsuariosViewModel(usuarioRepository, authRepository)
    }

    @Test
    fun `resetearPassword exitoso emite mensaje de confirmacion`() = runTest {
        coEvery { authRepository.resetearPasswordUsuario("op-1", "nueva12345") } returns Result.success(Unit)

        val mensajeDeferred = kotlinx.coroutines.async { viewModel.mensaje.first() }
        viewModel.resetearPassword(testUsuario, "nueva12345")

        assertEquals("Contraseña restablecida", mensajeDeferred.await())
    }

    @Test
    fun `resetearPassword fallido emite mensaje de error`() = runTest {
        coEvery { authRepository.resetearPasswordUsuario("op-1", "nueva12345") } returns
            Result.failure(Exception("Usuario no pertenece a tu empresa"))

        val mensajeDeferred = kotlinx.coroutines.async { viewModel.mensaje.first() }
        viewModel.resetearPassword(testUsuario, "nueva12345")

        assertEquals("Usuario no pertenece a tu empresa", mensajeDeferred.await())
    }
}
```

- [ ] **Step 2: Correr el test para confirmar que falla**

Run: `gradlew.bat testDebugUnitTest --tests "cl.storeflow.warehouse.ui.usuarios.UsuariosViewModelTest"`
Expected: FAIL — `UsuariosViewModel` no tiene constructor con `AuthRepository` como segundo parámetro con ese uso, ni existe `resetearPassword`

- [ ] **Step 3: Agregar `resetearPasswordUsuario` a `AuthRepository.kt`**

Insertar después del método `registrarUsuarioEnEmpresa` (después del cierre `}` de ese método, antes de `suspend fun logout()`):

```kotlin
    suspend fun resetearPasswordUsuario(userId: String, password: String): Result<Unit> {
        val sesion = authSessionDao.obtenerSesion()
            ?: return Result.failure(Exception("Sin sesión activa"))
        val httpClient = HttpClient(Android) { expectSuccess = false }
        return try {
            Timber.d("AUTH: resetearPasswordUsuario userId=$userId")
            val body = buildJsonObject {
                put("user_id", userId)
                put("password", password)
            }.toString()
            val response = httpClient.post("$SUPABASE_URL/functions/v1/resetear-password-usuario") {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer ${sesion.access_token}")
                    append("Content-Type", "application/json")
                }
                setBody(body)
            }
            val responseBody = response.bodyAsText()
            Timber.d("AUTH: resetearPasswordUsuario HTTP ${response.status.value} — $responseBody")
            if (!response.status.isSuccess()) {
                val msg = try {
                    Json.parseToJsonElement(responseBody).jsonObject["error"]?.jsonPrimitive?.content
                        ?: responseBody
                } catch (e: Exception) { responseBody }
                Result.failure(Exception(msg))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "AUTH: error en resetearPasswordUsuario")
            Result.failure(Exception("Error de conexión: ${e.message}"))
        } finally {
            httpClient.close()
        }
    }
```

No requiere imports nuevos (los mismos que usa `registrarUsuarioEnEmpresa` ya cubren `HttpClient`, `Android`, `buildJsonObject`, `put`, `Json`, `jsonObject`, `jsonPrimitive`).

- [ ] **Step 4: Agregar `resetearPassword` a `UsuariosViewModel.kt`**

Modificar el constructor (línea 29-32) para inyectar `AuthRepository` — **ya está inyectado** (`UsuariosViewModel` ya recibe `authRepository: AuthRepository` en el constructor actual, usado en `registrar()`). Solo agregar el método nuevo, después de `cambiarRol` (después de la línea 87, antes del `}` de cierre de la clase):

```kotlin
    fun resetearPassword(usuario: Usuario, nuevaPassword: String) {
        viewModelScope.launch {
            _operando.value = true
            authRepository.resetearPasswordUsuario(usuario.id, nuevaPassword)
                .onSuccess { _mensaje.emit("Contraseña restablecida") }
                .onFailure { _mensaje.emit(it.message ?: "Error al restablecer contraseña") }
            _operando.value = false
        }
    }
```

- [ ] **Step 5: Correr el test para confirmar que pasa**

Run: `gradlew.bat testDebugUnitTest --tests "cl.storeflow.warehouse.ui.usuarios.UsuariosViewModelTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/cl/storeflow/warehouse/data/repository/AuthRepository.kt app/src/main/java/cl/storeflow/warehouse/ui/usuarios/UsuariosViewModel.kt app/src/test/java/cl/storeflow/warehouse/ui/usuarios/UsuariosViewModelTest.kt
git commit -m "feat: agrega reseteo de password por ADMIN en UsuariosViewModel"
```

---

### Task 7: UI de reseteo en `UsuariosScreen`

**Files:**
- Modify: `app/src/main/java/cl/storeflow/warehouse/ui/usuarios/UsuariosScreen.kt`

- [ ] **Step 1: Agregar estado para el diálogo**

Junto a las otras variables de estado (después de la línea 33 `var usuarioCambiarRol by remember...`):

```kotlin
    var usuarioResetearPassword by remember { mutableStateOf<Usuario?>(null) }
```

- [ ] **Step 2: Pasar `puedeResetearPassword` y `onResetearPassword` al `UsuarioItem`**

Reemplazar el bloque del `LazyColumn` (líneas 82-98) por:

```kotlin
                    val adminCount = state.usuarios.count { it.esAdmin() }
                    LazyColumn(contentPadding = padding) {
                        items(state.usuarios, key = { it.id }) { usuario ->
                            val esSelf = usuario.id == state.usuarioActualId
                            val puedeEliminar = !esSelf
                            val puedeCambiarRol = !esSelf && !(usuario.esAdmin() && adminCount <= 1)
                            val puedeResetearPassword = !esSelf
                            Box(modifier = Modifier.animateItem()) {
                                UsuarioItem(
                                    usuario = usuario,
                                    esSelf = esSelf,
                                    puedeEliminar = puedeEliminar,
                                    puedeCambiarRol = puedeCambiarRol,
                                    puedeResetearPassword = puedeResetearPassword,
                                    onEliminar = { usuarioAEliminar = usuario },
                                    onCambiarRol = { usuarioCambiarRol = usuario },
                                    onResetearPassword = { usuarioResetearPassword = usuario }
                                )
                            }
                        }
                    }
```

- [ ] **Step 3: Agregar el diálogo `DialogResetearPassword` a la pantalla**

Después del bloque `usuarioCambiarRol?.let { ... }` (después de la línea 169, antes del `}` de cierre de `UsuariosScreen`):

```kotlin

    usuarioResetearPassword?.let { usuario ->
        DialogResetearPassword(
            usuario = usuario,
            operando = operando,
            onConfirmar = { nuevaPassword ->
                viewModel.resetearPassword(usuario, nuevaPassword)
                usuarioResetearPassword = null
            },
            onCancelar = { usuarioResetearPassword = null }
        )
    }
```

- [ ] **Step 4: Actualizar la firma de `UsuarioItem`**

Reemplazar la firma de `UsuarioItem` (líneas 172-180):

```kotlin
@Composable
private fun UsuarioItem(
    usuario: Usuario,
    esSelf: Boolean,
    puedeEliminar: Boolean,
    puedeCambiarRol: Boolean,
    puedeResetearPassword: Boolean,
    onEliminar: () -> Unit,
    onCambiarRol: () -> Unit,
    onResetearPassword: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }
    val mostrarMenu = puedeEliminar || puedeCambiarRol || puedeResetearPassword
```

- [ ] **Step 5: Agregar la opción al `DropdownMenu`**

Dentro del `DropdownMenu` (después del bloque `if (puedeCambiarRol) { ... }`, líneas 217-222, antes de `if (puedeEliminar) { ... }`):

```kotlin
                            if (puedeResetearPassword) {
                                DropdownMenuItem(
                                    text = { Text("Restablecer contraseña") },
                                    onClick = { expandedMenu = false; onResetearPassword() }
                                )
                            }
```

- [ ] **Step 6: Agregar el composable `DialogResetearPassword`**

Al final del archivo, después de `DialogRegistrarUsuario` (después de la línea 294, antes de `private fun Rol.toDisplayName()`):

```kotlin
@Composable
private fun DialogResetearPassword(
    usuario: Usuario,
    operando: Boolean,
    onConfirmar: (nuevaPassword: String) -> Unit,
    onCancelar: () -> Unit
) {
    var nuevaPassword by remember { mutableStateOf("") }
    val nuevaValida = nuevaPassword.length >= 8

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Restablecer contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Nueva contraseña para ${usuario.nombre.ifBlank { usuario.email }}. " +
                    "Comunícasela al usuario por fuera de la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nuevaPassword,
                    onValueChange = { nuevaPassword = it },
                    label = { Text("Nueva contraseña *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = nuevaPassword.isNotEmpty() && !nuevaValida,
                    supportingText = {
                        if (nuevaPassword.isNotEmpty() && !nuevaValida) Text("Mínimo 8 caracteres")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(nuevaPassword) },
                enabled = nuevaValida && !operando
            ) { Text("Restablecer") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 7: Verificar que compila**

Run: `gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/cl/storeflow/warehouse/ui/usuarios/UsuariosScreen.kt
git commit -m "feat: agrega dialogo de reseteo de contrasena en UsuariosScreen"
```

---

### Task 8: Build final, deploy de la edge function y validación en dispositivo

- [ ] **Step 1: Build completo**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Suite de tests completa**

Run: `gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures nuevos (56 tests: 51 preexistentes + 5 nuevos de esta feature)

- [ ] **Step 3: Desplegar la edge function al proyecto de Supabase**

Esto no es parte del build de Android — correr desde una terminal con Supabase CLI:

```
supabase functions deploy resetear-password-usuario --project-ref quvkxpjstzssivsaqimu
```

O vía Dashboard → Edge Functions, creando la función con el slug exacto
`resetear-password-usuario` (ver gotcha documentado en `.harness/MIGRACION_SUPABASE.md`
sección 5 — el nombre se define al crear y no se puede editar después).

- [ ] **Step 4: Instalar en dispositivo y validación manual**

Run: `gradlew.bat installDebug`

Validar en el dispositivo (regla del proyecto: build exitoso no es suficiente):
1. Login con un usuario existente → Configuración → "Cambiar contraseña" → probar con
   contraseña actual incorrecta (debe mostrar error) y luego con la correcta (debe
   confirmar y permitir re-login con la nueva).
2. Login como ADMIN → Usuarios → menú de un OPERADOR → "Restablecer contraseña" →
   confirmar → cerrar sesión → login con el OPERADOR usando la nueva contraseña.
3. Confirmar que el ícono de opciones (⋮) **no aparece** para la fila del propio ADMIN
   logueado (no debe poder resetearse su propia contraseña desde ahí).

- [ ] **Step 5: Esperar confirmación del usuario antes de considerar la feature terminada**

Por regla del proyecto (`.harness/CLAUDE.md` — "Validación física obligatoria entre
fases"), no dar la feature por completa hasta que el usuario confirme el resultado de
la prueba en dispositivo del Step 4.
