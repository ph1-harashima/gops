# Requirements Coverage & Remaining Gap Audit（Phase 8-I）

**Status**: 監査・整理のみ。Frontend/Backend/DB Migration/API変更は0件。Legacy（`phasep-gulliver`）はREAD/Grepのみで一切変更していない。Test実行なし（実装変更が無いため）。

**目的**: 2026/08/26 Gulliver改善要望（`docs/G-Sys_mtg_20260826_02.pptx`全15枚）に対し、Phase 7〜8-Hで実装・調査してきた内容が現在どこまでCoverしているかを横断監査する。個別Phaseの詳細調査（Reverse Engineering）を繰り返さず、既存Documentの集約・突合・優先順位付けに専念する。

---

## 1. Executive Summary

- 2026/08/26打ち合わせで提起された将来テーマ（Slide 1-15）のうち、**Ordering（発注）本体・承認・メーカー通信・Supplier Response・Fulfillmentは実装済み**（Phase 7-A〜7-J）。**Price Change・Stock/Sales Visibility・Arrival Visibility・Warehouse Stock VisibilityはFoundation実装済み**（Phase 8-B/8-H/8-G）。
- **仕入Gross Amount可視化（Slide 12由来）はGate STOPのまま**（Phase 8-E）: Legacy Source上、Invoice金額計算式がBatch間で非等価であり、正しいGross Amount定義をCustomer/Ernestへ確認しない限り安全に実装できない。
- **G-SYS→倉庫連携（Slide 13由来）は未着手のまま**: 既存のLogizero連携は逆方向（倉庫→G-SYS/Tempostar）であり、Slide 13の要望に直接応えるものではない。External Specification（Logizero/Tempostar Vendor仕様）が無いため着手できない。
- 現時点でGulliver社への確認待ち項目は**110項目**（`customer-review-decision-package.md`）、うちErnest（Phase1社内）への確認待ちが**22項目（Ernest Sheet39問中）**。9/17説明後、これらの回答が今後のScope・優先順位を最終的に左右する。
- 本監査の結論として、**次に安全に着手できる開発候補（Top 5）**を21章に整理した。いずれもCustomer回答を待たずに着手可能なREAD ONLY Foundation拡張、またはCross-Cutting Gapの解消である。

---

## 2. Audit Scope

- **監査対象**: Phase 7-A〜8-Hで作成された全Document（`docs/`配下、23件）、および実装済みPortal Source（Backend Controller 21本・DB Migration 16本・Frontend Feature一式）。
- **監査方法**: 全QA Document（Decision Package・Customer Question Sheet・Ernest Sheet）の再読了、全Reverse Engineering Documentの参照、2026/08/26 Meeting Source（全15枚）の再抽出・全文確認、既存Portal Source（Controller/Migration一覧）の確認。
- **対象外**: 新規Source Reverse Engineering（既存調査の再実施はしない）、Frontend/Backend/DB/API実装（本Phaseは監査のみ）、見積金額・人日算出。

---

## 3. Source Hierarchy

指示2章の区分に従い、本Documentが引用するSourceを以下のように厳密に階層化する。**過去Phase（8-C/8-D/8-F）で誤って再導入しかけた「10日遅延」「請求ミス」「請求書管理」「Logizero」「Selenium」等のWritten Source外表現は、本監査でも再導入しない。**

| 区分 | 意味 | 本Documentでの扱い |
|---|---|---|
| **A. Written Requirement** | 2026/08/26 Meeting Source（`G-Sys_mtg_20260826_02.pptx`全15枚）に文字として存在する内容 | 5章以降で「Slide N」として明示引用する場合のみ、この区分として扱う |
| **B. Meetingで確認された方向性** | Written Requirementの中で「将来テーマとして検討する」等、確定合意ではなく方向性のみ確認された内容（Slide 12・13・14が該当） | 「探索段階」「将来テーマ」と明記し、確定要望と混同しない |
| **C. Legacy Source Fact** | `phasep-gulliver`のCode/DB構造から確認できる事実（Phase 7-A〜8-Hの各RE Documentで確認済み） | 「Source Confirmed」として扱う |
| **D. Portal Proposal** | 本エンゲージメントが独自に設計・提案したTarget Design（未実装または実装済みのPortal機能） | 「Target Proposal」「Foundation実装済み」等、実装状況を明記 |
| **E. Customer Review未決定事項** | Gulliver社の意思決定が必要な項目（`customer-review-decision-package.md`に集約済み） | Theme別に13章で突合する |

**2026/08/26 Meeting Source 全15枚の要約（再確認済み、全文はSlide番号で参照）**:

| Slide | 内容要約 | 区分 |
|---|---|---|
| 1 | 打ち合わせ概要（Excel/CSV/メール作業をG-SYSへ集約、発注オンライン化中心） | A |
| 2 | 現在の課題（発注業務の作業負荷、ダウンロード・編集・再反映の繰り返し） | A |
| 3 | 発注業務のオンライン化フロー案（01確認〜09履歴蓄積） | A |
| 4 | 欠品・廃番・長期欠品等の状態表示 | A |
| 5 | 適正発注数量の算出（在庫・販売実績・発注済数量・リードタイム考慮） | A |
| 6 | 在庫・販売実績データ更新タイミング（月替わり後のタイムラグ） | A |
| 7 | 発注後の数量・納期変更 | A |
| 8 | メーカーへの発注書・メール送付 | A |
| 9 | 発注履歴の管理 | A |
| 10 | 権限・承認 | A |
| 11 | 価格変更業務 | A |
| 12 | 発注・請求・売上・仕入データ（**将来的なテーマ**、発注オンライン化とは別枠） | B |
| 13 | 倉庫・物流システムとの連携（**確認していくこととなった**、自動化範囲は未定） | B |
| 14 | 今後の方向性（詳細仕様は未確定、次回9/17提示予定） | A |
| 15 | 次回打ち合わせ予定（9/17） | A |

**確認事項**: 「10日遅延」「請求ミス」「請求書管理」「Logizero」「Selenium」という語句は、全15枚のいずれにも存在しない（Phase 8-C/8-D/8-Fで個別に確認済み、本監査で再確認済み）。これらはLegacy Source調査・口頭補足から得られた事実であり、Written Requirementとして引用する場合は必ず出典をLegacy Source側に明記する。

---

## 4. Status Definition

指示4章のとおり、8分類を採用する。

