# Gulliver 9/17 Phase1 実装レポート

本ドキュメントは `docs/gulliver-20260917-phase1-gap-analysis.md`（READ-ONLY監査、変更なし）を基準に、
「見積調整ではなく実装を優先する」という方針転換の指示に基づき実施した実装 (Phase 1-11) の結果をまとめたものです。

Gap Analysisの内容そのものは一切変更していません。本レポートは実装の事実関係のみを記録する、独立した新規ドキュメントです。

## 0. 前提・絶対ルールの遵守状況

以下の絶対ルールは実装期間を通じて一貫して遵守されています（詳細は各項目参照）。

- Legacy (`phasep-gulliver/`) のソースコード・DBへの書き込み: **一切なし**（READ ONLYでの参照のみ）
- Production接続・Productionデプロイ・Production Import Folder・Production SMTP: **一切なし**
- 実際のEDI通信: **一切なし**
- Secretsへのアクセス: **一切なし**
- 既存のG-OPS挙動・既存約550件のBackend Test/Frontend/i18n/E2Eの破壊: **なし**（全件Green、詳細は9章）
- MS_FORMULA / Recommended Qty計算ロジックの変更: **一切なし**（Legacyのまま。詳細は7章）
- MS_FORMULA編集UIの新規作成: **なし**（意図的に作成せず）
- 自動再発行（Official PO Reissueの自動実行）: **実装せず**（検知のみ、実行は人間の明示的クリックが必須）
- 「誰を必ずCCする」というBusiness Ruleの実装: **なし**（Default CCはprefillのみ、Business Rule化せず）
- 国内/海外向けの新しい発注数量計算式の作成: **なし**（Foundationのみ、計算式はLegacyのまま）
- Company Masterの新規作成: **なし**（既存のまま、TODOとして未着手）

## 1. 実装済み項目 (Implemented)

### Phase 1-3: B-2 / B-1 / B-3・B-4
- B-2: 発注履歴詳細画面のSKUをクリック可能なリンクにし、SKU詳細画面へ`returnTo`付きで遷移。SKU詳細画面側に4つ目のエントリポイントとして`/orders/`起点の遷移を検出する分岐を追加。
- B-1 (承認画面の判断材料): 発注履歴詳細の明細行に「現在庫」「当月販売数」「リードタイム」「未入荷数」を追加表示（Legacy在庫データをバルク取得し結合）。既存テーブルの末尾に追加し、既存のE2E `.nth()` ロケータへの影響を回避。
- B-3/B-4 (ファイル命名規則): `OfficialPO_{Supplier}_{Brand}_{YYYYMMDD}_{PONo}_{Revision3桁}.{拡張子}` 形式のWorking Assumptionとして`OfficialPoFileNaming`を新設。ファイルシステム上安全でない文字（`\/:*?"<>|`および空白）を`_`に置換。既存のImport Folder Contract（Excel生成本体）は変更せず、ファイル名生成のみを差し替え。

### Phase 4: Official PO PDF (C-1)
- 「G-OPS Standard Official PO PDF」として、Excelと同一の`OfficialPoExcelGenerationService.buildInput()`（共有Business Data Source）からPDFBox 2.0.31 + 埋め込みNoto Sans JPフォントでPDFを生成。
- Excel/PDFが同一データソースから生成されることをテストで明示的に検証（数量・PO情報の不一致が構造的に起こり得ないことを保証）。
- 正式フォーマットではない旨（Working Assumption）はコード内コメントで明記。正式なOfficial PO様式としての体裁確認は引き続き顧客確認が必要（8章参照）。

