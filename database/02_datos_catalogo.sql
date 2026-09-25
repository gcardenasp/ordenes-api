-- Estados

INSERT INTO estado_orden (codigo, nombre) VALUES ('CREADA',     'Creada');
INSERT INTO estado_orden (codigo, nombre) VALUES ('ASIGNADA',   'Asignada');
INSERT INTO estado_orden (codigo, nombre) VALUES ('EN_PROCESO', 'En proceso');
INSERT INTO estado_orden (codigo, nombre) VALUES ('SUSPENDIDA', 'Suspendida');
INSERT INTO estado_orden (codigo, nombre) VALUES ('FINALIZADA', 'Finalizada');
INSERT INTO estado_orden (codigo, nombre) VALUES ('CANCELADA',  'Cancelada');

-- Transiciones permitidas, resueltas por codigo para no depender de ids fijos.
-- FINALIZADA y CANCELADA son estados finales: no tienen transiciones de salida.

INSERT INTO transicion_estado (id_estado_origen, id_estado_destino)
SELECT origen.id, destino.id
  FROM estado_orden origen
 CROSS JOIN estado_orden destino
 WHERE (origen.codigo, destino.codigo) IN (
        ('CREADA',     'ASIGNADA'),
        ('CREADA',     'CANCELADA'),
        ('ASIGNADA',   'EN_PROCESO'),
        ('ASIGNADA',   'CANCELADA'),
        ('EN_PROCESO', 'SUSPENDIDA'),
        ('EN_PROCESO', 'FINALIZADA'),
        ('SUSPENDIDA', 'EN_PROCESO'),
        ('SUSPENDIDA', 'CANCELADA')
       );

-- Tipos de orden

INSERT INTO tipo_orden (codigo, nombre) VALUES ('INSTALACION', 'Instalación de servicio');
INSERT INTO tipo_orden (codigo, nombre) VALUES ('REPARACION',  'Reparación de daño');
INSERT INTO tipo_orden (codigo, nombre) VALUES ('SUSPENSION',  'Suspensión de servicio');
INSERT INTO tipo_orden (codigo, nombre) VALUES ('RECONEXION',  'Reconexión de servicio');
INSERT INTO tipo_orden (codigo, nombre) VALUES ('INSPECCION',  'Inspección técnica');

-- Clientes de prueba (datos ficticios)

INSERT INTO cliente (tipo_identificacion, numero_identificacion, nombre, apellido, usuario_creacion)
VALUES ('CC', '1000000001', 'Laura', 'Gómez', 'SISTEMA');

INSERT INTO cliente (tipo_identificacion, numero_identificacion, nombre, apellido, usuario_creacion)
VALUES ('CC', '1000000002', 'Andrés', 'Rodríguez', 'SISTEMA');

INSERT INTO cliente (tipo_identificacion, numero_identificacion, razon_social, usuario_creacion)
VALUES ('NIT', '900000001-1', 'Servicios de Prueba S.A.S.', 'SISTEMA');

COMMIT;
