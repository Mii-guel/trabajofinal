package cl.banco.common.client;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class PagosApi extends DownstreamClient {
  public PagosApi(RestClient.Builder b) { super(b, "http://pagos-service"); }

  @CircuitBreaker(name = "pagos", fallbackMethod = "fbL") @Retry(name = "pagos")
  public List<?> ultimos(String cuenta) { return get("/api/pagos?cuenta={c}", List.class, cuenta); }

  @CircuitBreaker(name = "pagos", fallbackMethod = "fbT")
  public Map<?, ?> transferir(String origen, String destino, BigDecimal monto, String canal) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("origen", origen); b.put("destino", destino); b.put("monto", monto); b.put("canal", canal);
    return post("/api/pagos/transferencia", b, Map.class);
  }

  @CircuitBreaker(name = "pagos", fallbackMethod = "fbR")
  public Map<?, ?> retiro(String origen, BigDecimal monto, String canal) {
    return post("/api/pagos/retiro", Map.of("origen", origen, "monto", monto, "canal", canal), Map.class);
  }

  public List<?> fbL(String c, HttpClientErrorException e) { throw e; }
  public List<?> fbL(String c, Throwable t) { throw caido("pagos-service", t); }
  public Map<?, ?> fbT(String o, String d, BigDecimal m, String c, HttpClientErrorException e) { throw e; }
  public Map<?, ?> fbT(String o, String d, BigDecimal m, String c, Throwable t) { throw caido("pagos-service", t); }
  public Map<?, ?> fbR(String o, BigDecimal m, String c, HttpClientErrorException e) { throw e; }
  public Map<?, ?> fbR(String o, BigDecimal m, String c, Throwable t) { throw caido("pagos-service", t); }
}
