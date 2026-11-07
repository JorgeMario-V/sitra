package co.coop.sitra.service;

import co.coop.sitra.dto.TokenResponse;
import co.coop.sitra.exception.SolicitudRechazadaException;
import co.coop.sitra.model.ClienteApi;
import co.coop.sitra.model.Rol;
import co.coop.sitra.repository.ClienteApiRepository;
import co.coop.sitra.security.JwtServicio;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autenticación (quién es: 401) y autorización por rol (qué puede hacer: 403). */
@Service
public class IdentificacionService {

    private final ClienteApiRepository clientes;
    private final PasswordEncoder codificador;
    private final JwtServicio jwt;

    public IdentificacionService(ClienteApiRepository clientes, PasswordEncoder codificador, JwtServicio jwt) {
        this.clientes = clientes;
        this.codificador = codificador;
        this.jwt = jwt;
    }

    /** POST /api/v1/auth/token: compara el secreto con su hash BCrypt y entrega un token. */
    @Transactional(readOnly = true)
    public TokenResponse emitirToken(String clientId, String secreto) {
        ClienteApi cliente = clientId == null ? null : clientes.findByClientIdAndActivoTrue(clientId).orElse(null);
        if (cliente == null || secreto == null || !codificador.matches(secreto, cliente.getSecretHash())) {
            throw new SolicitudRechazadaException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        String token = jwt.emitir(cliente.getClientId(), cliente.getRol().name());
        return new TokenResponse(token, "Bearer", jwt.getSegundosDeVida());
    }

    /** El cliente del token debe seguir existiendo y activo (si lo desactivan, su token deja de servir). */
    @Transactional(readOnly = true)
    public ClienteApi identificar(String clientId) {
        return clientes.findByClientIdAndActivoTrue(clientId)
                .orElseThrow(() -> new SolicitudRechazadaException(HttpStatus.UNAUTHORIZED,
                        "Cliente no reconocido o inactivo"));
    }

    @Transactional(readOnly = true)
    public ClienteApi exigirRol(String clientId, Rol rol) {
        ClienteApi cliente = identificar(clientId);
        if (cliente.getRol() != rol) {
            throw new SolicitudRechazadaException(HttpStatus.FORBIDDEN,
                    "Su rol (" + cliente.getRol() + ") no permite esta operación");
        }
        return cliente;
    }
}
