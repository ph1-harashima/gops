# Legacy Procurement Workflow Reverse Engineering（Phase 7-A）

**Status**: 調査完了（2026-08-28）。本Phaseでは実装を行っていない（Prototype側・Legacy側ともSource Code変更ゼロ、Docsのみ）。
**調査対象**: `phasep-gulliver/gulliver`（お客様受領の現行G-SYS Source一式、Java 8 / Spring Boot 1.x世代、208 Javaファイル）
**調査方針**: 「CUSTOMER REVIEWにする前に、現行Sourceから分かるものは徹底的に調べる」。全記述にSource根拠（ファイル:行）を付し、Confidence 3分類（CONFIRMED / HIGH CONFIDENCE / UNKNOWN）で整理する。

---

## 1. Executive Summary

現行G-SYSの購買業務は **「Excelが業務の主役、G-SYSはExcelの写像を保持するSystem of Record」** という構造である。最重要の発見は以下の6点。

1. **POのStatusは「承認Workflow」ではなく「どのExcelを最後にImportしたか」で決まる。** INITIAL→OFFICIALの遷移は、人がG-SYS上で承認ボタンを押すのではなく、**Official PO形式のExcelをImportフォルダに置くことそのもの**が契機である。G-SYS内に承認機能・承認画面・承認Roleは存在しない。
2. **PO再Importは常にDelete & Recreate。** 同一PO No.の再Importは、既存のTR_PO/TR_PO_DTLを物理DELETEしてから作り直す。Merge・差分Update・Optimistic Lockはない。訂正＝「直したExcelをもう一度Importする」が現行の正式な訂正手段である。
3. **PO No.はExcel側で採番され、G-SYSはValidateと分解のみ行う。** G-SYS内にPO No.採番Logicは存在しない（Credit PO No.とArrival Code＝入荷コードのみG-SYSが生成する）。
4. **実POのStatusにSTOCKINは実質使われていない。** `STOCKIN`を実POに設定するコードは存在せず（Bill-only用のDummy PO作成時のみ）、入荷進捗はPOではなく**Invoice（TR_INV）のStatus（TRANSIT→RECEIVING→STOCK_IN）**で管理される。POはOFFICIALのまま留まる。
5. **数量差異（発注≠入荷）はErrorではなく、Credit POという「差分伝票」を自動生成して吸収する。** Stock-in Report Importが invoice数量と入荷実績の差を検出すると、`元PO番号-入荷コード` 形式のCredit PO/Invoiceを自動生成する（数量は差分＝マイナスにもなる）。
6. **Excel Upload競合制御は存在しない。** ImportファイルにVersion/Timestamp検査はなく、古いExcelを再Uploadすれば古い内容で上書き（Delete & Recreate）される。防御は「OFFICIAL化済POへのINITIAL Import拒否」「Invoice作成済POのDELETE拒否」「Stock-in済数量を下回る変更の拒否」という**業務進行度ベースの巻き戻り防止**のみ。

新Portalへの示唆：ユーザー仮説（14章）の大半は現行Sourceと整合するが、「PO正式化＝管理者承認」（仮説9）だけは現行に存在しない新規要件であり、現行の「OFFICIAL Excel Importが正式化」というモデルとの接続設計が必要である。

---

## 2. PO Lifecycle

### 2.1 Status定義

`Const.java:236-242` で3 Status + 2 Typeのみ定義：

```java
TR_PO_STATUS_INITIAL  = "INITIAL"
TR_PO_STATUS_OFFICIAL = "OFFICIAL"
TR_PO_STATUS_STOCKIN  = "STOCKIN"
TR_PO_PO_TYPE_DUMMY   = "DUMMY"
TR_PO_PO_TYPE_CREDIT  = "CREDIT"
```

### 2.2 Status遷移の全設定箇所（Source網羅）

`trPo.setStatus(...)` はSource全体で**4箇所のみ**：

| # | 箇所 | 設定値 | 意味 |
|---|---|---|---|
| 1 | `PrInitialPoImportBatch.java:624` | INITIAL | Initial PO Excel Import時に新規作成 |
| 2 | `PrOfficialPoImportBatch.java:937` | OFFICIAL | Official PO Excel Import時に（Delete後）新規作成 |
| 3 | `PrStkInReportImportBatch.java:793` | OFFICIAL | 入荷差異から自動生成されるCredit PO（PO_TYPE=CREDIT） |
| 4 | `PrBLInvImportBatch.java:1688` | STOCKIN | **Bill-only（運賃等）用Dummy POの新規作成時のみ**。実POの遷移ではない |

**CONFIRMED**: 実POがINITIAL→OFFICIALに「遷移」するコードは存在しない。Official Importは既存INITIAL POを**DELETEして**OFFICIAL POを**新規作成**する（4章）。実POがSTOCKINに更新されるコードも存在しない。

### 2.3 Statusごとの回答（指示1章の10項目）

