package co.coop.sitra.dto;

/** Cuerpo JSON de todo error: código HTTP, nombre corto y explicación. */
public record ErrorResponse(int estado, String error, String mensaje) {
}
