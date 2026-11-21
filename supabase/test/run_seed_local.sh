#!/usr/bin/env bash
# =====================================================================
# SportPro · Prueba LOCAL de supabase/seed_pruebas.sql en un Postgres desechable (Docker).
# No toca el proyecto real de Supabase.
#
# Uso (desde la carpeta del proyecto, en Git Bash, con Docker Desktop abierto):
#     bash supabase/test/run_seed_local.sh
#
# Qué hace:
#   1. Levanta el contenedor «sportpro-seed» con Postgres 17.
#   2. Carga la imitación de Supabase (local_bootstrap.sql) y todas las migraciones.
#   3. Ejecuta el seed DOS veces: la segunda no debe hacer nada (no duplica).
#   4. Ejecuta seed_check.sql: entra como distintos usuarios y comprueba lo que ve cada uno.
#   5. Borra el contenedor (con KEEP=1 lo deja para inspeccionarlo).
# =====================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/../.."

NAME=sportpro-seed
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" -e POSTGRES_PASSWORD=local-only postgres:17 >/dev/null
if [ -z "${KEEP:-}" ]; then trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT; fi

echo "Esperando a Postgres…"
for _ in $(seq 1 60); do
  if docker exec "$NAME" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1; then
    sleep 2
    docker exec "$NAME" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1 && break
  fi
  sleep 1
done

docker exec "$NAME" mkdir -p /work
docker cp supabase/migrations "$NAME":/work/migrations >/dev/null
docker cp supabase/test "$NAME":/work/test >/dev/null
docker cp supabase/seed_pruebas.sql "$NAME":/work/seed_pruebas.sql >/dev/null

run() { docker exec "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -q -f "$1"; }

echo "== Arranque local y migraciones"
run /work/test/local_bootstrap.sql
for f in $(ls supabase/migrations/*.sql | sort); do
  run "/work/migrations/$(basename "$f")" >/dev/null 2>&1 || { echo "Falló la migración $(basename "$f")"; exit 1; }
done

echo "== Seed (primera vez)"
OUT=$(docker exec "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -q -f /work/seed_pruebas.sql 2>&1) || { echo "$OUT" | tail -n 20; echo "EL SEED FALLÓ"; exit 1; }
echo "$OUT" | grep -E "NOTICE:  Datos|ERROR" || true
echo "== Seed (segunda vez: no debe duplicar)"
OUT=$(docker exec "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -q -f /work/seed_pruebas.sql 2>&1) || { echo "$OUT" | tail -n 20; echo "EL SEED FALLÓ"; exit 1; }
echo "$OUT" | grep -E "NOTICE:  Los datos|ERROR" || true

echo "== Comprobaciones"
CHK=$(docker exec "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -f /work/test/seed_check.sql 2>&1) || { echo "$CHK" | tail -n 20; echo "LAS COMPROBACIONES FALLARON"; exit 1; }
echo "$CHK" | grep -E "OK |===" | sed 's/.*NOTICE:  //'
echo "Comprobaciones OK: $(echo "$CHK" | grep -c 'OK ')"
