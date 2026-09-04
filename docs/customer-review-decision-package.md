# Customer Review Decision Package — Phase 7-D / 7-D2 / 7-E / 7-J / 8-A / 8-C / 8-D / 8-E / 8-F / 8-G / 8-H / 8-I

**Status**: Docs Only（Phase 7-D／7-D2／7-E／7-J／8-A／8-C／8-D／8-E／8-F追加分とも）。コード変更・DB Migration・Legacy変更は一切行っていない。**Phase 8-G・8-Hのみ例外的にCode変更を伴う**（Arrival/Warehouse Stock Visibility Foundation・Stock/Sales Visibility Foundation実装、いずれもLegacy Source変更ゼロ・Portal DB Migrationゼロ、詳細は`legacy-warehouse-logistics-logizero-reverse-engineering.md`27章、`legacy-stock-sales-data-reverse-engineering.md`実装結果追記）。本Document自体はDocs Only（16.2章#13・#19の実装状況欄を更新したのみ）。

**目的**: Phase 7-A〜7-C6の各Documentに散在する`CUSTOMER REVIEW`項目をすべて回収し、「何を顧客に確認しないと本番実装できないか」を一本化する（Phase 7-D）。さらにPhase 7-D2で、**「Sourceコードから分からない」＝「Gulliver社へ質問する」ではない**という前提のもと、Phase1社内（特にG-SYS保守担当のErnest）への確認で解決可能な項目を切り分け、最終的にGulliver社へ聞く質問を最小化する。

**対象読者**: 9/17顧客レビューに向けて準備するTechlead（ChatGPT）・SEPG（Claude Code）・Ernest（Phase1 G-SYS保守担当）・実際に顧客へ質問する担当者。

**関連Document**:
- `docs/ernest-current-operation-question-sheet.md`（Phase 7-D2新規、Phase 7-JでEDI関連4項目・Phase 8-Aで価格変更関連6項目・Phase 8-Cで在庫販売実績データ更新関連5項目・Phase 8-Dで請求仕入売上粗利関連5項目・Phase 8-EでGross Amount定義関連1項目・Phase 8-Fで倉庫物流関連5項目追加 — Ernestへ確認する39項目）
- `docs/customer-review-question-sheet.md`（Gulliver社向け質問票Draft — **Ernest確認前のDraftであり、最終版として確定していない**）
- `docs/target-price-change-workflow.md`（Phase 8-A新規 — 価格変更Target Design。Theme I（12b章）のCUSTOMER REVIEW項目I-1〜I-12の一次情報）
- `docs/legacy-stock-sales-data-reverse-engineering.md`（Phase 8-C新規 — 在庫・販売実績データ更新のReverse Engineering & Target Analysis。Theme J（12c章）のCUSTOMER REVIEW項目J-1〜J-6の一次情報）
- `docs/legacy-invoice-purchase-sales-gross-profit-reverse-engineering.md`（Phase 8-D新規、Phase 8-Eで23〜25章追記 — 請求・仕入・売上・粗利のReverse Engineering & Target Analysis、およびGross Amount定義のImplementation Gate Audit。Theme K（12d章）のCUSTOMER REVIEW項目K-1〜K-6bの一次情報）
- `docs/legacy-warehouse-logistics-logizero-reverse-engineering.md`（Phase 8-F新規、Phase 8-Gで27章追記 — 倉庫・物流・Logizero連携のReverse Engineering & Target Analysis。Theme L（12e章）のCUSTOMER REVIEW項目L-1〜L-5bの一次情報）
- `docs/requirements-coverage-and-remaining-gap-audit.md`（Phase 8-I新規 — 2026/08/26 Gulliver要望に対する全Phase横断Coverage監査。新規CUSTOMER REVIEW項目は追加していない）

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

## 1d. Phase 7-J: QA/Decision Document運用原則の明文化 + Official PO Excel生成元Fact反映

Phase 7-JではLegacy Source調査により、Official PO Excelの生成元がG-SYS自体には存在しないこと（G-SYSはImport専業でありExport/生成は行わない）が確認された。この確認に伴い、以下2点を実施した。

**(1) 新事実の反映（A-1・A-2、9章参照）**: 上記の確認結果を各該当項目の「Current G-SYS Fact」欄へ追記した。既存のQuestion文（誰が・いつExcelを作成するか等、Source単独では確定できない残りの論点）はそのまま維持している——「Source Confirmedになった部分」と「依然として未確定な部分」を機械的に混同しないため。

**(2) C-10のErnest/Gulliver分割**: Phase 7-Hで追加したC-10（Supplier発注Channel）を、本原則に従いC-10a（現在の事実、B分類、Ernestへ）／C-10b（将来の決定、D分類、Gulliverへ）に再分割した。

**今後この2つのDocument（および`docs/ernest-current-operation-question-sheet.md`）を更新する際は、新規QA Documentを都度作成せず、既存の本Document体系へ反映することを標準運用とする。** 判定基準は1b章の4分類表をそのまま適用する:

| 新たに判明した情報の性質 | 反映先 | 扱い |
|---|---|---|
| Source（Legacy Code）で確定できた事実 | 該当項目の「Current G-SYS Fact」欄（A分類） | Questionから除外し、Fact欄へ記載。Questionのうち依然未確定な部分は残す |
| Phase1/Ernestが確認できる可能性が高い技術的実態・運用手順 | `docs/ernest-current-operation-question-sheet.md`（B分類） | 新規Q番号を追記、または既存Qを拡充。優先度別/Theme別サマリ表も更新する |
| Gulliver社固有の現行業務運用（Ernestでは確定できない） | 該当Theme内の項目（C分類） | 既存項目と重複しないか必ず確認し、重複する場合は既存項目を拡充する（新規ID化しない） |
| Portal導入後の新しい業務ルール・権限・運用方針（意思決定） | 該当Theme内の項目（D分類） | 同上。新規IDは既存項目で表現できない場合のみ追加する |

**新規ID発番は最終手段とする**（1b章で確立したa/b分割パターン——例: A-1a/A-1b、C-10a/C-10bのように、既存Question内での分割で表現できないかを先に検討する）。`docs/customer-review-question-sheet.md`（Gulliver向けDraft）は、Ernest確認前の暫定版という位置付け（同Document冒頭の注記）を維持し、Ernestの回答が得られ次第、🟣マーク項目を随時更新・削除する運用を継続する。

3章のQuick Reference・14章のTraceability・15章の集計は、今回の反映によりC-10a/C-10bの2項目が正式にカウントされ、**72項目→74項目**（B分類13→14、D分類51→52）に更新した。詳細は各章を参照。

## 1e. Phase 8-A: Price Change Target Design（Theme I追加）

`docs/target-price-change-workflow.md`（Price Change Target Design）17章のCUSTOMER REVIEW項目を、1d章の運用原則に従い新設**Theme I: Price Change**（12b章）として追加した。既存Theme A-Hとの重複は無い（Phase 8-A以前、本Document内にPrice Change関連項目は0件）。Ernestが確認可能な「現在の事実」成分を含む3項目（Item Group⇄SKU連動・Item GroupとBrandの対応関係・価格変更の現行実施頻度/実施者/承認者）はa/b分割し、`docs/ernest-current-operation-question-sheet.md`へQ18-Q23として追加した（うちa成分に対応するのはQ20/Q21/Q22の3件、残るQ18/Q19/Q23はPrice Change Target Design自体には直結しない技術確認事項でありDecision Package上のトピックとしては計上しない — H-1/H-2に対応するQ13と同種の扱い）。

3章のQuick Reference・14章のTraceability・15章の集計は、今回の反映によりI-1〜I-12（a/b分割込みで15項目）が追加され、**74項目→89項目**（B分類14→17、D分類52→64）に更新した。詳細は各章を参照。

## 1f. Phase 8-C: Stock / Sales Data Update Reverse Engineering & Target Analysis（Theme J追加）

`docs/legacy-stock-sales-data-reverse-engineering.md`（Stock/Sales Data Update Reverse Engineering & Target Analysis）16章のCUSTOMER REVIEW項目を、1d章の運用原則に従い新設**Theme J: Stock / Sales Data Update**（12c章）として追加した。既存Theme A-Iとの重複は無いことを確認済み（既存D-5「欠品/長期欠品/廃番の正式定義」とOOS/長期OOSの論点は重複するため、Theme Jには新規登録せずD-5をそのまま参照する — 本Phaseの指示どおり）。Ernestが確認可能な「現在の事実」成分（在庫データ反映タイミングの実際の運用）はa/b分割し、`docs/ernest-current-operation-question-sheet.md`へQ24-Q28として追加した（うちa成分に対応するのはQ24/Q25/Q28の3件、Q26/Q27はTempostar Batch起動頻度・File Transfer方式というInfrastructure確認事項でありDecision Package上のトピックとしては計上しない — H-1/H-2と同種の扱い）。

3章のQuick Reference・14章のTraceability・15章の集計は、今回の反映によりJ-1a・J-1b・J-2・J-3・J-4・J-5・J-6（計7項目）が追加され、**89項目→96項目**（B分類17→19、D分類64→69）に更新した。詳細は各章を参照。

