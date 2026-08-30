# Target Price Change Workflow（Phase 8-A）

**Status**: Docs / Design Only（Phase 8-A当時）。Frontend実装・Backend実装・DB Migration・API追加・Legacy変更は一切行っていない。本Documentは価格変更機能の**Target Design（Workflow / Architecture / Business Rule Classification）**であり、実装はPhase 8-Aでは禁止されていた。

**Phase 8-J追記（Documentation Inventory, ドキュメント自体は変更していない）**: 16章A（Foundation）の一部はPhase 8-B/8-Dで**FOUNDATION IMPLEMENTED**（Current Price/Margin Preview表示、Change Set DRAFT作成・編集・Baseline Snapshot・Concurrency Check — `docs/legacy-price-change-reverse-engineering.md`および実装コード参照）。**Submit/Approve/Apply/G-SYS反映・赤字警告Threshold・EC個別価格考慮はいずれもPhase 8-Jまで未実装**（Status Enumに`SUBMITTED`/`APPLIED`/`FAILED`/`CANCELLED`は定義されているが到達不可能な状態のまま）。本章以下のTarget Design自体は歴史的記録としてそのまま残す — 17章のCUSTOMER REVIEW事項（PC-1〜PC-15）はいずれも未解決のまま。

**目的**: Phase 7-Jで完了したLegacy Price ChangeのSource Reverse Engineering（`docs/legacy-price-change-reverse-engineering.md`）を基準線とし、Gulliver社の要望（2026/08/26打ち合わせ Slide 11）を踏まえた新Portalの価格変更機能について、Target Workflow・State Model・データ所有権・G-SYS反映方式・Concurrency対策・UI構成を設計する。未確定のBusiness Ruleは独自に決定せず、すべてCUSTOMER REVIEWとして明示する。

**表記凡例**（本Document全体で使用）:
- 🟢 **Source Confirmed** — Legacy Sourceで確認済みの事実（`legacy-price-change-reverse-engineering.md`を参照元とする）
- 🟡 **Gulliver Requirement** — 2026/08/26打ち合わせ議事メモの原文に基づく要望
- 🔵 **Target Proposal** — 本Documentが提案するTarget Design（未実装、Gulliver承認前提）
- 🔴 **CUSTOMER REVIEW** — Gulliver社の意思決定が必要な未確定事項

---

## 1. Executive Summary

- Legacy G-SYSの価格変更は、画面編集機能を持たず「Excel Export → 手作業編集 → Excel Import」の即時DB上書きのみで完結している（History・Effective Date・電子承認Workflow・赤字警告はいずれも存在しない）。この現状は発注(Ordering)機能におけるOfficial PO運用と同型のアーキテクチャパターンである。
- Gulliver要望4項目（一括変更／将来日予約／利益率・赤字警告／変更履歴）のうち、Legacyに現行踏襲可能なのは「Item Group単位の一括変更」のみで、残り3項目は新規Business Ruleとして扱う必要がある。
- 本Documentでは、この4項目を実現するTarget Workflow（Price Change Request/Change Setという新しい業務Transaction単位）を提案するが、**Approval要否・Effective Date予約の採否・赤字警告の閾値/挙動**という中核的なBusiness Ruleは、いずれもGulliver社の意思決定なしには確定できないため、すべてCUSTOMER REVIEW（17章）へ送る。
- G-SYSへの反映方式は、発注機能で確立済みの「Portal → Integration Artifact（Excel）→ 既存G-SYS Import Batch」というパターン（`docs/official-po-integration-detailed-design.md`）をPrice Changeにも踏襲することを推奨する（12章）。Legacy DBへの直接WriteやLegacy側への新規Trigger機構追加は、原則違反であり不採用とする。
- Concurrency対策は、発注機能で実装済みのBaseline/Fingerprint方式（`docs/excel-legacy-concurrency-control.md`）の**設計思想**（Snapshot→Compare→Diff→Conflict判定はMechanism、判定後の挙動はPolicyとして分離する）を参考にしつつ、Price特有の事情（複数Future変更・同一Effective Date衝突等）に合わせて別設計とする（13章）。機械的な流用はしない。

---

## 2. Current G-SYS Facts（基準線 — Source Confirmed）

Phase 7-Jで確定したCurrent Factのみを固定する。すべて`docs/legacy-price-change-reverse-engineering.md`のSource Confirmed Factsを一次情報とする（再調査はしない）。

