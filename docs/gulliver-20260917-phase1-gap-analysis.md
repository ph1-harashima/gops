# Gulliver社 9/17打合せ Phase 1（リピート発注オンライン化）Gap Analysis

**本ドキュメントの性質**: 本監査はREAD ONLY。Source Code変更・DB Migration作成・commit・push・Production/Legacy Production接続はいずれも実施していない（実績: 変更ファイル0件、`git status`差分なし）。`phasep-gulliver/`（Legacy）は読み取りのみ、変更0件。

**9/17打合せの原資料について**: `docs/`配下に9/17打合せの議事録・スライド等の専用ファイルは存在しない（`docs/9-17-demo-script.md`はデモ台本であり議事録ではない）。本ドキュメントの「2. 9/17 Requirements」は、ユーザーから本タスク依頼時にチャット上で提示された要求文をそのまま一次情報として扱う。これはTechlead（ChatGPT）側で整理された要求がユーザー経由で伝達されたものであり、`G-Sys_mtg_20260826_02.pptx`のような一次資料そのものではない点に留意する。

**監査方法**: 6領域（A/F、B、C、D、E、Phase2/3境界+テスト棚卸し）を並列調査し、実ソース（`backend/src/main/java/`、`frontend/src/`、`phasep-gulliver/`、DB Migration）を直接確認した。既存Phase 7〜9の実装記録ドキュメント（`docs/*.md`、31件）を出発点として使用したが、いずれも実ソースで裏取りした（Phase 8時点のドキュメントはPhase 9の変更を反映していないため、鵜呑みにしていない）。

---

## 1. Executive Summary

- **Phase 1（リピート発注オンライン化）の中核ワークフロー（Candidate→Draft→承認→Official PO→メーカー送信→回答→Fulfillment）は既にPrototype/Demoとして実装済み**（Phase 7〜9、Backend Test 499/499、E2E 154 passed）。9/17打合せで挙がった項目の多くは「新規開発」ではなく「既存実装の軽微な拡張」または「要件確定待ち」に分類される。
- **承認画面（Order Detail）には、承認者が数量判断に使えるはずのCurrent Stock / Sales Qty / Lead Time / Arrival予定が一切表示されていない**。これらは発注担当者側（Candidate List / Draft画面）には既に表示されており、データ自体もLegacyから取得可能（Legacy Fact）なため、追加は軽微な改修（B）で済む。
- **発注数量ロジック（Recommended Qty）に国内/海外・Brand別の分岐は実ソース上一切存在しない**（Fact）。9/17での「国内/海外で考え方が異なるかもしれない」という発言は現時点では顧客発言のみであり、具体的な要件が不明なため、新Business Logicとして実装計画に含めない（D: Requirement Confirmation）。
- **Official PO Excel生成・PO番号確定・Import Folder連携・Import ConfirmationはPhase 9-A〜9-Cで実装済み（A）。PDF生成は実装ゼロ（C、要件確認が前提）。File Naming（現状: `order-{orderId}-rev{revisionNo}-{timestamp}.xlsx`）にはCompany/Supplier/Brand/人間可読な日付が含まれておらず、テンプレート変更のみで対応可能な軽微改修（B）**だが、「Company」が何を指すかは未確定（D）。
- **Supplier/Brand/Contact（1 Supplier→複数Brand→Brand別担当者/Email）は、データモデル・自動選択ロジック・Admin UIのいずれも既に完全対応済み（A）**。9/17要望に対する追加実装は不要。
- **Supplier Responseによる数量変更後のOfficial PO再発行は、データモデル上は既に可能（新Revisionごとに新しいIntegration Requestを作成できる）が、トリガーは完全に手動。「自動検知・通知」の仕組みは存在しない（C、Business Rule確定後）**。
- **Phase 2（請求・支払Progress Management）、Phase 3（新規Brand/Supplier/SKU登録）のFoundationはいずれも実質ゼロ**（Fact）。既存Fulfillmentは入荷（Stock-In）で止まっており、既存Master（`supplier_contact`/`manufacturer_channel`）は「Legacy既存Codeへの追記」専用設計でありPhase 3（新規Code発行）とは逆方向。
- **工数**: 安全に見積可能な軽微改修（B）合計は約10〜12人日で、想定の32人日/約70万円に対して十分な余裕がある。一方、新規実装候補（C: PDF生成・PO再発行自動化・宛先変更UI等）はいずれもBusiness Rule未確定のため、Phase 1のCommitted Scopeには含めていない。仮にCを全て今回スコープに含めた場合、追加で約25〜35人日以上が必要となり32人日を超過する可能性が高い。**70万円に合わせるための工数調整は行っていない。**

