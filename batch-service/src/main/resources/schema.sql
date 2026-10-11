CREATE TABLE IF NOT EXISTS cuentas_legacy (numero VARCHAR(20) PRIMARY KEY, cliente VARCHAR(20), tipo VARCHAR(20), saldo NUMERIC(18,2));
CREATE TABLE IF NOT EXISTS reporte_transacciones (id BIGINT PRIMARY KEY, cuenta VARCHAR(20), fecha DATE, tipo VARCHAR(20), monto NUMERIC(18,2), anomalia BOOLEAN, motivo VARCHAR(60));
CREATE INDEX IF NOT EXISTS idx_rt_cuenta ON reporte_transacciones(cuenta);
CREATE TABLE IF NOT EXISTS resumen_diario (fecha DATE, tipo VARCHAR(20), cantidad BIGINT, total NUMERIC(18,2), anomalias BIGINT, PRIMARY KEY (fecha, tipo));
CREATE TABLE IF NOT EXISTS intereses_calculados (cuenta VARCHAR(20), periodo VARCHAR(7), tipo VARCHAR(20), saldo_base NUMERIC(18,2), tasa_anual NUMERIC(6,4), interes NUMERIC(18,2), PRIMARY KEY (cuenta, periodo));
CREATE TABLE IF NOT EXISTS estados_cuenta (cuenta VARCHAR(20), anio INT, saldo_actual NUMERIC(18,2), total_depositos NUMERIC(18,2), total_cargos NUMERIC(18,2), intereses NUMERIC(18,2), movimientos BIGINT, PRIMARY KEY (cuenta, anio));
