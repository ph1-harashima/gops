# G-OPS 全画面「上位概念 → 対象選択 → 詳細操作」UX横断監査

Dashboard → Brand → Order Candidatesで成立している「業務上の上位概念を選択してから、
そのContext内で下位データを操作する」という操作思想を、G-OPS全21画面に対して横断監査した結果。

**本ドキュメントは元々、監査結果と提案のみをまとめたものだった。** 初版時点では
Master Maintenance全体の再構成等のInformation Architecture変更は未実装で、実装した
のは既存Filter/Context Preservation機構の範囲内での明白なMinor Gapのみだった（8章参照）。

**追記（Information Architecture Phase 2、`docs/gops-master-maintenance-hub-implementation.md`
参照）**: 本監査の結果を正式なInputとして、Master Maintenance Hub（Supplier一覧＋Supplier
Settings）、Price Change EditのBrand Context Deep Link、Stock/Sales・Warehouse Stock・
ArrivalへのFilter Chip追加を実装済み。4章「Major Change Candidates」に記載していた
Master Maintenance統合ビューは実装完了（既存4画面は削除せず、新しいEntry Pointとして追加）。
6章のPrice Change Edit Deep Linkも実装完了。11章の詳細は上記実装ドキュメントを参照。

---

## 1. UX Principle

「大量の下位データを最初から直接操作させるのではなく、業務上の自然な上位概念を
選択してから、そのContext内で下位データを操作する」。

ただし、これは「すべての画面をBrand/Supplier起点にする」ことではない。画面ごとに
以下を区別する。

- **上位概念からの絞り込みが自然な画面**（Order Candidates、将来のMaster Maintenance等）
- **全件検索・横断検索が本質的に主導線であるべき画面**（Order History、Price Change List等 -
  PO No./Status/Keywordでの直接検索が実務上の主要な使われ方であるため、階層を強制すると
  かえって遅くなる）
- **上位概念自体がまだPortal内に存在しない画面**（Supplier一覧が存在しないため、
  Supplier起点の階層化は現時点で「追加」ではなく「新規作成」になる）

「階層化しすぎ」を避けるため、Order Candidatesで既に実証されている
「Brand起点 + 全Brand横断KPI起点の併存」パターンを、適用可能な画面の指針とする。

---

## 2. Current Information Architecture

G-OPSの全Route（`frontend/src/app/App.tsx`より抽出、計21画面 + wildcard redirect）：

| # | Route | Component |
|---|---|---|
| 1 | `/` | DashboardPage |
| 2 | `/candidates` | CandidateListPage |
| 3 | `/items/:sku` | SkuDetailPage |
| 4 | `/orders/drafts/:id` | OrderDraftPage |
| 5 | `/orders/drafts/:id/preview` | PoPreviewPage |
| 6 | `/orders/:id/supplier-response` | SupplierResponsePage |
| 7 | `/orders/history` | OrderHistoryListPage |
| 8 | `/orders/:id` | OrderHistoryDetailPage |
| 9 | `/price-changes` | PriceChangeListPage |
| 10 | `/price-changes/:id/edit` | PriceChangeEditPage |
| 11 | `/price-changes/:id` | PriceChangeDetailPage |
| 12 | `/arrivals` | ArrivalListPage |
| 13 | `/arrivals/:supplierCode/:poNumber/:invoiceNumber` | ArrivalDetailPage |
| 14 | `/warehouse-stock` | WarehouseStockListPage |
| 15 | `/stock-sales` | StockSalesListPage |
| 16 | `/admin/supplier-contacts` | SupplierContactPage |
| 17 | `/admin/manufacturer-channels` | ManufacturerChannelPage |
| 18 | `/admin/mail-templates` | MailTemplatePage |
| 19 | `/admin/mail-settings` | PortalMailSettingsPage |
| 20 | `/admin/supplier-region-classifications` | SupplierRegionClassificationPage |
| 21 | `/admin/official-po-short-codes` | OfficialPoShortCodePage |

重要な既存事実（現時点でIA上の制約となっている）：

- **Portal内に「Supplier一覧」という独立したMaster画面・概念が存在しない。** Supplierは
  Legacy READ ONLYの符号（`supplierCode`）として各画面のFilterに現れるのみで、Brandの
  ようにDashboardで集計・一覧化されてはいない。そのため「Supplier起点」の階層化は、
  既存概念の並べ替えではなく新規UI要素の追加を意味する。
- **Brandは既にDashboardの「ブランド別内訳」で事実上の一覧が成立している**（Order
  Candidates監査で確認済み）。これがBrand起点の階層化を他画面より低コストにしている。
