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
