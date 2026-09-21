# G-OPS Stage 5 — Controlled Production Snapshot UI Validation (Read-Only)

Scope: connect G-OPS's Legacy Adapter to the 2026-09-16 Production
Snapshot for the first time (READ ONLY) and validate whether G-OPS's
UI/Query/Workflow hold up at real Production scale and structure. **This
Stage did not reach live UI validation** — two independent blockers were
found before any browser session against the Snapshot could run. Per
instruction, neither was fixed. What follows documents both blockers plus
everything that *was* validated directly via read-only, aggregate-only SQL
against `gops_snapshot_audit`.

## 1. Baseline

Stage 4 Implementation Commit `2c8bf81` (approved). No code from that
commit was modified this Stage.

## 2. Safety Gate

Restarted the isolated `gsys-prod-snapshot-20260916` container (was
`Exited`, cleanly stopped after Stage 3B, data/volume preserved). Before
any query: `SHOW GRANTS FOR 'gops_snapshot_audit'@'%'` —

```
GRANT USAGE ON *.* TO `gops_snapshot_audit`@`%`
GRANT SELECT ON `goo_prod_snapshot_20260916`.* TO `gops_snapshot_audit`@`%`
```

SELECT-only, scoped to exactly one database, confirmed before any other
action this Stage. No write, migration, or schema-change statement was
issued against the Snapshot at any point.

## 3. Blocker 1 — Application Safety Guard vs. Application-Code Freeze

`SafetyGuardEnvironmentPostProcessor.java` (built and verified across
earlier Stages) hard-codes `ALLOWED_LEGACY_DB_NAMES = Set.of("legacy_demo")`
and refuses to start the application if the Legacy JDBC URL's database
name is anything else — by design, per its own Javadoc, to prevent ever
pointing at `goo` (the real Production schema name) or anything
unapproved. Connecting the Legacy Adapter to `goo_prod_snapshot_20260916`
(as this Stage requires) trips that guard, and this Stage's own
instructions separately prohibit any Application code change. These two
requirements are in direct conflict.

Per your explicit direction, a temporary, clearly-scoped allowlist
addition (limited to this validation session, reverted before any commit)
was the intended path. That specific file edit was itself denied by Claude
Code's own auto-mode safety classifier — a separate permission layer from
your approval — and per the standing instruction not to route around a
denied action through another tool, no workaround was attempted. **No
change was made to `SafetyGuardEnvironmentPostProcessor.java` or any other
Application file; `git status`/`git diff` confirm this file is unmodified
at the end of this Stage.**

## 4. Blocker 2 — `MS_ITEM` Table-Name Case Mismatch (Independent of Blocker 1)

Discovered via direct `SHOW TABLES` against the Snapshot: every table is
lowercase (`ms_stk`, `ms_comm`, `tr_po`, `tr_po_dtl`, `tr_arr`, etc.)
**except `MS_ITEM`, which is uppercase.** Both the Snapshot and the Legacy
Demo instance report `lower_case_table_names=0` (case-sensitive
comparison), and Legacy Demo's own item table is genuinely lowercase
(`ms_item`) — confirming this is a real naming difference between the two
environments, not a client/tooling artifact.

