# G-SYS Online Ordering Modernization
## 9/17 Prototype 要件・設計方針書

更新日：2026-08-26
ステータス：Draft
対象：Gulliver G-SYS
目的：2026年9月17日 顧客レビュー用 Prototype

---

# 0. 文書管理ルール

本書では、要件の状態を以下のラベルで明示する。

| ラベル | 意味 |
|---|---|
| `[CONFIRMED]` | これまでの合意により確定している事項 |
| `[PROTOTYPE DECISION]` | 9/17 Prototypeとして採用する設計判断 |
| `[TBD - SOURCE REVIEW]` | 実ソースコードおよびサンプルデータ確認後に確定する事項 |
| `[TBD - CUSTOMER REVIEW]` | 9/17顧客レビュー等で確認後に確定する事項 |

既存記載と最新決定事項が矛盾する場合は、既存内容を削除せず、最新決定事項を優先して統合する。

---

# 1. 本書の目的

本書は、G-SYSに対して検討している
「Online Ordering / Operations Portal」の要件および設計方針を整理するものである。

2026年9月17日の顧客打合せでは、
HTML等による画面モックではなく、
実際に動作するプログラムを提示する。

ただし、9月17日時点で本番システムを完成させることを目的とはしない。

目的は、

- 実際のG-SYSソースコード
- 実際のG-SYS業務ロジック
- 実際のサンプルデータ

を利用した動作Prototypeを構築し、

顧客に実際の操作イメージを提示しながら、
本開発に必要な業務要件を確認・確定することである。

---

# 2. 基本方針

## 2.1 Legacy G-SYS

現行G-SYSには可能な限り変更を加えない。

現行技術：

- Java 8
- Spring Boot 1.5.15.RELEASE
- Spring MVC
- Spring Data JPA / Hibernate
- Spring Security
- Spring Batch
- Thymeleaf
- jQuery
- Bootstrap
- Handsontable
- MySQL
- Excel / CSV
- SFTP
- Selenium / ChromeDriver

9/17 Prototypeのために、

- Java Upgrade
- Spring Boot Upgrade
- DB再設計
- Batch再構築
- Logizero連携変更
- Tempostar連携変更

等は行わない。

---

## 2.2 新規機能

新しいOnline Ordering機能は、
既存G-SYS内部へ追加するのではなく、
新しいOperations Portalとして構築する。

新規部分については、
最初からModern Stackを利用する。

暫定構成：

Frontend
- React
- TypeScript

Backend
- Java 21
- Spring Boot 3.x

Interface
- REST / JSON

Database
- Existing G-SYS / Sample Databaseを参照元として利用
- 新Portal固有データはPrototype専用Databaseへ保存

既存G-SYSとの接続方法については、
実ソース確認後に確定する。

`[PROTOTYPE DECISION]` PrototypeではLegacy G-SYS / Sample Databaseを原則READ ONLYとし、新Portalから発生する更新データを既存MySQLへ書き込まない。

---

# 3. Architecture方針

## 3.1 基本構成

9/17 Prototype：

New Operations Portal
↓
New Service API
↓
Legacy Adapter
↓
Existing G-SYS / Existing Data
↓
MySQL

原則：

Legacy = 可能な限り変更しない

New = Modern Stack

FrontendからDBを直接更新しない

新Portal独自ロジックから
G-SYSの業務整合性を破壊する更新を行わない

将来的には、

Portal
↓
API Gateway
↓
Domain / Service API
↓
G-SYS Business Logic / Modernized Services

へ発展可能な構造とする。

## 3.2 Prototype専用Database

`[PROTOTYPE DECISION]` 9/17 Prototypeでは、新Operations Portalから既存G-SYSの本番業務データを更新せず、Prototype専用Databaseを使用する。

基本構成：

```text
Existing G-SYS / Sample Database
        │
        │ READ
        ▼
New Operations Portal / New Service API
        │
        │ READ / WRITE
        ▼
Prototype専用Database
```

Prototype専用Databaseには、新Portalで発生する以下のデータを保存する。

- Order Draft
- Draft Detail
- Portal Workflow Status
- Supplier Response
- Confirmed Quantity
- Confirmed Delivery Date
- Attention Flag
- Change History / Audit Trail
- Prototype上のPO情報
- その他、新Portal固有データ

`[PROTOTYPE DECISION]` Prototype専用Databaseには、Legacy G-SYSに既に存在する商品、在庫、Brand、Supplier等のMasterを新たな正本として複製しない。Legacy / Sample G-SYSをSystem of Record、新PortalのPrototype専用DatabaseをWorkflow / Operation Stateの保存先として扱う。

発注時点の判断根拠や表示再現に必要な名称、在庫、販売実績等は、Masterの複製ではなくOrder Detail等のSnapshotとして保存する。

既存G-SYS / Sample Databaseからは以下の実サンプルデータを取得する。

- Brand
- Supplier
- SKU
- Item
- Stock
- Safety Stock
- Open PO
- Recent Sales
- Lead Time
- Existing Formula / Recommendationに必要な情報
- Arrival等

`[CONFIRMED]` PrototypeではLegacy G-SYS側を原則READ ONLYとし、既存`TrPo` / `TrPoDtl`等への正式な書き込みは行わない。

`[TBD - CUSTOMER REVIEW]` 9/17の顧客レビュー後、本開発要件が確定した段階で、「新PortalのOrderを、どのタイミングで既存G-SYSの正式POへ登録するか」を本番Architectureとして設計する。

## 3.3 データ更新境界

| データ領域 | READ | WRITE |
|---|---|---|
| Legacy / Sample G-SYS | Item、SKU、Brand、Supplier、Stock、Safety Stock、Open PO、Sales、Lead Time、Arrival、Formula関連データ | 原則なし |
| Prototype Database | Draft、Draft Detail、Workflow、Prototype PO、Supplier Response、Confirmed Qty、Confirmed Delivery、Attention、History / Audit | 同左 |

`[PROTOTYPE DECISION]` 上記境界を9/17 Prototypeのデータ更新原則とする。

---

# 4. AIについて

9/17 PrototypeではAI機能を実装対象外とする。

ただし、将来的にAI Agent等を追加できるよう、
新規機能はAPI経由で業務機能へアクセスする構造を基本とする。

AIを導入する場合でも、

AI
↓
Proposal
↓
Human Approval
↓
System Execution

を原則とする。

今回のPrototypeではAI機能そのものは実装しない。

---

# 5. 今回解決したい業務課題

現在の発注業務では、

G-SYS
↓
Excel / CSV Download
↓
担当者による確認・編集
↓
発注書作成
↓
メーカーへメール
↓
メーカー回答
↓
数量・納期変更
↓
関連情報修正
↓
必要に応じてUpload

という処理が存在する。

主な課題：

- Download / Uploadの反復
- Excel上での業務処理
- 手作業による転記
- 発注判断の属人性
- 数量変更の多重反映
- 納期変更の多重反映
- 欠品・長期欠品等の確認負荷
- データ更新タイミングの把握困難
- ブランド・発注件数増加に伴う作業量増大

---

# 6. TO-BE業務フロー

今回のOnline Orderingでは、

ブランド選択
↓
発注候補SKU一覧
↓
SKU詳細確認
↓
推奨発注数量確認
↓
担当者による発注数量変更
↓
Order Draft
↓
PO Preview
↓
メーカーへの発注
↓
メーカー回答待ち
↓
確定数量・納期登録
↓
変更履歴
↓
入荷
↓
完了

というオンライン業務を目標とする。

---

# 7. 発注数量計算

## 方針

新しい発注計算式をPrototype側で独自に作らない。

現行G-SYSに存在する発注Recommendationロジックを、
可能な限りそのまま利用する。

現行仕様上、以下の関連資産が確認されている。

- MsFormula
- Formula
- OrderQuantityCalculator
- StockCalculationHelper
- Order Judgement
- Stock Standard
- Safety Stock
- Lead Time
- Open Quantity

実ソースコード受領後、
実際の計算経路を確認する。

## 9/17での顧客確認

9/17では、

「現在のG-SYSで利用している発注計算式を
新画面でも利用する考え方でよいか」

を顧客へ確認する。

計算式変更の要望があれば、
本開発要件として整理する。

---

# 8. データ方針

`[CONFIRMED]`

Prototypeでダミーデータは原則使用しない。

実ソースコードおよび
G-SYSのサンプルデータを利用する。

最低限取得するデータ：

- Brand
- Supplier
- SKU
- Item Name
- Current Stock
- Safety Stock
- Open PO
- Recent Sales
- Lead Time
- Recommended Order Quantity
- PO
- Arrival
- Status

