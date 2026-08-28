# Official PO Integration Foundation — Phase 7-C2A 実装結果

`docs/official-po-integration-detailed-design.md`（Phase 7-C2-Design）のうち、Phase 7-C2Aで実装したFoundation部分（Integration Request/State/Preflight/Excel Generator Foundation/UI）の実装結果の詳細記録です。

Scope: 将来の APPROVED → Official PO生成 → G-SYS Import → G-SYS登録確認 → Supplier Send に必要なIntegration基盤をPortal側に実装。**LegacyへのFile投入・Legacy DB WRITE・Official PO正式採番・実メール送信はいずれも行っていない。**

## 1. PO番号概念整理

| 概念 | 実体 | 役割 |
|---|---|---|
| Portal Order ID | `portal_order.id` | Portal内部一意識別子 |
| Portal Draft No. | `portal_order.draft_no`（`DRAFT-yyyyMMdd-####`） | Draft段階の表示番号 |
| Portal Display Order No. | `portal_order.prototype_po_no`（`PO-DEMO-yyyyMMdd-####`） | Approved段階の表示番号。既存Demo互換のため削除せず維持 |
| **G-SYS Official PO No.（新規）** | `portal_order.official_po_no`（V9で追加、nullable） | Legacy `TR_PO.PO_NO`相当。**このPhaseでは常にNULL** |

`officialPoNo`はDomain（`PortalOrder.java`）に独立Fieldとして追加済み。Javadocで`prototypePoNo`との違いを明記（3者の混同防止）。正式採番ロジックは一切実装していない（7章のGate）。

## 2. Integration State Model（最終決定）

Business Workflow Statusとは完全に分離した、独立の状態軸として実装:

```
PENDING → GENERATED → SUBMITTED → CONFIRMED
              ↓            ↓
            FAILED       FAILED
```

`NOT_REQUESTED`は**永続化しない**（Requestの行が存在しないこと自体がNOT_REQUESTEDを意味する）。GENERATEDを追加した理由: Excel生成とLegacy投入を別ステップに分離するため（7-C2A 2章の検討どおり）。

Preflight BLOCKED は Integration Status を変化させない（18章）。Requestは常にPENDINGのままで、`preflightResult`列（別軸）にBLOCKED/WARNING/PASSを保持する。State Model を不自然に増やさない、という指示を踏まえた設計。

このPhaseで実際にBusiness ActionからPersistされるのは **PENDINGのみ**。GENERATED/SUBMITTED/CONFIRMED/FAILEDへの遷移メソッド（`OfficialPoIntegrationRequest.markGenerated/markSubmitted/markConfirmed/markFailed`）はEntity上に用意したが、Controller/Serviceのどのコードパスからも呼び出されない（`OfficialPoIntegrationRequestTest`でState Machineとしての整合性のみを検証）。

`portal_order.status`（Business Workflow Status）には`OFFICIAL_PO_CREATED`等を一切追加していない。

## 3. Integration Request Model（DB設計）

`official_po_integration_request`（V9 migration, Prototype PostgreSQLのみ）:

```
id, portal_order_id (FK), revision_no (default 1),
official_po_no (nullable), status,
generated_file_key (nullable),
requested_by, requested_at,
generated_at / submitted_at / confirmed_at / failed_at (すべてnullable),
error_code / error_message (nullable), retry_count,
preflight_result / preflight_issues_json / preflight_at (nullable),
created_at, updated_at
```

Unique制約: `(portal_order_id, revision_no)` — Idempotencyの基盤。

## 4. Idempotency

`OfficialPoIntegrationService.requestIntegration()`は`findByPortalOrderIdAndRevisionNo`で既存行を検索し、存在すれば新規作成せず**同一行を再利用**（Preflightのみ毎回再実行して最新化）。二重クリック・二重呼び出しでも行が増えないことを`doubleRequestIsIdempotent_noDuplicateRowCreated`テストで確認済み。Business Action（`POST /api/orders/{id}/official-po/request`）として実装、汎用Status更新APIは作っていない。

## 5. Request API

| Method | Path | 認可 |
|---|---|---|
| GET | `/api/orders/{id}/official-po` | 認証済みユーザー全員 |
| POST | `/api/orders/{id}/official-po/request` | ADMIN限定 |

命名は既存の`/api/orders/{id}/submit-for-approval`等のBusiness Action規則に準拠。Order Status = APPROVEDでない場合は409 `ORDER_NOT_APPROVED`。

## 6. Permission

ADMIN限定（7-C2A 6章の標準推奨どおり）。`@PreAuthorize("hasRole('ADMIN')")`をController側で強制。OPERATORが直接APIを叩くと403 `FORBIDDEN`（`OfficialPoIntegrationApiTest.operatorCallingRequestDirectlyGets403`で確認）。閲覧（GET）は制限なし。自己承認Scope（CUSTOMER REVIEW）とは明確に区別し、混同していない。

## 7. Excel Generator

`OfficialPoExcelGenerator`（`service/excel`パッケージ）: 純粋関数（`OfficialPoExcelInput` → `byte[]`）。Apache POI 5.2.5（Legacyの古いPOI 3.9とは別、新規Portal Codeとして最新版を採用）。Filesystem/Legacy Import Folderへの書き込み責任は一切持たない。**このPhaseでは、どのController/APIからも呼び出されない**（正式PO番号が確定しないため、9章のGateにより実業務Flowでは利用できない） — Unit/Contract Testからのみ利用。

Currency（3.4章で確認済みの「Cell Number Format依存」）はExcel標準の`[$記号]#,##0.00`形式で埋め込み、`AbstImportBatch.getCcyCode()`のマッチャーと整合することをContract Testで実証。

## 8. Excel Contract Test

