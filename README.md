# SITRA — Servicio de Integración y Trazabilidad

Proyecto individual del curso **Arquitectura de Software** sobre el
**Caso 5 — Cooperativa de ahorro y crédito**.

SITRA expone de forma segura y auditable un subconjunto mínimo de funciones del núcleo
transaccional legado de la cooperativa (consulta de saldo y transferencias) a una billetera
digital externa, **sin modificar el núcleo** y **sin inversión adicional**. Toda operación,
incluidos los intentos rechazados, queda en una bitácora que no se puede modificar ni borrar.

## Restricciones del cliente

- El núcleo legado no se modifica ni se reemplaza, ni ahora ni a futuro.
- Cero presupuesto: solo software libre e infraestructura existente; no se compran productos de API.
- Cero personal técnico: la solución debe operarse por configuración.
- Plazo regulatorio fijo de 6 meses.

## Arquitectura en una línea

N-Capas (seguridad → presentación → negocio → datos) con un **conector del núcleo** detrás de la
interfaz `NucleoConector`, que tiene dos versiones: la real y un simulador, porque no existe
ambiente de pruebas del núcleo legado. La bitácora la garantiza la base de datos con triggers y
una huella SHA-256 encadenada. Detalle en [`docs/DAS.pdf`](docs/DAS.pdf) y en [`docs/adr/`](docs/adr).

| Pieza | Tecnología |
|---|---|
| API | Java 21 + Spring Boot 3.3 (JAR ejecutable, puerto 8080) |
| Seguridad | Token JWT HS256 firmado con el JDK + secretos con BCrypt (ADR-005) |
| Datos | Spring Data JPA + MariaDB 10.4+ (la del XAMPP, ADR-002) |
| Pruebas | JUnit 5 + Mockito + MockMvc + colección Postman |

## Instalar y ejecutar (Windows, desde cero)

### 1. Requisitos

- **XAMPP** (trae MariaDB y HeidiSQL).
- **JDK 21**.
- **VS Code** con el *Extension Pack for Java* (o Maven 3.9+ si prefiere la terminal).
- **Postman**.
- Conexión a Internet la primera vez (Maven descarga las dependencias).

### 2. Crear la base de datos

1. Abra el **XAMPP Control Panel** y presione **Start** en **MySQL**.
2. Abra **HeidiSQL**, conéctese a `127.0.0.1` con el usuario `root` (sin contraseña).
3. Archivo → Cargar archivo SQL → `db/instalar_todo.sql` → presione **F9**.

Esto borra y crea la base `sitra`, aplica las migraciones `V1` a `V4` y carga los datos de prueba.
Volver a ejecutarlo deja todo como nuevo.

### 3. Arrancar la API

- **VS Code:** abra la carpeta del repositorio, abra
  `src/main/java/co/coop/sitra/SitraApplication.java` y presione **Run**.
- **Terminal (CMD):** `mvn spring-boot:run`

La API queda en `http://localhost:8080`. En el arranque aparece una advertencia de que
`SITRA_JWT_SECRET` no está definido: es normal en el laboratorio (se genera un secreto aleatorio).

### 4. Probar

- **Postman:** Import → `postman/coleccion-corte-vertical.json` → clic derecho en la colección →
  **Run collection** → **Run**. Son 19 peticiones en orden, todas con tests automáticos.
- **Pruebas unitarias** (no necesitan base de datos): `mvn test`
- **Prueba de integración** (necesita MySQL encendido y el paso 2 hecho): `mvn test -Pintegracion`
- En VS Code: pestaña **Testing** → ejecutar todo (la de integración también necesita MariaDB).

### Credenciales de prueba (solo laboratorio)

| clientId | secreto | rol |
|---|---|---|
| `billetera-externa` | `billetera-2026` | BILLETERA |
| `auditor-cumplimiento` | `auditor-2026` | AUDITOR |

En la base solo se guarda el hash BCrypt de cada secreto.

### Variables de entorno

Los valores sensibles llegan por variables de entorno; `.env.example` tiene solo los nombres.
El archivo `.env` con valores reales **nunca** se sube a Git (está en `.gitignore`).

| Variable | Para qué | Valor por defecto (laboratorio) |
|---|---|---|
| `SITRA_PORT` | Puerto de la API | `8080` |
| `SITRA_DB_URL` | Conexión a MariaDB | `jdbc:mariadb://localhost:3306/sitra` |
| `SITRA_DB_USER` / `SITRA_DB_PASSWORD` | Usuario y clave de la base | `root` / vacía |
| `SITRA_NUCLEO_MODO` | `simulado`, `sin-respuesta` o `legado` | `simulado` |
| `SITRA_JWT_SECRET` | Secreto de firma del token (mín. 32 caracteres) | aleatorio en cada arranque |
| `SITRA_JWT_MINUTOS` | Vida del token | `30` |

