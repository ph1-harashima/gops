# Customer Review Decision Package — Phase 7-D / 7-D2 / 7-E

**Status**: Docs Only（Phase 7-D／7-D2／7-E追加分とも）。コード変更・DB Migration・Legacy変更は一切行っていない。

**目的**: Phase 7-A〜7-C6の各Documentに散在する`CUSTOMER REVIEW`項目をすべて回収し、「何を顧客に確認しないと本番実装できないか」を一本化する（Phase 7-D）。さらにPhase 7-D2で、**「Sourceコードから分からない」＝「Gulliver社へ質問する」ではない**という前提のもと、Phase1社内（特にG-SYS保守担当のErnest）への確認で解決可能な項目を切り分け、最終的にGulliver社へ聞く質問を最小化する。

**対象読者**: 9/17顧客レビューに向けて準備するTechlead（ChatGPT）・SEPG（Claude Code）・Ernest（Phase1 G-SYS保守担当）・実際に顧客へ質問する担当者。

**関連Document**:
- `docs/ernest-current-operation-question-sheet.md`（Phase 7-D2新規 — Ernestへ確認する13項目）
- `docs/customer-review-question-sheet.md`（Gulliver社向け質問票Draft — **Ernest確認前のDraftであり、最終版として確定していない**）

---

## 0. 対象Document（すべて読み直し済み）

- `docs/target-production-procurement-workflow.md`（21章 CUSTOMER REVIEW 13項目、22章 Roadmap、23/25/26/27章の実装結果補記）
- `docs/legacy-procurement-workflow-reverse-engineering.md`（19章 CUSTOMER REVIEW 10項目、21章 Final Classification Table）
- `docs/official-po-integration-detailed-design.md`（21章 CUSTOMER REVIEW 10項目）
- `docs/official-po-integration-foundation.md`（20章 継続確認、新規項目なし）
- `docs/supplier-contact-mail-template-foundation.md`（20章 CUSTOMER REVIEW 8項目）
- `docs/supplier-response-revision-workflow.md`（26章 CUSTOMER REVIEW 7項目）
- `docs/fulfillment-follow-up-foundation.md`（27章 CUSTOMER REVIEW 10項目）
- `docs/excel-legacy-concurrency-control.md`（22章 CUSTOMER REVIEW 10項目）
- 追加参照: `docs/G-SYS_Online-Ordering_Prototype_Requirements.md`（27.4章 `[TBD - CUSTOMER REVIEW]` 19項目 — 全項目の起点）、`docs/G-SYS_Online-Ordering_Prototype_Technical_Design.md`、`docs/role-approval-implementation.md`（20章 CUSTOMER REVIEW 7項目）、`docs/production-ux-workflow-redesign.md`（15.4章 残存CUSTOMER REVIEW 12項目）、`docs/9-17-demo-script.md`（7-C1〜7-C6以前の旧台本 — 本Documentが質問内容として上書き・supersedeする）。

延べ約100件のCUSTOMER REVIEW記述を、1章の方針で**47項目**（Theme A-Hに整理可能なもの）＋**Appendix I 7項目**（Phase 0由来のPrototype UI細部）＝**計54項目**に統合した（重複統合の詳細対応表は14章参照）。**訂正**: Phase 7-D完了報告時点で「全46項目」と記載したが、Theme A-Hの項目を数え直した結果、正しくは47項目（Appendix I除く）であった。本改訂で訂正する。

---

## 1. 重複統合の方針（Phase 7-D）

同一論点が複数Documentに登場する場合は1項目に統合するが、**意味が異なる論点は無理に1つにまとめない**。例: 「PO番号の完全採番規則」と「Revision時にPO番号をどう扱うか」と「Cancellation後の番号再利用可否」は関連するが別問題であり、A-1/A-3/G-6として分離した。

また、**Sourceで既に確定している事実は質問に戻さない**。例:「G-SYSはPO番号を自動採番していますか」は既にSource監査で「しない」と確定済みのため質問化せず、Current G-SYS Factとして記載した上で、質問は「誰が・どのルールで」に絞った。

---

## 1b. Phase 7-D2: 4分類の追加（重要な前提変更）

**Phase1は現在Gulliver社のG-SYS保守・メンテナンスを担当しており、ErnestはG-SYSの保守担当としてSourceコードだけでは分からない現在の実運用・環境・保守手順を把握している可能性がある。** したがって「Sourceコードから分からない」を無条件に「Gulliver社へ質問する」に変換しない。全項目を以下4分類へ再整理した。

| 分類 | 意味 | 質問先 |
|---|---|---|
| **A. SOURCE CONFIRMED** | Sourceコードで既に確定済み。誰にも質問不要。 | なし |
| **B. PHASE1 / ERNEST CONFIRM** | Sourceだけでは確定できないが、G-SYS保守担当であるPhase1/Ernestへの確認で解決できる可能性が高い（現在の技術的実態・運用手順） | Ernest（`docs/ernest-current-operation-question-sheet.md`） |
| **C. GULLIVER CURRENT OPERATION CONFIRM** | 現在のGulliver社固有の業務運用であり、Ernestでも確定できなければGulliver社へ確認する | Gulliver（Current Fact確認） |
| **D. GULLIVER FUTURE DECISION** | Portal導入後の新しい業務ルール・権限・運用方針であり、Gulliver社の意思決定が必要 | Gulliver（Future Decision） |

**Current FactとFuture Decisionを混ぜない**（4章の指示）: 同じDecision Group内でも、「現在どうなっているか」（B/C）と「将来どうすべきか」（D）は別項目として分離した。例えば A-1（PO番号採番規則）は A-1a「現在誰が・どのルールで採番しているか」（B, Ernestへ）と A-1b「将来Portalが採番してよいか」（D, Gulliverへ）に分割した。

Splitを行った結果、47項目（Theme A-H）は**64項目**（一部が現在事実/将来決定で分割されたため）に細分化された。内訳は3章のQuick Referenceおよび15章の集計を参照。

## 1c. Phase 7-E: User Management関連8項目の追加

