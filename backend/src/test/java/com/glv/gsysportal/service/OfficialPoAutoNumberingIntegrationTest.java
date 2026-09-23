package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.OfficialPoShortCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): Official PO
 * No. auto-numbering - the actual sequence/Concurrency Control behavior,
 * exercised directly against {@link OfficialPoSequenceService}/
 * {@link OfficialPoNumberGenerator} rather than through a full Order
 * lifecycle (the composition rule itself is covered separately by the pure
 * Mockito unit test {@link OfficialPoNumberGeneratorTest}; the "wired into a
 * real Order" case is covered by
 * {@code OfficialPoNumberAndExcelGenerationIntegrationTest.requestIntegrationAutoNumbersImmediately}
 * and Reissue's own PO-No-preservation case by
 * {@code OfficialPoReissueIntegrationTest.fullReissueCycle_...}).
 *
 * <p>Uses the Demo/Test fixture Short Codes seeded by V30
 * (SUP_ALPHA=ALP/SUP_BETA=BET/SUP_GAMMA=GAM, BR_OUTDOOR=OUT/BR_HOME=HOM/
 * BR_KITCHEN=KIT) - these are real, already-registered Master rows, so no
 * per-test Short Code setup is needed.
 */
@SpringBootTest
@ActiveProfiles("test")
class OfficialPoAutoNumberingIntegrationTest {

    @Autowired
    private OfficialPoSequenceService sequenceService;
    @Autowired
    private OfficialPoNumberGenerator numberGenerator;
    @PersistenceContext
    private EntityManager entityManager;

    /** BR-08 as reconciled by docs/ux-audit/
     * gops-official-po-number-final-reality-audit.md: the format is always
     * exactly {@code #}{SupplierShortCode 3 chars}{@code -}{BrandShortCode
     * 3 chars}{3-digit sequence} - the Legacy-compatible shape, not the
     * no-separator shape this class used to document. */
    @Test
    @Transactional(transactionManager = "prototypeTransactionManager")
    void generateProducesTheExactLegacyCompatibleFormat() {
        String poNo = numberGenerator.generate("SUP_ALPHA", "BR_OUTDOOR");

        assertTrue(poNo.matches("^#[A-Z]{3}-[A-Z]{3}\\d{3}$"), "unexpected format: " + poNo);
        assertTrue(poNo.startsWith("#ALP-OUT"), "must use the registered Short Codes verbatim: " + poNo);
    }

    /** BR-08 §8/Scenario 5: repeated allocations for the SAME Supplier x
     * Brand pair strictly increment by 1, one at a time - never repeat a
     * value within this sequential run. */
    @Test
    @Transactional(transactionManager = "prototypeTransactionManager")
    void sequenceIncrementsByOneForTheSameSupplierBrandPair() {
        int first = sequenceService.nextSequence("SUP_ALPHA", "BR_OUTDOOR");
        int second = sequenceService.nextSequence("SUP_ALPHA", "BR_OUTDOOR");
        int third = sequenceService.nextSequence("SUP_ALPHA", "BR_OUTDOOR");

        assertEquals(first + 1, second);
        assertEquals(second + 1, third);
    }

    /** BR-08 Scenario 6: ABC x XYZ's sequence and ABC x DEF's sequence are
     * completely independent counters - allocating for one pair must never
     * consume or perturb the other pair's counter. */
    @Test
    @Transactional(transactionManager = "prototypeTransactionManager")
    void sequenceIsIndependentPerSupplierBrandPair() {
        int alphaOutdoorBefore = sequenceService.nextSequence("SUP_ALPHA", "BR_OUTDOOR");
        int alphaHomeBefore = sequenceService.nextSequence("SUP_ALPHA", "BR_HOME");

        int alphaOutdoorAfter = sequenceService.nextSequence("SUP_ALPHA", "BR_OUTDOOR");
        int alphaHomeAfter = sequenceService.nextSequence("SUP_ALPHA", "BR_HOME");
        int betaOutdoorAfter = sequenceService.nextSequence("SUP_BETA", "BR_OUTDOOR");

        assertEquals(alphaOutdoorBefore + 1, alphaOutdoorAfter, "SUP_ALPHA x BR_OUTDOOR's own counter must be untouched by the other pairs' calls");
        assertEquals(alphaHomeBefore + 1, alphaHomeAfter, "SUP_ALPHA x BR_HOME's own counter must be untouched by the other pairs' calls");
        assertTrue(betaOutdoorAfter >= 1, "SUP_BETA x BR_OUTDOOR is its own independent counter, starting from whatever it already was");
    }

