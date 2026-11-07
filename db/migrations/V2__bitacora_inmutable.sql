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
