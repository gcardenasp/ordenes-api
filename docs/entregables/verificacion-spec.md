# Verificación contra la especificación (T28)

Esta matriz relaciona cada criterio de aceptación y requisito no funcional de [`spec.md`](../../specs/001-gestion-ordenes/spec.md) con el código que lo implementa y la prueba que lo verifica.

**Estado de las pruebas:**

- `mvn verify`: 73 pruebas en verde.
- `mvn verify -Pintegracion`: además, 8 pruebas en verde contra Oracle con Testcontainers.
- Prueba manual T24/T25 contra Oracle local: todos los resultados fueron los esperados (ver README).

**Leyenda:**

- **Unit:** prueba unitaria con Mockito.
- **Web:** `@WebMvcTest`.
- **IT:** `OrdenRepositorioOracleIT`, contra Oracle real.
- **Manual:** T24/T25.
- **Diseño:** garantizado por construcción, sin una prueba dedicada.

## HU-01 Crear orden

| Criterio | Implementación | Verificación |
|---|---|---|
| CA-01.1 Estado inicial `CREADA` sin que el consumidor lo envíe | `CrearOrdenService` busca el id por `EstadoOrden.CODIGO_INICIAL`; `CrearOrdenRequest` no tiene campo de estado | Unit `creaLaOrdenEnEstadoInicialCuandoLaLlaveEsNueva`; IT `creaLaOrdenConSuHistoricoInicialYRespetaLaIdempotencia` |
| CA-01.2 Primer histórico (anterior nulo) en la misma transacción | `OrdenRepositorioOracle.crear` inserta ORDEN y ORDEN_HISTORICO en la transacción de `CrearOrdenService` | IT `creaLaOrdenConSuHistoricoInicialYRespetaLaIdempotencia` (anterior nulo, usuario, id de petición). Atomicidad: Diseño (`@Transactional`) |
| CA-01.3 Campos obligatorios y formato: 400 | Validaciones generadas desde el contrato (`@NotNull`, `@Size(min = 1, max)`) | Web `responde400SiFaltaUnCampoObligatorio`, `responde400SiElCanalEstaVacioOEsDemasiadoLargo`, `responde400SiElJsonEsInvalido` |
| CA-01.4 Cliente o tipo inexistente: 400 con mensaje claro | `CrearOrdenService.validarReferencias` y `ReferenciaInexistenteException` | Unit `rechazaUnClienteInexistente`, `rechazaUnTipoInexistente`; Web `responde400SiElClienteNoExiste`; Manual T24 |
| CA-01.5 Idempotencia: llave repetida devuelve 200 con la existente; con peticiones simultáneas solo una crea | Búsqueda previa por llave, más la violación de `UK_ORDEN_LLAVE_IDEMPOTENCIA` atrapada en el insert JDBC y relectura | Unit `retornaLaOrdenExistenteSinCrearCuandoLaLlaveYaExiste`, `retornaLaOrdenExistenteCuandoOtraPeticionGanaLaCarrera`; Web `responde200ConLaOrdenExistenteSiLaLlaveYaExistia`; IT `retornaLaOrdenExistenteCuandoPierdeLaCarreraPorLaLlave` (carrera real entre dos conexiones) |
| CA-01.6 201 con la orden creada | `OrdenController.crearOrden` | Web `responde201ConLaOrdenCreadaYElUsuarioDelToken`; Manual T24 |
| CA-01.7 `usuarioCreacion` sale del token, nunca del cuerpo | `UsuarioJwtProvider` (claim `sub`); el contrato no acepta usuario en el cuerpo | Web `ignoraElUsuarioSiVieneEnElCuerpo`; `SeguridadJwtTest.elUsuarioDeLaOrdenSaleDelSubDelTokenYElIdDePeticionDelHeader`; Manual T24 (`usuarioCreacion = ana.perez`) |

## HU-02 Consultar orden

