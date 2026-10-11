package cl.banco.batch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Migracion de los procesos batch legacy (COBOL/Shell) a Spring Batch.
 *  1) reporteTransaccionesDiarias  -> deteccion de anomalias + resumen
 *  2) calculoInteresesMensuales    -> interes mensual por cuenta
 *  3) estadosCuentaAnuales         -> consolidado anual por cuenta (auditoria)
 * Todos: chunk-oriented, multi-hilo, retry ante fallos transitorios, skip de registros corruptos,
 * escritura idempotente (UPSERT) para poder re-ejecutar sin duplicar datos.
 */
@Configuration
public class BatchJobsConfig {
  private static final Logger log = LoggerFactory.getLogger(BatchJobsConfig.class);
  static final int CHUNK = 500, HILOS = 4;
  static final BigDecimal UMBRAL_ANOMALIA = new BigDecimal("5000000");
  static final Set<String> TIPOS = Set.of("DEPOSITO", "RETIRO", "TRANSFERENCIA", "PAGO");
  static final Map<String, BigDecimal> TASA_ANUAL = Map.of("AHORRO", new BigDecimal("0.0300"), "CORRIENTE", BigDecimal.ZERO, "PRESTAMO", new BigDecimal("0.1200"));

  public record TransaccionCsv(Long id, String cuenta, String fecha, String tipo, BigDecimal monto) {}
  public record TransaccionAnalizada(TransaccionCsv tx, LocalDate fecha, boolean anomalia, String motivo) {}
  public record CuentaLegacy(String numero, String tipo, BigDecimal saldo) {}
  public record Interes(String cuenta, String periodo, String tipo, BigDecimal base, BigDecimal tasa, BigDecimal interes) {}
  public record Estado(String cuenta, int anio, BigDecimal saldo, BigDecimal depositos, BigDecimal cargos, BigDecimal intereses, long movs) {}

  private TaskExecutor ejecutor() { var e = new SimpleAsyncTaskExecutor("batch-"); e.setConcurrencyLimit(HILOS); return e; }
  private FixedBackOffPolicy espera() { var b = new FixedBackOffPolicy(); b.setBackOffPeriod(1000); return b; }

  // =============== JOB 1: Reporte de transacciones diarias ===============
  @Bean @StepScope
  SynchronizedItemStreamReader<TransaccionCsv> txReader(@Value("${batch.data-dir:/data}") String dir) {
    FlatFileItemReader<TransaccionCsv> r = new FlatFileItemReaderBuilder<TransaccionCsv>()
        .name("txReader").resource(new FileSystemResource(dir + "/transacciones.csv")).linesToSkip(1)
        .delimited().names("id", "cuenta", "fecha", "tipo", "monto").targetType(TransaccionCsv.class)
        .saveState(false).build();   // multi-hilo: sin estado; la re-ejecucion es segura por ser idempotente
    SynchronizedItemStreamReader<TransaccionCsv> s = new SynchronizedItemStreamReader<>();
    s.setDelegate(r);
    return s;
  }

  @Bean @StepScope
  ItemProcessor<TransaccionCsv, TransaccionAnalizada> txProcessor(@Value("#{jobParameters['valor']}") String filtroFecha) {
    return t -> {
      LocalDate f = LocalDate.parse(t.fecha());
      if (filtroFecha != null && !"all".equals(filtroFecha) && !f.toString().equals(filtroFecha)) return null; // filtra
      String motivo = null;
      if (t.monto() == null || t.monto().signum() <= 0) motivo = "MONTO_NO_POSITIVO";
      else if (t.monto().compareTo(UMBRAL_ANOMALIA) > 0) motivo = "MONTO_SOBRE_UMBRAL";
      else if (!TIPOS.contains(t.tipo())) motivo = "TIPO_DESCONOCIDO";
      return new TransaccionAnalizada(t, f, motivo != null, motivo);
    };
  }

  @Bean
  JdbcBatchItemWriter<TransaccionAnalizada> txWriter(DataSource ds) {
    return new JdbcBatchItemWriterBuilder<TransaccionAnalizada>().dataSource(ds).assertUpdates(false)
        .sql("INSERT INTO reporte_transacciones(id,cuenta,fecha,tipo,monto,anomalia,motivo) VALUES (:id,:cuenta,:fecha,:tipo,:monto,:anomalia,:motivo) "
           + "ON CONFLICT (id) DO UPDATE SET cuenta=EXCLUDED.cuenta, fecha=EXCLUDED.fecha, tipo=EXCLUDED.tipo, monto=EXCLUDED.monto, anomalia=EXCLUDED.anomalia, motivo=EXCLUDED.motivo")
        .itemSqlParameterSourceProvider(i -> new MapSqlParameterSource().addValue("id", i.tx().id()).addValue("cuenta", i.tx().cuenta())
            .addValue("fecha", Date.valueOf(i.fecha())).addValue("tipo", i.tx().tipo()).addValue("monto", i.tx().monto())
            .addValue("anomalia", i.anomalia()).addValue("motivo", i.motivo()))
        .build();
  }

