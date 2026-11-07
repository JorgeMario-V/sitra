-- =====================================================================
-- SITRA — Demostración en vivo (MariaDB / XAMPP + HeidiSQL)
-- Requisito: haber ejecutado db/instalar_todo.sql (F9) una vez.
-- NO usar F9 aquí. Seleccionar cada bloque y presionar Ctrl+F9.
-- =====================================================================
USE sitra;

-- ============================ PASO 1 — Transferencia insertada por SQL ============================
-- Aunque entre por HeidiSQL y no por la API, el trigger deja la evidencia (ADR-003).
-- Transferencia de 300.000 hacia la billetera externa, hoy a la 1:15 p. m.
INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, estado,
                           referencia_externa, comprobante_nucleo, created_at, updated_at)
VALUES ((SELECT id FROM cliente_api WHERE client_id = 'billetera-externa'),
        (SELECT id FROM cuenta_autorizada WHERE numero_cuenta = 'AH-1001'),
        'BILLETERA-EXTERNA', 300000, 'CONFIRMADA', 'OP-20261017-0001', 'NUC-0001',
        TIMESTAMP(CURDATE(), '13:15:00'), TIMESTAMP(CURDATE(), '13:15:00'));

-- El núcleo descuenta el saldo
UPDATE nucleo_simulado_cuenta SET saldo = saldo - 300000 WHERE numero_cuenta = 'AH-1001';

-- La fila que quedó en la bitácora (la creó el trigger, nadie la insertó a mano)
SELECT id, transferencia_id, numero_cuenta, operacion, resultado, referencia_externa, monto, ocurrido_en
FROM registro_auditoria;
-- ============================ FIN PASO 1 ============================


-- ============================ PASO 2 — INSERT venenosos ============================
-- 2a) Misma transferencia, mismo código de operación -> la rechaza la llave de idempotencia
INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, estado,
                           referencia_externa, comprobante_nucleo, created_at, updated_at)
VALUES ((SELECT id FROM cliente_api WHERE client_id = 'billetera-externa'),
        (SELECT id FROM cuenta_autorizada WHERE numero_cuenta = 'AH-1001'),
        'BILLETERA-EXTERNA', 300000, 'CONFIRMADA', 'OP-20261017-0001', 'NUC-0001',
        TIMESTAMP(CURDATE(), '13:15:00'), TIMESTAMP(CURDATE(), '13:15:00'));

-- 2b) Monto -50.000 -> lo rechaza el CHECK (monto > 0)
INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, referencia_externa)
VALUES ((SELECT id FROM cliente_api WHERE client_id = 'billetera-externa'),
        (SELECT id FROM cuenta_autorizada WHERE numero_cuenta = 'AH-1001'),
        'BILLETERA-EXTERNA', -50000, 'OP-20261017-0002');
-- ============================ FIN PASO 2 ============================


-- ============================ PASO 3 — Rastro de auditoría de Jorge hoy ============================
SELECT a.nombre             AS asociado,
       r.numero_cuenta      AS cuenta,
       r.operacion,
       r.resultado,
       r.referencia_externa AS codigo_operacion,
       r.monto,
       r.motivo,
       r.ocurrido_en        AS fecha_hora,
       ca.nombre            AS quien
FROM registro_auditoria r
JOIN cuenta_autorizada c ON c.numero_cuenta = r.numero_cuenta
JOIN asociado a          ON a.id = c.asociado_id
JOIN cliente_api ca      ON ca.id = r.cliente_api_id
WHERE a.nombre = 'Jorge Puerta'
  AND DATE(r.ocurrido_en) = CURDATE()
ORDER BY r.ocurrido_en, r.id;
-- ============================ FIN PASO 3 ============================


-- ============================ PASO 4 — Intentos RECHAZADOS ============================
-- Las transferencias entran por el procedimiento (igual que el servicio en Java).
-- Ejecutar cada CALL por separado.

-- 4a) Reintento con el mismo código -> rechazo de la BASE DE DATOS -> queda en la BITÁCORA
CALL registrar_transferencia('billetera-externa', 'AH-1001', 'BILLETERA-EXTERNA', 300000, 'OP-20261017-0001', NULL);

-- 4b) Monto -50.000 -> rechazo de la BASE DE DATOS -> queda en la BITÁCORA
CALL registrar_transferencia('billetera-externa', 'AH-1001', 'BILLETERA-EXTERNA', -50000, 'OP-20261017-0002', NULL);

-- 4c) 5.000.000 con saldo de 2.200.000 -> rechazo del NÚCLEO -> transacción con estado RECHAZADA
CALL registrar_transferencia('billetera-externa', 'AH-1001', 'BILLETERA-EXTERNA', 5000000, 'OP-20261017-0003', NULL);

-- Dónde quedaron: en la transacción (estado RECHAZADA) ...
SELECT id, referencia_externa, monto, estado, motivo_rechazo, created_at
FROM transferencia
ORDER BY id;

-- ... y en la bitácora (todos los intentos, incluidos los que la base de datos no dejó crear)
SELECT id, transferencia_id, operacion, resultado, referencia_externa, monto, motivo, ocurrido_en
FROM registro_auditoria
ORDER BY id;
-- ============================ FIN PASO 4 ============================


-- ============================ PASO 5 — El auditor verifica (HU-03) ============================
-- 5a) Consulta por cuenta y rango de fechas (usa el índice idx_auditoria_cuenta_fecha)
SELECT id, operacion, resultado, referencia_externa AS codigo_operacion, monto, motivo, ip_origen, ocurrido_en
FROM registro_auditoria
WHERE numero_cuenta = 'AH-1001'
  AND ocurrido_en BETWEEN '2026-10-01 00:00:00' AND '2026-12-31 23:59:59'
ORDER BY id;

-- 5b) Verificación de integridad: recalcula la huella de cada fila y revisa la cadena.
--     cadena = OK si la fila apunta a la huella de la anterior (ROTA = falta o se borró una fila)
--     huella = OK si el contenido no cambió (ALTERADO = alguien modificó la fila)
SELECT r.id,
       IF(r.hash_anterior = COALESCE(LAG(r.hash_actual) OVER (ORDER BY r.id), REPEAT('0', 64)), 'OK', 'ROTA') AS cadena,
       IF(r.hash_actual = SHA2(CONCAT_WS('|', r.hash_anterior, r.cliente_api_id,
            COALESCE(r.transferencia_id, '-'), r.numero_cuenta, r.operacion, r.resultado,
            COALESCE(r.referencia_externa, '-'), COALESCE(r.monto, '-'), COALESCE(r.motivo, '-'),
            r.ip_origen, r.ocurrido_en), 256), 'OK', 'ALTERADO') AS huella
FROM registro_auditoria r
ORDER BY r.id;

-- 5c) Intentos de alterar la evidencia: los dos deben fallar con el error 1644
UPDATE registro_auditoria SET monto = 1 WHERE id = 1;
DELETE FROM registro_auditoria WHERE id = 1;
-- ============================ FIN PASO 5 ============================
