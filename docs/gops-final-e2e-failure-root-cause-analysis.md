# G-OPS Final E2E Failure — Root Cause Analysis

Investigation only. No Application/Test/Fixture/Cleanup code was changed, no
DB write was issued (every DB query below is `SELECT`), Demo Reset was not
re-run, and Full E2E was not re-run. This document extends
`docs/gops-visual-re-review-final-correction.md`'s own Full E2E report by
completing the Root Cause for all 3 non-crash failures from that run
(baseline commit `954443d387321b0fd723a75449e5e0c3a800648f`), and audits the
Master-data lifecycle issue Failures #2/#3 exposed.

## 1. Scope

3 of the 16 Full E2E failures from the post-Visual-Correction regression run
are investigated here:

1. `core-demo-scenario.spec.ts:101` — `未採番` not found
2. `email-send.spec.ts:105` — new Mail Template never appears in the table
3. `email-send.spec.ts:204` — an extra address appears in the To field

The other 13 failures (all `worker process exited unexpectedly` /
`Target crashed`) are **not** re-examined here — they were already
classified as host-memory-pressure crashes, a distinct problem category (see
§9).

## 2. Full E2E Failure Overview

| # | Test | Type | Position in run |
|---|---|---|---|
| 1 | core-demo-scenario.spec.ts:101 | Assertion (deterministic) | #12 of 232, very early |
| 2 | email-send.spec.ts:105 | Assertion (data collision) | #29 of 232, early |
| 3 | email-send.spec.ts:204 | Assertion (data collision) | #30 of 232, early |
| 4–16 | (13 other specs) | `worker process exited` / `Target crashed` | Concentrated later in the run |

All 3 investigated failures occurred **early** in the single-worker
sequential run (positions 12, 29, 30 of 232), well before the memory-pressure
crash cluster began. None share the crash signature. This alone is evidence
against a memory-pressure explanation for any of the 3 (see §9).

## 3. Failure #1 Evidence

**A. Test flow** (`frontend/e2e/core-demo-scenario.spec.ts:62-101`):
- Fresh login as `purchase01` (OPERATOR).
- Navigates Candidates → Brand `BR_KITCHEN` → selects `KT-BOWL-001`/`-002` →
  **creates a brand-new Draft** (`create-draft-button`) — its own fixture,
  not shared/reused data.
- Sets Order Qty, Saves, clicks "Preview" (`go-to-preview-button`).
- Lands on `/orders/drafts/${draftId}/preview` — **before any approval**, so
  `officialPoNo` is genuinely still null (correct business state; this Draft
  was created seconds earlier in the same test).
- Line 101: `await expect(page.getByText('未採番')).toBeVisible()`.
- Demo Reset relationship: none — the test creates its own Draft after
  login, independent of whatever Demo Reset left in place.

**B. Application state at assertion time**: Portal管理番号 = the fresh
`draftNo` just created; 正式PO番号 = `null` (not yet requested); Revision =
n/a (pre-approval); Order Status = `DRAFT`; Approval Status = not yet
submitted; Official PO Integration Status = `NOT_REQUESTED`. This state is
exactly what the Preview page's "not yet issued" branch is meant to render —
the application state itself is correct.

**C. UI — where the text actually comes from**
(`frontend/src/features/drafts/PoPreviewPage.tsx:272`):

```tsx
{t('officialPoNo')}: <strong>{preview.officialPoNo ?? t('officialPoNoUnassigned')}</strong>
```

`officialPoNo` is `null` pre-approval, so the fallback i18n key
`officialPoNoUnassigned` renders. Its **current** Japanese value
(`frontend/src/shared/i18n/locales/ja/preview.json:20`):

```json
"officialPoNoUnassigned": "未発行"
```

A full-codebase grep for the literal string `未採番` (the string the test
looks for) returns **zero matches** anywhere in `frontend/src` — it does not
exist in any i18n file, any component, or any other source file. It exists
**only** inside the test assertion itself. This is not a conditional-render,
stale-cache, or route-transition issue — the string the test is looking for
is simply not the string the page prints, under any state.

**D. DB**: not required to explain this failure — the business data
(`officialPoNo IS NULL` pre-approval) is correct; this is a UI-text/test
-string mismatch, not a data problem. (Confirmed by inspection of §3C; no
DB query changes this conclusion.)

**E. Where "未採番" came from — confirmed via `git log`/`git show`**

