package cl.banco.pagos;
import cl.banco.common.client.CuentasApi;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Procesamiento de pagos, transferencias y depositos.
 * Consistencia distribuida: patron Saga con compensacion (si el abono falla, se revierte el cargo).
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {
  private static final Logger log = LoggerFactory.getLogger(PagoController.class);
  private static final BigDecimal UMBRAL_ALERTA = new BigDecimal("1000000");
  public record PagoRequest(String origen, String destino, BigDecimal monto, String canal, String referencia) {}

  private final PagoRepository repo;
  private final CuentasApi cuentas;
  private final EventosPublisher eventos;
  public PagoController(PagoRepository repo, CuentasApi cuentas, EventosPublisher eventos) {
    this.repo = repo; this.cuentas = cuentas; this.eventos = eventos;
  }

  @GetMapping
  public List<Pago> ultimos(@RequestParam String cuenta) { return repo.findTop20ByOrigenOrDestinoOrderByIdDesc(cuenta, cuenta); }

  @GetMapping("/{id}")
  public Pago obtener(@PathVariable Long id) {
    return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no existe"));
  }

  @PostMapping("/transferencia") @ResponseStatus(HttpStatus.CREATED)
  public Pago transferencia(@RequestBody PagoRequest r) { return procesar("TRANSFERENCIA", r.origen(), r.destino(), r); }

  @PostMapping("/deposito") @ResponseStatus(HttpStatus.CREATED)
  public Pago deposito(@RequestBody PagoRequest r) { return procesar("DEPOSITO", null, r.destino(), r); }

  @PostMapping("/retiro") @ResponseStatus(HttpStatus.CREATED)
  public Pago retiro(@RequestBody PagoRequest r) { return procesar("RETIRO", r.origen(), null, r); }

  @PostMapping("/pago") @ResponseStatus(HttpStatus.CREATED)
  public Pago pago(@RequestBody PagoRequest r) { return procesar("PAGO", r.origen(), null, r); }

  private Pago procesar(String tipo, String origen, String destino, PagoRequest r) {
    if (r.monto() == null || r.monto().signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monto invalido");
    if (origen == null && destino == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta requerida");
    Pago p = repo.save(new Pago(tipo, origen, destino, r.monto(), r.canal(), r.referencia()));
    try {
      if (origen != null) cuentas.mover(origen, r.monto().negate());            // 1) cargo
      if (destino != null) {
        try { cuentas.mover(destino, r.monto()); }                              // 2) abono
        catch (RuntimeException e) {
          if (origen != null) {
            try { cuentas.mover(origen, r.monto()); }                           // 3) compensacion
            catch (RuntimeException ce) { log.error("COMPENSACION FALLIDA pago {}", p.id); eventos.alerta(p, "COMPENSACION_FALLIDA"); }
          }
          throw e;
        }
      }
      p.estado = "COMPLETADA";
      repo.save(p);
      eventos.completada(p);
      if (p.monto.compareTo(UMBRAL_ALERTA) >= 0) eventos.alerta(p, "MONTO_ALTO");
      return p;
    } catch (RuntimeException e) {
      p.estado = "FALLIDA";
      repo.save(p);
      throw e;
    }
  }
}
