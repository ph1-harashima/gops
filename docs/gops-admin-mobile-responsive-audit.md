# G-OPS Admin/Approver Mobile Responsive 監査・実装報告

Admin/Approver向け画面群のMobile Responsive監査、および明確なGapに
限定した実装の記録。既存のReact/MUI/Theme/Breakpoint/Component構成を
そのまま再利用しており、新規Navigation Frameworkや PC/Mobile間の
Business Logic重複は一切発生させていない。

---

## 1. Target Screens

Login, Dashboard, Approval（= Order Detail、Approvalは独立画面ではなく
`PENDING_APPROVAL`時のOrder Detail自体）, Order History, Order Detail,
Official PO/Revision, Cancel Review/Approval, Supplier Response確認,
Order Candidates Brand Selection, Candidate List, Master Maintenance
（Supplier List/Overview/Contact/Communication/Region/PO Code）,
Mail Template, Default CC（Portal Mail Settings）。

## 2. Before State（実装前の実測）

実装着手前に、`frontend/e2e/_overflow-scan.spec.ts`（throwaway診断
スクリプト、後に`mobile-responsive.spec.ts`へ正式統合）で17代表Routeを
375x667/390x844/430x932/768x1024の4Viewportでスキャンした結果：

- **375/390/430の全Routeで例外なく `document.documentElement.scrollWidth
  = 602` の横スクロールが発生**（Route固有のTable/Form内容に関わらず
  完全に同一の値）。
- **768x1024では横スクロール0件**。

この「Route非依存で完全に同一の値」という結果から、原因は個別画面では
なく **全画面共通のGlobal Header（AppBar/Toolbar）** にあると判断した
（`App.tsx`のToolbarが、約7個のNav Button + マスタメンテナンスMenu +
User Area + Language Switcherを1行に`flexWrap`付きで並べている構成）。

## 3. Responsive Strategy

- MUIの **Default Theme**（`ThemeProvider`未設置のプロジェクトだが、
  `useTheme()`はMUIの組み込みDefault Theme・Breakpointを自動的に返す
  ため、新規Theme設定は不要だった）をそのまま使用。
- Breakpointの境界は **`md`（900px）** を採用。理由: 375/390/430/768の
  4 Viewportすべてを「Mobile扱い」で一括カバーでき、かつ768は単独では
  AppBarの横スクロールが起きない一方、実際の触感（タブレット/縦持ち
  スマホ相当）としてはHamburger Navigationの方が自然であるため。
  1440x900（Desktop Regression用）はこの境界の外側にある。
- Table Strategyは、密な列を持つ一覧のほとんどで **(A) Horizontal
  Scroll**（既存の`TableContainer`の`overflow-x: auto`をそのまま活用）
  を採用。Order Detail（Approval画面）とOrder Candidates Brand
  Selectionの2画面のみ、情報の見落としリスクが高いと判断し **(C) Card/
  List** へ切替。列を絞る (B) は今回一つも採用していない（「重要情報を
  隠さない」という要件を、A/Cのどちらかで満たせたため）。

## 4. Navigation（Hamburger + Drawer）

`frontend/src/app/App.tsx`に実装。

- `useMediaQuery(theme.breakpoints.down('md'))`で`isMobileNav`を判定。
  `md`以上ではDesktopのAppBar Nav（`header-nav-area`/マスタメンテナンス
  `Menu`/`header-user-area`）を**一切変更せず**そのまま表示。
- `isMobileNav`時は、AppBarに Hamburger `IconButton`
  （`data-testid="mobile-nav-open-button"`）+ 現在ページ名（既存の
  `t('navXxx')`キーをpathnameから解決、新規i18nキーは追加していない）
  + Language Switcherのみを表示（デモ環境Badgeは省略、Drawer内に
  同等表示あり）。
- Drawerは`data-testid="mobile-nav-drawer"`。中身: アプリ名+デモBadge→
  Dashboard/発注候補/発注一覧/価格変更/入荷確認/倉庫在庫/在庫・販売確認
  の各Nav（`mobile-nav-dashboard`等、Desktop用`nav-dashboard`等とは
  別のtestidとして新設）→（ADMINのみ）メーカー設定/共通設定の2
  Subheader付きMaster Maintenanceリンク群→User情報+ログアウト
  （`mobile-nav-logout`）。
