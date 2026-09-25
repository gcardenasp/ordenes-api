# API de Gestión de Órdenes

API REST para gestionar órdenes operativas de una empresa de servicios públicos: creación idempotente, consulta, cambio de estado con reglas de transición e historial, y listado paginado con filtros. Está diseñada para un volumen de un millón de órdenes al mes.

El proyecto sigue Spec-Driven Development: la especificación en [`specs/001-gestion-ordenes/`](specs/001-gestion-ordenes/) manda sobre el código.

| Documento | Contenido |
|---|---|
| [`spec.md`](specs/001-gestion-ordenes/spec.md) | Historias de usuario y criterios de aceptación |
| [`plan.md`](specs/001-gestion-ordenes/plan.md) | Decisiones técnicas y de arquitectura |
| [`data-model.md`](specs/001-gestion-ordenes/data-model.md) | Modelo de datos Oracle |
| [`contracts/openapi.yaml`](specs/001-gestion-ordenes/contracts/openapi.yaml) | Contrato de la API (contract-first) |
| [`verificacion-spec.md`](docs/entregables/verificacion-spec.md) | Cada criterio de aceptación y dónde se prueba |

## Arquitectura

Arquitectura hexagonal con Java 21, Spring Boot 4.1 y Oracle Database 23ai.

- Diagrama de contenedores (C4 nivel 2): [`docs/diagramas/c2-contenedores.mmd`](docs/diagramas/c2-contenedores.mmd)
- Diagrama de componentes (C4 nivel 3): [`docs/diagramas/c3-componentes.mmd`](docs/diagramas/c3-componentes.mmd)
- Modelo entidad-relación: [`docs/diagramas/der-ordenes.mmd`](docs/diagramas/der-ordenes.mmd)

```
com.empresa.ordenes
├── domain/                        Java puro: modelo y excepciones de negocio
├── application/
│   ├── port/in/                   Casos de uso y sus comandos
│   ├── port/out/                  OrdenRepositorio (lo que el negocio necesita de la persistencia)
│   └── service/                   Implementación de los casos de uso; aquí vive la transacción
└── infrastructure/
    ├── adapter/in/rest/           Controller (implementa la interfaz generada), mapper, manejo de errores
    ├── adapter/out/oracle/        JPA para leer, JDBC para insertar y llamar al procedimiento
    └── config/                    Seguridad, trazabilidad, configuración web
```

Reglas principales:

- El paquete `domain` no importa nada de Spring, JPA ni JDBC.
- Los DTOs se generan desde `openapi.yaml` en cada compilación (`target/generated-sources`) y solo los usa el adaptador REST.
- La regla de transiciones vive solo en la base de datos: la tabla `TRANSICION_ESTADO` y el procedimiento `prc_cambio_estado_orden`. El estado nunca se cambia con un UPDATE desde Java; la entidad JPA es `@Immutable`.
- La transacción la abre el caso de uso (`@Transactional`). JPA, `JdbcClient` y el procedimiento comparten la misma transacción, y el procedimiento no hace COMMIT.

## Requisitos

- Java 21 y Maven 3.8 o superior.
- Docker con Docker Compose.
- Python 3, solo para generar tokens de prueba.

## Cómo levantar el proyecto

1. **Variables de entorno.** Copia el ejemplo y asigna valores. `.env` no se sube al repositorio.

   ```bash
   cp .env.example .env
   # Editar .env: contraseñas de Oracle y JWT_SECRET (mínimo 32 caracteres)
   ```

2. **Base de datos.** Levanta Oracle Database Free. En el primer arranque se ejecutan `database/01_tablas.sql`, `02_datos_catalogo.sql` y `03_prc_cambio_estado_orden.sql` en el esquema del usuario de aplicación.

   ```bash
   docker compose up -d
   docker logs -f ordenes-oracle      # esperar "DATABASE IS READY TO USE!"
   ```

   Los scripts solo corren cuando se crea el volumen. Para recrear la base desde cero:

   ```bash
   docker compose down -v && docker compose up -d
   ```

3. **Aplicación.** Puedes arrancarla con seguridad JWT (la configuración real) o con el perfil `local`.

   ```bash
   # Con seguridad JWT: las variables deben estar en el entorno
   set -a; . ./.env; set +a
   mvn spring-boot:run

   # Perfil local: sin token, lee .env por su cuenta
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

La API queda en `http://localhost:8080/api/v1` y Swagger UI en **http://localhost:8080/swagger-ui.html**.

### Perfil `local`

