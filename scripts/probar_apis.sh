#!/usr/bin/env bash
# Parte 2 de la EFT: ejercita los 3 canales (BFF web, movil y cajero), la seguridad distribuida
# (OAuth2 entre servicios, con minimo privilegio por scopes) y el enrutamiento del api-gateway.
#
# Requiere el stack de docker-compose.yaml arriba y "healthy", curl y jq.
# Uso: bash scripts/probar_apis.sh
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/_common.sh

separador "0. Seguridad distribuida: un microservicio NO responde sin un token OAuth2 valido"
interno -o /dev/null -w "GET cuentas-service/internal/cuentas SIN token -> HTTP %{http_code} (se espera 401)\n" "$CUENTAS_INT/internal/cuentas"
interno -o /dev/null -w "GET clientes-service/internal/clientes SIN token -> HTTP %{http_code} (se espera 401)\n" "$CLIENTES_INT/internal/clientes"
interno -o /dev/null -w "GET pagos-service/internal/pagos?cuentaId=$CUENTA_A SIN token -> HTTP %{http_code} (se espera 401)\n" "$PAGOS_INT/internal/pagos?cuentaId=$CUENTA_A"

separador "0.1 auth-server emite un access token via client_credentials (flujo maquina-a-maquina)"
preparar_token_interno
TOKEN_SERVICIO="$TOKEN_INTERNO"
echo "Token obtenido para $WEB_CLIENT_ID: ${TOKEN_SERVICIO:0:24}..."
echo "Scopes del token (claim 'scope' del JWT):"
echo "$TOKEN_SERVICIO" | cut -d. -f2 | tr '_-' '/+' | awk '{ l = length($0) % 4; if (l == 2) $0 = $0 "=="; else if (l == 3) $0 = $0 "="; print }' \
  | base64 -d 2>/dev/null | jq -c '{sub, scope, iss}' || echo "(no se pudo decodificar el JWT; no afecta al resto de las pruebas)"

separador "0.2 Con ese token, cuentas-service SI responde"
interno -H "Authorization: Bearer $TOKEN_SERVICIO" "$CUENTAS_INT/internal/cuentas/$CUENTA_A" | jq 'del(.movimientos)'

separador "0.3 Minimo privilegio: el token del canal MOVIL solo tiene el scope cuentas.access"
TOKEN_MOVIL_SERVICIO=$(token_oauth "$MOBILE_CLIENT_ID" "$MOBILE_CLIENT_SECRET" "cuentas.access")
verificar_token "token de servicio del canal movil" "$TOKEN_MOVIL_SERVICIO"
interno -o /dev/null -w "Token movil -> cuentas-service  -> HTTP %{http_code} (se espera 200)\n" \
  -H "Authorization: Bearer $TOKEN_MOVIL_SERVICIO" "$CUENTAS_INT/internal/cuentas/$CUENTA_A"
interno -o /dev/null -w "Token movil -> clientes-service -> HTTP %{http_code} (se espera 403: scope insuficiente)\n" \
  -H "Authorization: Bearer $TOKEN_MOVIL_SERVICIO" "$CLIENTES_INT/internal/clientes/$CUENTA_A"
interno -o /dev/null -w "Token movil -> pagos-service    -> HTTP %{http_code} (se espera 403: scope insuficiente)\n" \
  -H "Authorization: Bearer $TOKEN_MOVIL_SERVICIO" "$PAGOS_INT/internal/pagos?cuentaId=$CUENTA_A"
echo "auth-server ni siquiera emite un scope que el cliente no tiene registrado:"
curl -s -u "$MOBILE_CLIENT_ID:$MOBILE_CLIENT_SECRET" -d "grant_type=client_credentials" --data-urlencode "scope=pagos.access" \
  "$AUTH_SERVER/oauth2/token" | jq -c .

separador "1. BFF WEB: login (cuenta $CUENTA_A, titular '$NOMBRE_A') y vista COMPLETA de la cuenta"
TOKEN_WEB=$(login_web "$CUENTA_A" "$NOMBRE_A")
verificar_token "token web" "$TOKEN_WEB"
echo "Token web obtenido: ${TOKEN_WEB:0:24}..."
echo "(respuesta compuesta por el BFF a partir de cuentas-service + clientes-service; se muestran los"
echo " agregados y solo los 3 primeros movimientos del historial completo)"
curl -s -k -H "Authorization: Bearer $TOKEN_WEB" "$WEB/api/web/cuentas/$CUENTA_A" | jq '.movimientos |= .[0:3]'

