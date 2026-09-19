# G-OPS Production Readiness Checklist & Gates

**Status**: Checklist/planning only — checking an item here does not authorize acting on it. No item in this checklist may be executed under this round's rules (no Production connection, no code deploy, no Secret registration, no SafetyGuard change, no real Email/EDI/Import Folder access).

## Gate Definitions

| Gate | Name | Entry Criteria | Exit Criteria |
|---|---|---|---|
| 0 | Phase1 Freeze | — | `docs/gops-phase1-freeze.md` finalized, Backend/Frontend/E2E all green (DONE — Final Commit `71ad25d`) |
| 1 | Production Architecture Confirmed | Gate 0 done | Hosting for Portal AND Legacy access path both answered (P0-1, P0-2); `gops-production-architecture.md`'s UNDECIDED boxes resolved into a real diagram |
| 2 | Network / Secrets Ready | Gate 1 done | Network route to real Legacy confirmed reachable (not yet connected-to); Secret management mechanism selected and provisioned (empty); SafetyGuard Allowlist-extension design reviewed and approved (P0-4) — **still not applied** |
| 3 | Portal Production Environment Ready | Gate 2 done | Real Portal DB instance provisioned (empty schema); Backend deployable artifact defined; Production profile config populated (via Secret mechanism, not committed YAML) |
| 4 | Legacy READ ONLY Connected | Gate 3 done | Real Legacy SELECT-only DB role provisioned (Ernest/DBA-side); SafetyGuard Allowlist extended (the one-way change, P0-4 approved); first real (READ ONLY) query succeeds against real Legacy; `GET /api/health/legacy` reports UP against the real instance |
| 5 | Official PO Integration Connected | Gate 4 done | P1-1 through P1-4 answered; `ProductionImportFolderAdapter` implemented (real transport) and connected to the real Import Folder; first successful placement + Legacy Batch pickup confirmed |
| 6 | SMTP / Communication Connected | Gate 4 done (parallel with Gate 5) | P1-5 through P1-7 answered; real SMTP credentials provisioned; first real send succeeds to a controlled test mailbox |
| 7 | Integration Test PASS | Gates 4-6 done | All P1 questions answered; end-to-end Order→Official PO→Import→Confirm and Order→Email flows tested against real (non-Production-customer-facing) Legacy/SMTP endpoints |
| 8 | Gulliver UAT PASS | Gate 7 done | All P2 questions answered; Gulliver has exercised the real workflow and signed off |
| 9 | Release Judgment | Gate 8 done | All P3 questions answered; Release/Rollback procedure documented; Backup/Retention policy implemented |
| 10 | Production Release | Gate 9 done | Explicit Go-Live decision by Gulliver + Phase1 Japan |

**This audit round's own status: Gate 0 complete. Gates 1+ not started (by design — this round is audit/planning only).**

---

## Checklist

### Architecture
- [ ] Portal hosting decided (P0-2)
- [ ] Legacy access path/network confirmed reachable (P0-1)
- [ ] Production Architecture diagram finalized (all UNDECIDED boxes in `gops-production-architecture.md` resolved)
- [ ] SafetyGuard Allowlist-extension design reviewed (not yet applied) (P0-4)

### Hosting
- [ ] Portal Frontend hosting target selected
- [ ] Portal Backend hosting target selected
- [ ] Portal DB hosting target selected
- [ ] Confirm real Legacy G-SYS hosting environment (Ernest, P0-1)

### Network
- [ ] User → Frontend path (TLS termination method) decided
- [ ] Backend → Portal DB path decided
- [ ] Backend → Legacy DB path decided (Ernest, P0-1)
- [ ] Backend → Import Folder path/protocol decided (Ernest, P1-2/P1-4)
- [ ] Backend → SMTP path decided (Infrastructure, P1-6)
- [ ] Firewall rules identified (not yet requested/changed)
- [ ] DNS / Production domain decided

### Portal DB
- [ ] Production PostgreSQL instance provisioned (empty)
- [ ] Flyway migration chain applied to Production instance (still not connected in this round)
- [ ] Connection pool sized for Production load (currently Demo value 5)
- [ ] Backup mechanism selected and configured
- [ ] Restore procedure tested
- [ ] Encryption at rest confirmed/enabled
- [ ] Retention/deletion policy decided (P3-1)

### Legacy DB
- [ ] Real Legacy MySQL host/port/DB name confirmed (Ernest, P0-1)
- [ ] SELECT-only DB role provisioned on real Legacy (Ernest/DBA-side operational task, not a Portal code change)
- [ ] Network route confirmed reachable
- [ ] Explicit connection/query timeout configured (currently HikariCP defaults only)
- [ ] Connection pool sized for Production load