  @Bean
  Step procesarTransaccionesStep(JobRepository repo, PlatformTransactionManager tm, SynchronizedItemStreamReader<TransaccionCsv> txReader,
                                 ItemProcessor<TransaccionCsv, TransaccionAnalizada> txProcessor, JdbcBatchItemWriter<TransaccionAnalizada> txWriter) {
    return new StepBuilder("procesarTransaccionesStep", repo)
        .<TransaccionCsv, TransaccionAnalizada>chunk(CHUNK, tm).reader(txReader).processor(txProcessor).writer(txWriter)
        .faultTolerant()
        .retry(TransientDataAccessException.class).retryLimit(3).backOffPolicy(espera())   // caida temporal de la BD
        .skip(FlatFileParseException.class).skip(DateTimeParseException.class).skipLimit(50) // registros corruptos
        .taskExecutor(ejecutor())                                                           // paralelismo
        .build();
  }

  @Bean
  Step resumenDiarioStep(JobRepository repo, PlatformTransactionManager tm, JdbcTemplate jdbc) {
    return new StepBuilder("resumenDiarioStep", repo).tasklet((c, ch) -> {
      int n = jdbc.update("INSERT INTO resumen_diario(fecha,tipo,cantidad,total,anomalias) "
          + "SELECT fecha, tipo, COUNT(*), SUM(monto), SUM(CASE WHEN anomalia THEN 1 ELSE 0 END) FROM reporte_transacciones GROUP BY fecha, tipo "
          + "ON CONFLICT (fecha,tipo) DO UPDATE SET cantidad=EXCLUDED.cantidad, total=EXCLUDED.total, anomalias=EXCLUDED.anomalias");
      log.info("Resumen diario generado: {} filas (fecha,tipo)", n);
      return RepeatStatus.FINISHED;
    }, tm).build();
  }

  @Bean
  Job reporteTransaccionesDiarias(JobRepository repo, Step procesarTransaccionesStep, Step resumenDiarioStep) {
    return new JobBuilder("reporteTransaccionesDiarias", repo).start(procesarTransaccionesStep).next(resumenDiarioStep).build();
  }

  // =============== lector comun de cuentas (BD, thread-safe) ===============
  @Bean @StepScope
  JdbcPagingItemReader<CuentaLegacy> cuentasReader(DataSource ds) {
    return new JdbcPagingItemReaderBuilder<CuentaLegacy>().name("cuentasReader").dataSource(ds).saveState(false)
        .selectClause("numero, tipo, saldo").fromClause("cuentas_legacy").sortKeys(Map.of("numero", Order.ASCENDING))
        .pageSize(CHUNK).rowMapper((rs, i) -> new CuentaLegacy(rs.getString("numero"), rs.getString("tipo"), rs.getBigDecimal("saldo")))
        .build();
  }

  // =============== JOB 2: Calculo de intereses mensuales ===============
  @Bean @StepScope
  ItemProcessor<CuentaLegacy, Interes> interesProcessor(@Value("#{jobParameters['valor']}") String periodo) {
    String per = (periodo == null || "all".equals(periodo)) ? java.time.YearMonth.now().toString() : periodo;
    return c -> {
      BigDecimal tasa = TASA_ANUAL.getOrDefault(c.tipo(), BigDecimal.ZERO);
      BigDecimal interes = c.saldo().multiply(tasa).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
      return new Interes(c.numero(), per, c.tipo(), c.saldo(), tasa, interes);
    };
  }

  @Bean
  JdbcBatchItemWriter<Interes> interesWriter(DataSource ds) {
    return new JdbcBatchItemWriterBuilder<Interes>().dataSource(ds).assertUpdates(false)
        .sql("INSERT INTO intereses_calculados(cuenta,periodo,tipo,saldo_base,tasa_anual,interes) VALUES (:cuenta,:periodo,:tipo,:base,:tasa,:interes) "
           + "ON CONFLICT (cuenta,periodo) DO UPDATE SET tipo=EXCLUDED.tipo, saldo_base=EXCLUDED.saldo_base, tasa_anual=EXCLUDED.tasa_anual, interes=EXCLUDED.interes")
        .itemSqlParameterSourceProvider(i -> new MapSqlParameterSource().addValue("cuenta", i.cuenta()).addValue("periodo", i.periodo())
            .addValue("tipo", i.tipo()).addValue("base", i.base()).addValue("tasa", i.tasa()).addValue("interes", i.interes()))
        .build();
  }

