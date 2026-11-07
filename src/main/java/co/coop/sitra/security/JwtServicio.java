package co.coop.sitra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Emite y valida tokens JWT firmados con HMAC-SHA256 (HS256), usando solo lo que trae Java
 * (javax.crypto): sin librerías ni productos adicionales (ADR-005, cero presupuesto).
 * El secreto llega por la variable SITRA_JWT_SECRET; si no existe, se genera uno aleatorio al
 * arrancar (los tokens dejan de servir si la aplicación se reinicia).
 */
@Component
public class JwtServicio {

    private static final Logger LOG = LoggerFactory.getLogger(JwtServicio.class);
    private static final String ALGORITMO = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secreto;
    private final long segundosDeVida;
    private final ObjectMapper json;
    private final Clock reloj;

    @Autowired
    public JwtServicio(@Value("${sitra.jwt.secreto:}") String secreto,
                       @Value("${sitra.jwt.minutos:30}") long minutos,
                       ObjectMapper json) {
        this(secreto, minutos, json, Clock.systemUTC());
    }

    JwtServicio(String secreto, long minutos, ObjectMapper json, Clock reloj) {
        this.secreto = prepararSecreto(secreto);
        this.segundosDeVida = minutos * 60;
        this.json = json;
        this.reloj = reloj;
    }

    private static byte[] prepararSecreto(String configurado) {
        if (configurado == null || configurado.isBlank()) {
            byte[] aleatorio = new byte[32];
            new SecureRandom().nextBytes(aleatorio);
            LOG.warn("SITRA_JWT_SECRET no está definido: se generó un secreto aleatorio para esta ejecución");
            return aleatorio;
        }
        byte[] bytes = configurado.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("SITRA_JWT_SECRET debe tener al menos 32 caracteres");
        }
        return bytes;
    }

    public long getSegundosDeVida() {
        return segundosDeVida;
    }

    /** Crea un token para el cliente: dice quién es (sub), su rol y cuándo vence (exp). */
    public String emitir(String clientId, String rol) {
        long ahora = reloj.instant().getEpochSecond();
        Map<String, Object> cabecera = new LinkedHashMap<>();
        cabecera.put("alg", "HS256");
        cabecera.put("typ", "JWT");
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("sub", clientId);
        datos.put("rol", rol);
        datos.put("iat", ahora);
        datos.put("exp", ahora + segundosDeVida);
        String sinFirma = codificar(cabecera) + "." + codificar(datos);
        return sinFirma + "." + B64.encodeToString(firmar(sinFirma));
    }

    /** Revisa la firma y la fecha de vencimiento. Si algo falla, lanza TokenInvalidoException. */
    public TokenValido validar(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException("Falta el token");
        }
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            throw new TokenInvalidoException("Token mal formado");
        }
        byte[] firmaRecibida;
        try {
            firmaRecibida = B64D.decode(partes[2]);
        } catch (IllegalArgumentException e) {
            throw new TokenInvalidoException("Token mal formado");
        }
        byte[] firmaEsperada = firmar(partes[0] + "." + partes[1]);
        if (!MessageDigest.isEqual(firmaEsperada, firmaRecibida)) {
            throw new TokenInvalidoException("La firma del token no es válida");
        }
        Map<?, ?> cabecera = decodificar(partes[0]);
        if (!"HS256".equals(cabecera.get("alg"))) {
            throw new TokenInvalidoException("Algoritmo de firma no permitido");
        }
        Map<?, ?> datos = decodificar(partes[1]);
        Object sub = datos.get("sub");
        Object rol = datos.get("rol");
        Object exp = datos.get("exp");
        if (!(sub instanceof String) || !(rol instanceof String) || !(exp instanceof Number)) {
            throw new TokenInvalidoException("Token incompleto");
        }
        if (((Number) exp).longValue() <= reloj.instant().getEpochSecond()) {
            throw new TokenInvalidoException("El token venció");
        }
        return new TokenValido((String) sub, (String) rol);
    }

    private byte[] firmar(String contenido) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    private String codificar(Map<String, Object> mapa) {
        try {
            return B64.encodeToString(json.writeValueAsBytes(mapa));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el token", e);
        }
    }

    private Map<?, ?> decodificar(String parte) {
        try {
            return json.readValue(B64D.decode(parte), Map.class);
        } catch (Exception e) {
            throw new TokenInvalidoException("Token mal formado");
        }
    }
}
