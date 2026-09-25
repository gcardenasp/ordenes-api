package com.empresa.ordenes.infrastructure.adapter.out.oracle;

import com.empresa.ordenes.domain.exception.DatosInvalidosException;
import com.empresa.ordenes.domain.exception.OrdenBloqueadaException;
import com.empresa.ordenes.domain.exception.OrdenNoEncontradaException;
import com.empresa.ordenes.domain.exception.TransicionInvalidaException;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TraductorErroresOracleTest {

    private static final Long ID_ORDEN = 10L;
    private static final Long ID_ESTADO = 3L;

    static Stream<Arguments> erroresDelProcedimiento() {
        return Stream.of(
                Arguments.of(20001, DatosInvalidosException.class),
                Arguments.of(20002, OrdenNoEncontradaException.class),
                Arguments.of(20003, TransicionInvalidaException.class),
                Arguments.of(20004, OrdenBloqueadaException.class));
    }

    @ParameterizedTest
    @MethodSource("erroresDelProcedimiento")
    void traduceCadaCodigoDelProcedimientoASuExcepcionDeDominio(int codigo, Class<? extends RuntimeException> esperada) {
        var error = errorDeSpring(new SQLException("ORA-" + codigo + ": detalle interno de Oracle", "72000", codigo));

        RuntimeException traducida = TraductorErroresOracle.traducirCambioEstado(error, ID_ORDEN, ID_ESTADO);

        assertThat(traducida).isInstanceOf(esperada);
        assertThat(traducida.getMessage()).doesNotContain("ORA-");
    }

    @Test
    void usaLosDatosDeLaPeticionEnElMensaje() {
        var error = errorDeSpring(new SQLException("ORA-20003", "72000", 20003));

        assertThat(TraductorErroresOracle.traducirCambioEstado(error, ID_ORDEN, ID_ESTADO))
                .hasMessage("La orden 10 no puede pasar al estado 3 desde su estado actual");
    }

    @Test
    void encuentraElCodigoAunqueLaSqlExceptionEsteAnidada() {
        var anidada = new RuntimeException("envoltura", new SQLException("ORA-20002", "72000", 20002));

        assertThat(TraductorErroresOracle.traducirCambioEstado(errorDeSpring(anidada), ID_ORDEN, ID_ESTADO))
                .isInstanceOf(OrdenNoEncontradaException.class);
    }

    @Test
    void retornaElErrorOriginalSiNoEsDelProcedimiento() {
        var error = errorDeSpring(new SQLException("ORA-00942: table or view does not exist", "42000", 942));

        assertThat(TraductorErroresOracle.traducirCambioEstado(error, ID_ORDEN, ID_ESTADO)).isSameAs(error);
    }

    @Test
    void retornaElErrorOriginalSiNoHaySqlException() {
        var error = new IllegalStateException("sin causa SQL");

        assertThat(TraductorErroresOracle.traducirCambioEstado(error, ID_ORDEN, ID_ESTADO)).isSameAs(error);
    }

    @Test
    void reconoceLaViolacionDeLaLlaveDeIdempotencia() {
        var sql = new SQLException("ORA-00001: unique constraint (ORDENES.UK_ORDEN_LLAVE_IDEMPOTENCIA) violated", "23000", 1);

        assertThat(TraductorErroresOracle.esLlaveIdempotenciaDuplicada(new PersistenceException("insert", sql))).isTrue();
    }

    @Test
    void noConfundeOtraViolacionUniqueConLaDeLaLlave() {
        var sql = new SQLException("ORA-00001: unique constraint (ORDENES.PK_ORDEN) violated", "23000", 1);

        assertThat(TraductorErroresOracle.esLlaveIdempotenciaDuplicada(new PersistenceException("insert", sql))).isFalse();
    }

    private static UncategorizedSQLException errorDeSpring(Throwable causa) {
        var sql = causa instanceof SQLException s ? s : new SQLException("envoltura", causa);
        return new UncategorizedSQLException("llamada al procedimiento", "{call PRC_CAMBIO_ESTADO_ORDEN}", sql);
    }
}
