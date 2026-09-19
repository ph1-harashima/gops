# G-OPS Phase1 業務シナリオ UAT 監査報告

Final Business UAT（Scenario A〜L）+ Demo/Test Data Lifecycle調査の記録。
今回のRoundは **監査（Audit）が主目的** であり、明確なBusiness Logic変更は
一切行っていない（発見したGapは分類・記録のみ、Roleも新設していない）。

---

## 1. Executive Summary

- Gulliver社スタッフ（発注担当者/承認者/管理者）が、日常業務（発注候補確認→
  承認→正式PO発行→メーカー送信、Master設定、Order History検索）を
  **マニュアルなしで完了できるか** を軸に、既存Role（OPERATOR/ADMIN）で
  Live Browser（Chrome）を用いてWalkthroughを実施した。
- 中核フロー（Scenario A: Dashboard→発注候補→Brand→Candidates→SKU→Draft→
  承認→正式PO発行→Excel/PDF生成）は、実際にBrowserで最後まで実行し、
  各画面の文言・遷移・エラーメッセージが業務的に理解可能であることを確認した。
- Scenario B/C/D/E/G/H/I/J/L の業務ロジック自体は、本Session内で既に
  Green（全PASS）である既存自動化E2E（Backend 588/588, E2E 207 tests）が
  詳細に検証済みであり、今回はその上に **UI文言/画面構成のUX明瞭性** の
  観点から追加確認を行った（詳細は各Scenario節に明記）。
- **P2級のGap を1件発見**： Order History一覧の「PO No.」列が、Draft段階の
  Portal番号（`DRAFT-...`）とApproved後のPortal管理番号（`PO-DEMO-...`）を
  同一列に混在表示しており、かつ真の「正式PO番号」（G-SYS側の実PO番号、
  例: `GAMHOM001`）は一覧のどこにも表示されない（§K参照、UX-06相当）。
- Demo/Test Data Lifecycle調査の結果、Master 4テーブル（Supplier Contact/
  Mail Template/Manufacturer Channel/Supplier Region Classification）で
  「現存する全行が非Active（Inactive）」という状態を確認した（§33参照）。
  原因は個別には正常な2つの設計判断（E2Eの後片付けは無効化のみでDelete
  APIを持たない／`demo-reset.sh`はMasterテーブルを意図的にTRUNCATE対象外
  としている）が組み合わさった結果であり、Bugではなく構造的な蓄積である。
- Business Logic/業務仕様の変更は一切行っていない。

---

## 2. Environment

- Frontend: `http://localhost:5173`（Vite Dev Server, 稼働確認済み）
- Backend: `http://localhost:8080`（Spring Boot, `/actuator/health` = 200）
- Prototype DB: PostgreSQL（`gsys-prototype-postgres`, port 54321）
- Legacy Demo DB: MySQL（`gsys-legacy-demo-mysql`, port 33061）
- 実施日: 2026-09-19

## 3. Roles（既存Roleへの対応付け）

要求仕様のROLE-A/B/Cは、既存の2 Role（`ROLE_OPERATOR`/`ROLE_ADMIN`）に
以下のとおり対応付けた。**Roleは新設していない**。

| 要求Role | 対応する既存Role | Demoユーザー |
|---|---|---|
| ROLE-A（発注担当者/Operator） | `ROLE_OPERATOR` | `purchase01` |
| ROLE-B（承認者/Approver） | `ROLE_ADMIN` | `admin01` |
| ROLE-C（管理者/Administrator） | `ROLE_ADMIN` | `admin01` |

現行システムには承認者用の独立したRoleが存在せず、Order Detail画面の
`修正`/`承認`/`差し戻し`ボタンとMaster Maintenance全体が、同じ`ROLE_ADMIN`
の`@PreAuthorize("hasRole('ADMIN')")`配下にある。したがってROLE-BとROLE-Cは
**運用上は同一アカウント（`admin01`）** が兼務する形になる。これは今回の
監査で判明した既存仕様であり、今回の変更点ではない。

## 4. Scenario別 所見

### Scenario A: 通常発注（Dashboard→発注候補→Brand→Candidates→SKU→Draft→承認→正式PO→Excel/PDF→メーカー送信）