Recent Salesについては、
実ソースから取得元・保持期間・粒度を確認する。

`[PROTOTYPE DECISION]` Legacy / Sample G-SYSから取得した参照データと、新Portalで作成・更新するデータを分離する。参照データはLegacy / Sample G-SYSからREADし、Draft、Workflow、Prototype PO、Supplier Response、Attention、Audit Trail等はPrototype専用DatabaseでREAD / WRITEする。

---

# 9. Prototype画面構成

9/17では以下の7画面を基本とする。

1. Dashboard
2. Brand / Order Candidate List
3. SKU Detail
4. Order Draft
5. PO Preview
6. Order History
7. Supplier Response / Order Update

---

# 10. Dashboard

目的：

「今日、何を発注し、何を確認し、
何に対応する必要があるか」

を一画面で把握する。

## KPI

- Order Candidates
- Out of Stock
- Long-term Out of Stock
- Draft Orders
- Awaiting Supplier
- Changed / Attention

## Brand一覧

表示例：

| Brand | Candidates | OOS | Draft | Awaiting Supplier | Attention |
|---|---:|---:|---:|---:|---:|
| Brand A | 28 | 4 | 1 | 3 | 2 |
| Brand B | 12 | 1 | 0 | 2 | 0 |

Brandを選択すると、
発注候補SKU一覧へ遷移する。

## Data Freshness

可能であれば、

- Inventory Updated
- Sales Data Through

を表示する。

古いデータの場合は警告する。

具体的な更新日時取得方法は要ソース確認。

## Action / Operation Cockpit

`[PROTOTYPE DECISION]` Dashboardは分析画面ではなく、「今日、どのブランド・商品について何を処理すべきか」を把握し、そのまま業務へ遷移するAction / Operation Cockpitとして設計する。

各KPIは単なる件数表示ではなく、クリックにより以下へ遷移する。

| KPI | 遷移先・条件 |
|---|---|
| 発注候補 | Order Candidate List |
| 欠品 | `OUT_OF_STOCK`でFilterしたOrder Candidate List |
| 長期欠品 | `LONG_TERM_OUT_OF_STOCK`でFilterしたOrder Candidate List |
| 発注作成中 | `DRAFT`のOrder一覧 |
| メーカー回答待ち | `AWAITING_SUPPLIER`のOrder一覧 |
| 要確認 | Active Attentionを持つOrder / SKU一覧 |

Brand別一覧にはBrand、Candidates、OOS、Long-term OOS、Draft、Awaiting Supplier、Attentionを表示する。Brand名または件数から、Brandと条件を引き継いで次画面へ遷移可能とする。

Data Freshnessとして在庫データ更新日時と販売実績更新日を表示可能とし、古い場合は`DATA_OUTDATED`をAttentionとして扱う。正確な更新日時取得元は`[TBD - SOURCE REVIEW]`とする。

## 要確認一覧

Dashboard下部に、数量変更あり、納期変更あり、一部回答、データ更新要確認等の優先Attentionを数件表示し、該当Order / SKUへ直接遷移可能とする。確認済みAttentionはActive件数から除外可能とする。

## Dashboardで実装しないもの

9/17 Scopeでは以下を追加しない。

- 売上分析
- 粗利分析
- 在庫回転率
- 高度なAnalytics
- AI分析
- Supplier評価

---

# 11. Order Candidate List

表示項目：

| 項目 | 編集 |
|---|:---:|
| Select | ○ |
| SKU | × |
| Item Name | × |
| Brand | × |
| Current Stock | × |
| Safety Stock | × |
| Open PO | × |
| Recent Sales | × |
| Lead Time | × |
| Recommended Qty | × |
| Order Qty | ×（Order Draftで編集） |
| Item Status | × |
| Attention | × |

Recommended Qty：

既存G-SYS Formulaによる計算値。

Order Qty：

Order Candidate Listでは編集せず、Order Draftで担当者が変更可能。

複数SKUを選択して、
Order Draftへ進める。

## 画面目的・Filter

`[PROTOTYPE DECISION]` 本画面の目的は「何を、なぜ、いくつ発注すべきか」を判断し、発注対象SKUを選択することとする。

Filter：

- Brand
- Supplier
- Item Status
- Keyword（SKU / Item Name）
- Attentionのみ

Dashboardから遷移した場合は、Dashboard側の条件を引き継ぐ。

## 編集・Draft作成

`[PROTOTYPE DECISION]` Order Candidate ListではOrder Qtyを原則として直接編集せず、候補確認、判断、選択までを責務とする。正式なOrder Qty編集はOrder Draftで行う。

複数SKUを選択して「発注Draftを作成」を実行し、Create Draft時の初期値を`Order Qty = Recommended Qty`とする。

原則として1つのOrder Draftには同一Supplierの商品をまとめる。異なるSupplierの商品を同時選択した場合は、暫定的にSupplier単位へDraftを分割する。1 PO / Draftに許容されるSupplier単位は`[TBD - SOURCE REVIEW]`とする。

SKU / Item NameからSKU Detailへ遷移可能とし、戻った場合は可能な限りFilterと選択状態を維持する。

Recommended Qtyまたは詳細表示から算出根拠を確認可能とする方向とするが、実際のLegacy Formulaで使用するデータのみを表示し、未使用項目を計算根拠として表示しない。表示可能な根拠項目は`[TBD - SOURCE REVIEW]`とする。

---

# 12. SKU Detail

## Product

- SKU
- Item Name
- Brand
- Supplier

## Inventory

- Current Stock
- Safety Stock
- Open PO
- Arrival Qty

## Sales / Ordering

- Recent Sales
- Lead Time
- Recommended Qty

## History / Timeline

- 過去発注
- 現在の発注
- 入荷予定

販売実績データが取得可能な場合、
月別販売推移を表示する。

## UI/UX詳細

`[PROTOTYPE DECISION]` SKU Detailは、そのSKUを発注すべきか判断するための参照画面とする。

HeaderにはSKU、Item Name、Brand、Supplier、Item Status、Attentionを表示する。

Inventoryには最低限、Current Stock、Safety Stock、Open PO、Arrival Qtyを表示し、Standard Stockは実データに存在し利用可能な場合に表示する。正確な取得元と業務上の意味は`[TBD - SOURCE REVIEW]`とする。

Salesは実ソースで取得可能な粒度を優先し、直近30 / 60 / 90日、または当月 / 前月 / 前々月等を表示する。採用する粒度はSalesデータ構造確認後に確定する。販売推移グラフは実データが取得可能な場合の実装候補とし、必須とはしない。

RecommendationではRecommended Qtyを大きく表示し、「現行G-SYSの発注数量計算に基づく推奨値」であることを明示する。算出根拠は実Formulaの入力項目を確認後に確定し、推測した根拠を表示しない。

Lead Timeの取得元およびBrand / Supplierとの関係は`[TBD - SOURCE REVIEW]`とする。

Open PO / Arrivalでは、取得可能であれば以下を表示する。

- PO No.、Order Date、Qty、Status
- Arrival No.、Qty、ETA

過去発注履歴は取得可能なLegacy POの直近履歴を表示する。ただしLegacyに存在しないAudit情報を推測・生成しない。

本画面は原則参照専用とし、「発注候補へ戻る」「発注対象へ追加 / 解除」程度の操作に限定する。Order Qtyの正式編集は行わない。

---

# 13. Order Draft

## Header

| 項目 | 編集 | 必須 | 要件・取得元 |
|---|:---:|:---:|---|
| Draft No. | × | ○ | `[PROTOTYPE DECISION]` 新Portal側で採番 |
| Supplier | 原則× | ○ | G-SYSデータから取得。`[TBD - SOURCE REVIEW]` 正確な取得元 |
| Brand | × | ○ | G-SYSデータから取得 |
| Order Date | ○ | ○ | 初期値は当日 |
| Requested Delivery | ○ | 未確定 | `[TBD - CUSTOMER REVIEW]` 必須 / 任意 |
| Currency | 未確定 | 未確定 | `[TBD - SOURCE REVIEW]` 既存G-SYS仕様および取得元 |
| Remark | ○ | × | 任意入力 |
| Status | × | ○ | `DRAFT` |

## Detail

| 項目 | 編集 | 要件 |
|---|:---:|---|
| SKU | × | 商品識別子 |
| Item Name | × | 商品名 |
| Current Stock | × | 現在庫 |
| Open PO | × | 未入荷発注残 |
| Recent Sales | × | `[TBD - SOURCE REVIEW]` 取得元・期間・粒度 |
| Lead Time | × | `[TBD - SOURCE REVIEW]` 取得元 |
| Recommended Qty | × | 既存Formulaによる推奨値 |
| Order Qty | ○ | ユーザーが変更可能 |
| Unit Price | × | `[TBD - SOURCE REVIEW]` 取得元 |
| Amount | × | `Order Qty × Unit Price`で自動計算 |
| Item Status | × | 商品状態 |
| Attention | × | 該当するAttention Flag |

