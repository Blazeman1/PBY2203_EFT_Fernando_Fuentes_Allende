#!/usr/bin/env bash
# Parte 1 de la EFT: ejecuta los procesos batch migrados a Spring Batch (contenedor "batch" del
# docker-compose, perfil "batch") contra el dataset oficial fin_legacy_data y muestra, con
# consultas SQL, los resultados y la metadata de ejecucion que deja Spring Batch.
#
#   1. Control de calidad: el Job de transacciones con un umbral estricto (20%) termina SIN
#      generar su reporte y se deriva a revision manual (politica de finalizacion del Job).
#   2. Un fallo critico provocado (limite de omisiones irreal) para ver la reejecucion automatica.
#   3. Los 3 Jobs base: transacciones diarias, intereses y estados de cuenta anuales
#      (multi-hilo: 3 hilos, chunk de 5, tolerancia a fallos con politicas de skip y retry).
#   4. El Job de transacciones en modo PARTICIONADO (escalamiento por particiones).
#
# Uso: bash scripts/probar_batch.sh
set -uo pipefail

cd "$(dirname "$0")/.."
source scripts/_common.sh

BATCH="$COMPOSE --profile batch"
sql() { $COMPOSE exec -T postgres psql -U bancoxyz -d batch_db -P pager=off -c "$1"; }

mkdir -p evidencias

# Ejecuta el contenedor batch con los argumentos dados. El log COMPLETO (trae una linea por cada
# registro procesado u omitido) se guarda en evidencias/; en pantalla se muestra un resumen
# legible: inicio y fin de cada Job y Step, particiones, decider de calidad, reejecuciones,
# errores, y cuantas lineas de log produjo cada hilo (la evidencia del procesamiento paralelo).
# Uso: ejecutar_batch ARCHIVO_LOG argumentos...
ejecutar_batch() {
  local archivo="evidencias/$1" codigo
  shift
  echo "+ $BATCH run --rm -T batch $*"
  $BATCH run --rm -T batch "$@" > "$archivo" 2>&1
  codigo=$?
  grep -E "INICIO JOB|FIN JOB|Estado de salida|Inicio Step|Fin Step|ControlCalidadDecider|Particion\[|Particionado del archivo|TaskExecutor inicializado|Resumen diario generado|Estados de cuenta anuales generados|Calculo de intereses mensuales finalizado|ATENCION|Reejecucion|reejecucion|termino con estado|tras falla transitoria|Se agotaron|NO omitible|limite de omisiones|APPLICATION FAILED|^Caused by|Exception in thread" \
    "$archivo" || true
  echo "--- Lineas de log por hilo de ejecucion (paralelismo) ---"
  sed -n 's/^[0-9-]* [0-9:.]* \[\([^]]*\)\].*/\1/p' "$archivo" | sort | uniq -c | sort -rn | head -n 12 || true
  echo "--- Avisos de omision en el log: $(grep -c "Registro omitido" "$archivo" || true) (incluye los repetidos al reprocesar un chunk que fallo al escribir; el total exacto esta en la metadata, seccion 6). 3 ejemplos: ---"
  grep -m 3 "Registro omitido" "$archivo" | cut -c1-230 || true
  echo "-> exit code del proceso batch: $codigo (0 = todos los Jobs terminaron en COMPLETED). Log completo: $archivo"
  return "$codigo"
}

FALLAS=0

separador "1. Politica de finalizacion: control de calidad con umbral estricto (20% de omisiones)"
echo "(el archivo oficial trae ~23% de registros invalidos: con este umbral el decider NO deja generar"
echo " el reporte y el Job termina COMPLETED con exit status REVISION_REQUERIDA)"
ejecutar_batch logs-batch-control-calidad.log transacciones --batch.umbral-calidad-porcentaje=20 || FALLAS=$((FALLAS + 1))

