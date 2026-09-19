# G-OPS Production Configuration Inventory

**Status**: Inventory only. No values listed — property keys and required/optional status only, taken verbatim from `@Value` bindings in Backend source (`grep -rn "@Value(" backend/src/main/java`) and `application.yml`/`application-production.yml`. No Secret value is present in this document or was requested from anyone while producing it.

Every key below already exists as a working Spring property binding in this codebase today (Local/Demo/Test values only) — Production readiness means supplying real values through whatever mechanism Infrastructure decides (env var / Secret Manager / Production-profile YAML), not writing new code.

## 1. Portal (Prototype) Database

| Key | Required | Current State | Owner | Source (code) | Verification Method |
|---|---|---|---|---|---|
| `app.prototype.datasource.jdbc-url` | Required | Demo value (`localhost:54321/gsys_portal`) | Infrastructure | `PrototypeDataSourceConfig.java:43` | Confirm connectivity via `/actuator/health` after profile switch |
| `app.prototype.datasource.username` | Required | Demo value | Infrastructure | `PrototypeDataSourceConfig.java:46` | Same |
| `app.prototype.datasource.password` | Required | Demo value, plaintext in `application.yml` | Infrastructure | `PrototypeDataSourceConfig.java:49` | Same |
| `app.prototype.datasource.driver-class-name` | Required | `org.postgresql.Driver` (Demo) | Infrastructure | `PrototypeDataSourceConfig.java:52` | Unlikely to change; confirm Production PG major version compatibility |
| `app.prototype.datasource.maximum-pool-size` | Optional (default 5) | 5 (Demo value) | Infrastructure | `PrototypeDataSourceConfig.java:55` | Load-test-driven, not yet sized for Production |

## 2. Legacy Database

| Key | Required | Current State | Owner | Source (code) | Verification Method |
|---|---|---|---|---|---|
| `app.legacy.datasource.jdbc-url` | Required | Demo value (`localhost:33061/legacy_demo`) | Ernest + Infrastructure | `LegacyDataSourceConfig.java:32` | `GET /api/health/legacy` after profile switch |
| `app.legacy.datasource.username` | Required | Demo value (`gsys_portal_ro`) | Ernest (real role must be SELECT-only, DBA-side) | `LegacyDataSourceConfig.java:35` | Confirm grants directly on the real MySQL role before first connect |
| `app.legacy.datasource.password` | Required | Demo value, plaintext | Ernest + Infrastructure | `LegacyDataSourceConfig.java:38` | — |
| `app.legacy.datasource.driver-class-name` | Required | `com.mysql.cj.jdbc.Driver` | Infrastructure | `LegacyDataSourceConfig.java:41` | Confirm Legacy MySQL version compatibility |
| `app.legacy.datasource.maximum-pool-size` | Optional (default 5) | 5 | Infrastructure | `LegacyDataSourceConfig.java:44` | — |
| *(read-only enforcement)* | N/A — **hardcoded**, not a config key | `config.setReadOnly(true)`, literal in code | N/A | `LegacyDataSourceConfig.java:57` | Cannot be misconfigured via YAML; verified by `LegacyReadOnlyIntegrationTest` |

## 3. SMTP / Email

| Key | Required | Current State | Owner | Source (code) | Verification Method |
|---|---|---|---|---|---|
| `spring.mail.host` | Required (for Production Email to function) | Empty default | Gulliver (provider choice) + Infrastructure | `SmtpEmailSenderAdapter.java:41` | Never testable in this environment (SafetyGuard) — must be verified in a future Production-reachable environment |
| `spring.mail.port` | Optional (default 587) | 587 default | Infrastructure | `SmtpEmailSenderAdapter.java:42` | Same |
| `spring.mail.username` | Required | Empty default | Gulliver + Infrastructure | `SmtpEmailSenderAdapter.java:43` | Same |
| `spring.mail.password` | Required | Empty default | Gulliver + Infrastructure | `SmtpEmailSenderAdapter.java:44` | Same |
| From address / Reply-To policy | Required (Business decision, not just a config key) | Not implemented — Preview currently assumes "logged-in user" | Gulliver | N/A — no property exists yet for this | Requires a small Backend change once decided (`DO NOT IMPLEMENT` now — see Audit §11 Remaining Items) |

## 4. Official PO Import Folder

| Key | Required | Current State | Owner | Source (code) | Verification Method |
|---|---|---|---|---|---|
| `app.official-po.import-folder.base-dir` | Required | Local demo path, profile-suffixed | Ernest (real path/protocol) + Infrastructure | `LocalFilesystemImportFolderAdapter.java:33` | N/A today — `ProductionImportFolderAdapter` is an unimplemented stub; this key is meaningless for Production until a transport is chosen and implemented |
| `app.official-po.storage.base-dir` | Required (Portal's own artifact store, not the hand-off) | Local demo path, profile-suffixed | Infrastructure | `OfficialPoExcelStorageService.java:42`, `OfficialPoPdfStorageService.java:31` | Confirm disk/volume sizing and backup policy (Audit §17) |
| Import Folder credential | Not yet a config key — no code path reaches this far | N/A | Ernest + Infrastructure | N/A (`ProductionImportFolderAdapter.place()` throws before any credential is used) | N/A until transport decided |

## 5. Feature / Operational Flags

| Key | Required | Current State | Owner | Source (code) | Verification Method |
|---|---|---|---|---|---|
| `app.demo-features.enabled` | Optional (default true) | `false` in `application-production.yml` (the ONLY property that file currently overrides) | Phase1 Japan | `SystemFeatureFlagsController.java:19` | Hides Demo Send UI when Production profile is (hypothetically) active |
| `app.demo-reset.enabled` | Not applicable to Production (CLI-only, never set in any YAML) | `false` default | N/A | `DemoResetRunner.java:76` | Must never be set to `true` outside local/demo/test — SafetyGuard would block it there regardless |
| `app.demo-reset.include-test-master-data` | Same as above | `false` default | N/A | `DemoResetRunner.java` (added this Phase) | Same |

## 6. Server / Actuator (no Secret involved, listed for completeness)

| Key | Required | Current State | Source |
|---|---|---|---|
| `server.port` | Required | `8080` | `application.yml` |
| `management.endpoints.web.exposure.include` | Required | `health` only | `application.yml` |
| `management.endpoint.health.show-details` | Required (Security-relevant) | `never` | `application.yml` |
| `management.health.db.enabled` | Required (prevents Legacy/Portal DB health ambiguity) | `false` | `application.yml` |

## 7. Not Yet a Config Key (no property exists in code today)

These are needed for Production but there is currently no `@Value`/property binding for them anywhere — they would require a (currently unplanned, `DO NOT IMPLEMENT` per this round's instructions) Backend change before they could even be set:

- EDI transmission endpoint/credential (no EDI code exists at all)
- Encryption key / at-rest encryption configuration
- Any Secret Manager reference/ARN (no Secret Manager integration exists)
- Log aggregation destination
- Metrics/Alerting endpoint

## 8. Classification Summary

| Classification | Keys |
|---|---|
| Environment Variable (would be, once Infra decides the mechanism) | All of §1-§4 above — currently Config File only |
| Secret Store | None yet — no Secret Manager integration exists |
| Config File | Everything currently (single `application.yml`, plaintext, committed — Demo-safe values only) |
| Hardcoded | Legacy DB pool `readOnly=true` (intentionally, for safety — see §2) |
| Unknown / Not Yet Designed | Import Folder credential, EDI everything, Encryption key |