| # | Status | 意味 |
|---|---|---|
| 1 | **IMPLEMENTED** | 実際にPortalで利用可能（Business Rule確定・Production化は別問題） |
| 2 | **FOUNDATION IMPLEMENTED** | 基盤は実装済みだがCustomer Rule/External Integration等が未確定 |
| 3 | **SOURCE CONFIRMED / NOT IMPLEMENTED** | Legacy事実は確認済みだが未実装 |
| 4 | **CUSTOMER REVIEW WAIT** | GulliverのBusiness Decisionが必要 |
| 5 | **CURRENT OPERATION WAIT** | Ernest等への現行運用確認が必要 |
| 6 | **EXTERNAL SPEC WAIT** | Vendor/外部System仕様確認が必要 |
| 7 | **BLOCKED** | 現時点では安全に進められない（Gate STOP等） |
| 8 | **OUT OF SCOPE** | 現Scopeでは対応しない |

複数条件を持つ項目は「Primary Status（+ Dependency）」の形式で記載する。

---

## 5. Requirement Coverage Matrix

Theme A-Vの22 Themeで整理する（指示のA-Tに加え、既存Decision Package Theme F/Hに対応する2 Themeを追加）。

| Theme | 名称 | Primary Status | 対応する既存Theme（Decision Package） |
|---|---|---|---|
| A | Ordering | **IMPLEMENTED**（Foundation、Production Handoff未着手） | Theme A |
| B | Order Approval | **IMPLEMENTED**（Prototype内） | Theme B（B-1〜B-6） |
| C | Manufacturer Communication | **IMPLEMENTED**（Demo Send、実送信未着手） | Theme C（C-1〜C-9） |
| D | Official PO | **FOUNDATION IMPLEMENTED**（Preflightまで、実Handoff未着手） | Theme A（A-1〜A-7） |
| E | EDI | **FOUNDATION IMPLEMENTED**（Channel記録のみ） | Theme C（C-10a/b） |
| F | Supplier Response / Agreement | **IMPLEMENTED** | Theme D |
| G | Fulfillment / Follow-up | **IMPLEMENTED** | Theme E |
| H | Price Change | **FOUNDATION IMPLEMENTED** | Theme I |
| I | Stock / Sales Visibility | **FOUNDATION IMPLEMENTED**（Phase 8-H） | Theme J（J-1a/J-1b） |
| J | Stock / Sales History / Trend | **CUSTOMER REVIEW WAIT** | Theme J（J-3〜J-6） |
| K | Out of Stock / Long-term Out of Stock | **SOURCE CONFIRMED / NOT IMPLEMENTED**（[PROTOTYPE]判定のみ存在） | Theme D（D-4/D-5） |
| L | Arrival Visibility | **FOUNDATION IMPLEMENTED**（Phase 8-G） | Theme L（L-1） |
| M | Warehouse Stock Visibility | **FOUNDATION IMPLEMENTED**（Phase 8-G） | Theme L（L-2） |
| N | Warehouse / Logistics Integration | **EXTERNAL SPEC WAIT**（一部**BLOCKED**: G-SYS→倉庫方向） | Theme L（L-4/L-5b） |
| O | Purchase / Gross Amount Visual Check | **BLOCKED**（Phase 8-E Gate STOP） | Theme K（K-6a/K-6b） |
| P | Sales Amount / Gross Profit Analysis | **CUSTOMER REVIEW WAIT**（一部**SOURCE CONFIRMED/NOT IMPLEMENTED**: 理論利益率） | Theme K（K-3/K-4） |
| Q | External Tool / System Integration | **EXTERNAL SPEC WAIT** | Theme L・Theme C-10 |
| R | Dashboard / Reporting | **IMPLEMENTED**（KPI Tile）／**OUT OF SCOPE**（横断Reporting） | — |
| S | Audit / History / Traceability | **IMPLEMENTED** | — |
| T | User / Role / Permission | **FOUNDATION IMPLEMENTED**（Role判定のみ、User CRUD未実装） | Theme B（B-7〜B-14） |
| U | Excel / Legacy Concurrency Coexistence | **FOUNDATION IMPLEMENTED**（検出のみ、解消Workflow未確定） | Theme F |
| V | Infrastructure / Environment | **CURRENT OPERATION WAIT** | Theme H |

詳細は6章以降でTheme単位に展開する。

---

## 6. Ordering Coverage

| 機能 | 状況 | Customer依存 | External依存 | Production化残件 |
|---|---|---|---|---|
| Candidate List | **IMPLEMENTED**（Backend Filter、Frontend全件取得ではない） | 無 | 無 | 無 |
| Recommended Qty（calc4） | **IMPLEMENTED**（Legacy Formula移植、変更なし） | Appendix I（Warning閾値等） | 無 | 無 |
| Draft作成・編集 | **IMPLEMENTED** | 無 | 無 | 無 |
| Preview | **IMPLEMENTED** | 無 | 無 | 無 |
| Order保存 | **IMPLEMENTED**（Portal DB、`portal_order`/`portal_order_detail`） | 無 | 無 | 無 |
| List / Detail | **IMPLEMENTED** | 無 | 無 | 無 |
| Approval | **IMPLEMENTED**（2段階、Edit-and-Approve） | Theme B（承認単位・階層・代行） | 無 | 無 |
| Revision | **IMPLEMENTED**（Order Revision History） | Theme D（D-2/D-6） | 無 | 無 |
| Manufacturer Send | **FOUNDATION IMPLEMENTED**（Demo Send、実送信は7-C4未着手） | Theme C（C-1b/C-2/C-6b） | 無（既存`SYS_SEND_MAIL`再利用予定） | **7-C4: 実メール送信未実装** |
| Official PO Integration | **FOUNDATION IMPLEMENTED**（Preflight=7-C2Aまで） | Theme A（A-1b/A-4b） | 無 | **7-C2B: 実Handoff（File生成・投入）未実装** |
| EDI | **FOUNDATION IMPLEMENTED**（Channel記録のみ） | Theme C（C-10b） | **EDI仕様（Q16回答待ち）** | 実EDI連携（File/API）未着手 |
| Supplier Response | **IMPLEMENTED** | Theme D | 無 | 無 |
| Difference acknowledgement | **IMPLEMENTED**（Attention機構） | Theme D（D-1） | 無 | 無 |
| Agreement | **IMPLEMENTED**（AGREED、Reopen） | Theme D（D-3） | 無 | 無 |
| Fulfillment | **IMPLEMENTED**（Ordered/Invoiced/Stock-in/Outstanding、Credit Netting込み） | Theme E（E-1、業務定義） | 無 | 無 |
| Follow-up | **IMPLEMENTED**（起票→Mail Preview→Close→再発注） | Theme E（E-2〜E-7） | 無 | 実送信未着手（Manufacturer Sendと同一） |
| Audit History | **IMPLEMENTED**（Audit Timeline、全操作記録） | 無 | 無 | 無 |
| Legacy Concurrency | **FOUNDATION IMPLEMENTED**（Baseline記録→検出→Structured Diff） | Theme F（F-1〜F-3、解消権限・Gate化） | 無 | Conflict自動解消は未実装（意図的、F-1でC自動解決を提案していない） |
| Role/Permission | **FOUNDATION IMPLEMENTED**（OPERATOR/ADMIN 2 Role） | Theme B（B-7〜B-14、User管理） | 無 | User CRUD UI/API自体が未実装（Flyway seedのみ） |