`[PROTOTYPE DECISION]` Recommended Qtyと実際のOrder Qtyの両方をPrototype専用Databaseへ保存し、システム推奨値とユーザー判断値を後から確認可能にする。

## Order Qty Validation

`[PROTOTYPE DECISION]` Order Qtyには以下のBusiness Ruleを適用する。

- 0以上の整数とする。
- Recommended Qtyは編集不可とする。
- Order Qtyはユーザーが変更可能とする。
- Order Qty = 0を許可する。
- Order Qty = 0のSKUは実際の発注対象から除外する。
- Recommended QtyとOrder Qtyが異なっていても保存可能とする。
- Recommended QtyとOrder Qtyの差異をAudit可能な状態で保存する。

Recommended Qtyから大きく乖離した場合も入力は禁止せず、以下のWarningを表示して処理継続を許可する。

> 発注数量が推奨発注数量から大きく異なっています。内容をご確認ください。

`[PROTOTYPE DECISION]` PrototypeではRecommended Qtyから±50%を超える乖離をWarning候補とする。

`[TBD - CUSTOMER REVIEW]` Warningを表示する正式な乖離閾値は9/17に顧客確認する。

## Draft Validation

Draftは作業途中データであるため、正式発注時より緩いValidationを適用する。Save Draftでは、後続処理に必要な項目が未確定でも保存可能とする。

Validationは以下の段階で、その処理に必要な内容を適用する。

- Draft保存時
- PO確認時
- 発注時
- メーカー回答時

PO Preview、Demo Send、Supplier Confirmedへ進む際は、それぞれの処理に必要なValidationを実行する。

## Order Draft UI/UX

`[CONFIRMED]` Order Draftは「システム推奨値を参考に、人が最終発注内容を決定する」画面とする。

- Recommended QtyとOrder Qtyを横並びで明確に表示する。
- Recommended Qtyは編集不可、Order Qtyは編集可能とする。
- Create Draft時は`Order Qty = Recommended Qty`を初期値とし、その後Recommended Qtyを変更しない。
- Draft上でもRecommended Qtyの根拠を確認可能とする。表示内容は`[TBD - SOURCE REVIEW]`とする。
- Add Itemは原則として同一Supplierの商品に限定する。正確なPO / Supplier単位は`[TBD - SOURCE REVIEW]`とする。
- Remove Itemにより、ユーザーが「今回発注しない」操作を実行できるようにする。内部的な削除またはOrder Qty = 0の扱いは実装設計で決定する。

PrototypeではRequested DeliveryをOrder Headerで入力し、その値を各DetailのOriginal Requested DeliveryとしてSnapshot可能な構造とする。メーカー回答時にはSKU単位でConfirmed Deliveryを保持する。

`[TBD - CUSTOMER REVIEW]` 発注時点からSKUごとにRequested Deliveryを設定する業務があるかを確認する。

## Summary・画面操作

画面下部にSKU数、発注数量合計、発注金額合計を表示する。

- Save Draft：作業途中保存を許可する。
- 発注内容を確認：PO Previewへ進む。この時点では`READY_TO_ORDER`へ変更しない。
- 未保存離脱：変更後未保存のまま離脱する場合、「変更内容が保存されていません。画面を移動しますか？」等の確認を表示する。

## Draft Audit

保存された変更をAudit対象とし、キーストローク単位では保存しない。最低限、Order Qty変更、Requested Delivery変更、Item追加、Item削除を記録する。

可能な操作：

- Save Draft
- Remove Item
- Add Item
- Cancel
- Preview PO
- Order Qty変更
- Remark入力

PrototypeではDraftとして保存可能にする。

---

# 14. PO Preview

Order Draftから、
メーカー向けPurchase OrderをPreviewする。

目的：

Excelを業務処理媒体として利用するのではなく、

Web上で業務処理
↓
必要な成果物としてPOを生成

という構造を確認する。

既存Excel Templateの再利用可否については、
実ソース確認後に判断する。

9/17では少なくとも
PO Previewが表示できる状態とする。

`[PROTOTYPE DECISION]` PO PreviewはExcelを編集する画面ではなく、Web上で確定した発注内容をメーカー送付前に確認する画面とする。

## Header

- PO No.
- Supplier
- Brand
- Order Date
- Requested Delivery
- Currency
- Total Qty
- Total Amount
- Remark

## Detail

- SKU
- Item Name
- Order Qty
- Unit Price
- Amount

## Manufacturer Communication

- To
- CC
- Subject
- Body
- Attachment
- Send

`[CONFIRMED]` 9/17 Prototypeでは実メール送信を行わない。Send操作はDemo Modeとし、`Demo Mode - No email was actually sent.` 等のメッセージを表示する。

Prototype内部では、次のWorkflow Status遷移を実行可能とする。

```text
READY_TO_ORDER
↓
SENT
↓
AWAITING_SUPPLIER
```

`[TBD - SOURCE REVIEW]` 既存Excel PO Templateを利用したExcel生成は、以下を確認してから再利用可否を決定する。

- 現在使用しているPO Template
- PO Export処理
- 国内ブランド用PO生成方式
- メーカー別Templateの有無

可能であれば9/17 Prototypeでも既存FormatによるExcel PO生成を実装するが、ソース確認前には必須要件としない。

## PO Preview Validation

`[PROTOTYPE DECISION]` PO Previewへ進むためには、Order Qty > 0の商品が最低1件必要とする。Order Qty = 0の商品だけの場合はPO Previewへ進めない。

Amountはユーザー入力させず、`Order Qty × Unit Price`で自動計算する。Unit PriceはPrototypeでは原則参照値とし、価格変更機能とは分離する。

## PO Preview UI/UX

`[PROTOTYPE DECISION]` PO Previewは原則READ ONLYとし、Order Draftで編集したメーカー向け発注内容を最終確認・確定する画面とする。

メーカー向けPOにはSKU、Item Name、Order Qty、Unit Price、Amountを表示し、Recommended Qty、Current Stock、Safety Stock、Recent Sales、Formula、Attention等の社内判断情報は原則分離する。

SummaryとしてSKU数、Total Qty、Total Amountを表示する。Web PO Previewを必須とし、Excel生成は既存Templateの再利用可否を確認後の実装候補とする。

## 発注内容確定・修正

Preview表示だけではStatusを変更しない。「発注内容を確定」の実行時に確認Dialogを表示し、`DRAFT → READY_TO_ORDER`へ遷移する。

`[PROTOTYPE DECISION]` `READY_TO_ORDER`かつメーカー送信前であれば、Draftへ戻して修正可能とする方向とし、`READY_TO_ORDER → DRAFT`をAudit Trailへ記録する。`AWAITING_SUPPLIER`以降はDraftへ戻さない。

`[TBD - CUSTOMER REVIEW]` READY_TO_ORDERからDraftへ戻す正式運用、および発注確定とメーカー送信の間に承認Workflowが必要かを確認する。

Demo Sendでは実メールを送信しない旨を明示的なDialogで表示し、`READY_TO_ORDER → SENT → AWAITING_SUPPLIER`へ遷移する。ユーザーには最終状態として「メーカー回答待ち」を表示する。

---

# 15. Supplier Response / Order Update

メーカー回答後に、
Web上で数量・納期等を変更できるようにする。

例：

Ordered Qty
10

Confirmed Qty
9

Requested Delivery
2026/09/25

Confirmed Delivery
2026/09/26

管理対象：

- Original Order Qty
- Confirmed Qty
- Requested Delivery
- Confirmed Delivery
- Change Reason
- Changed By
- Changed At

変更履歴を保持する。

## Header

| 項目 | 編集 |
|---|:---:|
| PO No. | × |
| Supplier | × |
| Brand | × |
| Order Date | × |
| Current Status | × |

Headerは原則編集不可とする。

## Detail

| 項目 | 要件 |
|---|---|
| SKU | 商品識別子 |
| Item Name | 商品名 |
| Ordered Qty | Original値。メーカー回答で上書きしない |
| Confirmed Qty | メーカー回答による確定数量 |
| Requested Delivery | Original値。メーカー回答で上書きしない |
| Confirmed Delivery | メーカー回答による確定納期 |
| Response Note | メーカー回答に関する備考 |

`[PROTOTYPE DECISION]` Original値とConfirmed値を両方保持する。

例：