Every one of G-OPS's Legacy read queries hardcodes lowercase `FROM
ms_item`/`JOIN ms_item` (confirmed present in `PriceReadQuery.sql`,
`RecommendedQtyReadQuery.sql`, `WarehouseStockBySkuQuery.sql`,
`WarehouseStockListCountQuery.sql`, `WarehouseStockListQuery.sql` — the
Candidate List, Price Change, Stock/Sales, and Arrival read paths). Direct
SQL confirms `SELECT ... FROM ms_item` fails against the Snapshot
(`Table 'goo_prod_snapshot_20260916.ms_item' doesn't exist`) while `FROM
MS_ITEM` succeeds. **This means essentially every G-OPS screen that
touches item data would fail outright against real Production's actual
table naming — independent of, and in addition to, Blocker 1.** This is a
real structural finding, not an artifact of this Stage's own work, and is
recorded as this Stage's most severe finding (§13).

**Decision (per your direction)**: given Blocker 2 would fail nearly all
UI screens even if Blocker 1 were resolved, no further attempt was made to
force a live application connection. The remainder of this Stage's
validation was completed via direct, read-only, aggregate SQL against
`gops_snapshot_audit` instead, substituting for what a live UI comparison
would have shown wherever the underlying formula/logic could be checked
without the application itself.

## 5. Connection Architecture / Data Copy

Not reached — G-OPS was never actually started against the Snapshot (§3).
No Production data was copied to the Portal DB, Demo DB, or any test
fixture. No screenshot was taken (no UI session existed to screenshot).

## 6. Formula-Level Validation (Direct SQL, Substituting for UI Comparison)

All queries below use `gops_snapshot_audit` (SELECT-only). All results are
aggregate counts; no individual SKU, order, or personal data appears
below or was retained anywhere.

**Current Stock (`PHISICAL_QTY`, Stage 4 §2)** — `SUM(stk_qty)` over
`wh_cd IN ('4','5','6','7','8','10','11','12','15')`:
- 1,051,506 rows fall in the included warehouse set; 350,502 in the
  excluded set (`9`/`13`/`14`), holding 342 total units — matches Stage
  3B's own earlier validation exactly (consistency cross-check passed).
- Of 116,835 items with at least one included-warehouse row: 9,731 have
  non-zero physical stock (matches Stage 3B's 8.3% finding exactly);
  1,969 of those have stock spread across 2+ included warehouses (the
  multi-warehouse case), 7,762 have it in exactly one.
- Zero-stock, non-zero-stock, and multi-warehouse cases are all
  confirmed present and computable in real data with no NULL-handling
  exception.

**Logical Qty (`PHISICAL_QTY + open_po + open_ship`, ARR_QTY excluded,
Stage 4 §3)**:
- 3,506 items have open PO qty > 0; 2,590 have open Ship qty > 0; 2,682
  have open Arrival qty > 0 in real data (all three fields are populated
  and meaningful, not edge cases).
- **249 real items have both open PO qty > 0 and open Arrival qty > 0
  simultaneously** — concrete, real-data confirmation that including
  ARR_QTY in `LOGICAL_QTY` would double-count against still-open PO qty
  for a material number of items. This validates Stage 3B's exclusion in
  practice, not only in Legacy source theory.

**DISCON (Stage 4 §7)**:
- 39,551 of 116,842 items have `DISCON=1` (matches Stage 3's earlier
  count exactly).
- Of those 39,551: 32,982 have `ITEM_STATUS` NULL/blank, 6,560 have
  `ITEM_STATUS='NEW'`, and **zero** have `ITEM_STATUS='DISCON'` literally
  — stronger than Stage 3's "ほぼ存在しない" (almost never): it is, in this
  Snapshot, *never*. Confirms `discon`-first Chip logic is necessary, not
  optional.

**Multi-Supplier Brand (Candidate Selection UX, Stage 4 §5)**:
- Of 802 Brands with PO history, 222 (27.7%) span 2+ distinct Suppliers —
  corroborates Stage 2's 27.1% figure. Maximum observed: one Brand spans
  34 distinct Suppliers.

**Mixed Brand realism check (§14, informational only — no enforcement
decision made)**:
- Of 310 Suppliers with PO history, 77 (24.8%) have supplied items across
  2+ distinct Brands; one Supplier's PO history spans up to 103 distinct
  Brands. This confirms same-Supplier/multi-Brand selection is a common,
  everyday real-data shape, not a rare edge case — reinforcing why the
  Mixed Brand decision remains a live Gulliver Confirmation Item rather
  than something to guess at.

## 7. Candidate Pagination Scale Framing (Not UI-Validated)

Brand-size distribution, filtered by the same `del_flg` condition G-OPS's
own query applies (`del_flg IS NULL OR del_flg = 0`) — i.e., the real
worst case an actual paginated query would face:

| Bucket | Brand count | Max SKUs in bucket |
|---|---|---|
| Small (≤20) | 244 | 20 |
| Medium (21-200) | 276 | 198 |
| Large (201-2,000) | 50 | 1,188 |
| Very Large (>2,000) | 1 | 3,880 |

**Correction to a previously-cited figure**: Stage 2/4 cited "18,596 SKUs"
as the largest-Brand worst case. That figure is the *raw*, unfiltered
`MS_ITEM` count for one Brand code; the same Brand's *active* (non-deleted)
count is only 3,880 — the raw figure included 14,716 soft-deleted rows
(79% of that Brand's raw total). Checking the next few largest raw-count
Brands found several with **0% active items** (100% soft-deleted) — a
Data Quality nuance, not a defect: a naive "largest Brand by raw
`MS_ITEM` count" query would badly overstate real UI-facing scale. The
true worst-case active Brand is 3,880 SKUs, still a meaningful pagination
stress case at 194 pages (size=20) but a very different number than
18,596. No UI pagination test was performed against this or any Brand
(Blockers 1/2).

## 8-12. UI-Dependent Validations (A/B/C/D-partial/E-partial/F-partial/G, Performance, Visual)

**Not performed.** Every section requiring a live G-OPS UI session
against the Snapshot (Brand Entry navigation, live Candidate/Price Change
pagination clicks, live Supplier-lock Chip behavior, live current-stock/
recommended-qty *display* comparison, live DISCON Chip rendering,
response-time measurement, and all screenshots) was blocked by §3/§4
before any browser session could start. What each Validation's
*underlying formula/data* would have shown is covered in §6-7 above via
direct SQL wherever that substitution was meaningful; none of it confirms
or refutes actual UI rendering, navigation, or timing.

## 13. Findings (DO NOT FIX — Classification Only)

| # | Finding | Severity | Category |
|---|---|---|---|
| 1 | `MS_ITEM` is uppercase in real Production data; every G-OPS Legacy query hardcodes lowercase `ms_item`. Blocks nearly every item-touching screen (Candidate List, Price Change, Stock/Sales, Arrival) against real Production table naming. | **P0** | **REAL-DATA BLOCKER** |
| 2 | `SafetyGuardEnvironmentPostProcessor`'s `ALLOWED_LEGACY_DB_NAMES` allowlist has no sanctioned path for a controlled, temporary Snapshot-validation connection without an Application code change — a legitimate, foreseeable operational need (this Stage) that the current design doesn't accommodate. | P2 | UX (developer/operator-facing) |
| 3 | Stage 2/4's previously-cited "largest Brand = 18,596 SKUs" overstates real UI-facing scale by not excluding soft-deleted rows; true worst case (active only) is 3,880. Several other large-by-raw-count Brands are 100% soft-deleted. | P3 | DATA QUALITY |
| 4 | 24.8% of real Suppliers span multiple Brands (up to 103) — confirms Mixed Brand selection is a common real scenario, not an edge case; no enforcement decision made this Stage. | P2 | GULLIVER CONFIRMATION |
| 5 | 27.7% of real Brands span multiple Suppliers (max 34) — corroborates Stage 2/4's existing Mixed-Supplier-UX design basis; no new risk found, confirmed as expected. | Informational | NO ISSUE |

## 14. Mixed Brand

Not enforced this Stage, as instructed. §6/§13 (Finding 4) records the
real-data evidence that same-Supplier/multi-Brand selection is common;
this remains an open Gulliver Confirmation Item, not decided here.

## 15. Full E2E Note

Not applicable — no Application code changed this Stage, so no
regression run was performed or required, per instruction.

## 16. Documentation

This file: `docs/real-data-audit/gops-stage5-controlled-snapshot-ui-validation.md`.
No individual Production data (SKU, order, personal data) appears above —
aggregate counts, formula-level spot checks, and anonymized bucket labels
only, per instruction. Brand codes surfaced during ad-hoc exploration
(e.g., identifying which single Brand carries the largest raw SKU count)
are deliberately not named here, consistent with the "aggregate/count/
timing/anonymized pattern only" requirement.

## 17. Remaining Risks / Next Recommendation

- **Blocker 1 and 2 are both unresolved.** Neither can be fixed within
  this Stage's own rules (no Application code change; Blocker 1's fix
  attempt was independently denied at the tool-permission layer).
- Recommend that Techlead (ChatGPT) review both blockers together: (a)
  whether/how `MS_ITEM`'s case should be reconciled (a Legacy-side fact
  to confirm — is this consistent across all real Production, or an
  artifact of this one Snapshot/export?), and (b) whether the Safety
  Guard's allowlist mechanism should gain a deliberate, reviewed exception
  path for controlled Snapshot validation sessions, decided outside an
  active session rather than requested ad hoc.
- Until Blocker 1 is resolved (at minimum), no live G-OPS-to-Snapshot UI
  validation is possible, regardless of the permission question.
- Stage 4's own implementation (current_stock/logical_qty formulas,
  DISCON propagation, Mixed-Supplier-UX basis) is *formula-level*
  corroborated by this Stage's direct SQL (§6) — the underlying business
  logic appears sound against real data; what remains unverified is
  purely whether the *application* can actually execute it, which this
  Stage could not confirm.

The Snapshot container (`gsys-prod-snapshot-20260916`) was stopped at the
end of this Stage; its data/volume was not deleted.