**結論**: Orderingの中核（Candidate→Draft→Approval→Send→Response→Fulfillment→Follow-up）はPrototype/Demoとして完結している。残る大物Gapは(1) 7-C2B 実Handoff、(2) 7-C4 実メール送信、(3) User Management UI/API — いずれもCustomer Decision（Theme A/C/B）が前提。

---

## 7. Price Change Coverage

| 機能 | 状況 | Source |
|---|---|---|
| Current Price | **IMPLEMENTED**（`MS_ITEM.PRC_SELL_W_TAX`読取） | Phase 8-A/8-B |
| Current Cost | **IMPLEMENTED**（`MS_ITEM.COST_THIS_MONTH_AVG`読取） | 同上 |
| Margin | **IMPLEMENTED**（`MarginCalculator`、Legacy `Formula.PROFIT_RATE_SELL`移植） | 同上 |
| Change Set | **IMPLEMENTED**（DRAFT状態のみ到達可能、他Statusは未実装） | Phase 8-B |
| SKU / Item Group選択 | **IMPLEMENTED**（個別SKU・Item Group一括） | 同上 |
| Baseline Snapshot | **IMPLEMENTED**（Concurrency検知用） | 同上 |
| Concurrency Check | **IMPLEMENTED**（UNCHANGED/CHANGED/NOT_AVAILABLE Chip） | 同上 |
| Approval | **SOURCE CONFIRMED / NOT IMPLEMENTED**（Status enum定義のみ、Submit/Apply API無し） | I-6b待ち |
| Future Effective Date | **CUSTOMER REVIEW WAIT**（未着手） | I-2/I-3 |
| Scheduled Change | **CUSTOMER REVIEW WAIT**（未着手） | I-2/I-3 |
| Loss Warning | **OUT OF SCOPE**（意図的に未実装、Threshold/Warning Rule追加禁止） | I-4 |
| Bulk Change（Brand単位） | **CUSTOMER REVIEW WAIT**（現状Item Group単位のみ実装） | I-1/I-11a/I-11b |
| Price History | **SOURCE CONFIRMED / NOT IMPLEMENTED**（Audit Trailはあるが正式History機能ではない） | I-5 |
| G-SYS Apply | **BLOCKED相当**（実装ゼロ、Statusは`DRAFT`のみ到達可能） | I-6b・Gulliver Apply方式決定待ち |
| Excel Artifact / Import Integration | **SOURCE CONFIRMED / NOT IMPLEMENTED** | Q18/Q19（Ernest） |

**結論**: Price ChangeはFoundation止まり（作成・シミュレーションのみ）。「実際に価格をG-SYSへ反映する」機能そのものが未着手であり、9章の「Demo=Production Readyと判定しない」原則が最も強く当てはまる領域。

---

## 8. Stock / Sales Coverage

**Current Snapshotと Historical/Analytical Functionを明確に分離する（指示の指示どおり）。**

### 8.1 Current Snapshot（Phase 8-C/8-Hで実装済み）

| 機能 | 状況 |
|---|---|
| Current Stock | **IMPLEMENTED**（`MS_STK`'XX'集約行、Order Candidate List/SKU Detail/Stock-Sales画面で共通利用） |
| Current Month Sales Qty | **IMPLEMENTED**（`SOLD_QTY`、当月累計として正しくラベル付け・Tooltip表示） |
| Open PO | **IMPLEMENTED** |
| Open Arrival | **IMPLEMENTED** |
| Update Timestamp | **IMPLEMENTED**（「G-SYSデータ更新日時」中立表現） |
| Warehouse Stock（per-warehouse） | **IMPLEMENTED**（Phase 8-G、Current Stockとは別Query・別画面） |

### 8.2 Historical / Analytical Function（未実装、いずれもCustomer Decision前提）

| 機能 | 状況 | 依存 |
|---|---|---|
| Sales History | **SOURCE CONFIRMED / NOT IMPLEMENTED**（Legacyに保持されておらず、Portal新規蓄積が必要） | J-6/K-3（SoR決定） |
| Stock History | **SOURCE CONFIRMED / NOT IMPLEMENTED** | J-3〜J-5 |
| Trend | **BLOCKED相当**（History Persistenceが技術的前提、18章参照） | Sales/Stock History実装後 |
| Forecast | **OUT OF SCOPE**（Written Requirementに存在せず） | — |
| Recommended Qty improvement | **CUSTOMER REVIEW WAIT**（calc4へのTrend組込み） | J-5 |
| Out of Stock | **SOURCE CONFIRMED / NOT IMPLEMENTED**（[PROTOTYPE]判定`currentStock==0`のみ、正式Business Rule未確定） | D-4/D-5 |
| Long-term Out of Stock | 同上（`currentStock==0 && openPo==0`） | D-4/D-5 |
| Alert | **OUT OF SCOPE**（Business Alert Rule追加禁止の指示が継続） | K-2/L-3 |
| Auto Reorder | **OUT OF SCOPE**（Written Requirementに存在せず、Slide 5でも「担当者が確認・変更」と明記） | — |

**結論**: SnapshotはFoundation完了。Historical/Analytical系は全てSales/Stock History Persistenceという共通Technical Dependencyを持ち、かつSoR責任分担（J-6）というCustomer Decisionが前提のため、まとめて足止めされている。

---

## 9. Arrival / Warehouse Coverage

| 機能 | 状況 |
|---|---|
| PO → Invoice → BL → Arrival → Stock In | **IMPLEMENTED**（Arrival Detail画面でSKU別内訳、Credit Netting込み） |
| Arrival Search | **IMPLEMENTED**（Backend Filter: Supplier/Brand/PO/Invoice/SKU/日付） |
| Warehouse Stock | **IMPLEMENTED**（WH_CD別一覧、Backend Pagination） |
| Per Warehouse Qty | **IMPLEMENTED**（SKU DetailからDrawerで全倉庫Qty表示） |
| Arrival vs Warehouse Trace | **OUT OF SCOPE（意図的、恒久制約）**（`TR_ARR`に`wh_cd`列が存在しないというSource Factに基づく。Schema/Query両方で非Join） |
| G-SYS → Warehouse | **BLOCKED**（既存連携が存在しない。Slide 13要望への回答は未着手） |
| Warehouse → G-SYS | **IMPLEMENTED（Legacy側、Portal未介在）**（Logizero→`MS_STK.STK_QTY`、Portalは READ ONLY参照のみ） |
| Synchronization Monitoring | **SOURCE CONFIRMED / NOT IMPLEMENTED**（`StkQtyDiscrepancyCheckerService`はLegacyに実装済みだがPortal未反映＝Discrepancy Visibility、Foundation Classification A） |
| Error Recovery | **OUT OF SCOPE**（現状SourceにResolution Workflow自体が存在せず、Business Rule未確定） |
| Selenium modernization | **EXTERNAL SPEC WAIT**（Logizero側は部分的にSFTP化実装済み、Tempostar側は代替なし） |
| API / SFTP integration | **EXTERNAL SPEC WAIT**（Logizero/Tempostar Vendor仕様確認が前提） |