```text
Ordered Qty: 10
Confirmed Qty: 9

Requested Delivery: 2026/09/25
Confirmed Delivery: 2026/09/26
```

これにより、将来的にSupplier Fulfillment Rate、Quantity Change Frequency、Delivery Change Frequency等の分析へ利用できる構造とする。

## Supplier Response Validation

Confirmed Qtyには以下のBusiness Ruleを適用する。

- 0以上の整数とする。
- Ordered Qtyより少ない値を許可する。
- Ordered Qtyと同じ値を許可する。
- Ordered Qtyより多い値を許可する。
- Confirmed Qty = 0を許可し、全数欠品等のケースに対応する。

Confirmed Qty < Ordered Qtyの場合は保存を許可し、`QUANTITY_CHANGED`を設定する。

Confirmed Qty > Ordered Qtyの場合も保存を許可するが、以下のWarningを表示する。Warning表示後も保存可能とする。

> 確定数量が発注数量を超えています。内容をご確認ください。

Ordered QtyとConfirmed Qtyが異なる場合は`QUANTITY_CHANGED`を設定する。

## Delivery Validation

Confirmed DeliveryはRequested Deliveryと異なる値を許可する。異なる場合は`DELIVERY_CHANGED`を設定する。

Confirmed Deliveryが未入力でもSupplier Responseを一時保存可能とし、数量だけ先に回答されるケース、納期だけ先に回答されるケースを許容する。

## Partial Supplier Response

`[PROTOTYPE DECISION]` メーカーから複数SKUの一部だけ回答されるケースを許容する。

例：

| SKU | Ordered Qty | Confirmed Qty |
|---|---:|---:|
| SKU-A | 10 | 9 |
| SKU-B | 20 | 20 |
| SKU-C | 15 | 未回答 |
| SKU-D | 30 | 未回答 |

一部明細のみ回答済みの場合は、以下の状態とする。

- Workflow Status：`AWAITING_SUPPLIER`
- Attention：`PARTIAL_CONFIRMATION`

全明細について必要な回答が揃った場合に、`AWAITING_SUPPLIER → SUPPLIER_CONFIRMED`へ遷移する。

`[TBD - CUSTOMER REVIEW]` 「必要な回答が揃った」状態の厳密な定義は、実業務確認を踏まえて最終決定する。

## Supplier Response UI/UX

本画面は、発注時のOriginal情報とメーカー回答を比較し、差異を登録する画面とする。

Headerは原則READ ONLYとし、PO No.、Supplier、Brand、Order Date、Current Status、Total Ordered Qty、Total Amountを表示する。

Response Header：

- Response Date
- Received / Registered By
- Response Note

Detail：

- SKU
- Item Name
- Ordered Qty
- Confirmed Qty
- Requested Delivery
- Confirmed Delivery
- Response Note / Reason
- Response State / Attention

## 0と未回答の分離

`[PROTOTYPE DECISION]` Confirmed Qty = 0とConfirmed Qty = null / 未回答は異なる意味として扱い、DB、API、UIのすべてで区別する。

- `0`：メーカーが0個と正式回答した状態
- `null / 未回答`：メーカーから数量回答がない状態

Confirmed Qty = 0だけを理由に`LONG_TERM_OUT_OF_STOCK`や`DISCONTINUED`へ自動変換しない。欠品、長期欠品、廃番等の正式定義と0回答時の業務Status / Item Statusは`[TBD - CUSTOMER REVIEW]`とする。

## Response Reason

Prototypeではメーカー回答理由を保持可能な構造とし、暫定候補を欠品、一部供給、長期欠品、廃番、納期変更、その他とする。正式な区分は`[TBD - CUSTOMER REVIEW]`とし、自由記述のResponse Noteも保持する。

## 回答保存と回答確定

「回答を保存」と「メーカー回答を確定」を分離する。

- 回答を保存：Partialな状態でも可能
- メーカー回答を確定：必要回答が揃った場合のみ可能

必要回答の完了条件は`[TBD - CUSTOMER REVIEW]`とする。

## 複数回更新

`[PROTOTYPE DECISION]` 同一Order / SKUについてSupplier Responseを複数回保存・更新できる構造とする。最新値を現在値として表示し、例えば`10 → 9 → 8`の過去値をAudit / Historyから追跡可能にする。Version管理方式は物理設計時に決定する。

## Difference Summary

画面上に以下を表示する。

- 回答済みSKU数 / 全SKU数
- 数量変更SKU数
- 納期変更SKU数
- Confirmed Qty = 0 SKU数
- 未回答SKU数

Supplier Response確定後も、PrototypeからLegacy PO、Arrival、Stock、Logizero、Tempostarを更新しない。

---

# 16. Order History

`[PROTOTYPE DECISION]` Order Historyは単なる発注一覧ではなく、新Portal上で行われた操作・変更を追跡できるAudit Trailとして実装する。

表示項目：

- Order / PO No.
- Order Date
- Supplier
- Brand
- SKU
- Ordered Qty
- Confirmed Qty
- Requested Delivery
- Confirmed Delivery
- Status
- Attention
- Updated By
- Updated At

最低限のFilter：

- Supplier
- Brand
- PO No.
- Status
- Order Date

詳細画面では、

Order Created
↓
Sent
↓
Supplier Response
↓
Qty Changed
↓
Delivery Changed
↓
Completed

等の履歴を確認できるようにする。

## Detail Timeline

以下のような時系列履歴を表示する。

```text
09/17 10:12
Order Draft Created
User: XXX

09/17 10:18
Order Qty Changed
30 → 40

09/17 10:25
PO Sent
Status: AWAITING_SUPPLIER

09/18 09:15
Supplier Response Received
Confirmed Qty: 40 → 35

09/18 09:16
Delivery Changed
09/25 → 09/26

09/18 09:17
Status Changed
AWAITING_SUPPLIER → SUPPLIER_CONFIRMED
```

`[PROTOTYPE DECISION]` Prototypeでも上記履歴を実際にPrototype専用Databaseへ保存する。

`[CONFIRMED]` 数量・納期等の変更について、現在値だけではなく変更履歴をPrototype専用Databaseへ保存する。

最低限保存する情報：

- Event Type
- Entity / Order / Detail識別子
- Old Value
- New Value
- Changed By
- Changed At

保存例：

```text
Event Type: QUANTITY_CHANGED
Old Value: 40
New Value: 35

Event Type: DELIVERY_CHANGED
Old Value: 2026/09/25
New Value: 2026/09/26
```

## Order History UI/UX

`[CONFIRMED]` Order Historyは「誰が、いつ、何を発注し、何が変更されたか」を追跡するAudit / Business History画面とする。

一覧表示：

- PO No.
- Order Date
- Supplier
- Brand
- SKU Count
- Total Ordered Qty
- Total Amount
- Status
- Attention
- Updated At
- Detail Link

FilterにはSupplier、Brand、PO No.、Status、Order Date、Attention onlyを設ける。

Detail HeaderにはPO No.、Supplier、Brand、Order Date、Requested Delivery、Currency、Status、Created By、Created At、Updated Atを表示する。

Detail LinesにはSKU、Item Name、Recommended Qty、Ordered Qty、Confirmed Qty、Requested Delivery、Confirmed Delivery、Attentionを表示し、Recommended → Ordered → Confirmedの3段階を追跡可能とする。

TimelineではDraft Created、Order Qty Changed、Requested Delivery Changed、Order Ready、Demo Sent、Supplier Response Received、Quantity Changed、Delivery Changed、Supplier Confirmed、Attention Acknowledged等を時系列表示する。

`performed_by`により、Draft作成者、数量変更者、発注内容確定者、Demo Send実行者、Supplier Response登録者・確定者、Attention確認者を追跡可能とする。Authentication方式は`[TBD - SOURCE REVIEW]`とする。

最新値だけでなくSupplier Responseの過去の変更履歴もTimelineで確認可能とする。

Portalで生成したOrderにはPortal Audit Trailを表示する。Legacy POにはLegacyに存在する事実データのみを表示し、存在しないAudit情報を推測・生成しない。

History画面は原則READ ONLYとし、Order Qty、Confirmed Qty、Status等を直接編集させない。修正が必要な場合は該当業務画面へ遷移する。

---

# 17. Portal Workflow Status

新Portalでは、
ユーザー向けWorkflow Statusを持つ。

暫定Status：

ORDER_CANDIDATE
↓
DRAFT
↓
READY_TO_ORDER
↓
SENT
↓
AWAITING_SUPPLIER
↓
SUPPLIER_CONFIRMED
↓
COMPLETED

現行G-SYSのPO Status：

INITIAL
↓
OFFICIAL
↓
STOCKIN

は変更しない。

