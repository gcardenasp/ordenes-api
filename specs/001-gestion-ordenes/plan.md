# Plan técnico

## Stack

- Java 21, Maven.
- Spring Boot: la versión estable más reciente que sea compatible con `openapi-generator-maven-plugin` (generador `spring`) y con `springdoc-openapi`. Verificar compatibilidad antes de fijarla; si la línea 4.x da problemas con alguno de los dos, usar la última 3.5.x y dejarlo anotado en el README.
- Spring Web (Spring MVC), Spring Data JPA, Spring JDBC (`SimpleJdbcCall`), Spring Security (OAuth2 Resource Server con JWT), Bean Validation.
- Driver `ojdbc11`.
- `springdoc-openapi-starter-webmvc-ui` para exponer Swagger UI en `/swagger-ui.html`.
- Pruebas: JUnit 5, Mockito, AssertJ, `@WebMvcTest`. Testcontainers con Oracle es opcional (fase final).
- Base local: Docker Compose con la imagen `gvenzl/oracle-free` (Oracle Database Free 23ai).

## Arquitectura (hexagonal)

Diagramas: `docs/diagramas/c2-contenedores.mmd` y `docs/diagramas/c3-componentes.mmd`.

Paquete raíz: `com.empresa.ordenes`

```
domain/
  model/          Orden, EstadoOrden (código), Pagina<T>, FiltroOrdenes (records o clases puras)
  exception/      OrdenNoEncontradaException, TransicionInvalidaException,
                  OrdenBloqueadaException, DatosInvalidosException, ReferenciaInexistenteException
application/
  port/in/        CrearOrdenUseCase, ConsultarOrdenUseCase, CambiarEstadoOrdenUseCase, ListarOrdenesUseCase
                  (con sus comandos de entrada, p. ej. CrearOrdenCommand)
  port/out/       OrdenRepositorio (puerto de persistencia)
  service/        Implementaciones de los casos de uso, con @Transactional
infrastructure/
  adapter/in/rest/     Controller que implementa la interfaz generada, mappers DTO <-> dominio,
                       GlobalExceptionHandler (@RestControllerAdvice)
  adapter/out/oracle/  Entidades JPA, OrdenJpaRepository (Spring Data), OrdenRepositorioOracle
                       (implementa OrdenRepositorio), llamada al procedimiento, traducción de errores
  config/              SecurityConfig, CorrelationIdFilter, configuración general
```

Reglas:
- `domain` es Java puro.
- `application` puede usar `@Transactional` y `@Service` (decisión documentada: la transacción vive en el caso de uso).
- Los DTOs generados quedan en un paquete propio, p. ej. `com.empresa.ordenes.infrastructure.adapter.in.rest.api` y `...rest.dto`, y solo los usa el adaptador REST.

## Contract-first

- `openapi-generator-maven-plugin` lee `specs/001-gestion-ordenes/contracts/openapi.yaml`.
- Opciones: `interfaceOnly=true`, `useSpringBoot3=true` (o la opción equivalente para la versión elegida), `useBeanValidation=true`, `useTags=true`, `openApiNullable=false`, `skipDefaultInterface=true`.
- El código generado va a `target/generated-sources` y no se sube al repositorio.
- Como el `servers.url` incluye `/api/v1`, configurar `server.servlet.context-path` o el prefijo de rutas para que las rutas finales sean `/api/v1/orden...`.

## Casos de uso

### CrearOrden
1. Validar que existan cliente y tipo (si no: `ReferenciaInexistenteException` -> 400).
2. Buscar por `llaveIdempotencia`; si existe, retornar la existente indicando que no es nueva (-> 200).
3. Obtener el id del estado `CREADA` por código.
4. Insertar ORDEN e insertar ORDEN_HISTORICO (estado anterior nulo) en la misma transacción.
5. Si al insertar se viola la UNIQUE de la llave (carrera entre dos peticiones iguales), capturar la violación, releer por la llave y retornar la existente (-> 200). Para esto, el insert debe hacer flush dentro del adaptador para detectar la violación ahí.
6. Retornar 201 con la orden.