- 既存のReact Router `<Routes>`構成・全既存`data-testid`（Desktop側）は
  **1つも変更していない**。Drawerは同じLinkへ`navigate`するだけで、
  新しいRoutingやBusiness Logicを一切持たない。

### After State（Header修正後の実測）

同じ17 Route + 新たに `/orders/{PENDING_APPROVAL Order}` を加えた
Overflow Scanで、375/390/430/768/1440の全Viewportで **横スクロール
0件** を確認（`mobile-responsive.spec.ts`の`Mobile Responsive Overflow
Audit`スイート、5 test全Pass）。

## 5. Tables（個別画面の追加対応）

Header修正だけでは解決しない、**画面固有の問題**を1件発見・修正した。

### 5.1 発見: 密な列を持つTableが「横スクロール」ではなく「列破壊」を起こす

Order History一覧を390x844で確認したところ、ページ全体の横スクロールは
発生しない一方で、**Table自体が極端に狭い列へ圧縮され、「メーカー」
「発注数量合計」等のCJK見出しが1文字ずつ縦積みで折り返され、行の高さが
233pxまで膨張する**という、Overflow Scanでは検出できない別種のGapを
発見した（`document.documentElement.scrollWidth`はTableContainer内で
吸収されるため、Page-level Overflow Auditには現れない）。

原因: `table-layout: auto`の下で、CJKテキストはスペースなしに任意の
文字境界で折返し可能（改行禁止指定なし）なため、ブラウザの自動列幅
アルゴリズムがCJK列を最小幅まで圧縮し、その分をChip/数値等の
「折返し不可能な内容を持つ列」に再配分してしまうことが原因（実測:
「メーカー」列46px→ヘッダーが4行に折返し）。

**修正**: `frontend/src/features/**/*ListPage.tsx`等、`stickyHeader`を
持つ13ファイルのTableに対し、機械的に以下2点を追加。

```tsx
<Table size="small" stickyHeader sx={{
  minWidth: 650,
  '& .MuiTableCell-root': { whiteSpace: 'nowrap' },
  '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' },
}}>
```

- `minWidth: 650`: TableContainerの`overflow-x: auto`が機能する下限を
  保証（Desktopでは常にこれより広いため無影響）。
- `whiteSpace: 'nowrap'`: 自動列幅アルゴリズムに「どの列も折返し不可」
  と伝え、各列が自身の内容の自然な幅を確保できるようにする（結果として
  Table Strategy (A) Horizontal Scrollが正しく機能するようになった）。

修正後、同じOrder Historyの行高は37px（通常の1行）に復帰し、
「メーカー」列も258px確保され読める状態になったことを実測で確認した。

対象ファイル（13件、いずれもDesktop表示は無変更）:
`CandidateListPage.tsx`, `OrderCandidateBrandListPage.tsx`（Table分岐側）,
`OrderHistoryListPage.tsx`, `PriceChangeListPage.tsx`,
`ArrivalListPage.tsx`, `StockSalesListPage.tsx`,
`WarehouseStockListPage.tsx`, `SupplierMasterListPage.tsx`,
`SupplierContactPage.tsx`, `ManufacturerChannelPage.tsx`,
`SupplierRegionClassificationPage.tsx`, `OfficialPoShortCodePage.tsx`,
`MailTemplatePage.tsx`。

### 5.2 Order Detail（Approval画面）: Table → Card切替

12列（SKU/商品名/推奨発注数/発注数量/確認数量/希望納期/回答納期/確認
事項/現在庫/当月販売実績/リードタイム/入荷予定）を持つ、アプリ内で
最も密なTable。Horizontal Scrollでも技術的には破綻しないが、
「Approve判断に必要な全項目を横スクロールなしで一目で見せる」という
最重要要件（§6）に応えるため、`md`未満でCard Layoutへ切替えた
（`data-testid="order-detail-line-cards"`、全12項目をラベル付きで
縦積み表示、Desktop側のTableは無変更）。

### 5.3 Order Candidates Brand Selection: Table → Card切替

