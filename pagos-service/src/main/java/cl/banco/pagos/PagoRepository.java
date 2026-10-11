package cl.banco.pagos;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PagoRepository extends JpaRepository<Pago, Long> {
  List<Pago> findTop20ByOrigenOrDestinoOrderByIdDesc(String origen, String destino);
}
