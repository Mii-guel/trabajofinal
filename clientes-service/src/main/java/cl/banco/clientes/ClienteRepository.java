package cl.banco.clientes;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
  boolean existsByRut(String rut);
}