---

## 2. 9/17 Requirements（ユーザー提示、一次資料は`docs/`に未格納）

今回の監査対象として提示された要求は以下（詳細は各章参照）:

- A. 承認画面: SKU/Current Stock/Sales Qty/Lead Time/Recommended Qty/Requested-Order Qty/Arrival予定の確認可否
- B. 発注数量ロジック: 現在の計算式、MS_FORMULA等各要素の役割、国内/海外・Brand別差異の有無、設定可能仕組みの有無
- C. Official PO: PO No./Excel/PDF/File Name/Revision/再発行/Supplier Response後の数量変更との関係/Integration Status/Import Folder連携
- D. Supplier/Brand/Contact: 1 Supplier→複数Brand→Brand別担当者/Emailの表現可否
- E. Supplier Response/Revision: Requested/Confirmed/Difference/Acknowledgement/Agreement/Revisionの関係、Official PO再発行可否
- F. Stock/Sales/Arrival: SKU単位でのCurrent Stock/Sales Qty/Open PO/Arrival予定/Lead Time/Recommended Qtyの表示可否
- Phase 2/3境界: 請求・支払Progress Management、新規Brand/Supplier/SKU登録のFoundation有無

---

## 3. Current Implementation（サマリ）

Phase 7〜9で実装済みの中核機能（詳細は`docs/requirements-coverage-and-remaining-gap-audit.md`、`docs/production-po-workflow-implementation.md`、`docs/production-email-edi-workflow.md`参照、いずれも実ソースで再確認済み）:

| 機能 | 状況 |
|---|---|
| Order Candidate List / Draft作成 / 承認 | 実装済み（Order Detail画面に統合、Phase 9-G） |
| Recommended Qty計算（calc1〜4） | 実装済み（Legacy `OrderQuantityCalculator`の逐語移植） |
| Official PO番号確定・Excel生成・Import Folder投入・Import Confirmation | 実装済み（Phase 9-A〜9-C） |
| Manufacturer Channel（Email/EDI分岐）・実Email送信 | 実装済み（Phase 9-D〜9-E） |
| Supplier Contact / Mail Template / Mail Preview | 実装済み（Phase 7-C3） |
| Supplier Response / Revision / Agreement / Reopen | 実装済み（Phase 7-C5） |
| Stock/Sales/Arrival可視化（SKU Detail等） | 実装済み（Phase 8-C/8-G/8-H） |
| Fulfillment（Ordered→Invoiced Qty→Stock-In） | 実装済み、入荷までで停止（Phase 7-C7A） |

---

## 4. Gap Matrix（横断サマリ）

| # | 項目 | 領域 | 分類 | 根拠章 |
|---|---|---|---|---|
| 1 | Order Detail(承認画面)にCurrent Stock/Sales Qty/Lead Time/Arrival予定を追加表示 | A | **B** | 5章 |
| 2 | Order Detail→SKU Detail遷移リンク追加（代替/補完策） | A | **B** | 5章 |
| 3 | 国内/海外・Brand別の発注数量ロジック差異対応 | B | **D**（内容次第でC） | 6章 |
| 4 | PDF生成機能 | C | **C** | 7章 |
| 5 | File Naming改善（Supplier/Brand/読みやすい日付） | C | **B** | 7章 |
| 6 | File NamingへのCompany表記 | C | **D** | 7章 |
| 7 | File Revision表記の整形（-001形式） | C | **B** | 7章 |
| 8 | Official PO再発行の自動検知・通知 | C/E | **C** | 7・9章 |
| 9 | Official POキャンセル/差し替え明示フロー | C | **D→C** | 7章 |
| 10 | Supplier→Brand別Contact/Emailデータモデル | D | **A** | 8章 |
| 11 | 発注時Contact自動選択 | D | **A** | 8章 |
| 12 | Userによる送信時宛先の都度変更 | D | **D→C** | 8章 |
| 13 | ADMIN CC対象の正式ルール | D | **D** | 8章 |
| 14 | Requested/Confirmed/Difference/Agreement基本ワークフロー | E | **A** | 9章 |
| 15 | Revision跨ぎでのAttention自動解消 | E | **D** | 9章 |
| 16 | Stock/Sales/Arrival表示（SKU Detail画面） | F | **A** | 10章 |
| 17 | 請求・支払Progress Management | Phase2 | **E**（Scope外） | 11章 |
| 18 | 新規Brand/Supplier/SKU登録 | Phase3 | **E**（Scope外） | 11章 |

---

## 5. Approval Screen Gap（A）

