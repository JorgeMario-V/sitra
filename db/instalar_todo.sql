-- =====================================================================
-- SITRA — INSTALACIÓN COMPLETA (MariaDB / XAMPP + HeidiSQL)
-- Abrir en HeidiSQL y presionar F9 UNA vez: borra y crea la base "sitra",
-- aplica las migraciones V1 a V4 y carga los datos de prueba.
-- Es la unión de db/migrations/V1..V4 + db/datos_prueba.sql (mismo contenido).
-- =====================================================================
DROP DATABASE IF EXISTS sitra;
CREATE DATABASE sitra CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sitra;

-- >>>>>>>>>>>>>>>>>>>> migrations/V1__esquema_inicial.sql <<<<<<<<<<<<<<<<<<<<
-- =====================================================================
-- SITRA — V1: tablas, llaves, CHECK, UNIQUE e índices
-- Motor: MariaDB 10.4+ (XAMPP). Ejecutar en orden: V1, V2, V3, V4.
-- =====================================================================

-- Quién usa SITRA: la billetera externa y el auditor
CREATE TABLE cliente_api (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre       VARCHAR(120)  NOT NULL,
    client_id    VARCHAR(80)   NOT NULL UNIQUE,
    secret_hash  VARCHAR(255)  NOT NULL,                              -- BCrypt, nunca el secreto
    rol          VARCHAR(20)   NOT NULL DEFAULT 'BILLETERA',
    activo       BOOLEAN       NOT NULL DEFAULT TRUE,                 -- borrado lógico
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cliente_rol CHECK (rol IN ('BILLETERA', 'AUDITOR'))
) ENGINE = InnoDB;

