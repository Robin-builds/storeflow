# Feature Specification: Planes & Entitlements v1

**Feature Branch**: sin rama propia todavía (la spec se escribió estando en `fix/deuda-constitucion`, sin commit)

**Created**: 2026-11-21

**Status**: Cerrada

**Input**: Spec "Planes & Entitlements v1" entregada por el dueño. Modo greenfield: la capa de planes no existe hoy. La spec describe el mecanismo de entitlements; los números por tier son configuración.

**Constitución**: v1.2.0. Esta spec toca aislamiento entre empresas, escritura de movimientos, cálculo de stock y datos locales, por lo que cita los principios I, II y III en sus requisitos.

## Problema y usuario

StoreFlow necesita monetizar diferenciando capacidades por plan, pero hoy toda cuenta tiene acceso completo, sin límite ni vencimiento. No hay forma de que una empresa experimente el valor de pago ni de convertir. Sin trial ni enforcement de entitlements no hay motor de conversión ni ingresos.

- **Usuario primario**: el dueño o admin de una PYME (el tenant es la **empresa**), que crea su cuenta, prueba el producto con sus datos reales y decide si paga.
- **Usuario secundario**: el operador de StoreFlow, que necesita que las primeras 100 empresas de pago reciban el beneficio Fundador de forma controlada y sin fugas.

## Clarifications

### Resueltas (conversación de diseño)

- **CL-1**: ¿Trial automático u opt-in? → **Automático** (reverse trial), 30 días.
- **CL-2**: ¿Trial da Standard o Premium? → **Standard**.
- **CL-3**: ¿Entitlement por empresa o usuario? → **Empresa**.
- **CL-4**: ¿Fundador lifetime o acotado? → **Lifetime** mientras mantenga la suscripción; se pierde si cancela y recontrata.
- **CL-5**: ¿Cap Fundador: empresas o usuarios? → **100 empresas**.

### Session 2026-11-21

- **CL-6**: ¿Qué hace perder el estatus Fundador? → Baja voluntaria, paso a Free, o impago sin regularizar en 7 días (FR-015).
- **CL-9**: ¿La gracia offline aplica al vencimiento del trial? → Sí. El cliente conserva Standard hasta 7 días desde su última validación; el servidor sigue aplicando el límite real (FR-024).
- **CL-10**: ¿Qué reciben las cuentas existentes? → Trial de 30 días desde la activación, sin aplicar límites a lo que ya tienen hasta que termine (FR-026).
- **CL-7**: ¿Cómo se registra el paso a Standard pagado sin billing? → Lo registra un operador de StoreFlow con acceso privilegiado, sin pantalla de backoffice, con constancia de quién y cuándo (FR-019).
- **CL-8**: ¿Qué recursos quedan editables tras el downgrade? → La bodega más antigua, que es la Bodega Principal salvo que se haya eliminado, y primero el ADMIN más antiguo (FR-007).
- **CL-11**: ¿Se libera el cupo al perder el estatus? → No, se considera consumido: el cupo cuenta otorgamientos históricos (FR-013).
- Q: ¿Qué debe hacer el servidor con un movimiento registrado sin señal mientras el plan estaba vigente, si llega después de que esa bodega pasó a solo lectura? → A: Se acepta si su fecha es anterior a vencimiento + 7 días y queda marcado como fuera de plan; los posteriores quedan retenidos (FR-028).
- Q: ¿Qué debe pasar cuando una empresa sin la capacidad de lotes registra la entrada de un producto perecedero? → A: La entrada se registra sin lote ni fecha de vencimiento; los lotes nuevos siguen siendo solo de Premium (FR-025).
- Q: ¿Qué puede hacer un usuario que queda fuera del límite de usuarios cuando su empresa baja de plan? → A: No puede iniciar sesión hasta que la empresa vuelva a un plan que lo cubra; sus movimientos con fecha anterior a vencimiento + 7 días se aceptan y quedan marcados, y el resto de su trabajo queda retenido en su dispositivo (FR-029; enmendada tras el análisis, hallazgo C1).
- Q: ¿Qué plan deben recibir las 11 empresas de prueba que hoy existen en Producción cuando se active la feature? → A: Las 11 reciben el trial de 30 días y luego caen a Free, para probar el ciclo real (FR-018, FR-026).
- Q: ¿Por qué medio deben llegarle al dueño los avisos del ciclo de trial en v1? → A: Aviso dentro de la app, visible para los ADMIN al abrirla, hasta que lo cierren (FR-017).
- Q: ¿Cómo ve el ADMIN el trabajo retenido en el dispositivo de otro usuario? → A: Ve en la app cuántos elementos retenidos tiene cada usuario y su última actualización; el detalle permanece en ese dispositivo (FR-023).
- Q: ¿Una empresa que pagó Premium y luego pasa por primera vez a Standard pagado puede ser Fundador? → A: Sí, si hay cupo y nunca tuvo el estatus (FR-012).
- Q: ¿Puede una sesión vencida o un cierre de sesión borrar trabajo sin sincronizar? → A: No; solo el mismo usuario puede retomar esa sesión (FR-030).
- Q: ¿Puede eliminarse en el servidor una bodega que todavía tiene productos? → A: No, porque arrastraría sus movimientos (FR-031).
- Q: ¿Cerrar un aviso del trial cierra también los anteriores? → A: Sí (FR-017).
- Q: ¿Qué sabe la app sobre los planes? → A: Solo que existen tipos de plan. Los planes y sus valores se manejan del lado web y servidor; la app recibe el tipo y los permisos ya resueltos (FR-011).
- Q: ¿Se acepta un producto nuevo creado offline antes de vencer, en una bodega ya en solo lectura? → A: No; queda retenido junto con sus movimientos (FR-028).
- Q: ¿Qué puede decir un aviso sobre cómo recuperar el plan? → A: Solo indicar que se contacte a StoreFlow, sin enlaces, precios ni llamado a comprar (FR-027).
- Q: ¿Un Fundador que pasa a Premium pagado conserva el estatus? → A: Sí (FR-015).

