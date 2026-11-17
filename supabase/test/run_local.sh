#!/usr/bin/env bash
# =====================================================================
# SportPro · Corre TODAS las migraciones y la prueba de humo en un Postgres
# desechable dentro de Docker. No toca el proyecto real de Supabase.
#
# Requisito: Docker Desktop abierto.
# Uso (desde la carpeta del proyecto, en Git Bash):
#     bash supabase/test/run_local.sh
#
# Qué hace:
#   1. Levanta un contenedor «sportpro-test» con Postgres 17 (si existía, lo recrea).
#   2. Carga local_bootstrap.sql (imitación mínima de auth/storage/roles de Supabase).
#   3. Ejecuta cada migración en orden de nombre (igual que en el SQL Editor).
#   4. Ejecuta cada migración OTRA VEZ, salvo la 001, para comprobar que son idempotentes.
#   5. Ejecuta security_audit.sql: revisa en el catálogo que todas las tablas tengan RLS,
#      que el rol anónimo no tenga permisos de más y que nada sensible se escriba directo.
#   6. Ejecuta smoke_test.sql y cuenta los «OK».
#   7. Borra el contenedor (con KEEP=1 lo deja para inspeccionarlo).
# =====================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1            # evita que Git Bash cambie las rutas /tmp/... al pasar a Docker
cd "$(dirname "$0")/../.."

NAME=sportpro-test
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" -e POSTGRES_PASSWORD=local-only postgres:17 >/dev/null
# Con KEEP=1 el contenedor no se borra al terminar (sirve para inspeccionar la base a mano)
if [ -z "${KEEP:-}" ]; then trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT; fi

echo "Esperando a Postgres…"
# Dos comprobaciones seguidas: la imagen reinicia Postgres una vez al inicializarse
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

run() { docker exec "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -q -f "$1"; }

echo "== Arranque local"
run /work/test/local_bootstrap.sql

echo "== Migraciones"
for f in $(ls supabase/migrations/*.sql | sort); do
  echo "   $(basename "$f")"
  run "/work/migrations/$(basename "$f")" >/dev/null
done

echo "== Idempotencia (segunda pasada, sin la 001 que crea las tablas base)"
for f in $(ls supabase/migrations/*.sql | sort | tail -n +2); do
  echo "   $(basename "$f")"
  run "/work/migrations/$(basename "$f")" >/dev/null
done

echo "== Auditoría de seguridad (RLS, permisos de anon, funciones)"
AUD=$(docker exec "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -f /work/test/security_audit.sql 2>&1) || { echo "$AUD" | tail -n 15; echo "LA AUDITORÍA DE SEGURIDAD FALLÓ"; exit 1; }
echo "$AUD" | grep -E "OK |===" || true

echo "== Prueba de humo"
OUT=$(docker exec "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -f /work/test/smoke_test.sql 2>&1) || { echo "$OUT" | tail -n 25; echo "LA PRUEBA DE HUMO FALLÓ"; exit 1; }
echo "$OUT" | grep -E "OK |===" || true
echo "Reglas de auditoría OK: $(echo "$AUD" | grep -c 'OK ')"
echo "Checks OK: $(echo "$OUT" | grep -c 'OK ')"
