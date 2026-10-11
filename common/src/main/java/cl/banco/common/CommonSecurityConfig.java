package cl.banco.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad OAuth2 (Resource Server) compartida por todos los microservicios y BFFs.
 * Los scopes exigidos se configuran por servicio: security.read / security.write
 * (backend: banco.read / banco.write; BFF: canal.web | canal.movil | canal.atm).
 */
@Configuration
@EnableWebSecurity
public class CommonSecurityConfig {
  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http,
      @Value("${security.read:banco.read}") String read,
      @Value("${security.write:banco.write}") String write) throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
            .requestMatchers(HttpMethod.GET, "/**").hasAuthority("SCOPE_" + read)
            .anyRequest().hasAuthority("SCOPE_" + write))
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
    return http.build();
  }
}