  @Bean
  Step calcularInteresesStep(JobRepository repo, PlatformTransactionManager tm, JdbcPagingItemReader<CuentaLegacy> cuentasReader,
                             ItemProcessor<CuentaLegacy, Interes> interesProcessor, JdbcBatchItemWriter<Interes> interesWriter) {
    return new StepBuilder("calcularInteresesStep", repo).<CuentaLegacy, Interes>chunk(CHUNK, tm)
        .reader(cuentasReader).processor(interesProcessor).writer(interesWriter)
        .faultTolerant().retry(TransientDataAccessException.class).retryLimit(3).backOffPolicy(espera())
        .taskExecutor(ejecutor()).build();
  }

  @Bean
  Job calculoInteresesMensuales(JobRepository repo, Step calcularInteresesStep) {
    return new JobBuilder("calculoInteresesMensuales", repo).start(calcularInteresesStep).build();
  }

  // =============== JOB 3: Estados de cuenta anuales ===============
  @Bean @StepScope
  ItemProcessor<CuentaLegacy, Estado> estadoProcessor(JdbcTemplate jdbc, @Value("#{jobParameters['valor']}") String valor) {
    int anio = (valor == null || "all".equals(valor)) ? LocalDate.now().getYear() : Integer.parseInt(valor);
    return c -> {
      Map<String, Object> q = jdbc.queryForMap(
          "SELECT COALESCE(SUM(CASE WHEN tipo='DEPOSITO' THEN monto END),0) AS dep, COALESCE(SUM(CASE WHEN tipo<>'DEPOSITO' THEN monto END),0) AS car, COUNT(*) AS n "
        + "FROM reporte_transacciones WHERE cuenta=? AND anomalia=false AND EXTRACT(YEAR FROM fecha)=?", c.numero(), anio);
      BigDecimal intereses = jdbc.queryForObject("SELECT COALESCE(SUM(interes),0) FROM intereses_calculados WHERE cuenta=? AND periodo LIKE ?",
          BigDecimal.class, c.numero(), anio + "-%");
      return new Estado(c.numero(), anio, c.saldo(), (BigDecimal) q.get("dep"), (BigDecimal) q.get("car"), intereses, ((Number) q.get("n")).longValue());
    };
  }

  @Bean
  JdbcBatchItemWriter<Estado> estadoWriter(DataSource ds) {
    return new JdbcBatchItemWriterBuilder<Estado>().dataSource(ds).assertUpdates(false)
        .sql("INSERT INTO estados_cuenta(cuenta,anio,saldo_actual,total_depositos,total_cargos,intereses,movimientos) VALUES (:cuenta,:anio,:saldo,:dep,:car,:int,:movs) "
           + "ON CONFLICT (cuenta,anio) DO UPDATE SET saldo_actual=EXCLUDED.saldo_actual, total_depositos=EXCLUDED.total_depositos, total_cargos=EXCLUDED.total_cargos, intereses=EXCLUDED.intereses, movimientos=EXCLUDED.movimientos")
        .itemSqlParameterSourceProvider(e -> new MapSqlParameterSource().addValue("cuenta", e.cuenta()).addValue("anio", e.anio()).addValue("saldo", e.saldo())
            .addValue("dep", e.depositos()).addValue("car", e.cargos()).addValue("int", e.intereses()).addValue("movs", e.movs()))
        .build();
  }

  @Bean
  Step generarEstadosStep(JobRepository repo, PlatformTransactionManager tm, JdbcPagingItemReader<CuentaLegacy> cuentasReader,
                          ItemProcessor<CuentaLegacy, Estado> estadoProcessor, JdbcBatchItemWriter<Estado> estadoWriter) {
    return new StepBuilder("generarEstadosStep", repo).<CuentaLegacy, Estado>chunk(100, tm)
        .reader(cuentasReader).processor(estadoProcessor).writer(estadoWriter)
        .faultTolerant().retry(TransientDataAccessException.class).retryLimit(3).backOffPolicy(espera())
        .taskExecutor(ejecutor()).build();
  }

  @Bean
  Job estadosCuentaAnuales(JobRepository repo, Step generarEstadosStep) {
    return new JobBuilder("estadosCuentaAnuales", repo).start(generarEstadosStep).build();
  }
}
