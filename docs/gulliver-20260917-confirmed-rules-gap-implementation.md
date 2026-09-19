# Gulliver社 確定Business Rules Gap Audit & Implementation

`docs/gulliver-20260917-confirmed-business-rules.md`（BR-01〜BR-10）を基準に、現行実装とのGapを監査し、
Domestic Recommended Qty具体式を除く全項目を実装した記録。

**方針**: 「現在のPrototype仕様に業務を合わせる」のではなく「確定したGulliver業務ルールにG-OPSを合わせる」。
無理な大規模Refactorは避け、既存Approval Workflow・既存Revision機構・既存Naming基盤を最大限再利用する。

---

## 1. Gap Audit サマリ

| BR | 項目 | 分類 | 理由 |
|---|---|---|---|
| BR-01 | Excel→PDF整合・両添付送信 | **C** | PDF/Excelは共有Data Sourceから論理的に一致した内容を生成済みだが、メール添付は現状Excelのみ（`EmailSendService`の`ATTACHMENT_TYPE_OFFICIAL_PO_EXCEL`単数構造）。PDF添付追加とExcel/PDF内容一致Testが必要。 |
| BR-02 | Reissue再承認必須化 | **A** | `OfficialPoIntegrationService.reissue()`は`order.getStatus()==APPROVED`を要求し、内容変更の唯一の経路である`OrderRevisionService.createCorrection`はOrderをDRAFTへ落とす。DRAFTからAPPROVEDに戻る唯一の経路は`submitForApproval`→ADMIN`approve`。したがって再承認を経ずにReissueすることは構造上不可能（既存実装で満たされている）。 |
| BR-03 | Cancel承認Workflow | **C** | 現行`cancel()`はReason必須の直接CANCELLED遷移のみ。Request→承認→通知→CANCELLEDの2段階Workflowが存在しない。 |
| BR-04 | Email Override権限・最終確認・Domain Warning | **B+D** | Override自体は既にADMIN Send権限と同一Gate内で動作（Override専用の追加制限は無し＝権限面はB/確認のみ）。送信直前の確認ダイアログ、Master Domain比較によるWarningは未実装（D）。 |
| BR-05 | Default CC = Initial Value | **A** | Phase 8で「マスタへのprefillのみ、Business Rule化せず、都度削除可能」として実装済み。BR-05の記述と完全一致。 |
| BR-06 | Demo Send Production非表示 | **D** | 環境（Production相当）を判定してUIから隠す仕組みが存在しない。SafetyGuardは`local/demo/test`のみ起動許可のため「Production相当」を模擬するFeature Flagの新設が必要。 |
| BR-07 | Portal管理番号 / Official PO番号の分離 | **A** | Acceptance Fix C-5で`PrototypePoNoGenerator`（自動採番、Portal内部限定）と`officialPoNo`（正式PO番号）は既に別概念として実装・UI表示（「Portal管理番号」Chip）済み。 |
| BR-08 | Official PO番号自動採番 | **D** | 現状ADMIN手入力（`confirmOfficialPoNumber`）。Supplier×Brand単位で排他制御された採番機構・Supplier/Brand略称Masterのいずれも存在しない。新規実装が必要。 |
| BR-09 | Domestic/Overseas Strategy Foundation | **B** | Region判定（`SupplierRegionClassificationResolutionService`）・表示（Chip）はPhase 9で実装済みだが、Recommended Qty計算自体にStrategy切替点が存在しない（`RecommendedQtyCalculator.calc4`は無条件にLegacy計算式を実行する静的メソッド）。Strategyインターフェース化とDomestic時のnull返却（誤表示防止）が必要。 |
| BR-10 | Company = Supplier | **A** | Company Masterは元々未作成（TODOのまま放置）。File Naming（`OfficialPoFileNaming`）は既にSupplierコードを使用しており、「Company」の別概念が不要になったことでGap自体が解消（コード変更不要、ドキュメント確定のみ）。 |

