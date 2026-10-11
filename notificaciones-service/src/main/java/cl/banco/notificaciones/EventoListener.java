package cl.banco.notificaciones;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consumidor Kafka: procesa eventos en tiempo real y simula el envio de notificaciones/alertas. */
@RestController
@RequestMapping("/api/notificaciones")
public class EventoListener {
  private static final Logger log = LoggerFactory.getLogger(EventoListener.class);
  private final ConcurrentLinkedDeque<String> recientes = new ConcurrentLinkedDeque<>();

  @KafkaListener(topics = "transacciones-completadas")
  public void transaccion(String ev) { registrar("NOTIFICACION al cliente", ev); }

  @KafkaListener(topics = "alertas-seguridad")
  public void alerta(String ev) { registrar("ALERTA DE SEGURIDAD al equipo de fraude", ev); }

  private void registrar(String tipo, String ev) {
    log.info(">>> {}: {}", tipo, ev);
    recientes.addFirst(tipo + ": " + ev);
    while (recientes.size() > 50) recientes.removeLast();
  }

  @GetMapping public List<String> recientes() { return new ArrayList<>(recientes); }
}
