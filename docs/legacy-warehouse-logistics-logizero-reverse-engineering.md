# Legacy Warehouse / Logistics / Logizero Reverse Engineering & Target Analysis（Phase 8-F）

**Status**: Docs / Audit Only。Frontend / Backend / DB Migration / API追加 / Selenium変更 / Logizero接続 / External System接続 / Production接続 / Legacy Source変更は一切行っていない。本Documentは「倉庫・物流（Warehouse/Logistics/Logizero）」を独立した改善Optionとして整理できるかの調査であり、実装ではない（実装は本Phaseでは禁止）。

**目的**: `customer-review-decision-package.md` 16章のModule/Option構成方針を前提に、倉庫・物流・Logizero連携を、Ordering・Price Change・Stock/Sales Data Update・Invoice/Purchase/Sales/Gross Profitと並ぶ独立した改善Optionとして提案できるかを検証する。**必ず実装する前提ではない。**

**表記凡例**（Phase 8-A〜8-E継続）:
- 🟢 Source Confirmed　🟡 Gulliver Requirement　🔵 Target Proposal　🔴 CUSTOMER REVIEW　⚪ Operational Unknown

---

## 1. Executive Summary

- 2026/08/26打ち合わせ資料（Slide 13）で確認できるGulliver社の要望は「入荷数量・入荷予定日の変更に伴い倉庫側の情報を修正するケースがあり、G-SYS上で変更した情報を倉庫・物流側へ効率的に連携できるか確認していく」という**探索的な将来テーマ**であり、「Logizero」「ロジゼロ」「Selenium」「API化」「リアルタイム連携」等の具体的な語はSlide 13を含め全15枚のいずれにも見つからなかった（Phase 8-C「10日」・Phase 8-D「突合」と同種の扱い）。
- Legacy Sourceには、Slide 13が想定する方向（**G-SYS→倉庫**への変更連携）とは**逆方向**の、**倉庫（Logizero）→G-SYS/Tempostar**への在庫数量連携が、既に本番相当のBatch群として実装されていることを確認した。すなわち、現状Source上で実際に動いている連携と、Meeting Sourceが将来検討したいと述べている連携は、**データの向きが異なる**。
- Logizero連携には、**Selenium WebDriverによるブラウザ自動操作方式**（`InvLogizeroStkDownloadBatch`等）と、**SFTPによるFile連携方式**（`MIMOSA_InvLogizeroStkDownloadBatch`等）の**2系統が実装済みSourceとして併存**している。どちらが現行の本番経路かはSourceから確定できない（12章・22章）。
- 在庫数量の差異検知機構（`StkQtyDiscrepancyCheckerService`、G-SYS `MS_STK.STK_QTY` とLogizero取込Snapshotを比較しEmail通知）が**既に実装済み**であることを確認した。ただし検知のみでResolution Workflowは無く、Phase 8-D（9章）で確認した「差異管理Business Ruleの不在」と同型のGapがWarehouse領域にも存在する。
- Batch名と実際の処理内容が一致しない箇所（`InvTempostarStkUploadBatch`という名称だが実際は価格比較Folder`PRC_COMP`を扱う等）、および使われていない`logizero*`変数・誤ったコメントが複数File（3件以上）に残存していることを確認した（16章）。
- `TR_INV_DTL.QTY_STK_IN`（Phase 8-D確認済み、調達側の入荷実績累積）と`MS_STK.STK_QTY`（本Phase確認、Logizero由来の倉庫現在庫Snapshot）は、**独立した別経路のDataであり、相互に突合する仕組みがSource上に存在しない**ことを確認した（7章・16章）。
- PortalのFulfillment機能（`FulfillmentService`）・発注数量参考値計算（`RecommendedQtyCalculator`等）は、いずれも既存のLegacy集計列（`TR_INV_DTL.QTY_STK_IN`、`MS_STK.ARR_QTY_1..20`）を参照しており、Logizero固有のCode・Selenium・SFTPには一切依存していない（14章）。Warehouse Improvementを導入しなくてもOrdering機能は成立する。

---

## 2. Gulliver Requirement（原文ベース）

出典: `docs/G-Sys_mtg_20260826_02.pptx` Slide 1-7・13・14（全文再確認、本Phaseで再抽出）。倉庫・物流・在庫・入荷に関する語を含むSlideのみ抽出。

> Slide 13. 倉庫・物流システムとの連携 / 入荷数量や入荷予定日の変更に伴い、倉庫側の情報を修正するケースもあるため、G-SYS上で変更した情報を倉庫・物流側へ効率的に連携できるかについても確認していくこととなりました。既存の物流システムとの連携方法を確認したうえで、自動化可能な範囲を検討します。

> Slide 14. 今後の方向性 / （中略）在庫・物流連携等について具体的な実現方法を検討します。次回は、想定する業務フローや画面イメージを具体化したものを提示し、認識合わせを行う予定です。

| 分類 | 内容 | 出典 |
|---|---|---|
| 🟡 Current Operation | 入荷数量・入荷予定日がメーカー回答後に変更になるケースがある（Slide 7、既存Ordering RE Documentで確認済み） | Slide 7 |
| 🟡 Current Problem | 上記変更に伴い、倉庫側の情報を**手作業で修正するケースがある** | Slide 13 |
| 🟡 Future Requirement（探索段階） | G-SYS上で変更した情報を倉庫・物流側へ**効率的に連携できるか確認していく**（決定ではなく「確認していくこととなった」という合意のみ） | Slide 13 |
| 🟡 Future Requirement（探索段階） | 既存の物流システムとの連携方法を確認したうえで、**自動化可能な範囲を検討する**（自動化の可否・範囲は未定） | Slide 13 |
| ⚪ Written Sourceに記載なし | 「Logizero」「ロジゼロ」という固有名詞 | 全15枚検索、0件 |
| ⚪ Written Sourceに記載なし | 「Selenium」「API」「リアルタイム」「SFTP」等の技術語 | 全15枚検索、0件 |
| ⚪ Written Sourceに記載なし | 在庫数量そのものの倉庫→G-SYS連携（本Phase3章で確認する既存Batch群）への言及 | 0件。Slide 13は「G-SYS→倉庫」の方向のみ言及しており、既存の「倉庫→G-SYS/Tempostar」連携（3-6章）には触れていない |