**E判定（Requirement Still Open）**: なし。BR-08のSupplier/Brand略称は「Gulliver社が決定しG-SYS Masterに登録」と
されているが、現行Legacy `ms_comm`（MS_SUPPL/MS_BRAND）には3文字略称カラムが存在しない（Fact、grep確認済み）。
この略称データそのものが未確定という点はBR-08の前提と矛盾するように見えるが、これは「Domestic計算式が未確定」
と同種の**データ未整備**であり、**採番の仕組み自体（Concurrency Control・フォーマット組立）を実装しない理由には
ならない**と判断した。Phase 9のSupplier Region Classification（Legacy未対応のBusiness ConceptをPortal専用
Masterとして補完した既存パターン）を踏襲し、Portal側に「Supplier/Brand略称」の管理画面を新設し、ADMINが
Gulliver社の決定値をそのまま登録する（G-OPSが値を生成することはない）。したがってE判定にはせず、Dとして実装した
（詳細は4章）。

---

## 2. 実装したGap一覧

優先順位順（ユーザー指示どおり）:

1. Official PO自動採番（BR-08） — 4章
2. Reissue再承認（BR-02） — 5章（実装は不要、既存動作の追加検証テストのみ）
3. Cancel承認（BR-03） — 6章
4. Excel → PDF整合（BR-01） — 7章
5. Email Override Warning（BR-04） — 8章
6. Default CC確認（BR-05） — 9章（実装不要、確認のみ）
7. Demo Send Production非表示（BR-06） — 10章
8. Company=Supplier整理（BR-10） — 11章（実装不要、確認のみ）
9. Domestic/Overseas Strategy Foundation（BR-09） — 12章

---

## 3. Supplier略称 / Brand略称 Source（BR-08前提の解決）

1章の「E判定にはしない」判断に基づき、Legacy `ms_comm`に3文字略称カラムが存在しない状況を
`supplier_region_classification`（Phase 9）と同じPortal専用Masterパターンで解決した。

- 新規Table: `official_po_short_code`（V29）— `code_type`(SUPPLIER/BRAND) + `business_code`
  （既存Legacy Supplier/BrandコードとのFK的紐付け、Legacy READ ONLYで存在確認） + `short_code`（3文字）。
- 新規Admin画面: `Official PO略称コード管理`（`/admin/official-po-short-codes`）— ADMINがGulliver社の
  決定値をそのまま入力する画面。G-OPS側が値を生成するロジックは一切存在しない。
- Demo/Test Fixture Seed（V30）: 既存3 Supplier×3 Brandに対し `SUP_ALPHA→ALP` `SUP_BETA→BET`
  `SUP_GAMMA→GAM` `BR_OUTDOOR→OUT` `BR_HOME→HOM` `BR_KITCHEN→KIT` をSeed（`V5__seed_portal_users.sql`の
  デモアカウントと同じ精神 - 本番ではADMINが実際の値を登録する）。

---

## 4. Official PO自動採番（BR-07/BR-08）実装結果

### Before
`OfficialPoIntegrationService.confirmOfficialPoNumber`でADMINが自由入力。長さ（30文字以内）以外の
検証なし。

### Gap
- 手入力UIが残っている → 自動採番に置き換える必要（BR-08）。
- Supplier×Brand単位の排他制御された採番機構が存在しない（BR-08）。
- Supplier/Brand略称Masterが存在しない（3章で解決）。

### Implementation
- `official_po_sequence`（V29）: `(supplier_code, brand_code)`をPKとする1行1カウンタのTable。
- `OfficialPoSequenceService.nextSequence`: `INSERT ... ON CONFLICT (supplier_code, brand_code)
  DO UPDATE SET next_seq = next_seq + 1 RETURNING next_seq`という単一の原子的SQLで採番（MAX+1を
  読んでから書き込む無排他パターンは一切使用していない）。同一キーへの同時採番はPostgresの行ロックにより
  完全に直列化され、異なるキー同士は互いにブロックしない。