7列KPI Table（Brand/発注候補/欠品/長期欠品/発注作成中/メーカー回答待ち/
要確認）を、`sm`未満でBrand Card Layoutへ切替え（`OrderCandidateBrandListPage.tsx`）。
Brand名 + ラベル付きKPI行を縦積み表示。既存の全`data-testid`/遷移先URL
はCard側でも完全に同一のものを使用（E2Eからは見分けがつかない構造）。

## 6. Approval画面（最重要）

- Mobile Card Layout（§5.2）により、Supplier/Brand/SKU/発注数量/
  推奨発注数/現在庫/当月販売実績/未入荷数（入荷予定）/リードタイムが、
  スクロール操作だけで全SKU分確認できる。
- 承認/差し戻し/修正の3ボタンを、Card Layout時のみ
  `position: sticky; bottom: 0`の Action Bar に変更し、SKU一覧を
  スクロールしても画面外に消えないようにした（誤タップ防止のための
  ボタン間隔・配置自体はDesktopと同じ`Stack spacing={2}`を維持）。
- 承認/差し戻し/Cancel/Email Send確認の各Dialogに`fullScreen={isCardLayout}`
  を追加し、Mobileでは確認内容が画面いっぱいに表示されるようにした
  （対象: 承認Dialog、差し戻しDialog、Cancel Request Dialog、
  Email Send確認Dialog、正式PO再発行Dialog）。

## 7. Order Detail（一般）

Approval状態以外（DRAFT/APPROVED/AWAITING_SUPPLIER等）でも同じCard
Layoutが適用されるため、Portal管理番号/正式PO No/Revision/Supplier/
Brand/Statusのヘッダー情報（既存構成、無変更）とSKU明細Cardが両方とも
横スクロールなしで確認できる。正式PO/Supplier Response/Revision
History/Cancel/Audit Trailの各セクションは元々縦積みのBox/Paper構成
だったため、追加のRoute/Table対応なしで既にMobile対応済みだった
（Overflow Auditで確認）。

## 8. Master Maintenance

- Supplier List: §5.1の`minWidth`+`nowrap`修正を適用（Horizontal
  Scroll、3社のみのためCard化は不要と判断）。
- Supplier Settingsの5 Tabs（Overview/Contacts/Communication/Region/
  PO Code）: `Tabs`に`variant="scrollable" scrollButtons="auto"
  allowScrollButtonsMobile`を追加。MUI標準のScrollable Tabsパターンを
  採用しており、独自のSelect/Menu実装は行っていない（Desktopでは
  5 Tabsが元々収まるため無効果）。
- Context Header（Supplier名+コード）は、Tab切替のたびに再検索なしで
  維持されることを`mobile-responsive.spec.ts`のM4シナリオで検証済み。

## 9. Forms / Dialogs

- 上記Approval関連Dialog（§6）に加え、他のCreate/Editダイアログは
  MUIの`Dialog`が持つDefaultの`margin: 32px` + `maxWidth`挙動により、
  375px幅でもDialog自体が画面幅を超えないことをOverflow Auditで確認
  済み（追加のfullScreen化は必須要件ではないため、Approval関連以外は
  今回対象外とした）。
- Autocomplete/Select/Date系Inputの個別Mobile最適化（Keyboard表示時の
  Submit/Cancel到達性の実機検証等）は、Emulator上のViewport変更では
  Software Keyboardの出現自体を再現できないため、**今回はコード
  レビューベースの確認に留めた**（Dialog内のActionsが`DialogActions`
  としてScroll領域の外に固定されており、構造的にはKeyboard表示時にも
  隠れない設計になっていることを確認）。実機での最終確認は未実施
  （Known Limitations参照）。

## 10. Filters（Order History/Stock-Sales/Warehouse/Arrival）

調査の結果、これらの一覧は元々（Phase 8時点から）
`Stack direction="row" sx={{ flexWrap: 'wrap' }}` + 各TextFieldへの
`minWidth`（`width`ではない）指定で構成されていたため、**狭い画面幅では
自然に1Field=1行の縦積みへ折り返る**構造だった。Filter Chip
（`active-filter-chips`、Phase 3で追加済み）も`flexWrap`済みで、追加の
Accordion/Drawer化は不要と判断し、**変更していない**（§5.1のPadding
調整のみ実施）。

## 11. Touch Target

