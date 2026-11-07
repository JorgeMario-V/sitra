# ADR-005: Token JWT firmado con HMAC del JDK y secretos con BCrypt, sin Spring Security completo

## Estatus
Aceptada — reemplaza la identificación provisional por encabezado `X-Cliente-Id` del arranque
(Clase 5).

## Fecha
2026-11-06

## Contexto
El consumidor de SITRA es otro sistema (la billetera), no una persona con navegador. Cada petición
debe probar quién la hace, porque ese dato queda en la bitácora como responsable (**AC-01**:
responsabilidad y no repudio; meta: 401 en el 100 % de los intentos sin token). En el arranque se
usó el encabezado `X-Cliente-Id`, que cualquiera puede falsificar.

Restricciones: cero presupuesto (RA-02) y cero personal técnico (RA-03): no hay quién instale ni
opere un servidor de identidad.

Alternativas consideradas:
- **Sesiones en servidor:** pensadas para navegadores; no aplican a un sistema que llama a otro.
- **Servidor de identidad (por ejemplo Keycloak, OAuth2):** muy completo, pero es otra plataforma
  que instalar, actualizar y operar.
- **Spring Security completo + librería JWT externa:** estándar, pero agrega una cadena de filtros
  y configuración que un mantenedor sin experiencia difícilmente entiende.
- **Token JWT HS256 firmado con `javax.crypto` (incluido en Java) + BCrypt de
  `spring-security-crypto` + un filtro propio.**

## Decisión
Decidimos usar JWT HS256 firmado con HMAC-SHA256 del propio JDK (`JwtServicio`), validado en un
solo filtro (`FiltroJwt`) antes de llegar a los controladores, y guardar los secretos de los
clientes solo como hash BCrypt (`spring-security-crypto`, sin el resto de Spring Security), porque
AC-01 exige identificar a quien llama en cada petición y RA-02/RA-03 exigen no agregar plataformas
ni productos. El secreto de firma llega por la variable `SITRA_JWT_SECRET` (mínimo 32
caracteres); si no existe, se genera uno aleatorio al arrancar. El token vence a los 30 minutos.

## Consecuencias / Trade-offs
- **Positivas:** 401 sin token en todas las rutas `/api/**` (salvo `/api/v1/auth/token`); el rol
  viaja firmado y no se puede cambiar sin invalidar la firma (probado en `JwtServicioTest`); los
  secretos nunca se guardan en texto plano; no hay nada nuevo que instalar.
- **Negativas:** código de seguridad propio (unas 240 líneas en el paquete `security`) que hay que cuidar: comparación de
  firmas en tiempo constante, se rechaza cualquier algoritmo distinto de HS256 y se valida el
  vencimiento. No hay renovación de tokens (refresh) ni lista de revocación; se mitiga porque en
  cada petición se verifica que el cliente siga activo en la base de datos: desactivarlo bloquea
  sus tokens de inmediato.
- **Deuda técnica asumida:** HTTPS. En el laboratorio la API corre en HTTP (puerto 8080) y el
  token viaja sin cifrar. Condición para revisarla: antes de producción se activa HTTPS en Spring
  Boot con un certificado gratuito y se rota `SITRA_JWT_SECRET`.
