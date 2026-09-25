# Especificación funcional: Gestión de órdenes operativas

## Contexto

Una empresa de servicios públicos requiere un sistema para la gestión de órdenes operativas. Cada orden tiene cliente, tipo, estado, trazabilidad e historial de cambios. Volumen esperado: 1 millón de órdenes al mes.

## Actores

- **Consumidor API**: cualquier sistema o aplicación que consume la API (web, móvil, procesos programados o consumidores de colas). Llega a través de un API Gateway corporativo (supuesto: autentica, autoriza y aplica rate limiting).

## Historias de usuario y criterios de aceptación

### HU-01 Crear orden (`POST /api/v1/orden`)

Como consumidor API quiero crear una orden para un cliente con un tipo de orden.

- CA-01.1 La orden se crea en el estado inicial `CREADA`, sin que el consumidor envíe estado.
- CA-01.2 En la misma transacción se inserta el primer registro en `ORDEN_HISTORICO` con estado anterior nulo y estado nuevo `CREADA`.
- CA-01.3 Campos obligatorios: `idCliente`, `idTipo`, `canal`, `llaveIdempotencia`. Si falta alguno o no cumple formato: 400.
- CA-01.4 Si el cliente o el tipo no existen: 400 con mensaje claro.
- CA-01.5 Idempotencia: si llega una `llaveIdempotencia` ya registrada, NO se crea otra orden; se responde 200 con la orden existente. Si dos peticiones con la misma llave llegan al mismo tiempo, solo una crea la orden y la otra recibe la existente.
- CA-01.6 Respuesta exitosa: 201 con la orden creada.
- CA-01.7 `usuarioCreacion` sale del token, nunca del cuerpo de la petición.

### HU-02 Consultar orden (`GET /api/v1/orden/{id}`)

- CA-02.1 Si existe: 200 con la orden.
- CA-02.2 Si no existe: 404.

### HU-03 Actualizar estado (`PUT /api/v1/orden/{id}/estado`)

Como consumidor API quiero cambiar el estado de una orden respetando las transiciones permitidas.

- CA-03.1 El cambio se ejecuta llamando al procedimiento `prc_cambio_estado_orden`.
- CA-03.2 Transición permitida: 200 con la orden actualizada, y queda un registro en `ORDEN_HISTORICO` con estado anterior, estado nuevo, usuario, observación e id de petición.
- CA-03.3 Mapeo de errores del procedimiento:
  - -20001 datos inválidos: 400
  - -20002 la orden no existe: 404
  - -20003 transición no permitida: 422
  - -20004 la orden está bloqueada por otra solicitud (espera agotada): 409
- CA-03.4 Concurrencia: si dos solicitudes cambian la misma orden al mismo tiempo, la segunda espera (máximo 5 s), luego valida contra el estado ya actualizado. Nunca quedan dos registros del histórico que salgan del mismo estado anterior.
- CA-03.5 Ante cualquier error, no queda ningún cambio parcial (ni en `ORDEN` ni en `ORDEN_HISTORICO`).

### HU-04 Listar órdenes (`GET /api/v1/orden?estado=&fechaInicio=&fechaFin=&pagina=&tamano=`)

- CA-04.1 Todos los filtros son opcionales y se combinan con AND.
- CA-04.2 `fechaInicio` y `fechaFin` filtran por `fechaCreacion`, ambas inclusive (fechaFin incluye todo ese día).
- CA-04.3 Si `fechaInicio` es mayor que `fechaFin`: 400.
- CA-04.4 Paginación: `pagina` inicia en 0 (por defecto 0), `tamano` entre 1 y 100 (por defecto 20).
- CA-04.5 Orden de resultados: `fechaCreacion` descendente y luego `id` descendente.
- CA-04.6 La respuesta incluye `contenido`, `pagina`, `tamano`, `totalElementos`, `totalPaginas`.

## Requisitos no funcionales

- RNF-01 Transaccional: cada caso de uso de escritura es atómico.
- RNF-02 Seguridad: el servicio valida el token JWT y el permiso de la acción (`ordenes:crear`, `ordenes:leer`, `ordenes:actualizar-estado`). Sin token válido: 401. Sin permiso: 403.
- RNF-03 Trazabilidad: cada petición tiene un id de petición (`X-Correlation-Id`); si no llega, se genera. Se devuelve en la respuesta, se escribe en todos los logs y se guarda en el histórico.
- RNF-04 Logs estructurados en JSON con: fecha, nivel, id de petición, endpoint, canal (cuando aplique), id de orden (cuando aplique), duración y resultado. Sin datos personales.
- RNF-05 Manejo global de excepciones: toda respuesta de error usa el esquema `Error` del contrato (`codigo`, `mensaje`, `fecha`). Nunca se exponen trazas ni mensajes internos de Oracle.
- RNF-06 Pruebas unitarias de casos de uso, dominio, controlador y traducción de errores.
- RNF-07 La API cumple exactamente el contrato `contracts/openapi.yaml`.

## Fuera de alcance

- Crear o administrar clientes, tipos y estados por API (se cargan por script).
- Eliminar órdenes o modificar campos distintos al estado.
- Publicación de eventos a un broker (queda como evolución futura; la arquitectura hexagonal permite agregar ese adaptador).