Phase 7-EのNavigation / Save-to-Next UX Audit（Section 4: Master Maintenance Information Architecture監査）の一環で、`PortalUser`（Portal-owned Masterの一つ）についてSource/Docs調査を行った結果、User作成・編集・無効化・削除・Password運用・Role変更に関するBusiness Ruleが一切確定していないことが判明した。これらはUser Management UI・CRUD API未実装（本Phaseでも実装しない）の状態で新たに発見された未決事項であり、Theme B（Approval / Permission）へ**B-7〜B-14として追加**する。全8項目とも、現行G-SYS（Legacy）に対応する機能・権限情報が存在しない（`legacy-procurement-workflow-reverse-engineering.md`15章 — 11 User TypeはPO操作権限を持たない）ため、Ernest／Gulliver Current Operationのどちらにも「現在の事実」として聞く対象がなく、全て**Gulliver Future Decision（D）**に分類する（Theme Bの既存6項目と同じ扱い）。

既存のB-2（海外Supplierの自己承認範囲＝`User.selfApprovalScope`）は、User Masterのフィールド案の一つとして本追加項目群と関連するが、B-2自体は独立した既存項目として据え置き、重複登録はしない。

Splitの結果、64項目は**72項目**に増える。内訳は3章のQuick Referenceおよび15章の集計を参照。

---

## 2. 分類基準（Phase 7-D、Timing/Blocker/Theme）

### 2.1 Timing（3分類）

| 記号 | 意味 | 判定基準 |
|---|---|---|
| **A** | 9/17より前に確認必須 | 回答がないと9/17デモ内容そのものが誤解を招く、または次の実装（7-C2B/7-C4等）が着手できない |
| **B** | 9/17デモ当日に確認 | Prototypeは現状でも見せられるが、Target業務を確定するため当日確認すべき |
| **C** | デモ後・本番設計時でもよい | 本番化には必要だが、Prototype/9/17説明には影響しない |

*(注: このTiming分類の記号A/B/Cは、1b章の4分類A/B/C/Dとは別の記号体系。混同しないこと。)*

### 2.2 Blocker区分

| 記号 | 意味 |
|---|---|
| **BLOCKER** | 回答なしで次の実装（主に7-C2B: 実Handoff、7-C4: Real Mail Send）を開始すべきでないもの |
| **IMPORTANT** | 本番設計上重要だが、回答なしでも次の実装に一旦着手できる |
| **LATER** | 優先度は低いが記録しておくべき論点 |

### 2.3 Theme（8分類）

A. PO番号・Official PO　B. Approval / Permission　C. Supplier Communication　D. Supplier Response / Revision　E. Fulfillment / Follow-up　F. Excel coexistence / Conflict　G. Cancellation / Correction　H. Infrastructure / Operation

---

## 3. Quick Reference（全72項目、Phase 7-D2分類つき。B-7〜B-14はPhase 7-E追加分）

凡例: **7-D2分類** = A(Source Confirmed) / B(Ernest Confirm) / C(Gulliver Current Operation) / D(Gulliver Future Decision)

