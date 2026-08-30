# Legacy Invoice / Purchase / Sales / Gross Profit Reverse Engineering & Target Analysis（Phase 8-D、Phase 8-Eで23〜25章追記）

**Status**: Docs / Audit Only。Frontend / Backend / DB Migration / API追加 / Legacy Source変更は一切行っていない。本Documentは「請求・仕入・売上・粗利」を独立した改善Optionとして整理できるかの調査であり、実装ではない（実装は本Phaseでは禁止）。

**Phase 8-E追記**: 「仕入集計・請求書目視確認支援 Foundation」の実装前提監査を実施した結果、**Gate: STOP**（23章）。Gross Amount定義がLegacy Sourceから一意に確定できないため、Foundation実装は行っていない（Docs Onlyで停止）。詳細は23〜25章。

**目的**: `customer-review-decision-package.md` 16章のModule/Option構成方針を前提に、請求・仕入・売上・粗利（Invoice/Purchase/Sales/Gross Profit）を、Ordering・Price Change・Stock/Sales Data Updateと並ぶ独立した改善Optionとして提案できるかを検証する。**必ず実装する前提ではない。**

**表記凡例**（Phase 8-A〜8-C継続）:
- 🟢 Source Confirmed　🟡 Gulliver Requirement　🔵 Target Proposal　🔴 CUSTOMER REVIEW　⚪ Operational Unknown

---

## 1. Executive Summary

- Gulliver社の要望（Slide 12）は「発注データを起点に、請求／仕入／売上／ブランド別実績／粗利等を確認・分析できるようにする」という**将来テーマとしての一括した課題提起**であり、「突合」「請求ミス」「請求書」「可視化」といった具体的な語は、2026/08/26打ち合わせ資料（Requirements.md／GSYS_Specification.md／議事メモpptx全15枚）のいずれにも見つからなかった（本Phaseの指示文中の例示であり、Written Sourceからの引用ではないと判断する。Phase 8-Cの「10日」と同種の扱い）。
- Legacy Sourceには`TR_INV`／`TR_INV_DTL`（Invoice相当）に加え、`TR_INV_PO`（PO⇄Invoice⇄船積⇄送金の明示的な紐付けTable）、`TR_INV_ADD_COST`（請求付帯費用）、`TR_BL`（Bill of Lading、船積書類）という、想定より充実したPurchase/Invoice側のTable群が存在することを確認した。
- **重要な発見**: `TR_INV`／`TR_INV_DTL`は、独立したSupplier請求書としてではなく、**Official PO Excel Importの同一処理（`PrOfficialPoImportBatch`）内で、PO自体と同時に生成される**。しかも、PO明細行の単価とInvoice明細行の単価は**同一Excelセル（列インデックス9固定）から読み取られる**ため、生成時点では構造的に「発注価格＝請求価格」であり、両者が異なるのは後続の訂正（Credit行）等に限られる。数量は別列であり、発注数量と請求数量は構造的に独立して持てる。
- **Sales側には金額Transactionが一切存在しない**。Tempostar受注CSV自体には単価・金額列（`PRC_UNIT`/`AMT_LINE`）が含まれているが、`SlTempostarImportBatch`はこれをWork Table（`WK_CPO`）で一時的に扱うのみで、最終的に`MS_STK`へ永続化されるのは数量（`SOLD_QTY`）だけであり、**金額は一切保存されない**（Phase 8-Cの確認結果と整合、かつ本Phaseで金額列自体の存在とその廃棄を追加確認）。
- したがって、**「実績粗利（実際の売上金額－実際の仕入原価）」を計算する材料はLegacy内に現存しない**。`Formula.java`の`PROFIT_RATE_SELL`はItem Master上の理論価格（売価－原価）による理論利益率であり、実績とは別概念であることを明確にした（6章）。
- PO→InvoiceのTraceabilityは、SKU・PO_NO単位では**完全Join可能**（Source Confirmed）。Invoice→Salesの金額単位でのTraceabilityは、Sales側に金額データが無いため**不可能**。
- 差異管理（Reconciliation/Difference Management）のBusiness RuleはLegacy・Meeting Sourceのいずれにも存在せず、全面的にCUSTOMER REVIEWへ送る。

---

## 2. Gulliver Requirements（原文ベース）

出典: `docs/G-Sys_mtg_20260826_02.pptx` Slide 12「発注・請求・売上・仕入データ」（全文引用、本Phaseで再抽出・確認）。

> 12. 発注・請求・売上・仕入データ / 将来的なテーマとして、発注データを起点に、請求／仕入／売上／ブランド別実績／粗利／等を確認・分析できるようにすることについても話題となりました。発注業務そのもののオンライン化とは分けながら、今後の拡張テーマとして検討します。

| 項目 | Meeting Sourceでの確認状況 |
|---|---|
| 発注データを起点に請求/仕入/売上/粗利を確認・分析したいという課題意識 | 🟡 確認済み（原文） |
| 「発注業務のオンライン化とは分ける」＝将来拡張テーマという位置づけ | 🟡 確認済み（原文） |
| 「突合」という語・概念 | ⚪ **Written Sourceに記載なし**（Requirements.md／GSYS_Specification.md／議事メモpptx全文検索、0件） |
| 「請求ミス」「請求ミス率」という表現 | ⚪ **Written Sourceに記載なし**（0件） |
| 「請求書」という語 | ⚪ **Written Sourceに記載なし**（0件、Slide 12では「請求」という語のみ） |
| 「可視化」という語 | ⚪ **Written Sourceに記載なし**（0件、Slide 12では「確認・分析できるように」という表現） |
| 何と何を突合したいかの具体的な言明 | ⚪ 原文になし |
| 発注金額/請求金額/仕入金額/売上/粗利をどこまで一連で確認したいか | ⚪ 「発注データを起点に」という順序性の言及のみで、突合や不一致検知への言及はない |

**結論**: Meeting Sourceで確定できるのは「発注データを起点に、請求・仕入・売上・ブランド別実績・粗利を確認・分析できるようにしたい」という将来テーマの提起までであり、本Phase指示に含まれていた「突合」「請求ミス率は低いが0ではない」等の具体的な文言はWritten Sourceには存在しない。Current OperationとFuture Requirementを混同しないよう、以降の章では**Sourceに存在する語彙のみ**を用いる。

