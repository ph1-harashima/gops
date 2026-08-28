# Role / Approval Workflow — Phase 7-C1 実装結果

このファイルは `docs/target-production-procurement-workflow.md`（Phase 7-B設計）のうち、Phase 7-C1で実装した範囲（Role / Approval Foundation）の実装結果の詳細記録です。設計原則・CUSTOMER REVIEW項目そのものは7-B側が一次情報のままとし、ここでは「実装として何をどう作ったか」「Sourceのどこにあるか」に絞ります。

Scope: Draft → Approval（承認依頼／承認／修正のうえ承認／差し戻し）＋ Audit Trail のみ。正式PO番号の発行・Official PO Excel生成・G-SYS WRITE・実メール送信（SYS_SEND_MAIL INSERT）は明示的にスコープ外（次Phase以降）。

## 1. 既存実装の監査結果（着手前）

実装前に以下を確認済み（Phase 7-C1指示 2章）：

- **認証**: `PortalUser`（username / password_hash / display_name / role）+ Spring Security Session Form Login（`SecurityConfig`, `PortalUserDetailsService`, `PortalUserPrincipal`）。ログインIDは username（emailではない）。
- **Role**: 実装前は `PURCHASE` / `SALES_ADMIN` / `SYS_ADMIN` の3値。PO操作に対するRole別ガードは実質皆無（7-A §15で確認済みのLegacy同様の状態がPrototype側にも実質そのまま踏襲されていた）。
- **Order Status遷移**: `OrderStatusTransitionService` に `confirm()`（DRAFT→READY_TO_ORDER）/ `returnToDraft()`（READY_TO_ORDER→DRAFT）/ `demoSend()`（READY_TO_ORDER→SENT→AWAITING_SUPPLIER）のみ。承認の概念なし。
- **Draft API**: `OrderDraftController` / `OrderDraftPersistenceService` — 編集可否は `status == DRAFT` の一点判定のみ、Ownership（作成者）判定なし。
- **AuditEvent**: 既存テーブル・Entity（`event_type` CHECK制約つき、`note` 列は未使用のフリーテキスト列として既に存在）。新規Audit基盤は不要と判断（8章参照）。
- **Flyway**: V1〜V7がPrototype PostgreSQLのみに適用。Legacy MySQLは対象外（既存構成のまま）。

## 2. User / Role 最終モデル

`portal_user` テーブル：`id / username / display_name / password_hash / role / email(nullable, 新規) / active`。

Role は2値に集約：`OPERATOR`（起票・承認依頼）/ `ADMIN`（承認・差し戻し・修正承認・Master管理）。

**ログインIDの扱い（7-C1 3章の分岐報告）**: Target設計（7-B 17章）はemailをログインIDとする方針だが、既存ログインAPI（`AuthController`）・E2Eログインヘルパー・Frontend `LoginPage`/`AuthContext` は username 前提で広く配線されており、この切替は認証まわり全体に影響が及ぶ規模。ユーザーの明示許可（7-C1 3章）に基づき、**このPhaseでは Role Foundation のみを実装し、Email Login への切替は後続Stepへ分割**した。`email` 列は追加・全デモユーザーへ値を投入済み（`*.@portal-demo.invalid`）だが、ログイン自体は引き続き username で行う。

## 3. Permission Matrix（Backend実装・実測）

| 操作 | OPERATOR | ADMIN | 実施箇所 |
|---|---|---|---|
| Draft作成 | ○ | ○ | `OrderDraftController.createDraft` |
| Draft閲覧 | ○ | ○ | `OrderDraftController.getDraft` |
| DRAFT状態のDraft編集 | ○（自分がcreatedByの場合のみ） | ○（誰のでも） | `OrderDraftPersistenceService.update` |
| PENDING_APPROVAL状態のDraft編集 | × | ○（修正のうえ承認の準備） | 同上 |
| 承認依頼（submit-for-approval） | ○（自分がcreatedByの場合のみ） | ○ | `OrderStatusTransitionService.submitForApproval` |
| 承認（approve） | × | ○ | `OrderApprovalController.approve` — `@PreAuthorize("hasRole('ADMIN')")` |
| 差し戻し（return-for-correction） | × | ○ | `OrderApprovalController.returnForCorrection` — 同上 |
| APPROVED→DRAFT（return-to-draft） | × | ○ | `ReturnToDraftController` — 同上 |
| PO Preview閲覧 | ○ | ○ | `PoPreviewController`（Status: DRAFT/PENDING_APPROVAL/APPROVED） |