承認アクションは独立画面ではなく、Order Detail画面（`frontend/src/features/history/OrderHistoryDetailPage.tsx`、Phase 9-Gで統合）に組み込まれている。ADMIN/OPERATORの違いはボタン表示の出し分けのみ（`isAdmin`判定: 同ファイル184行目）。

### Field Matrix

| Field | 発注担当者: Candidate List | 発注担当者: Draft作成 | 承認者: Order Detail |
|---|---|---|---|
| SKU | ✅ `OrderCandidateResponse.java:13` | ✅ `OrderDraftPage.tsx:348` | ✅ `OrderHistoryDetailLineView.java:12` |
| Current Stock | ✅ `OrderCandidateResponse.java:19` | ✅ `OrderDraftPage.tsx:350,369` | ❌ 列自体が存在しない |
| Sales Qty（月間） | ✅ `OrderCandidateResponse.java:22` | ❌ | ❌ |
| Lead Time | ✅ `OrderCandidateResponse.java:23` | ✅ `OrderDraftPage.tsx:351,370` | ❌ |
| Recommended Qty | ✅ `OrderCandidateResponse.java:24` | ✅ `OrderDraftPage.tsx:352,371` | ✅ `OrderHistoryDetailLineView.java:14` |
| Requested/Order Qty | ✅（候補時点はn/a） | ✅ `OrderDraftPage.tsx:353` | ✅ `OrderHistoryDetailLineView.java:15` |
| Arrival予定 | ❌ | ❌ | ❌（`SkuDetailResponse.java:32`という別画面にのみ存在） |

### 所見

承認者が実際に確認できるのは **SKU / Recommended Qty / Ordered Qty / Confirmed Qty / 納期 / Attentions** のみ（`OrderHistoryDetailResponse.java:11-49`）。Current Stock・Sales Qty・Lead Time・Arrival予定はAPIレスポンスに含まれず、フロントにも表示ロジックがない。Order Detail画面からSKU Detail画面への遷移リンクも存在しない（0件確認）。これは意図的なBusiness Rule変更ではなく、「Order Detailは発注時点のSnapshot（SKU/Qty/納期）のみを見せる」という既存設計の帰結であり、データ自体はLegacy側にリアルタイムで存在するため技術的な取得は容易。

### 分類: **B（Minor Modification）**
既存の`LegacyStockReadRepository`/SKU Detail取得ロジックを再利用してLine Responseに列追加するのみ。新Business Logic・新Legacy接続は不要。代替/補完策として、Order Detail→SKU Detail遷移リンク追加（Phase 8-J同種実装のパターン踏襲）も同じくBで対応可能。

---

## 6. Recommended Qty Logic Gap（B）

### 現在の計算式（Fact）

Legacy原本: `phasep-gulliver/gulliver/src/main/java/jp/ne/glv/utilities/OrderQuantityCalculator.java:44-153`（calc1〜calc4）。Portal側は`backend/src/main/java/com/glv/gsysportal/legacy/calc/OrderQuantityCalculator.java`として逐語移植（`RecommendedQtyCalculator.java:37-40`が直接呼び出し）。

```
calc1 = AT - AR + AT×multiplier(AT閾値: ≤5/≤10/>10で3段階)
calc2 = max(0, calc1 - AT×multiplier)
calc3 = IF((c1-c2)+ΣPO+ΣArr > AT×p1, AT×p2-Σ, c1-c2)
calc4 = (calc3 ≤ threshold) ? 0 : calc3
```
AT=stkStandard、AR=logicalQty。乗数・閾値はMS_FORMULAのFORMULA_11〜14文字列を`FormulaParser`が数式パースして抽出する（固定値ではない）。

### 各要素の役割（Fact）

| 要素 | 役割 | 根拠 |
|---|---|---|
| MS_FORMULA | PK=SKU(Item Code)。**SKU単位**の式設定テーブル、Brand/国キーではない | `MsFormula.java:30-33`、`AllUsersRestController.java:789` |
| StockCalculationHelper | calc1〜4のオーケストレーションのみ。FORMULA未設定時は固定文字列にフォールバック | `StockCalculationHelper.java:14-17,35-76` |
| OrderQuantityCalculator | 数式本体 | 上記 |
| MS_STK.STK_STANDARD | calc1〜3のAT（在庫基準数） | `MsStkRepositoryImpl.java:1783,1903` |
| LOGICAL_QTY | 生カラムでなくSQL算出値（現在庫+PO_QTY+SHIP_QTYの合算） | `MsStkRepositoryImpl.java:1855-1888` |
| SOLD_QTY | 当月販売実績。**計算式には未使用**、表示専用 | — |
| Lead Time | Item Masterから取得されるが**計算式には一切組み込まれていない**（Javadoc「not used in current implementation」） | `StockCalculationHelper.java:25` |

