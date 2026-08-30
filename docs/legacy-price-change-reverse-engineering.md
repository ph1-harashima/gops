# Legacy Price Change Reverse Engineering（Phase 7-J）

**Status**: Docs Only。Frontend / Backend / DB Migration / Legacy Sourceへの変更は一切行っていない。本Documentは次Phase候補領域のSource調査結果であり、価格変更機能の実装ではない（実装は本Phaseでは禁止）。

**目的**: 発注(Ordering)機能に続く次期開発領域として、Gulliver社が2026/08/26打ち合わせ（`docs/G-Sys_mtg_20260826_02.pptx`）で要望した「価格変更業務」について、Legacy G-SYS（`phasep-gulliver`）のSourceを調査し、現行仕様・データ構造・既存の業務ルールを整理する。あわせて、価格変更以外に打ち合わせで挙がった改善テーマも0章で概観し、次Phase選定の位置づけを示す。

**本Documentの性質**: `docs/customer-review-decision-package.md`等の「QA Document」ではなく、`docs/legacy-procurement-workflow-reverse-engineering.md`と同じ位置づけの**Source Reverse Engineeringレポート**である。したがって「CUSTOMER REVIEW」セクションで顧客確認事項を洗い出す形式は踏襲するが、既存3 QA Documentへの統合は行わない（内容が異なるためQA Documentの4分類とは別体系として独立させる）。

---

## 0. 発注以外の次期開発領域サーベイ（5分野）

2026/08/26打ち合わせ議事メモ（`docs/G-Sys_mtg_20260826_02.pptx`、全15枚）を読み直し、発注(Ordering)以外にGulliver社が言及した改善テーマを整理した。打ち合わせ内の該当スライドと、対応するLegacy既存機能・Portal実装状況を突き合わせる。

| # | 領域 | Gulliver打ち合わせでの言及（スライド） | Legacy既存機能 | Portal実装状況 | Source RE要否 | Customer Review要否 | 他システム依存 |
|---|---|---|---|---|---|---|---|
| A | **価格変更** | Slide 11「価格変更業務」: 一括変更／将来日予約／利益率・赤字警告／変更履歴 | `MsPriceListImportBatch`（Excel Import）／`PriceList.java`（Excel Export）／`Formula.java`（利益率計算）。History・Effective Date・承認Workflowは無し（1〜9章で詳述） | 未実装（Portal側に価格関連画面・APIは一切無い） | **要（本Documentで実施）** | 要（11章） | Tempostar（価格関連の一部バッチがSelenium経由でTempostar画面を操作、13.3章） |
| B | **在庫・販売実績データの更新** | Slide 6「在庫・販売実績データの更新」: 月替わり後のタイムラグ、日次データ利用可否 | `MS_STK.UPDATE_DATETIME`が在庫更新・販売実績更新の両方で書き換わり分離不可（`G-SYS_Online-Ordering_Prototype_Requirements.md`記載の`[CONFIRMED]`事項、Phase 0.5で確認済み） | Dashboard等でPortalが参照する形で部分実装済み（発注候補選定に利用）。データ更新頻度自体の改善は未着手 | 未実施（発注機能側で部分調査済みのため差分調査で足りる可能性が高い） | 要（更新タイミング・日次データ可否はGulliver運用判断） | Tempostar（在庫連携）、EC各モール |
| C | **発注・請求・仕入・売上・粗利** | Slide 12「発注・請求・売上・仕入データ」: 請求／仕入／売上／ブランド別実績／粗利の確認・分析 | `costThisMonthAvg`等のCostフィールドは`PrStkInReportImportBatch`等で更新。請求・仕入・売上を横断する分析画面はLegacyにも存在しない可能性が高い（未調査） | 未実装。`G-SYS_Online-Ordering_Prototype_Requirements.md`のDashboard章で「粗利分析」等は明示的にPrototype対象外（9/17 Scope外）と記載済み | **未実施（次々Phase候補、本Documentでは価格Costフィールドの範囲のみ扱う）** | 要 | 未調査 |
| D | **倉庫・物流システム連携** | Slide 13「倉庫・物流システムとの連携」: 入荷数量・入荷予定日変更の倉庫側反映 | Logizero連携が既存（`CLAUDE.md`記載の外部連携）。詳細はSource未調査 | 未実装 | 未実施 | 要（既存物流システムとの連携方法確認） | Logizero |
| E | **外部ツール・システム連携（EC/Tempostar等）** | 打ち合わせでは独立テーマとして明示されていないが、Legacy調査中に判明（本Document 13章） | `MsItem`にRakuten C15/Lucida/Peewee、Yahoo（3種）、Amazon、Qoo10、Ponpare、Wowmaの**モール別売価フィールドと変更フラグ**が既存。`SlTempostarPriceChangeDownloadBatch`はSeleniumでTempostar管理画面を自動操作し価格変更情報をダウンロード | 未実装 | 未実施 | 要（モール別価格運用の実態） | Tempostar、Rakuten、Yahoo、Amazon、Qoo10、Ponpare、Wowma |

