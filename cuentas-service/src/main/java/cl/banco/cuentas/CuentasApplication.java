package cl.banco.cuentas;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "cl.banco")
public class CuentasApplication {
  public static void main(String[] a) { SpringApplication.run(CuentasApplication.class, a); }

  @Bean CommandLineRunner seed(CuentaRepository r) {
    return a -> {
      try {
        if (!r.existsByNumero("1001")) r.save(new Cuenta("1001", 1L, "CORRIENTE", new BigDecimal("500000")));
        if (!r.existsByNumero("1002")) r.save(new Cuenta("1002", 2L, "AHORRO", new BigDecimal("250000")));
        if (!r.existsByNumero("1003")) r.save(new Cuenta("1003", 3L, "CORRIENTE", new BigDecimal("2500000")));
      } catch (Exception e) { /* otra instancia ya cargo los datos */ }
    };
  }
}
