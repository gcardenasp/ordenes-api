#!/bin/bash
# La imagen ejecuta los .sql de initdb como SYS. Por eso los scripts de database/
# se montan aparte y se ejecutan aqui conectados como el usuario de aplicacion,
# para que todos los objetos queden en su esquema.
set -euo pipefail

export NLS_LANG=AMERICAN_AMERICA.AL32UTF8

sqlplus -s -L /nolog <<SQL
WHENEVER SQLERROR EXIT FAILURE
WHENEVER OSERROR EXIT FAILURE
CONNECT ${APP_USER}/"${APP_USER_PASSWORD}"@//localhost:1521/FREEPDB1

@/opt/ordenes/database/01_tablas.sql
@/opt/ordenes/database/02_datos_catalogo.sql
@/opt/ordenes/database/03_prc_cambio_estado_orden.sql

-- Un error de compilacion PL/SQL no dispara WHENEVER SQLERROR, se valida aparte
DECLARE
    v_errores NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_errores FROM user_errors;
    IF v_errores > 0 THEN
        RAISE_APPLICATION_ERROR(-20000, 'Hay objetos con errores de compilacion, revisar USER_ERRORS');
    END IF;
END;
/
EXIT
SQL
