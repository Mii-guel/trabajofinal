package cl.banco.batch;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Migracion inicial del maestro de cuentas legacy (cuentas.csv) a la base relacional. */
@Component
public class LegacyDataLoader implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(LegacyDataLoader.class);
  private final JdbcTemplate jdbc; private final String dir;
  public LegacyDataLoader(JdbcTemplate jdbc, @Value("${batch.data-dir:/data}") String dir) { this.jdbc = jdbc; this.dir = dir; }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    Path f = Path.of(dir, "cuentas.csv");
    if (!Files.exists(f)) { log.warn("No existe {}", f); return; }
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM cuentas_legacy", Long.class);
    if (n != null && n > 0) { log.info("cuentas_legacy ya cargada ({} filas)", n); return; }
    List<String> lines = Files.readAllLines(f).stream().skip(1).filter(s -> !s.isBlank()).toList();
    jdbc.batchUpdate("INSERT INTO cuentas_legacy(numero,cliente,tipo,saldo) VALUES (?,?,?,?) ON CONFLICT DO NOTHING", lines, 500, (ps, l) -> {
      String[] c = l.split(",");
      ps.setString(1, c[0].trim()); ps.setString(2, c[1].trim()); ps.setString(3, c[2].trim()); ps.setBigDecimal(4, new BigDecimal(c[3].trim()));
    });
    log.info("Cargadas {} cuentas legacy", lines.size());
  }
}