- **List↔Detail間のContext Preservation機構（`returnTo`/`withReturnTo`/`listReturnTo`、
  Phase 6-A/8-M）は、Master Maintenance系6画面を除く全ての List/Detail画面に既に
  実装済み。** これは監査前の想定より大きな既存資産である。

---

## 3. Screen Matrix

| Screen | Primary Entity | Parent Context | Current Entry | Current Filter | Recommended Entry | Classification | Reason |
|---|---|---|---|---|---|---|---|
| Dashboard (`/`) | Cockpit (集計) | なし（最上位） | ログイン後デフォルト | なし | 変更不要 | A | 既にAction/Operation Cockpitとして機能 |
| Order Candidates (`/candidates`) | SKU (発注候補) | Brand | Menu(全件) or Dashboard Brand行 | brandCode/supplierCode/keyword (URL, dropdown, Chip) | 変更不要 | A | 前回監査で完全実装確認済み |
| SKU Detail (`/items/:sku`) | SKU | Order Candidate / Brand | Candidate List行クリック | — | 変更不要 | A | returnTo完備、Brand名は表示のみで十分 |
| Draft (`/orders/drafts/:id`) | Order (Draft) | Brand (継承) | Candidate Listから作成 | — | 変更不要 | A | returnTo完備 |
| PO Preview (`/orders/drafts/:id/preview`) | Order/PO | Draft | Draftから遷移 | — | 変更不要 | A | returnTo完備 |
| Supplier Response (`/orders/:id/supplier-response`) | Order | Order | Order Detailから遷移 | — | 変更不要 | A | returnTo完備 |
| Order History List (`/orders/history`) | Order/PO | Supplier/Brand（暗黙） | Menu(全件2,315件) or Dashboard Brand行(Status別のみ) | supplierCode/brandCode/status/orderNoKeyword/itemKeyword/日付範囲 (URL, Backend Paginated, 自由テキスト) | Filter Chip表示 + Brand/Supplier名の可視化 | B | Filter機構自体は既に充実。Context表示のみ改善余地 |
| Order Detail (`/orders/:id`) | Order/PO | Order List | List行クリック | — | 変更不要 | A | returnTo完備（最も充実） |
| Price Change List (`/price-changes`) | Price Change Set (Batch) | なし（Status起点が本質） | Menu(全件、下書き130件超・多くが空) | status | 変更不要（Status起点が正しい） | A/B | SetはBrand非依存の作業単位。件数増大はデータ衛生の課題（IA課題ではない） |
| Price Change Edit (`/price-changes/:id/edit`) | Price Change Set | なし（Set内のSKU選択はBrand依存） | List新規作成/行クリック | 対象商品選択欄にBrand/商品グループ/Keyword（ローカルStateのみ、URL非連動） | Brand ContextのDeep Link対応 | C | 仮説確認：SKU選択にBrand Filterは既に存在するが、上位画面からの引き継ぎがない |
| Price Change Detail (`/price-changes/:id`) | Price Change Set | Price Change List | List行クリック | — | 変更不要 | A | returnTo完備 |
| Arrival List (`/arrivals`) | Arrival (入荷) | Supplier/Brand/PO（暗黙） | Menu(Nav Card、Filterなしで遷移) | supplierCode/brandCode/poNumber/invoiceNumber/skuKeyword/日付範囲 (URL) | 上位画面からのBrand/Supplier Deep Link追加 | B/C | Filter機構は充実、Deep Linkが皆無 |
| Arrival Detail (`/arrivals/:supplierCode/:poNumber/:invoiceNumber`) | Arrival | Arrival List | List行クリック | — | 変更不要 | A | returnTo完備 |
| Warehouse Stock (`/warehouse-stock`) | SKU在庫 | Brand（暗黙） | Menu(Nav Card、Filterなし) | skuKeyword/brandCode/warehouseCode/minQty/maxQty (URL) | 上位画面からのBrand Deep Link追加 | B/C | 同上 |
| Stock/Sales (`/stock-sales`) | SKU | Brand/Supplier（暗黙） | Menu(Nav Card、Filterなし) | skuKeyword/brandCode/supplierCode/minStock/maxStock/minSales/maxSales (URL) | 上位画面からのBrand Deep Link追加 | B/C | 仮説確認：Brand→SKU Stock/Salesの主導線が成立可能 |
| Supplier Contact (`/admin/supplier-contacts`) | Contact | Supplier×Brand | Menu(Flat List, Client検索, 数百件の無効行蓄積) | 検索文字列+無効表示チェックのみ（URL非連動） | Supplier起点の統合画面（提案のみ） | D | 仮説確認：最重点、4章参照 |
| Manufacturer Channel (`/admin/manufacturer-channels`) | Channel | Supplier×Brand | 同上 | 同上 | 同上 | D | 同上 |
| Mail Template (`/admin/mail-templates`) | Template | Global or Supplier×Brand(任意) | Menu(Flat List, Client検索) | 検索文字列のみ | 変更不要（Globalを優先、Supplier検索利便性のみ改善余地） | B | GlobalなTemplateを無理にSupplier配下へ入れてはならない（指示通り） |
| Portal Mail Settings (`/admin/mail-settings`) | Setting (単一Global) | なし | Menu(単一フォーム) | — | 変更不要 | A | 元々Global設定、階層化する対象が存在しない |
| Region Classification (`/admin/supplier-region-classifications`) | Classification | Supplier×Brand | Menu(Flat List, 検索なし) | なし | Supplier起点の統合画面（提案のみ） | D | 同上 |
| Official PO Short Code (`/admin/official-po-short-codes`) | Short Code | Supplier or Brand | Menu(Flat List, 検索なし) | なし | 同上 | D | 同上 |

