# Production Deployment & Recovery Runbook（Phase 8-L）

**Status**: これは「今すぐProductionへDeployできる手順書」ではない。Production Readiness Runbookとして、現時点で確定している技術Foundation（Phase 8-L: Health Check/Structured Error Handling/Correlation ID/Technical Idempotency Foundation）を前提に、Deploy前チェック・必要な外部設定・Migration確認・Health確認・Smoke Test・障害検知・Rollback/Recovery概念・Escalation Pointを整理する。**Hosting Vendor・具体的なInfrastructure製品は選定しない**（`docs/production-readiness-and-integration-boundary-audit.md` 9章のとおり未確定）。未確定部分は明示的に **TBD** と記す。

**前提Document**: `docs/production-readiness-and-integration-boundary-audit.md`（Phase 8-K、Production Readiness全体監査）、`docs/requirements-coverage-and-remaining-gap-audit.md`。

---

## 1. Pre-deployment Checks

| # | Check | 現状 |
|---|---|---|
| 1 | Backend Full Test Suite green | `./mvnw clean test`（Legacy Demo MySQL + Prototype PostgreSQLがLocal/Demo環境で起動していることが前提。Production Legacy DBへの接続はまだ存在しない） |
| 2 | Frontend Build/Lint green | `npm run build` / `npm run lint` |
| 3 | Flyway Migration整合性 | `flyway:validate`相当（Spring Boot起動時に自動実行、`PrototypeFlywayConfig`）。詳細は2章 |
| 4 | SafetyGuard Allowlist確認 | `SafetyGuardEnvironmentPostProcessor`のHost/DB Allowlistが対象環境を許可しているか（**Production向けにはこのAllowlist自体の拡張が必要 - 本Phaseでは実施していない**、19章） |
| 5 | Legacy READ ONLY確認 | Production Legacy DB Userが本当にSELECT-onlyか（`demo-data/03-readonly-user.sql`と同じGRANT/REVOKEパターンをProduction Legacy DBAへ依頼する必要 - TBD） |
| 6 | Secret未Commit確認 | `application.yml`にProduction Secretが含まれていないこと（現状Demo専用平文値のみ、`docs/production-readiness-and-integration-boundary-audit.md` 8章） |
| 7 | E2E Full Regression green | `npx playwright test`（Demo環境限定、実Production環境での実行は未検証） |

---

## 2. Required Environment / Configuration

現在の`backend/src/main/resources/application.yml`は**単一File**であり、Profile別のOverride File（`application-demo.yml`等）は存在しない。Production化には以下のConfiguration Contractが必要（値は本Phaseで投入しない）:

| Key | 現状（Demo値、Commit済み） | Production要件 |
|---|---|---|
| `spring.profiles.active` | `local`/`demo`/`test`のいずれか必須（`SafetyGuardEnvironmentPostProcessor`が強制） | **新しいProduction Profile名が必要**（例: `production` - 本Phaseでは追加していない。追加した場合、SafetyGuardのAllowlistにこのProfile名を含める設計変更が必須になる） |
| `app.legacy.datasource.jdbc-url` | `jdbc:mysql://localhost:33061/legacy_demo` | Production Legacy MySQLの実接続情報（TBD、`docs/production-readiness-and-integration-boundary-audit.md` 9章のHosting未確定） |
| `app.legacy.datasource.username`/`password` | Demo平文値 | 環境変数/Secret Manager経由（未選定、8章） |
| `app.prototype.datasource.jdbc-url` | `jdbc:postgresql://localhost:54321/gsys_portal` | Production PostgreSQLの実接続情報（TBD） |
| `app.prototype.datasource.username`/`password` | Demo平文値 | 環境変数/Secret Manager経由（未選定） |
| `server.port` | `8080` | Production Reverse Proxy/Load Balancer構成に依存（TBD） |
| `management.endpoints.web.exposure.include` | `health`のみ | Production運用でMetrics等を追加公開するかは別途検討（現状は意図的に`health`のみ、`docs/production-readiness-and-integration-boundary-audit.md` §4） |
| `logging.pattern.console` | correlationId付きConsole出力 | Production Log集約基盤（未選定）へのShip方式はTBD |

**Configuration Contractの原則**（本Phaseで確定、値は未投入）: 上記いずれも「値をどこから読み込むか」の型（環境変数/Secret Manager経由）だけが決まっており、**実際のProduction値・実際のSecret Manager製品は本Phaseで一切投入・選定していない**。

---

## 3. Migration Check

