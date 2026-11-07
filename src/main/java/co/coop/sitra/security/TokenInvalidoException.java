package co.coop.sitra.security;

/** El token falta, está mal formado, fue alterado o venció: la respuesta es 401. */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String mensaje) {
        super(mensaje);
    }
}