**結論**: Arrival/WarehouseはVisibility三点セット（Arrival・Warehouse Stock・Discrepancy）のうち2つが完了、Discrepancy Visibilityのみ未着手（21章のTop候補）。ArrivalとWarehouse Stockを直接結合できないというSource Factは、Schema設計（`tr_arr`に`wh_cd`列を追加しない）でも構造的に担保されている。

---

## 10. Purchase / Gross Amount Coverage

**本領域は「Invoice Management」ではない**（Phase 8-E Section 0で確定済みのCustomer Requirement）。目的は、担当者が手元の請求書を目視確認する際にG-SYS側のGross Amountを参考表示することであり、Invoice保管・OCR・自動突合は行わない。

| 機能 | 状況 |
|---|---|
| Purchase Visibility | **SOURCE CONFIRMED / NOT IMPLEMENTED**（Phase 8-D 17章ではFoundation Classification Aと評価したが、Phase 8-EのGate Audit後は「金額系Fieldを見せる限り同じ不確実性を継承する」ため単独では推奨しない、20章参照） |
| Gross Amount Summary | **BLOCKED**（Phase 8-E Gate STOP、`TR_INV.AMT_TTL`計算式がBatch間で非等価） |
| Breakdown（Brand別/PO別/Invoice別） | **BLOCKED**（同上、Summaryに従属） |
| Detail（明細） | **BLOCKED**（同上） |
| Supplier Invoice storage | **OUT OF SCOPE**（恒久的な禁止事項、Section 0で明記） |
| OCR | **OUT OF SCOPE**（同上） |
| Auto Reconciliation | **OUT OF SCOPE**（同上） |
| Difference judgement | **OUT OF SCOPE**（同上） |
| Additional Cost（`TR_INV_ADD_COST`） | **SOURCE CONFIRMED / NOT IMPLEMENTED**（AMT_TTL/SUB_TTL_AMTへの反映有無が未確認） |
| Tax | **SOURCE CONFIRMED / NOT IMPLEMENTED**（該当Field自体がSource上未発見） |
| Credit | **SOURCE CONFIRMED**（Netting機構は確認済み、Gross Amount計算への統合方法が未確定） |
| Gross Amount定義 | **CUSTOMER REVIEW WAIT + CURRENT OPERATION WAIT**（K-6a=Ernest Q34、K-6b=Gulliver Future Decision） |

**結論**: 本領域はK-6a（Ernest確認）・K-6b（Gulliver決定）の**両方**が揃わない限り、Summary/Breakdown/Detailいずれも着手不可（BLOCKED）。19章のTop候補には含めない。

---

## 11. Sales / Gross Profit Coverage

Legacy Source Factを前提に整理する（**勝手にGross Profitが算出可能とはしない**）。

| Source Fact | 内容 | Status |
|---|---|---|
| Purchase Amount系 | `TR_INV.AMT_TTL`/`TR_INV_DTL.AMT_LINE`として存在（ただし10章のとおり計算式が非確定） | SOURCE CONFIRMED（信頼性は要確認） |
| Theoretical Margin | `MsItem.profitRateSell`/`profitRateSale`として存在、Price Change Foundationが既に再利用 | **IMPLEMENTED**（Price Change画面内） |
| Persistent Sales Amount | **存在しない**（Tempostar CSVの金額列は取り込み時に破棄される、Phase 8-C確認済み） | SOURCE CONFIRMED（不存在の確認） |
| Actual Gross Profit | Current SoR無し（計算材料自体が存在しない） | **SOURCE CONFIRMED / NOT IMPLEMENTED**（技術的に計算不能、Sales Amount Persistence実装後のみ着手可能） |

| 機能 | 状況 | Dependency |
|---|---|---|
| Theoretical Margin表示 | **IMPLEMENTED**（Price Change画面のみ、Stock/Sales画面では意図的に非表示） | 無 |
| Sales Amount Persistence | **CUSTOMER REVIEW WAIT**（K-3、SoR責任分担が前提） | Portal DB新規Table追加が必要（現在ゼロ件の前提を破る初のOption） |
| Actual Gross Profit | **BLOCKED（技術的前提欠如）**（Sales Amount Persistence実装完了後のみ着手可能） | Sales Amount Persistence |
| Brand Performance | **OUT OF SCOPE**（Written Requirement上はSlide 12の「将来テーマ」域を出ない、具体設計無し） | Actual Gross Profit |
| Reporting（横断） | **OUT OF SCOPE** | 複数機能の完成後 |

---

## 12. External Integration

| 対象 | 現状 | 方向 |
|---|---|---|
| Logizero（在庫） | Legacy側で稼働中（Selenium/SFTP併存）、Portalは`MS_STK.STK_QTY`をREAD ONLY参照のみ | 倉庫→G-SYS |
| Logizero（Arrival/入荷実績） | 直接連携経路がSource上未確認 | 不明 |
| Tempostar（在庫） | Legacy側で稼働中（Selenium） | 倉庫→Tempostar（G-SYSはBridge） |
| Tempostar（価格） | Legacy側で稼働中（Selenium、Portal Export Artifactとの連携はPhase 8-A Foundationのみ） | G-SYS→Tempostar |
| Tempostar（売上） | Legacy側で稼働中（CSV Import、金額列は破棄） | Tempostar→G-SYS |
| EDI（Supplier発注） | Portal Foundation実装済み（Channel記録のみ）、実連携は0% | G-SYS→Supplier |
| EC各モール（Rakuten/Yahoo/Amazon等） | **未調査**（RE未実施、決定package 16.2章#12「未整理」のまま） | 不明 |

**結論**: External Integrationは軒並みEXTERNAL SPEC WAIT。Vendor仕様が無い限り、Selenium代替やAPI化の検討自体が着手できない（Phase 8-F 12章の結論を継承）。

---

## 13. Customer Review Dependency