```
git log --oneline -- frontend/src/shared/i18n/locales/ja/preview.json
d50ac09  Post-Freeze Visual Walkthrough Findings Fix: 7 Findings ...
```

```diff
 diff --git a/frontend/src/shared/i18n/locales/ja/preview.json
-  "prototypePoNo": "PO No.",
-  "prototypePoNoUnassigned": "未採番",
+  "portalManagementNo": "Portal管理番号",
+  "officialPoNo": "正式PO番号",
+  "officialPoNoUnassigned": "未発行",
```

Commit `d50ac09` is the same commit that implemented **Finding #1 (Number
Terminology)** from the prior Post-Freeze Visual Walkthrough round: the old,
single ambiguous "PO No." field (key `prototypePoNo`, unassigned label
`未採番`) was deliberately split into two explicit fields — Portal管理番号
(`portalManagementNo`) and 正式PO番号 (`officialPoNo`, unassigned label
renamed to `未発行`). `core-demo-scenario.spec.ts` was never updated to
match this rename; it still asserts the pre-rename string.

**Test independence (§ E of the task instruction)**: this Draft is
test-created and does not depend on any other spec's data, so single-run vs.
full-suite-order makes no difference — the assertion is **deterministic** and
fails identically either way. It failed at position #12 of 232, in 10.2s,
consistent with an immediate, first-encounter mismatch rather than a
timing-sensitive flake.

## 4. Failure #1 Root Cause

The i18n key that renders the "official PO not yet issued" label was
**renamed and re-worded** (`prototypePoNoUnassigned: "未採番"` →
`officialPoNoUnassigned: "未発行"`) as part of commit `d50ac09`'s own
Finding #1 fix (a change from an *earlier* round of this same initiative,
not from this round's Visual Correction). `core-demo-scenario.spec.ts:101`
was not updated to match at that time and has asserted a now-nonexistent
string ever since. The commit's own regression note lists only "Targeted E2E
suites for Official PO/Supplier Response/Manufacturer Stockout/Order
History/Brand Candidates/Mobile Responsive/Post-Freeze Business Refinement:
77/77 passed" — `core-demo-scenario.spec.ts` was not part of that targeted
list, and the one Full E2E attempt from that same round was itself
interrupted by memory pressure before its result for this specific test was
confirmed. This is the first time a Full E2E run has reached this assertion
since the rename landed.

## 5. Failure #1 Classification

**B. Test Bug.**

Evidence: the exact string the test polls for (`未採番`) does not exist
anywhere in the current application source or i18n content (full-repo grep,
zero matches outside the test file); `git show d50ac09` proves the
application's own copy was intentionally renamed to `未発行` at the same
commit that split the old ambiguous field into Portal管理番号/正式PO番号, and
the test assertion was simply never updated to track that rename. Not an
Application Bug (the app's current behavior — printing 正式PO番号: 未発行 for
a not-yet-issued Order — is correct and matches Finding #1's own accepted
design), not a Fixture/Data Lifecycle Bug, not Test-order Dependency (the
Draft is self-created), not Stale Cache/SPA Transition (the string is absent
from the bundle entirely, not merely late to arrive), and not Environment
contamination.

## 6. Failure #2 Root Cause

**Test** (`email-send.spec.ts:59-105`): logs in, creates+approves a Draft for
SKU `OD-TENT-001` (SUP_ALPHA/BR_OUTDOOR), ensures a Manufacturer Channel of
EMAIL for `SUP_ALPHA`/`BR_OUTDOOR`, creates a Supplier Contact
(`taro@example.com`), then creates a Mail Template named `"PO Template"` for
`SUP_ALPHA`/`BR_OUTDOOR`/`PURCHASE_ORDER`/`ja` and asserts the table shows
it. The assertion times out — the table still shows only 2 pre-existing rows
(`"Scenario B Number Model Template"`, `"Mobile M6 Template"`), neither of
which is the row the test just tried to create.

**DB schema** (`\d mail_template`):

```
"uq_mail_template_active_identity" UNIQUE, btree
  (template_type, COALESCE(supplier_code,''), COALESCE(brand_code,''), language)
  WHERE is_active = true
```

At most one **active** row may exist per
(type, supplier, brand, language).

**Pre-existing residual row** (SELECT, confirmed before this analysis'
own queries could have altered anything):

```
id=1275  "Scenario B Number Model Template"  PURCHASE_ORDER  SUP_ALPHA  BR_OUTDOOR  ja
         created_at 2026-09-20 22:59:29  is_active=true (at test time; later
         deactivated 2026-09-21 00:33:01 — see §8)
```

