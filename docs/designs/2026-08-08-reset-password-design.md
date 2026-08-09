# Cambio y reseteo de contraseña — Spec

**Fecha:** 2026-08-08
**Rama:** `feat/reset-password`
**Plataforma:** Android (Kotlin, Jetpack Compose, Clean Architecture sin capa UseCase)

## Contexto

Hoy no existe ningún flujo para cambiar o resetear contraseñas. Un ADMIN puede crear
cuentas OPERADOR (`registrar-usuario-empresa` edge function, con la contraseña que el
ADMIN define y le comunica al usuario por fuera de la app), pero no hay forma de:

1. Que un usuario logueado (ADMIN u OPERADOR) cambie su propia contraseña.
2. Que un ADMIN resetee la contraseña de otro usuario de su empresa (olvido, mal manejo).

No se incluye recuperación "olvidé mi contraseña" sin sesión activa (requeriría SMTP y
manejo de deep link de recovery) — fuera de alcance por decisión explícita del usuario.

## Arquitectura

Sigue el patrón existente del repo: `Repository → Result<T>` / `ViewModel → StateFlow<UiState>`
/ `UI → observa`. Sin capa UseCase (regla del proyecto). Todo pasa por Supabase Auth
(GoTrue); no hay cambios de schema ni de políticas RLS — ninguna tabla `public` se toca.

Dos flujos independientes, sin superposición de código:

- **Auto-servicio** (cambiar mi propia contraseña): 100% client-side contra GoTrue.
- **Reseteo por ADMIN** (cambiar la contraseña de otro usuario): requiere `service_role`,
  igual que la creación de usuarios — pasa por una Edge Function nueva.

## Componentes

### 1. Auto-servicio — `ConfiguracionScreen`

**Nuevo `ConfiguracionViewModel`** (hoy `ConfiguracionScreen` no tiene ViewModel propio):

```kotlin
sealed class ConfiguracionUiState {
    object Idle : ConfiguracionUiState()
    object Operando : ConfiguracionUiState()
}

@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    val uiState: StateFlow<ConfiguracionUiState>
    val mensaje: SharedFlow<String>   // snackbar, igual patrón que UsuariosViewModel

    fun cambiarPassword(actual: String, nueva: String)
}
```

**`AuthRepository.cambiarPassword`** (nuevo método):

```kotlin
suspend fun cambiarPassword(actual: String, nueva: String): Result<Unit> {
    val sesion = authSessionDao.obtenerSesion()
        ?: return Result.failure(Exception("Sin sesión activa"))
    return try {
        // Reautenticar para confirmar que "actual" es correcta
        supabaseClient.gotrue.loginWith(Email) {
            email = sesion.correo
            password = actual
        }
        // Cambiar a la nueva
        supabaseClient.gotrue.modifyUser {
            password = nueva
        }
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

API confirmada en `gotrue-kt` 1.4.6 (`GoTrue.kt`): `modifyUser(config: UserUpdateBuilder.() -> Unit)`
opera sobre la sesión actual — no requiere pasar el `user.id`, ni invalida el token
existente (el usuario sigue logueado después del cambio).

**UI — `ConfiguracionScreen.kt`:** nueva sección "Cuenta" (arriba de "Apariencia"), con un
`ConfigFilaItem` "Cambiar contraseña" que abre un `AlertDialog` con 3 `OutlinedTextField`
(`PasswordVisualTransformation`): contraseña actual, nueva, confirmar nueva. Botón
"Cambiar" habilitado solo si: todos los campos no vacíos, nueva.length >= 8, nueva ==
confirmar, nueva != actual. Mismo patrón visual que `DialogRegistrarUsuario` en
`UsuariosScreen.kt`.

### 2. Reseteo por ADMIN — `UsuariosScreen`

**Nueva Edge Function `supabase/functions/resetear-password-usuario/index.ts`** — mismo
esqueleto que `registrar-usuario-empresa/index.ts`:

1. Verifica `Authorization` header, resuelve el usuario caller vía JWT.
2. Consulta `public.usuarios` del caller → debe tener `rol = 'ADMIN'` (403 si no).
3. Lee body `{ user_id, password }`. Valida `password.length >= 8` (400 si no).
4. Consulta `public.usuarios` del `user_id` objetivo → debe existir y su `empresa_id`
   debe ser **igual** al `empresa_id` del caller (403 "Usuario no pertenece a tu
   empresa" si no — chequeo crítico, evita reseteos cross-tenant).
5. Cliente con `service_role`: `supabaseAdmin.auth.admin.updateUserById(user_id) { password }`.
6. Responde `{ success: true }` (200) o `{ error }` (4xx/500), mismo formato que la
   función existente.

**`AuthRepository.resetearPasswordUsuario`** (nuevo método, mismo patrón HTTP que
`registrarUsuarioEnEmpresa` — `HttpClient(Android)` POST directo a
`$SUPABASE_URL/functions/v1/resetear-password-usuario` con headers `apikey` +
`Authorization: Bearer <access_token>`):

```kotlin
suspend fun resetearPasswordUsuario(userId: String, password: String): Result<Unit>
```

**`UsuariosViewModel`** — nuevo método `resetearPassword(usuario: Usuario, nueva: String)`,
reutiliza los `_operando`/`_mensaje` ya existentes (mismo patrón que `eliminar`/`cambiarRol`).

**UI — `UsuariosScreen.kt`:** nueva entrada `"Restablecer contraseña"` en el
`DropdownMenu` de `UsuarioItem`, visible bajo la misma condición que "Eliminar"
(`puedeEliminar = !esSelf` — cualquier ADMIN puede resetear la contraseña de cualquier
otro usuario de su empresa, incluyendo otros ADMIN, pero no la propia — para eso está
el flujo de auto-servicio). Nuevo `AlertDialog` con un solo campo (nueva contraseña),
mismo patrón visual que el diálogo de "Cambiar rol".

## Flujo de datos

```
Auto-servicio:
ConfiguracionScreen → ConfiguracionViewModel.cambiarPassword()
  → AuthRepository.cambiarPassword() → GoTrue (loginWith + modifyUser) → Supabase Auth
  → Result → snackbar