| ID | Theme | 質問（要約） | Timing | Blocker | 7-D2分類 |
|---|---|---|---|---|---|
| A-1a | PO番号 | 現在、誰が・どのルールで採番しているか、ID Codeの意味 | A | BLOCKER | **B**（Ernest Q1） |
| A-1b | PO番号 | 将来Portalが採番してよいか | A | BLOCKER | **D** |
| A-2 | PO番号 | 正式PO Excelは誰が作成・Import Folderへ配置しているか | A | BLOCKER | **B**（Ernest Q2） |
| A-3a | PO番号 | 現在、修正時は同一PO番号への再Importが実際の運用か | B | IMPORTANT | **B**（Ernest Q3） |
| A-3b | PO番号 | 将来もこの方式（同一番号への再投入）を踏襲してよいか | B | IMPORTANT | **D** |
| A-4a | PO番号 | 現在の手作業Excel運用の実態（誰が・頻度） | A | BLOCKER | **B**（Ernest Q4） |
| A-4b | PO番号 | 将来も手作業Excel併用を残すか | A | BLOCKER | **D** |
| A-5a | PO番号 | Initial POは現在も現役で使われているか | C | LATER | **B**（Ernest Q5） |
| A-5b | PO番号 | Portalとして書き込む必要があるか | C | LATER | **D** |
| A-6 | PO番号 | Import Error時の現在の運用 | C | LATER | **B**（Ernest Q6） |
| B-1 | Approval | 管理者承認Workflowの採否・単位・否認時扱い・代行 | B | IMPORTANT | **D** |
| B-2 | Approval | 海外Supplierの自己承認範囲 | C | LATER | **D** |
| B-3 | Approval | ADMINがその場で修正して承認可能でよいか | B | IMPORTANT | **D** |
| B-4 | Approval | 修正版（Revision）再送時に再承認が必要か | B | IMPORTANT | **D** |
| B-5 | Approval | 複数段階承認者階層の要否 | C | LATER | **D** |
| B-6 | Approval | 承認後の再編集の許容範囲 | B | IMPORTANT | **D** |
| B-7 | Approval (User Mgmt) | User作成・編集・無効化を誰が実行できるか | C | LATER | **D** |
| B-8 | Approval (User Mgmt) | User削除を許可するか、enabledによる論理無効化のみとするか | C | LATER | **D** |
| B-9 | Approval (User Mgmt) | 自分自身の無効化・Role変更を許可するか | C | LATER | **D** |
| B-10 | Approval (User Mgmt) | 最後のADMINを無効化／降格できないようにするか | C | LATER | **D** |
| B-11 | Approval (User Mgmt) | Password初期発行・変更・Resetの運用 | C | LATER | **D** |
| B-12 | Approval (User Mgmt) | User Masterのregion項目の用途・値域 | C | LATER | **D** |
| B-13 | Approval (User Mgmt) | ログインIDをemail認証へ将来切り替えるか | C | LATER | **D** |
| B-14 | Approval (User Mgmt) | Legacy MS_USERとの将来SSO統合要否 | C | LATER | **D** |
| C-1a | Supplier Mail | 現在、メーカーへのメール送信はどう行われているか（SYS_SEND_MAIL経由か等） | A | BLOCKER | **B**（Ernest Q8） |
| C-1b | Supplier Mail | PortalからSYS_SEND_MAILキューへの書込みを許可してよいか | A | BLOCKER | **D** |
| C-2 | Supplier Mail | Fromルール | A | BLOCKER | **D** |
| C-3 | Supplier Mail | CC対象の正式ルール | B | IMPORTANT | **D** |
| C-4a | Supplier Mail | 現在、複数担当者へ同時送信する運用があるか | B | IMPORTANT | **C** |
| C-4b | Supplier Mail | 将来のSupplier複数宛先運用ルール | B | IMPORTANT | **D** |
| C-5 | Supplier Mail | 日本/海外Supplierの判定に使えるLegacy Master情報 | B | IMPORTANT | **B**（Ernest Q9） |
| C-6a | Supplier Mail | 既存メールTemplateの有無 | A | BLOCKER | **B**（Ernest Q10） |
| C-6b | Supplier Mail | 送信前編集可否・管理方法 | A | BLOCKER | **D** |
| C-7 | Supplier Mail | Supplier担当者のBrand単位/全社単位の割当方針 | B | IMPORTANT | **C** |
| C-8a | Supplier Mail | 現在、メーカー担当者連絡先はどこで管理されているか | B | IMPORTANT | **B**（Ernest Q11） |
| C-8b | Supplier Mail | 将来G-SYS本体Master化するか | B | IMPORTANT | **D** |
| C-9 | Supplier Mail | 各Mail Template種別の内容確定 | C | LATER | **C** |
| D-1 | Response/Revision | メーカー回答差異を誰が・どう受け入れるか（AGREED判定条件） | B | IMPORTANT | **D** |
| D-2 | Response/Revision | Revisionをいつ作るか、Sendの瞬間確定でよいか | B | IMPORTANT | **D** |
| D-3 | Response/Revision | AGREED後のReopen権限の範囲・回数上限 | C | LATER | **D** |
| D-4 | Response/Revision | Confirmed Qty=0の理由分類の正式リスト | A | IMPORTANT | **C** |
| D-5 | Response/Revision | Supply Status正式定義 | A | IMPORTANT | **C** |
| D-6 | Response/Revision | Correction/Revisionの境界線 | B | IMPORTANT | **D** |
| D-7 | Response/Revision | Supplier Response完了条件 | B | IMPORTANT | **D** |
| D-8 | Response/Revision | COMPLETED Statusの新設要否 | C | LATER | **D** |
| D-9 | Response/Revision | 数量・納期変更時に追加承認が必要か | B | IMPORTANT | **D** |
| E-1 | Fulfillment | 未納・Partialの正式業務定義 | B | IMPORTANT | **C** |
| E-2 | Fulfillment | 問い合わせ開始Timing | C | LATER | **D** |
| E-3 | Fulfillment | 問い合わせReason正式分類 | C | LATER | **D** |
| E-4 | Fulfillment | 問い合わせClose権限者 | C | LATER | **D** |
| E-5 | Fulfillment | 再発注判断者・Outstanding Qty default化 | C | LATER | **D** |
| E-6 | Fulfillment | 再発注時PO番号関係 | C | LATER | **D** |
| E-7 | Fulfillment | Supplier返信記録方法 | C | LATER | **D** |
| F-1 | Excel Conflict | Portal/Excel競合時どちらを正とするか | A | BLOCKER | **D** |
| F-2 | Excel Conflict | Conflict解消権限者 | B | IMPORTANT | **D** |
| F-3 | Excel Conflict | Handoff直前の再比較を必須Gateにするか | B | IMPORTANT | **D** |
| F-4 | Excel Conflict | Conflict対象Field範囲 | C | LATER | **D** |
| F-5a | Excel Conflict | 現在、Excelファイルにバージョン管理の慣習があるか | C | LATER | **C** |
| F-5b | Excel Conflict | 将来のExcel Version管理方法 | C | LATER | **D** |
| G-1 | Cancellation | 現在のOfficial PO取消の実際の処理方法 | B | IMPORTANT | **B**（Ernest Q12） |
| G-2a | Cancellation | 現在、一部取消のケースがあるか | C | LATER | **C** |
| G-2b | Cancellation | 将来の一部取消要否 | C | LATER | **D** |
| G-3 | Cancellation | APPROVED後のPortal側キャンセル可否 | C | LATER | **D** |
| H-1 | Infrastructure | G-SYS Hosting・Import Folder Hosting・Network情報 | C | LATER | **B**（Ernest Q13） |
| H-2 | Infrastructure | 既存Import Batchの起動Trigger・実行頻度 | A | BLOCKER | **B**（Ernest Q7） |
| AppI-1〜7 | (Appendix I) | Phase 0由来のPrototype UI/計算仕様細部（7項目、13章参照） | C | LATER | **D**（全項目） |

---

## 4. 9/17 Demoで「見せる/見せない」

（Phase 7-Dから変更なし）

### SHOW（実際に画面を操作して見せる — 7-C1〜7-C6ですべて実装・E2E確認済み）

- **Candidate → Draft → Preview → 承認Workflow（OPERATOR申請 → ADMIN承認/修正承認/差し戻し）**
- **Demo Send（メーカーへの送信を模したデモモード、実メール送信なし）**
- **Supplier Response入力・確定（数量/納期回答、Confirmed Qty=0、Supply Status選択）**
- **差異検出（Attention）→ 差異のまま合意 or 修正版（Revision）作成 → Agreement（AGREED）→ Reopen**
- **Order Revision History / Response History（Revision別の履歴表示）**
- **Supplier Contact / Mail Template管理画面（ADMIN）とMail Preview（To/CC/件名/本文、実送信なし）**
- **G-SYS入荷状況（Fulfillment: Ordered/Invoiced/Stock-in/Outstanding、テストPO紐付け）**
- **問い合わせ（Follow-up Case）起票 → Follow-upメールPreview → Close → 再発注Draft作成**
- **G-SYS正式PO連携（Preflight）Section（Supplier/Brand/Item Masterの整合確認、正式PO番号は未採番のまま表示）**
- **G-SYSとの整合確認（Legacy PO Concurrency: Baseline記録 → 差異検出 → Structured Diff表示）**
- **Dashboard KPI（発注候補/発注作成中/メーカー回答待ち/要確認/問い合わせ中）とBrand別内訳からのDeep Link**
- **操作履歴（Audit Timeline）— 全操作が実行者・実行時刻付きで記録されていること**

### EXPLAIN ONLY

- **実際のG-SYS Handoff**（7-C2A Preflightまで実装済み、7-C2B未実装）
- **実メール送信**（Mail Preview完成、7-C4未実装）
- **Excel Download/Upload本体**（Conflict検出Foundationのみ完成）
- **在庫・販売データのLegacy側連携Timing**（PortalはREAD ONLY参照のみ）

### DO NOT SHOW

