# Customer Review Decision Package — Phase 7-D

**Status**: Docs Only。本Phaseでコード変更・DB Migration・Legacy変更は一切行っていない（Frontend/Backend/DB Migration/Legacy変更禁止の指示どおり）。

**目的**: Phase 7-A〜7-C6（本番目標設計・7-C1 Role/Approval・7-C2A Official PO Integration Foundation・7-C3 Supplier Contact/Mail Template・7-C5 Supplier Response Revision・7-C7A Fulfillment/Follow-up・7-C6 Excel/Legacy Concurrency Control）の各Documentに散在する`CUSTOMER REVIEW`項目をすべて回収し、「何を顧客に確認しないと本番実装できないか」を一本化したDecision Packageとして整理する。

**対象読者**: 9/17顧客レビューに向けて準備するTechlead（ChatGPT）・SEPG（Claude Code）・実際に顧客へ質問する担当者。

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
- 追加参照: `docs/G-SYS_Online-Ordering_Prototype_Requirements.md`（27.4章 `[TBD - CUSTOMER REVIEW]` 19項目 — 全項目の起点）、`docs/G-SYS_Online-Ordering_Prototype_Technical_Design.md`（実装時の暫定判断とCUSTOMER REVIEW未決事項）、`docs/role-approval-implementation.md`（20章 CUSTOMER REVIEW 7項目）、`docs/production-ux-workflow-redesign.md`（15.4章 残存CUSTOMER REVIEW 12項目）、`docs/9-17-demo-script.md`（7-C1〜7-C6以前の旧台本 — 本Documentが質問内容として上書き・supersedeする）。

延べ約100件のCUSTOMER REVIEW記述を、下記2章の方針で**46項目**に統合した（重複統合の詳細対応表は10章参照）。

---

## 1. 重複統合の方針

同一論点が複数Documentに登場する場合は1項目に統合するが、**意味が異なる論点は無理に1つにまとめない**（指示どおり）。例: 「PO番号の完全採番規則」と「Revision時にPO番号をどう扱うか」と「Cancellation後の番号再利用可否」は関連するが別問題であり、A-1/A-3/G-6として分離した。

また、**Sourceで既に確定している事実は質問に戻さない**。例:「G-SYSはPO番号を自動採番していますか」は既にSource監査で「しない」と確定済みのため質問化せず、Current G-SYS Factとして記載した上で、質問は「誰が・どのルールで」に絞った。

---

## 2. 分類基準

### 2.1 Timing（3分類）

| 記号 | 意味 | 判定基準 |
|---|---|---|
| **A** | 9/17より前に確認必須 | 回答がないと9/17デモ内容そのものが誤解を招く、または次の実装（7-C2B/7-C4等）が着手できない |
| **B** | 9/17デモ当日に確認 | Prototypeは現状でも見せられるが、Target業務を確定するため当日確認すべき |
| **C** | デモ後・本番設計時でもよい | 本番化には必要だが、Prototype/9/17説明には影響しない |

### 2.2 Blocker区分

| 記号 | 意味 |
|---|---|
| **BLOCKER** | 回答なしで次の実装（主に7-C2B: 実Handoff、7-C4: Real Mail Send）を開始すべきでないもの |
| **IMPORTANT** | 本番設計上重要だが、回答なしでも次の実装に一旦着手できる（Foundation実装は進むが、業務ルールの最終確定が必要） |
| **LATER** | 優先度は低いが記録しておくべき論点 |

### 2.3 Theme（8分類）

A. PO番号・Official PO　B. Approval / Permission　C. Supplier Communication　D. Supplier Response / Revision　E. Fulfillment / Follow-up　F. Excel coexistence / Conflict　G. Cancellation / Correction　H. Infrastructure / Operation

---

## 3. Quick Reference（全46項目）

| ID | Theme | 質問（要約） | Timing | Blocker |
|---|---|---|---|---|
| A-1 | PO番号 | 正式PO番号は誰が・どのルールで採番しているか | A | BLOCKER |
| A-2 | PO番号 | 正式PO Excelは誰が作成・Import Folderへ配置しているか | A | BLOCKER |
| A-3 | PO番号 | 修正版・再送時のPO番号の扱い | B | IMPORTANT |
| A-4 | PO番号 | Portal導入後の手作業Excel Upload継続可否 | A | BLOCKER |
| A-5 | PO番号 | Initial PO（打診段階）をG-SYSに残す必要があるか | C | LATER |
| A-6 | PO番号 | Integration失敗時の一次対応責任者 | C | LATER |
| B-1 | Approval | 管理者承認Workflowの採否・単位・否認時扱い・代行 | B | IMPORTANT |
| B-2 | Approval | 海外Supplierの自己承認範囲（Supplier/Region/Type粒度） | C | LATER |
| B-3 | Approval | ADMINがその場で修正して承認可能でよいか | B | IMPORTANT |
| B-4 | Approval | 修正版（Revision）再送時に再承認が必要か | B | IMPORTANT |
| B-5 | Approval | 複数段階承認者階層の要否 | C | LATER |
| B-6 | Approval | 承認後の再編集の許容範囲 | B | IMPORTANT |
| C-1 | Supplier Mail | メーカーへの送信基盤（既存SYS_SEND_MAIL vs Portal独自SMTP） | A | BLOCKER |
| C-2 | Supplier Mail | Fromルール（ログインユーザー実アドレス or Reply-To方式） | A | BLOCKER |
| C-3 | Supplier Mail | CC対象の正式ルール（複数ADMIN時） | B | IMPORTANT |
| C-4 | Supplier Mail | Supplier複数宛先の正式運用 | B | IMPORTANT |
| C-5 | Supplier Mail | 日本/海外Supplierの正式判定方法 | B | IMPORTANT |
| C-6 | Supplier Mail | Mail Template管理方法（編集可否・管理者・送信前確認要否） | A | BLOCKER |
| C-7 | Supplier Mail | Supplier担当者のBrand単位/全社単位の割当方針 | B | IMPORTANT |
| C-8 | Supplier Mail | Supplierメール宛先の正式Master構造（現状はTR_PO履歴からの暫定導出） | B | IMPORTANT |
| C-9 | Supplier Mail | 各Mail Template種別（発注/修正版/問い合わせ/取消）の内容確定 | C | LATER |
| D-1 | Response/Revision | メーカー回答差異を誰が・どう受け入れるか（AGREED判定条件） | B | IMPORTANT |
| D-2 | Response/Revision | Revision（修正版）をいつ作るか、Sendの瞬間確定でよいか | B | IMPORTANT |
| D-3 | Response/Revision | AGREED後のReopen（取消）権限の範囲・回数上限 | C | LATER |
| D-4 | Response/Revision | Confirmed Qty=0の意味・理由分類の正式リスト | A | IMPORTANT |
| D-5 | Response/Revision | Supply Status（欠品/長期欠品/廃番等）の正式定義 | A | IMPORTANT |
| D-6 | Response/Revision | Correction（軽微訂正）とRevision（修正版）の境界線 | B | IMPORTANT |
| D-7 | Response/Revision | Supplier Response完了条件（回答納期の入力必須化） | B | IMPORTANT |
| D-8 | Response/Revision | COMPLETED（完了）Statusの新設要否・正式遷移条件 | C | LATER |
| D-9 | Response/Revision | 数量・納期変更時に追加承認が必要か | B | IMPORTANT |
| E-1 | Fulfillment | 未納・Partialの正式業務定義 | B | IMPORTANT |
| E-2 | Fulfillment | 問い合わせ開始のTiming（納期超過何日で警告か） | C | LATER |
| E-3 | Fulfillment | 問い合わせReasonの正式分類 | C | LATER |
| E-4 | Fulfillment | 問い合わせCloseの権限者 | C | LATER |
| E-5 | Fulfillment | 再発注の判断者・Outstanding Qtyをdefaultにしてよいか | C | LATER |
| E-6 | Fulfillment | 再発注時のPO番号関係・元PO残数量の扱い | C | LATER |
| E-7 | Fulfillment | Supplier返信内容の記録方法 | C | LATER |
| F-1 | Excel Conflict | Portal/Excel競合時、どちらを正とするか | A | BLOCKER |
| F-2 | Excel Conflict | Conflict解消権限者 | B | IMPORTANT |
| F-3 | Excel Conflict | Handoff直前の再比較を必須Gateにするか、警告に留めるか | B | IMPORTANT |
| F-4 | Excel Conflict | Conflict対象Fieldの範囲（Price/Delivery含むか） | C | LATER |
| F-5 | Excel Conflict | Excelファイル自体のVersion管理方法 | C | LATER |
| G-1 | Cancellation | Official PO取消の正式な業務手順（G-SYS上でどう処理しているか） | B | IMPORTANT |
| G-2 | Cancellation | 一部取消（Partial Cancel）の要否 | C | LATER |
| G-3 | Cancellation | APPROVED後のPortal側キャンセル可否 | C | LATER |
| H-1 | Infrastructure | 現在のG-SYS Hosting・Import FolderのHosting/Access方式 | C | LATER |
| H-2 | Infrastructure | 既存Import Batchの起動Trigger・実行頻度 | A | BLOCKER |

