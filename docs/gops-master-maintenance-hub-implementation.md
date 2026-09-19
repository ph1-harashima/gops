# G-OPS Master Maintenance Hub 実装報告

Information Architecture Phase 2 - 前回監査（`docs/gops-information-architecture-cross-screen-audit.md`、
Commit `c5f005c`）の結果を正式なInputとして実施した実装の記録。

**変更範囲**: Master Maintenance Hub新設（Supplier一覧＋Supplier Settings）、Price Change Edit
のBrand Context Deep Link、Stock/Sales・Warehouse Stock・ArrivalへのFilter Chip追加。
**Master Maintenance全体のInformation Architecture再構成そのもの**（既存4画面の廃止や
統合等）は行っていない — 既存4画面は完全にそのまま維持し、新Hubはその上に追加された
新しいEntry Pointである。

---

## 1. Before IA

```
Master Maintenance（Flat Menu）
├─ メーカー担当者          (/admin/supplier-contacts)      - Supplier×Brand Flat List
├─ メールテンプレート       (/admin/mail-templates)         - Global/Supplier×Brand Flat List
├─ メーカー通信方法         (/admin/manufacturer-channels)  - Supplier×Brand Flat List
├─ メール送信設定(Default CC) (/admin/mail-settings)        - Global単一設定
├─ 国内／海外区分           (/admin/supplier-region-classifications) - Supplier×Brand Flat List
└─ Official PO略称コード    (/admin/official-po-short-codes) - Supplier/Brand Flat List
```

6画面が独立しており、あるSupplierの設定を一通り確認するには4画面それぞれで
Supplierコードを再入力する必要があった（前回監査4.2章で実測確認済み）。

## 2. After IA

```
Master Maintenance
├─ メーカー設定 (Supplier Settings)
│   ├─ メーカー一覧 (/master/suppliers) ← 新設Hub Entry Point
│   ├─ メーカー担当者 (/admin/supplier-contacts) ← 既存、URL/機能とも変更なし
│   ├─ メーカー通信方法 (/admin/manufacturer-channels) ← 既存、URL/機能とも変更なし
│   ├─ 国内／海外区分 (/admin/supplier-region-classifications) ← 既存、URL維持
│   └─ Official PO略称コード (/admin/official-po-short-codes) ← 既存、URL維持
└─ 共通設定 (Global Settings)
    ├─ メールテンプレート (/admin/mail-templates) ← 既存、変更なし
    └─ メール送信設定(Default CC) (/admin/mail-settings) ← 既存、変更なし

新Hub内部（/master/suppliers/:supplierCode/*）：
メーカー: {Supplier名}
メーカーコード: {Supplier Code}
[基本情報] [担当者] [通信方法] [国内／海外区分] [PO略称コード]
  ↑ タブはそれぞれ既存4画面のComponentを supplierCodeFilter Prop付きで再利用
```

既存メニュー項目は1つも削除していない。「メーカー一覧」が新規追加されただけであり、
既存の4項目はメニュー上も従来通りクリック可能（Backward Compatibility）。

## 3. Supplier Source

**新しいPortal Supplier Masterテーブルは作成していない。**

Supplier Code/Nameは、既存の`OfficialPoPreflightReadRepository`（Legacy `ms_comm`
READ ONLYアクセス、BR-08のSupplier/Brand存在チェックで既に使われているクラス）に
2つのメソッドを追加しただけで取得している。

```java
// 新規追加（既存クラスへの追記、新規Legacy接続先なし）
findAllSuppliers(): List<LegacySupplierRow>  // SELECT code_id, code_name FROM ms_comm WHERE cate_id = 'MS_SUPPL'
findSupplierName(code): String               // 単一Supplierの名称取得（findBrandNameと対称）
```

実データ確認（Legacy Demo MySQL、本実装中に直接確認）：

| Supplier Code | Supplier Name |
|---|---|
| SUP_ALPHA | 東和ライフサプライ株式会社 |
| SUP_BETA | 蒼空プロダクト株式会社 |
| SUP_GAMMA | 東都リビングパートナーズ株式会社 |

Brand紐付け（取り扱いBrand一覧、Overview表示用）は、DashboardServiceが既存で
使っているのと全く同じ導出方法（`OrderCandidateService.findOrderCandidates()` と
`PortalOrderRepository.findAll()` の実データからSupplier×Brandの組み合わせを集計）を
再利用しており、Legacyへの新規問い合わせは一切追加していない。

## 4. URL Structure

| Route | 内容 |
|---|---|
| `/master/suppliers` | Supplier一覧（新規） |
| `/master/suppliers/:supplierCode/overview` | Overview（新規、Brand一覧＋4設定の要約） |
| `/master/suppliers/:supplierCode/contacts` | 担当者（既存`SupplierContactPage`を`supplierCodeFilter`付きで再利用） |
| `/master/suppliers/:supplierCode/communication` | 通信方法（既存`ManufacturerChannelPage`を再利用） |
| `/master/suppliers/:supplierCode/region` | 国内／海外区分（既存`SupplierRegionClassificationPage`を再利用） |
| `/master/suppliers/:supplierCode/po-code` | PO略称コード（既存`OfficialPoShortCodePage`を再利用） |

既存の`/admin/supplier-contacts`等5つのRouteは`App.tsx`から一切削除していない
（該当Diffにルート削除なし、追加のみ）。

## 5. Component Reuse

`SupplierContactPage`/`ManufacturerChannelPage`/`SupplierRegionClassificationPage`/
`OfficialPoShortCodePage`の4Componentすべてに、**オプショナルな`supplierCodeFilter?: string`
Propを追加しただけ**で、Backend API呼び出し・Validation・Mutation Hookは一切変更していない。

