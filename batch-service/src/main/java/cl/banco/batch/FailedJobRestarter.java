package cl.banco.batch;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Politica de reejecucion automatica: periodicamente busca ejecuciones FAILED y las reintenta
 * (misma instancia de job / mismos parametros) hasta un maximo de reintentos.
 */
@Component
public class FailedJobRestarter {
  private static final Logger log = LoggerFactory.getLogger(FailedJobRestarter.class);
  private final JobExplorer explorer; private final JobLauncher launcher; private final Map<String, Job> jobs;
  private final int maxRestarts;
  public FailedJobRestarter(JobExplorer explorer, JobLauncher launcher, Map<String, Job> jobs, @Value("${batch.restart.max-restarts:3}") int maxRestarts) {
    this.explorer = explorer; this.launcher = launcher; this.jobs = jobs; this.maxRestarts = maxRestarts;
  }

  @Scheduled(initialDelayString = "${batch.restart.delay-ms:60000}", fixedDelayString = "${batch.restart.delay-ms:60000}")
  public void reintentar() {
    for (String name : explorer.getJobNames()) {
      Job job = jobs.get(name);
      if (job == null) continue;
      for (JobInstance inst : explorer.getJobInstances(name, 0, 10)) {
        JobExecution last = explorer.getLastJobExecution(inst);
        if (last == null || last.getStatus() != BatchStatus.FAILED) continue;
        if (explorer.getJobExecutions(inst).size() > maxRestarts) { log.error("Job {} instancia {} supero {} reintentos: requiere intervencion", name, inst.getInstanceId(), maxRestarts); continue; }
        try { log.warn("Reejecucion automatica de {} (instancia {})", name, inst.getInstanceId()); launcher.run(job, last.getJobParameters()); }
        catch (Exception e) { log.error("Fallo la reejecucion de {}: {}", name, e.getMessage()); }
      }
    }
  }
}
