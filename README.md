# SITRA — Servicio de Integración y Trazabilidad

Proyecto individual del curso **Arquitectura de Software** sobre el
**Caso 5 — Cooperativa de ahorro y crédito**.

SITRA expone de forma segura y auditable un subconjunto mínimo de funciones
(consulta de saldo y transferencias) de un núcleo transaccional legado a una
billetera digital externa, **sin modificar el núcleo** y **sin inversión adicional**.

## Restricciones del cliente

- El núcleo legado no se modifica ni se reemplaza, ni ahora ni a futuro.
- Cero presupuesto: solo software libre e infraestructura existente; no se compran productos de API.
- Cero personal técnico: la solución debe operarse por configuración.
- Plazo regulatorio fijo de 6 meses.

## Contenido del repositorio

| Carpeta | Contenido |
|---|---|
| `docs/` | Documento de Arquitectura de Software (DAS) vigente en Word y PDF |
| `docs/adr/` | Registros de Decisiones de Arquitectura (desde la Clase 5) |
| `docs/diagramas/` | Fuentes y exportaciones de los diagramas UML (desde la Clase 3) |
| `evidencias/` | Entregas semanales, una carpeta por clase |

## Entregas

| Clase | Fecha | Entrega | Estado |
|---|---|---|---|
| 1 | 26/09/2026 | Ficha de drivers + Sección 1 del DAS | ✅ [`evidencias/entrega-01-drivers-y-seccion1`](evidencias/entrega-01-drivers-y-seccion1) |
| 2 | 03/10/2026 | Sección 2: patrón y pila tecnológica + estructura del código | ⏳ |
| 3 | 10/10/2026 | Vistas de Contexto y Contenedores (Sección 3, parte 1) | ⏳ |
| 4 | 17/10/2026 | Clases, Secuencia y script SQL DDL (3 parte 2 y 5.2) | ⏳ |
| 5 | 24/10/2026 | ADR, Vista de Despliegue y arranque del corte vertical | ⏳ |
| 6 | 07/11/2026 | CRUD punta a punta (5.3) | ⏳ |
| 7 | 14/11/2026 | Pruebas, trade-offs y DAS v1.0 (5.4 y 6) | ⏳ |
| 8 | 21/11/2026 | Entrega final y sustentación | ⏳ |

Las instrucciones de instalación y ejecución se agregarán cuando exista el código (Clase 5 en adelante).
