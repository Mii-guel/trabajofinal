package cl.banco.cuentas;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
public class Cuenta {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @Column(unique = true, nullable = false) public String numero;
  public Long clienteId;
  public String tipo;    // AHORRO | CORRIENTE
  public String estado;  // ABIERTA | CERRADA
  @Column(precision = 18, scale = 2) public BigDecimal saldo;
  public Cuenta() {}
  public Cuenta(String numero, Long clienteId, String tipo, BigDecimal saldo) {
    this.numero = numero; this.clienteId = clienteId; this.tipo = tipo; this.saldo = saldo; this.estado = "ABIERTA";
  }
}
