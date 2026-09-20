# G-OPS Phase1 Freeze

## 1. Freeze Date

2026-09-20 (updated after the Freeze Blocker Final Fix round - see `docs/gops-phase1-final-cleanup-report.md` §7)

## 2. Baseline Commit

Phase1 Final Cleanup work started from `4bb7473` (Phase 5: Mobile Responsive audit + implementation, Business UAT audit). See §22 (Git) for the Final Commit this Freeze applies to.

## 3. Phase1 Scope

Implemented Business Capability (Prototype/Demo, never connected to Production):

- Dashboard → Brand-first Order Candidate entry (every entry point, including the Dashboard KPI itself as of the Freeze Blocker fix - see the Final Cleanup Report §7.1) → Draft creation/edit
- Draft → Submit for Approval → Approval / Return for Correction (Role-gated: OPERATOR/ADMIN)
- Approval → Official PO Integration Preparation (G-SYS連携準備) → Official PO No. auto-numbering (Supplier Short Code 3 + Brand Short Code 3 + 3-digit Serial, BR-08) → Excel/PDF generation
- Official PO → Import Folder hand-off (Demo, no real file placement) → G-SYS取込確認 (Demo)
- Manufacturer Send (Demo Send - Email/EDI channel, no real SMTP/EDI)
- Supplier Response entry/confirmation → Agreement
- Reissue (new Revision) / Cancel (Request → ADMIN Approval) workflow
- Order History (List/Detail, READ ONLY) - now with distinct Management No. / Official PO No. / Revision display (this Phase's Cleanup-1, see the Final Cleanup Report)
- Fulfillment/Arrival/Warehouse Stock/Stock-Sales visibility (Legacy READ ONLY)
- Follow-up Case (問い合わせ) / Reorder Draft creation
- Legacy PO Concurrency Control (整合確認)
- Price Change Foundation
- Master Maintenance Hub: Supplier一覧, Supplier Contact, Mail Template, Manufacturer Channel, Supplier Region Classification, Official PO Short Code, Global Settings
- Mobile Responsive (Admin/Approver scope, see §7)
- i18n (ja/en)

## 4. Architecture

- **Frontend**: React 19 + TypeScript + MUI + TanStack Query, Vite (`frontend/`)
- **Backend**: Spring Boot 3.3 (Java 17+ required; verified against Java 23 this session), `com.glv.gsysportal` (`backend/`)
- **Portal DB**: PostgreSQL 16 (`gsys_portal`), Flyway-migrated, read/write - the Prototype's own data store
- **Legacy Adapter**: MySQL 8 read-only DataSource (`legacy-readonly-pool`) against a disposable Legacy Demo Instance
- **Legacy READ ONLY**: enforced at 3 layers - DB-user grants (`gsys_portal_ro`), connection-pool `read-only=true`, and no write-capable Legacy repository exists in code
- **Safety Gate**: `SafetyGuardEnvironmentPostProcessor` refuses to start the application unless the active Spring profile is one of `local`/`demo`/`test` AND both JDBC URLs resolve to allowlisted local hosts/DB names - Production cannot be reached by this codebase's current Safety Gate at all (by design; see §11)

## 5. Main Business Flow

```
Dashboard
  → Brand (Order Candidate entry)
  → Candidates
  → Draft
  → Approval
  → Official PO (auto-numbered, BR-08)
  → Excel / PDF
  → Manufacturer Send (Demo)
  → Supplier Response
  → Agreement / Reissue / Cancel
```

## 6. Master Maintenance

- Supplier Hub (一覧 + Supplier Settings)
- Supplier Contact
- Mail Template (Communication)
- Manufacturer Channel
- Supplier Region Classification
- Official PO Short Code
- Global Settings

## 7. Mobile Scope

Admin / Approver responsive coverage only (390/430/768px + Desktop 1440x900 regression), per `docs/gops-admin-mobile-responsive-audit.md`. Operator/Candidate-entry mobile flows are explicitly out of this Phase's Mobile scope.

## 8. Test Baseline (post-Cleanup, see §20-28 of the Final Cleanup Report for exact numbers)

- Backend: full `mvn test` suite
- Frontend: Vitest unit tests
- TypeScript: `tsc -b` (0 errors)
- Vite: production build
- E2E: full Playwright suite (existing + this Phase's new Order History / Test Data Lifecycle scenarios)
- Mobile Responsive E2E
- i18n key parity (ja/en)

Exact pass counts are recorded in `docs/gops-phase1-final-cleanup-report.md` §20 (Final Test), not duplicated here since this document is meant to stay stable while the Report is the point-in-time record.

## 9. Confirmed Business Rules

Primary source: `docs/gulliver-20260917-confirmed-business-rules.md`. This Phase additionally confirms and implements, in the UI, the pre-existing entity-level distinction (already correct in `PortalOrder`/`OfficialPoIntegrationRequest`, previously not surfaced consistently):

- **BR-07 (Portal管理番号 / Management No.)**: Portal-internal only, `PortalOrder.prototypePoNo` (assigned at `approve()`, format `PO-DEMO-yyyyMMdd-####`), falling back to `draftNo` before approval. Never sent to Legacy/G-SYS in any form.
- **BR-08 (正式PO番号 / Official PO No.)**: `PortalOrder.officialPoNo`, auto-numbered as Supplier Short Code (3) + Brand Short Code (3) + 3-digit Serial at Official PO Integration Request creation. This is the number Excel/PDF/Manufacturer Send/G-SYS use - never derived from or replaced by the Management No. Unchanged by Reissue.
- **Revision**: tracked as a separate integer (`OfficialPoIntegrationRequest.revisionNo`), never concatenated into a new combined PO-number string. Display convention: `<Official PO No.> / Rev.<NNN>`.

## 10. Known Limitations

- Domestic Recommended Qty Formula not yet finalized (still Legacy `MS_FORMULA`, unchanged this Phase)
- Software Keyboard not verified on a real device
- Icon Button real-device touch-target confirmation not performed
- Past Revision individual download is out of Phase1 scope
- Production Integration not performed (by design - see §11, §14)
- **Test Data Lifecycle (Cleanup-2 + Freeze Blocker-2, resolved for growth; existing ambiguous backlog remains)**: a safe, identifier-based physical-delete cleanup exists for `supplier_contact` (`@example.com` domain) and a subset of `mail_template` rows (`"Follow-up E2E Template %"` name pattern) - both 100%-confident Test-only markers. Separately, `manufacturer_channel` and `supplier_region_classification` (which have no content-based Test marker, so cannot use the same DELETE approach) now instead **reuse a prior run's Fixture row** rather than creating a new one every E2E run - verified to add zero new rows across repeated runs (Final Cleanup Report §7.2/§7.4). The already-accumulated ambiguous backlog for these two tables, and the majority of `mail_template`'s rows, is **not** retroactively cleaned - per the explicit rule "曖昧な条件によるDELETEは禁止" / "100%識別できない場合：削除しない", this is deliberate, not an oversight. `price_change_set` was audited too (Final Cleanup Report §7.3) and needed no new mechanism - it is ordinary workflow data already fully covered by the pre-existing Demo Reset TRUNCATE, same as `portal_order` itself.

## 11. Production Readiness Remaining

- Production Network
- Secrets management
- Production Spring profile (does not exist yet - `SafetyGuardEnvironmentPostProcessor` currently allows only `local`/`demo`/`test`)
- SafetyGuard extension/replacement for a real Production topology
- Legacy Production READ ONLY connection (real Legacy G-SYS, not the disposable Legacy Demo Instance)
- Supplier Master source confirmation
- Supplier/Brand short-code source confirmation (BR-08's numbering input)
- Official PO Import Folder (real placement, not Demo)
- SMTP (real send, not Demo/Logging adapter)
- EDI (real integration - format/API/auth all unresolved, explicitly out of every Phase so far)
- Actual Manufacturer Send
- Actual G-SYS Official PO Import confirmation
- Logging/Monitoring
- Backup/Recovery
- Integration Test (against real Legacy/G-SYS)
- Gulliver UAT
- Release Judgment

## 12. Explicit Non-Scope

Everything in §11, plus any new Business Capability beyond §3 - all Phase 2+ / Backlog.

## 13. Legacy Change

0 (this Phase, and every Phase so far - Legacy is READ ONLY by construction; see §4)

## 14. Production Connection

0 (this Phase never connects to Production - `SafetyGuardEnvironmentPostProcessor` structurally cannot reach it; see §4, §11)

## 15. Freeze Rule

After this Freeze, no new Phase1 Feature is added except to address a P0/P1 Production Readiness issue. Any new Feature Request goes to Phase2 / Backlog.

## 16. Post-Freeze Business Refinement (Implementation 1)

This is **not** a Freeze failure or a reopening of Phase1 scope - it is a small, explicitly-scoped Business Refinement round requested after the 9/17 Business Requirements Re-Audit (`docs/gops-20260917-business-requirements-re-audit.md`), addressing 3 concrete gaps the Re-Audit identified. Full detail, evidence, and test results: `docs/gops-post-freeze-business-refinement-implementation.md`. Summary:

- **Mobile Approval UX**: fixed the Order Detail header (title/back-link/status-chip overflow at 375-430px) and added a Mobile Card layout to the Order History / Approval Waiting List (previously the raw Desktop Table below `sm`). No Workflow/Permission change.
- **BR-08 Cleanup**: confirmed the manual PO number input UI was already functionally retired (BR-08's auto-numbering fully replaced it); removed the 2 remaining dead frontend error-handling branches referencing it.
- **Stockout / Restock Date**: added a Portal-only "再入荷予定" (Manual Expected Restock, editable, with an explicit "未定" state) alongside the pre-existing "入荷予定" (Legacy Expected Arrival, READ ONLY, from `TR_ARR`) - Legacy always wins display priority, Manual data is never overwritten, surfaced on Candidate List / SKU Detail (with edit) / Approval Detail / Stock-Sales.

Same absolute constraints as every prior round applied and were re-verified: 0 Legacy Source changes, 0 Legacy DB writes, 0 Production/UAT connection, 0 Production deploy, 0 SafetyGuard changes, no Supplier/Brand Data Model change, no Domestic Recommended Qty Formula change.

## 17. Post-Freeze Business Refinement 2 (Manufacturer Stockout Information Management)

Extends Implementation 1's §16 "再入荷予定" foundation into full Manufacturer Stockout Information Management - Stockout Status/Shortage Qty/Information Received Date/Contact Method/Business History, reflecting the real Gulliver process (Manufacturer stockout information arrives via phone/email/Order response at unpredictable timing, not just a single restock date). Full detail: `docs/gops-manufacturer-stockout-information-management.md`. Summary:

- `sku_expected_restock` (Implementation 1) extended in place (V32 untouched, new `V33` migration) rather than a parallel table; new append-only `sku_manufacturer_stockout_history` table for a Business-facing timeline, separate from the unchanged `audit_event` Technical Audit.
- Legacy Expected Arrival and Manufacturer Stockout Information are now shown **side by side** whenever both exist, with a Conflict marker when they disagree - reconsidered from Implementation 1's strict "Legacy always wins, Manual hidden" rule per this round's explicit requirement that neither may fully hide the other. The API's merged `source`/`date` fields are kept for backward compatibility.
- New entry point on Supplier Response ("欠品情報として登録", prefilled from the Ordered/Confirmed Qty difference) - a deliberate, separate write, never auto-created from a mere quantity difference.
- Legacy's own `ITEM_STATUS`/`DISCON` (廃番) concept was audited and confirmed already fully surfaced via the existing `ItemStatusChip` - kept entirely separate from the new Manufacturer Stockout Status, per the explicit instruction not to independently merge them.

Same absolute constraints re-verified: 0 Legacy Source changes, 0 Legacy DB writes, 0 Production/UAT connection, 0 Production deploy, 0 SafetyGuard changes, no Supplier/Brand relationship change, no Official PO Short Code Master redesign, no Domestic Recommended Qty Formula change.

## 18. Post-Freeze Technical Stability Audit

Audit/stability-fix round, no business feature change - full root-cause analysis of the Full E2E Suite's 5 intermittent (full-suite-only) failures, previously only classified as "existing flaky tests." Found and fixed 4 genuine, distinct causes (a React Query cache-consistency gap in Official PO Integration, a missing stable per-row test hook on the Order History Desktop table, a shared hardcoded PO-number test literal with no mutual defense between the E2E and Backend test suites, and a racy forced double-click simulation) - none were real business-logic bugs. Also characterized (not fixed - a business decision, out of scope) a real constraint: Official PO auto-numbering silently exceeds its documented 3-digit format past the 999th PO for a given Supplier×Brand pair. Full detail: `docs/gops-post-freeze-e2e-stability-audit.md`.

## 22. Git (Final Commit)

**Final Commit**: `f6a9d069dd02f8d9550234fc9d7626a8a8e95e2b` (Post-Freeze Technical Stability Audit - Full E2E flaky test root-cause fix, see `docs/gops-post-freeze-e2e-stability-audit.md`). Prior Final Commits: `d90db9922902f3d3db0092a92a6c9f133adf3a31` (Post-Freeze Business Refinement 2 - Manufacturer Stockout Information Management, see `docs/gops-manufacturer-stockout-information-management.md`), `f80ea216d68eda343cd7d39e47d12244e03bf19f` (Post-Freeze Business Refinement / Implementation 1 - see `docs/gops-post-freeze-business-refinement-implementation.md`), `71ad25d1433e2e23e64df3348759e4424c015df2` (Freeze Blocker Final Fix round; the preceding round's Final Commit was `2816ce35fd607c16c2d6dc6def66649ae809cf78` - see `docs/gops-phase1-final-cleanup-report.md` §7 for that round's change list, §1-6 for the round before it)