### 国内/海外差異・Brand別差異（Fact: いずれも存在しない）

MS_FORMULAはSKU単位キーのみで、国・地域・Brandを表す列は皆無（grep徹底確認）。Legacy/Portal双方のcalc1-4に`country`/`region`/`brand`等の分岐は一切ない。

### 計算式設定可能仕組み

データモデル上はSKU単位で式（FORMULA_11〜14文字列）を個別設定可能（`MsFormulaRepository`）。ただし対話的な編集画面は本監査範囲では発見できず（Excel Export用の読み取りのみ確認、`UsersController.java:1203,1350`）。**Unknown**: 編集UIの存在有無（Legacy側、バッチ経由の一括投入経路のみ確認済み）。

### Fact / Unknown / Future Requirement

| 区分 | 内容 |
|---|---|
| Fact | 計算式はSKU単位のMS_FORMULA文字列＋固定閾値ロジック。Portalは逐語移植。国/地域・Brand分岐は不在。Lead Timeは取得のみで計算未使用。 |
| Unknown | MS_FORMULA編集UIの存在有無（Legacy側）。9/17議事録原本が`docs/`未格納。 |
| Future Requirement（顧客発言のみ、推測禁止） | 「国内/海外で発注数量の考え方が異なる」。現ソースに対応する区分が無いため、新Business Logicとして実装計画に含めない。 |

### 分類: **D（Requirement Confirmation）**
現状Fact不在のため断定不可。「国内/海外差異の具体的内容（閾値？計算式自体？対象SKU範囲？）」の確認が必須。確認後の要件次第でC（新規実装）へ移行しうるが、現時点で規模見積は不可能（3〜10人日以上まで振れ幅あり、Legacy変更を要する場合はREAD ONLY原則との調整も必要）。

---

## 7. Official PO / Revision Gap（C）

**PO No. / Document Revision / File Revision は独立した3概念として扱う（ユーザー指示どおり混同しない）。**

### 現状（Fact）

| 項目 | 現状 |
|---|---|
| PO No. | Staff（ADMIN限定）が手入力。Validationは空文字禁止・30文字以内のみ、ID Code構造検証なし（`OfficialPoIntegrationService.confirmOfficialPoNumber`）。SUBMITTED以降はLock。 |
| Excel生成 | 実装済み（`OfficialPoExcelGenerationService`、Legacy Batch定数と一致確認済み）。 |
| PDF生成 | **実装ゼロ**（backend全体で"pdf"grep 0件）。`attachment_type`列がCHECK制約なしの自由文字列のため、将来`OFFICIAL_PO_PDF`を追加してもSchema変更は不要という「受け皿」のみ存在。 |
| File Naming | `"order-" + orderId + "-rev" + revisionNo + "-" + timestamp(yyyyMMddHHmmss) + ".xlsx"`（`OfficialPoExcelStorageService.java:56`、`OfficialPoIntegrationService.java:337`の2箇所）。**Company/Supplier/Brand/人間可読な日付はいずれも含まれない**。 |
| Document Revision | `OfficialPoIntegrationRequest.revisionNo` = `PortalOrder.currentRevisionNo`と同一概念。Order再承認のたびに増分する正式な文書版数（実装済み）。 |
| File Revision | **独立した概念としては存在しない**。File名の`-rev{N}`はDocument Revisionの値をそのまま転記しているだけで、「同一Document Revisionを複数回再生成した場合の版数」を区別する仕組みは無い。 |
| 再発行 | 技術的Retry（FAILED→再配置、Excel再生成なし）は実装済み。**内容変更を伴う正式な再発行・旧PO無効化フローは存在しない**。 |
| Supplier Response後の数量変更との関係 | Orderが再承認されRevisionが上がれば新しいIntegration Requestが機構的に作成可能（`UNIQUE(portal_order_id, revision_no)`）。ただし自動トリガーは無く、ADMINの手動操作起点。 |
| Integration Status | `PENDING→GENERATED→SUBMITTED→CONFIRMED`、`FAILED`はGENERATED/SUBMITTEDいずれからも到達可（`OfficialPoIntegrationRequest.java`）。`NOT_YET_IMPORTED`/`MISMATCH`はConfirm時点のレスポンス理由コード（永続Statusではない）。 |
| Import Folder / Import Confirmation | Port+Adapter方式、実装済み（Local/Demo/Test）。Production Adapterは`@Profile("production")`スタブ、SafetyGuardにより到達不可。手動・都度確認方式（バックグラウンドPollingなし、意図的）。 |

