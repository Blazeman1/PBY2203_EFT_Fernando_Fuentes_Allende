-- =============================================================================================
-- Inicializacion de PostgreSQL para el ecosistema Banco XYZ (EFT).
--
-- El contenedor oficial de postgres ejecuta este archivo UNA sola vez, la primera vez que se
-- crea el volumen de datos (directorio /docker-entrypoint-initdb.d), con el usuario definido en
-- POSTGRES_USER. Para volver a ejecutarlo hay que eliminar el volumen:
--     docker compose -f docker-compose.yaml down -v
--
-- Patron "database-per-service": cada microservicio es dueno exclusivo de SU base de datos. Se
-- usa un unico servidor PostgreSQL con cuatro bases de datos logicas (suficiente y economico
-- para el laboratorio); en produccion cada una seria una instancia administrada independiente
-- (por ejemplo, Amazon RDS), sin cambiar una linea de codigo: solo la URL de conexion.
--
-- El esquema se crea aqui, y no con "ddl-auto" de Hibernate, de forma deliberada: asi queda
-- creado antes de que arranque cualquier replica, y varias instancias de un mismo microservicio
-- pueden iniciar a la vez sin competir entre si por crear las mismas tablas.
-- =============================================================================================

CREATE DATABASE clientes_db;
CREATE DATABASE cuentas_db;
CREATE DATABASE pagos_db;
CREATE DATABASE batch_db;

-- ---------------------------------------------------------------------------------------------
-- clientes-service
-- ---------------------------------------------------------------------------------------------
\connect clientes_db

CREATE TABLE clientes (
    id         BIGINT       PRIMARY KEY,
    nombre     VARCHAR(120) NOT NULL,
    edad       INTEGER      NOT NULL CHECK (edad BETWEEN 18 AND 90),
    email      VARCHAR(160),
    telefono   VARCHAR(40),
    direccion  VARCHAR(200),
    estado     VARCHAR(20)  NOT NULL CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    creado_en  TIMESTAMP    NOT NULL,
    version    BIGINT
);

-- Los clientes migrados del legacy usan identificadores bajo 1000; los nuevos parten en 1001.
CREATE SEQUENCE cliente_id_seq START WITH 1001;

-- ---------------------------------------------------------------------------------------------
-- cuentas-service
-- ---------------------------------------------------------------------------------------------
\connect cuentas_db

CREATE TABLE cuentas (
    id              BIGINT           PRIMARY KEY,
    cliente_id      BIGINT           NOT NULL,   -- referencia logica a clientes-service (otra base: sin FK)
    tipo            VARCHAR(30)      NOT NULL,
    saldo           DOUBLE PRECISION NOT NULL CHECK (saldo >= 0),
    estado          VARCHAR(20)      NOT NULL CHECK (estado IN ('ACTIVA', 'CERRADA')),
    fecha_apertura  DATE,
    version         BIGINT
);
CREATE INDEX idx_cuentas_cliente ON cuentas (cliente_id);

CREATE TABLE movimientos (
    id               BIGSERIAL        PRIMARY KEY,
    cuenta_id        BIGINT           NOT NULL REFERENCES cuentas (id),
    fecha            DATE             NOT NULL,
    tipo_movimiento  VARCHAR(40)      NOT NULL,
    monto            DOUBLE PRECISION NOT NULL,
    descripcion      VARCHAR(255)
);
CREATE INDEX idx_movimientos_cuenta ON movimientos (cuenta_id);

-- Registro de idempotencia: una fila por cada operacion de dinero ya aplicada (pasos de la saga
-- y llamadas REST con idOperacion). La clave primaria es lo que impide aplicar dos veces la misma.
CREATE TABLE operaciones_procesadas (
    id         VARCHAR(120) PRIMARY KEY,
    resultado  VARCHAR(20)  NOT NULL,
    detalle    VARCHAR(500),
    creado_en  TIMESTAMP    NOT NULL
);

CREATE SEQUENCE cuenta_id_seq START WITH 1001;

-- ---------------------------------------------------------------------------------------------
-- pagos-service
-- ---------------------------------------------------------------------------------------------
\connect pagos_db

CREATE TABLE pagos (
    id              VARCHAR(36)      PRIMARY KEY,
    tipo            VARCHAR(30)      NOT NULL,
    cuenta_origen   BIGINT,
    cuenta_destino  BIGINT,
    monto           DOUBLE PRECISION NOT NULL CHECK (monto > 0),
    estado          VARCHAR(20)      NOT NULL,
    detalle         VARCHAR(500),
    glosa           VARCHAR(255),
    creado_en       TIMESTAMP        NOT NULL,
    actualizado_en  TIMESTAMP        NOT NULL,
    version         BIGINT
);
CREATE INDEX idx_pagos_origen  ON pagos (cuenta_origen);
CREATE INDEX idx_pagos_destino ON pagos (cuenta_destino);

-- batch_db queda vacia a proposito: el modulo batch (Parte 1) crea sus propias tablas al
-- arrancar (schema.sql + tablas de metadatos de Spring Batch).
