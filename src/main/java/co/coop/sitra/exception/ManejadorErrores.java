package co.coop.sitra.exception;

import co.coop.sitra.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Manejo centralizado de errores (AC-03): cada error se convierte en su código HTTP. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(SolicitudRechazadaException.class)
    public ResponseEntity<ErrorResponse> rechazada(SolicitudRechazadaException e) {
        return respuesta(e.getEstado(), e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> formatoInvalido(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo o los parámetros de la petición no tienen el formato esperado");
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ErrorResponse> sinIdentificacion(ServletRequestBindingException e) {
        return respuesta(HttpStatus.UNAUTHORIZED, "La petición no trae un cliente identificado");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException e) {
        return respuesta(HttpStatus.CONFLICT, "La base de datos rechazó la operación por una regla de integridad");
    }

    /**
     * Bloqueo o deadlock de MariaDB (varias escrituras simultáneas en la bitácora, riesgo R-03):
     * todo se deshizo, así que la billetera puede reintentar con el mismo código de operación.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> bloqueo(PessimisticLockingFailureException e) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE,
                "La base de datos está ocupada: reintente con el mismo código de operación");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception e) {
        // Errores propios de Spring que ya traen su código (404 ruta inexistente, 405 método no permitido...)
        if (e instanceof org.springframework.web.ErrorResponse conCodigo) {
            return respuesta(HttpStatus.valueOf(conCodigo.getStatusCode().value()), e.getMessage());
        }
        LOG.error("Error no controlado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorResponse(estado.value(), estado.getReasonPhrase(), mensaje));
    }
}