**Backend強制**（7-C1 4章/17章 — Frontendのボタン非表示のみでは権限制御にしない）:
- ADMIN限定操作は Spring Security `@PreAuthorize("hasRole('ADMIN')")`（`SecurityConfig` に `@EnableMethodSecurity` 追加）。
- Ownership判定（自分のDraftか）は `AccessDeniedException` を投げ、`SecurityConfig` のカスタム `accessDeniedHandler` が `403 {"errorCode":"FORBIDDEN"}` を返す（`@PreAuthorize` 違反と同じ形状・同じ経路）。
- HTTPレベルで直接検証済み: `ApprovalWorkflowApiTest`（OPERATORが `/approve` `/return-for-correction` `/return-to-draft` を直接叩いて403になることを確認）。

## 4. Workflow Status

Target設計（7-B 5.1章）の6値のうち、このPhaseで実装したのは3値の遷移のみ：

```
DRAFT --submit-for-approval--> PENDING_APPROVAL --approve--> APPROVED
PENDING_APPROVAL --return-for-correction--> DRAFT
APPROVED --return-to-draft(ADMIN)--> DRAFT
```

**既存Prototype Statusとの migration mapping**（7-B 5.1章の対応表どおり、V8で実施）:
- `READY_TO_ORDER` → `APPROVED`（既存の全Order行をUPDATE。以後このStatusを書き込むコードは存在しない）
- `AWAITING_SUPPLIER` / `SUPPLIER_CONFIRMED` はそのまま維持（Demo Send以降の遷移ロジックのみ入口StatusをAPPROVEDに変更）

## 5. Approval API（Business Action方式・7-C1 7章）

汎用Status更新APIは作らず、明示的なBusiness Action APIとして新設：

| Method | Path | 認可 | 説明 |
|---|---|---|---|
| POST | `/api/orders/{id}/submit-for-approval` | 本人 or ADMIN | DRAFT→PENDING_APPROVAL |
| POST | `/api/orders/{id}/approve` | ADMIN | PENDING_APPROVAL→APPROVED（初回のみPO No.採番） |
| POST | `/api/orders/{id}/return-for-correction` | ADMIN | PENDING_APPROVAL→DRAFT（`{"reason": "..."}` 必須） |

いずれも `OrderApprovalController`（新設）。既存の `SupplierWorkflowController` / `ReturnToDraftController` と同じ「Workflow区分ごとに1 Controller」の既存規約を踏襲。

## 6. Submit for Approval（DRAFT→PENDING_APPROVAL）

`OrderStatusTransitionService.submitForApproval(id, performedBy, performerIsAdmin)`:
- Ownership: `performerIsAdmin` が false かつ `performedBy != order.createdBy` の場合 `AccessDeniedException`。
- 発注可能行が1件もない場合 `NoOrderableItemsException`（400 `NO_ORDERABLE_ITEMS`、既存バリデーションを流用）。
- Audit: `SUBMITTED_FOR_APPROVAL`（orderId/performedBy/performedAt）+ `STATUS_CHANGED`（DRAFT→PENDING_APPROVAL）。

## 7. Approve（PENDING_APPROVAL→APPROVED）

`OrderStatusTransitionService.approve(id, performedBy)`:
- 発注可能行の再検証（Submit後にNGになっていないか）。
- PO No.採番: `order.getPrototypePoNo() == null` の場合のみ新規採番。差し戻し→再承認のサイクルでは既存PO No.を再利用（重複採番しない）。
- **正式PO Excel生成・G-SYS送信・メール送信はこの時点で一切行わない**（7-C1 9章の明示的スコープ外）。

## 8. Edit-and-Approve（修正のうえ承認）

新しいAudit Frameworkは作らず、既存 `AuditEvent` 構造を最大限利用（7-C1 8章の指示どおり）。

判定ロジック（`approverChangedDraftSinceSubmission`）: そのOrderの直近の `SUBMITTED_FOR_APPROVAL` イベントのタイムスタンプ以降に、**同一承認者による** Draft変更系イベント（`ORDER_QTY_CHANGED` / `ORDER_DATE_CHANGED` / `REQUESTED_DELIVERY_CHANGED` / `REMARK_CHANGED`）が1件でも存在すれば `APPROVED_WITH_CHANGES` を、なければ `ORDER_APPROVED` を書き込む。個々のBefore/After行は、ADMINがDraft画面から保存した時点で既存の `OrderDraftPersistenceService` が通常どおり書き込み済みのものをそのまま利用する（Approve側で複製・再構築はしない）。