| Criterio | Implementación | Verificación |
|---|---|---|
| CA-02.1 Existe: 200 | `ConsultarOrdenService` | Unit `retornaLaOrdenCuandoExiste`; Web `responde200ConLaOrden` |
| CA-02.2 No existe: 404 | `OrdenNoEncontradaException` → `ORDEN_NO_ENCONTRADA` | Unit `lanzaOrdenNoEncontradaCuandoNoExiste`; Web `responde404SiNoExiste`; Manual T24 |
| CA-02.3 Id no numérico: 400 | `GlobalExceptionHandler` (error de tipo de parámetro) | Web `responde400SiElIdNoEsNumerico` |

## HU-03 Actualizar estado

| Criterio | Implementación | Verificación |
|---|---|---|
| CA-03.1 El cambio se hace con `prc_cambio_estado_orden` | `OrdenRepositorioOracle.cambiarEstado` (`SimpleJdbcCall`); `OrdenEntity` es `@Immutable` y `OrdenJpaRepository` no tiene `save` | Unit `delegaElCambioAlPuertoYRetornaLaOrdenReleida`; IT `cambiaElEstadoConElProcedimientoYRegistraElHistorico` |
| CA-03.2 Transición permitida: 200 e histórico con anterior, nuevo, usuario, observación e id de petición | El procedimiento, más la relectura en `CambiarEstadoOrdenService` | IT `cambiaElEstadoConElProcedimientoYRegistraElHistorico`; Web `responde200ConLaOrdenActualizada`; Manual T25 (histórico con `concurrencia-2`) |
| CA-03.3 Errores -20001 → 400, -20002 → 404, -20003 → 422, -20004 → 409 | `TraductorErroresOracle` y `GlobalExceptionHandler` | Unit `traduceCadaCodigoDelProcedimientoASuExcepcionDeDominio` (4 casos), `encuentraElCodigoAunqueLaSqlExceptionEsteAnidada`; Web `responde400SiElProcedimientoRechazaLosDatos`, `responde404SiLaOrdenNoExiste`, `responde409SiLaOrdenEstaBloqueada`, `responde422SiLaTransicionNoEstaPermitida`; IT `traduceLosErroresDelProcedimientoSinDejarCambiosParciales`, `responderOrdenBloqueadaSiOtraTransaccionLaTieneTomadaMasDeCincoSegundos` |
| CA-03.3.1 Estado destino inexistente: 422 | El procedimiento no encuentra la transición y lanza -20003 | IT `traduceLosErroresDelProcedimientoSinDejarCambiosParciales` (estado 999999) |
| CA-03.4 Concurrencia: la segunda solicitud espera hasta 5 s y valida contra el estado ya actualizado | `SELECT ... FOR UPDATE WAIT 5` en el procedimiento; ORA-30006 y ORA-00054 se traducen a -20004 | IT `dosCambiosConcurrentesNuncaSalenDelMismoEstadoAnterior`, `responderOrdenBloqueadaSiOtraTransaccionLaTieneTomadaMasDeCincoSegundos`; Manual T25 (200 + 422 con un solo histórico desde CREADA; 409 a los 5,0 s) |
| CA-03.5 Ningún cambio parcial ante error | `@Transactional` en `CambiarEstadoOrdenService`; el procedimiento no hace COMMIT | IT `traduceLosErroresDelProcedimientoSinDejarCambiosParciales` (el estado y el histórico no cambian) |

## HU-04 Listar órdenes

| Criterio | Implementación | Verificación |
|---|---|---|
| CA-04.1 Filtros opcionales combinados con AND | `Specification` dinámica en `OrdenRepositorioOracle.listar` | Web `pasaTodosLosFiltrosAlCasoDeUso`; Unit `aceptaUnaSolaFechaSinLaOtra`; IT `listaConFiltrosOrdenDescendenteYPaginacion` (estado y fecha) |
| CA-04.2 `fechaInicio` y `fechaFin` inclusive (fechaFin cubre todo el día) | `fecha_creacion >= inicio` y `fecha_creacion < fin + 1 día`, en hora de Bogotá | IT `listaConFiltrosOrdenDescendenteYPaginacion` (hoy/hoy incluye las órdenes de hoy; ayer/ayer las excluye); Unit `aceptaElMismoDiaComoInicioYFin` |
| CA-04.3 `fechaInicio > fechaFin`: 400 | `ListarOrdenesService` | Unit `rechazaFechaInicioMayorQueFechaFin`; Web `responde400SiFechaInicioEsMayorQueFechaFin`; Manual T24 |
| CA-04.4 `pagina` desde 0 (por defecto 0); `tamano` entre 1 y 100 (por defecto 20) | Valores por defecto y `@Min`/`@Max` generados desde el contrato | Web `responde200ConLaPaginaYUsaLosValoresPorDefecto`, `responde400SiElTamanoEstaFueraDeRango`, `responde400SiLaPaginaEsNegativa` |
| CA-04.5 Orden `fechaCreacion` DESC, luego `id` DESC | `Sort` `ORDEN_LISTADO` | IT `listaConFiltrosOrdenDescendenteYPaginacion` (la más reciente primero). El desempate por `id`: Diseño |
| CA-04.6 Respuesta con `contenido`, `pagina`, `tamano`, `totalElementos`, `totalPaginas` | `OrdenRestMapper.aDto(Pagina)` | Web `responde200ConLaPaginaYUsaLosValoresPorDefecto`; IT (totales con página de tamaño 1) |