**結論**: Meeting Sourceで確定できるのは「入荷変更時の倉庫側手作業修正という現状の課題認識」と「G-SYS→倉庫方向の連携可能性を今後確認する」という探索的合意までである。技術方式（Logizero/Selenium/API/SFTP）や自動化の範囲は一切決まっていない。**Legacy Sourceに実装済みの「倉庫→G-SYS/Tempostar」連携（3章以降）は、Slide 13が言及する方向とは異なる、既存の別目的の仕組みである**ことに注意して以降の章を読む必要がある。

---

## 3. Current Architecture（Batch/Service一覧）

`grep -ril "logizero"` により、Legacy Source全体から関連Fileを特定した（14 File）。うちCore Logicを持つものを実装レベルで確認した。

| File | 種別 | 役割 | 本Phase確認状況 |
|---|---|---|---|
| `InvTempostarStkUploadBatch` | Batch | クラス名は"Stk"（在庫）だが、実際は`CODE_ID_FILE_IMP_PRC_COMP`（価格比較Folder）のCSVをTempostarの**商品CSV Import画面**へアップロード。`logizeroLoginUrl`等3変数は宣言のみで未使用（Dead Code）、コメントも誤り（16章） | 🟢 全文読了 |
| `MsInvTempostarStkUploadService` | Service | 上記Batchの同型Service版（同じ`PRC_COMP`Folder、同じTempostar商品CSV Import画面）。REST経由で同期実行可能 | 🟢 全文読了 |
| `InvLogizeroStkDownloadBatch` | Batch | Logizero管理画面へSeleniumでLogin→在庫Export（`在庫`/`デフォルト`パターン）→Local File Downloadのみ。G-SYS DBへの書込み無し | 🟢 全文読了 |
| `InvLogizeroStkImportBatch` | Batch | 上記でDownloadされたCSVを読み込み、`WK_STK`経由で`MS_STK.STK_QTY`を更新。**Logizero→G-SYS DBの実際の取込経路** | 🟢 全文読了 |
| `InvLogizeroTempostarStkUploadBatch` | Batch | Logizero管理画面へSeleniumでLogin→「出荷可能在庫」Export（`TEMPOSTAR用`パターン）→**そのままTempostarの在庫CSV Import画面へ直接Upload**。G-SYS DB（`MS_STK`等）は一切読み書きしない | 🟢 全文読了 |
| `MIMOSA_InvLogizeroStkDownloadBatch` | Batch | `InvLogizeroStkDownloadBatch`のSFTP版。Selenium不使用、`SftpService`経由でFile取得のみ | 🟢 全文読了 |
| `MIMOSA_InvLogizeroTempostarStkUploadBatch` | Batch | Download区間はSFTP、Upload区間（Tempostarへ）は引き続きSelenium、というHybrid方式 | 🟢 全文読了 |
| `StkQtyDiscrepancyCheckerService` | Service | `MS_STK.STK_QTY`と当日Logizero取込Snapshot（`WK_STK`）の差異を検出しEmail通知。`/api/validateStkQtyDiscrepancy`（認証Bypass）から起動 | 🟢 全文読了 |
| `MsStkListDownloadBatch` / `MsStkListDownloadRemoteBatch` | Batch | `HIS_STK_LIST`フルSnapshot出力（Phase 8-C既述）。`logizero*`変数はDead Code | 🟡 Logizero関連部分のみ確認（既存Phase 8-Cの範囲外部分は非対象） |
| `CheckSMTPErrorFromDailyBatch` / `SysGoogleDriveBackUp` | Batch | grep一致は`logizero`という語がLog文字列等に副次的に含まれるのみで、Warehouse固有Logicなし | 🟡 対象外と判断 |

**関連Entity/Repository**:

| 対象 | 内容 |
|---|---|
| `TR_ARR`（`TrArr`） | Arrivalヘッダ。PK: `SUPPLIER_CD`+`PO_NO`+`INV_NO`（`LINE_NO`はPK外、ヘッダ単位）。ETA/ETA_WH/入荷/通関/搬送/保管料/送金まで含む輸入貿易実務のフルセット（7章で詳述） |
| `StockDiscrepancyDto` / `MsStkRepositoryImpl.validateStkQtyDiscrepancy` | 差異検出SQL本体（8章で詳述） |
| `Const.java` | `CODE_ID_FILE_IMP_LOGI_INV`/`LOGI_TEMP`/`PRC_COMP`、`WK_STK_BACK_UP_ID_LOGIZERO`、`CODE_ID_MS_WH_4`〜`14`、`MS_USER_USER_TYPE_LOGISTIC` 等 |
| `WebSecurityConfig` | `MS_USER.USER_TYPE`に`LOGISTIC`/`WH_USER`/`WH_LOG`が既存（Legacyに倉庫系User Roleが既に存在、9章・14章） |
| `PrEtaWhMailBatch` | `TR_ARR.ETA_WH`確定時に「WH DECISION - ETA WH UPDATE」Emailを送信（既存の倉庫向け通知経路、7章） |
| `MigArrListImpBatch2` | クラス名`Mig`（Migration）プレフィックスから、一過性のデータ移行用Batchと判断し、本Phaseでは継続運用中のDataflowとして扱わない |

---

## 4. Logizero Integration（Trigger→Logging 追跡）

指示どおり、`InvLogizeroTempostarStkUploadBatch`を主経路として、Trigger〜Loggingまでを追跡した。

