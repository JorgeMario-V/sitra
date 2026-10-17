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
| `docs/` | Documento de Arquitectura de Software (DAS) vigente en Word y PDF (v0.4) |
| `docs/adr/` | Registros de Decisiones de Arquitectura (desde la Clase 5) |
| `docs/diagramas/` | Diagramas UML: fuentes PlantUML (`.puml`) e imágenes (`.png`) |
| `db/migrations/` | Script SQL DDL versionado (Flyway): `V1` esquema, `V2` bitácora inmutable |
| `db/pruebas/` | Script que verifica PK, FK, CHECK, UNIQUE, borrado lógico y bitácora inmutable |
| `evidencias/` | Entregas semanales, una carpeta por clase |

## Entregas

| Clase | Fecha | Entrega | Estado |
|---|---|---|---|
| 1 | 26/09/2026 | Ficha de drivers + Sección 1 del DAS | ✅ [`evidencias/entrega-01-drivers-y-seccion1`](evidencias/entrega-01-drivers-y-seccion1) |
| 2 | 03/10/2026 | Sección 2: patrón N-Capas y pila tecnológica | ✅ [`evidencias/entrega-02-patron-y-pila`](evidencias/entrega-02-patron-y-pila) |
| 3 | 10/10/2026 | Vistas de Contexto y Contenedores (Sección 3, parte 1) | ✅ [`evidencias/entrega-03-contexto-y-contenedores`](evidencias/entrega-03-contexto-y-contenedores) |
| 4 | 17/10/2026 | Clases, Secuencia y script SQL DDL (3 parte 2 y 5.2) | ✅ [`evidencias/entrega-04-clases-secuencia-ddl`](evidencias/entrega-04-clases-secuencia-ddl) |
| 5 | 24/10/2026 | ADR, Vista de Despliegue y arranque del corte vertical | ⏳ |
| 6 | 07/11/2026 | CRUD punta a punta (5.3) | ⏳ |
| 7 | 14/11/2026 | Pruebas, trade-offs y DAS v1.0 (5.4 y 6) | ⏳ |
| 8 | 21/11/2026 | Entrega final y sustentación | ⏳ |

## Arquitectura en una línea

N-Capas (seguridad → presentación → negocio → datos) con un **conector del núcleo** detrás de la
interfaz `NucleoConector`, que tiene dos versiones: la real y un simulador para pruebas, porque no
existe ambiente de pruebas del núcleo legado.

## Verificar el script SQL (PostgreSQL 16)

```bash
createdb sitra
psql -d sitra -f db/migrations/V1__esquema_inicial.sql
psql -d sitra -f db/migrations/V2__bitacora_inmutable.sql
psql -d sitra -f db/pruebas/verificar_restricciones.sql   # debe imprimir 7 líneas "OK"
```

Las instrucciones para ejecutar la aplicación se agregarán con el código (Clase 5 en adelante).