- `OfficialPoNumberGenerator.generate(supplierCode, brandCode)`: `{Supplier略称3文字}{Brand略称3文字}
  {3桁通番}`を組み立てる。略称が未登録の場合は`OfficialPoShortCodeNotConfiguredException`（G-OPSが
  略称を推測することは一切ない）。
- `OfficialPoIntegrationService.requestIntegration`: このOrderにとって初めてのIntegration Request
  作成時にのみ`OfficialPoNumberGenerator.generate`を呼び自動採番。**2回目以降（Reissueによる新Revision
  作成時を含む）は、直前のRequestの`officialPoNo`をそのまま引き継ぐだけで、絶対に再採番しない。**
  画面Reloadや2重クリックで再採番されないことは、既存の「同一Revisionへの2回目Requestは同一行を再利用する」
  Idempotency機構（`findByPortalOrderIdAndRevisionNo`）にそのまま乗っており、追加のガードは不要だった。
- `confirmOfficialPoNumber`からOfficial PO番号フィールドを削除（配送週/納期/輸送方法/決済条件のみを
  扱うAPIに変更、DTO `ConfirmOfficialPoNumberRequest`から`officialPoNo`を削除）。UI側もPO番号入力欄を
  削除し、「at a glance」パネルの自動採番済み番号をそのまま表示する形に変更。

### Test
- `OfficialPoNumberGeneratorTest`（純粋Mockito Unit Test）: フォーマット組み立て・3桁ゼロ埋め・
  略称未登録時の例外を検証。
- `OfficialPoAutoNumberingIntegrationTest`: 同一Supplier×Brandでの連番増加、異なるSupplier×Brand間の
  独立性（Scenario 6相当）、**16スレッドによる同時採番でも重複が一切発生しないことを検証する
  Concurrency Test（Scenario 5）**。
- `OfficialPoReissueIntegrationTest.fullReissueCycle_...`: Reissue後もOfficial PO番号が完全に同一で
  あることを明示的にAssert（Scenario 7）。
- `OfficialPoRevisionConsistencyIntegrationTest`の全Scenario、`OfficialPoNumberAndExcelGenerationIntegrationTest`
  を自動採番前提に全面改訂。
- E2E: `official-po-integration.spec.ts`の全Scenario、`email-send.spec.ts`、
  `legacy-po-concurrency-control.spec.ts`、`gulliver-phase1-integration.spec.ts`から手入力ステップを削除し、
  自動採番された値をUIから読み取って以降のAssertionに使う形に改訂。

---

## 5. Reissue再承認（BR-02）確認結果

### 監査結果: 既存実装で完全に満たされている（A）

`OfficialPoIntegrationService.reissue()`は`order.getStatus() == APPROVED`を要求する。Order内容を
変更できる唯一の経路は`OrderRevisionService.createCorrection`（前提: `SUPPLIER_CONFIRMED`、実行後:
`DRAFT`）であり、DRAFTからAPPROVEDに戻る経路は`submitForApproval`→ADMIN`approve`のみ。したがって
「変更 → 再承認を経ずにReissue」は構造的に到達不可能。コード変更は行っていない。

### Test（新規追加、実装検証）
`OfficialPoReissueIntegrationTest.reissueIsRefusedBeforeReapproval_evenAfterAGenuineCorrection`:
修正版作成直後（DRAFT）、submit-for-approval直後（PENDING_APPROVAL）のそれぞれでReissueを試み、
両方とも`OrderNotApprovedException`で拒否されること、ADMIN承認後に初めてReissueが成功することを
明示的に検証。

---

## 6. Cancel承認Workflow（BR-03）実装結果

### Before
`OfficialPoIntegrationService.cancel(orderId, reason, performedBy)`が理由必須の直接CANCELLED遷移
のみを行っていた（Request/承認の2段階構造なし）。