---

## 4. 9/17 Demoで「見せる/見せない」

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

### EXPLAIN ONLY（機能としては未実装、または動くが実データに繋がっていないため、口頭説明に留める）

- **実際のG-SYS Handoff（Official PO Excel生成 → Import Folder投入 → G-SYS反映確認）**: 7-C2A（Preflight Foundation）までは実装済みだが、実際のExcel生成・投入・確認（7-C2B）は未実装。「次にこの部分を実装する」という位置づけで説明する。
- **実メール送信**: Mail Preview機能は完成しているが、実際にメーカーへ送信する機能（7-C4）は未実装。「Previewまでは完成しており、送信は正式なMail運用ルールが確定次第実装する」と説明する。
- **Excel Download/Upload本体**: Conflict検出（Baseline/Compare/Diff）のFoundationは完成しているが、実際にExcelファイルをDownload/Uploadする機能自体はまだ存在しない。
- **在庫・販売データのLegacy側連携Timing（Logizero反映等）**: Portalは既存Legacy DBをREAD ONLYで参照するのみで、Legacy側のBatch連携Timingそのものは変更していないことを説明する。

### DO NOT SHOW（顧客に見せると誤解を招くため意図的に見せない）

- **Test専用のPO番号・Fixture**（`PO-CONC-01`等、E2E/統合テスト専用の値）— 実際のG-SYS番号体系と誤認されないよう、デモでは触れない。
- **Legacy Demo MySQLのDocker管理画面・直接SQL操作**（Test Fixture変更に使っている`docker exec`操作） — 技術的なDemo基盤の内部事情であり、業務Demoでは不要。
- **正式PO番号が常にNULLである内部実装の詳細**（Fieldの存在自体は「G-SYS正式PO連携」Sectionで自然に見えるが、「なぜNULLのままなのか」を技術的に深掘りしない — A-1/A-2の回答待ちであることの説明に留める）。
- **Safety Guard / READ ONLY強制の実装詳細**（Legacy書込み拒否の技術的仕組み）— 「Legacyには一切書き込んでいない」という事実の説明で十分。

---

## 5. Theme A: PO番号・Official PO（最優先Decision Group）

### A-1. 正式PO番号の採番規則・採番主体

- **Current G-SYS Fact**: G-SYS自身はOfficial PO番号を自動採番しない。番号はExcel側（`PrOfficialPoImportBatch`が読み込むExcelファイル自体）に既に入力された状態でImportされ、G-SYSはその文字列からSupplier Code（4桁）・Brand Code（3桁）・ID Code（2桁）部分を解析・Master照合するのみ（`SSSS-BBB-II...`形式、Phase 0.5/7-A監査で確認済み）。11桁目以降（連番・日付部と推定される部分）の規則、およびID Codeの正確な値域はSourceからは確定不能。
- **Target Portal Proposal**: 現時点でPortalは正式PO番号を一切採番しない（`officialPoNo`は常にNULL）。将来的にPortalが番号を生成する場合は「形式準拠の候補番号を生成し、ADMINが確認・上書き可能」という設計を候補としている。
- **Customer Question**: 「現在、正式PO番号は誰が、どのルールで採番していますか？11桁目以降（連番部と思われる部分）の規則と、ID Codeが何を表す値なのか（Masterの所在）を教えてください。また、将来的にPortalがこの番号を採番してよいでしょうか、それとも引き続き従来の方法（Excel作成者による手入力等）を維持すべきでしょうか。」
- **Why we need the answer**: 7-C2B（実Handoff、Excel生成・投入）の実装は、生成するExcelのPO番号欄に何を書き込むかが決まらないと着手できない。
- **Impact if undecided**: 7-C2Bの実装に着手できない。デモでは「正式PO番号は未確定」という前提を明示する必要がある。
- **Timing**: A　**Blocker**: BLOCKER
- **Answer Options**: A. 従来どおり人手（Excel作成者）が採番し続ける　B. Portalが形式準拠の番号を生成し人が確認・上書き　C. Portalが完全自動採番　
- **Our Recommendation**: B（移行期間として人の確認を残しつつ、入力ミスを減らす）。将来的にA-4（手作業Excel廃止）が決まればCへ移行。
- **Source**: target-production 21章#1／7-A 19章#2／official-po-integration-detailed-design 21章#1