## 1g. Phase 8-D: Invoice / Purchase / Sales / Gross Profit Reverse Engineering & Target Analysis（Theme K追加）

`docs/legacy-invoice-purchase-sales-gross-profit-reverse-engineering.md`（Invoice/Purchase/Sales/Gross Profit Reverse Engineering & Target Analysis）18章のCUSTOMER REVIEW項目を、1d章の運用原則に従い新設**Theme K: Invoice / Purchase / Sales / Gross Profit**（12d章）として追加した。既存Theme A-Jとの重複は無いことを確認済み。Ernestが確認可能な「現在の事実」成分（Supplier Invoiceの実際の受領形式）はa/b分割し、`docs/ernest-current-operation-question-sheet.md`へQ29-Q33として追加した（うちa成分に対応するのはQ29の1件、Q30/Q31/Q32/Q33はInvoice機能自体の詳細確認事項でありDecision Package上のトピックとしては計上しない — Theme J章のQ26/Q27等と同種の扱い）。

3章のQuick Reference・14章のTraceability・15章の集計は、今回の反映によりK-1・K-2・K-3・K-4・K-5a・K-5b（計6項目）が追加され、**96項目→102項目**（B分類19→20、D分類69→74）に更新した。詳細は各章を参照。

## 1h. Phase 8-E: 仕入集計・請求書目視確認支援 Foundation — Implementation Gate Audit（Theme K追加分）

`docs/legacy-invoice-purchase-sales-gross-profit-reverse-engineering.md` 23章（Phase 8-E追記、Gross Amount定義の一意性監査）の結果、**Gate: STOP**と判定し、「仕入確認」Foundationの実装は行わなかった（Docs Onlyで停止、23.4章）。監査で新たに確認したCUSTOMER REVIEW論点を、既存**Theme K**（12d章）へK-6a/K-6bとして追加した（新規Theme・新規QA Documentは作成していない）。K-6aはErnestが確認可能な「現在の事実」成分（`PrOfficialPoImportBatch`由来のAMT_TTL/AMT_LINE不整合の認識・`PrBLInvImportBatch`の実際の適用範囲）のためa/b分割し、`docs/ernest-current-operation-question-sheet.md`へQ34として追加した。

また、Section 0で確定した「本機能は請求書管理システムではなく、G-SYS側のGross Amountを表示し、様々な形式の請求書と担当者が目視確認する仕組みである」というCustomer Requirementを、16.2章（#10行）へ記録した。

3章のQuick Reference・14章のTraceability・15章の集計は、今回の反映によりK-6a・K-6b（計2項目）が追加され、**102項目→104項目**（B分類20→21、D分類74→75）に更新した。詳細は各章を参照。

## 1i. Phase 8-F: Warehouse / Logistics / Logizero Reverse Engineering & Target Analysis（Theme L追加）

`docs/legacy-warehouse-logistics-logizero-reverse-engineering.md`（Warehouse/Logistics/Logizero Reverse Engineering & Target Analysis）21章のCUSTOMER REVIEW項目を、1d章の運用原則に従い新設**Theme L: Warehouse / Logistics / Logizero**（12e章）として追加した。既存Theme A-Kとの重複は無いことを確認済み（Theme J「在庫・販売実績データ更新」はTempostar発`SOLD_QTY`側のTiming論点であり、Theme L「倉庫・物流」はLogizero発`STK_QTY`側・Arrival側の論点であるため、重複ではなく隣接領域として整理した）。Ernestが確認可能な「現在の事実」成分（Selenium方式/SFTP方式のどちらが本番経路か）はa/b分割し、`docs/ernest-current-operation-question-sheet.md`へQ35-Q39として追加した（うちa成分に対応するのはQ35の1件、Q36-Q39はWarehouse機能の詳細確認事項でありDecision Package上のトピックとしては計上しない — Theme K章のQ30-Q33等と同種の扱い）。

3章のQuick Reference・14章のTraceability・15章の集計は、今回の反映によりL-1・L-2・L-3・L-4・L-5a・L-5b（計6項目）が追加され、**104項目→110項目**（B分類21→22、D分類75→80）に更新した。詳細は各章を参照。

## 1j. Phase 8-I: Requirements Coverage & Remaining Gap Audit（新規項目追加なし、記載更新のみ）

Phase 8-Iは監査Phaseであり、新規CUSTOMER REVIEW項目の追加・新規QA Documentの作成は行っていない（`docs/requirements-coverage-and-remaining-gap-audit.md`参照）。既存記載の更新のみ2件実施した: (1) K-1へ「Phase 8-GのArrival Detailが並列表示をFoundation実装済み」という補足を追記（12d章）、(2) 16.2章#11（旧・未整理のままだった「Warehouse/Logistics連携」総称行）を、Phase 8-F/Gで#19-21として詳細分解済みである旨を明記する形に更新。**項目数（110項目、B分類22・D分類80）は変更なし。**

---

## 1k. 2026-09-04: Production PO Workflow Working Assumption適用（項目のStatus変更なし、実装状況の追記のみ）

ユーザーから、以下の項目について**最終確定ではないWorking Assumption**が提示され、これを前提としてProduction指向の発注Workflow実装（Phase 9-A〜9-G、`docs/production-po-workflow-implementation.md`・`docs/production-email-edi-workflow.md`）を進めた。**重要: これらの項目はいずれも本Decision Package上では引き続きOPEN（B/D分類のまま）であり、Working Assumptionの採用によって「解決済み」に変更してはいない** — Gulliver/Ernestからの正式回答が得られ次第、実装は変更され得る。

| 項目 | 本Decision Packageでの現在の分類（変更なし） | 今回のWorking Assumption | 実装への反映 |
|---|---|---|---|
| A-1a/A-1b（PO番号採番規則・採番主体） | B（Ernest Q1）/ D | 「G-SYS側に11桁目以降の明確なRuleはなく、30文字以内であること以外に制約はない」を前提に、PO番号はStaffが入力（将来自動採番へ変更可能な構造） | PO番号Validationは長さチェックのみ、ID Code/区切り文字の構造検証は追加していない（`production-po-workflow-implementation.md` 2.1章） |
| A-2（正式PO番号の決定者・Excel作成/配置の実運用フロー） | B（Ernest Q2） | Official POは購買担当Staffが作成し、G-OPS→Excel生成→Import Folder配置→既存Batch取込、という流れ | Import Folder Pathは設定値としてHardcodeせず、Local/Demo/Test/Productionを分離（`production-po-workflow-implementation.md` 3章） |
| C-10a/C-10b（Supplier発注Channel、EDI） | B（Ernest Q14-Q17）/ D | 約90% Email・約10% EDI、メーカーごとにCommunication Channelを持つ | 新規Portal専用Master `manufacturer_channel`を実装（初期データは空、実データはCUSTOMER CONFIRMATION REQUIRED）。実EDI連携（File/API等）は引き続き未実装（`production-email-edi-workflow.md` 1章） |

Quick Reference（3章）・各Theme章（5章以降）の記載・分類・項目数は本追記により変更しない。実装状況の詳細は上記2つの新規ドキュメントを参照。

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

### 2.3 Theme（11分類、Phase 8-AでTheme I、Phase 8-CでTheme J、Phase 8-DでTheme K追加）

A. PO番号・Official PO　B. Approval / Permission　C. Supplier Communication　D. Supplier Response / Revision　E. Fulfillment / Follow-up　F. Excel coexistence / Conflict　G. Cancellation / Correction　H. Infrastructure / Operation　I. Price Change（Phase 8-A追加）　J. Stock / Sales Data Update（Phase 8-C追加）　K. Invoice / Purchase / Sales / Gross Profit（Phase 8-D追加）

---

