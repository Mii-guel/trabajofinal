package cl.banco.clientes;
import jakarta.persistence.*;
@Entity
public class Cliente {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @Column(unique = true, nullable = false) public String rut;
  public String nombre, email, telefono, tipo; // tipo: PERSONA | EMPRESA
  public Cliente() {}
  public Cliente(String rut, String nombre, String email, String telefono, String tipo) {
    this.rut = rut; this.nombre = nombre; this.email = email; this.telefono = telefono; this.tipo = tipo;
  }
}
