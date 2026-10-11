package cl.banco.common.client;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class CuentasApi extends DownstreamClient {
  public CuentasApi(RestClient.Builder b) { super(b, "http://cuentas-service"); }

  @CircuitBreaker(name = "cuentas", fallbackMethod = "fbL") @Retry(name = "cuentas")
  public List<?> porCliente(Long clienteId) { return get("/api/cuentas?clienteId={c}", List.class, clienteId); }

  @CircuitBreaker(name = "cuentas", fallbackMethod = "fbM") @Retry(name = "cuentas")
  public Map<?, ?> obtener(String numero) { return get("/api/cuentas/{n}", Map.class, numero); }

  /** monto positivo = abono, negativo = cargo */
  @CircuitBreaker(name = "cuentas", fallbackMethod = "fbMov")
  public Map<?, ?> mover(String numero, BigDecimal monto) {
    return post("/api/cuentas/{n}/movimiento", Map.of("monto", monto), Map.class, numero);
  }

  public List<?> fbL(Long c, HttpClientErrorException e) { throw e; }
  public List<?> fbL(Long c, Throwable t) { throw caido("cuentas-service", t); }
  public Map<?, ?> fbM(String n, HttpClientErrorException e) { throw e; }
  public Map<?, ?> fbM(String n, Throwable t) { throw caido("cuentas-service", t); }
  public Map<?, ?> fbMov(String n, BigDecimal m, HttpClientErrorException e) { throw e; }
  public Map<?, ?> fbMov(String n, BigDecimal m, Throwable t) { throw caido("cuentas-service", t); }
}