| # | Current G-SYS Fact | 出典（RE Document章） |
|---|---|---|
| 1 | 🟢 価格変更専用の画面編集機能は存在しない | RE 1章・12章-1 |
| 2 | 🟢 実運用は「Excel Export（`PriceList.java`）→ 手作業編集 → Excel Import（`MsPriceListImportBatch`）」のRound Tripのみ | RE 1章・2章 |
| 3 | 🟢 Importは`MS_ITEM`（SKU単位）／`MS_ITEM_GRP`（Item Group単位）を**即時**UPDATEする | RE 4章・12章-2 |
| 4 | 🟢 Item Group単位の一括変更（Excel `grp`行）は既存機能として存在するが、配下SKUへの自動連動有無はSource未確認 | RE 4章・7章・11章Q-P3 |
| 5 | 🟢 利益率（`PROFIT_RATE_SELL`/`PROFIT_RATE_SALE`）の計算ロジックは`Formula.java`に存在する | RE 6章・12章-5 |
| 6 | 🟢 Future Price・Effective Dateに相当するColumn/Entity/BatchはSource全体で0件 | RE 1章・12章-2 |
| 7 | 🟢 Price History（変更履歴）に相当するTable/Entity/Batchは0件（`updateUserId`/`updateDatetime`のみ残存） | RE 1章・12章-3 |
| 8 | 🟢 赤字（マイナス利益率）を検知して警告・エラーにするロジックは0件 | RE 6章・12章-5 |
| 9 | 🟢 電子的なApproval Workflow（画面上の承認）は存在しない | RE 1章・12章-4 |
| 10 | 🟢 変更幅超過時の「承認」はExcelファイル自体への`APPROVAL`列手入力（`ACCEPT`固定文字列）による自己申告であり、電子承認ではない | RE 4章・12章-4 |
| 11（本Phase追加） | 🟢 `MsPriceListImportBatch`は`AbstImportBatch.addErrorCell()`経由で1行でもCell Validation Errorがあるとファイル全体が`errorFileNameHolder`に登録され、DB更新フェーズはファイル単位でスキップされる（`AbstImportBatch.java:332-336`, `661-668`）。すなわちPrice List Importも Official PO Importと同じ**File単位オールオアナッシング**である | 本Phase Source確認（`AbstImportBatch.java`） |

Current Factと推測は混在させない。11番のみ本Phaseで追加確認したSource Confirmed事実であり、他はすべてPhase 7-Jからの再掲。

---

## 3. Gulliver Requirements（原文ベース）

出典: `docs/G-Sys_mtg_20260826_02.pptx` Slide 11「11. 価格変更業務」（原文はテキスト抽出済み、`legacy-price-change-reverse-engineering.md` 7章に既出）。

> 発注業務に加えて、価格変更についてもオンライン化の要望がありました。現在行っているデータのダウンロード、価格修正、CSV等による再反映を減らし、G-SYS上で価格を変更できる仕組みについて検討しました。あわせて、複数商品の一括変更／将来日の価格変更予約／利益率や赤字となる価格への警告／変更履歴等についても今後の検討対象とします。

4項目を分類する（分類基準は本Phaseの指示に基づく: A=Legacyに存在／B=Legacyに部分的に存在／C=Legacyに存在しない／D=Sourceだけでは判断不能）。

| Gulliver要望 | 分類 | 理由 |
|---|---|---|
| 🟡 複数商品の一括変更 | **B** | `MS_ITEM_GRP`単位の一括設定は既存（2章-4）。ただし「複数商品」＝真に任意のSKUを跨いだ選択という意味であれば、その単位はLegacyに存在しない |
| 🟡 将来日の価格変更予約 | **C** | Effective Date/Future Price概念がSource上0件（2章-6） |
| 🟡 利益率や赤字となる価格への警告 | **B/C混在** | 利益率の**計算**自体はB（既存ロジックを再利用可能、6章参照）。**警告・閾値判定・保存禁止等のAction**はC（Source上0件） |
| 🟡 変更履歴 | **C** | Price History相当のTable/Entity/Batchが0件（2章-7） |
| （参考）🟡 Item Group⇄SKU連動 | **D** | Group価格変更が配下SKUへ自動反映される想定かはSource未確認（2章-4、RE 11章Q-P3） |

---

## 4. Legacy Gap（3章の再整理・詳細版）

`legacy-price-change-reverse-engineering.md` 7章の内容を、本Documentの目的（Target Design）に沿って再整理する。新規調査は行わない。

| Gap領域 | 現状（Legacy） | Target Designで必要になる新規要素 | 新規Business Ruleか |
|---|---|---|---|
| 一括変更の単位 | Item Group単位のみ | SKU横断の任意選択、Brand単位、Filter単位（9章で比較検討） | 単位の拡張自体はTarget Proposal、「どの単位を正式採用するか」はCUSTOMER REVIEW |
| Effective Date | 存在しない（即時反映のみ） | Future Price予約・Scheduled Apply機構 | 全面的に新規Business Rule（CUSTOMER REVIEW） |
| 赤字警告 | 計算のみ存在、警告なし | 警告UI・閾値判定・保存可否制御 | 閾値・Action部分はCUSTOMER REVIEW（計算再利用自体はTarget Proposalとして安全） |
| 変更履歴 | 存在しない | Price History Table（Portal側で新規保持） | 「何を記録するか」の骨格はTarget Proposalとして提示可能だが、Retention期間等はCUSTOMER REVIEW |
| Approval | 電子承認なし（ファイル内自己申告のみ） | Portal上のApproval Workflow要否自体が未確定 | 全面的にCUSTOMER REVIEW（8章・11章） |

---

## 5. Target Workflow（提案 — Target Proposal）