---

## 3. Invoice Data（Source Confirmed）

Legacy Source全体を「Invoice」「Billing」「Claim」「請求」で検索したが、これらの語を含むクラス名は存在しない。実際の業務的に対応するのは`TR_INV`（Legacy Entity: `TrInv`）ファミリーである。

| Table | 内容 |
|---|---|
| `TR_INV`（`TrInv`） | Invoiceヘッダ。PK: `SUPPLIER_CD`+`INV_NO`。`STATUS`（TRANSIT/RECEIVING/STOCK_IN）、`TRAN_TYPE`（10=DEPOSIT／30=INVOICE／32=BILL_ONLY_DEPOSIT／35=BILL_ONLY／70=CREDIT）、`SUB_TTL_AMT`／`FREIGHT`／`AMT_TTL`／`QTY_TTL`、`ETD`/`ETA`/`STK_IN_DATE`（輸送関連日付） |
| `TR_INV_DTL`（`TrInvDtl`） | Invoice明細。PK: `SUPPLIER_CD`+`INV_NO`+`LINE_NO`。`ITEM_CD`／`QTY_ORDR`（発注数量）／`QTY`（Invoice数量）／`QTY_STK_IN`（実入荷数量）／`PRC_UNIT`／`AMT_LINE`／`PO_NO`（発注PO番号への参照） |
| `TR_INV_PO`（`TrInvPo`） | PO⇄Invoice⇄船積の明示的な紐付けTable。PK: `SUPPLIER_CD`+`INV_NO`+`PO_NO`。`BL_NO`（Bill of Lading番号）／`RMT_CCY`／`RMT_AMT`（**送金通貨・送金金額**）／`QTY` |
| `TR_INV_ADD_COST`（`TrInvAddCost`） | Invoice付帯費用明細。PK: `SUPPLIER_CD`+`INV_NO`+`LINE_NO`。`PAY_ITEM`（費目）／`AMT`（金額） |
| `TR_BL`（`TrBl`） | Bill of Lading（船荷証券）。`BL_NO`／`VESSEL_NO`／`SHIP_VIA`／`CONTR_NO`（コンテナ番号）等、輸入貿易実務の書類情報 |

**生成経路（本Phase新規確認、`PrOfficialPoImportBatch.java` 1061-1218行付近）**:

```
Official PO Excel（PO自体のSheet構成の中に、INV_NO列が存在する場合のみ実行される「CREATE INV」処理を含む）
  ↓ COL_IDX_INV_NO（ヘッダ文字列から動的に列を特定）が見つかった場合のみ
TR_INV（新規 or 既存Update、PK=SUPPLIER_CD+INV_NO）
  ↓ 同一Excel、PO明細行と同じ行範囲（ROW_IDX_PO_HEADER_2_1+1〜最終行）をループ
TR_INV_DTL（各Item行ごとに新規生成）
  - ITEM_CD: PO明細と同じセルから取得
  - PRC_UNIT: **COL_IDX_PRC_UNIT（固定列9）から取得 — PO明細行のPRC_UNIT検証・生成で使うのと"同一の列定数"**
  - QTY: COL_IDX_INV_NO列（動的特定された「Invoice数量」列、PO側のQTY列とは別）から取得
  - PO_NO: `trPoDtlRepository.findAllByPoNoAndItemCd(trPo.getPoNo(), itemCd)`でPO明細を明示的に検索し、紐付ける
```

**重要な意味**: TR_INVはSupplierから別途受領した請求書ドキュメントの入力ではなく、**Official PO Excel自体に埋め込まれた「Invoice区画」から、PO生成と同一のBatch実行・同一のExcelファイルの中で生成される**。PRC_UNITは固定列9番をPOとInvoiceの両方が参照するため、**生成時点ではPO単価とInvoice単価は構造的に同一値**である。QTYは別列のため、発注数量とInvoice数量は構造的に独立し得る（7章・8章で詳述）。

---

## 4. Purchase Data（Source Confirmed）

`TR_PO`／`TR_PO_DTL`（既存Reverse Engineering Documentで確認済み）に加え、本Phaseで以下を確認した。

- **Cost（原価）**: `MsItem`の`costLastMonthAvg`／`costThisMonthAvg`／`costLatestStkIn`（Phase 8-A `legacy-price-change-reverse-engineering.md` 5章で既確認）は、`PrStkInReportImportBatch`（Stock-In Report Import、`CODE_ID_FILE_IMP_STK_IN_REP`）が更新する。このBatchは`TR_INV`／`TR_INV_DTL`の**既存行**を検索・更新する処理が中心であり（`findBySupplierCdAndInvNoAndPoNoAndItemCd`）、新規TrInvDtl作成は主にCredit（差額調整）行の生成に限られる（3章で確認したPrOfficialPoImportBatchが主たる新規生成経路）。
- **Purchase Amount**: `TR_INV.AMT_TTL`／`TR_INV_DTL.AMT_LINE`が、実質的な「仕入金額」に相当する（PRC_UNIT×QTYで計算、3章参照）。
- **発注時価格と実際の仕入価格が別に存在するか**: 3章の確認どおり、**生成時点では同一**。ただしCredit PO（差額調整）経由で作られるTrInvDtlの価格は、既存Invoice行から複写される（`trInvDtl_org.getPrcUnit()`）か、明示的に0がセットされるパスも存在し（`PrStkInReportImportBatch.java` 761/822行）、その後の運用次第で実質的な差異が生まれる余地はある。

---

## 5. Sales Data（Phase 8-Cの結果を再利用、金額面のみ追加調査）

Phase 8-C（`legacy-stock-sales-data-reverse-engineering.md`）で確認済みの「`MS_STK.SOLD_QTY`＝Tempostar受注CSV基準の当月累計出荷数量」という結果はそのまま再利用し、本Phaseでは重複調査しない。本Phaseで追加確認したのは**金額面**のみ。

🟢 Source Confirmed（`SlTempostarImportBatch.java`、Phase 8-C時点で読了済みのColumn定義を金額観点で再確認）:

- Tempostar受注CSV自体には`COL_IDX_CPO_PRC_UNIT`（列9）・`COL_IDX_CPO_AMT_LINE`（列10）という**単価・金額列が存在する**。
- これらはWork Table（`WK_CPO`）へ一旦取り込まれ、日次・月次のSummary処理を経るが、**最終的にMS_STKへ反映されるのは`SOLD_QTY`（数量）のみ**であり、金額列はいずれの永続Tableにも書き込まれない（`msStkRepository.clearAllSoldQty()`→`msStk.setSoldQty(wkCpo.getQtyShip())`という数量オンリーの反映ロジックを再確認、6章のPhase 8-C記載どおり）。
- **結論**: Tempostar CSV自体には売上金額の材料（単価・金額）が含まれているにもかかわらず、G-SYSはそれを意図的に（またはその後の要件変化で）**捨てている**。Sales Amount／Selling Price（実売価格）／Discount／Tax／Net Salesに相当する永続Tableは、Legacy Source全体を検索しても存在しない（`TrSales`等のクラス名は0件）。

---

## 6. Gross Profit

Legacy Sourceで「Gross Profit」「Profit」「Margin」「Profit Rate」に相当するのは、`MsItem`の`profitRateSell`／`profitRateSale`（Phase 8-Aで確認済み、`Formula.PROFIT_RATE_SELL()`/`PROFIT_RATE_SALE()`により算出）のみである。

| 概念 | 実体 | 単位 | 実績か理論か |
|---|---|---|---|
| `MsItem.profitRateSell`/`profitRateSale` | `(販売価格 − 当月平均原価) ÷ 販売価格` | SKU単位、Item Master上の**現在時点の**値 | 🟢 **理論利益率**（商品マスタの売価と原価の差、Phase 8-A確認済み） |
| 実績粗利（実際の販売数量×実際の売上金額－実際の仕入原価） | **Legacy内に対応する計算・保存箇所なし** | — | 存在しない |

**明確化**: Price Change Foundation（Phase 8-B/`MarginCalculator`）が再利用したのは前者（理論利益率）である。「実績粗利」（実際に何個売れて、いくらの売上があり、いくらの原価がかかったか）を計算するには、5章で確認したとおり**永続化された金額付きSales Transactionが存在しないため、Legacy内のデータだけでは計算不能**。この区別は、8章のReconciliation設計および17章の改善Option整理において特に重要である。

---

## 7. PO → Invoice Traceability

指示のとおり、完全Join可能／部分的に可能／不可能をSource Evidence付きで分類する。

| 対象 | 分類 | Evidence |
|---|---|---|
| **PO ⇄ Invoice**（明細単位） | 🟢 **完全Join可能** | `TR_INV_DTL.PO_NO`＋`ITEM_CD`で`TR_PO_DTL`を直接特定できる（`findAllByPoNoAndItemCd`、3章）。ヘッダ単位でも`TR_INV_PO`（PK: SUPPLIER_CD+INV_NO+PO_NO）が明示的に存在 |
| **PO/Invoice ⇄ Arrival**（入荷実績） | 🟢 **完全Join可能** | 既存Fulfillment Reverse Engineering（Phase 7-C7A）で確認済みの`TR_INV_DTL.QTY_STK_IN`とCredit PO/Invoiceによる差異netting機構がそのまま使える（本Phaseで再調査していない） |
| **PO/Invoice ⇄ 船積（BL）** | 🟢 **完全Join可能** | `TR_INV_PO.BL_NO`→`TR_BL`（本Phaseでは`TR_BL`の詳細構造までは深掘りしていない） |
| **PO/Invoice ⇄ 送金（Payment）** | 🟡 **部分的に可能** | `TR_INV_PO.RMT_CCY`/`RMT_AMT`は存在するが、実際の支払実行・消込のBusiness LogicはSource上未調査（本Phaseの調査範囲外） |
| **PO/Invoice ⇄ Sales（金額）** | 🔴 **不可能** | Sales側に金額Transactionが存在しない（5章）。唯一可能なのはSKU×月単位の`SOLD_QTY`（数量）との突合のみであり、個別PO/Invoiceとの1:1紐付けはできない |

---

## 8. Reconciliation（Target Proposal — Gulliver要望と明確に分離）

**この章の内容はSourceから確認されたGulliver Requirementではなく、7章の調査結果に基づく本Documentの設計案（🟡 Target Proposal）である。** 2章で確認したとおり、Meeting Sourceに「突合」という具体的な要望は存在しない。

7章のJoin可能性を踏まえ、技術的に構成しうるTarget Conceptの候補を整理する（**採否・優先度はGulliver社の意思決定事項であり、本Documentでは決定しない**）:

| 比較対象 | 技術的に構成可能か | 根拠 |
|---|---|---|
| Ordered Qty vs Invoiced Qty | 🟡 可能 | `TR_PO_DTL.QTY_PO` vs `TR_INV_DTL.QTY`（別列から生成、構造的に独立し得る） |
| Ordered Unit Price vs Invoiced Unit Price | 🟡 可能だが**生成時点では常に一致する構造**（3章） | 差異が生じるとすれば、生成後の訂正・Credit処理経由のみ |
| Ordered Amount vs Invoice Amount | 🟡 可能 | 上記2つの積 |
| Received Qty（Arrival）vs Invoice Qty | 🟡 可能、**Fulfillment機能が既に類似の計算を実施済み** | `TR_INV_DTL.QTY_STK_IN`は既にPhase 7-C7Aで利用実績あり |

**特記事項**: 「Ordered Unit Price vs Invoiced Unit Price」は、Legacyの現行実装が「同一Excelセルから両方生成する」という構造である以上、**通常運用では差異が発生しない設計**になっている。したがって、この比較を新機能として提案する場合、実際にどのようなケースで発注価格と請求価格の差異が発生しているのか（後日の価格改定、輸送中の為替変動調整等）をGulliver社に確認しない限り、何を検出したいのかという目的自体が定義できない。

---

## 9. Difference Management（現状把握 — CUSTOMER REVIEW）

