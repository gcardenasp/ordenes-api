package com.empresa.ordenes.infrastructure.adapter.out.oracle;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.Optional;

// Extiende Repository y no JpaRepository: sin save ni delete, nadie puede actualizar una orden por JPA
interface OrdenJpaRepository extends Repository<OrdenEntity, Long>, JpaSpecificationExecutor<OrdenEntity> {

    Optional<OrdenEntity> findById(Long id);

    Optional<OrdenEntity> findByLlaveIdempotencia(String llaveIdempotencia);
}