## User Scenarios & Testing *(mandatory)*

**Corte de alcance**: US-1, US-2 y US-3 son el núcleo. US-4 y US-5 agregan conversión, pero el núcleo funciona sin ellas y se pueden recortar.

### User Story 1 - Reverse trial (Priority: P1)

Como dueño de una PYME nueva, al crear mi cuenta tengo acceso completo a Standard por 30 días sin pagar ni dar tarjeta, para evaluar el producto antes de decidir.

**Why this priority**: Es la activación sin fricción y el motor de conversión.

**Independent Test**: Crear una empresa y comprobar su plan efectivo y su fecha de término, sin ningún dato de pago.

**Acceptance Scenarios**:

1. **Given** una empresa creada en t0, **When** se consulta su plan en t0, **Then** es Standard y el trial termina en t0 + 30 días.
2. **Given** un trial que termina en T y sin plan pagado, **When** el instante es T − 1 segundo, **Then** el plan efectivo es Standard.
3. **Given** el mismo trial, **When** el instante es T, **Then** el plan efectivo es Free.
4. **Given** una empresa cuyo trial ya terminó, **When** se intenta iniciar otro, **Then** el término no cambia.

---

### User Story 2 - Downgrade seguro (Priority: P1)

Como dueño cuyo trial venció sin pagar, mis datos se conservan, lo que exceda Free queda en solo lectura y no se borra nada, para no perder mi trabajo y poder recuperar el plan después.

**Why this priority**: Evita la pérdida de clientes por miedo a perder datos y habilita la reconversión.

**Independent Test**: Vencer el trial de una empresa con más bodegas que el límite Free y comparar sus datos y su stock antes y después.

**Acceptance Scenarios**:

1. **Given** una empresa con N bodegas, N mayor que el límite Free, **When** pasa a Free, **Then** sigue teniendo N bodegas.
2. **Given** una bodega en solo lectura, **When** se consulta, **Then** muestra el mismo stock que antes del downgrade.
3. **Given** una bodega en solo lectura, **When** se intenta editar, **Then** se rechaza.
4. **Given** una empresa en Free con recursos en solo lectura, **When** vuelve a un plan que los cubre, **Then** aceptan edición y sus datos no cambiaron.

---

### User Story 3 - Enforcement server-side (Priority: P1)

Como sistema, permito o bloqueo cada capacidad según el plan efectivo de la empresa del lado servidor, para que los límites no se eludan desde un cliente manipulado u offline.

**Why this priority**: Protege la integridad del modelo de negocio.