| Step | 内容 | Evidence |
|---|---|---|
| **Trigger/Input** | Batch起動契機はSource上に存在しない（`main()`から`executeRun()`を呼ぶのみ）。OS Scheduler等の外部Trigger（既存Theme H-2と同型のOperational Unknown） | 🔴 22章 |
| **Data Extraction** | Logizero管理画面（`ap.logizard.net/LPSTD001/PM08/Index`）でExport種別`出荷可能在庫`・抽出パターン`TEMPOSTAR用`を選択し、CSVをExport | 🟢 |
| **Conversion** | Data変換処理は無し。LogizeroがExportしたCSVをそのままファイルCopy（`copyFileUsingStream`）してTempostarへ渡す。**G-SYS側での加工・検証は一切行われない** | 🟢 |
| **Login/Auth** | Logizero: `user_id`/`password`フィールドへID/PWをSendKeys→Submit。Tempostar: `companyid`/`userid`/`password`の3要素。いずれも平文フィールド送信、二要素認証等の痕跡なし | 🟢 |
| **Browser/Selenium Operation** | ChromeDriver、固定Xpath（例: `/html/body/div[3]/div[3]/div[2]/div[2]/div[1]/div/form/div[29]/...`）、`Thread.sleep(1000)`の多用。DOM構造変更に対し極めて脆弱（12章で詳述） | 🟢 |
| **Upload** | Tempostar在庫CSV Import画面（`stock/csvimport/index.nhn`）へFile Path送信、「在庫を上書きする」を選択しSubmit | 🟢 |
| **Result** | Upload後、特定のDOM要素の再出現をPollingで待つのみ（`closeUploadAndBackup`）。Tempostar側のImport結果（成功件数・Errorの有無）を読み取る処理は無い | 🔴 11章 |
| **Error Handling** | Try/Catchは`TimeoutException`のみを捕捉しログ出力するが、以降の処理（Driver quit等）は続行。業務的なError通知・Retryは無い | 🔴 11章 |
| **Logging** | `System.out.println`相当のシンプルな標準出力ログのみ。構造化Log・監査Trailは無い | 🟢 |

---

## 5. G-SYS → Logizero（Data Flow）

**Source上、G-SYS自身のDB（`MS_STK`/`TR_PO`/`TR_ARR`等）のDataをLogizeroへ送信する経路は確認できなかった。**

| 候補項目（指示4章） | 確認結果 |
|---|---|
| SKU / Stock Qty / Arrival Qty / PO / Supplier / Brand / Warehouse / Date | 🔴 **いずれもG-SYS→Logizero方向でSourceに存在しない** |
| File形式（CSV/Excel/Web Upload/API/Manual） | 該当なし |

**唯一G-SYS発でLogizero側の管理画面を経由する処理**は、4章の`InvLogizeroTempostarStkUploadBatch`におけるLogizero**Export（読み取り）**操作であり、これはLogizeroへのDataの送信ではなくLogizeroからのDataの取得である。したがって「G-SYS→Logizero」という書き込み方向のData Flowは、**本Phaseで調査した範囲内では存在しない**（Slide 13が将来検討したい「G-SYS上で変更した情報を倉庫側へ連携する」仕組みは、現時点でSource上に実装されていないことを意味する）。

---

## 6. Logizero → G-SYS（Data Flow）

| 候補項目 | SoR/経路 | Evidence |
|---|---|---|
| **Stock Qty（倉庫別在庫数量）** | 🟢 `InvLogizeroStkDownloadBatch`（Export種別`在庫`/`デフォルト`パターン）→`InvLogizeroStkImportBatch`→`WK_STK`→`MS_STK.STK_QTY`（`WH_CD`別） | 3-4章、実装確認済み |
| **Arrival** | 🔴 Logizeroから直接G-SYSへ入荷（Arrival）情報が流れ込む経路はSource上に見つからない。`TR_ARR`はもっぱらG-SYS側（PO/Invoice/BL Import Batch群）で生成・更新される（7章） | Arrival情報のSoRはG-SYS側 |
| **Stock In / Received Qty** | 🔴 同様に、Logizero発の入荷実績FileがG-SYSへ流れ込む経路は見つからない。`TR_INV_DTL.QTY_STK_IN`は`PrStkInReportImportBatch`（Phase 8-D確認済み）が別のImport Fileから更新しており、**そのFileがLogizero発かどうかはBatch名・列定義からは判別できない**（22章Ernest確認候補） |
| **Warehouse Stock（物理倉庫在庫）** | 🟢 Stock Qtyと同一（`MS_STK.STK_QTY`、`WH_CD`4〜15） | 上記 |
| **Shipment Result** | 🔴 Sourceに見つからない | 22章 |
| **Error/Return File** | 🟡 `InvLogizeroStkImportBatch`内で「MS_ITEM未登録」「MS_STK未登録」の2種のErrorを検出しEmail通知するが、これはLogizero発Fileの**受信後**にG-SYS側で検出するEmailであり、Logizero自身が発行するError/Return Fileではない | 3章・9章 |

**File形式**: いずれもCSV（Selenium経由・SFTP経由問わず）。SJISエンコーディング（`InvLogizeroStkImportBatch`で確認）。固定列Index（`WH_CD`=6, `LAST_STK_DATE`=9, `ITEM_CD`=10, `STK_QTY`=25）で読み取っており、Phase 8-DのOfficial PO Excel（ヘッダ文字列で動的に列特定）とは異なり、**Logizero側のExport列順が変わった場合に検知する仕組みが無い**（16章）。

---

## 7. Arrival / Stock In

`PO → Invoice → BL → Arrival → Stock In → Current Stock`をStepごとに整理する（Phase 8-Dの3-4章・7章を再利用し、本Phaseでは`TR_ARR`・`Current Stock`部分のみ新規調査）。