**この章で示す業務フローの順序・ステップ構成は、Legacy Factではなく本Documentが提案するTarget Proposalである。** 発注(Ordering)機能のOrder Draft → Submit → Approve → Send → Supplier Responseという既存パターンを参考にしつつ、Price Change特有の事情（Effective Date・複数SKU一括・G-SYS反映が非同期）に合わせて再構成した概念モデルであり、機械的な流用ではない。

```
[1] Price Change Request（Change Set）作成
        ↓
[2] 対象商品選択（SKU個別 / Item Group / Brand / Filter条件、9章参照）
        ↓
[3] 現在価格表示（G-SYSを都度READ、7章のSource of Truth原則）
        ↓
[4] 新価格入力（SKUごと、またはItem Group一括値）
        ↓
[5] 変更影響表示（旧価格・新価格・差額・変更率・原価・利益・利益率、10章）
        ↓
[6] 🔴 必要なReview / Approval（要否自体がCUSTOMER REVIEW、11章）
        ↓
[7] 🔴 Effective Date（即時 or 将来日、採否自体がCUSTOMER REVIEW、8章）
        ↓
[8] G-SYS反映（Excel Artifact生成 → 既存Import Pipeline、12章）
        ↓
[9] History記録（Portal側Audit Evidence、14章）
```

[1]〜[5]・[9]は技術的に必要な骨格としてTarget Proposalに含める。[6][7]は、それを実施するかどうか自体がGulliver社の意思決定に依存するため、**フロー図に載せてはいるが未確定のOptional Stepとして扱う**（Approval不要・即時反映のみという回答であれば[6][7]は消える）。

---

## 6. State Model

**指示のとおり、DRAFT/PENDING_APPROVAL/APPROVED/SCHEDULED/APPLIED/FAILED/CANCELLEDという候補Stateをそのまま採用しない。** 各Stateについて、技術的必要性（Foundation）とBusiness Rule依存性（CUSTOMER REVIEW）を個別に判定する。

| State候補 | 必要性の根拠 | 分類 |
|---|---|---|
| **DRAFT** | Price Change Requestは複数SKU・複数値を扱う入力作業であり、確定前の一時保存領域が技術的に必須（Order Draftと同型の必要性） | 🔵 Foundation（技術的必要性、Business Rule非依存） |
| **PENDING_APPROVAL** | Approval Workflow自体の要否が未確定（11章） | 🔴 CUSTOMER REVIEW依存 — Approval不要という回答ならこのState自体が不要になる |
| **APPROVED** | 同上 | 🔴 CUSTOMER REVIEW依存 |
| **SCHEDULED** | Future Price予約の採否が未確定（3章でC分類） | 🔴 CUSTOMER REVIEW依存 — 即時反映のみという回答ならこのState自体が不要になる |
| **APPLIED** | G-SYSへの反映は非同期（12章、Officialのpolling方式と同型）であり、「Portalが送信した」と「G-SYSに実際に反映された」は別事実。この区別自体はBusiness Ruleでなく、G-SYS反映方式（Excel Import Batch）の技術的性質から生じる | 🔵 Foundation（技術的必要性） |
| **FAILED** | File単位オールオアナッシング（2章-11）であるため、Import失敗はSKU単位ではなくChange Set単位で起こりうる。失敗を表現するState自体はApproval要否と無関係に必要 | 🔵 Foundation（技術的必要性） |
| **CANCELLED** | 未反映のChange Setを取り下げる操作自体は基本的なUX安全弁として必要だが、「誰が・いつまで取消せるか」（特にSCHEDULED後の取消権限）はBusiness Rule | State自体🔵Foundation、取消権限は🔴CUSTOMER REVIEW（11章） |

**Target Proposal（最小State Skeleton）**: Business Ruleに依存しない部分だけを先に固定すると、以下の骨格になる。

```
DRAFT → SUBMITTED → (G-SYS反映試行) → APPLIED
                                    → FAILED
DRAFT / SUBMITTED → CANCELLED（未反映の間のみ）
```

PENDING_APPROVAL・APPROVED・SCHEDULEDは、11章・8章のCUSTOMER REVIEW回答が得られた時点で、この骨格の`SUBMITTED`と`(G-SYS反映試行)`の間に挿入する形で拡張する（例: Approval必須ならDRAFT→PENDING_APPROVAL→APPROVED→SUBMITTED、Future予約ありならAPPROVED→SCHEDULED→(Effective Date到来)→SUBMITTED）。**この拡張の具体形自体は今回決定しない。**

---

## 7. Data Ownership / Source of Truth（原則）