separador "1.1 BFF WEB: historial filtrado por tipo=deposito (interfaz compleja; se muestran 5)"
curl -s -k -H "Authorization: Bearer $TOKEN_WEB" "$WEB/api/web/cuentas/$CUENTA_A/movimientos?tipo=deposito" | jq '.[0:5]'

separador "1.2 BFF WEB: perfil del titular (clientes-service)"
curl -s -k -H "Authorization: Bearer $TOKEN_WEB" "$WEB/api/web/cuentas/$CUENTA_A/titular" | jq .

separador "1.3 BFF WEB: un token valido NO puede consultar la cuenta de otra persona (autorizacion)"
curl -s -k -o /dev/null -w "GET /api/web/cuentas/$CUENTA_B con token de la cuenta $CUENTA_A -> HTTP %{http_code} (se espera 403)\n" \
  -H "Authorization: Bearer $TOKEN_WEB" "$WEB/api/web/cuentas/$CUENTA_B"
curl -s -k -o /dev/null -w "GET /api/web/cuentas/$CUENTA_A SIN token -> HTTP %{http_code} (se espera 401)\n" "$WEB/api/web/cuentas/$CUENTA_A"

separador "2. BFF MOVIL: login (cuenta $CUENTA_A + PIN) y resumen LIVIANO"
TOKEN_MOBILE=$(curl -s -k -X POST "$MOBILE/api/mobile/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"cuentaId\":$CUENTA_A,\"pin\":\"$PIN_A\"}" | jq -r .token)
verificar_token "token movil" "$TOKEN_MOBILE"
echo "Token movil obtenido: ${TOKEN_MOBILE:0:24}..."
curl -s -k -H "Authorization: Bearer $TOKEN_MOBILE" "$MOBILE/api/mobile/cuentas/$CUENTA_A/resumen" | jq .

separador "2.1 Optimizacion por canal: tamano de la respuesta para la MISMA cuenta"
BYTES_WEB=$(curl -s -k -H "Authorization: Bearer $TOKEN_WEB" "$WEB/api/web/cuentas/$CUENTA_A" | wc -c | tr -d ' ')
BYTES_MOBILE=$(curl -s -k -H "Authorization: Bearer $TOKEN_MOBILE" "$MOBILE/api/mobile/cuentas/$CUENTA_A/resumen" | wc -c | tr -d ' ')
echo "BFF WEB   (detalle completo + historial): $BYTES_WEB bytes"
echo "BFF MOVIL (saldo + 3 ultimos movimientos): $BYTES_MOBILE bytes"

separador "3. BFF CAJERO: apertura de sesion con tarjeta + PIN (cuenta $CUENTA_B, '$NOMBRE_B')"
SESION_JSON=$(curl -s -k -X POST "$ATM/api/atm/sesion" \
  -H "Content-Type: application/json" \
  -d "{\"numeroTarjeta\":\"$TARJETA_B\",\"pin\":\"$PIN_B\"}")
echo "$SESION_JSON" | jq .
TOKEN_ATM=$(echo "$SESION_JSON" | jq -r .sessionToken)
verificar_token "token de sesion ATM" "$TOKEN_ATM"

separador "3.1 BFF CAJERO: consulta de saldo (respuesta minima, sin nombre ni historial)"
curl -s -k -H "X-Atm-Session: $TOKEN_ATM" "$ATM/api/atm/cuentas/$CUENTA_B/saldo" | jq .

separador "3.2 BFF CAJERO: retiro de \$1.000 (operacion critica, debita en cuentas-service)"
curl -s -k -X POST "$ATM/api/atm/cuentas/$CUENTA_B/retiro" \
  -H "Content-Type: application/json" \
  -H "X-Atm-Session: $TOKEN_ATM" \
  -d '{"monto":1000}' | jq .

separador "3.3 BFF CAJERO: la sesion se invalida tras la operacion (debe fallar con HTTP 401)"
curl -s -k -o /dev/null -w "GET /api/atm/cuentas/$CUENTA_B/saldo reusando la sesion ya usada -> HTTP %{http_code} (se espera 401)\n" \
  -H "X-Atm-Session: $TOKEN_ATM" "$ATM/api/atm/cuentas/$CUENTA_B/saldo"

