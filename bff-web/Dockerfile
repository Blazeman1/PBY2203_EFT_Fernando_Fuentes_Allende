# syntax=docker/dockerfile:1
# Build multi-stage: la imagen final solo contiene un JRE minimo y el JAR ya empaquetado.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Cache del repositorio local de Maven compartido entre los builds de todos los modulos: las
# dependencias se descargan una sola vez. "sharing=locked" evita escrituras concurrentes sobre
# el mismo repositorio cuando docker compose construye varias imagenes en paralelo.
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
# curl: usado por el healthcheck de docker-compose (/actuator/health).
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /build/target/bff-web.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
