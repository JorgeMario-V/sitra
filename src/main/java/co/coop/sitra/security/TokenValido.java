package co.coop.sitra.security;

/** Lo que se sabe de quien llama después de validar su token. */
public record TokenValido(String clientId, String rol) {
}