- **Legacy Source**: 差異発生時の「誰が確認する」「許容差」「自動承認」「手動確認」「差戻し」「Supplier問い合わせ」「修正」「Close」に相当するBusiness LogicはSource全体を検索しても見つからない。既存の関連する仕組みは、Phase 7-A/7-C6で確認済みの**Official PO Import時のGuard**（Invoiceが既に存在するPOの削除拒否等、`legacy-procurement-workflow-reverse-engineering.md`既述）と、**Credit PO/Invoiceによる差額の事後記録**（新しい負のPO/Invoiceペアとして記録する、7章）のみであり、いずれも「差異を検知して人にReviewさせる」というWorkflowではなく、システム上の整合性制約・事後修正の記録手段である。
- **Meeting Source**: 2章で確認したとおり、差異管理に関する具体的な要望はSlide 12を含めどこにも存在しない。
- **結論**: 差異管理のBusiness Ruleは全面的に未確定であり、勝手に確定せずCUSTOMER REVIEWへ送る（20章）。

---

## 10. Target Workflow候補（🟡 Target Proposal、機械的に採用しない）

7-9章の調査結果に基づき、妥当性を検証したうえでの概念設計を示す。

```
[🟢 Source Confirmed] PO確定（Ordering機能、既存実装）
        ↓
[🟢 Source Confirmed] Official PO Excel Import時にInvoiceも同時生成（TR_INV/TR_INV_DTL、3章）
        ↓
[🟡 Target Proposal] PO/Invoice突合（8章の比較候補、Qty中心。Priceは通常一致するため対象外の可能性）
        ↓
[🔴 CUSTOMER REVIEW] Difference Detection時の許容差・承認フロー（9章、未確定）
        ↓
[🟢 Source Confirmed（既存機能）] Arrival/Fulfillment（Phase 7-C7A、QTY_STK_INベース）
        ↓
[🔴 CUSTOMER REVIEW] Sales Link（5章の結論どおり、金額での連携は現状不可能。数量のみSKU×月単位で可能）
        ↓
[🟡 Target Proposal] Gross Profit可視化（6章の区別を踏まえ、「理論利益率の表示」と「実績粗利の計算」は別物として扱う必要がある）
```

**評価**: 指示例で示された順序（PO→Supplier Invoice取込→突合→Difference Detection→Review→Purchase Confirmation→Sales Link→Gross Profit）は、**Invoiceを「別途取り込むもの」と仮定している点でLegacyの実態と異なる**（3章の確認どおり、InvoiceはPOと同時生成される）。したがって「Supplier Invoice取込」を独立したStepとして機械的に採用せず、上記のように「PO確定と同時にInvoiceが生成される」という実態に即した形へ修正した。

---

## 11. System of Record

| データ | SoR候補 | 根拠 |
|---|---|---|
| PO | Legacy（`TR_PO`） | 既存Ordering機能の前提どおり |
| Invoice | Legacy（`TR_INV`） | 3章、PO生成と同一Batchで作られる |
| Purchase（仕入金額） | Legacy（`TR_INV.AMT_TTL`等） | 4章 |
| Arrival | Legacy（`TR_ARR`） | 既存Fulfillment機能の前提どおり |
| Sales（数量） | Legacy（`MS_STK.SOLD_QTY`、Tempostar由来） | Phase 8-C確認済み |
| Sales（金額） | **現状SoR無し** | 5章、Legacyのどこにも永続化されていない。将来Portalが保持するかはGulliver Future Decision（Phase 8-Cの14章と同種の論点） |
| Cost（原価） | Legacy（`MsItem.costThisMonthAvg`等） | 4章、Phase 8-A確認済み |
| Selling Price（売価） | Legacy（`MsItem.prcSellWTax`等） | Phase 8-A `target-price-change-workflow.md` 7章の原則どおり |
| Gross Profit（理論） | Legacy（`MsItem.profitRateSell`等） | 6章 |
| Gross Profit（実績） | **現状SoR無し**（計算材料自体が存在しない） | 6章 |

**PortalがSoRになる提案の責任範囲**（Target Proposal、決定ではない）: 仮にPortalがSales金額を新たに蓄積する場合、Phase 8-Cの14章と同じ考え方で「Portalは観測した時点の値の記録を保証するのみ」であり、Legacy側の実績を遡って再現・保証するものではない。

---

## 12. Integration Alternatives（未決定、比較のみ）

| 案 | 内容 | 評価 |
|---|---|---|
| A. Excel Upload | Supplier発行のInvoice Excel等を新規Import Folder経由で取り込む | 既存Import Batch群と同型のアーキテクチャで実装しやすいが、実運用でSupplierがどんな形式のInvoiceを発行しているか不明（⚪ Operational Unknown、20章） |
| B. CSV Upload | 同上、CSV版 | 同上 |
| C. Existing Legacy Import再利用 | 3章で確認した`PrOfficialPoImportBatch`のInvoice生成部分をArtifact生成方式で再利用 | Price Change/Official PO Integrationと同じ「Portal→Artifact→既存Import」パターンが適用できる可能性 |
| D. External System連携 | Tempostar等、外部システムから直接データを受ける | External System仕様が必要（12章の指示どおりCustomer/Ernest Questionへ） |
| E. Manual Entry | Portal画面から手入力 | 最もLegacy非依存だが、実運用の入力負荷は未評価 |

**結論**: 現時点でいずれかを決定しない。特にSupplier Invoiceの実際の受領形式（PDF/Excel/紙等）はSource・Meeting Sourceのいずれからも確認できず、19章のErnest/Customer Questionへ送る。

---

## 13. Improvement Options分解

事前提示のA〜G分類を検証したうえで整理する。

