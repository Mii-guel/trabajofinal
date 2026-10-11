# Guion del video (5–7 min) – grabar con webcam visible + pantalla
| Tiempo | Qué decir / mostrar |
|---|---|
| 0:00–1:00 | **Resumen ejecutivo.** Banco XYZ, sistema legacy COBOL/Shell; objetivo: migrar a microservicios cloud. Mostrar diagrama de arquitectura del informe. Alcance: 3 partes (Batch, BFF, Microservicios). |
| 1:00–3:30 | **Resultados.** Mostrar `docker compose ps` y Eureka; token por canal; 3 BFFs (respuesta web vs móvil vs ATM); transferencia + evento Kafka/alerta; ejecutar los 3 jobs batch (20.000 registros, 3 omitidos, 39 anomalías); escalar a 3 instancias; apagar `cuentas-service` y mostrar degradación + circuito abierto. |
| 3:30–4:30 | **Comparación con legacy.** Mainframe monolítico → servicios independientes; batch secuencial → paralelo con retry/skip/reejecución; un backend para todos → BFF por canal; seguridad centralizada → OAuth2 por canal; sin tolerancia a fallos → Resilience4j; escalado vertical → horizontal. |
| 4:30–5:45 | **Desafíos y soluciones.** Consistencia distribuida (Saga/compensación + bloqueo pesimista), fallos en cascada (Retry+CB+fallback), arranque ordenado (healthchecks), HTTPS, escalado sin conflictos de puertos (Eureka + Gateway). |
| 5:45–7:00 | **Mejoras y próximos pasos.** Idempotency-Key en pagos, Outbox pattern, tracing distribuido (OpenTelemetry), Authorization Code + usuarios reales, Kubernetes/ECS autoscaling, MSK/RDS gestionados, particionado de batch con Spring Batch remoto. |