## 9. Return for Correction（差し戻し）

- 理由は必須（`reason` が null/空白なら400 `RETURN_REASON_REQUIRED` — `ReturnReasonRequiredException`）。
- 理由の保存先は既存 `AuditEvent.note`（新規列は追加しない）。`RETURNED_FOR_CORRECTION` イベントの `note` に理由を格納。
- Draft画面での表示: `OrderDraftService.resolveReturnReason()` がAudit Trailを遡り、直近の `RETURNED_FOR_CORRECTION` を「その後 `SUBMITTED_FOR_APPROVAL` が発生していない」かつ「現在Status=DRAFT」の場合のみ表示対象として返す（再提出すると自動的に消える）。

## 10. Ownership

既存 `PortalOrder.createdBy` をそのまま利用（新規列・新規Supplier/Brand別担当割当ロジックは追加しない — 7-C1 13章の明示的な指示どおり）。

## 11. Dashboard 承認待ちKPI

`DashboardResponse.pendingApprovalCount`（`DashboardService` で `status == PENDING_APPROVAL` をカウント）。Frontendは既存のDashboard KPI→Order List Deep Linkパターンをそのまま再利用（`/orders/history?status=PENDING_APPROVAL`）。新規画面・新規APIは追加していない。

## 12. Order Detail（発注詳細）の変更

`OrderHistoryDetailPage`:
- `PENDING_APPROVAL`: ADMINには「修正」（Draft編集へ）「承認」「差し戻し」の3アクション、OPERATORには読み取り専用の「承認待ちです」インジケーターを表示。
- `APPROVED`: 既存の単一primaryActionパターンに「PO Previewを見る」を追加（旧 `READY_TO_ORDER` ケースを置き換え）。

## 13. PO Preview との関係

`PoPreviewService` の閲覧可能Status集合を `DRAFT/READY_TO_ORDER` → `DRAFT/PENDING_APPROVAL/APPROVED` に拡張。

旧 `POST /orders/drafts/{id}/confirm`（DRAFT→READY_TO_ORDER、「発注内容を確定」ボタン）は**このPhaseで完全に削除**（Controller/Service双方、対応するE2E `PoPreviewConfirmApiTest` も削除）。Demo Send（メーカーへ送信）の入口StatusをREADY_TO_ORDER→APPROVEDに変更。Official PO生成・G-SYS送信に相当する機能はPreview画面に元々存在せず、Demo Send自体がDemo専用機能である旨は既存コメント（`manufacturerCommunication.demoNotice`）のとおり変更なし。

## 14. Audit / Timeline

新規AuditEvent種別4つ（`ck_audit_event_type` へ追加、既存全種別との併記）: `SUBMITTED_FOR_APPROVAL` / `ORDER_APPROVED` / `APPROVED_WITH_CHANGES` / `RETURNED_FOR_CORRECTION`。i18n（ja/en）も追加済み。

## 15. DB Migration（V8, Prototype PostgreSQLのみ）

`backend/src/main/resources/db/migration/V8__role_approval_foundation.sql`:
1. `portal_user.email` 列追加（nullable、部分UNIQUE index）。
2. 既存3ユーザーのRole再マッピング（`purchase01`→OPERATOR、`sales_admin`/`sys_admin`→ADMIN）+ 新規ADMIN専用デモユーザー `admin01` 追加。
3. `portal_order.status` の `READY_TO_ORDER`→`APPROVED` データ移行（CHECK制約をDROP→UPDATE→新allow-listでADD、の順序で実施 — 移行前に制約を外さないとUPDATE自体が失敗する）。
4. `audit_event.event_type` のCHECK制約を新4種別を含む全allow-listで再定義。

Forward-only。Legacy MySQLへは一切触れない。ローカル使い捨てDocker Prototype Postgresで実行・検証済み（後述17章）。

## 16. Demo User

| username | password | Role |
|---|---|---|
| purchase01 | DemoPass123! | OPERATOR |
| sales_admin | DemoPass123! | ADMIN |
| sys_admin | DemoPass123! | ADMIN |
| admin01（新規） | DemoPass123! | ADMIN |

`admin01` は7-C1 18章の「専用ADMINデモユーザー」要求に対応する新規追加。パスワードハッシュは `purchase01` のV5行から再利用（同一デモパスワードのBCryptハッシュ、コミットして問題ない）。Production認証情報とは無関係（`.invalid` TLDのデモ用emailも同様）。

## 17. Authorization Test（実施結果）