| Step | Entity/Table | Batch | Key | Date | Qty | Status |
|---|---|---|---|---|---|---|
| PO | `TR_PO`/`TR_PO_DTL` | `PrOfficialPoImportBatch`等（既存） | `PO_NO` | - | `QTY_PO` | - |
| Invoice | `TR_INV`/`TR_INV_DTL` | `PrOfficialPoImportBatch`（Phase 8-D） | `SUPPLIER_CD`+`INV_NO` | - | `QTY` | `STATUS`(TRANSIT/RECEIVING/STOCK_IN) |
| BL | `TR_BL`/`TrBlDtl` | `PrBLInvImportBatch`（Phase 8-D） | `BL_NO` | - | - | - |
| **Arrival** | 🟢 `TR_ARR`（本Phase新規） | `PrOfficialPoImportBatch`/`PrBLInvImportBatch`/`PrCreditPoImportBatch`/`PrStkInReportImportBatch`/`PrEtaWhMailBatch`が更新 | `SUPPLIER_CD`+`PO_NO`+`INV_NO`（ヘッダ単位、`LINE_NO`はPK外） | `ETD`/`ETA`/`ETA_WH`/`STK_IN_DATE`/`CONF_QTY_DATE`等、輸送・通関・保管の各段階Date群 | `QTY`（1列のみ、明細別ではない） | `WH_REP_STATUS`/`WH_REP_RESULT`（倉庫Report状況）、`ETA_WH_SEND_FLG` |
| Stock In | `TR_INV_DTL.QTY_STK_IN` | `PrStkInReportImportBatch`（Phase 8-D） | `SUPPLIER_CD`+`INV_NO`+`LINE_NO` | - | `QTY_STK_IN` | - |
| **Current Stock** | 🟢 `MS_STK.STK_QTY`（本Phase新規） | `InvLogizeroStkImportBatch`（本Phase） | `WH_CD`+`ITEM_CD`（PO/Invoice/Arrivalへの参照列なし） | Import実行日時（Batch内`sdf`のみ、Table上の永続Timestampは限定的） | `STK_QTY` | - |

**重要な発見**: `TR_ARR`は輸入貿易実務のフルセット（通関Broker、関税、地方消費税、Demurrage/Drayage/Devanning Charge、送金銀行等）を持つ、**当初想定より遥かに詳細なEntity**である。一方で`QTY`列は1本のみでLine別ではなく、`TR_INV_DTL`の各明細行との対応は`SUPPLIER_CD`+`PO_NO`+`INV_NO`単位の粒度に留まる。

**Stock InからCurrent Stockへの経路が2系統に分岐している**ことが本Phaseの最重要発見である：
1. `TR_INV_DTL.QTY_STK_IN`（調達側、PO/Invoice明細ごとの入荷実績累積、Phase 8-D確認済み）
2. `MS_STK.STK_QTY`（倉庫側、Logizero発Snapshotによる`WH_CD`+`ITEM_CD`単位の一括上書き、本Phase確認）

**この2つを結びつけるKeyやJoin条件はSource上に存在しない**。すなわち「どのPO/Invoiceの入荷が、倉庫のどの在庫数量に反映されたか」をSourceからTraceすることはできない（13章）。

---

## 8. Warehouse Stock

- `MS_STK`は`WH_CD='XX'`（全社集約行）と`WH_CD`4〜15（物理倉庫別行）の構成（Phase 8-C確認済み、本Phaseで倉庫別行の意味を深掘り）。
- `InvLogizeroStkImportBatch`は「出荷可能在庫」の閾値通知計算において、`WH_CD` IN `{4,5,6,7,11,13}`のみを合算している（Source Confirmed、"Sellable"倉庫の暗黙のWhitelist）。
- 一方`StkQtyDiscrepancyCheckerService`の差異検出SQLは`WH_CD` IN `{4,5,6,7,8,9,10,11,12,13,14,15}`（12コード）を対象としており、**閾値計算の対象倉庫（6コード）より広い**。
- `8,9,10,12,14,15`が何を表す倉庫か（不良品保管・検品中・他倉庫間移動中等）はSource上のコメント・命名からは判別できない。**Ernestへの確認候補**（22章）。
- `MS_STK.STK_QTY_OLD`/`LAST_STK_IN_DATE_OLD`（Phase 8-C既述の"前回値保持"列）は、本Phase確認の`InvLogizeroStkImportBatch`のStep 7-1で、`WH_CD='XX'`行に対してのみ計算・保存される（倉庫別行には適用されない）。

---

## 9. System of Record

| データ | SoR候補 | 根拠 |
|---|---|---|
| Warehouse Stock（物理倉庫在庫数量） | 🟢 **Logizero**（`MS_STK.STK_QTY`はLogizero Export値の単純合算・上書き、6章・8章） | 6章 |
| Arrival Schedule（ETA/ETA_WH） | 🟢 **G-SYS**（`TR_ARR`、Logizeroからの直接連携は確認できず、G-SYS側Batchが算出・保持） | 7章 |
| Received Qty（入荷実績、調達側） | 🟢 **G-SYS**（`TR_INV_DTL.QTY_STK_IN`、Phase 8-D確認済み。ただし入力元Fileの最終発行元がLogizeroかどうかはOperational Unknown） | 7章・22章 |
| Available/Sellable Stock | ⚪ **Unknown**（`InvLogizeroStkImportBatch`の閾値判定は都度計算のTransient値であり、永続化された「利用可能在庫」Fieldは確認できず） | 8章 |
| Shipment / Sales Shipment | ⚪ **Unknown**（Source上に確認できる経路なし） | 6章 |
| PO | 🟢 **G-SYS**（`TR_PO`、既存Ordering機能の前提どおり） | 既存 |
| SKU Master | 🟢 **G-SYS**（`MS_ITEM`、既存） | 既存 |
| Warehouse Stock Discrepancy（差異記録） | 🟡 **G-SYS Email**のみ（`SysSendMail`、`StkQtyDiscrepancyCheckerService`）。永続Tableとしての差異履歴は無い | 11章 |

---

## 10. Synchronization / Timing

- Batch自体にScheduler・Cron定義はSource上に存在しない（既存Theme H-2「既存Import Batchの起動Trigger・実行頻度」と同型のOperational Unknown。Windows Task Scheduler等、Source外の仕組みが起動している可能性が高いが確認不能）。
- `StkQtyDiscrepancyCheckerService`の`backupFileId`は`yyyyMMdd`（日単位）で構成されており、**設計上は日次実行を想定している**と推測されるが、これはSource構造からの推測であり、実際の実行頻度はErnest確認が必要（推測で確定しない、22章）。
- Selenium方式とSFTP方式（MIMOSA_）が同一目的のBatchとして併存している理由・使い分け（並行運用か、片方が旧方式か）はSourceから判別できない。

