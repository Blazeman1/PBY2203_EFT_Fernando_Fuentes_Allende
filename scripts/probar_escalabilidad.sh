#!/usr/bin/env bash
# Escalabilidad horizontal de los 3 microservicios de negocio: agrega replicas EN CALIENTE (sin
# detener nada), verifica que se registran en Eureka y demuestra, con los logs de cada replica,
# que tanto las peticiones REST (balanceo del lado del cliente) como los eventos Kafka (reparto
# de particiones dentro del consumer group) se distribuyen entre ellas.
#
# Uso: bash scripts/probar_escalabilidad.sh [REPLICAS]      (por defecto 2)
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/_common.sh

REPLICAS="${1:-2}"
preparar_token_interno

separador "1. Estado inicial: instancias registradas en Eureka"
for servicio in CLIENTES-SERVICE CUENTAS-SERVICE PAGOS-SERVICE NOTIFICACIONES-SERVICE; do
  echo "$servicio: $(instancias_en_eureka $servicio) instancia(s)"
done

separador "2. Escalando a $REPLICAS replicas: clientes-service, cuentas-service y pagos-service"
echo "+ $COMPOSE up -d --no-recreate --scale clientes-service=$REPLICAS --scale cuentas-service=$REPLICAS --scale pagos-service=$REPLICAS"
$COMPOSE up -d --no-recreate \
  --scale clientes-service="$REPLICAS" --scale cuentas-service="$REPLICAS" --scale pagos-service="$REPLICAS" 2>&1 | tail -n 20
esperar_healthy 120

separador "3. Las replicas nuevas se registran solas en Eureka (nadie reconfigura a los clientes)"
esperar_instancias CLIENTES-SERVICE "$REPLICAS" 90
esperar_instancias CUENTAS-SERVICE "$REPLICAS" 90
esperar_instancias PAGOS-SERVICE "$REPLICAS" 90
echo "--- Instancias por servicio (host:puerto registrados) ---"
curl -s -H "Accept: application/json" "$EUREKA/eureka/apps" \
  | jq -r '.applications.application[] | "\(.name): \([.instance] | flatten | map(.ipAddr + ":" + (.port["$"] | tostring)) | join(", "))"' | sort
echo "--- Contenedores ---"
$COMPOSE ps | grep -E "NAME|clientes-service|cuentas-service|pagos-service|notificaciones-service" || true

echo "Esperando 15 s a que los BFF refresquen su copia del registro y su cache de balanceo..."
sleep 15

separador "4. Carga de prueba: 30 consultas (movil + web), 6 pagos y 8 transferencias"
TOKEN_WEB=$(login_web "$CUENTA_A" "$NOMBRE_A")
verificar_token "token web" "$TOKEN_WEB"
TOKEN_MOBILE=$(curl -s -k -X POST "$MOBILE/api/mobile/auth/login" -H "Content-Type: application/json" \
  -d "{\"cuentaId\":$CUENTA_A,\"pin\":\"$PIN_A\"}" | jq -r .token)
verificar_token "token movil" "$TOKEN_MOBILE"

# Marca de tiempo (UTC) del inicio de la carga: los conteos de mas abajo solo consideran los logs
# posteriores a este instante, es decir, con todas las replicas ya en servicio.
INICIO_CARGA=$(date -u +%Y-%m-%dT%H:%M:%SZ)

OK=0
for i in $(seq 1 15); do
  c1=$(curl -s -k -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN_MOBILE" "$MOBILE/api/mobile/cuentas/$CUENTA_A/resumen")
  c2=$(curl -s -k -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN_WEB" "$WEB/api/web/cuentas/$CUENTA_A")
  [ "$c1" = "200" ] && OK=$((OK + 1))
  [ "$c2" = "200" ] && OK=$((OK + 1))
done
echo "Consultas respondidas con HTTP 200: $OK de 30"
for i in $(seq 1 6); do
  curl -s -k -o /dev/null -X POST "$WEB/api/web/cuentas/$CUENTA_A/pagos" -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN_WEB" -d "{\"monto\":$((i * 10)),\"comercio\":\"Servicio $i\"}"
done
for i in $(seq 1 8); do
  curl -s -k -o /dev/null -X POST "$WEB/api/web/cuentas/$CUENTA_A/transferencias" -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN_WEB" -d "{\"cuentaDestino\":$CUENTA_B,\"monto\":$((i * 5))}"
done
echo "6 pagos de servicios y 8 transferencias enviados. Esperando a que terminen las sagas..."
sleep 10

# Cuenta, por contenedor, las lineas de log que contienen un patron (desde el inicio de la carga).
# El prefijo "nombre-del-contenedor |" que agrega docker compose identifica a cada replica.
reparto() {
  local servicio="$1" patron="$2"
  $COMPOSE logs --no-color --since "$INICIO_CARGA" "$servicio" 2>/dev/null | grep -F "$patron" | awk '{print $1}' | sort | uniq -c \
    | awk '{printf "    %-28s %s linea(s)\n", $2, $1}' || true
}

separador "5. Balanceo de carga REST: peticiones atendidas por cada replica (log de auditoria PETICION)"
for servicio in cuentas-service clientes-service pagos-service; do
  echo "$servicio:"
  reparto "$servicio" "PETICION"
done

separador "6. Eventos Kafka: particiones repartidas entre las replicas del mismo consumer group"
echo "cuentas-service (pasos de la saga procesados por cada replica):"
reparto cuentas-service "Saga transferencia"
echo "pagos-service (resultados de transferencias aplicados por cada replica):"
reparto pagos-service "estado final ->"
echo "notificaciones-service (notificaciones procesadas por cada replica):"
reparto notificaciones-service "Notificacion procesada"
echo "--- Asignacion de particiones del consumer group cuentas-service (kafka-consumer-groups) ---"
$COMPOSE exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group cuentas-service 2>/dev/null \
  | awk 'NF && $1 != "Consumer" {printf "    %-45s %-10s %-15s %-15s %-6s %s\n", $2, $3, $4, $5, $6, $8}' || true
echo "(cada particion tiene un unico consumidor asignado -columna HOST-: dos replicas nunca procesan el mismo evento)"

separador "7. Consistencia con varias replicas: el dinero total no cambia"
echo "Saldo cuenta $CUENTA_A = $(saldo_de $CUENTA_A) | cuenta $CUENTA_B = $(saldo_de $CUENTA_B)"
echo "(todas las replicas leen y escriben la misma base PostgreSQL con bloqueo de fila: no hay saldos divergentes)"

echo
echo "=== Fin de la prueba de escalabilidad horizontal ==="
