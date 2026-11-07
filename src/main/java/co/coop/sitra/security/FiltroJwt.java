package co.coop.sitra.security;

import co.coop.sitra.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Capa de seguridad (AC-01): toda petición a /api/** necesita "Authorization: Bearer <token>".
 * Sin token válido responde 401 y la petición nunca llega a los controladores.
 * Solo /api/v1/auth/** queda abierto (es donde se pide el token).
 */
@Component
public class FiltroJwt extends OncePerRequestFilter {

    public static final String ATRIBUTO_CLIENTE = "sitra.clienteId";
    private static final String PREFIJO = "Bearer ";

    private final JwtServicio jwt;
    private final ObjectMapper json;

    public FiltroJwt(JwtServicio jwt, ObjectMapper json) {
        this.jwt = jwt;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        return !ruta.startsWith("/api/") || ruta.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String encabezado = peticion.getHeader("Authorization");
        if (encabezado == null || !encabezado.startsWith(PREFIJO)) {
            rechazar(respuesta, "Falta el token: envíe el encabezado Authorization: Bearer <token>");
            return;
        }
        try {
            TokenValido token = jwt.validar(encabezado.substring(PREFIJO.length()).trim());
            peticion.setAttribute(ATRIBUTO_CLIENTE, token.clientId());
        } catch (TokenInvalidoException e) {
            rechazar(respuesta, e.getMessage());
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private void rechazar(HttpServletResponse respuesta, String mensaje) throws IOException {
        respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        respuesta.setContentType("application/json;charset=UTF-8");
        respuesta.getWriter().write(json.writeValueAsString(new ErrorResponse(401, "No autorizado", mensaje)));
    }
}