Reseteo por ADMIN:
UsuariosScreen → UsuariosViewModel.resetearPassword()
  → AuthRepository.resetearPasswordUsuario() → HTTP POST edge function
  → resetear-password-usuario (valida ADMIN + misma empresa) → Admin API (service_role)
  → Result → snackbar
```

Ninguno de los dos flujos toca Room ni dispara `SyncWorker`/`PullWorker` — no hay campos
de contraseña en ninguna entidad local.

## Manejo de errores

| Caso | Mensaje mostrado |
|---|---|
| Contraseña actual incorrecta (auto-servicio) | "Contraseña actual incorrecta" |
| Nueva contraseña < 8 caracteres | Validación de UI, botón deshabilitado (no llega a red) |
| Nueva == actual | Validación de UI, botón deshabilitado |
| Sin conexión / error de red | "Error de conexión: {detalle}" |
| Caller no es ADMIN (reseteo) | Edge function 403 → "Se requiere rol ADMIN para esta operación" |
| Usuario objetivo de otra empresa (reseteo) | Edge function 403 → "Usuario no pertenece a tu empresa" |
| Éxito | Snackbar "Contraseña actualizada" / "Contraseña restablecida" |

Todos los mensajes de error de la edge function siguen el mismo formato JSON
`{ error: string }` que ya usa `registrar-usuario-empresa`, y `AuthRepository` los
parsea igual que en `registrarUsuarioEnEmpresa` (reutiliza el mismo bloque de parseo).

## Testing

Unit tests (JUnit4 + mockk, siguiendo convención del repo — `*Test.kt` junto a las
clases existentes):

- `AuthRepositoryTest` (nuevo o extendido): `cambiarPassword` — éxito, contraseña actual
  incorrecta, sin sesión activa, error de red.
- `UsuarioRepositoryTest` o `AuthRepositoryTest`: `resetearPasswordUsuario` — éxito, 403
  no-ADMIN, 403 cross-tenant, error de red. (Puede testearse a nivel de parseo de
  respuesta HTTP mockeada, igual que patrón usado para `registrarUsuarioEnEmpresa` si
  existe test previo — si no existe, replicar el mismo approach.)
- `UsuariosViewModelTest`: `resetearPassword` actualiza `operando`/`mensaje` en éxito y
  error.
- UI: validaciones de habilitado/deshabilitado del botón en ambos diálogos (longitud
  mínima, coincidencia de confirmación) — test instrumentado opcional, no bloqueante
  para el MVP dado que el resto del repo tiene solo 1 test instrumentado.

No se requieren tests de Edge Function en Deno dentro de este repo (no hay precedente
para `registrar-usuario-empresa` tampoco).

## Diferencias de plataforma

N/A — proyecto solo Android.

## Fuera de alcance (explícito)

- Recuperación de contraseña sin sesión activa ("olvidé mi contraseña" vía email/OTP).
- Forzar cierre de sesión en otros dispositivos al cambiar contraseña.
- Historial/auditoría de cambios de contraseña.
- Política de complejidad de contraseña más allá del mínimo de 8 caracteres ya usado
  en `registrar-usuario-empresa`.