| Option | 内容 | Customer Value | Legacy Gap | 単独導入可能性 | Technical Dependency | Business/Estimate Dependency | External System Dependency | Customer Decision Dependency | 実装難易度（相対） |
|---|---|---|---|---|---|---|---|---|---|
| **A. Purchase Visibility（仕入可視化）** | 既存`TR_INV`/`TR_PO`データをPortalで参照・表示するのみ | 中（現状Excel/画面でしか見えない情報の可視化） | 表示機能自体が無い | 可 | 低（READ ONLY） | 無 | 無 | 低 |
| **B. PO/Invoice Reconciliation（8章のQty比較）** | Ordered Qty vs Invoiced Qty等の比較表示 | 中〜高（要望の具体化次第） | 比較機能自体が無い | 可（Aと独立） | 低（READ ONLY、7章のJoinのみ） | 低 | 無 | 中（何を比較すべきかの確定が必要） |
| **C. Difference Management（9章）** | 差異検知後のWorkflow | 高（要望次第） | Business Rule自体が無い（9章） | 不可（Bへの依存） | 中（Bの結果を使う） | 高 | 無 | **高（9章の未確定Business Rule全て）** |
| **D. Sales Amount蓄積（Portal新規）** | Tempostar CSVの単価/金額列を新たにPortalで保持 | 高（実績粗利計算の前提を作る） | Sales金額の永続化自体が無い（5章） | 可（他Optionと独立） | 中（新規蓄積の仕組みが必要） | 高（SoRをPortalにする判断） | 間接（Tempostar由来） | 高 |
| **E. Realized Gross Profit可視化** | 実績粗利の計算・表示 | 高 | 6章のとおり計算材料が無い | 不可（Dへの完全依存） | **高（Dへの直接Technical Dependency）** | 高 | 無 | 高 |
| **F. Theoretical Margin表示** | `profitRateSell`等の既存理論値をそのまま表示 | 低〜中（Price Change Foundationで既に一部実装済みの`MarginCalculator`を流用可能） | 無（Phase 8-Bで一部実装済み） | 可 | 低（Price Change Foundationとの共有、15章） | 低 | 無 | 低 |
| **G. Dashboard / Reporting** | 上記の集約表示 | 高 | Dashboard機能自体は既存だが本領域は9/17 Scope外と既に整理済み | 不可（A〜Fの後続） | 高（複数Optionに依存） | 高 | 無 | 高 |

**検証結果**: 事前提示のA〜G分類は概ね妥当だが、**「実績粗利」（E）は「Sales Amount蓄積」（D）への強いTechnical Dependencyを持つ**ため、単独提案はできない。**A（仕入可視化）・B（Qty比較）・F（理論Margin表示）が単独導入可能な候補**であり、C・E・Gはこれらの後続として位置づける。

---

## 14. Ordering Dependency

- **`portal_order`/`portal_order_detail`**: 発注時のSKU/Supplier/Brand/Qty/Unit PriceのSnapshotを持つ。Invoice機能が7章のPO⇄Invoice Joinを行う際、Portal側で保持しているofficialPoNoを起点にLegacy TR_INVをREAD ONLY参照する設計が可能（Official PO Integration機能で既に確立された`officialPoNo`の扱いをそのまま使える）。
- **Revision / Supplier Response**: Invoice機能に直接使う必要はない（PO確定後のLegacy側処理が対象のため）。
- **Official PO Integration Request**: `officialPoNo`が確定していることがInvoice参照の前提となるため、Business/Estimate上の関連はある。
- **Fulfillment / Follow-up（`FulfillmentReadRepository`）**: **既に`TR_INV_DTL`のQty系列（`QTY_STK_IN`等）を参照している**（Phase 7-C7A確認済み）。したがってInvoice機能の一部（Qty面）は既にPortalが部分的に触れている。
- **Invoice機能を導入しなくてもOrderingは成立するか**: 🟢 成立する。Ordering機能はTR_PO/TR_ARR/MS_STKのみに依存し、TR_INVへの依存はFulfillmentのQty参照のみ（既存実装済み範囲）に限られる。
- **逆にInvoice機能がOrderingのどのDataを必要とするか**: officialPoNo（Official PO Integration機能が既に持つ）、Supplier/Brand/SKU識別子（既存）。

**Business/Estimate DependencyとTechnical Dependencyの区別**: FulfillmentがTR_INV_DTLのQtyを読む実装は既に存在する（**Technical Dependency: 既存**）。一方、「請求・粗利を確認したい」という要望自体がOrdering機能の延長線上で語られている（Slide 12「発注データを起点に」）のは**Business/Estimate上の関連**であり、Invoice機能の新規実装がOrdering機能のCode変更を必要とするわけではない。

---

## 15. Price Change Dependency

- **共通利用可能なもの**: SKU identity（`item_cd`）、Brand（`brand_cd`）、Cost（`MsItem.costThisMonthAvg`）は、Price Change Foundation（Phase 8-B）の`LegacyPriceReadRepository`が既に参照している列と完全に一致する。
- **Cost更新の共有関係**: 4章で確認した`PrStkInReportImportBatch`（Invoice/Arrival処理の一部）がCostを更新し、Price Change機能の`MarginCalculator`（Phase 8-A/8-B）がそのCostを読む、という**既存の技術的connection**が既に成立している（新規に作る必要はない）。
- **Formula/MarginCalculatorの再利用可能性**: 6章のとおり、Price Changeが扱う利益率は「理論利益率」であり、本Phaseで検討する「実績粗利」とは別概念。ただし計算ロジック自体（`MarginCalculator`）は同一であり、13章のOption F（Theoretical Margin表示）ではそのまま再利用できる。
- **不自然な密結合を作らないための注意**: Price ChangeのChange Set構造とInvoice機能を無理に結合する必要は無い。両者はSKU/Cost参照という共通基盤を共有するのみで、片方が無くても他方は成立する（16.3章の原則）。

---

## 16. Stock/Sales Dependency

- Phase 8-Cで確認した`SOLD_QTY`（数量）と、本Phaseで確認した「Tempostar CSVには金額列があるが破棄されている」という事実は**同一のBatch（`SlTempostarImportBatch`）の同一処理内**の話であり、Sales Amount蓄積（Option D）を実装する場合、Phase 8-Cで提案した「Portal側Sales History蓄積」（Option B、Stock/Sales Data Update側）と**技術的に同じ拡張ポイント**（同じCSV・同じWK_CPO処理）を使うことになる。
- したがって、将来Option D（Sales Amount蓄積）とPhase 8-CのOption B（Sales History蓄積）は、**Estimate上は別Optionとして提示可能だが、実装時には同一のBatch改修で同時に対応した方が自然**である可能性が高い（16.3章の「見積上のOption分割とSoftware Module分割を1対1にしない」原則の具体例）。
- Audit: Price Change Foundation（Phase 8-B）が確立した`AuditEvent`の第2集約ルート方式（`price_change_set_id`）と同じ考え方が、将来Invoice機能の監査ログにも転用できる可能性がある（技術パターンの共有、Table自体は独立させるかは実装時の判断）。

