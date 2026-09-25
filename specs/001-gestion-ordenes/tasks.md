# Tareas

Ejecutar en orden. Al terminar cada fase: compilar, correr pruebas, marcar `[x]`, resumir, hacer commit y esperar revisión.

## Fase 0: Base de datos
- [x] T01 Crear `database/01_tablas.sql` según `data-model.md` (tablas, PK, FK, UK, CHECK, particiones, índices).
- [x] T02 Crear `database/02_datos_catalogo.sql` (estados, transiciones por código, tipos, clientes de prueba, COMMIT).
- [x] T03 Crear `docker-compose.yml` con Oracle Free y ejecución automática de los tres scripts en el esquema de la aplicación.
- [x] T04 Levantar el contenedor y verificar: tablas creadas, particiones activas, procedimiento compilado sin errores (`USER_ERRORS` vacío), catálogos cargados.

## Fase 1: Proyecto y contrato
- [x] T05 Crear proyecto Maven Spring Boot (Java 21) con las dependencias del plan. Verificar y fijar versiones compatibles.
- [x] T06 Agregar al contrato las respuestas 403 y 500 (aprobado por el usuario). Configurar `openapi-generator-maven-plugin` con el contrato y confirmar que genera interfaces y DTOs.
- [x] T07 Crear la estructura de paquetes hexagonal vacía y `application.yml` con variables de entorno y perfil `local`.
- [x] T08 `.gitignore` (target, IDE, .env). `.env.example` con las variables sin valores reales.

## Fase 2: Dominio y aplicación
- [x] T09 Modelo de dominio y excepciones de dominio.
- [x] T10 Puertos de entrada (casos de uso + comandos) y puerto de salida `OrdenRepositorio`.
- [x] T11 Servicios de aplicación con `@Transactional` / `readOnly`.
- [x] T12 Pruebas unitarias de los servicios con Mockito (puerto de salida simulado): creación, idempotencia (llave repetida y carrera), consulta inexistente, cambio de estado delegando al puerto, listado con validación de fechas.

## Fase 3: Adaptador Oracle
- [x] T13 Entidades JPA y `OrdenJpaRepository`.
- [x] T14 `OrdenRepositorioOracle`: crear (orden + histórico), buscar por id y por llave, listar con filtros y paginación, existencia de cliente/tipo, id de estado por código.
- [x] T15 Llamada a `prc_cambio_estado_orden` con `SimpleJdbcCall` y traductor de errores ORA-20001..20004 a excepciones de dominio.
- [x] T16 Prueba unitaria del traductor de errores (cada código a su excepción).

## Fase 4: Adaptador REST
- [x] T17 Controller que implementa la interfaz generada y mappers DTO <-> dominio.
- [x] T18 `GlobalExceptionHandler` según la tabla del plan.
- [x] T19 Pruebas `@WebMvcTest` de los cuatro endpoints: casos felices y cada error (400, 404, 409, 422), incluido 200 por idempotencia.

## Fase 5: Seguridad y trazabilidad
- [x] T20 `SecurityConfig` (JWT HS256, permisos por endpoint) y perfil `local` sin seguridad.
- [x] T21 `UsuarioActualProvider` para obtener el usuario del token.
- [x] T22 `CorrelationIdFilter`, logs JSON y log de fin de petición.
- [x] T23 Pruebas: 401 sin token, 403 sin permiso, header `X-Correlation-Id` presente en la respuesta.

## Fase 6: Verificación y entrega
- [x] T24 Prueba manual contra Oracle local: crear, consultar, cambiar estado válido e inválido, listar con filtros. Dejar los ejemplos `curl` en el README.
- [x] T25 Prueba de concurrencia manual: dos cambios de estado simultáneos sobre la misma orden; verificar que el histórico es consistente.
- [x] T26 (Opcional) Prueba de integración con Testcontainers Oracle.
- [x] T27 README: descripción, arquitectura (enlazar diagramas), cómo levantar (Docker + Maven), perfil `local`, cómo generar un token de prueba, tabla de códigos de error, ruta de Swagger UI, supuestos.
- [x] T28 Revisión final contra `spec.md`: cada criterio de aceptación cumplido y dónde se prueba.