1. **INITIALはいつ作られるか** — Initial PO Excel（発注書の初期版）をImportフォルダに置き、`PrInitialPoImportBatch`が実行された時。Excel 2行目の発注日欄に "init" という文字が含まれることがInitial PO判定条件（`PrInitialPoImportBatch.java:285-288`）。
2. **OFFICIALへ変わる契機** — Official PO形式のExcel（発注日が実日付、単価・通貨入り）を`PrOfficialPoImportBatch`がImportした時。
3. **OFFICIAL化は人の承認なのか** — **いいえ（CONFIRMED）**。承認画面・承認API・承認Roleは存在しない。「Official Excelを作ってImportフォルダに置く」という行為が事実上の承認である。
4. **Excel Importなのか** — はい。PO作成・更新経路はExcel Import Batchのみ。POを作成/編集するWeb画面・APIは存在しない（Web UIはarrival-list/stock-list等の参照・入荷訂正系のみ。`resources/templates/all-user/`）。
5. **Batchなのか** — はい。`PrInitialPoImportBatch`/`PrOfficialPoImportBatch`/`PrCreditPoImportBatch`（`main()`起動のSpring Boot Batch、Importフォルダ監視型）。
6. **既存POのUpdateなのか新規作成なのか** — **Delete & Recreate（CONFIRMED）**。`PrOfficialPoImportBatch.java:884-948`: 既存TR_PO_DTL全行DELETE→TR_PO DELETE→flush→新規INSERT。Initial側も同様（`PrInitialPoImportBatch.java:584-633`）。
7. **STOCKINへの遷移条件** — 実POには存在しない。入荷完了はTR_INV.STATUSがSTOCK_INになることで表現される（`PrStkInReportImportBatch.java:1100-1101`: 未入荷数量が残っていないInvoiceのみSTOCK_INに遷移。残っていればRECEIVINGのまま＝部分入荷状態）。
8. **Status変更時の関連更新** — Import成功のたびに `BusinessLogicUtil.refreshMsStkTransactions(poNo)`（MS_STKのPOスロット再構築）、`refreshTrArr(poNo)`（TR_ARR入荷一覧の再生成）、`refreshMsStkShipQtyTransactions(poNo)`（Transit数量再集計）、`HisArrListRepository.saveHistory(...)`（TR_ARRのSnapshot保存）が連鎖する。
9. **Statusを戻すことが可能か** — OFFICIAL→INITIALへ戻す手段はない。`PrInitialPoImportBatch.java:306-308`: OFFICIAL/STOCKIN登録済のPO No.に対するInitial Importは「OFFICIAL PO IS ALREADY REGISTERED. INITIAL PO CAN NOT BE UPDATED.」でReject（**CONFIRMED**）。
10. **Delete/Cancel概念があるか** — ある。Excelヘッダーの所定セル（3行目・11列）に「DELETE」と記入してImportすると物理削除（`Const.KEY_DELETE`）。ただしOfficial側はInvoice作成済だと「INVOICE IS ALREADY CREATED. PO CAN NOT DELETED.」でReject（`PrOfficialPoImportBatch.java:390-395`）。論理削除用DEL_FLGカラムはあるがPO Importでは物理DELETEが使われる。

---

## 3. Initial PO

- **File形式**: .xlsx（POIで読む。シート1枚目）。ヘッダー検証: "PURCHASE"（2行目）、"ORDER TO"（7行目）、"PURCHASER"（14行目）等の文言一致（`PrInitialPoImportBatch.java:249-280`）。
- **Key**: PO No.（4行目・10列目、最大30桁）。
- **Initial判定**: 発注日欄に "init" 文字列（大文字小文字不問）。
- **Delivery Week**: **"TBD"のみ許可**（`:350-352`「DELIVERY WEEK IS INVALID.」）。Delivery Dateは**入力禁止**（`:359-363`「DELIVERY DATE IS NOT NEEDED IN INITIAL PO.」）。つまりInitial POは納期未定が前提。
- **単価・金額**: 読み取りコードは全てコメントアウト済（`:403-412, 508-541`）。**Initial POは数量のみ**でTR_PO_DTL.PRC_UNITはNULL、TR_PO.AMT_TTLもNULL。
- **明細Validation**: Item Master存在チェック（存在しない場合はError + `M_INI_PO2`グループへ通知メール自動追加）、旧Item ID拒否、セット品はセット数量の倍数のみ許可。
- **DB反映**: TR_PO（STATUS=INITIAL）+ TR_PO_DTL新規作成 → MS_STKのPOスロット反映（PO_NO_n / PO_QTY_n / ORDR_WEEK_n / DELIV_WEEK_n）。

---

## 4. Official PO

### 4.1 Import内容（Initialとの差分）

- 発注日は実日付必須（"init"を含むとReject: 「THIS IS NOT A VALID OFFICIAL PO.」）。
- Delivery Week/Delivery Date両方必須。
- **単価・通貨必須**。通貨はセルの書式（¥/$等の表示形式文字列）から判定し、`MS_CCY`マスタと突合。1ファイル内に複数通貨が混在するとファイル単位Reject（`:684-687`）。
- 明細の追加列: 原産国（CntryOrg1）、Box寸法3辺、重量 — これらは**MS_ITEM（商品マスタ）側に書き戻される**（`:979-1003`）。Official PO ImportはItem Masterのメンテ経路を兼ねている。
- **Invoice列**: PO Excelの右側（17列目以降）にInvoice No.と数量列を追記でき、ImportするとTR_INV/TR_INV_DTL（STATUS=TRANSIT）も同時生成される。**発注書Excelが出荷案内を兼ねる**運用（`:1063-1192`）。

### 4.2 同一PO No.再Importの挙動（指示2章の核心）

**CONFIRMED: Delete & Recreate。** Reject/Merge/Duplicate Errorではない。

`PrOfficialPoImportBatch.java:884-948`の処理順：
1. 既存TR_PO_DTL全行を物理DELETE（各行のMS_STKスロットもclear）
2. 既存TR_POを物理DELETE
3. （Excelの該当Invoice列があれば）該当TR_INV/TR_INV_DTLもDELETE（ETA等の既存値は`preserveTrInvHolder`に退避し、再作成時に引き継ぐ）
4. flush & clear
5. Excel内容どおりに全て新規INSERT（STATUS=OFFICIAL）
6. TR_ARR再生成（`deleteForNonTransactionTrArr`→`refreshTrArr`）+ MS_STK再構築 + HIS_ARR_LIST Snapshot

再Importを制限するガードは業務進行度ベースの3つのみ：
- Stock-in済のItemをPOから削除しようとすると Reject（`:698-700`「ITEM CD = X IS ALREADY STOCK IN. CAN NOT REMOVED FROM PO.」）
- Stock-in済InvoiceのTransit数量変更は Reject（`:734-737`）
- Invoice作成済POのDELETEは Reject（`:392-394`）
- Arrival数量 > PO数量 も Reject（`:812-814`）、Arrival数量 < Stock-in済数量も Reject（`:817-819`）

---

## 5. Stock-in