---

## 17. Foundation Classification

| 分類 | 内容 |
|---|---|
| **A. Customer回答前でも安全に実装可能** | Option A（Purchase Visibility、既存TR_INV/TR_POのREAD ONLY表示）、Option F（既存Formula/MarginCalculatorの理論値表示） |
| **B. Customer回答後まで待つもの** | Option B（何を比較するかの確定後）、Option C（差異管理Business Rule確定後）、Option D（Sales Amount蓄積の採否・SoR判断後）、Option E（Dの完成後） |
| **C. External仕様確認後まで待つもの** | Supplier Invoiceの実際の受領形式・Integration方式（12章）、Option G（複数Option依存のため） |

**今回は実装しない**（指示どおり）。

---

## 18. Customer Review

新規CUSTOMER REVIEW項目（既存項目との重複なし）。

| 論点 | 分類 | 出典 |
|---|---|---|
| PO/Invoice間のQty比較（8章）を機能として実装すべきか | D（Future Decision） | 8章・13章Option B |
| 差異発生時のReview/承認/Supplier問い合わせ等のBusiness Rule | D | 9章・13章Option C |
| Sales Amount（金額）をPortal側で新規蓄積すべきか、その場合のSoR責任分担 | D | 5章・11章・13章Option D |
| 実績粗利可視化の要否 | D | 6章・13章Option E |
| Supplier Invoiceの実際の受領形式・Integration方式 | D（Ernest確認候補あり、19章） | 12章 |

---

## 19. Ernest Questions

`ernest-current-operation-question-sheet.md`への追加候補（重複確認済み、既存Q1-28とは非重複）。

| 論点 | 出典 |
|---|---|
| Supplier Invoiceは実際にどの形式（PDF/Excel/紙等）で受領しているか。3章で確認したOfficial PO Excel内のInvoice区画と、実際にSupplierから受け取る請求書は同一のものか別物か | 3章・12章 |
| PO単価とInvoice単価が実際に異なるケースがどの程度あるか、あるとすればどんな場面か（価格改定・為替調整等） | 3章・8章 |
| Credit PO/Invoiceによる差額調整が実際にどのくらいの頻度で発生しているか | 4章・9章 |
| `TR_INV_PO.RMT_AMT`（送金金額）の実際の消込・支払実行運用 | 7章 |
| 発注データ・請求データ・売上データを一連で確認したい」というSlide 12の要望について、具体的にどんな場面・頻度で必要になるか | 2章 |

---

## 20. Risks

- **「突合」「請求ミス」等の語がWritten Sourceに存在しない**: 本Phase指示に含まれていたが、Meeting Sourceには見つからなかった（Phase 8-Cの「10日」と同種のリスク）。口頭でのやり取りに由来する可能性があり、事実確認せず設計前提にするリスクがある。
- **「理論利益率」と「実績粗利」の混同リスク**: 6章で明確に区別したが、Gulliver社への説明時にこの区別が失われると、既存のPrice Change Foundation（理論値のみ）が「粗利分析機能」であるかのように誤解される可能性がある。
- **PO単価=Invoice単価という構造の見落としリスク**: 8章のReconciliation機能を設計する際、「発注価格と請求価格は通常差異が無い」という3章の構造を踏まえずに設計すると、実際にはほとんど差異が検出されない機能を作ってしまう可能性がある。
- **Sales Amount非保持のリスク**: 5章で確認したとおり、Tempostarから来る金額情報は現在完全に破棄されている。将来Sales Amount蓄積を検討する場合、**過去に遡って再現することはできない**（今後受信する分からしか蓄積できない）。

---

## 21. Recommended Next Step

提案のみ、確定ではない。

- 18章のCUSTOMER REVIEW・19章のErnest Question回答を待つ。
- 17章のFoundation A（Purchase Visibility・理論Margin表示）は他Optionへの前提を作らない最小範囲であり、次Phase候補として検討しやすい。
- Sales Amount蓄積（Option D）は、Phase 8-CのStock/Sales Option Bと技術的な実装ポイントが重なるため、両者を将来同時に検討すると効率的である可能性がある（16章）。

---

## 22. Final Classification Table

| # | 論点 | 分類 | 状態 |
|---|---|---|---|
| P-1 | 「突合」「請求ミス」等の具体的文言 | Ernest/Gulliver確認要（Written Sourceに存在せず） | 19章・2章 |
| P-2 | Supplier Invoice受領形式 | Ernest確認候補 | 19章 |
| P-3 | PO/Invoice単価差異の実例 | Ernest確認候補 | 19章 |
| P-4 | Credit PO/Invoice発生頻度 | Ernest確認候補 | 19章 |
| P-5 | 送金消込運用 | Ernest確認候補 | 19章 |
| P-6 | PO/Invoice Qty比較機能の要否 | D（Future Decision） | 18章 |
| P-7 | 差異管理Business Rule | D | 18章 |
| P-8 | Sales Amount蓄積・SoR判断 | D | 18章 |
| P-9 | 実績粗利可視化の要否 | D | 18章 |
| P-10 | Supplier Invoice Integration方式 | D（一部Ernest確認候補） | 18章・12章 |

---

## 23. Phase 8-E: Gross Amount定義の一意性監査（Implementation Gate Audit）

**目的**: 「仕入集計・請求書目視確認支援」Foundation（仕入確認画面）の実装前に、「G-SYS上の仕入金額合計（Gross Amount）」としてLegacy Sourceのどの値・計算式を正とすべきかが、Sourceから安全かつ一意に確定できるかを監査する。**推測で計算式を決めない**という指示に基づき、Legacy Batch実装を直接調査した。

### 23.0 Phase 8-Eで確定した前提（Business Requirement）

