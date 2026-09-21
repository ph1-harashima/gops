# G-OPS Final E2E Remediation + Final Regression

Scope: permanently remove the root causes of the 3 confirmed Assertion
Failures from `docs/gops-final-e2e-failure-root-cause-analysis.md` (commit
`c405a30`), restore Full E2E reproducibility, and record the final
Regression evidence. No Application business logic was changed - only test
code, one demo-only cleanup utility (`DemoResetRunner`), and its own test
coverage.

## 1. RCA Baseline

`c405a30` - "G-OPS Final E2E Failure Root Cause Analysis: complete
investigation of 3 non-crash Full E2E failures", approved by ChatGPT Techlead
review before this Remediation began. Application code untouched by that
commit; this Remediation is the first round to make code changes based on
it.

## 2. Failure #1 Remediation

`frontend/e2e/core-demo-scenario.spec.ts` (lines ~101-133): the assertion
followed the pre-rename string (`未採番`, removed by commit `d50ac09`'s own
Finding #1 Number Terminology fix) - fixed to follow the current canonical
wording without touching the Application or i18n content in any way:

- Line ~113 (pre-Approval Preview): now asserts the exact, whole-element text
  `正式PO番号: 未発行` (`{ exact: true }`, not a bare substring match) -
  correctly reflects 正式PO番号 (officialPoNo) staying unissued at this point,
  since this test's own flow never requests an Official PO Integration.
  `exact: true` was required because `DemoManufacturerCommunicationFactory`'s
  own Demo mail-preview body independently renders the identical
  `正式PO番号: 未発行` line as part of its (unrelated) multi-line preview text
  - a bare substring `getByText('未発行')` hits both and throws a Playwright
    strict-mode violation (confirmed live during the first Targeted Test
    attempt, fixed immediately - not a false pass).
- Line ~135 (post-Approval Preview): the original test's real intent - "PO
  No. is now assigned" - was root-caused to `portalManagementNo`
  (`prototypePoNo ?? draftNo`), not `officialPoNo`.
  `OrderStatusTransitionService#approve` (backend/src/main/java/.../
  service/OrderStatusTransitionService.java:121-124) proves `prototypePoNo`
  is generated and set exactly at Approval, never earlier. The test now
  captures Portal管理番号's displayed text before and after Approval and
  asserts it actually changed - a meaningful, stronger replacement for the
  original (which, after the rename, would have trivially always passed
  regardless of real behavior). 正式PO番号 is asserted to still read `未発行`
  here too, since this test never requests Integration - asserting its
  absence would have been a false, not a stronger, check.

No Application file was changed for this item.

## 3. Master-Data Ownership Fix

`frontend/e2e/mobile-responsive.spec.ts`'s M6 test created a
`supplier_contact` and a `mail_template` for SUP_BETA/BR_HOME with no
cleanup of its own (RCA §8) - the only spec found with this gap. Added an
ownership-scoped cleanup block at the end of M6 (after its own assertions),
deactivating only the exact rows it just created (matched by the unique
`email`/`templateName` it used, never by `supplierCode+brandCode` alone).

## 4. Cleanup Isolation Fix

`frontend/e2e/email-send.spec.ts`'s dedicated cleanup test (RCA §9) filtered
by `supplierCode==='SUP_ALPHA' && brandCode==='BR_OUTDOOR' && active` -
broad enough to deactivate ANY active row for that key, including ones this
file never created (confirmed root cause of both Failures #2/#3). Narrowed
to owned identity: `email==='taro@example.com'` for the Contact,
`templateName==='E2E PO Template'` for the Template - the only Contact/
Template this file's own tests create (confirmed via grep: exactly one
`supplier-contact-create-button`/`mail-template-create-button` pair in the
whole file). `manufacturer_channel`'s cleanup was deliberately left
unchanged: unlike the other two tables, every spec reuses the same fixed
`SUP_ALPHA`/`BR_OUTDOOR` row via the shared `ensureManufacturerChannel`
idiom (Freeze Blocker-2's own resolution for this table), so there is no
"other spec's row" it could accidentally catch.

Also audited (not modified, per this task's explicit minimum scope):
`gulliver-phase1-integration.spec.ts` and `supplier-contact-mail-template.spec.ts`
already use owned-identity cleanup (the correct, established idiom this fix
now also applies to email-send.spec.ts); `official-po-integration.spec.ts`
still uses the same broad business-key cleanup pattern email-send.spec.ts
had; `master-maintenance-hub.spec.ts` already uses per-run unique names with
inline deactivation, no issue. Flagged for a future, broader pass - out of
this round's confirmed-failure-driven scope.

## 5. Namespace Convention

Every E2E spec that dynamically creates a `mail_template` row now prefixes
its name with `"E2E "` (`mobile-responsive.spec.ts`: `"E2E Mobile M6
Template"`; `email-send.spec.ts`: `"E2E PO Template"`). Existing
`toContainText` assertions against the old, un-prefixed names still pass
unchanged (substring match) - no assertion was altered to make this work.
`supplier_contact` needed no such change: its existing `%@example.com` DELETE
marker is already broad enough to cover every synthetic E2E email in this
codebase (confirmed: 12 of 13 rows already matched it before this round).
No DB schema change.

`DemoResetRunner.cleanupTestMasterData()`'s `mail_template` DELETE pattern
widened from `template_name LIKE 'Follow-up E2E Template %'` alone to
`... OR template_name LIKE 'E2E %'`, still gated by `is_active = false` (an
Active row is never touched, regardless of name - Regression C unchanged).
2 new integration tests added to
`DemoResetRunnerCleanupTestMasterDataIntegrationTest` (A/C scenarios for the
new marker), using a distinctive, non-colliding fixture name
(`"E2E Cleanup Regression Template ..."`) so they can never be confused with
a real row an actual E2E spec run left in this same shared local DB - a
first draft using the literal `"E2E PO Template"`/`"E2E Mobile M6 Template"`
names briefly collided with real rows from this round's own targeted E2E
runs (caught and fixed before this doc was written, not shipped).

## 6. Pre-suite Sanitation

Confirmed via `DemoResetRunner.java`'s own Javadoc/TRUNCATE list that Demo
Reset's Master-data exclusion is intentional (`mail_template`,
`supplier_contact`, `manufacturer_channel`, `supplier_region_classification`,
`official_po_short_code`, `portal_user` are never truncated) - left
unchanged, per instruction. Ran the existing, already-safety-tested
`includeTestMasterData` mechanism
(`--app.demo-reset.enabled=true --app.demo-reset.include-test-master-data=true`)
once, ahead of Targeted Verification, as the sanctioned sanitation step -
never a raw manual SQL DELETE. `official_po_short_code` and every other
Master table outside `mail_template`/`supplier_contact` were untouched, as
designed.

## 7. Local Orphan Cleanup Result

The 2 orphans RCA identified as currently active
(`mail_template` id=761 "Mobile M6 Template", `supplier_contact` id=1096
"m6-mobile-scenario@example.com", both SUP_BETA/BR_HOME) were first
deactivated through the Application's own Admin API (authenticated as
`admin01`, `PUT /api/admin/mail-templates/761` and
`/api/admin/supplier-contacts/1096` with `active:false`) - the same action a
real ADMIN user would take, never a raw SQL UPDATE. Re-running M6 without
this step would have failed immediately on its own re-creation attempt
(identical active-uniqueness collision to Failure #2/#3), which is why this
had to precede Targeted Verification.

| Table | Total before | Active before | Total after | Active after |
|---|---|---|---|---|
| `mail_template` | 201 | 1 (id=761, deactivated by the API step above) | 199 | 0 |
| `supplier_contact` | 13 | 1 (id=1096, deactivated by the API step above) | 1 | 0 |

`mail_template` dropped by exactly 2 (the 2 rows that already matched the
original `'Follow-up E2E Template %'` marker before this round - unrelated
to id=761). `id=761` itself correctly **survives**, now inactive: its name
("Mobile M6 Template") does not match either safe DELETE marker (no "E2E "
prefix, since it predates this round's naming fix), so
`cleanupTestMasterData()` correctly leaves it alone per this project's own
"100%識別できない場合：削除しない" policy - expected, policy-compliant
behavior, not a residual bug. `supplier_contact` dropped from 13 to 1 (12
deleted, all matching the pre-existing `%@example.com` marker).

## 8. Targeted Test Results

`core-demo-scenario.spec.ts`, `email-send.spec.ts`, `mobile-responsive.spec.ts`,
`order-history-number-model.spec.ts`, `manufacturer-stockout-information.spec.ts`,
`supplier-response-revision-workflow.spec.ts`,
`supplier-response-confirmation-bugfix.spec.ts`,
`supplier-response-confirm-dialog-stability.spec.ts` (8 files): first pass
50 passed / 1 failed (`core-demo-scenario.spec.ts`'s new assertion hit the
strict-mode violation described in §2 - fixed immediately, not deferred).
Re-run of the fixed file: 1/1 passed. **Total: 51/51 passed, 0 failed.**

## 9. Repeatability Results

`email-send.spec.ts` + `mobile-responsive.spec.ts` (19 tests combined) run
twice, back to back, with no Demo Reset between runs - the direct test of
whether the Ownership fix (§3/§4) actually prevents residual-data
collisions on a second pass, not just a first, clean one.

- **Repeat Run #1**: 19 passed, 0 failed (4.6m)
- **Repeat Run #2**: 19 passed, 0 failed (4.0m)

No residual-data failure on the second run - confirms the fix, not just the
first-run happy path.

## 10. Full E2E Result

| # | Value |
|---|---|
| Total | 232 |
| Passed | 136 |
| Failed | 15 |
| Skipped | 2 |
| Did not run | 79 |
| Duration (before interruption) | 15.2m |

All 15 failures show the identical, pre-existing signature already seen
twice earlier in this session: `Error: worker process exited unexpectedly
(code=3221225794, signal=null)`. **Zero** show an assertion-style error.
The background run was stopped by the harness's own memory-pressure
protection (not by Claude Code restarting it, and not restarted afterward,
per instruction) - the third such interruption in this project's session
history, an environmental/host-resource condition, not an application or
test regression.

All 3 remediated files (`core-demo-scenario.spec.ts`,
`email-send.spec.ts` x5, `mobile-responsive.spec.ts` x14) ran again inside
this same Full E2E attempt, at their normal position early in the run
(before the crash cluster began), and **all passed cleanly** - the fixes
hold under the full-suite run, not just the isolated Targeted run.

Per instruction, no fix was attempted for these 15 crash-signature failures,
and the suite was not restarted.

## 11. Remaining Technical Debt

- `mail_template` still carries 199 ambiguously-named rows (down from 201,
  §7) that no safe marker can identify - correct, policy-compliant, and
  explicitly out of this round's scope (same conclusion as
  `docs/gops-phase1-final-cleanup-report.md` §6). Will keep growing slowly
  from any spec not yet using the `"E2E "` convention (§5's audit list in
  §4).
- `official-po-integration.spec.ts` still uses the broader,
  business-key-only cleanup pattern (§4) - not implicated in any confirmed
  failure this round, flagged for the same future audit as
  `gulliver-phase1-integration.spec.ts`'s sibling pattern already avoids.
- Full E2E still has not completed a single, uninterrupted run since this
  session began (3 interruptions total, all the identical host
  memory-pressure signature) - an environment capacity issue, not resolved
  by this Remediation (out of this task's scope: it targets Assertion
  Failures, not host memory capacity).

## 12. Final Judgment

The Remediation itself is **verified**: Failure #1's root cause is gone
(current canonical wording, meaningfully re-checked); Failures #2/#3's root
cause is gone (Ownership Principle fix + Namespace Convention, confirmed
non-recurring across 2 consecutive repeat runs); all 3 previously-failing
spec files pass cleanly both in isolation and inside a real Full E2E
attempt; full Application regression (Backend 614/614, Vitest 53/53,
`tsc -b` clean, `vite build` clean, i18n parity 0 mismatches) is clean.

However, per this task's own explicit criterion, Full E2E did **not** reach
0 FAIL in this round (interrupted by host memory pressure at 136/232,
15 crash-signature failures, 0 assertion failures, 79 did not run) - the
"PHASE1 LOCAL BUSINESS FLOW + VISUAL + REGRESSION VERIFIED" judgment
requires a clean 0-FAIL Full E2E, which this run did not achieve.

**Final Judgment: FINAL REGRESSION NOT VERIFIED** (Remediation confirmed
correct; a clean, uninterrupted Full E2E run is still outstanding, blocked
by host memory capacity, not by any Application/Test defect).
