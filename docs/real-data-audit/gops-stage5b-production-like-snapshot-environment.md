# G-OPS Stage 5B — Production-Like Snapshot Environment & Validation Safety Gate

Scope: build a Controlled Snapshot Validation environment that reproduces
Production's inferred case-insensitive table-name behavior (Stage 5A §6),
and implement the config-driven Snapshot Validation Gate Stage 5A designed
(§11) — closing the gap that blocked Stage 5's UI validation. **Targeted
implementation.** No Legacy G-SYS change, no Production/UAT connection, no
Production DB change, no `ms_item`→`MS_ITEM` rewrite, no Mixed Brand
change, no Stage 5 UI business validation performed.

## 1. Working Assumption

Real Production MySQL's `lower_case_table_names` setting is treated as
**case-insensitive (1 or 2)** as a *strongly evidenced working assumption*,
**not a confirmed fact**. Ernest's confirmation is pending; this Stage
does not wait on it. If the answer later differs, this Stage's own
container/behavior will be re-validated against it — nothing here is
treated as permanent or self-confirming.

## 2. Evidence

From Stage 5A, carried forward unchanged:
- Every Legacy G-SYS JPA entity (`MsItem`, `MsStk`, `MsComm`, `TrPo`,
  `TrPoDtl`, `TrArr`, `MsFormula`, `TrInvDtl`, and every other model class
  checked) declares its `@Table(name=...)` in **uppercase**.
- The real Production dump (`goo_20260916.dmp`, checked directly, not
  merely the restored container) shows only `MS_ITEM` physically
  uppercase; the other 39 tables are lowercase.
- Legacy G-SYS is the live, currently-operating system of record, running
  against real Production with these uppercase mappings.
- The only way a uniformly-uppercase Hibernate mapping layer resolves
  correctly against a mostly-lowercase-stored schema is case-insensitive
  table name comparison at the database level.

## 3. Snapshot Container Rebuild

New, separate container/volume — **the existing `gsys-prod-snapshot-20260916`
container/volume from Stage 1C was not touched, modified, or deleted**:

| | Existing (Stage 1C) | New (Stage 5B) |
|---|---|---|
| Container | `gsys-prod-snapshot-20260916` | `gsys-prod-snapshot-20260916-ci` |
| Volume | `gops-prod-snapshot-20260916-data` | `gops-prod-snapshot-20260916-ci-data` |
| Port | 33199 | 33200 |
| `lower_case_table_names` | 0 (case-sensitive) | **1 (case-insensitive)** |

Created via `docker run ... mysql:8.0 --lower-case-table-names=1` (the
only way to set this - it is fixed at data-directory initialization and
cannot be changed on an existing instance). Restored from the exact same,
unmodified `goo_20260916.dmp` file used in Stage 1C, via the identical
Stage 1C restore command (`SET SESSION innodb_strict_mode=OFF` via
`--init-command` only - no `--force`, no dump edit, no `SET GLOBAL`).

**One restore attempt was killed by the host's own memory-pressure
protection at 11/41 tables** (not a failure of the restore itself - the
system reaper stopped it while the session was otherwise idle). Per
established project discipline, this was not auto-retried: reported to
you, checked for stoppable G-OPS processes (none found - no dev servers
were running at the time), the partial container/volume was discarded,
and a clean retry was run from the top after your explicit go-ahead.

## 4. Restore Verification