### Phase 5-6: Official PO Reissue / Revision History / Revision Required検知 (C-2/C-3)
- 既存のRevisionモデルを再利用し、Document Lifecycle軸（ACTIVE/SUPERSEDED/CANCELLED）をIntegration Status軸（PENDING/GENERATED/SUBMITTED/CONFIRMED/FAILED）とは独立した別軸として追加。
- Revision History画面: Revision No/作成日時/作成者/PO No/Excel/PDF/連携状況/送信状況を一覧表示。
- Revision Required検知: 「修正版を作成」操作（既存の`ORDER_REVISION_CREATED`監査イベント）が、発行済みIntegration Requestの`submittedAt`（なければ`generatedAt`／`requestedAt`）より後に発生した場合にのみ検知。**自動再発行は実装せず**、バナー表示＋ADMIN明示クリックによる`POST /reissue`のみで再発行を実行。
- 旧Revisionは`SUPERSEDED`として保持（削除しない）、新Revisionが`ACTIVE`になる。

### Phase 7: Official PO Cancel (C-4)
- G-OPS内部のWorkflow状態としてのみ実装（Legacyへの書き込みは一切なし）。理由(reason)必須、監査証跡（誰が・いつ・何を・なぜ）を`AuditEvent`に記録。
- Integration Status（Excel/Import Folder連携状況）は変更されないことをテストで明示的に確認（2軸の直交性の担保）。

### Phase 8: Email To/CC Override + Default CC Foundation (C-5 / §11)
- 送信時のみのTo/CC上書き（one-time send override）と、マスタデータ編集（`portal_mail_settings`、prefill専用）を明確に分離。
- 監査には「Masterで解決されたアドレス」と「実際に送信されたアドレス」の両方＋override有無フラグ＋実行者＋タイムスタンプを記録。
- Local/Demo環境では実SMTP送信は行わず、`LoggingEmailSenderAdapter`（ログ出力のみ）を使用。
- Default CC機能は「マスタへのprefill」のみを実装。「誰を必ずCCするか」というBusiness Ruleは実装していない（管理画面から都度削除可能な単なる初期値）。

### Phase 9: Domestic/Overseas Foundation (§12)
- LegacyのMsComm.java/BusinessLogicUtil.javaをREAD ONLYで調査し、Supplierの国内/海外区分に対応するLegacyフィールドが存在しないことを確認した上で、新規Portal専用Master（`supplier_region_classification`）を追加。
- Order Detail画面に解決済み区分（DOMESTIC/OVERSEAS）をChip表示。**表示のみ**であり、Recommended Qty計算には一切関与しない。
- 新しい国内向け/海外向け計算式は作成していない。将来Strategyパターンで計算式を切り替えられる構造の設計自体も、今回はMaster/表示Foundationの範囲に留め、計算ロジックの分岐実装は行っていない（Recommended QtyはLegacy側で完結しており、Portal側にStrategy切替ポイントを設けると、かえってLegacy計算との二重管理・不整合リスクを生むため）。

### Phase 10: End-to-End Integration
- Scenario 1（通常発注フルフロー）をCandidate→SKU Detail→Draft→承認→正式PO番号確定→Excel→PDF→Import Folder配置(Sim)→G-SYS登録状況確認(Sim)→Mail Preview→メール送信(Sim)→デモ送信→メーカー回答→差異→合意→入荷→完納まで、1本の連続したE2Eテストとして実装・確認。
- Scenario 2 (Reissue)・Scenario 3 (Cancel)・Scenario 4 (Email Override) は各Phaseで追加した専用E2E (`official-po-integration.spec.ts` Scenario K/L、`email-send.spec.ts`の2件目) で個別に確認済み。

### Phase 11: Full Regression
- Backend全テスト・Frontend E2E全テストをまとめて実行し、全件Green（9章参照）。

## 2. Working Assumptions（作業仮定）一覧

以下は要件未確定部分について、安全側・可逆的な形で置いた作業仮定です。いずれも顧客確認により変更され得ます。