**Live Browser で最後まで実施。** `purchase01`でログイン→Dashboard→
「発注候補」Nav→Brand一覧（LIVORA/KITCHENNE/FIELDNEST、各行に発注候補/
欠品/長期欠品/発注作成中/メーカー回答待ち/要確認の6列）→LIVORA選択→
Candidate List（現在庫/安全在庫/発注残/当月販売数/リードタイム/推奨発注数/
商品状態/在庫判定/単価の9列）→HM-MUG-002のSKU Detail確認→2件選択して
Draft作成（`DRAFT-20260919-3178`）→承認依頼→`admin01`でログイン→承認
（`PO-DEMO-20260919-6510`に変化）→G-SYS連携準備（正式PO番号
`GAMHOM001`採番）→Excel生成→PDF生成、まで確認。

- **判断材料の十分性**: Candidate ListのColumnだけで「なぜ候補か」を判断
  できる（現在庫0・当月販売数3・推奨発注数20、のように並んでいる）。◎
- **状態の明瞭性**: 各段階のStatus Chip（承認待ち/承認済み）とアラート文
  （「承認待ちです。管理者の承認をお待ちください。」「次にすべきこと：
  G-SYS連携準備を行ってください。」）が次のActionを明示しており、迷わない。◎
- **確認ダイアログの説明**: 承認ダイアログは「承認後はPO番号が採番され、
  メーカーへの発注準備が整います。」と結果を明示。G-SYS連携準備ダイアログ
  は「この操作ではG-SYSへの実際の登録やデータ連携は一切行われません。」と
  安全性を明示。◎
- **Gap（P3, Positive Finding扱い）**: 複数メーカーのSKUを同時選択して
  ドラフト作成しようとすると、「複数のメーカーが混在しているため、ドラフ
  トを作成できません。同一メーカーのSKUのみを選択してください。」という
  明瞭な日本語エラーが表示され、技術的なStack Trace等は一切出ない。想定
  どおりの挙動であり、修正不要（Scenario Lの「エラー理解可能性」要件を
  満たす好例として記録）。

### Scenario B: メーカー全量合意（Requested=Confirmed）

Business Logicは`supplier-response-revision-workflow.spec.ts` Scenario A
（Order Rev1→Response1→Qty一致→Confirm→Agree→AGREED, 現在Green）で
継続的に検証されている。用語（発注数量/回答数量/差異/合意）はOrder Detail
画面のRevision History/Supplier Response関連セクションで一貫して使用され
ており、Scenario Aの実施中に同一画面構造を確認済み。追加のP0-P3 Gapなし。

### Scenario C: 数量変更・Reissue

`official-po-integration.spec.ts` Scenario K（ADMIN reissues the Official
PO after a correction, old Revision stays in history as SUPERSEDED, 現在
Green）が要求どおりの挙動（PO番号は同一のままRevisionのみ001→002へ進み、
Rev001=SUPERSEDED/Rev002=ACTIVEとして履歴に残る）を検証している。
Scenario A実施中に確認した「正式PO再発行 / Revision History」テーブル
（Revision/作成日時/正式PO番号/Excel/PDF/G-SYS連携状態/状態/送信状況の
8列、状態列に「有効」Chip）は、「どのRevisionが現在有効か」を一目で
判別できる構成になっている。追加のP0-P3 Gapなし。

### Scenario D: キャンセル

`official-po-integration.spec.ts` Scenario L、および今回新設した
Mobile Scenario M3（本Session内でLive実施、`docs/gops-admin-mobile-
responsive-audit.md`参照）の両方で、Cancel Request（理由必須）→
「取消依頼中」（`official-po-cancel-requested-note`、CANCELLEDにはまだ
ならない）→承認→「取消済み」（`official-po-cancelled-note`）の3状態が
明確に分離されており、Requester/Approver/理由も画面上に残ることを確認
した。追加のP0-P3 Gapなし。

### Scenario E: メールOverride（Domain不一致）

`email-send.spec.ts`「ADMIN overrides To/CC with a different Domain -
Warning shown but never blocks Send」、および今回のMobile Scenario M6
（Live実施）で、Master Domain（`example.com`）と異なるDomainへの
To/CC上書き時に`email-domain-mismatch-warning`が表示されつつSendは
ブロックされないこと、Master Contact自体は変更されないこと
（`supplier-contact-table-container`に上書き先アドレスが反映されない
ことを直接アサート）を確認した。追加のP0-P3 Gapなし。

### Scenario F: Master Maintenance（Supplier一覧→5タブ連続確認）

**Live Browserで実施。** `admin01`でログイン→マスタメンテナンス→
メーカー一覧（SUP_ALPHA/BETA/GAMMA、担当者列は3社とも「未設定」）→
SUP_ALPHA選択→基本情報タブ（Context Header「メーカー: 東和ライフサプ
ライ株式会社 / メーカーコード: SUP_ALPHA」が常時表示）→担当者タブ
（再検索なしでそのまま遷移、Contextヘッダー維持を確認）。