Portal StatusとLegacy StatusのMappingは、
実ソース確認後に確定する。

## Workflow Status遷移制御

`[PROTOTYPE DECISION]` Workflow StatusはユーザーがDropdown等から自由に変更する設計とせず、業務操作の結果としてシステムが遷移させる。

原則としてStatusの飛び越しを禁止する。例えば、`DRAFT → SUPPLIER_CONFIRMED`のような直接遷移は許可しない。

| 業務操作 | 遷移後Status |
|---|---|
| Create Draft | `DRAFT` |
| Confirm Order（Preview表示のみでは遷移しない） | `READY_TO_ORDER` |
| Demo Send | `SENT`、続いて`AWAITING_SUPPLIER` |
| Supplier Response完了 | `SUPPLIER_CONFIRMED` |

`[TBD - SOURCE REVIEW]` `[TBD - CUSTOMER REVIEW]` `COMPLETED`への正式な遷移条件は、Legacy PO / Arrival / StockとのMappingおよび実業務確認後に確定する。

---

# 18. Item Status

暫定：

- NORMAL
- OUT_OF_STOCK
- LONG_TERM_OUT_OF_STOCK
- DISCONTINUED
- ON_HOLD

ただし、
現行G-SYSに既存Status / Flagが存在する場合は、
原則としてそれを利用する。

実ソース確認後に確定する。

---

# 19. Attention Flag

以下はWorkflow Statusとは分離する。

暫定Flag：

- `QUANTITY_CHANGED`
- `DELIVERY_CHANGED`
- `PARTIAL_CONFIRMATION`
- `DATA_OUTDATED`
- `OTHER_ATTENTION`

`[PROTOTYPE DECISION]` Supplier Response保存時にOriginal値との差分からAttention Flagを設定する。

- 数量が変更された場合：`QUANTITY_CHANGED`
- 納期が変更された場合：`DELIVERY_CHANGED`
- 複数条件に該当する場合：複数Attention Flagを保持

Dashboardでは、
Attention件数として表示する。

## Attention Acknowledgement

`[PROTOTYPE DECISION]` Attentionには発生状態だけでなく、担当者が確認したことを示す出口を設ける。

- `ACTIVE`
- `ACKNOWLEDGED / RESOLVED`

具体的な内部Codeは実装設計で決定する。Order Detail / History等から「確認済みにする」操作を提供し、`acknowledged_by`、`acknowledged_at`または同等情報を保持してAudit Trailへ記録する。

確認済みAttentionはDashboardのActive Attention件数から除外可能とするが、履歴は削除しない。本機能は承認Workflowではなく、要確認事項を担当者が確認した事実の記録とする。

---

# 20. 9/17 Demo Scenario

`[CONFIRMED]` 主要業務シナリオは以下の4画面を中心とするEnd-to-End Workflowとして固定する。

```text
Order Candidate List
↓
Create Draft
↓
Order Draft
↓
PO Preview
↓
Demo Send
↓
AWAITING_SUPPLIER
↓
Supplier Response / Order Update
↓
Confirmed Qty / Confirmed Delivery Update
↓
SUPPLIER_CONFIRMED
↓
Order History / Audit Trail
```

この一連の処理はHTMLモックではなく、実際のプログラムとしてEnd-to-Endで動作させる。

1. Dashboardを開く
2. Order Candidates / OOS / Awaiting Supplier / Attentionを確認
3. Brandを選択
4. 発注候補SKU一覧を表示
5. SKU詳細を確認
6. Current Stock / Sales / Open PO / Lead Time等を確認
7. Recommended Qtyを確認
8. Order Qtyを変更
9. Order Draftを作成
10. PO Previewを表示
11. Demo上で発注処理
12. Awaiting SupplierへStatus変更
13. Supplier Response画面を開く
14. Confirmed Qtyを変更
15. Confirmed Deliveryを変更
16. 保存
17. Attentionを表示
18. Order Historyで変更履歴を確認

---

# 21. 9/17 Prototype Scope

## 必須

- New Operations Portal
- Dashboard
- Brand別表示
- Order Candidate List
- SKU Detail
- 実データ取得
- 既存FormulaによるRecommended Qty
- Order Qty編集
- Order Draft
- PO Preview
- Order History
- Supplier Response
- Confirmed Qty変更
- Confirmed Delivery変更
- Status
- Attention
- History

## 可能であれば実装

- Excel PO生成
- Data Freshness Alert
- Sales Trend Chart
- Mail Preview

## 今回実施しない

- 実メーカーMail送信
- 本番G-SYSへの正式更新
- Logizero変更
- Tempostar変更
- Legacy Batch変更
- Java 8 Upgrade
- Spring Boot 1.5 Upgrade
- Full DB Migration
- AI Agent

---

# 22. 実ソース受領後の最優先調査

Claude Codeで以下を実ソースから追跡する。

## Order Recommendation

- /order-judgement
- MsFormula
- Formula
- OrderQuantityCalculator
- StockCalculationHelper
- Stock Standard
- Safety Stock
- Lead Time
- Open Quantity

Recommended Qtyが
実際にどのデータと計算式から生成されるか確認する。

## Sales

- SKU単位販売履歴の保存場所
- Tempostarとの関係
- 保存期間
- 日次 / 月次粒度
- 最新データ更新日時

## PO

- TrPo
- TrPoDtl
- PO生成処理
- PO Status
- Excel Template
- Mail処理

## Stock / Arrival

- MsStk
- TrArr
- TrArrDtl
- BusinessLogicUtil
- PO → Arrival → Stock更新経路

## Brand / Supplier

- MsItem
- MsComm
- Brand / Supplier Code
- Lead Time

## Sample Data

- goo_dummy_dumpfile.sql
- DB backup
- fixture
- testfile

Prototypeで利用可能な実サンプルデータを確認する。

---

# 23. 実ソース確認後に確定する事項

以下は現時点では確定しない。

1. New APIからLegacyへ接続する具体方式
2. Legacy API再利用可否
3. MySQL直接Readの可否
4. Recent Sales取得元
5. Recommended Qtyの正確な計算経路
6. Brand / Supplierの正確なデータ構造
7. Portal StatusとLegacy StatusのMapping
8. Item Statusの既存定義
9. Excel PO Template再利用方式
10. Mail機構再利用方式
11. Data Freshness取得方式
12. BrandとSupplierの実際のEntity / Master構造
13. Currency取得元
14. Unit Price取得元
15. Recent Salesの期間・粒度
16. Lead Time取得元
17. PO Number採番方式
18. 現行PO Excel Template
19. メーカー別PO Template有無
20. メーカーMail Address取得元
21. `TrPo` / `TrPoDtl`への正式登録タイミング
22. PO Status遷移の実装箇所
23. Supplier Responseに相当する既存機能の有無
24. Arrival / Stockへの反映タイミング
25. Sample Databaseで9/17デモに必要なデータが揃うか
26. Legacy既存REST APIの再利用可能範囲
27. Legacy MySQL READ ONLY接続の可否
28. Recommended Qty計算ロジックの再利用方式
29. Legacy Business Serviceを外部から安全に利用可能か
30. 発注判断時Snapshotとして保存すべき実データ項目
31. PrototypeのAuthentication方式
32. 1 PO / Draftに許容されるSupplier単位
33. SKU Detailで取得可能なSales粒度
34. SKU Detailで表示可能なLegacy PO / Arrival履歴
35. Recommended Qty算出根拠として実際に表示可能な項目
36. Supplier Responseの既存Legacy相当機能
37. Supplier Response Version管理に利用可能な既存構造
38. メールアドレス / PO Template取得元

Prototype専用Databaseを使用する方針および保存対象は確定済みである。DB製品、物理構成、Schema詳細は新Portal技術設計で決定する。

実ソースを確認してから決定する。

---

# 24. 9/17 顧客確認事項

Prototypeを見せながら、
最低限以下を確認する。

## 発注候補

- 現行Formulaをそのまま利用してよいか
- 発注判断に必要な情報に不足はないか
- Recent Salesの適切な期間
- Lead Timeの扱い
- Recommended Qtyの考え方

## Workflow

- Draftは必要か
- 承認者は必要か
- 誰がメーカーへ送信するか
- Supplier Responseを誰が入力するか
- 数量変更時の承認要否
- 納期変更時の承認要否

## Status

- 欠品
- 長期欠品
- 廃番
- 発注停止

等の正式な業務定義。

## Output

- 現行Excel POを維持するか
- メーカー別Formatが存在するか
- Mail送信までG-SYSで行うか

## Integration

- Logizeroへいつ反映するか
- 在庫更新タイミング
- Sales Data更新タイミング
- メーカー回答をどこまでシステム化するか

