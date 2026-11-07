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