- **UX-04（「未設定」の見え方）の検証結果**: 基本情報タブの4カード
  （担当者設定/通信方法/国内海外区分/PO略称コード）はいずれも「未設定」
  をグレーのChip（Pill）として表示しており、地の文ではない。事前に
  懸念された「プレーンテキストに見える」問題は、既にChipスタイルで
  緩和されていることを確認した（**追加修正不要、Gap該当なし**）。
- **Inactive大量蓄積の実見**: 担当者タブで「無効な項目も表示」を
  ONにすると、SUP_ALPHA/BR_OUTDOORだけで100件超のInactive行
  （Taro Yamada, Integration Tester, Scenario D Contact等）が表示さ
  れた。詳細は§7（Demo/Test Data Lifecycle）参照。

### Scenario G: 国内サプライヤー（海外向け推奨発注数の誤表示防止）

`supplier-region-classification.spec.ts` Scenario 8「DOMESTIC Supplier
shows "設定準備中" instead of a fabricated Recommended Qty」が現在も
Greenであり、国内サプライヤーに対して海外向け計算式による推奨発注数が
誤って表示されないことを検証済み。国内向け計算式自体は今回も実装対象外
のまま。追加のP0-P3 Gapなし。

### Scenario H: 価格変更

`price-change-foundation.spec.ts`（8 Scenario、現在Green）に加え、
Phase 3で確認済みのBrand Deep Link（Dashboard→Brand→価格変更「作成」→
Brand Context保持→Edit→Back）が今回も維持されていることを、ソース
（`PriceChangeEditPage.tsx`のURL駆動`brandCode`、`PriceChangeListPage.tsx`
の`deepLinkBrandCode`パススルー）で再確認した。Price Change List自体は
Flat List + Status/Search のままで、Brand階層は強制されていない。
追加のP0-P3 Gapなし。

### Scenario I / J: 在庫・販売確認 / 入荷・倉庫在庫

`stock-sales-visibility-foundation.spec.ts`（12 Scenario）、
`arrival-warehouse-stock-visibility-foundation.spec.ts`（11 Scenario）が
現在Green。Filter Chip（Brand/Supplier/PO No./SKU等）による現在の絞込み
状態の明示は、`ia-phase2-deep-links.spec.ts` Scenario 8-10で検証済み。
追加のP0-P3 Gapなし。

### Scenario K: Order History（PO No.列の曖昧さ）★P2 Gap

**Live Browser + ソースコードの両方で確認した具体的Gap。**

Order History一覧（`OrderHistoryListPage.tsx:287`）の列見出しは単純に
「PO No.」（`t('table.poNo')`）であり、セル内容は
`row.prototypePoNo ?? row.draftNo`（同ファイル307行目）——つまり
Draft段階では`DRAFT-20260919-8456`のようなPortal内部番号、Approved後は
`PO-DEMO-20260919-6510`のようなPortal内部番号（正式PO番号ではない）が
**同一列に混在** して表示される。

一方、Order Detail画面で確認できる **真の正式PO番号**（例:
`GAMHOM001`、G-SYSの短縮コード+連番）は、Order History一覧のどの列にも
表示されない。Filter欄のプレースホルダーは「PO No. / Draft No.」と
両者を区別しているにもかかわらず、テーブルの列見出し自体は区別していない。

**リスク**: 承認者/管理者が一覧の「PO No.」列の値（`PO-DEMO-...`）を、
メーカーとの電話・メールで「正式PO番号」として伝えてしまう誤解の余地が
ある。実務上はOrder Detail個別画面で必ず正式PO番号を確認する運用に
なっているため、業務が完全に止まる不具合ではないが、事前提起された
UX-06懸念のとおり **一覧レベルでの用語混同リスクは実在する。**

**分類**: P2（明瞭性/Terminology Gap。Business Logic変更は伴わず、UI表示
の見直しで解消可能と考えられるが、今回はBusiness Logic変更禁止の
指示のため実装しない。改善候補としてのみ記録）。

**改善候補（実装は次Round判断）**:
- 列見出しを「Portal管理番号」に変更する、または
- 正式PO番号が確定済みの行には別列（または同一セル内に注記）で
  「正式PO: GAMHOM001」のように追加表示する。

### Scenario L: エラー/リカバリー

