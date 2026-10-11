package cl.banco.web;
import cl.banco.common.ServicioNoDisponibleException;
import cl.banco.common.client.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** BFF Web: respuesta completa (cliente + cuentas + movimientos) con degradacion controlada si un servicio cae. */
@RestController
@RequestMapping("/web")
public class WebController {
  public record Transferencia(String origen, String destino, BigDecimal monto) {}
  private final ClientesApi clientes; private final CuentasApi cuentas; private final PagosApi pagos;
  public WebController(ClientesApi c, CuentasApi cu, PagosApi p) { clientes = c; cuentas = cu; pagos = p; }

  @GetMapping("/dashboard/{clienteId}")
  public Map<String, Object> dashboard(@PathVariable Long clienteId) {
    Map<String, Object> r = new LinkedHashMap<>();
    List<String> advertencias = new ArrayList<>();
    r.put("canal", "web");
    r.put("cliente", clientes.obtener(clienteId));            // dato esencial: si falla -> 503/404
    List<?> lista = List.of();
    try { lista = cuentas.porCliente(clienteId); }
    catch (ServicioNoDisponibleException e) { advertencias.add("Cuentas no disponibles temporalmente"); }
    List<Object> movimientos = new ArrayList<>();
    for (Object c : lista) {
      String numero = String.valueOf(((Map<?, ?>) c).get("numero"));
      try { movimientos.addAll(pagos.ultimos(numero)); }
      catch (ServicioNoDisponibleException e) { advertencias.add("Movimientos no disponibles"); break; }
    }
    r.put("cuentas", lista);
    r.put("movimientos", movimientos);
    r.put("advertencias", advertencias);
    return r;
  }

  @PostMapping("/transferencias") @ResponseStatus(HttpStatus.CREATED)
  public Map<?, ?> transferir(@RequestBody Transferencia t) { return pagos.transferir(t.origen(), t.destino(), t.monto(), "WEB"); }
}