## 3. Quick Reference（全102項目、Phase 7-D2分類つき。B-7〜B-14はPhase 7-E追加分。A-7はPhase 7-H追加分だが、旧Appendix Iの1項目を移動・再整理したものであり総数への純増はない。C-10a/C-10bはPhase 7-H時点で本文のみ存在しQuick Referenceへの反映漏れがあったものをPhase 7-Jで是正・Ernest/Gulliver分割した2項目で、正味+2。I-1〜I-12（a/b分割込みで15項目）はPhase 8-Aで新規追加。J-1a〜J-6（7項目）はPhase 8-Cで新規追加。K-1〜K-5b（6項目）はPhase 8-Dで新規追加）

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
| A-7 | PO番号 | 希望納期（Delivery）をPortalのどのWorkflow段階で必須にすべきか | B | IMPORTANT | **D** |
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
| C-10a | Supplier Communication (EDI) | 現在、EDI Supplierへの実際の発注方式・PO File作成有無・識別方法・回答管理方法 | B | IMPORTANT | **B**（Ernest Q14-Q17） |
| C-10b | Supplier Communication (EDI) | 将来、EDI Supplierの正式な特定・管理方法、連携方式の統一性 | B | IMPORTANT | **D** |
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
| I-1 | Price Change | 一括変更の正式単位（Item Group限定かSKU横断/Brand単位も要るか） | C | LATER | **D** |
| I-2 | Price Change | Future Price予約機能の採否 | C | LATER | **D** |
| I-3 | Price Change | Effective Date到来時のApplyトリガー方式 | C | LATER | **D** |
| I-4 | Price Change | 赤字警告の閾値・Action | C | LATER | **D** |
| I-5 | Price Change | Price History記録要否・Retention期間 | C | LATER | **D** |
| I-6a | Price Change | 現在、価格変更の実施頻度・実施者・承認者 | C | LATER | **B**（Ernest Q22） |
| I-6b | Price Change | 将来、Approval Workflow要否・承認者・閾値・自己承認・Correction | C | LATER | **D** |
| I-7 | Price Change | SCHEDULED（予約中）Change Setの取消権限 | C | LATER | **D** |
| I-8a | Price Change | 現在、Item Group価格変更が配下SKUへ連動しているか | C | LATER | **B**（Ernest Q20） |
| I-8b | Price Change | 将来、Item Group⇄SKU自動連動を採用すべきか | C | LATER | **D** |
| I-9 | Price Change | 同一SKU複数Future変更・同一Effective Date衝突時の扱い | C | LATER | **D** |
| I-10 | Price Change | Legacy側手動変更検出時のPolicy | C | LATER | **D** |
| I-11a | Price Change | 現在、Item GroupとBrandの対応関係 | C | LATER | **B**（Ernest Q21） |
| I-11b | Price Change | 将来、Brand単位一括変更を正式機能とすべきか | C | LATER | **D** |
| I-12 | Price Change | 価格変更関連の追加Role要否 | C | LATER | **D** |
| J-1a | Stock/Sales Data | 現在、在庫データ反映タイミング（フラグ操作者・オーバーライド運用・実際の遅延日数） | C | LATER | **B**（Ernest Q24/Q25/Q28） |
| J-1b | Stock/Sales Data | 将来、在庫データ反映の即時性改善要否 | C | LATER | **D** |
| J-2 | Stock/Sales Data | Tempostar関連Batchの起動頻度・File Transfer方式 | C | LATER | **B**（Ernest Q26/Q27） |
| J-3 | Stock/Sales Data | 日次/週次Sales Data蓄積・可視化の要否 | C | LATER | **D** |
| J-4 | Stock/Sales Data | 過去Sales Trend参照期間 | C | LATER | **D** |
| J-5 | Stock/Sales Data | Recommended Qty（calc4）へのSales Trend組み込み要否 | C | LATER | **D** |
| J-6 | Stock/Sales Data | Portal Sales History System of Record化の承認 | C | LATER | **D** |
| K-1 | Invoice/Purchase/Sales | PO/Invoice Qty比較機能の要否 | C | LATER | **D** |
| K-2 | Invoice/Purchase/Sales | 差異管理Business Rule | C | LATER | **D** |
| K-3 | Invoice/Purchase/Sales | Sales Amount蓄積・System of Record判断 | C | LATER | **D** |
| K-4 | Invoice/Purchase/Sales | 実績粗利可視化の要否 | C | LATER | **D** |
| K-5a | Invoice/Purchase/Sales | 現在、Supplier Invoiceの実際の受領形式 | C | LATER | **B**（Ernest Q29） |
| K-5b | Invoice/Purchase/Sales | 将来、Supplier Invoice Integration方式の正式決定 | C | LATER | **D** |
| K-6a | Invoice/Purchase/Sales | 現在、`TR_INV.AMT_TTL`/`AMT_LINE`のBatch間不整合・`PrBLInvImportBatch`の適用範囲（Phase 8-E Gate Audit） | C | LATER | **B**（Ernest Q34） |
| K-6b | Invoice/Purchase/Sales | 将来、「仕入確認」機能で正とすべきGross Amountの正式定義（付帯費用・Tax・Credit Nettingの扱いを含む） | C | LATER | **D** |
| L-1 | Warehouse/Logistics | Arrival Visibility機能の要否 | C | LATER | **D** |
| L-2 | Warehouse/Logistics | Warehouse Stock Visibility機能の要否 | C | LATER | **D** |
| L-3 | Warehouse/Logistics | Stock Discrepancy Visibility機能の要否・運用ルール | C | LATER | **D** |
| L-4 | Warehouse/Logistics | G-SYS→倉庫 変更情報連携の自動化範囲 | C | LATER | **D** |
| L-5a | Warehouse/Logistics | 現在、Logizero/Tempostar連携のSelenium/SFTPどちらが本番経路か | C | LATER | **B**（Ernest Q35） |
| L-5b | Warehouse/Logistics | 将来、連携方式の統一方針 | C | LATER | **D** |
| AppI-1〜6 | (Appendix I) | Phase 0由来のPrototype UI/計算仕様細部（6項目、13章参照。Requested Deliveryの必須/任意はA-7へ移動済み） | C | LATER | **D**（全項目） |

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
  - **（Phase 7-J追加）G-SYS自身にOfficial PO Excel/PDFを生成する機能は存在しない**（Source Confirmed）: `jp.ne.glv.utilities.export`配下にはArrivalList/ArrivalSchedule/PriceList/ProductDescription/StockList(History)/TariffQuota等の帳票Export Classが存在するが、PO/PurchaseOrder相当のExport Classは存在しない。PO番号はG-SYSが生成するのではなく、外部で作成済みのExcelから読み取るのみ。
- **Target Portal Proposal**: 現時点でPortalは正式PO番号を一切採番しない（`officialPoNo`は常にNULL）。
- **A-1a（現在の事実）**: 「現在、正式PO番号は誰が、どのルールで採番していますか？11桁目以降の規則、ID Codeの値域を教えてください。」　**7-D2分類: B（Ernest Q1）**　**Timing**: A　**Blocker**: BLOCKER
- **A-1b（将来の決定）**: 「将来的にPortalがこの番号を採番してよいでしょうか？」　**7-D2分類: D（Gulliver Future Decision）**　**Timing**: A　**Blocker**: BLOCKER
  - **Answer Options**: A. 従来どおり人手が採番　B. Portalが形式準拠の候補番号を生成し人が確認・上書き　C. Portalが完全自動採番
  - **Our Recommendation**: B（移行期間として人の確認を残す）
- **Source**: target-production 21章#1／7-A 19章#2／official-po-integration-detailed-design 21章#1

### A-2. 正式PO番号の決定者・Excel作成/配置の実運用フロー

- **Current G-SYS Fact**: Import Pipelineの構造（Upload/Work/Backupフォルダ）はSourceから確認できたが、「誰が」「どのタイミングで」配置しているかはSourceからは追えない。G-SYSは常にImport側（既に作成されたExcelを読み込む側）であり、Excelの作成元（どの部署・ツール・システムで作られているか）はSource上一切特定できない（A-1の追記のとおり、G-SYS自身に生成機能がないため必然的に外部で作られている）。
  - **（Phase 7-J追加）`PrOfficialPoImportBatch`/`PrInitialPoImportBatch`はどちらも「所定Folderに置かれたExcelを読む」処理のみで構成されており（Source Confirmed）、生成・Downloadに相当する処理は存在しない。**
  - **（Phase 7-J追加）Test用Fixtureとの区別**: `gulliver/testfile/OfficialPO.xlsx`はPrOfficialPoImportBatchの単体Test用Import Test Dataであり、正式なExcel生成Template・雛形ではない（Source Confirmed - このファイルを書き出すコードは存在せず、Testコードから読み込まれるだけ）。将来Portalが正式Excelを生成する際、このFile自体をTemplateとして流用できるとは限らない点に注意。
- **Target Portal Proposal**: 将来的にPortalが承認済みOrderからExcelを自動生成し、Import Folderへ配置する（7-C2B）ことを目標とする。
- **Question**: 「現在、正式Official PO Excelは誰が作成し、どのフォルダへ、どのタイミングで配置していますか。」　**7-D2分類: B（Ernest Q2）**　**Timing**: A　**Blocker**: BLOCKER
- **Our Recommendation**: 承認直後に自動生成・投入する設計を提案するが、既存の人手フローとの並走期間を設けるか要確認。
- **Source**: official-po-integration-detailed-design 21章#2/#3／Phase 7-J Source監査（`jp.ne.glv.utilities.export`配下・testfile確認）

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

### A-7. 希望納期（Delivery）をPortalのどのWorkflow段階で必須にすべきか（Phase 7-H追加）

- **Current G-SYS Fact（Source Confirmed）**: `PrOfficialPoImportBatch`/`PrInitialPoImportBatch`（同一Row/Col定数構造、`official-po-integration-detailed-design.md` 4章の比較表と同じSource）を確認した結果：
  - **Official PO Import**: Delivery Week・Delivery Dateの両セルとも**必須**（空欄は`ERR_MSG_CELL_REQUIRED`でImport全体が失敗）。DB上（`TR_PO.DELIV_WEEK`/`DELIV_DATE`、Portal側`tr_po`ミラーでも同じ）は両方NULL許容だが、Import Validationレベルで事実上必須。
  - **Initial PO Import**: Delivery Weekセルは値が必須だが、リテラルの文字列`"TBD"`のみ許可（実日付は拒否）。Delivery Dateセルは逆に**値があるとエラー**（`DELIVERY DATE IS NOT NEEDED IN INITIAL PO`）。
  - **Portal自身の現状**: `OrderDraftPersistenceService`/`OrderStatusTransitionService`のいずれも`requestedDelivery`の必須Validationを一切持たない（`@NotNull`等のAnnotationも存在しない） - Draft保存・承認依頼・ADMIN承認・Demo Send、すべて未入力のまま通過可能（Source確認済み、Phase 7-H）。
  - **A-5の既存結論との関係**: 4章の評価どおりPortalはInitial PO Integrationを行わない前提（`APPROVED → G-SYS OFFICIAL`のみ）のため、上記Initial PO側の制約（Delivery Date拒否）はPortalには直接関係しない。関係するのはOfficial PO Import側の必須制約のみ。
  - **データ形式の非対称**: PortalのrequestedDeliveryは単一の実`Date`だが、LegacyのDelivery Weekは`"WK36"`のような週コード文字列、Delivery Dateは別列の自由記述文字列（`DELIV_DATE VARCHAR(50)`、実DATE型ではない） - 1:1対応しない。
