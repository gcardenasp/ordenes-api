package com.empresa.ordenes.infrastructure.adapter.out.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Solo lectura. La orden se inserta por JDBC (ver OrdenRepositorioOracle.crear) y su estado
 * solo lo cambia prc_cambio_estado_orden; @Immutable impide que Hibernate emita un UPDATE.
 */
@Entity
@Immutable
@Table(name = "ORDEN")
public class OrdenEntity {

    @Id
    private Long id;

    @Column(name = "ID_CLIENTE", nullable = false)
    private Long idCliente;

    @Column(name = "ID_TIPO", nullable = false)
    private Long idTipo;

    @Column(name = "ID_ESTADO", nullable = false)
    private Long idEstado;

    @Column(name = "CANAL", nullable = false, length = 30)
    private String canal;

    @Column(name = "LLAVE_IDEMPOTENCIA", nullable = false, length = 100)
    private String llaveIdempotencia;

    @Column(name = "USUARIO_CREACION", nullable = false, length = 100)
    private String usuarioCreacion;

    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "USUARIO_MODIFICACION", length = 100)
    private String usuarioModificacion;

    @Column(name = "FECHA_MODIFICACION")
    private LocalDateTime fechaModificacion;

    protected OrdenEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public Long getIdTipo() {
        return idTipo;
    }

    public Long getIdEstado() {
        return idEstado;
    }

    public String getCanal() {
        return canal;
    }

    public String getLlaveIdempotencia() {
        return llaveIdempotencia;
    }

    public String getUsuarioCreacion() {
        return usuarioCreacion;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public String getUsuarioModificacion() {
        return usuarioModificacion;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }
}
