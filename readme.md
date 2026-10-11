# Banco XYZ – Modernización a Microservicios (PBY2203 – Evaluación Final Transversal)

**Autor:** Miguel Castillo · **Asignatura:** Desarrollo Backend III (PBY2203)
**Repositorio GitHub:** _https://github.com/TU_USUARIO/TU_REPO_

Migración del sistema legacy (COBOL + Shell en mainframe) del Banco XYZ a una arquitectura de microservicios cloud-ready con
**Spring Batch**, **Backend for Frontend (BFF)**, **Spring Cloud** (Config, Eureka, Gateway), **OAuth2**, **Resilience4j**, **Kafka**, **Docker** y despliegue en **AWS**.

## Arquitectura
```
 Web / Móvil / ATM ──HTTPS──> API Gateway (8443) ──lb://──> BFF Web | BFF Móvil | BFF ATM
                                                                   │ (JWT propagado, Retry + CircuitBreaker)
                                          ┌────────────────────────┼───────────────────────┐
                                    clientes-service        cuentas-service         pagos-service ──Kafka──> notificaciones-service
                                          └────────── PostgreSQL ──┴───────────────────────┘     (transacciones-completadas, alertas-seguridad)
 Spring Cloud Config (8888) · Eureka (8761) · Auth Server OAuth2 (9000) · batch-service (Spring Batch) · Kafka · PostgreSQL
```

| Módulo | Rol |
|---|---|
| `common` | Librería compartida: seguridad OAuth2 (resource server), clientes HTTP con Resilience4j, manejo de errores, logging de requests |
| `config-server` | Spring Cloud Config: configuración centralizada (`src/main/resources/configs`) |
| `eureka-server` | Descubrimiento de servicios |
| `api-gateway` | Punto de entrada HTTPS, enrutamiento y balanceo (`lb://`) |
| `auth-server` | OAuth2 Authorization Server (un cliente/scope/TTL por canal) |
| `clientes-service` · `cuentas-service` · `pagos-service` | Microservicios de negocio (PostgreSQL) |
| `notificaciones-service` | Consumidor Kafka (notificaciones y alertas de seguridad) |
| `bff-web` · `bff-movil` · `bff-atm` | Backends for Frontend por canal |
| `batch-service` | 3 jobs Spring Batch (reporte de transacciones, intereses mensuales, estados de cuenta anuales) |

## Documentación (misma carpeta)
- [`instrucciones.md`](instrucciones.md) – cómo ejecutar y probar cada componente
- [`despliegue.md`](despliegue.md) – despliegue en AWS
- `Informe_Tecnico.pdf` – informe técnico con diagramas
- Video de presentación (MP4)

## Inicio rápido
```bash
docker compose up --build -d      # primera vez: varios minutos
./scripts/generar_evidencia.sh    # ejecuta todas las pruebas y guarda evidencia en /evidencias
```
Requiere Docker con al menos **6 GB de RAM** asignados.
