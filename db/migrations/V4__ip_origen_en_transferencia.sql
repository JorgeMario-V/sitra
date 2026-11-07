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