---

## 11. Failure / Recovery

- **Retry**: 4章で確認したとおり、Selenium系Batchに明示的なRetry機構は無い。例外発生時はChrome Driverを`quit()`してBatch自体が異常終了するのみ。
- **Manual Retry**: Batchの再実行手順（同一Fileの再Upload時の重複防止等）はSource上に確認できない。
- **Duplicate Prevention**: File名にTimestampを付与しBackup Folderへ移動する仕組みはあるが、これはFile名の衝突回避が目的であり、同一Dataの二重取込・二重Upload自体を防ぐ仕組みではない。
- **Partial Failure**: `InvLogizeroStkImportBatch`は、`MS_ITEM`未登録／`MS_STK`未登録の行を**Skipして処理を継続**する（3章）。Skipされた行はEmail通知されるが、処理自体は成功扱いで完了する（Silent Partial Success）。
- **Error File / Logging**: 構造化Errorファイルの生成は無い。Email本文へのText埋め込みのみ。
- **Notification**: `SysSendMail`経由のEmail通知のみ（Phase 8-D「差異管理Business Ruleの不在」と同型。検知はできるが、対応・解消のWorkflowはSource上に存在しない）。

---

## 12. Selenium Dependency（依存評価）

指示どおり「APIに変えるべき」と即断せず、事実を分類する。

| 論点 | 分類 | 根拠 |
|---|---|---|
| Logizero側にAPIが存在するか | ⚪ Sourceからは確認不能 | External Specification確認が必要（23章） |
| Logizero側に非Browser Interfaceが存在するか | 🟢 **存在する（Source Confirmed）** | `MIMOSA_InvLogizeroStkDownloadBatch`が実際にSFTPでLogizeroの在庫Fileを取得する実装が**既に存在**している。少なくとも「在庫Export」用途については、Logizero側（または仲介環境）が非Browser Interfaceを提供している事実がSourceから確認できる |
| Tempostar側にAPIが存在するか | ⚪ Sourceからは確認不能 | 本Codebase内では一度もAPI/SFTPで到達された形跡が無く、常にBrowser経由（4章）。ただし「Tempostarが提供していない」ことの証明にはならない（Codebaseが採用していないだけの可能性も残る） |
| File Uploadが正式Interfaceか | ⚪ Unknown | Tempostar管理画面のCSV Import機能自体は正式機能と推測されるが、それが「正式な連携方式」として契約・仕様化されているかはSourceから確認できない |
| Browser Automationしか選択肢が無いか | 🔴 **少なくともTempostarへのUpload区間については、Source上で確認できる限りBrowser Automationのみ**（SFTP版=`MIMOSA_`variantも、Download区間はSFTP化されているがUpload区間は依然Selenium） | 4章 |

**結論**: Logizero「Download」側は既にSFTPへの移行実装が存在する一方、Tempostar「Upload」側は全実装がBrowser Automationのみである。**「Seleniumを廃止すべき」という単純な結論は導けない**（Tempostar側の代替手段の有無が未確認のため）。External Specification確認（23章）を経てから技術方式を検討すべき。

---

## 13. PO → Arrival Traceability

Phase 8-Dの7章を再利用し、本Phaseで新規確認した`TR_ARR`・Warehouse Stock部分を追加する。

| 対象 | 分類 | Evidence |
|---|---|---|
| PO ⇄ Invoice ⇄ BL | 🟢 完全Join可能（Phase 8-D確認済み） | Phase 8-D 7章 |
| PO/Invoice ⇄ Arrival（`TR_ARR`） | 🟢 **完全Join可能（本Phase確認）** | `TR_ARR`のPKが`SUPPLIER_CD`+`PO_NO`+`INV_NO`で、`TR_INV`と直接一致 |
| Arrival ⇄ Warehouse Stock（`MS_STK.STK_QTY`） | 🔴 **不可能（本Phase確認）** | `MS_STK`にPO/Invoice/Arrivalを参照するField自体が存在しない。倉庫在庫は`WH_CD`+`ITEM_CD`単位の集計値であり、特定の入荷Lotとの対応は取れない |
| Stock In（`QTY_STK_IN`）⇄ Warehouse Stock（`STK_QTY`） | 🔴 **不可能（本Phase確認）** | 7章のとおり、2つの経路を結ぶKeyがSourceに無い |

---

## 14. Ordering / Fulfillment Dependency

- **`FulfillmentService`**: `LegacyInvoiceLineRow.qtyStkIn()`（`TR_INV_DTL.QTY_STK_IN`）を合算し`stockInQty`として使用（Phase 8-D・Phase 7-C7Aで既確認の依存関係を本Phaseで再確認）。Logizero固有のCode・Table・Batchへの依存は無い。
- **`RecommendedQtyCalculator`/`OrderQuantityCalculator`/`StockCalculationHelper`**: `MS_STK`の`ARR_QTY_1..20`（Open Arrival集計列、Phase 8-C確認済み）を参照する。これは`TR_ARR`や本Phaseで確認したLogizero Batchが直接書き込む列ではなく、既存の別Batch群（Phase 7既確認）が更新する列であるため、**本Phaseで確認したLogizero固有連携への技術的依存は無い**。
- **Warehouse Improvementを導入しなくてもOrderingは成立するか**: 🟢 成立する。ただしOrderingの発注数量参考値・Fulfillment表示は`MS_STK.STK_QTY`（6章の在庫Fresh性）に**間接的に依存**しており、Logizero連携Batchが正しく動いていることを**前提として**現在の数値が成立している（Technical Dependency: 無、Operational Dependency: 有）。
- **Warehouse Optionを追加した場合の改善点**: 現状Email通知（`PrEtaWhMailBatch`）とExcel/CSV手作業に閉じているArrival状況・在庫差異情報をPortalへ可視化することで、担当者の確認導線を一本化できる可能性がある（17-18章のTarget Concept）。
- **Business/Estimate DependencyとTechnical Dependencyの区別**: 「発注データを起点に倉庫状況も見たい」という要望（Slide 13）はBusiness/Estimate上の関連に過ぎない。Fulfillment機能が`QTY_STK_IN`を読むCode自体は**既存のTechnical Dependency**（新規実装不要）。

