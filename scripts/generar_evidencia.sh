#!/usr/bin/env bash
# Orquesta una corrida COMPLETA de la EFT sobre docker-compose.yaml y guarda la evidencia de
# ejecucion en evidencias/ (un archivo .log por etapa). Es el mismo script que ejecuta el
# workflow de GitHub Actions (.github/workflows/evidencia-ejecucion.yml) y el que se usa en la
# instancia EC2 de AWS (ver despliegue.md).
#
#   01  construccion de las 12 imagenes Docker
#   02  levantamiento del stack                 03  estado de los contenedores (docker compose ps)
#   04  registro en Eureka
#   05  PARTE 1: procesos batch (Spring Batch)
#   06  PARTE 2: canales BFF + seguridad OAuth2 + api-gateway
#   07  PARTE 3: microservicios de clientes, cuentas y pagos
#   08  transferencias (saga con Kafka)        09  eventos consumidos por notificaciones-service
#   10  escalabilidad horizontal                11  tolerancia a fallos (Resilience4j)
#   logs-<servicio>.log  log completo de cada servicio
#
# Requiere: Docker con el plugin "docker compose", curl y jq. Puertos libres: 5432, 8081-8083,
# 8443, 8761, 8888, 9000 y 9094. Memoria recomendada para Docker: 8 GB.
#
# Uso:
#   bash scripts/generar_evidencia.sh                   # corrida completa (parte de datos limpios)
#   bash scripts/generar_evidencia.sh --skip-build      # reutiliza las imagenes ya construidas
#   bash scripts/generar_evidencia.sh --mantener        # deja el stack arriba al terminar
#   bash scripts/generar_evidencia.sh --sin-tolerancia  # omite la prueba que detiene servicios
set -uo pipefail

cd "$(dirname "$0")/.."
source scripts/_common.sh

SKIP_BUILD=false
MANTENER=false
SIN_TOLERANCIA=false
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=true ;;
    --mantener) MANTENER=true ;;
    --sin-tolerancia) SIN_TOLERANCIA=true ;;
    *) echo "Argumento desconocido: $arg (usar --skip-build, --mantener o --sin-tolerancia)" >&2; exit 1 ;;
  esac
done

for herramienta in docker curl jq; do
  if ! command -v "$herramienta" >/dev/null 2>&1; then
    echo "No se encontro '$herramienta' en el PATH. Requisitos: Docker (con 'docker compose'), curl y jq." >&2
    exit 1
  fi
done