- 入荷実績はLogizero（倉庫システム）由来のStock-in Report Excelを`PrStkInReportImportBatch`がImportする。
- TR_INV_DTL.QTY_STK_INに入荷数量が入り、**Invoice内の全明細が入荷済になった時のみ** TR_INV.STATUS=STOCK_IN（`:1100-1101`）。一部のみ入荷ならRECEIVINGのまま（＝**部分入荷はInvoice Statusで表現**）。
- 入荷時にMS_ITEM.LAST_STK_IN_DATE更新、寸法・輸入関連属性のItem Master書き戻し、MS_STK ARRスロットからの控除（`BusinessLogicUtil.refreshMsStkTransactionsIternal`の「REMOVE FROM ARR QTY」節）。
- **PO自体のStatusは変わらない**（OFFICIALのまま）。

---

## 6. PO Number Rule

**CONFIRMED: G-SYSは採番しない。Excel側で採番され、G-SYSは分解・Validateのみ行う。**

`BusinessLogicUtil.java:1028-1069`（Source根拠そのもの）：

```java
getSupplierCd(poNo) = poNo.substring(0, 4)   // 先頭4桁 = Supplier Code
getBrandCd(poNo)    = poNo.substring(5, 8)   // 6-8桁目 = Brand Code（5桁目は区切り文字）
getIdCd(poNo)       = poNo.substring(9, 11)  // 10-11桁目 = ID Code（9桁目は区切り文字）
```

- 想定形式: `SSSS-BBB-II...`（最大30桁、`LEN_TR_PO_PO_NO=30`）。11桁目以降（連番・日付等）の規則は**G-SYS Sourceに一切現れない**（UNKNOWN — Excelテンプレート側の規則）。
- Import時のValidation: `getIdCd()`がnull（=11桁未満）なら「INVALID PO FORMAT」、Supplier Code部が`MS_COMM(MS_SUPPL)`に無ければ「SUPPLIER CODE IN PO# IS INVALID.」、Brand Code部が`MS_COMM(MS_BRAND)`に無い/Excel記載のBrand名と不一致なら「BRAND CODE IN PO# IS INVALID.」。
- Check Digitは存在しない。Initial POとOfficial POのPO No.形式差も存在しない（同一PO No.を使い続ける）。
- **G-SYSが生成する番号は2つだけ**：
  - **Arrival Code（入荷コード）**: `供給元Code + 3桁連番`（`BusinessLogicUtil.getNewArrCode():1166-1195`。連番はMS_COMM(MS_SUPPL).VAL_10に保持し払い出し）
  - **Credit PO No.**: `元PO No. + "-" + Arrival Code`（`creditPoNo():1099-1107`）。Credit Invoice No.も同形式。（旧形式 `-C` サフィックスのLogicはコメントアウト済＝廃止）

---

## 7. PO Correction / Re-import

| 変更内容 | 可否 | 根拠 |
|---|---|---|
| 数量変更 | ○ 再Importで可能（Delete&Recreate） | 4.2章。ただしArrival数量>PO数量になる変更・Stock-in済数量を下回る変更はReject |
| 単価変更 | ○ 同上（Official再Import） | 4.2章 |
| 納期変更（Deliv Week/Date） | ○ 同上。加えてTR_ARR側のETA/ETA-WHはWeb画面から個別訂正可能 | 4.2章、11章 |
| 明細追加 | ○ 再Importで可能 | 4.2章 |
| 明細削除 | △ 可能だがStock-in済Itemの削除はReject | `PrOfficialPoImportBatch.java:698-700` |
| PO取消 | △ DELETE記入Importで物理削除。ただしInvoice作成済はReject | 2.3章#10 |

変更時の波及（Official再Import 1回で全て連鎖）: TR_PO/TR_PO_DTL（作り直し）→ TR_INV/TR_INV_DTL（該当Invoice列があれば作り直し、ETA等は引継ぎ）→ TR_ARR（`deleteForNonTransactionTrArr`+`refreshTrArr`で再生成、ETA-WH・通関・費用等の既存手入力値はArrival Code経由で引継ぎ `BusinessLogicUtil.java:637-700`）→ MS_STK（POスロット再構築）→ HIS_ARR_LIST（Snapshot追記）。

---

## 8. Credit PO

**CONFIRMED: Credit POの一義的な意味は「入荷数量差異の訂正伝票」である。**

生成フロー（`PrStkInReportImportBatch.java:700-873`）：
1. Stock-in Report Importが、Invoice明細数量（QTY）と入荷実績数量（QTY_STK_IN）の差異（Discrepancy）を検出
2. 差異があるArrival Code単位で、`元PO No.-ArrivalCode` のCredit POを**自動生成**（PO_TYPE=CREDIT、STATUS=OFFICIAL、数量=入荷数量−Invoice数量 → **負数になり得る**＝Short Shipmentは負数量のCredit明細）
3. 対応するCredit Invoice（TRAN_TYPE=70/CREDIT）も自動生成。単価は元明細から複製（見つからなければ0で作成し、Alert対象）
4. その後、購買担当がCredit PO Excel（単価・明細情報を記入したもの）を`PrCreditPoImportBatch`でImportすると、**自動生成済のCredit PO/Invoice明細に単価・金額が上書き**される（新規作成ではなく既存Credit明細のUPDATE。`PrCreditPoImportBatch.java:661-705`）

- 元POへのLink: PO No.の命名規則そのもの（`nonCreditPoNo()`は`-#`区切りで元PO No.を復元 — 実データ上のArrival Code区切りは`-`だが、コード上の逆変換は`-#`を探す実装。**この非対称は要注意（SOURCE上の潜在的不整合、HIGH CONFIDENCE: 実運用ではArrival Codeからの逆引き`trArrRepository.findByArrCode...`が主経路**）。
- 会計面: Credit Invoice（TRAN_TYPE_CREDIT=70）としてInvoice体系に乗り、ETA等は元Invoiceから複製（`BusinessLogicUtil.java:749-771`）。
- **新Portalへの評価**: Credit POは「取消」ではなく「数量確定差異の事後訂正」に相当する。Portalの「メーカー回答で数量が減った」ケースとは発生タイミングが異なる（Legacyは入荷後、Portalは発注直後）が、「元Orderに紐づく差分Recordを別伝票として起こし、元は書き換えない」という思想は、PortalのAttention/訂正設計の直接の参考になる。

---

## 9. PO → Invoice → Arrival

