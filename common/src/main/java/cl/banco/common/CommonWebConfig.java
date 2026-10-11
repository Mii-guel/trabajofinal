package cl.banco.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.Map;

@Configuration
public class CommonWebConfig {
  /** RestClient con balanceo de carga client-side (Eureka + Spring Cloud LoadBalancer) y timeouts. */
  @Bean @LoadBalanced @Scope("prototype")
  RestClient.Builder lbRestClientBuilder() {
    var f = new SimpleClientHttpRequestFactory();
    f.setConnectTimeout(3000);
    f.setReadTimeout(10000);
    return RestClient.builder().requestFactory(f);
  }

  /** Registro (log) de cada request: metodo, ruta, status, duracion e instancia que respondio. */
  @Component
  public static class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("requests");
    private static final String HOST = host();
    private static String host() { try { return InetAddress.getLocalHost().getHostName(); } catch (Exception e) { return "?"; } }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) { return r.getRequestURI().startsWith("/actuator"); }
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
      long t = System.currentTimeMillis();
      try { chain.doFilter(req, res); }
      finally { log.info("[{}] {} {} -> {} ({} ms)", HOST, req.getMethod(), req.getRequestURI(), res.getStatus(), System.currentTimeMillis() - t); }
    }
  }

  /** Traduce errores de servicios downstream a respuestas HTTP coherentes. */
  @RestControllerAdvice
  public static class ApiExceptionHandler {
    @ExceptionHandler(HttpClientErrorException.class)
    ResponseEntity<String> cliente(HttpClientErrorException e) {
      return ResponseEntity.status(e.getStatusCode()).header("Content-Type", "application/json").body(e.getResponseBodyAsString());
    }
    @ExceptionHandler(ServicioNoDisponibleException.class)
    ResponseEntity<Map<String, String>> noDisponible(ServicioNoDisponibleException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }
  }
}