**Independent Test**: Con una empresa en Free, intentar crear una segunda bodega por acceso directo, sin usar la app.

**Acceptance Scenarios**:

1. **Given** una empresa en Free con 1 bodega, **When** intenta crear otra por cualquier canal, **Then** el servidor la rechaza.
2. **Given** un cliente con un plan cacheado obsoleto, **When** intenta una operación que el plan real no permite, **Then** el servidor la rechaza.
3. **Given** un admin de una empresa, **When** intenta cambiar su propio plan, trial o estatus, **Then** se rechaza y nada cambia.
4. **Given** trabajo registrado offline que el plan no permite, **When** se sincroniza, **Then** queda retenido y visible, sin perderse.

---

### User Story 4 - Plan Fundador (Priority: P2)

Como una de las primeras 100 empresas que paga Standard, recibo acceso Premium de por vida mientras mantenga mi suscripción.

**Why this priority**: Es un incentivo para los primeros clientes, genera testimonios y reduce la pérdida de clientes.

**Independent Test**: Con 99 Fundadores, registrar 10 pagos simultáneos y contar los otorgados.

**Acceptance Scenarios**:

1. **Given** 99 Fundadores, **When** 10 empresas pasan a Standard pagado a la vez, **Then** exactamente 1 recibe el estatus y el total es 100.
2. **Given** el cupo en 100, **When** otra empresa pasa a Standard pagado, **Then** su plan efectivo es Standard.
3. **Given** una empresa Fundador, **When** se da de baja o pasa a Free, **Then** pierde el estatus de inmediato.
4. **Given** una empresa Fundador con su período vencido en V, **When** regulariza antes de V + 7 días, **Then** conserva el estatus.
5. **Given** la misma empresa, **When** llega V + 7 días sin regularizar, **Then** pierde el estatus.
6. **Given** una empresa que perdió el estatus, **When** vuelve a Standard pagado, **Then** no lo recupera.

---

### User Story 5 - Avisos del ciclo de trial (Priority: P2)

Como dueño en trial, recibo avisos oportunos durante el trial, antes de vencer, al caer y después, para entender qué tengo, qué voy a perder y cómo recuperarlo.

**Why this priority**: Llega en los momentos de mayor intención de conversión.

**Independent Test**: Avanzar el reloj por el ciclo del trial y verificar qué eventos de aviso se emiten y en qué momento.

**Acceptance Scenarios**:

1. **Given** una empresa en trial, **When** llega cada momento definido, **Then** se emite su aviso una sola vez.
2. **Given** una empresa que ya pagó, **When** llega un momento de aviso de vencimiento, **Then** no se emite.

### Edge Cases

- **Límite exacto**: el valor igual al límite se acepta y el siguiente se rechaza.
- **Dos dispositivos crean la última bodega a la vez**: solo una se acepta.
- **Empresa sin bodegas activas tras un downgrade**: imposible, porque la más antigua siempre queda activa.
- **Único ADMIN excedente**: imposible, porque el ADMIN más antiguo siempre queda activo.
- **Usuario excedente con sesión abierta**: al confirmarse el plan queda bloqueado, sin que se borre nada de su dispositivo (FR-029).
- **Usuario excedente sin señal**: sigue trabajando localmente durante la gracia; al reconectar, sus movimientos con fecha anterior a vencimiento + 7 días se aceptan y marcan, y el resto queda retenido (FR-023, FR-024, FR-029).
- **Usuario excedente con trabajo retenido en un dispositivo compartido**: la app no ofrece cerrar sesión mientras exista ese trabajo, porque cerrar sesión borra los datos locales (FR-030).
- **Sesión inválida con trabajo pendiente**: los datos se conservan y la app pide volver a ingresar; otra cuenta no puede usar ese dispositivo hasta sincronizar (FR-030).
- **Eliminar por acceso directo una bodega con productos**: se rechaza, y ningún movimiento se pierde (FR-031).
- **Eliminar la única bodega activa con otras en solo lectura**: se rechaza, porque sus productos no tendrían un destino editable.
- **Eliminar una bodega en solo lectura para liberar cupo**: se rechaza (FR-006).
- **Bodega creada offline sobre el límite, con productos y movimientos**: todo queda retenido y visible (FR-023).
- **Producto nuevo creado offline antes de vencer, en una bodega ya en solo lectura**: no se acepta y queda retenido con sus movimientos (FR-028).
- **Salida de un producto perecedero sin la capacidad de lotes**: se sigue registrando y descontando de sus lotes existentes (FR-025).
- **Entrada de un producto perecedero sin la capacidad de lotes**: se registra sin lote ni fecha de vencimiento y suma al stock del producto (FR-025).
- **Historial limitado en Free**: se limita lo visible; el stock sigue sumando todos los movimientos (FR-021).
- **Reloj del dispositivo adelantado o atrasado**: no extiende el trial ni la gracia.
- **Empresa Fundador eliminada**: su otorgamiento sigue contando para el cupo (FR-013).
- **Crear otra empresa para repetir el trial**: el trial es único por empresa; impedir cuentas duplicadas queda fuera de v1.
- **Logout e ingreso en otra empresa**: no queda información de plan de la anterior.
- **Regularización justo al cierre de la gracia Fundador**: en V + 7 días exactos ya no se conserva el estatus (FR-015).
- **Cliente offline validado el día 29 del trial**: conserva Standard localmente hasta el día 36; al reconectar, sus movimientos con fecha anterior a vencimiento + 7 días se aceptan y quedan marcados (FR-028), y el resto de lo que su plan real no permita queda retenido (FR-023, FR-024).
- **Movimiento con fecha exacta de vencimiento + 7 días sobre una bodega en solo lectura**: ya no se acepta y queda retenido (FR-028).
- **Empresa existente con más recursos que Standard durante su trial**: todo lo que ya tenía sigue editable, pero no puede crear más bodegas ni usuarios por sobre el límite (FR-026).