-- Asociado de la cooperativa
CREATE TABLE asociado (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    documento   VARCHAR(30)   NOT NULL UNIQUE,
    nombre      VARCHAR(120)  NOT NULL,
    activo      BOOLEAN       NOT NULL DEFAULT TRUE,                  -- borrado lógico
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB;

-- Cuenta (lista blanca de cuentas expuestas). NO guarda saldo: el saldo vive en el núcleo.
CREATE TABLE cuenta_autorizada (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    asociado_id    BIGINT       NOT NULL,
    numero_cuenta  VARCHAR(30)  NOT NULL UNIQUE,
    tipo           VARCHAR(20)  NOT NULL,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,                -- borrado lógico
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cuenta_asociado FOREIGN KEY (asociado_id) REFERENCES asociado(id),
    CONSTRAINT ck_cuenta_tipo CHECK (tipo IN ('AHORROS', 'CORRIENTE'))
) ENGINE = InnoDB;

-- Transacción (transferencia)
CREATE TABLE transferencia (
    id                  BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cliente_api_id      BIGINT         NOT NULL,
    cuenta_origen_id    BIGINT         NOT NULL,
    cuenta_destino      VARCHAR(30)    NOT NULL,
    monto               DECIMAL(14,2)  NOT NULL,
    estado              VARCHAR(20)    NOT NULL DEFAULT 'PENDIENTE',
    referencia_externa  VARCHAR(80)    NOT NULL,                      -- código de operación
    comprobante_nucleo  VARCHAR(80)    NULL,
    motivo_rechazo      VARCHAR(255)   NULL,                          -- por qué se rechazó
    activo              BOOLEAN        NOT NULL DEFAULT TRUE,          -- borrado lógico
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transf_cliente FOREIGN KEY (cliente_api_id)   REFERENCES cliente_api(id),
    CONSTRAINT fk_transf_cuenta  FOREIGN KEY (cuenta_origen_id) REFERENCES cuenta_autorizada(id),
    CONSTRAINT uq_transf_idempotencia UNIQUE (referencia_externa),    -- llave de idempotencia
    CONSTRAINT ck_transf_monto  CHECK (monto > 0),                    -- CHECK de monto > 0
    CONSTRAINT ck_transf_estado CHECK (estado IN ('PENDIENTE','CONFIRMADA','RECHAZADA','FALLIDA','ANULADA'))
) ENGINE = InnoDB;

-- Bitácora de auditoría (obligatoria: la trazabilidad es el atributo crítico).
-- Guarda también los intentos RECHAZADOS: código, monto intentado y motivo.
-- monto NO tiene CHECK: debe poder guardar lo que se intentó, aunque sea inválido (ej. -50.000).
CREATE TABLE registro_auditoria (
    id                  BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cliente_api_id      BIGINT         NOT NULL,                      -- quién
    transferencia_id    BIGINT         NULL,                          -- vacío si la transferencia no se pudo crear
    numero_cuenta       VARCHAR(30)    NOT NULL,                      -- sobre qué cuenta
    operacion           VARCHAR(30)    NOT NULL,                      -- qué
    resultado           VARCHAR(20)    NOT NULL,                      -- EXITOSA / RECHAZADA / FALLIDA
    referencia_externa  VARCHAR(80)    NULL,                          -- código de operación intentado
    monto               DECIMAL(14,2)  NULL,                          -- monto intentado
    motivo              VARCHAR(255)   NULL,                          -- por qué se rechazó
    ip_origen           VARCHAR(45)    NOT NULL DEFAULT 'local',
    ocurrido_en         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- cuándo
    hash_anterior       CHAR(64)       NULL,
    hash_actual         CHAR(64)       NULL UNIQUE,
    CONSTRAINT fk_audit_cliente FOREIGN KEY (cliente_api_id)   REFERENCES cliente_api(id),
    CONSTRAINT fk_audit_transf  FOREIGN KEY (transferencia_id) REFERENCES transferencia(id),
    CONSTRAINT ck_audit_operacion CHECK (operacion IN ('CONSULTA_SALDO','TRANSFERENCIA','ANULACION','CONSULTA_AUDITORIA')),
    CONSTRAINT ck_audit_resultado CHECK (resultado IN ('EXITOSA','RECHAZADA','FALLIDA'))
) ENGINE = InnoDB;

-- Simulador del núcleo legado: aquí vive el SALDO (SITRA no guarda saldos)
CREATE TABLE nucleo_simulado_cuenta (
    numero_cuenta  VARCHAR(30)    NOT NULL PRIMARY KEY,
    saldo          DECIMAL(14,2)  NOT NULL,
    CONSTRAINT ck_nucleo_saldo CHECK (saldo >= 0)
) ENGINE = InnoDB;

-- Índices justificados
CREATE INDEX idx_transferencia_cuenta   ON transferencia (cuenta_origen_id);
CREATE INDEX idx_auditoria_cuenta_fecha ON registro_auditoria (numero_cuenta, ocurrido_en);

-- >>>>>>>>>>>>>>>>>>>> migrations/V2__bitacora_inmutable.sql <<<<<<<<<<<<<<<<<<<<
-- =====================================================================
-- SITRA — V2: la bitácora la garantiza la base de datos (ADR-003)
--  * Huella SHA-256 encadenada en cada fila nueva.
--  * La bitácora no se puede modificar ni borrar.
--  * Toda transferencia que se inserte o cambie deja su fila, entre por
--    donde entre (la API, HeidiSQL u otro programa).
-- Motor: MariaDB 10.4+ (XAMPP). Ejecutar después de V1.
-- =====================================================================

DELIMITER //

-- Huella encadenada automática de cada fila nueva de la bitácora
CREATE TRIGGER trg_bitacora_huella
BEFORE INSERT ON registro_auditoria
FOR EACH ROW
BEGIN
    SET NEW.hash_anterior = COALESCE(
        (SELECT r.hash_actual FROM registro_auditoria r ORDER BY r.id DESC LIMIT 1),
        REPEAT('0', 64));
    SET NEW.hash_actual = SHA2(CONCAT_WS('|', NEW.hash_anterior, NEW.cliente_api_id,
        COALESCE(NEW.transferencia_id, '-'), NEW.numero_cuenta, NEW.operacion, NEW.resultado,
        COALESCE(NEW.referencia_externa, '-'), COALESCE(NEW.monto, '-'), COALESCE(NEW.motivo, '-'),
        NEW.ocurrido_en), 256);
END //

-- La bitácora NO se puede modificar
CREATE TRIGGER trg_bitacora_no_update
BEFORE UPDATE ON registro_auditoria
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'registro_auditoria no se puede modificar';
END //

-- La bitácora NO se puede borrar
CREATE TRIGGER trg_bitacora_no_delete
BEFORE DELETE ON registro_auditoria
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'registro_auditoria no se puede borrar';
END //

-- Toda transferencia que se guarda deja SIEMPRE su fila en la bitácora
CREATE TRIGGER trg_auditar_transferencia_insert
AFTER INSERT ON transferencia
FOR EACH ROW
BEGIN
    INSERT INTO registro_auditoria
        (cliente_api_id, transferencia_id, numero_cuenta, operacion, resultado,
         referencia_externa, monto, motivo, ocurrido_en)
    VALUES (NEW.cliente_api_id, NEW.id,
        (SELECT c.numero_cuenta FROM cuenta_autorizada c WHERE c.id = NEW.cuenta_origen_id),
        'TRANSFERENCIA',
        CASE NEW.estado WHEN 'FALLIDA' THEN 'FALLIDA' WHEN 'RECHAZADA' THEN 'RECHAZADA' ELSE 'EXITOSA' END,
        NEW.referencia_externa, NEW.monto, NEW.motivo_rechazo, NEW.created_at);
END //

-- Todo cambio de estado de una transferencia también queda en la bitácora
CREATE TRIGGER trg_auditar_transferencia_update
AFTER UPDATE ON transferencia
FOR EACH ROW
BEGIN
    INSERT INTO registro_auditoria
        (cliente_api_id, transferencia_id, numero_cuenta, operacion, resultado,
         referencia_externa, monto, motivo, ocurrido_en)
    VALUES (NEW.cliente_api_id, NEW.id,
        (SELECT c.numero_cuenta FROM cuenta_autorizada c WHERE c.id = NEW.cuenta_origen_id),
        IF(NEW.estado = 'ANULADA', 'ANULACION', 'TRANSFERENCIA'),
        CASE NEW.estado WHEN 'FALLIDA' THEN 'FALLIDA' WHEN 'RECHAZADA' THEN 'RECHAZADA' ELSE 'EXITOSA' END,
        NEW.referencia_externa, NEW.monto, NEW.motivo_rechazo, NOW());
END //

DELIMITER ;

-- >>>>>>>>>>>>>>>>>>>> migrations/V3__registro_de_rechazados.sql <<<<<<<<<<<<<<<<<<<<
-- =====================================================================
-- SITRA — V3: registro de los intentos RECHAZADOS (pedido en la Clase 4)
-- Procedimiento para operar y demostrar desde HeidiSQL. Hace lo mismo que
-- TransferenciaService (deuda técnica declarada en el ADR-003).
-- Motor: MariaDB 10.4+ (XAMPP). Ejecutar después de V2.
-- =====================================================================

DELIMITER //

-- ---------------------------------------------------------------------
-- PROCEDIMIENTO registrar_transferencia: hace lo mismo que TransferenciaService.
--  * Si la BASE DE DATOS rechaza el intento (código repetido, monto <= 0):
--    la transferencia no puede existir, así que el intento queda en la
--    BITÁCORA con resultado RECHAZADA, el código, el monto y el motivo.
--  * Si el NÚCLEO lo rechaza (saldo insuficiente): la transferencia se
--    guarda con ESTADO 'RECHAZADA' y su motivo (y el trigger la registra).
--  * Si todo está bien: se guarda CONFIRMADA y el núcleo descuenta el saldo.
-- p_fecha: hora de la operación (NULL = ahora).
-- ---------------------------------------------------------------------
CREATE PROCEDURE registrar_transferencia(
    IN p_cliente     VARCHAR(80),
    IN p_cuenta      VARCHAR(30),
    IN p_destino     VARCHAR(30),
    IN p_monto       DECIMAL(14,2),
    IN p_referencia  VARCHAR(80),
    IN p_fecha       DATETIME)
BEGIN
    DECLARE v_cliente BIGINT;
    DECLARE v_cuenta  BIGINT;
    DECLARE v_saldo   DECIMAL(14,2);
    DECLARE v_motivo  TEXT;
    DECLARE v_fecha   DATETIME;

    SET v_fecha = COALESCE(p_fecha, NOW());
    SELECT id INTO v_cliente FROM cliente_api       WHERE client_id     = p_cliente;
    SELECT id INTO v_cuenta  FROM cuenta_autorizada WHERE numero_cuenta = p_cuenta;

    BEGIN
        -- Rechazo de la BASE DE DATOS: se registra el intento en la bitácora
        DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1 v_motivo = MESSAGE_TEXT;
            INSERT INTO registro_auditoria
                (cliente_api_id, numero_cuenta, operacion, resultado,
                 referencia_externa, monto, motivo, ocurrido_en)
            VALUES (v_cliente, p_cuenta, 'TRANSFERENCIA', 'RECHAZADA',
                    p_referencia, p_monto, LEFT(v_motivo, 255), NOW());
            SELECT 'RECHAZADA' AS resultado, 'Bitácora' AS donde_queda, v_motivo AS motivo;
        END;

        SELECT saldo INTO v_saldo FROM nucleo_simulado_cuenta WHERE numero_cuenta = p_cuenta;

        IF p_monto > 0 AND v_saldo IS NOT NULL AND p_monto > v_saldo THEN
            -- Rechazo del NÚCLEO: la transacción queda con estado RECHAZADA
            INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, estado,
                                       referencia_externa, motivo_rechazo, created_at, updated_at)
            VALUES (v_cliente, v_cuenta, p_destino, p_monto, 'RECHAZADA',
                    p_referencia, 'Saldo insuficiente en el núcleo', v_fecha, v_fecha);
            SELECT 'RECHAZADA' AS resultado, 'Transacción (estado RECHAZADA) + bitácora' AS donde_queda,
                   'Saldo insuficiente en el núcleo' AS motivo;
        ELSE
            -- Camino feliz: se confirma y el núcleo descuenta el saldo
            INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, estado,
                                       referencia_externa, comprobante_nucleo, created_at, updated_at)
            VALUES (v_cliente, v_cuenta, p_destino, p_monto, 'CONFIRMADA',
                    p_referencia, CONCAT('NUC-', p_referencia), v_fecha, v_fecha);
            UPDATE nucleo_simulado_cuenta SET saldo = saldo - p_monto WHERE numero_cuenta = p_cuenta;
            SELECT 'EXITOSA' AS resultado, 'Transacción (estado CONFIRMADA) + bitácora' AS donde_queda,
                   CONCAT('Saldo restante en el núcleo: ',
                          (SELECT saldo FROM nucleo_simulado_cuenta WHERE numero_cuenta = p_cuenta)) AS motivo;
        END IF;
    END;
