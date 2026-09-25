package com.empresa.ordenes.infrastructure.adapter.out.oracle;

import com.empresa.ordenes.domain.exception.DatosInvalidosException;
import com.empresa.ordenes.domain.exception.OrdenBloqueadaException;
import com.empresa.ordenes.domain.exception.OrdenNoEncontradaException;
import com.empresa.ordenes.domain.exception.TransicionInvalidaException;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Traduce los errores de Oracle a excepciones de dominio. Los mensajes se construyen aquí:
 * el texto original de Oracle nunca llega al cliente.
 */
final class TraductorErroresOracle {

    private static final int VIOLACION_UNIQUE = 1;
    private static final String UK_LLAVE_IDEMPOTENCIA = "UK_ORDEN_LLAVE_IDEMPOTENCIA";

    private TraductorErroresOracle() {
    }

    /**
     * Excepción de dominio para los errores -20001 a -20004 de prc_cambio_estado_orden;
     * cualquier otro error se retorna sin cambios.
     */
    static RuntimeException traducirCambioEstado(RuntimeException error, Long idOrden, Long idEstadoNuevo) {
        return buscarSqlException(error)
                .map(sql -> switch (sql.getErrorCode()) {
                    case 20001 -> new DatosInvalidosException("La orden, el estado nuevo y el usuario son obligatorios");
                    case 20002 -> new OrdenNoEncontradaException(idOrden);
                    case 20003 -> new TransicionInvalidaException(idOrden, idEstadoNuevo);
                    case 20004 -> new OrdenBloqueadaException(idOrden);
                    default -> error;
                })
                .orElse(error);
    }

    static boolean esLlaveIdempotenciaDuplicada(Throwable error) {
        return buscarSqlException(error)
                .filter(sql -> sql.getErrorCode() == VIOLACION_UNIQUE)
                .filter(sql -> sql.getMessage() != null && sql.getMessage().toUpperCase().contains(UK_LLAVE_IDEMPOTENCIA))
                .isPresent();
    }

    // Spring y Hibernate envuelven la SQLException del driver, a veces dentro de otra SQLException
    // sin código; se busca en la cadena de causas la primera que traiga el código de Oracle
    private static Optional<SQLException> buscarSqlException(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof SQLException sql && sql.getErrorCode() != 0) {
                return Optional.of(sql);
            }
        }
        return Optional.empty();
    }
}