`customer-review-decision-package.md`全110項目（Theme A-L、Appendix I）を、Coverage Matrix（5章）のThemeへ突合する。

### 13.1 突合結果サマリ

| Decision Package Theme | 対応するCoverage Theme | 件数 | Blocking対象 |
|---|---|---|---|
| A（PO番号・Official PO） | D（Official PO） | 9 | 7-C2B 実Handoff全体 |
| B（Approval/Permission、User Mgmt含む） | B・T | 14 | Approval正式仕様、User CRUD実装 |
| C（Supplier Communication、EDI含む） | C・E | 13 | 7-C4 実送信、EDI実連携 |
| D（Supplier Response/Revision） | F | 9 | Agreement判定基準の正式化 |
| E（Fulfillment/Follow-up） | G | 7 | 未納定義・再発注ルール正式化 |
| F（Excel Coexistence/Conflict） | U | 5 | Conflict解消Workflow |
| G（Cancellation/Correction） | A（Ordering内） | 3 | Cancellation機能実装 |
| H（Infrastructure/Operation） | V | 2 | 7-C2B/7-C4双方のBlocker |
| I（Price Change） | H | 12 | Approval/Future Price等、Price Change「実際にG-SYSへ反映する」機能全体 |
| J（Stock/Sales Data Update） | I・J | 7 | Historical/Analytical機能全体 |
| K（Invoice/Purchase/Sales/Gross Profit） | O・P | 8 | 仕入確認・実績粗利機能全体 |
| L（Warehouse/Logistics） | L・M・N | 6 | Discrepancy Visibility・G-SYS→倉庫連携 |
| Appendix I | A（Ordering内、UI細部） | 6 | 影響軽微（Prototype UI仕様のみ） |
| **合計** | | **110** | |

### 13.2 重複Question監査

全110項目を再確認した結果、**新たな重複は発見されなかった**。既存の重複回避策（D-5とJ-1a/J-1bの分離、K theme内でのK-1〜K-6bの粒度分離等）は維持されている。

### 13.3 Source Confirmedで解決済みのため質問として残っていないかのAudit

15.3章の記載どおり、Phase 7-D時点で「Sourceで既に確定していることは質問に戻さない」原則が適用済みであり、**A分類（SOURCE CONFIRMED）は0件のまま**である。本監査で新たにSource Confirmedへ格下げできる項目を探索した結果、**該当なし**（Phase 8-Iは新規Source調査を行わないため、Phase 8-Hまでに確認済みの事実の範囲内での判定）。

ただし、**K-1（PO/Invoice Qty比較機能の要否）について、実装状況の記載更新が必要**であることが判明した（下記13.4参照）。

### 13.4 発見事項: K-1の実装状況記載の陳腐化

`customer-review-decision-package.md`のK-1は「PO/Invoice Qty比較機能の要否」をD（Gulliver Future Decision）として記載しているが、**Phase 8-GのArrival Detail画面が、SKU単位でOrdered Qty/Invoice Qty/Stock-In Qtyを既に並列表示している**（差異判定・Diff表示は行っていない）。したがって「機能の要否」自体は依然D分類のままで正しいが、**「並べて見る」という最小限の実現方法は既にFoundation実装済み**という事実が、K-1の記載に反映されていない。21章でDocument更新候補として扱う。

---

## 14. Ernest / Current Operation Dependency

Ernest Sheet全39問（Phase 7-D2〜8-F追加分）を、指示12章のA〜D分類で再監査する。

| 分類 | 意味 | 該当数 |
|---|---|---|
| A. Current Operation確認として適切 | Ernestの技術保守知見で回答可能 | 33 |
| B. Sourceで解決可能（誤分類候補） | 0 |
| C. Gulliver Decisionであるべき（誤分類候補） | 4（Q22, Q30, Q33, Q38 — 下記） |
| D. External Specificationであるべき（誤分類候補） | 2（Q16, Q19 — 下記） |

### 14.1 誤分類候補（根拠付き、未確定・Document未変更）

| Q# | 現在の分類 | 疑義 | 根拠 |
|---|---|---|---|
| Q22 | B（Ernest、価格変更の実施頻度・実施者・承認者） | 実施者・承認者は**組織・業務運用の事実**であり、Ernestの技術保守範囲というよりGulliver社内の人事・体制情報に近い | 既存C-4a/C-7/C-9等、同種の「Gulliver組織内情報」はC分類（Gulliver Current Operation）としている先例と整合しない |
| Q30 | B（Ernest、PO/Invoice単価差異の実例） | 「価格改定・為替調整等でPO単価とInvoice単価が実際に異なる例」は、購買業務の実例であり技術保守知見ではない | Ernestは技術保守担当であり、価格交渉の実態を把握しているとは限らない |
| Q33 | B（Ernest、発注・請求・売上・粗利を一連確認したい要望の具体的利用場面） | 「なぜその情報が必要か」という業務ニーズの言語化はGulliver社自身にしか答えられない | 既存C-9（Mail Template文面の中身）と同種のC分類対象 |
| Q38 | B（Ernest、ETA_WH Email受信後の倉庫側反映実態） | 倉庫側の実際の作業手順はGulliver社の倉庫担当者の業務であり、G-SYS保守担当のErnestが把握しているとは限らない | Ernestが把握していない場合、L-4のGulliver確認へ直接合流する必要がある |
| Q16 | B（Ernest、EDIの実際の発注方式） | 「メーカー側Web Portal手入力／File授受／API連携等」という技術方式の詳細は、Ernestが知らない場合、最終的にはEDI Vendor（メーカー側システム）の仕様確認が必要になる可能性がある | Q16自体はまず現状把握としてErnestに聞く価値があるため、現分類（B）を維持しつつ、「Ernestが未回答の場合はEXTERNAL SPEC WAITへ昇格する」という注記を推奨 |
| Q19 | B（Ernest、Price List Export/Import列フォーマット一致） | 「Export結果をそのままImportに使っているか」という運用実態はErnestに聞けるが、フォーマットの正式仕様自体はG-SYS自身のSourceで確認可能な範囲（Export/Import両Class）であり、本来はSource再調査（RE）で解決すべきだった可能性がある | 優先度が低く（P3）、次回RE時に確認すれば足りる |

**Document修正提案（未実施、21章で判断）**: 上記6件は、いずれも「誤分類」というより「Ernestが答えられない場合の次善の確認先」を明示する注記の追加が適切と判断する。Ernestの回答内容次第で確定的に再分類すべきであり、**回答が得られる前に本Documentが一方的に移動・削除することはしない**（指示12章の「勝手にQuestion削除・移動する場合は根拠をDocumentへ残すこと」を遵守し、本監査では「候補提示」に留める）。