| データ | Source of Truth | 理由 |
|---|---|---|
| 現在価格（Current Price） | **G-SYS**（`MS_ITEM`/`MS_ITEM_GRP`） | Portalは価格の複製を正としない。Dashboard等で既に確立された「Portalは都度G-SYSをREADし、長期キャッシュしない」原則（`G-SYS_Online-Ordering_Prototype_Requirements.md`のData Freshness議論と同型）をPrice Changeにも適用する |
| 将来価格（Future Price、採用される場合） | **Portal**（暫定） | G-SYSにこの概念が無い以上、採用するならPortalが唯一の保持者にならざるを得ない。ただしFuture Price自体の採否がCUSTOMER REVIEW（3章）であるため、本項目は条件付きのTarget Proposal |
| 適用済み価格（Applied Price） | **G-SYS**（確認後） | 8章参照。Portal側の「Applied」表示は、Legacy側の実値をREADして確認できた場合にのみ真とする（Portalの送信意図だけでApplied扱いにしない） |
| 変更履歴（Price History） | **Portal**（新規） | Legacyに保持機構が無いため、Portal自身が記録しない限りどこにも残らない。ただしPortal起点の変更のみを記録でき、Legacy側で直接Excel編集された変更は捕捉できない（19章のRiskとして明記） |

---

## 8. Current / Future / Applied Price（詳細設計）

### A. Current Price
🔵 G-SYSを唯一のSource of Truthとする。Portalは画面表示のたびにLegacy READ ONLY接続で都度取得し、Portal DB側に恒久的なコピーを持たない（7章）。Change Set作成時に一時的なSnapshot（13章のBaseline）を取ることはあるが、これは「表示用キャッシュ」ではなく「Concurrency検出用の一時記録」という別目的である。

### B. Future Price
🔴 CUSTOMER REVIEW（3章でC分類、採否未定）。仮に採用する場合の責任範囲（Target Proposalとして提示、確定ではない）:
- Portalが「まだG-SYSには反映されていない、予約中の価格」を保持する唯一の主体になる。
- G-SYSは将来価格の概念を持たないため、Effective Date到来までG-SYS側の現在価格は変わらない — Portal上の「現在価格」表示とLegacy実値は、予約期間中ずっと一致している（Future Priceは別欄に表示する必要がある、UIの誤解防止）。
- Effective Date到来時に何をトリガーとしてApplyするか（Batch/Scheduler/手動確認）はG-SYS反映方式（12章）と併せてCUSTOMER REVIEW。

### C. Applied Price
🔵 Target Proposal: 「Applied」は**Portalが送信した時点ではなく、G-SYSへの反映をLegacy READ ONLYで確認できた時点**を指す。これは`official-po-integration-detailed-design.md` 9章のSuccess Detection（TR_PO/STATUS='OFFICIAL'をPollingで確認）と同型の設計思想であり、Price Changeでも「Import Batch実行後、対象`MS_ITEM`/`MS_ITEM_GRP`の価格列がChange Setの新価格と一致しているかをPollingで確認する」方式を提案する。ただし2章-11（File単位オールオアナッシング）により、一部SKUのみ成功という状態はChange Set内では基本的に発生しない（ファイル全体が成功/失敗のいずれか）。

### D. Price History
14章で詳述。ここではCurrent/Future/Appliedとの関係のみ: Change SetがAPPLIEDに遷移した時点で、その内容（変更前値・変更後値等）をHistoryへ記録する。DRAFT/CANCELLEDのまま終わったChange Setを記録するかどうかは17章のCUSTOMER REVIEW（監査要件次第）。

---

## 9. Bulk Price Change設計

Gulliver要望「複数商品の一括変更」（3章）を具体化する。

| 選択単位 | Legacy現行 | Target Proposal | 備考 |
|---|---|---|---|
| SKU個別選択 | 存在（Excel行単位） | 🔵 対応（Change Set内に複数SKU行を持てる） | 最も細かい粒度 |
| Item Group単位 | 🟢 既存（`grp`行、2章-4） | 🔵 継続対応 | Legacy踏襲部分。ただし配下SKUへの自動連動有無は🔴CUSTOMER REVIEW（RE 11章Q-P3） |
| Brand単位 | Source上、価格とBrandの直接対応は未確認（RE 8章） | 🔴 CUSTOMER REVIEW | Item GroupとBrandの対応関係自体がSource未確認のため、Brand単位一括変更を安全に設計できる材料が今は無い |
| Filter結果一括選択（在庫状況・カテゴリ等の検索条件） | 存在しない | 🔵 Target Proposal（技術的には可能） | 「Filter条件で選んだ後、実際に何件のSKUが対象になったか」を確定させるタイミング設計が必要（下記の大量SKU対策） |
| Excel Upload併用 | 🟢 これがLegacyの唯一の実運用方式（RE 2章） | 🔵 Target Proposal（Portal内Upload機能として再構成） | 12章のG-SYS反映方式と直結。Portal内で完結させるか、Legacy方式をそのまま画面化するかは設計選択（12章） |

**大量SKUを扱う際の設計注意**（指示に基づく明示的な原則）: Filter結果一括選択やItem Group一括選択で数百〜数千SKUが対象になり得る場合、**Frontendが全件をメモリ上に保持し1回のHTTPリクエストで送信する設計は採用しない**。Target Proposalとして、Change Setは「対象SKUの実データ配列」ではなく「選択条件（明示的SKUリストのServer側永続化、またはItem Group参照、またはFilter条件のSnapshot）」として保持し、対象件数の確定・変更影響計算（10章）はServer側で行う方式を推奨する。件数が大きい場合のPagination/非同期処理の詳細は実装Phaseで検討する（本Phaseでは設計原則の提示に留める）。