Flyway、`V1`〜`V17`まで全てForward-only（Legacy MySQLには一切適用されない、Prototype PostgreSQL専用）。

- 起動時に自動的に`flyway:validate`相当のチェックが走る（`PrototypeFlywayConfig`、既存の全Phaseで確認済みの挙動）。
- Rollback用のDown Migrationは存在しない（Flywayの標準的な運用方針どおり、Forward-onlyを前提とした設計）。Migration失敗時のRecoveryは7章参照。
- V17（本Phase追加、`idempotent_operation`）は他Tableとの外部キー依存を持たない独立Tableであり、Migration失敗時の影響範囲は限定的。

---

## 4. Health Verification

Phase 8-Lで実装した2つのHealth Endpoint（`docs/production-readiness-and-integration-boundary-audit.md` §2-4の実装結果）:

| Endpoint | 認証 | 内容 | Deploy後の確認方法 |
|---|---|---|---|
| `GET /actuator/health` | 不要（`SecurityConfig`でpermitAll） | Application + Portal DB（`PortalDatabaseHealthIndicator`）。`{"status":"UP"}`のみ、詳細非公開（`management.endpoint.health.show-details=never`） | Deploy直後、Load Balancer/Orchestratorのヘルスチェック対象として利用可能 |
| `GET /api/health/legacy` | 不要 | Legacy G-SYS Adapterの疎通のみ（`LegacyHealthCheckService`）。`{"status":"UP"\|"DOWN"}`、200/503 | Legacy接続確立後に確認。**このEndpointがDOWNでもApplication自体はUPのままであることを確認**（設計上必ずそうなる、両者は独立したEndpoint） |

Deploy後は両方を叩き、`/actuator/health`がUPであることを必須条件、`/api/health/legacy`はLegacy接続がまだ確立していない段階（本Phase時点）ではDOWNでも許容されることを確認する。

---

## 5. Smoke Test

Production/Staging相当環境へのDeploy直後に確認すべき最小限のシナリオ（実際のBusiness Dataを使わない、既存Demo Scenario相当の操作のみ）:

1. `GET /actuator/health` → 200 `{"status":"UP"}`
2. ログイン（`POST /api/auth/login`）→ セッションCookie発行確認
3. 認証必須Endpoint（例: `GET /api/orders/history`）→ 未認証で401、認証後200
4. 存在しないリソースへのアクセス（例: `GET /api/orders/999999999`）→ 404、`errorCode`フィールドを含む構造化Error Response（Phase 8-L §5-7）
5. Correlation ID（`X-Correlation-Id`Response Header）が全Response（成功・エラー問わず）に付与されていることを確認（Phase 8-L §8）

**実際のOrdering Workflow（Draft作成〜承認等）やLegacy接続を伴う機能（Candidate List等）のSmoke Testは、Production Legacy接続が確立してから追加で必要**（TBD、6章参照）。

---

## 6. Failure Detection（Monitoring Runbook, §21）

現在のPortalにおける具体的なFailure Pointと、その観測ポイント（監視製品は未選定、TBD）:

| Failure Point | 観測方法（現状実装済み） | 現在のResponse | 備考 |
|---|---|---|---|
| **Application unavailable** | `GET /actuator/health`が応答しない、またはUP以外 | 該当なし（Application自体が落ちている） | Load Balancer/OrchestratorのHealth Check失敗として検知（製品TBD） |
| **Portal DB unavailable** | `GET /actuator/health`が`{"status":"DOWN"}` | 個別APIは`PORTAL_DB_ERROR`（503、Phase 8-L §6-7） | `PortalDatabaseHealthIndicator`が検知 |
| **Legacy unavailable** | `GET /api/health/legacy`が503 | 個別APIは`LEGACY_UNAVAILABLE`（503、Phase 8-L §10、`LegacyFailureTranslatingJdbcTemplate`が変換） | **Application自体はUPのまま**（§3の設計方針どおり、Legacy障害はApplication全体をDOWNにしない） |
| **External integration failed** | 該当機能が未実装のため現状発生し得ない（Official PO Handoff/実SMTP/EDI、いずれも7-C2B/7-C4/EDI実連携が未着手） | — | 実装時にIdempotency Foundation（Phase 8-L §11-16、`IdempotencyService`）のSTARTED/FAILED状態が観測ポイントになる想定（TBD、実装は本Phaseの範囲外） |
| **Validation/Business Rule違反** | 個別API Response | `VALIDATION_ERROR`（400）またはBusiness固有errorCode（Phase 8-L §5-7） | 想定内の挙動、障害ではない |
| **予期しない例外** | 個別API Response + Server Log（ERROR、Stack Trace付き、Client Responseには含まれない） | `INTERNAL_ERROR`（500） | `GlobalExceptionHandler.handleUnexpected` |

