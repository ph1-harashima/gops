package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.IdempotentOperation;
import com.glv.gsysportal.repository.prototype.IdempotentOperationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §11-16/§23). No class-level test-transaction rollback wrapper (Propagation.
 * NOT_SUPPORTED) - each {@code claim()} call, including ones from background
 * threads in the concurrency test, must commit against the real Postgres DB
 * for the DB UNIQUE constraint race to be genuinely exercised across
 * separate connections/transactions, not masked by a shared rolled-back
 * test transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class IdempotencyServiceTest {

    private static final String OP_TYPE = "TEST_OPERATION_8L";

    @Autowired
    private IdempotencyService idempotencyService;
    @Autowired
    private IdempotentOperationRepository repository;

    @AfterEach
    void cleanUp() {
        repository.findAll().stream()
                .filter(op -> OP_TYPE.equals(op.getOperationType()))
                .forEach(op -> repository.deleteById(op.getId()));
    }

    @Test
    void firstClaimSucceedsWithStartedStatus() {
        var claim = idempotencyService.claim(OP_TYPE, "biz-1", "key-1");
        assertTrue(claim.claimed());
        assertEquals(IdempotentOperation.STATUS_STARTED, claim.operation().getStatus());
        assertEquals(1, claim.operation().getAttempt());
    }

    @Test
    void secondClaimWhileStartedIsRejectedAsDuplicate() {
        idempotencyService.claim(OP_TYPE, "biz-2", "key-2");
        var second = idempotencyService.claim(OP_TYPE, "biz-2", "key-2");
        assertFalse(second.claimed());
    }

    @Test
    void claimAfterSucceededIsRejectedAsDuplicate() {
        var first = idempotencyService.claim(OP_TYPE, "biz-3", "key-3");
        idempotencyService.markSucceeded(first.operation().getId());
        var second = idempotencyService.claim(OP_TYPE, "biz-3", "key-3");
        assertFalse(second.claimed());
        assertEquals(IdempotentOperation.STATUS_SUCCEEDED, second.operation().getStatus());
    }

    /** §16 Retry Foundation: a Technical Mechanism only - reclaiming a
     * FAILED attempt is allowed (attempt incremented), but nothing decides
     * automatically whether/when this should happen. */
    @Test
    void claimAfterFailedIsAllowedAsARetryWithIncrementedAttempt() {
        var first = idempotencyService.claim(OP_TYPE, "biz-4", "key-4");
        idempotencyService.markFailed(first.operation().getId(), "SOME_ERROR");
        var retry = idempotencyService.claim(OP_TYPE, "biz-4", "key-4");
        assertTrue(retry.claimed());
        assertEquals(2, retry.operation().getAttempt());
        assertEquals(IdempotentOperation.STATUS_STARTED, retry.operation().getStatus());
    }

    /** §15 Duplicate Prevention / §23 "Concurrent duplicate": 8 threads race
     * to claim the exact same key at (as close as possible to) the same
     * instant - exactly one must win, and exactly one row must ever exist,
     * proving the DB UNIQUE constraint (not a SELECT-then-INSERT check
     * alone) is what actually prevents the duplicate. */
    @Test
    void concurrentClaimsForTheSameKeyOnlyOneSucceeds() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger claimedCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    var claim = idempotencyService.claim(OP_TYPE, "biz-concurrent", "key-concurrent");
                    if (claim.claimed()) {
                        claimedCount.incrementAndGet();
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertEquals(1, claimedCount.get(), "exactly one concurrent attempt should win the claim");
        long rowCount = repository.findAll().stream()
                .filter(op -> "key-concurrent".equals(op.getIdempotencyKey()) && OP_TYPE.equals(op.getOperationType()))
                .count();
        assertEquals(1, rowCount, "no duplicate row should ever be created");
    }
}