**結論**: 5分野のうち、価格変更（A）はGulliver社からの直接要望（Slide 11）かつLegacyに既存のImport/Export/Formula基盤があり調査難易度も相対的に低いため、本Phaseで先行してSource Reverse Engineeringを行う（本Document 1〜16章）。B〜Eは今回Sourceの深追いはせず、0章の概観に留める（Source Reverse Engineering自体は次々Phase以降）。

---

## 1. Executive Summary

- Legacy G-SYSにおける価格変更は、**画面上の編集機能ではなく「Excel Export → 手作業編集 → Excel Import」のRound Tripでのみ行われる**（Official PO運用と同型のパターン）。
- 価格を保持するのは`MS_ITEM`（Legacy Entity: `MsItem`、SKU単位）と`MS_ITEM_GRP`（Legacy Entity: `MsItemGrp`、商品グループ単位）の2テーブルで、**Import時にDBの現在値へ直接上書き**される。Price History（変更履歴）テーブルは存在しない。
- 価格変更に**Effective Date（適用日）・Future Price（将来価格）の概念はSource上一切存在しない** — Importした瞬間に即時反映される。
- 「変更幅が一定範囲を超える場合はApproval（承認）が必要」という業務ルールは存在するが、これは**Portal的な画面上のApproval Workflowではなく、Import Excelファイル自体に`APPROVAL`列（値`ACCEPT`固定）を担当者が事前に手入力しておく方式**であり、電子承認の記録・証跡は残らない。
- 利益率（`PROFIT_RATE_SELL`/`PROFIT_RATE_SALE`）は`Formula.java`で自動計算されているが、**マイナス利益率（赤字価格）を検知してエラー・警告にするロジックはSource上見つからなかった**（単なる参考値計算のみ）。
- Gulliver社がSlide 11で要望した4項目（複数商品の一括変更／将来日の価格変更予約／利益率・赤字警告／変更履歴）は、**「複数商品の一括変更」のみItem Group単位で部分的に現行踏襲可能**、残り3項目（将来日予約・赤字警告・変更履歴）は**Legacyに存在しない、新規のBusiness Rule**であることが判明した（7章）。

---

## 2. 価格変更の全体フロー（現行運用の推定）

Source上に画面（Controller/UI）は存在しないため、以下はバッチの入出力仕様から逆算した**推定フロー**である（実際の運用担当者・頻度・トリガーはSourceからは分からず、Ernest/Gulliver確認が必要 — 11章参照）。

```
1. 担当者がStock List画面等でSKUを選択
   → `/api/export/{type}` (type=priceList) を呼び出し
   → PriceList.java が MsItem/MsItemGrp の現在価格を Excel へ Export
2. 担当者がExcelファイルを手元で編集（新しい価格を入力）
   → 変更幅が MS_COMM 設定の許容範囲を超える場合、
      APPROVAL列に "ACCEPT" と手入力しないとImport時にエラーになる
3. 編集済みExcelを Import Folder（PRC_LISTコード）へ配置
4. MsPriceListImportBatch がバッチ実行時に Import Folder を読み込み、
   MS_ITEM / MS_ITEM_GRP を直接UPDATE（即時反映、Effective Date無し）
5. 実行結果メール（PRICE LIST IMPORTのタイトル）が SYS_SEND_MAIL 経由で送信される
```

この3〜4のExcel配置〜バッチ取込という構造は、`PrOfficialPoImportBatch`（Official PO Import）と全く同じアーキテクチャパターンである（`AbstImportBatch`を継承する共通基盤）。

---

## 3. Excel Export側: `PriceList.java`