END //

DELIMITER ;

-- >>>>>>>>>>>>>>>>>>>> migrations/V4__ip_origen_en_transferencia.sql <<<<<<<<<<<<<<<<<<<<
-- =====================================================================
-- SITRA — V4: IP de origen en la transacción (cambio pedido en la Clase 4)
-- Motor: MariaDB 10.4+ (XAMPP). Ejecutar en orden: V1, V2, V3, V4.
-- =====================================================================

-- 1) La transacción guarda desde qué IP se originó
ALTER TABLE transferencia
    ADD COLUMN ip_origen VARCHAR(45) NULL AFTER referencia_externa;

-- 2) La bitácora copia esa IP; si no viene, toma la de la conexión
DROP TRIGGER IF EXISTS trg_bitacora_huella;
DROP TRIGGER IF EXISTS trg_auditar_transferencia_insert;
DROP TRIGGER IF EXISTS trg_auditar_transferencia_update;

DELIMITER //

CREATE TRIGGER trg_bitacora_huella
BEFORE INSERT ON registro_auditoria
FOR EACH ROW
BEGIN
    IF NEW.ip_origen IS NULL OR NEW.ip_origen = 'local' THEN
        SET NEW.ip_origen = SUBSTRING_INDEX(USER(), '@', -1);
    END IF;
    SET NEW.hash_anterior = COALESCE(
        (SELECT r.hash_actual FROM registro_auditoria r ORDER BY r.id DESC LIMIT 1),
        REPEAT('0', 64));
    SET NEW.hash_actual = SHA2(CONCAT_WS('|', NEW.hash_anterior, NEW.cliente_api_id,
        COALESCE(NEW.transferencia_id, '-'), NEW.numero_cuenta, NEW.operacion, NEW.resultado,
        COALESCE(NEW.referencia_externa, '-'), COALESCE(NEW.monto, '-'), COALESCE(NEW.motivo, '-'),
        NEW.ip_origen, NEW.ocurrido_en), 256);