---

## 15. Stock/Sales Dependency

- Phase 8-Cで確認した`MS_STK.SOLD_QTY`（Tempostar CSV由来）と、本Phaseで確認した`MS_STK.STK_QTY`（Logizero由来）は、**同一Table・同一PK（`WH_CD`+`ITEM_CD`）の別列でありながら、完全に独立した2つのBatch Pipeline（`SlTempostarImportBatch`系 と `InvLogizeroStkImportBatch`系）によって別々に更新される**。両者を突合・整合確認する仕組みはSource上に存在しない。
- **「G-SYS在庫」と「Logizero在庫」は同じ意味か**: 🟢 `STK_QTY`という1つのFieldに関する限り、**Import直後は数値的に同一**である（LogizeroのExport値をそのまま合算・上書きするのみで、G-SYS側での加工・按分は無い、6章）。ただし直後から次回Import（頻度Unknown、10章）までの間は、Logizero側の実際の変動に対してG-SYS側の値は**静的なSnapshotとして陳腐化する**。したがって「同じ意味だが、取得Timingによって数値が乖離しうるSnapshot」という性質を持つ。

---

## 16. Current Gaps

1. **Batch名と実際の処理内容の不一致**: `InvTempostarStkUploadBatch`/`MsInvTempostarStkUploadService`は名称に"Stk"（在庫）を含むが、実際は価格比較Folder（`PRC_COMP`）のCSVをTempostar**商品**Import画面へ送る処理であり、在庫データではない（3-4章）。
2. **未使用のLogizero関連変数・誤ったコメントの残存**: `InvTempostarStkUploadBatch`（`logizeroLoginUrl`等、宣言のみ未使用、「GO TO LOGIZERO WEBSITE」という誤ったコメント）、`MsStkListDownloadBatch`/`MsStkListDownloadRemoteBatch`（同様の未使用変数）。Copy-Paste起源と推測され、将来の保守者（本チーム含む）の誤読リスクがある。
3. **`InvLogizeroStkImportBatch`のError検証ロジックの疑わしい重複**: Step 4（`MS_ITEM`未登録検出）とStep 5（`MS_STK`未登録検出）が、いずれも同一Method`validateItemCdExistsInMsItem`を呼んでおり、Step 5が実際には`MS_STK`を検証していない可能性がある（Copy-Paste起源の疑い、Phase 8-EのAMT_TTL変数スコープ問題と同種のパターン）。
4. **Stock In経路とWarehouse Stock経路の不整合**: 7章・13章のとおり、`TR_INV_DTL.QTY_STK_IN`（調達側）と`MS_STK.STK_QTY`（倉庫側）を結ぶKeyが無く、相互検証不能。
5. **差異検知はあるがResolution Workflowが無い**: `StkQtyDiscrepancyCheckerService`はEmail通知のみで、Phase 8-D確認の「差異管理Business Rule不在」と同型のGapがWarehouse領域にも存在する。
6. **`/api/validateStkQtyDiscrepancy`が認証Bypass対象**: `WebSecurityConfig`の`web.ignoring()`リストに含まれ、Spring Securityを一切経由しない（他の内部保守用Endpointと同一パターン）。
7. **Selenium実装の脆弱性**: 固定絶対XPath・固定`Thread.sleep`・Login成功確認処理の欠如（4章・11章）。
8. **2系統の連携方式が未整理のまま併存**: Selenium方式とSFTP方式（`MIMOSA_`）のどちらが現行運用かSourceから判別不能（10章・12章・22章）。
9. **WH_CD（倉庫コード）の意味が未文書化**: 4〜15の12コードのうち、"Sellable"として扱われるのは6コードのみで、残り6コードの意味は不明（8章）。

---

## 17. Target Concepts（🟡 Target Proposal、機械的に採用しない）

```
[🟢 Source Confirmed] Arrival確定（TR_ARR、既存Batch群）
        ↓
[🔵 Target Proposal] Arrival Visibility（TR_ARRのETA/ETA_WH/WH_REP_STATUS等をPortalへREAD ONLY表示、Email通知の代替/補完）
        ↓
[🟢 Source Confirmed（既存機能）] Logizero→G-SYS Stock Qty取込（変更なし）
        ↓
[🔵 Target Proposal] Warehouse Stock Visibility（MS_STK.STK_QTYのWH_CD別表示、Phase 8-CのOption Aと隣接）
        ↓
[🟢 Source Confirmed（既存機能）] StkQtyDiscrepancyCheckerServiceによる差異検知（変更なし）
        ↓
[🔵 Target Proposal] Discrepancy Visibility（既存の差異検知結果をEmailの代わりにPortalへ表示するのみ、判定Logic自体は変更しない）
        ↓
[🔴 CUSTOMER REVIEW] Slide 13の「G-SYS→倉庫」変更連携（Business Rule・External Specification共に大きく未確定、本Phaseでは提案のみに留め設計しない）
```

**評価**: 3系統（Arrival/Warehouse Stock/Discrepancy）はいずれも**既存Source Confirmed Dataの READ ONLY表示**に留まる限り、Legacy Write不要・Business Rule決定不要で構成可能である。一方Slide 13が示す「G-SYS→倉庫」双方向連携は、External SpecificationとBusiness Rule決定の両方が大きく不足しており、本Phaseでは設計に踏み込まない。

---

## 18. Improvement Options（改善Option候補）

事前提示の例（A〜F）をそのまま採用せず、調査結果に基づき単位を整理した。