- パッケージ: `jp.ne.glv.utilities.export.PriceList`（`ExcelExportable`実装、`ArrivalList`/`StockList`等と同じExportクラス群の一つ）。
- 呼び出し元: `UsersController.java`の汎用Export Endpoint `POST /api/export/{type}`（`type=priceList`のcaseで生成）。UsersControllerという命名だが、実際は複数種類のExcel Export（EC、priceList、ArrivalSchedule、ArrivalScheduleCost、pageCreateDirection等）を集約した共通Controllerである。
- 入力データ: `MsStkRepository.getStockList(...)`（Stock List画面と同じクエリ）で取得したSKU一覧＋該当する`MsItemGrp`。
- 出力: 現在のMS_ITEM/MS_ITEM_GRP価格をレポートとして書き出すExcelファイル（Conditional Formatting等の装飾処理あり）。**Importフォーマットと完全に同一列構成かは未確認**（Export用とImport用で別クラスであり、列名一致は運用上の慣習に依存している可能性がある — 11章 Q-P2）。

---

## 4. Excel Import側: `MsPriceListImportBatch.java`

- `AbstImportBatch`を継承した Spring Batch（`PrOfficialPoImportBatch`と同一基盤）。
- Import対象列（Excelヘッダ名で判定、順序非依存）:

| 列名 | 内容 | 必須 |
|---|---|---|
| `ITEM_CD` | SKUコード（`grp`行の場合はItem Group Code） | 必須 |
| `PRC_LIST` | List Price（希望小売価格、税込） | 任意（値があれば数値検証） |
| `PRC_SELL_W_TAX` | 通常販売価格（税込） | 任意 |
| `PRC_SALE_W_TAX` | セール価格（税込） | 任意 |
| `PRC_GLV_B2B` | GLV B2B向け価格 | 任意（Item Group行では対象外） |
| `PRC_WS_FOR_KOREA` | 韓国向け卸価格 | 任意（Item Group行では対象外） |
| `FREE_SHIP_FLG` | 送料無料フラグ（`Y`/`N`/`SC`） | 任意 |
| `SALE_FLG` | セール対象フラグ（`Y`/`N`） | 任意（Item Group行では対象外） |
| `APPROVAL` | 変更幅超過時の承認済みマーク（`ACCEPT`固定文字列） | 変更幅超過時のみ必須 |
| `DIFFERENCE` | 変更差分（担当者が事前計算して入力する数値、単位不明） | 変更幅超過判定に使用 |

- **行種別**: 1列目（システム使用列）の値が`grp`ならItem Group行（`MS_ITEM_GRP`をUPDATE）、`ftr`ならフッター行（読込終了）、それ以外はSKU行（`MS_ITEM`をUPDATE）。
- **承認range判定ロジック**（`execute()`メソッド、136〜138行目・418〜442行目）:
  - `MS_COMM`（`CATE_ID='FILE_IMP', CODE_ID='PRC_LIST'`）の`VAL5`/`VAL6`列に許容変更幅の下限・上限を設定。
  - Excelの`DIFFERENCE`列の値がこの範囲外なら`needApproval=true`となり、`APPROVAL`列が`ACCEPT`（大文字小文字を無視して比較）でなければエラー行としてスキップされる。
  - **`DIFFERENCE`は担当者がExcel上で事前に手計算して入力する値であり、Batch自身が旧価格との差分を自動計算する処理は存在しない**（445〜479行目、旧ロジックの残骸が丸ごとコメントアウトされている — Legacy自体が過去にBatch側自動計算からExcel入力方式へ実装変更した形跡）。
- **DB更新**（487〜654行目）: `MsItem`/`MsItemGrp`のフィールドへ直接`set...()`し`save()`。UPDATE時に`updateUserId`/`updateDatetime`は記録するが、**変更前の値を別テーブルへ退避する処理は無い**（＝上書きのみ、履歴喪失）。
- Item Group行では`Formula`計算を経由せず生値をそのままセットするのに対し、SKU行では`Formula`クラスで`PRC_LIST`（税抜換算）・`PRC_SELL`・`PROFIT_RATE_SELL`等の派生値を都度再計算してからセットする（619〜650行目）——Item GroupとSKUで更新ロジックが非対称である点に注意。

---

## 5. 価格関連フィールド一覧（`MsItem.java`）

