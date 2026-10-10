# Instrucciones para ejecutar y probar cada componente

Banco XYZ · Evaluación Final Transversal · PBY2203 Desarrollo Backend III

Este documento explica cómo levantar el sistema y cómo comprobar, componente por componente, que funciona. Todo se ejecuta con Docker: no hace falta instalar Java, Maven, PostgreSQL ni Kafka.

- [1. Requisitos](#1-requisitos)
- [2. Levantar el sistema completo](#2-levantar-el-sistema-completo)
- [3. Corrida automática de todas las pruebas](#3-corrida-automática-de-todas-las-pruebas)
- [4. Datos y credenciales de demostración](#4-datos-y-credenciales-de-demostración)
- [5. Parte 1: procesos batch](#5-parte-1-procesos-batch)
- [6. Parte 2: los tres canales (BFF)](#6-parte-2-los-tres-canales-bff)
- [7. Parte 3: microservicios](#7-parte-3-microservicios)
- [8. Infraestructura Spring Cloud](#8-infraestructura-spring-cloud)
- [9. Escalabilidad horizontal](#9-escalabilidad-horizontal)
- [10. Tolerancia a fallos](#10-tolerancia-a-fallos)
- [11. Desarrollo y depuración desde el IDE](#11-desarrollo-y-depuración-desde-el-ide)
- [12. Detener y limpiar](#12-detener-y-limpiar)
- [13. Problemas frecuentes](#13-problemas-frecuentes)

---

## 1. Requisitos

| Herramienta | Para qué | Notas |
|---|---|---|
| **Docker** con el plugin `docker compose` | Construir y ejecutar todo | Docker Desktop en Windows y macOS; Docker Engine en Linux |
| **curl** y **jq** | Ejecutar los scripts de prueba | En Windows vienen con Git Bash (jq: `winget install jqlang.jq`) |
| **Git Bash** (solo Windows) | Ejecutar los scripts `.sh` | Abrir la aplicación *Git Bash*; no ejecutar `bash` desde PowerShell |

**Memoria.** El sistema levanta 13 contenedores (15 al escalar). En Docker Desktop, asignar al menos **8 GB** de memoria en *Settings → Resources*.

**Puertos que deben estar libres** en el computador: `5432`, `8081`, `8082`, `8083`, `8443`, `8761`, `8888`, `9000` y `9094`.

Todos los comandos se ejecutan desde la raíz del repositorio.

## 2. Levantar el sistema completo

```bash
docker compose -f docker-compose.yaml up -d --build
```

La primera vez tarda entre 10 y 15 minutos, porque cada imagen compila su módulo con Maven dentro de Docker y descarga sus dependencias. Las veces siguientes toma uno o dos minutos.

Comprobar que todo quedó arriba:

```bash
docker compose -f docker-compose.yaml ps
```

Cada servicio debe aparecer como `(healthy)`. El orden de arranque lo resuelve Docker Compose: primero la infraestructura, luego los microservicios, después los BFF y al final el gateway.

| Qué | Dirección |
|---|---|
| Punto de entrada único (api-gateway) | `https://localhost:8443` |
| Canal web · móvil · cajero (acceso directo) | `https://localhost:8081` · `:8082` · `:8083` |
| Panel de Eureka | <http://localhost:8761> |
| Servidor de configuración | `http://localhost:8888` |
| Servidor de autorización | `http://localhost:9000` |
| PostgreSQL | `localhost:5432` (usuario `bancoxyz`, clave `bancoxyz`) |

Los tres microservicios de negocio **no publican puertos** a propósito: solo son alcanzables dentro de la red interna de Docker. La sección 7 explica cómo llamarlos.

Los certificados HTTPS son autofirmados, de uso académico: `curl` necesita la opción `-k` y el navegador mostrará una advertencia.

## 3. Corrida automática de todas las pruebas

Un solo comando construye, levanta, ejecuta todas las pruebas de este documento y guarda la salida de cada etapa en `evidencias/`:

```bash
bash scripts/generar_evidencia.sh
```

| Opción | Efecto |
|---|---|
| `--skip-build` | Reutiliza las imágenes ya construidas |
| `--mantener` | Deja el sistema arriba al terminar, en vez de detenerlo |
| `--sin-tolerancia` | Omite la prueba que detiene servicios a propósito |

El script parte siempre de datos limpios: elimina los contenedores y los datos de corridas anteriores.

Cada prueba puede ejecutarse también por separado, con el sistema ya arriba:

| Script | Qué prueba | Sección |
|---|---|---|
| `scripts/probar_batch.sh` | Los procesos batch y sus resultados | 5 |
| `scripts/probar_apis.sh` | Los tres canales, OAuth2 y el gateway | 6 |
| `scripts/probar_microservicios.sh` | Clientes, cuentas y pagos | 7 |
| `scripts/probar_transferencias.sh` | La saga de transferencias sobre Kafka | 7.4 |
| `scripts/probar_escalabilidad.sh` | Escalado a 2 réplicas | 9 |
| `scripts/probar_tolerancia_fallos.sh` | Circuit breaker, reintentos y recuperación | 10 |

## 4. Datos y credenciales de demostración

Al arrancar por primera vez, `clientes-service` y `cuentas-service` migran a sus bases de datos los registros válidos del dataset oficial del caso ([fin_legacy_data](https://github.com/KariVillagran/fin_legacy_data), carpeta `data/semana_3`): 50 cuentas con sus titulares y 836 movimientos. Los registros con errores (tipo de cuenta no soportado, edad fuera de rango, saldo vacío, fechas irreconocibles) se omiten y se contabilizan en el log.

Cuentas que usan los scripts de prueba:

| Cuenta | Titular | Tipo | Saldo inicial | PIN (móvil y cajero) | Tarjeta (cajero) |
|---|---|---|---|---|---|
| **101** | John Doe | préstamo | 5.000 | `7373` | `4915000000000101` |
| **105** | Jane Smith | préstamo | 8.000 | `7665` | `4915000000000105` |
| 107 | John Doe | ahorro | 12.000 | `7811` | `4915000000000107` |
| 110 | Charlie Green | préstamo | 12.000 | `8030` | `4915000000000110` |

El dataset legacy no trae contraseñas, por lo que las credenciales se derivan de los propios datos, con fines de demostración:

- **Canal web:** número de cuenta + nombre del titular.
- **Canal móvil:** número de cuenta + PIN. El PIN es `(cuenta × 73) mod 10000`, con cuatro dígitos.
- **Cajero:** número de tarjeta + PIN. La tarjeta es `4915` seguido del número de cuenta con ceros a la izquierda hasta completar 12 dígitos.

Clientes OAuth2 registrados en `auth-server` (autenticación entre servicios):

| `client_id` | Scopes otorgados | Quién lo usa |
|---|---|---|
| `bff-web-client` | `cuentas.access` `clientes.access` `pagos.access` | Canal web |
| `bff-mobile-client` | `cuentas.access` | Canal móvil |
| `bff-atm-client` | `cuentas.access` `pagos.access` | Canal cajero |
| `pagos-service-client` | `cuentas.access` | `pagos-service`, para mover saldos |
| `cuentas-service-client` | `clientes.access` | `cuentas-service`, para validar al titular |

Los secretos están en `auth-server/src/main/resources/application.yml`. Son valores de demostración.

---

## 5. Parte 1: procesos batch

El módulo `batch` migra a Spring Batch los tres procesos del sistema legacy. Lee los tres archivos del dataset oficial (1.000 filas cada uno), incluidos en `batch/src/main/resources/data/`, y escribe en la base de datos `batch_db`.

| Argumento | Job | Archivo de entrada |
|---|---|---|
| `transacciones` | Reporte de transacciones diarias | `movimientos_financieros_diarios.csv` |
| `intereses` | Cálculo de intereses | `intereses_trimestrales.csv` |
| `cuentas-anuales` | Estados de cuenta anuales | `estados_financieros_anuales.csv` |
| `todos` | Los tres anteriores, en ese orden | |
| `transacciones-particionado` | Transacciones diarias con particionado | `movimientos_financieros_diarios.csv` |

### 5.1 Ejecutar

El contenedor `batch` no queda corriendo: ejecuta el Job indicado y termina. Su código de salida es `0` solo si el Job terminó en estado `COMPLETED`.

```bash
# Los tres Jobs
docker compose -f docker-compose.yaml --profile batch run --rm batch todos

# Un Job específico
docker compose -f docker-compose.yaml --profile batch run --rm batch intereses

# Modo particionado, con 6 particiones
docker compose -f docker-compose.yaml --profile batch run --rm batch transacciones-particionado --batch.grid-size=6
```

Para verlo todo de una vez, con un resumen legible y las consultas de resultados:

```bash
bash scripts/probar_batch.sh
```

### 5.2 Qué observar en el log

| Línea del log | Qué demuestra |
|---|---|
| `[Batch-Thread-1]`, `[Batch-Thread-2]`, `[Batch-Thread-3]` | Tres hilos procesando chunks en paralelo |
| `Particion[0] asignada: filas 1 a 250` | Reparto del archivo en particiones independientes |
| `Registro omitido (12/900 permitidas) por InvalidDataException` | Política de omisión: un registro inválido no detiene el proceso |
| `fecha corregida de formato legacy` | Corrección automática de datos recuperables |
| `Intento 2/3 tras falla transitoria` | Política de reintento ante una caída momentánea de la base de datos |
| `[ControlCalidadDecider] ... omitidos de ... intentados` | Control de calidad por porcentaje de omisiones |
| `FIN JOB [...] - estado=COMPLETED` | Resumen del Job: leídos, escritos, omitidos y duración |

### 5.3 Parámetros ajustables

Se pasan por línea de comandos, sin recompilar:

| Parámetro | Valor por defecto | Efecto |
|---|---|---|
| `--batch.chunk-size` | 5 | Registros por transacción |
| `--batch.hilos` | 3 | Hilos del modo multi-hilo |
| `--batch.grid-size` | 4 | Particiones del modo particionado |
| `--batch.limite-omisiones` | 900 | Tope de registros omitidos antes de fallar |
| `--batch.maximo-reintentos` | 3 | Reintentos por registro ante una falla transitoria |
| `--batch.umbral-calidad-porcentaje` | 85 | Porcentaje de omisiones sobre el cual no se genera el reporte |
| `--batch.reejecucion-max-intentos` | 2 | Reejecuciones automáticas de un Job que falló |
| `--runId` | marca de tiempo | Identifica la ejecución; repetirlo reanuda la misma instancia |

### 5.4 Probar las políticas de finalización y reejecución

**Control de calidad.** Con un umbral estricto, el Job de transacciones (que omite cerca del 23% de las filas) termina sin generar su reporte y queda derivado a revisión manual:

```bash
docker compose -f docker-compose.yaml --profile batch run --rm batch transacciones --batch.umbral-calidad-porcentaje=20
```

El log muestra `*** ATENCION: el proceso ... NO generara su reporte final` y el Job termina `COMPLETED` con estado de salida `REVISION_REQUERIDA`.

**Límite de omisiones y reejecución automática ante un fallo crítico.** Con un límite de omisiones irreal, el Step supera el tope y el Job termina `FAILED`:

```bash
docker compose -f docker-compose.yaml --profile batch run --rm batch cuentas-anuales \
  --batch.limite-omisiones=10 --batch.reejecucion-espera-ms=1000
```

El log muestra `Se alcanzo el limite de omisiones permitidas (10)` y luego `Reejecucion automatica del Job [...]`: el lanzador vuelve a ejecutar el Job con los mismos parámetros, y Spring Batch reanuda la **misma instancia** a partir de su metadata en vez de crear una nueva. Como en esta prueba la causa persiste, tras dos reejecuciones el proceso termina con código de salida distinto de `0`. Ante una falla pasajera (por ejemplo, una caída breve de la base de datos), actúan primero los reintentos por registro y, si no bastan, la reejecución completa el trabajo.

Después de esta prueba, volver a ejecutar `cuentas-anuales` sin parámetros para dejar cargados sus resultados (`scripts/probar_batch.sh` ya ejecuta las pruebas en el orden correcto).

### 5.5 Revisar los resultados

```bash
docker compose -f docker-compose.yaml exec postgres psql -U bancoxyz -d batch_db
```

```sql
-- Ejecuciones de Jobs registradas por Spring Batch
SELECT i.job_name, e.status, e.exit_code, e.start_time, e.end_time
FROM batch_job_execution e JOIN batch_job_instance i USING (job_instance_id) ORDER BY 4;

-- Leídos, escritos y omitidos por Step
SELECT step_name, read_count, write_count,
       read_skip_count + process_skip_count + write_skip_count AS omitidos
FROM batch_step_execution ORDER BY step_execution_id;

-- Resultados de negocio
SELECT * FROM resumen_transacciones_diarias;
SELECT * FROM cuentas_interes ORDER BY cuenta_id LIMIT 10;
SELECT * FROM estados_cuenta_anuales ORDER BY cuenta_id LIMIT 10;
SELECT * FROM transacciones_procesadas WHERE es_anomalia LIMIT 10;
```

---

## 6. Parte 2: los tres canales (BFF)

Prueba automática de los tres canales, la seguridad y el gateway:

```bash
bash scripts/probar_apis.sh
```

Los ejemplos siguientes llaman a cada BFF directamente. Todos funcionan igual a través del gateway, cambiando el puerto por `8443`.

### 6.1 Canal web (`bff-web`, puerto 8081)

Respuesta completa, pensada para una interfaz de escritorio. Sesión de 30 minutos.

```bash
# Iniciar sesión: devuelve un JWT
TOKEN=$(curl -sk -X POST https://localhost:8081/api/web/auth/login \
  -H "Content-Type: application/json" \
  -d '{"cuentaId":101,"nombre":"John Doe"}' | jq -r .token)

# Cuenta completa: titular, tasa, totales e historial de movimientos
curl -sk -H "Authorization: Bearer $TOKEN" https://localhost:8081/api/web/cuentas/101 | jq .

# Historial filtrado por tipo y por fechas
curl -sk -H "Authorization: Bearer $TOKEN" \
  "https://localhost:8081/api/web/cuentas/101/movimientos?tipo=deposito&desde=2024-01-01&hasta=2024-06-30" | jq .

# Perfil del titular, y actualización de sus datos de contacto
curl -sk -H "Authorization: Bearer $TOKEN" https://localhost:8081/api/web/cuentas/101/titular | jq .
curl -sk -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"telefono":"+56 9 1234 5678"}' https://localhost:8081/api/web/cuentas/101/titular | jq .

# Pagar un servicio y ver el historial de pagos
curl -sk -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"monto":300,"comercio":"Agua Potable"}' https://localhost:8081/api/web/cuentas/101/pagos | jq .
curl -sk -H "Authorization: Bearer $TOKEN" https://localhost:8081/api/web/cuentas/101/pagos | jq .
```

Las transferencias se muestran en la sección 7.4.

### 6.2 Canal móvil (`bff-mobile`, puerto 8082)

Respuesta liviana: saldo, tipo de cuenta y los tres últimos movimientos. Sesión de 5 minutos.

```bash
TOKEN_M=$(curl -sk -X POST https://localhost:8082/api/mobile/auth/login \
  -H "Content-Type: application/json" -d '{"cuentaId":101,"pin":"7373"}' | jq -r .token)

curl -sk -H "Authorization: Bearer $TOKEN_M" https://localhost:8082/api/mobile/cuentas/101/resumen | jq .
```

Comparar el tamaño de ambas respuestas para la misma cuenta muestra la optimización por canal:

```bash
curl -sk -H "Authorization: Bearer $TOKEN"   https://localhost:8081/api/web/cuentas/101            | wc -c
curl -sk -H "Authorization: Bearer $TOKEN_M" https://localhost:8082/api/mobile/cuentas/101/resumen | wc -c
```

### 6.3 Canal cajero (`bff-atm`, puerto 8083)

Solo operaciones críticas. La sesión dura 2 minutos, viaja en el encabezado `X-Atm-Session` y **se invalida después de cada retiro o depósito**.

```bash
# Abrir sesión con tarjeta y PIN
SESION=$(curl -sk -X POST https://localhost:8083/api/atm/sesion -H "Content-Type: application/json" \
  -d '{"numeroTarjeta":"4915000000000105","pin":"7665"}' | jq -r .sessionToken)

# Consultar saldo
curl -sk -H "X-Atm-Session: $SESION" https://localhost:8083/api/atm/cuentas/105/saldo | jq .

# Retirar
curl -sk -X POST -H "X-Atm-Session: $SESION" -H "Content-Type: application/json" \
  -d '{"monto":1000}' https://localhost:8083/api/atm/cuentas/105/retiro | jq .

# La sesión ya no sirve: responde 401. Para depositar hay que abrir otra.
curl -sk -o /dev/null -w "%{http_code}\n" -H "X-Atm-Session: $SESION" https://localhost:8083/api/atm/cuentas/105/saldo

SESION=$(curl -sk -X POST https://localhost:8083/api/atm/sesion -H "Content-Type: application/json" \
  -d '{"numeroTarjeta":"4915000000000105","pin":"7665"}' | jq -r .sessionToken)
curl -sk -X POST -H "X-Atm-Session: $SESION" -H "Content-Type: application/json" \
  -d '{"monto":2000}' https://localhost:8083/api/atm/cuentas/105/deposito | jq .
```

### 6.4 Controles de seguridad por canal

| Prueba | Resultado esperado |
|---|---|
| Llamar a un BFF sin token | `401` |
| Consultar la cuenta 105 con el token de la cuenta 101 | `403` |
| Transferir desde una cuenta que no es la propia | `403` |
| Reutilizar la sesión del cajero después de un retiro | `401` |
| Retirar en el cajero más del límite por operación (500.000) | `422` |
| Iniciar sesión con datos incorrectos | `401`, con el mismo mensaje exista o no la cuenta |

### 6.5 Gateway (`api-gateway`, puerto 8443)

```bash
# Tabla de rutas vigente
curl -sk https://localhost:8443/actuator/gateway/routes | jq '.[] | {route_id, uri, predicate}'

# El mismo login del canal web, entrando por el gateway
curl -sk -i -X POST https://localhost:8443/api/web/auth/login \
  -H "Content-Type: application/json" -d '{"cuentaId":101,"nombre":"John Doe"}'
```

La respuesta incluye el encabezado `X-Banco-XYZ-Gateway: api-gateway`, que confirma que pasó por el gateway. Las rutas `/internal/**` de los microservicios no existen en el gateway: responden `404`.

---

## 7. Parte 3: microservicios

Prueba automática del ciclo completo (alta de cliente, apertura de cuenta, mantenimiento, depósito, pago, transferencia, cierre y baja):

```bash
bash scripts/probar_microservicios.sh
```

### 7.1 Cómo llamar a un microservicio

Los microservicios no publican puertos, así que las llamadas se hacen **desde dentro de la red de Docker**, y con un token OAuth2. En Git Bash, Linux o macOS:

```bash
# Token con los tres scopes (credenciales del canal web)
TOKEN_S=$(curl -s -u "bff-web-client:secreto-oauth2-bff-web-banco-xyz-no-usar-en-produccion-2026" \
  -d grant_type=client_credentials --data-urlencode "scope=cuentas.access clientes.access pagos.access" \
  http://localhost:9000/oauth2/token | jq -r .access_token)

# Atajo: ejecuta curl dentro de la red interna
interno() { docker compose -f docker-compose.yaml exec -T auth-server curl -s "$@"; }
```

Con eso definido, los ejemplos de esta sección se pueden copiar tal cual.

### 7.2 Gestión de Clientes (`clientes-service`, puerto interno 8086)

| Operación | Método y ruta |
|---|---|
| Listar clientes | `GET /internal/clientes` |
| Obtener un cliente | `GET /internal/clientes/{id}` |
| Crear un cliente | `POST /internal/clientes` |
| Actualizar datos de contacto | `PUT /internal/clientes/{id}` |
| Desactivar · reactivar | `POST /internal/clientes/{id}/desactivar` · `/reactivar` |

```bash
interno -H "Authorization: Bearer $TOKEN_S" http://clientes-service:8086/internal/clientes/101 | jq .

interno -X POST -H "Authorization: Bearer $TOKEN_S" -H "Content-Type: application/json" \
  -d '{"nombre":"Camila Rojas","edad":34,"email":"camila.rojas@correo.example"}' \
  http://clientes-service:8086/internal/clientes | jq .
```

Actualizar el perfil o desactivar a un cliente publica una alerta de seguridad en Kafka (sección 7.5).

### 7.3 Gestión de Cuentas (`cuentas-service`, puerto interno 8080)

| Operación | Método y ruta |
|---|---|
| Listar cuentas (opcional: de un cliente) | `GET /internal/cuentas?clienteId=` |
| Obtener una cuenta con sus movimientos | `GET /internal/cuentas/{id}` |
| **Apertura** | `POST /internal/cuentas` |
| **Mantenimiento** (cambio de tipo) | `PUT /internal/cuentas/{id}` |
| **Cierre** (exige saldo cero) | `POST /internal/cuentas/{id}/cierre` |
| Debitar · acreditar | `POST /internal/cuentas/{id}/debito` · `/credito` |

```bash
# Apertura: cuentas-service valida al cliente en clientes-service antes de abrir
interno -X POST -H "Authorization: Bearer $TOKEN_S" -H "Content-Type: application/json" \
  -d '{"clienteId":101,"tipo":"ahorro","saldoInicial":50000}' http://cuentas-service:8080/internal/cuentas | jq .

# Cuentas del cliente 101
interno -H "Authorization: Bearer $TOKEN_S" "http://cuentas-service:8080/internal/cuentas?clienteId=101" | jq .
```

Tipos de cuenta admitidos: `ahorro`, `prestamo` y `corriente`.

### 7.4 Procesamiento de Pagos (`pagos-service`, puerto interno 8087)

| Operación | Método y ruta | Modalidad |
|---|---|---|
| **Transferencia** | `POST /internal/pagos/transferencias` | Asíncrona: responde `202` e inicia una saga en Kafka |
| Estado de una transferencia | `GET /internal/transferencias/{id}` | Sincrónica |
| **Depósito** | `POST /internal/pagos/depositos` | Sincrónica: llama a `cuentas-service` |
| **Pago de servicio** | `POST /internal/pagos/servicios` | Sincrónica: llama a `cuentas-service` |
| Obtener un pago | `GET /internal/pagos/{id}` | |
| Historial de una cuenta | `GET /internal/pagos?cuentaId=` | |

```bash
interno -X POST -H "Authorization: Bearer $TOKEN_S" -H "Content-Type: application/json" \
  -d '{"cuentaDestino":101,"monto":10000,"glosa":"Deposito en sucursal"}' \
  http://pagos-service:8087/internal/pagos/depositos | jq .
```

Un depósito o un pago responde `201` con el pago registrado; su campo `estado` indica el desenlace (`COMPLETADA` o `RECHAZADA`, con el motivo en `detalle`).

**Transferencias (saga con Kafka).** La prueba automática recorre los tres desenlaces posibles:

```bash
bash scripts/probar_transferencias.sh
```

| Escenario | Qué ocurre | Estado final |
|---|---|---|
| Transferencia válida | Se debita el origen y se acredita el destino | `COMPLETADA` |
| Monto mayor al saldo | No se llega a debitar | `RECHAZADA` |
| La cuenta destino no existe | Se debita el origen y luego se le devuelve el monto | `COMPENSADA` |

A mano, desde el canal web:

```bash
# Solicitar: responde 202 con el identificador
ID=$(curl -sk -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"cuentaDestino":105,"monto":500}' https://localhost:8081/api/web/cuentas/101/transferencias | jq -r .transferenciaId)

# Consultar el resultado
curl -sk -H "Authorization: Bearer $TOKEN" https://localhost:8081/api/web/transferencias/$ID | jq .
```

### 7.5 Eventos en Kafka

| Tópico | Lo publica | Lo consume | Contenido |
|---|---|---|---|
| `bancoxyz.transferencias.solicitadas` | `pagos-service` | `cuentas-service` | Inicio de la saga |
| `bancoxyz.transferencias.debito-aplicado` | `cuentas-service` | `cuentas-service` | Paso intermedio de la saga |
| `bancoxyz.transferencias.resultado` | `cuentas-service` | `pagos-service`, `notificaciones-service` | Transacción completada, rechazada o compensada |
| `bancoxyz.pagos.completados` | `pagos-service` | `notificaciones-service` | Depósito o pago completado |
| `bancoxyz.seguridad.alertas` | `pagos-service`, `clientes-service` | `notificaciones-service` | Alertas de seguridad |

```bash
# Tópicos existentes
docker compose -f docker-compose.yaml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Qué réplica consume cada partición
docker compose -f docker-compose.yaml exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group notificaciones-service

# Leer un tópico desde el principio (Ctrl+C para salir)
docker compose -f docker-compose.yaml exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic bancoxyz.seguridad.alertas --from-beginning

# Ver cómo notificaciones-service procesa los eventos en tiempo real
docker compose -f docker-compose.yaml logs -f notificaciones-service
```

Se genera una alerta de seguridad cuando una transferencia iguala o supera 1.000.000 (umbral configurable en `config-repo/pagos-service.yml`), y cuando se modifican los datos de contacto de un cliente o se lo desactiva.

### 7.6 Seguridad entre servicios (OAuth2)

```bash
# Sin token: 401
interno -o /dev/null -w "%{http_code}\n" http://cuentas-service:8080/internal/cuentas

# El token del canal móvil solo tiene el scope cuentas.access
TOKEN_MOVIL=$(curl -s -u "bff-mobile-client:secreto-oauth2-bff-mobile-banco-xyz-no-usar-en-produccion-2026" \
  -d grant_type=client_credentials -d scope=cuentas.access http://localhost:9000/oauth2/token | jq -r .access_token)

interno -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN_MOVIL" http://cuentas-service:8080/internal/cuentas/101   # 200
interno -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN_MOVIL" http://clientes-service:8086/internal/clientes/101 # 403
```

### 7.7 Registro de operaciones

Cada microservicio registra cada petición que atiende: método, ruta, código de respuesta, quién la hizo (el `client_id` del token) y cuánto tardó.

```bash
docker compose -f docker-compose.yaml logs cuentas-service | grep PETICION | tail
```

```
cuentas-service-1 | ... PETICION GET /internal/cuentas/101 -> HTTP 200 (cliente=bff-web-client, 12 ms)
```

---

## 8. Infraestructura Spring Cloud

| Componente | Cómo comprobarlo |
|---|---|
| **Config Server** | `curl -s http://localhost:8888/cuentas-service/docker \| jq '.propertySources[].name'` muestra los archivos de configuración que recibe ese servicio |
| **Eureka** | Abrir <http://localhost:8761>, o `curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps \| jq -r '.applications.application[].name'` |
| **Auth Server** | `curl -s http://localhost:9000/.well-known/oauth-authorization-server \| jq .` muestra los endpoints OAuth2; `curl -s http://localhost:9000/oauth2/jwks \| jq .` la clave pública con que se validan los tokens |
| **Gateway** | Sección 6.5 |

La configuración centralizada está en `config-server/src/main/resources/config-repo/`. El archivo `application.yml` de esa carpeta lo reciben todos los servicios (nombres de los tópicos y dirección del servidor de autorización); cada servicio recibe además su propio archivo.

## 9. Escalabilidad horizontal

```bash
bash scripts/probar_escalabilidad.sh
```

El script agrega réplicas con el sistema en marcha, genera carga y muestra cómo se reparte. A mano:

```bash
docker compose -f docker-compose.yaml up -d --no-recreate \
  --scale clientes-service=2 --scale cuentas-service=2 --scale pagos-service=2
```

Qué comprobar:

1. **Las réplicas se registran solas.** En el panel de Eureka aparecen dos instancias de cada servicio; nadie reconfigura a los clientes.
2. **Las peticiones REST se reparten.** `docker compose -f docker-compose.yaml logs cuentas-service | grep PETICION` muestra líneas de `cuentas-service-1` y de `cuentas-service-2`.
3. **Los eventos Kafka se reparten.** Cada tópico tiene 4 particiones; `kafka-consumer-groups.sh --describe --group cuentas-service` muestra que cada réplica atiende algunas.
4. **El saldo sigue siendo correcto.** Todas las réplicas comparten la misma base de datos y usan bloqueo de fila.

Para volver a una réplica: repetir el comando con `=1`.

## 10. Tolerancia a fallos

```bash
bash scripts/probar_tolerancia_fallos.sh
```

El script detiene servicios a propósito y muestra cómo responde el resto:

| Falla provocada | Comportamiento alternativo |
|---|---|
| `clientes-service` caído | La web sigue mostrando la cuenta (`200`), con el titular marcado como no disponible. El móvil y el cajero no se ven afectados |
| `cuentas-service` caído | La web responde `503` con un mensaje claro. Tras 5 fallas el circuito se abre y responde de inmediato, sin intentar la llamada |
| `cuentas-service` caído durante un depósito | `pagos-service` reintenta 3 veces, registra el pago como `FALLIDA` y responde `503` |
| `cuentas-service` caído durante una transferencia | La transferencia se acepta (`202`) y queda en Kafka; se procesa cuando el servicio vuelve |
| El servicio vuelve | El circuito se cierra solo y todo opera con normalidad |

A mano:

```bash
docker compose -f docker-compose.yaml stop cuentas-service
curl -sk -H "Authorization: Bearer $TOKEN" https://localhost:8081/api/web/cuentas/101   # 503 controlado
docker compose -f docker-compose.yaml start cuentas-service
```

## 11. Desarrollo y depuración desde el IDE

Para ejecutar los módulos desde IntelliJ IDEA (con puntos de interrupción), se levanta solo la infraestructura de datos en Docker y el resto desde el IDE. Requiere JDK 21.

```bash
docker compose -f docker-compose.yaml down          # ambos archivos publican el puerto 5432
docker compose -f docker-compose.infra.yml up -d    # PostgreSQL en 5432 y Kafka en 9092
```

Luego, en el panel *Services* de IntelliJ, iniciar las aplicaciones en este orden, esperando a que cada grupo termine de arrancar:

1. `ConfigServerApplication`, `EurekaServerApplication`, `AuthServerApplication`
2. `ClientesServiceApplication`, `CuentasServiceApplication`, `PagosServiceApplication`
3. `BffWebApplication`, `BffMobileApplication`, `BffAtmApplication`, `NotificacionesServiceApplication`
4. `ApiGatewayApplication`

En este modo los microservicios sí escuchan en `localhost` (8086, 8080 y 8087), por lo que se pueden llamar con `curl` directamente. Los scripts de `scripts/` están pensados para el sistema levantado con `docker-compose.yaml`.

Para compilar todos los módulos sin Docker: `mvn -DskipTests package` desde la raíz.

## 12. Detener y limpiar

```bash
docker compose -f docker-compose.yaml down        # detiene; conserva los datos
docker compose -f docker-compose.yaml down -v     # detiene y borra los datos (vuelve al estado inicial)
```

## 13. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `port is already allocated` | Otro programa usa uno de los puertos de la sección 1. Cerrarlo o detener el contenedor que lo ocupa |
| Un contenedor se reinicia o no llega a `healthy` | Casi siempre es memoria insuficiente: asignar 8 GB a Docker. Revisar con `docker compose -f docker-compose.yaml logs <servicio>` |
| `relation "cuentas" does not exist` | El volumen de PostgreSQL viene de una versión anterior del esquema. Ejecutar `down -v` y levantar de nuevo |
| Los scripts fallan con `execvpe(/bin/bash) failed` en Windows | Se ejecutó `bash` desde PowerShell, que apunta a WSL. Abrir la aplicación *Git Bash* |
| `\r: command not found` al ejecutar un script | El repositorio se clonó con finales de línea de Windows. `git config core.autocrlf input` y volver a clonar |
| `No servers available for service` justo después de levantar | El registro en Eureka aún no se propaga. Esperar unos segundos |
| El login responde `401` | Revisar la tabla de credenciales de la sección 4: el nombre debe coincidir con el titular de la cuenta |
