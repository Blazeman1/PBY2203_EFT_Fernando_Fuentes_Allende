#!/usr/bin/env bash
# Funciones compartidas por los scripts de pruebas y de evidencia. No se ejecuta directamente:
# se importa con "source scripts/_common.sh" (desde la raiz del repositorio).
#
# Todos los scripts trabajan contra el stack levantado con docker-compose.yaml. Requieren, en la
# maquina desde donde se ejecutan: Docker (con el plugin "docker compose"), curl y jq. Funcionan
# igual en Linux, macOS y Windows (Git Bash).

# En Git Bash (Windows), MSYS convierte automaticamente a rutas de Windows los argumentos que
# parecen rutas POSIX ("/opt/kafka/bin/...", "/dev/null"), lo que rompe los comandos que se
# ejecutan DENTRO de un contenedor con "docker compose exec". Se desactiva esa conversion SOLO
# para docker (no de forma global: el curl del host si la necesita para "-o /dev/null").
docker() { MSYS_NO_PATHCONV=1 command docker "$@"; }

# Algunas compilaciones de jq para Windows terminan cada linea con CRLF: un token o un id leido
# con "jq -r" quedaria con un retorno de carro al final y romperia el encabezado o la URL donde
# se use. Quitar los "\r" es inocuo en Linux y macOS.
jq() { command jq "$@" | tr -d '\r'; }

# Ningun curl de las pruebas puede quedar esperando indefinidamente: si un servicio acepta la
# conexion pero no responde, el paso falla a los 30 s con un mensaje en vez de congelar el script.
curl() { command curl --connect-timeout 5 --max-time 30 "$@"; }

COMPOSE="docker compose -f docker-compose.yaml"

# Puntos de entrada publicados al host. Se usa 127.0.0.1 y no "localhost": en Windows con Docker
# Desktop (WSL 2), "localhost" se resuelve primero a ::1, que atiende un relay (wslrelay.exe)
# distinto del puerto IPv4 que publica Docker; ese relay puede dejar conexiones colgadas.
GATEWAY=https://127.0.0.1:8443
WEB=https://127.0.0.1:8081
MOBILE=https://127.0.0.1:8082
ATM=https://127.0.0.1:8083
AUTH_SERVER=http://127.0.0.1:9000
EUREKA=http://127.0.0.1:8761

# Direcciones INTERNAS de los 3 microservicios: solo existen dentro de la red de docker-compose
# (no publican puertos al host). Se usan a traves de la funcion "interno".
CLIENTES_INT=http://clientes-service:8086
CUENTAS_INT=http://cuentas-service:8080
PAGOS_INT=http://pagos-service:8087

# Credenciales OAuth2 de demostracion (las mismas de auth-server/src/main/resources/application.yml).
# Se usan aqui SOLO para verificar "por detras" el efecto de cada operacion, igual que lo haria un
# operador con acceso a la red interna; un frontend real nunca las conoce.
WEB_CLIENT_ID="bff-web-client"
WEB_CLIENT_SECRET="secreto-oauth2-bff-web-banco-xyz-no-usar-en-produccion-2026"
MOBILE_CLIENT_ID="bff-mobile-client"
MOBILE_CLIENT_SECRET="secreto-oauth2-bff-mobile-banco-xyz-no-usar-en-produccion-2026"

# Cuentas de demostracion (dataset oficial fin_legacy_data, semana_3, tras la validacion aplicada
# en la migracion): ver instrucciones.md, "Credenciales de demostracion".
CUENTA_A=101; NOMBRE_A="John Doe";   PIN_A="7373"
CUENTA_B=105; NOMBRE_B="Jane Smith"; PIN_B="7665"; TARJETA_B="4915000000000105"

separador() { echo; echo "=== $1 ==="; }

# Ejecuta curl DENTRO de la red de docker-compose (desde el contenedor de auth-server, que ya
# trae curl). Es la unica forma de llegar a los 3 microservicios, que deliberadamente no publican
# puertos al host. Uso: interno [opciones de curl...] URL
interno() {
  $COMPOSE exec -T auth-server curl -s --connect-timeout 5 --max-time 30 "$@"
}

# Obtiene un access token OAuth2 (client_credentials). Uso: token_oauth CLIENT_ID SECRET "scope1 scope2"
token_oauth() {
  curl -s -u "$1:$2" -d "grant_type=client_credentials" --data-urlencode "scope=$3" \
    "$AUTH_SERVER/oauth2/token" | jq -r .access_token
}

# Token con los 3 scopes (el del canal web), para las verificaciones internas de los scripts.
# preparar_token_interno debe invocarse DIRECTAMENTE (no dentro de "$(...)", que corre en un
# subshell y perderia la variable) una vez al inicio de cada script; despues se usa $TOKEN_INTERNO.
TOKEN_INTERNO=""
preparar_token_interno() {
  TOKEN_INTERNO=$(token_oauth "$WEB_CLIENT_ID" "$WEB_CLIENT_SECRET" "cuentas.access clientes.access pagos.access")
  verificar_token "token de servicio (client_credentials)" "$TOKEN_INTERNO"
}

# Saldo actual de una cuenta, leido directamente de cuentas-service (fuente de verdad).
# Requiere haber llamado antes a preparar_token_interno.
saldo_de() {
  interno -H "Authorization: Bearer $TOKEN_INTERNO" "$CUENTAS_INT/internal/cuentas/$1" | jq -r .saldo
}

# Corta la ejecucion con un mensaje claro si un login no devolvio un token valido, en vez de
# seguir con "Bearer null" y fallar varios pasos despues con errores dificiles de relacionar.
verificar_token() {
  local nombre="$1" valor="$2"
  if [ -z "$valor" ] || [ "$valor" = "null" ]; then
    echo "ERROR: no se obtuvo un $nombre valido. Revise 'docker compose -f docker-compose.yaml ps'" >&2
    echo "y los logs del servicio correspondiente." >&2
    exit 1
  fi
}