### 9/17項目とのGap Matrix

| 9/17議論項目 | 現状 | 分類 |
|---|---|---|
| Excel生成 | 実装済み | **A** |
| PDF生成 | 実装ゼロ | **C**（レイアウト要件確認が先） |
| File NamingへのSupplier/Brand名/読みやすい日付追加 | 未対応だが必要フィールドは全て既存取得可能、テンプレート2箇所の変更のみ | **B** |
| File NamingへのCompany表記 | フィールド自体が存在しない、「Company」の定義が未確認 | **D** |
| File Revision表記（-001形式） | Document Revisionをそのまま整形すれば軽微対応可 | **B** |
| 再発行（PO内容修正後の差し替え・旧PO無効化） | Revision機構はあるが明示フローがない | **D→C** |
| Official PO再発行の自動検知・通知 | 完全手動、検知ロジック自体が存在しない | **C** |

### Fact / Unknown
- **Fact**: 上記全項目、実ソース確認済み。
- **Unknown**: (a) 9/17でいう「Company」が具体的に何を指すか（自社名／グループ会社名／請求元法人名）。(b) File Naming規則の「正解」はGulliver社のImport Folder運用担当者の識別基準に依存し、Source断定不可。(c) PDF要求がExcelと同一内容の別形式か、別レイアウトかは未確認。

---

## 8. Supplier / Brand / Contact Gap（D）

### テーブル構造（Fact）

- `supplier_contact`（`V10__supplier_contact_mail_template_foundation.sql:13-31`）: `supplier_code`, `brand_code`（**NULLABLE**）, `contact_name`, `email`, `contact_type`(TO/CC), `is_primary`, `is_active`等。Uniqueness: `(supplier_code, COALESCE(brand_code,''), lower(email))`をActive行間でのみ一意（V10:39-41）。
- `manufacturer_channel`（`V21__manufacturer_channel_and_edi_status.sql:25-40`）: `supplier_code`, `brand_code`（NULLABLE）, `channel`(EMAIL/EDI)。`supplier_contact`と同一形状・同一Uniqueness方針。

### 6つの問いへの回答（Fact）

1. **「1 Supplier→複数Brand→Brand別担当者/Email」は現行モデルで表現可能** — `brand_code`が既存nullable列であり対応済み。
2. **Supplier共通Contact（Brand指定なし）も表現可能** — `brand_code IS NULL`行として登録。
3. **Brand別Contactも表現可能** — 同上機構。
4. **発注時自動選択**: `SupplierContactResolutionService`/`ManufacturerChannelResolutionService`いずれも「Brand指定行優先→Supplier単独行→null（自動推定しない）」で統一。
5. **Userによる送信先の都度上書きは不可**（Fact）: `EmailSendService.send(orderId, performedBy)`は受信者パラメータを受け取らず、常に`MailPreviewService`の解決結果をそのまま使用。送信先は常にMaster登録値固定。
6. **Mail Preview = 実送信**: 完全同一ロジック（`EmailSendService.java:114`が`mailPreviewService.preview()`を直接呼び出し）。専用Compose UIは存在しない。

Admin UI（`frontend/src/features/admin/SupplierContactPage.tsx`）もBrand Code入力欄を持ち、上記モデルをそのまま画面化済み。

### Gap整理

| 項目 | 分類 |
|---|---|
| Supplier→Brand別Contact/Emailデータモデル | **A** |
| 発注時自動選択（Brand優先解決） | **A** |
| Admin UIでのBrand別Contact登録 | **A** |
| Userによる送信時宛先の都度上書き | **D→C**（要件が確定すれば新規実装） |
| ADMIN複数時のCC対象の正式ルール | **D**（既存の暫定実装＝全Active ADMIN、未確定のまま） |
| Supplier担当者のBrand単位/全社単位の正式割当方針 | **D** |

### Fact / Unknown
- **Fact**: 上記1-6すべて実ソース・Migration確認済み。
- **Unknown**: (a) 実際にどのSupplier/BrandがContact/Channelを実データとして持つか（両Masterとも初期データは空）、(b) 「担当者が送信直前に宛先を確認・変更したい」という運用要望の有無。

---

## 9. Supplier Response Gap（E）

### 現在の関係（実装済み、Fact）

