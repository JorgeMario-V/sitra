-- =====================================================================
-- SITRA — Servicio de Integración y Trazabilidad (Caso 5, cooperativa)
-- V1: esquema inicial — PostgreSQL 16
-- Requisitos evaluables: PK en toda tabla, FK con REFERENCES, CHECK,
-- UNIQUE, borrado lógico (activo) en toda entidad de negocio e índices.
-- =====================================================================

-- Sistemas y personas autorizadas a usar SITRA (la billetera y el auditor).
CREATE TABLE cliente_api (
    id            BIGSERIAL     PRIMARY KEY,
    nombre        VARCHAR(120)  NOT NULL,
    client_id     VARCHAR(80)   NOT NULL UNIQUE,
    secret_hash   VARCHAR(255)  NOT NULL,                 -- BCrypt, nunca el secreto en claro
    rol           VARCHAR(20)   NOT NULL DEFAULT 'BILLETERA'
                  CHECK (rol IN ('BILLETERA', 'AUDITOR')),
    activo        BOOLEAN       NOT NULL DEFAULT TRUE,    -- borrado lógico
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Lista blanca: únicas cuentas que se pueden exponer a la billetera (RF-07).
-- NO guarda saldo: el saldo siempre se lee en vivo del núcleo (RF-01).
CREATE TABLE cuenta_autorizada (
    id                 BIGSERIAL    PRIMARY KEY,
    numero_cuenta      VARCHAR(30)  NOT NULL UNIQUE,
    titular_documento  VARCHAR(30)  NOT NULL,
    tipo               VARCHAR(20)  NOT NULL
                       CHECK (tipo IN ('AHORROS', 'CORRIENTE')),
    activo             BOOLEAN      NOT NULL DEFAULT TRUE, -- borrado lógico
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Entidad principal de la HU-01 y del corte vertical.
CREATE TABLE transferencia (
    id                  BIGSERIAL      PRIMARY KEY,
    cliente_api_id      BIGINT         NOT NULL REFERENCES cliente_api(id),
    cuenta_origen_id    BIGINT         NOT NULL REFERENCES cuenta_autorizada(id),
    cuenta_destino      VARCHAR(30)    NOT NULL,
    monto               NUMERIC(14,2)  NOT NULL CHECK (monto > 0),
    estado              VARCHAR(20)    NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'FALLIDA', 'ANULADA')),
    referencia_externa  VARCHAR(80)    NOT NULL UNIQUE,   -- evita duplicados (RF-08, 409)
    comprobante_nucleo  VARCHAR(80),                      -- lo devuelve el núcleo al confirmar
    activo              BOOLEAN        NOT NULL DEFAULT TRUE, -- borrado lógico (RF-10)
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Bitácora de auditoría (RF-03, RF-05, AC-01 Seguridad).
-- No tiene campo "activo": es la evidencia; no se borra ni lógica ni físicamente.
CREATE TABLE registro_auditoria (
    id                BIGSERIAL    PRIMARY KEY,
    cliente_api_id    BIGINT       NOT NULL REFERENCES cliente_api(id),   -- quién
    transferencia_id  BIGINT       REFERENCES transferencia(id),          -- vacío en consultas de saldo
    numero_cuenta     VARCHAR(30)  NOT NULL,                              -- sobre qué cuenta
    operacion         VARCHAR(30)  NOT NULL                               -- qué
                      CHECK (operacion IN ('CONSULTA_SALDO', 'TRANSFERENCIA', 'ANULACION', 'CONSULTA_AUDITORIA')),
    resultado         VARCHAR(20)  NOT NULL                               -- con qué resultado
                      CHECK (resultado IN ('EXITOSA', 'RECHAZADA', 'FALLIDA')),
    ip_origen         VARCHAR(45)  NOT NULL,
    ocurrido_en       TIMESTAMP    NOT NULL DEFAULT NOW(),                -- cuándo
    hash_anterior     CHAR(64)     NOT NULL,                              -- huella del registro previo
    hash_actual       CHAR(64)     NOT NULL UNIQUE                        -- huella de este registro
);

-- Índices justificados por las consultas de las HU
CREATE INDEX idx_transferencia_cuenta  ON transferencia(cuenta_origen_id);            -- historial por cuenta
CREATE INDEX idx_transferencia_activas ON transferencia(estado) WHERE activo = TRUE;  -- listado sin anuladas
CREATE INDEX idx_auditoria_cuenta_fecha ON registro_auditoria(numero_cuenta, ocurrido_en); -- HU-03: auditor consulta por cuenta y rango de fechas (meta ≤ 5 s)