- 本機能は「請求書管理システム」ではない。SupplierのInvoiceをPortalへ取り込む・保存する・OCRする・Portal上のInvoice Dataとして自動突合する、といった機能は一切作らない。
- 本機能の役割は、**手元の請求書（形式は不定：SKU明細型／PO単位Summary型／Supplier×期間Gross Only型／紙／PDF／データ／別システム）に記載されたGross Amountが妥当かを、G-SYS上の仕入Dataを任意条件で集計して担当者が目視確認できるようにする**ことに限定される（Portal自身がAmountの正誤を判定しない）。
- この前提は4章のPurchase Data・11章のSystem of Recordとも整合するが、23.1以降で確認するとおり、**「G-SYS上の仕入金額合計」という一見単純な値自体が、Sourceの実装上は複数の非等価な計算式で書き込まれている**ため、Gross Amountの定義確定が本機能実装のBlockerになる。

### 23.1 Source Confirmed：`TR_INV.AMT_TTL`／`TR_INV_DTL.AMT_LINE`を書き込むBatchと計算式

`grep -rn "setAmtTtl" src/main/java/jp/ne/glv/batch/*.java src/main/java/jp/ne/glv/services/*.java`により、`TR_INV.AMT_TTL`（またはPO側`TR_PO.AMT_TTL`）へ書き込みを行うBatchが**最低5つ**存在することを確認した。うち代表的な3つを実装レベルで詳細確認した。

| Batch | 書き込み箇所 | 計算式 | 特記事項 |
|---|---|---|---|
| **`PrOfficialPoImportBatch`**（3章、Invoice/POの主たる新規生成経路） | `trInv.setAmtTtl(amtTtl)`（1180行） | 変数`amtTtl`は953行で宣言、1050行で`amtTtl.add(prcUnit × qtyPo)`により**PO発注数量（QTY_PO）ベース**で累計される、PO自身の`trPo.setAmtTtl(amtTtl)`（1054行）と**同一の外側scope変数** | 🔴 **Invoice明細ごとに正しく積算される`trInvAmtTtl`（1176行、`amtLine`の累計）は変数として存在するにもかかわらず、ヘッダへは書き込まれず破棄される**。かつ主経路（既存Delete行の複写でない新規行、1168行`else`節）では`trInvDtl.setAmtLine(...)`が一切呼ばれず、**`TR_INV_DTL.AMT_LINE`はNULLのまま**（`setPrcUnit`のみ実行） |
| **`PrBLInvImportBatch`**（Bill of Lading／通関実務に紐づく、3章では未言及の別Batch） | `trInv.setSubTtlAmt(subSmtTtl)`／`setFreight(freight)`／`setAmtTtl(subSmtTtl.add(freight))`（1663-1665行） | `subSmtTtl`は1619行で明細ループごとに`prcUnit × qty`を正しく積算。**新規明細行には`trInvDtl.setAmtLine(prcUnit × qty)`（1610行）も実行される** | 🟢 この経路のみ、Header（`SUB_TTL_AMT`+`FREIGHT`=`AMT_TTL`）とDetail（`AMT_LINE`）が内部整合的に計算・永続化される。ただし同一PK（`SUPPLIER_CD`+`INV_NO`）に対する**後発の上書き**として動作するため、このBatchが実行されて初めて`PrOfficialPoImportBatch`の値が補正される構造 |
| **`PrCreditPoImportBatch`**（Credit PO/Invoice、差額調整） | `trInv.setAmtTtl(trInv.getSubTtlAmt().add(trInv.getFreight()))`（701行）／`trInv.setAmtTtl(trInv.getSubTtlAmt())`（703行、条件分岐で別式） | Credit調整固有のロジック、`SUB_TTL_AMT`+`FREIGHT`の場合と`SUB_TTL_AMT`のみの場合の**2通りの式が条件分岐で使い分けられる**（分岐条件は本Phaseでは未特定） | 🔴 Credit行がどの条件でどちらの式を通るかは未確認。通常Invoiceとの合算方法（符号・Netting）も未確認 |
| `PrStkInReportImportBatch`（4章で既述、Stock-In Report Import） | `crdTrInvDtl.setAmtLine(...)`（834行）等 | Credit行（`crdTrInvDtl`）生成時のみ実行 | 🔴 **通常（非Credit）の既存`TrInvDtl`行に対しては`AMT_LINE`／`PRC_UNIT`を一切更新しない**ことを確認済み（`setAmtLine\|setPrcUnit`の全6件がCredit行生成分岐内）。つまりこのBatch（在庫受入の実運用上最も高頻度に走るBatchの一つ、Phase 7-C7A）は、`PrOfficialPoImportBatch`が残したNULL/誤ったAMT_LINEを補正する経路には**ならない** |
| `PrBLInvImportBatch`「DUMMY TRANSACTIONS」区画 | `trPo.setAmtTtl(entity.getRmtAmt())`（1691行） | **送金金額（RMT_AMT）起点**の第3・第4の計算パターン | 🔴 未完了・非最終Transaction向けの仮Record生成と見られるが、本Phaseでは用途・発生条件を確定できていない |

### 23.2 確定できないこと（Operational Unknown）

- **`TR_INV.STATUS`（TRANSIT／RECEIVING／STOCK_IN）が、どのBatch由来の値が権威（authoritative）かを判別する信頼できるSignalになるか**は未確認。本Phaseの時間内では検証しきれず、次の調査ステップとして残した。
- **`PrBLInvImportBatch`が全Invoiceに対して実行されるのか、一部（海上輸送／輸入貨物等、実際にBLが発行される取引）に限られるのか**はSourceから確定できない。もしBL取引のみに限られる場合、それ以外のInvoiceは`PrOfficialPoImportBatch`由来の誤った/NULLのAMT_TTL/AMT_LINEのまま残り続ける可能性がある。
- **`PrCreditPoImportBatch`の701行/703行の条件分岐がどのような業務条件で使い分けられるか**、および通常Invoiceとの合算（Netting）方法。
- **`TR_INV_ADD_COST`（Invoice付帯費用、3章既述）がいずれかのAMT_TTL/SUB_TTL_AMTに含まれているか、別立てで加算すべきか**：本Phaseで`TR_INV_ADD_COST`への参照箇所を再検索した範囲では、23.1の主要3 Batchの`AMT_TTL`/`SUB_TTL_AMT`計算式のいずれにも`TrInvAddCost`由来の値を加算する処理は確認できなかった（＝付帯費用は現状どのHeader Totalにも反映されていない可能性がある）。ただし全参照箇所を網羅的に確認しきれておらず、Operational Unknownとして扱う。
- **Tax（消費税・関税等）に相当するField／計算ロジック**: `TR_INV`/`TR_INV_DTL`/`TR_INV_ADD_COST`のいずれにも`TAX`様のField名は確認できなかった（`Freight`はあるが`Tax`相当は無し）。Taxが別のTable/仕組みで管理されているか、単に対象外（輸入取引でSupplier Invoice自体に税が乗らない）なのかは未確認。
- **Cancelled/Invalid Dataの除外方法**: `TR_INV`/`TR_PO`に論理削除・無効化Flagが存在するかどうかは、本Phaseの調査範囲では確認していない（既存Reverse Engineeringでも未言及）。