- **Question**: 「①Portal側で希望納期を入力必須にすべきタイミングはどこか（Draft保存時／承認依頼時／ADMIN承認時／G-SYS Integration Prep時のいずれか、それとも現状どおり任意のままでよいか）。②必須にする場合、LegacyのDelivery Week（週コード）／Delivery Date（自由記述日付）という2フィールド形式と、Portalの単一Dateフィールドをどう対応付けるべきか。」
- **7-D2分類**: **D（Gulliver Future Decision）** - Legacy側の必須制約自体はSourceで確定済みだが、それをPortal自身のどのWorkflow段階で・どの形式で反映すべきかは業務判断であり、Sourceだけでは決定できない。
- **Timing**: B（9/17デモ当日に確認 - Prototypeは現状の任意のまま見せられるが、Official PO Integration実装（7-C2B相当）に進む前に確定したい）　**Blocker**: IMPORTANT
- **今回の対応（Phase 7-H）**: 上記の理由によりPortal側のValidationは追加していない（勝手な必須化はしない、というPhase 7-H自身の指示に従った）。Order Draft画面の希望納期入力欄に「任意項目です」というHelper Textのみ追加し、現在の実際の挙動（未入力でも進める）を正直に表示する変更のみ実施。
- **Source**: `PrOfficialPoImportBatch.java`（Row/Col 14,3〜14,4）／`PrInitialPoImportBatch.java`（同座標）／`official-po-integration-detailed-design.md` 4章／`OrderDraftPersistenceService.java`／`OrderStatusTransitionService.java`

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

## 7. Theme C: Supplier Communication（メール・Phase 7-JでEDI等その他Channelを追加）

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

### C-10. Supplier発注Channel（EDI等）の正式運用（Phase 7-H追加、Phase 7-JでErnest/Gulliver分割）

- **Current G-SYS Fact（Source Confirmed）**: 現行Source（Backend全体・全docs）に「EDI」「communicationChannel」「procurementMethod」に相当する概念・列・Masterは一切存在しない（`\bEDI\b`でSource全体を検索し0件、Phase 7-H監査で確認）。Legacy Supplier Master（`MS_COMM CATE_ID='MS_SUPPL'`）も、Portal側が現在READする範囲ではコード・名称のみで、発注方法に関する属性は無い。
- **手動確認で判明した新業務前提**: メーカーとの発注方法は約90%がEmail、約10%がメーカー側EDI Systemとのこと（Source外の実運用情報、口頭確認）。EDI Supplierの場合、PortalからのMail Send自体を行わず、メーカー側EDI Systemで発注する運用の可能性がある。
- **Phase 7-Hの対応**: 「Order/POが確定したこと」と「どのChannelでSupplierへ伝えたか」を分離するFoundationのみ実装（`portal_order.communication_channel`列、EMAIL/EDIの記録、Supplier Response到達性の確保）。実際のEDI連携（File Format・API・認証・SFTP等）は一切実装していない。どのSupplierが実際にEDIを使うかというMasterデータも追加していない（架空のSupplier区分を推測で作らないため） - PO Preview画面でADMIN/OPERATORが送信のたびに手動選択する設計に留めている。
- **C-10a（現在の事実）**: 「①EDI Supplierへの実際の発注方式は何か（手操作／メーカー側Web Portal手入力／File授受（SFTP等）／API連携等）。②EDI発注の場合、G-SYS側でもPO File（Excel/PDF等）を作成・保存しているか、それともTR_PO登録のみか。③現在、どのSupplierがEDI対象かをどこで・どうやって識別しているか。④EDI発注後のメーカー回答（数量・納期確定等）を現在どう受け取り・記録しているか。」　**7-D2分類: B（Ernest Q14-Q17）**　**Timing**: B　**Blocker**: IMPORTANT
- **C-10b（将来の決定）**: 「①実際にEDIで発注しているSupplierを正式にどう特定・管理すべきか（Supplier Master拡張／Portal専用Master／その都度手動選択のまま、等）。②将来的に実際のEDI連携（File/API等）を実装する場合、Supplierごとに方式が異なりうるか。」　**7-D2分類: D（Gulliver Future Decision）**　**Timing**: B　**Blocker**: IMPORTANT
- **Source**: Phase 7-H Source監査（Backend全体・docs全体、EDI関連ゼロ件を確認）／ernest-current-operation-question-sheet Q14-Q17（Phase 7-J追加）

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
- **（Phase 8-K追記）Production Readiness監査での重み付け**: `docs/production-readiness-and-integration-boundary-audit.md` 9章の詳細監査により、本Questionの回答が「Legacy MySQL reachability／Network path／Firewall／VPN／DNS／TLS／Reverse proxy／Production domain」を含むHosting/Network関連9項目すべての起点であることを確認した（現時点でSource確認済みの事実はゼロ、7-A §18-6の「AWS利用の示唆」も未確定のまま）。分類・Timing・Blockerの変更なし、影響範囲の広さのみ再確認。

### H-2. 既存Import Batchの起動Trigger・実行頻度

- **Question**: 「既存バッチはどのくらいの頻度で動いているか。」　**7-D2分類: B（Ernest Q7）**　**Timing**: A　**Blocker**: BLOCKER
- **Source**: official-po-integration-detailed-design 21章#9-10

---

## 12b. Theme I: Price Change（Phase 8-A追加）

Phase 8-Aの`docs/target-price-change-workflow.md`（Target Design）17章のCUSTOMER REVIEW項目（PC-1〜PC-14、PC-15はSource追加調査のため本章では扱わない）を、既存Themeの分類基準（2章）に沿って整理する。9/17デモは発注(Ordering)機能のみが対象であり価格変更は含まれないため、全項目**Timing: C（デモ後・本番設計時でよい）**とする。a/b分割は、Ernestが確認可能な「現在の事実」成分を含む項目（I-6・I-8・I-11）にのみ適用する（1b章の原則）。

### I-1. 複数商品の一括変更の正式単位

- **Question**: 「一括価格変更の対象単位は、既存のItem Group単位のままでよいか。それとも、SKUを任意に跨いだ選択やBrand単位も必要か。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 9章・17章PC-1

### I-2. Future Price予約機能の採否

- **Question**: 「将来日の価格変更予約機能を、新Portalに実装すべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 8章B・17章PC-2

### I-3. Effective Date到来時のApplyトリガー方式

- **Question**: 「Future Price予約を採用する場合、Effective Date到来時に何をもってG-SYSへ反映すべきか（自動Batch／手動確認後実行等）。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 8章B・17章PC-3

### I-4. 赤字警告の閾値・Action

- **Question**: 「利益率が一定以下、または赤字となる価格変更に対し、警告のみとするか、保存自体を禁止するか、追加承認を要求するか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 10章・17章PC-4

### I-5. Price History記録要否・Retention期間

- **Question**: 「価格変更履歴をどこまで詳細に、どのくらいの期間保持すべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 14章・17章PC-5

### I-6a/I-6b. 価格変更のApproval Workflow

- **I-6a（現在の事実）**: 「価格変更は現在どのくらいの頻度で行われているか。実施者・（変更幅超過時の）承認者は誰か。」　**7-D2分類: B（Ernest Q22）**　**Timing**: C　**Blocker**: LATER
- **I-6b（将来の決定）**: 「新PortalにApproval Workflowが必要か。必要な場合、承認者・承認閾値・ADMIN自己承認の可否・適用後Correctionの扱いをどうすべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 11章・17章PC-6/PC-7/PC-9

### I-7. SCHEDULED（予約中）Change Setの取消権限

- **Question**: 「Future Price予約を採用する場合、予約の取消は誰がいつまで行えるべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 11章・13.2章・17章PC-8

### I-8a/I-8b. Item Group価格変更のSKU連動

- **I-8a（現在の事実）**: 「実運用上、Item Group価格を変更した際、配下SKUの価格も連動して変わっているか。」　**7-D2分類: B（Ernest Q20）**　**Timing**: C　**Blocker**: LATER
- **I-8b（将来の決定）**: 「新Portalで採用する場合、Item Group変更が配下SKUへ自動連動する仕様にすべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 4章・9章・17章PC-10

### I-9. 同一SKU複数Future変更・同一Effective Date衝突時の扱い

- **Question**: 「Future Price予約を採用する場合、同一SKUに複数の予約が並存したらどう扱うか（最新優先・拒否・Effective Date順等）。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 13.2章・17章PC-11

