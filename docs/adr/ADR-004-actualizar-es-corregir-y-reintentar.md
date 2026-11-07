# ADR-004: En el CRUD de Transferencia, actualizar (PUT) es "corregir y reintentar"

## Estatus
Aceptada

## Fecha
2026-11-06

## Contexto
La plantilla exige un CRUD completo de la entidad principal de la HU-01 (Transferencia). Pero una
transferencia no es un dato cualquiera: si ya movió dinero en el núcleo, cambiar su monto o su
destino en SITRA dejaría la evidencia diciendo algo distinto de lo que pasó. Además, en la Clase 5
se identificó un hueco de fiabilidad: si el núcleo no respondía (FALLIDA, 503), la billetera no
podía reintentar con el mismo código de operación, porque el código ya existía (409).

Atributos en juego: **AC-01 Seguridad** (la evidencia debe coincidir con lo ocurrido) y
**AC-02 Fiabilidad** (poder recuperarse de un fallo del núcleo sin duplicar operaciones).

Alternativas consideradas:
- **PUT libre sobre cualquier campo:** cumple la plantilla, pero permite "maquillar" una
  transferencia confirmada.
- **No tener PUT:** incumple el CRUD completo.
- **PUT solo sobre lo que no movió dinero (RECHAZADA o FALLIDA):** corregir destino o monto y
  volver a enviarla al núcleo con el mismo código de operación.

## Decisión
Decidimos que `PUT /api/v1/transferencias/{id}` solo acepte transferencias RECHAZADAS o FALLIDAS:
corrige `cuentaDestino` y `monto`, conserva el código de operación y vuelve a pasar por el núcleo
(200 si queda CONFIRMADA, 422 si el núcleo la vuelve a rechazar, 503 si sigue sin responder). Sobre
una CONFIRMADA responde 409. Y `DELETE` es un borrado lógico (ANULADA, `activo = false`, 204): si la
transferencia ya había movido dinero, primero se pide el reverso al núcleo; si el núcleo no
responde, no se anula nada (503).

## Consecuencias / Trade-offs
- **Positivas:** el UPDATE tiene sentido de negocio; resuelve el reintento después de un 503 sin
  romper la idempotencia; la bitácora (trigger de UPDATE) guarda el intento original y el
  reintento, así que nada se borra ni se maquilla.
- **Negativas:** el PUT no es "genérico" como en el ejemplo de la plantilla; quien lo use debe
  conocer la regla (está en el README y la colección Postman la prueba: 409 y 200).
- **Deuda técnica asumida:** el reverso depende de que el núcleo real ofrezca una operación para
  devolver dinero; hoy solo existe en el simulador. Condición para revisarla: al implementar
  `NucleoLegadoConector` (ADR-001), confirmar con el cliente cómo se reversa una operación.
