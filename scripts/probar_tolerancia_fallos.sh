#!/usr/bin/env bash
# Tolerancia a fallos con Resilience4j: detiene servicios A PROPOSITO y muestra el comportamiento
# alternativo de cada dependiente, y luego la recuperacion automatica al volver el servicio.
#
#   A. clientes-service caido -> bff-web DEGRADA (entrega la cuenta sin datos del titular).
#   B. cuentas-service caido  -> bff-web responde 503 controlado (fallback del circuit breaker) y
#                                 pagos-service reintenta, registra el pago como FALLIDA y responde 503.
#   C. Recuperacion: al volver cuentas-service, el circuito se cierra solo y todo vuelve a operar.
#
# Uso: bash scripts/probar_tolerancia_fallos.sh
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/_common.sh

TOKEN=$(login_web "$CUENTA_A" "$NOMBRE_A")
verificar_token "token web" "$TOKEN"
preparar_token_interno
TOKEN_SERVICIO="$TOKEN_INTERNO"

# Al salir (bien o mal), se asegura de dejar arriba los servicios que este script detiene.
restaurar() { $COMPOSE start clientes-service cuentas-service >/dev/null 2>&1 || true; }
trap restaurar EXIT

# Consulta la vista web de la cuenta e imprime el codigo HTTP junto a un resumen del cuerpo.
consultar_cuenta() {
  local salida codigo cuerpo
  salida=$(curl -s -k -w "\n%{http_code}" -H "Authorization: Bearer $TOKEN" "$WEB/api/web/cuentas/$CUENTA_A")
  codigo=$(echo "$salida" | tail -n1)
  cuerpo=$(echo "$salida" | sed '$d')
  echo "HTTP $codigo: $(echo "$cuerpo" | jq -c 'if has("cuentaId") then {cuentaId, titular, edadTitular, saldoActual} else . end' 2>/dev/null || echo "$cuerpo")"
}

# Estado de un circuito de bff-web segun Resilience4j (/actuator/circuitbreakers).
estado_circuito() {
  curl -s -k "$WEB/actuator/circuitbreakers" \
    | jq -c --arg n "$1" '.circuitBreakers[$n] | {circuito: $n, state, failureRate, bufferedCalls, failedCalls, notPermittedCalls}' 2>/dev/null \
    || echo "(no se pudo leer /actuator/circuitbreakers)"
}

# ============================================================================================
separador "A. DEGRADACION CONTROLADA: clientes-service caido"
# ============================================================================================
echo "--- Con todo arriba: la vista web incluye al titular ---"
consultar_cuenta
echo "+ $COMPOSE stop clientes-service"
$COMPOSE stop clientes-service 2>&1 | tail -n 3
sleep 3
echo "--- Con clientes-service caido: la cuenta SIGUE respondiendo (HTTP 200), sin datos personales ---"
for i in 1 2 3; do
  consultar_cuenta
done
echo "--- El login web (que SI necesita el nombre del titular) responde un 503 controlado ---"
curl -s -k -w "\nHTTP %{http_code}\n" -X POST "$WEB/api/web/auth/login" -H "Content-Type: application/json" \
  -d "{\"cuentaId\":$CUENTA_A,\"nombre\":\"$NOMBRE_A\"}"
echo "--- El canal movil no depende de clientes-service: no se entera de la falla ---"
TOKEN_MOBILE=$(curl -s -k -X POST "$MOBILE/api/mobile/auth/login" -H "Content-Type: application/json" \
  -d "{\"cuentaId\":$CUENTA_A,\"pin\":\"$PIN_A\"}" | jq -r .token)
curl -s -k -o /dev/null -w "GET /api/mobile/cuentas/$CUENTA_A/resumen -> HTTP %{http_code} (se espera 200)\n" \
  -H "Authorization: Bearer $TOKEN_MOBILE" "$MOBILE/api/mobile/cuentas/$CUENTA_A/resumen"
echo "+ $COMPOSE start clientes-service"
$COMPOSE start clientes-service 2>&1 | tail -n 3

# ============================================================================================
separador "B. CIRCUIT BREAKER + RETRY: cuentas-service caido"
# ============================================================================================
echo "+ $COMPOSE stop cuentas-service"
$COMPOSE stop cuentas-service 2>&1 | tail -n 3
sleep 3
echo "--- B.1 bff-web -> cuentas-service: 8 llamadas seguidas ---"
echo "(el circuito se abre cuando, con al menos 5 llamadas registradas, las fallas llegan al 50%; desde ahi el fallback responde"
echo " SIN intentar la llamada: lo prueban el estado OPEN y el contador notPermittedCalls)"
echo "Estado del circuito ANTES:   $(estado_circuito cuentasService)"
for i in $(seq 1 8); do
  curl -s -k -o /dev/null -w "intento $i -> HTTP %{http_code} en %{time_total}s\n" \
    -H "Authorization: Bearer $TOKEN" "$WEB/api/web/cuentas/$CUENTA_A"