---

## 15. External Specification Dependency

External Specification（Vendor/外部System仕様）が必要な項目を明示的に集約する。**Customer Decision（Gulliver社の意思決定）とは明確に区別する。**

| # | 対象 | 必要な理由 | 現状 |
|---|---|---|---|
| 1 | Logizero API | SFTP以外の非Browser Interfaceの有無・対象範囲（在庫Exportのみか、Arrival等も含むか）が未確認 | 未確認（Phase 8-F 23章） |
| 2 | Logizero SFTP | 現在使用中のSFTP接続が正式契約に基づくものか、拡張利用可能か | 未確認 |
| 3 | Tempostar API | Browser Automation以外の商品/在庫CSV取込手段の有無 | 未確認 |
| 4 | Tempostar non-browser integration | Import画面のフォーマット変更に関するVendor事前通知の有無 | 未確認 |
| 5 | EDI specification | Supplier側EDI Systemの実際の連携方式（Web Portal／File／API） | 未確認（Q16、Ernest確認後もVendor確認が必要になる可能性） |
| 6 | Official PO external operational source | 正式PO Excelの作成元（社内の別部署か、別ツールか）の技術的な出力仕様 | 未確認（A-2、Ernest確認が前提） |

**結論**: 6項目すべてが未着手。Customer（Gulliver）へ確認すべき事項ではなく、Ernest経由でVendor窓口の有無を確認するか、Gulliver社経由でVendorへ確認依頼する必要がある。

---

## 16. Production Readiness Gap

Prototype/Demo FoundationとProduction Readyを区別する。**「Demoで動く」＝「Production Ready」とは判定しない。**

| Theme | Demo Ready | Functional Foundation | Production Integration Pending | Production Security/Config Pending | Operational Monitoring Pending | 外部Credential/仕様 Pending |
|---|---|---|---|---|---|---|
| A. Ordering | ✅ | ✅ | ✅（7-C2B Handoff未実装） | ✅（Legacy書込User未整備） | ✅ | — |
| B. Approval | ✅ | ✅ | — | ✅（承認正式Rule未確定） | — | — |
| C. Manufacturer Communication | ✅ | ✅ | ✅（7-C4実送信未実装） | ✅（SMTP設定未実施） | ✅ | — |
| E. EDI | Partial | ✅（Channel記録のみ） | ✅（実連携ゼロ） | — | — | ✅ |
| H. Price Change | ✅ | ✅ | ✅（G-SYS Apply未実装） | ✅ | — | — |
| I. Stock/Sales Visibility | ✅ | ✅ | — | — | — | — |
| L/M. Arrival/Warehouse Visibility | ✅ | ✅ | — | — | — | — |
| O. Purchase/Gross Amount | — | — | — | — | — | ✅（Gross Amount定義自体が前提） |
| N. Warehouse Integration | — | — | ✅ | — | — | ✅ |
| V. Infrastructure | Partial | — | ✅（Legacy Hosting/Network情報自体が未確認） | ✅ | ✅ | ✅ |

**共通のProduction Gap**（Theme横断）:
- 全Themeで**Legacy書込（実Handoff）は未実装**（本エンゲージメントの絶対制約「Legacy READ ONLY」が継続する限り、Production化には別途正式なWrite経路の設計・合意が必須）。
- **実SMTP送信は未実装**（Manufacturer Send/Follow-up双方が対象）。
- **Production DB接続情報・Secrets管理は未設計**（Demo環境はDocker Compose固定Credential）。
- **Legacy側Hosting/Network情報自体がErnest確認待ち**（H-1/Q13）であり、これが分からない限りProduction接続方式そのものを設計できない。

---

## 17. Cross-Cutting Gap

現Source/Portalに対する具体的なGapのみ整理する（一般論の大量追加はしない）。

| Gap | 該当箇所 | 内容 |
|---|---|---|
| Backend Pagination未適用 | Candidate List・Order History List | Phase 8-G/8-Hで確立した`PageResponse<T>`パターンが、それ以前に実装されたList画面（Candidate List・Order History List）には未適用。現Demo Instance件数では問題化していないが、実データ規模でのScalabilityに懸念。 |
| Search scalability | 同上 | Frontend側で全件取得後Filter（Order History List・Candidate List）を行っている箇所が残存。 |
| User Management UI/API不在 | Theme T | `portal_user`のCRUD API・画面が一切存在しない（Flyway seedのみ）。Business Rule未確定（B-7〜B-14）だが、機能自体の不在は技術的Gapとしても記録すべき。 |
| Discrepancy Detection未活用 | Theme L/M | Legacy側`StkQtyDiscrepancyCheckerService`が既に存在するにもかかわらず、Portalからの可視化が無い（Foundation Classification A、21章のTop候補）。 |
| Dashboard未統合 | Theme R | Phase 8-F/G/Hで追加した3画面（入荷確認・倉庫在庫・在庫販売確認）が、既存Dashboard（KPI Tile）から到達できない。Nav Bar経由のみ。 |
| 実送信/実Handoff機構の不在 | Theme A/C | 7-C2B/7-C4がいずれも未着手のため、「実際に外部へ影響を与える」操作が一切存在しない（Prototype全体の性質として意図的だが、Production化の最大のGap）。 |
| Error Handling/Retry | 全Backend Adapter | Legacy READ ONLY Adapterに明示的なRetry機構は無い（DB接続断時は例外がそのままHTTPエラーへ伝播）。Demo規模では問題化していないが、Production化時は要検討。 |

**意図的に含めなかった項目**: Monitoring/Backup/Deployment/Observability/一般的なSecurity Hardeningは、現Prototype Scope外の一般論であり、具体的なSource/Portal Gapとして特定できていないため本章には含めない（Production Readiness Gap、16章で個別に言及した範囲に留める）。

---

## 18. Technical Dependency Map

Requirement間のTechnical Dependencyを整理する（Business/Estimate Dependencyとは別軸）。

