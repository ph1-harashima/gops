# G-OPS Stage 5I: Active Ordering Population & Dashboard Freshness Architecture Audit

AUDIT / DESIGN / MEASUREMENT ONLY. No code, migration, cache, Read Model,
Dashboard, or Business Rule change was made in this Stage. All measurements
were taken with the backend connected to the same read-only Production
Snapshot (`gsys-prod-snapshot-20260916-ci`, SELECT-only `gops_snapshot_audit`)
Stage 5B-5H used; the `GET /api/dashboard` measurements reuse the existing,
unmodified `calc4` pipeline as-is — no simulated/mocked logic. Baseline:
commit `e01e240` (Stage 5H).

## 1. Executive Summary

**The core question this Stage asked — "is it Business-correct to run calc4
over all 116,842 SKUs on every Dashboard load?" — has a clear, data-backed
answer: no population-recency filter, at any window from 1 to 12 months,
can safely replace the current full-catalog evaluation without discarding a
large, real, currently-visible set of order candidates.** At the most
generous window tested (Brand active within 12 months), **3,982 real
candidates (23.6% of today's 16,863) would silently disappear** from
`candidateCount` — items whose Brand hasn't had a fresh PO in the last year
but which are, right now, in a state (stock/sales/formula) that calls for
reordering. At a 1-month window the loss is 76.2%. This is the central,
decision-driving finding of this Stage (§9).

A second, independent finding narrows the picture further: **only 47,280
of the Snapshot's 116,842 `MS_ITEM` rows are even non-deleted** — the
"116,842 SKU" figure repeated across Stage 5D-5H was always the *physical*
row count, not the *evaluated* population; `calc4`'s actual input has been
~47,280 rows (`del_flg IS NULL OR del_flg = 0`) since RC-A. This doesn't
change any of this Stage's conclusions, but it corrects the framing for
Stage 5J.

A third finding: **no recency-based population filter, even at its most
aggressive (1-month), reaches this Stage's ~1-2s performance target**,
because SKU-count reduction alone is not enough — even the smallest tested
population (9,070 SKU) linearly projects to ~3s, well above target, and
that population also has the worst (76.2%) false-exclusion rate. Speed and
safety point the same direction: population-based real-time filtering is
not a viable path on its own.

**The 9/17 Business Context figures this Stage was asked to reconcile
against (~150 orders/month, ~70 manufacturers, ~200 brands) could not be
confirmed against any primary source in this repository** — see §2. They
are carried through this report labeled exactly as the instruction
requires: **Business Meeting Information / Customer Confirmation
Candidate**, not CONFIRMED. Independently, the Snapshot's own PO history
(§6-§7) shows a real recent operational scale in a broadly comparable
order of magnitude (median 6 PO/order-day, 95-196 distinct active
Suppliers depending on window, 134-420 distinct active Brands depending on
window) — closer to, but not identical to, the 9/17 figures.

## 2. 9/17 Business Context — Source Verification

**Finding: the figures in this Stage's own instruction (~150 domestic
Brand orders/month, ~70 manufacturers, ~200 Brands) do not appear in any
document in this repository, including the one file whose name matches
the date.**

- `docs/20260917_打合せ議事メモ.pdf` — the only repository file dated
  9/17 — was already investigated in a prior session
  (`docs/gops-20260917-business-requirements-re-audit.md` §1, read in this
  Stage): it is a Zoom AI-generated summary of a **single-attendee,
  unrelated business discussion** ("T.Harashima," an EC/product-
  registration system, a cost estimate) with **zero mentions of Gulliver,
  G-SYS, Official PO, Supplier, or Brand** — it shares its filename date
  with the real Gulliver 9/17 session by coincidence only, and that prior
  audit explicitly warns not to use it as a 9/17 source. This Stage
  independently re-read it in full and reached the same conclusion. Its
  own text carries an AI-disclaimer ("AIの出力結果は正しいとは限りません")
  and, even taken at face value, states different numbers than this
  Stage's hypothesis anyway (現在150-200件の月次処理量; 最初のフェーズと
  して300ブランドの日次発注機能; 月50-770点から200-250点への増加) — none
  of which is "約150回/月, 約70社, 約200ブランド" precisely.