### Implementation
- `OfficialPoIntegrationRequest`のLifecycleに`CANCEL_REQUESTED`を追加（ACTIVE → CANCEL_REQUESTED
  → CANCELLED、ACTIVEから直接CANCELLEDへの遷移経路は削除）。
- `cancel_requested_by`/`cancel_requested_at`/`cancel_reason`列を追加（V31）— 誰がRequestし誰がApprove
  したかを、既存の`lifecycle_changed_by`/`lifecycle_changed_at`（現在の状態への遷移者、Approve時点では
  Approverの情報に上書きされる）とは別に、Requester側の情報として個別に保持。
- `requestCancel(orderId, reason, performedBy)`: ACTIVE→CANCEL_REQUESTED（理由必須）。
- `approveCancel(orderId, performedBy)`: CANCEL_REQUESTED→CANCELLED。ADMINのみ。承認と同時に
  `OfficialPoCancelNotificationService`経由で「メーカーへ取消連絡」を試行（既存の
  `SupplierContactResolutionService`＋`EmailSenderPort`をそのまま再利用、Local/Demo/Testでは
  `LoggingEmailSenderAdapter`によるSimulationのみ、Production SMTPには一切接続しない）。連絡先未登録等で
  通知できない場合でも、承認自体は必ず完了する（通知はCancel Approvalの必須前提にしない）。通知結果は
  `OFFICIAL_PO_CANCEL_NOTIFIED`監査イベントとして常に記録。
- 既存Approval機構の再利用: 新しいApproval Frameworkは作らず、既存のRequest/Approve 2アクション分離
  パターン（Order自体のsubmitForApproval/approveと同じ発想）をOfficial PO Lifecycle軸に適用しただけ。
- UI: 「正式POをキャンセル」ボタンを「正式POのキャンセルを申請」に変更（ACTIVE時のみ活性）。新規
  「キャンセルを承認」ボタルを追加（CANCEL_REQUESTED時のみ活性）。CANCEL_REQUESTED中は理由を含む
  警告Bannerを表示。

### Test
- `OfficialPoCancelIntegrationTest`を全面改訂: Request単独では絶対にCANCELLEDへ到達しないこと、
  Approve単独ではCANCEL_REQUESTEDが無い限り拒否されること、RequesterとApproverが異なるユーザーでも
  それぞれ正しくAudit Trailに記録されること、通知未登録時も承認が完了すること（`SKIPPED`が記録される
  こと）を検証。
- `OfficialPoIntegrationRequestTest`（Entity単体）を2段階遷移前提に全面改訂。
- E2E: `official-po-integration.spec.ts` Scenario Lを2段階Workflow（Request→Banner確認→Approve→
  CANCELLED確認）に全面改訂。

---

## 7. Excel → PDF整合・両添付送信（BR-01）実装結果

### Before
Excel/PDFは共有Data Source（`OfficialPoExcelGenerationService.buildInput()`）から生成され論理的な
内容一致は既に保証されていた（Revision Consistency Audit以前から）。ただしManufacturer Send
（`EmailSendService.send`）はExcelのみを添付していた（`EmailEnvelope`が単一Attachment構造）。

### Implementation
- `EmailEnvelope`を単一Attachment構造から`List<Attachment>`（`record Attachment(fileName, bytes)`）
  へ変更。
- `EmailSendService.send`: PDFも必須Gateに追加（`generatedFileKey`と`pdfFileKey`の両方がnullでない
  ことを要求、片方でも未生成なら`EmailAttachmentNotReadyException`）。送信時にExcel/PDF両方を
  Attachmentとして添付。
- `LoggingEmailSenderAdapter`/`SmtpEmailSenderAdapter`を複数Attachment対応に更新。

