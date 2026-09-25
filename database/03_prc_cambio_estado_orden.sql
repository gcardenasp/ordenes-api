CREATE OR REPLACE PROCEDURE prc_cambio_estado_orden (
    p_id_orden        IN orden.id%TYPE,
    p_id_estado_nuevo IN orden.id_estado%TYPE,
    p_usuario         IN orden_historico.usuario%TYPE,
    p_observacion     IN orden_historico.observacion%TYPE DEFAULT NULL,
    p_id_peticion     IN orden_historico.id_peticion%TYPE DEFAULT NULL
) IS
    -- Variables internas
    v_estado_actual   orden.id_estado%TYPE;
    v_transicion      NUMBER;

    -- Excepciones personalizadas
    e_datos_invalidos     EXCEPTION;
    e_orden_no_existe     EXCEPTION;
    e_transicion_invalida EXCEPTION;
    e_orden_bloqueada     EXCEPTION;

    -- Asocia el error de Oracle "recurso ocupado" (espera agotada) a nuestra excepcion
    PRAGMA EXCEPTION_INIT(e_orden_bloqueada, -30006);
BEGIN
    -- 1. Validar datos de entrada
    IF p_id_orden IS NULL OR p_id_estado_nuevo IS NULL OR p_usuario IS NULL THEN
        RAISE e_datos_invalidos;
    END IF;

    -- 2. Leer el estado actual y bloquear la fila (control de concurrencia)
    --    Si otra peticion tiene la orden bloqueada, espera maximo 5 segundos
    BEGIN
        SELECT id_estado
          INTO v_estado_actual
          FROM orden
         WHERE id = p_id_orden
           FOR UPDATE WAIT 5;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE e_orden_no_existe;
    END;

    -- 3. Validar que la transicion este permitida
    SELECT COUNT(*)
      INTO v_transicion
      FROM transicion_estado
     WHERE id_estado_origen  = v_estado_actual
       AND id_estado_destino = p_id_estado_nuevo;

    IF v_transicion = 0 THEN
        RAISE e_transicion_invalida;
    END IF;

    -- 4. Actualizar la orden con sus campos de auditoria
    UPDATE orden
       SET id_estado            = p_id_estado_nuevo,
           usuario_modificacion = p_usuario,
           fecha_modificacion   = SYSTIMESTAMP
     WHERE id = p_id_orden;

    -- 5. Registrar el cambio en el historico
    --    (el id del historico se genera solo, columna IDENTITY)
    INSERT INTO orden_historico (
        id_orden, id_estado_anterior, id_estado_nuevo,
        fecha, usuario, observacion, id_peticion
    ) VALUES (
        p_id_orden, v_estado_actual, p_id_estado_nuevo,
        SYSTIMESTAMP, p_usuario, p_observacion, p_id_peticion
    );

    -- Sin COMMIT: la transaccion la confirma o revierte Spring

EXCEPTION
    -- 6. Traducir cada excepcion a un codigo y mensaje que recibe Java
    WHEN e_datos_invalidos THEN
        RAISE_APPLICATION_ERROR(-20001, 'La orden, el estado nuevo y el usuario son obligatorios');
    WHEN e_orden_no_existe THEN
        RAISE_APPLICATION_ERROR(-20002, 'La orden ' || p_id_orden || ' no existe');
    WHEN e_transicion_invalida THEN
        RAISE_APPLICATION_ERROR(-20003, 'Transicion no permitida del estado '
            || v_estado_actual || ' al estado ' || p_id_estado_nuevo);
    WHEN e_orden_bloqueada THEN
        RAISE_APPLICATION_ERROR(-20004, 'La orden ' || p_id_orden
            || ' esta siendo actualizada por otra solicitud, intente nuevamente');
END prc_cambio_estado_orden;
/