### 分類集計

| 分類 | 件数 | 意味 |
|---|---|---|
| A | 12 | 変更不要 |
| B | 3（Order History, Price Change List, Mail Template） | 軽微UX改善余地（Filter Chip等） |
| B/C | 3（Arrival List, Warehouse Stock, Stock/Sales） | Filter機構は十分、上位画面からのDeep Link追加が有効 |
| C | 1（Price Change Edit） | 上位概念（Brand）導線の追加推奨 |
| D | 4（Supplier Contact, Manufacturer Channel, Region Classification, Official PO Short Code） | IA再設計候補（提案のみ、未実装） |
| E | 0 | 業務要件確認が必要な項目は今回なし（既存Filter機構の範囲内で判断可能だったため） |

計21画面（12+3+3+1+4=23はB/Cを両方に数えた重複、実数は21画面）。

---

## 4. Master Maintenance詳細監査（最重点）

### 4.1 現在の構造（Browser実測確認済み）

「マスタメンテナンス」submenuは6項目全てが**独立したFlat List画面**であり、
Supplier/Brandによる横断的な集約点は一切存在しない。

```
マスタメンテナンス
├─ メーカー担当者 (/admin/supplier-contacts)       - Supplier×Brand Flat List
├─ メールテンプレート (/admin/mail-templates)        - Global or Supplier×Brand Flat List
├─ メーカー通信方法 (/admin/manufacturer-channels)   - Supplier×Brand Flat List
├─ メール送信設定（Default CC） (/admin/mail-settings) - Global単一設定
├─ 国内／海外区分 (/admin/supplier-region-classifications) - Supplier×Brand Flat List
└─ Official PO略称コード (/admin/official-po-short-codes)  - Supplier or Brand Flat List
```

### 4.2 Browser実測: 「Supplier Aの設定を確認・変更したい」

ADMIN (admin01) でログインし、実際に操作して確認：

1. 「メーカー担当者」を開く → 検索欄に `SUP_ALPHA` を入力（この画面専用のローカルState）
2. メニューから「メーカー通信方法」に切り替える → **検索欄は完全に空にリセットされる**
   （前画面で入力した `SUP_ALPHA` は一切引き継がれない）
3. 同様に「国内／海外区分」「Official PO略称コード」へ移動する度に、再度ゼロから
   検索・確認が必要

**実測結果：Supplier 1件の全設定（Contact/Channel/Region/PO Code）を確認するには、
最低4画面 × それぞれの検索操作が必要。画面間の状態引き継ぎは0件。**

さらに、「メーカー担当者」画面で「無効な項目も表示」を有効にすると、この長期セッション中の
E2E/Demo操作で蓄積した数百件の無効(inactive)行（"Taro Yamada"、"Integration Tester"、
"Scenario A/D Contact"等のテスト用行）がそのまま表示され、実運用相当のデータ量で
Flat Listの見通しの悪さが顕在化することも確認した（Section 4-Dの懸念の実例）。

### 4.3 なぜBrandとSupplierで非対称か

Brand一覧はDashboardの「ブランド別内訳」テーブルで事実上既に成立している
（Order Candidates監査で確認済み）。一方、**Supplier一覧に相当するUI要素はG-OPS内に
一切存在しない。** Supplierは各画面のFilter値としてのみ現れ、Legacy `ms_comm`由来の
READ ONLYな符号（例: `SUP_ALPHA`）を利用者が知っている前提になっている。