- **Test専用のPO番号・Fixture**（`PO-CONC-01`等）
- **Legacy Demo MySQLのDocker管理画面・直接SQL操作**
- **正式PO番号が常にNULLである内部実装の詳細**
- **Safety Guard / READ ONLY強制の実装詳細**

---

## 5. Theme A: PO番号・Official PO（最優先Decision Group）

### A-1. 正式PO番号の採番規則・採番主体

- **Current G-SYS Fact**: G-SYS自身はOfficial PO番号を自動採番しない。番号はExcel側に既に入力された状態でImportされ、G-SYSはその文字列からSupplier Code（4桁）・Brand Code（3桁）・ID Code（2桁）部分を解析・Master照合するのみ（`SSSS-BBB-II...`形式）。11桁目以降の規則・ID Codeの値域はSourceからは確定不能。
- **Target Portal Proposal**: 現時点でPortalは正式PO番号を一切採番しない（`officialPoNo`は常にNULL）。
- **A-1a（現在の事実）**: 「現在、正式PO番号は誰が、どのルールで採番していますか？11桁目以降の規則、ID Codeの値域を教えてください。」　**7-D2分類: B（Ernest Q1）**　**Timing**: A　**Blocker**: BLOCKER
- **A-1b（将来の決定）**: 「将来的にPortalがこの番号を採番してよいでしょうか？」　**7-D2分類: D（Gulliver Future Decision）**　**Timing**: A　**Blocker**: BLOCKER
  - **Answer Options**: A. 従来どおり人手が採番　B. Portalが形式準拠の候補番号を生成し人が確認・上書き　C. Portalが完全自動採番
  - **Our Recommendation**: B（移行期間として人の確認を残す）
- **Source**: target-production 21章#1／7-A 19章#2／official-po-integration-detailed-design 21章#1

### A-2. 正式PO番号の決定者・Excel作成/配置の実運用フロー

- **Current G-SYS Fact**: Import Pipelineの構造（Upload/Work/Backupフォルダ）はSourceから確認できたが、「誰が」「どのタイミングで」配置しているかはSourceからは追えない。
- **Target Portal Proposal**: 将来的にPortalが承認済みOrderからExcelを自動生成し、Import Folderへ配置する（7-C2B）ことを目標とする。
- **Question**: 「現在、正式Official PO Excelは誰が作成し、どのフォルダへ、どのタイミングで配置していますか。」　**7-D2分類: B（Ernest Q2）**　**Timing**: A　**Blocker**: BLOCKER
- **Our Recommendation**: 承認直後に自動生成・投入する設計を提案するが、既存の人手フローとの並走期間を設けるか要確認。
- **Source**: official-po-integration-detailed-design 21章#2/#3

### A-3. 修正版・再送時のPO番号の扱い

- **Current G-SYS Fact**: 同一PO Noへの再Import（Delete & Recreate）は、Invoice（入荷実績）が絡んでいなければ無条件で成功する（7-A 11章確認済み、技術的メカニズムはSource Confirmed）。版番号という概念自体はG-SYS側に存在しない。
- **Target Portal Proposal**: PortalはOrder Revision（1, 2, 3...）という版番号をPortal内部で管理しており（7-C5実装済み）、修正版のG-SYS投入自体は現行のDelete & Recreateをそのまま利用できる設計とした。
- **A-3a（現在の事実）**: 「発注内容を修正した場合、現在は修正版のExcelを再取込して元の発注内容を差し替える実際の運用ですか？」　**7-D2分類: B（Ernest Q3）**　**Timing**: B　**Blocker**: IMPORTANT
- **A-3b（将来の決定）**: 「今後Portalで複数回の修正が発生した場合も、同じPO番号への再投入で問題ないでしょうか。」　**7-D2分類: D**　**Timing**: B　**Blocker**: IMPORTANT
- **Our Recommendation**: 現行のDelete & Recreateをそのまま踏襲。
- **Source**: target-production 21章#5／7-A 19章#4／official-po-integration-detailed-design 21章#6

### A-4. Portal導入後の手作業Official PO Excel継続可否

- **Current G-SYS Fact**: 現行Import Batchは「誰が投入したExcelか」を区別する仕組みを持たない。
- **Target Portal Proposal**: Baseline Snapshot + Fingerprint比較によるConcurrency検出（7-C6実装済み）を用意したが、これは「検出」のみ。
- **A-4a（現在の事実）**: 「現在、Official PO Excelは誰が・どのくらいの頻度で・どのツールで作成していますか。」　**7-D2分類: B（Ernest Q4）**　**Timing**: A　**Blocker**: BLOCKER
- **A-4b（将来の決定）**: 「Portal導入後も、担当者が手作業でExcelを作成してG-SYSへ直接取り込む運用を残しますか？」　**7-D2分類: D**　**Timing**: A　**Blocker**: BLOCKER
  - **Answer Options**: A. Portalのみ　B. Portal＋手作業Excel併用（移行期間）　C. 従来Excelのみ
  - **Our Recommendation**: B
- **Source**: target-production 21章#12／7-A 19章#7／official-po-integration-detailed-design 21章#4／excel-legacy-concurrency-control 22章

### A-5. Initial PO（打診段階のPO）をG-SYSに残す必要があるか

- **Current G-SYS Fact**: 現行はInitial（打診）とOfficial（確定）を別のExcel Importとして扱う2段階構造。
- **A-5a（現在の事実）**: 「Initial POは現在も現役で使われていますか。」　**7-D2分類: B（Ernest Q5）**　**Timing**: C　**Blocker**: LATER
- **A-5b（将来の決定）**: 「Portalとして書き込む必要がありますか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Our Recommendation**: 投入不要と推定。
- **Source**: official-po-integration-detailed-design 21章#5

### A-6. Import Error時の一次対応

- **Current G-SYS Fact**: 該当する業務プロセス自体がSource上存在しない。
- **Question**: 「G-SYSへの取込がエラーになった場合、現在は誰が気づいて対応していますか。」　**7-D2分類: B（Ernest Q6）**　**Timing**: C　**Blocker**: LATER
- **Source**: official-po-integration-detailed-design 21章#7

---

## 6. Theme B: Approval / Permission（全項目 7-D2分類: D）

Theme Bの6項目は、現行G-SYSに承認機能自体が一切存在しない（人による承認機能はSource上存在しない）ため、Ernest／Gulliver Current Operationのどちらにも「現在の事実」として聞く対象がなく、全てPortal導入後の新しい業務ルールを問う**Gulliver Future Decision（D）**である。

