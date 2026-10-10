#!/usr/bin/env bash
# Parte 3 de la EFT: ejercita directamente las APIs internas de los 3 microservicios de negocio
# (Gestion de Clientes, Gestion de Cuentas y Procesamiento de Pagos), con el ciclo de vida
# completo: alta de cliente -> apertura de cuenta -> mantenimiento -> deposito -> pago -> cierre.
#
# Las llamadas se hacen DENTRO de la red de docker-compose (funcion "interno" de _common.sh),
# porque los microservicios no publican puertos al host, y con un token OAuth2 valido.
#
# Uso: bash scripts/probar_microservicios.sh
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/_common.sh

preparar_token_interno
TOKEN="$TOKEN_INTERNO"
AUTH="Authorization: Bearer $TOKEN"
JSON="Content-Type: application/json"

# Llamada interna que imprime el cuerpo (formateado) y luego el codigo HTTP.
# Uso: llamar METODO URL [CUERPO_JSON]
llamar() {
  local metodo="$1" url="$2" cuerpo="${3:-}" respuesta
  if [ -n "$cuerpo" ]; then
    respuesta=$(interno -w "\n%{http_code}" -X "$metodo" -H "$AUTH" -H "$JSON" -d "$cuerpo" "$url")
  else
    respuesta=$(interno -w "\n%{http_code}" -X "$metodo" -H "$AUTH" "$url")
  fi
  ULTIMO_CODIGO=$(echo "$respuesta" | tail -n1)
  ULTIMO_CUERPO=$(echo "$respuesta" | sed '$d')
  echo "$ULTIMO_CUERPO" | jq . 2>/dev/null || echo "$ULTIMO_CUERPO"
  echo "-> $metodo ${url#http://} : HTTP $ULTIMO_CODIGO"
}

# =============================================================================================
separador "A. GESTION DE CLIENTES (clientes-service)"
# =============================================================================================
echo "--- A.1 Cliente migrado del legacy (id $CUENTA_A) ---"
llamar GET "$CLIENTES_INT/internal/clientes/$CUENTA_A"

echo "--- A.2 Alta de un cliente nuevo ---"
llamar POST "$CLIENTES_INT/internal/clientes" '{"nombre":"Camila Rojas","edad":34,"email":"camila.rojas@correo.example","telefono":"+56 9 5555 0101","direccion":"Av. Providencia 1234, Santiago"}'
CLIENTE_NUEVO=$(echo "$ULTIMO_CUERPO" | jq -r .clienteId)
verificar_token "id del cliente nuevo" "$CLIENTE_NUEVO"

echo "--- A.3 Validacion: un cliente menor de edad se rechaza (se espera 400) ---"
llamar POST "$CLIENTES_INT/internal/clientes" '{"nombre":"Menor De Edad","edad":15}'

echo "--- A.4 Actualizacion del perfil (datos de contacto): publica una alerta de seguridad en Kafka ---"
llamar PUT "$CLIENTES_INT/internal/clientes/$CLIENTE_NUEVO" '{"telefono":"+56 9 5555 0202","direccion":"Los Leones 456, Providencia"}'

echo "--- A.5 Cliente inexistente (se espera 404) ---"
llamar GET "$CLIENTES_INT/internal/clientes/999999"

# =============================================================================================
separador "B. GESTION DE CUENTAS (cuentas-service): apertura, mantenimiento y cierre"
# =============================================================================================
echo "--- B.1 Apertura de una cuenta de ahorro para el cliente $CLIENTE_NUEVO (cuentas-service valida al cliente en clientes-service) ---"
llamar POST "$CUENTAS_INT/internal/cuentas" "{\"clienteId\":$CLIENTE_NUEVO,\"tipo\":\"ahorro\",\"saldoInicial\":50000}"
CUENTA_NUEVA=$(echo "$ULTIMO_CUERPO" | jq -r .cuentaId)
verificar_token "id de la cuenta nueva" "$CUENTA_NUEVA"

echo "--- B.2 Apertura para un cliente que no existe (se espera 422) ---"
llamar POST "$CUENTAS_INT/internal/cuentas" '{"clienteId":999999,"tipo":"ahorro","saldoInicial":1000}'

echo "--- B.3 Mantenimiento: cambio de tipo de producto (ahorro -> corriente) ---"
llamar PUT "$CUENTAS_INT/internal/cuentas/$CUENTA_NUEVA" '{"tipo":"corriente"}'

echo "--- B.4 Cuentas del cliente $CLIENTE_NUEVO ---"
llamar GET "$CUENTAS_INT/internal/cuentas?clienteId=$CLIENTE_NUEVO"

echo "--- B.5 Idempotencia: el MISMO credito (mismo idOperacion) enviado dos veces se aplica una sola vez ---"
ID_OPERACION="prueba-idempotencia-$(date +%s)"
interno -X POST -H "$AUTH" -H "$JSON" -d "{\"monto\":1000,\"descripcion\":\"Abono de prueba\",\"idOperacion\":\"$ID_OPERACION\"}" \
  "$CUENTAS_INT/internal/cuentas/$CUENTA_NUEVA/credito" | jq '{cuentaId, saldo}'