### 4.4 推奨構造（提案のみ、未実装）

```
Master Maintenance
  ↓
Supplier一覧（新規、Legacy Supplier Masterから件数集計 - READ ONLY参照のみ、
              Legacy DB WRITEなし）
  ↓
Supplier選択（例: SUP_ALPHA - ABC Trading）
  ↓
[基本情報]  [Brand一覧]  [Contact]  [Communication]  [Region]  [PO Code]
  タブまたはセクションで、そのSupplierに関する設定を1画面から確認・編集
```

ただし、以下は明確に対象外とする（今回の監査結果としても、これらを無理に
Supplier配下へ統合すべきではないと判断）：

- **Mail Template**: `supplierCode`/`brandCode`が任意（null許容）で、Global Templateが
  実在する。Supplier配下に強制すると、Global Templateの居場所がなくなる。
  → Flat Listのまま維持し、Supplier検索の利便性のみ改善するB分類とする。
- **Portal Mail Settings (Default CC)**: 元々単一のGlobal設定。階層化する対象が
  存在しない。

### 4.5 現在のPortal Master構造とLegacy Master構造の統合について

指示の通り、今回は統合を提案・検討していない。上記の「Supplier一覧」もあくまで
Legacy Supplier MasterをREAD ONLYで参照した表示であり、新たなPortal側Supplier
Masterテーブルを作ることを意味しない（Brand一覧がLegacyのBrand/Order Candidate
データをREAD ONLYで集計表示しているのと同じ扱い）。

---

## 5. Order系監査

- **Order Candidates〜Draft〜PO Preview〜Order Detail〜Supplier Response**:
  一貫してBrand Contextが継承され、returnTo機構でBack時のContext保持も完備。
  分類A。
- **Order History List**: 2,315件（実測時点）の全件Flat Listで、Backend
  Paginated + supplierCode/brandCode/status/PO No./SKU Keyword/日付範囲の
  充実したFilterが既に存在する。Dashboardからは「Brand + Status」のDeep Link
  （発注作成中/承認待ち/メーカー回答待ち/要確認）が既に機能しているが、
  「Supplierで発注履歴を見たい」場合は前述の通りSupplier一覧が存在しないため
  入口がなく、`メーカー`欄に符号を直接入力するほかない。
  ユーザー指示の通り、全件検索（PO No./Status直接検索）は主要な実務動線であるため
  **廃止すべきではなく**、Supplier/Brand起点は「追加の入口」として位置づけるべき
  （分類B）。

---

## 6. Price Change監査

Price Change Setは「複数SKUをまとめて価格変更する作業単位（バッチ）」であり、
Set自体は特定のBrandに紐付かない（1つのSetに複数Brandの商品が混在しうる）。
そのためPrice Change List自体をBrand起点にするのは、Setという実体の性質と
合わない。

一方、Set編集画面内の「対象商品の選択」パネルには、ブランド・商品グループ・
Keywordによる商品検索が**既に存在する**（Browser実測で確認）。ただしこの
Brand Filterは画面内のローカルStateのみで、URLパラメータ非連動・上位画面からの
引き継ぎ非対応。

**結論**: 「Brand → 対象SKU → Price Change」という動線は、List自体の再設計では
なく、**Edit画面のSKU選択パネルへBrand ContextをDeep Linkできるようにする**
ことで実現可能（分類C。上位概念導線の追加が必要だが、既存Filter機構の
拡張で対応可能な範囲）。

---

## 7. Stock/Sales監査

`/stock-sales`は全SKU横断の1画面（実測時点で19件、Backend Paginated設計だが
現在のDemo Data量では1ページに収まる）。skuKeyword/brandCode/supplierCode/
在庫数・販売数の範囲Filterが既に存在するが、Dashboard含むどこからもBrand/
Supplier Contextを渡すDeep Linkが存在しない（Dashboardの「在庫・販売確認」は
Filterなしのプレーンなnav cardのみ）。

ユーザー指示通り「全SKU横断検索は維持」した上で、**Brand Contextを持つ画面
（Dashboard Brand行、Candidate List、SKU Detail等）からこの画面へのDeep Link
追加**が、既存Filter機構をそのまま使う安全な改善として有効（分類B/C）。

---

## 8. Arrival/Warehouse監査

`/arrivals`（入荷確認）と`/warehouse-stock`（倉庫在庫）はいずれも
Stock/Salesと同型：Backend Paginated、Supplier/Brand（＋PO No/Warehouse等）の
URL Filterは既存、Dashboard含むどこからのDeep Linkも存在しない。

