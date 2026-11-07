package co.coop.sitra.integration;

/** El núcleo no respondió a tiempo (AC-02: la operación queda FALLIDA, nunca a medias). */
public class NucleoNoDisponibleException extends RuntimeException {

    public NucleoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