### B-1. 管理者承認Workflowの採否・承認単位・否認時扱い・代行

- **Current G-SYS Fact**: 人による承認機能はSource上存在しない。
- **Target Portal Proposal**: OPERATORがDraftを作成し、ADMINが承認する2段階Workflow（7-C1実装済み）。
- **Question**: 「発注内容の承認は、ご提案どおり『担当者が作成→管理者が承認』という1発注ごとの2段階で問題ないでしょうか。」　**Timing**: B　**Blocker**: IMPORTANT
- **Our Recommendation**: 現行実装（PO単位・2段階・差し戻し理由必須）を正式仕様として採用。
- **Source**: target-production 21章#2／7-A 19章#1

### B-2. 海外Supplierの自己承認範囲

- **Target Portal Proposal**: `User.selfApprovalScope`設計案があるが未実装。
- **Question**: 「海外メーカー向けの発注について、担当者自身の判断で確定できるケースはありますか。」　**Timing**: C　**Blocker**: LATER
- **Source**: target-production 21章#3／role-approval-implementation 20章

### B-3. ADMINがその場で修正して承認可能でよいか

- **Target Portal Proposal**: 「Edit-and-Approve」実装済み。
- **Question**: 「管理者が承認する際、その場で修正して承認してよいでしょうか。」　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 21章#2（関連）

### B-4. 修正版（Revision）再送時に再承認が必要か

- **Target Portal Proposal**: 既存の単一段階承認Workflowをそのまま再利用。
- **Question**: 「その修正版も初回と同じように管理者の承認が必要でしょうか。」　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: supplier-response-revision-workflow 26章

### B-5. 複数段階承認者階層の要否

- **Question**: 「発注金額や数量によって、複数人の承認が必要になるケースはありますか。」　**Timing**: C　**Blocker**: LATER
- **Source**: role-approval-implementation 20章

### B-6. 承認後の再編集の許容範囲

- **Current G-SYS Fact**: 現行は誰でも訂正可能（履歴のみ残る、承認概念自体がないため） — この部分はSource Confirmed。
- **Target Portal Proposal**: APPROVED以降の編集は差し戻し・修正版作成経由のみ。
- **Question**: 「一度承認された発注内容を後から直接編集できる必要がありますか。」　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: role-approval-implementation 20章／7-A 20章#4

---

### B-7〜B-14. User Management関連（Phase 7-E追加、1c章参照）

Phase 7-EのPortalUser仕様調査（Source: `PortalUser.java`／`SecurityConfig.java`／`V4,V5,V8`migration、Docs: `target-production-procurement-workflow.md`17章）で判明した、User作成・編集・無効化・削除・Password・Role・regionに関する8つの未決事項。**いずれもBusiness Ruleの決定は行わず、記録のみ**（実装・仕様確定は本Phaseでは行わない）。

### B-7. User作成・編集・無効化を誰が実行できるか

- **Current G-SYS Fact**: PortalUserのCRUD API・画面は現状一切存在しない（Flyway migrationでの直接投入のみ）。
- **Question**: 「Userの作成・編集・無効化は、Portal内のADMIN Roleが行う想定でよいでしょうか。それとも別の管理者・別システムでの運用を想定されていますか。」　**Timing**: C　**Blocker**: LATER
- **Source**: Phase 7-E PortalUser仕様調査

### B-8. User削除を許可するか、enabledによる論理無効化のみとするか

- **Current G-SYS Fact**: `portal_user.enabled`列は存在するが、これをfalseにする、または行を物理削除するコードはSource全体に存在しない。
- **Question**: 「Userを完全に削除できる必要がありますか。それとも履歴保持のため無効化のみで十分でしょうか。」
- **Why**: 他のPortal Master（Supplier Contact等）では「Inactiveを推奨」方針が既に採用されており（7-C3 12章）、一貫性の観点で参考になる。　**Timing**: C　**Blocker**: LATER
- **Source**: Phase 7-E PortalUser仕様調査

### B-9. 自分自身の無効化・Role変更を許可するか

- **Question**: 「ログイン中の管理者が、自分自身のRoleを変更したりアカウントを無効化したりできてよいでしょうか。」　**Timing**: C　**Blocker**: LATER
- **Source**: Phase 7-E PortalUser仕様調査

### B-10. 最後のADMINを無効化／降格できないようにするか

- **Question**: 「システム上、ADMIN Roleのアカウントが0件になる操作（最後の管理者を無効化・降格する等）を防ぐガードが必要でしょうか。」　**Timing**: C　**Blocker**: LATER
- **Source**: Phase 7-E PortalUser仕様調査

### B-11. Password初期発行・変更・Resetの運用

- **Current G-SYS Fact**: 現状、全Passwordの発行はFlyway migrationでのBCryptハッシュ固定値投入のみ。変更・Reset機能（API・画面）は存在しない。
- **Question**: 「新規Userのパスワードは誰がどう初期発行しますか。本人によるパスワード変更、管理者による強制Resetは必要でしょうか。」　**Timing**: C　**Blocker**: LATER
- **Source**: Phase 7-E PortalUser仕様調査

### B-12. User Masterのregion項目の用途・値域

- **Target Portal Proposal**: `target-production-procurement-workflow.md`17章にUser Masterフィールド案として`region`が記載されているが、値域・用途は未定義（Portal専用の将来フィールドで、Legacy側に対応する情報はない）。
- **Question**: 「User Masterのregion項目は、どのような値（例: 国内/海外、担当地域名など）を想定されていますか。不要であれば項目自体の削除も検討可能です。」　**Timing**: C　**Blocker**: LATER
- **Source**: target-production 17章／Phase 7-E PortalUser仕様調査

### B-13. ログインIDをemail認証へ将来切り替えるか

- **Current G-SYS Fact**: 現在の認証はusername + password（Phase 7-C1でemail化を見送り、Role Foundationのみ先行実装済み） — この部分はSource Confirmed。
- **Target Portal Proposal**: `target-production-procurement-workflow.md`17章に「認証: email + password」という将来案の記載あり。`portal_user.email`列は追加済みだが未使用。
- **Question**: 「ログインIDを将来usernameからemailへ切り替える必要がありますか。切り替える場合の時期は？」　**Timing**: C　**Blocker**: LATER
- **Source**: target-production 17章／Phase 7-C1報告／Phase 7-E PortalUser仕様調査