### A-2. 正式PO番号の決定者・Excel作成/配置の実運用フロー

- **Current G-SYS Fact**: Official PO Excel Import Pipelineの構造（Upload/Work/Backupフォルダ）自体はSourceから確認できたが、「誰が」「どのタイミングで」Excelファイルをそのフォルダへ配置しているか（担当者・部署・手順）はSourceからは追えない。
- **Target Portal Proposal**: 将来的にPortalが承認済みOrderからExcelを自動生成し、Import Folderへ配置する（7-C2B）ことを目標とする。
- **Customer Question**: 「現在、正式Official PO Excelは誰が作成し、どのフォルダへ、どのタイミングで配置していますか（発注確定直後か、まとめて日次で行うか等）。」
- **Why we need the answer**: A-1と合わせて、Portalが代替すべき業務範囲・タイミングを特定するために必須。
- **Impact if undecided**: 7-C2Bの投入Timing設計（承認直後に即投入か、バッチ化するか）が決まらない。
- **Timing**: A　**Blocker**: BLOCKER
- **Our Recommendation**: 承認（APPROVED）直後に自動生成・投入する設計を提案するが、既存の人手フローとの並走期間を設けるか要確認。
- **Source**: official-po-integration-detailed-design 21章#2/#3

### A-3. 修正版・再送時のPO番号の扱い

- **Current G-SYS Fact**: 同一PO Noへの再Import（Delete & Recreate）は、Invoice（入荷実績）が絡んでいなければ無条件で成功する（7-A 11章確認済み）。すなわち現行は「同一番号のまま中身を差し替える」運用であり、版番号（Revision番号）という概念自体はG-SYS側に存在しない。
- **Target Portal Proposal**: PortalはOrder Revision（1, 2, 3...）という版番号をPortal内部で管理しており（7-C5実装済み）、修正版のG-SYS投入自体は現行のDelete & Recreateをそのまま利用できる設計とした。
- **Customer Question**: 「発注内容を修正した場合、現在は修正版のExcelを再取込して元の発注内容を差し替える運用でしょうか。それとも、別の訂正伝票（Credit PO等）として管理していますか。今後Portalで複数回の修正が発生した場合も、同じPO番号への再投入で問題ないでしょうか。」
- **Why we need the answer**: 7-C2B以降、Revision 2以降のExcel再投入方式を確定する必要がある。
- **Impact if undecided**: 7-C2B自体は着手可能（7-C2Aで基礎は完成）だが、Revision2以降のHandoff実装が止まる。
- **Timing**: B　**Blocker**: IMPORTANT
- **Our Recommendation**: 現行のDelete & Recreateをそのまま踏襲（同一PO番号への再投入）。Portal側のRevision履歴が、現行G-SYSにない「変更履歴が消える」欠点を補完する。
- **Source**: target-production 21章#5／7-A 19章#4／official-po-integration-detailed-design 21章#6

### A-4. Portal導入後の手作業Official PO Excel継続可否

- **Current G-SYS Fact**: 現行Import Batchは「誰が投入したExcelか」を区別する仕組みを持たない（正しい形式であれば無条件で処理する）。
- **Target Portal Proposal**: Portal側でBaseline Snapshot + Fingerprint比較によるConcurrency検出（7-C6実装済み）を用意したが、これは「検出」のみであり、手作業Excelの投入自体を防ぐものではない。
- **Customer Question**: 「Portal導入後も、担当者が手作業でExcelを作成してG-SYSへ直接取り込む運用を残しますか。それとも将来的にはPortal経由の投入のみに一本化しますか。」
- **Why we need the answer**: 7-C2B・7-C6双方の設計（Conflict発生時にPortalが何をすべきか）の前提となる。
- **Impact if undecided**: 7-C2Bの投入方式（PortalのみでよいかExcel併用を前提に設計すべきか）が決まらない。
- **Timing**: A　**Blocker**: BLOCKER
- **Answer Options**: A. Portalのみ（手作業Excel廃止）　B. Portal + 手作業Excel併用（移行期間）　C. 従来Excelのみ（Portalは参照のみ）
- **Our Recommendation**: B（移行期間を設け、Portal Conflict検出で安全性を担保しながら段階的にAへ移行）。
- **Source**: target-production 21章#12／7-A 19章#7／official-po-integration-detailed-design 21章#4／excel-legacy-concurrency-control 22章

### A-5. Initial PO（打診段階のPO）をG-SYSに残す必要があるか

- **Current G-SYS Fact**: 現行はInitial（打診・単価/納期未定）とOfficial（確定）を別のExcel Importとして扱う2段階構造。
- **Target Portal Proposal**: PortalのDraft→承認→送信という多段Statusは、この現行の粒度をより細かくしたものとして自然に接続できると評価しているが、Initial PO自体をG-SYSへ投入する必要があるかは未評価。
- **Customer Question**: 「発注内容が固まる前の『打診段階』のPOも、現在G-SYSへ登録していますか。それとも口頭・メール等G-SYS外で完結していますか。」
- **Why we need the answer**: PortalのDraft/承認前段階をG-SYSへ投入すべきかどうかの要否判断に必要。
- **Impact if undecided**: 影響は限定的（Portal Draft自体はPrototype PostgreSQLで完結しており、G-SYS投入なしでも動作する）。
- **Timing**: C　**Blocker**: LATER
- **Our Recommendation**: 投入不要と推定（Portal Draftで代替可能）。
- **Source**: official-po-integration-detailed-design 21章#5

### A-6. Integration失敗時の一次対応責任者

- **Current G-SYS Fact**: 該当する業務プロセス自体がSource上存在しない（現行はException時、担当者が気づいて再投入する運用と推測されるが未確認）。
- **Target Portal Proposal**: PortalはIntegration失敗をAttention化する設計を想定しているが、誰が一次対応するかは未定義。
- **Customer Question**: 「G-SYSへの取込がエラーになった場合、現在は誰が気づいて対応していますか。」
- **Why we need the answer**: 7-C2B以降のAttention/Notification設計の宛先を決めるため。
- **Impact if undecided**: 7-C2Bの実装自体は進められる（Attention化のUIは既存パターンを流用できる）。
- **Timing**: C　**Blocker**: LATER
- **Source**: official-po-integration-detailed-design 21章#7