### 見た目 vs 論理的同一性についての監査結果
PDFとExcelは同一の`buildInput()`（PO No/Supplier/Brand/SKU/Quantity/Price/Delivery等の全項目を含む
共通Business Data Source）から生成されるため、**内容（値）の一致は構造的に保証**されている
（`pdfAndExcelAgreeOnQuantityAndPoInfo_sameBusinessDataSource`テストで検証）。ただし**見た目（Layout）
までの完全一致は保証していない** - ExcelはExcelのSheet Layout、PDFはPDFBox独自のStandard Formatで
描画されており、レイアウトの一致は今回の変更禁止事項（「PDF Layout」）にも触れるため対象外。既存
Gulliver Formatとの体裁一致についても、Acceptance Fix以前から「G-OPS Standard Format」という
自己ラベル付きの代替フォーマットのままであり、正式な体裁承認は引き続き顧客確認が必要（Remaining
Requirement参照）。

### Test
- `EmailSendServiceRetryTest.sendAttachesBothExcelAndPdf`（新規）: 送信されたEnvelopeが必ずExcel+PDFの
  2ファイルを含むことをArgumentCaptorで検証。
- `EmailSendServiceIntegrationTest`/`EmailSendStatusConsistencyIntegrationTest`/
  `OfficialPoRevisionConsistencyIntegrationTest`のSend系Scenarioを全てPDF事前生成込みに更新。
- E2E: `email-send.spec.ts`/`gulliver-phase1-integration.spec.ts`/`official-po-integration.spec.ts`の
  Send系ScenarioにPDF生成ステップを追加。

---

## 8. Email Override Warning（BR-04）実装結果

### Before
Override自体（To/CC編集）は既にADMIN Send権限と同一のGate内で動作しており、Override専用の追加制限は
無かった（権限面は元々BR-04の記述と整合）。ただし、送信直前の最終確認ステップ、Master Domainとの
比較によるWarning表示はいずれも未実装だった。

### 権限についての結論
現行システムはADMIN/OPERATORの2 Role構成であり、Official PO関連操作（Email Send含む）はそもそも
Section全体がADMIN限定（`isAdmin`ゲート）。BR-04の「ADMIN限定ではありません」は「Override専用の
追加のより厳しい権限を要求しない」という意味であり、現状Override自体に個別の追加制限は存在しない
ため、この点はA（既存実装で充足）と判断した。将来OPERATORにもSend権限を広げる場合は別途の
Business Rule確定が必要（Remaining Requirement参照）。

### Implementation
- Send ボタンのクリックで即送信していたのを、確認Dialog（最終To/CC表示＋必要ならWarning）を
  経由してから`handleConfirmSendEmail`が実際に送信する形に変更。
- `addressesWithUnknownDomain(actual, master)`: 実際の送信先の各アドレスのDomain部分を、Master
  Contact（Mail Previewが解決した本来のTo/CC）のDomain集合と比較。含まれないものをWarning対象として
  列挙。ローカルパートは比較しない（Domain-onlyの比較、大小文字を無視）。
- Warningは送信を一切ブロックしない（Alert表示のみ、確認Dialogの「この内容で送信する」ボタンは
  常に活性）。

### Test
- E2E `email-send.spec.ts`:
  - 既存Overrideテストに「同一Domainの場合はWarningが出ないこと」を追加（Case A）。
  - 新規テストで、To/CC双方を異なるDomainにOverrideした場合にWarningが表示されること（Case B/C）、
    Warning表示後も送信が成功すること（Case D）、Master Contact自体は変更されないこと（Case E）を検証。

---

## 9. Default CC（BR-05）確認結果

### 監査結果: 既存実装で完全に満たされている（A）

Phase 8で実装済みの`portal_mail_settings`（Default CC機能）は「マスタへのprefillのみ」であり、
Business Rule化（強制CC）は一切行っていない。送信権限者（ADMIN）はMail Preview画面でCCフィールドを
自由に追加・変更・削除できる。BR-05の「Default CC = Initial Value（Mandatory CCではない）」の記述と
完全に一致するため、コード変更は行っていない。

---

## 10. Demo Send Production非表示（BR-06）実装結果