```tsx
// Before
export function SupplierContactPage() { ... }

// After（Propが無ければ完全に従来通り）
export function SupplierContactPage({ supplierCodeFilter }: Props = {}) {
  const filtered = useMemo(() => data.filter(c =>
    (!supplierCodeFilter || c.supplierCode === supplierCodeFilter) && ...
  ), [...])
  function openCreate() {
    setForm(supplierCodeFilter ? { ...EMPTY_FORM, supplierCode: supplierCodeFilter } : EMPTY_FORM)
    ...
  }
}
```

`supplierCodeFilter`が未指定の場合（既存の直接URL経由）は、フィルタ条件が
`false || ...`となり従来と完全に同じ挙動になる。Backend Serviceの`list()`
メソッドはSupplierMasterServiceからも既存Adminサービス（`SupplierContactService`等）
からも**同じメソッドがそのまま呼ばれており、二重実装は一切ない**。

## 6. Context Preservation

`/master/suppliers/:supplierCode/*`は単一のReact Router Route（`SupplierSettingsLayout`）
が`useParams()`でSupplier Codeを取得し、内部で入れ子の`<Routes>`によりOverview/Contacts/
Communication/Region/PO Codeを切り替える。Supplier CodeはURL Path自体に含まれるため、

- タブ間の移動: 新しいNavigation Frameworkは作らず、既存の`useNavigate()`呼び出しのみ。
- Browser Back/Forward: 通常のBrowser History機構がそのまま機能（実測確認済み、7章）。
- Bookmark/直接URL: `/master/suppliers/SUP_ALPHA/contacts`を直接開いても同じ結果になる。

Context Header（「メーカー: {name}」「メーカーコード: {code}」）は`SupplierSettingsLayout`
自身がTab切り替えとは独立してレンダリングするため、5Tab全てで常時表示される。

## 7. Global Settings Separation

メールテンプレート（`/admin/mail-templates`）とメール送信設定Default CC
（`/admin/mail-settings`）は、Supplier配下へは一切統合していない。理由：

- メールテンプレートは`supplierCode`/`brandCode`が**任意（null許容）**であり、
  Global Templateが実在する（既存のBackend Entityで確認済み）。無理にSupplier配下へ
  入れるとGlobal Templateの居場所がなくなる。
- Default CCは元々単一のGlobal設定であり、Supplierという概念自体が存在しない。

メニュー上は「共通設定」という別グループに分離して表示している（4章参照）。

## 8. Inactive Handling

前回監査でSupplier Contactに数百件のInactive行の蓄積を確認していた。今回：

- `SupplierContactPage`/`ManufacturerChannelPage`は元々「Active=Default表示」を
  実装済みだった（変更なし）。
- `SupplierRegionClassificationPage`/`OfficialPoShortCodePage`はInactiveフィルタが
  一切存在せず全件表示だったため、同じ「Active=Default表示、無効な項目も表示チェックボックス
  で切替」パターンを追加した（既存Requirementとの矛盾は確認されなかった - 両画面とも
  Inactive行の表示方針に関する既存Requirementの明記はなく、姉妹画面との一貫性を優先）。

削除は一切行っていない - 既存の「Active/Inactive」ソフトデリート方式をそのまま踏襲。

## 9. Price Change Edit - Brand Context Deep Link

前回監査でC判定（上位概念導線追加推奨）だったPrice Change EditのBrand Filterを
URL Query Parameter駆動に変更した。

- `PriceChangeEditPage`: `brandCode`をローカルStateからURL(`useSearchParams`)駆動に変更。
  Deep Link経由で`brandCode`が既にURLにある場合、検索も自動実行される（手動「検索」
  クリック不要）。
- `PriceChangeListPage`: 自身のURLに`brandCode`があれば、それをUI上のFilterとしては
  一切表示せず（15章の指示通りStatus起点のFlat Listを維持）、「新規価格変更を作成」
  クリック時にだけ新規Setの編集URLへサイレントに引き継ぐ。
- 新規Entry Point: Dashboardの「ブランド別内訳」テーブルに「価格変更」列を追加し、
  各Brand行から`/price-changes?brandCode={code}`へ遷移できるようにした（既存の
  Candidates/Draft/AwaitingSupplier/Attention列と全く同じ実装パターンを再利用）。

全Brand横断の通常操作（Brand未指定での価格変更作成）は一切変更していない。

## 10. Context Preservation監査（Filter Chip追加）

Stock/Sales・Warehouse Stock・Arrivalの3画面に、CandidateListPageと同じ視覚パターンの
Filter Chipを追加した。いずれもBackend Paginated + 既存URL Query Parameter方式は
一切変更せず、「現在の絞り込み条件」を明示するChip UIのみを追加。

- 各TextFieldに`key`をFilter値に連動させ、Chipの削除ボタンで実際に表示もクリアされる
  ようにした（`defaultValue`のuncontrolled TextFieldはChip削除だけではリセットされない
  ため、この対応が必要だった）。

## 11. E2E Results

新規E2Eファイル2本、計13 Scenario：

- `frontend/e2e/master-maintenance-hub.spec.ts`（8 tests）: Scenario 1, 2, 3, 4, 5, 6,
  12、およびOPERATOR 403確認。
- `frontend/e2e/ia-phase2-deep-links.spec.ts`（5 tests）: Scenario 7, 8, 9, 10, 11。

全13件PASS（詳細は完了報告参照）。

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
