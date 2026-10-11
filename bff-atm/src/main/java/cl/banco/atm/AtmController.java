package cl.banco.atm;
import cl.banco.common.client.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** BFF Cajeros: solo operaciones criticas (saldo y retiro) con validaciones estrictas. Sin degradacion: si falla, 503. */
@RestController
@RequestMapping("/atm")
public class AtmController {
  private static final BigDecimal MAX_RETIRO = new BigDecimal("300000"), MULTIPLO = new BigDecimal("10000");
  public record Retiro(String cuenta, BigDecimal monto) {}
  private final CuentasApi cuentas; private final PagosApi pagos;
  public AtmController(CuentasApi cu, PagosApi p) { cuentas = cu; pagos = p; }

  @GetMapping("/saldo/{cuenta}")
  public Map<String, Object> saldo(@PathVariable String cuenta) {
    Map<?, ?> c = cuentas.obtener(cuenta);
    return Map.of("cuenta", cuenta, "saldo", c.get("saldo"));
  }

  @PostMapping("/retiro") @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> retiro(@RequestBody Retiro r) {
    if (r.cuenta() == null || r.monto() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos incompletos");
    if (r.monto().compareTo(MAX_RETIRO) > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximo por retiro: 300000");
    if (r.monto().signum() <= 0 || r.monto().remainder(MULTIPLO).signum() != 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser multiplo de 10000");
    Map<?, ?> p = pagos.retiro(r.cuenta(), r.monto(), "ATM");
    return Map.of("id", p.get("id"), "estado", p.get("estado"), "monto", r.monto());
  }
}
