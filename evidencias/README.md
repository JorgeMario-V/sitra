# Evidencias de entregas semanales

Cada carpeta contiene la entrega que corresponde a esa clase del cronograma.

| Carpeta | Clase | Fecha de la clase | Contenido |
|---|---|---|---|
| `entrega-01-drivers-y-seccion1/` | 1 | 26/09/2026 | Ficha de drivers arquitectónicos del Caso 5 (texto marcado + tablas RF, AC, RT, RA + pregunta al cliente) y DAS v0.1 con la Sección 1, en Word y PDF |
| `entrega-02-patron-y-pila/` | 2 | 03/10/2026 | Sección 2 del DAS: patrón N-Capas justificado y pila tecnológica |
| `entrega-03-contexto-y-contenedores/` | 3 | 10/10/2026 | Secciones 3.1 y 3.2: vista de contexto y vista de contenedores (PDF + imágenes) |
| `entrega-04-clases-secuencia-ddl/` | 4 | 17/10/2026 | Secciones 3.3, 3.4 y 5.2: diagrama de clases, secuencia de HU-01 y script SQL DDL con su verificación |
| `entrega-05-adr-despliegue-arranque/` | 5 | 24/10/2026 | DAS v0.5: vista de despliegue (3.5), ADR-001 a ADR-003 (Sección 4) y arranque del corte vertical |
| `entrega-06-crud-jwt/` | 6 | 07/11/2026 | DAS v1.0: ADR-004 y ADR-005, repositorio (5.1), SQL (5.2) y CRUD punta a punta con JWT (5.3) |
| `entrega-07-pruebas-y-tradeoffs/` | 7 | 14/11/2026 | DAS v1.0: pruebas (5.4) y trade-offs (Sección 6); captura del Runner de Postman y salidas de las pruebas (ver abajo) |
| `entrega-08-entrega-final/` | 8 | 21/11/2026 | DAS v1.1 completo (Secciones 1 a 6) en formato APA |

Fechas de subida a GitHub (pueden diferir de la fecha de la clase):

- Las entregas 2, 3 y 4 se subieron juntas el **16/10/2026** (la noche anterior a la clase del 17/10), como parte del DAS v0.4.
- La entrega 5 se presentó en clase el 24/10/2026, pero se subió el **06/11/2026**, junto con las entregas 6 a 8.
- Las entregas 7 y 8 se **adelantaron**: se subieron el 06/11/2026, antes de sus clases (14/11 y 21/11).
- `entrega-04-clases-secuencia-ddl/resultado_verificacion_ddl.txt` es **histórico**: se hizo en PostgreSQL 16 antes
  del ADR-002. La verificación vigente, en MariaDB, está en `entrega-07-pruebas-y-tradeoffs/resultado_verificacion_mariadb.txt`.

## Evidencias de ejecución (entrega-07-pruebas-y-tradeoffs)

| Archivo | Qué muestra | Cómo se genera |
|---|---|---|
| `Postman-Runner.png` | Resultado del Runner de Postman (19 peticiones) | Postman → Run collection |
| `resultado_verificacion_mariadb.txt` | `demo_sustentacion.sql` en MariaDB: errores 1062, 4025 y 1644 esperados y cadena de huellas íntegra | `scripts\generar_evidencias.cmd` |
| `resultado_minimo_privilegio.txt` | El usuario de mínimo privilegio no puede desactivar triggers ni borrar (error 1142) | `scripts\generar_evidencias.cmd` |
| `resultado_concurrencia.txt` | 6 transacciones simultáneas en la bitácora: deadlocks y cadena íntegra (XAMPP) | `scripts\generar_evidencias.cmd` |
| `resultado_concurrencia_referencia.txt` | La misma prueba en MariaDB 10.11 con `db/pruebas/concurrencia.sh` (medición de referencia del DAS) | `sh db/pruebas/concurrencia.sh` |
| `mvn-test.txt` y `mvn-test-integracion.txt` | Salida de las pruebas JUnit: unitarias e integración | `scripts\generar_evidencias.cmd` |

## Cambios de la ficha respecto a la primera sustentación

Se incorporaron las aclaraciones del cliente que no se habían considerado:

- La cooperativa no reemplazará el núcleo (RA-04) → se descarta Strangler Fig; la integración es permanente.
- Cero presupuesto, sin inversión en APIs (RA-02) → solo software libre sobre infraestructura existente.
- Cero personal técnico (RA-03) → operación por configuración y mínimo mantenimiento.
- La pregunta al cliente ahora apunta a la contradicción entre RA-02 y RA-03.

## Cambios en la Sección 1 (DAS v0.4)

- AC-01 (Seguridad) ahora nombra su métrica principal: **completitud del registro de auditoría**
  (ISO/IEC 25023), X = operaciones registradas / operaciones realizadas, meta X = 1 (100 %).
- Se agrega la meta de tiempo de la consulta del auditor (≤ 5 s), porque el caso dice que hoy la
  trazabilidad "no se puede entregar a tiempo".

## Cambios de la Clase 5 (DAS v0.5)

- **ADR-002:** la base de datos pasa de PostgreSQL a la **MariaDB del XAMPP** que ya está en los equipos.
- **ADR-003:** la bitácora la escriben triggers de la base de datos; los intentos rechazados siempre se guardan.
- Se agregan la tabla `asociado`, el registro de rechazados (V3) y la IP de origen (V4, `ALTER TABLE`).

## Cambios de las Clases 6 a 8 (DAS v1.0)

- CRUD completo de Transferencia: `POST`, `GET`, `PUT` (corregir y reintentar, ADR-004) y `DELETE` lógico con reverso.
- Token JWT + BCrypt en lugar del encabezado provisional `X-Cliente-Id` (ADR-005).
- Consulta de saldo (HU-02) y consulta por fechas + verificación de integridad de la bitácora (HU-03).
- Pruebas JUnit (unitarias e integración), colección Postman de 19 peticiones y Sección 6 (trade-offs y 7 riesgos).

## Cambios de la versión 1.1 (revisión de trazabilidad)

- Se corrigen los hallazgos H-01 a H-12 de la revisión (detalle en el Anexo B del DAS v1.1).
- Se agregan el Maven Wrapper, la creación de la base por consola, el script que genera las evidencias,
  la prueba de concurrencia y el test de tiempo de respuesta de la consulta del auditor (30 tests en Postman).
- El DAS pasa a formato APA 7: portada, resumen, glosario de términos técnicos, tablas y figuras con
  número y título, y referencias.