```
Actual Gross Profit
  └─ Technical Dependency → Sales Amount Persistence（Portal DB新規Table）
        └─ Business/Estimate Dependency → K-3 SoR責任分担（Gulliver決定）

Trend（Stock/Sales）
  └─ Technical Dependency → Stock/Sales History Persistence（Portal DB新規Table）
        └─ Business/Estimate Dependency → J-6 SoR責任分担・J-3/J-4参照期間（Gulliver決定）

G-SYS → Warehouse Automation（Slide 13要望）
  └─ Technical Dependency → 新規Write経路の設計（現在Sourceに存在しない）
        └─ External Specification → Logizero/Tempostar Vendor仕様（15章#1-4）
        └─ Business/Estimate Dependency → L-4 自動化範囲（Gulliver決定）

Official PO Production Send（7-C2B）
  └─ Technical Dependency → Excel/PDF生成Artifact仕様の確定
        └─ Current Operation Dependency → A-2（Ernest Q2、実際の作成元確認）
        └─ Business/Estimate Dependency → A-1b/A-4b（Portal採番可否・手作業併存可否）

実メール送信（7-C4）
  └─ Technical Dependency → SMTP接続情報・既存SYS_SEND_MAILキューへの書込許可
        └─ Current Operation Dependency → C-1a（Ernest Q8）
        └─ Business/Estimate Dependency → C-1b/C-2/C-6b（Gulliver決定）

Purchase/Gross Amount Visual Check
  └─ Technical Dependency → Gross Amount定義の一意確定（現在不可能、Batch間非等価）
        └─ Current Operation Dependency → K-6a（Ernest Q34）
        └─ Business/Estimate Dependency → K-6b（Gulliver決定）

Discrepancy Visibility（Warehouse）
  └─ Technical Dependency → 既存StkQtyDiscrepancyCheckerServiceクエリの再利用のみ（新規計算ロジック不要）
        └─ 依存なし（Foundation Classification A、即着手可能）

User Management UI/API
  └─ Technical Dependency → 無し（既存PortalUser Entity/Repositoryを再利用可能）
        └─ Business/Estimate Dependency → B-7〜B-14（8項目全てGulliver決定待ち、着手不可）
```

---

## 19. Option / Estimate Structure

`customer-review-decision-package.md` 16章の方針（Architecture ModuleとEstimate Optionを1:1にしない）を踏襲する。**金額・人日は記載しない。**

| Option | 分類 | Customer Value | Included Functions | Technical Dependency | Business Dependency | 単独導入可能性 | 相対難易度 | Shared Foundation |
|---|---|---|---|---|---|---|---|---|
| 1. Ordering Core | Core | 高（起点機能） | Candidate〜Approval〜Send〜Response〜Fulfillment〜Follow-up | 無 | 承認・Mail等の各Theme決定 | 不可（他Moduleの前提） | 実装済み | AuditEvent, PortalUser, Legacy Adapter |
| 2. EDI連携 | Optional | 中（一部Supplier対応） | Channel記録+将来の実連携 | Ordering Core | C-10a/b | 不可 | 高（実連携は外部仕様依存） | Ordering Coreの一部 |
| 3. Price Change Foundation | Optional | 中〜高 | Draft作成・Margin Preview・Concurrency | 無 | I-1〜I-12（Apply方式等） | 可 | 実装済み（Apply未実装） | Legacy Adapter, AuditEvent |
| 4. Price Change Advanced（Approval/Future/Bulk） | Optional | 高 | Approval Workflow・Future Price予約・Brand一括 | Price Change Foundation | I-1〜I-12全般 | 不可 | 高 | Price Change Foundationの拡張 |
| 5. Stock/Sales Visibility | Optional | 中〜高 | Current Snapshot一覧・Detail | 無 | J-1b（即時性改善） | 可 | 実装済み | Legacy Adapter（LegacyStockReadRepository） |
| 6. Stock/Sales Analytics | Optional | 高 | Sales/Stock History・Trend・calc4改善 | Stock/Sales Visibility（表示基盤の共有） | J-3〜J-6（SoR・期間） | 不可（Historyが前提） | 中〜高 | 5と同一Query基盤を一部共有 |
| 7. Arrival / Warehouse Visibility | Optional | 中〜高 | Arrival Search/Detail・Warehouse Stock一覧/Detail | 無 | L-1/L-2 | 可 | 実装済み | Legacy Adapter（新規、Arrival/WarehouseStock専用） |
| 8. Discrepancy Visibility | Optional | 中 | 既存Discrepancy検知結果の表示 | Arrival/Warehouse Visibility基盤を再利用可能（必須ではない） | L-3 | 可 | 低（21章参照） | 7と同一UI Pattern |
| 9. Warehouse Integration Modernization | Optional | 高（長期的） | Selenium代替・API/SFTP統一 | 無 | L-5b | 不可（External Spec必須） | 高 | — |
| 10. Purchase Visibility（限定形） | Optional | 低〜中 | Gross Amount定義確定後のみ着手可 | Gross Amount定義確定 | K-6a/K-6b | 不可 | 中（定義確定後は低） | Legacy Adapter |
| 11. Sales Amount / Gross Profit Analytics | Optional | 高（長期的） | Sales Amount蓄積・実績粗利 | Sales Amount Persistence | K-3/K-4 | 不可 | 高 | 6と拡張ポイントを共有 |
| 12. User Management | Optional | 中（内部統制） | User CRUD・Password運用 | 無（既存Entity再利用） | B-7〜B-14全項目 | 可 | 低〜中 | PortalUser, Role Foundation |
| 13. External System Integration（EC各モール） | Optional | 未評価（RE未実施） | 未定 | 無 | 未整理 | 可 | 未評価 | — |

---

## 20. STOP / WAIT List

**「未実装」だからといって自動的に次開発候補にしない。** 以下は現時点で進めてはいけない項目。

| # | 項目 | STOP/WAIT理由 |
|---|---|---|
| 1 | Gross Amount Visual Check（仕入確認、Summary/Breakdown/Detail全て） | Phase 8-E Gate STOP。正しいGross Amount定義がSourceから一意に確定できない。K-6a（Ernest）・K-6b（Gulliver）双方の回答待ち。 |
| 2 | Purchase Visibility（Invoice/PO単体表示、10章で評価を下方修正） | Gross Amount系のField（AMT_TTL/AMT_LINE）を含む限り、Gate STOPと同じ不確実性を継承する。K-6a/K-6b回答後に再評価。 |
| 3 | G-SYS → Warehouse Integration（Slide 13要望本体） | External Specification（Logizero/Tempostar Vendor仕様）待ち。現在Sourceに書込経路自体が存在しない。 |
| 4 | Warehouse/Logizero連携方式見直し（Selenium代替） | External Specification待ち（15章#1-4）。Tempostar側の代替手段の有無が未確認のまま「Selenium廃止」を決めるのは技術的に危険。 |
| 5 | Out of Stock / Long-term Out of Stock正式Business Rule化 | D-4/D-5未回答。現在の[PROTOTYPE]判定（`currentStock==0`）は暫定表示であり、正式化にはGulliver社の業務用語定義が必須。 |
| 6 | Sales/Stock History Persistence | J-6/K-3のSoR責任分担が未確定。Portal DBへ新規Table追加という、これまでの全Phaseで避けてきた設計判断を伴うため、Customer合意なしに着手すべきでない。 |
| 7 | Price Change Approval Workflow / Future Price / Bulk（Brand単位） | I-1〜I-9未回答。現在のDRAFT-onlyという安全な状態を、Customer合意なしに拡張しない。 |
| 8 | 実Official PO Handoff（7-C2B）・実メール送信（7-C4） | A-1b/A-2/A-4b・C-1a/C-1b/C-2/C-6b未回答。Legacy側への実書込・実送信という不可逆的操作を伴うため、最も慎重な合意形成が必要。 |
| 9 | User Management（CRUD実装） | B-7〜B-14の8項目全てGulliver Future Decision。Password運用・削除可否等、セキュリティに関わるBusiness Ruleを推測で実装しない。 |
| 10 | EDI実連携（File/API） | Q16（Ernest）・EDI Vendor仕様（External Spec）双方待ち。 |

