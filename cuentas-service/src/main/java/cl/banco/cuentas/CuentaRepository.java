package cl.banco.cuentas;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {
  Optional<Cuenta> findByNumero(String numero);
  boolean existsByNumero(String numero);
  List<Cuenta> findByClienteId(Long clienteId);
  /** Bloqueo pesimista: evita saldos inconsistentes con varias instancias escribiendo a la vez. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Cuenta> findWithLockByNumero(String numero);
}
