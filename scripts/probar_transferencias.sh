#!/usr/bin/env bash
# Transferencias entre cuentas desde el canal web: saga por coreografia con Kafka entre
# pagos-service y cuentas-service, con sus 3 desenlaces posibles (COMPLETADA, RECHAZADA y
# COMPENSADA), mas el pago de servicios y el historial de pagos del canal web.
#
# Uso: bash scripts/probar_transferencias.sh
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/_common.sh

separador "0. Login canal WEB (cuenta $CUENTA_A, '$NOMBRE_A') - se reutiliza en todos los escenarios"
preparar_token_interno
TOKEN=$(login_web "$CUENTA_A" "$NOMBRE_A")
verificar_token "token web" "$TOKEN"
echo "Sesion web iniciada."

# Solicita una transferencia desde CUENTA_A. Uso: transferir DESTINO MONTO -> deja RESPUESTA, HTTP_CODE e ID
transferir() {
  local salida
  salida=$(curl -s -k -w "\n%{http_code}" -X POST "$WEB/api/web/cuentas/$CUENTA_A/transferencias" \
    -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
    -d "{\"cuentaDestino\":$1,\"monto\":$2}")
  HTTP_CODE=$(echo "$salida" | tail -n1)
  RESPUESTA=$(echo "$salida" | sed '$d')
  ID=$(echo "$RESPUESTA" | jq -r .transferenciaId)
}

# ============================================================================================
separador "1. Transferencia EXITOSA: cuenta $CUENTA_A -> cuenta $CUENTA_B, \$500"
# ============================================================================================
echo "Saldo antes: cuenta $CUENTA_A = $(saldo_de $CUENTA_A) | cuenta $CUENTA_B = $(saldo_de $CUENTA_B)"
transferir "$CUENTA_B" 500
echo "$RESPUESTA" | jq .
echo "POST /transferencias -> HTTP $HTTP_CODE (se espera 202: la escritura es asincrona)"
echo "Esperando el resultado final de la saga..."
ESTADO=$(esperar_estado_transferencia "$TOKEN" "$ID")
echo "$ESTADO" | jq .
echo "Estado final -> $(echo "$ESTADO" | jq -r .estado) (se espera COMPLETADA)"
echo "Saldo despues: cuenta $CUENTA_A = $(saldo_de $CUENTA_A) (baja \$500) | cuenta $CUENTA_B = $(saldo_de $CUENTA_B) (sube \$500)"

# ============================================================================================
separador "2. Transferencia RECHAZADA: fondos insuficientes (y alerta de seguridad por monto alto)"
# ============================================================================================
SALDO_ANTES=$(saldo_de $CUENTA_A)
transferir "$CUENTA_B" 999999999
ESTADO=$(esperar_estado_transferencia "$TOKEN" "$ID")
echo "$ESTADO" | jq .
echo "Estado final -> $(echo "$ESTADO" | jq -r .estado) (se espera RECHAZADA)"
echo "Saldo cuenta $CUENTA_A: antes=$SALDO_ANTES despues=$(saldo_de $CUENTA_A) (deben ser iguales: nunca se llego a debitar)"
echo "(por superar el umbral de monitoreo, pagos-service publico ademas una alerta en bancoxyz.seguridad.alertas)"

# ============================================================================================
separador "3. Transferencia COMPENSADA: la cuenta destino no existe (se revierte el debito)"
# ============================================================================================
SALDO_ANTES=$(saldo_de $CUENTA_A)
transferir 999999 250
ESTADO=$(esperar_estado_transferencia "$TOKEN" "$ID")
echo "$ESTADO" | jq .
echo "Estado final -> $(echo "$ESTADO" | jq -r .estado) (se espera COMPENSADA)"
echo "Saldo cuenta $CUENTA_A: antes=$SALDO_ANTES despues=$(saldo_de $CUENTA_A) (deben ser iguales: se debito \$250 y la compensacion lo devolvio)"
echo "Movimientos que dejo la saga en la cuenta $CUENTA_A (debito y su reverso):"
interno -H "Authorization: Bearer $TOKEN_INTERNO" "$CUENTAS_INT/internal/cuentas/$CUENTA_A" \
  | jq -c '.movimientos[-2:][] | {tipoMovimiento, monto, descripcion}'

# ============================================================================================
separador "4. Autorizacion: el token de la cuenta $CUENTA_A no puede transferir DESDE la cuenta $CUENTA_B"
# ============================================================================================
curl -s -k -o /dev/null -w "POST /api/web/cuentas/$CUENTA_B/transferencias con token de la cuenta $CUENTA_A -> HTTP %{http_code} (se espera 403)\n" \
  -X POST "$WEB/api/web/cuentas/$CUENTA_B/transferencias" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d "{\"cuentaDestino\":$CUENTA_A,\"monto\":10}"

# ============================================================================================
separador "5. Pago de un servicio desde el canal web (pagos-service -> cuentas-service, REST sincronico)"
# ============================================================================================
curl -s -k -X POST "$WEB/api/web/cuentas/$CUENTA_A/pagos" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"monto":300,"comercio":"Agua Potable"}' | jq .

# ============================================================================================
separador "6. Varias transferencias mas (para ver el reparto de particiones entre replicas)"
# ============================================================================================
for monto in 50 75 120 40 60 90 30 20; do
  transferir "$CUENTA_B" "$monto"
  echo "transferencia de \$$monto -> HTTP $HTTP_CODE id=$ID"
done
echo "Esperando a que las sagas terminen y notificaciones-service procese los eventos..."
sleep 8

separador "7. Historial de pagos de la cuenta $CUENTA_A visto desde el canal web"
curl -s -k -H "Authorization: Bearer $TOKEN" "$WEB/api/web/cuentas/$CUENTA_A/pagos" \
  | jq -c '.[] | {tipo, monto, estado, cuentaDestino, glosa}'

echo
echo "=== Fin de las pruebas de transferencias ==="