| Option | 内容 | Customer Value | Legacy Gap | 単独導入可能性 | Technical Dependency | Business/Estimate Dependency | External System Dependency | Customer Decision Dependency | 実装難易度（相対） |
|---|---|---|---|---|---|---|---|---|---|
| **A. Arrival Visibility** | `TR_ARR`のETA/ETA_WH/倉庫Report状況をPortalへREAD ONLY表示 | 中（Email依存の現状からの脱却） | 表示機能自体が無い | 可 | 低（READ ONLY） | 無 | 無 | 低 |
| **B. Warehouse Stock Visibility** | `MS_STK.STK_QTY`のWH_CD別内訳をPortalへREAD ONLY表示 | 中（Phase 8-CのOption Aと統合可能性あり） | 表示機能自体が無い | 可（Aと独立） | 低（READ ONLY） | 低（Phase 8-C Option Aとの統合要否） | 無 | 低 |
| **C. Stock Discrepancy Visibility** | 既存`StkQtyDiscrepancyCheckerService`の検知結果をPortalへ表示 | 中〜高（既存Email通知の見落としリスク軽減） | 表示機能自体が無い（判定Logic自体は既存） | 可（A/Bと独立） | 低（既存Queryの再利用のみ） | 低 | 無 | 低〜中（差異発生時の運用ルール確認が必要） |
| **D. Logizero/Tempostar連携の可観測性向上** | Batch実行結果・Error件数をPortalへ表示（連携方式自体は変更しない） | 中 | 可視化機能が無い | 可 | 中（既存Batch実行Log/結果の集約が必要） | 低 | 無（既存連携の結果参照のみ） | 中 |
| **E. Logizero/Tempostar連携方式の見直し** | Selenium依存軽減、SFTP/API化の検討 | 高（安定性向上） | 12章のSelenium脆弱性 | 不可（External Specification確認が前提） | 高（連携方式自体の変更） | 中 | **大（Logizero/Tempostar双方のVendor仕様確認が必須）** | 高 |
| **F. G-SYS→倉庫 双方向連携** | Slide 13の将来要望（変更情報を倉庫側へ連携） | 高（Slide 13の要望に直接対応） | 連携経路自体が存在しない（5章） | 不可（Business Rule・External Specification共に大きく未確定） | 高 | **高** | **大** | **極めて高い** |

**検証結果**: A・B・Cは既存Source Confirmed Dataの READ ONLY表示に留まるため単独導入可能。D以降はExternal Specification確認またはBusiness Rule決定が前提となり、単独提案はできない。

---

## 19. Module / Estimate Dependency

`customer-review-decision-package.md` 16章の枠組みに従い整理する（同章への反映は本Phase完了後に別途実施）。

| # | 機能領域 | Core/Optional | 他機能へのDependency | 単独導入可能性 | Legacy影響 | External System依存 | Customer Decision依存 |
|---|---|---|---|---|---|---|---|
| 19-1 | Warehouse/Logistics（Arrival Visibility・Warehouse Stock Visibility・Discrepancy Visibility） | Optional | 無（Ordering非依存で成立、14章） | 可 | 低（READ ONLY、既存Table/既存Query参照のみ） | 間接（LogizeroがG-SYSへ供給するDataのFreshness次第） | Theme L（21章） |
| 19-2 | Logizero/Tempostar連携方式見直し（Selenium/SFTP/API） | Optional | 19-1の可観測性向上とは独立 | 不可（External Specification確認が前提） | 中〜高（連携方式の変更） | 大 | Theme L・23章 |
| 19-3 | G-SYS→倉庫 双方向連携 | Optional | 19-1・19-2の完成後を推奨 | 不可 | 高 | 大 | Theme L |

**Software Architecture上の方針**（16.3章の原則の継続適用）: 19-1（Visibility系）は、Phase 8-CのStock/Sales Option A（既存Legacy値のREAD ONLY表示）と実装Patternが同一であり、共通のRead-only Legacy Adapter層を再利用できる可能性が高い。見積上のOption分割と実装上のModule分割を1対1にしない。

---

## 20. Foundation Classification

| 分類 | 内容 |
|---|---|
| **A. Customer回答前でも安全に実装可能** | Option A（Arrival Visibility）、Option B（Warehouse Stock Visibility）、Option C（Discrepancy Visibility）。いずれもSource Confirmed Dataの READ ONLY表示のみで、Legacy Write・Business Rule決定を伴わない |
| **B. Customer回答後まで待つもの** | Option D（可観測性向上の対象範囲確定後）、Option F（Business Rule確定後） |
| **C. External仕様確認後まで待つもの** | Option E（Logizero/Tempostar双方のVendor仕様確認後）、Option F（同上） |

**今回は実装しない**（指示どおり）。

---

## 21. Customer Review

新規Theme **L: Warehouse / Logistics / Logizero**として追加する（既存Theme A-Kとの重複なし）。

| 論点 | 分類 | 出典 |
|---|---|---|
| L-1. Arrival Visibility機能の要否 | D（Future Decision） | 17章・18章Option A |
| L-2. Warehouse Stock Visibility機能の要否、Phase 8-C Option Aとの統合要否 | D | 17章・18章Option B |
| L-3. Stock Discrepancy Visibility機能の要否、差異発生時の運用ルール（Phase 8-D 9章と同型のGap） | D | 11章・18章Option C |
| L-4. Slide 13の「G-SYS→倉庫」変更情報連携を、どこまで自動化すべきか | D | 2章・18章Option F |
| L-5a（現在の事実）. Logizero/Tempostar連携において、Selenium方式とSFTP方式（MIMOSA_）のどちらが現行の本番経路か | B（Ernest Q35） | 10章・12章 |
| L-5b（将来の決定）. 連携方式（Selenium/SFTP/API）を将来どう整理・統一すべきか | D | 18章Option E |

---

## 22. Ernest Questions

`ernest-current-operation-question-sheet.md`への追加候補（既存Q1-34との重複確認済み）。