すべてのAPI Response（成功・エラー問わず）は`X-Correlation-Id`を持ち、Server LogにもMDC経由で同じIDが付与される（`CorrelationIdFilter`）ため、特定のFailureをClientからのReport（Correlation ID付き）とServer Logで突合できる。

**未選定（TBD）**: Log集約基盤・Metrics基盤・Alert基盤（`docs/production-readiness-and-integration-boundary-audit.md` §12で確認済みのGap、本Phaseでも製品選定は行わない）。

---

## 7. Rollback Concept

| 対象 | Rollback方法（概念） | 現状 |
|---|---|---|
| Application（Backend/Frontend） | 前Versionへの再Deploy（Blue/Green、Canary等の具体的方式はHosting決定に依存、TBD） | Deployment Pipeline自体が未構築（`docs/production-readiness-and-integration-boundary-audit.md` §15） |
| Flyway Migration | **Down Migrationは存在しない**。新Migrationが問題を起こした場合、Forward-onlyの原則上、修正Migrationを追加する方式が基本（Flyway標準運用）。緊急時のみDB Snapshot Restore（8章） | 未実施・未演習 |
| Portal DB Data | 8章のBackup/Restore概念を参照 | 未実施 |

---

## 8. DB Recovery Concept

| 対象 | Backup対象（技術的Draft、Retention期間はCustomer Review Wait） | 現状 |
|---|---|---|
| Portal PostgreSQL（Business Data: `portal_order`等） | Table一覧は`DemoResetRunner`のTRUNCATE対象リストと同一（`portal_order`/`portal_order_detail`/`portal_order_revision`/`portal_order_revision_detail`/`supplier_response`/`supplier_response_detail`/`order_attention`/`audit_event`/`official_po_integration_request`/`follow_up_case`/`legacy_po_baseline`/`price_change_set`/`price_change_set_detail`/`idempotent_operation`（Phase 8-L追加）） | Backup機構自体が未実装（`docs/production-readiness-and-integration-boundary-audit.md` §11/§16） |
| Portal PostgreSQL（設定Master: `portal_user`/`supplier_contact`/`mail_template`） | Demo Resetでも保持される「設定的」Data - Backup優先度は高い | 同上 |
| Recovery Point Objective (RPO) / Recovery Time Objective (RTO) | 未確定 | Customer Review Wait |

**Official PO Artifact自体が未確定**（`docs/production-readiness-and-integration-boundary-audit.md` §4）のため、Artifact（生成Excel等）のBackupは対象外・回答待ちのまま。

---

## 9. Escalation Points

| Scenario | Escalation先（現状） |
|---|---|
| Legacy接続不能が継続 | TBD（Production Legacy DB運用担当者 - Ernest確認待ち事項、`docs/production-readiness-and-integration-boundary-audit.md` §18B） |
| Portal DB障害 | TBD（Infrastructure担当、Hosting未確定のため担当自体も未確定） |
| Migration失敗 | TBD（本Runbookの範囲では技術手順のみ整理、対応の意思決定者はCustomer Review Wait） |
| Business Ruleに関する疑義（例: Attentionの扱い） | 既存の`docs/customer-review-decision-package.md`のプロセスに従う |

**「誰が」対応するかは、`docs/production-readiness-and-integration-boundary-audit.md` §14（Error Recovery Gap）で既に確認済みのとおりCustomer Review Wait**。本Runbookは技術的な検知・切り分け手順までを整理し、対応責任者の決定は行わない。

---

## 10. Explicitly Out of Scope（本Documentで扱わないこと）

- 具体的なHosting Vendor・Cloud Provider（AWS/GCP/Azure等）の選定・設定手順
- 具体的なSecret Manager製品・Monitoring製品・Log集約製品の選定・設定手順
- SafetyGuardのProduction Allowlist拡張の実装（本Phaseでは意図的に行っていない、19章参照）
- Production Legacy/SMTP/EDIへの実接続手順（いずれも未実装）
- Business Retention Policy・Recovery Point/Time Objectiveの正式な数値決定（Customer Review Wait）

---

**変更したFrontend/Backend/DB Migration/API/Legacy Source: 0件（本Document自体は新規作成だが、Phase 8-Lの実装内容を記述するのみで、Documentの作成自体はCode変更ではない）。**
