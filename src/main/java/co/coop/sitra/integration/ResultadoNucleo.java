package co.coop.sitra.integration;

/** Respuesta del núcleo: aprobada con comprobante, o rechazada con motivo. */
public record ResultadoNucleo(boolean aprobada, String comprobante, String motivo) {

    public static ResultadoNucleo aprobada(String comprobante) {
        return new ResultadoNucleo(true, comprobante, null);
    }

    public static ResultadoNucleo rechazada(String motivo) {
        return new ResultadoNucleo(false, null, motivo);
    }
}
