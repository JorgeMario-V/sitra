-- =====================================================================
-- SITRA — Verificación del script DDL (Sección 5.2)
-- Ejecutar sobre una base recién migrada (V1 + V2):
--   psql -d sitra -f db/pruebas/verificar_restricciones.sql
-- Cada prueba imprime OK o FALLA. Al final se deshace todo (ROLLBACK).
-- =====================================================================
BEGIN;

-- Datos válidos de prueba
INSERT INTO cliente_api (nombre, client_id, secret_hash, rol)
VALUES ('Billetera de prueba', 'billetera-prueba', '$2a$10$hashDePrueba', 'BILLETERA');
INSERT INTO cuenta_autorizada (numero_cuenta, titular_documento, tipo)
VALUES ('1001', '1020304050', 'AHORROS');
INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, referencia_externa)
VALUES ((SELECT id FROM cliente_api WHERE client_id = 'billetera-prueba'),
        (SELECT id FROM cuenta_autorizada WHERE numero_cuenta = '1001'),
        'W-77', 50000, 'REF-PRUEBA-001');
INSERT INTO registro_auditoria (cliente_api_id, transferencia_id, numero_cuenta, operacion, resultado,
                                ip_origen, hash_anterior, hash_actual)
VALUES ((SELECT id FROM cliente_api WHERE client_id = 'billetera-prueba'),
        (SELECT id FROM transferencia WHERE referencia_externa = 'REF-PRUEBA-001'),
        '1001', 'TRANSFERENCIA', 'EXITOSA', '10.0.0.5', repeat('0', 64), repeat('a', 64));

DO $$
DECLARE
    v_cliente BIGINT := (SELECT id FROM cliente_api WHERE client_id = 'billetera-prueba');
    v_cuenta  BIGINT := (SELECT id FROM cuenta_autorizada WHERE numero_cuenta = '1001');

    PROCEDURE_DUMMY INT;
BEGIN
    -- 1. UNIQUE: la misma referencia no se puede registrar dos veces (409 en la API)
    BEGIN
        INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, referencia_externa)
        VALUES (v_cliente, v_cuenta, 'W-1', 10, 'REF-PRUEBA-001');
        RAISE NOTICE 'FALLA 1: aceptó una referencia duplicada';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK 1: UNIQUE rechaza referencia duplicada';
    END;

    -- 2. CHECK: el monto debe ser positivo
    BEGIN
        INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, referencia_externa)
        VALUES (v_cliente, v_cuenta, 'W-1', -5, 'REF-PRUEBA-002');
        RAISE NOTICE 'FALLA 2: aceptó un monto negativo';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK 2: CHECK rechaza monto negativo';
    END;

    -- 3. FK: solo se puede transferir desde una cuenta autorizada existente
    BEGIN
        INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, referencia_externa)
        VALUES (v_cliente, -1, 'W-1', 10, 'REF-PRUEBA-003');
        RAISE NOTICE 'FALLA 3: aceptó una cuenta que no existe';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'OK 3: FK rechaza cuenta inexistente';
    END;

    -- 4. CHECK: el estado solo puede tomar los valores definidos
    BEGIN
        INSERT INTO transferencia (cliente_api_id, cuenta_origen_id, cuenta_destino, monto, referencia_externa, estado)
        VALUES (v_cliente, v_cuenta, 'W-1', 10, 'REF-PRUEBA-004', 'INVENTADO');
        RAISE NOTICE 'FALLA 4: aceptó un estado inválido';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK 4: CHECK rechaza estado inválido';
    END;

    -- 5. Trigger: la bitácora no se puede modificar
    BEGIN
        UPDATE registro_auditoria SET resultado = 'FALLIDA';
        RAISE NOTICE 'FALLA 5: permitió modificar la bitácora';
    EXCEPTION WHEN raise_exception THEN RAISE NOTICE 'OK 5: trigger impide modificar la bitácora';
    END;

    -- 6. Trigger: la bitácora no se puede borrar
    BEGIN
        DELETE FROM registro_auditoria;
        RAISE NOTICE 'FALLA 6: permitió borrar la bitácora';
    EXCEPTION WHEN raise_exception THEN RAISE NOTICE 'OK 6: trigger impide borrar la bitácora';
    END;

    -- 7. Borrado lógico: anular una transferencia no la elimina ni toca su evidencia
    UPDATE transferencia SET activo = FALSE, estado = 'ANULADA' WHERE referencia_externa = 'REF-PRUEBA-001';
    IF EXISTS (SELECT 1 FROM transferencia WHERE referencia_externa = 'REF-PRUEBA-001' AND activo = FALSE)
       AND (SELECT count(*) FROM registro_auditoria WHERE numero_cuenta = '1001') = 1 THEN
        RAISE NOTICE 'OK 7: borrado lógico conserva la transferencia y su registro de auditoría';
    ELSE
        RAISE NOTICE 'FALLA 7: el borrado lógico no se comportó como se esperaba';
    END IF;
END $$;

ROLLBACK;