mkdir -p evidencias
# evidencias/*.log se versiona a proposito (ver .gitignore). Se limpia ANTES de empezar para que
# la presencia de cada archivo refleje que ESTA corrida, y no una anterior, llego hasta esa etapa.
rm -f evidencias/*.log

TODOS="$COMPOSE --profile batch"
SERVICIOS="config-server eureka-server auth-server api-gateway clientes-service cuentas-service pagos-service notificaciones-service bff-web bff-mobile bff-atm kafka postgres"

guardar_logs() {
  echo "=== Guardando el log completo de cada servicio en evidencias/logs-<servicio>.log ==="
  local servicio
  for servicio in $SERVICIOS; do
    $COMPOSE logs --no-color "$servicio" > "evidencias/logs-$servicio.log" 2>&1 || true
  done
}

al_salir() {
  local codigo=$?
  guardar_logs
  if [ "$MANTENER" = true ]; then
    echo "=== El stack queda ARRIBA (--mantener). Para detenerlo: $COMPOSE down ==="
  else
    echo "=== Deteniendo el stack ($TODOS down) ==="
    $TODOS down --remove-orphans >/dev/null 2>&1 || true
  fi
  exit "$codigo"
}
trap al_salir EXIT

# Ejecuta una etapa de pruebas guardando su salida. Uso: etapa ARCHIVO_LOG DESCRIPCION comando...
ETAPAS_FALLIDAS=()
etapa() {
  local archivo="$1" descripcion="$2"
  shift 2
  echo
  echo "################################################################################"
  echo "# $descripcion"
  echo "################################################################################"
  "$@" 2>&1 | tee "evidencias/$archivo"
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "!!! La etapa '$descripcion' termino con error (ver evidencias/$archivo)" >&2
    ETAPAS_FALLIDAS+=("$descripcion")
    return 1
  fi
}

echo "=== Partiendo de un estado limpio (contenedores y datos de corridas anteriores) ==="
$TODOS down -v --remove-orphans >/dev/null 2>&1 || true

if [ "$SKIP_BUILD" = false ]; then
  echo "=== Construyendo las 12 imagenes Docker (multi-stage, desde el codigo fuente) ==="
  $TODOS build 2>&1 | tee evidencias/evidencia01-docker-build.log
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "La construccion de imagenes fallo, revisa evidencias/evidencia01-docker-build.log" >&2
    exit 1
  fi
fi

echo "=== Levantando el stack completo ==="
$COMPOSE up -d 2>&1 | tee evidencias/evidencia02-docker-up.log
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
  echo "docker compose up fallo, revisa evidencias/evidencia02-docker-up.log" >&2
  exit 1
fi

echo "=== Esperando a que todos los contenedores reporten 'healthy' ==="
esperar_healthy 150 || exit 1
$COMPOSE ps 2>&1 | tee evidencias/evidencia03-docker-ps.log

echo "=== Esperando el registro en Eureka y la disponibilidad de punta a punta ==="
{
  for nombre in CLIENTES-SERVICE CUENTAS-SERVICE PAGOS-SERVICE BFF-WEB BFF-MOBILE BFF-ATM API-GATEWAY; do
    esperar_instancias "$nombre" 1 60
  done
  esperar_instancias NOTIFICACIONES-SERVICE 2 60
  echo
  echo "--- Servicios registrados en Eureka (nombre: instancias) ---"
  curl -s -H "Accept: application/json" "$EUREKA/eureka/apps" \
    | jq -r '.applications.application[] | "\(.name): \([.instance] | flatten | length)"' | sort
} 2>&1 | tee evidencias/evidencia04-eureka.log
esperar_canales_listos 60 || exit 1

# Las etapas siguientes NO abortan la corrida si una falla: se registra la falla, se continua con
# las demas (para no perder su evidencia) y el script termina con codigo distinto de 0.
etapa evidencia05-parte1-batch.log            "PARTE 1 - Procesos batch (Spring Batch)"                     bash scripts/probar_batch.sh
etapa evidencia06-parte2-canales-bff.log      "PARTE 2 - Canales BFF, seguridad OAuth2 y api-gateway"       bash scripts/probar_apis.sh
etapa evidencia07-parte3-microservicios.log   "PARTE 3 - Microservicios de clientes, cuentas y pagos"       bash scripts/probar_microservicios.sh
etapa evidencia08-transferencias-saga.log     "PARTE 3 - Transferencias: saga por coreografia con Kafka"    bash scripts/probar_transferencias.sh

echo
echo "=== Eventos consumidos por notificaciones-service (2 replicas, mismo consumer group) ==="
{
  echo "--- Notificaciones de transferencias, por replica (particion y offset de cada mensaje) ---"
  $COMPOSE logs --no-color notificaciones-service 2>/dev/null | grep -F "Notificacion procesada" || echo "(ninguna)"
  echo
  echo "--- Pagos completados notificados (topico bancoxyz.pagos.completados) ---"
  $COMPOSE logs --no-color notificaciones-service 2>/dev/null | grep -F "Pago notificado" || echo "(ninguno)"
  echo
  echo "--- Alertas de seguridad procesadas en tiempo real (topico bancoxyz.seguridad.alertas) ---"
  $COMPOSE logs --no-color notificaciones-service 2>/dev/null | grep -F "ALERTA DE SEGURIDAD" || echo "(ninguna)"
  echo
  echo "--- Mensajes procesados por cada replica ---"
  $COMPOSE logs --no-color notificaciones-service 2>/dev/null | grep -E "Notificacion procesada|Pago notificado|ALERTA DE SEGURIDAD" \
    | awk '{print $1}' | sort | uniq -c || true
  echo
  echo "--- Topicos existentes en el broker ---"
  $COMPOSE exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | grep bancoxyz || true
} 2>&1 | tee evidencias/evidencia09-eventos-kafka.log

etapa evidencia10-escalabilidad-horizontal.log "Escalabilidad horizontal de los 3 microservicios"            bash scripts/probar_escalabilidad.sh 2

if [ "$SIN_TOLERANCIA" = false ]; then
  etapa evidencia11-tolerancia-fallos.log     "Tolerancia a fallos (Resilience4j: circuit breaker, retry y fallback)" bash scripts/probar_tolerancia_fallos.sh
fi

echo
echo "=== Evidencia generada en evidencias/: ==="
ls -1 evidencias/evidencia*.log
if [ "${#ETAPAS_FALLIDAS[@]}" -ne 0 ]; then
  echo >&2
  echo "ATENCION: ${#ETAPAS_FALLIDAS[@]} etapa(s) terminaron con error:" >&2
  printf '  - %s\n' "${ETAPAS_FALLIDAS[@]}" >&2
  exit 1
fi
echo "Todas las etapas terminaron correctamente."