    /** BR-08 §8/Scenario 5: "同一Supplier×Brandについて複数Userが同時に
     * Official POを作成しても、同じSequence Numberが発行されない" - 16 threads
     * race to allocate for the exact SAME (SUP_GAMMA, BR_KITCHEN) pair at
     * (as close as possible to) the same instant; every returned value must
     * be distinct, proving the atomic {@code INSERT ... ON CONFLICT DO
     * UPDATE ... RETURNING} (never a bare "read current value, add 1, write
     * back" race). Runs outside the class's (non-existent, deliberately)
     * ambient transaction - propagation NOT_SUPPORTED per thread, mirroring
     * {@code IdempotencyServiceTest}'s own concurrency idiom, so each thread
     * genuinely opens its own DB transaction rather than sharing one
     * EntityManager/connection across threads. */
    @Test
    @Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    void concurrentAllocationsForTheSameSupplierBrandPairNeverCollide() throws Exception {
        int threadCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Integer> results = new CopyOnWriteArrayList<>();
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
                    results.add(sequenceService.nextSequence("SUP_GAMMA", "BR_KITCHEN"));
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

        assertEquals(threadCount, results.size());
        assertEquals(threadCount, Set.copyOf(results).size(),
                "every concurrently-allocated sequence value must be distinct - no duplicate PO Sequence Number");
    }

    /** Post-Freeze Technical Stability Audit
     * (docs/gops-post-freeze-e2e-stability-audit.md §16/§34) - a
     * characterization test, not a behavior fix: {@code String.format("%03d", seq)}
     * does not truncate or wrap once {@code seq} exceeds 999 - it simply
     * widens to 4+ digits, silently breaking BR-08's documented fixed
     * "3-digit sequence" format (and this class's own
     * {@code generateProducesTheExactLegacyCompatibleFormat} regex,
     * {@code ^#[A-Z]{3}-[A-Z]{3}\d{3}$}). No collision results (the counter
     * itself is still correct and monotonic - see
     * {@code concurrentAllocationsForTheSameSupplierBrandPairNeverCollide}
     * above), but any downstream system assuming a fixed 9-character
     * Official PO No. would start receiving 10+ character values. This is
     * NOT fixed here - the numbering rule itself is explicitly out of this
     * audit's scope - it is recorded so the eventual UAT Real Data Audit
     * checks whether any real Supplier x Brand pair is already near or
     * past 999 cumulative Official POs. Uses a synthetic Supplier/Brand
     * pair with its own dedicated Short Code fixture rows (cleaned up
     * after) so it never perturbs another test's counter. */
    @Test
    @Transactional(transactionManager = "prototypeTransactionManager")
    void sequenceExceeding999WidensPastTheDocumented3DigitFormat() {
        String supplierCode = "STABAUD_SU";
        String brandCode = "STABAUD_BR";
        insertShortCodeFixture(OfficialPoShortCode.TYPE_SUPPLIER, supplierCode, "ZZZ");
        insertShortCodeFixture(OfficialPoShortCode.TYPE_BRAND, brandCode, "ZZZ");

        // The pair's counter starts at 1 on its first ever allocation, so
        // 998 direct calls here leave it AT 998; the next allocation
        // (via numberGenerator.generate() below) is the one that returns
        // 999 - still within the documented 3-digit format.
        for (int i = 0; i < 998; i++) {
            sequenceService.nextSequence(supplierCode, brandCode);
        }
        String at999 = numberGenerator.generate(supplierCode, brandCode);
        assertTrue(at999.matches("^#[A-Z]{3}-[A-Z]{3}\\d{3}$"), "the 1000th call (seq=1000) is the one that overflows, not this one: " + at999);

        String at1000 = numberGenerator.generate(supplierCode, brandCode);
        assertEquals("#ZZZ-ZZZ1000", at1000,
                "documents the actual current behavior: seq=1000 widens to a 4-digit tail " +
                "(\"#ZZZ-ZZZ1000\", 12 characters) rather than truncating/wrapping/erroring - " +
                "a real constraint to verify against actual UAT PO volume per Supplier x Brand pair, " +
                "not something this audit changes (business numbering rule is out of scope).");
        assertTrue(!at1000.matches("^#[A-Z]{3}-[A-Z]{3}\\d{3}$"),
                "confirms this value would now fail BR-08's own documented fixed-format regex");
    }

    private void insertShortCodeFixture(String codeType, String businessCode, String shortCode) {
        entityManager.createNativeQuery(
                "INSERT INTO official_po_short_code (code_type, business_code, short_code, is_active, created_by, created_at, updated_by, updated_at) " +
                        "VALUES (:codeType, :businessCode, :shortCode, true, 'stability-audit', now(), 'stability-audit', now())")
                .setParameter("codeType", codeType)
                .setParameter("businessCode", businessCode)
                .setParameter("shortCode", shortCode)
                .executeUpdate();
    }
}
