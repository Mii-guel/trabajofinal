# Instrucciones para ejecutar y probar cada componente

Requisitos: **Docker Desktop** (≥ 6 GB RAM), **Git Bash/WSL/Linux/Mac** para los comandos `curl`. No se requiere Java ni Maven (se compila dentro de Docker).

## 1. Levantar el sistema
```bash
docker compose up --build -d
docker compose ps          # esperar ~2-3 min hasta que todos estén "running"
```
Orden automático: PostgreSQL y Kafka → Config Server → Eureka → Auth Server → microservicios → BFFs → Gateway.
Verificar registro: http://localhost:8761 (deben aparecer CLIENTES-SERVICE, CUENTAS-SERVICE, PAGOS-SERVICE, NOTIFICACIONES-SERVICE, BFF-WEB, BFF-MOVIL, BFF-ATM, BATCH-SERVICE, API-GATEWAY, AUTH-SERVER).

> **Prueba automática de todo:** `./scripts/generar_evidencia.sh` ejecuta todas las secciones siguientes y guarda la salida en `evidencias/evidencia_ejecucion.txt`.

## 2. Autenticación OAuth2 (por canal)
| Cliente | Secret | Scopes | Vigencia token |
|---|---|---|---|
| `web-client` | `web-secret` | banco.read banco.write canal.web | 30 min |
| `movil-client` | `movil-secret` | banco.read banco.write canal.movil | 15 min |
| `atm-client` | `atm-secret` | banco.read banco.write canal.atm | 5 min |
| `batch-client` | `batch-secret` | banco.admin | 30 min |
```bash
GW=https://localhost:8443      # HTTPS (certificado autofirmado: usar -k)
tok(){ curl -sk -u "$1" -d "grant_type=client_credentials&scope=$2" $GW/oauth2/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/'; }
WEB=$(tok web-client:web-secret "banco.read banco.write canal.web")
MOV=$(tok movil-client:movil-secret "banco.read banco.write canal.movil")
ATM=$(tok atm-client:atm-secret "banco.read banco.write canal.atm")
ADM=$(tok batch-client:batch-secret "banco.admin")
```
Esperado: sin token → **401**; token de otro canal → **403**.
```bash
curl -ski $GW/web/dashboard/1 | head -1                         # 401
curl -ski -H "Authorization: Bearer $MOV" $GW/web/dashboard/1 | head -1   # 403
```

## 3. BFFs (Parte 2)
```bash
curl -sk -H "Authorization: Bearer $WEB" $GW/web/dashboard/1        # completo: cliente + cuentas + movimientos
curl -sk -H "Authorization: Bearer $MOV" $GW/movil/resumen/1        # liviano: nombre + (numero, saldo)
curl -sk -H "Authorization: Bearer $ATM" $GW/atm/saldo/1001         # solo saldo
curl -sk -X POST -H "Authorization: Bearer $ATM" -H "Content-Type: application/json" -d '{"cuenta":"1001","monto":100000}' $GW/atm/retiro   # 201
curl -sk -X POST -H "Authorization: Bearer $ATM" -H "Content-Type: application/json" -d '{"cuenta":"1001","monto":15000}'  $GW/atm/retiro   # 400 (no múltiplo de 10000)
```

## 4. Microservicios, pagos y Kafka (Parte 3)
```bash
# Transferencia (WEB)
curl -sk -X POST -H "Authorization: Bearer $WEB" -H "Content-Type: application/json" -d '{"origen":"1001","destino":"1002","monto":50000}' $GW/web/transferencias
# Monto alto (>= 1.000.000) genera además una ALERTA DE SEGURIDAD
curl -sk -X POST -H "Authorization: Bearer $WEB" -H "Content-Type: application/json" -d '{"origen":"1003","destino":"1001","monto":1500000}' $GW/web/transferencias
# Ver eventos consumidos
docker compose logs notificaciones-service | grep -E "NOTIFICACION|ALERTA"
```
Los microservicios (`clientes`, `cuentas`, `pagos`) no publican puertos al host: se acceden vía Gateway/BFF (permite escalar sin conflictos de puertos).

## 5. Spring Batch (Parte 1)
Datos de entrada en `batch-data/` (`cuentas.csv`, `transacciones.csv`; 20.000 transacciones, 3 filas corruptas y 39 anomalías intencionales).
```bash
curl -sk -X POST -H "Authorization: Bearer $ADM" "$GW/api/batch/reporteTransaccionesDiarias?valor=all"   # fecha yyyy-MM-dd o all
curl -sk -X POST -H "Authorization: Bearer $ADM" "$GW/api/batch/calculoInteresesMensuales?valor=2026-10"  # periodo yyyy-MM
curl -sk -X POST -H "Authorization: Bearer $ADM" "$GW/api/batch/estadosCuentaAnuales?valor=2026"         # año (ejecutar después del primero)
curl -sk -H "Authorization: Bearer $ADM" $GW/api/batch/ejecuciones/reporteTransaccionesDiarias
```
**Resultado esperado del job 1:** 20.000 leídos/escritos, 3 omitidos (filas corruptas), 39 anomalías. Verificar en BD:
```bash
docker compose exec postgres psql -U banco -d batch -c "SELECT COUNT(*), SUM(CASE WHEN anomalia THEN 1 ELSE 0 END) FROM reporte_transacciones;"
docker compose exec postgres psql -U banco -d batch -c "SELECT * FROM resumen_diario LIMIT 5;"
docker compose exec postgres psql -U banco -d batch -c "SELECT * FROM intereses_calculados LIMIT 5;"
docker compose exec postgres psql -U banco -d batch -c "SELECT * FROM estados_cuenta LIMIT 5;"
```
Reintentos/reejecución: fallos transitorios de BD → *retry* (3 intentos, 1 s); registros corruptos → *skip* (máx. 50); jobs `FAILED` se reintentan automáticamente cada 60 s (máx. 3). Ejecutarlo dos veces no duplica datos (UPSERT).
> Para usar los archivos reales de `fin_legacy_data`, reemplace los CSV de `batch-data/` y, si las columnas difieren, ajuste `names(...)` y los registros en `batch-service/.../BatchJobsConfig.java`.

## 6. Escalabilidad horizontal y balanceo
```bash
docker compose up -d --no-build --no-recreate --scale cuentas-service=3 --scale pagos-service=2
# Eureka mostrará 3 y 2 instancias; las peticiones se reparten (round-robin):
docker compose logs cuentas-service | grep "GET /api/cuentas"      # el [nombre] cambia entre contenedores
```

## 7. Resiliencia
```bash
docker compose stop cuentas-service
curl -sk -H "Authorization: Bearer $WEB" $GW/web/dashboard/1       # 200 degradado: "advertencias"
curl -sk -H "Authorization: Bearer $MOV" $GW/movil/resumen/1       # 200 con "aviso"
curl -sk -i -H "Authorization: Bearer $ATM" $GW/atm/saldo/1001     # 503 controlado (repetir 4-5 veces: circuito OPEN, respuesta inmediata)
docker compose start cuentas-service                               # el circuito se recupera (HALF_OPEN -> CLOSED) tras 15 s
```

## 8. Detener
```bash
docker compose down        # agregar -v para borrar los datos de PostgreSQL
```
## 9. Solución de problemas
`docker compose logs <servicio>` · si un servicio no arrancó: `docker compose up -d` (se reintenta solo, `restart: on-failure`) · memoria insuficiente: aumentar RAM de Docker.