## 画面・運用詳細

- 発注時点からSKU別Requested Deliveryを指定するケースの有無
- Supplier Response Reasonの正式区分
- Confirmed Qty = 0時の業務Status / Item Status
- Supplier Responseの完了条件
- Attentionを誰が確認・解消するか
- READY_TO_ORDERからDraftへ戻す正式運用
- 発注確定とメーカー送信の間に承認Workflowが必要か

---

# 25. Prototypeの成功条件

9/17時点で、

「画面がある」

だけでは成功としない。

以下が一連で動作することを成功条件とする。

実サンプルデータ
↓
発注候補
↓
既存FormulaによるRecommendation
↓
担当者による数量変更
↓
Order Draft
↓
PO Preview
↓
Supplier Response
↓
数量・納期変更
↓
History

これを顧客へ実際に操作して見せられる状態を目標とする。

`[CONFIRMED]` Draft作成、PO Preview、Demo Send、Supplier Response、Attention設定、Status遷移およびAudit Trail保存が、Prototype専用Databaseを利用してEnd-to-Endで実動作することを成功条件に含める。

---

# 26. 現時点の設計原則

1. Legacy G-SYSは可能な限り変更しない
2. LegacyのBusiness Ruleを勝手に再実装しない
3. 新規部分はModern Stackで構築する
4. 実ソース・実サンプルデータを利用する
5. 発注Recommendationは既存Formulaを利用する
6. Excelは入力媒体ではなく成果物へ寄せる
7. Portal WorkflowとLegacy Statusを分離する
8. 変更履歴を残す
9. FrontendからDBを直接更新しない
10. 9/17では本番Integrationより業務フロー確認を優先する
11. 将来的なAPI Gateway / AI Agent追加を阻害しない構造にする
12. AIは今回のPrototype Scopeから除外する
13. PrototypeではLegacy / Sample G-SYSを原則READ ONLYとする
14. 新Portal固有の更新データはPrototype専用Databaseへ保存する
15. 既存`TrPo` / `TrPoDtl`等へ正式な書き込みを行わない
16. Recommended QtyとOrder Qty、Original値とConfirmed値をそれぞれ分離して保存する
17. Attention FlagはWorkflow Statusと分離し、複数保持可能とする
18. Order HistoryはPrototype専用Databaseに保存するAudit Trailとする
19. ユーザー向け画面は日本語を初期表示言語とする
20. UI表示文言と内部Codeを分離する
21. 表示文言をReact Componentへ直接ハードコードせず、i18n Resourceへ分離する
22. 内部実装のSource Code、API、JSON、Database、Enum等は原則英語とする
23. Workflow Statusは業務操作の結果として遷移させ、ユーザーによる自由変更と飛び越しを原則禁止する
24. Draft保存、PO確認、発注、メーカー回答の各段階でValidationを適用する
25. LegacyをMaster DataのSystem of Recordとして扱い、Prototype専用DatabaseへMasterを二重管理しない
26. 発注判断時点の根拠データをOrder DetailのSnapshotとして保存する
27. FrontendからLegacy DatabaseおよびPrototype Databaseへ直接アクセスさせない
28. 汎用Status変更APIを作らず、業務操作の結果としてBackendがStatusを遷移させる
29. APIは原則として日本語表示文言を返さず、内部CodeをFrontend i18n Resourceで表示変換する
30. Order Candidate ListではOrder Qtyを原則編集しない
31. Draft作成時にOrder Qty = Recommended Qtyを初期値とし、正式編集はOrder Draftで行う
32. PO Previewは原則READ ONLYとし、Preview表示と発注内容確定を分離する
33. 発注内容確定とメーカー送信を分離する
34. メーカー送信前はREADY_TO_ORDERからDRAFTへ戻せる方向とし、AWAITING_SUPPLIER以降は戻さない
35. Confirmed Qty = 0と未回答 / nullを分離する
36. Supplier Responseを複数回保存・更新可能とする
37. History画面は原則READ ONLYとする
38. Attentionは確認済み状態を保持可能とする
39. Legacyに存在しない履歴を推測・生成しない

---

# 27. 要件ステータス一覧

## 27.1 `[CONFIRMED]`

- 実ソース、既存業務ロジックおよび実サンプルデータを利用した動作Prototypeを構築する
- PrototypeではLegacy / Sample G-SYSを原則READ ONLYとする
- 既存`TrPo` / `TrPoDtl`等への正式な書き込みを行わない
- 9/17 Prototypeでは実メール送信を行わない
- 発注計算は既存Formula / Recommendationロジックを可能な限り利用する
- 4画面を中心とする主要業務シナリオを実プログラムとしてEnd-to-Endで動作させる
- 9/17 Prototypeの初期UI言語は日本語とする
- UI表示値と内部Codeを分離する
- 数量・納期変更のOld Value / New ValueをAudit Trailへ保存する
- Order Draftを人が最終発注内容を決定する画面とする
- Order HistoryをAudit / Business Historyの参照画面とする

## 27.2 `[PROTOTYPE DECISION]`

- 新Portal固有データの保存先としてPrototype専用Databaseを使用する
- Recommended QtyとOrder Qtyの両方を保存する
- Original値とConfirmed値の両方を保存する
- Supplier Response保存時にAttention Flagを設定する
- 複数Attention Flagを保持可能とする
- Order HistoryをAudit Trailとして保存する
- Demo Sendにより`READY_TO_ORDER → SENT → AWAITING_SUPPLIER`へ遷移可能とする
- 将来英語を追加できるi18n構造として実装する
- Order Qty = 0を許可し、実際の発注対象から除外する
- Recommended QtyとOrder Qtyの差異を許可し、Audit可能な状態で保存する
- Recommended Qtyからの大幅乖離はWarningとし、処理継続を許可する
- Order Qty > 0の商品がない場合はPO Previewへ進めない
- Confirmed Qty > Ordered QtyをWarning付きで許可する
- Confirmed Qty = 0を許可する
- Partial Supplier Responseを許可する
- Statusは業務操作によって遷移させる
- Statusの飛び越しを原則禁止する
- Draftでは段階的Validationを採用する
- Legacy / Sample G-SYSをMaster DataのSystem of Recordとして扱う
- Prototype専用Databaseには新Portal固有のWorkflow / Operation StateおよびAudit情報を保存する
- 発注判断時点の在庫・販売実績等をSnapshotとして保存する
- Frontendから各Databaseへ直接アクセスさせず、New Service APIを経由する
- Legacy Adapterは既存REST API、READ ONLY DB接続、必要最小限のロジック利用・移植の順で検討する
- 汎用Status変更APIを原則作成しない
- APIは内部Codeを返し、日本語表示はFrontend i18n Resourceで行う
- DashboardをAction / Operation Cockpitとして設計する
- Order Candidate ListではOrder Qtyを編集せず、Draft作成時にRecommended Qtyを初期値とする
- SKU Detailを発注判断用の参照画面とする
- PO Previewを原則READ ONLYとし、Preview、発注確定、Demo Sendを分離する
- Confirmed Qty = 0と未回答 / nullをDB、API、UIで区別する
- Supplier Responseの保存と確定を分離する
- Supplier Responseを複数回保存・更新可能とする
- AttentionにACTIVEと確認済み状態を持たせ、確認操作をAuditする
- History画面を原則READ ONLYとする

## 27.3 `[TBD - SOURCE REVIEW]`

- Legacyとの具体的な接続方式およびMySQL直接Readの可否
- Brand / SupplierのEntityおよびMaster構造
- Currency、Unit Price、Recent Sales、Lead Timeの取得元
- Recommended Qtyの実計算経路
- PO Number採番方式
- PO Excel Templateおよびメーカー別Templateの有無
- メーカーMail Address取得元
- PO Status遷移の実装箇所
- Supplier Response相当機能の有無
- Arrival / Stockへの反映タイミング
- Sample Databaseのデモデータ充足状況
- `COMPLETED`へのLegacy PO / Arrival / Stock連動条件
- Legacy既存REST APIの再利用可能範囲
- Legacy MySQL READ ONLY接続の可否
- Recommended Qty計算ロジックの再利用方式
- Legacy Business Serviceを外部から安全に利用可能か
- 発注判断時Snapshotとして保存すべき実データ項目
- PrototypeのAuthentication方式
- 1 PO / Draftに許容されるSupplier単位
- SKU Detailで取得可能なSales粒度
- SKU Detailで表示可能なLegacy PO / Arrival履歴
- Recommended Qty算出根拠として実際に表示可能な項目
- Supplier Responseの既存Legacy相当機能
- Supplier Response Version管理に利用可能な既存構造
- メールアドレス / PO Template取得元

