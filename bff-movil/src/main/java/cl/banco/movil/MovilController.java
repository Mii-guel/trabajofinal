package cl.banco.movil;
import cl.banco.common.ServicioNoDisponibleException;
import cl.banco.common.client.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** BFF Movil: payload minimo (solo datos esenciales) para ahorrar ancho de banda. */
@RestController
@RequestMapping("/movil")
public class MovilController {
  public record Transferencia(String origen, String destino, BigDecimal monto) {}
  private final ClientesApi clientes; private final CuentasApi cuentas; private final PagosApi pagos;
  public MovilController(ClientesApi c, CuentasApi cu, PagosApi p) { clientes = c; cuentas = cu; pagos = p; }

  @GetMapping("/resumen/{clienteId}")
  public Map<String, Object> resumen(@PathVariable Long clienteId) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("nombre", clientes.obtener(clienteId).get("nombre"));
    try {
      List<Map<String, Object>> lista = new ArrayList<>();
      for (Object c : cuentas.porCliente(clienteId)) {
        Map<?, ?> m = (Map<?, ?>) c;
        Map<String, Object> liviana = new LinkedHashMap<>();
        liviana.put("numero", m.get("numero")); liviana.put("saldo", m.get("saldo"));
        lista.add(liviana);
      }
      r.put("cuentas", lista);
    } catch (ServicioNoDisponibleException e) { r.put("aviso", "Saldos no disponibles por ahora, intente en unos minutos"); }
    return r;
  }

  @PostMapping("/transferencias") @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> transferir(@RequestBody Transferencia t) {
    Map<?, ?> p = pagos.transferir(t.origen(), t.destino(), t.monto(), "MOVIL");
    return Map.of("id", p.get("id"), "estado", p.get("estado"));
  }
}