done
echo "Estado del circuito DESPUES: $(estado_circuito cuentasService)"
echo "--- Cuerpo de la respuesta de fallback ---"
curl -s -k -H "Authorization: Bearer $TOKEN" "$WEB/api/web/cuentas/$CUENTA_A"; echo

echo "--- B.2 pagos-service -> cuentas-service: un deposito con el servicio de cuentas caido ---"
echo "(pagos-service reintenta 3 veces, registra el pago como FALLIDA y responde 503: el dinero no se pierde ni se duplica)"
interno -w "\nHTTP %{http_code} en %{time_total}s\n" -X POST -H "Authorization: Bearer $TOKEN_SERVICIO" -H "Content-Type: application/json" \
  -d "{\"cuentaDestino\":$CUENTA_A,\"monto\":777,\"glosa\":\"Deposito durante la caida\"}" "$PAGOS_INT/internal/pagos/depositos"
echo "--- El intento quedo registrado en el historial de pagos con su motivo ---"
interno -H "Authorization: Bearer $TOKEN_SERVICIO" "$PAGOS_INT/internal/pagos?cuentaId=$CUENTA_A" \
  | jq -c '[.[] | select(.estado == "FALLIDA")][0] | {tipo, monto, estado, detalle}'

echo "--- B.3 Una transferencia solicitada durante la caida NO se pierde: queda en Kafka ---"
ID_PENDIENTE=$(curl -s -k -X POST "$WEB/api/web/cuentas/$CUENTA_A/transferencias" -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" -d "{\"cuentaDestino\":$CUENTA_B,\"monto\":111}" | jq -r .transferenciaId)
echo "Transferencia $ID_PENDIENTE aceptada (202) con cuentas-service caido; estado actual:"
curl -s -k -H "Authorization: Bearer $TOKEN" "$WEB/api/web/transferencias/$ID_PENDIENTE" | jq -c '{estado}'

# ============================================================================================
separador "C. RECUPERACION AUTOMATICA"
# ============================================================================================
echo "+ $COMPOSE start cuentas-service"
$COMPOSE start cuentas-service 2>&1 | tail -n 3
trap - EXIT
esperar_healthy 120
echo "Esperando a que el circuito pase a semiabierto (10 s) y a que Eureka propague el registro..."
RECUPERADO=false
for intento in $(seq 1 40); do
  codigo=$(curl -s -k -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$WEB/api/web/cuentas/$CUENTA_A")
  if [ "$codigo" = "200" ]; then
    echo "bff-web volvio a responder HTTP 200 (intento $intento): el circuito esta SEMIABIERTO y deja pasar llamadas de prueba."
    RECUPERADO=true
    break
  fi
  sleep 3
done
if [ "$RECUPERADO" != true ]; then
  echo "bff-web no se recupero a tiempo tras reiniciar cuentas-service." >&2
  exit 1
fi
# En HALF_OPEN, Resilience4j deja pasar 3 llamadas de prueba (permitted-number-of-calls-in-half-open-state)
# y, si fallan menos del umbral, cierra el circuito. Se completan esas llamadas para mostrar el cierre.
echo "Estado tras la primera llamada exitosa: $(estado_circuito cuentasService)"
for prueba in 1 2 3 4 5; do
  if curl -s -k "$WEB/actuator/circuitbreakers" | jq -e '.circuitBreakers.cuentasService.state == "CLOSED"' >/dev/null 2>&1; then
    break
  fi
  curl -s -k -o /dev/null -w "llamada de prueba $prueba en estado semiabierto -> HTTP %{http_code}\n" \
    -H "Authorization: Bearer $TOKEN" "$WEB/api/web/cuentas/$CUENTA_A"
done
echo "Estado final del circuito: $(estado_circuito cuentasService)"
echo "--- Transiciones de estado registradas por Resilience4j (/actuator/circuitbreakerevents) ---"
curl -s -k "$WEB/actuator/circuitbreakerevents/cuentasService" \
  | jq -r '.circuitBreakerEvents[] | select(.type == "STATE_TRANSITION") | "\(.creationTime)  \(.stateTransition)"' 2>/dev/null \
  || echo "(no se pudo leer /actuator/circuitbreakerevents)"
echo "--- La transferencia que quedo pendiente se proceso al volver el servicio (Kafka la conservo) ---"
esperar_estado_transferencia "$TOKEN" "$ID_PENDIENTE" 40 | jq -c '{transferenciaId, estado, monto}'

echo
echo "=== Fin de la prueba de tolerancia a fallos ==="