1. Official POファイル名フォーマット: `OfficialPO_{Supplier}_{Brand}_{YYYYMMDD}_{PONo}_{Revision3桁}`（B-3/B-4）
2. Official PO PDFのレイアウト・体裁は「G-OPS Standard Format」という自己ラベル付きの独自フォーマット（正式なOfficial PO様式ではない旨を明記）
3. Reissue検知条件: 「発行後に`ORDER_REVISION_CREATED`監査イベントが発生したか」を判定基準とする（`targetRevisionNo`の単純比較ではない - 初回送信直後に誤検知するため採用せず）
4. Email Override機能のUI/UX（To/CCフィールドの編集可否、Override時の表示文言）
5. Default CCは「prefillのみ」であり、Business Rule化はしていない
6. Supplier Region Classification（国内/海外区分）は完全に新規のPortal専用Master。Legacyには対応するフィールドが存在しないことを確認済み
7. Region ClassificationはSupplier単位・Brand単位のいずれでも設定可能（既存のManufacturer Channel Masterと同一の解決優先順位パターンを踏襲）

## 3. 引き続き顧客確認が必要な項目 (Still Requires Customer Confirmation)

- Official PO PDFの正式な体裁・記載項目がこのままで良いか（現状は「G-OPS Standard Format」という自己ラベル付きの代替案）
- Official POファイル名フォーマットが実運用のImport Folder運用と整合するか
- Reissue（再発行）の業務フロー・承認要否（現状は検知＋ADMIN手動実行のみ）
- Cancel/Replaceの業務上の意味づけ（LegacyへのCancel通知が必要か、G-OPS内部状態のみで運用可能か）
- Email To/CC Overrideの利用シーン・権限範囲（現状はADMIN限定）
- Default CCに設定すべき実際のアドレス・運用ルール（現状はBusiness Rule未実装、prefillのみ）
- 国内/海外の区分がSupplier/Brand単位で十分か、より細かい粒度（SKU単位等）が必要か
- 将来的な国内/海外別の発注数量計算式の要否・その具体的な計算ロジック（現状は完全に未着手・未設計）
- Company Masterの必要性・スコープ（今回は未着手のTODO）

## 4. Legacy (`phasep-gulliver/`) 変更確認

- ソースコード変更: **0件**
- DBスキーマ変更・マイグレーション: **0件**
- 書き込みAPI呼び出し: **0件**（すべてREAD ONLY接続経由のSELECTのみ）
- 参照した内容: `MsComm.java`, `BusinessLogicUtil.java` など、Supplierの国内/海外区分に相当するフィールドの有無を確認する目的でのみ参照（9章 Domestic/Overseas Foundationの設計根拠）

## 5. Production変更確認

- Production DB接続: **0件**
- Production Import Folder接続: **0件**
- Production SMTP接続: **0件**
- Productionデプロイ: **0件**
- `SafetyGuardEnvironmentPostProcessor`により、`local`/`demo`/`test`プロファイル以外での起動そのものが拒否される構成を維持（既存の安全機構、変更なし）

## 6. DBマイグレーション一覧（今回追加分）

| バージョン | 内容 |
|---|---|
| V24 | Official PO PDF: `official_po_integration_request`に`pdf_file_key`/`pdf_generated_at`追加 |
| V25 | Audit Event種別追加: `OFFICIAL_PO_PDF_GENERATED`/`OFFICIAL_PO_REISSUED`/`OFFICIAL_PO_CANCELLED`/`EMAIL_RECIPIENT_OVERRIDE_USED`を`ck_audit_event_type`に追加 |
| V26 | Official PO Lifecycle: `lifecycle_status`/`lifecycle_reason`/`lifecycle_changed_by`/`lifecycle_changed_at`追加 |
| V27 | Email Override + Default CC: `order_email`に`master_to_addresses`/`master_cc_addresses`/`recipient_override_used`追加、`portal_mail_settings`テーブル新設 |
| V28 | Domestic/Overseas Foundation: `supplier_region_classification`テーブル新設 |

Prototype PostgreSQL（ローカル・使い捨て環境）のみへの適用。Legacy MySQLへのマイグレーションは一切なし。

## 7. API変更一覧（今回追加分）