| カテゴリ | フィールド | 備考 |
|---|---|---|
| 税込入力値 | `listPrcWTax`, `prcSellWTax`, `prcSaleWTax` | Excel Importの直接入力元 |
| 税抜計算値 | `prcList`, `prcSell`, `prcSale` | `Formula`が税込値から`1.10`で割り戻して算出（消費税10%決め打ち、旧8%ロジックはコメントアウト済み） |
| モール別派生価格 | `prcFreeShipGt4999WTax`, `prcSellP15WTax`, `prcSaleP15WTax`, `prcSellAmzWTax`, `prcFreeShip` | `Formula`が各モールの手数料・送料ルールに応じて算出する参考値（3.項参照） |
| 利益率 | `profitRateSell`, `profitRateSale` | `Formula.PROFIT_RATE_SELL/SALE()`。販売価格とCostの差分を販売価格で割った比率。**マイナス値（赤字）になっても例外・警告にはならない**（6章） |
| B2B/海外卸 | `prcGlvB2B`, `prcWsForKorea` | Import対象列に存在、SKU単位のみ（Item Group単位の設定は無い） |
| Cost | `costLastMonthAvg`, `costThisMonthAvg`, `costLatestStkIn` | 価格Importの対象外。`PrStkInReportImportBatch`（入荷実績Import）等、別バッチが更新（6章） |
| EC個別売価 | `rakC15SalePrice`, `rakLucidaSalePrice`, `rakPeeweeSalePrice`, `yahooSalePrice`, `yahooLucidaSalePrice`, `yahooPeeweeSalePrice`, `amazonSalePrice`, `qoo10SalePrice`, `ponpareSalePrice`, `wowmaSalePrice` | モールごとの個別価格。対応する`***ChangedFlag`（Boolean）が各モールに存在し、変更検知フラグとして機能している模様（13章） |

---

## 6. 利益率計算ロジック（`Formula.java`）

- `PROFIT_RATE_SELL(PRC_SELL, COST_THIS_MONTH_AVG, FREE_SHIP_FLG, SHIP_FEE, PRC_SELL_W_TAX)`: `(販売価格 - 当月平均原価) / 販売価格`（送料無料の場合は送料を差し引く分岐あり）。
- `PROFIT_RATE_SALE(...)`: セール価格版の同等ロジック。
- **Costが0または未設定の場合は`null`を返す**（ゼロ除算回避）だけで、価格がCostを下回る（結果がマイナス）場合の特別処理は無い —— 計算結果は保存されるだけで、UI/Batchのどちらにも「赤字警告」に相当するチェック・エラー・メール通知は見当たらない。
- Cost自体（`costThisMonthAvg`）は価格変更のInput/Outputの対象外であり、別系統のバッチ（入荷実績取込等）で更新される値をそのまま参照する。したがって「価格変更のたびに利益率が再計算される」のは事実だが、それは結果表示用の副産物であり、業務ルールとしての警告機構ではない。

---

## 7. Gulliver要望（Slide 11）とLegacy Gapの分類

打ち合わせ議事メモSlide 11の原文: **「複数商品の一括変更／将来日の価格変更予約／利益率や赤字となる価格への警告／変更履歴」**。それぞれについて、Source調査結果に基づき分類する（「勝手に新Business Ruleを決めない」の原則により、Legacyに存在しないものは全て「要Gulliver Future Decision」として扱い、実装方式は提案しない）。

| Gulliver要望 | Legacy現行仕様 | 分類 | 根拠 |
|---|---|---|---|
| **複数商品の一括変更** | **部分的に既存**: `MS_ITEM_GRP`（Item Group）単位であれば、1回のExcel Import（`grp`行1行）で所属する複数SKUに影響する価格を一括設定できる（ただし`MsItemGrp`自体は独立したUPDATE対象であり、配下の個別`MsItem`へ自動伝播する処理は見つかっていない — 未確認点として11章に記載）。SKUを跨いだ「複数商品を選んで一括変更」という単位はItem Group以外には存在しない | **既にLegacyに存在（Item Group単位）／SKU横断の任意選択一括変更は存在しない** | 4章・5章 |
| **将来日の価格変更予約** | Effective Date・Future Priceに相当するColumn/Entity/BatchはSource全体で0件（`grep`確認済み）。Importは常に即時反映 | **Legacyに存在しない（新規Business Rule）** | 4章・1章 |
| **利益率や赤字となる価格への警告** | 利益率の計算自体はある（6章）が、閾値判定・警告・エラー化のロジックは0件 | **計算ロジックは存在するが「警告」は存在しない（新規Business Rule）** | 6章 |
| **変更履歴** | Price History相当のTable/Entity/Batchは0件。`updateUserId`/`updateDatetime`のみ残り、変更前値・差分・変更者の意図は残らない | **Legacyに存在しない（新規Business Rule）** | 4章・1章 |