## Requisitos no funcionales

| Requisito | Implementación | Verificación |
|---|---|---|
| RNF-01 Cada escritura es atómica | `@Transactional` en los servicios; JPA, JDBC y el procedimiento comparten la transacción | IT `traduceLosErroresDelProcedimientoSinDejarCambiosParciales` |
| RNF-02 JWT y permiso por acción; 401 y 403 | `SecurityConfig` (HS256, `scope`, `sub` obligatorio) y `RespuestaErrorSeguridad` | `SeguridadJwtTest`: permiso correcto por endpoint (4), 403 sin permiso (4), 401 sin token (4), firma ajena, token vencido, token sin `sub`; Manual T24 |
| RNF-03 `X-Correlation-Id`: se genera si no llega, se devuelve, va a los logs y al histórico | `CorrelationIdFilter` (MDC), controller y comandos | `CorrelationIdFilterTest` (5 pruebas); `SeguridadJwtTest.responde401SinToken` (header también en 401), `elUsuarioDeLaOrdenSaleDelSubDelTokenYElIdDePeticionDelHeader`; IT (histórico con `id_peticion`) |
| RNF-04 Logs JSON con fecha, nivel, id de petición, endpoint, canal, id de orden, duración y resultado, sin datos personales | Formato ECS, log de fin de petición y MDC; el cliente se valida con `COUNT(*)` y nunca se carga | `CorrelationIdFilterTest.escribeUnLogJsonAlTerminarLaPeticion`; Manual (línea de log revisada en la Fase 5) |
| RNF-05 Esquema `Error` en toda respuesta de error, sin trazas ni mensajes de Oracle | `GlobalExceptionHandler`, `RespuestaErrorSeguridad` y mensajes propios del traductor | Web `responde500SinExponerElDetalleInterno`, `responde405ConElEsquemaDeError`, `responde415SiElCuerpoNoEsJson`, `responde404SiLaRutaNoExiste`; Unit `traduceCadaCodigo...` (el mensaje no contiene `ORA-`) |
| RNF-06 Pruebas de casos de uso, dominio, controlador y traducción de errores | — | 14 de casos de uso, 10 del traductor, 27 del controller, 22 de seguridad y trazabilidad, 8 de integración |
| RNF-07 La API cumple el contrato | El controller implementa la interfaz generada desde `openapi.yaml`: si se desvía, no compila | Compilación; Web (nombres de campos y códigos HTTP); rutas publicadas por springdoc revisadas en la Fase 5. No hay una validación automática de esquema contra el contrato |

## Observaciones

- **Dominio sin pruebas propias:** el dominio son records y excepciones sin lógica. Sus reglas (estado inicial, validación de fechas, idempotencia) viven en los casos de uso y se prueban ahí.
- **Desempate por `id` en CA-04.5:** está implementado pero no tiene una prueba dedicada. Probarlo exige dos órdenes con la misma `fecha_creacion` al microsegundo.
- **RNF-07:** la conformidad con el contrato está garantizada en rutas, parámetros y validaciones. Una prueba que valide las respuestas contra el esquema OpenAPI (por ejemplo con `swagger-request-validator`) sería una mejora posible.
