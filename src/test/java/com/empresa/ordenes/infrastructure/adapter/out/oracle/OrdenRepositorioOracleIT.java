package com.empresa.ordenes.infrastructure.adapter.out.oracle;

import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenCommand;
import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenUseCase;
import com.empresa.ordenes.application.port.in.CrearOrdenCommand;
import com.empresa.ordenes.application.port.in.CrearOrdenUseCase;
import com.empresa.ordenes.application.port.in.ListarOrdenesUseCase;
import com.empresa.ordenes.application.port.in.ResultadoCrearOrden;
import com.empresa.ordenes.domain.exception.OrdenBloqueadaException;
import com.empresa.ordenes.domain.exception.OrdenNoEncontradaException;
import com.empresa.ordenes.domain.exception.TransicionInvalidaException;
import com.empresa.ordenes.domain.model.FiltroOrdenes;
import com.empresa.ordenes.domain.model.Orden;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba contra un Oracle real en Docker, creado con los mismos scripts de database/ que usa
 * docker-compose. Se ejecuta con: mvn verify -Pintegracion
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class OrdenRepositorioOracleIT {

    @Container
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23")
            .withUsername("ordenes")
            .withPassword("ordenes_it")
            .withEnv("TZ", "America/Bogota")
            .withCopyFileToContainer(MountableFile.forHostPath("database"), "/opt/ordenes/database")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/oracle/initdb/01_crear_esquema.sh", 0755),
                    "/container-entrypoint-initdb.d/01_crear_esquema.sh")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void conexion(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", ORACLE::getJdbcUrl);
        propiedades.add("spring.datasource.username", ORACLE::getUsername);
        propiedades.add("spring.datasource.password", ORACLE::getPassword);
    }

    private static final String PREFIJO_LLAVE = "it-";

    @Autowired
    private CrearOrdenUseCase crearOrden;
    @Autowired
    private CambiarEstadoOrdenUseCase cambiarEstado;
    @Autowired
    private ListarOrdenesUseCase listarOrdenes;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DataSource dataSource;

    @Test
    void creaLaOrdenConSuHistoricoInicialYRespetaLaIdempotencia() {
        var comando = comando(llaveNueva());

        ResultadoCrearOrden primera = crearOrden.crear(comando);
        ResultadoCrearOrden repetida = crearOrden.crear(comando);

        assertThat(primera.nueva()).isTrue();
        assertThat(primera.orden().idEstado()).isEqualTo(idEstado("CREADA"));
        assertThat(primera.orden().fechaCreacion()).isNotNull();
        assertThat(repetida.nueva()).isFalse();
        assertThat(repetida.orden().id()).isEqualTo(primera.orden().id());

        var historico = jdbc.sql("SELECT id_estado_anterior, id_estado_nuevo, usuario, id_peticion FROM orden_historico WHERE id_orden = ?")
                .param(primera.orden().id()).query().listOfRows();
        assertThat(historico).hasSize(1);
        assertThat(historico.getFirst().get("ID_ESTADO_ANTERIOR")).isNull();
        assertThat(historico.getFirst().get("USUARIO")).isEqualTo("usuario.it");
        assertThat(historico.getFirst().get("ID_PETICION")).isEqualTo("peticion-it");
    }

    @Test
    void laFechaDeCreacionQuedaEnHoraDeBogota() {
        Orden orden = crearOrden.crear(comando(llaveNueva())).orden();

        assertThat(orden.fechaCreacion().toLocalDate()).isEqualTo(LocalDate.now(ZoneId.of("America/Bogota")));
    }

    @Test
    void retornaLaOrdenExistenteCuandoPierdeLaCarreraPorLaLlave() throws Exception {
        String llave = llaveNueva();
        try (Connection otra = dataSource.getConnection()) {
            otra.setAutoCommit(false);
            // Otra "petición" inserta la misma llave y aún no confirma
            try (var insert = otra.prepareStatement(
                    "INSERT INTO orden (id_cliente, id_tipo, id_estado, canal, llave_idempotencia, usuario_creacion) "
                            + "VALUES (?, ?, ?, 'WEB', ?, 'otra.peticion')")) {
                insert.setLong(1, idCliente());
                insert.setLong(2, idTipo());
                insert.setLong(3, idEstado("CREADA"));
                insert.setString(4, llave);
                insert.executeUpdate();
            }

            // No ve la fila sin confirmar, intenta insertar y queda esperando en el índice único
            var enCurso = CompletableFuture.supplyAsync(() -> crearOrden.crear(comando(llave)));
            Thread.sleep(1500);
            assertThat(enCurso).isNotDone();

            otra.commit();
            ResultadoCrearOrden resultado = enCurso.get(10, TimeUnit.SECONDS);

            assertThat(resultado.nueva()).isFalse();
            assertThat(resultado.orden().usuarioCreacion()).isEqualTo("otra.peticion");
        }
        assertThat(contar("SELECT COUNT(*) FROM orden WHERE llave_idempotencia = ?", llave)).isEqualTo(1);
    }

    @Test
    void cambiaElEstadoConElProcedimientoYRegistraElHistorico() {
        Orden orden = crearOrden.crear(comando(llaveNueva())).orden();

        Orden asignada = cambiarEstado.cambiarEstado(new CambiarEstadoOrdenCommand(
                orden.id(), idEstado("ASIGNADA"), "usuario.it", "Asignada a cuadrilla", "peticion-cambio"));

        assertThat(asignada.idEstado()).isEqualTo(idEstado("ASIGNADA"));
        assertThat(asignada.usuarioModificacion()).isEqualTo("usuario.it");
        assertThat(asignada.fechaModificacion()).isNotNull();
        assertThat(contar("SELECT COUNT(*) FROM orden_historico WHERE id_orden = ? AND id_peticion = 'peticion-cambio'",
                orden.id())).isEqualTo(1);
    }

    @Test
    void traduceLosErroresDelProcedimientoSinDejarCambiosParciales() {
        Orden orden = crearOrden.crear(comando(llaveNueva())).orden();

        assertThatThrownBy(() -> cambiarEstado.cambiarEstado(cambio(orden.id(), idEstado("FINALIZADA"))))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(() -> cambiarEstado.cambiarEstado(cambio(orden.id(), 999_999L)))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(() -> cambiarEstado.cambiarEstado(cambio(-1L, idEstado("ASIGNADA"))))
                .isInstanceOf(OrdenNoEncontradaException.class);

        assertThat(contar("SELECT COUNT(*) FROM orden_historico WHERE id_orden = ?", orden.id())).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM orden WHERE id = ? AND id_estado = ?", orden.id(), idEstado("CREADA")))
                .isEqualTo(1);
    }

    @Test
    void responderOrdenBloqueadaSiOtraTransaccionLaTieneTomadaMasDeCincoSegundos() throws Exception {
        Orden orden = crearOrden.crear(comando(llaveNueva())).orden();
        try (Connection otra = dataSource.getConnection()) {
            otra.setAutoCommit(false);
            try (var bloqueo = otra.prepareStatement("SELECT id FROM orden WHERE id = ? FOR UPDATE")) {
                bloqueo.setLong(1, orden.id());
                bloqueo.executeQuery();
            }

            assertThatThrownBy(() -> cambiarEstado.cambiarEstado(cambio(orden.id(), idEstado("ASIGNADA"))))
                    .isInstanceOf(OrdenBloqueadaException.class);
            otra.rollback();
        }
    }

    @Test
    void dosCambiosConcurrentesNuncaSalenDelMismoEstadoAnterior() throws Exception {
        Orden orden = crearOrden.crear(comando(llaveNueva())).orden();
        Long asignada = idEstado("ASIGNADA");

        try (Connection otra = dataSource.getConnection()) {
            otra.setAutoCommit(false);
            try (var bloqueo = otra.prepareStatement("SELECT id FROM orden WHERE id = ? FOR UPDATE")) {
                bloqueo.setLong(1, orden.id());
                bloqueo.executeQuery();
            }
            // Las dos solicitudes quedan esperando el bloqueo y compiten cuando se libera
            var primera = CompletableFuture.supplyAsync(() -> intentarCambio(orden.id(), asignada));
            var segunda = CompletableFuture.supplyAsync(() -> intentarCambio(orden.id(), asignada));
            Thread.sleep(1500);
            otra.rollback();

            assertThat(List.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "TransicionInvalidaException");
        }
        assertThat(contar("SELECT COUNT(*) FROM orden_historico WHERE id_orden = ? AND id_estado_anterior = ?",
                orden.id(), idEstado("CREADA"))).isEqualTo(1);
    }

    @Test
    void listaConFiltrosOrdenDescendenteYPaginacion() {
        Orden primera = crearOrden.crear(comando(llaveNueva())).orden();
        Orden segunda = crearOrden.crear(comando(llaveNueva())).orden();
        Orden tercera = crearOrden.crear(comando(llaveNueva())).orden();
        cambiarEstado.cambiarEstado(cambio(tercera.id(), idEstado("ASIGNADA")));
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        var creadasHoy = listarOrdenes.listar(new FiltroOrdenes(idEstado("CREADA"), hoy, hoy, 0, 100));
        var paginaDeUna = listarOrdenes.listar(new FiltroOrdenes(idEstado("CREADA"), hoy, hoy, 0, 1));
        var deAyer = listarOrdenes.listar(new FiltroOrdenes(null, hoy.minusDays(1), hoy.minusDays(1), 0, 20));

        assertThat(creadasHoy.contenido()).extracting(Orden::id)
                .containsSubsequence(segunda.id(), primera.id())
                .doesNotContain(tercera.id());
        assertThat(paginaDeUna.contenido()).hasSize(1);
        assertThat(paginaDeUna.totalElementos()).isEqualTo(creadasHoy.totalElementos());
        assertThat(paginaDeUna.totalPaginas()).isEqualTo((int) creadasHoy.totalElementos());
        assertThat(deAyer.contenido()).extracting(Orden::id).doesNotContain(primera.id(), segunda.id(), tercera.id());
    }

    private String intentarCambio(Long idOrden, Long idEstadoNuevo) {
        try {
            cambiarEstado.cambiarEstado(cambio(idOrden, idEstadoNuevo));
            return "OK";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    private CrearOrdenCommand comando(String llave) {
        return new CrearOrdenCommand(idCliente(), idTipo(), "WEB", llave, "usuario.it", "peticion-it");
    }

    private CambiarEstadoOrdenCommand cambio(Long idOrden, Long idEstadoNuevo) {
        return new CambiarEstadoOrdenCommand(idOrden, idEstadoNuevo, "usuario.it", null, "peticion-it");
    }

    private static String llaveNueva() {
        return PREFIJO_LLAVE + UUID.randomUUID();
    }

    private Long idEstado(String codigo) {
        return jdbc.sql("SELECT id FROM estado_orden WHERE codigo = ?").param(codigo).query(Long.class).single();
    }

    private Long idCliente() {
        return jdbc.sql("SELECT MIN(id) FROM cliente").query(Long.class).single();
    }

    private Long idTipo() {
        return jdbc.sql("SELECT MIN(id) FROM tipo_orden").query(Long.class).single();
    }

    private long contar(String sql, Object... parametros) {
        return jdbc.sql(sql).params(parametros).query(Long.class).single();
    }
}