- **Revision**: `portal_order_revision`/`portal_order_revision_detail`（V11）。実際にSendされた瞬間に確定するSnapshot。`portal_order.current_revision_no`が現在値を保持。
- **Response**: `supplier_response`は`UNIQUE(portal_order_id, order_revision_id)`— 1 Revisionにつき1 Responseの1:1関係。
- **Requested/Confirmed/Difference**: `requestedQty`＝Revision Snapshot、`confirmedQty`＝Response側。NULL(未回答)と0(明示ゼロ回答)を全レイヤーで区別。Differenceは永続化せず都度計算。
- **Acknowledgement**: Attention機構（`QUANTITY_CHANGED`/`DELIVERY_CHANGED`/`SUPPLY_STATUS_CHANGED`等）がユーザー確認までACTIVEのまま残る。
- **Agreement/Reopen**: `SUPPLIER_CONFIRMED→AGREED`（ADMIN限定、未確認Attentionがあると409、`forceAgree`で上書き可）。Reopenで`AGREED→SUPPLIER_CONFIRMED`に戻せる（非破壊、`reopened_by/at/reason`を別列追加）。

### Official PO再発行が現Data Modelで扱えるか（結論: 技術的には可能、トリガーは完全手動）

`official_po_integration_request`は`UNIQUE(portal_order_id, revision_no)`（`V9__official_po_integration_foundation.sql:53`）— **1 Order=1 Requestではなく、Revisionごとに複数行が作成できる構造**。`targetRevisionNo()`（Phase 9-Aで実際のRevision連動に切替済み）が新規Revision番号を算出するため、「修正版作成→再承認→再Send」のサイクルを一巡すれば新しいOfficial PO Integration Requestが作成できる。ただしこの作成はADMINの明示的なAPI呼び出しでのみ発生し、**「Supplier Responseで数量/納期が変わったら自動的にOfficial POを再発行すべき」という検知・強制・通知の仕組みは一切存在しない**（`OrderAttention`にRevision参照フィールドなし）。

### Gap整理

| # | 項目 | 分類 |
|---|---|---|
| 1 | 基本ワークフロー（Requested/Confirmed/Difference/Revision/Agreement/Reopen） | **A** |
| 2 | 修正版作成→再承認→再SendによるOfficial PO再発行（データモデル） | **A** |
| 3 | Supplier Response変更を契機とした自動検知・通知・強制 | **C** |
| 4 | Revision跨ぎでAttentionが自動解消されない問題 | **D**（一部C） |
| 5 | Supply Status正式値定義、差異受入権限の正式ルール、Correction/Revision境界線 | **D** |
| 6 | 修正版のPO番号採番規則 | **D** |

### Fact / Unknown
- **Fact**: Revision/Response/Agreement/Reopenの状態遷移、Official PO Integration RequestのRevision別複数行対応。
- **Unknown**: 「必ず再発行すべきか」「誤差許容範囲」「旧Official PO Excelの無効化要否」はBusiness Rule未確定のため判断・提案していない。

---

## 10. Stock / Sales / Arrival Gap（F）

最も網羅的なのは`SkuDetailResponse.java:20-41`（currentStock/safetyStock/openPo/openArrival/monthlySales/leadTime/recommendedQty全て保持）。

| Field | 区分 | 根拠 |
|---|---|---|
| Current Stock / Open PO / Open Arrival / Sales Qty / Stock Standard | **Legacy Fact** | `RecommendedQtyReadQuery.sql:58-71`（`ms_stk`集約行から直接SELECT） |
| Lead Time | **Legacy Fact** | `RecommendedQtyReadQuery.sql:55`（`ms_item.lead_time`） |
| Recommended Qty（calc4） | **Portal独自算出** | `RecommendedQtyCalculator.java:30-50`（Legacy生カラムではなく都度計算） |

分類: **A（Already Implemented）**。SKU Detail画面で全項目表示可能。ただしOrder Detail（承認画面）には反映されていない（5章参照、Bで対応）。入荷確認・倉庫在庫画面自体の列構成は今回のFork担当外のため**Unknown**。

---

## 11. Phase 2 / Phase 3 Boundary

### Phase 2（発注→請求→支払Progress Management）— Foundation: 実質ゼロ

- Fulfillment機能は「入荷（Stock-In）」までで明確に停止。`FulfillmentLineView.java:10-22`の`invoicedQty`は**数量としてのInvoice Qty**であり請求金額ではない。
- backend全体で"payment"/"支払"/"請求"のgrep結果は、Official POの`PaymentTerm`（発注条件文言）以外ゼロ件。
- Legacy側の請求金額計算式自体が非確定（`docs/legacy-invoice-purchase-sales-gross-profit-reverse-engineering.md`、Phase 8-E Gate STOP）という既知の事実もあり、技術的にも未着手が妥当。
- **今回実装しない。Foundation報告のみ。**

### Phase 3（新規Brand/Supplier/SKU登録）— Foundation: 実質ゼロ

