package cl.banco.pagos;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Productor Kafka: eventos de transacciones completadas y alertas de seguridad. */
@Component
public class EventosPublisher {
  public static final String TX = "transacciones-completadas", ALERTAS = "alertas-seguridad";
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper mapper;
  public EventosPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) { this.kafka = kafka; this.mapper = mapper; }

  @Bean NewTopic topicTx() { return new NewTopic(TX, 3, (short) 1); }
  @Bean NewTopic topicAlertas() { return new NewTopic(ALERTAS, 1, (short) 1); }

  public void completada(Pago p) { enviar(TX, p, null); }
  public void alerta(Pago p, String motivo) { enviar(ALERTAS, p, motivo); }

  private void enviar(String topic, Pago p, String motivo) {
    try {
      Map<String, Object> ev = new LinkedHashMap<>();
      ev.put("pagoId", p.id); ev.put("tipo", p.tipo); ev.put("origen", p.origen); ev.put("destino", p.destino);
      ev.put("monto", p.monto); ev.put("canal", p.canal); ev.put("estado", p.estado); ev.put("fecha", p.fecha.toString());
      if (motivo != null) ev.put("motivo", motivo);
      kafka.send(topic, String.valueOf(p.id), mapper.writeValueAsString(ev)); // asincrono: no bloquea la transaccion
    } catch (Exception e) { throw new IllegalStateException("No se pudo publicar evento", e); }
  }
}
