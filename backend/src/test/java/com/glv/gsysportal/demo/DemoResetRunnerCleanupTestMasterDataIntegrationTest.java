package com.glv.gsysportal.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 1 Final Cleanup (Test Data Lifecycle) regression suite for
 * {@link DemoResetRunner#cleanupTestMasterData()} - maps directly to the
 * A/B/C cleanup regression scenarios (docs/gops-phase1-final-cleanup-report.md):
 * <ul>
 *   <li>A: a row matching a 100%-confident Test marker is deleted.</li>
 *   <li>B: a row that merely LOOKS like it could be Test data, but does not
 *       match any confident marker (ambiguous name, non-test email domain),
 *       survives - "曖昧な条件によるDELETEは禁止".</li>
 *   <li>C: an Active row is never deleted, even if it otherwise matches a
 *       marker pattern - Active Demo/Production-like Master data must
 *       survive Cleanup.</li>
 * </ul>
 * D (Production Profile refusal) and E (Legacy DB untouched) are not
 * re-tested here: D is the exact same {@code validateJdbcUrl} guard already
 * covered by {@link DemoResetRunnerTest}, which {@link
 * DemoResetRunner#cleanupTestMasterData()} calls unconditionally before any
 * DELETE; E holds by construction - this class never references the Legacy
 * DataSource at all (only {@code prototypeDataSource}/{@code prototypeJdbc}).
 *
 * <p>{@code @Transactional} rolls every inserted fixture row (and the
 * DELETEs {@code cleanupTestMasterData()} issues against them) back at the
 * end of each test - this suite never leaves rows behind in the shared
 * local Prototype DB.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class DemoResetRunnerCleanupTestMasterDataIntegrationTest {

    @Autowired
    private DemoResetRunner demoResetRunner;

    @Autowired
    @Qualifier("prototypeDataSource")
    private DataSource prototypeDataSource;

    private JdbcTemplate jdbc;

    private JdbcTemplate jdbc() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(prototypeDataSource);
        }
        return jdbc;
    }

    private void insertSupplierContact(String email, boolean active) {
        jdbc().update("INSERT INTO supplier_contact "
                        + "(supplier_code, contact_name, email, contact_type, language, is_active, created_by, updated_by) "
                        + "VALUES (?, ?, ?, 'TO', 'ja', ?, 'tester01', 'tester01')",
                "CLNTEST", "Cleanup Test Contact", email, active);
    }

    private void insertMailTemplate(String templateName, boolean active) {
        jdbc().update("INSERT INTO mail_template "
                        + "(template_name, template_type, language, subject_template, body_template, is_active, created_by, updated_by) "
                        + "VALUES (?, 'PURCHASE_ORDER', 'ja', 'subject', 'body', ?, 'tester01', 'tester01')",
                templateName, active);
    }

    private int countSupplierContactByEmail(String email) {
        Integer count = jdbc().queryForObject(
                "SELECT count(*) FROM supplier_contact WHERE email = ?", Integer.class, email);
        return count == null ? 0 : count;
    }

    private int countMailTemplateByName(String templateName) {
        Integer count = jdbc().queryForObject(
                "SELECT count(*) FROM mail_template WHERE template_name = ?", Integer.class, templateName);
        return count == null ? 0 : count;
    }

    // --- A: 100%-identifiable Test rows are deleted ---------------------

    @Test
    void deletesInactiveExampleDomainSupplierContact() throws Exception {
        String email = "cleanup-regression-a@example.com";
        insertSupplierContact(email, false);

        demoResetRunner.cleanupTestMasterData();

        assertEquals(0, countSupplierContactByEmail(email));
    }

    @Test
    void deletesInactiveFollowUpE2EMailTemplate() throws Exception {
        String name = "Follow-up E2E Template 999999999999";
        insertMailTemplate(name, false);

        demoResetRunner.cleanupTestMasterData();

        assertEquals(0, countMailTemplateByName(name));
    }

    /** Final E2E Remediation (docs/gops-final-e2e-failure-root-cause-analysis.md
     * §12-13): every E2E spec that dynamically creates a Mail Template now
     * names it with an "E2E " prefix (e.g. mobile-responsive.spec.ts's
     * "E2E Mobile M6 Template", email-send.spec.ts's "E2E PO Template") -
     * this second, broader marker closes the gap where only 2 of 201
     * accumulated mail_template rows matched the original, narrower
     * "Follow-up E2E Template %" pattern. Uses a distinctive numeric suffix
     * (like the "Follow-up E2E Template" tests above), never the literal
     * names actual E2E specs use, so this fixture can never collide with a
     * real, already-committed row of the same name from an actual E2E run
     * against this same shared local DB. */
    @Test
    void deletesInactiveE2EPrefixedMailTemplate() throws Exception {
        String name = "E2E Cleanup Regression Template 999999999999";
        insertMailTemplate(name, false);

        demoResetRunner.cleanupTestMasterData();

        assertEquals(0, countMailTemplateByName(name));
    }

    // --- B: ambiguous rows (no confident marker) survive -----------------

    @Test
    void keepsInactiveNonExampleDomainSupplierContact() throws Exception {
        String email = "cleanup-regression-b@real-supplier.co.jp";
        insertSupplierContact(email, false);

        demoResetRunner.cleanupTestMasterData();

        assertEquals(1, countSupplierContactByEmail(email));
    }

    @Test
    void keepsInactiveAmbiguouslyNamedMailTemplate() throws Exception {
        String name = "Cleanup Regression B Ambiguous Template";
        insertMailTemplate(name, false);

        demoResetRunner.cleanupTestMasterData();

        assertEquals(1, countMailTemplateByName(name));
    }

    // --- C: Active rows are never deleted, even if otherwise matching ----

    @Test
    void keepsActiveExampleDomainSupplierContact() throws Exception {
        String email = "cleanup-regression-c@example.com";
        insertSupplierContact(email, true);

        demoResetRunner.cleanupTestMasterData();

        assertEquals(1, countSupplierContactByEmail(email));
    }

    @Test
    void keepsActiveFollowUpE2EMailTemplate() throws Exception {
        String name = "Follow-up E2E Template 888888888888";
        insertMailTemplate(name, true);

        demoResetRunner.cleanupTestMasterData();

        assertEquals(1, countMailTemplateByName(name));
    }

    @Test
    void keepsActiveE2EPrefixedMailTemplate() throws Exception {
        String name = "E2E Cleanup Regression Template 888888888888";
        insertMailTemplate(name, true);

        demoResetRunner.cleanupTestMasterData();

        assertEquals(1, countMailTemplateByName(name));
    }
}
