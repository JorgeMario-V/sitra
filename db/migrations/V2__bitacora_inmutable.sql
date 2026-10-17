-- =====================================================================
-- SITRA — V2: la base de datos impide modificar o borrar la bitácora.
-- Así la regla no depende del código de la aplicación (AC-01: 0 registros modificables).
-- =====================================================================

CREATE OR REPLACE FUNCTION fn_bitacora_inmutable() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'registro_auditoria no se puede modificar ni borrar (operación: %)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bitacora_inmutable
    BEFORE UPDATE OR DELETE ON registro_auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_bitacora_inmutable();