---

## 10. Margin / Loss Warning設計

`Formula.java`の利益率計算ロジック（RE 6章）を土台に、価格変更時の影響表示を設計する。

**用語の分離**（指示どおり）:
- **Cost**: `costThisMonthAvg`等、価格変更Batchの対象外で別Batch（`PrStkInReportImportBatch`等）が更新する値。Price Changeは参照するのみで、Costの変更は本機能のスコープ外。
- **Selling Price**: `PRC_SELL`/`PRC_SALE`（税抜換算値、`Formula.PRC_LIST()`等と同じ計算式）。
- **Margin**: 販売価格 − Cost（金額）。
- **Margin Rate（利益率）**: `Formula.PROFIT_RATE_SELL()`/`PROFIT_RATE_SALE()`と同じ計算式（送料無料時の分岐を含む）。

🔵 Target Proposal: 新価格入力画面（5章[5]）で、SKUごとに以下を並べて表示する再利用可能な計算式（`Formula.java`のロジックをPortal側で再実装するか、参照アルゴリズムとして踏襲するかは実装Phaseで検討）:

```
旧価格 | 新価格 | 差額 | 変更率 | 原価(Cost) | 利益(Margin) | 利益率(Margin Rate)
```

**明確に決めないこと**（指示どおりCUSTOMER REVIEWへ送る、17章）:
- 「利益率○%以下なら警告」の閾値
- 「赤字なら保存禁止」とするか、警告のみに留めるか
- 赤字価格の場合に追加承認を要求するか

計算結果の**表示**自体はSource確認済みロジックの再利用でありBusiness Rule決定を伴わないため🔵Target Proposal（Foundation候補、16章）とするが、**その結果に対するAction（警告・禁止・承認要求）は一切決めない**。

---

## 11. Approval設計

発注(Ordering)のApproval Workflow（`PortalOrder`: DRAFT → PENDING_APPROVAL → APPROVED、ADMINによるApproved-with-Changes・Return-to-Operator等）は、あくまで**参考パターン**として存在するが、**Price Changeにそのまま流用できるとは仮定しない**（指示どおり）。理由: 発注は個別Order単位の金額判断だが、価格変更は「一度に多数のSKUへ波及し、しかもEC各モールへ即座に露出しうる」（RE 13.1章）という異なるリスク特性を持つため、承認要否の基準が同じとは限らない。

以下をCUSTOMER REVIEW候補として整理する（17章に転記）:

| 論点 | 内容 | 既存Role（OPERATOR/ADMIN）で表現可能か |
|---|---|---|
| 作成者 | 誰がPrice Change Requestを作成できるか | 既存の`ROLE_OPERATOR`/`ROLE_ADMIN`（`PortalUser.java`）で表現可能と推定されるが、価格変更に第三のRole（例: 価格管理専任者）が必要かはGulliver組織構造次第 — 未確認 |
| 承認者 | 誰が承認するか、Approval自体が必要か | 未確定（8章） |
| 承認閾値 | 変更額・変更率によって承認要否が変わるか（Legacyの`VAL5`/`VAL6`許容幅と同様の考え方が使えるか） | Legacyの許容幅設定（RE 4章）は参考になるが、その具体値・対象価格種別（List/Sell/Sale）の再利用可否は未確認 |
| 自己承認 | ADMIN自身の変更を自己承認可能とするか | 発注では自己承認に相当する概念（ADMINがDraftを直接編集し承認）が存在するが、Price Changeで同様に扱ってよいかは別判断 |
| 予約取消権限 | SCHEDULED状態（採用される場合）の取消を誰が行えるか | 未確定 |
| 適用後Correction | Applied後に誤りが発覚した場合、取消ではなく「訂正」として扱うフローが必要か（Excel再Importで上書きするだけで良いか、別のCorrection Workflowが要るか） | 未確定。発注のRevision（修正版再送）に相当する概念が価格変更にも必要か検討が要る |

---

## 12. G-SYSへの反映方式

`docs/official-po-integration-detailed-design.md` 8章の比較（Portal→G-SYSの投入方式比較）と同じ観点でPrice Changeを評価する。

| 案 | 内容 | 評価 |
|---|---|---|
| **A. 既存Excel Import Pipeline再利用** | PortalがPrice List Import用Excel（4章の列契約に準拠したArtifact）を生成し、既存Import Folder（`PRC_LIST`コード）へ書き込むのみ。`MsPriceListImportBatch`の起動方式には一切手を加えない | **推奨**。Official PO Integrationと全く同じ結論に至る根拠がある: (1) Price List Importも`AbstImportBatch`基盤であり起動方式がSource上不明（Officialと同型、8章参照）、(2) 既存のValidation（承認幅チェック等、RE 4章）とBusiness Logic（`Formula`による派生値再計算）をそのまま再利用でき、Portal側で同じロジックを再実装する必要がない、(3) Legacy側の変更が一切不要 |
| **B. 新Integration Worker** | Excelを書き込む主体をPortal Serverではなく専用Workerに分離する | Aと直交する論点（Officialと同じ整理）。採用してもAの「Fileを置くだけ」という投入方式自体は変わらない |
| **C. G-SYS DB直接更新** | Portalが`MS_ITEM`/`MS_ITEM_GRP`へ直接UPDATE | **不採用**。標準constraint（Legacy DB直接Write禁止）に加え、TR_PO同様`MS_ITEM`にも実効的なOptimistic Lock機構が無い可能性が高く（未確認だがTR_PO.VERSIONが`@Transient`という既知の前例、`excel-legacy-concurrency-control.md` 1章）、Legacy側のValidation・派生値再計算ロジック（`Formula`）を完全にPortal側で再現しない限り整合性を保証できない |
| **D. その他（API連携等）** | Legacy側に新規APIを公開する | **不採用**。Legacy変更が必要になり原則違反 |