- `GET/POST /api/orders/{id}/official-po`, `/official-po/request`, `/official-po/intent`
- `PUT /api/orders/{id}/official-po/number`（正式PO番号確定）
- `POST /api/orders/{id}/official-po/generate`（Excel生成）, `/official-po/place`（Import Folder配置）, `/official-po/confirm-import`（G-SYS登録状況確認）
- `GET /api/orders/{id}/official-po/excel`（ダウンロード）
- `POST /api/orders/{id}/official-po/pdf/generate`, `GET /api/orders/{id}/official-po/pdf`
- `POST /api/orders/{id}/official-po/reissue`, `GET /api/orders/{id}/official-po/revisions`
- `POST /api/orders/{id}/official-po/cancel`
- `GET/PUT /api/admin/mail-settings`（Default CC Foundation）
- `POST /api/orders/{id}/mail-preview`, `GET /api/orders/{id}/email`, `POST /api/orders/{id}/email/send`（Email Override対応）
- `GET/POST /api/admin/supplier-region-classifications`, `PUT /api/admin/supplier-region-classifications/{id}`

すべて既存の認証・`@PreAuthorize("hasRole('ADMIN')")`等の権限制御パターンを踏襲。

## 8. UI変更一覧（今回追加分）

- 発注履歴詳細画面: SKUリンク化、在庫/販売/リードタイム/未入荷列、PDF生成・ダウンロードボタン、Reissueボタン+ダイアログ、Revision Requiredバナー、Revision History表、Cancelボタン+理由必須ダイアログ、To/CC編集可能フィールド（ADMIN限定）、解決済みRegion Classification Chip
- `PortalMailSettingsPage`（新設、Default CC管理画面）
- `SupplierRegionClassificationPage`（新設、国内/海外区分管理画面）
- ナビゲーション: マスタメンテナンスサブメニューに「メール送信設定」「国内／海外区分」を追加
- i18n: ja/en両方に対応キーを追加、パリティテスト green

## 9. テスト結果

- Backend: **550/550 passing**（`./mvnw -o test`, BUILD SUCCESS）
- Frontend TypeScript: `npx tsc -b` エラー0件
- Frontend i18n parity test: 39/39 passing（ja/en完全一致）
- E2E (Playwright, 全24 specファイル合算): **171/171 passing**（Skip/Fail 0件）
  - 内訳には本Phaseで新規追加した`supplier-region-classification.spec.ts`（3件）、`gulliver-phase1-integration.spec.ts`（Scenario 1連続フロー、2件）を含む
- 既存の約500件規模のBackend Test/E2Eに破壊的変更なし（全件Green）

## 10. Known Limitations（既知の制約）

- Official PO PDFは正式なOfficial PO様式ではなく、「G-OPS Standard Format」という自己ラベル付きの代替フォーマット
- Reissue検知は`ORDER_REVISION_CREATED`イベントの有無に依存しており、Revisionを経由しない直接的なPO内容変更経路が将来追加された場合は再検討が必要
- Default CCはBusiness Rule化されておらず、運用上「誰を必ずCCすべきか」は依然として人手判断に依存
- 国内/海外区分は表示用Foundationのみで、発注数量計算への反映は一切ない（意図的な未実装）
- Company Masterは未着手（TODO）
- G-SYS Integration Status確認（Import確認）は、実際のG-SYS Import Batchが動作する環境がないため、本番同等のCONFIRMED遷移はローカル/Demo環境では検証不能（NOT_YET_IMPORTEDの応答のみ確認可能）

## 11. Productionization Remaining Work（本番化に向けた残作業）

- Official PO PDF/Excelファイル名・体裁について顧客の正式承認を得る
- Reissue/Cancelの業務フロー承認フローの要否を確認し、必要であれば承認ステップを追加
- Default CCの実運用アドレス・ポリシーを確定し、必要であればBusiness Rule化を検討
- 国内/海外区分の粒度・将来の計算式要否について顧客と要件確定
- Company Masterの要件確定・実装
- 実SMTPアダプタへの切り替え（現状は`LoggingEmailSenderAdapter`のみ）
- 実G-SYS Import Folder/Import Batchとの結合テスト（本番相当環境）
- Production環境向けの`SafetyGuardEnvironmentPostProcessor`設定・Secrets管理の最終レビュー

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
