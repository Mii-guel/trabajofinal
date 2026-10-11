package cl.banco.common.client;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class ClientesApi extends DownstreamClient {
  public ClientesApi(RestClient.Builder b) { super(b, "http://clientes-service"); }

  @CircuitBreaker(name = "clientes", fallbackMethod = "fb") @Retry(name = "clientes")
  public Map<?, ?> obtener(Long id) { return get("/api/clientes/{id}", Map.class, id); }

  public Map<?, ?> fb(Long id, HttpClientErrorException e) { throw e; }
  public Map<?, ?> fb(Long id, Throwable t) { throw caido("clientes-service", t); }
}