# Login del canal web. Uso: login_web CUENTA "NOMBRE" -> imprime el JWT (o "null").
login_web() {
  curl -s -k -X POST "$WEB/api/web/auth/login" -H "Content-Type: application/json" \
    -d "{\"cuentaId\":$1,\"nombre\":\"$2\"}" | jq -r .token
}

# Espera a que TODOS los contenedores de larga duracion reporten "healthy".
esperar_healthy() {
  local intentos="${1:-120}" intento total saludables
  for ((intento = 1; intento <= intentos; intento++)); do
    total=$($COMPOSE ps -q | wc -l | tr -d ' ')
    saludables=$($COMPOSE ps | grep -c "(healthy)" || true)
    if [ "$total" -gt 0 ] && [ "$saludables" -ge "$total" ]; then
      echo "Los $total contenedores estan 'healthy' (intento $intento/$intentos)."
      return 0
    fi
    echo "Saludables: $saludables/$total (intento $intento/$intentos)..."
    sleep 5
  done
  echo "No todos los contenedores llegaron a 'healthy' a tiempo." >&2
  $COMPOSE ps >&2
  return 1
}

# Cantidad de instancias que Eureka tiene registradas para un servicio (nombre en MAYUSCULAS).
instancias_en_eureka() {
  curl -s -H "Accept: application/json" "$EUREKA/eureka/apps" 2>/dev/null \
    | jq -r --arg n "$1" '[.applications.application[]? | select(.name == $n) | .instance] | flatten | length' 2>/dev/null \
    || echo 0
}

# Espera a que un servicio tenga, como minimo, N instancias registradas en Eureka.
# Uso: esperar_instancias NOMBRE N [intentos]
esperar_instancias() {
  local nombre="$1" minimo="$2" intentos="${3:-60}" intento actuales
  for ((intento = 1; intento <= intentos; intento++)); do
    actuales=$(instancias_en_eureka "$nombre")
    if [ "${actuales:-0}" -ge "$minimo" ]; then
      echo "$nombre: $actuales instancia(s) registrada(s) en Eureka."
      return 0
    fi
    sleep 2
  done
  echo "$nombre: solo ${actuales:-0} de $minimo instancia(s) esperadas tras ~$((intentos * 2))s." >&2
  return 1
}

# Un contenedor "healthy" ya responde HTTP, pero eso no garantiza que su registro en Eureka se
# haya propagado a la copia local de cada cliente (@LoadBalanced y el gateway resuelven por esa
# copia). Esta funcion espera activamente lo unico que de verdad importa: que el flujo completo
# canal -> BFF -> microservicios responda, tanto directo como a traves del api-gateway.
esperar_canales_listos() {
  local intentos="${1:-60}" intento cuerpo codigo_directo codigo_gateway
  local login="{\"cuentaId\":$CUENTA_A,\"nombre\":\"$NOMBRE_A\"}"
  echo "Esperando a que los canales respondan de punta a punta (BFF y api-gateway)..."
  for ((intento = 1; intento <= intentos; intento++)); do
    # --max-time evita que un servicio que acepta la conexion pero no responde congele el script.
    cuerpo=$(curl -s -k --max-time 10 -w $'\n%{http_code}' -X POST "$WEB/api/web/auth/login" \
      -H "Content-Type: application/json" -d "$login" 2>/dev/null || true)
    codigo_directo=$(printf '%s' "$cuerpo" | tail -n1)
    codigo_gateway=$(curl -s -k --max-time 10 -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/web/auth/login" \
      -H "Content-Type: application/json" -d "$login" 2>/dev/null || true)
    if [ "$codigo_directo" = "200" ] && [ "$codigo_gateway" = "200" ]; then
      echo "Canales listos (intento $intento/$intentos)."
      return 0
    fi
    if (( intento % 5 == 0 )); then
      echo "  intento $intento/$intentos: login directo bff-web=HTTP ${codigo_directo:-000}, via api-gateway=HTTP ${codigo_gateway:-000}"
    fi
    sleep 3
  done
  echo "Los canales no respondieron de punta a punta tras ~$((intentos * 3))s." >&2
  echo "Ultima respuesta del login directo a bff-web (HTTP ${codigo_directo:-000}):" >&2
  printf '%s\n' "$cuerpo" | sed '$d' | head -c 1500 >&2; echo >&2
  echo "Revise: $COMPOSE logs --tail=80 bff-web cuentas-service clientes-service api-gateway" >&2
  return 1
}

# Espera a que una transferencia deje de estar EN_PROCESO: el POST responde 202 de inmediato y
# la saga (eventos Kafka entre pagos-service y cuentas-service) se resuelve unos instantes despues.
# Imprime el JSON del ultimo estado visto. Uso: esperar_estado_transferencia TOKEN_WEB ID [intentos]
esperar_estado_transferencia() {
  local token="$1" transferencia_id="$2" intentos="${3:-30}"
  local intento respuesta estado
  for ((intento = 1; intento <= intentos; intento++)); do
    respuesta=$(curl -s -k -H "Authorization: Bearer $token" "$WEB/api/web/transferencias/$transferencia_id")
    estado=$(echo "$respuesta" | jq -r .estado 2>/dev/null || true)
    if [ "$estado" != "EN_PROCESO" ] && [ -n "$estado" ] && [ "$estado" != "null" ]; then
      echo "$respuesta"
      return 0
    fi
    sleep 1
  done
  echo "$respuesta"
  echo "La transferencia $transferencia_id sigue EN_PROCESO tras $intentos intentos (~${intentos}s)." >&2
  return 1
}