- The actual confirmed 9/17 Gulliver record in this repository is
  `docs/gulliver-20260917-confirmed-business-rules.md` (BR-01–BR-10,
  re-verified in Stage 5D's own re-audit as 9/10 IMPLEMENTED). **None of
  BR-01–BR-10 mentions order volume, Supplier count, or Brand count.**
- No other document in this repository (searched by keyword across all of
  `docs/`) contains "150," "70社," or "約200ブランド" in this context.

**Classification per this Stage's own instruction: Business Meeting
Information / Customer Confirmation Candidate — NOT CONFIRMED.** This is
carried into §17's Gulliver Confirmation Questions as an explicit,
first-priority item: the figures should be re-confirmed directly with
Gulliver, not sourced to this PDF.

## 3. Supplier Activity Analysis

602 Suppliers total (`ms_comm CATE_ID='MS_SUPPL'`), measured against
Snapshot "now" = 2026-09-16 (the Snapshot's own latest `tr_po.ordr_date`):

| Segment | Count | % of 602 |
|---|---|---|
| DEL_FLG = 1 (deleted) | 21 | 3.5% |
| DEL_FLG NULL/0 (not deleted) | 581 | 96.5% |
| Not deleted, PO history ever | 308 | 51.2% |
| Not deleted, PO within 12 months | 196 | 32.6% |
| Not deleted, PO within 6 months | 179 | 29.7% |
| Not deleted, PO within 3 months | 154 | 25.6% |
| Not deleted, PO within 1 month | 95 | 15.8% |

**273 not-deleted Suppliers (47.0%) have never had a PO at all.** DEL_FLG
alone captures almost none of this — the overwhelming majority of "never
ordered from" Suppliers are simply not flagged deleted.

## 4. Brand Activity Analysis

1,504 Brands total (`ms_comm CATE_ID='MS_BRAND'`):

| Segment | Count | % of 1,504 |
|---|---|---|
| DEL_FLG = 1 (deleted) | 46 | 3.1% |
| DEL_FLG NULL/0 (not deleted) | 1,458 | 96.9% |
| Not deleted, PO history ever | 802 | 53.3% |
| Not deleted, PO within 12 months | 420 | 27.9% |
| Not deleted, PO within 6 months | 345 | 22.9% |
| Not deleted, PO within 3 months | 256 | 17.0% |
| Not deleted, PO within 1 month | 134 | 8.9% |
| Not deleted, has ≥1 non-deleted Item | 571 | 38.0% |

**Relationship to the 9/17 "~200 Brand" figure (§2, unconfirmed)**: the
12-month-active count (420) is ~2x the hypothesis; the 3-month count (256)
and 1-month count (134) bracket it from both sides. **Data alone cannot
produce a single authoritative "~200" figure** — every recency window
tested gives a materially different number, and none is obviously "the"
right cutoff without a Business decision (see §17 Q1/Q3). This is
discussed further as the central risk in §9.

## 5. SKU Population Funnel

| Stage | SKU Count | Cumulative % of 116,842 |
|---|---|---|
| All `MS_ITEM` rows | 116,842 | 100% |
| Not Deleted (`DEL_FLG` NULL/0) | 47,280 | 40.5% |
| Not Deleted + Not Discontinued (`DISCON` NULL/0) | 37,620 | 32.2% |
| Not Deleted, Brand active (PO) within 12 months | 33,419 | 28.6% |
| Not Deleted, Brand active (PO) within 6 months | 27,303 | 23.4% |
| Not Deleted, Brand active (PO) within 3 months | 21,107 | 18.1% |
| Not Deleted, Brand active (PO) within 1 month | 9,070 | 7.8% |
| Not Deleted, Item's own PO ever | 28,288 | 24.2% |
| Not Deleted, Item's own PO within 12 months | 14,569 | 12.5% |
| Not Deleted, Item's own PO within 1 month | 2,212 | 1.9% |

No exclusion Business Rule is proposed here — this is purely the count at
each condition, as instructed. Two funnel branches are shown (Brand-level
PO recency vs. the Item's own PO recency) because they diverge sharply:
item-level recency is a much more aggressive cut (14,569 at 12 months vs.
33,419 at Brand-level) — consistent with most Items being ordered as part
of a Brand-level PO batch rather than individually, so item-level recency
alone would be an even riskier population-restriction basis than
Brand-level (§9).

## 6. Monthly PO Volume (Last 12 Months)

| Month | PO Headers | PO Detail Lines | Distinct Suppliers | Distinct Brands | Distinct SKUs |
|---|---|---|---|---|---|
| 2025-09 | 44 | 664 | 31 | 35 | 596 |
| 2025-10 | 100 | 1,710 | 42 | 72 | 1,613 |
| 2025-11 | 72 | 664 | 28 | 34 | 546 |
| 2025-12 | 103 | 2,157 | 45 | 72 | 1,737 |
| 2026-01 | 103 | 1,406 | 54 | 80 | 1,267 |
| 2026-02 | 99 | 1,614 | 57 | 78 | 1,530 |
| 2026-03 | 152 | 3,479 | 71 | 107 | 3,052 |
| 2026-04 | 169 | 3,005 | 92 | 127 | 2,759 |
| 2026-05 | 139 | 2,924 | 84 | 109 | 2,778 |
| 2026-06 | 214 | 3,707 | 110 | 145 | 3,322 |
| 2026-07 | 208 | 2,693 | 96 | 131 | 2,358 |
| 2026-08 | 162 | 2,455 | 99 | 131 | 2,347 |
| 2026-09 (partial, through 9/16) | 86 | 1,103 | 55 | 70 | 1,072 |

**Clear growth trend**: PO Headers/month roughly quadrupled from 2025-09
(44) to the 2026-06 peak (214), broadly consistent with the 9/17 meeting's
own qualitative theme (order volume growing, operations becoming
burdensome) even though the specific PDF cited for that theme is not a
Gulliver source (§2). Recent months (Jun-Aug 2026) average ~195 PO
Headers/month — the same order of magnitude as the unconfirmed "~150"
figure, somewhat higher. Whether "150" refers to PO Headers, Brand-level
order actions, or something else **cannot be determined from Source alone**
(§17 Q4) — PO Headers is the closest matching Legacy unit, but Supplier
count/month (55-110) and Brand count/month (70-145) are each also
plausible readings depending on what "1 order" meant in that conversation.

## 7. Daily Operational Scale

Computed over the 273 distinct calendar days with at least one PO in the
last 12 months (of ~365 possible — consistent with a mostly-weekday
operation, not confirmed):

| Metric | Average | Median | P90 | Max |
|---|---|---|---|---|
| PO Headers/order-day | 6.0 | 6 | 11 | 19 |
| Distinct Suppliers/order-day | 5.5 | - | - | 18 |
| Distinct Brands/order-day | 5.8 | - | - | 18 |
| Distinct SKUs/order-day | 98.1 | - | - | 699 |

**20 business days × 6.0 PO/order-day ≈ 120/month** by this measure,
in the same range as both the unconfirmed "~150/month" and this Stage's
own "20 business days × 7.5 = 150" arithmetic — median PO/order-day (6)
is reasonably close to 7.5 but not identical. The Operational Working Set
G-OPS actually needs to serve well on a typical day is small: on a median
day, ~6 POs across ~5-6 Suppliers/Brands and well under 100 SKUs — several
orders of magnitude below the 47,280-SKU population `calc4` evaluates on
every Dashboard load.

## 8. Candidate Population Simulation

**Methodology**: no code was changed to measure this. Populations A and
the Brand-recency variants reuse the real, unmodified `GET /api/dashboard`
response (`candidateCount` is genuinely `calc4`-derived per Brand, summed)
cross-referenced against Brand PO-recency computed directly from
`tr_po`/`tr_po_dtl` via read-only SQL — every `candidateCount` value below
for these populations is a **real, measured** number, not an estimate.
Execution time for Population A is directly measured; other populations'
execution time is a **linear projection** from Population A's measured
per-SKU cost (~15.5s ÷ 47,280 SKU ≈ 0.33ms/SKU, consistent with Stage
5D-5H's own finding that `calc4` cost scales with evaluated-row count) —
labeled explicitly as **projected, not measured**, since measuring a
true population-filtered execution would require a code change out of
this Stage's scope.

| Population | SKU Count | candidateCount | % of Population A | Execution Time |
|---|---|---|---|---|
| **A. Current All (not-deleted)** | 47,280 | **16,863** (measured) | 100% | **~13.6-20.2s** (measured, run-to-run variance) |
| Active Brand, 12 months | 33,419 | **12,881** (measured) | 76.4% | ~11.0s (projected) |
| Active Brand, 6 months | 27,303 | **10,440** (measured) | 61.9% | ~9.0s (projected) |
| Active Brand, 3 months | 21,107 | **8,561** (measured) | 50.8% | ~6.9s (projected) |
| Active Brand, 1 month | 9,070 | **4,018** (measured) | 23.8% | ~3.0s (projected) |
| B. Not Deleted + Not Discontinued | 37,620 | ~13,400 (rough estimate, see note) | ~79% (est.) | ~12.4s (projected) |
| D. Recent Active Supplier (item-level) | not separately measurable from Dashboard's per-Brand breakdown; SKU-count only, see §5 item-own-PO rows | not measured | - | - |

Note on Population B's `candidateCount`: Dashboard's current computation
does **not** filter on `DISCON` at all (confirmed by code reading — no
`DISCON` predicate anywhere in `DashboardCandidateInputsQuery.sql` or
`RecommendedQtyReadQuery.sql`), so there is no existing per-Brand
breakdown to cross-reference against a Discon filter the way there is for
Brand recency. The ~13,400 figure is a rough proportional estimate
(SKU-count ratio applied to Population A's `candidateCount`), explicitly
**not a measured value** — a real answer would need either a code change
or an ad hoc script duplicating `calc4`'s own logic outside the
application, neither of which is in this Stage's scope.

**No population tested reaches the ~1-2s target.** Even the most
aggressive real population (Active Brand, 1 month: 9,070 SKU) projects to
~3s — and that same population has the worst false-exclusion rate (§9).
SKU-count reduction via recency filtering, even where Business-acceptable,
does not appear sufficient **on its own** to hit this Stage's performance
target through real-time calculation alone.

## 9. False Exclusion Analysis (Most Important Finding)

Directly measured (not estimated) against real `calc4` output, via the
same Brand-recency cross-reference as §8:

| Active Brand Window | candidateCount Retained | candidateCount Lost (False Exclusion) | False Exclusion Rate |
|---|---|---|---|
| 1 month | 4,018 | 12,845 | **76.2%** |
| 3 months | 8,561 | 8,302 | **49.2%** |
| 6 months | 10,440 | 6,423 | **38.1%** |
| 12 months | 12,881 | **3,982** | **23.6%** |

**At every window tested, a large, real fraction of today's genuine order
candidates belongs to a Brand that has not had a PO recently.** Even at
the most generous 12-month window, nearly 1 in 4 current candidates would
silently vanish from the Dashboard/Candidate population. These are, by
definition, real `recommendedQty > 0` items — stock/sales/formula
conditions the existing, unmodified Business logic already says warrant
reordering — being excluded purely because the Brand happens not to have
ordered recently, which is exactly the risk this Stage's instruction
warned about (§9's own framing: 最近発注していないから対象外, という単純
Ruleは危険).

**Conclusion: a simple "recent PO = active" rule, at any window tested, is
not safe to use as a performance shortcut for real-time calculation.**
This is the single strongest, most decision-relevant finding in this
Stage, and it is measured from real production data, not inferred.

## 10. Existing Active/Inactive Fields (Legacy Master Audit)

| Table | Field | Usable as Active/Inactive? | Finding |
|---|---|---|---|
| `ms_comm` (Supplier/Brand) | `DEL_FLG` | Partially | Binary only; 96%+ of both Suppliers and Brands are DEL_FLG NULL/0 regardless of real order activity (§3/§4) - far too coarse alone |
| `ms_comm` (Supplier/Brand) | `VAL_1`-`VAL_10` | No | Sampled directly: values are ARGB-style hex color codes (e.g. `FF00FF00`), a Legacy UI display/highlight attribute, not a Business status |
| `ms_item` | `DEL_FLG` | Yes, coarse | Meaningful split confirmed (47,280 / 116,842 not deleted) - already the only filter `calc4`'s current query applies |
| `ms_item` | `DISCON` | Yes, but not currently used for population | Meaningful, clean binary (17,555 NULL / 20,065 = 0 / 9,660 = 1 among not-deleted); not currently filtered by `calc4`'s query at all |
| `ms_item` | `ITEM_STATUS` | **No - data quality issue** | Sampled directly: dominated by NULL (61,573), blank string (30,501), and a long tail of apparently corrupted numeric values (e.g. "26000.0", "NEW ID") that do not look like a coherent status enum - not usable as-is without a separate Legacy data-quality investigation |
| `ms_item` | `STK_QTY_STATUS` | No | 47,271 of 47,280 not-deleted rows are NULL - effectively unpopulated |
| `ms_item` | `SALE_FLG` | Unclear | Mostly 0 (41,213) with very few 1 (933) among not-deleted rows - shape suggests an EC-channel "currently listed for sale" flag, not an ordering-active flag; not confirmed |

**No existing Legacy field cleanly represents "is this Supplier/Brand/Item
currently part of active ordering operations."** `DEL_FLG` is the only
clean, non-corrupted signal at the Master (Supplier/Brand) level, and it
answers a different question ("was this ever formally removed") than
"is this currently active" - the vast majority of clearly-inactive
Suppliers/Brands (by PO recency) are never marked deleted at all.

## 11. Dashboard KPI Dependency

| KPI | Business Definition | Source | Table(s) | Calculation | FormulaParser |
|---|---|---|---|---|---|
| 発注候補 (candidateCount) | Count of SKUs where `calc4`-derived Recommended Qty > 0 | Legacy | `ms_item`, `ms_stk`, `ms_formula`, `tr_po_dtl`/`tr_po` (latest_po) | Per-SKU `calc4` (formula evaluation + region dispatch) | Yes - `FORMULA_11`-`14` |
| 欠品 (outOfStockCount) | `current_stock == 0` | Legacy | `ms_item`, `ms_stk` | SQL aggregate (`DashboardStockAggregateQuery.sql`) | No |
| 長期欠品 (longTermOutOfStockCount) | `current_stock == 0 AND open_po == 0` | Legacy | same | SQL aggregate | No |
| 発注作成中 (draftCount) | Portal Order status = DRAFT | Portal | `portal_order` | Java Stream filter over `portalOrderRepository.findAll()` | No |
| 承認待ち (awaitingApprovalCount) | Portal Order status = PENDING_APPROVAL | Portal | `portal_order` | same | No |
| メーカー回答待ち (awaitingSupplierCount) | Portal Order status = AWAITING_SUPPLIER | Portal | `portal_order` | same | No |
| 要確認 (attentionCount) | Portal Order `hasAttention` flag | Portal | `portal_order` | same | No |
| 問い合わせ中 | Follow-Up Case open | Portal | `follow_up_case` | Java Stream filter | No |
| 価格変更（下書き） | Price Change Set status = DRAFT | Portal | `price_change_set` | same pattern | No |
| Brand別内訳 | Same KPIs, grouped by Brand | Both | union of the above | Java Stream `groupingBy` | inherits candidateCount's Yes |

Every non-`candidateCount` KPI is either a pure Legacy SQL aggregate (cheap,
already fixed by RC-A) or a small Portal-side Java filter over an already-
bulk-fetched list (cheap at current Portal row counts). **`candidateCount`
is the sole KPI requiring per-row `calc4`/`FormulaParser` evaluation, and
is therefore the sole driver of Dashboard's ~13.6-20s cost.**

## 12. Legacy Refresh Timing

Traced from `docs/legacy-stock-sales-data-reverse-engineering.md` (an
existing, thorough reverse-engineering document re-read in full for this
Stage) and prior Phase audits - not re-derived from scratch:

| Table | Updated By | Trigger | Frequency |
|---|---|---|---|
| `ms_stk.SOLD_QTY` | `SlTempostarImportBatch` | Tempostar sales CSV via Import Folder | UNKNOWN - zero `@Scheduled`/cron-equivalent annotations found anywhere in Legacy source (re-confirmed this Stage's source doc); frequency is OS Task Scheduler-level, outside the codebase |
| `ms_stk` (Current Stock components) | Tempostar/Logizero push-pull via `InvTempostarStkUploadBatch` | Selenium WebDriver browser automation against Tempostar **and** Logizero admin screens | UNKNOWN |
| `ms_stk.STK_QTY_LAST_MONTH` | `MsMonthEndStockUpdate` | Month-boundary batch, gated by an `MS_COMM` flag (`MON_PRC_STK_UPD`) | **Structural anomaly found in Source**: no code anywhere sets this flag to "UPDATE" - the batch's own comment says it waits for an external "Stock Standard Upload" step whose owner/frequency is not in Source |
| `ms_stk.STK_QTY_LAST_MONTH` (alternate path) | `MsLastMonthQtyUpdate` | Manual Excel import, independent of the above | UNKNOWN frequency |
| `ms_comm` (Brand/Supplier Master) | Unknown import path | Not traced this Stage (out of the cited reverse-engineering doc's scope) | UNKNOWN |
| `ms_formula` | Not traced in the available Source | - | UNKNOWN |
| `tr_po`/`tr_po_dtl` | Official PO workflow (this application's own Portal, read-only from Legacy's perspective) plus whatever Legacy-native PO entry exists | Portal side is event-driven (on approval/send); Legacy-native path UNKNOWN | N/A (Portal side) / UNKNOWN (Legacy side) |
| `tr_arr` | Arrival/Stock-in import | Not traced this Stage | UNKNOWN |

**The "15分周期Import" cited in this Stage's own instruction (§12) could
not be found in any repository source** (searched explicitly) - classified
UNKNOWN/unconfirmed, same treatment as §2's business figures. The one
concrete, Source-confirmed structural fact most relevant to a future Read
Model's refresh design: **Legacy itself has no cron/scheduled-job
mechanism visible in source at all** - any "refresh cadence" observed in
practice is an operations/infrastructure-layer fact, not a code fact, and
must come from Gulliver/Ernest rather than further source reading.

## 13. Real-Time vs. Read Model Comparison

**PATH 1 - Active Population Real-Time Calculation**: Ruled out as a
standalone solution by this Stage's own data. Even setting aside the
False Exclusion problem (§9) entirely, no tested population reaches the
~1-2s target via linear projection (§8) - the smallest tested population
(9,070 SKU, 1-month Brand recency) still projects to ~3s, and that
population is also the least Business-safe (76.2% false exclusion). A
population narrow enough to hit ~1-2s would need to be smaller still,
compounding the false-exclusion risk further. Real-time calculation over
the **full, correct** population (whatever Gulliver confirms that to be)
remains too slow by itself.

**PATH 2 - Precomputed Aggregate / Read Model**: Better matches what this
Stage's data shows. A scheduled or triggered recompute of the **full,
correct** population (not a narrowed one) removes the speed/safety
tradeoff entirely - `candidateCount` keeps its full, correct meaning
(no false exclusions), and the Dashboard read path becomes a fast Portal
SELECT regardless of Legacy-side per-SKU calculation cost. The tradeoff
moves to staleness, not correctness - and Legacy's own refresh timing is
largely UNKNOWN (§12), so an aggressive refresh schedule (e.g. hourly)
is a safe, conservative default until Gulliver/Ernest can confirm actual
Legacy update cadence.

**Hybrid observation**: §7's Daily Operational Scale data (median 6
PO/order-day, ~100 SKU/order-day) suggests the *portion* of the catalog
that actually changes day-to-day is small - a future design could
recompute only Brands with **new PO activity since the last refresh**
(an actually-changed-data trigger, not a recency-based population filter)
without the False Exclusion risk, since it still eventually recomputes
everything, just prioritized. This is a design idea for Stage 5J, not
evaluated in depth here.

## 14. Preferred Architecture

Based on §8/§9/§13: **Precomputed Aggregate / Read Model, computed over
the full correct population**, is the architecture this Stage's own data
points toward - not because Active Population filtering is inherently
wrong, but because filtering by *recency* specifically (the only
population-narrowing signal this Stage could construct from existing
Legacy data - see §10) is demonstrably unsafe at every window tested.
This does not preclude Gulliver later confirming a different, non-recency-
based Active definition (e.g. an explicit Business-maintained Active
Brand list) that could make Path 1 viable after all - but that would be a
new Business Rule, not something derivable from Legacy data as it exists
today, and is out of this Stage's scope to invent (§14's own "禁止"
instruction: never define Active purely to make it faster).

## 15. Brand List Implication

1,504 total Brands; only 571 have any non-deleted Item at all (§4). If
Gulliver confirms an Active Brand concept (§17 Q1-Q3), defaulting the
Brand List's *display* to Active Brands would be a reasonable UX
direction - but exactly which of §4's recency windows (or a different,
Business-maintained definition) should define "Active" for **display**
purposes is a separate, lower-stakes question from candidateCount's
**calculation** population (§9's finding applies to the latter, not
necessarily the former - hiding an inactive Brand row from a list is
reversible/low-risk; silently excluding it from candidateCount is not).
No UI change is made in this Stage, per instruction.

## 16. Performance Target Re-Assessment

| Area | Target | This Stage's Finding |
|---|---|---|
| Dashboard | ~1s, max ~2s | Not reachable via real-time population filtering alone (§8/§13) - requires Path 2 |
| Brand List | ~1s | Same call as Dashboard - same conclusion |
| Candidate | Maintain Stage 5H | Unaffected by this Stage (no candidate-path code touched) - Stage 5H's ~0.36-1.3s results stand |

## 17. Gulliver Confirmation Questions

Only questions Source/Data cannot answer:

1. **9/17 の figure sourcing (new, discovered this Stage)**: The "約150回
   /月, 約70社, 約200ブランド" figures used to frame this Stage's audit do
   not appear in any record in this repository, including the file dated
   9/17 (confirmed to be an unrelated document - §2). Please confirm
   whether these figures come from a source outside this repository, and
   if so, share it so it can be reconciled against the real PO history in
   §6-§7 (which shows a broadly similar but not identical scale).
2. Is "約200 Brand" intended as "the Brand set currently managed as
   active ordering targets"? If so, is that set Business-maintained
   (an explicit list) or intended to be inferred from data (and if the
   latter, from what signal - since no existing Legacy field cleanly
   represents this per §10)?
3. Does a current Business Rule exist for managing Supplier/Brand as
   Active/Inactive for ordering purposes? (§10 confirms no such field
   exists in Legacy Master data today.)
4. Can a Brand/Supplier that has not ordered in a long time (a "recent
   PO" definition confirms no fixed answer - see the range in §4/§9)
   become an order candidate again based on stock/sales conditions alone,
   without a fresh PO first? (This Stage's §9 finding assumes yes, since
   the current unmodified `calc4` logic already answers yes for 3,982+
   real SKUs at the 12-month window - confirming this assumption directly
   would resolve the central tension in this report.)
5. Does "月150回" refer to PO Headers, Brand-level order actions, or
   individual order-preparation work sessions? §6 shows these three units
   diverge materially in the real data (recent PO Headers/month: ~195;
   recent distinct Brands/month: 70-145).

## Stage 5J Proposal (Not Started This Stage)

Contingent on Gulliver's answers to §17, propose Stage 5J as a design-only
Stage (still no implementation) to specify the Precomputed Aggregate /
Read Model's refresh strategy in detail: full-population recompute
frequency (needs §12's UNKNOWNs resolved or a conservative default
chosen), the "changed-since-last-refresh" hybrid idea from §13, staleness
display (Last Calculated Timestamp, per Stage 5G's own §5 design), and a
concrete Portal DB schema for the Read Model - before any Stage 5K
implementation is proposed.

## 19. Final Judgment

**B. PRECOMPUTED AGGREGATE REQUIRED**

Justification: §9's False Exclusion measurement (real, not estimated)
shows every recency-based population narrow enough to matter still
discards a large, genuine fraction of current order candidates (23.6% at
the most generous window tested), and §8's linear projection shows even
the narrowest, least-safe population does not reach the performance
target on its own. Both the safety argument and the speed argument point
away from Path 1 (Real-Time Active Population Calculation) as a
standalone solution. This is not simultaneously judgment C, because the
data needed to make this specific architectural call (Real-Time vs. Read
Model) did not actually depend on the unconfirmed 9/17 figures being
resolved - it came from directly measuring the real `calc4` output against
real PO history. The §17 questions remain open and should still be sent
to Gulliver, but they inform *how* to define any future Active Brand
concept for display/UX purposes (§15) and *how* to size Stage 5J's refresh
design (§12/§13) - not *whether* a Read Model is needed.
