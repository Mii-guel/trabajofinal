#!/bin/bash
# Ejecuta TODAS las pruebas y guarda la salida REAL en evidencias/evidencia_ejecucion.txt
# Requisitos: sistema levantado (docker compose up --build -d) y bash (Git Bash / WSL / Linux / Mac)
cd "$(dirname "$0")/.." || exit 1
OUT=evidencias/evidencia_ejecucion.txt; mkdir -p evidencias
GW=https://localhost:8443; J='Content-Type: application/json'
tok(){ curl -sk -u "$1" -d "grant_type=client_credentials&scope=$2" $GW/oauth2/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/'; }
sec(){ echo; echo "=================== $1 ==================="; }
call(){ echo "\$ $*"; "$@"; echo; }
{
echo "EVIDENCIA DE EJECUCION - Banco XYZ - $(date)"
sec "1. Contenedores"; docker compose ps --format 'table {{.Name}}\t{{.Service}}\t{{.Status}}'
sec "2. Servicios registrados en Eureka"
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps | grep -o '"app":"[A-Z-]*"' | sort | uniq -c
WEB=$(tok web-client:web-secret "banco.read banco.write canal.web")
MOV=$(tok movil-client:movil-secret "banco.read banco.write canal.movil")
ATM=$(tok atm-client:atm-secret "banco.read banco.write canal.atm")
ADM=$(tok batch-client:batch-secret "banco.admin")
sec "3. OAuth2 por HTTPS: tokens emitidos por canal (primeros 40 caracteres)"
for t in WEB MOV ATM ADM; do echo "$t: ${!t:0:40}..."; done
sec "4. Sin token -> 401"; curl -sk -i $GW/web/dashboard/1 | head -3
sec "5. BFF WEB (respuesta completa)"; curl -sk -H "Authorization: Bearer $WEB" $GW/web/dashboard/1; echo
sec "6. BFF MOVIL (respuesta liviana)"; curl -sk -H "Authorization: Bearer $MOV" $GW/movil/resumen/1; echo
sec "7. BFF ATM (saldo)"; curl -sk -H "Authorization: Bearer $ATM" $GW/atm/saldo/1001; echo
sec "8. Autorizacion por canal: token MOVIL en BFF WEB -> 403"; curl -sk -i -H "Authorization: Bearer $MOV" $GW/web/dashboard/1 | head -1
sec "9. Transferencia por WEB (50000 de 1001 a 1002) -> 201 COMPLETADA"
curl -sk -X POST -H "Authorization: Bearer $WEB" -H "$J" -d '{"origen":"1001","destino":"1002","monto":50000}' $GW/web/transferencias; echo
sec "10. ATM: retiro 100000 -> 201 | retiro 15000 (no multiplo) -> 400 | retiro 999999 (sobre limite) -> 400"
for m in 100000 15000 999999; do curl -sk -w " [HTTP %{http_code}]\n" -X POST -H "Authorization: Bearer $ATM" -H "$J" -d "{\"cuenta\":\"1001\",\"monto\":$m}" $GW/atm/retiro; done
sec "11. Saldo insuficiente (cuenta 1002 -> 99999999) -> 422"
curl -sk -w " [HTTP %{http_code}]\n" -X POST -H "Authorization: Bearer $WEB" -H "$J" -d '{"origen":"1002","destino":"1001","monto":99999999}' $GW/web/transferencias
sec "12. Monto alto (1.500.000 de 1003 a 1001) -> genera ALERTA DE SEGURIDAD en Kafka"
curl -sk -X POST -H "Authorization: Bearer $WEB" -H "$J" -d '{"origen":"1003","destino":"1001","monto":1500000}' $GW/web/transferencias; echo; sleep 4
sec "13. Kafka: eventos consumidos por notificaciones-service"
docker compose logs notificaciones-service 2>&1 | grep -E "NOTIFICACION|ALERTA" | tail -8
sec "14. Spring Batch: ejecucion de los 3 jobs (esperado: 20000 escritos, 3 omitidos)"
for j in reporteTransaccionesDiarias:all calculoInteresesMensuales:2026-10 estadosCuentaAnuales:2026; do
  echo "--- ${j%%:*}"; curl -sk -X POST -H "Authorization: Bearer $ADM" "$GW/api/batch/${j%%:*}?valor=${j##*:}"; echo; done
sec "15. Resultados en base de datos (batch)"
docker compose exec -T postgres psql -U banco -d batch -c "SELECT COUNT(*) AS transacciones, SUM(CASE WHEN anomalia THEN 1 ELSE 0 END) AS anomalias FROM reporte_transacciones;" \
  -c "SELECT motivo, COUNT(*) FROM reporte_transacciones WHERE anomalia GROUP BY motivo;" \
  -c "SELECT * FROM resumen_diario ORDER BY fecha DESC, tipo LIMIT 5;" \
  -c "SELECT * FROM intereses_calculados WHERE periodo='2026-10' ORDER BY interes DESC LIMIT 5;" \
  -c "SELECT * FROM estados_cuenta WHERE anio=2026 ORDER BY movimientos DESC LIMIT 5;"
sec "16. Escalabilidad horizontal: 3 instancias de cuentas-service y 2 de pagos-service"
docker compose up -d --no-build --no-recreate --scale cuentas-service=3 --scale pagos-service=2 2>&1 | tail -3
echo "Esperando registro en Eureka..."; sleep 60
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps | grep -o '"app":"[A-Z-]*"' | sort | uniq -c
for i in 1 2 3 4 5 6 7 8; do curl -sk -o /dev/null -H "Authorization: Bearer $WEB" $GW/web/dashboard/1; done
echo "Peticiones atendidas por distintas instancias (el nombre entre [] es el contenedor):"
docker compose logs cuentas-service 2>&1 | grep "GET /api/cuentas" | tail -8
sec "17. Resiliencia: se detiene cuentas-service (todas las instancias)"
docker compose stop cuentas-service >/dev/null 2>&1; sleep 3
echo "WEB (degradacion: responde con advertencias):"; curl -sk -H "Authorization: Bearer $WEB" $GW/web/dashboard/1; echo
echo "MOVIL (degradacion: aviso):"; curl -sk -H "Authorization: Bearer $MOV" $GW/movil/resumen/1; echo
echo "ATM (operacion critica: 503 controlado, circuito se abre tras fallos repetidos):"
for i in 1 2 3 4 5; do curl -sk -w " [HTTP %{http_code}]\n" -H "Authorization: Bearer $ATM" $GW/atm/saldo/1001; done
echo "Logs de fallback / circuito:"; docker compose logs bff-atm 2>&1 | grep -E "Fallback|CallNotPermitted" | tail -4
sec "18. Recuperacion"; docker compose start cuentas-service >/dev/null 2>&1; echo "cuentas-service reiniciado"
echo; echo "FIN"
} 2>&1 | tee "$OUT"
echo; echo "Evidencia guardada en $OUT"
