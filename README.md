

<h1 align="center">StoreFlow</h1>

<p align="center">
  <strong>Sistema de gestión de inventario para PYMEs chilenas</strong><br/>
  Minimarkets · Ferreterías · Distribuidoras · Bodegas
</p>

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=android&logoColor=white" />
  <img alt="Supabase" src="https://img.shields.io/badge/Supabase-PostgreSQL%20%2B%20RLS-3ECF8E?logo=supabase&logoColor=white" />
  <img alt="Play Store" src="https://img.shields.io/badge/Google%20Play-Internal%20Testing-414141?logo=google-play&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/License-Proprietary-red" />
</p>

---



## ¿Qué es StoreFlow?

StoreFlow es un Micro-SaaS de inventario diseñado para el segmento PYME chileno — negocios que hoy gestionan su stock en cuadernos, planillas Excel o sistemas legacy que no se integran con nada.

**Propuesta de valor concreta:**
- Control de stock en tiempo real desde el celular, sin conexión a internet
- Multi-bodega y multi-empresa desde una sola app
- Historial de movimientos auditable (entradas, salidas, ajustes)
- Alertas de quiebre de stock antes de que ocurra
- Sincronización automática cuando vuelve la conexión (offline-first)

**Canal de distribución:** contadores y distribuidores que gestionan múltiples clientes PYME — no venta directa al consumidor final.

---

## 🛠️ Stack Técnico

| Capa | Tecnología |
|------|-----------|
| **UI** | Jetpack Compose + Material 3 |
| **State** | ViewModel + StateFlow + Hilt |
| **Persistencia local** | Room (AppDatabase v8, 11 entidades) |
| **Backend** | Supabase — PostgreSQL + Row Level Security |
| **Auth** | Supabase Auth con JWT custom claims (`empresa_id`) |
| **Sync** | WorkManager — offline-first, last-write-wins |
| **Escaneo** | ML Kit (QR / código de barras) |
| **Crash reporting** | Firebase Crashlytics |
| **Lenguaje** | Kotlin 2.0, JDK 17 |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 35 (Android 15) |

---

## 🏗️ Arquitectura

Clean Architecture en 3 capas con separación estricta:

```
cl.storeflow.warehouse/
├── ui/               # Composables + ViewModels
├── domain/           # Modelos de negocio
├── data/
│   ├── local/        # Room (entities, DAOs, AppDatabase)
│   ├── remote/       # Supabase (repositorios remotos)
│   └── sync/         # WorkManager + SyncEntity queue
├── di/               # Módulos Hilt
└── utils/            # Extensions, helpers
```

### Decisiones de arquitectura clave

**Stock siempre calculado, nunca almacenado:**
```sql
SELECT COALESCE(SUM(quantity), 0)
FROM movimientos
WHERE product_id = :id
```
Los `MovementEntity` son **inmutables** — nunca se editan ni eliminan. El stock es una vista agregada.

**Multi-tenancy vía RLS, no código:**
El `empresa_id` viaja en los JWT custom claims de Supabase. Las políticas de Row Level Security filtran automáticamente. El código Kotlin no filtra por empresa nunca — evita errores de aislamiento de datos.

**Sincronización offline-first:**
Toda operación se escribe primero en Room, se encola en `SyncEntity`, y WorkManager la sube a Supabase en background. Resolución de conflictos: last-write-wins por `updated_at`.

---

## 📊 Modelo de datos

```
Nivel 0  EmpresaEntity          (raíz del tenant)
Nivel 1  UsuarioEntity          FK → empresa
         BodegaEntity           FK → empresa
         ProveedorEntity        FK → empresa
Nivel 2  ProductoEntity         FK → empresa + bodega
Nivel 3  MovementEntity         FK → producto — INMUTABLE
Nivel 4  SyncEntity             cola de sincronización
```

AppDatabase v8 — 11 entidades en total (incluye entidades de soporte para lotes, atributos y sesión).

---

## 🔐 Seguridad

- RLS en todas las tablas de Supabase — un tenant nunca ve datos de otro
- JWT custom claims para propagar `empresa_id` sin filtros manuales
- Crashlytics con stack traces verificados en Firebase Console
- Keystore de release: `storeflow-release.jks` (alias `storeflow-key`) — **no incluido en el repo**

---

## 📦 Módulos en roadmap

| Spec | Feature | Estado |
|------|---------|--------|
| 01–10 | Auth, Productos, Movimientos, Bodegas, Dashboard, Sync, QR, Alertas, Historial | ✅ En `main` |
| 11 | Órdenes de Compra (Proveedores) | 🔄 En spec |
| 12 | Trazabilidad por lote / FEFO | 🔜 Diseñado |
| 13 | Dashboard web — Next.js / Vercel | 🔜 Paralelo |
| 14 | Facturación electrónica DTE / SII | 🔜 Planificado |

---

## 💰 Modelo de negocio

Tres tiers de suscripción: **Básico**, **Profesional**, **Partner** — sin tier gratuito.

Canal principal: **contadores** que gestionan 20–50 clientes PYME y **distribuidores** cuyos clientes también usan StoreFlow (efecto de red bilateral → switching costs altos).

Potencial de subsidio del Estado vía programas **SERCOTEC / CORFO** que cubren hasta el 70% del costo de suscripción para PYMEs elegibles.

---

## 🚀 Estado actual

- ✅ App publicada en Google Play (track: Internal Testing)
- ✅ Specs 01–10 mergeadas a `main`
- ✅ Crashlytics integrado y verificado
- ✅ Web dashboard desplegado en Vercel (mismo Supabase)
- 🔄 `feat/historial-movimientos-android` — en desarrollo
- 🔄 Spec 11 (Órdenes de Compra) — en diseño

---

## ⚙️ Setup local

> Requiere acceso al proyecto Supabase `eygbgykglovbivthyqfb`. Contacta al maintainer.

```bash
# 1. Clonar
git clone https://github.com/Robin-builds/storeflow.git
cd storeflow

# 2. Crear local.properties con credenciales Supabase
echo "SUPABASE_URL=https://<project>.supabase.co" >> local.properties
echo "SUPABASE_ANON_KEY=<tu_anon_key>" >> local.properties

# 3. Compilar
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

Más de 50 unit tests pasando. Cobertura en repositorios, ViewModels y lógica de sync.

---

## 📁 Documentación técnica

| Documento | Contenido |
|-----------|-----------|
| `.harness/CLAUDE.md` | Contexto persistente para sesiones de Claude Code |
| `.harness/ESTADO.md` | Snapshot dinámico del estado por sesión |
| `.harness/TASKS.md` | Tareas atómicas de la sesión activa |
| `.harness/DEFINITION_OF_DONE.md` | DoD por capa — documento canónico de calidad |

---

## 📄 Licencia

Propietaria — todos los derechos reservados. Sin licencia de uso, copia ni distribución sin autorización expresa del autor.

---

<p align="center">
  Desarrollado en Chile 🇨🇱 por <a href="https://github.com/Robin-builds">Robinson Arriagada</a>
</p>
