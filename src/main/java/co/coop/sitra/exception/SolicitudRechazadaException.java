package co.coop.sitra.exception;

import org.springframework.http.HttpStatus;

/** Regla de negocio no cumplida: lleva el código HTTP que debe responder el controlador. */
public class SolicitudRechazadaException extends RuntimeException {

    private final HttpStatus estado;

    public SolicitudRechazadaException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