## Requirements *(mandatory)*

### Functional Requirements

**Trial**

- **FR-001**: Al crearse una empresa, el sistema MUST asignarle automáticamente plan efectivo **Standard**, con término de trial igual al instante de creación + 30 días.
- **FR-002**: Mientras el instante actual del servidor sea anterior al término del trial, el plan efectivo MUST ser Standard, salvo plan pagado superior o estatus Fundador vigente.
- **FR-003**: Cuando el instante actual del servidor sea igual o posterior al término del trial y la empresa no tenga plan pagado, el plan efectivo MUST ser **Free**.
- **FR-004**: El trial MUST ser automático y **único** por empresa: no requiere acción ni medio de pago, y no se renueva ni reinicia.

**Downgrade y solo lectura**

- **FR-005**: Al pasar a Free, el sistema MUST NOT eliminar ningún dato de la empresa.
- **FR-006**: Al pasar a Free, los recursos que excedan los límites de Free MUST quedar en **solo lectura** (visibles, no editables ni eliminables).
- **FR-007**: El sistema MUST determinar de forma **determinista** qué recursos quedan activos: bodegas por antigüedad registrada por el servidor; usuarios con el ADMIN más antiguo primero y luego el resto por antigüedad.
- **FR-008**: Si la empresa vuelve a un plan que cubre esos recursos, estos MUST reactivarse sin pérdida ni alteración de datos.

**Enforcement**

- **FR-009**: Toda operación que cree o modifique un recurso sujeto a límite de plan (alta de bodega, alta de usuario, export, etc.) MUST validarse **del lado servidor** contra el plan efectivo vigente. Una operación que excede el plan MUST ser rechazada.
- **FR-010**: El plan efectivo MUST calcularlo el **servidor** como fuente de verdad. El cliente puede cachearlo para la interfaz, pero no es autoritativo.
- **FR-011**: El conjunto de límites por tier (bodegas, usuarios, export, historial, lotes/FEFO, multiempresa) MUST ser **configurable** sin publicar una versión de la app, y aplicarse según el tier efectivo. La app MUST conocer solo el tipo de plan y los permisos resueltos por el servidor, nunca los valores de los límites.

**Fundador**