---

## 6. Theme B: Approval / Permission

### B-1. 管理者承認Workflowの採否・承認単位・否認時扱い・代行

- **Current G-SYS Fact**: 人による承認機能はSource上存在しない（現行はOfficial PO Excel Importが即座に正式化のトリガーであり、承認Stepはない）。
- **Target Portal Proposal**: OPERATORがDraftを作成し、ADMINが承認する2段階Workflow（DRAFT→PENDING_APPROVAL→APPROVED）として実装済み（7-C1）。承認単位は1発注（PO）単位。否認時は「差し戻し」（理由必須）としてOPERATORへ戻す。承認者不在時の代行機構は未実装。
- **Customer Question**: 「発注内容の承認は、ご提案どおり『担当者が作成→管理者が承認』という1発注ごとの2段階で問題ないでしょうか。却下（差し戻し）の場合の扱い、承認者が不在の場合の代行はどうしていますか。」
- **Why we need the answer**: 既にPrototypeとして実装・デモ可能だが、業務ルールとして正式に合っているかの確認が必要。
- **Impact if undecided**: デモ自体は問題なく実施できる（実装済みのため）。ただし本番設計の前提が固まらない。
- **Timing**: B　**Blocker**: IMPORTANT
- **Our Recommendation**: 現行実装（PO単位・2段階・差し戻し理由必須）を正式仕様として採用することを推奨。
- **Source**: target-production 21章#2／7-A 19章#1

### B-2. 海外Supplierの自己承認範囲

- **Current G-SYS Fact**: 該当する権限区分の前例はSource上ない。
- **Target Portal Proposal**: `User.selfApprovalScope`（Supplier/Region単位のリスト）としてOPERATORが自己承認できる範囲を持たせる設計案があるが、未実装（フィールド自体もまだ存在しない）。
- **Customer Question**: 「海外メーカー向けの発注について、担当者自身の判断で（管理者承認なしに）確定できるケースはありますか。あるとすれば、どのメーカー・地域が対象ですか。」
- **Why we need the answer**: 実装するかどうか、実装する場合の粒度（Supplier単位/Region単位/Procurement Type単位）を決めるため。
- **Impact if undecided**: 影響なし（未実装のため、7-C1のシンプルな2段階承認がそのままデモ・本番初期でも機能する）。
- **Timing**: C　**Blocker**: LATER
- **Source**: target-production 21章#3／role-approval-implementation 20章

### B-3. ADMINがその場で修正して承認可能でよいか

- **Current G-SYS Fact**: 該当なし（承認機能自体が現行にない）。
- **Target Portal Proposal**: PENDING_APPROVAL中にADMINがDraft内容を直接編集した上で承認する「Edit-and-Approve」を実装済み（`APPROVED_WITH_CHANGES`Audit Event）。
- **Customer Question**: 「管理者が承認する際、内容に軽微な誤りがあれば、差し戻さずその場で修正して承認してよいでしょうか。」
- **Why we need the answer**: 実装済みの挙動が正しい業務前提かを確認するため。
- **Impact if undecided**: デモには影響しない（動作する）。
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 21章#2（関連）

### B-4. 修正版（Revision）再送時に再承認が必要か

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 現行実装は「修正版を作成」でDRAFTへ戻し、既存の単一段階承認Workflowをそのまま再利用する（Revision専用の特別な承認ロジックは追加していない）。
- **Customer Question**: 「メーカーとの回答内容に差異があり発注内容を修正した場合、その修正版も初回と同じように管理者の承認が必要でしょうか。」
- **Why we need the answer**: 現行実装（単純に同じ承認Workflowを再利用）で十分かの確認。
- **Impact if undecided**: デモには影響しない。
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: supplier-response-revision-workflow 26章

### B-5. 複数段階承認者階層の要否

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 未実装・未設計（現行は単一段階のみ）。
- **Customer Question**: 「発注金額や数量によって、複数人の承認（例: 課長→部長）が必要になるケースはありますか。」
- **Why we need the answer**: 将来的な機能追加の要否を判断するため。
- **Impact if undecided**: 影響なし。
- **Timing**: C　**Blocker**: LATER
- **Source**: role-approval-implementation 20章

### B-6. 承認後の再編集の許容範囲

- **Current G-SYS Fact**: 現行は誰でも訂正可能（履歴のみ残る、承認概念自体がないため）。
- **Target Portal Proposal**: APPROVED以降の編集は「差し戻し（Return for Correction）」または「修正版を作成（Revision）」経由のみに限定し、直接編集は許可していない。
- **Customer Question**: 「一度承認された発注内容を後から直接編集できる必要がありますか。それとも、必ず差し戻し・修正版作成のような明示的な手続きを経るべきでしょうか。」
- **Why we need the answer**: 現行実装の制約（直接編集不可）が業務上妥当かの確認。APPROVED後のキャンセル可否（Theme G-3）とも関連。
- **Impact if undecided**: デモには影響しない。
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: role-approval-implementation 20章／7-A 20章#4

---

## 7. Theme C: Supplier Communication（メール）

### C-1. メーカーへの送信基盤（既存SYS_SEND_MAIL vs Portal独自SMTP）

- **Current G-SYS Fact**: 既存G-SYSに`SYS_SEND_MAIL`という送信キューテーブルと、それを処理する既存Batchが存在する（添付・CC/BCC対応済み、7-A 14章確認済み）。
- **Target Portal Proposal**: 第一候補として、PortalがこのSYS_SEND_MAILキューへ書き込み、既存Batchに送信させる方式を提案（Portal独自SMTPは代替案）。
- **Customer Question**: 「メーカーへのメール送信は、現在使っている送信の仕組み（SYS_SEND_MAIL経由の既存バッチ）にPortalからも相乗りする形でよいでしょうか。それとも、Portal専用の送信の仕組みを別途用意すべきでしょうか。」
- **Why we need the answer**: 7-C4（Real Mail Send）の実装方式そのものを左右する。
- **Impact if undecided**: 7-C4に着手できない。
- **Timing**: A　**Blocker**: BLOCKER（7-C4のBlocker）
- **Answer Options**: A. 既存SYS_SEND_MAILキューへINSERT　B. Portal独自SMTP
- **Our Recommendation**: A（既存の添付・CC/BCC機構を再利用でき、既存の監査・運用ルールとも整合しやすい）。
- **Source**: target-production 10章／7-A 20章#6／9章

### C-2. Fromルール