separador "3.4 BFF CAJERO: un retiro que excede el limite maximo por operacion debe rechazarse"
TOKEN_ATM_2=$(curl -s -k -X POST "$ATM/api/atm/sesion" -H "Content-Type: application/json" \
  -d "{\"numeroTarjeta\":\"$TARJETA_B\",\"pin\":\"$PIN_B\"}" | jq -r .sessionToken)
verificar_token "token de sesion ATM (segundo intento)" "$TOKEN_ATM_2"
curl -s -k -X POST "$ATM/api/atm/cuentas/$CUENTA_B/retiro" \
  -H "Content-Type: application/json" \
  -H "X-Atm-Session: $TOKEN_ATM_2" \
  -d '{"monto":999999}' | jq .

separador "3.5 BFF CAJERO: deposito de \$2.000 (procesado por pagos-service, que acredita en cuentas-service)"
TOKEN_ATM_3=$(curl -s -k -X POST "$ATM/api/atm/sesion" -H "Content-Type: application/json" \
  -d "{\"numeroTarjeta\":\"$TARJETA_B\",\"pin\":\"$PIN_B\"}" | jq -r .sessionToken)
verificar_token "token de sesion ATM (deposito)" "$TOKEN_ATM_3"
echo "Saldo antes del deposito: $(saldo_de $CUENTA_B)"
curl -s -k -X POST "$ATM/api/atm/cuentas/$CUENTA_B/deposito" \
  -H "Content-Type: application/json" \
  -H "X-Atm-Session: $TOKEN_ATM_3" \
  -d '{"monto":2000}' | jq .
echo "Saldo despues del deposito: $(saldo_de $CUENTA_B)"

separador "4. API GATEWAY: punto de entrada unico (puerto 8443), enruta por canal via Eureka"
echo "--- Tabla de rutas vigente (/actuator/gateway/routes) ---"
curl -s -k "$GATEWAY/actuator/gateway/routes" | jq -c '.[] | {route_id, uri, predicate}'
echo "--- Login del canal WEB a traves del gateway (notese el encabezado X-Banco-XYZ-Gateway) ---"
curl -s -k -D - -o /dev/null -X POST "$GATEWAY/api/web/auth/login" -H "Content-Type: application/json" \
  -d "{\"cuentaId\":$CUENTA_A,\"nombre\":\"$NOMBRE_A\"}" | tr -d '\r' | grep -i -E "^HTTP|x-banco-xyz-gateway" || true
TOKEN_GW=$(curl -s -k -X POST "$GATEWAY/api/web/auth/login" -H "Content-Type: application/json" \
  -d "{\"cuentaId\":$CUENTA_A,\"nombre\":\"$NOMBRE_A\"}" | jq -r .token)
verificar_token "token web via gateway" "$TOKEN_GW"
curl -s -k -o /dev/null -w "GET $GATEWAY/api/web/cuentas/$CUENTA_A (canal web via gateway)            -> HTTP %{http_code} (se espera 200)\n" \
  -H "Authorization: Bearer $TOKEN_GW" "$GATEWAY/api/web/cuentas/$CUENTA_A"
curl -s -k -o /dev/null -w "GET $GATEWAY/api/mobile/cuentas/$CUENTA_A/resumen (canal movil via gateway) -> HTTP %{http_code} (se espera 200)\n" \
  -H "Authorization: Bearer $TOKEN_MOBILE" "$GATEWAY/api/mobile/cuentas/$CUENTA_A/resumen"
curl -s -k -o /dev/null -w "POST $GATEWAY/api/atm/sesion (canal cajero via gateway)                -> HTTP %{http_code} (se espera 200)\n" \
  -X POST "$GATEWAY/api/atm/sesion" -H "Content-Type: application/json" -d "{\"numeroTarjeta\":\"$TARJETA_B\",\"pin\":\"$PIN_B\"}"
curl -s -k -o /dev/null -w "GET $GATEWAY/internal/cuentas (los microservicios NO tienen ruta en el gateway) -> HTTP %{http_code} (se espera 404)\n" \
  "$GATEWAY/internal/cuentas"

echo
echo "=== Fin de las pruebas de canales (Parte 2) ==="