END //

CREATE TRIGGER trg_auditar_transferencia_insert
AFTER INSERT ON transferencia
FOR EACH ROW
BEGIN
    INSERT INTO registro_auditoria
        (cliente_api_id, transferencia_id, numero_cuenta, operacion, resultado,
         referencia_externa, monto, motivo, ip_origen, ocurrido_en)
    VALUES (NEW.cliente_api_id, NEW.id,
        (SELECT c.numero_cuenta FROM cuenta_autorizada c WHERE c.id = NEW.cuenta_origen_id),
        'TRANSFERENCIA',
        CASE NEW.estado WHEN 'FALLIDA' THEN 'FALLIDA' WHEN 'RECHAZADA' THEN 'RECHAZADA' ELSE 'EXITOSA' END,
        NEW.referencia_externa, NEW.monto, NEW.motivo_rechazo, COALESCE(NEW.ip_origen, 'local'), NEW.created_at);
END //

CREATE TRIGGER trg_auditar_transferencia_update
AFTER UPDATE ON transferencia
FOR EACH ROW
BEGIN
    INSERT INTO registro_auditoria
        (cliente_api_id, transferencia_id, numero_cuenta, operacion, resultado,
         referencia_externa, monto, motivo, ip_origen, ocurrido_en)
    VALUES (NEW.cliente_api_id, NEW.id,
        (SELECT c.numero_cuenta FROM cuenta_autorizada c WHERE c.id = NEW.cuenta_origen_id),
        IF(NEW.estado = 'ANULADA', 'ANULACION', 'TRANSFERENCIA'),
        CASE NEW.estado WHEN 'FALLIDA' THEN 'FALLIDA' WHEN 'RECHAZADA' THEN 'RECHAZADA' ELSE 'EXITOSA' END,
        NEW.referencia_externa, NEW.monto, NEW.motivo_rechazo, COALESCE(NEW.ip_origen, 'local'), NOW());