### I-10. Legacy側手動変更検出時のPolicy

- **Question**: 「Portalで予約中の価格変更について、その間にLegacy側で直接Excel編集された場合、Apply中止・警告・強制上書きのどれを採るべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 13.2章・17章PC-12

### I-11a/I-11b. Brand単位一括変更

- **I-11a（現在の事実）**: 「Item GroupとBrandはどう対応しているか（1対1か、1Brandに複数Item Groupか）。」　**7-D2分類: B（Ernest Q21）**　**Timing**: C　**Blocker**: LATER
- **I-11b（将来の決定）**: 「Brand単位の一括価格変更を正式機能として実装すべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 9章・17章PC-13

### I-12. 価格変更関連の追加Role要否

- **Question**: 「価格変更について、既存のOPERATOR/ADMIN以外の専任Role（価格管理担当等）が必要か。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: target-price-change-workflow 11章・17章PC-14

---

## 12c. Theme J: Stock / Sales Data Update（Phase 8-C追加）

`docs/legacy-stock-sales-data-reverse-engineering.md`（Stock/Sales Data Update Reverse Engineering & Target Analysis）16章のCUSTOMER REVIEW項目を整理する。9/17デモは発注(Ordering)機能のみが対象であり在庫・販売実績データ更新は含まれないため、全項目**Timing: C（デモ後・本番設計時でよい）**とする。既存D-5（欠品/長期欠品/廃番の正式定義）と重複する論点は本Themeへ新規登録しない。

### J-1a/J-1b. 在庫データ反映タイミング

- **J-1a（現在の事実）**: 「前月在庫確定フラグ（MON_PRC_STK_UPD）を実際に誰が・いつ・どのくらいの頻度で操作しているか。SOLD_QTY対象月の手動オーバーライドは実際に使われているか。実際の『月替わり後のタイムラグ』は何日程度か。」　**7-D2分類: B（Ernest Q24/Q25/Q28）**　**Timing**: C　**Blocker**: LATER
- **J-1b（将来の決定）**: 「在庫データ反映の即時性改善が必要か、必要な場合どの程度の即時性を求めるか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-stock-sales-data-reverse-engineering 4章・6章・9章・16章

### J-2. Tempostar関連Batchの起動頻度・File Transfer方式

- **Question**: 「Tempostar関連Batch群（Sales CSV Import／Stock Upload等）は実際にどのくらいの頻度で、どういうトリガーで動いているか。Tempostar CSVは実際にどうG-SYS Import Folderへ転送されているか。」　**7-D2分類: B（Ernest Q26/Q27）**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-stock-sales-data-reverse-engineering 7章

### J-3. 日次/週次Sales Data蓄積・可視化の要否

- **Question**: 「日次/週次単位のSales Data蓄積・可視化機能が必要か。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-stock-sales-data-reverse-engineering 8章・13章Option B/C

### J-4. 過去Sales Trend参照期間

- **Question**: 「過去Sales Trendをどの程度の期間参照できる必要があるか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-stock-sales-data-reverse-engineering 8章・13章Option B/C

### J-5. Recommended Qty（calc4）へのSales Trend組み込み要否

- **Question**: 「発注数量計算（calc4）にSales Trendを組み込むべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-stock-sales-data-reverse-engineering 10章・13章Option D

### J-6. Portal Sales History System of Record化の承認

- **Question**: 「Portal側でSales Historyを蓄積する場合、Current StockはLegacyが正のまま、Sales HistoryのみPortalが正になるという責任分担でよいか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-stock-sales-data-reverse-engineering 14章

---

## 12d. Theme K: Invoice / Purchase / Sales / Gross Profit（Phase 8-D追加、Phase 8-EでK-6a/K-6b追加）

`docs/legacy-invoice-purchase-sales-gross-profit-reverse-engineering.md`（Invoice/Purchase/Sales/Gross Profit Reverse Engineering & Target Analysis）18章・23章のCUSTOMER REVIEW項目を整理する。9/17デモは発注(Ordering)機能のみが対象であり請求・仕入・売上・粗利は含まれないため、全項目**Timing: C（デモ後・本番設計時でよい）**とする。

**Phase 8-Eで確定したCustomer Requirement（記録）**: 「仕入確認」機能は請求書管理システムではなく、**Supplierから受領する請求書（形式は紙／PDF／データ／別システム等、不定）に記載されたGross Amountが妥当かを、G-SYS上の仕入Dataを任意条件で集計して担当者が目視確認できるようにする**機能である。Portal側でInvoice PDF/OCR結果/手入力Invoice金額/突合結果を保持する設計は行わない（`legacy-invoice-purchase-sales-gross-profit-reverse-engineering.md` 23.0章）。この前提は、K-1〜K-5bの「取り込み・突合・差異管理」を前提とした論点とは異なる、より限定されたScopeであることに留意する。

### K-1. PO/Invoice Qty比較機能の要否

- **Question**: 「発注数量とInvoice数量（TR_PO_DTL.QTY_PO vs TR_INV_DTL.QTY）を比較する機能が必要か。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-invoice-purchase-sales-gross-profit-reverse-engineering 8章・13章Option B
- **（Phase 8-I追記）実装状況の補足**: 「機能の要否」自体は依然D分類（Gulliver Future Decision）のまま未回答だが、Phase 8-GのArrival Detail画面（`/arrivals/:supplierCode/:poNumber/:invoiceNumber`）が、SKU単位でOrdered Qty/Invoice Qty/Stock-In Qtyを既に並列表示している（差異判定・Diff表示・OK/NG判定は行っていない）。したがって「並べて見る」という最小限の実現方法はFoundation実装済みであり、本質問はあくまで「Diff表示・許容差判定等の追加Business Ruleが必要か」という残りの論点に絞られる。詳細は`requirements-coverage-and-remaining-gap-audit.md` 13.4章参照。

### K-2. 差異管理Business Rule

- **Question**: 「PO/Invoice間で差異が検出された場合、誰が確認し、許容差・自動承認・手動確認・差戻し・Supplier問い合わせ・Closeをどう運用すべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-invoice-purchase-sales-gross-profit-reverse-engineering 9章・13章Option C

### K-3. Sales Amount蓄積・System of Record判断

- **Question**: 「Tempostar由来の売上金額データ（現在はLegacyのどこにも永続化されていない）をPortal側で新規蓄積すべきか。その場合のSystem of Record責任分担は。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-invoice-purchase-sales-gross-profit-reverse-engineering 5章・11章・13章Option D

### K-4. 実績粗利可視化の要否

- **Question**: 「実際の売上・仕入に基づく実績粗利（Item Master上の理論利益率とは別概念）を可視化する機能が必要か。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-invoice-purchase-sales-gross-profit-reverse-engineering 6章・13章Option E

### K-5a/K-5b. Supplier Invoice Integration方式

- **K-5a（現在の事実）**: 「Supplier Invoiceは実際にどの形式（PDF/Excel/紙等）で受領しているか。Official PO Excel内のInvoice区画と同一のものか、別物か。」　**7-D2分類: B（Ernest Q29）**　**Timing**: C　**Blocker**: LATER
- **K-5b（将来の決定）**: 「Supplier Invoiceを新Portalへ取り込む場合、Excel Upload／CSV Upload／既存Legacy Import再利用／External System連携／Manual Entryのどれを正式な方式とすべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-invoice-purchase-sales-gross-profit-reverse-engineering 3章・12章・18章

### K-6a/K-6b. Gross Amount定義の一意性（Phase 8-E Gate Audit、K-5a/K-5bとは別論点）

**Phase 8-EのImplementation Gate Audit（`legacy-invoice-purchase-sales-gross-profit-reverse-engineering.md` 23章）で新たに判明**: `TR_INV.AMT_TTL`／`TR_INV_DTL.AMT_LINE`は、Invoice作成の主経路（`PrOfficialPoImportBatch`）ではPO発注数量ベースの誤った値／NULLとして書き込まれ、別Batch（`PrBLInvImportBatch`）が実行されて初めて正しい値に補正される構造であることをSource調査で確認した。この結果、**「G-SYS上の仕入金額合計」として何を表示すべきかが、Sourceの調査だけでは一意に確定できない**。

- **K-6a（現在の事実）**: 「`PrOfficialPoImportBatch`由来のAMT_TTL/AMT_LINE不整合（PO発注数量ベースの値がInvoice金額として書き込まれる、明細AMT_LINEがNULLのまま残る）について、実際の請求書確認業務で気づかれたことがあるか。`PrBLInvImportBatch`（Bill of Lading経由の補正）は実際にどのSupplier・取引形態で実行され、全Invoiceが最終的にこの経路を通るか。」　**7-D2分類: B（Ernest Q34）**　**Timing**: C　**Blocker**: LATER
- **K-6b（将来の決定）**: 「上記の不確実性（付帯費用`TR_INV_ADD_COST`の扱い、Tax、Credit Nettingを含む）を踏まえたうえで、『仕入確認』機能で正とすべきGross Amountの正式な定義をどうすべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-invoice-purchase-sales-gross-profit-reverse-engineering 23章（23.1〜23.5）
- **Note**: 本論点により、「仕入確認」Foundationの実装はPhase 8-Eで**Gate: STOP**（Docs Onlyで停止）と判定した。K-6b確定後、実装再検討の対象とする。

