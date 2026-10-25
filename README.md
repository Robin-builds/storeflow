<h1 align="center">StoreFlow</h1>

<p align="center">
  <strong>Sistema de gestión de inventario para PYMEs chilenas</strong><br/>
  Minimarkets · Ferreterías · Distribuidoras · Bodegas
</p>

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=android&logoColor=white" />
  <img alt="Supabase" src="https://img.shields.io/badge/Supabase-PostgreSQL%20%2B%20RLS-3ECF8E?logo=supabase&logoColor=white" />
  <img alt="Play Store" src="https://img.shields.io/badge/Google%20Play-Closed%20Testing-414141?logo=google-play&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/License-Proprietary-red" />
</p>

---

## ¿Qué es StoreFlow?

StoreFlow es un Micro-SaaS de inventario diseñado para el segmento PYME chileno — negocios que hoy gestionan su stock en cuadernos, planillas Excel o sistemas legacy que no se integran con nada.

**Propuesta de valor concreta:**
- Control de stock en tiempo real desde el celular, sin necesidad de conexión a internet
- Multi-bodega y multi-empresa desde una sola app
- Historial de movimientos auditable (entradas, salidas, ajustes)
- Alertas de quiebre de stock antes de que ocurra
- Trazabilidad por lote con soporte FEFO
- Sincronización automática cuando vuelve la conexión (offline-first)

**Canal de distribución:** contadores y distribuidores que gestionan múltiples clientes PYME — no venta directa al consumidor final.

---

## 🛠️ Stack Técnico

| Capa | Tecnología |
|------|-----------|
| **UI** | Jetpack Compose + Material 3 |
| **State** | ViewModel + StateFlow + Hilt |
| **Persistencia local** | Room (AppDatabase v9, 11 tablas) |
| **Backend** | Supabase — PostgreSQL + Row Level Security |
| **Auth** | Supabase Auth con JWT custom claims (`empresa_id`) |
| **Sync** | WorkManager — offline-first, last-write-wins |
| **Escaneo** | ML Kit (QR / código de barras) |
| **Crash reporting** | Firebase Crashlytics |
| **Lenguaje** | Kotlin 2.0, JDK 11 |
| **Min SDK** | 27 (Android 8.1) |
| **Target SDK** | 36 (Android 16) |

---

## 🏗️ Arquitectura

Clean Architecture en 3 capas con separación estricta:

```
cl.storeflow.warehouse/
├── ui/               # Composables + ViewModels
├── domain/           # Modelos de negocio
├── data/
│   ├── local/        # Room (entities, DAOs, AppDatabase)
│   ├── remote/       # Cliente Supabase
│   ├── repository/   # Repositorios (lógica de negocio)
│   └── sync/         # WorkManager + SyncEntity queue
└── di/               # Módulos Hilt
```

### Decisiones de arquitectura clave

**Stock siempre calculado, nunca almacenado:**
```sql
SELECT COALESCE(SUM(cantidad), 0)
FROM movimientos
WHERE producto_id = :id
```
Los `movimientos` son **inmutables** — nunca se editan ni eliminan. El stock es una vista agregada sobre ENTRADA / SALIDA / AJUSTE.

**Multi-tenancy vía RLS, no código:**
El `empresa_id` viaja en los JWT custom claims de Supabase. Las políticas de Row Level Security filtran automáticamente. El código Kotlin no filtra por empresa nunca — evita errores de aislamiento de datos entre tenants.

**Sincronización offline-first:**
Toda operación se escribe primero en Room, se encola en `sync_queue`, y WorkManager la sube a Supabase en background. Resolución de conflictos: last-write-wins por `updated_at`.

---

## 📊 Modelo de datos

```
empresas              raíz del tenant (RLS)
├── usuarios          FK → empresas
├── bodegas           FK → empresas
├── atributo_templates  FK → empresas
└── productos         FK → empresas + bodegas
    ├── movimientos   FK → productos  — INMUTABLE (ENTRADA/SALIDA/AJUSTE)
    ├── lotes         FK → productos  — trazabilidad FEFO
    └── producto_atributos  FK → productos + atributo_templates

sync_queue            cola de sincronización offline-first
```

RLS habilitado en todas las tablas. Ninguna consulta Kotlin filtra por `empresa_id` explícitamente.

---

## 🔐 Seguridad

- RLS en todas las tablas — un tenant nunca ve datos de otro
- JWT custom claims para propagar `empresa_id` sin filtros manuales en código
- Firebase Crashlytics integrado y verificado (stack traces en consola Firebase)
- Keystore de release **no incluido en el repositorio** (Google Play App Signing activo)

---

## 📦 Módulos en roadmap

| Spec | Feature | Estado |
|------|---------|--------|
| 01–10 | Auth, Productos, Movimientos, Bodegas, Dashboard, Sync, QR, Alertas, Historial | ✅ En `main` |
| 12 | Trazabilidad por lote / FEFO | ✅ En `main` |
| 13 | Dashboard web — Next.js / Vercel (solo lectura, mismo Supabase) | ✅ En producción |
| — | Proveedores | 🔄 En definición del modelo de datos |

---

## 💰 Modelo de negocio

La app se distribuye **gratis** en Google Play. Modelo previsto: **freemium** con suscripciones in-app (Google Play Billing).

Canal principal: **contadores** que gestionan 20–50 clientes PYME y **distribuidores** cuyos clientes también usan StoreFlow (efecto de red bilateral → switching costs altos).

Potencial de subsidio del Estado vía programas **SERCOTEC / CORFO** que cubren hasta el 70% del costo de suscripción para PYMEs elegibles.

---

## 🚀 Estado actual

- 🧪 En **prueba cerrada** de Google Play (versión 1.0.2) — aún no publicada en producción
- ✅ Specs 01–10 y lotes/FEFO mergeados a `main`
- ✅ Historial global de movimientos, cambio/reseteo de contraseña y ayuda contextual
- ✅ Crashlytics integrado y verificado
- ✅ Web dashboard desplegado en Vercel (mismo Supabase)
- 🔄 Proveedores — en definición del modelo de datos

---

## ⚙️ Setup local

> Este proyecto usa Supabase como backend. La URL y la anon key están en `data/remote/SupabaseClient.kt`; para usar tu propio proyecto, reemplázalas ahí.

```bash
# 1. Clonar
git clone https://github.com/Robin-builds/storeflow.git
cd storeflow

# 2. Compilar (en Windows: gradlew.bat)
./gradlew assembleDebug
```

El keystore de release no está incluido en el repositorio.

---

## 🧪 Testing

```bash
# Unit tests (MockK + JUnit)
./gradlew test

# Tests en device
./gradlew connectedAndroidTest
```

55 tests (54 unitarios + 1 instrumentado) sobre modelos de dominio y repositorios.

---

## 📁 Documentación técnica

| Documento | Contenido |
|-----------|-----------|
| `.harness/CLAUDE.md` | Contexto persistente para sesiones de Claude Code |
| `.harness/ESTADO.md` | Snapshot dinámico del estado por sesión |
| `.harness/TASKS.md` | Tareas atómicas de la sesión activa |
| `.harness/PUENTE.md` | Contrato compartido con el dashboard web (schema, RLS, roles) |
| `.harness/MIGRACION_SUPABASE.md` | Registro de la migración entre proyectos Supabase |

---

## 📄 Licencia

Propietaria — todos los derechos reservados. Sin licencia de uso, copia ni distribución sin autorización expresa del autor.

---

<p align="center">
  Desarrollado en Chile 🇨🇱 por <a href="https://github.com/Robin-builds">Robinson Arriagada</a>
</p>
