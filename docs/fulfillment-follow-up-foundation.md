# Fulfillment / Follow-up Foundation — Phase 7-C7A 実装結果

`docs/target-production-procurement-workflow.md` のうち、Phase 7-C7Aで実装した Fulfillment（G-SYS入荷状況の READ ONLY 参照）と Follow-up（問い合わせ・再発注 Foundation）の実装結果の詳細記録です。

Scope: G-SYS に存在する Official PO / Invoice / Arrival / Stock-in を **READ ONLY** で参照し、Portal 上で未納/一部納品/完納・問い合わせ・再発注候補を管理できる Foundation を実装。**Legacy DB WRITE・Official PO Import・Import Folder Write・SMTP・SYS_SEND_MAIL INSERT・External Mail API・Production接続はいずれも本Phaseのコードパスに一切存在しない。**

## 0. 実装前Legacy Source監査

`phasep-gulliver` の以下を実際にSourceレベルで監査した（推測でSQLを書いていない）:

- `jp.ne.glv.model.TrPo` / `TrPoDtl` / `TrInv` / `TrInvDtl` / `TrArr` / `MsStk`
- `jp.ne.glv.batch.PrOfficialPoImportBatch`（Official PO Import - Delete&Recreateの実装を再確認）
- `jp.ne.glv.batch.PrStkInReportImportBatch`（Stock-in Report Import - qty_stk_in設定ロジックとCredit PO自動生成ロジック）
- `jp.ne.glv.utilities.BusinessLogicUtil`（Credit PO/Invoice命名規則 `creditPoNo`/`creditInvNo`/`nonCreditPoNo`/`nonCreditInvNo`）
- `Const.java`（`TR_PO_STATUS_*` / `TR_INV_STATUS_*` / `TR_INV_TRAN_TYPE_*`）

### 主な発見事項

1. **TR_PO.STATUSはINITIAL→OFFICIALが実質的な終端**（7-Aの既存知見どおり再確認）。`STOCKIN`という第3の値がConst.javaに存在するが、実際に書き込まれるのは`PrBLInvImportBatch`内の「Bill-Only/Deposit（実商品行を持たないダミーPO）」という特殊な決済専用エッジケースのみ（`Const.ITEM_CD_DUMMY`）であり、通常のFulfillment終端ではない。
2. **TR_INV.STATUSはTRANSIT→RECEIVING→STOCK_IN**の3値遷移。TRANSITはOfficial PO Import時にEmbeddedされたInvoice列から作成される時点、RECEIVINGはBL Invoice ImportまたはStock-in Report Importの再設定時、STOCK_INは「そのInvoiceの全行がqty_stk_in未確定でなくなった」時点（`findAllBySupplierCdAndInvNoWithWaitingStockInQty`が空になった時）。
3. **Credit PO/Invoiceによる差異調整メカニズム**（`PrStkInReportImportBatch`実装から正確に抽出）: Stock-in Report Importで「実際の入荷数量 ≠ Invoice記載数量」の差異を検出すると、
   - **元のTR_INV_DTL.qty_stk_inは常にその行自身のqty（Invoice記載数量）に固定される**（実際の入荷数量そのものではない）。
   - **差異分（実際入荷数量 − Invoice記載数量、マイナスもあり得る）は、自動生成される「Credit」PO/Invoice（PO No.=`{元PO No.}-{ArrCode}`、PO_TYPE=CREDIT）自身のqty_stk_inとして記録される。**
   - したがって **「真の物理入荷数量」＝元TR_INV_DTL.qty_stk_in + Credit TR_INV_DTL.qty_stk_in（存在する場合）** という決定論的な式が成り立つ（推測ではなくSource実装から直接導出）。
4. **Credit PO命名規則にLegacy自身の内部的な不整合を発見**: `BusinessLogicUtil.creditPoNo()`は `poNo + "-" + arrCode` を生成するが、その逆変換用の`nonCreditPoNo()`/`nonCreditInvNo()`は`"-#"`という別のデリミタを探しており、実際には一致しない（事実上の死コード）。本Phaseはこの逆変換に一切依存せず、順方向の命名規則（`{officialPoNo}-%`というLIKEパターン）のみを使うことでこの不整合を回避した。
5. **MS_STKの「Open PO」列（PO_NO_1..20/PO_QTY_1..20/ARR_QTY_1..20）はITEM単位（複数PO横断）の集計キャッシュ**であり、本Phaseが必要とする「特定の1つのOfficial POに対するOutstanding」というPOスコープの問いには直接対応しない、別の概念であると判断した。両者は矛盾ではなく、スコープが異なる別の指標として扱う。
6. **Legacy Demo MySQLには元々`tr_inv`/`tr_inv_dtl`テーブルが存在しなかった**（`tr_po`にも`po_type`列がなかった）。既存の`01-schema.sql`は過去のPhaseで必要な列だけを抜粋する設計方針（"reduce columns, not business rules"）のため、本Phaseで実際に読み取りに必要な最小限の列だけを追加した（詳細は5章）。

