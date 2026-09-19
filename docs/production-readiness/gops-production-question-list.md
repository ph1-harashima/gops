# G-OPS Production Readiness Question List

**Status**: Extraction only. Every item below already exists as an open question somewhere in this repository (primarily `docs/ernest-current-operation-question-sheet.md`, 39 items, and `docs/customer-review-question-sheet.md`) or is newly identified by this audit as an Infrastructure/Business gap with no prior question on file. Nothing here re-asks anything already answered — no answer text was found anywhere in the repository for any of the 39 Ernest items; `docs/production-po-workflow-implementation.md` explicitly confirms Phase 9 proceeded using a **user-provided Working Assumption instead of Ernest's answers**, so all 39 remain open.

**Priority scheme for this document** (different from the P1/P2/P3 scheme used inside `ernest-current-operation-question-sheet.md` itself — that scheme is about Phase1 *feature* implementation order; this one is about the Production Readiness Gate sequence defined in `gops-production-readiness-checklist.md`):

- **P0**: required before Production connection/configuration can safely begin at all (Gate 1-2)
- **P1**: required before Integration Test (Gate 7)
- **P2**: required before Gulliver UAT (Gate 8)
- **P3**: required before Release (Gate 9-10)

---

## P0 — Before Production Connection Begins

| # | Question | Why Needed | Owner | Blocking Stage |
|---|---|---|---|---|
| P0-1 | Where does the real Legacy G-SYS server and its Official PO Import Folder actually run (on-prem/cloud/which), and what network path/authentication would Portal need to reach it? | Nothing about Legacy Production connectivity can be designed, let alone built, without this — the single highest-priority unknown in the whole audit. (= Ernest Q13) | **Ernest** | Gate 2 (Network/Secrets Ready) |
| P0-2 | Where will the G-OPS Portal itself (Frontend/Backend/Portal DB) be hosted? | `target-production-procurement-workflow.md` only lists EC2/RDS/S3/CloudWatch as an unconfirmed candidate — no decision exists. Everything about Network, Secrets, and Deployment design depends on this. | **Infrastructure** | Gate 1 (Architecture Confirmed) |
| P0-3 | Which Secret management mechanism will Production use (env vars, AWS Secrets Manager, Vault, other)? | No such mechanism exists in this codebase at all today; every credential in §Configuration Inventory needs somewhere real to live before any Production value can be entered. | **Infrastructure** | Gate 2 |
| P0-4 | Who is authorized to approve the one-way `SafetyGuardEnvironmentPostProcessor` Allowlist extension (adding a `production` profile and real hosts/DB names), and under what review process? | This is explicitly a point-of-no-return code change (Audit §19) — it should never happen as a routine PR. | **Business Decision** (Phase1 Japan to propose, Gulliver/Infrastructure to approve) | Gate 2 |

---

## P1 — Before Integration Test

| # | Question | Why Needed | Owner | Blocking Stage |
|---|---|---|---|---|
| P1-1 | What is the actual Official PO numbering rule beyond "≤30 characters" (11th-character-onward structure, meaning of the 2-digit ID Code)? | Excel Generator can write *a* PO No. today (Working Assumption: unrestricted), but writing one that Legacy's Import Batch actually accepts requires the real rule. (= Ernest Q1) | **Ernest → Gulliver** | Gate 7 |
| P1-2 | Who actually creates the Official PO Excel today, and does it go into the Import Folder at order-confirmation time or batched later? Is the file mailed to the Supplier the same file placed in the Import Folder, or a different one? | Determines whether Portal's existing Excel Generator can simply replace this step or must produce two distinct artifacts. (= Ernest Q2) | **Ernest** | Gate 7 |
| P1-3 | What is the current real-world Official PO Excel workflow (manual, tool-assisted, frequency)? | Determines whether Portal can fully replace manual Excel creation or must coexist with it for a transition period. (= Ernest Q4) | **Ernest** | Gate 7 |
| P1-4 | How is `PrOfficialPoImportBatch` actually triggered, and at what frequency? | Directly determines Portal's own confirmation-polling design (already deliberately built as manual/on-demand, not a background poller, specifically because this is unknown). (= Ernest Q7) | **Ernest** | Gate 7 |
| P1-5 | Is the current manufacturer-email process really routed through Legacy's `SYS_SEND_MAIL` queue, or handled individually (e.g. Outlook)? | Determines whether `SmtpEmailSenderAdapter`'s direct-SMTP approach is the right integration shape, or whether Portal should instead write into the existing `SYS_SEND_MAIL` queue. (= Ernest Q8) | **Ernest** | Gate 7 |
| P1-6 | What SMTP Provider will Production use, and what are its host/port/TLS/auth requirements? | `SmtpEmailSenderAdapter` is fully coded against standard `spring.mail.*` properties but has never been pointed at a real provider. | **Infrastructure** (provider selection is explicitly *not* made by this audit) | Gate 7 |
| P1-7 | What From address and Reply-To policy should outgoing Manufacturer emails use? | Mail Preview currently assumes "the logged-in user" — this is a real Business decision, not a technical default. | **Gulliver** | Gate 7 |
| P1-8 | Final confirmation of the Official PO numbering rule as a permanent Business Rule (not just "unrestricted for now"). | The current Working Assumption (§Confirmed Business Rules BR-08, "structure not validated") may need to be replaced by a real validation rule once P1-1 is answered. | **Gulliver** | Gate 7 |

---

## P2 — Before Gulliver UAT

Grouped by theme; each references the existing question sheet for full "What we already know" context rather than repeating it verbatim here.