Second attempt: **succeeded, exit code 0, 16m14.8s, zero SQL errors, zero
SQL warnings** (matching Stage 1C's own clean-run signature). Table count
and every core-table row count checked against Stage 1C's own documented
aggregate - **exact match**:

| Table | Stage 1C count | Stage 5B count | Match? |
|---|---|---|---|
| Tables (total) | 41 | 41 | Yes |
| `MS_ITEM` | 116,842 | 116,842 | Yes |
| `ms_stk` | 1,518,842 | 1,518,842 | Yes |
| `ms_comm` | 3,825 | 3,825 | Yes |
| `tr_po` | 8,376 | 8,376 | Yes |
| `tr_po_dtl` | 183,480 | 183,480 | Yes |
| `tr_arr` | 17,722 | 17,722 | Yes |
| `his_arr_list` | 282,592 | 282,592 | Yes |
| `his_stk_list` | 67,199 | 67,199 | Yes |

The dedicated read-only `gops_snapshot_audit` user was re-created on this
new container (identical `GRANT SELECT` only, re-verified via
`SHOW GRANTS` immediately after creation).

## 5. `lower_case_table_names`

`SHOW VARIABLES LIKE 'lower_case_table_names';` → **1**, as targeted.

## 6. Case Compatibility

Verified directly, both directions, for every core table G-OPS references
(§Stage 5A §4's full 8-table list was spot-checked on the 4 most
structurally significant): `SELECT COUNT(*) FROM MS_ITEM` and `FROM
ms_item` both succeed and return the identical count (116,842); same for
`MS_STK`/`ms_stk` (1,518,842), `MS_COMM`/`ms_comm` (3,825), and
`TR_PO`/`tr_po` (8,376) / `TR_PO_DTL`/`tr_po_dtl` (183,480). No individual
business/row data was output for this check - aggregate `COUNT(*)` only.

## 7. Safety Gate Implementation

`SafetyGuardEnvironmentPostProcessor.java` extended per Stage 5A §11's
design exactly:

- `ALLOWED_PROFILES` gained one new entry: `snapshot-validation` (used
  *in addition to* `local`/`demo`/`test`, never alone in practice - the
  codebase's own `@Profile({"local","demo","test"})` beans, e.g.
  `LoggingEmailSenderAdapter`, still require one of those three active).
- New `resolveEffectiveLegacyDbNames(activeProfiles, configuredSnapshotDbName)`:
  returns the unchanged base `{legacy_demo}` set unless **both**
  `snapshot-validation` is active **and** `GOPS_SNAPSHOT_VALIDATION_ALLOWED_DB_NAME`
  is a non-blank, non-hard-denied value - in which case that one exact
  name is added *on top of*, never instead of, the base set.
- New `HARD_DENIED_LEGACY_DB_NAMES = {"goo"}`, checked **twice,
  independently**: once inside `resolveEffectiveLegacyDbNames` (so `goo`
  can never even be added to the effective allowlist), and again inside
  `validateDataSource` itself (so `goo` is rejected even if some future
  caller passed an allowlist that mistakenly already contained it) - true
  defense in depth, not a single check.
- Exact `String.equals` match only - no regex, no prefix, no wildcard,
  anywhere in this path.
- WARN-level, unmissable audit log whenever the exception actually
  engages - see §9 for why a plain `log.warn()` alone was insufficient
  and had to be paired with a direct `System.out.println`, mirroring this
  same file's own pre-existing pattern for violation messages.
- Every other check (host allowlist, Prototype guard, base Legacy
  allowlist) is completely unchanged.

## 8. Safety Tests

14 new tests added (`SafetyGuardEnvironmentPostProcessorTest`: 12;
`SafetyGuardIntegrationTest`: 2), covering exactly the 9 required cases
plus 2 extra (base-allowlist-still-works-when-snapshot-profile-active;
blank-not-just-null property):

| Case | Test |
|---|---|
| Normal `legacy_demo` allowed | `legacyLocalhostAndLegacyDemoSchema_pass` (pre-existing, still passes) |
| Snapshot DB without profile denied | `snapshotDbName_withoutSnapshotProfile_isDenied` |
| Snapshot profile without exact DB property denied | `snapshotProfile_withoutExactDbProperty_isDenied` |
| Exact DB property without profile denied | `exactDbProperty_withoutSnapshotProfile_isDenied` |
| Snapshot profile + exact DB property allowed | `snapshotProfilePlusExactDbProperty_isAllowed` |
| Wrong snapshot DB name denied | `wrongSnapshotDbName_isDenied` |
| Wildcard-like value denied | `wildcardLikeConfiguredValue_isDenied` (`*` and `%` both tested as literal, non-matching strings) |
| `goo` always denied | `goo_isNeverAllowed_evenWithSnapshotProfileAndPropertySetToGoo` + `goo_isNeverAllowed_evenIfSomehowPresentInAllowedSet` (defense-in-depth, both layers) |
| Case mismatch in allowed DB name denied | `caseMismatchInConfiguredDbName_isDenied` |
| All existing Safety tests still passing | Every pre-existing test in both files, unmodified, still green |

Plus 2 end-to-end integration tests (`SafetyGuardIntegrationTest`,
real `SpringApplicationBuilder` boot attempts, no Docker required since
both fail before any connection): snapshot profile active but property
missing still fails startup; `goo` denied end-to-end even with the
snapshot-validation profile and property both set to `goo`.

**Total: 32 unit tests (was 20) + 7 integration tests (was 5) = 39,
all passing.**

## 9. Startup Verification

**First attempt** (via `-Dspring-boot.run.arguments="...&..."`) failed for
an unrelated, purely mechanical reason: the JDBC URL's `&`-separated query
parameters were split by the Windows shell into separate garbled
sub-commands, so only a truncated URL and none of the other properties
(including `GOPS_SNAPSHOT_VALIDATION_ALLOWED_DB_NAME`) actually reached
the application - the Safety Guard correctly rejected the resulting,
still-unmodified `legacy_demo`-only configuration. **Fixed** by switching
to environment variables (`APP_LEGACY_DATASOURCE_JDBC_URL`, etc. - Spring
Boot's own relaxed-binding convention, dash→underscore), which pass
through the process environment untouched by shell command-line parsing.

**Second attempt** genuinely engaged the Snapshot Validation Gate and
Hikari successfully connected - confirmed via `netstat`, not merely log
inspection: the running process (verified by PID) held 5 ESTABLISHED TCP
connections to `127.0.0.1:33200` (the new Snapshot container) for Legacy,
and 5 to `127.0.0.1:54321` (unchanged local Portal Postgres) for
Prototype - simultaneously, cleanly separated, exactly as required. It
then failed at the very last step ("Port 8080 was already in use") -
diagnosed to a **pre-existing, unrelated G-OPS backend dev server process**
left running from earlier in this session (plain `--spring.profiles.active=local`,
pointed at Legacy Demo, unrelated to this Stage's work) that had been
holding port 8080 the whole time. Confirmed via the process's own command
line before stopping it (not assumed) - a legitimate, stoppable G-OPS
dev-server process, not user Chrome, not Docker, not unrelated software.

**Third attempt** (after freeing port 8080): the intended
`SNAPSHOT VALIDATION MODE ACTIVE` WARN log was written by `log.warn(...)`
but **did not appear in the captured output** - `postProcessEnvironment`
runs early enough in Spring Boot's own lifecycle that a plain SLF4J call
here is not reliably captured, exactly the same limitation this file's own
pre-existing `SafetyGuardViolationException` path already documents and
works around (`System.err.println` alongside the exception). **Fixed** by
pairing the WARN log with an explicit `System.out.println` of the same
message, mirroring that established pattern. Re-verified (fourth attempt,
after re-running the Safety Guard test suite to confirm the fix broke
nothing): the WARN message now appears in full, containing all four
required elements ("SNAPSHOT VALIDATION MODE", the allowed database name,
"READ-ONLY validation only", and explicit Production/UAT prohibition
text).

**Final verification, all green**:
- G-OPS startup: succeeded (`/actuator/health` → `{"status":"UP"}`)
- Safety Guard WARN: present, correct content
- Legacy datasource: connected to the new Snapshot (33200), confirmed via
  live TCP connection state, not log inspection alone
- Portal datasource: connected to local Postgres (54321) only, never
  33200 - confirmed the same way
- Flyway: log explicitly confirms its migration target is
  `jdbc:postgresql://localhost:54321/gsys_portal` only - never touched
  the Legacy Snapshot
- Normal `local`-only startup (Legacy Demo) re-verified immediately after,
  to confirm the new Safety Guard logic has zero effect on existing
  behavior when the snapshot-validation profile is not used

**No business UI, API endpoint beyond `/actuator/health`, or SKU/Brand
data was touched during any of these four attempts** - health check only,
per instruction. All 4 spawned processes were explicitly stopped
(`taskkill`) once each verification concluded; both Snapshot containers
(old and new) were stopped at the end of this Stage, data/volumes
preserved.

## 10. Remaining Uncertainty

- Production's real `lower_case_table_names` value remains **unconfirmed**
  - this Stage's entire premise rests on the working assumption in §1.
- The Safety Guard's `HARD_DENIED_LEGACY_DB_NAMES` currently contains only
  `"goo"` - if any other known Production-adjacent schema name becomes
  relevant later, it should be added to that same set (the mechanism
  already supports more than one entry).
- `snapshot-validation` was only exercised combined with `local` this
  Stage; combining it with `demo` or `test` was not separately verified
  (no reason to expect a difference, since the Safety Guard logic itself
  doesn't distinguish among the three, but noted for completeness).

## 11. Ernest Confirmation Pending

Awaiting Ernest's answer on Production's real `lower_case_table_names`
setting. Per instruction, this Stage does not block on it, and no
Architecture decision here is being treated as final because of it. When
the answer arrives, it will be recorded and cross-checked against this
Stage's working assumption (§1) - if it contradicts the assumption, this
container's own validity as a "Production-like" environment would need to
be reassessed in a future Stage, not silently kept.

## 12. Recommendation

The Production-like Snapshot environment and the Safety Gate are both
complete and verified: G-OPS can now start successfully, read-only,
against the case-insensitive Snapshot, with the Legacy datasource
correctly isolated from Portal, Flyway correctly scoped to Portal only,
and the Safety Guard's WARN clearly auditing the exception whenever it is
used. No Application SQL was changed. No business UI validation has been
performed - Stage 5's actual Brand/Candidate/Price-Change validation work
remains fully pending a separate, explicit resume instruction, per this
Stage's own restriction.