## 1. Fulfillment Status（Workflow Statusと分離）

`OPEN` / `PARTIAL` / `FULFILLED` の3値。G-SYSデータのみからは判定できない`CANCELLED`は自動判定しない（12章で別途扱う）。加えて、Fulfillment Status とは別軸として **Link State**（`NOT_LINKED` / `PO_NOT_FOUND` / `LINKED`）を導入した - officialPoNoが未設定、または設定されているがLegacyにPOが見つからない場合を明示的に区別し、「0件」「未納」と誤表示しない（4章）。

## 2. Fulfillment Source of Truth

SKU単位で以下をSourceから直接算出する（`FulfillmentReadRepository`のJavadocに実装根拠を全文記載）:

```
orderedQty    = 元Official PO自身のTR_PO_DTL.qty_po（Credit POのqty_poは含めない）
invoicedQty   = 元Invoice自身のTR_INV_DTL.qty（Credit Invoiceのqtyは含めない）
stockInQty    = 元TR_INV_DTL.qty_stk_in + Credit TR_INV_DTL.qty_stk_in（存在する場合）
outstandingQty = orderedQty − stockInQty（0でクランプしない。マイナス＝超過入荷）
```

**Portal Ordered QtyではなくG-SYS Official PO Qtyを正とする**（7-C7A方針どおり）。Portal Agreement値（Supplier Response Confirmed Qty）をStock-in実績として扱うことは一切ない - `FulfillmentService`はPortal側の`SupplierResponseDetail`/`PortalOrderDetail`を一切参照せず、常にLegacyのTR_PO_DTL/TR_INV_DTLのみから算出する。

## 3. Fulfillment Calculation

`FulfillmentService`（純粋READ、Prototype/Legacyのいずれにも一切書き込まない）。行単位のStatus判定:

- Invoiced=0 かつ Stock-in=0 → `OPEN`
- Stock-in ≥ Ordered → `FULFILLED`
- それ以外 → `PARTIAL`

Order全体は明細から集約（全行OPEN→OPEN、全行FULFILLED→FULFILLED、それ以外→PARTIAL）。

## 4. Official PO Link Gate

`portal_order.official_po_no`（7-C2A由来、現状は常にNULL）が未設定の場合は`NOT_LINKED`として明示 - 0件や未納として誤表示しない。設定されているがLegacyにPOが存在しない場合は`PO_NOT_FOUND`として区別する。

## 5. Demo / Test Strategy

Legacy Demo MySQLのスキーマを拡張した（`backend/demo-data/01-schema.sql`）: `tr_inv`/`tr_inv_dtl`テーブルを新設し、`tr_po`に`po_type`列を追加（いずれも実Legacy `jp.ne.glv.model`のSubset、既存の`tr_po`/`tr_po_dtl`と同じ「reduce columns, not business rules」方針）。既存の`PO-OUTDOOR-01/02/05`（`02-seed.sql`）に対してTest FixtureのInvoice/Credit行を追加し、完納・一部納品・Credit PO差異調整の3パターンを実データで検証可能にした。

Portal OrderとのLinkはPrototype Demo DB内で直接`officialPoNo`を設定する（test-only、Production番号と混同しない - `FulfillmentServiceIntegrationTest`ではRepository経由、Browser Scenario B-DではE2E層から直接SQL UPDATEで同じことを行う）。

## 6. Order Detail UI

「G-SYS入荷状況」セクション: Official PO No. / Overall Status / 明細（SKU/Ordered/Invoiced/Stock-in/Outstanding/Status）。Sourceから取得できない「最終更新日時」は表示していない（架空のUPDATE_DATETIME解釈をしない）。Revision/Response Historyとは別セクションとして分離。

## 7. Partial

「メーカー回答（Supplier Response）」と「実際のStock-in」は完全に別概念として扱う - `SupplierResponseDetail.confirmedQty`はFulfillment計算に一切使われない（2章）。Partial判定はあくまでLegacy Invoice/Stock-inのみに基づく。

## 8. Outstanding

`Official PO Qty − Stock-in Qty`を採用（Invoice Qtyではなく、Official PO Qtyを基準とした）。Credit PO分を含めないと正しいOutstandingが算出できないことをSourceから確認したため、Credit POのqty_stk_inをネットして計算している（2章）。PO Outstanding（本Phaseで実装）とItem-level Open PO（MS_STK、未実装）は明確に別指標として区別した。

## 9. Follow-up Model

