@echo off
rem =====================================================================
rem SITRA - Genera las evidencias de ejecucion que cita el DAS v1.1
rem (hallazgos H-04, H-05 y H-07 de la revision de trazabilidad).
rem
rem Uso: desde la carpeta del repositorio, en la terminal de VS Code
rem      (perfil JavaSE-21) o en CMD:      scripts\generar_evidencias.cmd
rem Requisitos: XAMPP con MySQL encendido, JDK 21 e Internet la primera vez
rem (el Maven Wrapper descarga Maven). La API NO necesita estar corriendo.
rem Si XAMPP no esta en C:\xampp:  set MYSQL=D:\xampp\mysql\bin\mysql.exe
rem
rem Salidas en evidencias\entrega-07-pruebas-y-tradeoffs\ :
rem   resultado_verificacion_mariadb.txt   demo_sustentacion.sql en MariaDB
rem   resultado_minimo_privilegio.txt      usuario sin permisos de borrar ni de tocar triggers
rem   resultado_concurrencia.txt           6 transacciones simultaneas sobre la bitacora
rem   mvn-test.txt                         17 pruebas unitarias
rem   mvn-test-integracion.txt             21 pruebas: 17 unitarias + 4 de integracion
rem =====================================================================
setlocal
cd /d "%~dp0.."
if not defined MYSQL set "MYSQL=C:\xampp\mysql\bin\mysql.exe"
if not exist "%MYSQL%" (
  echo No se encontro el cliente de MySQL en "%MYSQL%".
  echo Defina la ruta, por ejemplo:  set MYSQL=D:\xampp\mysql\bin\mysql.exe
  exit /b 1
)
set "EV=evidencias\entrega-07-pruebas-y-tradeoffs"
set "CS=--default-character-set=utf8mb4"

echo [1/6] Creando la base de datos desde cero con db\instalar_todo.sql...
"%MYSQL%" -u root %CS% < db\instalar_todo.sql
if errorlevel 1 goto error_bd

echo [2/6] Verificacion del esquema en MariaDB con db\pruebas\demo_sustentacion.sql...
(
  echo Verificacion de SITRA en MariaDB - %DATE% %TIME%
  "%MYSQL%" -u root -N -e "SELECT CONCAT('Motor: ', VERSION())"
  echo Comando: mysql -u root -t -v --force sitra ^< db\pruebas\demo_sustentacion.sql
  echo Los errores 1062, 4025 y 1644 son ESPERADOS: prueban que la base rechaza lo invalido.
  echo.
  "%MYSQL%" -u root %CS% -t -v --force sitra < db\pruebas\demo_sustentacion.sql 2>&1
) > "%EV%\resultado_verificacion_mariadb.txt"

echo [3/6] Prueba del usuario de minimo privilegio...
set "PW=Sitra%RANDOM%%RANDOM%x"
set "U1='sitra_prueba'@'localhost'"
set "U2='sitra_prueba'@'127.0.0.1'"
set "U3='sitra_prueba'@'::1'"
(
  echo Prueba del usuario de minimo privilegio - %DATE% %TIME%
  echo Se crea un usuario temporal con los mismos permisos de db\produccion\usuario_minimo_privilegio.sql:
  echo SELECT, INSERT, UPDATE y EXECUTE sobre la base sitra. Su clave es aleatoria y no se guarda.
  echo Se intenta desactivar un trigger de la bitacora y borrar una transferencia.
  echo Los dos intentos deben fallar con ERROR 1142. Al final el usuario temporal se elimina.
  echo.
  "%MYSQL%" -u root -e "DROP USER IF EXISTS %U1%, %U2%, %U3%; CREATE USER %U1% IDENTIFIED BY '%PW%'; CREATE USER %U2% IDENTIFIED BY '%PW%'; CREATE USER %U3% IDENTIFIED BY '%PW%'; GRANT SELECT, INSERT, UPDATE, EXECUTE ON sitra.* TO %U1%; GRANT SELECT, INSERT, UPDATE, EXECUTE ON sitra.* TO %U2%; GRANT SELECT, INSERT, UPDATE, EXECUTE ON sitra.* TO %U3%;" 2>&1
  echo Intento 1: DROP TRIGGER trg_bitacora_no_update
  "%MYSQL%" -u sitra_prueba -p%PW% sitra -e "DROP TRIGGER trg_bitacora_no_update;" 2>&1
  echo Intento 2: DELETE FROM transferencia WHERE id = 1
  "%MYSQL%" -u sitra_prueba -p%PW% sitra -e "DELETE FROM transferencia WHERE id = 1;" 2>&1
  echo Usuario temporal eliminado.
  "%MYSQL%" -u root -e "DROP USER IF EXISTS %U1%, %U2%, %U3%;" 2>&1
) > "%EV%\resultado_minimo_privilegio.txt"

echo [4/6] Prueba de concurrencia con db\pruebas\concurrencia.cmd...
call db\pruebas\concurrencia.cmd > "%EV%\resultado_concurrencia.txt" 2>&1

echo [5/6] Base limpia y pruebas unitarias con el Maven Wrapper (la primera vez descarga Maven)...
"%MYSQL%" -u root %CS% < db\instalar_todo.sql
call mvnw.cmd -B test > "%EV%\mvn-test.txt" 2>&1

echo [6/6] Pruebas unitarias + de integracion con MariaDB...
call mvnw.cmd -B test -Pintegracion > "%EV%\mvn-test-integracion.txt" 2>&1

echo.
echo ===== Resumen =====
findstr /C:"Tests run:" /C:"BUILD" "%EV%\mvn-test.txt"
findstr /C:"Tests run:" /C:"BUILD" "%EV%\mvn-test-integracion.txt"
findstr /C:"ERROR 1142" "%EV%\resultado_minimo_privilegio.txt"
findstr /C:"deshechas_por_deadlock" /C:"ROTA" /C:"ALTERADO" "%EV%\resultado_concurrencia.txt"
echo.
echo Revise los archivos en %EV% y luego haga el commit de las evidencias.
echo Si mvn-test.txt menciona JAVA_HOME, ejecute este script desde la terminal de VS Code (perfil JavaSE-21).
endlocal
exit /b 0

:error_bd
echo No se pudo crear la base. Revise que MySQL este encendido en el XAMPP Control Panel.
endlocal
exit /b 1
