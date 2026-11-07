-- =====================================================================
-- SITRA — Usuario de la aplicación con mínimo privilegio (producción)
-- En el laboratorio la aplicación usa root sin contraseña (XAMPP).
-- En producción se crea este usuario y su clave va en SITRA_DB_PASSWORD.
-- Antes de ejecutar: reemplace CAMBIE_ESTA_CLAVE por una clave fuerte
-- (la clave real NUNCA se guarda en el repositorio).
-- =====================================================================
CREATE USER IF NOT EXISTS 'sitra_app'@'localhost' IDENTIFIED BY 'CAMBIE_ESTA_CLAVE';

-- Solo lo que necesita: leer, insertar, actualizar y llamar el procedimiento.
-- Sin DELETE (el borrado es lógico), sin DROP, sin ALTER y sin TRIGGER:
-- no puede desactivar la protección de la bitácora.
GRANT SELECT, INSERT, UPDATE, EXECUTE ON sitra.* TO 'sitra_app'@'localhost';
FLUSH PRIVILEGES;
