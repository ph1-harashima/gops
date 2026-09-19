# G-OPS Production Readiness Audit

**Status**: Audit / Planning only. Read-only. 0 code changes, 0 Production connections, 0 Legacy writes, 0 real Email/EDI/Import Folder access, SafetyGuard unchanged.

**Baseline**: Phase1 Freeze (`docs/gops-phase1-freeze.md`, Final Commit `71ad25d`). Backend 594/594, Frontend 51/51, Full E2E 212/212, Mobile 11/11, Legacy WRITE 0, Production Connection 0 — this Freeze state is unchanged by this audit.

**Primary sources** (read in full or in relevant part for this audit): `docs/production-readiness-and-integration-boundary-audit.md` (Phase 8-K/8-L — the direct predecessor of this document; most of its findings are re-confirmed here and updated for Phase 9's subsequent implementation), `docs/production-po-workflow-implementation.md`, `docs/production-email-edi-workflow.md`, `docs/production-deployment-and-recovery-runbook.md`, `docs/local-dev-environment-notes.md`, `docs/ernest-current-operation-question-sheet.md`, `docs/customer-review-question-sheet.md`, `docs/customer-review-decision-package.md`, `docs/gulliver-20260917-confirmed-business-rules.md`, `docs/official-po-integration-detailed-design.md`, `docs/official-po-integration-foundation.md`, `docs/target-production-procurement-workflow.md`, and direct Source reads of `backend/src/main/resources/application.yml`/`application-production.yml`, `SafetyGuardEnvironmentPostProcessor.java`, `SecurityConfig.java`, `LegacyDataSourceConfig.java`, `docker-compose.yml`, the Official PO/Excel/PDF/Import Folder/Email/EDI service classes, and `backend/demo-data/03-readonly-user.sql`.

**Classification legend used throughout**: **CONFIRMED** (verified directly in this repository's code/config) · **DOCUMENTED** (stated in a docs/ file, not independently verified in code) · **OPERATION CONFIRMED** (a named person, e.g. Ernest, has actually answered) · **UNKNOWN** (asked, not yet answered) · **UNDECIDED** (not yet even asked/decided, no proposal exists) · **STUB** (code exists but throws/no-ops) · **SIMULATED** (a Local/Demo-only substitute, e.g. logs instead of sending).

---

## 1. Environment Matrix

| Aspect | Local | Test | Demo | Production |
|---|---|---|---|---|
| Frontend | Vite dev server, `http://localhost:5173` | same build, Playwright-driven | same build | **UNDECIDED** — no build/hosting target defined |
| Backend | Spring Boot, profile `local`, `mvn spring-boot:run` | profile `test` (same DB endpoints as local unless overridden — `application-production.yml` is the only profile-specific override file that exists) | profile `demo` | **UNDECIDED** — `application-production.yml` exists but only overrides one property (`app.demo-features.enabled=false`); no DB/SMTP/Import Folder/Secret values are defined for it, and it can never load anyway (see SafetyGuard, §Safety Gate below) |
| Portal DB | Docker `postgres:16`, `localhost:54321/gsys_portal` (CONFIRMED, `docker-compose.yml`) | same container | same container | UNDECIDED (no RDS/managed-PG decision on file; `target-production-procurement-workflow.md:336` names RDS PostgreSQL only as a candidate, explicitly unconfirmed) |
| Legacy DB | Docker `mysql:8.0`, `localhost:33061/legacy_demo`, disposable demo schema (CONFIRMED) | same | same | UNDECIDED — real Legacy MySQL host/port/network path is UNKNOWN (Ernest Q13, unanswered) |
| SMTP | none (SIMULATED via `LoggingEmailSenderAdapter`, logs only, never opens a socket) | same | same | **IMPLEMENTED but unreachable**: `SmtpEmailSenderAdapter` (`@Profile("production")`) is a real `JavaMailSender`-based implementation reading standard `spring.mail.*` properties — fully coded, never exercised, provider/credentials UNDECIDED |
| Import Folder | Local filesystem dir via `LocalFilesystemImportFolderAdapter` (CONFIRMED) | same | same | **STUB**: `ProductionImportFolderAdapter` (`@Profile("production")`) exists and implements the interface, but its `place()` method unconditionally `throw`s `UnsupportedOperationException` — the real transport (SMB/SFTP/other) was never decided, so nothing was implemented |
| EDI | UI-only status field (`portal_order.edi_status`), no transmission code at all | same | same | **NOT CONFIGURED** — zero EDI transmission code exists anywhere in the repository (0 matches for real EDI file/API/SFTP code) |
| Authentication | Spring Security session form login, `portal_user` table, BCrypt | same | same | Same mechanism would run if reached, but User CRUD UI/API does not exist (Flyway-seeded accounts only), and SSO/MFA is UNDECIDED |
| Secrets | Plaintext in `application.yml`, committed to Git (Demo-only values, explicitly documented as safe-to-commit demo credentials) | same | same | UNDECIDED — no Secret Manager/env-var-injection mechanism exists in this repo at all |
| Logging | Console only, `logging.level.com.glv.gsysportal: INFO`, structured Correlation ID per request (CONFIRMED, `CorrelationIdFilter`) | same | same | Same code would run; no log aggregation/shipping target decided |

---

## 2. Hosting

**No hosting decision exists in this repository for either Portal or Legacy.** Per `docs/production-readiness-and-integration-boundary-audit.md` §9 (re-confirmed here, unchanged since Phase 8-K):

- Portal hosting: `docs/target-production-procurement-workflow.md:334-337` lists "EC2(App)/RDS PostgreSQL/S3/CloudWatch" — explicitly labeled as a **candidate proposal**, not a decision.
- Legacy hosting: the same doc, same lines, states current G-SYS's AWS/EC2/S3 usage "仕様書から確認できていない" (cannot be confirmed from the spec). `docs/G-SYS_Online-Ordering_Prototype_Technical_Design.md:924` separately confirms the current codebase has zero AWS/external-network wiring anywhere.
- No repository file mentions Windows, specific file paths (e.g. `C:\g-sys-batch`), Task Scheduler, RDP, or VPN as **confirmed** Legacy facts. These may be true, but nothing in this repository backs them — treat any such detail from outside this repo as **UNCONFIRMED** until Ernest verifies it (Ernest Q13, still **UNKNOWN** — see the Question List).

**Classification: Hosting = UNDECIDED, both Portal and Legacy.**

---

## 3. Network

No network path in the Production direction has been established or even researched (Environment Matrix, `production-readiness-and-integration-boundary-audit.md` §9 "9項目すべてUnconfirmed"):

| Path | Protocol | Current State | Production Requirement |
|---|---|---|---|
| User → G-OPS Frontend | HTTPS (assumed) | N/A (Local only, HTTP) | TLS termination method UNDECIDED (no reverse proxy/ALB config in repo) |
| G-OPS Backend → Portal DB | PostgreSQL wire protocol, port 5432 (mapped 54321 locally) | Docker bridge network, no TLS (`useSSL` not set — Postgres JDBC default) | Network path + TLS UNDECIDED |
| G-OPS Backend → Legacy MySQL | MySQL wire protocol, port 3306 (mapped 33061 locally) | Docker bridge network, `useSSL=false` explicit in the JDBC URL (CONFIRMED, `application.yml`) | Real network route (VPN/private link/other) UNKNOWN (Ernest Q13) |
| G-OPS Backend → Official PO Import Folder | Filesystem write (local dir only) | N/A | Protocol itself UNDECIDED (SMB/SFTP/other) — `ProductionImportFolderAdapter` is an unimplemented stub precisely because this is unknown |
| G-OPS Backend → SMTP | SMTP/submission (587 default) | N/A, adapter unreached | Host/port/TLS/auth all UNDECIDED (values would come from `spring.mail.*`, currently empty) |
| G-OPS Backend → EDI | N/A | No code exists | Entirely UNDECIDED — External Spec required before this can even be scoped |

Firewall, DNS, and Production domain are all **UNDECIDED** — no repository evidence of any of them.

---

## 4. Portal Database

- Engine: PostgreSQL 16, Flyway-migrated, currently 31 migrations applied (`V1`-`V31`, forward-only — CONFIRMED, `mvn` startup logs this session: "Successfully validated 31 migrations").
- Connection pool: HikariCP, `maximum-pool-size: 5` (Demo value, `PrototypeDataSourceConfig.java`).
- Transaction manager: dedicated `prototypeTransactionManager`, `@Primary` DataSource.
- Credentials: `gsys_portal` / `gsys_portal_demo_pw`, plaintext in `application.yml`, committed to Git — explicitly a Demo-only value per multiple prior Phase reports, not a leak of anything real.
- Backup / Restore / Encryption / HA: **none exist**. The only persistence is a Docker named volume (`prototype-postgres-data`); no backup job, no restore procedure, no encryption-at-rest configuration, no replica.
- Migration strategy for Production: Flyway itself needs no change — the same forward-only migration chain applies to any Postgres instance reachable at Production config time. This is the one sub-item already **Production Ready** in the strict "no further engineering needed" sense.

**Classification: hosting UNDECIDED; migration strategy CONFIRMED ready; backup/restore/encryption/HA UNDECIDED (not just unconfigured — never designed).**

---

## 5. Legacy Database Integration

Full technical detail (already independently re-verified this round, see the accompanying research trail): `LegacyDataSourceConfig.java` configures a HikariCP pool (`legacy-readonly-pool`, `maximum-pool-size: 5`) against `app.legacy.datasource.*` properties, with `config.setReadOnly(true)` **hardcoded** in the bean method (not driven by the `app.legacy.datasource.read-only` YAML key — that key has no `@Value` binding anywhere, so it is documentation-only; the Java-level guarantee cannot be disabled by editing YAML). A dedicated `legacyTransactionManager` and `legacyJdbcTemplate`/`legacyNamedParameterJdbcTemplate` (the latter wrapped in `LegacyFailureTranslatingJdbcTemplate` since Phase 8-L, translating any connectivity failure into `LegacyUnavailableException`) are entirely separate beans from the Prototype DataSource — no sharing.

7 read-only repository classes exist under `repository/legacy/` (`ArrivalReadRepository`, `FulfillmentReadRepository`, `LegacyPoConcurrencyReadRepository`, `LegacyPriceReadRepository`, `LegacyStockReadRepository`, `OfficialPoPreflightReadRepository`, `WarehouseStockReadRepository`) — grepping the entire package for `INSERT INTO|UPDATE |DELETE FROM|.save(|.delete(` returns zero matches.

No retry logic exists anywhere (`@Retryable`/resilience4j: 0 matches in `pom.xml` or source). No explicit connection/query timeout is configured (HikariCP defaults apply). Legacy connectivity failure propagates as `LegacyUnavailableException` → HTTP 503 (via `GlobalExceptionHandler`, added Phase 8-L) — this is a functioning graceful-degradation path, not a crash.

**Production information still needed** (values, not decisions — do not request the values themselves in this document): Host, Port, DB Name, a SELECT-only DB username/credential, and the network route to reach it. None of these exist anywhere in this repository for a real Legacy instance.

---

## 6. Legacy READ ONLY Safety (3 layers, all CONFIRMED)

1. **DB Account layer**: `backend/demo-data/03-readonly-user.sql` creates `gsys_portal_ro`, grants `SELECT` only on `legacy_demo.*`, and explicitly `REVOKE`s `INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX`. For Production, an equivalent SELECT-only Legacy DB role is a DBA-side operational task (not a Portal Source change) — see the Checklist.
2. **Connection Pool layer**: `LegacyDataSourceConfig.legacyDataSource()` hardcodes `config.setReadOnly(true)`.
3. **Application layer**: every `repository.legacy` method runs under `@Transactional(readOnly = true, transactionManager = "legacyTransactionManager")`; `LegacyReadOnlyIntegrationTest.java` regression-tests that a write attempt against `TR_PO_DTL` is rejected.

This 3-layer design is unaffected by Production configuration — it travels with the code regardless of which Legacy host it eventually points at. **The one Production-specific action required is Layer 1's real-world counterpart**: provisioning a SELECT-only role on the real Legacy MySQL, a Legacy-DBA-side operational task, not a Portal code change.

---

## 7. Authentication / Authorization

- Mechanism: Spring Security **session-based form login** (`SecurityConfig.java`), backed by `portal_user` (username/display_name/password_hash BCrypt/role/email/active), `loginProcessingUrl=/api/auth/login`.
- CSRF is explicitly disabled (`csrf.disable()`), documented in the class Javadoc as an accepted simplification "for a local-only Prototype... not a decision meant to carry forward into any later Step that touches a non-local environment" — i.e. the codebase's own comment already flags this as a Production gap.
- Roles: `OPERATOR`/`ADMIN` only, DB-value-only (no role-management UI). `@PreAuthorize("hasRole('ADMIN')")` enforced at the Backend for every Admin-only action (HTTP-level tested).
- `/api/order-candidates/**`, `/actuator/health`, and `/api/health/**` are `permitAll` by design (health endpoints must be reachable without a session for a load balancer; candidate browsing was an intentional Step-0 decision).
- **No User CRUD UI/API exists** — accounts are Flyway-seeded only.
- No SSO/OAuth/SAML, no MFA, no explicit session timeout/concurrent-session policy — Spring Security defaults only.
- Local/Demo/Test and the (unreachable) Production profile would use the **identical** authentication code path — there is no Production-specific auth mechanism, because none was ever requested or decided.

**Classification: mechanism CONFIRMED and functional; Production-appropriateness (CSRF, SSO/MFA, User CRUD, session policy) is an explicit, flagged Gap — UNDECIDED whether it's even required (Customer Decision, see Question List).**

---

## 8. Secrets

| Item | Current Source | Classification |
|---|---|---|
| Portal DB credential | `application.yml`, plaintext, committed to Git | Config File (Demo-only value, explicitly documented elsewhere as safe) |
| Legacy DB credential | same | Config File (Demo-only value) |
| SMTP credential | `spring.mail.username`/`spring.mail.password`, `@Value("${...:}")`, empty default | Environment Variable (would be — nothing currently supplies one) |
| Import Folder credential | none — `LocalFilesystemImportFolderAdapter` takes only a filesystem path; `ProductionImportFolderAdapter` throws before any credential concept is even reached | Unknown / Not Yet Designed |
| EDI credential | N/A, no EDI code exists | Unknown / Not Yet Designed |
| Encryption key | none found anywhere in the codebase | Unknown / Not Yet Designed |
| API key | none found | N/A |

No Secret Manager, Vault, or `.env`-based separation exists (`.gitignore` has no Secret-exclusion pattern; there is exactly one config file, `application.yml`, shared by all Local/Demo/Test profiles — `application-production.yml` overrides only the one demo-features flag). A grep of the entire backend/frontend source for literal-looking `password`/`secret`/`api_key` assignments outside test code found only the already-documented Demo credentials above — **no Production secret is hardcoded anywhere, because no Production secret exists anywhere in this codebase.**

**Classification: Secrets handling = UNDECIDED (no mechanism exists to build on; this is a from-scratch Infrastructure decision, not a code defect).**

---

## 9. Official PO Import Folder

- Interface: `OfficialPoImportFolderAdapter.place(byte[] excelBytes, String fileName)` — deliberately minimal, "placed" means only "the bytes reached wherever this Adapter writes them," never "Legacy confirmed receipt" (own Javadoc).
- Local/Demo/Test implementation: `LocalFilesystemImportFolderAdapter`, writes to `${app.official-po.import-folder.base-dir}` (a plain local directory, profile-suffixed).
- **Production implementation: `ProductionImportFolderAdapter`, `@Profile("production")`, unconditionally throws `UnsupportedOperationException` from `place()`.** This is not a partial implementation — no SMB/SFTP/UNC/network code exists at all. Its own Javadoc states the real transport/credential mechanism is "Source-unconfirmed... therefore NOT implemented here."
- Idempotency: real, working — `IdempotencyService`, key = `orderId-officialPoNo-revisionNo`, DB-UNIQUE-constraint-backed (not a race-prone SELECT-then-INSERT), regression-tested under 8-thread concurrent claims.
- Retry: the Integration Request state machine supports FAILED → re-submit (re-places the already-generated Excel bytes, does not regenerate), but this only exercises `LocalFilesystemImportFolderAdapter` today.
- Legacy's own consuming batch: `PrOfficialPoImportBatch.java` (`phasep-gulliver/gulliver/src/main/java/jp/ne/glv/batch/`), a standalone CLI batch, **not a Web API** — confirmed directly from Legacy Source (`docs/ernest-current-operation-question-sheet.md` Q1-Q2 "What we already know"). It performs Delete & Recreate on re-import and blocks re-import once an Invoice is linked.

**Required Production information (Ernest, P0 — see Question List)**: real Import Folder path, access method (SMB/SFTP/local-mount/other), credential, permission model, exact file-naming expectation beyond what `OfficialPoFileNaming.java` already assumes, polling/trigger schedule of `PrOfficialPoImportBatch`, file move/archive behavior, failure handling, duplicate handling (partially covered by the Idempotency layer above, but only on the Portal side).

---

## 10. Official PO End-to-End Flow

```
Order Approved
  → Formal PO No. confirmed (Staff/ADMIN input today — auto-numbering per BR-08 exists as a Working
    Assumption implementation, format {Supplier3}{Brand3}{3-digit serial}, concurrency-safe)
  → Excel generated (OfficialPoExcelGenerationService — REAL, cell-contract-verified against
    PrOfficialPoImportBatch's own ROW_IDX_/COL_IDX_ constants)
  → PDF generated (OfficialPoPdfGenerationService — REAL, same data model as Excel)
  → [Internal Approval — NOT IMPLEMENTED, no such step exists in this codebase today]
  → Import Folder placement (LocalFilesystemImportFolderAdapter = REAL/Local only;
    ProductionImportFolderAdapter = STUB, throws)
  → G-SYS Batch import (PrOfficialPoImportBatch — Legacy-side, entirely outside Portal's reach,
    trigger/schedule UNKNOWN)
  → G-OPS Confirmation (OfficialPoIntegrationService.confirmImport — REAL, manual/on-demand
    READ ONLY comparison against TR_PO/TR_PO_DTL, not a background poller by deliberate design
    choice: "定期実行するJob自体が意味を持たない" while Legacy Production is unreachable, and a
    scheduled job reaching out on its own was judged undesirable for Production Safety)
```

**Automatic**: PO No. confirmation validation, Excel/PDF generation, Local Import Folder placement, Idempotency, Import confirmation comparison logic itself.
**Manual**: PO No. value entry (Staff/ADMIN types it — no G-SYS-side auto-numbering source confirmed to read from), triggering the confirmation check (button press, not a poller), the still-nonexistent "Internal Approval" step.
**Not implemented at all**: real Import Folder transport, any Internal Approval workflow beyond the existing Draft→PENDING_APPROVAL→APPROVED chain.

---

## 11. Excel / PDF Formal Format

- Both Excel and PDF are generated from one shared data model (`OfficialPoExcelInput`/`OfficialPoPdfInput`, confirmed shared by `production-po-workflow-implementation.md` §2.2).
- The Excel **cell contract** (which row/column holds what) is **CONFIRMED against real Legacy source**: `OfficialPoExcelGeneratorContractTest` asserts the generator's cell mapping matches `PrOfficialPoImportBatch`'s own `ROW_IDX_`/`COL_IDX_` constants exactly.
- **`testfile/OfficialPO.xlsx`** (inside `phasep-gulliver/`) is confirmed to be **only a unit-test fixture for the Legacy Import Batch, not the real business-facing Gulliver Official PO template** — this repository has no real, customer-facing Official PO Excel/PDF template or sample anywhere. Do not treat `testfile/OfficialPO.xlsx` as a production format source beyond its cell-position contract (which the contract test already validates).
- Currency is read from Excel **cell display format**, not cell value — a documented technical trap for anyone regenerating the writer logic.
- **Gap**: whether the actual Excel/PDF PortalOfficialPO Generator's *visual layout* (as opposed to just cell positions) matches what Gulliver actually expects/sends to Suppliers is unconfirmed — Ernest Q2 asks precisely this ("実際にメーカーへ送付しているPO文書は、G-SYSのImport Folderへ配置しているOfficial PO Excelと同一のファイルか").

---

## 12. SMTP / Manufacturer Email

- Local/Demo/Test: `LoggingEmailSenderAdapter` — never opens a socket, logs the would-be send.
- Production: `SmtpEmailSenderAdapter` (`@Profile("production")`) — a genuinely complete `JavaMailSender`-based implementation (not a stub), reading `spring.mail.host`/`port`/`username`/`password` (Spring Boot's own standard namespace, `MailSenderAutoConfiguration` deliberately excluded so this is the only place a `JavaMailSender` is ever built). It has never run in any environment because `@Profile("production")` + SafetyGuard make it unreachable here.
- Required Production values: SMTP Host, Port, TLS mode, Username, Secret, From address, Reply-To policy, allowed-sender domain, network route, timeout, retry policy, bounce handling — **all UNDECIDED** (`docs/production-email-edi-workflow.md` §5 CUSTOMER CONFIRMATION REQUIRED #3 lists this explicitly, still open).
- Send-side mechanics that already work end-to-end in Demo: Mail Preview resolution reuse (single source of truth for what gets sent), Manufacturer Channel/Contact/Template resolution, Idempotency (key = `orderId-revisionNo`), Audit (`EMAIL_SENT`/`EMAIL_SEND_FAILED`), UI-level Failure Handling with a resend button.

---

## 13. EDI

- Current implementation: **UI/status-tracking only**. `portal_order.edi_status` (`WAITING_INPUT`/`COMPLETED`) + `edi_completed_by`/`edi_completed_at`. `OrderStatusTransitionService.recordEdiSend` sets `WAITING_INPUT` (a DB write, nothing transmitted); `completeEdiInput` (ADMIN/OPERATOR) sets `COMPLETED` (again, a DB write only).
- **Zero transmission code**: no file generation, no API client, no SFTP client — confirmed absent from the entire repository.
- `manufacturer_channel` Master (Portal-only, new this Phase) records which Supplier uses EMAIL vs. EDI, but starts empty — real data entry is a post-decision Operations task, not a code gap.
- Business framing already on file (`production-readiness-and-integration-boundary-audit.md` §6, still valid): EDI requires an **External Specification** from the Vendor/manufacturer-side EDI system that does not exist anywhere yet, and Ernest's own operational knowledge (Q14-Q17) may not be sufficient — this could escalate to a Vendor conversation. **Recommendation carried forward unchanged: Email-only start is architecturally viable; EDI is not on the Ordering Critical Path.**

---

## 14. Master Data (Supplier / Brand / Contact / Channel / Region / Short Code / Template)

| Master | Source of Truth | Notes |
|---|---|---|
| Supplier | Legacy `MS_COMM` (`CATE_ID='MS_SUPPL'`), READ ONLY | Code/name only — no attributes beyond that are read (confirmed via Source, `customer-review-decision-package.md:543`) |
| Brand | Legacy, READ ONLY (same pattern) | |
| Supplier/Brand 3-char short code (BR-08 numbering input) | **Portal-only** (`official_po_short_code` table, V29/V30) | Legacy `MS_COMM` has **no** 3-char abbreviation column (CONFIRMED by Source search) — BR-08 itself states Gulliver decides these values and "G-SYS Masterに登録する", but since no such Legacy field exists, the Portal Master exists as a manual-entry-by-ADMIN substitute; G-OPS still never *generates* a value itself (an ADMIN types Gulliver's decided value in) |
| Supplier Contact | Portal-only (`supplier_contact`) | Foundation complete; real data entry is an Operations task |
| Manufacturer Channel | Portal-only (`manufacturer_channel`) | Starts empty; real Email/EDI assignment per Supplier is a Customer Decision + data-entry task |
| Supplier Region Classification (Domestic/Overseas) | Portal-only (`supplier_region_classification`) | Same pattern |
| Mail Template | Portal-only (`mail_template`) | Foundation complete (priority-resolution, ambiguity detection); real wording is a Customer Decision |

**Important distinction to keep straight**: Supplier/Brand identity itself is Legacy-sourced READ ONLY; everything *about* how to communicate with that Supplier (Contact, Channel, Region, Short Code, Template) is Portal-owned, because no equivalent Legacy Master field exists for any of it.

---

## 15. Logging / Monitoring

Phase 8-L already implemented real Foundation here (re-confirmed unchanged this round):

- `GET /actuator/health` — Application + Portal DB only (`PortalDatabaseHealthIndicator`, scoped to `prototypeDataSource`; Spring Boot's automatic multi-DataSource aggregation disabled via `management.health.db.enabled=false` specifically so Legacy is never ambiguously folded in).
- `GET /api/health/legacy` — Legacy connectivity, **deliberately separate** so a Legacy outage never flips the main app to DOWN.
- Both endpoints: `permitAll`, minimal body (`{"status":"UP"|"DOWN"}` only), `management.endpoint.health.show-details=never` — no DB URL/host/credential/stack trace ever exposed.
- `CorrelationIdFilter` (`Ordered.HIGHEST_PRECEDENCE`): validates/generates `X-Correlation-Id`, propagates via MDC to every log line, echoes it in the response header, emits one structured summary line per request (`operation`/`result`/`errorCode`/`durationMs`).
- `GlobalExceptionHandler`: ~40 Business exception handlers plus `VALIDATION_ERROR`, `LEGACY_UNAVAILABLE` (503), `PORTAL_DB_ERROR` (503), `INTERNAL_ERROR` (500, no stack trace ever returned to the client), all via one `error()` factory producing a consistent `{timestamp, status, errorCode, path, correlationId}` shape.
- `audit_event` table: rich Business-action audit trail (Order/Approval/Official PO/Revision/Send/Recipient Override/Supplier Response/Cancel/Master Change — all already recorded).

**What's still missing**: log aggregation/shipping destination (console only today), Metrics (no Micrometer/provider selected), Alerting (UNDECIDED — provider-dependent), Disk/CPU/Memory/DB-connection-pool monitoring (none wired up), SMTP/Import failure alerting (no failure can occur yet since neither is connected).

---

## 16. Audit Log

Already comprehensive for **Business** events (Order, Approval, Official PO issuance/revision/reissue/cancel, Send success/failure, recipient override, Supplier Response, Master Data changes) — `audit_event` table, `AuditEventView`, resolved to human display names at read time. This is functionally sufficient to answer "who did what when" for every Business action already in scope.

**Gap**: no retention/deletion policy exists — the table has no cleanup logic at all (unbounded growth by design, same category of issue the Phase1 Test Data Lifecycle work addressed for Master tables, but `audit_event` is Business data, not Test data, and was explicitly out of that scope). Retention period is a **Business/Customer Decision**, not a technical unknown.

---

## 17. Backup / Recovery

- Portal DB: no backup/restore/RPO/RTO defined. Current persistence is a bare Docker volume.
- Official PO Excel/PDF artifacts: generated on demand from data already in Portal DB, so they are in principle **regenerable**, not irreplaceable — but whether a Production deployment should also *archive* the exact bytes once sent (for audit/dispute purposes) is undecided.
- `docs/production-deployment-and-recovery-runbook.md` already exists as a Document-only Phase 8-L deliverable — its content should be treated as the current Runbook baseline; this audit does not duplicate it, only cross-references it (see the Checklist for what it does/doesn't cover).

---

## 18. Deployment

- Build: Maven (`mvnw`) for Backend, npm/Vite for Frontend — both confirmed working (this session's own regression runs).
- Migration: Flyway, forward-only, confirmed clean.
- CI/CD: **none exists** — no `.github/workflows` or equivalent found anywhere in the repository.
- Environment config separation: effectively none — one `application.yml` plus a nearly-empty `application-production.yml`.
- Rollback: undocumented — no Flyway-down procedure, no application-version-rollback procedure.
- Release procedure: does not exist as a document (confirmed absent in earlier Phase Documentation Inventory work, unchanged).

---

## 19. Production SafetyGuard (current mechanism, unchanged this round)

`SafetyGuardEnvironmentPostProcessor` (full Source re-read this round) runs during Spring Boot's `EnvironmentPostProcessor` phase — **before any DataSource bean exists** — and throws `SafetyGuardViolationException`, aborting `SpringApplication.run()`, unless:

1. **Profile Guard**: at least one active profile is set, and every active profile ⊆ `{"local", "demo", "test"}` — no default profile, no `production`, no anything else, ever.
2. **Host Allowlist**: both Legacy and Prototype JDBC URL hosts (parsed via `java.net.URI`, never substring-matched) ⊆ `{"localhost", "127.0.0.1", "legacy-demo-mysql", "prototype-postgres"}`.
3. **DB Name Guard**: Legacy DB name must be exactly `legacy_demo` (explicitly never `goo`, the real Legacy schema name, per the class's own Javadoc); Prototype DB name must be exactly `gsys_portal`.

This is a **process-level, code-based gate** — not a config toggle, not something disabled by an environment variable. It is registered via `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`, so it runs unconditionally on every startup of this JAR, in any environment, including a hypothetical future Production deployment of this exact artifact.

**This audit does not propose changing it.** Per the explicit instruction for this round, only the *design* of what a safe extension would look like is sketched (Checklist item), never implemented:
- A real Production deploy would need `ALLOWED_PROFILES` to include `production`, `ALLOWED_HOSTS` to include the real Portal/Legacy hosts, and `ALLOWED_*_DB_NAMES` to include the real DB names — this is an irreversible, one-way design change to this exact class, and should be treated as **Gate 2 / a point of no return**, not a routine config edit.

---

## 20. Summary Table (per the requested 10-way classification)

| Area | Classification |
|---|---|
| Portal DB Migration | B (Production Foundation Ready) |
| Legacy READ ONLY (3-layer design) | B — travels with the code, only Layer-1's real-world DBA counterpart is outstanding |
| Health Check / Structured Errors / Correlation ID | B (implemented, works today, just not exercised against real Production dependencies) |
| Idempotency Foundation | B |
| Official PO Excel/PDF Generator (cell contract) | B |
| Official PO Import Folder (real transport) | I (BLOCKED — no decision exists to implement against) |
| SMTP send | B (code complete) / H (Secrets) / D (Customer: From/Reply-To/Template) |
| EDI | F (External Spec Wait), effectively out of Ordering Critical Path |
| Hosting / Network | G (Infrastructure Wait) — not even researched yet, not just undecided |
| Secrets management | H (Security/Config Wait) |
| Authentication (SSO/MFA/User CRUD) | D (Customer Decision) |
| CI/CD, Release Procedure, Rollback | G / J — largely not attempted yet |
| Master Data (Supplier/Brand identity) | B — Legacy READ ONLY source already correct |
| Master Data (Contact/Channel/Region/Short Code/Template) | C (mechanism complete) — real data entry is an Operations task pending Customer Decision on values |

See `gops-production-readiness-checklist.md` for the actionable checklist form of this table, `gops-production-question-list.md` for every open item that requires a person's answer, and `gops-production-risk-register.md` for what could go wrong.
