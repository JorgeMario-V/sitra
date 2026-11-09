@echo off
rem =====================================================================
rem SITRA - Prueba de concurrencia sobre la bitacora (riesgo R-03, Seccion 6.1)
rem Lanza 6 transacciones al mismo tiempo; cada una inserta una fila en
rem registro_auditoria y espera 0,5 s antes de confirmar. Luego muestra:
rem   1) cuales inserciones quedaron guardadas (las que faltan terminaron en
rem      deadlock, error 1213, y la base de datos las deshizo completas);
rem   2) la verificacion de la cadena de huellas (todas deben decir OK).
rem Uso (CMD, desde la carpeta del repositorio):  db\pruebas\concurrencia.cmd
rem Requisito: XAMPP con MySQL encendido y db\instalar_todo.sql ya ejecutado.
rem El numero de deadlocks cambia en cada ejecucion.
rem =====================================================================
setlocal
if not defined MYSQL set "MYSQL=C:\xampp\mysql\bin\mysql.exe"
if not exist "%MYSQL%" (
  echo No se encontro el cliente de MySQL en "%MYSQL%".
  echo Defina la ruta, por ejemplo:  set MYSQL=D:\xampp\mysql\bin\mysql.exe
  exit /b 1
)
echo Prueba de concurrencia SITRA - %DATE% %TIME%
"%MYSQL%" -u root -N -e "SELECT CONCAT('Motor: ', VERSION())"
echo Lanzando 6 transacciones simultaneas...
for /L %%i in (1,1,6) do start "" /b "%MYSQL%" -u root sitra -e "START TRANSACTION; INSERT INTO registro_auditoria (cliente_api_id, numero_cuenta, operacion, resultado, ip_origen, ocurrido_en) VALUES (1, 'AH-1001', 'CONSULTA_SALDO', 'EXITOSA', 'concurrencia-%%i', NOW()); DO SLEEP(0.5); COMMIT;"
rem Espera unos 6 segundos a que terminen las 6 sesiones
ping -n 7 127.0.0.1 > nul
echo.
echo 1) Inserciones que quedaron guardadas (de 6 intentos):
"%MYSQL%" -u root -t sitra -e "SELECT ip_origen AS sesion, id FROM registro_auditoria WHERE ip_origen LIKE 'concurrencia-%%' ORDER BY id; SELECT COUNT(*) AS guardadas, 6 - COUNT(*) AS deshechas_por_deadlock FROM registro_auditoria WHERE ip_origen LIKE 'concurrencia-%%';"
echo 2) Verificacion de la cadena de huellas (todas deben decir OK):
"%MYSQL%" -u root -t sitra -e "SELECT r.id, IF(r.hash_anterior = COALESCE(LAG(r.hash_actual) OVER (ORDER BY r.id), REPEAT('0', 64)), 'OK', 'ROTA') AS cadena, IF(r.hash_actual = SHA2(CONCAT_WS('|', r.hash_anterior, r.cliente_api_id, COALESCE(r.transferencia_id, '-'), r.numero_cuenta, r.operacion, r.resultado, COALESCE(r.referencia_externa, '-'), COALESCE(r.monto, '-'), COALESCE(r.motivo, '-'), r.ip_origen, r.ocurrido_en), 256), 'OK', 'ALTERADO') AS huella FROM registro_auditoria r ORDER BY r.id;"
endlocal
