# ADR-003: La trazabilidad la garantiza la base de datos, no solo el código

## Estatus
Aceptada

## Fecha
2026-10-24

## Contexto
La trazabilidad es el atributo crítico del caso (**AC-01 Seguridad**: responsabilidad y no
repudio): el auditor la exige y de ella depende la licencia de operación. En la Clase 4 se
comprobó que, si la bitácora la escribe solo la aplicación, una transferencia insertada
directamente por SQL no deja rastro. Además el auditor pidió registrar también los intentos
rechazados.

Alternativas consideradas:
- **Solo la aplicación escribe la bitácora:** simple, pero cualquier acceso directo a la base de
  datos (HeidiSQL, otro programa) se salta el registro.
- **Servicio de auditoría en la nube:** con costo recurrente (prohibido por RA-02).
- **Triggers en la base de datos + registro de rechazos desde el servicio.**

## Decisión
Decidimos que la base de datos garantice la bitácora con triggers: toda transferencia que se
inserte o cambie deja su fila automáticamente; la bitácora no se puede modificar ni borrar; y cada
fila guarda una huella SHA-256 encadenada con la anterior. Los intentos rechazados se guardan así:
si la base de datos o la validación impiden crear la transferencia, el servicio escribe la fila
RECHAZADA en la bitácora en una transacción propia (`REQUIRES_NEW`); si el núcleo la rechaza
(saldo insuficiente), la transferencia se guarda con estado RECHAZADA. Todo con funciones
gratuitas de MariaDB.

## Consecuencias / Trade-offs
- **Positivas:** ninguna transferencia queda sin evidencia, sin importar por dónde entre; los
  rechazados nunca se pierden; la regla no depende de que un programador "se acuerde".
- **Negativas:** parte de la lógica vive en la base de datos (triggers y un procedimiento), que es
  menos visible que el código Java. Cada inserción hace una escritura extra en la bitácora.
- **Deuda técnica asumida:** el procedimiento `registrar_transferencia` repite en SQL la regla del
  `TransferenciaService`, para poder operar y demostrar desde HeidiSQL. Condición para revisarla:
  si las dos versiones empiezan a diferir, se deja una sola y la otra la llama.