- **Current G-SYS Fact**: 現行の送信元運用（誰のアドレスから送っているか）はSourceからは確認できない。
- **Target Portal Proposal**: 「From=ログインユーザー本人のアドレス」という案と、「Reply-To方式（Fromは固定アドレス、返信のみ担当者へ）」の2案を検討中、未確定。
- **Customer Question**: 「メーカーへ送るメールのFromアドレスは、担当者本人のメールアドレスにすべきでしょうか。それとも共通の送信アドレスにして、返信だけ担当者へ届く形にすべきでしょうか。」
- **Why we need the answer**: 7-C4のMail生成ロジックの前提となる。
- **Impact if undecided**: 7-C4に着手できない（Fromの決定はメール生成の根幹）。
- **Timing**: A　**Blocker**: BLOCKER（7-C4のBlocker）
- **Our Recommendation**: Reply-To方式（共通Fromで統一し、返信を担当者へ）。退職・異動時の運用が安定する。
- **Source**: supplier-contact-mail-template-foundation 20章

### C-3. CC対象の正式ルール（複数ADMIN時）

- **Current G-SYS Fact**: 該当なし（新規Master概念）。
- **Target Portal Proposal**: 「CC: 必ずADMIN」という方針だが、ADMINが複数いる場合に「全Active ADMIN」「Order担当ADMIN」「Primary ADMIN」「Supplier・Region担当ADMIN」のどれにするかは未確定。
- **Customer Question**: 「メーカーへのメールをCCで管理者へ共有する場合、対象は管理者全員でしょうか。それとも特定の担当者（発注のPrimary担当者等）に絞るべきでしょうか。」
- **Impact if undecided**: デモには影響しない（現行は全Active ADMINで動作）。
- **Timing**: B　**Blocker**: IMPORTANT
- **Our Recommendation**: 全Active ADMIN（現行実装のまま）。
- **Source**: target-production 21章#13／supplier-contact-mail-template-foundation 20章

### C-4. Supplier複数宛先の正式運用

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: Supplier Contact MasterはTO/CC複数登録に対応可能な構造で実装済みだが、実際に複数の担当者へ同時送信するのが正しい運用かは未確認。
- **Customer Question**: 「メーカー1社に対して、担当者が複数いる場合、発注メールは全員に同時送信すべきでしょうか。それとも代表者1名のみでしょうか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 21章#13／supplier-contact-mail-template-foundation 20章

### C-5. 日本/海外Supplierの正式判定方法

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: Supplier Contactに`language`（ja/en）フィールドを持たせているが、これをどう自動判定するか（国コード、Supplier Master上のFlag等）は未確定。
- **Customer Question**: 「メーカーが日本国内か海外かは、どのMaster情報（住所・国コード等）から判断すればよいでしょうか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: supplier-contact-mail-template-foundation 20章

### C-6. Mail Template管理方法（編集可否・管理者・送信前確認要否）

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: Mail Template MasterをADMIN権限で作成・編集可能な形でPrototype実装済み。送信前に本文を毎回編集できるようにするか、Template固定にするかは未確定。
- **Customer Question**: 「メーカーへの発注メールは毎回決まった文面（Template）でよいでしょうか。それとも、送信の都度、担当者が本文を手直しできる必要がありますか。」
- **Why we need the answer**: 7-C4のUI設計（送信前に編集画面を出すか、Preview→即送信にするか）を左右する。
- **Impact if undecided**: 7-C4に着手できない。
- **Timing**: A　**Blocker**: BLOCKER（7-C4のBlocker）
- **Answer Options**: A. Template固定・編集不可（統制重視）　B. 送信前に本文編集可能（柔軟性重視、編集後本文を送信履歴に保存）
- **Our Recommendation**: A（Template統制を優先し、内容変更が必要な場合はTemplate自体をADMINが更新する運用）。
- **Source**: target-production 21章#11／supplier-contact-mail-template-foundation 20章

### C-7. Supplier担当者のBrand単位/全社単位の割当方針

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: Supplier ContactはSupplier+Brand単位で登録可能な構造（Brand未指定なら全社共通）で実装済みだが、実際の割当運用は未確認。
- **Customer Question**: 「メーカー担当者は、特定のブランドごとに分かれていますか。それとも1社につき窓口は1つですか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: supplier-contact-mail-template-foundation 20章

### C-8. Supplierメール宛先の正式Master構造

- **Current G-SYS Fact**: 現行G-SYSにはSupplierのメール担当者情報を持つMasterが存在しない。PortalはTR_PO/TR_PO_DTLの直近PO履歴からSupplier情報を導出する暫定回避策を取っている。
- **Target Portal Proposal**: Portal専用のSupplier Contact Masterとして新設済み（7-C3）。将来的にG-SYS本体のMasterへ統合すべきかは未確定。
- **Customer Question**: 「メーカー担当者の連絡先情報は、将来的にG-SYS本体のMaster機能として管理すべきでしょうか。それともPortal専用の管理画面のままでよいでしょうか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: legacy-procurement-workflow-reverse-engineering 19章#6／G-SYS_Online-Ordering_Prototype_Technical_Design（Supplierの正式Master構造）

### C-9. 各Mail Template種別の内容確定

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: `PURCHASE_ORDER`（発注）に加え、`PURCHASE_ORDER_REVISION`（修正版）/`FOLLOW_UP`（問い合わせ）/`CANCELLATION`（取消）のTemplate種別を型として用意済みだが、詳細な文面仕様は未確定。
- **Customer Question**: 「修正版の発注連絡・納品確認の問い合わせ・発注取消の連絡について、それぞれ既存のメール文面（あれば）を共有いただけますか。」
- **Timing**: C　**Blocker**: LATER
- **Source**: supplier-contact-mail-template-foundation 20章

---

## 8. Theme D: Supplier Response / Revision

### D-1. メーカー回答差異を誰が・どう受け入れるか（AGREED判定条件）

- **Current G-SYS Fact**: 発注直後の合意形成プロセス自体がSource上存在しない（現行の対応物はCredit PO＝入荷後の事後訂正のみ）。
- **Target Portal Proposal**: ADMINが明示的に「合意（Agreement）」を行う専用Business Actionとして実装済み（7-C5）。メーカー回答の確定＝自動合意にはしていない。
- **Customer Question**: 「メーカーからの回答内容（数量・納期の変更を含む）を受け入れて発注を確定させる判断は、誰が・どのタイミングで行っていますか。担当者の受入で足りますか、それとも管理者の最終確認が必要ですか。」
- **Impact if undecided**: デモには影響しない（実装済み、ADMIN限定Actionとして動作）。
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 21章#6／7-A 19章#4

