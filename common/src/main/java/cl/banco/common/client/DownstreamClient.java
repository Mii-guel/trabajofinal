package cl.banco.common.client;
import cl.banco.common.ServicioNoDisponibleException;
import cl.banco.common.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/** Base de los clientes HTTP hacia otros microservicios (nombre logico resuelto por Eureka). */
public abstract class DownstreamClient {
  private static final Logger log = LoggerFactory.getLogger(DownstreamClient.class);
  protected final RestClient rest;
  protected DownstreamClient(RestClient.Builder b, String baseUrl) { this.rest = b.clone().baseUrl(baseUrl).build(); }

  protected <T> T get(String uri, Class<T> type, Object... vars) {
    return rest.get().uri(uri, vars).header("Authorization", "Bearer " + Tokens.current()).retrieve().body(type);
  }
  protected <T> T post(String uri, Object body, Class<T> type, Object... vars) {
    return rest.post().uri(uri, vars).header("Authorization", "Bearer " + Tokens.current())
        .header("Content-Type", "application/json").body(body).retrieve().body(type);
  }
  /** Comportamiento alternativo: respuesta controlada cuando el servicio no responde o el circuito esta abierto. */
  protected static ServicioNoDisponibleException caido(String servicio, Throwable t) {
    log.warn("Fallback {} ({}): {}", servicio, t.getClass().getSimpleName(), t.getMessage());
    return new ServicioNoDisponibleException(servicio + " no disponible, intente mas tarde");
  }
}