Para ver el flujo de falla del núcleo (AC-02) en CMD:

```cmd
set SITRA_NUCLEO_MODO=sin-respuesta
mvn spring-boot:run
```

## Endpoints

Todos, menos el de token, exigen el encabezado `Authorization: Bearer <token>`.

| Método y ruta | Qué hace | Respuestas |
|---|---|---|
| `POST /api/v1/auth/token` | `{clientId, secreto}` → token JWT | 200 · 401 |
| `POST /api/v1/transferencias` | Registra la transferencia (HU-01) | 201 · 400 · 401 · 403 · 404 · 409 · 422 · 503 |
| `GET /api/v1/transferencias` | Lista las activas del cliente | 200 · 401 · 403 |
| `GET /api/v1/transferencias/{id}` | Consulta una | 200 · 401 · 404 |
| `PUT /api/v1/transferencias/{id}` | Corrige y reintenta una RECHAZADA o FALLIDA (ADR-004) | 200 · 400 · 404 · 409 · 422 · 503 |
| `DELETE /api/v1/transferencias/{id}` | Anulación lógica (con reverso si movió dinero) | 204 · 404 · 409 · 503 |
| `GET /api/v1/cuentas/{numero}/saldo` | Saldo leído del núcleo (HU-02) | 200 · 401 · 403 · 404 · 503 |
| `GET /api/v1/auditoria?cuenta=&desde=&hasta=` | Bitácora por cuenta y fechas, solo AUDITOR (HU-03) | 200 · 400 · 401 · 403 |
| `GET /api/v1/auditoria/verificacion` | Recalcula la cadena de huellas de la bitácora | 200 · 401 · 403 |

Ejemplo de cuerpo para crear una transferencia:

```json
{ "cuentaOrigen": "AH-1001", "cuentaDestino": "BILLETERA-EXTERNA", "monto": 300000, "referenciaExterna": "OP-0001" }
```

## Demostración solo con base de datos

`db/pruebas/demo_sustentacion.sql` (en HeidiSQL, seleccionar cada bloque y Ctrl+F9) muestra que
una transferencia insertada por SQL también deja su fila en la bitácora, que los rechazos quedan
registrados, que la bitácora no se puede modificar ni borrar y cómo el auditor verifica la cadena
de huellas.

## Estructura del repositorio

| Carpeta | Contenido |
|---|---|
| `docs/` | DAS v1.0 en Word y PDF, `adr/` (ADR-001 a ADR-005) y `diagramas/` (PlantUML + PNG) |
| `db/migrations/` | `V1` tablas · `V2` triggers de la bitácora · `V3` registro de rechazados · `V4` IP de origen |
| `db/` | `instalar_todo.sql`, `datos_prueba.sql`, `pruebas/demo_sustentacion.sql`, `produccion/usuario_minimo_privilegio.sql` |
| `src/main/java/co/coop/sitra/` | `security`, `controller`, `service`, `repository`, `model`, `integration`, `dto`, `exception` |
| `src/test/java/co/coop/sitra/` | Pruebas unitarias (JWT, servicio) e integración (corte vertical completo) |
| `postman/` | Colección del corte vertical (19 peticiones con tests) |
| `evidencias/` | Entregas semanales, una carpeta por clase |

## Entregas

| Clase | Fecha | Entrega | Estado |
|---|---|---|---|
| 1 | 26/09/2026 | Ficha de drivers + Sección 1 del DAS | ✅ [`entrega-01`](evidencias/entrega-01-drivers-y-seccion1) |
| 2 | 03/10/2026 | Sección 2: patrón N-Capas y pila tecnológica | ✅ [`entrega-02`](evidencias/entrega-02-patron-y-pila) |
| 3 | 10/10/2026 | Vistas de Contexto y Contenedores | ✅ [`entrega-03`](evidencias/entrega-03-contexto-y-contenedores) |
| 4 | 17/10/2026 | Clases, Secuencia y script SQL DDL | ✅ [`entrega-04`](evidencias/entrega-04-clases-secuencia-ddl) |
| 5 | 24/10/2026 | ADR, Vista de Despliegue y arranque del corte vertical | ✅ [`entrega-05`](evidencias/entrega-05-adr-despliegue-arranque) |
| 6 | 07/11/2026 | CRUD punta a punta con JWT (5.3) | ✅ [`entrega-06`](evidencias/entrega-06-crud-jwt) |
| 7 | 14/11/2026 | Pruebas, trade-offs y riesgos (5.4 y 6) | ✅ [`entrega-07`](evidencias/entrega-07-pruebas-y-tradeoffs) |
| 8 | 21/11/2026 | Entrega final: DAS v1.0 + repositorio | ✅ [`entrega-08`](evidencias/entrega-08-entrega-final) |