### B-14. Legacy MS_USERとの将来SSO統合要否

- **Current G-SYS Fact**: PortalUserはLegacy `MS_USER`と完全に独立（FK・ID共有一切なし） — この部分はSource Confirmed。統合しない、という判断自体は`target-production-procurement-workflow.md`17章で既に文書化済みだが、「将来のSSO統合」は同章で別Phase課題として保留されているのみで、正式に不要と決定されたわけではない。
- **Question**: 「G-SYS（Legacy）のログインとPortalのログインを、将来的にSSO等で統合する必要がありますか。」　**Timing**: C　**Blocker**: LATER
- **Source**: target-production 17章／Phase 7-E PortalUser仕様調査

---

## 7. Theme C: Supplier Communication（メール）

### C-1. メーカーへの送信基盤

- **Current G-SYS Fact**: `SYS_SEND_MAIL`キューと既存Batchが存在（Source Confirmed）。
- **C-1a（現在の事実）**: 「現在、メーカーへの発注連絡メールは、実際にこの仕組みを使って送っていますか？」　**7-D2分類: B（Ernest Q8）**　**Timing**: A　**Blocker**: BLOCKER（7-C4）
- **C-1b（将来の決定）**: 「Portalからこのキューへの書込みを許可してよいでしょうか？」　**7-D2分類: D**　**Timing**: A　**Blocker**: BLOCKER（7-C4）
  - **Our Recommendation**: A（既存の添付・CC/BCC機構を再利用）
- **Source**: target-production 10章／7-A 20章#6

### C-2. Fromルール

- **Question**: 「Fromアドレスは担当者本人か、共通アドレス＋Reply-To方式か。」　**7-D2分類: D**　**Timing**: A　**Blocker**: BLOCKER（7-C4）
- **Our Recommendation**: Reply-To方式。
- **Source**: supplier-contact-mail-template-foundation 20章

### C-3. CC対象の正式ルール

- **Question**: 「複数ADMIN時のCC対象は全員か、特定担当者か。」　**7-D2分類: D**　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 21章#13／supplier-contact-mail-template-foundation 20章

### C-4. Supplier複数宛先の正式運用

- **C-4a（現在の事実）**: 「現在、メーカー担当者が複数いる場合、複数の担当者へ同時送信する運用がありますか？」　**7-D2分類: C（Gulliver Current Operation ― Ernestの技術的知見の範囲外の可能性が高い人手の送信慣行のため、Ernestで確定できなければGulliverへ）**　**Timing**: B　**Blocker**: IMPORTANT
- **C-4b（将来の決定）**: 「Portal導入後、複数担当者への同時送信を正式運用にしますか？」　**7-D2分類: D**　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 21章#13／supplier-contact-mail-template-foundation 20章

### C-5. 日本/海外Supplierの正式判定方法

- **Question**: 「日本/海外の判定に使えるLegacy Master情報（国コード等）はあるか。」　**7-D2分類: B（Ernest Q9）**　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: supplier-contact-mail-template-foundation 20章

### C-6. Mail Template管理方法

- **C-6a（現在の事実）**: 「メーカーへのメール送信で、現在使っている決まった文面（Template）はありますか？」　**7-D2分類: B（Ernest Q10）**　**Timing**: A　**Blocker**: BLOCKER（7-C4）
- **C-6b（将来の決定）**: 「Portalでの送信前編集可否・管理方法をどうするか。」　**7-D2分類: D**　**Timing**: A　**Blocker**: BLOCKER（7-C4）
  - **Our Recommendation**: A（Template固定・編集不可、統制重視）
- **Source**: target-production 21章#11／supplier-contact-mail-template-foundation 20章

### C-7. Supplier担当者のBrand単位/全社単位の割当方針

- **Question**: 「メーカー担当者は特定のブランドごとに分かれていますか、それとも1社1窓口ですか。」　**7-D2分類: C（Gulliver Current Operation — Gulliver社内の組織・担当割当情報であり、G-SYS技術保守の範囲外）**　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: supplier-contact-mail-template-foundation 20章

### C-8. Supplierメール宛先の正式Master構造

- **Current G-SYS Fact**: 現行G-SYSにSupplierのメール担当者情報を持つMasterは存在しない（Source Confirmed）。
- **C-8a（現在の事実）**: 「メーカー担当者の連絡先情報は、現在どこかに一覧化されていますか（Excel台帳・他システム等）？」　**7-D2分類: B（Ernest Q11）**　**Timing**: B　**Blocker**: IMPORTANT
- **C-8b（将来の決定）**: 「将来的にG-SYS本体のMaster機能として管理すべきか、Portal専用のままでよいか。」　**7-D2分類: D**　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: legacy-procurement-workflow-reverse-engineering 19章#6

### C-9. 各Mail Template種別の内容確定

- **Question**: 「修正版・問い合わせ・取消の連絡について、既存の文面があれば共有してほしい。」　**7-D2分類: C（Gulliver Current Operation — 具体的な文面の中身はGulliverの業務コミュニケーション内容そのものであり、Ernestの技術知見では代替できない。ただしQ10でErnestが「文面が存在する」ことまでは確認できる可能性がある）**　**Timing**: C　**Blocker**: LATER
- **Source**: supplier-contact-mail-template-foundation 20章

---

## 8. Theme D: Supplier Response / Revision（全項目 7-D2分類: D、一部C）

Theme Dは大半がPortal独自の新しいWorkflow概念（AGREED/Revision/Reopen等）についての将来方針であり、現行G-SYSには対応する業務プロセスが存在しない（Source上「該当なし」）。したがって主にGulliver Future Decision（D）。ただしD-4/D-5（欠品等の業務用語の定義）はGulliver社内の業務用語そのものであり、技術保守のErnestではなくGulliver Current Operationとして扱う。

### D-1〜D-3, D-6〜D-9（7-D2分類: D）

- D-1 メーカー回答差異を誰が・どう受け入れるか（AGREED判定条件） — target-production 21章#6
- D-2 Revisionをいつ作るか — supplier-response-revision-workflow 2章
- D-3 AGREED後のReopen権限の範囲・回数上限 — supplier-response-revision-workflow 26章
- D-6 Correction/Revisionの境界線 — target-production 21章#10
- D-7 Supplier Response完了条件 — G-SYS_Online-Ordering_Prototype_Requirements 27.4
- D-8 COMPLETED Statusの新設要否 — production-ux-workflow-redesign 15.4章
- D-9 数量・納期変更時に追加承認が必要か — production-ux-workflow-redesign 15.4章

