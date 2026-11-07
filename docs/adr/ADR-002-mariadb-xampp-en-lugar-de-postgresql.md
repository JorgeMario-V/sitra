# ADR-002: MariaDB (la del XAMPP existente) en lugar de PostgreSQL

## Estatus
Aceptada — reemplaza la elección de PostgreSQL 16 de la Sección 2.2 del DAS v0.4.

## Fecha
2026-10-24

## Contexto
En el DAS v0.4 se eligió PostgreSQL 16. En la sustentación de la Clase 4 el modelo se probó en
vivo en los equipos disponibles, que tienen XAMPP con MariaDB y HeidiSQL ya instalados. La
restricción RA-02 dice que solo se trabaja con lo que se tiene y la RA-03 que no hay personal
técnico para instalar y mantener otro motor.

Atributos en juego: **AC-01 Seguridad** (integridad de la bitácora), **AC-03 Mantenibilidad**.
Restricciones: cero presupuesto, cero personal técnico, usar la infraestructura existente.

Alternativas consideradas:
- **PostgreSQL 16:** muy completo, pero hay que instalarlo y administrarlo aparte.
- **MariaDB 10.4+ del XAMPP:** ya instalado, gratuito, con HeidiSQL para operarlo.
- **H2 en memoria:** no sirve como evidencia porque se borra al apagar.

## Decisión
Decidimos usar MariaDB 10.4+ incluida en XAMPP, porque cumple todo lo que exige AC-01 —llaves
foráneas, CHECK (MariaDB los aplica desde la 10.2), UNIQUE, transacciones InnoDB y triggers que
bloquean la bitácora— y porque ya está instalada, cumpliendo RA-02 y RA-03.

Se descarta PostgreSQL porque, aunque es técnicamente equivalente para este caso, exige instalar y
mantener un motor nuevo sin personal que lo haga. Se descarta H2 porque no conserva la evidencia.

## Consecuencias / Trade-offs
- **Positivas:** cero instalación; el mismo motor sirve para desarrollo, pruebas y la
  demostración en clase; HeidiSQL permite al auditor consultar la bitácora sin programar.
- **Negativas:** se pierden funciones propias de PostgreSQL (índices parciales, pgcrypto). Se
  reemplazan: el índice parcial por uno normal y la huella por `SHA2()` de MariaDB. Los scripts
  PostgreSQL de la Clase 4 quedan en el historial de Git, no en la versión actual.
- **Deuda técnica asumida:** XAMPP trae MariaDB 10.4, una versión sin soporte del fabricante.
  Condición para revisarla: si el sistema pasa a producción real, actualizar a una versión con
  soporte (10.11 LTS); los scripts no cambian.