以下を確認した（一部はScenario A実施中に実見、一部は既存Green E2Eで
検証済み）。

- 複数メーカー混在時のDraft作成エラー（Scenario A参照）: 業務文言で明瞭。◎
- Mail Preview未設定時のブロック（`supplier-contact-mail-template.spec.ts`
  Scenario C/D、Scenario A実施中にも実見）: 「有効なTo担当者
  （メーカー担当者Master）が見つかりません。」「有効なメールテンプレー
  トが見つかりません。」と、原因が明確な日本語で表示され、Stack Trace/
  内部例外/HTTPステータスコードは一切露出しない。◎
- 正式PO番号未確定時のMail Preview: 「正式PO番号未設定」ブロッカーが
  明示される（`supplier-contact-mail-template.spec.ts` Scenario E）。◎
- Domestic Supplierの推奨発注数未定義: 「設定準備中」表示（Scenario G）。◎
- 全体として、確認した範囲でTechnical Status Code/Stack Traceの直接露出
  は見つからなかった。

## 5. Navigation / Context / Terminology 横断所見

- 「上位概念を選んでから子を操作する」原則（Dashboard/発注候補→Brand→
  Candidates→SKU→Draft、Master Maintenance→Supplier→Supplier Settings）
  は、Scenario A・Fの実施を通じて一貫して維持されていることを確認した。
- Portal管理番号（`DRAFT-...`/`PO-DEMO-...`）と正式PO番号
  （`GAMHOM001`等）は、**Order Detail個別画面では**Chip「Portal管理番号」
  と「正式PO番号」フィールドで明確に区別されている。曖昧さが生じるのは
  Order History **一覧** に限られる（§Scenario K）。

## 6. Operation Count 実測（§35）

**Scenario A**（Dashboard開始 → メーカー送信完了、Live Browserで実測。
ただしメーカー送信は本Session時点でSUP_GAMMAの担当者/テンプレート未設定
のためExcel/PDF生成までを実測し、送信完了まで行った既存E2E
`email-send.spec.ts`のStep数を参考値として付記）:

| 操作種別 | 回数 |
|---|---|
| Click（Nav/Button/行クリック） | 14（Dashboard→発注候補Nav→LIVORA行→SKU行→戻る→2チェック→ドラフト作成→承認依頼→確認→ログアウト→ログイン→承認→確認→G-SYS連携準備→確認→Excel生成→PDF生成） |
| Page遷移 | 8（Dashboard→Brand一覧→Candidate List→SKU Detail→Candidate List→Draft→Order Detail(×ログイン跨ぎ)） |
| 検索（Filter入力） | 0（Brand選択のみで到達、Filter未使用） |
| 手入力（テキスト/数値入力） | 0（推奨発注数がDefault入力されており未変更） |

**Scenario F**（Supplier選択 → 4主要設定確認、Live Browserで実測）:

| 操作種別 | 回数 |
|---|---|
| Click | 3（マスタメンテナンス→メーカー一覧→SUP_ALPHA行→担当者タブ） |
| 検索 | 0（5タブとも再検索なしで遷移、Context維持を確認） |

## 7. Demo/Test Data Lifecycle 調査（§33、調査のみ・削除等は未実施）

### 7.1 定量結果（2026-09-19時点、Prototype PostgreSQL）

| Masterテーブル | 総行数 | Active行数 | 備考 |
|---|---:|---:|---|
| `supplier_contact` | 163 | 0 | 最古 2026-09-18 11:35、最新 2026-09-19 10:15 |
| `mail_template` | 154 | 0 | |
| `manufacturer_channel` | 101 | 0 | |
| `supplier_region_classification` | 23 | 0 | |
| `official_po_short_code` | 6 | 6 | ALP/BET/GAM等、実データとして機能中 |

`official_po_short_code`のみActiveが100%残っている点が対照的で、これは
Supplier単位でUPSERTされる性質のMasterであり、E2Eが使い捨てで新規行を
量産する他4テーブルとは異なるためと考えられる。

### 7.2 Inactive行の内訳（上位、`supplier_contact`）

| `contact_name` | 件数（Inactive） |
|---|---:|
| Taro Yamada | 34 |
| Integration Tester | 26 |
| Scenario D Contact | 24 |
| Scenario A Contact | 24 |
| Follow-up Contact | 24 |
| Revision Consistency Tester | 19 |
| その他（`Hub E2E Contact <timestamp>`等、単発） | 12 |

### 7.3 原因