**結論**: A（既存Excel Import Pipeline再利用）を推奨。Official PO Integrationの「Portal → Integration Artifact → Existing G-SYS Import」パターンをPrice Changeにも踏襲する。ただしArtifact生成の列フォーマットがExport（`PriceList.java`）と完全一致するかはRE 11章Q-P2が未確認のため、実装Phase着手前に追加確認が必要。**本Phaseでは実装しない**（Artifact生成コード・Import Folder書き込みコードは一切作成していない）。

---

## 13. Concurrency / Safety

発注のBaseline/Fingerprint方式（`excel-legacy-concurrency-control.md`）を機械的にコピーせず、Price特有の論点に合わせて再設計する。

### 13.1 Baseline / Current / Proposed比較の要否
🔵 Target Proposal: Price Change Request作成時に対象SKU/Item Groupの現在価格をBaselineとしてSnapshotし、G-SYS反映直前（12章のArtifact生成直前）に現在のLegacy価格を再READしてBaselineと比較する、という設計思想自体は発注のBaseline/Fingerprintパターンと同型に妥当する（「Portalが確認した時点」と「実際に反映する直前」の間にLegacy側で価格が変わっているリスクは、PO同様に存在する）。ただし対象がPO単体ではなく複数SKU/Item Groupにまたがるため、Fingerprintは「Change Set全体」ではなく**SKU/Item Group単位**で個別に持つ必要がある（1つのSKUだけ競合していても他のSKUは反映してよいのか、Change Set全体を止めるのかは、2章-11のFile単位オールオアナッシング制約と合わせてCUSTOMER REVIEW）。

### 13.2 検討すべき個別論点

| 論点 | 内容 | 分類 |
|---|---|---|
| 同一SKUへの複数Future Price変更 | 同じSKUに対して複数のSCHEDULED Change Setが並存する場合の扱い（最新が勝つか、拒否するか、Effective Date順に並べるか） | 🔴 CUSTOMER REVIEW（SCHEDULED自体の採否が前提のため8章と連動） |
| 同一Effective Date | 複数Change Setが同じ日を指定した場合の適用順序 | 🔴 CUSTOMER REVIEW |
| 重複Change Set | 同一SKUを含む複数のDRAFT/PENDING Change Setの同時存在を許すか | 🔴 CUSTOMER REVIEW（技術的にはBaseline比較で検出可能、許容方針は業務判断） |
| 予約後のLegacy側手動変更 | SCHEDULED中にLegacy側で直接Excel編集された場合、Baseline比較で検出できるが、その後どう扱うか（Apply中止・警告・強制上書き） | 🔴 CUSTOMER REVIEW（Mechanism=検出はTarget Proposalとして提示可能、Policy=対応はCUSTOMER REVIEW、`excel-legacy-concurrency-control.md`と同じMechanism/Policy分離の考え方） |
| 一部SKUだけApply失敗 | 2章-11により、Import Batchは**File単位**でオールオアナッシングであることがSource確認済みのため、「一部SKUだけ成功」という状態はChange Set＝1 Artifactである限り基本的に発生しない。ただし1 Change Setを複数Artifactに分割する設計（9章の大量SKU対策）を採る場合は、Artifact単位での部分失敗が起こり得る | 🔵 Target Proposal（Artifact分割方針が決まった時点で再設計） |

---

## 14. Audit / History

Price History Table（Portal側で新規保持、7章）の記録項目を設計する。**Retention期間・「どこまで遡って保持するか」は決定しない**（指示どおり）。

| 項目 | 内容 |
|---|---|
| 変更前（Before） | List Price / Sell Price / Sale Price等、変更対象の全価格列の変更前値 |
| 変更後（After） | 同上の変更後値 |
| Effective Date | 適用日（即時反映の場合は反映日時と同一） |
| 作成者 | Change Set作成者（PortalUser） |
| 承認者 | Approval採用時のみ（11章、未確定） |
| 適用日時 | G-SYSへの反映が確認できた日時（8章C. Applied Priceの定義に基づく） |
| 対象SKU / Item Group | 変更対象の識別子 |
| Change Set ID | どのRequest/Change Setに属する変更かの参照 |
| 適用結果 | APPLIED / FAILED（6章のState） |