---

## 21. Development Candidate Ranking（Top 5）

未実装項目のうち、**Customer回答なしで安全に着手可能**なものを、指示18章の7判断軸で抽出した。

### 候補1: Stock Discrepancy Visibility

- **なぜ今やるか**: 既存`StkQtyDiscrepancyCheckerService`（Legacy側、READ ONLYクエリ）をそのまま再利用でき、新規計算ロジック不要。Arrival/Warehouse Stock Visibility（Phase 8-G）で確立した画面Pattern（List+Drawer、Backend Pagination）をそのまま踏襲できるため実装Riskが低い。Phase 8-F 20章で既にFoundation Classification Aと判定済み。
- **なぜ今まで やらなかったか**: Phase 8-GではOption A/Bを優先し、Option C（本候補）は次点として意図的に見送った（Phase 8-G 22章）。
- **Dependency**: 無（L-3は「要否」自体はD分類だが、表示のみのVisibility機能はArrival/Warehouse Visibilityと同じ論理でFoundation実装可能）。
- **推奨順序**: 1位。

### 候補2: Backend Pagination retrofit（Candidate List / Order History List）

- **なぜ今やるか**: Phase 8-G/8-Hで確立した`PageResponse<T>`パターンをCross-Cutting Gap（17章）の解消として適用できる。Business Rule変更を伴わない純粋な技術改善であり、Customer回答が一切不要。
- **なぜ今まで やらなかったか**: これまでのDemo Instance規模ではPagination無しでも問題化しなかったため、後回しにされていた。
- **Dependency**: 無。
- **推奨順序**: 2位（技術的負債の早期解消）。

### 候補3: Theoretical Margin Reference表示（Stock/Sales or SKU Detail拡張）

- **なぜ今やるか**: 既存`MarginCalculator`（Price Change Foundationで実装済み）を再利用するのみ。理論利益率は既にLegacy `MsItem.profitRateSell`として存在するSource Confirmed値であり、新Business Rule不要。
- **なぜ今まで やらなかったか**: Phase 8-H自身が「画面複雑化を避ける」ため明示的に見送った（Phase 8-H Section 17）。今回は独立した小さな追加として再評価する。
- **Dependency**: 無。
- **推奨順序**: 3位（優先度は中、画面設計の慎重な検討が必要）。

### 候補4: Dashboard統合（Arrival/Warehouse/Stock-Sales KPI Tile）

- **なぜ今やるか**: Phase 8-F/G/Hで追加した3画面が現在Nav Bar経由でしか到達できない。既存Dashboard KPI Tile Patternを再利用し、件数等の単純集計のみ追加すれば良く、新規判定ロジック不要。
- **なぜ今まで やらなかったか**: 各Phaseが個別の画面実装に専念しており、Dashboard統合は各PhaseのScope外だった。
- **Dependency**: 無（候補1が完了していればDiscrepancy件数もTile化できるが、必須ではない）。
- **推奨順序**: 4位。

### 候補5: SKU Detail ⇄ Arrival Detail 相互Navigation拡張

- **なぜ今やるか**: 既存SKU Detail画面のPO History（Legacy TR_PO直接参照）から、対応するArrival Detail（Phase 8-G）への遷移Linkを追加できれば、既存2画面の組み合わせで価値が上がる。新規Backend不要（既存API呼び出しのみ）。
- **なぜ今まで やらなかったか**: 各Phaseのタイミングでは相手画面が未実装だったため。
- **Dependency**: 無。
- **推奨順序**: 5位（優先度は最も低いが、実装コストも最小）。

**選外理由（主要なもの）**: Purchase Visibility・EDI実連携・Sales Amount Persistence・Official PO Handoff・実メール送信は、いずれもCustomer Review WaitまたはExternal Spec Waitが前提のため対象外（20章参照）。

---

## 22. Recommended Next Phase

複数の妥当な進め方があるため、断定せず選択肢として提示する。

**選択肢A（技術的負債解消優先）**: 21章候補1・2（Discrepancy Visibility + Pagination retrofit）を1 Phaseにまとめる。Customer回答を一切必要とせず、既存3画面の完成度を高める。

**選択肢B（Customer Review回答待ち）**: 9/17説明後のGulliver回答・Ernest回答（22項目）を待ち、回答内容に応じて13章のBlocking Top Items（Official PO Handoff、実送信、Price Change Approval等）のいずれかへ進む。

**選択肢C（Dashboard統合による説明力強化）**: 21章候補4を優先し、次回Demo/説明の際にPhase 8-F〜8-Hの成果を一望できる形に整理する。

いずれの場合も、**Gross Amount Visual Check・G-SYS→倉庫連携・Sales/Stock History・Price Change Advanced・User Managementへは進まない**（20章のSTOP/WAIT List）。

---

## 23. Conclusion

- Ordering本体・承認・メーカー通信・Supplier Response・FulfillmentはPrototype/Demoとして完結し、9/17説明時点で「実際に動く」ことを示せる状態にある。
- Price Change・Stock/Sales・Arrival/Warehouse StockはFoundation実装済みで、いずれも「READ ONLY Foundation」という一貫した設計原則（Legacy Write禁止、新Business Rule禁止、既存計算Logic再利用）のもとに構築されている。
- 仕入Gross Amount可視化とG-SYS→倉庫連携（いずれもSlide 12・13の将来テーマ）は、技術的事実（計算式の非等価性、書込経路の不在）により現時点でBLOCKED/EXTERNAL SPEC WAITであり、これは調査不足ではなくSource自体の限界である。
- 残る110件のCustomer Review項目・22件のErnest確認事項の回答が、次のScope・優先順位を最終的に決定する。本監査で特定した21章のTop 5候補は、その回答を待たずに着手可能な範囲に限定されている。

---

**変更したFrontend/Backend/DB Migration/API/Legacy Source: 0件。** 本Documentと（必要な範囲での）既存QA Document更新のみ。
