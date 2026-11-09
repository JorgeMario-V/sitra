#!/bin/sh
# =====================================================================
# SITRA — Prueba de concurrencia sobre la bitácora (riesgo R-03, Sección 6.1)
# Lanza 6 transacciones al mismo tiempo; cada una inserta una fila en
# registro_auditoria y espera 0,5 s antes de confirmar. Luego muestra:
#   1) cuáles inserciones quedaron guardadas (las que faltan terminaron en
#      deadlock, error 1213, y la base de datos las deshizo completas);
#   2) la verificación de la cadena de huellas (todas deben decir OK).
# Uso (Linux/macOS):  sh db/pruebas/concurrencia.sh        (versión Windows: concurrencia.cmd)
# Variable opcional:  MYSQL="mysql -u root"  (cliente y usuario a usar)
# El número de deadlocks cambia en cada ejecución: depende del orden en que
# el motor atiende las sesiones.
# =====================================================================
MYSQL=${MYSQL:-"mysql -u root"}
echo "Prueba de concurrencia SITRA - $(date '+%Y-%m-%d %H:%M:%S')"
$MYSQL -N -e "SELECT CONCAT('Motor: ', VERSION())"
echo "Lanzando 6 transacciones simultaneas..."
for i in 1 2 3 4 5 6; do
  $MYSQL sitra -e "START TRANSACTION; INSERT INTO registro_auditoria (cliente_api_id, numero_cuenta, operacion, resultado, ip_origen, ocurrido_en) VALUES (1, 'AH-1001', 'CONSULTA_SALDO', 'EXITOSA', 'concurrencia-$i', NOW()); DO SLEEP(0.5); COMMIT;" 2>&1 | sed "s/^/  sesion $i: /" &
done
wait
echo
echo "1) Inserciones que quedaron guardadas (de 6 intentos):"
$MYSQL -t sitra -e "SELECT ip_origen AS sesion, id FROM registro_auditoria WHERE ip_origen LIKE 'concurrencia-%' ORDER BY id; SELECT COUNT(*) AS guardadas, 6 - COUNT(*) AS deshechas_por_deadlock FROM registro_auditoria WHERE ip_origen LIKE 'concurrencia-%';"
echo "2) Verificacion de la cadena de huellas (todas deben decir OK):"
$MYSQL -t sitra -e "SELECT r.id, IF(r.hash_anterior = COALESCE(LAG(r.hash_actual) OVER (ORDER BY r.id), REPEAT('0', 64)), 'OK', 'ROTA') AS cadena, IF(r.hash_actual = SHA2(CONCAT_WS('|', r.hash_anterior, r.cliente_api_id, COALESCE(r.transferencia_id, '-'), r.numero_cuenta, r.operacion, r.resultado, COALESCE(r.referencia_externa, '-'), COALESCE(r.monto, '-'), COALESCE(r.motivo, '-'), r.ip_origen, r.ocurrido_en), 256), 'OK', 'ALTERADO') AS huella FROM registro_auditoria r ORDER BY r.id;"