## 27.4 `[TBD - CUSTOMER REVIEW]`

- Requested Deliveryの必須 / 任意
- 現行Formulaを新画面でも利用する方針の妥当性
- Draftおよび承認Workflowの正式要件
- メーカー送信およびSupplier Response入力の担当者
- 数量・納期変更時の承認要否
- 欠品、長期欠品、廃番、発注停止の正式定義
- 新PortalのOrderを既存G-SYSの正式POへ登録するタイミング
- 現行Excel PO維持およびMail送信範囲
- Recommended Qty乖離Warningの正式な閾値
- Supplier Response完了条件
- 日本語表示文言の最終確定
- `COMPLETED`への正式な業務遷移条件
- 発注時点からSKU別Requested Deliveryを指定するケースの有無
- Supplier Response Reasonの正式区分
- Confirmed Qty = 0時の業務Status / Item Status
- Attentionを誰が確認・解消するか
- READY_TO_ORDERからDraftへ戻す正式運用
- 発注確定とメーカー送信の間に承認Workflowが必要か

---

# 28. UI言語・国際化方針

## 28.1 初期表示言語

`[CONFIRMED]` 9/17 Prototypeで新規開発するOperations Portalは、日本語を初期表示言語とする。顧客デモでは、原則としてユーザー向け画面を日本語で表示する。

対象：

- Dashboard
- Brand / Order Candidate List
- SKU Detail
- Order Draft
- PO Preview
- Supplier Response / Order Update
- Order History
- Validation Message
- Warning
- Confirmation Dialog
- Status表示
- Attention表示
- Navigation
- Button
- Form Label
- その他ユーザー向け表示文言

内部実装は英語を基本とする。

- Source Code
- Class Name
- Method Name
- Variable Name
- API Endpoint
- JSON Property
- Database Table / Column
- Enum
- Internal Status Code
- Attention Code

内部CodeとUI表示値を分離する。

```text
Internal: AWAITING_SUPPLIER
Display:  メーカー回答待ち

Internal: QUANTITY_CHANGED
Display:  数量変更あり
```

## 28.2 Internationalization / i18n

`[PROTOTYPE DECISION]` 新Operations Portalは、将来的に日本語 / 英語を切り替えられる構造として最初から実装する。

- 画面表示文言をReact Componentへ直接ハードコードしない。
- 表示文言をi18n Resourceへ分離する。
- 具体的なi18nライブラリおよびResource構成は新Portal技術設計時に決定する。

概念構成：

```text
Frontend
  ↓
i18n
├── ja
└── en
```

9/17 Prototypeでは、日本語Resourceに実際に使用する全画面文言を実装する。英語ResourceおよびLanguage Selectorの完成は必須要件としないが、後から英語Resourceと「日本語 | English」等のSelectorを追加できるArchitectureとする。

## 28.3 Portal Workflow Status表示

| Internal Code | 日本語表示 |
|---|---|
| `ORDER_CANDIDATE` | 発注候補 |
| `DRAFT` | 下書き |
| `READY_TO_ORDER` | 発注準備完了 |
| `SENT` | 送付済み |
| `AWAITING_SUPPLIER` | メーカー回答待ち |
| `SUPPLIER_CONFIRMED` | メーカー回答済み |
| `COMPLETED` | 完了 |

## 28.4 Item Status表示

| Internal Code | 日本語表示 |
|---|---|
| `NORMAL` | 通常 |
| `OUT_OF_STOCK` | 欠品 |
| `LONG_TERM_OUT_OF_STOCK` | 長期欠品 |
| `DISCONTINUED` | 廃番 |
| `ON_HOLD` | 発注停止 |

## 28.5 Attention表示

| Internal Code | 日本語表示 |
|---|---|
| `QUANTITY_CHANGED` | 数量変更あり |
| `DELIVERY_CHANGED` | 納期変更あり |
| `PARTIAL_CONFIRMATION` | 一部回答 |
| `DATA_OUTDATED` | データ更新要確認 |
| `OTHER_ATTENTION` | その他要確認 |

`[TBD - CUSTOMER REVIEW]` 上記日本語表現はPrototype表示用の暫定文言とし、9/17顧客レビュー後に最終確定する。

---

# 29. Prototype専用DB 論理モデル

## 29.1 設計原則

`[PROTOTYPE DECISION]` Prototype専用Databaseには、Legacy G-SYSに存在するItem、SKU、Stock、Brand、Supplier等のMasterを新たな正本として複製しない。

```text
Legacy / Sample G-SYS（System of Record）
├── Item / SKU
├── Stock
├── Brand / Supplier
├── Sales / Lead Time / Formula
└── Existing PO / Arrival
          │ READ
          ▼
New Service API
          │ READ / WRITE
          ▼
Prototype Database（Workflow / Operation State）
├── Order Draft / Detail
├── Supplier Response
├── Attention
├── Audit Trail
└── Portal Workflow State
```

Prototypeの暫定論理モデルは以下とする。

```text
portal_order
 ├─ portal_order_detail
 ├─ supplier_response
 │    └─ supplier_response_detail
 ├─ order_attention
 └─ audit_event
```

物理Schema、DB製品、型、Index等は実装設計時に決定する。

## 29.2 `portal_order`

主な項目：

- `id`
- `draft_no`
- `prototype_po_no`
- `supplier_code`
- `supplier_name_snapshot`
- `brand_code`
- `brand_name_snapshot`
- `order_date`
- `requested_delivery`
- `currency`
- `status`
- `remark`
- `total_qty`
- `total_amount`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`

`[PROTOTYPE DECISION]` `ORDER_CANDIDATE`は原則として保存状態ではなく、Legacyデータと既存Formula等から導出される状態として扱う。発注候補からCreate Draftされた時点でPrototype Databaseへの永続化を開始する。

## 29.3 `portal_order_detail`

主な項目：

- `id`
- `portal_order_id`
- `sku`
- `item_name_snapshot`
- `recommended_qty`
- `order_qty`
- `unit_price`
- `amount`
- `current_stock_snapshot`
- `safety_stock_snapshot`
- `open_po_snapshot`
- `recent_sales_snapshot`
- `lead_time_snapshot`
- `item_status_snapshot`
- `created_at`
- `updated_at`

`[PROTOTYPE DECISION]` 発注判断時点の情報をSnapshotとして保存し、後から在庫・販売実績等が更新された場合でも、「その発注を行った時点で何を根拠に判断したか」を追跡可能にする。Recommended QtyとOrder Qtyは別々に保存する。

## 29.4 `supplier_response`

主な項目：

- `id`
- `portal_order_id`
- `response_date`
- `response_note`
- `response_status`
- `received_by`
- `created_at`
- `updated_at`

Prototypeでは実メール自動受信を行わず、メーカーからの回答を担当者がWeb画面から登録する。

## 29.5 `supplier_response_detail`

主な項目：

- `id`
- `supplier_response_id`
- `portal_order_detail_id`
- `ordered_qty`
- `confirmed_qty`
- `requested_delivery`
- `confirmed_delivery`
- `response_note`
- `is_confirmed`
- `created_at`
- `updated_at`

Original値を上書きせず、Ordered Qty / Confirmed QtyおよびRequested Delivery / Confirmed Deliveryを分離して保持する。

`[PROTOTYPE DECISION]` `confirmed_qty = 0`は正式な0回答、`confirmed_qty = null`は未回答として区別する。同一Order / SKUへの複数回の回答更新を許容し、最新値とは別に変更履歴を`audit_event`から追跡可能とする。Version管理の物理方式は実装設計時に決定する。

## 29.6 `order_attention`

主な項目：

- `id`
- `portal_order_id`
- `portal_order_detail_id`
- `attention_type`
- `is_active`
- `detected_at`
- `resolved_at`
- `acknowledged_by`
- `acknowledged_at`
- `note`

暫定Attention Type：

- `QUANTITY_CHANGED`
- `DELIVERY_CHANGED`
- `PARTIAL_CONFIRMATION`
- `DATA_OUTDATED`
- `OTHER_ATTENTION`

Workflow Statusとは独立して管理し、1 Order / Detailに複数Attentionを保持可能とする。

## 29.7 `audit_event`

主な項目：

- `id`
- `portal_order_id`
- `portal_order_detail_id`
- `event_type`
- `field_name`
- `old_value`
- `new_value`
- `performed_by`
- `performed_at`
- `note`

暫定Event Type：

- `ORDER_DRAFT_CREATED`
- `ORDER_QTY_CHANGED`
- `ORDER_READY`
- `DEMO_SENT`
- `STATUS_CHANGED`
- `SUPPLIER_RESPONSE_RECEIVED`
- `QUANTITY_CHANGED`
- `DELIVERY_CHANGED`
- `ATTENTION_ADDED`
- `ATTENTION_RESOLVED`

`audit_event`をOrder History / Timelineのデータソースとして利用する。

## 29.8 Prototypeで新規作成しないMaster

9/17時点では、原則として以下の新規Masterを作成せず、Legacyに存在する業務データを二重管理しない。

- Brand Master
- Supplier Master
- Item Master
- Stock Master
- Price Master
- AI関連Master
- Logizero連携Master
- Tempostar連携Master

`[TBD - SOURCE REVIEW]` User / Authenticationは、9/17 Prototypeの実行方式を確認し、必要最小限の方式を別途決定する。

---

# 30. New Service API 暫定仕様

## 30.1 基本方針

`[PROTOTYPE DECISION]` FrontendからLegacy DatabaseおよびPrototype Databaseへ直接アクセスさせない。

```text
React / TypeScript
        │ REST / JSON
        ▼