- Controller一覧（22本）に新規Brand/Supplier/SKU作成用CRUD APIは存在しない。
- 既存`supplier_contact`/`manufacturer_channel`は「Legacy既存Codeに対する追記専用」設計（`supplier_code`/`brand_code`はLegacy READ ONLYで検証される、`V10:6`のコメント明記）— **Phase 3（新規Code発行）とは逆方向の設計思想**であり、単純な拡張はできない。
- SKU/Item新規登録、Legacyへの新商品取込導線も存在しない。
- **今回実装しない。Foundation報告のみ。**

---

## 12. Estimated Effort

**方針**: B（Minor Modification）のみを今回のCommitted Scope候補として積み上げる。C（New Implementation）はBusiness Rule未確定（D）が前提のため、参考レンジとして別掲し、32人日には含めない。70万円に合わせるための調整は行っていない。

### 12.1 B項目（安全に見積可能、Committed Scope候補）

| # | 項目 | Design | Backend | Frontend | DB Migration | Unit Test | Integration Test | E2E | Documentation | Release Prep | 小計 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| B-1 | Order Detail(承認画面)にCurrent Stock/Sales Qty/Lead Time/Arrival予定追加 | 0.5 | 1.0 | 1.0 | 0 | 0.5 | 1.0 | 1.0 | 0.5 | 0.5 | **6.0** |
| B-2 | Order Detail→SKU Detail遷移リンク追加（B-1の代替/補完） | 0.25 | 0 | 0.5 | 0 | 0 | 0 | 0.5 | 0.25 | 0 | **1.5** |
| B-3 | Official PO File Naming改善（Supplier/Brand/読みやすい日付） | 0.5 | 1.0 | 0 | 0 | 0.5 | 0.5 | 0.5 | 0.5 | 0.25 | **3.75** |
| B-4 | File Revision表記の整形（-001形式） | 0.25 | 0.5 | 0 | 0 | 0.25 | 0.25 | 0 | 0 | 0 | **1.25** |
| | **合計（B-1のみ採用、B-2は代替のため除外）** | | | | | | | | | | **約11人日** |

B-1とB-2は代替/補完関係（B-2はArrival予定のみ簡易対応する場合の軽量案）。両方併用する場合は約12.5人日。B-3とB-4は同時実施でファイル名テンプレート変更を1回にまとめられるため、実施時は合計4.5人日から0.5人日程度の圧縮余地あり。

**B項目合計: 約10〜12人日**（32人日中、十分な余裕）。

### 12.2 C項目（参考レンジのみ、Committed Scope外・要件確定が前提）

| # | 項目 | 参考人日レンジ | 前提となるD項目 |
|---|---|---|---|
| C-1 | PDF生成機能 | 8〜18人日（レイアウト次第で大幅変動） | PDFレイアウト要件、Excel同一内容か否か |
| C-2 | Official PO再発行の自動検知・通知 | 約10人日 | 再発行を要する条件の業務ルール |
| C-3 | Official POキャンセル/差し替え明示フロー | 約6〜8人日 | 旧PO無効化の業務ルール |
| C-4 | Userによる送信時宛先の都度変更UI | 約4.5人日 | 宛先変更要否の要件 |
| C-5 | 国内/海外発注数量差異ロジック | 見積不能（3〜10人日以上、Legacy変更要なら別途協議） | 差異の具体的内容（6章） |

**C項目を全て含めた場合の追加見積: 約28.5〜46.5人日以上**（見積不能項目除く）。

### 12.3 32人日以内で可能か

- **B項目のみ（今回安全に着手可能な範囲）: 約10〜12人日で32人日以内に十分収まる。**
- **C項目を1つでもCommitted Scopeに含める場合、32人日/約70万円を超過する可能性が高い。** 特にPDF生成・再発行自動化は要件確定前のレンジ推定であり、要件確定後に再見積が必要。
- 現時点の判定: **「Phase 1 = 中核ワークフローの軽微改修＋要件確認」と定義するなら32人日以内で可能。「Phase 1 = 9/17で挙がった新機能（PDF・自動再発行・宛先変更・越境ロジック）まで含む」と定義するなら32人日を超過する。** スコープの再定義をTechlead/顧客と行うことを推奨する。

---

## 13. Test Impact

既存テスト（Backend 60ファイル、E2E 21ファイル、Frontend Unit 3ファイル＝すべてi18n関連で業務ロジックComponent単体テストはゼロ件）の領域別内訳:

| 領域 | Backend Test | E2E Test |
|---|---|---|
| 承認画面 | 1（`ApprovalWorkflowApiTest`） | 1（`role-approval-workflow.spec.ts`） |
| 発注数量ロジック | 2（`OrderQuantityCalculatorGoldenTest`等） | 0（専用specなし） |
| Official PO | 9 | 4 |
| Supplier/Brand/Contact | 9 | 2 |
| Supplier Response/Revision | 9 | 4 |
| Stock/Sales/Arrival | 7 | 4 |
| 汎用/横断 | 23 | 9 |

### 各改修候補への影響

- **B-1（承認画面Field追加）**: `OrderHistoryDetailResponse`/`OrderHistoryDetailLineView`変更のため、既存Backend Integration Test（Order Historyカテゴリ）への影響あり。**新規E2E必須**（`role-approval-workflow.spec.ts`拡張、専用E2Eが薄い領域のため）。Regression Risk: 中（Order Detail画面は共有コンポーネント）。
- **B-2（SKU Detail遷移リンク）**: 新規Backend変更なし。E2Eに遷移確認シナリオを1件追加すれば十分。Regression Risk: 低。
- **B-3/B-4（File Naming）**: `OfficialPoExcelGeneratorContractTest`等、Official PO領域は既存Test密度が高い（9件）ため、ファイル名アサーションの機械的な追従修正が発生する。`official-po-integration.spec.ts`のファイル名検証部分も要更新。Regression Risk: 中（既存Contract Testが厳密にファイル名を検証している可能性）。
- **C項目全般**: Official PO / Supplier Response / Supplier-Brand-Contactはいずれも既存Backend Integration Test密度が高く（各9件前後）、スキーマ変更を伴う場合は影響半径が広い。Frontend側はComponent単体テストが存在せず全てE2Eのため、小さなUI変更でもフルE2E実行（約15分、Phase 9-H実績）による確認が必須。

---

## 14. Risks / Unknowns

| # | リスク/Unknown | 影響 |
|---|---|---|
| 1 | 国内/海外発注数量差異の具体的内容が不明（6章） | Phase 1の最大のRequirement Risk。内容次第でC-5の規模が3〜10人日以上に変動し、Legacy変更を要する可能性もある |
| 2 | PDFレイアウト要件が不明（7章） | C-1の規模が8〜18人日で変動 |
| 3 | 「Company」がFile Naming/Excelで何を指すか不明（7章） | B項目かD項目か未確定のまま |
| 4 | MS_FORMULA編集UIの存在有無（Legacy側、6章） | Unknown。存在すれば要件確認の選択肢が広がる |
| 5 | 入荷確認・倉庫在庫画面自体の列構成が今回未調査（10章） | F領域の一部が未確認のまま残る |
| 6 | 承認者へのField追加がOrder Detail共有コンポーネントに影響（13章） | Regression Risk中、E2E追加必須 |
| 7 | Revision跨ぎでAttentionが自動解消されない既知Gap（9章） | Business Rule確定まで放置される設計上の穴 |

**最大のTechnical Risk**: Order Detail画面がPhase 9-Gで統合済みの共有コンポーネントであるため、承認者向けField追加が発注担当者向け表示や他の状態パネル（"at a glance"）に意図せず影響するリスク。**最大のRequirement Risk**: 国内/海外発注数量差異（項目1）— 内容が「表示上のラベル分け」程度なら軽微だが、「計算式自体の分岐」ならMS_FORMULA拡張・Legacy協議まで波及し、READ ONLY原則との調整が必要になる。

---

## 15. Recommended Implementation Order

Business Rule未確定のC項目を除き、B項目のみを対象に依存関係の少ない順で提案する（**今回は実装しない、順序の提案のみ**）:

1. **B-2（Order Detail→SKU Detail遷移リンク）**: 最小コスト（1.5人日）、Backend変更なし、既存Navigationパターンの横展開のみ。着手障壁が最も低い。
2. **B-1（Order Detail Field追加）**: B-2と独立に着手可能。承認者のGap解消として最もCustomer Valueが高い。
3. **B-3/B-4（Official PO File Naming改善）**: 「Company」の定義（D項目）が未確定のため、先にD項目の確認を挟むことを推奨。確認後にB-3/B-4をまとめて実施。
4. **D項目の確認（Gulliver/Techlead）**: 国内/海外発注数量差異、PDF要件、Company定義、再発行/キャンセルの正式ルール、宛先変更要否、ADMIN CC正式ルール — これらの回答が、C項目群の実装可否・規模を最終的に決定する。
5. **C項目**: D項目の回答後、個別に規模再見積の上で着手判断。

---

以上、監査のみで完了。実装・DB変更・commit・push・Legacy変更・Production接続はいずれも0件。