separador "2. Fallo critico y reejecucion automatica (limite de omisiones forzado a 10)"
echo "(con un limite de omisiones irreal, el Step supera el tope y el Job termina FAILED: el lanzador lo"
echo " reejecuta automaticamente 2 veces sobre la MISMA JobInstance y, al persistir la causa, lo da por"
echo " fallido con exit code distinto de 0. Este fallo es el resultado ESPERADO de esta prueba.)"
if ejecutar_batch logs-batch-reejecucion.log cuentas-anuales --batch.limite-omisiones=10 --batch.reejecucion-espera-ms=1000; then
  echo "INESPERADO: el Job debio fallar con el limite de omisiones en 10." >&2
  FALLAS=$((FALLAS + 1))
else
  echo "-> Fallo esperado: la politica de limite de omisiones detuvo el Job y la reejecucion automatica quedo registrada."
fi

separador "3. Jobs base (multi-hilo): transacciones, intereses y cuentas-anuales"
ejecutar_batch logs-batch-jobs-base.log todos || FALLAS=$((FALLAS + 1))

separador "4. Job de transacciones en modo PARTICIONADO (grid-size=4)"
ejecutar_batch logs-batch-particionado.log transacciones-particionado --batch.grid-size=4 || FALLAS=$((FALLAS + 1))

separador "5. Metadata de Spring Batch: ejecuciones de Jobs (base de la re-ejecucion y reanudacion)"
sql "SELECT e.job_execution_id AS id, i.job_name, e.status, e.exit_code,
            to_char(e.start_time, 'HH24:MI:SS') AS inicio, to_char(e.end_time, 'HH24:MI:SS') AS fin,
            round(extract(epoch FROM (e.end_time - e.start_time))::numeric, 2) AS segundos
     FROM batch_job_execution e JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id
     ORDER BY e.job_execution_id;"

separador "6. Metadata por Step: leidos, escritos, omitidos (skip), commits y rollbacks"
sql "SELECT step_name, status, read_count AS leidos, write_count AS escritos,
            (read_skip_count + process_skip_count + write_skip_count) AS omitidos,
            commit_count AS commits, rollback_count AS rollbacks
     FROM batch_step_execution ORDER BY step_execution_id;"

separador "7. Resultados de negocio"
sql "SELECT 'transacciones_procesadas' AS tabla, count(*) AS filas, count(*) FILTER (WHERE es_anomalia) AS anomalias FROM transacciones_procesadas
     UNION ALL SELECT 'cuentas_interes', count(*), NULL FROM cuentas_interes
     UNION ALL SELECT 'cuentas_anuales_movimientos', count(*), count(*) FILTER (WHERE es_anomalia) FROM cuentas_anuales_movimientos
     UNION ALL SELECT 'estados_cuenta_anuales', count(*), NULL FROM estados_cuenta_anuales;"
echo "--- Reporte de transacciones diarias (resumen generado por el Job) ---"
sql "SELECT total_procesadas, total_anomalias, total_omitidas, monto_total_creditos, monto_total_debitos
     FROM resumen_transacciones_diarias ORDER BY id DESC LIMIT 2;"
echo "--- Calculo de intereses (5 cuentas de ejemplo) ---"
sql "SELECT cuenta_id, nombre, tipo, saldo_inicial, tasa_interes_mensual AS tasa, interes_calculado, saldo_final
     FROM cuentas_interes ORDER BY cuenta_id LIMIT 5;"
echo "--- Estados de cuenta anuales (5 cuentas de ejemplo) ---"
sql "SELECT cuenta_id, anio, total_depositos, total_retiros, total_compras, saldo_neto, cantidad_movimientos, cantidad_anomalias
     FROM estados_cuenta_anuales ORDER BY cuenta_id LIMIT 5;"

echo
if [ "$FALLAS" -ne 0 ]; then
  echo "=== Fin de las pruebas batch: $FALLAS ejecucion(es) terminaron con error ===" >&2
  exit 1
fi
echo "=== Fin de las pruebas batch (Parte 1) ==="