### CambiarEstadoOrden
1. Llamar a `prc_cambio_estado_orden` con: id orden, id estado nuevo, usuario (del token), observación, id de petición.
2. El adaptador traduce `SQLException.getErrorCode()` (buscando en la cadena de causas de la `DataAccessException`): 20001 -> DatosInvalidos, 20002 -> OrdenNoEncontrada, 20003 -> TransicionInvalida, 20004 -> OrdenBloqueada.
3. Releer la orden y retornarla.
4. La regla de transición NO se valida en Java.

### ConsultarOrden
`@Transactional(readOnly = true)`. No existe -> OrdenNoEncontradaException.

### ListarOrdenes
`@Transactional(readOnly = true)`. Consulta con filtros opcionales (Specification o JPQL con condiciones nulas), paginación con `PageRequest` y orden `fechaCreacion DESC, id DESC`. `fechaFin` inclusive: filtrar `fecha_creacion < fechaFin + 1 día`.

## Transacciones

- Escritura: `@Transactional` en los servicios de aplicación. Rollback por excepciones no chequeadas (todas las excepciones de dominio son `RuntimeException`).
- JPA y `SimpleJdbcCall` comparten la misma transacción mediante `JpaTransactionManager` (el que configura Spring Boot por defecto con JPA).
- El procedimiento no hace COMMIT.
- Aislamiento: READ COMMITTED (por defecto en Oracle). La concurrencia del cambio de estado la resuelve `SELECT ... FOR UPDATE WAIT 5` dentro del procedimiento.

## Seguridad

- Perfil por defecto: Resource Server JWT con clave simétrica HS256 leída de la variable `JWT_SECRET`. El usuario sale del claim `sub`. Los permisos vienen en el claim `scope` (`ordenes:crear ordenes:leer ordenes:actualizar-estado`) y se validan por endpoint.
- Perfil `local`: seguridad deshabilitada y usuario fijo `usuario.local`, para probar sin token. Documentarlo en el README.
- Obtener el usuario mediante un componente del adaptador (p. ej. `UsuarioActualProvider`) y pasarlo al caso de uso dentro del comando. El dominio no conoce Spring Security.

## Logging y trazabilidad

- `CorrelationIdFilter`: toma `X-Correlation-Id` o genera un UUID, lo pone en el MDC (`idPeticion`), lo devuelve en el header de respuesta y lo limpia al final.
- Logs estructurados JSON nativos de Spring Boot (`logging.structured.format.console=ecs` o `logstash`).
- Un log por petición al terminar con: método, endpoint, estado HTTP, duración en ms, idPeticion; y cuando aplique idOrden y canal.
- Nunca registrar nombre, apellido, razón social ni identificación del cliente.

## Manejo global de excepciones

`@RestControllerAdvice` que devuelve el esquema `Error` (`codigo`, `mensaje`, `fecha`):

| Excepción | HTTP | codigo |
|---|---|---|
| Validación de Bean Validation / parámetros | 400 | DATOS_INVALIDOS |
| DatosInvalidosException | 400 | DATOS_INVALIDOS |
| ReferenciaInexistenteException | 400 | REFERENCIA_INEXISTENTE |
| OrdenNoEncontradaException | 404 | ORDEN_NO_ENCONTRADA |
| OrdenBloqueadaException | 409 | ORDEN_BLOQUEADA |
| TransicionInvalidaException | 422 | TRANSICION_INVALIDA |
| Cualquier otra | 500 | ERROR_INTERNO (mensaje genérico, el detalle solo al log) |

## Configuración

- `application.yml` con placeholders: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`.
- `spring.jpa.hibernate.ddl-auto=validate` (el esquema lo crean los scripts, nunca Hibernate).
- `spring.jpa.open-in-view=false`.

## Entorno local

- `docker-compose.yml` con `gvenzl/oracle-free`, usuario de aplicación (`APP_USER`/`APP_USER_PASSWORD`) y los scripts de `database/` montados para ejecutarse al iniciar.
- Verificar en la documentación de la imagen cómo se ejecutan los scripts de inicio y garantizar que los objetos queden en el esquema del usuario de aplicación, no en SYS. El script del procedimiento termina en `/`, compatible con SQL*Plus.