**Portal起点の変更のみを記録できる**という制約を明記する: Legacy側で直接Excelを編集し画面を介さず価格を変えた場合、Portalはそれを検知・記録できない（19章のRiskとして再掲）。

---

## 15. UI Screen Map

指示に基づき候補7画面を提示した上で、統合案を検討する。**画面を増やしすぎない**方針に基づき、以下の3画面に統合することを提案する。

| 統合後の画面 | 統合される候補 | 統合理由 |
|---|---|---|
| **Price Change List** | Price Change List + Scheduled Changes + Price History | Order List画面がStatus別Filter（Draft/Awaiting Supplier等）で一覧を出し分けているのと同じパターンで、Status（DRAFT/APPLIED/SCHEDULED等）・期間によるFilter/Tabで1画面に統合できる。History専用の別画面を設ける必然性は薄い |
| **Price Change Create/Edit** | Price Change Create/Edit + Product Selection + Price Impact Review | Order Draft画面が商品選択・数量入力・保存を1画面（1つのWizard的な状態）で完結させているのと同型のパターン。対象商品選択→新価格入力→影響表示を、画面遷移ではなく同一画面内のStep/Sectionとして扱う |
| **Price Change Detail** | Approval Detail + （個別Change SetのHistory明細） | Order Detail画面が承認操作・Audit Timelineを1画面に統合しているのと同型。Approval操作（採用される場合）とそのChange Set固有の変更履歴明細をここに集約する |

**既存Portal Navigationへの統合**: 既存の`nav-orders`等と並ぶ形で`nav-price-changes`のような新規Navigation項目を追加する想定（🔵Target Proposal、実装はしない）。Dashboard等への価格変更Attention表示要否は別途検討（本Phase範囲外）。

---

## 16. Foundation候補の分類

「Customer回答前でも安全に実装可能」の基準は、**未確定Business RuleをDB SchemaやStateに固定しないこと**（指示どおり）。

### A. Customer回答前でも安全に実装可能
- Current Price / Cost / Margin / Margin Rateの読み取り専用表示（10章、既存`Formula`ロジックの再利用・計算のみで判断/警告を伴わない）
- Price Change Request（Change Set）のDRAFT State骨格とSKU/Item Group選択の永続化（6章の最小State Skeleton、Approval/Scheduled関連フィールドを含まない形）
- Baseline Snapshot機構（13章のMechanism部分のみ。Conflict検出時のPolicyは含まない）
- G-SYS反映用Artifact（Excel）生成ロジックの土台（12章、列フォーマットはRE Q-P2の追加確認が前提）
- Portal起点の変更のみを対象としたAudit Historyの記録機構（14章、Retention期間を除く）

### B. Customer回答後に実装すべき
- PENDING_APPROVAL / APPROVED / SCHEDULED Stateとその遷移ロジック（6章）
- Approval Routing・承認閾値・自己承認可否（11章）
- Future Price予約・Effective Date到来時のApplyトリガー設計（8章B）
- 赤字警告の閾値・保存禁止等のAction（10章）
- Bulk Change単位の正式範囲（Brand単位採否等、9章）
- Concurrency競合時のPolicy（強制上書き/警告/中止、13章）
- Price History Retention期間

### C. 現時点では実装すべきでない
- Legacy DBへの直接Write（12章C、標準constraint違反）
- Legacy側への新規Trigger/API追加（12章D、標準constraint違反）
- EC各モール（Rakuten/Yahoo/Amazon等）個別価格との同期（RE 13.1章、スコープ外）
- Tempostar連携の変更（RE 13.3章、スコープ外・未調査）

---

## 17. CUSTOMER REVIEW

本Documentで発生したCUSTOMER REVIEW項目を集約する。既存3 QA Documentとの重複は無い（Price Change関連項目はPhase 8-A以前は0件、13章「既存QA更新」で別途反映）。

| ID | 論点 | 分類（7-D2） | 出典 |
|---|---|---|---|
| PC-1 | 「複数商品の一括変更」の正式単位（Item Group限定か、SKU横断選択やBrand単位も必要か） | D | 9章 |
| PC-2 | Future Price予約機能の採否そのもの | D | 3章・8章B |
| PC-3 | Effective Date到来時のApplyトリガー方式 | D | 8章B |
| PC-4 | 赤字警告の閾値・Action（警告のみ／保存禁止／追加承認） | D | 10章 |
| PC-5 | Price History記録要否とRetention期間 | D | 14章 |
| PC-6 | Approval Workflowの要否・承認者・承認閾値 | D | 11章 |
| PC-7 | ADMIN自己承認の可否 | D | 11章 |
| PC-8 | SCHEDULED取消権限 | D | 11章・13.2章 |
| PC-9 | 適用後Correctionの扱い（訂正Workflowの要否） | D | 11章 |
| PC-10 | Item Group単位変更の配下SKUへの自動連動有無 | D（Source未確認のためErnest確認も検討余地あり） | 4章・9章 |
| PC-11 | 同一SKUへの複数Future変更・同一Effective Date衝突時の扱い | D | 13.2章 |
| PC-12 | Legacy側手動変更検出時のPolicy（Apply中止/警告/強制上書き） | D | 13.2章 |
| PC-13 | Brand単位一括変更の実現可否（Item GroupとBrandの対応関係、Master構造） | D（Ernest確認候補: Master構造はErnestが把握している可能性） | 9章・RE 11章Q-P4 |
| PC-14 | 価格変更に関する追加Role（価格管理専任者等）の要否 | D | 11章 |
| PC-15 | G-SYS反映Artifactの列フォーマットがExport（`PriceList.java`）と一致するか | Source追加調査候補（RE Q-P2の再掲、Gulliver判断ではなくSource調査） | 12章 |