El perfil `local` desactiva la autenticación: todas las peticiones se atienden sin token y con el usuario fijo `usuario.local`. Además lee las variables del archivo `.env` y apunta al Oracle de docker-compose.

> ⚠️ Solo para desarrollo. Nunca actives el perfil `local` en un ambiente compartido.

## Token de prueba

Por defecto, la API es un Resource Server que valida JWT firmados con HS256 usando `JWT_SECRET`:

- El usuario sale del claim `sub`, que es obligatorio.
- Los permisos salen del claim `scope`.

| Endpoint | Permiso requerido |
|---|---|
| `POST /orden` | `ordenes:crear` |
| `GET /orden`, `GET /orden/{id}` | `ordenes:leer` |
| `PUT /orden/{id}/estado` | `ordenes:actualizar-estado` |

El script `scripts/generar_token.py` firma un token con el `JWT_SECRET` del entorno o del archivo `.env`:

```bash
# usuario, permisos (por defecto los tres) y minutos de vigencia (por defecto 60)
TOKEN=$(python3 scripts/generar_token.py ana.perez)
TOKEN_SOLO_LECTURA=$(python3 scripts/generar_token.py ana.perez "ordenes:leer" 30)
```

## Ejemplos con curl

Estos ejemplos se ejecutaron contra el Oracle local (T24). Los ids de estado son los del catálogo inicial: 1 CREADA, 2 ASIGNADA, 3 EN_PROCESO, 4 SUSPENDIDA, 5 FINALIZADA, 6 CANCELADA.

```bash
API=http://localhost:8080/api/v1
TOKEN=$(python3 scripts/generar_token.py ana.perez)

# Crear una orden: 201. Si se repite la llave de idempotencia: 200 con la misma orden
curl -i -X POST $API/orden \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-001' \
  -d '{"idCliente":1,"idTipo":1,"canal":"WEB","llaveIdempotencia":"demo-orden-1"}'

# Consultar: 200, o 404 si no existe
curl -i $API/orden/1 -H "Authorization: Bearer $TOKEN"

# Cambio de estado permitido (CREADA -> ASIGNADA): 200
curl -i -X PUT $API/orden/1/estado \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idEstadoNuevo":2,"observacion":"Asignada a cuadrilla norte"}'

# Cambio de estado no permitido (ASIGNADA -> FINALIZADA): 422
curl -i -X PUT $API/orden/1/estado \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idEstadoNuevo":5}'

# Listar con filtros (todos opcionales, se combinan con AND)
curl -i "$API/orden?estado=2&fechaInicio=2026-09-01&fechaFin=2026-09-30&pagina=0&tamano=10" \
  -H "Authorization: Bearer $TOKEN"

# Fechas invertidas: 400
curl -i "$API/orden?fechaInicio=2026-09-30&fechaFin=2026-09-01" -H "Authorization: Bearer $TOKEN"
```

Respuesta de ejemplo:

```json
{"id":43,"idCliente":1,"idTipo":1,"idEstado":2,"canal":"WEB","llaveIdempotencia":"demo-orden-1",
 "usuarioCreacion":"ana.perez","fechaCreacion":"2026-09-25T12:38:24.358003-05:00",
 "usuarioModificacion":"ana.perez","fechaModificacion":"2026-09-25T12:38:24.661416-05:00"}
```

### Prueba de concurrencia (T25)

La prueba se hizo contra la API real. Una sesión de SQL*Plus retuvo la orden con `SELECT ... FOR UPDATE` mientras llegaban cambios de estado:

| Caso | Resultado |
|---|---|
| Bloqueo de 3 s, dos solicitudes simultáneas a ASIGNADA | Las dos esperan unos 2,2 s. Al liberarse, una responde **200** y la otra **422** (ya no parte de CREADA). En el histórico queda **un solo** registro CREADA → ASIGNADA. |
| Bloqueo de 7 s | El procedimiento espera 5,0 s y responde **409** `ORDEN_BLOQUEADA`, sin dejar cambios. |

El mismo escenario está automatizado en `OrdenRepositorioOracleIT.dosCambiosConcurrentesNuncaSalenDelMismoEstadoAnterior`.

## Códigos de error

Toda respuesta de error usa el esquema `Error` del contrato: `{"codigo": "...", "mensaje": "...", "fecha": "..."}`. Nunca se exponen trazas ni mensajes internos de Oracle.

