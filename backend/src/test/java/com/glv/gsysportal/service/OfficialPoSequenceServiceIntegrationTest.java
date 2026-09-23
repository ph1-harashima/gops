package com.glv.gsysportal.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-OPS Operational Workflow Realignment Phase B (docs/ux-audit/
 * gops-official-po-number-final-reality-audit.md §22's own explicit
 * "concurrency"/"duplicate prevention" test requirement): proves the real
 * atomic {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING} against a
 * real Postgres connection - not mocked - actually serializes concurrent
 * callers for the SAME Supplier x Brand pair with no duplicate/skipped
 * sequence value, while a DIFFERENT pair is unaffected. Real DB, real
 * threads, same {@code @SpringBootTest}/{@code test} profile idiom every
 * other integration test in this package already uses.
 */
@SpringBootTest
@ActiveProfiles("test")
class OfficialPoSequenceServiceIntegrationTest {

    @Autowired
    private OfficialPoSequenceService sequenceService;

    @Autowired
    private PlatformTransactionManager prototypeTransactionManager;

    /** 20 concurrent callers, same (Supplier, Brand) pair - the sequence
     * table's own PK (supplier_code, brand_code) forces Postgres to
     * serialize every one of them; if the atomic ON CONFLICT approach were
     * ever weakened back to a non-atomic MAX+1 read-then-write, this test
     * would surface duplicate/skipped values under real concurrency, not
     * just in a single-threaded unit test. */
    @Test
    void concurrentCallersForTheSamePairNeverProduceADuplicateSequence() throws Exception {
        // official_po_sequence.supplier_code/brand_code are VARCHAR(10) -
        // stay well under that with a short, still-unique-per-run suffix.
        String supplierCode = "CS" + (System.nanoTime() % 100000);
        String brandCode = "CB" + (System.nanoTime() % 100000);
        int callers = 20;

        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Callable<Integer>> tasks = IntStream.range(0, callers)
                    .<Callable<Integer>>mapToObj(i -> () -> runInOwnTransaction(() -> sequenceService.nextSequence(supplierCode, brandCode)))
                    .toList();
            List<Future<Integer>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);

            List<Integer> results = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            Set<Integer> distinct = results.stream().collect(Collectors.toSet());
            assertEquals(callers, distinct.size(), "every one of " + callers + " concurrent callers must receive a distinct sequence value: " + results);
            assertEquals(Set.copyOf(IntStream.rangeClosed(1, callers).boxed().toList()), distinct,
                    "the distinct values must be exactly 1.." + callers + " with no gap and no duplicate");
        } finally {
            executor.shutdown();
        }
    }

    /** Different Supplier x Brand pairs never block or interfere with each
     * other's own sequence - each pair's counter starts independently at 1. */
    @Test
    void differentPairsHaveIndependentSequences() {
        String supplierA = "IA" + (System.nanoTime() % 100000);
        String supplierB = "IB" + (System.nanoTime() % 100000);
        String brand = "IB2" + (System.nanoTime() % 100000);

        int firstForA = runInOwnTransaction(() -> sequenceService.nextSequence(supplierA, brand));
        int secondForA = runInOwnTransaction(() -> sequenceService.nextSequence(supplierA, brand));
        int firstForB = runInOwnTransaction(() -> sequenceService.nextSequence(supplierB, brand));

        assertEquals(1, firstForA);
        assertEquals(2, secondForA);
        assertEquals(1, firstForB, "a different Supplier's sequence for the same Brand starts at 1 independently");
    }

    private <T> T runInOwnTransaction(java.util.function.Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(prototypeTransactionManager);
        return template.execute(status -> action.get());
    }
}
