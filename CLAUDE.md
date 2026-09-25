# Constitución del proyecto: API de Gestión de Órdenes

Este proyecto se desarrolla con Spec-Driven Development (SDD). La especificación manda sobre el código.

## Fuentes de verdad (leer antes de escribir código)

1. `specs/001-gestion-ordenes/spec.md`: qué debe hacer el sistema y criterios de aceptación.
2. `specs/001-gestion-ordenes/plan.md`: decisiones técnicas y de arquitectura.
3. `specs/001-gestion-ordenes/data-model.md`: modelo de datos Oracle.
4. `specs/001-gestion-ordenes/contracts/openapi.yaml`: contrato de la API (contract-first).
5. `database/03_prc_cambio_estado_orden.sql`: procedimiento PL/SQL ya aprobado.
6. `specs/001-gestion-ordenes/tasks.md`: orden de ejecución.

Si el código y la especificación no coinciden, se corrige el código. Si la especificación tiene un vacío o una contradicción, NO se inventa: se detiene el trabajo y se pregunta al usuario.

## Reglas no negociables

- Arquitectura hexagonal. El paquete `domain` no importa nada de Spring, JPA, JDBC ni Oracle.
- Los DTOs generados desde OpenAPI viven solo en el adaptador REST. Nunca llegan a los casos de uso.
- La transacción se define en los servicios de aplicación (casos de uso) con `@Transactional`. El procedimiento PL/SQL NO hace COMMIT ni ROLLBACK.
- La regla de transiciones de estado vive SOLO en la base de datos (tabla `TRANSICION_ESTADO` + procedimiento). No duplicarla en Java.
- El cambio de estado se hace SIEMPRE llamando a `prc_cambio_estado_orden`. Nunca con un UPDATE desde JPA.
- No modificar `openapi.yaml` ni `03_prc_cambio_estado_orden.sql` sin aprobación explícita del usuario.
- Nada de credenciales en el repositorio: todo por variables de entorno.
- No registrar datos personales del cliente en los logs.
- Código, nombres de clases de dominio, mensajes y comentarios en español. Nombres técnicos estándar (Controller, Repository, Config) pueden ir en inglés.

## Forma de trabajo

- Ejecutar `tasks.md` fase por fase. Al terminar cada fase: compilar, correr las pruebas, marcar las tareas con `[x]`, resumir en pocas líneas lo hecho y ESPERAR la revisión del usuario antes de seguir.
- Al cerrar cada fase, explicar brevemente las decisiones técnicas no obvias.
- Commits pequeños, uno por fase como mínimo, con mensajes en español.