- **FR-012**: Cuando una empresa pase **por primera vez** a Standard pagado y el cupo no esté agotado, el sistema MUST otorgarle estatus **Fundador**, que eleva su plan efectivo a **Premium**. Un pago previo de Premium no impide el otorgamiento, salvo que la empresa ya haya tenido el estatus.
- **FR-013**: El cupo Fundador MUST ser de **100 empresas**. El otorgamiento 101 y siguientes se rechazan y la empresa queda en Standard pagado. El cupo cuenta otorgamientos históricos, incluidos los de empresas que perdieron el estatus o fueron eliminadas.
- **FR-014**: El conteo del cupo y el otorgamiento MUST ser **atómicos** del lado servidor: nunca más de 100, aun con solicitudes concurrentes.
- **FR-015**: El estatus Fundador MUST mantenerse mientras la suscripción pagada, Standard o Premium, esté activa. La empresa pierde el estatus de forma **permanente** ante: (a) baja voluntaria, (b) paso a Free, o (c) impago no regularizado dentro de `[vencimiento, vencimiento + 7 días)`. Durante esa ventana conserva el estatus y el plan efectivo Premium.
- **FR-016**: Una empresa que perdió el estatus Fundador MUST NOT poder volver a obtenerlo, aunque recontrate.

**Avisos y cuentas especiales**

- **FR-017**: El sistema MUST emitir una sola vez por empresa los eventos de aviso del ciclo de trial en los días 0, −7, −1, término y +2, y MUST NOT emitir los de vencimiento a una empresa con plan pagado. En v1 el único canal es un **aviso dentro de la app**, visible para cada ADMIN al abrirla hasta que lo cierre. Cerrar un aviso cierra también los anteriores. Sin señal, se muestra según el último plan guardado. Push y correo quedan fuera de v1.
- **FR-018**: Una empresa con marca de tester MUST conservar funcionalidad completa indefinida, fuera del flujo de trial y downgrade. Solo el operador de StoreFlow puede asignarla, y ninguna empresa la recibe automáticamente al activarse la feature.

**Protección de datos** *(derivados de la constitución)*

- **FR-019** *(Principio III)*: Ningún usuario de la app, incluido ADMIN, MUST poder crear o modificar plan, trial, estatus Fundador o marca de tester. El paso a Standard pagado lo registra un operador de StoreFlow con acceso privilegiado, y queda constancia de quién y cuándo.
- **FR-020** *(Principio III)*: Un usuario MUST poder consultar solo el plan de su propia empresa, resuelta desde su sesión.
- **FR-021** *(Principio I)*: Ningún límite de plan, incluido el de historial, MUST alterar el stock: siempre es la suma de todos los movimientos. La feature MUST NOT guardar un valor de stock.
- **FR-022** *(Principio II)*: Ningún flujo de esta feature MUST editar o borrar un movimiento. El límite de historial restringe lo visible, no lo almacenado.
- **FR-023** *(Principios I y II)*: El trabajo registrado offline que el servidor rechace por plan MUST quedar retenido en el dispositivo donde se registró, con sus datos dependientes, y MUST NOT descartarse sin importar los reintentos. El ADMIN MUST ver en la app, para su empresa, cuántos elementos retenidos tiene cada usuario y su última actualización. El detalle permanece en ese dispositivo.
- **FR-024**: Si el cliente no puede confirmar el plan con el servidor, MUST mantener el último plan conocido hasta 7 días y luego degradar localmente, sin que cambiar el reloj del dispositivo extienda ese plazo. La gracia MUST aplicar también al vencimiento del trial y se cuenta desde la **última validación** del cliente, en `[última validación, última validación + 7 días)`. No modifica el plan efectivo del servidor.
- **FR-025** *(Principios I y II)*: Una empresa sin la capacidad de lotes/FEFO MUST poder seguir registrando entradas y salidas de productos perecederos. Las entradas se registran sin lote ni fecha de vencimiento, y las salidas descuentan primero de sus lotes existentes y el remanente sin lote. El límite solo impide crear lotes nuevos, que son exclusivos de Premium, salvo la excepción de FR-026.
- **FR-026**: Al entrar en vigencia, cada empresa existente, incluidas las 11 de prueba, MUST recibir un trial de 30 días desde la activación, sin alterar sus datos de negocio. Hasta su término, todo lo que ya tenía MUST seguir editable aunque exceda Standard, y las capacidades que ya usaba, incluidos los lotes, MUST seguir disponibles. Las altas nuevas de bodegas y usuarios por sobre el límite de Standard MUST rechazarse. Al terminar aplica el downgrade normal.
- **FR-027**: La app MUST NOT incluir cobro, precios ni enlaces de compra. Los avisos informan el estado del plan y, para recuperarlo, solo indican que se contacte a StoreFlow, sin enlaces, precios ni llamado a comprar.
- **FR-028** *(Principios I y II)*: Un movimiento sobre una bodega en solo lectura con fecha anterior a `vencimiento + 7 días` MUST aceptarse y quedar marcado como fuera de plan, sin alterar ninguno de sus campos. Con fecha igual o posterior MUST rechazarse y retenerse según FR-023. Esta excepción aplica también a los movimientos de usuarios excedentes (FR-029). Un producto nuevo creado offline en una bodega ya en solo lectura MUST NOT aceptarse: queda retenido junto con sus movimientos (FR-023).
- **FR-029** *(Principios I, II y III)*: Un usuario excedente MUST NOT poder iniciar sesión ni leer o escribir datos de la empresa, y el **servidor** MUST rechazar esos accesos aunque tenga una sesión vigente. Solo puede conocer su propio estado de bloqueo, informar la cantidad de su trabajo retenido y enviar sus movimientos pendientes, sin recibir datos de la empresa. Los movimientos con fecha anterior a `vencimiento + 7 días` MUST aceptarse y quedar marcados (FR-028). El resto de su trabajo MUST quedar retenido en su dispositivo, y el bloqueo MUST NOT borrar datos locales. Al volver a un plan que lo cubra, recupera el acceso y su trabajo retenido se sincroniza.
- **FR-030** *(Principios I y II)*: Ni una sesión vencida o inválida ni un cierre de sesión MUST borrar trabajo sin sincronizar o retenido. Con ese trabajo en el dispositivo, la app MUST NOT ofrecer cerrar sesión, y solo el mismo usuario MUST poder volver a ingresar.
- **FR-031** *(Principio II)*: El servidor MUST rechazar, por cualquier canal, la eliminación de una bodega que todavía tenga productos.

