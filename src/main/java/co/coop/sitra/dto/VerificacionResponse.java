package co.coop.sitra.dto;

import java.util.List;

/** HU-03: resultado de recalcular la cadena de huellas de toda la bitácora. */
public record VerificacionResponse(boolean integra, int registrosRevisados, List<Long> filasAlteradas,
                                   List<Long> filasConCadenaRota) {
}