（各項目の詳細なQuestion/Fact/Impactは前版から変更なし。3章Quick Reference参照）

### D-4. Confirmed Qty=0の意味・理由分類の正式リスト

- **7-D2分類: C（Gulliver Current Operation）** — 「欠品」「廃番」等の理由分類はGulliver購買部門の業務用語であり、G-SYS技術保守（Ernest）の範囲外。ただしLegacy `ITEM_STATUS`列の値の技術的意味自体はErnestに確認できる可能性があり、その範囲は`docs/ernest-current-operation-question-sheet.md`には含めていない（優先度が低いため今回は見送り、必要ならP3として追加検討）。
- **Timing**: A　**Blocker**: IMPORTANT
- **Source**: target-production 21章#9

### D-5. Supply Status（欠品/長期欠品/廃番等）の正式定義

- **7-D2分類: C（Gulliver Current Operation）** — D-4と同様の理由。
- **Timing**: A　**Blocker**: IMPORTANT
- **Source**: target-production 21章#4／7-A 19章#3

---

## 9. Theme E: Fulfillment / Follow-up（全項目 7-D2分類: D、一部C）

Follow-up/Reorderの運用ルール（Timing/Reason分類/Close権限/再発注判断）はいずれもPortal導入後の新しい業務プロセスであり、Gulliver Future Decision（D）。

### E-1. 未納・Partialの正式業務定義

- **7-D2分類: C（Gulliver Current Operation）** — G-SYSは実入荷状況を技術的に正確に追跡可能（TR_INV/Credit PO、Source Confirmed）だが、「何をもって業務上『一部納品』と呼ぶか」はGulliver購買部門の用語であり、Ernestの技術保守範囲では確定できない。
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 21章#7-8

### E-2〜E-7（7-D2分類: D）

- E-2 問い合わせ開始Timing — target-production 21章#7
- E-3 問い合わせReason正式分類 — fulfillment-follow-up-foundation 27章
- E-4 問い合わせClose権限者 — fulfillment-follow-up-foundation 27章
- E-5 再発注判断者・Outstanding Qty default化 — target-production 21章#8
- E-6 再発注時PO番号関係 — target-production 21章#8
- E-7 Supplier返信記録方法 — fulfillment-follow-up-foundation 27章

---

## 10. Theme F: Excel Coexistence / Conflict

### F-1〜F-4（7-D2分類: D）

- F-1 Portal/Excel競合時どちらを正とするか — target-production 21章#12
- F-2 Conflict解消権限者 — excel-legacy-concurrency-control 22章
- F-3 Handoff直前の再比較を必須Gateにするか — excel-legacy-concurrency-control 22章
- F-4 Conflict対象Field範囲 — excel-legacy-concurrency-control 22章

### F-5. Excelファイル自体のVersion管理方法

- **F-5a（現在の事実）**: 「現在、Excelファイルにバージョン管理の慣習（版番号・タイムスタンプ埋込等）がありますか。」　**7-D2分類: C（Gulliver Current Operation — ファイル運用の慣習はGulliver業務側の話であり、Ernestは把握していない可能性が高い）**　**Timing**: C　**Blocker**: LATER
- **F-5b（将来の決定）**: 「今後、版番号・タイムスタンプを入れる運用は可能か。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: excel-legacy-concurrency-control 22章

---

## 11. Theme G: Cancellation / Correction

### G-1. Official PO取消の正式な業務手順

- **Current G-SYS Fact**: SourceだけではTarget運用が未確定（G-SYS上でPOをどう取消扱いにしているか確認できていない）。
- **Question**: 「発注を取り消す場合、現在G-SYS上ではどのような操作をしていますか（技術的な操作手順）。」　**7-D2分類: B（Ernest Q12）**　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 13章／fulfillment-follow-up-foundation 27章

### G-2. 一部取消（Partial Cancel）の要否

- **G-2a（現在の事実）**: 「現在、発注の一部SKU・一部数量だけを取り消すケースがありますか。」　**7-D2分類: C（Gulliver Current Operation — 業務発生頻度の話であり、Ernestの技術保守範囲外）**　**Timing**: C　**Blocker**: LATER
- **G-2b（将来の決定）**: 「Portalとして一部取消機能が必要か。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: fulfillment-follow-up-foundation 27章（関連）

### G-3. APPROVED後のPortal側キャンセル可否

- **Question**: 「承認済みの発注をPortal上でキャンセルできる必要がありますか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: role-approval-implementation 20章

---

## 12. Theme H: Infrastructure / Operation

Theme Hの2項目はいずれも技術的な環境情報であり、**Gulliver社の業務判断ではなくG-SYS保守担当のErnestが直接答えられる可能性が最も高い**分類（B）である。

### H-1. 現在のG-SYS Hosting・Import FolderのHosting/Access方式

- **Question**: 「G-SYSサーバー、Import Folderはどこにhostingされているか。Portalからの接続方式は。」　**7-D2分類: B（Ernest Q13）**　**Timing**: C　**Blocker**: LATER
- **Source**: 指示14章

### H-2. 既存Import Batchの起動Trigger・実行頻度

- **Question**: 「既存バッチはどのくらいの頻度で動いているか。」　**7-D2分類: B（Ernest Q7）**　**Timing**: A　**Blocker**: BLOCKER
- **Source**: official-po-integration-detailed-design 21章#9-10

---

## 13. Appendix I: Phase 0/0.5由来の未決事項

Theme A-Hに自然に収まらない、Prototype UI/計算仕様の細部（7項目）。いずれもPrototypeは暫定値で正常に動作しており、9/17デモの成立を妨げない。**全項目 7-D2分類: D（Gulliver Future Decision、LATER）** — Portal UI・計算方針そのものの採否であり、Ernestの技術保守範囲でもGulliverの現行業務運用でもなく、純粋にPortalの仕様として将来決めればよい事項のため。

- Requested Deliveryの必須/任意
- 現行Formula（`calc4`）を新画面でもそのまま利用する方針の妥当性
- `calc4Alt`を通常画面に表示するか
- Recommended Qty乖離Warningの正式な閾値
- 発注時点からSKU別にRequested Deliveryを指定するケースの有無
- 日本語表示文言の最終確定
- 混在Supplier発注時の分割仕様

