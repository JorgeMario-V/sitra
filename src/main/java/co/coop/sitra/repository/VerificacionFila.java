package co.coop.sitra.repository;

/** Resultado de recalcular la huella de una fila de la bitácora (consulta nativa). */
public interface VerificacionFila {

    Long getId();

    /** OK o ROTA (falta o se borró una fila anterior). */
    String getCadena();

    /** OK o ALTERADO (alguien cambió el contenido de la fila). */
    String getHuella();
}