### 9.1 データ連鎖（CONFIRMED）

```
TR_PO / TR_PO_DTL          発注（数量=QTY_PO）
  ↓ Official PO ExcelのInvoice列 or BL/INV Import
TR_INV / TR_INV_DTL        出荷案内（QTY_ORDR=発注数量の写し, QTY=出荷数量, QTY_STK_IN=入荷済数量）
TR_INV_PO                  Invoice-PO関連（送金・Bill Type等）
  ↓ BL/INV Import（船積）
TR_BL / TR_BL_DTL          船荷証券
  ↓ refreshTrArr()
TR_ARR                     入荷管理一覧（PO×Invoice単位。ETA/ETA-WH/通関/費用/倉庫検品）
  ↓ Stock-in Report Import
MS_STK                     在庫（POスロットから控除、在庫数量へ）
```

### 9.2 数量不一致の扱い（Order 10 / Invoice 9 / Arrival 8 の例）

- **PO 10 vs Invoice 9**: 許容される（Invoice数量合計がPO数量以下であることだけ検証 `PrOfficialPoImportBatch.java:812-814`）。**残数量1はMS_STKのPOスロット（PO_QTY_n）に残り続ける** — `refreshMsStkTransactionsIternal`はPO数量を全量スロットに置き、Invoice化された分だけ`substractPoQty`→`addArrQty`で移すため、未Invoice分がOpen POとして自然に残る（`BusinessLogicUtil.java:203-244`）。自動Close・Error化はしない。
- **Invoice 9 vs Stock-in 8**: Stock-in Report Importが差異-1を検出し、**Credit PO/Invoice（数量-1）を自動生成**して帳尻を合わせる（8章）。Invoice自体は全明細入荷までRECEIVINGのまま。
- **CONFIRMED**: 「自動Close」「強制Error」はどちらも存在しない。残はOpen POとして残るか、Credit POで明示的に相殺されるかの2択。

---

## 10. Partial / Unfulfilled Supply

| 概念 | 現行G-SYSでの表現 | 根拠 |
|---|---|---|
| Partial Delivery（分納） | 1 POに複数Invoice（Official PO Excelに複数回Invoice列を追記して再Import）。MS_STKのSHIP_NO_1..10/SHIP_QTY_1..10スロットに便単位のTransit数量 | `refreshMsStkShipQtyTransactions` |
| Open PO（発注残） | MS_STK.PO_NO_1..20 / PO_QTY_1..20 スロットの残数量（未Invoice分） | 9.2章 |
| Short Shipment | Credit PO（負数量）による事後訂正 | 8章 |
| Back Order / 再発注 | **存在しない（CONFIRMED: 該当コードなし）**。残数量の追跡はMS_STKスロット表示のみで、督促・再発注を促す機能はない | — |
| 部分入荷 | TR_INV.STATUS=RECEIVING（全明細入荷でSTOCK_IN） | 5章 |

MS_STKは1商品あたりPOスロット20個・出荷スロット10個の固定枠であり、`PONotAvalableException`（21個目のPOが来た場合）はImport Errorとしてメール通知される（`MsStkRepositoryImpl.java:404`, `PrOfficialPoImportBatch.java:1207-1212`「AVAILABLE PO NO DOES NOT EXISTS」）。

**新Portalへの評価**: 「未納」の一次情報は現行にも存在する（PO数量−Invoice数量、Invoice数量−Stock-in数量）が、それを「問い合わせ・再発注」につなげる業務機能は存在しない。Portal側で新設する場合、Legacyから読み取るべきは MS_STKのPOスロット（既にPortalのopen_po由来）とTR_INV_DTLのQTY/QTY_STK_IN差分である。

---

## 11. Arrival / ETA Correction

**CONFIRMED: 確定後の入荷情報訂正は現行でも日常操作として存在する。**

- Web画面（arrival-list/arrival-detail, Handsontable型インライン編集）から`/api/updateEtaWh`等のREST APIでTR_ARRの個別フィールドを直接UPDATE（`AllUsersRestController.java:841-921`, `ArrivalCustomQuery.updateFromHandsonTable`）。
- 訂正のたびに：
  - `UPDATE_USER_ID`/`UPDATE_DATETIME`を更新（`ArrivalCustomQuery.java:89-91`）
  - **`HisArrListRepository.saveHistory(...)`でTR_ARR行全体のSnapshotをHIS_ARR_LISTへINSERT**（`AllUsersRestController.java:903-921ほか多数`）
- ETA-WH（倉庫着予定日）確定時は`ETA_WH_SEND_FLG=true`を立て、`PrEtaWhMailBatch`が後続で倉庫向けメールをSYS_SEND_MAILにキューイングし、送信後フラグを倒す（`PrEtaWhMailBatch.java:87-99`）。メッセージ本文はユーザーが画面から入力可能（`updateEtaWhSendFlg` API, `:1069-1115`）。
- Stock-in Date等もImport/画面から更新され、そのたびにMS_STK再計算（`refreshMsStkTransactions`連鎖）が走る。

**新Portalへの示唆**: 「Supplier Response確定後の訂正」に対する現行の答えは「訂正可能。ただし全訂正でSnapshot履歴＋更新者/日時を残し、下流（在庫・メール）への再計算を必ず連鎖させる」である。確定の不可逆性より**履歴付き可逆性**が現行の思想。

---

## 12. Excel Download / Upload Conflict

| 機構 | 有無 | 根拠 |
|---|---|---|
| Optimistic Lock | **実質なし（CONFIRMED）** | 全Entityの`@Version`フィールドに`@Transient`が付いており永続化されない＝JPAの楽観ロックは無効化されている（`TrPo.java:28-30`他全Entity共通） |
| Update Date/Version検査 | なし | Importは無条件Delete&Recreate。Excel内にVersion/DL時刻の欄もない |
| Duplicate Check | ファイル内のみ | `checkDuplicateKey()`は同一Import内の`PO:Item`重複検出のみ（`AbstImportBatch.java:347-368`） |
| File Process ID | あり | Import実行ごとに`yyyyMMddHHmmss`のprocessIdをファイル名に付与しwork/へ移動（`AbstImportBatch.java:99,113-116,620`） |
| Backup | あり | 成功ファイルは`backup/`へ`元名_processId.xlsx`で保存、失敗ファイルは`error_`プレフィックスでupload/へ戻す（`:656-673,803-830`） |
| Import Log | Mail通知のみ | 成否・エラー明細はSYS_SEND_MAIL経由の通知メール本文（DB上の構造化Import Logテーブルはない） |
| 更新者記録 | Excel著者メタデータ | `getLastModifiedBy()`がxlsxのlastModifiedByプロパティをMS_USER.USER_NAME_AWSと突合してユーザー特定（`:525-545`）— ただしPO Import 3種では未使用（`getCreateUserId`はプログラムIDを記録） |

