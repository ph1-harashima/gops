# G-OPS 発注候補 Brand起点の主導線 実装報告

Information Architecture Phase 3 - Master Maintenance Hub
（`docs/gops-master-maintenance-hub-implementation.md`）と同じ「上位業務概念を
選択してから、そのContext内で下位データを操作する」原則を、発注候補（Order
Candidates）の入口導線に適用した実装の記録。

---

## 1. 変更前Navigation

上部Navigationの「発注候補」（`nav-candidates`）は`/candidates`へ直接遷移し、
`CandidateListPage`（全Brand混在のSKU Flat List）が即座に表示されていた。
Brandによる絞り込みは可能だが、それは「後から適用するFilter」であり、
「まずBrandを選ぶ」という導線にはなっていなかった。

## 2. 変更後Navigation

```
発注候補（上部Nav）
  ↓
/candidates （Query Parameterなし）
  ↓
Brand一覧（新規 OrderCandidateBrandListPage）
  Brand | 発注候補 | 欠品 | 長期欠品 | 発注作成中 | メーカー回答待ち | 要確認
  ↓ Brand選択
/candidates?brandCode={code}
  ↓
そのBrandの発注候補SKU一覧（既存 CandidateListPage、無変更）
  ↓ SKU選択
Draft
```

`/candidates`は**Query Parameterの有無だけ**で表示を切り替える（`CandidatesEntryPage`
という薄いラッパーを新設）。Query Parameterが1つでも付いていれば（`brandCode`・
`recommendedOnly`・`outOfStockOnly`等）、既存の`CandidateListPage`がそのまま
表示される - これにより、Dashboard等の既存Deep Link呼び出し元は**1行も変更不要**。

## 3. Brand一覧画面の構成

新規`OrderCandidateBrandListPage`は、Dashboard自身が使っている`useDashboard()`
（`GET /api/dashboard`）を**そのまま再利用**している。新しいCandidate API・
新しいDashboard的Endpointは一切作成していない。

列構成（既存Requirement `G-SYS_Online-Ordering_Prototype_Requirements.md`
§10 Brand一覧表に準拠）：

| Brand | 発注候補 | 欠品 | 長期欠品 | 発注作成中 | メーカー回答待ち | 要確認 |
|---|---|---|---|---|---|---|

「長期欠品」列はDashboard自体の内訳テーブルにこれまで存在しなかった列で、
今回`DashboardBrandRow`（Backend DTO）に`longTermOutOfStockCount`を追加した
（既存の`isLongTermOutOfStock`判定をBrand単位に集計しただけで、判定ロジック
自体は無変更）。Dashboard自身の内訳テーブル表示は今回変更していない
（スコープはOrder Candidates入口のみ）。

画面右上に「すべての発注候補を表示」ボタンを設置し、クリックすると
`/candidates?recommendedOnly=true`（既存のDashboard KPI「発注候補」タイルと
全く同じURL）へ遷移する。

## 4. Brand選択後URL

Brand名クリック: `/candidates?brandCode={code}`（追加Filterなし、そのBrandの
全SKU表示）。件数セルクリックは既存Dashboard踏襲のパターンで各種条件付き
URLへ遷移する（`recommendedOnly`/`outOfStockOnly`/`longTermOutOfStockOnly`等）。

## 5. 全Brand横断導線

「すべての発注候補を表示」ボタンからの`/candidates?recommendedOnly=true`が
全Brand横断導線の唯一の入口である。

> **Freeze Blocker-1 訂正（docs/gops-phase1-final-cleanup-report.md参照）**:
> 本セクション作成時点ではDashboardの「発注候補」KPIタイルも同一URLへ直接
> 遷移する設計を「無変更維持」としていたが、これは実運用上の導線として
> 誤りと判断され、Phase1 Freeze直前の修正でBrand一覧（`/candidates`、
> QueryParameterなし）を経由するよう変更された。KPIタイルは他の「発注候補」
> 入口（Global Navigation等）と同じBrand-first導線に統一されている。

## 6. Dashboard Deep Link維持結果

DashboardPageの「ブランド別内訳」テーブルの各Brand行（Brand名・発注候補数・
欠品数・発注作成中数・メーカー回答待ち数・要確認数）は、すべて**既存のまま
一切変更していない** - クリックすると直接`/candidates?brandCode=...`（Query
Parameter付き）へ遷移するため、新設したBrand一覧画面を経由しない。Browser実測
で確認済み（完了報告Scenario 5）。

## 7. Back Context結果

Brand別Candidate ListからSKU Detail/Draftへ進んだ後の「戻る」は、既存の
`returnTo`/`backTo`Architecture（Phase 6-A）をそのまま再利用しており、
新規実装は一切ない。加えて、Candidate List自体に新規「ブランド一覧へ戻る」
ボタンを追加し、`/candidates`（Query Parameterなし）へ戻ることでBrand一覧へ
復帰できるようにした。

## 8. 既存E2Eへの影響と対応

`nav-candidates`クリック後に即座にSKU Flat Listを前提としていた既存E2E
テスト（22ファイル、約35箇所）は、新しいBrand一覧が間に挟まることで
そのままでは動作しなくなるため、以下の方針で更新した。

- 対象SKUが判明している場合: そのSKUのBrandへの`order-candidate-brand-link-{code}`
  クリックへ変更（「すべての発注候補を表示」= `recommendedOnly=true`だと、
  このセッション中の反復実行で推奨発注数が0まで減少したSKUを取りこぼす
  ことが判明したため、より安全なBrand経由に統一）。
- 特定のSKUに依存しない汎用チェック（最初の行を使う等）: 「すべての発注候補を
  表示」のままで問題ない。
- Supplier/Brandが呼び出し元によって変わる共通Helper関数: SKUの接頭辞
  （`HM-`→LIVORA/`KT-`→KITCHENNE/`OD-`→FIELDNEST）からBrandを動的に算出する
  ロジックをHelper内に追加。

`page.goto('/candidates')`のような直接URL遷移も同様の影響を受けたため、
該当箇所（`header-and-list-ux.spec.ts`、`stock-judgement-visibility.spec.ts`）
はBrand指定付きURLに更新した。

## 9. E2E Results

新規E2Eファイル: `frontend/e2e/order-candidates-brand-entry.spec.ts`
（Scenario 1〜7 + Back-to-Brand-List、計8 tests）。

既存22ファイルの更新（Backward Compatibility維持のための最小限の調整、
テストの意図・検証内容自体は変更していない）。

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