### Before
Demo Send表示を環境に応じて切り替える仕組みが存在しなかった（常に表示）。

### Implementation
- `application.yml`に`app.demo-features.enabled: true`（Local/Demo/Test = 常にtrue）を追加。
- `application-production.yml`（新規）に`app.demo-features.enabled: false`を設定 - **この環境では
  実際に読み込まれることは無い**（`SafetyGuardEnvironmentPostProcessor`が`local`/`demo`/`test`以外の
  Profileでの起動そのものを拒否するため）。将来Production Deployが実施される際、Spring Boot標準の
  Profile別YAML上書き機構（既存の環境判定方式）だけでDemo Sendが自動的に非表示になる設計。
- 新規Read Only Endpoint `GET /api/system/feature-flags`（`{ demoSendEnabled: boolean }`）。
- Frontend: `useFeatureFlags()`Hookを追加し、`PoPreviewPage`のDemo Sendボタンを
  `demoSendEnabled !== false`の場合のみ表示するよう変更（フラグ取得前のデフォルトはtrue = 現状維持）。
- Local/Demo/Test環境では`app.demo-features.enabled`が常にtrueのため、既存のDemo Send依存Testは
  全て無変更で動作する。

### Test
- `SystemFeatureFlagsControllerTest`: Test Profileでのデフォルト値（true）と未認証時401を検証。
- Production相当（false）のUI非表示自体は、Production Profileがこの環境で起動不可能な設計上、
  Browser経由でE2E検証することはできない（Remaining Limitation参照）。フラグの伝播経路
  （Backend設定値 → API → Frontend条件分岐）自体はコードレビュー及びBackend Testで担保。

---

## 11. Company = Supplier（BR-10）確認結果

### 監査結果: 既存実装で完全に満たされている（A）

Company Masterは元々作成していない（Phase 8以来のTODOのまま）。`OfficialPoFileNaming`は既に
`supplierCode`（Legacy Supplier Code）をファイル名に使用しており、「Company」の独立した概念を
必要としない。BR-10により「Company」が指す実体はSupplierそのものであることが確定したため、
7章（旧Revision Consistency Audit・Gap Analysis）に残っていた「Companyの定義が未確定」という
Unknownは本ドキュメントの確定をもって解消された。コード変更は行っていない。

---

## 12. Domestic/Overseas Strategy Foundation（BR-09）実装結果

### Before
`SupplierRegionClassificationResolutionService`（Region判定）と解決済みChip表示（Phase 9）は実装
済みだったが、Recommended Qty計算（`RecommendedQtyCalculator.calc4`）自体は無条件にLegacy計算式を
実行する`static`メソッドであり、Strategy切替点が存在しなかった。

### Implementation
- `RecommendedQtyStrategy`インターフェース（`calculate(LegacyStockRow row): Integer`）を新設。
- `OverseasRecommendedQtyStrategy`: 既存のcalc1-4オーケストレーションを**一切変更せず**そのまま
  抽出しただけ（Legacy Logic維持）。
- `DomesticRecommendedQtyStrategy`: 常に`null`を返す。**国内向け計算式は一切実装していない**
  （推測禁止の明示的遵守）。
- `RecommendedQtyCalculator`をSpring Bean化し、`SupplierRegionClassificationResolutionService`で
  解決したRegionに応じて上記2 Strategyのいずれかへdispatchする形に変更。
- **未分類（Region Classification Masterに行が無い、現状ほぼ全てのSupplier）は必ずOVERSEASとして
  扱う** - BR-09の「従来Gulliver社では海外Supplier中心」という確定事実とも整合し、かつ既存の
  全Order/全Testの計算結果を1件も変えないための意図的な設計判断（DOMESTIC扱いへ安易にフォールバック
  すると、既存の正常動作している計算が全てサイレントに「未設定」化してしまう重大な後退になる）。