**Sourceだけでは判断できない事項**（Ernest/Gulliver確認が必要、実装方式の決定は含まない）:
- Item Group単位の一括変更が実際の業務でどの程度使われているか（Ernest/Gulliver Current Operation）。
- 「複数商品の一括変更」がItem Group単位で十分か、それとも真に任意のSKUを跨いだ一括選択が必要か（Gulliver Future Decision）。

---

## 8. Brand / Supplier / SKU の関係

- `MsItemGrp`は商品グループ単位（`itemGrpCd`）でList Price/Sell Price/Sale Price/B2B Price/Free Ship Flagを保持する（Brand単位ではない — Item Group ≠ Brand。Item GroupとBrandの対応関係はSourceからは特定できず、Master構造の詳細調査が必要）。
- Supplier（仕入先）との関連は、価格Import/Export双方の列に一切存在しない。価格はSupplierからの仕入原価（Cost）とは別立てで管理されており、Costとの紐付けは「同一`MsItem`行に両方のフィールドがある」という間接的な関係のみ。

---

## 9. Currency / 為替

- `Formula.java`に`CUSTOM_CCY_RATE`（為替レート）というフィールドが存在するが、これは`CUST_AMT_JPY()`（`CUSTOM_AMT × CUSTOM_CCY_RATE`）でのみ使用されており、変数名（`CUSTOM_*`）から関税・通関計算（Customs）関連のFormulaと推測される。価格変更（Sell/Sale Price）の計算パスとは別系統であり、**価格変更に為替レートが関与する形跡は無い**。

---

## 10. Permission / Role

- `MsPriceListImportBatch`自体はBatch（サーバ側で定期実行または手動起動されるコマンド）であり、Batch内にRole/Permissionチェックのコードは存在しない —— 誰でもImport Folderへファイルを配置できればDBが更新される構造は、Official PO Importと同型。
- Export側（`/api/export/{type}`エンドポイント）にRole制限のアノテーションがあるかどうかは、Spring Security設定ファイル側の調査が必要であり、本Documentの調査範囲（Batch/Formula/Entity中心）では確認していない（11章 未調査事項）。

---

## 11. Source調査で確認できなかった事項（未調査・要追加確認）

以下は本Documentの調査範囲では判明せず、次のPrice Change Reverse Engineering深掘り、またはErnest/Gulliverへの確認が必要な事項である。実装方針の提案は行わない。

- **Q-P1**: Import Folder（PRC_LISTコード）の実際の配置・トリガー方法（Official PO Importと同じ手動配置か、専用の運用フローがあるか）。
- **Q-P2**: `PriceList.java`（Export）と`MsPriceListImportBatch`（Import）の列フォーマットが実際に一致しているか（Export結果をそのまま編集してImportに使う運用を前提にしているか、それとも別物か）。
- **Q-P3**: `MsItemGrp`（Item Group）配下の個別`MsItem`へ、Group価格変更がどう反映される想定か（自動連動する別ロジックがコード外に存在するか、それとも運用上Group価格は参考値に留まるか）。
- **Q-P4**: Item GroupとBrandの対応関係（Master構造）。
- **Q-P5**: 価格変更の実施頻度・実施者・承認者（`APPROVAL`列への"ACCEPT"入力を誰がどう判断しているか）。
- **Q-P6**: `/api/export/{type}`エンドポイントおよびImport Batch起動のRole/Permission制限有無（Spring Security設定の追加調査が必要）。
- **Q-P7**: `SlTempostarPriceChangeDownloadBatch`がダウンロードするTempostar側の「価格変更」情報の内容・用途（Tempostar側で先に価格変更されG-SYSへ取り込む運用なのか、逆方向なのかは、13章時点でも未確定）。

---

## 12. Source-confirmed Facts（CONFIRMED一覧）