### D-2. Revisionをいつ作るか

- **Current G-SYS Fact**: 該当なし（G-SYSに版番号の概念がない）。
- **Target Portal Proposal**: 「Revisionは実際にメーカーへSendされた瞬間にのみ確定するSnapshot」という設計を採用（能動的にRevisionを作成・編集するモデルではない）。
- **Customer Question**: 「発注内容の『版』は、実際にメーカーへ送った時点で固定される、という考え方でよいでしょうか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: supplier-response-revision-workflow 2章（設計判断そのものの確認）

### D-3. AGREED後のReopen（取消）権限の範囲・回数上限

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: AGREED→SUPPLIER_CONFIRMEDへの巻き戻し（Reopen、理由必須）を実装済みだが、権限の範囲・回数上限は未設定（現状無制限）。
- **Customer Question**: 「一度合意（AGREED）とした発注を後から取り消す（差し戻す）ケースはありますか。ある場合、誰が・何回まで行えるべきでしょうか。」
- **Timing**: C　**Blocker**: LATER
- **Source**: supplier-response-revision-workflow 26章

### D-4. Confirmed Qty=0の意味・理由分類の正式リスト

- **Current G-SYS Fact**: 該当なし（現行に欠品概念自体が存在しない）。
- **Target Portal Proposal**: 回答数量0は「未回答（null）」とは明確に区別して保持する設計とし、暫定の理由候補（欠品/一部供給/長期欠品/廃番/納期変更/その他）を用意している。
- **Customer Question**: 「メーカーからの回答数量が0だった場合、その理由（欠品・廃番・単なる保留等）を分類する必要がありますか。必要な場合、正式な分類項目を教えてください。」
- **Why we need the answer**: DashboardのKPI表示（欠品/長期欠品）が、現状Prototype独自の暫定Proxy定義（在庫0のみで判定）で動いており、正式定義と異なると誤解を招く。
- **Impact if undecided**: デモの該当箇所で「これは暫定定義です」という明示的な注記が必要になる。
- **Timing**: A（デモで見せるKPIの前提のため）　**Blocker**: IMPORTANT
- **Source**: target-production 21章#9／supplier-response-revision-workflow 26章／G-SYS_Online-Ordering_Prototype_Requirements 27.4

### D-5. Supply Status（欠品/長期欠品/廃番等）の正式定義

- D-4と対を成す項目（Confirmed Qtyの理由分類 vs 商品自体の供給状況Status）。
- **Current G-SYS Fact**: 現行Sourceに欠品概念は存在しない。
- **Target Portal Proposal**: `supplier_response_detail.supply_status`列（AVAILABLE/OUT_OF_STOCK/LONG_TERM_OUT_OF_STOCK/DISCONTINUED/WAITING_FOR_ARRIVAL/UNKNOWN）として実装済みだが、正式な業務定義・Dashboard KPIとの連携は未着手。
- **Customer Question**: 「『欠品』『長期欠品』『廃番』は、それぞれどのような状態を指しますか（在庫数・期間・メーカー回答内容のどれを基準にしますか）。」
- **Timing**: A　**Blocker**: IMPORTANT
- **Source**: target-production 21章#4／7-A 19章#3／production-ux-workflow-redesign 15.4章#1-2

### D-6. Correction（軽微訂正）とRevision（修正版）の境界線

- **Current G-SYS Fact**: 現行は誰でも訂正可能（履歴のみ残る）。
- **Target Portal Proposal**: 本Prototypeでは大小を問わずすべて「修正版を作成（Revision +1）」に統一しており、軽微な値訂正という別Actionは実装していない。
- **Customer Question**: 「誤字・単価の誤記のような軽微な訂正と、数量・納期の再交渉を伴うような大きな変更を、業務上区別する必要がありますか。区別する場合、その境界はどこですか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 21章#10／supplier-response-revision-workflow 26章

### D-7. Supplier Response完了条件

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 暫定完了条件は「非削除の全明細でConfirmed Qtyが非null」のみとし、Confirmed Delivery（回答納期）の入力は必須としていない。
- **Customer Question**: 「メーカーからの回答が『揃った』とみなす条件は、数量の回答だけで十分でしょうか。納期の回答も必須にすべきでしょうか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: G-SYS_Online-Ordering_Prototype_Requirements 27.4／G-SYS_Online-Ordering_Prototype_Technical_Design 13.5節

### D-8. COMPLETED（完了）Statusの新設要否

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: `COMPLETED`という値はV1時点のCHECK制約に存在するが、実装Codeからは一度も書き込まれていない（死Value）。AGREED実装（7-C5）で一定の「合意成立」概念は導入したが、それを「完了」と呼ぶべきかは未確定。
- **Customer Question**: 「メーカーとの発注内容合意が成立した後、さらに『発注完了』という別の状態が必要ですか。必要な場合、何をもって完了とみなしますか（入荷完了時点等）。」
- **Timing**: C　**Blocker**: LATER
- **Source**: production-ux-workflow-redesign 15.4章#3-4／G-SYS_Online-Ordering_Prototype_Requirements 27.4

### D-9. 数量・納期変更時に追加承認が必要か

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: メーカー回答による数量・納期変更はAttentionとして表示されるのみで、追加の承認Workflowは発生しない（AGREEDというADMIN Actionが実質的な確認Stepを兼ねる設計）。
- **Customer Question**: 「メーカー回答で数量や納期が変わった場合、通常の承認とは別に、追加の承認Stepが必要ですか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: G-SYS_Online-Ordering_Prototype_Requirements 27.4／production-ux-workflow-redesign 15.4章#8

---

## 9. Theme E: Fulfillment / Follow-up

### E-1. 未納・Partialの正式業務定義

- **Current G-SYS Fact**: G-SYSは実際の入荷状況（TR_INV/TR_INV_DTL/Credit PO含む）を正確に追跡可能。PortalはこれをREAD ONLYで参照し、Ordered/Invoiced/Stock-in/Outstandingを算出する（7-C7A実装済み）。
- **Target Portal Proposal**: `OPEN`（未着手）/`PARTIAL`（一部入荷）/`FULFILLED`（完納）の3値、およびOfficial PO未連携を示す`NOT_LINKED`/G-SYS未発見を示す`PO_NOT_FOUND`。
- **Customer Question**: 「発注数量の一部だけがメーカーから届いた状態（一部入荷）と、まだ何も届いていない状態を、業務上どう呼び分けていますか。今回の3区分（未着手/一部納品/完納）で問題ないでしょうか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 21章#7-8（関連）／fulfillment-follow-up-foundation 27章