### Key Entities *(include if feature involves data)*

- **Suscripción** (1:1 con Empresa): plan contratado, término del trial, estado de pago y marca de tester.
- **Catálogo de planes** (configuración): por tier, límites de bodegas y usuarios, y capacidades de export, historial, lotes/FEFO y multiempresa.
- **Otorgamiento Fundador**: empresa, ola, fecha de otorgamiento y fecha de pérdida. Es un registro histórico que sobrevive a la pérdida del estatus y permite distinguir olas futuras (NO-2).
- **Registro de cambio de plan**: empresa, cambio, operador de StoreFlow y fecha.
- **Evento de aviso**: empresa, momento del ciclo y fecha de emisión; garantiza la emisión única. Registra además qué ADMIN lo cerró.
- **Marca de movimiento fuera de plan**: referencia al movimiento aceptado por FR-028 y fecha de la marca. Es un registro separado, porque el movimiento es inmutable.
- **Plan cacheado** (local): último tipo de plan confirmado, permisos resueltos por el servidor y fecha de validación. No contiene valores de límites y nunca es fuente de verdad.
- **Trabajo retenido** (local): elemento rechazado por plan, motivo, fecha y dependientes.
- **Resumen de trabajo retenido**: por usuario y dispositivo, cantidad de elementos retenidos y última actualización. No contiene el detalle.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Creada una empresa en t0, su plan efectivo en t0 es Standard y su término de trial es t0 + 30 días. *(Integración sobre el alta)*
- **SC-002**: Con término de trial T y sin plan pagado, el plan efectivo en T − 1 s es Standard y en T es Free. *(Parametrizado con reloj controlado)*
- **SC-003**: Tras el downgrade de una empresa con N bodegas, N mayor que el límite Free, sigue habiendo N bodegas y el stock de cada producto difiere en 0.
- **SC-004**: Tras el downgrade, una edición sobre un recurso en solo lectura es rechazada y una lectura es permitida. *(Permisos)*
- **SC-005**: Una empresa en Free que intenta crear una segunda bodega por acceso directo, sin la app, es rechazada por el servidor. *(Enforcement)*
- **SC-006**: Reactivado un plan que cubre los recursos, los antes en solo lectura vuelven a aceptar edición.
- **SC-007**: Con 99 Fundadores, 10 otorgamientos concurrentes resultan en exactamente 1 nuevo Fundador y 9 rechazos; total 100. *(Concurrencia)*
- **SC-008**: Con el cupo en 100, una empresa que pasa a Standard pagado no recibe Fundador y su plan efectivo es Standard.
- **SC-009**: Una empresa Fundador conserva el estatus si regulariza en V + 7 días − 1 s, lo pierde en V + 7 días o ante baja o paso a Free, y no lo recupera al volver a Standard pagado. *(Parametrizado con reloj controlado)*
- **SC-010** *(entrega: manual)*: Los eventos de aviso se emiten en los momentos definidos (día 0, −7, −1, término, +2). La emisión es automatizable; la visualización dentro de la app para los ADMIN, su cierre y el texto se verifican manualmente en v1.
- **SC-011**: En una matriz de rechazos por plan, 0 elementos de trabajo offline se pierden tras superar el máximo de reintentos.
- **SC-012**: Sin la capacidad de lotes, el 100 % de las entradas y salidas de perecederos queda registrado, 0 lotes nuevos se crean, y el stock por producto y por lote coincide con la suma de sus movimientos.
- **SC-013**: Tras la activación, las empresas existentes tienen 0 diferencias en sus datos de negocio y 0 recursos en solo lectura hasta el término de su trial.
- **SC-014**: Sobre una bodega en solo lectura, un movimiento con fecha en vencimiento + 7 días − 1 s se acepta, queda marcado y conserva sus campos; uno con fecha en vencimiento + 7 días queda retenido. *(Parametrizado)*
- **SC-015**: Un usuario excedente con sesión vigente obtiene 0 filas de datos de la empresa y 0 escrituras aceptadas por acceso directo, salvo sus movimientos dentro de la ventana de FR-028; su trabajo retenido se conserva al 100 % y se sincroniza al volver a un plan que lo cubra.
- **SC-016**: Con trabajo pendiente y una sesión vencida o inválida, el 100 % de ese trabajo sigue en el dispositivo y se sincroniza cuando el mismo usuario vuelve a ingresar.
- **SC-017**: Eliminar por acceso directo una bodega con productos se rechaza y sus movimientos difieren en 0.