1. 価格変更のUI/Controller（画面からの編集機能）はSource全体に存在しない（`priceList` Export以外、価格編集APIは0件）。
2. 価格変更はExcel Import Batch（`MsPriceListImportBatch`）による即時DB上書きのみで、Effective Date・Future Price・Scheduled Changeの概念はSource上0件。
3. Price History（変更履歴）に相当するTable/Entity/Batchは0件。
4. 変更幅超過時の「承認」は、電子承認Workflowではなく、Excelファイル自体への`APPROVAL`列手入力（値`ACCEPT`固定）というファイルベースの自己申告方式。
5. 利益率（`PROFIT_RATE_SELL`/`SALE`）は自動計算されるが、赤字（マイナス値）に対する警告・エラー処理は0件。
6. `MS_ITEM_GRP`（Item Group）単位の一括価格設定機能は既存（Excel `grp`行）。
7. EC各モール（Rakuten/Yahoo/Amazon/Qoo10/Ponpare/Wowma）向けの個別売価フィールドと変更検知フラグが`MsItem`に既存。
8. `SlTempostarPriceChangeDownloadBatch`はSelenium/ChromeDriverでTempostar管理画面を自動操作し、価格変更関連情報をダウンロードする（Web API連携ではなく画面操作の自動化）。

---

## 13. External連携の補足（0章 Area Eの詳細）

### 13.1 EC個別売価（5章参照）
Rakuten（C15/Lucida/Peewee）、Yahoo（本体/Lucida/Peewee）、Amazon、Qoo10、Ponpare、Wowmaの計10フィールド＋各`ChangedFlag`が`MsItem`に存在する。これはPortal側では一切扱っていない領域であり、価格変更機能を将来Portal化する場合、G-SYS内部価格（Sell/Sale/List）とEC個別売価の関係（同期方式・優先順位）を別途整理する必要がある。

### 13.2 Cost更新経路
`costThisMonthAvg`等は`PrStkInReportImportBatch`（入荷実績Import）等、価格変更Batchとは別のBatchが更新する。価格変更機能を将来設計する際、「Costがいつ更新されるか」は利益率計算のタイミングに直接影響するため、Area C（発注・請求・仕入・売上・粗利）の調査と合わせて確認が必要。

### 13.3 Tempostar連携
`SlTempostarPriceChangeDownloadBatch`および`SlTempostarPriceChangeDownloadSecondBatch`は、Selenium WebDriverでTempostar管理画面にログインし、価格変更に関する情報をダウンロードするバッチである。API連携ではなく画面操作の自動化（スクレイピング）である点は、将来の本番設計において考慮すべき技術的制約となる（Tempostar側の画面変更に弱い）。本Documentでは両バッチの詳細フロー（ダウンロード後のデータの用途、G-SYS側への反映有無）までは調査していない（11章 Q-P7）。

---

## 14. Final Classification Table

| # | 論点 | 分類 | 実装Blocker |
|---|---|---|---|
| P-1 | 複数商品の一括変更 | Legacy既存（Item Group単位）。SKU横断選択の要否はGulliver Future Decision | 要検討 |
| P-2 | 将来日の価格変更予約 | Legacyに存在しない。新規Business Rule（Gulliver Future Decision） | 要決定 |
| P-3 | 利益率・赤字警告 | 計算ロジックのみ既存。警告閾値・対象は新規Business Rule（Gulliver Future Decision） | 要決定 |
| P-4 | 変更履歴 | Legacyに存在しない。新規Business Rule（Gulliver Future Decision） | 要決定 |
| P-5 | Item Group⇄SKUの価格連動 | Source上未確認（Q-P3） | 要Source追加調査 |
| P-6 | 承認方式（ファイル内`APPROVAL`列） | Legacy既存の実運用実態がErnest/Gulliverでも未確認（Q-P5） | 要確認 |
| P-7 | EC個別売価との関係 | Portal側スコープ外の既存Legacy機能（13.1章） | 次々Phase |
| P-8 | Tempostar連携の詳細 | 未調査（Q-P7） | 次々Phase |

---

## 15. 完了報告用サマリ

- 変更したFrontend/Backend/DB Migration/Legacy Source: **0件**（本Documentのみ新規作成）。
- 調査したLegacyファイル（主要）: `MsPriceListImportBatch.java`, `MsPriceListImportService.java`, `PriceListExcelPOJO.java`, `PriceList.java`(export), `Formula.java`, `MsItem.java`, `MsItemGrp.java`, `SlTempostarPriceChangeDownloadBatch.java`, `SlTempostarPriceChangeDownloadSecondBatch.java`, `PrStkInReportImportBatch.java`, `UsersController.java`（Export Endpoint部分）。
- Gulliver要望4項目中、Legacyに既存: 1項目（一括変更、Item Group単位のみ）。新規Business Rule: 3項目（将来日予約／赤字警告／変更履歴）。