`OfficialPoExcelGeneratorContractTest`: `PrOfficialPoImportBatch.java`のROW_IDX/COL_IDX定数を独立に再宣言し、Generator出力を突き合わせ。Header文字列・PO No.位置・Item行位置・Currency Cell Format・Order Dateの型（真のExcel Date）を検証。Legacy Source/Test Fileは一切変更していない。

## 9. Preflight

`OfficialPoPreflightService` + `OfficialPoPreflightReadRepository`（Legacy READ ONLY, `legacyTransactionManager`）:
- Supplier存在確認（`ms_comm` / `MS_SUPPL`）
- Brand存在確認（`ms_comm` / `MS_BRAND`）
- Item存在確認（`ms_item`, 削除フラグ考慮）
- 常時付与される情報Issue: `OFFICIAL_PO_NO_NOT_ASSIGNED`（正式PO番号未確定のため既存PO/Invoice/Stock-in確認は次Phase以降）

Existing PO/Invoice/Stock-in確認は、正式PO番号が無いと対象を特定できないため、このPhaseでは実行不可能であることを明示（Issueとして可視化、サイレントskipにしない）。

## 10. Preflight Result

構造化Result（`OfficialPoPreflightResult` = `result`(PASS/WARNING/BLOCKED) + `issues[]`）。各Issueは`code`/`severity`/`message`/`skuCode`を持つ。`severity`集約ロジック: BLOCKED優先 > WARNING > (INFOのみならPASS)。Legacy例外を生のままUIに出さない。

## 11. Order Detail UI

`OrderHistoryDetailPage`に「G-SYS正式PO連携」Sectionを追加。表示条件: `status===APPROVED`（ADMIN操作可能な間）または既にRequestが存在する場合（Demo Send後もSection自体は残り、Foundationの履歴が消えない）。表示内容: Integration Status/正式PO番号（未設定時は明示的な非エラー表示）/Revision/申請日時/申請者/最終Preflight結果+Issue一覧。

## 12. Demo Sendとの分離

既存Demo Send機能は無変更。新Sectionには「デモ送信とは独立した、将来の正式G-SYS連携のための機能です」という説明文を明記し、Production Target CTAとの混同を防止。

## 13. Audit

新規AuditEvent種別2つ: `OFFICIAL_PO_INTEGRATION_REQUESTED`（Request初回作成時のみ）、`PRECHECK_COMPLETED`（Preflight実行毎、noteにPASS/WARNING/BLOCKEDサマリ）。正式登録を意味するEventは作っていない。

## 14. DB Migration（V9）

`portal_order.official_po_no`列追加＋部分UNIQUE index、`official_po_integration_request`テーブル新設、`audit_event.event_type`列を30→50文字へ拡張（`OFFICIAL_PO_INTEGRATION_REQUESTED`が33文字で既存の30文字制限を超過するため）、CHECK制約に新Event種別2つを追加。既存Demo Reset（`DemoResetRunner`）のTRUNCATE対象に`official_po_integration_request`を追加（FK制約により必須）。

## 15. Backend Full Test

`mvn clean test`: **202 tests, 0 failures, 0 errors, 0 skipped**（21クラス、Phase 7-C1時点の169から+33: Integration Request Test/Idempotency Test/Permission Test/Preflight Test/Excel Generator Contract Test/State Transition Test を追加）。

## 16. Safety / READ ONLY

- Legacy JDBC READ ONLY: `OfficialPoPreflightReadRepository`は全メソッド`@Transactional(readOnly=true, transactionManager="legacyTransactionManager")`。`LegacyReadOnlyIntegrationTest`にTR_PO INSERT拒否テストを追加し確認。
- Production Host reject: SafetyGuard関連テスト全件成功（既存どおり変更なし）。
- Import Folder Writeなし: `OfficialPoExcelGenerator`はFilesystemに一切触れない（`byte[]`を返すのみ）。
- SMTPなし、SYS_SEND_MAIL INSERTなし、External Serviceなし: いずれも本Phaseのコードパスに存在しない。

## 17. Frontend Build/Lint

`tsc -b && vite build`成功。oxlintエラー0（既存warning 4件のみ、本Phase起因なし）。

## 18. Full E2E

30 tests（既存5ファイル＋新設`official-po-integration.spec.ts`）、2回連続実行で完全安定（各回30/30 passed、skipなし）。

## 19. Browser Scenario A〜E

| Scenario | 内容 | 結果 |
|---|---|---|
| A | ADMIN: APPROVED Order→Order Detail→G-SYS連携準備→Request作成→Preflight結果表示（PASS）→正式PO番号未設定確認 | ✅ |
| B | 同一Order/Revisionで二重Request→Revision 1のまま、行が増えない | ✅ |
| C | OPERATOR直接API呼び出し→403 FORBIDDEN、UI上もボタン非表示 | ✅ |
| D | DRAFT/PENDING_APPROVAL OrderからRequest→409 ORDER_NOT_APPROVED | ✅ |
| E | 一連の操作後もStatusはPENDINGのまま（SUBMITTED/CONFIRMEDへは到達不可能）、正式PO番号も未設定のまま | ✅ |

## 20. CUSTOMER REVIEW残件（変更なし、7-C2-Designから継続）

Official PO No.完全採番規則／PO番号決定者／Import Folder実Path／Import Trigger／手作業Excel Upload継続可否／Initial PO要否／Revision再投入範囲／Integration失敗責任者／Official PO取消／実行頻度 — いずれも本Phaseでは決定していない。

## 21. Legacy変更ゼロ確認

`phasep-gulliver`に対する変更は本Phase中ゼロ（`git diff -w --numstat`で実質差分0件、HEADも不変）。Write/Edit系ツールは一度も呼んでいない。
