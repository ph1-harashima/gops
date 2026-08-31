package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.IdempotentOperation;
import com.glv.gsysportal.repository.prototype.IdempotentOperationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §11-16): Technical Idempotency Foundation. Not wired to any real External
 * Side Effect this Phase - no Official PO Handoff/Email/EDI Send exists yet
 * to protect (7-C2B/7-C4/EDI real integration are all still unimplemented,
 * Production Readiness Audit §4-6). A future Phase implementing one of
 * those calls {@link #claim} before performing the side effect, and {@link
 * #markSucceeded}/{@link #markFailed} afterward.
 *
 * <p>Retry is a Technical Mechanism only (§16): a FAILED claim MAY be
 * re-claimed (attempt count incremented), but nothing here decides
 * automatically WHEN/whether to retry, how many times, or at what interval
 * - those remain Production Operation decisions this Phase does not make.
 *
 * <p><b>Why {@link TransactionTemplate} instead of {@code @Transactional}</b>:
 * when the claim attempt loses a concurrent race, the DB throws a
 * constraint/optimistic-lock violation and Hibernate marks that
 * transaction's persistence context unusable for anything further ("don't
 * flush the Session after an exception occurs") - so the follow-up "find
 * what the winner wrote" read MUST run in a genuinely separate transaction.
 * A plain {@code @Transactional} method calling a {@code private} sibling
 * cannot do this (Spring AOP self-invocation does not open a new
 * transaction); explicit {@link TransactionTemplate#execute} with {@code
 * PROPAGATION_REQUIRES_NEW} does.
 */
@Service
public class IdempotencyService {

    private final IdempotentOperationRepository repository;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyService(IdempotentOperationRepository repository,
                               @Qualifier("prototypeTransactionManager") PlatformTransactionManager prototypeTransactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(prototypeTransactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Attempts to claim {@code idempotencyKey} for {@code operationType}.
     *
     * @return a claim that is either new (caller should proceed with the
     *         side effect) or a duplicate (caller must NOT proceed - either
     *         another attempt is in flight ({@code STARTED}), already
     *         succeeded ({@code SUCCEEDED}), or a concurrent caller won a
     *         race on this exact call). A previously {@code FAILED} attempt
     *         is reclaimed (attempt count incremented) rather than treated
     *         as a duplicate.
     */
    public IdempotencyClaim claim(String operationType, String businessKey, String idempotencyKey) {
        try {
            return transactionTemplate.execute(status -> attemptClaim(operationType, businessKey, idempotencyKey));
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            // Lost a genuine concurrent race (INSERT UNIQUE-constraint
            // violation, or a lost retry-after-FAILED optimistic-lock
            // update) - Production Readiness Audit §15's explicit
            // requirement that the DB constraint, not a plain
            // SELECT-then-INSERT check, is what actually prevents the
            // duplicate. Read what the winner wrote in a FRESH transaction.
            IdempotentOperation winner = transactionTemplate.execute(status ->
                    repository.findByOperationTypeAndIdempotencyKey(operationType, idempotencyKey).orElse(null));
            if (winner == null) {
                throw e;
            }
            return new IdempotencyClaim(winner, false);
        }
    }

    private IdempotencyClaim attemptClaim(String operationType, String businessKey, String idempotencyKey) {
        Optional<IdempotentOperation> existing = repository.findByOperationTypeAndIdempotencyKey(operationType, idempotencyKey);
        if (existing.isPresent()) {
            IdempotentOperation op = existing.get();
            if (IdempotentOperation.STATUS_FAILED.equals(op.getStatus())) {
                op.setStatus(IdempotentOperation.STATUS_STARTED);
                op.setAttempt(op.getAttempt() + 1);
                op.setErrorCode(null);
                op.setUpdatedAt(OffsetDateTime.now());
                IdempotentOperation retried = repository.saveAndFlush(op);
                return new IdempotencyClaim(retried, true);
            }
            return new IdempotencyClaim(op, false);
        }
        IdempotentOperation created = repository.saveAndFlush(
                new IdempotentOperation(operationType, businessKey, idempotencyKey, OffsetDateTime.now()));
        return new IdempotencyClaim(created, true);
    }

    public void markSucceeded(Long id) {
        transactionTemplate.executeWithoutResult(status ->
                repository.findById(id).ifPresent(op -> {
                    op.setStatus(IdempotentOperation.STATUS_SUCCEEDED);
                    op.setUpdatedAt(OffsetDateTime.now());
                }));
    }

    public void markFailed(Long id, String errorCode) {
        transactionTemplate.executeWithoutResult(status ->
                repository.findById(id).ifPresent(op -> {
                    op.setStatus(IdempotentOperation.STATUS_FAILED);
                    op.setErrorCode(errorCode);
                    op.setUpdatedAt(OffsetDateTime.now());
                }));
    }

    /** @param claimed true if the caller may proceed with the side effect (a
     *                 genuinely new or retried attempt); false if this is a
     *                 duplicate - something already claimed/finished this
     *                 key and the caller must not repeat the side effect. */
    public record IdempotencyClaim(IdempotentOperation operation, boolean claimed) {
    }
}