Approve/Cancel/送信等の危険操作ボタンの間隔・配置はDesktop/Mobile共通の
`Stack spacing={2}`を維持しており、隣接配置の見直しは不要と判断した
（元々Approve/差し戻しは離れた位置に配置済み）。個別のIcon Button
サイズ調整（44px目安のタップ領域確保等）は、今回のScope（明確な
Responsive Gapの修正）には含めておらず、**未実施**（Known Limitations
参照）。

## 12. Mobile Scenarios（M1-M6、`frontend/e2e/mobile-responsive.spec.ts`）

全6 Scenario、390x844固定Viewportで、**それぞれ独自にOrder/Master
Fixtureを新規作成**する形で実装（既存Demo Order IDへの依存を排除し、
他Suite実行による状態変化の影響を受けない設計）。

| Scenario | 内容 | 結果 |
|---|---|---|
| M1 | Login→Dashboard→Approval→Order確認→Approve | PASS |
| M2 | Order History→検索→Order Detail→PO/Revision確認 | PASS |
| M3 | Cancel Request→Cancel Approval | PASS |
| M4 | Master Maintenance→Supplier→5 Tab連続確認、Context常時表示 | PASS |
| M5 | 発注候補→Brand選択→Candidate確認 | PASS |
| M6 | メーカーへ送信→To/CC→Domain Warning→Final Confirmation | PASS |

## 13. Overflow Audit（正式Suite化）

診断用の`_overflow-scan.spec.ts`は削除し、`mobile-responsive.spec.ts`内
の`Mobile Responsive Overflow Audit`として正式化した。375/390/430/768/
1440の5 Viewport × 16 Stable Route（特定のOrder/Draft IDに依存しない
Route）で、`document.documentElement.scrollWidth <= clientWidth + 1`を
アサートする恒久的な回帰ゲート。5 test全Pass。

## 14. Desktop Regression（1440x900）

- 全既存E2E（207 tests、4 shard）を**Default Viewport（1280x720相当、
  `md`を超えるためDesktop Nav表示）** で実行し、全件Pass（regression
  0件）。
- `mobile-responsive.spec.ts`内に1440x900のOverflow Auditケースを追加し、
  Desktop側でも横スクロールが発生しないことを明示的に確認。
- §5.1のTable修正（`minWidth`/`nowrap`）はDesktop幅では常に無効果
  （元々Desktop幅はminWidthを超えているため）であることをE2E Pass
  （既存Table関連E2E全件）で確認済み。

## 15. Known Limitations（未対応・今回Scope外）

- Software Keyboard表示時の実機挙動（§9）は未検証。
- Icon Button個別のタップ領域拡大（§11）は未実施。
- Order History一覧「PO No.」列の用語混同（Business UAT §Scenario K、
  P2）はBusiness Logic/表示仕様の変更を伴うため、Mobile Responsive
  Round（今回）ではなく、UAT側のRecommended Fix Scopeとして記録した。
- Master Suppliers一覧のCard化は行っていない（3社のみで実害が小さい
  ため、Horizontal Scroll(A)のみで対応）。

## 16. Final Verdict（Mobile）

Admin/Approver（`admin01`相当）が、スマートフォン（390x844相当）から
外出先で以下を実務的に行えるかを、Browser実測（`mobile-responsive.spec.ts`
M1-M6、全Pass）に基づき判定する。

- **承認**: Card Layout+Sticky Action Barにより、SKU明細を全項目
  スクロールで確認しながら、Approve/差し戻しボタンが常に画面内に
  留まる状態で承認operationを完了できることを確認した（M1）。
- **Supplier設定確認**: Supplier一覧→5 Tab連続確認（再検索不要、
  Context常時表示）を確認した（M4）。
- **Order History検索/確認**: 検索→Detail→戻る、が横スクロールや
  レイアウト崩れなしに行えることを確認した（M2、§5.1のTable修正込み）。
- **Cancel/メーカー送信**: Domain Warning付きの送信、Cancel申請→承認の
  両方をMobileで完了できることを確認した（M3, M6）。

**判定: PASS**（Software Keyboard実機挙動とIcon Button個別タップ領域
拡大は§15のとおり未検証/未実施のため、実機での最終確認を推奨する）。

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
