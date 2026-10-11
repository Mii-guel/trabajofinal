package cl.banco.pagos;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity
public class Pago {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public String tipo;     // TRANSFERENCIA | DEPOSITO | RETIRO | PAGO
  public String origen, destino, canal, estado, referencia;
  @Column(precision = 18, scale = 2) public BigDecimal monto;
  public Instant fecha = Instant.now();
  public Pago() {}
  public Pago(String tipo, String origen, String destino, BigDecimal monto, String canal, String referencia) {
    this.tipo = tipo; this.origen = origen; this.destino = destino; this.monto = monto;
    this.canal = canal == null ? "API" : canal; this.referencia = referencia; this.estado = "PENDIENTE";
  }
}