New Service API（Java 21 / Spring Boot 3.x）
        │
 ┌──────┴────────────┐
 ▼                   ▼
Legacy Read Adapter  Prototype Repository
 │ READ ONLY          │ READ / WRITE
 ▼                   ▼
Existing G-SYS       Prototype DB
```

## 30.2 Legacy Adapter接続方式

`[TBD - SOURCE REVIEW]` 実ソース確認後、以下の優先順位で接続方式を判断する。

1. 既存REST API利用
2. Legacy MySQLへのREAD ONLY接続
3. 必要最小限の既存Business Logicを新Backend側で利用・移植

原則として1から3の順で検討し、Legacy Business Ruleを新Service側へ安易に二重実装しない。特にRecommended Qtyは既存Formulaの実計算経路を確認してから方式を確定する。

## 30.3 Dashboard API

### `GET /api/dashboard`

取得対象：

- Order Candidates
- Out of Stock
- Long-term Out of Stock
- Draft Orders
- Awaiting Supplier
- Attention
- Inventory Updated At
- Sales Data Through

### `GET /api/dashboard/brands`

ブランド別のCandidate、OOS、Draft、Awaiting Supplier、Attention等を取得する。

## 30.4 Order Candidate API

### `GET /api/order-candidates`

主なQuery：

- `brandCode`
- `status`
- `keyword`
- `page`
- `size`

返却対象：

- SKU
- Item Name
- Brand
- Supplier
- Current Stock
- Safety Stock
- Open PO
- Recent Sales
- Lead Time
- Recommended Qty
- Item Status
- Attention

Recommended Qtyは既存G-SYS Formulaを利用する。

## 30.5 SKU Detail API

### `GET /api/items/{sku}/ordering-context`

Product、Inventory、Sales、Open PO、Arrival、Lead Time、Recommendation、Historyを1回の業務APIでまとめて返却可能な構造を基本とする。

## 30.6 Order Draft API

- `POST /api/orders/drafts`
- `GET /api/orders/drafts/{draftId}`
- `PUT /api/orders/drafts/{draftId}`
- `PATCH /api/orders/drafts/{draftId}/items/{detailId}`

Draft作成時に、Legacyから取得した発注判断時点の情報をSnapshot保存する。Order Qty変更時はRecommended Qtyを変更せず、ユーザー入力値としてOrder Qtyを保存する。

## 30.7 PO Preview / Confirm API

### `POST /api/orders/drafts/{draftId}/preview`

Preview時に以下をValidationする。

- Order Qty > 0の商品が1件以上存在すること
- 必要なHeader情報
- Unit Price等の必要データ

Preview表示のみではStatusを変更しない。正式なConfirm操作によって`DRAFT → READY_TO_ORDER`へ遷移させる。Confirm用Endpointの具体名は実装設計時に決定する。

## 30.8 Demo Send API

### `POST /api/orders/{orderId}/demo-send`

実メールは送信せず、`READY_TO_ORDER → SENT → AWAITING_SUPPLIER`へ遷移させ、`DEMO_SENT`および`STATUS_CHANGED`等をAudit Trailへ保存する。

UIには以下のような日本語メッセージを表示する。

> デモモードのため、実際のメールは送信されていません。

## 30.9 Supplier Response API

- `GET /api/orders/{orderId}/supplier-response`
- `PUT /api/orders/{orderId}/supplier-response`

Confirmed Qty、Confirmed Delivery、Response Noteを保存し、Original値との差分をBackendで判定する。

回答の一時保存と回答確定は別の業務操作として扱う。Endpointを分離するか、明示的なActionを持たせるかは実装設計時に決定する。確定操作では必要回答の完了条件をValidationする。

`confirmedQty: 0`と`confirmedQty: null`をAPI上でも区別する。

| 条件 | Backend処理 |
|---|---|
| Confirmed Qty != Ordered Qty | `QUANTITY_CHANGED` |
| Confirmed Delivery != Requested Delivery | `DELIVERY_CHANGED` |
| 一部SKUのみ回答 | `PARTIAL_CONFIRMATION`を設定し、`AWAITING_SUPPLIER`を維持 |
| 必要回答が全て揃った | `SUPPLIER_CONFIRMED`へ遷移 |

`[TBD - CUSTOMER REVIEW]` 「必要回答が全て揃った」の正式定義は顧客確認後に確定する。

## 30.10 Order History API

- `GET /api/orders/history`
- `GET /api/orders/{orderId}`
- `GET /api/orders/{orderId}/events`

Filter：

- Supplier
- Brand
- PO No.
- Status
- Order Date

`audit_event`をOrder History / Timelineのデータソースとして利用する。

## 30.11 Status変更APIの禁止

`[PROTOTYPE DECISION]` `PUT /api/orders/{id}/status`のような汎用Status変更APIは原則作成しない。

StatusはCreate Draft、Confirm Order、Demo Send、Save Supplier Response等の業務操作の結果としてBackendが遷移させ、Frontendから任意のStatusへ変更できないようにする。

## 30.12 APIと日本語UIの分離

`[PROTOTYPE DECISION]` APIは原則として日本語表示文言を返さず、英語の内部Codeを返す。

```json
{
  "status": "AWAITING_SUPPLIER",
  "attention": ["QUANTITY_CHANGED", "DELIVERY_CHANGED"]
}
```

Frontend i18n Resourceで、`AWAITING_SUPPLIER`を「メーカー回答待ち」、`QUANTITY_CHANGED`を「数量変更あり」、`DELIVERY_CHANGED`を「納期変更あり」へ変換する。Backend / API / Database / Internal Codeは英語、ユーザー表示は日本語という既存方針を維持する。

---

# 31. 7画面の責務と画面横断UI/UX原則

## 31.1 画面責務

| 画面 | 責務 |
|---|---|
| Dashboard | 仕事を発見し、対象業務へ遷移する |
| Order Candidate List | 発注候補を比較・選択する |
| SKU Detail | 発注判断に必要な詳細情報を確認する |
| Order Draft | 人が最終発注数量・希望納期等を決定する |
| PO Preview | メーカー向け発注内容を最終確認・確定する |
| Supplier Response / Order Update | メーカー回答とOriginal発注内容との差異を登録・確認する |
| Order History | 発注から回答・変更までの履歴を追跡する |

`[PROTOTYPE DECISION]` 各画面の責務をまたぐ編集機能を安易に追加しない。

## 31.2 画面横断UI/UX原則

- Order Candidate ListではOrder Qtyを原則編集しない。
- Draft作成時に`Order Qty = Recommended Qty`を初期値とする。
- 正式なOrder Qty編集はOrder Draftで行う。
- PO Previewは原則READ ONLYとする。
- Preview表示と発注内容確定を分離する。
- 発注内容確定とメーカー送信を分離する。
- メーカー送信前は`READY_TO_ORDER → DRAFT`へ戻せる方向とする。
- `AWAITING_SUPPLIER`以降はDraftへ戻さない。
- Confirmed Qty = 0と未回答 / nullを分離する。
- Supplier Responseを複数回保存可能とする。
- History画面は原則READ ONLYとする。
- Attentionは確認済み状態を保持可能とする。
- Legacyに存在しない履歴を推測して生成しない。

## 31.3 保存・確定・送信の分離

```text
Save Draft
  ↓
PO Preview（Status変更なし）
  ↓
発注内容を確定
DRAFT → READY_TO_ORDER
  ↓
Demo Send
READY_TO_ORDER → SENT → AWAITING_SUPPLIER
  ↓
回答を保存（Partial可）
  ↓
メーカー回答を確定
AWAITING_SUPPLIER → SUPPLIER_CONFIRMED
```

各段階で必要なValidationを適用し、業務操作とStatus遷移を明確に対応させる。