`ApprovalWorkflowApiTest`（新設）でHTTPレベル実測:
- 未認証での各Approval系エンドポイント呼び出し → 401。
- OPERATOR（purchase01）が `/approve` `/return-for-correction` `/return-to-draft` を直接叩く → 403 `FORBIDDEN`（3ケースとも確認）。
- 完全なsubmit→approve flow、PO No.採番タイミング、重複approve拒否（409）、差し戻し→理由必須→再提出まで一気通貫でテスト。

## 18. 回帰結果

- **Backend Full Test**（`./mvnw clean test`、ローカル使い捨てDocker Prototype Postgres対象）: **169 tests, 0 failures, 0 errors, 0 skipped**（19クラス）。`clean` なしの再実行では過去の削除済みテストクラスの stale surefire report が残存し得ることを確認したため、報告値は必ず `clean test` 実行分。
- **Frontend Build**: `tsc -b && vite build` 成功（警告はチャンクサイズのみ、既存の事象）。
- **Frontend Lint**: oxlint、エラー0（既存warning 4件のみ、いずれも本Phase以前からの既知事象で本Phase起因ではない）。
- **Frontend E2E**: 25 tests（既存4ファイル＋新設 `role-approval-workflow.spec.ts`）、2回連続実行で安定（各回 23 passed / 2 skipped — skipはAWAITING_SUPPLIER関連2件で、直前のテストが同じOrderをSUPPLIER_CONFIRMEDまで進めてしまうことに起因する既存の仕様どおりのskipであり、本Phase由来の新規事象ではない）。
- **Legacy READ ONLY**: `phasep-gulliver` の `git diff -w --numstat` は0件（実質的な内容差分ゼロ）。`git status --porcelain` 上は多数のファイルが「変更あり」と表示されるが、これは全て改行コード（CRLF/LF）差分のみで、`-w`（whitespace無視）を付けると完全に消える。本Phaseで `phasep-gulliver` 配下に対して一度もWrite/Edit系ツールを呼んでいないことも確認済み（Read専用の調査は7-A時点のみ）。Source変更ゼロを確認。
- **Safety Guard**: `SafetyGuardEnvironmentPostProcessorTest` / `SafetyGuardIntegrationTest` とも既存どおり全件成功。Production接続なし、Official PO Importフォルダ未使用、SMTP未使用、SYS_SEND_MAIL INSERTなし。

## 19. Browser Scenario A〜E（`e2e/role-approval-workflow.spec.ts`）

| Scenario | 内容 | 結果 |
|---|---|---|
| A | OPERATOR: Candidate→Draft→承認依頼→承認待ち | ✅ |
| B | ADMIN: Dashboard承認待ちKPI→Order List→Order Detail→承認→APPROVED | ✅ |
| C | OPERATOR提出→ADMIN修正→承認、AuditにAPPROVED_WITH_CHANGESと変更内容が記録されることを確認 | ✅ |
| D | OPERATOR提出→ADMIN差し戻し+理由→OPERATORが理由を確認→編集→再提出 | ✅ |
| E | OPERATORが `/approve` APIを直接呼び出し→403 `FORBIDDEN`（Status不変も確認） | ✅ |

既存 `core-demo-scenario.spec.ts` も新Workflowに合わせて更新（旧「発注内容を確定」ボタンを「承認依頼→（Role切替）→承認」の実ログイン切替に置き換え）、2回連続実行で安定。

## 20. CUSTOMER REVIEW（このPhaseで確定していない事項 — 7-B 21章から継続）

- OPERATOR自己承認可能なSupplier/Regionの範囲（7-B 4章の例外設計候補）。
- 複数段階承認者階層の要否。
- Supplier/Brand別の担当者割当ルール（このPhaseでは意図的に作らず、Ownershipは `createdBy` のみで運用）。
- APPROVED後のキャンセル可否。
- 承認後の再編集の許容範囲（このPhaseはreturn-to-draft経由のみ）。
- 正式PO番号の採番方式そのもの（7-C2スコープ、7-B 8章のCUSTOMER REVIEW #1）。
- 正式PO Excel生成タイミング（7-C2スコープ）。

## 21. Email Login 移行（分割した後続Step）

3章に記載のとおり、ログインID切替（username→email）はこのPhaseでは実施していない。`portal_user.email` は値投入済みだが未使用列。後続Stepで着手する際の影響範囲メモ：`AuthController`（ログインAPIパラメータ）、`LoginPage.tsx`/`AuthContext.tsx`（Frontend）、E2Eログインヘルパー（4ファイル）、Flywayでの一意制約強化（現在は部分UNIQUE indexのみ）。