---

## 12e. Theme L: Warehouse / Logistics / Logizero（Phase 8-F追加）

`docs/legacy-warehouse-logistics-logizero-reverse-engineering.md`（Warehouse/Logistics/Logizero Reverse Engineering & Target Analysis）21章のCUSTOMER REVIEW項目を整理する。9/17デモは発注(Ordering)機能のみが対象であり倉庫・物流連携は含まれないため、全項目**Timing: C（デモ後・本番設計時でよい）**とする。

**Phase 8-Fで確認した重要な事実**: 2026/08/26打ち合わせSlide 13の要望（G-SYS上の変更情報を倉庫側へ連携したい）は、Legacy Sourceに**既に実装されている**倉庫→G-SYS/Tempostar方向の在庫連携（Selenium/SFTPによるLogizero連携）とは**逆方向**である。既存の連携はSlide 13の要望に対する回答にはならないため、混同しないよう注意する（`legacy-warehouse-logistics-logizero-reverse-engineering.md` 1章・5章）。

### L-1. Arrival Visibility機能の要否

- **Question**: 「`TR_ARR`のETA/ETA_WH/倉庫Report状況等をPortalへREAD ONLY表示する機能が必要か。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-warehouse-logistics-logizero-reverse-engineering 17章・18章Option A

### L-2. Warehouse Stock Visibility機能の要否

- **Question**: 「`MS_STK.STK_QTY`のWH_CD別内訳をPortalへREAD ONLY表示する機能が必要か。Phase 8-CのStock/Sales Option Aとの統合要否は。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-warehouse-logistics-logizero-reverse-engineering 17章・18章Option B

### L-3. Stock Discrepancy Visibility機能の要否

- **Question**: 「既存の在庫差異検知（`StkQtyDiscrepancyCheckerService`）の結果をPortalへ表示する機能が必要か。差異発生時に誰がどう対応すべきかの運用ルールは。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-warehouse-logistics-logizero-reverse-engineering 11章・18章Option C
- **（Phase 8-J追記）実装状況の補足**: Phase 8-I時点では「安全に着手可能な開発候補」として一時検討されたが、Phase 8-Jで**あえて実装しない**方針とした。理由：`QTY_STK_IN`（Stock In実績）と`STK_QTY`（Warehouse Stock）をTransaction単位で結ぶSource確認済みKeyが存在しないことがPhase 8-F/8-Gで既に判明しており、本Question（差異発生時の運用ルール）が未回答のまま「差異」「不足」等のBusiness Meaningを持つ画面を作ることは危険と判断したため。「機能の要否」自体の分類（D）・状態は変わらず未回答のまま。詳細は`requirements-coverage-and-remaining-gap-audit.md` 20章#11・21.1章参照。

### L-4. G-SYS→倉庫 変更情報連携の自動化範囲

- **Question**: 「2026/08/26打ち合わせSlide 13の要望どおり、入荷数量・入荷予定日の変更情報を倉庫・物流側へ連携する場合、どこまで自動化すべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-warehouse-logistics-logizero-reverse-engineering 2章・18章Option F

### L-5a/L-5b. Logizero/Tempostar連携方式

- **L-5a（現在の事実）**: 「Logizero/Tempostar在庫連携において、Selenium方式（`InvLogizeroStkDownloadBatch`等）とSFTP方式（`MIMOSA_InvLogizeroStkDownloadBatch`等）のどちらが現行の本番運用経路か。」　**7-D2分類: B（Ernest Q35）**　**Timing**: C　**Blocker**: LATER
- **L-5b（将来の決定）**: 「連携方式（Selenium/SFTP/API）を将来どう整理・統一すべきか。」　**7-D2分類: D**　**Timing**: C　**Blocker**: LATER
- **Source**: legacy-warehouse-logistics-logizero-reverse-engineering 10章・12章・18章Option E

---

## 13. Appendix I: Phase 0/0.5由来の未決事項

Theme A-Hに自然に収まらない、Prototype UI/計算仕様の細部（6項目）。いずれもPrototypeは暫定値で正常に動作しており、9/17デモの成立を妨げない。**全項目 7-D2分類: D（Gulliver Future Decision、LATER）** — Portal UI・計算方針そのものの採否であり、Ernestの技術保守範囲でもGulliverの現行業務運用でもなく、純粋にPortalの仕様として将来決めればよい事項のため。

- ~~Requested Deliveryの必須/任意~~ → **A-7へ格上げ・移動済み（Phase 7-H）**。Source監査の結果、単なるPortal UI仕様の話ではなく、Legacy Official PO Importの実際の必須制約（`PrOfficialPoImportBatch`）に根ざした論点であることが判明したため、Theme A（PO番号・Official PO）側で詳細なFact/Questionとして再整理した。Timingも当初のLATERからIMPORTANTへ引き上げている。5章A-7参照。
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
| A-7 | PrOfficialPoImportBatch.java／PrInitialPoImportBatch.java／official-po-integration-detailed-design#4（Phase 7-H新規。旧Appendix Iの「Requested Deliveryの必須/任意」を統合） |
| B-1〜B-6 | target-production#2,#3／7-A#1／role-approval-implementation／supplier-response-revision-workflow |
| B-7〜B-14 | Phase 7-E PortalUser仕様調査（本Doc初出、他Docからの統合ではない）／target-production17章（B-12,B-13,B-14） |
| C-1〜C-9 | supplier-contact-mail-template-foundation（8項目）／target-production#11,#13／7-A#6 |
| C-10a/C-10b | Phase 7-H Source監査／ernest-current-operation-question-sheet Q14-Q17（Phase 7-J追加） |
| D-1〜D-9 | target-production#4,#6,#9,#10／supplier-response-revision-workflow／requirements27.4／production-ux-workflow-redesign15.4 |
| E-1〜E-7 | target-production#7,#8／7-A#5／fulfillment-follow-up-foundation |
| F-1〜F-5 | target-production#12／excel-legacy-concurrency-control／official-po-integration-detailed-design17章 |
| G-1〜G-3 | target-production13章／fulfillment-follow-up-foundation／role-approval-implementation |
| H-1,H-2 | 指示14章／official-po-integration-detailed-design#9,#10 |
| I-1〜I-12 | target-price-change-workflow 17章PC-1〜PC-14／legacy-price-change-reverse-engineering／ernest-current-operation-question-sheet Q18-Q23（Phase 8-A追加） |
| J-1a〜J-6 | legacy-stock-sales-data-reverse-engineering 16章／ernest-current-operation-question-sheet Q24-Q28（Phase 8-C追加） |
| K-1〜K-5b | legacy-invoice-purchase-sales-gross-profit-reverse-engineering 18章／ernest-current-operation-question-sheet Q29-Q33（Phase 8-D追加） |
| K-6a〜K-6b | legacy-invoice-purchase-sales-gross-profit-reverse-engineering 23章（Gate Audit）／ernest-current-operation-question-sheet Q34（Phase 8-E追加） |
| L-1〜L-5b | legacy-warehouse-logistics-logizero-reverse-engineering 21章／ernest-current-operation-question-sheet Q35（Phase 8-F追加） |
| Appendix I | requirements27.4 |

---

## 15. まとめ（Phase 7-D2反映後）

### 15.1 Phase 7-D時点（Timing/Blocker軸、47項目・分割前）

- 全47項目（Theme A-H）＋Appendix I 7項目 ＝ 計54項目。Timing=A（9/17前必須）は11項目、Blocker=BLOCKERは9項目。

### 15.2 Phase 7-D2時点＋Phase 7-E/7-H/7-J/8-A/8-C/8-D/8-E/8-F追加後（4分類軸、110項目）

| 分類 | 件数 | 質問先 |
|---|---|---|
| **A. SOURCE CONFIRMED** | **0** | なし（詳細は15.3参照） |
| **B. PHASE1 / ERNEST CONFIRM** | **22**（Phase 7-D2時点13 ＋ Phase 7-J追加分C-10aの1 ＋ Phase 8-A追加分I-6a/I-8a/I-11aの3 ＋ Phase 8-C追加分J-1a/J-2の2 ＋ Phase 8-D追加分K-5aの1 ＋ Phase 8-E追加分K-6aの1 ＋ Phase 8-F追加分L-5aの1、Ernest Q14-Q35に対応） | `docs/ernest-current-operation-question-sheet.md` |
| **C. GULLIVER CURRENT OPERATION CONFIRM** | **8** | Gulliver（現行業務の事実確認） |
| **D. GULLIVER FUTURE DECISION** | **80**（Phase 7-D2時点43 ＋ Phase 7-E追加分B-7〜B-14の8 ＋ Phase 7-J追加分C-10bの1 ＋ Phase 8-A追加分I-1,2,3,4,5,6b,7,8b,9,10,11b,12の12 ＋ Phase 8-C追加分J-1b,3,4,5,6の5 ＋ Phase 8-D追加分K-1,2,3,4,5bの5 ＋ Phase 8-E追加分K-6bの1 ＋ Phase 8-F追加分L-1,2,3,4,5bの5。Phase 7-Hで旧Appendix Iの1項目をA-7として再整理したのはD内での移動のため総数は変わらない） | Gulliver（将来方針の意思決定） |
| **合計** | **110** | |