END //

DELIMITER ;

-- >>>>>>>>>>>>>>>>>>>> datos_prueba.sql <<<<<<<<<<<<<<<<<<<<
-- =====================================================================
-- SITRA — Datos de prueba (solo laboratorio)
-- Credenciales de PRUEBA para pedir el token (POST /api/v1/auth/token):
--   billetera-externa    / billetera-2026   (rol BILLETERA)
--   auditor-cumplimiento / auditor-2026     (rol AUDITOR)
-- En la base solo se guarda el hash BCrypt del secreto, nunca el secreto.
-- =====================================================================

INSERT INTO cliente_api (nombre, client_id, secret_hash, rol) VALUES
  ('Billetera digital externa', 'billetera-externa',
   '$2a$10$s6tU60nyxHKIrJLWl8Bj/eSfC7v6cWTSAqCCzFo1Q/B70Wx27cC0m', 'BILLETERA'),
  ('Oficial de cumplimiento (auditor)', 'auditor-cumplimiento',
   '$2a$10$1t2f/XosiFMZ8IxlmUg6sOdhgyEp6NiJwkdMv9t3lIYkSWflsD4uu', 'AUDITOR');

INSERT INTO asociado (documento, nombre) VALUES
  ('71000001', 'Jorge Puerta'),
  ('43000002', 'Marta Restrepo');

-- Lista blanca: solo estas cuentas son visibles para la billetera
INSERT INTO cuenta_autorizada (asociado_id, numero_cuenta, tipo) VALUES
  ((SELECT id FROM asociado WHERE documento = '71000001'), 'AH-1001', 'AHORROS'),
  ((SELECT id FROM asociado WHERE documento = '43000002'), 'CO-2001', 'CORRIENTE');

-- El saldo vive en el núcleo (simulador), no en SITRA
INSERT INTO nucleo_simulado_cuenta (numero_cuenta, saldo) VALUES
  ('AH-1001', 2500000),
  ('CO-2001', 800000);
