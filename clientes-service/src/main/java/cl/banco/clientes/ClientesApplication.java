package cl.banco.clientes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "cl.banco")
public class ClientesApplication {
  public static void main(String[] a) { SpringApplication.run(ClientesApplication.class, a); }

  @Bean CommandLineRunner seed(ClienteRepository r) {
    return a -> {
      try {
        if (!r.existsByRut("11.111.111-1")) r.save(new Cliente("11.111.111-1", "Ana Perez", "ana@mail.cl", "+56911111111", "PERSONA"));
        if (!r.existsByRut("22.222.222-2")) r.save(new Cliente("22.222.222-2", "Luis Soto", "luis@mail.cl", "+56922222222", "PERSONA"));
        if (!r.existsByRut("76.333.333-3")) r.save(new Cliente("76.333.333-3", "Comercial Rojas SpA", "contacto@rojas.cl", "+56933333333", "EMPRESA"));
      } catch (Exception e) { /* otra instancia ya cargo los datos */ }
    };
  }
}