**CONFIRMED: 「Download後にPortal側で更新されたPOを、古いExcelから再Upload」した場合、現行G-SYSはこれを検出できず、古い内容で上書きされる。** 唯一の防波堤は業務進行度ガード（Stock-in済数量を下回る等）のみ。

**新Portal側のConflict Control候補（設計のみ、実装しない）**：
1. Excel Download時に各行へ`updated_at`（またはrevision番号）を埋め込み、Upload時に現在値と比較して不一致ならReject/警告（楽観ロックのExcel版）
2. Download操作自体を記録し（誰が・いつ・どのPO）、Uploadとの突合を可能にする
3. Upload時のdiffプレビュー（Delete&Recreate前に現在値との差分を提示して確認させる）
4. Portal管理POはPortal側をSystem of Recordとし、Excel Uploadは「取込申請」としてADMIN承認後に反映（仮説9との整合）

---

## 13. Supplier / Brand Master

Supplier/Brandは専用テーブルではなく汎用コードマスタ`MS_COMM`（CATE_ID + CODE_ID + VAL_1..VAL_10）に格納（**CONFIRMED**）。

| 項目 | 存在 | 場所 |
|---|---|---|
| Supplier Code/名称 | ○ | MS_COMM(CATE_ID=MS_SUPPL)。CODE_ID=4桁Supplier Code、CODE_NAME=名称 |
| Brand Code/名称 | ○ | MS_COMM(CATE_ID=MS_BRAND)。VAL_7=進行中Arrival Code一時保持にも流用 |
| Arrival Code連番 | ○ | MS_SUPPL.VAL_10（6章） |
| Lead Time | △ | **Supplier Masterにはない**。MS_ITEM.LEAD_TIME（商品単位、"6SP"等の地域+月数コード）+ `Formula.java:935-`の係数表。CATE_ID=LEAD_TIMEコード定義あり |
| **Supplierメールアドレス** | **×（CONFIRMED: 存在しない）** | MS_SUPPLのVAL列にメールアドレスを読むコードは皆無。現行はメーカーへの発注書送付をG-SYS外（人手のメール/Excel添付）で行っている |
| 通知メール宛先 | ○（社内向けのみ） | MS_COMM(CATE_ID=M_INI_PO/M_OFF_PO/M_CRD_PO等).VAL_1にImport結果通知の社内宛先リスト |
| Currency | ○ | MS_COMM(MS_CCY)。VAL_1=通貨記号（Excel書式判定用） |
| 日本/海外判定・発注先区分 | ×（Supplier単位では存在しない） | Lead Timeコードの地域プレフィックス（SP/EU/US）が商品単位に間接的に存在するのみ |

**新Portalとの差分**: Portalのメーカー送付（To/CC/件名）に必要な「Supplierごとの担当者メール・CC」は現行Masterに存在せず、**新規Master項目が必要（CUSTOMER REVIEW #6）**。

---

## 14. Mail Capability

**CONFIRMED: キュー型メール基盤が存在し、再利用可能性は高い。**

- **キュー**: `SYS_SEND_MAIL`テーブル（SEND_TO_GRP / SEND_TO / SEND_FROM / SEND_CC / SEND_BCC / SUBJECT / CONTENTS(最大2万字) / ATTACHED(ファイルパスリスト) / SEND_FLG / SEND_DATETIME）。
- **送信**: `SysSendMailBatch`が未送信（SEND_FLG IS NULL）を古い順に送信し、成功後SEND_FLG=true+送信日時を記録。JavaMail/MimeMessage、UTF-8、Excel添付対応（`SysSendMailBatch.java:74-168`）。
- **宛先解決**: SEND_TO_GRPにMS_COMMのCATE_IDを入れるとそのカテゴリのVAL_1全員に展開（グループ配信）+ SEND_TO直接指定の合算（`:210-228`）。
- **Template**: 本文はコード内組み立て（Template Engineなし）。
- **Retry/Failure**: 明示的リトライなし。送信失敗はSEND_FLGがNULLのまま残る（次回Batchで再試行される構造）+ `CheckSMTPErrorFromDailyBatch`によるSMTPエラー監視メールあり。
- **From**: `Const.MAIL_ADD_FROM = "e-sys@glv.ne.jp"` 固定 + MS_COMM(MS_MAIL).VAL_2による上書き。
- **注意点**: `SysSendMailBatch.initialize()`に開発者向け許可リスト（実メール抑止のDEBUGコード痕跡）が残っており、本番挙動はコメントアウトで制御されている。流用時は要整理。

**評価**: 「メーカー向けPO送信」への再利用は技術的に可能（キュー・添付・CC/BCC・グループ配信あり）。不足はSupplierメールMaster（13章）とHTML/Template機能のみ。

---

## 15. Permission / Approval

- User Typeは11種（`Const.java:12-22`）: SYS_ADMIN / SLS_ADMIN / MANAGEMENT / PURCHASE / LOGISTIC / WH_USER / WH_LOG / EC_USER / PRC_USER / ACCOUNT / OPERATOR。
- **アクセス制御は粗い（CONFIRMED）**: `WebSecurityConfig.java:70-85` — `/api/**`は**全User Typeがアクセス可**、`/common-master/**`（コードマスタ管理）のみSYS_ADMIN限定。コントローラ内の個別Roleチェックは倉庫レポート系のWH_USER判定2箇所のみ（`UsersController.java:298,516`）。
- **PO作成/修正/Import/承認のRole制御は存在しない**。Importはそもそも認証の外（ファイル設置＋Batch実行）であり、誰がファイルを置けるかはOS/共有フォルダ権限の世界。
- PO Draft相当機能: なし（Initial POが実質のDraftだが、G-SYS内で編集はできない）。
- Official化可能Role: 該当なし（2.3章#3）。
- 管理者承認相当: 該当なし。

