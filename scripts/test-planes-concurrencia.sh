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
  # Los otorgamientos no tienen FK y plan_cambios es de solo inserción: se limpian aparte.
  if [ "${fundador_sembrado:-0}" = 1 ]; then
    sql "begin;
         delete from public.fundador_otorgamientos
         where empresa_id in (select md5('${prefijo}' || i)::uuid from generate_series(1, 99) i)
            or empresa_id in (select id from public.empresas where nombre like '${prefijo}%');
         alter table public.plan_cambios disable trigger plan_cambios_solo_insercion;
         delete from public.plan_cambios where operador = '${prefijo}';
         alter table public.plan_cambios enable trigger plan_cambios_solo_insercion;
         commit;" >/dev/null 2>&1 ||
      echo "⚠ concurrencia: no se pudieron limpiar los datos de Fundador de $prefijo" >&2
  fi
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

# --- T063: creaciones simultáneas de la última bodega ---------------------------
# Standard permite 5: con 4 creadas, de 10 altas simultáneas solo 1 puede entrar.
empresa=$(sql "insert into public.empresas (nombre) values ('${prefijo} limite') returning id;" | head -n 1)
sql "insert into public.bodegas (empresa_id, nombre) select '$empresa', 'base ' || i from generate_series(1, 4) i;" >/dev/null

simultaneo 10 "insert into public.bodegas (empresa_id, nombre) values ('$empresa', 'simultanea');"
verificar "última bodega: una sola alta aceptada" 1 "$(contar_exitos)"
verificar "última bodega: rechazos con SFP01" 9 "$(cat "$dir_tmp"/*.out | grep -c 'límite de bodegas')"
verificar "última bodega: nunca supera el límite" 5 \
  "$(sql "select count(*) from public.bodegas where empresa_id = '$empresa';")"
rm -f "$dir_tmp"/*.out "$dir_tmp"/*.rc

# --- T094 / SC-007: con 99 otorgamientos, 10 pagos concurrentes ------------------
previos=$(sql "select count(*) from public.fundador_otorgamientos where ola = 1;")
if [ "$previos" -gt 99 ]; then
  echo "✖ fundador: Sandbox ya tiene $previos otorgamientos; se necesitan 99 o menos" >&2
  fallos=$((fallos + 1))
else
  fundador_sembrado=1
  sql "insert into public.fundador_otorgamientos (empresa_id, ola)
       select md5('${prefijo}' || i)::uuid, 1 from generate_series(1, $((99 - previos))) i;" >/dev/null
  empresas=$(sql "insert into public.empresas (nombre)
                  select '${prefijo} fundador ' || i from generate_series(1, 10) i returning id;")

  i=1
  for empresa_pago in $empresas; do
    (
      psql "$STOREFLOW_SANDBOX_DB_URL" -v ON_ERROR_STOP=1 -X -q -t -A -c \
        "select private.registrar_pago('$empresa_pago', 'STANDARD', now() + interval '30 days', '$prefijo')->>'motivo';" \
        >"$dir_tmp/$i.out" 2>&1
      echo $? >"$dir_tmp/$i.rc"
    ) &
    i=$((i + 1))
  done
  wait

  verificar "fundador: 10 pagos registrados" 10 "$(contar_exitos)"
  verificar "fundador: 1 otorgado" 1 "$(cat "$dir_tmp"/*.out | grep -c '^OTORGADO')"
  verificar "fundador: 9 sin cupo" 9 "$(cat "$dir_tmp"/*.out | grep -c '^CUPO_AGOTADO')"
  verificar "fundador: total 100" 100 \
    "$(sql "select count(*) from public.fundador_otorgamientos where ola = 1;")"
  rm -f "$dir_tmp"/*.out "$dir_tmp"/*.rc
fi

[ "$fallos" -eq 0 ]
