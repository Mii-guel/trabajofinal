package cl.banco.common;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
/** Obtiene el JWT de la peticion actual para propagarlo a otros microservicios. */
public final class Tokens {
  private Tokens() {}
  public static String current() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a instanceof JwtAuthenticationToken j) return j.getToken().getTokenValue();
    throw new IllegalStateException("No hay token en el contexto");
  }
}
