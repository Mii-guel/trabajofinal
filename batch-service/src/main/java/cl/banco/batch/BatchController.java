package cl.banco.batch;

import java.util.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Disparo manual de jobs y consulta de ejecuciones (requiere scope banco.admin). */
@RestController
@RequestMapping("/api/batch")
public class BatchController {
  private final Map<String, Job> jobs;
  private final JobLauncher launcher;
  private final JobExplorer explorer;
  public BatchController(Map<String, Job> jobs, JobLauncher launcher, JobExplorer explorer) { this.jobs = jobs; this.launcher = launcher; this.explorer = explorer; }

  @GetMapping("/jobs") public Set<String> jobs() { return new TreeSet<>(jobs.keySet()); }

  /** valor: reporteTransaccionesDiarias = fecha (yyyy-MM-dd) o "all"; intereses = periodo yyyy-MM; estados = anio. */
  @PostMapping("/{job}")
  public Map<String, Object> ejecutar(@PathVariable String job, @RequestParam(defaultValue = "all") String valor) throws Exception {
    Job j = jobs.get(job);
    if (j == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job no existe: " + job);
    JobParameters p = new JobParametersBuilder().addString("valor", valor).addLong("ts", System.currentTimeMillis()).toJobParameters();
    return resumen(launcher.run(j, p));
  }

  @GetMapping("/ejecuciones/{job}")
  public List<Map<String, Object>> ejecuciones(@PathVariable String job) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (JobInstance i : explorer.getJobInstances(job, 0, 10)) for (JobExecution e : explorer.getJobExecutions(i)) r.add(resumen(e));
    return r;
  }

  static Map<String, Object> resumen(JobExecution e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("executionId", e.getId()); m.put("job", e.getJobInstance().getJobName());
    m.put("estado", e.getStatus().toString()); m.put("exit", e.getExitStatus().getExitCode());
    if (e.getStartTime() != null && e.getEndTime() != null) m.put("duracionMs", java.time.Duration.between(e.getStartTime(), e.getEndTime()).toMillis());
    List<Map<String, Object>> steps = new ArrayList<>();
    for (StepExecution s : e.getStepExecutions()) {
      Map<String, Object> x = new LinkedHashMap<>();
      x.put("step", s.getStepName()); x.put("estado", s.getStatus().toString()); x.put("leidos", s.getReadCount());
      x.put("escritos", s.getWriteCount()); x.put("filtrados", s.getFilterCount()); x.put("omitidos", s.getSkipCount());
      steps.add(x);
    }
    m.put("steps", steps);
    return m;
  }
}
