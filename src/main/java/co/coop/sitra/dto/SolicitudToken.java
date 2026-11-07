package co.coop.sitra.dto;

/** POST /api/v1/auth/token — credenciales del cliente (billetera o auditor). */
public record SolicitudToken(String clientId, String secreto) {
}
