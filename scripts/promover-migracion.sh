#!/usr/bin/env bash
# ============================================================================
# Promueve UNA migración de Sandbox a Producción — Constitución, sección Ambientes.
#
# Uso:   scripts/promover-migracion.sh supabase/migrations/<version>_<nombre>.sql
#
# Requiere:
#   - psql en el PATH
#   - STOREFLOW_SANDBOX_DB_URL  (connection string de storeflow-sandbox)
#   - STOREFLOW_PROD_DB_URL     (connection string de Producción)
#   Ambas en Supabase → Project Settings → Database → Connection string (URI).
#   No se guardan en el repo.
#
# Qué hace, en orden (se detiene en el primer fallo):
#   1. Verifica que la migración YA esté registrada en Sandbox.
#   2. Verifica que Sandbox y Producción tengan el mismo historial previo (sin deriva).
#   3. Corre supabase/tests/*.sql contra Sandbox.
#   4. Pide confirmación escrita.
#   5. Aplica el archivo SIN EDITAR en Producción, en una transacción, y lo registra en
#      supabase_migrations.schema_migrations con la misma versión.
#   6. Corre supabase/tests/*.sql contra Producción (los tests terminan en ROLLBACK).
# ============================================================================
set -euo pipefail

archivo="${1:-}"
[ -f "$archivo" ] || { echo "✖ Uso: $0 supabase/migrations/<version>_<nombre>.sql" >&2; exit 1; }
command -v psql >/dev/null || { echo "✖ psql no está en el PATH" >&2; exit 1; }
: "${STOREFLOW_SANDBOX_DB_URL:?✖ falta STOREFLOW_SANDBOX_DB_URL}"
: "${STOREFLOW_PROD_DB_URL:?✖ falta STOREFLOW_PROD_DB_URL}"

base=$(basename "$archivo" .sql)
version="${base%%_*}"
nombre="${base#*_}"
raiz=$(git rev-parse --show-toplevel)

sql() { psql "$1" -v ON_ERROR_STOP=1 -X -q -At -c "$2"; }

echo "▶ Migración $version ($nombre)"

# 1. Debe existir en Sandbox
if [ "$(sql "$STOREFLOW_SANDBOX_DB_URL" "select count(*) from supabase_migrations.schema_migrations where version = '$version'")" != "1" ]; then
  echo "✖ La migración $version no está aplicada en Sandbox. Aplícala y valídala ahí primero." >&2
  exit 1
fi
echo "✔ registrada en Sandbox"

# 2. Ya en Producción → nada que hacer
if [ "$(sql "$STOREFLOW_PROD_DB_URL" "select count(*) from supabase_migrations.schema_migrations where version = '$version'")" != "0" ]; then
  echo "✔ ya estaba en Producción. Nada que hacer."
  exit 0
fi

# 2b. Historial previo idéntico (sin deriva)
consulta_hist="select string_agg(version, ',' order by version) from supabase_migrations.schema_migrations where version < '$version'"
hist_sb=$(sql "$STOREFLOW_SANDBOX_DB_URL" "$consulta_hist")
hist_pr=$(sql "$STOREFLOW_PROD_DB_URL" "$consulta_hist")
if [ "$hist_sb" != "$hist_pr" ]; then
  echo "✖ Deriva de historial entre Sandbox y Producción. Iguálalos antes de promover." >&2
  echo "  Sandbox:    $hist_sb" >&2
  echo "  Producción: $hist_pr" >&2
  exit 1
fi
echo "✔ historial previo idéntico"

# 3. Tests en Sandbox
for t in "$raiz"/supabase/tests/*.sql; do
  psql "$STOREFLOW_SANDBOX_DB_URL" -v ON_ERROR_STOP=1 -X -q -f "$t" >/dev/null
  echo "✔ Sandbox: $(basename "$t")"
done

# 4. Confirmación
read -r -p "¿Aplicar $base en PRODUCCIÓN? Escribe 'promover' para continuar: " respuesta
[ "$respuesta" = "promover" ] || { echo "Cancelado."; exit 1; }

# 5. Aplicar + registrar en una sola transacción (-1): todo o nada
psql "$STOREFLOW_PROD_DB_URL" -v ON_ERROR_STOP=1 -X -q -1 \
  -v version="$version" -v nombre="$nombre" -v contenido="$(cat "$archivo")" \
  -f "$archivo" -f - <<'SQL'
insert into supabase_migrations.schema_migrations (version, name, statements)
values (:'version', :'nombre', array[:'contenido']);
SQL
echo "✔ aplicada y registrada en Producción"

# 6. Tests en Producción (cada test termina en ROLLBACK: no deja datos)
for t in "$raiz"/supabase/tests/*.sql; do
  psql "$STOREFLOW_PROD_DB_URL" -v ON_ERROR_STOP=1 -X -q -f "$t" >/dev/null
  echo "✔ Producción: $(basename "$t")"
done
echo "✔ $base promovida."