`follow_up_case`テーブル新設（Portal専用、Legacyに対応物なし）。`portal_order_id`（必須）/`order_revision_id`（nullable、作成時点のRevisionをSnapshot）/`official_po_no`（作成時点のSnapshot、Order側の値が後で変わっても履歴として残る）/`sku_code`（nullable=Order全体）/`status`/`reason`/`note`/作成者・更新者・Close者。

## 10. Follow-up Status

到達可能なStatus: `OPEN` → `INQUIRY_PREPARED` → `CLOSED` の3値のみ。`INQUIRY_SENT`は実装していない（実送信が存在しないため、実際に送っていないのに設定できる設計を避けた）。`INQUIRY_PREPARED`への遷移は、Follow-up Mail Previewが**非BLOCKED状態で生成された時点**で自動的に発生する（実送信の代替となる唯一の「準備が整った」シグナルがPreviewの成功だからであり、専用の「準備完了にする」ボタンを別途作ると実態と乖離しうるため - 13章）。`WAITING_RESPONSE`は未実装（将来Phase、CUSTOMER REVIEW）。

## 11. Follow-up Reason

`DELIVERY_OVERDUE` / `PARTIAL_DELIVERY` / `NO_ARRIVAL` / `QUANTITY_DIFFERENCE` / `OTHER` の5値。正式な分類はCUSTOMER REVIEW。

## 12. Follow-up Manual Creation

`POST /api/orders/{id}/follow-up-cases`（OPERATOR/ADMINとも作成可能）。Order Detail画面のFulfillment明細行、またはFollow-upセクション自体から起票できる。自動生成は一切行わない。Generic Status APIは使用していない（Agreement/Reopen等、既存のBusiness Action命名規則を踏襲）。

## 13. Follow-up Mail Preview

Phase 7-C3のMail Template Foundationをそのまま再利用（`MailTemplate.TEMPLATE_TYPE_FOLLOW_UP`は7-C3時点で既に予約済みの値だった）。`FollowUpMailPreviewService`は`MailPreviewService`とほぼ同一の構造（Contact解決/Template解決/Admin CC解決/officialPoNo Gate）。Previewのみ、実送信は一切実装していない。Template未設定ならBLOCKED。CLOSED状態のCaseはPreview不可（10章）。

## 14. Supplier Responseとの区別

`FollowUpCase`と`SupplierResponse`/`SupplierResponseDetail`は完全に別Entityであり、一切のリレーションを持たない（`portal_order_id`経由でのみ間接的に同じOrderに属する）。将来Follow-upへのメーカー返信を履歴化する余地は`note`欄と将来の拡張列で確保しているが、本Phaseでは実装していない。

## 15./16. Reorder Foundation / Action

`portal_order`に`source_order_id`（自己参照FK）/`source_follow_up_case_id`（FK）/`reorder_reason`の3列を追加（すべてnullable、既存Orderは無影響）。`ReorderService.createReorderDraft()`は**既存の`OrderDraftService.createDraft()`をそのまま呼び出す薄いラッパー**として実装した - Order作成ロジック自体には一切変更を加えていない（28章のSTOP条件「既存Order Workflowの大幅変更」は非該当と判断）。数量は自動決定しない - 作成されるDraftのOrder Qtyは通常のCreate Draft同様、Legacy Recommended Qtyがそのまま初期値になる。Outstanding Qtyは表示のみ（Fulfillment明細）で、自動確定はしていない。

## 17. Cancellation扱い

Portal上のCancel Actionは実装していない。G-SYS Official POのCancel運用はCUSTOMER REVIEW（未確定）のため、本Phaseで一切のCancel処理を行わない。取消が必要な場合はFollow-up Caseのreason/noteとして記録するに留める。

## 18. Order Detail（再掲）

「G-SYS入荷状況」と「問い合わせ」を、既存のRevision History/Response Historyとは明確に別セクションとして配置した。

## 19. Dashboard

「問い合わせ中」KPI（`openFollowUpCaseCount` = OPEN + INQUIRY_PREPARED件数）を追加。自動督促期限が未確定なため「納期超過」KPIは追加していない（欠品/長期欠品KPIと同じ「件数のみ・専用Filterなし」パターンを踏襲）。

## 20. Permission

| 操作 | OPERATOR | ADMIN |
|---|---|---|
| Fulfillment閲覧 | ○ | ○ |
| Follow-up Case作成 | ○ | ○ |
| Note更新 | ○ | ○ |
| Follow-up Mail Preview | ○ | ○ |
| Follow-up Close | × (403) | ○ |
| Reorder Draft作成 | × (403) | ○ |

Backendで`@PreAuthorize("hasRole('ADMIN')")`により強制（`FollowUpCaseApiTest`でOPERATOR 403を実測）。

## 21. Audit

