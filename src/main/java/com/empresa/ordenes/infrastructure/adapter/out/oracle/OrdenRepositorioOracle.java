package com.empresa.ordenes.infrastructure.adapter.out.oracle;

import com.empresa.ordenes.application.port.out.OrdenRepositorio;
import com.empresa.ordenes.domain.exception.LlaveIdempotenciaDuplicadaException;
import com.empresa.ordenes.domain.model.FiltroOrdenes;
import com.empresa.ordenes.domain.model.NuevaOrden;
import com.empresa.ordenes.domain.model.Orden;
import com.empresa.ordenes.domain.model.Pagina;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Optional;

/**
 * JPA para leer órdenes; JDBC para insertar, consultar catálogos y llamar al procedimiento de cambio de estado.
 * Todo corre en la transacción que abre el caso de uso.
 */
@Component
public class OrdenRepositorioOracle implements OrdenRepositorio {

    private static final Sort ORDEN_LISTADO = Sort.by(Sort.Order.desc("fechaCreacion"), Sort.Order.desc("id"));

    private final OrdenJpaRepository ordenJpaRepository;
    private final JdbcClient jdbcClient;
    private final SimpleJdbcCall cambioEstado;

    public OrdenRepositorioOracle(OrdenJpaRepository ordenJpaRepository, JdbcClient jdbcClient, DataSource dataSource) {
        this.ordenJpaRepository = ordenJpaRepository;
        this.jdbcClient = jdbcClient;
        // Parámetros declarados a mano: evita consultar los metadatos del procedimiento en cada arranque
        this.cambioEstado = new SimpleJdbcCall(dataSource)
                .withProcedureName("PRC_CAMBIO_ESTADO_ORDEN")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_id_orden", Types.NUMERIC),
                        new SqlParameter("p_id_estado_nuevo", Types.NUMERIC),
                        new SqlParameter("p_usuario", Types.VARCHAR),
                        new SqlParameter("p_observacion", Types.VARCHAR),
                        new SqlParameter("p_id_peticion", Types.VARCHAR));
    }

    @Override
    public boolean existeCliente(Long idCliente) {
        return existe("SELECT COUNT(*) FROM cliente WHERE id = ?", idCliente);
    }

    @Override
    public boolean existeTipo(Long idTipo) {
        return existe("SELECT COUNT(*) FROM tipo_orden WHERE id = ?", idTipo);
    }

    @Override
    public Optional<Long> buscarIdEstadoPorCodigo(String codigo) {
        return jdbcClient.sql("SELECT id FROM estado_orden WHERE codigo = ?")
                .param(codigo)
                .query(Long.class)
                .optional();
    }

    @Override
    public Optional<Orden> buscarPorId(Long idOrden) {
        return ordenJpaRepository.findById(idOrden).map(OrdenRepositorioOracle::aDominio);
    }

    @Override
    public Optional<Orden> buscarPorLlaveIdempotencia(String llaveIdempotencia) {
        return ordenJpaRepository.findByLlaveIdempotencia(llaveIdempotencia).map(OrdenRepositorioOracle::aDominio);
    }

    @Override
    public Orden crear(NuevaOrden nuevaOrden) {
        // Insert por JDBC y no por JPA: si falla, Hibernate no marca la transacción como rollback-only
        // y Oracle revierte solo esta sentencia, así el caso de uso puede releer la orden existente
        var llaveGenerada = new GeneratedKeyHolder();
        try {
            jdbcClient.sql("""
                            INSERT INTO orden (id_cliente, id_tipo, id_estado, canal, llave_idempotencia, usuario_creacion)
                            VALUES (:idCliente, :idTipo, :idEstado, :canal, :llaveIdempotencia, :usuario)""")
                    .param("idCliente", nuevaOrden.idCliente())
                    .param("idTipo", nuevaOrden.idTipo())
                    .param("idEstado", nuevaOrden.idEstadoInicial())
                    .param("canal", nuevaOrden.canal())
                    .param("llaveIdempotencia", nuevaOrden.llaveIdempotencia())
                    .param("usuario", nuevaOrden.usuarioCreacion())
                    .update(llaveGenerada, "ID");
        } catch (DuplicateKeyException e) {
            if (TraductorErroresOracle.esLlaveIdempotenciaDuplicada(e)) {
                throw new LlaveIdempotenciaDuplicadaException(nuevaOrden.llaveIdempotencia());
            }
            throw e;
        }
        Long idOrden = llaveGenerada.getKeyAs(Number.class).longValue();

        jdbcClient.sql("""
                        INSERT INTO orden_historico (id_orden, id_estado_anterior, id_estado_nuevo, usuario, id_peticion)
                        VALUES (:idOrden, NULL, :idEstado, :usuario, :idPeticion)""")
                .param("idOrden", idOrden)
                .param("idEstado", nuevaOrden.idEstadoInicial())
                .param("usuario", nuevaOrden.usuarioCreacion())
                .param("idPeticion", nuevaOrden.idPeticion())
                .update();

        return buscarPorId(idOrden).orElseThrow();
    }

    @Override
    public void cambiarEstado(Long idOrden, Long idEstadoNuevo, String usuario, String observacion, String idPeticion) {
        var parametros = new MapSqlParameterSource()
                .addValue("p_id_orden", idOrden)
                .addValue("p_id_estado_nuevo", idEstadoNuevo)
                .addValue("p_usuario", usuario)
                .addValue("p_observacion", observacion)
                .addValue("p_id_peticion", idPeticion);
        try {
            cambioEstado.execute(parametros);
        } catch (DataAccessException e) {
            throw TraductorErroresOracle.traducirCambioEstado(e, idOrden, idEstadoNuevo);
        }
    }

    @Override
    public Pagina<Orden> listar(FiltroOrdenes filtro) {
        var pagina = ordenJpaRepository.findAll(especificacion(filtro),
                PageRequest.of(filtro.pagina(), filtro.tamano(), ORDEN_LISTADO));
        return new Pagina<>(pagina.getContent().stream().map(OrdenRepositorioOracle::aDominio).toList(),
                pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    // Solo se agregan las condiciones presentes, para que Oracle pueda usar índices y descartar particiones
    private static Specification<OrdenEntity> especificacion(FiltroOrdenes filtro) {
        return (raiz, consulta, cb) -> {
            var condiciones = new ArrayList<Predicate>();
            if (filtro.idEstado() != null) {
                condiciones.add(cb.equal(raiz.get("idEstado"), filtro.idEstado()));
            }
            if (filtro.fechaInicio() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fechaCreacion"), filtro.fechaInicio().atStartOfDay()));
            }
            if (filtro.fechaFin() != null) {
                // fechaFin incluye todo ese día
                condiciones.add(cb.lessThan(raiz.get("fechaCreacion"), filtro.fechaFin().plusDays(1).atStartOfDay()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    private boolean existe(String sql, Long id) {
        return id != null && jdbcClient.sql(sql).param(id).query(Long.class).single() > 0;
    }

    private static Orden aDominio(OrdenEntity entidad) {
        return new Orden(entidad.getId(), entidad.getIdCliente(), entidad.getIdTipo(), entidad.getIdEstado(),
                entidad.getCanal(), entidad.getLlaveIdempotencia(), entidad.getUsuarioCreacion(),
                entidad.getFechaCreacion(), entidad.getUsuarioModificacion(), entidad.getFechaModificacion());
    }
}