### E-2. 問い合わせ開始のTiming

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 自動督促ルールは実装していない（人が明示的に「問い合わせ対象にする」操作をした場合のみFollow-up Caseが作られる）。
- **Customer Question**: 「納期を過ぎても入荷しない場合、何日超過したらメーカーへ問い合わせるべきか、目安はありますか。」
- **Timing**: C　**Blocker**: LATER
- **Source**: target-production 21章#7／fulfillment-follow-up-foundation 27章

### E-3. 問い合わせReasonの正式分類

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 暫定候補（DELIVERY_OVERDUE/PARTIAL_DELIVERY/NO_ARRIVAL/QUANTITY_DIFFERENCE/OTHER）を実装済み。
- **Timing**: C　**Blocker**: LATER
- **Source**: fulfillment-follow-up-foundation 27章

### E-4. 問い合わせCloseの権限者

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 本Phaseは暫定的にADMIN限定としている。
- **Customer Question**: 「メーカーへの問い合わせ対応が完了した記録は、誰が締めてよいでしょうか（担当者本人か、管理者限定か）。」
- **Timing**: C　**Blocker**: LATER
- **Source**: fulfillment-follow-up-foundation 27章

### E-5. 再発注の判断者・Outstanding Qtyのdefault化

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 再発注Draft作成機能は実装済みだが、数量は常にLegacy推奨数量が初期値になり、未納数量（Outstanding Qty）を自動的な初期値にはしていない。
- **Customer Question**: 「未納分の再発注は誰が判断しますか。また、再発注時の初期数量は、未納数量をそのまま使ってよいでしょうか、それとも改めて需要を見直すべきでしょうか。」
- **Timing**: C　**Blocker**: LATER
- **Source**: target-production 21章#8／fulfillment-follow-up-foundation 27章

### E-6. 再発注時のPO番号関係・元PO残数量の扱い

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 再発注は新しいPO番号の新規注文とし、元Orderへの参照（`source_order_id`）のみ保持する設計。元PO残数量を継続するか、完全新規として扱うかは未確定。
- **Timing**: C　**Blocker**: LATER
- **Source**: target-production 21章#8／fulfillment-follow-up-foundation 27章

### E-7. Supplier返信内容の記録方法

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 現状は`FollowUpCase.note`への人手更新のみ（メーカーからの返信メールを自動的に取り込む機能はない）。
- **Customer Question**: 「メーカーからの問い合わせ回答（メール等）を、Portal上でどのように記録すべきですか（手入力の要約でよいか、メール本文をそのまま添付すべきか）。」
- **Timing**: C　**Blocker**: LATER
- **Source**: fulfillment-follow-up-foundation 27章

---

## 10. Theme F: Excel Coexistence / Conflict

### F-1. Portal/Excel競合時、どちらを正とするか

- **Current G-SYS Fact**: 現行Import Batchは投入元を区別できないため、後から投入した方が常に勝つ（Delete & Recreateで上書きされる）。
- **Target Portal Proposal**: Baseline Snapshot（Portal確認時点のG-SYS状態）とFingerprint比較による差異「検出」機能は実装済み（7-C6）だが、検出した際にどちらを正とするかの自動解決は一切実装していない。
- **Customer Question**: 「Portalで確認していた発注内容と、実際のG-SYS側の内容に食い違いが見つかった場合、どちらを正しい内容として扱うべきですか（Portal側の内容で上書きする／G-SYS側の内容をPortalに取り込む／必ず人が個別に判断する）。」
- **Why we need the answer**: 7-C2B（実Handoff）が実際にConflictを検出した際の挙動（自動停止するか、警告だけ出すか）を左右する。
- **Impact if undecided**: 7-C2Bの「Handoff直前チェック」部分の実装方針が決まらない。
- **Timing**: A　**Blocker**: BLOCKER
- **Our Recommendation**: 常に人が個別判断（自動解決しない）。Portalは検出とDiff表示までを担い、実際の投入可否はADMINの目視確認を必須Gateにする。
- **Source**: target-production 21章#12／excel-legacy-concurrency-control 22章

### F-2. Conflict解消権限者

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 未実装（検出のみ）。
- **Customer Question**: 「Portal/G-SYS間の食い違いが見つかった場合、誰が最終判断しますか（発注担当者か、管理者限定か）。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: excel-legacy-concurrency-control 22章

### F-3. Handoff直前の再比較を必須Gateにするか

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: `canProceedToHandoff`という判定ロジック（Preflight正常 かつ Concurrency結果=変更なし）をServiceレベルで実装済みだが、これを7-C2Bで強制Gateにするか、警告表示に留めるかは未確定。
- **Customer Question**: 「G-SYSへ最終投入する直前に、内容の食い違いが見つかった場合、投入を自動的に止めるべきですか。それとも警告を出した上で人が投入判断できるようにすべきですか。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: excel-legacy-concurrency-control 22章

### F-4. Conflict対象Fieldの範囲

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 現状はHeader（Status/Supplier/Brand/発注日/通貨/納期/合計金額）とLine（数量/単価）の9項目すべてを比較対象にしている。
- **Customer Question**: 「食い違いを検出する項目に、無視してよい差異（例: 端数の金額差）はありますか。」
- **Timing**: C　**Blocker**: LATER
- **Source**: excel-legacy-concurrency-control 22章

### F-5. Excelファイル自体のVersion管理方法

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 未実装。
- **Customer Question**: 「手作業で使うExcelファイル自体に、版番号やタイムスタンプを入れて管理する運用は可能ですか。」
- **Timing**: C　**Blocker**: LATER
- **Source**: excel-legacy-concurrency-control 22章／official-po-integration-detailed-design 17章

---

## 11. Theme G: Cancellation / Correction

### G-1. Official PO取消の正式な業務手順

- **Current G-SYS Fact**: SourceだけではTarget運用が未確定（G-SYS上でPOをどう取消扱いにしているか、Delete相当の操作なのか、Status変更なのかが確認できていない）。
- **Target Portal Proposal**: 未実装。PortalのCancel ActionとG-SYS Official POのCancelは同義ではないという前提のみ置いている。
- **Customer Question**: 「発注を取り消す場合、現在G-SYS上ではどのような操作をしていますか（PO自体を削除、Statusを変更、または別途取消伝票を起票する等）。」
- **Timing**: B　**Blocker**: IMPORTANT
- **Source**: target-production 13章／fulfillment-follow-up-foundation 27章

