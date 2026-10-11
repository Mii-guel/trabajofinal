package cl.banco.cuentas;
import cl.banco.common.client.ClientesApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Gestion de cuentas: apertura, cierre y mantenimiento (movimientos de saldo). */
@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {
  public record Apertura(Long clienteId, String tipo, BigDecimal saldoInicial) {}
  public record Movimiento(BigDecimal monto) {}

  private final CuentaRepository repo;
  private final ClientesApi clientes;
  public CuentaController(CuentaRepository repo, ClientesApi clientes) { this.repo = repo; this.clientes = clientes; }

  @GetMapping
  public List<Cuenta> listar(@RequestParam(required = false) Long clienteId) {
    return clienteId == null ? repo.findAll() : repo.findByClienteId(clienteId);
  }

  @GetMapping("/{numero}")
  public Cuenta obtener(@PathVariable String numero) {
    return repo.findByNumero(numero).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no existe"));
  }

  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public Cuenta abrir(@RequestBody Apertura r) {
    if (r.clienteId() == null || (!"AHORRO".equals(r.tipo()) && !"CORRIENTE".equals(r.tipo())))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clienteId y tipo (AHORRO|CORRIENTE) son obligatorios");
    clientes.obtener(r.clienteId()); // valida el cliente en clientes-service (404 si no existe, 503 si esta caido)
    String numero;
    do { numero = String.valueOf(2_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L)); } while (repo.existsByNumero(numero));
    BigDecimal ini = r.saldoInicial() == null ? BigDecimal.ZERO : r.saldoInicial();
    return repo.save(new Cuenta(numero, r.clienteId(), r.tipo(), ini));
  }

  @PostMapping("/{numero}/cierre") @Transactional
  public Cuenta cerrar(@PathVariable String numero) {
    Cuenta c = repo.findWithLockByNumero(numero).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no existe"));
    if (c.saldo.signum() != 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta debe tener saldo 0 para cerrarse");
    c.estado = "CERRADA";
    return repo.save(c);
  }

  /** monto positivo = abono, negativo = cargo */
  @PostMapping("/{numero}/movimiento") @Transactional
  public Cuenta mover(@PathVariable String numero, @RequestBody Movimiento m) {
    Cuenta c = repo.findWithLockByNumero(numero).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no existe"));
    if (!"ABIERTA".equals(c.estado)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cuenta cerrada");
    BigDecimal nuevo = c.saldo.add(m.monto());
    if (nuevo.signum() < 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Saldo insuficiente");
    c.saldo = nuevo;
    return repo.save(c);
  }
}
