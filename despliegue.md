# Despliegue en la nube (AWS)

Dos opciones. **Opción A** es la más rápida para la evaluación; **Opción B** es la recomendada para producción.

## Opción A – EC2 + Docker Compose (rápida)
1. **Cuenta y red:** crear VPC por defecto, Security Group con entrada `22` (tu IP) y `8443` (HTTPS público). No exponer 8761/8888/9000 en producción.
2. **Instancia EC2:** Amazon Linux 2023, tipo **t3.large** (2 vCPU / 8 GB) o superior, disco 30 GB, par de llaves `.pem`.
3. **Instalar Docker:**
   ```bash
   ssh -i llave.pem ec2-user@IP_PUBLICA
   sudo dnf install -y docker git && sudo systemctl enable --now docker
   sudo usermod -aG docker ec2-user && newgrp docker
   sudo mkdir -p /usr/local/lib/docker/cli-plugins
   sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
   sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   ```
4. **Obtener código y levantar:**
   ```bash
   git clone https://github.com/Mii-guel/trabajofinal.git && cd TU_REPO
   docker compose up --build -d
   docker compose up -d --no-build --no-recreate --scale cuentas-service=2 --scale pagos-service=2   # escalar
   ```
5. **Probar:** `curl -sk -u web-client:web-secret -d "grant_type=client_credentials&scope=banco.read banco.write canal.web" https://IP_PUBLICA:8443/oauth2/token`
6. **Certificado real (opcional):** dominio en Route 53 + certificado de ACM/Let's Encrypt; reemplazar `api-gateway/src/main/resources/keystore.p12`.

## Opción B – Contenedores gestionados (ECS Fargate) – producción
| Componente local | Servicio AWS |
|---|---|
| Imágenes Docker | **Amazon ECR** (una imagen por módulo) |
| docker-compose | **ECS Fargate** (un *service* por módulo, `desiredCount` > 1 para escalar) |
| PostgreSQL | **Amazon RDS for PostgreSQL** (Multi-AZ; BD `clientes`, `cuentas`, `pagos`, `batch`) |
| Kafka | **Amazon MSK** (Kafka gestionado) |
| API Gateway / HTTPS | **Application Load Balancer** + certificado de **ACM** (HTTPS 443) → servicio `api-gateway` |
| Descubrimiento | Eureka (se mantiene) o **ECS Service Connect / Cloud Map** |
| Secretos (client secrets, passwords BD) | **AWS Secrets Manager / SSM Parameter Store** |
| Logs y métricas | **CloudWatch Logs** (driver `awslogs`) y CloudWatch Alarms |
| Escalado automático | **Application Auto Scaling** (objetivo CPU 60 %) |
| Batch programado | **EventBridge Scheduler** → llama `POST /api/batch/{job}` o tarea ECS |

### Pasos
1. **Imágenes → ECR**
   ```bash
   aws ecr create-repository --repository-name banco/cuentas-service
   aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <CUENTA>.dkr.ecr.us-east-1.amazonaws.com
   docker build --build-arg MODULE=cuentas-service -t <CUENTA>.dkr.ecr.us-east-1.amazonaws.com/banco/cuentas-service:1.0 .
   docker push <CUENTA>.dkr.ecr.us-east-1.amazonaws.com/banco/cuentas-service:1.0     # repetir por cada módulo
   ```
2. **Red:** VPC con 2 subredes privadas (servicios, RDS, MSK) y 2 públicas (ALB). Security Groups: ALB→gateway (8443), servicios entre sí, servicios→RDS (5432), servicios→MSK (9092).
3. **RDS:** crear instancia PostgreSQL 16, crear las 4 bases (`clientes`, `cuentas`, `pagos`, `batch`). Actualizar `spring.datasource.url` en `config-server/.../configs/*.yml` al endpoint de RDS y mover credenciales a Secrets Manager.
4. **MSK:** crear clúster; poner el *bootstrap servers* en `spring.kafka.bootstrap-servers` (`configs/application.yml`).
5. **ECS:** crear clúster; por cada módulo una *task definition* (0.5 vCPU / 1 GB, variable `CONFIG_URL=http://config-server:8888`) y un *service*. Orden de creación: `config-server` → `eureka-server` → `auth-server` → microservicios → BFFs → `api-gateway`.
6. **ALB:** *target group* hacia `api-gateway` (puerto 8443, protocolo HTTPS), listener 443 con certificado ACM; registro DNS en Route 53.
7. **Escalado horizontal:** `aws ecs update-service --cluster banco --service cuentas-service --desired-count 3` o política de Auto Scaling por CPU.
8. **Observabilidad:** logs a CloudWatch; alarmas por 5xx del ALB y por CPU/memoria; endpoint `/actuator/health` como health check.
9. **Validación:** repetir las pruebas de `instrucciones.md` usando la URL del ALB.

### Consideraciones de producción
Reemplazar el certificado autofirmado, mover el almacén de clientes OAuth2 a una base de datos (o Amazon Cognito), usar *authorization_code* para usuarios finales, y cifrar el Config Server (`{cipher}`).