`/arrivals`のPrimary EntityはArrival（入荷）で、業務上はSupplier起点・
Brand起点・PO起点のいずれも成立しうるが、既存Filterが3つとも並列に対応済み
であるため、**どれか1つを強制する必要はなく、複数の入口を追加する**方針が
適切（分類B/C）。

---

## 9. Context Preservation監査

`returnTo`/`withReturnTo`/`listReturnTo`（Phase 6-A/8-M）の使用状況を
全Detail/Edit系画面についてコードベースで確認した結果：

| 画面 | returnTo対応 |
|---|---|
| SKU Detail | ✅ |
| Draft | ✅ |
| PO Preview | ✅ |
| Supplier Response | ✅ |
| Order Detail | ✅（最も充実） |
| Price Change Edit | ✅ |
| Price Change Detail | ✅ |
| Arrival Detail | ✅ |
| Master Maintenance 6画面 | N/A（Dialogベースの同一画面内編集のため、そもそも別Routeへの遷移がない） |

**結論**: List↔Detail間のBack Context保持は、Master Maintenance系（そもそも
別画面遷移が存在しない）を除く全ての画面で既に完備している。今回の監査で
新たなContext Preservationの欠落は発見しなかった。

一方、**Filter Chipによる「現在の絞り込み条件」の可視化**は`/candidates`にしか
実装されておらず、Order History/Arrival/Warehouse Stock/Stock-Salesには存在
しない（Filterの値自体はTextFieldに残っているため完全な消失ではないが、
一覧性・視認性の面でCandidate Listに劣る）。

---

## 10. Recommended Information Architecture

優先順位付き推奨（**全て提案であり、本フェーズでは未実装**、10章のMinor Fixes
に該当するものを除く）：

1. **（提案）Master Maintenance統合ビュー**: Supplier一覧（Legacy READ ONLY集計）
   → Supplier選択 → Contact/Channel/Region/PO Codeをタブ表示。Mail Template・
   Default CCはGlobal性を尊重し対象外。
2. **（提案）Price Change Edit画面のBrand Context Deep Link対応**: SKU選択
   パネルのBrand FilterをURLパラメータ化し、Brand Contextを持つ画面から
   引き継げるようにする。
3. **（提案）Arrival/Warehouse Stock/Stock-SalesへのBrand/Supplier Deep Link
   追加**: 既存のbrandCode/supplierCode Filterパラメータへ、Dashboard等の
   Brand Context画面からリンクする。
4. **（提案）Order History/Arrival/Warehouse Stock/Stock-SalesへのFilter Chip
   追加**: Candidate Listと同じ視覚的パターンを横展開。

---

## 11. Minor Fixes（本フェーズで実装したもの）

初版時点では、Master Maintenance全体の再構成を含む今回のスコープの大きさ、および
依頼文末尾の明示的なレビューゲートを踏まえ、実装を行わず提案のみに留めていた。

**追記（Information Architecture Phase 2で実装完了）**: Stock/Sales・Warehouse Stock・
ArrivalへのFilter Chip追加を実装済み。詳細は`docs/gops-master-maintenance-hub-implementation.md`
10章を参照。

---

## 12. Major Change Candidates（Information Architecture Phase 2で実装完了）

- Master Maintenance全体のSupplier起点統合ビュー（4.4章）— **実装完了**。
  `docs/gops-master-maintenance-hub-implementation.md`参照。既存4画面（Supplier
  Contact/Manufacturer Channel/Region Classification/Official PO Short Code）は
  一切削除・変更せず、新しい「メーカー一覧」Entry Pointを追加する形で実現した。
- Price Change Edit画面のBrand Context対応（6章）— **実装完了**。同上ドキュメント
  9章参照。

---

## 13. Requirement Confirmation Items

今回の監査範囲では、既存のFilter機構（brandCode/supplierCode等）と
Legacy READ ONLY原則の範囲内で全ての分類判断が可能であり、**業務要件の
確認が必須な項目（E分類）は無かった。**

ただし、実装に進む場合は以下を事前に確認することが望ましい：

- Supplier一覧をLegacyのどのMaster（`ms_comm`等）からどう集計するか
  （Brand一覧がOrder Candidate/PortalOrder経由で集計しているのと同じ
  方式で良いか、Legacyの正式なSupplier Master構造の確認が必要）。
- Master Maintenance統合ビューを実装する場合、既存6画面のURLは維持するか、
  Supplier配下の新URL体系（例: `/admin/suppliers/:code`）に移行するか。

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
