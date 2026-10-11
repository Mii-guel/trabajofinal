package cl.banco.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Servidor de autorizacion OAuth2.0. Un cliente por canal, cada uno con sus scopes y
 * tiempo de vida de token distinto (autenticacion/autorizacion especificas por canal).
 */
@Configuration
public class AuthServerConfig {
  @Bean @Order(1)
  SecurityFilterChain authServerChain(HttpSecurity http) throws Exception {
    OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
    return http.build();
  }
  @Bean @Order(2)
  SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }

  private RegisteredClient client(String id, String secret, int minutos, String... scopes) {
    RegisteredClient.Builder b = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId(id).clientSecret("{noop}" + secret)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(minutos)).build());
    for (String s : scopes) b.scope(s);
    return b.build();
  }

  @Bean
  RegisteredClientRepository clients() {
    return new InMemoryRegisteredClientRepository(
        client("web-client", "web-secret", 30, "banco.read", "banco.write", "canal.web"),
        client("movil-client", "movil-secret", 15, "banco.read", "banco.write", "canal.movil"),
        client("atm-client", "atm-secret", 5, "banco.read", "banco.write", "canal.atm"),
        client("batch-client", "batch-secret", 30, "banco.admin"));
  }

  @Bean
  JWKSource<SecurityContext> jwkSource() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    KeyPair kp = g.generateKeyPair();
    RSAKey rsa = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
        .privateKey((RSAPrivateKey) kp.getPrivate()).keyID(UUID.randomUUID().toString()).build();
    return new ImmutableJWKSet<>(new JWKSet(rsa));
  }
  @Bean AuthorizationServerSettings settings() { return AuthorizationServerSettings.builder().build(); }
}
