# syntax=docker/dockerfile:1
# Build multi-stage del modulo batch (Parte 1 de la EFT). A diferencia de los microservicios,
# este contenedor NO queda corriendo: ejecuta el Job indicado como argumento y termina, con
# exit code 0 solo si el Job termino en COMPLETED.
#
#   docker compose -f docker-compose.yaml run --rm batch todos
#   docker compose -f docker-compose.yaml run --rm batch transacciones-particionado --batch.grid-size=6
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/banco-xyz-batch.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["todos"]