interno -X POST -H "$AUTH" -H "$JSON" -d "{\"monto\":1000,\"descripcion\":\"Abono de prueba\",\"idOperacion\":\"$ID_OPERACION\"}" \
  "$CUENTAS_INT/internal/cuentas/$CUENTA_NUEVA/credito" | jq '{cuentaId, saldo}'
echo "(ambas respuestas muestran el mismo saldo, 51000: el segundo envio se reconocio como repetido)"

# =============================================================================================
separador "C. PROCESAMIENTO DE PAGOS (pagos-service): depositos, pagos y transferencias"
# =============================================================================================
echo "--- C.1 Deposito de \$10.000 en la cuenta $CUENTA_NUEVA (REST sincronico hacia cuentas-service) ---"
llamar POST "$PAGOS_INT/internal/pagos/depositos" "{\"cuentaDestino\":$CUENTA_NUEVA,\"monto\":10000,\"glosa\":\"Deposito en sucursal\"}"

echo "--- C.2 Pago de un servicio por \$25.000 ---"
llamar POST "$PAGOS_INT/internal/pagos/servicios" "{\"cuentaOrigen\":$CUENTA_NUEVA,\"monto\":25000,\"comercio\":\"Compania de Electricidad\"}"

echo "--- C.3 Pago que excede el saldo: queda registrado como RECHAZADA, con su motivo ---"
llamar POST "$PAGOS_INT/internal/pagos/servicios" "{\"cuentaOrigen\":$CUENTA_NUEVA,\"monto\":99999999,\"comercio\":\"Automotora\"}"

echo "--- C.4 Transferencia de \$6.000 a la cuenta $CUENTA_A (asincrona: saga por eventos Kafka; responde 202) ---"
llamar POST "$PAGOS_INT/internal/pagos/transferencias" "{\"cuentaOrigen\":$CUENTA_NUEVA,\"cuentaDestino\":$CUENTA_A,\"monto\":6000}"
ID_TRANSFERENCIA=$(echo "$ULTIMO_CUERPO" | jq -r .pagoId)
echo "Esperando el resultado de la saga..."
for _ in $(seq 1 30); do
  ESTADO=$(interno -H "$AUTH" "$PAGOS_INT/internal/transferencias/$ID_TRANSFERENCIA" | jq -r .estado)
  [ "$ESTADO" != "EN_PROCESO" ] && [ "$ESTADO" != "null" ] && break
  sleep 1
done
llamar GET "$PAGOS_INT/internal/transferencias/$ID_TRANSFERENCIA"

echo "--- C.5 Historial de pagos de la cuenta $CUENTA_NUEVA ---"
interno -H "$AUTH" "$PAGOS_INT/internal/pagos?cuentaId=$CUENTA_NUEVA" | jq -c '.[] | {tipo, monto, estado, glosa, detalle}'

echo "--- C.6 Saldo y movimientos resultantes (50000 + 1000 + 10000 - 25000 - 6000 = 30000) ---"
interno -H "$AUTH" "$CUENTAS_INT/internal/cuentas/$CUENTA_NUEVA" | jq '{cuentaId, tipo, estado, saldo, movimientos: [.movimientos[] | {tipoMovimiento, monto, descripcion}]}'

# =============================================================================================
separador "D. CIERRE DE CUENTA Y BAJA DE CLIENTE"
# =============================================================================================
echo "--- D.1 Cierre con saldo pendiente: se rechaza (se espera 422) ---"
llamar POST "$CUENTAS_INT/internal/cuentas/$CUENTA_NUEVA/cierre"

echo "--- D.2 Se retira el saldo restante y se cierra la cuenta ---"
SALDO_RESTANTE=$(saldo_de "$CUENTA_NUEVA")
interno -X POST -H "$AUTH" -H "$JSON" -d "{\"monto\":$SALDO_RESTANTE,\"descripcion\":\"Retiro por cierre de cuenta\"}" \
  "$CUENTAS_INT/internal/cuentas/$CUENTA_NUEVA/debito" | jq '{cuentaId, saldo}'
llamar POST "$CUENTAS_INT/internal/cuentas/$CUENTA_NUEVA/cierre"

echo "--- D.3 Una cuenta cerrada no admite operaciones: el deposito queda RECHAZADA ---"
llamar POST "$PAGOS_INT/internal/pagos/depositos" "{\"cuentaDestino\":$CUENTA_NUEVA,\"monto\":500}"

echo "--- D.4 Desactivacion del cliente (publica una alerta de seguridad de severidad ALTA) ---"
llamar POST "$CLIENTES_INT/internal/clientes/$CLIENTE_NUEVO/desactivar"

echo "--- D.5 A un cliente inactivo no se le puede abrir una cuenta (se espera 422) ---"
llamar POST "$CUENTAS_INT/internal/cuentas" "{\"clienteId\":$CLIENTE_NUEVO,\"tipo\":\"ahorro\",\"saldoInicial\":0}"

echo
echo "=== Fin de las pruebas de microservicios (Parte 3) ==="