---

## 18. Recommended Implementation Phases（提案・未確定）

以下はTechlead（ChatGPT）・ユーザーの確認を前提とした**提案**であり、確定した実装計画ではない。

- **Phase 8-B候補**: 16章A（Foundation）の範囲 — Current Price/Margin表示、Change Set DRAFT骨格、Baseline Mechanism、Artifact生成土台。CUSTOMER REVIEW回答を待たずに着手可能な範囲。
- **Phase 8-C候補**: 17章のCUSTOMER REVIEW回答が揃った領域から順次、Approval/Scheduled/赤字警告Action/Retentionを実装。
- **Phase 8-D候補**: G-SYS反映の実結線（12章A方式のArtifact書き込み・Polling）。Official PO Integrationの7-C2B相当のPhaseとして、実結線は別途Gateを設ける。

---

## 19. Risks

- **EC個別価格との不整合**: `MsItem`のモール別売価（RE 13.1章）はPortalのスコープ外のままであり、Portalで変更したSell/Sale価格とEC各モール掲載価格が乖離する可能性がある。ユーザーへの誤解防止のため、UI上で「これはG-SYS内部価格のみであり、EC掲載価格は別管理」と明示する必要がある。
- **非同期反映によるUX混乱**: G-SYS反映がPolling方式（12章A、8章C）である以上、Portal上で「送信済み」と表示されてから実際に「Applied」になるまでタイムラグが生じる。発注のOFFICIAL_PO_PENDING同様のUXパターンを踏襲する必要がある。
- **File単位オールオアナッシングによる分かりにくさ**: 2章-11の確認により、多数SKUを含むChange Setで1SKUでもValidation Errorがあると全体が失敗する。Portal UI側で「どのSKUが原因で全体が失敗したか」を分かりやすく提示できないと、利用者が混乱する（Legacy側のError通知がフリーテキストのMailのみである制約はOfficial PO Integrationと同様）。
- **Historyの不完全性**: Portal起点の変更のみ記録できるため、Legacy側で直接Excel編集された変更はHistoryに現れない。「Portal Historyが全履歴である」という誤解を招かないUI表現が必要。
- **Approval境界の曖昧さ**: Approval要否・自己承認可否が未確定のまま実装が先行すると、後からBusiness Ruleに合わせて権限モデルを作り直すコストが生じる。16章のFoundation分類を厳密に守り、Approval関連StateはCUSTOMER REVIEW確定後にのみ追加する。
- **Legacy Optimistic Lockの不在**: `MS_ITEM`にTR_PO同様の実効的なLock機構が無い可能性が高く（未検証）、13章のConcurrency対策なしに直接反映すると意図しない上書きが起こり得る。

---

## 20. Final Classification Table

7-D2taxonomy（Source Confirmed / Ernest Question / Gulliver Current Operation / Gulliver Future Decision）に沿って、本Document全体の論点を最終整理する。

| # | 論点 | 分類 | 状態 |
|---|---|---|---|
| PC-Fact-1〜11 | 2章のCurrent G-SYS Facts | A（Source Confirmed） | 確定済み、質問化しない |
| PC-1 | 一括変更の正式単位 | D | 17章CUSTOMER REVIEW |
| PC-2 | Future Price予約の採否 | D | 17章CUSTOMER REVIEW |
| PC-3 | Effective Date Applyトリガー | D | 17章CUSTOMER REVIEW |
| PC-4 | 赤字警告閾値・Action | D | 17章CUSTOMER REVIEW |
| PC-5 | History Retention | D | 17章CUSTOMER REVIEW |
| PC-6〜9 | Approval関連（要否/承認者/自己承認/Correction） | D | 17章CUSTOMER REVIEW |
| PC-10 | Item Group⇄SKU連動 | D（Ernest確認余地あり） | 17章CUSTOMER REVIEW／13章QA更新で検討 |
| PC-11〜12 | Concurrency Policy | D | 17章CUSTOMER REVIEW |
| PC-13 | Brand単位一括変更・Master構造 | D（Ernest確認候補） | 17章CUSTOMER REVIEW／13章QA更新で検討 |
| PC-14 | 追加Role要否 | D | 17章CUSTOMER REVIEW |
| PC-15 | Artifact列フォーマット一致確認 | Source追加調査（分類外） | 実装Phase着手前に確認 |

**変更したFrontend/Backend/DB Migration/Legacy Source: 0件。** 本Documentのみ新規作成。