## Assumptions

- **SUP-1**: El tenant de entitlement es la **empresa**, no el usuario. *(Decidido)*
- **SUP-2**: Límites vigentes según el doc comercial §3, **a validar** tras el smoke test: Free 1 bodega, 2 usuarios, sin export, historial 90 días. Standard 3–5 bodegas, 5–10 usuarios, export, historial extendido. Premium bodegas y usuarios ilimitados, lotes/FEFO, multiempresa, historial completo. Estos valores se manejan del lado web y servidor, y no llegan a la app.
- **SUP-3**: En v1 **no hay cobro automatizado** (sin DTE propio). El paso a Standard pagado se registra manualmente. La spec gestiona el entitlement, no el cobro. *(Confirmado en CL-7)*
- **SUP-4**: La marca de tester existe para uso futuro del operador de StoreFlow. Las 11 empresas de prueba actuales **no** la reciben: pasan por el trial y el downgrade para probar el ciclo real.
- **SUP-5**: La gracia offline de 7 días aplica en el cliente, incluido el vencimiento del trial (CL-9). En el servidor, la única excepción al plan son los movimientos cubiertos por FR-028.
- **SUP-9**: El export es una capacidad **solo del panel web**. Las acciones de compartir texto de la app no son export y no se limitan por plan. *(Definido por el dueño)*
- **SUP-8**: La fecha de FR-028 proviene del dispositivo. Se acepta el riesgo de que se antedate un movimiento para entrar en la ventana, porque queda marcado y es auditable.
- **SUP-6**: "Premium de por vida" significa plan efectivo Premium mientras la suscripción pagada, Standard o Premium, esté activa. **No** implica gratuidad ni precio bloqueado.
- **SUP-7**: El momento "+2/+3" del aviso posterior al término se fija en **+2** y es configurable.
- **Dato de Producción (2026-11-21)**: de 11 empresas, 3 tienen más de 1 bodega (máximo 9), 2 tienen más de 2 usuarios (máximo 7), 2 tienen productos perecederos y 1 tiene lotes. **Todas son empresas de prueba; hoy no hay clientes reales** (confirmado por el dueño).
- **Dependencias (defectos actuales, sin corregir)**: FR-019, FR-023 y la operación offline requieren resolver antes: (1) abrir la app sin red con la sesión vencida borra los datos locales, incluido lo no sincronizado; (2) la sincronización descarta un elemento tras 3 fallos; (3) si se descarta el traslado de productos y luego se sincroniza la eliminación de la bodega, el servidor borra esos productos y sus movimientos; (4) una cuenta autenticada puede agregarse a cualquier empresa.