**新Portal 2 Role（OPERATOR/ADMIN）へのMapping評価**: 現行の11 Typeは「画面の見え方の違い」程度にしか使われておらず、権限モデルとしてPortalが引き継ぐべき資産は薄い。PURCHASE≒Portal OPERATOR、SYS_ADMIN/MANAGEMENT≒Portal ADMINという対応は自然に置けるが、**「ADMINだけがOfficial化（承認）できる」というPortal仮説9の権限は現行に前例がなく、新規設計**となる（CUSTOMER REVIEW #1）。

---

## 16. Audit / History

| 対象 | 履歴機構 | 粒度 | Before/After |
|---|---|---|---|
| TR_ARR（入荷） | `HIS_ARR_LIST`へ行Snapshot INSERT（`HisArrListRepository.saveHistory` — NativeQueryでTR_ARR全カラム複製） | 更新のたび1行（全列） | Snapshot比較で追跡可能（フィールド単位のBefore/Afterは持たない） |
| MS_STK/在庫 | `HIS_STK_LIST`へ同様のSnapshot | 同上 | 同上 |
| TR_PO / TR_PO_DTL | **履歴なし（CONFIRMED）**。Delete&Recreateのため旧内容はDBから消失。復元手段はExcelのbackup/フォルダのみ | — | — |
| 全テーブル共通 | CREATE_USER_ID/CREATE_DATETIME/UPDATE_USER_ID/UPDATE_DATETIME | 最終更新のみ | なし |
| Import操作 | 通知メール本文 + backupファイル | ファイル単位 | — |

- 「誰が」: Web画面更新はログインUSER_ID、Batch ImportはプログラムID（`getCreateUserId`）。Excel著者から実ユーザーを引く仕組み（`getLastModifiedBy`）はStock-in Report等でのみ使用。
- **新PortalのAudit設計との整合**: PortalのAuditEvent（フィールド単位Before/After+行為者+日時）は、現行のSnapshot方式より細粒度で上位互換。ただし**現行はPO本体の変更履歴を一切持たない**ため、Portal側がPO変更履歴のSystem of Recordになることに競合はない（むしろ空白を埋める）。

---

## 17. Source-confirmed Business Rules（CONFIRMED一覧）

1. PO StatusはINITIAL/OFFICIAL/STOCKINの3値だが、実POはINITIAL→OFFICIALのみで、OFFICIALが終端。STOCKINはBill-only Dummy PO専用。
2. OFFICIAL化＝Official PO Excel Importそのもの。承認機能は存在しない。
3. PO再Importは物理Delete & Recreate。TR_PO/TR_PO_DTLに変更履歴はない。
4. OFFICIAL/STOCKIN登録後のPO No.へのInitial ImportはReject（格下げ不可）。
5. PO No.はExcel側採番。G-SYSは先頭4桁=Supplier、6-8桁=Brand、10-11桁=ID Codeの分解とMaster突合のみ。
6. PO削除はExcelのDELETE指定。Invoice作成済はReject。Stock-in済Itemの明細削除もReject。
7. Invoice数量合計はPO数量以下、Stock-in済数量以上でなければReject（巻き戻り防止）。
8. 未Invoice残数量はMS_STKのPOスロットにOpen POとして残り、自動Close・Errorはない。
9. 入荷完了はTR_INV.STATUS（TRANSIT→RECEIVING→STOCK_IN）で管理。全明細入荷までSTOCK_INにならない（部分入荷=RECEIVING）。
10. 入荷数量とInvoice数量の差異はCredit PO/Invoice（`元PO-ArrivalCode`、数量は差分で負数可）を自動生成して処理。単価は後からCredit PO Excel Importで補完。
11. Arrival Code（入荷コード）はG-SYSが採番（Supplier+3桁連番、MS_COMM.VAL_10で連番管理）。
12. TR_ARR/在庫の訂正は確定後も可能で、全訂正がHIS_ARR_LIST/HIS_STK_LISTに行Snapshotとして残る。
13. ETA-WH確定はフラグ+メールキュー（SYS_SEND_MAIL）+送信Batchの3段構成。
14. Optimistic Lockは全Entityで無効化されており（@Version+@Transient）、Excel Upload競合は検出されない。
15. メール基盤はDBキュー型で添付・グループ配信対応。ただしSupplier宛メールアドレスMasterは存在しない。
16. 権限は実質「ログインできるか」+「コードマスタ管理はSYS_ADMINのみ」の2段。PO業務の操作権限制御は存在しない。
17. Import基盤は upload→work（processId付与）→backup/error の3フォルダ運用で、結果はメール通知。

## 18. High-confidence Inferences（HIGH CONFIDENCE一覧）

1. **Initial POの業務的意味は「数量だけ先に押さえる仮発注」**。単価読取コードが全てコメントアウトされ、納期はTBD固定であることから、単価・納期が未確定の段階でメーカーに数量を打診するための書式と推定。
2. **Official PO Excelはメーカーとの往復文書を兼ねる**。ヘッダー"ORDER TO"、Invoice列の追記運用（メーカー側が出荷情報を書き足して返す）から、Excel自体がメーカーとやり取りされる帳票と推定。
3. **PO No.の11桁目以降はExcelテンプレート側の自由裁量**（日付や連番と推定されるがG-SYSは関知しない）。
4. **Credit PO Excelは自動生成されたCredit POを「清書」する運用**。Import処理が既存Credit明細のUPDATEのみで新規作成しないことから、必ずStock-in Report起点で発生した後に単価補完する順序と推定。
5. **`nonCreditPoNo()`の`-#`区切りは旧仕様の名残または不完全な実装**で、実運用のCredit逆引きはArrival Code経由が主経路。
6. **Importフォルダへのファイル設置はAWS上の共有ストレージ/転送経由**（MS_USER.USER_NAME_AWS、`findMsUserByUserNameAws`の存在から、AWS WorkSpaces等のユーザー名とG-SYSユーザーを突合する運用と推定）。