### 15.3 なぜ「A. SOURCE CONFIRMED」が0件なのか

Phase 7-Dの時点で「Sourceで既に確定していることは質問に戻さない」という原則を適用済みだったため（1章）、この47項目＋Appendix I自体が、そもそもSourceで解決できなかった残りである。したがって7-D2で改めて「Sourceだけで完全に解決する」項目を探しても新たに見つからなかった（0件）。ただし各項目の"Current G-SYS Fact"欄には、Source Confirmedな前提事実（例: A-3の「同一PO No.への再Import機構自体の存在」、B-6の「現行は誰でも訂正可能」、C-1の「SYS_SEND_MAILキューの存在」）が引き続き記載されており、これらは質問化されていない。**「0件」はSource監査の手抜きではなく、Phase 7-Dの設計原則が正しく機能していたことの裏付け**と解釈する。

### 15.4 Ernest確認で解決が期待される22項目（B分類）の内訳

| Theme | 件数 | Ernest Sheet番号 |
|---|---|---|
| PO番号関連（採番規則そのもの） | 1 | Q1 |
| Official PO Import運用関連 | 6 | Q2, Q3, Q4, Q5, Q6, Q7 |
| Mail関連 | 4 | Q8, Q9, Q10, Q11 |
| Cancellation関連 | 1 | Q12 |
| Infrastructure関連 | 1 | Q13 |
| EDI発注関連（Phase 7-J追加） | 1 | C-10a（Ernest Sheet Q14-Q17として4項目を展開、Decision Package上は1トピック=C-10aとして計上） |
| 価格変更関連（Phase 8-A追加） | 3 | I-6a（Ernest Q22）、I-8a（Ernest Q20）、I-11a（Ernest Q21）。Ernest SheetにはQ18/Q19/Q23も存在するが、Price Change Target Design 17章のCUSTOMER REVIEW項目と直接対応しない技術確認事項のためDecision Package上はトピック計上しない |
| 在庫・販売実績データ更新関連（Phase 8-C追加） | 2 | J-1a（Ernest Sheet Q24/Q25/Q28として3項目を展開）、J-2（Ernest Sheet Q26/Q27として2項目を展開） |
| 請求・仕入・売上・粗利関連（Phase 8-D追加） | 1 | K-5a（Ernest Q29）。Ernest SheetにはQ30/Q31/Q32/Q33も存在するが、Decision Package上のCUSTOMER REVIEW項目と直接対応しない詳細確認事項のためトピック計上しない |
| 請求・仕入・売上・粗利関連（Phase 8-E追加、Gate Audit） | 1 | K-6a（Ernest Q34） |
| 倉庫・物流・Logizero関連（Phase 8-F追加） | 1 | L-5a（Ernest Q35） |

優先度: P1（7-C2B/7-C4のBlocker）6件、P2（9/17前に把握したい）9件（Q3,Q9,Q10,Q11,Q12に加えPhase 7-J追加のQ14-Q17）、P3（本番設計まででよい）20件（Q5,Q6に加えPhase 8-A追加のQ18-Q23、Phase 8-C追加のQ24-Q28、Phase 8-D追加のQ29-Q33、Phase 8-E追加のQ34、Phase 8-F追加のQ35）。

### 15.5 Ernest回答で「消える」可能性があるGulliver質問

`docs/customer-review-question-sheet.md`（Ernest確認前Draft）には、上記20項目（B分類）が現時点でGulliver向け質問としてそのまま含まれている（うちC-10a・J-1aはそれぞれ複数論点をまとめた1トピックとして🟣マーク）。Ernestの回答が得られ次第、この**20項目はGulliver向け質問票から削除、または「確認済み事実の共有」に置き換える**（Ernestが確定的な回答をできなかった場合のみ、C分類＝Gulliver Current Operationとして質問票に残る）。したがって、Ernest回答によって最終的にGulliverへ聞く質問がゼロになる可能性がある項目は**最大20件**。

### 15.6 Ernest確認後も確実にGulliver判断が必要な項目

- **C. GULLIVER CURRENT OPERATION CONFIRM（8件）**: Ernestが技術保守担当として確定できない、Gulliver社の業務運用・組織構造に関する事実（例: メーカー担当者のBrand別割当、複数宛先送信の実際の運用、欠品/廃番の業務定義）。Ernestに聞いても解決しない可能性が高いため、最初からGulliver向けとして扱う。
- **D. GULLIVER FUTURE DECISION（80件、うちAppendix I 6件＋Theme A側のA-7として再整理された1件、Phase 7-E追加分B-7〜B-14の8件、Phase 7-J追加分C-10bの1件、Phase 8-A追加分I-1〜I-12（b分含む）の12件、Phase 8-C追加分J-1b/J-3/J-4/J-5/J-6の5件、Phase 8-D追加分K-1/K-2/K-3/K-4/K-5bの5件、Phase 8-E追加分K-6bの1件、Phase 8-F追加分L-1/L-2/L-3/L-4/L-5bの5件を含む）**: Portal導入後の新しい業務ルール・権限・運用方針そのものであり、これは「事実確認」ではなく「意思決定」であるため、Ernestが何を答えても消えない。Gulliverの最終承認が必須。
- **合計 88件が、Ernest確認後も確実にGulliverへの確認が必要な項目数。**

### 15.7 「件数を減らすこと」自体を目的にしない

上記のとおり、B分類22件をEarnestへ振り分けても、Gulliverへの質問自体は最大88件（C:8＋D:80）残る。これは「件数を無理に減らした」結果ではなく、**「Phase1として調べれば分かることをGulliverに聞かない」**という本Phaseの目的を優先した結果である。件数の多寡よりも、各質問が正しい相手（Ernest / Gulliver Current Operation / Gulliver Future Decision）に向いていることを優先した。

---

## 16. 案件全体のModule/Option構成方針（Phase 8-B追加、Phase 8-Cで#13-15追加、Phase 8-Dで#10更新+#16-18追加、Phase 8-Eで#10更新、Phase 8-Fで#19-21追加、Phase 8-Gで#19実装状況更新、Phase 8-Hで#13実装状況更新、共通前提）

**この章は、以降のすべてのPhaseで維持する共通前提である。** 最終提案は「全機能を一括導入する固定Scope」を前提とせず、機能領域（Module/Option）ごとにGulliver社が採否を選択できる提案構造を目指す。最終Scopeは `Customer Requirement × Priority × Dependency × Implementation Cost × Customer Budget` によって決まる想定であり、**現時点で価格・工数を推測せず、正式なPackage構成も独自に決定しない**。目的は、機能境界とDependencyを先に明確にしておくことである。

### 16.1 識別する8項目

各機能領域について、判明している範囲で以下を識別する。未確定のものは空欄または「未確定」とし、推測で埋めない。

1. **機能単位（Module/Option名）**
2. **必須Core / Optional**（Ordering自体が導入されない限りPortalの意味が無い、等の必須性）
3. **他機能へのDependency**（この機能が無いと動かない機能があるか）
4. **単独導入可能性**（他のOptionを採らずにこれ単体で価値が出るか）
5. **実装難易度**（Phase 7/8時点のReverse Engineering・Target Design結果からの相対評価。工数の絶対値は出さない）
6. **Legacyへの影響**（Legacy側の既存Batch/Table/画面をどこまで踏襲・依存するか）
7. **External System依存**（Tempostar/Logizero/EC各モール等）
8. **Customer Decision依存**（Gulliver社のBusiness Rule決定を待つ必要があるか、`docs/customer-review-question-sheet.md`の該当項目）

### 16.2 現時点で識別済みの機能領域

