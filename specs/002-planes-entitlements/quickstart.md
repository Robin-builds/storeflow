# Quickstart de validación: Planes & Entitlements v1

Guía para comprobar la feature una vez implementada. **Todo se ejecuta primero en Sandbox.**

## Prerrequisitos

- P1–P4 de [plan.md](./plan.md) resueltos.
- Migraciones M1–M6 aplicadas en Sandbox e historial igual al de Producción.
- `psql` instalado y `STOREFLOW_SANDBOX_DB_URL` configurada.
- Build instalado con `gradlew installSandbox` y panel web apuntando a Sandbox.

## 1. Tests automáticos

```bash
gradlew testDebugUnitTest
for f in supabase/tests/*.sql; do psql "$STOREFLOW_SANDBOX_DB_URL" -v ON_ERROR_STOP=1 -f "$f"; done
sh scripts/test-planes-concurrencia.sh
```

**Esperado**: todo en verde, incluidos `aislamiento_empresas.sql` y `movimientos_inmutables.sql`. Cubre SC-001 a SC-009 y SC-011 a SC-015. SC-007 se valida con el script, porque requiere conexiones simultáneas reales.

## 2. Escenarios en dispositivo físico

| # | Escenario | Pasos | Resultado esperado |
|---|---|---|---|
| 1 | Trial (US-1) | Registrar una empresa | Standard, termina en 30 días, sin datos de pago |
| 2 | Límite real (US-3) | En Free, `POST /rest/v1/bodegas` con JWT válido | Rechazo `SFP01` |
| 3 | Plan protegido (US-3) | Como ADMIN, `PATCH /rest/v1/suscripciones` | Error o 0 filas; nada cambia |
| 4 | Token vencido o sesión inválida (FR-030) | Registrar en modo avión, esperar a que venza el token, reabrir. Repetir revocando la sesión en el servidor | Datos intactos; tras revocar, pide ingresar, solo acepta al mismo usuario y sincroniza |
| 5 | Downgrade (US-2) | Vencer un trial con 3 bodegas | Solo la más antigua es editable; todas muestran el mismo stock |
| 6 | Movimiento en ventana (FR-028) | Registrar offline antes de vencer y reconectar después | Aceptado y marcado; stock correcto |
| 7 | Rechazo offline (FR-023) | Crear offline una bodega sobre el límite, con productos y movimientos | Todo sigue en el equipo, retenido y visible para el ADMIN |
| 8 | Perecedero sin lotes (FR-025) | En Standard, registrar entrada y salida | Entrada sin lote; la salida descuenta de lotes existentes |
| 9 | Usuario excedente (FR-029) | Vencer el trial con una sesión abierta de un tercer usuario | Pantalla de bloqueo, sin datos del servidor, sin perder lo local y sin opción de cerrar sesión. Sus movimientos dentro de la ventana entran al stock marcados, y el ADMIN ve la cantidad retenida |
| 10 | Reactivación (US-2) | Registrar un pago que cubra todo y sincronizar | Excedentes editables, usuario desbloqueado, retenidos enviados |
| 11 | Gracia offline | 6 días simulados sin red; luego atrasar el reloj tras vencer | Conserva el plan; atrasar la hora no extiende |
| 12 | Avisos (US-5) | Avanzar el ciclo del trial como ADMIN | Un aviso por momento; cerrarlo cierra también los anteriores y no reaparecen; un OPERADOR de bodega no lo ve |
| 13 | Historial (FR-021) | En Free, con movimientos de más de 90 días | Solo muestra 90 días; el stock no cambia |
| 14 | Export web (FR-009) | En Free, abrir `/api/export/movimientos` | 403; en Standard descarga el CSV |
| 15 | Upgrade de Room | Instalar sobre la versión v9 con datos | 100 % de los datos conservados |
| 16 | Sin compra en el APK (FR-027) | Revisar pantalla de plan y avisos | Sin precios, enlaces ni llamado a comprar |
| 17 | Bodega con productos (FR-031) | `DELETE /rest/v1/bodegas` con productos | Rechazo `SFP06`; productos y movimientos intactos |

## 3. Fundador (US-4)

Ejecutar en Sandbox las operaciones de [contracts/operador.md](./contracts/operador.md):

- Con 99 otorgamientos, 10 pagos concurrentes generan exactamente 1 Fundador.
- Regularizar en V + 7 días − 1 s conserva el estatus; en V + 7 días lo pierde.
- Tras perderlo, un pago nuevo no lo devuelve.

## 4. Antes de Producción

- Pre-request validado en Sandbox: un request normal pasa y uno de excedente responde 403.
- App publicada y dispositivos de prueba actualizados.
- Panel web compatible desplegado.
- Promoción con `scripts/promover-migracion.sh` y confirmación explícita del dueño.
