# Contrato: operaciones del operador de StoreFlow

Funciones en el esquema `private`, no expuesto por la API. `EXECUTE` revocado a `public`, `anon` y `authenticated`. Se ejecutan por acceso privilegiado a la base (FR-019). Cada una exige `p_operador` no vacío y escribe en `plan_cambios` dentro de la misma transacción.

| Función | Efecto | Requisitos |
|---|---|---|
| `private.registrar_pago(p_empresa, p_plan, p_hasta, p_operador)` | Registra o renueva el período. Si es el primer pago Standard y hay cupo, otorga Fundador | FR-012 a FR-014 |
| `private.registrar_baja(p_empresa, p_operador)` | Termina el período, fija `cobertura_fin = ahora` y hace perder Fundador con motivo `BAJA` | FR-015 |
| `private.pasar_a_free(p_empresa, p_operador)` | Quita el plan pagado, fija `cobertura_fin = ahora` y hace perder Fundador con motivo `PASO_A_FREE` | FR-015 |
| `private.marcar_tester(p_empresa, p_valor, p_operador)` | Asigna o quita la marca de tester | FR-018 |

## Reglas de `registrar_pago`

1. Bloquea la suscripción.
2. Si existe un Fundador vigente con `ahora >= pagado_hasta + 7 días`, fija `perdido_at` con motivo `IMPAGO` **antes** de renovar.
3. Actualiza el período, limpia `cobertura_fin` y, si el plan es `STANDARD`, fija `primer_pago_standard_at` si era nulo.
4. Si este es el primer pago de `STANDARD` y la empresa no tiene otorgamiento, aunque antes haya pagado Premium: bloquea la fila de la ola, cuenta y otorga si hay cupo.
5. Devuelve `{ "fundador": true | false, "motivo": "OTORGADO" | "CUPO_AGOTADO" | "NO_APLICA" }`.

Con cupo agotado el pago se registra igual: la empresa queda en Standard pagado (FR-013). Un Fundador que pasa a Premium pagado conserva el estatus (FR-015).

## Precisiones de la implementación (M5)

- La pérdida por `IMPAGO` se fecha en `pagado_hasta + 7 días`, aunque se registre después (R9).
- En baja y paso a Free, `cobertura_fin` es `ahora`, salvo que quede un trial en curso (entonces su término) o que la cobertura ya haya terminado (entonces ese vencimiento).
- `obtener_entitlement` informa `PAGADO` durante la gracia de un Fundador, sin revelar el estatus (FR-027).