---

## 14. 統合前後の対応表（トレーサビリティ）

（Phase 7-Dから変更なし。統合元Doc・章・元番号との対応は3章のIDで引ける）

| 統合先ID | 統合元（Doc・章・元番号） |
|---|---|
| A-1 | target-production#1／7-A#2／official-po-integration-detailed-design#1,#2 |
| A-2 | official-po-integration-detailed-design#2,#3 |
| A-3 | target-production#5／7-A#4／official-po-integration-detailed-design#6 |
| A-4 | target-production#12／7-A#7／official-po-integration-detailed-design#4／excel-legacy-concurrency-control |
| A-5 | official-po-integration-detailed-design#5 |
| A-6 | official-po-integration-detailed-design#7 |
| B-1〜B-6 | target-production#2,#3／7-A#1／role-approval-implementation／supplier-response-revision-workflow |
| B-7〜B-14 | Phase 7-E PortalUser仕様調査（本Doc初出、他Docからの統合ではない）／target-production17章（B-12,B-13,B-14） |
| C-1〜C-9 | supplier-contact-mail-template-foundation（8項目）／target-production#11,#13／7-A#6 |
| D-1〜D-9 | target-production#4,#6,#9,#10／supplier-response-revision-workflow／requirements27.4／production-ux-workflow-redesign15.4 |
| E-1〜E-7 | target-production#7,#8／7-A#5／fulfillment-follow-up-foundation |
| F-1〜F-5 | target-production#12／excel-legacy-concurrency-control／official-po-integration-detailed-design17章 |
| G-1〜G-3 | target-production13章／fulfillment-follow-up-foundation／role-approval-implementation |
| H-1,H-2 | 指示14章／official-po-integration-detailed-design#9,#10 |
| Appendix I | requirements27.4 |

---

## 15. まとめ（Phase 7-D2反映後）

### 15.1 Phase 7-D時点（Timing/Blocker軸、47項目・分割前）

- 全47項目（Theme A-H）＋Appendix I 7項目 ＝ 計54項目。Timing=A（9/17前必須）は11項目、Blocker=BLOCKERは9項目。

### 15.2 Phase 7-D2時点＋Phase 7-E追加後（4分類軸、72項目）

| 分類 | 件数 | 質問先 |
|---|---|---|
| **A. SOURCE CONFIRMED** | **0** | なし（詳細は15.3参照） |
| **B. PHASE1 / ERNEST CONFIRM** | **13** | `docs/ernest-current-operation-question-sheet.md` |
| **C. GULLIVER CURRENT OPERATION CONFIRM** | **8** | Gulliver（現行業務の事実確認） |
| **D. GULLIVER FUTURE DECISION** | **51**（Phase 7-D2時点43 ＋ Phase 7-E追加分B-7〜B-14の8） | Gulliver（将来方針の意思決定） |
| **合計** | **72** | |

### 15.3 なぜ「A. SOURCE CONFIRMED」が0件なのか

Phase 7-Dの時点で「Sourceで既に確定していることは質問に戻さない」という原則を適用済みだったため（1章）、この47項目＋Appendix I自体が、そもそもSourceで解決できなかった残りである。したがって7-D2で改めて「Sourceだけで完全に解決する」項目を探しても新たに見つからなかった（0件）。ただし各項目の"Current G-SYS Fact"欄には、Source Confirmedな前提事実（例: A-3の「同一PO No.への再Import機構自体の存在」、B-6の「現行は誰でも訂正可能」、C-1の「SYS_SEND_MAILキューの存在」）が引き続き記載されており、これらは質問化されていない。**「0件」はSource監査の手抜きではなく、Phase 7-Dの設計原則が正しく機能していたことの裏付け**と解釈する。

### 15.4 Ernest確認で解決が期待される13項目（B分類）の内訳

| Theme | 件数 | Ernest Sheet番号 |
|---|---|---|
| PO番号関連（採番規則そのもの） | 1 | Q1 |
| Official PO Import運用関連 | 6 | Q2, Q3, Q4, Q5, Q6, Q7 |
| Mail関連 | 4 | Q8, Q9, Q10, Q11 |
| Cancellation関連 | 1 | Q12 |
| Infrastructure関連 | 1 | Q13 |

優先度: P1（7-C2B/7-C4のBlocker）6件、P2（9/17前に把握したい）5件、P3（本番設計まででよい）2件。

### 15.5 Ernest回答で「消える」可能性があるGulliver質問

`docs/customer-review-question-sheet.md`（Ernest確認前Draft）には、上記13項目（B分類）が現時点でGulliver向け質問としてそのまま含まれている。Ernestの回答が得られ次第、この**13項目はGulliver向け質問票から削除、または「確認済み事実の共有」に置き換える**（Ernestが確定的な回答をできなかった場合のみ、C分類＝Gulliver Current Operationとして質問票に残る）。したがって、Ernest回答によって最終的にGulliverへ聞く質問がゼロになる可能性がある項目は**最大13件**。

### 15.6 Ernest確認後も確実にGulliver判断が必要な項目

- **C. GULLIVER CURRENT OPERATION CONFIRM（8件）**: Ernestが技術保守担当として確定できない、Gulliver社の業務運用・組織構造に関する事実（例: メーカー担当者のBrand別割当、複数宛先送信の実際の運用、欠品/廃番の業務定義）。Ernestに聞いても解決しない可能性が高いため、最初からGulliver向けとして扱う。
- **D. GULLIVER FUTURE DECISION（51件、うちAppendix I 7件、Phase 7-E追加分B-7〜B-14の8件含む）**: Portal導入後の新しい業務ルール・権限・運用方針そのものであり、これは「事実確認」ではなく「意思決定」であるため、Ernestが何を答えても消えない。Gulliverの最終承認が必須。
- **合計 59件が、Ernest確認後も確実にGulliverへの確認が必要な項目数。**

### 15.7 「件数を減らすこと」自体を目的にしない

上記のとおり、B分類13件をEarnestへ振り分けても、Gulliverへの質問自体は最大59件（C:8＋D:51）残る。これは「件数を無理に減らした」結果ではなく、**「Phase1として調べれば分かることをGulliverに聞かない」**という本Phaseの目的を優先した結果である。件数の多寡よりも、各質問が正しい相手（Ernest / Gulliver Current Operation / Gulliver Future Decision）に向いていることを優先した。