## Fuera de alcance (no-objetivos)

- **NO-1**: Cobro, pasarela de pago, emisión de DTE o factura y gestión automatizada de suscripción.
- **NO-2**: **Ola 2 del Fundador** (siguientes 200 empresas con plan anual y beneficio acotado). El modelo debe permitir distinguir olas, pero su lógica no se implementa en v1.
- **NO-3**: Plan anual, descuentos y precios finales.
- **NO-4**: Multiempresa de contador y efecto red distribuidor.
- **NO-5**: Autoservicio de upgrade o downgrade en la app.
- **NO-6**: Prorrateo, reembolsos y cambios de plan a mitad de ciclo.
- **NO-7**: Notificaciones push y correo electrónico para los avisos del ciclo de trial.

## Pendientes con fecha de revisión

Decisiones postergadas por el dueño el 2026-11-21. **Revisión: marzo de 2027.** No bloquean v1 y su riesgo está aceptado hasta esa fecha.

| ID | Pendiente | Comportamiento vigente | Riesgo aceptado |
|---|---|---|---|
| PEND-1 | Cupo de bodegas sin señal: evaluar que la app reciba las bodegas disponibles en vez de un permiso sí/no | La app guarda `crear_bodega` y puede crear offline más bodegas de las que el servidor aceptará | Las bodegas sobrantes quedan retenidas con sus productos y movimientos, sin perderse, y ese stock no llega al servidor hasta que un plan las cubra |
| PEND-2 | Producto nuevo creado offline antes de vencer, en una bodega ya en solo lectura | No se acepta y queda retenido con sus movimientos (FR-028) | Ese stock no llega al servidor hasta que un plan cubra la bodega |

## Trazabilidad (requisito → test)

| FR | Test / SC |
|----|-----------|
| FR-001 | SC-001 |
| FR-002 | SC-002 |
| FR-003 | SC-002 |
| FR-004 | Test: una segunda alta o intento no reinicia ni duplica el trial |
| FR-005 | SC-003 |
| FR-006 | SC-004 |
| FR-007 | Test de regla de selección: con 3 bodegas y límite 1 queda activa la más antigua; el ADMIN más antiguo siempre queda activo |
| FR-008 | SC-006 |
| FR-009 | SC-005 |
| FR-010 | Test: cliente con plan cacheado obsoleto es rechazado por el servidor |
| FR-011 | Test parametrizado por tier + test: la respuesta a la app no contiene valores de límites |
| FR-012 | SC-007 + test: con pago previo de Premium, el primer pago de Standard otorga el estatus si hay cupo |
| FR-013 | SC-008 |
| FR-014 | SC-007 |
| FR-015 | SC-009 + test: un Fundador que pasa a Premium pagado conserva el estatus |
| FR-016 | SC-009 + test de no reotorgamiento |
| FR-017 | SC-010 |
| FR-018 | Test: empresa con marca de tester no cae a Free al vencer el trial; tras la activación, 0 empresas tienen la marca |
| FR-019 | Test: ADMIN y OPERADOR de bodega no pueden modificar plan, trial ni estatus |
| FR-020 | Test de aislamiento: usuario de A no lee el plan de B |
| FR-021 | SC-003 + test: stock igual con historial limitado |
| FR-022 | Test: editar o borrar un movimiento tras un downgrade falla |
| FR-023 | SC-011 + test: el ADMIN ve la cantidad retenida por usuario, sin el detalle |
| FR-024 | Test con reloj controlado: última validación + 7 días − 1 s conserva el plan, + 7 días degrada, y atrasar el reloj no extiende |
| FR-025 | SC-012 |
| FR-026 | SC-013 + test: alta sobre el límite rechazada durante el trial de activación |
| FR-027 | Test: el APK no contiene dependencia de cobro, precios ni enlaces de compra |
| FR-028 | SC-014 + test: un producto nuevo en bodega en solo lectura queda retenido con sus movimientos |
| FR-029 | SC-015 |
| FR-030 | SC-016 |
| FR-031 | SC-017 |

**Regla de trazabilidad**: un test de trazabilidad falla si algún FR queda sin test asociado. Todos los FR tienen un test o criterio asociado.