| HTTP | codigo | Causa |
|---|---|---|
| 400 | `DATOS_INVALIDOS` | Validación del contrato, JSON inválido, parámetro con formato inválido, `fechaInicio > fechaFin`, o error -20001 del procedimiento |
| 400 | `REFERENCIA_INEXISTENTE` | El cliente o el tipo de orden no existen |
| 401 | `NO_AUTORIZADO` | Token ausente, vencido, con firma inválida o sin `sub` |
| 403 | `ACCESO_DENEGADO` | El token no tiene el permiso del endpoint |
| 404 | `ORDEN_NO_ENCONTRADA` | La orden no existe (-20002) |
| 404 | `RECURSO_NO_ENCONTRADO` | La ruta no existe |
| 405 / 406 / 415 | `SOLICITUD_NO_SOPORTADA` | Método, formato de respuesta o tipo de contenido no soportados |
| 409 | `ORDEN_BLOQUEADA` | Otra solicitud tiene la orden bloqueada por más de 5 s (-20004) |
| 422 | `TRANSICION_INVALIDA` | Transición no permitida o estado destino inexistente (-20003) |
| 500 | `ERROR_INTERNO` | Error inesperado; el detalle solo va al log |

## Trazabilidad y logs

- Cada petición tiene un id de petición, el header `X-Correlation-Id`:
  - Si llega uno válido (`[A-Za-z0-9._-]`, hasta 100 caracteres), se usa; si no, se genera un UUID.
  - Se devuelve en la respuesta, incluidos los 401 y 403.
  - Se escribe en todos los logs y se guarda en `ORDEN_HISTORICO.ID_PETICION`.
- Los logs son JSON en formato ECS. Al terminar cada petición se registra una línea como esta:

  ```json
  {"@timestamp":"2026-09-25T17:20:40.003Z","log":{"level":"INFO"},"message":"Petición finalizada",
   "idPeticion":"demo-001","metodo":"POST","endpoint":"/api/v1/orden","estadoHttp":201,
   "duracionMs":851,"canal":"WEB","idOrden":"41"}
  ```

- No se registran datos personales del cliente: la aplicación valida su existencia con un `COUNT(*)` y nunca carga su nombre ni su identificación.

## Pruebas

```bash
mvn verify                  # 73 pruebas unitarias y de controller; no necesita Docker
mvn verify -Pintegracion    # además, 8 pruebas contra Oracle real con Testcontainers
```

- **Unitarias:** casos de uso con Mockito, traductor de errores de Oracle, `@WebMvcTest` de los cuatro endpoints, seguridad con JWT reales firmados y filtro de correlación.
- **Integración (`OrdenRepositorioOracleIT`):** levanta `gvenzl/oracle-free:23` con los mismos scripts de `database/`. Cubre creación con histórico, la carrera de idempotencia, el procedimiento y sus errores, el bloqueo (409), los cambios concurrentes y el listado. El primer arranque del contenedor tarda cerca de un minuto.

## Supuestos y decisiones

- **API Gateway:** un gateway corporativo autentica, autoriza y aplica rate limiting. El servicio valida el JWT de todas formas.
- **Particionamiento:** `ORDEN` y `ORDEN_HISTORICO` están particionadas por intervalo mensual. Oracle Free lo incluye para desarrollo; en producción requiere Enterprise Edition con la opción Partitioning.
- **Zona horaria:** las columnas `TIMESTAMP` guardan la hora de `America/Bogota` sin zona. El contenedor de Oracle corre con esa zona y la API agrega el offset (`-05:00`) al responder. Los filtros `fechaInicio` y `fechaFin` se interpretan en esa zona.
- **Versiones:** Spring Boot 4.1.1, springdoc 3.1.1 y openapi-generator 7.25.0 (`useSpringBoot4`) son compatibles entre sí, así que no fue necesario bajar a la línea 3.5.
- **Inserción por JDBC:** la orden y su histórico inicial se insertan con JDBC. Si el insert fallara dentro de Hibernate, la transacción quedaría marcada *rollback-only* y la carrera de idempotencia no podría responder 200. Está verificado contra Oracle; el detalle está en `plan.md`.
- **ORA-00054:** en Oracle 23ai, `FOR UPDATE WAIT` agotado lanza ORA-00054 en lugar de ORA-30006. El procedimiento traduce ambos a -20004.
- **Estado destino inexistente:** responde 422, igual que una transición no permitida.
- **Cadenas vacías:** en Oracle `''` es `NULL`, por eso el contrato exige `minLength: 1` en `canal` y `llaveIdempotencia`.
- **Swagger UI y `/v3/api-docs`** son públicos. Para ocultarlos en producción: `springdoc.swagger-ui.enabled=false` y `springdoc.api-docs.enabled=false`.
- **Fuera de alcance:** administración de clientes, tipos y estados (se cargan por script), eliminación de órdenes y publicación de eventos a un broker.