新規AuditEvent種別4つ: `FOLLOW_UP_CREATED` / `FOLLOW_UP_UPDATED`（Note変更時のみ、無変化での再保存は記録しない） / `FOLLOW_UP_CLOSED` / `REORDER_CREATED`。Legacy Fulfillment READ自体（`GET /api/orders/{id}/fulfillment`）は毎回Auditしていない（既存の他のGET系エンドポイントと同じ扱い、21章の明示的許可）。

## 22. DB Migration（V12）

`follow_up_case`テーブル新設、`portal_order`への3列追加（`source_order_id`/`source_follow_up_case_id`/`reorder_reason`）、`audit_event`のCHECK制約更新。`portal_order`と`follow_up_case`が相互参照するため、`DemoResetRunner`のTRUNCATE文で両方を同じ文に含めている。

## 23. Backend Full Test

`mvn clean test`で**299/299 tests, 0 failures, 0 errors, 0 skipped**（Phase 7-C5時点の275から+24: Fulfillment READ/Calculation/NOT_LINKED/Partial/Fulfilled/Credit PO netting、Follow-up CRUD/Close/Mail Preview/Reorder、Permission）。※検証中、本Phaseと無関係な既存7-C3テスト間（`MailTemplateServiceIntegrationTest`/`SupplierContactMailTemplateApiTest`）の既知の稀なflakinessに再度遭遇したが、7-C5フェーズで既にこの2クラスのみを単独抽出して本Phaseと無関係であることを実証済み。

## 24. Safety / Legacy READ ONLY

Legacy書き込み経路はゼロ。`FulfillmentReadRepository`は`legacyNamedParameterJdbcTemplate`経由のSELECT文のみ（既存の`OfficialPoPreflightReadRepository`/`LegacyStockReadRepository`と同じREAD ONLY保証3層構造）。Legacy Demo MySQLのスキーマ拡張自体はdocker init script（`01-schema.sql`、rootで一度だけ実行）によるものであり、Portal Applicationの実行時接続（`gsys_portal_ro`、SELECT権限のみ）とは完全に別の経路。

## 25. Frontend Build / Lint / Full E2E

`tsc -b && vite build`成功、oxlintエラー0（既存warning 4件のみ）。全E2E suite（50 tests）が2回連続で完全green（0 failures, 0 skipped）。

## 26. Browser Scenario A-G

| Scenario | 内容 | 結果 |
|---|---|---|
| A | officialPoNoなし → G-SYS正式PO未連携 → 未納と誤表示されない | ✅ |
| B | Test Official PO（完納）→ Fulfillment表示 → Ordered/Invoiced/Stock-in/Outstanding確認 | ✅ |
| C | Partial Order → PARTIAL → 問い合わせ対象作成 | ✅ |
| D | Follow-up Case → Follow-up Mail Preview → 実送信なし | ✅ |
| E | ADMIN → Follow-up Close | ✅ |
| F | Reorder → 元OrderとのReference確認 → 数量自動確定なし | ✅ |
| G | 全操作後Legacy変更ゼロ（UI観測可能な範囲） | ✅ |

**実装上の注意（E2E）**: officialPoNoは実UIから設定不可能（7-C2B未実装）なため、E2E層でも「Prototype Demo DB内へ直接SQL UPDATE」というBackend統合テストと同じ考え方のTest-only手法を採用した（Legacy WRITEではなくPrototype WRITE、docker exec経由）。実装中、Supplier Contact/Mail Templateの後片付け漏れが既存7-C3 E2E specを一時的に壊す実バグを発見・修正した（永続Masterデータのため、確実な後片付けにはPOSTレスポンスのidを信用せず必ずGETで再取得して照合する既存の確立されたパターンを使う必要があった）。

## 27. CUSTOMER REVIEW（このPhaseで確定していない事項）

- 未納の正式業務定義、Partialの正式業務定義。
- 問い合わせ開始Timing（自動督促ルールなし）。
- 問い合わせReason分類の正式版。
- 誰が問い合わせをCloseするか（本Phaseは暫定的にADMIN限定）。
- 再発注判断者、Outstanding Qtyを再発注数量のDefaultにするか。
- 再発注時のPO番号関係、Original PO残数量の扱い。
- CancellationのG-SYS上の正式処理。
- Follow-upメールTemplate内容の正式版。
- Supplier返信の記録方法（`FollowUpCase.note`は現状人手更新のみ）。
- `WAITING_RESPONSE`/`INQUIRY_SENT`の扱い（実送信機能が実装される将来Phase）。

## 28. Legacy変更ゼロ確認

`phasep-gulliver`に対する変更は本Phase中ゼロ（`git diff -w --numstat`で実質差分0件、HEADも不変）。Write/Edit系ツールは一度も呼んでいない。