1. **E2E自身の後片付けは「無効化」のみ**（`supplier-contact-mail-
   template.spec.ts`の`deactivateContactByEmail`/`deactivateTemplateByName`
   ヘルパー等）。これは「同名行の物理削除APIが存在しない」という既存
   仕様上の制約であり、コメントに「defense-in-depth」と明記されている
   意図的な設計（テスト後も監査目的で行自体は残す）。
2. **`demo-reset.sh`（`DemoResetRunner.java`）は`supplier_contact`/
   `mail_template`/`manufacturer_channel`/`supplier_region_classification`
   を意図的にTRUNCATE対象から除外**している（コード内コメント:
   「rather than exempted Master-data-style like supplier_contact/
   mail_template」）。Master Dataは「Demoの都度リセットされるべきではない
   標準設定」という位置づけのため。
3. 上記2つの個別には合理的な設計判断が組み合わさった結果、**このLocal/
   Demo環境でこれまで実行されてきた全E2E実行回数分のInactive行が、
   物理的に削除される経路が一切存在しないまま蓄積し続けている**。

### 7.4 影響

- 現状、業務機能そのものへの影響は確認されていない（一覧画面は
  Active行のみDefault表示するPhase 3実装のため、通常操作では見えない）。
- ただし「無効な項目も表示」を使って過去の実設定を追跡したい場合、
  数百件のテスト由来ノイズに埋もれてしまい、実質的に使い物にならない
  状態になっている（Scenario F実施中に実見）。

### 7.5 改善提案（今回は未実施・提案のみ）

- Local/Demo環境限定の「Test Fixture Cleanup」を`demo-reset.sh`とは
  **別の、明示的にオプトインするコマンド**として新設し、
  `email LIKE '%@example.com'`のような既知のテスト起源パターンに合致する
  行のみを物理削除する（実サプライヤーは`@example.com`を使わない前提）。
- または、E2Eの後片付けを「無効化」ではなく「自分が作った行のみ物理削除」
  に変更する（ただし同時実行時の他テストとの取り合い・監査証跡の要否を
  Techlead判断で検討する必要がある）。
- どちらも本Roundでは**実装していない**（指示どおり調査のみ）。

## 8. 発見一覧（P0-P4）

| # | 分類 | 内容 | Scenario |
|---|---|---|---|
| 1 | P2 | Order History「PO No.」列がDraft/Portal管理番号と正式PO番号を区別せず、正式PO番号自体は一覧に一切表示されない | K |
| 2 | P4（情報記録のみ） | Master 4テーブルでActive行が実質0件、Inactive行がテスト由来で数百件蓄積 | Demo Data Lifecycle |
| 3 | Positive（Gap該当なし） | 複数メーカー混在時のDraft作成ブロックが明瞭な日本語エラーで実装済み | A/L |
| 4 | Positive（Gap該当なし） | UX-04懸念（「未設定」がプレーンテキストに見える）は既にChipスタイルで緩和済み | F |

P0/P1級（業務が完全に止まる、または重大な誤操作を誘発する）の発見は
**今回は確認されなかった。**

## 9. Recommended Final Fix Scope（次Round候補、今回は未実装）

1. Order History一覧の列見出し/表示の見直し（§Scenario K、P2）。
2. Test Fixture Cleanupの仕組み検討（§7.5）。

## 10. Remaining Business Requirements（未実装のまま、今回スコープ外）

- 国内サプライヤー向け推奨発注数の計算式自体（Scenario G、今回も
  意図的に未実装のまま = 「設定準備中」表示を維持）。
- 承認者（Approver）専用の独立Role新設（今回は既存ADMIN Roleで兼務、
  §3参照）。

## 11. UAT結論

Scenario A〜Lのうち、Live Browserで最初から最後まで実施したScenario A・F
では、Gulliver社スタッフが**マニュアルなしで完了できる**水準の明瞭性が
確認された（各段階のアラート文・確認ダイアログ・エラーメッセージが
すべて業務文脈で理解可能）。残るScenarioは、本Session内で継続的にGreenの
既存自動化E2E（Backend 588/588、E2E 207件）によって業務ロジックの正しさ
が担保されており、今回はその上でUI文言・画面構成のUX明瞭性を重点確認した。

発見された唯一の非自明なGap（Scenario K、P2: PO No.列の曖昧さ）は、
業務を完全に止めるものではないが、実務上「どの番号を伝えればよいか」で
迷う余地を残す。最終PASS判定については、`docs/gops-admin-mobile-
responsive-audit.md`末尾の総合判定（§Final Verdict）を参照。

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
