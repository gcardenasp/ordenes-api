package com.empresa.ordenes.domain.model;

import java.util.List;
import java.util.function.Function;

public record Pagina<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    public <R> Pagina<R> map(Function<T, R> transformacion) {
        return new Pagina<>(contenido.stream().map(transformacion).toList(),
                pagina, tamano, totalElementos, totalPaginas);
    }
}
