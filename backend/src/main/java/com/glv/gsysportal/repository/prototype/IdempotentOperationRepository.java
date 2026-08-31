package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.IdempotentOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Phase 8-L (Production Reliability Foundation) - see {@link IdempotentOperation}. */
public interface IdempotentOperationRepository extends JpaRepository<IdempotentOperation, Long> {
    Optional<IdempotentOperation> findByOperationTypeAndIdempotencyKey(String operationType, String idempotencyKey);
}
