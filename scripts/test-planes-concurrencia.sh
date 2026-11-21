#!/bin/sh
# Pruebas de Planes & Entitlements que requieren conexiones simultáneas reales.
# Los tests de supabase/tests/ corren en una sola conexión y no pueden cubrirlas.
#
# Uso:  STOREFLOW_SANDBOX_DB_URL=... sh scripts/test-planes-concurrencia.sh
# Solo se conecta a Sandbox. Limpia sus datos al terminar y sale con 1 si algo falla.

set -u

if [ -z "${STOREFLOW_SANDBOX_DB_URL:-}" ]; then
  echo "✖ concurrencia: falta STOREFLOW_SANDBOX_DB_URL" >&2
  exit 1
fi
if ! command -v psql >/dev/null 2>&1; then
  echo "✖ concurrencia: psql no está en el PATH" >&2
  exit 1
fi
case "$STOREFLOW_SANDBOX_DB_URL" in
  *quvkxpjstzssivsaqimu*)
    echo "✖ concurrencia: la URL apunta a Producción. Este script solo corre en Sandbox." >&2
    exit 1
    ;;
esac

dir_tmp=$(mktemp -d) || exit 1
prefijo="TESTCONC-$$-$(date +%s)"
fallos=0

sql() {
  psql "$STOREFLOW_SANDBOX_DB_URL" -v ON_ERROR_STOP=1 -X -q -t -A -c "$1"
}

# Borra las empresas creadas por esta ejecución. Las FK eliminan sus datos en cascada.
limpiar() {
  sql "delete from public.empresas where nombre like '${prefijo}%';" >/dev/null 2>&1 ||
    echo "⚠ concurrencia: no se pudieron limpiar los datos de $prefijo" >&2
  rm -rf "$dir_tmp"
}
trap limpiar EXIT
trap 'exit 1' HUP INT TERM

# Ejecuta N veces la misma sentencia en conexiones simultáneas.
# Deja en $dir_tmp/<i>.out la salida y en <i>.rc el código de cada una.
simultaneo() {
  n=$1
  sentencia=$2
  i=1
  while [ "$i" -le "$n" ]; do
    (
      psql "$STOREFLOW_SANDBOX_DB_URL" -v ON_ERROR_STOP=1 -X -q -t -A -c "$sentencia" \
        >"$dir_tmp/$i.out" 2>&1
      echo $? >"$dir_tmp/$i.rc"
    ) &
    i=$((i + 1))
  done
  wait
}

contar_exitos() {
  cat "$dir_tmp"/*.rc | grep -c '^0$'
}

verificar() {
  descripcion=$1
  esperado=$2
  obtenido=$3
  if [ "$esperado" = "$obtenido" ]; then
    echo "✔ $descripcion"
  else
    echo "✖ $descripcion: esperado $esperado, obtenido $obtenido" >&2
    fallos=$((fallos + 1))
  fi
}

# --- Base: el soporte abre conexiones simultáneas -------------------------------
simultaneo 5 "select 1;"
verificar "5 conexiones simultáneas" 5 "$(contar_exitos)"
rm -f "$dir_tmp"/*.out "$dir_tmp"/*.rc

# Los casos de límites (T063) y Fundador (T094) se agregan aquí.

[ "$fallos" -eq 0 ]
