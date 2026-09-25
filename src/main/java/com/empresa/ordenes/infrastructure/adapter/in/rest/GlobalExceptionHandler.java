package com.empresa.ordenes.infrastructure.adapter.in.rest;

import com.empresa.ordenes.domain.exception.DatosInvalidosException;
import com.empresa.ordenes.domain.exception.OrdenBloqueadaException;
import com.empresa.ordenes.domain.exception.OrdenNoEncontradaException;
import com.empresa.ordenes.domain.exception.ReferenciaInexistenteException;
import com.empresa.ordenes.domain.exception.TransicionInvalidaException;
import com.empresa.ordenes.infrastructure.adapter.in.rest.dto.ErrorDto;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Toda respuesta de error usa el esquema Error del contrato. Nunca se exponen trazas ni mensajes de Oracle.
 * Extiende ResponseEntityExceptionHandler para cubrir también los errores propios de Spring MVC.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";

    @ExceptionHandler(DatosInvalidosException.class)
    ResponseEntity<ErrorDto> datosInvalidos(DatosInvalidosException e) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, e.getMessage());
    }

    @ExceptionHandler(ReferenciaInexistenteException.class)
    ResponseEntity<ErrorDto> referenciaInexistente(ReferenciaInexistenteException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "REFERENCIA_INEXISTENTE", e.getMessage());
    }

    @ExceptionHandler(OrdenNoEncontradaException.class)
    ResponseEntity<ErrorDto> ordenNoEncontrada(OrdenNoEncontradaException e) {
        return respuesta(HttpStatus.NOT_FOUND, "ORDEN_NO_ENCONTRADA", e.getMessage());
    }

    @ExceptionHandler(OrdenBloqueadaException.class)
    ResponseEntity<ErrorDto> ordenBloqueada(OrdenBloqueadaException e) {
        return respuesta(HttpStatus.CONFLICT, "ORDEN_BLOQUEADA", e.getMessage());
    }

    @ExceptionHandler(TransicionInvalidaException.class)
    ResponseEntity<ErrorDto> transicionInvalida(TransicionInvalidaException e) {
        return respuesta(HttpStatus.UNPROCESSABLE_CONTENT, "TRANSICION_INVALIDA", e.getMessage());
    }

    // Validación de parámetros hecha por el proxy de @Validated de la interfaz generada
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorDto> restriccionIncumplida(ConstraintViolationException e) {
        var detalle = e.getConstraintViolations().stream()
                .map(violacion -> ultimoNodo(violacion.getPropertyPath().toString()) + ": " + violacion.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, detalle);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorDto> errorInesperado(Exception e) {
        log.error("Error no controlado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error interno, intente más tarde");
    }

    // Punto único por el que pasan los errores de Spring MVC (validación, JSON inválido, 404, 405, 415...)
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object cuerpo, HttpHeaders headers,
                                                             HttpStatusCode estado, WebRequest peticion) {
        if (estado.is5xxServerError()) {
            log.error("Error de Spring MVC", e);
            return new ResponseEntity<>(OrdenRestMapper.error("ERROR_INTERNO",
                    "Ocurrió un error interno, intente más tarde"), headers, estado);
        }
        var error = switch (estado.value()) {
            case 400 -> OrdenRestMapper.error(DATOS_INVALIDOS, mensajeDeValidacion(e));
            case 404 -> OrdenRestMapper.error("RECURSO_NO_ENCONTRADO", "El recurso solicitado no existe");
            case 405 -> OrdenRestMapper.error("SOLICITUD_NO_SOPORTADA", "El método HTTP no está permitido para este recurso");
            case 406 -> OrdenRestMapper.error("SOLICITUD_NO_SOPORTADA", "El formato de respuesta solicitado no está soportado");
            case 415 -> OrdenRestMapper.error("SOLICITUD_NO_SOPORTADA", "El tipo de contenido no está soportado");
            default -> OrdenRestMapper.error("SOLICITUD_NO_SOPORTADA", "La solicitud no está soportada");
        };
        return new ResponseEntity<>(error, headers, estado);
    }

    private static String mensajeDeValidacion(Exception e) {
        return switch (e) {
            case MethodArgumentNotValidException invalido -> invalido.getFieldErrors().stream()
                    .map(campo -> campo.getField() + ": " + campo.getDefaultMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            case HandlerMethodValidationException invalido -> invalido.getParameterValidationResults().stream()
                    .flatMap(GlobalExceptionHandler::mensajes)
                    .sorted()
                    .collect(Collectors.joining("; "));
            case TypeMismatchException tipo -> "El valor del parámetro '" + tipo.getPropertyName() + "' no es válido";
            default -> "La solicitud no es válida: revise el cuerpo y los parámetros";
        };
    }

    private static Stream<String> mensajes(ParameterValidationResult resultado) {
        var parametro = resultado.getMethodParameter().getParameterName();
        return resultado.getResolvableErrors().stream().map(error -> parametro + ": " + error.getDefaultMessage());
    }

    private static String ultimoNodo(String ruta) {
        return ruta.substring(ruta.lastIndexOf('.') + 1);
    }

    private static ResponseEntity<ErrorDto> respuesta(HttpStatus estado, String codigo, String mensaje) {
        return ResponseEntity.status(estado).body(OrdenRestMapper.error(codigo, mensaje));
    }
}