## 19. CUSTOMER REVIEW Items（Sourceに存在せず顧客確認が必要）

1. **PO正式化を「管理者承認」に変える件（仮説9）** — 現行は承認機能ゼロ。Portalで承認制にする場合、承認の単位（PO単位か日次バッチか）、否認時の扱い、承認者不在時の代行を要確認。
2. **PO No.の完全な採番規則** — 11桁目以降（連番・日付部）の規則はExcel側にあり、Source から確定不能。正式PO No.をPortalが採番する場合（仮説11）、既存Excelテンプレートの規則の開示が必要。
3. **欠品/長期欠品の正式定義（仮説1）** — 現行Sourceに「欠品」概念は存在しない（在庫0の表示のみ）。「メーカー案内・発注後回答で判明する」という仮説1のフローは現行に対応物がなく、新規業務設計。
4. **メーカー回答差異時の「修正版注文書再送→双方合意Close」（仮説2）** — 現行の対応物はCredit PO（入荷後の事後訂正）のみで、発注直後の合意形成プロセスは存在しない。再送時に同一PO No.を使うか（現行のDelete&Recreate流儀）、版番号を付けるかを要確認。
5. **未納時の問い合わせ・再発注（仮説4）** — Open PO残数量の情報は現行にもあるが、それを起点とする業務機能はない。再発注を新PO（新番号）とするか元POの残数量継続とするかを要確認。
6. **Supplierメール宛先Master** — 現行に存在しない。新規項目（To/CC/担当者名/言語）の管理主体（G-SYS Master画面かPortal専用Masterか）を要確認。
7. **Excel Download/Upload競合制御の採否** — 12章の候補1〜4のどこまでを本番要件とするか。
8. **Credit PO運用のPortal継承範囲** — Portal導入後もStock-in差異のCredit PO自動生成（Legacy Batch）は並存する。Portal発注分にもこの仕組みをそのまま適用してよいか。
9. **確定後訂正の権限** — 現行は誰でも訂正可能（履歴のみ残る）。Portalでは仮説7・8（差し戻し・確定後訂正）にADMIN限定等の制約を付けるかを要確認。
10. **STOCKIN Statusの扱い** — 実POで未使用の現行仕様を踏まえ、Portalの「入荷済」表現をPO Statusに持たせるか、現行同様Invoice/入荷側に持たせるかを要確認。

## 20. Portal Design Implications

1. **System of Record分担（仮説10・13と整合）**: Legacy G-SYSはPO・Invoice・入荷・在庫のSoRであり続け、PortalはWorkflow（Draft→回答→合意）のSoRとする現行方針は、Legacyの「PO変更履歴を持たない」構造とも噛み合う。Portalで合意確定した内容を「Official PO Excel形式」または直接TR_PO/TR_PO_DTL相当として連携する出口設計が本番の核心になる。
2. **PO No.連携**: Portalの`PO-DEMO-...`採番は本番では使えない。`SSSS-BBB-II...`形式（Supplier/Brand/ID Code位置固定）に準拠しないとLegacyのImport Validationを通らない（6章）。採番主体をExcel/人からPortalへ移す場合はCUSTOMER REVIEW #2の規則開示が前提。
3. **「送信」と「正式化」の分離は現行と整合**: 現行でもInitial（打診）とOfficial（確定）は別Importであり、Portalの「Draft→送信→回答→確定」多段Statusは現行業務の粒度を細かくしたものとして自然に接続できる。
4. **訂正は履歴付きで許す**: 現行思想（11章・16章）に合わせ、Portalも確定後訂正を禁止するのではなく、AuditEvent＋再計算連鎖で受け止める設計が現場に馴染む。
5. **Excel共存（仮説12）**: Download/Uploadを残す場合、現行に存在しない競合制御（12章）をPortal側で新設することが必須。少なくともdiffプレビュー（候補3）は低コストで現行のDelete&Recreate事故を大きく減らす。
6. **メール送信**: SYS_SEND_MAILキューへのINSERTという既存I/Fが再利用可能（14章）。Portal本番のメーカー送信は「PortalがSYS_SEND_MAILに積む→既存Batchが送る」構成が最小変更。ただしSupplier宛先Master新設（19章#6）が前提。
7. **Attention/差異処理**: Credit POの「元を書き換えず差分を別Recordで表す」思想（8章）は、Portalの数量差異Attentionの確定処理（合意済差異の記録）と同型であり、用語・画面設計で対応付けて説明すると顧客の現行業務理解と接続しやすい。

---

## 21. Final Classification Table

