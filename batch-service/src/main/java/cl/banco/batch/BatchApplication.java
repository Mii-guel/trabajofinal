package cl.banco.batch;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "cl.banco")
@EnableScheduling
public class BatchApplication { public static void main(String[] a) { SpringApplication.run(BatchApplication.class, a); } }