### G-2. 一部取消（Partial Cancel）の要否

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 未実装。
- **Customer Question**: 「発注の一部SKU・一部数量だけを取り消すケースはありますか。」
- **Timing**: C　**Blocker**: LATER
- **Source**: fulfillment-follow-up-foundation 27章（関連）

### G-3. APPROVED後のPortal側キャンセル可否

- **Current G-SYS Fact**: 該当なし。
- **Target Portal Proposal**: 未実装（Theme B-6「承認後の再編集許容範囲」とも密接に関連）。
- **Customer Question**: 「承認済みの発注をPortal上でキャンセルできる必要がありますか。」
- **Timing**: C　**Blocker**: LATER
- **Source**: role-approval-implementation 20章

---

## 12. Theme H: Infrastructure / Operation

推測を避け、Source上確認できていない技術運用情報を分離した。**9/17のBusiness Reviewには不要であればC分類のままでよい**（指示どおり）。

### H-1. 現在のG-SYS Hosting・Import FolderのHosting/Access方式

- **Current G-SYS Fact**: Current Legacy Specification上、EC2/S3利用は確認できていない（推測で記載しない）。
- **Customer Question**: 「現在のG-SYSサーバー、および正式PO Excelを配置するImport Folderは、どこにHostingされていますか（オンプレミス／クラウド／その他）。Portalからはどのように接続する想定ですか（VPN、専用線、直接アクセス等）。」
- **Why we need the answer**: 7-C2B（実際のFile投入）の技術的な接続方式設計に必須。
- **Timing**: C（Business Reviewには不要、Infrastructure Reviewとして別途）　**Blocker**: LATER
- **Source**: 指示14章

### H-2. 既存Import Batchの起動Trigger・実行頻度

- **Current G-SYS Fact**: Import Pipelineの構造は確認できたが、起動Trigger（定期実行か、ファイル配置検知か）はSource上確認できていない。
- **Customer Question**: 「正式PO Excelを取り込む既存バッチは、どのくらいの頻度で動いていますか（1日1回、数分おき等）。ファイルを置けばすぐ処理されますか。」
- **Why we need the answer**: 7-C2Bの投入結果確認（Polling）間隔の設計に直結する。
- **Impact if undecided**: 7-C2Bの投入結果確認方式が設計できない。
- **Timing**: A　**Blocker**: BLOCKER
- **Source**: official-po-integration-detailed-design 21章#9-10

### （参考・LATER）Network境界・Credential管理

Import Folderへの認証方式・Network境界・Credential管理方法についても、7-C2B設計時にH-1と合わせて別途Infrastructure Reviewとして確認する。9/17時点では確認不要（C分類）。

---

## 13. Appendix I: Phase 0/0.5由来の未決事項（Prototype暫定値で稼働中・9/17説明に影響小）

Theme A-Hに自然に収まらない、Prototype UI/計算仕様の細部に関する未決事項（`G-SYS_Online-Ordering_Prototype_Requirements.md` 27.4章由来）。いずれもPrototypeは暫定値・デフォルト挙動で正常に動作しており、9/17デモの成立を妨げない（すべてC/LATER）。

- Requested Deliveryの必須/任意（現状: 任意）
- 現行Formula（`calc4`）を新画面でもそのまま利用する方針の妥当性
- `calc4Alt`（代替推奨数計算）を通常画面に表示するか（現状: 非表示）
- Recommended Qty乖離Warningの正式な閾値
- 発注時点からSKU別にRequested Deliveryを指定するケースの有無
- 日本語表示文言の最終確定
- 混在Supplier発注時の分割仕様（現状: 単一Supplierのみ許可、混在時400エラー）

---

## 14. 統合前後の対応表（トレーサビリティ）

| 統合先ID | 統合元（Doc・章・元番号） |
|---|---|
| A-1 | target-production#1／7-A#2／official-po-integration-detailed-design#1,#2／requirements27.4「登録タイミング」 |
| A-2 | official-po-integration-detailed-design#2,#3／7-A「Excel作成者」 |
| A-3 | target-production#5／7-A#4／official-po-integration-detailed-design#6 |
| A-4 | target-production#12／7-A#7／official-po-integration-detailed-design#4／excel-legacy-concurrency-control |
| A-5 | official-po-integration-detailed-design#5 |
| A-6 | official-po-integration-detailed-design#7 |
| B-1 | target-production#2／7-A#1 |
| B-2 | target-production#3／role-approval-implementation |
| B-3 | （実装済み挙動の確認のみ、新規） |
| B-4 | supplier-response-revision-workflow |
| B-5 | role-approval-implementation |
| B-6 | role-approval-implementation／7-A#4（Portal Design Implications） |
| C-1 | target-production10章／7-A#6,9章 |
| C-2〜C-9 | supplier-contact-mail-template-foundation（8項目全て）／target-production#11,#13／7-A#6 |
| D-1〜D-3 | target-production#6／supplier-response-revision-workflow |
| D-4,D-5 | target-production#4,#9／7-A#3／supplier-response-revision-workflow／production-ux-workflow-redesign15.4 |
| D-6 | target-production#10／supplier-response-revision-workflow |
| D-7〜D-9 | requirements27.4／Technical Design13.5節／production-ux-workflow-redesign15.4 |
| E-1〜E-7 | target-production#7,#8／7-A#5／fulfillment-follow-up-foundation（10項目） |
| F-1〜F-5 | target-production#12／excel-legacy-concurrency-control（10項目）／official-po-integration-detailed-design17章 |
| G-1〜G-3 | target-production13章／fulfillment-follow-up-foundation／role-approval-implementation |
| H-1,H-2 | 指示14章／official-po-integration-detailed-design#9,#10 |
| Appendix I | requirements27.4（残り7項目、他Themeに未整理のもの） |

---

## 15. まとめ

- 全46項目、うちTiming=A（9/17より前に確認必須）は**11項目**、Blocker=BLOCKERは**9項目**。
- BLOCKER 9項目の内訳: PO番号関連4件（A-1,A-2,A-4,H-2）、Supplier Mail関連2件（C-1,C-2,C-6）、Excel Conflict関連1件（F-1）。**この9項目が、7-C2B（実Handoff）・7-C4（Real Mail Send）に着手する前に最優先で確認すべき論点**である。
- 9/17デモ自体は、これら未確定事項の有無にかかわらず4章「SHOW」記載の全機能を問題なく実演できる（Prototype/Demo Modeとして完結しているため）。デモの目的は「機能を見せて業務適合性を確認しながら、上記BLOCKER項目への回答を引き出すこと」である。
