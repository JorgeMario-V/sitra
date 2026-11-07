package co.coop.sitra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Configuración de seguridad: BCrypt para comparar el secreto con su hash (nunca en texto plano). */
@Configuration
public class ConfiguracionSeguridad {

    @Bean
    public PasswordEncoder codificadorDeSecretos() {
        return new BCryptPasswordEncoder();
    }
}
