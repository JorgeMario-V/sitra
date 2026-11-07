package co.coop.sitra.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Simulador del núcleo legado (no existe ambiente de pruebas del núcleo real).
 * Usa la tabla nucleo_simulado_cuenta, que representa los saldos del núcleo.
 * Modos: "simulado" (responde normal) y "sin-respuesta" (simula que el núcleo no contesta).
 */
@Component
@ConditionalOnExpression("'${sitra.nucleo.modo:simulado}' != 'legado'")
public class NucleoSimuladoConector implements NucleoConector {

    private final JdbcTemplate jdbc;
    private final String modo;

    public NucleoSimuladoConector(JdbcTemplate jdbc, @Value("${sitra.nucleo.modo:simulado}") String modo) {
        this.jdbc = jdbc;
        this.modo = modo;
    }

    @Override
    public ResultadoNucleo ejecutarTransferencia(String numeroCuenta, BigDecimal monto) {
        verificarQueResponde();
        BigDecimal saldo = leerSaldo(numeroCuenta);
        if (saldo == null) {
            return ResultadoNucleo.rechazada("La cuenta no existe en el núcleo");
        }
        if (monto.compareTo(saldo) > 0) {
            return ResultadoNucleo.rechazada("Saldo insuficiente en el núcleo");
        }
        jdbc.update("UPDATE nucleo_simulado_cuenta SET saldo = saldo - ? WHERE numero_cuenta = ?",
                monto, numeroCuenta);
        return ResultadoNucleo.aprobada("NUC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    @Override
    public ResultadoNucleo reversarTransferencia(String numeroCuenta, BigDecimal monto, String comprobante) {
        verificarQueResponde();
        int filas = jdbc.update("UPDATE nucleo_simulado_cuenta SET saldo = saldo + ? WHERE numero_cuenta = ?",
                monto, numeroCuenta);
        if (filas == 0) {
            return ResultadoNucleo.rechazada("La cuenta no existe en el núcleo");
        }
        return ResultadoNucleo.aprobada("REV-" + comprobante);
    }

    @Override
    public BigDecimal consultarSaldo(String numeroCuenta) {
        verificarQueResponde();
        return leerSaldo(numeroCuenta);
    }

    private void verificarQueResponde() {
        if ("sin-respuesta".equals(modo)) {
            throw new NucleoNoDisponibleException("El núcleo no respondió en 5 segundos");
        }
    }

    private BigDecimal leerSaldo(String numeroCuenta) {
        List<BigDecimal> saldos = jdbc.queryForList(
                "SELECT saldo FROM nucleo_simulado_cuenta WHERE numero_cuenta = ?", BigDecimal.class, numeroCuenta);
        return saldos.isEmpty() ? null : saldos.get(0);
    }
}