- Recommended Qtyを算出する4箇所（`OrderCandidateService`/`OrderDraftPersistenceService`/
  `SkuDetailService`/`StockSalesService`）を、静的呼び出しからSpring DI経由のInstance呼び出しに変更
  （ロジック自体は無変更、呼び出し方法のみの変更）。
- UI: Candidate List画面で、`recommendedQty`が`null`かつ`regionClassification === 'DOMESTIC'`の場合
  のみ「設定準備中」を表示し、それ以外（未分類含む）は従来どおり数値を表示する
  （`OrderCandidateResponse`に`regionClassification`を追加）。

### Test
- `RecommendedQtyCalculatorTest`（純粋Mockito Unit Test）: 未分類→Overseas結果と同一、
  Overseas明示分類→Overseas結果と同一、Domestic分類→常にnull、をそれぞれ検証。
- E2E `supplier-region-classification.spec.ts` Scenario 8: SUP_ALPHA/BR_OUTDOORを一時的にDOMESTIC
  分類に切り替え、Candidate ListがOD-TENT-001に対して「設定準備中」を表示すること、直後に
  OVERSEASへ戻すとOD-TENT-001が再び数値のRecommended Qtyを表示することを、同一テスト内で
  完結させて検証（他のテストに一切影響を与えない自己完結設計）。
- Backend Full Testが全件Green（最終確認数は「13. Regression結果（最終確認）」参照）であることを
  もって、既存の全Recommended Qty計算結果が1件も変わっていないことを確認済み。

---

## 13. Regression結果（最終確認）

BR-01〜BR-10の全実装完了後、最終的に実行した回帰確認の結果（すべてGreen、Regression 0件）。

| 項目 | 結果 |
|---|---|
| Backend Full Test | 574 / 574 PASS（Failures 0, Errors 0, Skipped 0） |
| Frontend TypeScript Build | エラー0件（`tsc --noEmit`） |
| Frontend Vitest（Unit Test） | 49 / 49 PASS |
| うち i18n Resource Parity（ja⇔en全20 namespace） | 41 / 41 PASS（`i18nResources.test.ts`、上記49件の内数） |
| E2E（Playwright, Chromium） | 174 / 174 PASS, 0 FAIL（環境のメモリ制約により`--shard=1/3`〜`3/3`の3分割で実行、62+56+56件） |

**実行時に発見・対処した問題（コード修正ではなくテスト環境側の事象）:**
- 実行時点でPrototype DB（Postgres）に、過去のセッション内で発生したメモリ不足による
  強制終了（テストJVMのkillではなく、それ以前の別セッションでのDemo/手動検証由来）が原因の
  残留行（`portal_order.official_po_no` = `PO-CONC-01`/`PO-CONC-02`/`PO-CONC-03`、各3件）が
  残っており、`uq_portal_order_official_po_no`制約に対して`LegacyPoConcurrencyServiceIntegrationTest`
  /`OfficialPoImportConfirmationIntegrationTest`が同じ固定Fixture値を使おうとして衝突していた。
  実装バグではなくテストデータの残留が原因と特定し、当該残留行と従属行（Audit Event/Supplier
  Response/Official PO Integration Request等）をCascade削除した上で再実行し、Green化を確認。
- `email-send.spec.ts`のE2Eで一時的に、`official-po-integration.spec.ts` Scenario Mが登録する
  テスト専用Supplier Contact（`revision-consistency@example.com`）が未Cleanupのまま残存し、
  Mail Preview解決結果に混入する事象が1件観測されたが、単体再実行では再現せず（Scenario M自身の
  Cleanup Blockは実装済み）、環境のメモリ起因で中断された過去の実行回の残留影響と判断。
  最終Regression実行では発生せず、174/174 全件Greenを確認。

**変更禁止対象への抵触:** なし（Legacy G-SYSソース変更0件、Legacy DB WRITE 0件、Production接続0件 -
上記はすべてLocal Prototype DB (`gsys-prototype-postgres`, port 54321) に対する作業）。

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
