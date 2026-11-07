package co.coop.sitra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AC-01 Seguridad: el token solo sirve si nadie lo alteró y no ha vencido. No necesita base de datos. */
class JwtServicioTest {

    private static final String SECRETO = "secreto-solo-para-pruebas-con-mas-de-32-caracteres";
    private final ObjectMapper json = new ObjectMapper();
    private final Clock a_las_2 = Clock.fixed(Instant.parse("2026-11-07T14:00:00Z"), ZoneOffset.UTC);
    private final Clock a_las_3 = Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Un token emitido se valida y conserva quién es y su rol")
    void emiteYValida() {
        JwtServicio servicio = new JwtServicio(SECRETO, 30, json, a_las_2);
        TokenValido token = servicio.validar(servicio.emitir("billetera-externa", "BILLETERA"));
        assertEquals("billetera-externa", token.clientId());
        assertEquals("BILLETERA", token.rol());
    }

    @Test
    @DisplayName("Si alguien cambia el contenido del token (por ejemplo el rol), la firma ya no coincide")
    void rechazaTokenAlterado() {
        JwtServicio servicio = new JwtServicio(SECRETO, 30, json, a_las_2);
        String[] partes = servicio.emitir("billetera-externa", "BILLETERA").split("\\.");
        String datosFalsos = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"billetera-externa\",\"rol\":\"AUDITOR\",\"iat\":1793973600,\"exp\":1893973600}"
                        .getBytes(StandardCharsets.UTF_8));
        String falso = partes[0] + "." + datosFalsos + "." + partes[2];
        assertThrows(TokenInvalidoException.class, () -> servicio.validar(falso));
    }

    @Test
    @DisplayName("Un token firmado con otro secreto no sirve")
    void rechazaOtroSecreto() {
        JwtServicio propio = new JwtServicio(SECRETO, 30, json, a_las_2);
        JwtServicio ajeno = new JwtServicio("otro-secreto-distinto-tambien-de-mas-de-32-caracteres", 30, json, a_las_2);
        String token = ajeno.emitir("billetera-externa", "BILLETERA");
        assertThrows(TokenInvalidoException.class, () -> propio.validar(token));
    }

    @Test
    @DisplayName("Un token vencido se rechaza")
    void rechazaTokenVencido() {
        String token = new JwtServicio(SECRETO, 30, json, a_las_2).emitir("billetera-externa", "BILLETERA");
        JwtServicio unaHoraDespues = new JwtServicio(SECRETO, 30, json, a_las_3);
        TokenInvalidoException e = assertThrows(TokenInvalidoException.class, () -> unaHoraDespues.validar(token));
        assertEquals("El token venció", e.getMessage());
    }

    @Test
    @DisplayName("Un texto que no es un token se rechaza")
    void rechazaTokenMalFormado() {
        JwtServicio servicio = new JwtServicio(SECRETO, 30, json, a_las_2);
        assertThrows(TokenInvalidoException.class, () -> servicio.validar("esto-no-es-un-token"));
        assertThrows(TokenInvalidoException.class, () -> servicio.validar("a.b.c"));
    }

    @Test
    @DisplayName("Un secreto corto se rechaza al arrancar")
    void exigeSecretoLargo() {
        assertThrows(IllegalStateException.class, () -> new JwtServicio("corto", 30, json, a_las_2));
    }
}