### 23.3 Gross Amount候補（いずれも欠陥あり、一意に決定不能）

| 候補 | 内容 | 欠陥・懸念 |
|---|---|---|
| 候補1: `TR_INV.AMT_TTL`（Header） | Invoiceヘッダの`AMT_TTL`をそのまま合計 | 🔴 23.1のとおり、`PrOfficialPoImportBatch`由来の値は**PO発注数量ベースの誤った値**であり、`PrBLInvImportBatch`が後から実行されて初めて正しい値に上書きされる。どちらの状態のInvoiceが混在しているか、集計時点では判別できない |
| 候補2: `SUM(TR_INV_DTL.AMT_LINE)`（Detail集計） | Invoice明細のAMT_LINEを合計 | 🔴 `PrOfficialPoImportBatch`の主経路ではAMT_LINEが**NULL**のまま残るため、Batch実行順序次第で欠損値を含んだ集計になる（NULLをどう扱うか自体もBusiness Rule未確定） |
| 候補3: `TR_INV.SUB_TTL_AMT + FREIGHT` | `PrBLInvImportBatch`/`PrCreditPoImportBatch`が実際に使う内部整合的な式 | 🔴 `PrOfficialPoImportBatch`由来の未上書きInvoiceには`SUB_TTL_AMT`/`FREIGHT`自体が設定されていない可能性が高く（23.1のBatch別experiment未実施）、全件に適用できるか不明。加えて`TR_INV_ADD_COST`が含まれるか不明（23.2） |
| 候補4: 候補1〜3 + `SUM(TR_INV_ADD_COST.AMT)` | 付帯費用を明示的に加算する案 | 🔴 23.2のとおり、付帯費用を加算すべきという業務的な根拠（顧客からの請求書にAdditional Costがどう反映されているか）自体が未確認 |

**結論**: 4候補いずれも、少なくとも1つの重大な未確定要素（Batch実行順序依存／NULL欠損／付帯費用の要否／適用範囲）を抱えており、**Legacy Sourceの調査のみでは一意に決定できない**。

### 23.4 Gate判定：**STOP**

**判定根拠**（指示21章の基準に基づく）:

1. `TR_INV.AMT_TTL`は、Invoice作成の主経路（`PrOfficialPoImportBatch`）において、Invoice自身の明細から計算されるべき値ではなく、**PO発注側の変数を誤って参照する実装上の不具合とみられる状態**で書き込まれている（23.1）。
2. `TR_INV_DTL.AMT_LINE`は、同じ主経路の新規明細行に対して**恒常的にNULL**として残る（23.1）。
3. 上記を補正しうる`PrBLInvImportBatch`が、**全InvoiceかBLを伴う一部Invoiceのみかが確定できない**（23.2）。
4. 結果として、任意時点で任意のInvoiceを集計対象にした場合、その値が「補正済み・正しい値」なのか「未補正・誤った値／NULL」なのかを、Portal側のQueryだけでは判別できない。
5. 加えてTax・付帯費用（`TR_INV_ADD_COST`）・Credit Netting・Cancelled Data除外の扱いも未確定（23.2）。

以上より、「請求書確認に使うGross Amountとして、どのLegacy金額を正とするか」を**推測で決めることはできない**と判断し、本Phaseでは**仕入確認Foundationの実装を行わない（Docs Onlyで停止）**。

### 23.5 Customer/Ernest確認事項（新規、既存Theme A-Kとの重複なし）

| 論点 | 分類 | 想定回答者 |
|---|---|---|
| `PrOfficialPoImportBatch`由来のAMT_TTL/AMT_LINEの不整合（PO発注数量ベースの値がInvoice金額として書き込まれる、明細AMT_LINEがNULLのまま残る）について、**実際の請求書確認業務で気づかれたことがあるか／どう対処しているか** | B（Ernest、23.1） | Ernest |
| `PrBLInvImportBatch`（Bill of Lading経由のInvoice補正）が実際にどのSupplier・取引形態（海上輸送／その他）で実行されるか、全Invoiceが最終的にこの経路を通るか | B（Ernest、23.2） | Ernest |
| `TR_INV_ADD_COST`（付帯費用）が、Supplierからの実際の請求書Gross Amountに含まれているか、別立て費用として扱われているか | D（Gulliver Future Decision） | Gulliver |
| Tax（消費税・関税等）がSupplier Invoiceに乗るか、乗る場合Legacyのどこかに記録されているか | B/D混在 | Ernest→Gulliver |
| 上記の不確実性を踏まえたうえで、「仕入確認」機能におけるGross Amountの正式な定義（暫定値であることを明示したうえでの表示可否を含む） | D（Gulliver Future Decision） | Gulliver |

---

## 24. Phase 8-E Implementation Gate 結果サマリ

| 項目 | 結果 |
|---|---|
| Gate判定 | **STOP**（23.4） |
| 理由 | Gross Amount計算式がBatch実行順序・Invoice種別によって非等価であり、Legacy Sourceのみからは一意に確定できないため |
| 実装した機能 | なし（Docs Onlyで停止、指示21章の指示どおり） |
| 次のAction | 23.5のCustomer/Ernest確認事項の回答を待つ。回答が得られ、Gross Amount定義が一意に確定できた場合にのみ、次Phaseで「仕入確認」Foundation実装を再検討する |

---

## 25. 変更ファイル

**変更したFrontend/Backend/DB Migration/Legacy Source: 0件。** 本Document（23〜25章追記）と既存3 QA Documentの更新のみ。
