# Evidencias de entregas semanales

Cada carpeta contiene la entrega que corresponde a esa clase del cronograma.

| Carpeta | Clase | Fecha | Contenido |
|---|---|---|---|
| `entrega-01-drivers-y-seccion1/` | 1 | 26/09/2026 | Ficha de drivers arquitectónicos del Caso 5 (texto marcado + tablas RF, AC, RT, RA + pregunta al cliente) y DAS v0.1 con la Sección 1, en Word y PDF |
| `entrega-02-patron-y-pila/` | 2 | 03/10/2026 | Sección 2 del DAS: patrón N-Capas justificado y pila tecnológica |
| `entrega-03-contexto-y-contenedores/` | 3 | 10/10/2026 | Secciones 3.1 y 3.2: vista de contexto y vista de contenedores (PDF + imágenes) |
| `entrega-04-clases-secuencia-ddl/` | 4 | 17/10/2026 | Secciones 3.3, 3.4 y 5.2: diagrama de clases, secuencia de HU-01 y script SQL DDL con su verificación |

Las entregas 2, 3 y 4 se subieron juntas el 17/10/2026, como parte del DAS v0.4 (ver `docs/DAS.pdf`).

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