### Legacy READ ONLY
- [ ] Layer 1 (DB account SELECT-only) provisioned on real Legacy
- [ ] Layer 2 (pool `readOnly=true`) — already hardcoded in code, travels automatically, no action needed
- [ ] Layer 3 (`@Transactional(readOnly=true)`) — already enforced in code, travels automatically, no action needed
- [ ] `LegacyReadOnlyIntegrationTest` re-run against real Legacy READ replica/staging before Go-Live (never against real Production directly)

### Authentication
- [ ] Decide whether current session/form-login mechanism is sufficient for Production (P2-10)
- [ ] Decide SSO/MFA requirement (Gulliver)
- [ ] Build User CRUD UI/API if required (currently does not exist)
- [ ] Define session timeout / concurrent session policy
- [ ] Re-enable CSRF protection or explicitly document why it stays disabled for Production (`SecurityConfig.java` currently disables it "for a local-only Prototype" per its own comment)

### Secrets
- [ ] Select Secret management mechanism (P0-3)
- [ ] Provision Portal DB credential via that mechanism
- [ ] Provision Legacy DB credential via that mechanism
- [ ] Provision SMTP credential via that mechanism
- [ ] Confirm no Production secret is ever committed to Git (current repo has zero Production secrets — keep it that way)

### Official PO
- [ ] Ernest P1-1 through P1-4 answered
- [ ] Real Import Folder transport implemented (`ProductionImportFolderAdapter` currently throws — this is a real code change, `DO NOT IMPLEMENT` under this round's rules)
- [ ] Idempotency Foundation re-verified against real transport (already proven against Local adapter)
- [ ] Retry path re-verified against real transport

### Excel / PDF
- [ ] Confirm Portal-generated Excel visual layout matches the real Gulliver-facing document (P2-9)
- [ ] Decide whether manual Excel upload continues to be allowed post-Go-Live (existing unresolved "Excel Coexistence" question, `official-po-integration-detailed-design.md` §17)

### SMTP
- [ ] SMTP Provider selected (P1-6)
- [ ] From/Reply-To policy decided (P1-7)
- [ ] Real credentials provisioned via Secret mechanism
- [ ] Bounce/delivery-failure handling designed (currently none)
- [ ] First real send tested against a controlled mailbox (Gate 6/7, never before)

### EDI
- [ ] Confirm Email-only start is acceptable for Go-Live (recommended; EDI is not on the Ordering Critical Path)
- [ ] If EDI required for Go-Live: obtain External Vendor Specification (currently zero spec exists)
- [ ] Identify actual EDI Suppliers and method (Ernest, P2-6)

### Master Data
- [ ] Real Manufacturer Channel data entered (Email/EDI per Supplier) (P2-7)
- [ ] Real Supplier Contact data entered/migrated (Ernest may have a source ledger, P2-4)
- [ ] Real Mail Template content finalized (P2-8)
- [ ] Real Supplier/Brand short codes entered (Gulliver-decided values, ADMIN data entry, mechanism already built)
- [ ] Real Supplier Region Classification (Domestic/Overseas) entered

### Logging
- [ ] Log aggregation destination selected
- [ ] Structured (JSON) log format decided if required by the aggregation target
- [ ] Confirm Correlation ID propagation works end-to-end in the real deployment topology

### Monitoring
- [ ] Metrics provider selected (none today)
- [ ] `/actuator/health` and `/api/health/legacy` wired into the real orchestrator/load balancer
- [ ] Alerting mechanism selected and configured
- [ ] CPU/Memory/Disk/DB-connection-pool monitoring wired up

### Audit
- [ ] Confirm `audit_event` coverage is sufficient for Production Business events (already broad — Order/Approval/Official PO/Revision/Send/Override/Response/Cancel/Master Change)
- [ ] Retention/deletion policy decided and implemented (P3-1)

### Backup
- [ ] Portal DB backup mechanism implemented
- [ ] Backup frequency/retention decided
- [ ] Official PO Excel/PDF artifact retention policy decided (regenerable vs. must-archive-exact-bytes)

### Recovery
- [ ] RPO/RTO targets decided (P3-2)
- [ ] Restore procedure documented and tested
- [ ] First-responder ownership for each failure mode decided (P3-3)

### Deployment
- [ ] CI pipeline built (none exists today)
- [ ] Production profile config-injection mechanism decided (currently only a near-empty `application-production.yml`)
- [ ] Build artifact strategy confirmed (JAR/container — currently JAR via `mvnw`)

### Rollback
- [ ] Application version rollback procedure documented (none exists)
- [ ] Flyway migration rollback/down procedure documented (none exists — Flyway itself is forward-only by design)

### Integration Test
- [ ] All P1 questions answered
- [ ] End-to-end Order→Official PO→Import→Confirm tested against a real (non-Production-customer-facing) Legacy/Import endpoint
- [ ] End-to-end Email send tested against a real (test) SMTP endpoint

### UAT
- [ ] All P2 questions answered
- [ ] Gulliver walkthrough completed and signed off

### Release
- [ ] All P3 questions answered
- [ ] Release procedure documented
- [ ] Explicit Go-Live decision recorded