| # | 機能領域（Module/Option） | Core/Optional | 他機能への主なDependency | 単独導入可能性 | 実装難易度（相対） | Legacy影響 | External System依存 | Customer Decision依存 |
|---|---|---|---|---|---|---|---|---|
| 1 | Online Ordering（発注） | **Core**（本案件の起点機能） | — | 可（他Moduleの前提になる） | 実装済み（7-C系） | 高（TR_PO/TR_PO_DTL/TR_ARR等、Delete&Recreate方式） | Logizero（倉庫連携は別途） | `customer-review-question-sheet.md` A/B/D/F/G章 |
| 2 | Approval（承認Workflow） | Optional | Ordering | 不可（Orderingに従属する機能） | 実装済み（7-C系） | 低（Portal内で完結） | 無 | B章（承認単位・階層等） |
| 3 | Supplier Communication / Email | Optional（Ordering運用上は実質Core相当） | Ordering | 不可 | 実装済み（7-C系） | 中（SYS_SEND_MAILキュー活用） | 無 | C章 |
| 4 | EDI（Supplier発注Channel） | Optional | Ordering、Supplier Communication | 不可 | Foundationのみ実装済み（7-H）、実EDI連携は未着手 | 低（現状はChannel記録のみ、実連携は別） | メーカー側EDI System（詳細未確認） | C-10a/C-10b |
| 5 | Supplier Response / Difference Management | Optional（Ordering運用上は実質Core相当） | Ordering | 不可 | 実装済み（7-C系） | 中 | 無 | D章 |
| 6 | Fulfillment / Follow-up | Optional | Ordering | 一部可（納品追跡のみなら理論上は独立させ得るが、現設計はOrdering前提） | 実装済み（7-C7A） | 中（TR_ARR等参照） | 無 | E章 |
| 7 | Price Change（Foundation） | Optional | 無（Ordering非依存で成立） | **可**（Ordering非導入でも単独価値あり） | Foundation実装中（8-B） | 中（MS_ITEM/MS_ITEM_GRP、Excel Import Pipeline） | 無（Tempostar等は対象外） | Theme I（I-1〜I-12） |
| 8 | Future Price / Scheduled Price | Optional | **Price Change（Technical Dependency: Change Set構造に依存）** | 不可（Price Change無しに成立しない） | 未着手（Customer Review待ち） | 低（Legacyにこの概念自体が無いためPortal内で完結） | 無 | I-2, I-3 |
| 9 | Margin / Loss Warning | Optional | **Price Change（Technical Dependency: 価格入力画面に付随）** | 不可 | 計算ロジックの土台のみ実装中（8-B）、Warning Action自体は未着手 | 低（`Formula.java`ロジック参照のみ） | 無 | I-4 |
| 10 | Invoice / Purchase / Sales / Gross Profit（総称） | Optional | **Phase 8-DでRE完了、Phase 8-EでImplementation Gate Audit実施** | 一部可（#16-18参照） | Phase 8-DでReverse Engineering完了。**Phase 8-Eで「仕入確認」Foundation実装をGate: STOPと判定（Gross Amount定義がSourceから一意に確定できないため、Docs Onlyで停止）** | 低（READ ONLYの範囲は既存Table参照のみ） | 間接（Sales金額はTempostar由来だが現状永続化されていない） | Theme K（K-1〜K-6b） |
| 11 | Warehouse / Logistics連携（総称、旧行） | Optional | 無 | 可 | **Phase 8-FでRE完了、Phase 8-GでFoundation一部実装済み — 詳細は#19-21（Phase 8-F/G/H追記）へ分解済み。本行は履歴として残すのみで、以後は#19-21を参照する。** | Logizero | Logizero | Theme L |
| 12 | External System Integration（EC各モール・Tempostar等） | Optional | 無（Price Changeと概念的に隣接するが、Portal側は現状未実装） | 可 | 未着手 | 高（Selenium画面操作等、Legacy側の実装が特殊） | Tempostar、Rakuten、Yahoo、Amazon、Qoo10、Ponpare、Wowma | 未整理 |
| 13 | Stock/Sales Data Update（表示改善） | Optional | 無（Ordering非依存で成立、10章 legacy-stock-sales-data-reverse-engineering） | 可 | **Current Snapshot VisibilityはPhase 8-HでFoundation実装済み**（在庫・販売確認画面、SOLD_QTY/STK_QTY/Open PO/Open Arrivalの横断表示。History蓄積・Trendは未実装のまま） | 低（READ ONLY） | 無 | J-1a/J-1b |
| 14 | Stock/Sales Data Update（Sales History蓄積、Portal側） | Optional | 13とは独立に単独導入可 | 可 | 中（新規蓄積の仕組みが必要） | 低（READ ONLY、PortalがHistoryのみ新規保持） | 間接（SOLD_QTYがTempostar由来） | J-3/J-4/J-6 |
| 15 | Stock/Sales Data Update（Trend可視化・calc4改善・Alert改善） | Optional | **14（Sales History蓄積）へのTechnical Dependency**、一部D-5（欠品/長期欠品定義）確定へのBusiness/Estimate Dependency | 不可（14の完成、またはD-5確定が前提） | 中〜高（calc4改変は特に慎重な検証が必要） | 低〜中 | 無 | J-5、D-5 |
| 16 | Invoice/Purchase/Sales/Gross Profit（Purchase可視化・仕入確認・理論Margin表示） | Optional | 無（Ordering非依存、Price ChangeのMarginCalculatorを再利用可能） | 可（ただし仕入金額集計＝仕入確認機能自体はGate STOP中、下記参照） | **理論Margin表示部分は低。仕入金額集計（仕入確認）部分はPhase 8-EでGate: STOP**（`TR_INV.AMT_TTL`/`TR_INV_DTL.AMT_LINE`がBatch間で非等価な計算式のため、Gross Amount定義がK-6b確定まで実装不可） | 低（READ ONLY） | 無 | K-5a/K-5b/K-6a/K-6b |
| 17 | Invoice/Purchase/Sales/Gross Profit（PO/Invoice Qty比較） | Optional | 16とは独立に単独導入可（7章のJoinのみで完結） | 可 | 低（READ ONLY、Join自体はSource Confirmed） | 中（比較目的の確定が必要） | 無 | K-1 |
| 18 | Invoice/Purchase/Sales/Gross Profit（差異管理・Sales Amount蓄積・実績粗利・Dashboard） | Optional | **Sales Amount蓄積へのTechnical Dependency（実績粗利側）**、差異管理Business Rule確定へのBusiness/Estimate Dependency。Stock/Sales Data UpdateのOption B（14番）と同じTempostar CSV拡張ポイントを共有し得る（16.3章参照） | 不可（Sales Amount蓄積の完成、または差異管理Business Rule確定が前提） | 中〜高 | 高（複数の未確定Business Rule次第） | 間接（Tempostar由来） | K-2/K-3/K-4 |
| 19 | Warehouse/Logistics（Arrival Visibility・Warehouse Stock Visibility・Discrepancy Visibility） | Optional | 無（Ordering非依存で成立、legacy-warehouse-logistics-logizero-reverse-engineering 14章） | 可 | **Arrival Visibility・Warehouse Stock VisibilityはPhase 8-GでFoundation実装済み**（同Document 27章）。Discrepancy Visibilityは未実装のまま | 間接（LogizeroがG-SYSへ供給するDataのFreshness次第） | Theme L（L-1/L-2/L-3） |
| 20 | Warehouse/Logistics（Logizero/Tempostar連携方式見直し） | Optional | **19（可観測性向上）とは独立、19-1の完成後に着手する方が自然** | 不可（External Specification確認が前提） | 高（Selenium/SFTP/API方式自体の変更） | 中 | 大（Logizero/Tempostar双方のVendor仕様確認が必須） | L-5a/L-5b |
| 21 | Warehouse/Logistics（G-SYS→倉庫 双方向連携） | Optional | **19・20の完成後を推奨** | 不可 | 高 | 高 | 大 | L-4 |

**Business/Estimate上のDependencyとTechnical Dependencyの区別**（指示による）:
- 例: 「Future Price / Scheduled Price」は**Technical Dependency**として Price Change の Change Set 構造に依存する（Change Setという入れ物が無ければ予約日時を持たせる先が無い）。一方、Approval・Fulfillment等が Ordering に依存するのは主として**Business/Estimate上のDependency**（Orderingという業務プロセスが無ければ承認や納品追跡という概念自体が発生しない）であり、Technicalには疎結合な実装（別Table・別Service）を既に採っている（7-C系のPortalOrderRevision/AuditEvent等はOrdering専用Tableであり、Approval機能を外してもOrdering自体のCore Tableは壊れない設計）。
- この区別を今後のDocumentでも維持する。Estimate上「OptionalだからOrderingに依存する」と書かれていても、それが実装上も密結合であるとは限らない。

### 16.3 Software Architecture上の方針（指示による共通原則）

- **見積上のOption分割とSoftware Module分割は1対1で一致させない。** 見積は「Gulliverが選べる機能単位」として整理し、共通基盤（例: PortalUser/Role、AuditEvent、Legacy READ ONLY Adapter、Toast等の共通UI部品）は技術的に妥当な形で共通化する。
- **見積Option化を理由にした不自然な実装分割はしない。** 例えばPrice ChangeとOrderingが将来同一のAudit Event基盤やLegacy Adapter層を共有すること自体は問題なく、Option分割のためにCode/DB/APIを不必要に分離しない（Phase 8-BのPrice Change ForegroundでもAuditEvent enumやPortalUser参照は既存の仕組みをそのまま再利用する、18章参照）。
- **一方でCore機能への不要な密結合も避ける。** あるOptional機能（例: EDI、Future Price）を採用しなかった場合に、Core機能（Ordering、Price Change本体）まで動作しなくなるような設計は避ける。Phase 8-BのChange Set構造も、Future Price/Approvalを将来追加しない場合でもDRAFT→SUBMITTED→APPLIED/FAILEDという最小構成だけで単独価値を持つように設計する（`target-price-change-workflow.md` 6章の最小State Skeletonと整合）。

### 16.4 未確定の明示

本章の実装難易度・Dependency評価は、Phase 7/8時点でReverse Engineering/Target Designが完了した機能領域（Ordering、Price Change、Phase 8-CでStock/Sales Data Update、**Phase 8-DでInvoice/Purchase/Sales/Gross Profitも追加**）についてのみ確度が高い。Warehouse/Logistics、External System Integrationは依然未調査であり、表内の評価は暫定である。**価格・工数の数値化、正式なPackage名の確定は、本Documentでは一切行わない。**