| # | Question | Why Needed | Owner | Blocking Stage |
|---|---|---|---|---|
| P2-1 | Re-import operational reality when an order is revised (same PO No. re-used, or a new one issued)? (= Ernest Q3) | Shapes how Portal's Revision feature maps onto Legacy's Delete&Recreate Import behavior. | Ernest | Gate 8 |
| P2-2 | Does Legacy hold a Domestic/Overseas flag for Suppliers anywhere? (= Ernest Q9) | Needed for automatic ja/en Mail Template language selection; currently manual via `supplier_region_classification`. | Ernest | Gate 8 |
| P2-3 | Existing manufacturer-communication mail wording/templates in current use? (= Ernest Q10) | Could seed `mail_template` content instead of drafting from scratch. | Ernest | Gate 8 |
| P2-4 | Where are Manufacturer Contact emails currently tracked (ledger, other system)? (= Ernest Q11) | Determines whether `supplier_contact` can be bulk-imported vs. entered from scratch. | Ernest | Gate 8 |
| P2-5 | What is the actual current PO Cancellation procedure in G-SYS? (= Ernest Q12) | Portal's Cancellation workflow (Request→Approve, already built) needs to align with whatever Legacy-side reality exists. | Ernest | Gate 8 |
| P2-6 | EDI operational reality — which Suppliers, what method, how are responses tracked? (= Ernest Q14-Q17) | Confirms whether EDI truly stays out of scope for this Release, or must be addressed sooner. | Ernest → possibly External (EDI Vendor) | Gate 8 |
| P2-7 | Real Manufacturer Channel data (which Supplier uses Email vs. EDI) | `manufacturer_channel` starts empty; ADMIN needs real values to enter before Go-Live communication can work at all. | Gulliver | Gate 8 |
| P2-8 | Final Mail Template wording (order confirmation / revision / cancellation / inquiry) | Foundation (priority resolution, ambiguity detection) is complete; content is not. | Gulliver | Gate 8 |
| P2-9 | Does the actual Gulliver-facing Official PO Excel/PDF visual layout match what Portal generates? | The cell **contract** is verified against Legacy's Import Batch; the **visual/customer-facing** format is unconfirmed — no real template exists in this repo to compare against (see Audit §11). | Gulliver | Gate 8 |
| P2-10 | Is User Management CRUD / SSO actually required, or is Flyway-seeded-only sufficient for the foreseeable Production population? | No CRUD UI/API exists; building one is real scope, not a config change. | Gulliver | Gate 8 |
| P2-11 | Approval Workflow refinements — multi-stage approval, self-approval boundaries for overseas Suppliers | Existing 2-stage Approval is Production Ready as-is; these are *additional* Business Rule refinements, not blockers to Go-Live unless Gulliver requires them. | Gulliver | Gate 8 |

---

## P3 — Before Release

| # | Question | Why Needed | Owner | Blocking Stage |
|---|---|---|---|---|
| P3-1 | Audit / Business Data retention period (`audit_event`, `portal_order`, etc.) | Drives Backup design and eventual DB-size planning; no code change needed once decided. | Gulliver (Business Decision) | Gate 9 |
| P3-2 | Portal DB Backup RPO/RTO | Nothing is designed yet; needs a target before a mechanism can be chosen. | Business Decision + Infrastructure | Gate 9 |
| P3-3 | Who owns first-response when Legacy/Portal DB/SMTP/Import Folder becomes unavailable in Production? | Recovery "type" already exists (Attention mechanism); "who" is unanswered (`production-readiness-and-integration-boundary-audit.md` §14). | Business Decision | Gate 9 |
| P3-4 | Import Error handling — who notices and how, today? (= Ernest Q6) | Shapes Attention/Notification escalation design for Official PO failures. | Ernest | Gate 9 |
| P3-5 | Initial (打診段階) PO usage reality — still used? (= Ernest Q5) | Determines if Portal needs to write pre-confirmation state to Legacy (currently assumed unnecessary). | Ernest | Gate 9 |
| P3-6 | Price Change / Stock-Sales / Invoice-Purchase / Warehouse-Logistics operational questions (= Ernest Q18-Q39, 22 items) | All explicitly out of the Ordering Critical Path — relevant only once those feature areas are scheduled for their own Production work, not for this Release. | Ernest (mostly), a few escalate to Gulliver (already flagged individually in the source sheet) | Gate 9, only if those feature areas ship in this Release |
| P3-7 | Release Procedure / Rollback procedure documentation | Does not exist yet; explicitly deferred until Production scope is fixed (`production-readiness-and-integration-boundary-audit.md` §15). | Phase1 Japan | Gate 9 |

---

## Owner Summary (cross-reference)

- **Gulliver**: P1-7, P1-8, P2-7, P2-8, P2-9, P2-10, P2-11, P3-1, P3-2 (co-owner), P3-3 (co-owner)
- **Ernest**: P0-1, P1-1 through P1-4, P2-1 through P2-6, P3-4, P3-5, most of P3-6
- **Phase1 Japan**: P0-4 (proposer), P3-7
- **Infrastructure**: P0-2, P0-3, P1-6, P3-2 (co-owner)
- **Business Decision** (Gulliver + Phase1 Japan jointly, not a pure technical answer): P0-4 (approver), P3-1 through P3-3

Full per-item context for every Ernest question remains authoritative in `docs/ernest-current-operation-question-sheet.md` (39 items) — this document only re-sequences them against the Production Readiness Gates, it does not replace that sheet.