| # | Topic | Current G-SYS Behavior | Evidence / Source | Confidence | Portal Implication | CUSTOMER REVIEW |
|---|---|---|---|---|---|---|
| 1 | PO Status体系 | INITIAL/OFFICIAL/STOCKIN。実POはOFFICIALが終端 | Const.java:236-238, setStatus全4箇所 | CONFIRMED | Portal Statusは入荷をPOに持たせない現行と整合させるか要設計 | #10 |
| 2 | OFFICIAL化契機 | Official PO Excel Import（承認機能なし） | PrOfficialPoImportBatch.java:937 | CONFIRMED | 承認制導入は新規要件 | #1 |
| 3 | 再Import挙動 | Delete & Recreate（履歴なし） | 同:884-948 | CONFIRMED | Portal側でPO変更履歴を持つ価値大 | — |
| 4 | INITIAL格下げ禁止 | OFFICIAL済POへのInitial ImportはReject | PrInitialPoImportBatch.java:306-308 | CONFIRMED | Portalの逆遷移禁止と同思想 | — |
| 5 | PO No.採番 | Excel側採番。G-SYSは分解・検証のみ（4-3-2桁構造） | BusinessLogicUtil.java:1028-1069 | CONFIRMED | Portal採番は形式準拠必須。全規則は要開示 | #2 |
| 6 | PO削除 | Excel DELETE指定。Invoice済Reject | PrOfficialPoImportBatch.java:383-398 | CONFIRMED | 取消概念の下敷きに使える | — |
| 7 | 数量ガード | Invoice≦PO、Stock-in≦Invoice を強制 | 同:812-819 | CONFIRMED | Portal Validationに移植可能 | — |
| 8 | Open PO | 未Invoice残はMS_STK POスロットに残存。自動Closeなし | BusinessLogicUtil.java:203-244 | CONFIRMED | 未納表示の一次データ | #5 |
| 9 | 部分入荷 | TR_INV.STATUS=RECEIVING（全量入荷でSTOCK_IN） | PrStkInReportImportBatch.java:1100-1101 | CONFIRMED | 入荷進捗はInvoice粒度が現行流 | #10 |
| 10 | Credit PO | 入荷差異の自動生成差分伝票（負数量可）。後からExcelで単価補完 | PrStkInReportImportBatch.java:700-873, PrCreditPoImportBatch.java:661-705 | CONFIRMED | 差異処理・訂正設計の参考 | #8 |
| 11 | Arrival Code採番 | G-SYS採番（Supplier+3桁連番、MS_COMM.VAL_10） | BusinessLogicUtil.java:1166-1195 | CONFIRMED | 採番機構の既存前例 | — |
| 12 | 入荷/ETA訂正 | 確定後も画面訂正可。全訂正Snapshot履歴+ユーザー/日時 | AllUsersRestController.java:841-921, HisArrListRepository | CONFIRMED | 確定後訂正は履歴付き許可が現行思想 | #9 |
| 13 | Excel競合制御 | なし（@Version無効化、時刻検査なし）。古いExcelで上書きされる | TrPo.java:28-30, AbstImportBatch.java全体 | CONFIRMED | Portal側Conflict Control新設が必須 | #7 |
| 14 | Import基盤 | upload/work/backup+error戻し+メール通知+processId | AbstImportBatch.java:547-673 | CONFIRMED | Portal Excel I/Fの下敷き | — |
| 15 | Supplier Master | MS_COMM汎用マスタ。メールアドレス項目なし | Const.java:30, 13章 | CONFIRMED | 宛先Master新設が必要 | #6 |
| 16 | Lead Time | 商品単位（MS_ITEM.LEAD_TIME、地域+月数コード） | MsItem.java:79, Formula.java:935- | CONFIRMED | Portal既存実装と同一 | — |
| 17 | メール基盤 | SYS_SEND_MAILキュー+送信Batch。添付/グループ配信可、Templateなし | SysSendMailBatch.java, SysSendMail.java | CONFIRMED | メーカー送信に再利用可能 | #6 |
| 18 | 権限 | 11 User Typeだが実質2段（一般/管理者）。PO操作権限なし | WebSecurityConfig.java:70-85 | CONFIRMED | OPERATOR/ADMIN 2 Role化は現実に即す | #1,#9 |
| 19 | Audit | TR_ARR/在庫=Snapshot履歴あり。PO=履歴なし | HisArrListRepository, 16章 | CONFIRMED | PortalのField粒度Auditが空白を埋める | — |
| 20 | Initial POの意味 | 単価なし・納期TBDの数量先行打診 | 単価読取コメントアウト, TBD強制 | HIGH CONFIDENCE | 「Draft送信」に相当する既存業務 | — |
| 21 | Excel=メーカー往復文書 | Invoice列追記運用から推定 | PrOfficialPoImportBatch.java:1063- | HIGH CONFIDENCE | メールPDF化しても様式互換が望ましい | #4 |
| 22 | PO No.末尾規則 | Source上に存在しない | — | UNKNOWN | 採番移管の前提情報 | #2 |
| 23 | 欠品の業務定義 | Source上に存在しない | — | UNKNOWN | 仮説1は新規業務設計 | #3 |
| 24 | 未納の督促/再発注 | Source上に存在しない | — | UNKNOWN | 仮説4は新規業務設計 | #5 |

---

## 22. ユーザー仮説（14章）とSourceの突合結果

| 仮説 | Sourceとの関係 |
|---|---|
| 1. 欠品はメーカー案内/回答で判明 | Sourceに欠品概念なし。矛盾はしないが裏付けもない（UNKNOWN→CUSTOMER REVIEW #3） |
| 2. 差異時は修正版注文書再送・双方合意Close | 現行は同一PO No.の再Import（Delete&Recreate）が「修正版再送」に相当。合意Close処理はない（部分整合） |
| 3. 発注と受領は同一PO lifecycle | 整合。TR_PO→TR_INV→TR_ARR→MS_STKが単一PO No.で貫通 |
| 4. 未納時の問い合わせ・再発注 | データはあるが業務機能なし（新規設計） |
| 5. Confirmed Qty=0は取消/供給不可 | 現行の類似物は「Invoice数量0行はskip」「負数Credit」。直接の対応なし（矛盾もなし） |
| 6. 納期変更あり | 整合。ETA/ETA-WH訂正は日常操作（11章） |
| 7. 差し戻しあり | 現行に差し戻しなし（OFFICIAL→INITIAL不可）。Portal新規要件 |
| 8. 回答確定後の訂正あり得る | 整合。現行は確定後訂正を履歴付きで許容（11章） |
| 9. PO正式化は管理者承認が契機 | **現行と不一致（現行は承認なし）**。Source優先で報告：承認制は新規導入となる |
| 10. G-SYSがPOのSoR | 整合 |
| 11. 正式PO No.はG-SYSルール | 部分整合：形式検証はG-SYS、採番はExcel。「G-SYSルール」の実体はExcelテンプレート規則（要開示） |
| 12. Excel Download/Uploadは残す | 整合（ただし競合制御新設が前提） |
| 13. Tempostar/LogizeroはG-SYS経由 | 整合（Inv*/Sl*Tempostar系Batch、Logizero Stk連携Batch群が存在） |
| 14. メーカーとのやり取りは主にEmail | 整合（ただし現行はG-SYSから直接メーカーへは送っていない。社内通知のみ） |
| 15. Portal RoleはOPERATOR/ADMIN | 現行権限の実態（実質2段）と整合 |