| 論点 | 出典 |
|---|---|
| Q35. Logizero/Tempostar連携（在庫）において、`InvLogizeroStkDownloadBatch`系（Selenium）と`MIMOSA_InvLogizeroStkDownloadBatch`系（SFTP）のどちらが現在の本番運用経路か。両方動いているとすれば、使い分けの意図は | 10章・12章 |
| Q36. `MS_STK`の`WH_CD`4〜15それぞれが具体的にどの倉庫・保管区分（例: 良品倉庫、検品中、不良品、他倉庫間移動中等）を表すか。特に閾値判定で使われる`{4,5,6,7,11,13}`とそれ以外の6コードの違い | 8章 |
| Q37. `StkQtyDiscrepancyCheckerService`のEmail通知を受け取った担当者が、実際にどのように対応（現地確認・再取込・手動修正等）しているか | 9章・11章 |
| Q38. `PrEtaWhMailBatch`の「WH DECISION - ETA WH UPDATE」Emailは倉庫側の誰が受信し、その後どのように倉庫システム側へ情報を反映しているか（Slide 13の「手作業で修正するケース」の具体的実態） | 2章・14章 |
| Q39. `TR_INV_DTL.QTY_STK_IN`を更新する`PrStkInReportImportBatch`（Phase 8-D確認済み）が読み込むFileは、Logizero発行のFileか、それとも別の手段（手入力・別システム）で作成されるFileか | 6章・7章 |

---

## 23. External Specification Questions（Logizero/Tempostar Vendorへの確認事項）

Ernest（社内技術担当）では確認できない、外部Vendor仕様に関する確認事項。想定確認先を明示する。

| 論点 | 想定確認先 |
|---|---|
| Logizeroは公式API・Webhook等、SFTP以外の非Browser Interfaceを提供しているか。提供している場合、対象は在庫Exportのみか、Arrival/入荷実績等も含むか | Logizero Vendor |
| 現在`MIMOSA_`系Batchが使用しているSFTP接続は、正式な契約・仕様書に基づくものか。今後の拡張（在庫以外のData種別）に利用可能か | Logizero Vendor（またはErnest経由で契約内容を確認） |
| Tempostarは公式API・SFTP等、Browser Automation以外の商品/在庫CSV取込手段を提供しているか | Tempostar Vendor |
| Tempostar CSV Import画面（商品/在庫）のフォーマット・列定義は、将来のBatch側の変更（列追加等）に対してVendor側からの事前通知があるか | Tempostar Vendor |

---

## 24. Risks

- **Selenium実装の脆弱性リスク**: 12章のとおり、固定XPath・固定Waitに依存しており、LogizeroまたはTempostarの管理画面がUI変更された場合、Batchが無警告で失敗し得る（11章のError Handling不足と複合）。
- **Batch名と実処理の乖離による誤解リスク**: 16章の`InvTempostarStkUploadBatch`（実際は価格Folder）のような命名不一致は、将来の保守（本チーム含む）が誤った前提でCodeを変更するリスクを生む。
- **2系統の連携方式併存による運用リスク**: Selenium方式とSFTP方式のどちらが本番か不明な状態が続くと、意図せず両方が動作し重複Importが発生するリスク、または片方が実は既に死んでいるのに気づかれないリスクがある。
- **Stock In・Warehouse Stockの不整合が発見されないリスク**: 7章・13章のとおり両者を結ぶKeyが無いため、仮に大きな乖離が生じても、既存の`StkQtyDiscrepancyCheckerService`はこの種の乖離（経路が違う2つの数値の突合）を検出できない（同Serviceが比較しているのはLogizero由来Snapshot同士であり、`QTY_STK_IN`とは無関係）。
- **差異検知はあるが対応が属人化しているリスク**: 11章のとおりEmail通知のみで、対応記録が残らないため、同じ差異が繰り返し発生していても気づきにくい。

---

## 25. Recommended Next Step

提案のみ、確定ではない。

- 21章のCUSTOMER REVIEW（Theme L）・22章のErnest Questionの回答を待つ。
- 20章のFoundation A（Arrival/Warehouse Stock/Discrepancy Visibility）は、他Optionへの前提を作らずLegacy Writeも伴わない最小範囲であり、次Phase候補として検討しやすい。特にOption C（Discrepancy Visibility）は判定Logic自体が既にSource上に存在するため、実装コストが低い可能性がある。
- Option E（連携方式見直し）・Option F（双方向連携）は、23章のExternal Specification確認が完了するまで着手しない。

---

## 26. Final Classification Table

| # | 論点 | 分類 | 状態 |
|---|---|---|---|
| W-1 | Slide 13「G-SYS→倉庫」連携の具体的要望内容 | Ernest/Gulliver確認要（探索段階） | 2章 |
| W-2 | Selenium方式/SFTP方式のどちらが本番経路か | Ernest確認候補（Q35） | 10章・12章・22章 |
| W-3 | WH_CD 4〜15の意味 | Ernest確認候補（Q36） | 8章 |
| W-4 | 差異Email受信後の実際の対応実態 | Ernest確認候補（Q37） | 11章・22章 |
| W-5 | ETA_WH Email受信後の倉庫側反映実態 | Ernest確認候補（Q38） | 14章・22章 |
| W-6 | Stock In Report Fileの発行元 | Ernest確認候補（Q39） | 7章・22章 |
| W-7 | Arrival Visibility機能の要否 | D（Future Decision） | 21章L-1 |
| W-8 | Warehouse Stock Visibility機能の要否 | D | 21章L-2 |
| W-9 | Discrepancy Visibility機能の要否・運用ルール | D | 21章L-3 |
| W-10 | G-SYS→倉庫 双方向連携の自動化範囲 | D | 21章L-4 |
| W-11 | 連携方式の将来統一方針 | D（External Specification確認後） | 21章L-5b・23章 |
| W-12 | Logizero公式API有無 | External Specification | 23章 |
| W-13 | Tempostar公式API有無 | External Specification | 23章 |

**変更したFrontend/Backend/DB Migration/Legacy Source: 0件。** 本Documentと既存3 QA Documentの更新のみ。