This row occupies **exactly** the same identity key
(`PURCHASE_ORDER`/`SUP_ALPHA`/`BR_OUTDOOR`/`ja`) the test's own new "PO
Template" row targets. `MailTemplateService.create()`
(`backend/src/main/java/.../service/MailTemplateService.java:47-58`) calls
`repository.save(template)` with **no application-level duplicate/active
pre-check** — it relies entirely on the DB constraint. There is also no
`DuplicateMailTemplateException` registered in `GlobalExceptionHandler`
(unlike `supplier_contact`, which has a proper
`DuplicateSupplierContactException` → 400 handler). A constraint violation
here falls through to the generic `DataAccessException` handler
(`GlobalExceptionHandler.java:118-122`) → HTTP 503 `PORTAL_DB_ERROR`, a
non-specific error the test's UI flow does not surface as a recognizable
message — the Save silently fails from the test's point of view, and the
pre-existing stray row remains the only active one, so the new template
never appears.

**Root cause**: a residual, orphaned, active `mail_template` row from an
earlier session/run occupies the same active-uniqueness identity the test's
own fixture needs, and the backend has no graceful handling for that
collision.

## 7. Failure #3 Root Cause

**Test** (`email-send.spec.ts:170-204`): a separate test in the same file
(SUP_ALPHA/BR_OUTDOOR again), expects the mail-send "To" field to be
pre-filled with exactly `taro@example.com` (the one Contact test #1 in this
same file created).

**DB schema** (`\d supplier_contact`):

```
"uq_supplier_contact_active_identity" UNIQUE, btree
  (supplier_code, COALESCE(brand_code,''), lower(email))  WHERE is_active = true
```

This constraint is scoped per-**email**, not per-supplier/brand — so
**multiple** active Contacts can legitimately coexist for one supplier/brand
(unlike `mail_template`, which allows only one active row total per key). The
"To" field aggregates every active TO-type Contact for the Order's
supplier/brand.

**Pre-existing residual row** (SELECT):

```
id=1871  "Scenario B Number Model Contact"  scenario-b-number-model@example.com
         SUP_ALPHA  BR_OUTDOOR  TO
         created_at 2026-09-20 22:58:55  is_active=true (at test time; later
         deactivated 2026-09-21 00:33:01 — see §8)
```

Same supplier/brand as the test, same "orphan" naming pattern as the
Failure #2 row, created one minute apart — almost certainly created by the
same earlier manual/E2E session. With this row active, the "To" field
correctly aggregates **both** active TO Contacts:
`taro@example.com, scenario-b-number-model@example.com` — exactly the
observed (failing) value. The application's aggregation behavior itself is
correct; the test's expectation of a single-Contact list is only true in an
otherwise-clean environment.

**Root cause**: same class as Failure #2 — a residual, orphaned, active
`supplier_contact` row for the same key the test's own fixture uses.

## 8. Master-data Lifecycle Audit

Which specs `POST` Master data and whether each pairs CREATE → USE →
CLEANUP:

| Spec | Creates | Own cleanup? | Notes |
|---|---|---|---|
| `email-send.spec.ts` | supplier_contact, mail_template (SUP_ALPHA/BR_OUTDOOR), manufacturer_channel | **Yes** — dedicated `test('cleanup: deactivate the Master data rows the tests above created', ...)`, runs last in the file | Deactivates by **supplier_code+brand_code match**, not by an owned-row identifier — see below |
| `mobile-responsive.spec.ts` (M6) | supplier_contact (`m6-mobile-scenario@example.com`), mail_template (`"Mobile M6 Template"`), both SUP_BETA/BR_HOME | **No** | No cleanup step anywhere in this file for these 2 rows. Currently sitting **active** in the DB since 2026‑09‑19 (id=1096 / id=761 — see §10) |
| `supplier-contact-mail-template.spec.ts` | supplier_contact, mail_template | Partial (this file explicitly depends on SUP_ALPHA/BR_OUTDOOR starting with no active row, per `email-send.spec.ts`'s own comment at line 359-361) | Cross-file dependency by design |
| `official-po-integration.spec.ts`, `gulliver-phase1-integration.spec.ts`, `header-and-list-ux.spec.ts`, `master-maintenance-hub.spec.ts` | supplier_contact and/or mail_template (various) | Not audited in this pass (out of the 2 failures' direct evidence chain) | Flagged for a future, broader audit — see §14 |

**Where "Scenario B Number Model Template/Contact" came from**: not present
in any current or historical (`git log --all -S`) e2e spec source, and no
spec file has been deleted from git history. This orphan pair was almost
certainly created by **manual live-browser verification** in this session
(both rows: `created_by=admin01`, created 2026‑09‑20 22:58–22:59, one minute
apart, naming that mimics — but does not match — this project's actual test
titles), not by an automated spec that still exists today. Its owning
"test" no longer exists to clean it up even under the Ownership Principle.

## 9. Cleanup Ownership Audit

`email-send.spec.ts`'s own cleanup step
(`frontend/e2e/email-send.spec.ts:365-394`):

```ts
const templates = await (await page.request.get('/api/admin/mail-templates')).json()
for (const t of templates) {
  if (t.supplierCode === 'SUP_ALPHA' && t.brandCode === 'BR_OUTDOOR' && t.active) {
    await page.request.put(`/api/admin/mail-templates/${t.id}`, { data: { ...t, active: false } })
  }
}
```

(identical pattern for `supplier_contact` and `manufacturer_channel`.)

**Confirmed: this is a deliberate, documented design choice, not an
accident.** The comment immediately above it
(lines 353-364) states explicitly: *"other specs in a full-suite run
(supplier-contact-mail-template.spec.ts's own Scenario C/D, in particular)
assume SUP_ALPHA/BR_OUTDOOR starts with no active Contact/Template
configured yet, the same assumption this file's own tests relied on when
THEY ran."* The filter is **by business key** (`supplierCode`+`brandCode`+
`active`), not by an owned-row marker (no "created by this test run" tag
exists on these rows at all) — so it will deactivate **any** active row for
that key, regardless of which spec created it.

This is exactly the Ownership Principle violation the task anticipated:
`email-send.spec.ts`'s cleanup deactivated the orphaned "Scenario B Number
Model Template"/"Contact" rows (both SUP_ALPHA/BR_OUTDOOR) at
`2026-09-21 00:33:01` — timestamp-matched to this same Full E2E run's test
#33 — even though `email-send.spec.ts` did not create them. It happened to
help this time (it removed the orphan after the fact), but the design is
**"last test for this key cleans everything for this key"**, not **"each
test cleans only what it created"**. The `mobile-responsive.spec.ts` M6 pair
(SUP_BETA/BR_HOME) was not touched, because no spec's cleanup filter
currently targets that key at all — confirming the lifecycle gap is
real and current, not just historical.

## 10. Residual Master-data Inventory (SELECT ONLY — nothing modified)

| Table | Total rows | Active rows | Notes |
|---|---|---|---|
| `mail_template` | 201 | 1 | Only 2 of 201 total rows match the one safe, existing cleanup marker (`'Follow-up E2E Template %'`); the rest (199, including both rows implicated in Failure #2) are ambiguous by content alone. |
| `supplier_contact` | 13 | 1 | 12 of 13 match the safe `@example.com` marker and are cleanup-eligible (once inactive); this table's cleanup mechanism already works correctly when invoked. |
| `manufacturer_channel` | 125 | 1 | No content-based Test marker exists (realistic-looking supplier/brand codes reused by every spec) — by design, this table is not content-cleanable; Freeze Blocker-2 addressed this differently (Fixture reuse, not deletion — see §11). |
| `supplier_region_classification` | 26 | (not queried for active — out of this failure's evidence chain) | Same "no marker" situation as `manufacturer_channel`, already known per `docs/gops-phase1-final-cleanup-report.md`. |
| `official_po_short_code` | 6 | 6 | Static Master config; confirmed unchanged since the prior Freeze Blocker-2 audit ("never touched, any run") — no lifecycle issue here. |

**Currently-active rows with collision potential** (the ones that matter for
a future run):

| id | Table | Business key | Owning spec | Created | Collision risk |
|---|---|---|---|---|---|
| 761 | mail_template | "Mobile M6 Template" / PURCHASE_ORDER / SUP_BETA / BR_HOME / ja | `mobile-responsive.spec.ts` M6 (no cleanup) | 2026-09-19 10:35 | Any future spec creating an active PURCHASE_ORDER/ja template for SUP_BETA/BR_HOME will collide the same way Failure #2 did |
| 1096 | supplier_contact | m6-mobile-scenario@example.com / SUP_BETA / BR_HOME / TO | `mobile-responsive.spec.ts` M6 (no cleanup) | 2026-09-19 10:34 | Will inflate any future "To" field aggregation for SUP_BETA/BR_HOME the same way Failure #3 did |

The "Scenario B Number Model Template/Contact" pair that actually caused
Failures #2/#3 is **already inactive** now (deactivated incidentally by
`email-send.spec.ts`'s own cleanup during this same run, per §9) — so this
exact pair will not recur, but nothing about that was a designed fix; it was
a side effect of a different test's overly-broad cleanup scope.

## 11. Demo Reset Semantics

Confirmed via `backend/src/main/java/com/glv/gsysportal/demo/DemoResetRunner.java`
(read only): Demo Reset's `TRUNCATE` targets only
transactional/business-workflow tables (`portal_order`, `portal_order_detail`,
`portal_order_revision(_detail)`, `supplier_response(_detail)`,
`order_attention`, `audit_event`, `official_po_integration_request`,
`order_email`, `follow_up_case`, `legacy_po_baseline`,
`price_change_set(_detail)`, `idempotent_operation`). `portal_user`,
`mail_template`, `supplier_contact`, `manufacturer_channel`,
`supplier_region_classification`, and `official_po_short_code` are **never**
in that list — this is intentional, documented Master-data preservation, not
an oversight, and this task's own instruction correctly warns against
proposing "Demo Reset should wipe Master data" as a fix.

The class Javadoc also documents an **existing, partial** prior fix
(`app.demo-reset.include-test-master-data=true`,
`cleanupTestMasterData()`) which physically `DELETE`s only rows matching a
100%-certain Test marker:
- `supplier_contact WHERE is_active=false AND email LIKE '%@example.com'`
- `mail_template WHERE is_active=false AND template_name LIKE 'Follow-up E2E Template %'`

Both predicates require `is_active=false` — an active row (Demo Master or
E2E-created) is never touched. The Javadoc states the project's own explicit
policy directly: *"no content-based marker distinguishes their E2E-created
rows from genuine Demo Master data today, and '曖昧な条件によるDELETEは禁止'
(100%識別できない場合：削除しない) is an absolute rule here, not a
preference."* This SAME limitation is independently documented in
`docs/gops-phase1-final-cleanup-report.md` §2.2/§6 as a **known, still-open
gap**: *"The majority of mail_template's accumulated rows remain ambiguous
and unaddressed."* Failures #2/#3 are a concrete, reproduced instance of
exactly that already-known gap — not a new discovery, but its first observed
real-world consequence (an actual test failure, not just an inventory
count).

Two things follow directly from this:
1. **Do not touch Demo Reset's TRUNCATE scope.** That is correct, intentional
   design and out of scope regardless.
2. The existing `includeTestMasterData` DELETE mechanism is safe and already
   works (see `supplier_contact`'s 12/13 cleanup-eligible rows in §10) — the
   gap is that (a) it is opt-in and was not invoked before this Full E2E run,
   and (b) even if it had been, `mail_template`'s naming convention across
   specs is inconsistent (`"PO Template"`, `"Mobile M6 Template"`, `"Scenario
   B Number Model Template"`, `"Integration PO Template"`, ...), so almost
   none of it matches the one safe marker pattern the mechanism looks for.

## 12. Permanent Fix Options (not implemented)

| Option | Description | Isolation | Reproducibility | Risk | Implementation scope | Production behavior impact |
|---|---|---|---|---|---|---|
| **A. Per-spec `finally`/cleanup** | Every spec that creates Master data adds its own deactivation step (like `email-send.spec.ts` already has, but scoped to only the rows it itself created, not by shared business key) | High, once every spec has one | High | Low | Medium — touches every spec file, ~6+ files identified in §8, plus needs a way to identify "own" rows (see Option B) | None (test-only change) |
| **B. Test-specific business key / namespace** | Every E2E-created Master row uses a per-run-unique, clearly-marked business key (e.g. Contact email prefixed `e2e-<spec>-<run>@example.com`, Template name prefixed `E2E ${specName} ${timestamp}`) instead of realistic-looking shared names | High — collisions become structurally impossible | High | Low | Medium — a shared test-helper convention, then each spec adopts it | None; makes `includeTestMasterData`'s existing marker-based DELETE (§11) actually effective for ~all E2E rows, closing the exact gap that caused Failures #2/#3 |
| **C. Dedicated E2E Master Fixture Manager** | A shared Playwright fixture/helper that centrally creates-and-tracks-and-tears-down all Master rows a run touches, replacing ad-hoc per-spec `page.request` calls | Highest | Highest | Medium — larger refactor, more surface for a mistake during rollout | High — new shared module, migrate every spec off ad-hoc creation | None; best long-term shape, but the biggest single change |
| **D. Pre-suite Master-data sanitation** | Run `includeTestMasterData`-style cleanup (or an expanded version of it) before every Full E2E run, in addition to Demo Reset | Medium — only removes already-**inactive** ambiguous debris; does nothing for a row an earlier run left **active** (exactly what caused Failures #2/#3) | Medium | Low (mechanism already proven safe in production use, §10) | Low — just remember to pass the existing flag, or fold it into the standard Full E2E pre-step | None; this table is never Legacy/Production data |
| **E. Post-suite cleanup** | A final, suite-wide sweep (all specs done) that deactivates every Master row created since the run started | Medium — same gap as D for rows still active from a *previous* run, since it only knows about *this* run's own rows if it tracks them (needs B or C to know what "this run created" means) | Medium | Low–Medium (needs a reliable "created this run" marker, i.e. depends on B) | Medium | None |

**Recommended (not implemented)**: **B (naming/namespace convention) as the
immediate, low-risk fix, paired with D (always invoking the existing
`includeTestMasterData` cleanup before a Full E2E run) as the operational
practice.** B directly closes the exact gap both this analysis and the prior
Freeze Blocker-2 report already identified — inconsistent, ambiguous naming
— using the project's own existing, already-tested-safe DELETE mechanism
(§11), with no new infrastructure. A is worth doing at the same time for the
2 specs (`mobile-responsive.spec.ts` M6 in particular) that currently have no
cleanup at all, so they stop leaving *active* orphans that D's
inactive-only sweep cannot reach. C is the correct end-state but is a much
larger change and not warranted just to close this specific gap.

## 13. Recommended Remediation (not implemented this round)

1. Adopt a consistent, unambiguous naming convention for every E2E-created
   `mail_template`/`supplier_contact` row (Option B), e.g. a fixed prefix
   such as `"E2E "` + spec name, and widen
   `DemoResetRunner.cleanupTestMasterData()`'s `mail_template` LIKE pattern
   accordingly (still `is_active=false`-gated, so the existing safety
   invariant is unchanged).
2. Add a deactivation step to `mobile-responsive.spec.ts`'s M6 test (and
   audit the other specs listed in §8's "not audited" row) so no spec leaves
   an **active** Master row behind — this is the precondition for #1's
   DELETE-based cleanup to ever reach the row at all.
3. Change `email-send.spec.ts`'s cleanup (and any other spec with the same
   pattern) from "deactivate every active row for this business key" to
   "deactivate only the row IDs this test itself created" — closes the
   Ownership Principle gap identified in §9, independent of #1/#2.
4. Standardize on always invoking `--include-test-master-data=true` as part
   of this project's normal Full E2E pre-step (Option D), now that #1 makes
   its `mail_template` coverage meaningfully more complete.

None of this was implemented in this round, per instruction.

## 14. Risk if Left Unfixed

- Failures #2/#3 will very likely recur on a future Full E2E run: the
  `mobile-responsive.spec.ts` M6 pair (SUP_BETA/BR_HOME, §10) is sitting
  **active** right now with no owner cleanup, and any future spec that
  touches an active Mail Template or Contact for that same supplier/brand
  will hit the identical class of failure.
- Every additional ad-hoc-named `mail_template` row created by manual
  verification or a new spec adds to the 199-row ambiguous backlog (§10),
  each one a latent collision the next matching-key test could trip over.
- The failure signature (a generic `toContainText`/`toHaveValue` timeout) is
  easy to misdiagnose as test flakiness or an application regression on a
  future round, costing investigation time each time it recurs, unless the
  lifecycle gap itself is closed.
- Failure #1 will fail on **every** Full E2E run indefinitely until the
  assertion is corrected — it is fully deterministic, not intermittent.

## 15. Next Action Recommendation

Await explicit instruction before implementing any of §12's options. No
code, test, fixture, or cleanup change is proposed as part of this
investigation-only pass. If/when authorized, the lowest-risk first step is
§13 item 1 (naming convention + LIKE-pattern widening) since it reuses an
already-safety-tested mechanism (§11) with no new infrastructure, and item 2
for `mobile-responsive.spec.ts`'s M6 test since it is the one currently-known
spec leaving an **active** (not just ambiguous) orphan behind.
