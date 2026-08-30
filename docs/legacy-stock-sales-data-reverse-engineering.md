# Legacy Stock / Sales Data Reverse Engineering & Target Analysis（Phase 8-C、Phase 8-Hで末尾に実装結果追記）

**Status**: Phase 8-C時点はDocs / Audit Onlyのみ。**Phase 8-Hで、本Documentの調査結果に基づき「在庫・販売確認」Current Snapshot Visibility Foundationを実装した**（末尾の「Phase 8-H 実装結果」章参照）。Sales/Stock History蓄積・Trend・Forecast等は今回も未実装のまま。

**目的**: `customer-review-decision-package.md` 16章のModule/Option構成方針を前提に、在庫・販売実績データ更新（Stock/Sales Data Update）を、Ordering・Price Changeと並ぶ独立した改善Optionとして提案できるかどうかを、Legacy Source・既存Document・Gulliver打ち合わせ原文に基づいて検証する。**Stock/Sales Data Updateを必ず実装する前提ではない** — 将来Gulliver社が機能単位で選択し、Customer Budgetに応じてScopeを決められるようにするための、機能境界とDependencyの整理が目的。

**表記凡例**（Phase 8-Aから継続）:
- 🟢 **Source Confirmed** — Legacy Sourceで確認済みの事実
- 🟡 **Gulliver Requirement** — 2026/08/26打ち合わせ議事メモの原文に基づく要望
- 🔵 **Target Proposal** — 本Documentが提案するTarget Design要素（未実装）
- 🔴 **CUSTOMER REVIEW** — Gulliver社の意思決定が必要な未確定事項
- ⚪ **Operational Unknown** — Sourceからは確定できず、Ernest/Gulliverへの確認が必要な運用事実

---

## 1. Executive Summary

- Gulliver社の要望（Slide 6）は「月替わり後の在庫・販売関連データの反映にタイムラグがあり、月初からデータ更新までの期間の販売状況を発注判断へ反映しにくい」という**定性的な課題提起**であり、遅延日数の具体的な言明（例:「約10日」）は2026/08/26打ち合わせの書き起こしSource（Requirements.md／GSYS_Specification.md／議事メモpptx全15枚）のいずれにも見つからなかった。**この数値は本調査の対象Source内には存在しない** — 本Phaseの指示文に含まれていた具体例であり、Written Sourceからの引用ではないと判断する（4章・9章で詳述）。
- `MS_STK.SOLD_QTY`は既にPhase 0.5で確定済みの通り「Tempostar受注CSVを基にした当月累計出荷数量」の単一値であり、日次/前月/前々月に相当するデータ構造はMS_STK自体には存在しない（Requirements.md 660行目、再確認済み）。
- 本Phaseの追加調査で、**別テーブル`HIS_STK_LIST`（在庫・商品Master変更時の監査Snapshot）が実際に存在し、SOLD_QTYを含む値をSnapshotしている**ことが判明したが、これは「編集操作をトリガーとした不定期な変更履歴」であり、体系的な日次/月次のSales Historyとしては機能しない（Retention 3ヶ月、`SysPurgeBatch.java`確認済み）。したがって「MS_STK.SOLD_QTYだけでは過去Trendを作れない」というPhase 0.5の結論は、本Phaseの追加調査でも覆らない（6章で詳述）。
- 月次境界処理には、`MsMonthEndStockUpdate`（月初のみ実行、`MS_COMM`フラグでゲートされる手動待機ステップを含む）と、SOLD_QTY自体の対象月切替（`SlTempostarImportBatch`、Excelで手動上書き可能な対象月指定）という、**2つの独立した、いずれも人手の介在を含みうる仕組み**が存在する。これは「タイムラグ」の発生源になり得るSource Confirmedな構造であるが、**実際の遅延日数・頻度・誰がいつ操作しているかはSourceからは確定できない**（7章で詳述、Ernest確認へ）。
- Ordering機能（発注候補・calc4推奨数量算出）は、Current Stock/Open PO/Open Arrival/SOLD_QTYという既存4値のみに依存しており、Stock/Sales Data Improvementを導入しなくても**Ordering Moduleは現状のまま完全に成立する**（10章）。両者にBusiness/Estimate上のDependencyはあるが（同じ画面・同じ判断材料）、Technical Dependencyは無い（Ordering側のCodeがStock/Sales Improvement実装を前提にしていない）。
- 改善Optionとして5候補（A〜E、13章）に分解したが、いずれも現時点でGulliver社の意思決定（新規Business Rule）に強く依存し、Sourceのみでは実装可否を確定できない。

---

## 2. Gulliver Requirements（原文ベース）

出典: `docs/G-Sys_mtg_20260826_02.pptx` Slide 6「在庫・販売実績データの更新」（全文引用、Phase 7-Jで一度全文テキスト抽出済みのものを再確認）。

> 6. 在庫・販売実績データの更新 / 発注数量を適切に判断するためには、在庫および販売実績データの更新タイミングも重要との話がありました。 / 現状では、月替わり後の在庫・販売関連データの反映にタイムラグがあり、月初からデータ更新までの期間の販売状況を発注判断へ反映しにくいケースがあるとのことでした。 / 今後、現在庫の更新タイミング／販売実績の取得・保持方法／日次単位でのデータ利用可否／発注数量計算に利用するデータ について確認・整理する必要があります。

原文から確定できる要素は以下のみである（それ以上の推測は行わない）:

| 項目 | Meeting Sourceでの確認状況 |
|---|---|
| 在庫データの更新タイミングが重要という問題意識 | 🟡 確認済み（原文） |
| 月替わり後にタイムラグがあるという指摘 | 🟡 確認済み（原文、"月替わり後の...タイムラグがあり"） |
| タイムラグの具体的日数（例:「約10日」） | ⚪ **Written Sourceに記載なし**（Requirements.md／GSYS_Specification.md／議事メモpptx全15枚を全文検索し0件） |
| Sales Dataの更新頻度の具体的な現状値 | ⚪ 原文になし（「確認・整理する必要がある」という課題提起のみ） |
| Daily Salesの保持状況 | ⚪ 原文になし（"日次単位でのデータ利用可否"は今後の確認事項として挙げられているのみ） |
| 過去Sales Dataの参照範囲 | ⚪ 原文になし |
| 発注数量計算との関係 | 🟡 確認済み（"発注数量を適切に判断するためには...重要"という関連付けのみ、具体的な計算式変更要望ではない） |
| 欠品/長期欠品判定との関係 | ⚪ Slide 6原文には言及なし（Slide 4「価格・欠品・発注待ちの管理」は状態の**表示**についての言及であり、欠品の**定義**そのものへの言及ではない） |
| Tempostarとの関係 | ⚪ Slide 6原文には言及なし（Tempostarという語自体はGulliver打ち合わせ議事メモ内に一度も登場しない — Legacy Source側の実装詳細である） |

**結論**: Meeting Sourceで確定できるのは「月替わり後のタイムラグがある」という定性的課題提起までであり、それ以上の定量的な情報（日数・頻度・保持期間）は本調査対象のWritten Sourceには存在しない。すべて⚪ Operational Unknownとして12章のQA更新へ反映する。

---

## 3. Current Stock Data Model（Source Confirmed）

`MS_STK`（Legacy Entity: `MsStk`、複合PK: `WH_CD` + `ITEM_CD`）が在庫の中心Table。既に`docs/target-production-procurement-workflow.md`・`RecommendedQtyReadQuery.sql`のコメントで確認済みの構造を、本Phaseで`MsStk.java`から再確認した。

| フィールド | 内容 | 備考 |
|---|---|---|
| `STK_QTY` | 在庫数量 | `WH_CD='XX'`が集約行（Const.MS_STK_WH_CD_PRI、Phase 0.5確認済み）、物理倉庫別行（`WH_CD`が実倉庫コード）は各倉庫の実在庫のみを持つ |
| `STK_STANDARD` | 在庫基準値 | calc4（発注数量計算）の入力値の一つ |
| `PO_QTY_1〜20` / `ARR_QTY_1〜10` | 発注残数量・入荷予定数量（Open PO/Open Arrival） | 'XX'集約行が保持 |
| `SHIP_QTY_1〜10` | 出荷関連数量 | 'XX'集約行が保持。Logical Qty計算に加算される（`RecommendedQtyReadQuery.sql`コメント既述） |
| `SOLD_QTY` | 当月累計出荷数量 | Phase 0.5確定済み、5章で詳述 |
| `STK_QTY_LAST_MONTH`（本Phase新規確認） | 前月末在庫数量スナップショット | 4章で詳述。**「10日」等のタイムラグと関連しうる仕組み** |
| `UPDATE_DATETIME` | `@UpdateTimestamp`（Hibernate自動更新） | Sales/PO/Arrival等**複数の異なる更新処理すべてで書き換わる同一列**（Phase 0.5確認済み、Requirements.md 516行目）。「販売データ最終更新日時」等特定の意味には使えない |

---

## 4. Stock Data Flow（Source Confirmed）

### 4.1 通常の在庫更新経路（既存確認事項の再掲）
Physical Stock（倉庫別`STK_QTY`）・Open PO（`PO_QTY_*`）・Open Arrival（`ARR_QTY_*`）は、発注(`PrOfficialPoImportBatch`)・入荷実績(`PrStkInReportImportBatch`)等、既にPhase 7で確認済みの各種Import Batchが個別に更新する（本Phaseで再調査していない範囲、`docs/legacy-procurement-workflow-reverse-engineering.md`参照）。

### 4.2 月末処理（本Phase新規確認、`MsMonthEndStockUpdate.java`）

🟢 Source Confirmed:

```
[毎日実行と推定されるBatch起動]
  ↓
月の1日のみ:
  1. WK_STK（作業Table）へ、当日時点のMS_STK全体を"MONTH_END_STK_{前月yyyyMM}"というBack Up IDでバックアップ
  2. MS_COMM(CATE_ID_MONTH_PRC, CODE_ID_MON_PRC_STK_UPD).VAL1 を "NOT UPDATE" にリセット
  ↓
[VAL1が"UPDATE"の場合のみ、日付を問わず実行]:
  3. bulkupdateStkQtyLastMonth: WK_STK.STK_QTY → MS_STK.STK_QTY_LAST_MONTH へ反映
  4. summarizeStkQtyLastMonth: 倉庫別STK_QTY_LAST_MONTHを'XX'集約行へ合算
  5. VAL1を"NOT UPDATE"に戻す
  ↓
6. 結果通知メール送信（SYS_SEND_MAIL経由）
```

**重要な発見**: Step 3〜5は`MS_COMM.VAL1 = "UPDATE"`のときにしか実行されない。しかし、**このフラグを"UPDATE"へセットするコードはLegacy Source全体に一切存在しない**（`grep`で`MON_PRC_STK_UPD`の参照元を全探索し、読み取り/リセットする`MsMonthEndStockUpdate.java`自身以外に書き込み箇所が無いことを確認）。同バッチ自身のコメントは「WAIT LAST MONTH INVENTORY UNTIL STOCK STANDARD UPLOAD」と記しており、**「Stock Standard Upload」という別の（Source上は未特定の）操作が完了して初めてこのフラグが立つ運用を前提としている**が、そのUploadが具体的にどのBatch/画面/手順を指すのか、誰が・いつ・どのくらいの頻度で行っているのかはSourceからは確定できない（⚪ Operational Unknown）。

### 4.3 手動Excel上書き経路（本Phase新規確認、`MsLastMonthQtyUpdate.java`）

🟢 Source Confirmed: `STK_QTY_LAST_MONTH`には**もう一つ完全に独立した更新経路**が存在する。`AbstImportBatch`を継承したExcel Import Batchで、担当者が`ITEM_CD`/`STK_QTY_LAST_MONTH`の2列だけを持つExcelファイルをImport Folderへ配置すると、既存値を全クリアしたうえで、そのExcelの値でMS_STK.STK_QTY_LAST_MONTHを一括上書きする。4.2の自動的な仕組みとは無関係に、**人がExcelで前月在庫数を直接指定できる**。

**評価**: 4.2・4.3のいずれも、「前月在庫数」が自動集計だけでは完結せず、外部からのトリガー（4.2: Source不明のフラグ操作／4.3: 手動Excel Import）を必要とする構造である。Gulliver社の「月替わり後のタイムラグ」という課題提起と構造的に矛盾しない仕組みがSource上に実在することは確認できたが、これが実際の遅延の原因であると断定はしない（7章）。

---

## 5. Sales Data Model（Source Confirmed、Phase 0.5の再確認+深掘り）

`MS_STK.SOLD_QTY`は「当月累計出荷数量、Tempostar受注CSV基準」という単一値のみを持つ（Phase 0.5確定、Requirements.md 438行目・660行目で既述）。本Phaseでは`SlTempostarImportBatch.java`を実際に読み、その導出過程を確認した。

---

## 6. Sales Data Flow（本Phase新規確認、`SlTempostarImportBatch.java`）

🟢 Source Confirmed、Data Flow全体:

```
Tempostar受注CSV（SJIS、CPO_NO/CPO_NO_SHOP/ORDR_DATE/STATUS/ITEM_CD/SHIP_QTY/PRC_UNIT/AMT_LINE/SET_ITEM_CD/FREIGHT列）
  ↓ Import Folder経由（AbstImportBatch共通基盤、Official PO Importと同型）
WK_CPO（作業Table）へ全行INSERT（"TMP.TODAY.{timestamp}"のBack Up ID）
  ↓
日次単位でCopy（"TMP.DAY.{yyyyMMdd}"）
  ↓
MS_COMM(CATE_ID_TMPS_EXCLD)に登録された除外対象（Shop等）をFilterして除外（"TMP.DAY.FILTER.{yyyyMMdd}"）
  ↓
CPO_NO・ITEM単位で日次Summary（"DAY.SUM.{yyyyMMdd}"）
  ↓
Item単位で月次Summary（"MONTH.SUM.{yyyyMM}"）
  ↓
msStkRepository.clearAllSoldQty() で **全ItemのSOLD_QTYを一旦クリア**
  ↓
対象月のMONTH.SUM値で、該当ItemのMS_STK（WH_CD='XX'）.SOLD_QTY を **上書き（累積ではなく置換）**
  ↓
結果通知メール（成功件数・Item Master不存在エラー件数）
```

**重要な発見**:
1. **SOLD_QTYは「クリア→置換」であり、月をまたいだ累積処理は存在しない**。1回のBatch実行につき、その回のCSVが表す対象月のMONTH.SUM値がそのままMS_STK.SOLD_QTYになる。
2. **対象月は原則CSVファイル自身のORDR_DATE列から自動判定される**が、`MS_COMM(CATE_ID_MONTH_TEMP, CODE_ID_MONTH)`に手動で対象月（`yyyy/MM`形式）を設定しておくと、その月を強制的に対象月として扱う**手動オーバーライド機構**が存在する。このオーバーライド値が月替わり後に適切に更新されない場合、SOLD_QTYが古い月のデータのまま留まり得る（Source Confirmedな仕組みの存在。実際にそのような運用ミスが発生しているかはOperational Unknown）。
3. Item MasterまたはMsStk（'XX'行）が存在しないItemはエラーとして`WK_CPO`の別Back Up IDへ退避され、メール本文で報告される（Import Errorの扱いはOfficial PO Importと類似）。

---

## 7. Tempostar Integration（本Phase新規確認）

Legacy SourceにおけるTempostar関連Batchは、本Phase確認時点で最低4種類存在する（発注/価格変更RE時点で確認済みの`SlTempostarPriceChangeDownloadBatch`を含む）。

| Batch | 方向 | 方式 | 備考 |
|---|---|---|---|
| `SlTempostarImportBatch` | Tempostar → G-SYS（Sales） | CSV Import（Import Folder経由） | 6章で詳述。File形式・列構成はSource Confirmed |
| `InvTempostarStkUploadBatch` | G-SYS → Tempostar（Stock） | **Selenium WebDriverによるブラウザ自動操作**（Tempostar管理画面 **および Logizero管理画面** の両方にログイン） | API連携ではない。Logizero（倉庫システム）の認証情報も同一Batch内で扱っており、在庫Push処理がLogizero側のデータとも関係する可能性を示唆するが、詳細フローは本Phaseでは追跡していない |
| `SlTempostarPriceChangeDownloadBatch` / `SlTempostarPriceChangeDownloadSecondBatch` | Tempostar → G-SYS（価格変更情報） | Selenium WebDriver | Phase 7-J（`legacy-price-change-reverse-engineering.md` 13.3章）で確認済み、本Phaseでは再調査していない |
| `SlTempostarDownloadBatch` | 未確認 | 未確認 | ファイル名からTempostarダウンロード系と推定されるが、本Phaseでは内部を読んでいない |
| `CheckStuckTempostarFile` | 監視のみ | ローカルFolder（`C:\D\g-sys\80.system\3.TempostarPrice`固定パス）内のCSV残留を検知しメール通知 | **フォルダ名が"TempostarPrice"であり、Sales CSV用フォルダと同一かは未確認**。Retry機構は無く、人への通知のみ（Error Handling全体としては「検知→人手対応」） |

**Frequency（実行頻度）**: Legacy Source全体（`@Scheduled`/cron相当のアノテーション）を検索し、**0件**であることを再確認した（Phase 7-A時点の既存知見と一致）。したがって上記いずれのBatchも起動頻度はSourceからは確定不能であり、OS層のTask Scheduler等、Infrastructure/Operations設定に依存する（⚪ Operational Unknown、Ernest確認 - 既存Q7/Q13と同種の論点）。

**Duplicate Handling**: `SlTempostarImportBatch`は同一CPO_NO/ITEMの重複行を弾く専用ロジックは確認できず（Work Table再構築の都度、前回分のBack Up IDを削除してから作り直す設計のため、同じ日を2回実行した場合の挙動はSource上明確ではない）。

**Backup/Retry**: `WK_CPO`は各処理ステップでBack Up ID単位のDelete&Recreateであり、失敗時に途中から再開する仕組みは確認できなかった。

---

## 8. Historical Data Availability（本Phase重点調査）

### 8.1 `HIS_STK_LIST`（本Phase新規確認）

🟢 Source Confirmed:
- Table: `HIS_STK_LIST`（Legacy Entity: `HisStkList`）。`MsItem`+`MsStk`（'XX'行および倉庫別WH4〜15の各行）を結合したフルSnapshotで、`SOLD_QTY`・`STK_QTY_LAST_MONTH`・全PO/ARR/SHIP数量・価格情報などを含む。
- **トリガー**: `HisStkListRepository.saveHistory(itemCd)`の呼び出し元は`UsersController.java`・`AllUsersRestController.java`の**CRUD操作（商品Master編集・在庫詳細編集・商品Group一括編集・商品削除等）内のみ**であり、Scheduled Batchからの呼び出しは0件。すなわち**「誰かがその商品の情報を画面上で編集した瞬間」にのみSnapshotが1件残る**、変更監査ログに近い性質であり、体系的な日次/月次のSales/Stock Historyではない。
- **粒度**: 編集イベント単位（Item×操作のたび）。SKU全件を横断した「ある日の全在庫スナップショット」には相当しない（未編集のItemはSnapshotされない）。
- **Retention**: `SysPurgeBatch.java`が`getAllByCreateDatetimeBefore(3ヶ月前の日付)`を用いて**3ヶ月より古いHIS_STK_LIST行を削除**している（Source Confirmed、`Calendar.MONTH, -3`）。
- **PortalからのREAD可能性**: 現在Portal（`gsysportal`）はこのTableを一切参照していない（未実装）。Legacy READ ONLY接続を使えば技術的には参照可能だが、上記の性質上、体系的なSales Trend表示の基盤としては不向き。

### 8.2 `HIS_ARR_LIST`（本Phase参考確認、詳細未調査）
Arrival（入荷）についても同種のHistory Table（`HisArrList`/`HisArrListRepository`）が存在することを`saveHistory`呼び出し箇所から確認したが、本Phaseでは内部構造まで調査していない（Stock/Salesの調査範囲外）。

### 8.3 結論
🟢 **Phase 0.5の確定事項（「MS_STK.SOLD_QTYだけでは過去Trendを作れない」）は、本Phaseの追加調査でも覆らない。** `HIS_STK_LIST`という編集監査目的のTableは存在するが、(a) 全SKU・全日を体系的にカバーしない、(b) Retentionが3ヶ月と短い、という2点で、Daily/Weekly/Monthly Sales TrendやShipment Historyとして要求される用途には使えない。Daily Sales・Weekly Sales・Sales Aggregate専用のTable/Entityは、Source全体を検索した範囲では**確認できなかった**（0件）。

---

## 9. Update Timing / Delay Analysis

指示のとおり、Source ConfirmedとOperational Unknownを明確に分離する。**「ここが遅延の原因である」という断定は行わない。**

### 9.1 Source Confirmedな「遅延が発生し得る構造」

| # | 構造 | 該当箇所 |
|---|---|---|
| 1 | `STK_QTY_LAST_MONTH`更新が、Source上どこにも書き込み箇所が存在しないフラグ（`MON_PRC_STK_UPD`）のON操作を前提としている | `MsMonthEndStockUpdate.java`（4.2章） |
| 2 | `STK_QTY_LAST_MONTH`には、上記とは独立した手動Excel Import経路が別に存在する | `MsLastMonthQtyUpdate.java`（4.3章） |
| 3 | `SOLD_QTY`の対象月は原則CSV自動判定だが、`MS_COMM`の手動オーバーライド値が優先される仕組みがある | `SlTempostarImportBatch.java`（6章） |
| 4 | 全Import Batchの起動頻度・トリガーはSource上確認不能（`@Scheduled`0件） | 全体（7章） |
| 5 | Tempostar関連ファイルの「詰まり」を検知する仕組み（`CheckStuckTempostarFile`）は存在するが、自動リトライは無い | 7章 |

### 9.2 Operational Unknown（Source単独では確定できない事項）

- 実際の遅延日数（「約10日」を含む、いかなる具体的日数もWritten Sourceには存在しない）
- 上記1・3のフラグ/オーバーライド値を実際に誰が・いつ操作しているか
- 「Stock Standard Upload」という`MsMonthEndStockUpdate`のコメントが指す具体的な操作・担当・頻度
- Tempostar CSV自体がTempostar側でいつ生成され、いつG-SYS側のImport Folderへ転送されるか（File Transfer方式自体もSourceからは確認できない — Import Folderへ「置かれた後」の処理しかSourceには存在しない、Official PO Import等と同型の限界）

**したがって、本Phaseの結論は「タイムラグを生みうる構造はSource上複数確認できたが、実際の遅延日数・頻度・原因の特定はErnest/Gulliverへの確認が必須」である。**

---

## 10. Ordering Dependency

### 10.1 Stock/Sales Improvementを導入しない場合のOrdering成立性
🟢 Source Confirmed: `RecommendedQtyReadQuery.sql`・`OrderQuantityCalculator`（calc1-4）が使用する入力は、Current Stock（`STK_QTY`集約値）・STK_STANDARD・SOLD_QTY（当月累計）・Open PO（`PO_QTY_*`合計）・Open Arrival（`ARR_QTY_*`合計）のみであり、いずれも**既存のMS_STK単一値**から取得される。Stock/Sales Data Improvement（History蓄積等）を一切導入しなくても、Ordering Moduleは現状のまま完全に成立する（Technical Dependencyなし）。

### 10.2 導入した場合に改善が見込める可能性のある領域（確定Business Ruleではなく、あくまで可能性）

| 領域 | 改善の可能性 | 前提 |
|---|---|---|
| Recommended Qty精度 | 当月内の販売Trend（急増/急減）を見た上での補正判断材料になり得る | 現状のcalc4式自体を変更するかはGulliver Future Decision、本Documentでは提案しない |
| 欠品判断 | 「在庫0」という瞬間値だけでなく、直近の販売Velocityを加味した早期検知の可能性 | OOS/長期OOSの正式定義自体が未確定（11章）であり、Historyの活用可否はその先の議論 |
| Long-term OOS | 同上。「どのくらいの期間欠品が続いているか」を厳密に判定するには、日次のStock推移データが理論上役立ちうる | 現Prototypeの暫定Predicateとは無関係の、将来の話 |
| Follow-up | 未納問い合わせのタイミング判断に、販売Trendの参考情報が使える可能性 | 既存Follow-up機能（Phase 7-C7A）は入荷実績ベースであり、Sales Historyとは独立した仕組み |
| Dashboard | 売上分析・在庫回転率等の表示（現状`G-SYS_Online-Ordering_Prototype_Requirements.md`のDashboard章で明示的に9/17 Scope外とされている項目、535行目付近） | Historyが無いと実現不可能な項目そのもの |

**重要**: 上記はいずれも「技術的に可能性がある」という整理であり、実装するかどうか・具体的な計算式・閾値はGulliver社の意思決定事項であって、本Documentでは一切決定しない。

---

## 11. OOS / Long-term OOS

現Prototypeの暫定Predicate（`frontend/src/shared/domain/stockJudgement.ts`で確認済み）:

```
Out of Stock:       currentStock === 0
Long-term Out of Stock: currentStock === 0 && openPo/openArrival === 0
```

**本Phase調査結果**: 正式な定義がLegacy SourceまたはMeeting Sourceから確定できるか確認したが、**確定できなかった**。

- Legacy Source: 「欠品」「長期欠品」という業務用語に対応するBusiness Logic・閾値・時間条件はSource全体を検索しても見つからない（Item Statusコード`DISCON`/`ON_HOLD`等は状態コードとして存在するが、これは商品の「取扱状態」であり「欠品/長期欠品」という在庫観点の判定とは別軸）。
- Meeting Source: Slide 4「価格・欠品・発注待ちの管理」は、状態を**一覧で見やすくする**という表示上の要望であり、欠品・長期欠品の**定義そのもの**（何日で「長期」とみなすか等）への言及はない。

**既存QAとの重複確認**: この論点は`customer-review-decision-package.md`の**D-5（Supply Status（欠品/長期欠品/廃番等）の正式定義）として既に登録済み**であり、`customer-review-question-sheet.md`のD-5とも重複する。本Phaseで新規IDは追加しない。指示どおり、時間条件等を勝手に追加することもしない。D-5はCUSTOMER REVIEWのまま維持する。

---

## 12. Legacy Gap

| 項目 | Legacy現行 | Gap |
|---|---|---|
| 在庫データの即時反映 | Import Batch経由の非同期更新、フラグ/手動Excel経由の前月値更新（4章） | 「即時性」という概念自体がSource上存在しない、Gulliver Future Decision |
| 日次Sales Data | 存在しない（WK_CPOは処理途中のWork Tableで日次粒度を一時的に経由するが永続化されない） | 新規Business Rule（採否含めて） |
| Sales History（月次超） | 存在しない（8章） | 新規Business Rule |
| 欠品/長期欠品の正式定義 | 存在しない（D-5、既存CUSTOMER REVIEW） | 新規Business Rule（重複登録なし） |
| Tempostar連携の可視性 | Selenium/CSVベースで閉じた仕組み、Portalからは不可視 | Portal側で可視化する場合はArchitecture決定が必要（14章） |

---

## 13. Target Improvement Options

指示のとおり、事前提示された5分類（A〜E）をそのまま採用せず、調査結果から妥当性を検証したうえで整理する。

| Option | 内容 | Customer Value | Legacy Gap | Technical Dependency | Business/Estimate Dependency | External System Dependency | 実装難易度（相対） | Customer Decision依存 | 単独導入可能性 |
|---|---|---|---|---|---|---|---|---|---|
| **A. Current Stock Update可視化** | 在庫更新タイミング・フラグ状態等をPortal上で「いつのデータか」明示する（新しい値を作らず、既存値の鮮度を見せるだけ） | 中（現状の不透明感を軽減） | 4章のフラグ機構が不可視である点 | 低（既存MS_STK/MS_COMM READ ONLY参照のみ、Ordering非依存） | 低 | なし | 低（表示のみ） | 低（新Business Ruleをほぼ伴わない） | 高 |
| **B. Sales History蓄積（Portal側）** | Portal独自DBにDaily/Monthly Sales Snapshotを日次で蓄積開始する | 高（将来Trend分析の基盤） | 8章で確認した「Historyが存在しない」点そのもの | 中〜高（新規蓄積の仕組み・Batch/Job設計が必要） | 高（何を・どの粒度で・いつから蓄積するかはGulliver判断） | Tempostar（間接、SOLD_QTY自体がTempostar由来） | 中 | 高 | 高（Ordering非依存で単独価値あり） |
| **C. Sales Trend可視化** | Bで蓄積したデータをDashboard等でグラフ表示 | 高 | 9/17 Scope外と既に整理済み（Requirements.md） | 高（Bに依存、Technical Dependencyあり） | 高 | なし（Portal内で完結） | 中〜高 | 高 | **低（Bへの技術的先行完成が前提、単独導入不可）** |
| **D. Recommended Qty入力改善** | calc4等の計算式自体にSales Trendを組み込む | 中〜高（発注精度向上の可能性） | 現calc4はSOLD_QTY単一値のみ使用 | 高（既存Formula移植ロジックの変更を伴う可能性） | 高（Legacy Formulaを変更するのか、Portal側で別ロジックを作るのかもGulliver判断） | なし | 高（Legacy Formula改変は特に慎重な検証が必要） | 高 | 低（B/Cの後続、かつ既存Formula資産との整合が前提） |
| **E. Stock Alert改善（欠品/長期欠品の早期検知）** | OOS/長期OOSの正式定義確定後、Historyを使った早期Alert | 中 | D-5が未確定である限りOptionとして具体化できない | 中（B/Historyに部分依存） | 高（D-5の確定が前提） | なし | 中 | 高（D-5の確定が前提条件） | 低（D-5確定が前提、現時点では単独導入不可） |

**検証結果**: 5分類は概ね妥当だが、CとD・Eは**Bへの技術的先行完成、またはD-5の確定を前提とする**ため「単独導入可能」とは言えない。**独立した改善Optionとして単独提案できるのはA（表示改善のみ）とB（蓄積基盤）**であり、C/D/Eはこれらに続く段階的Optionとして位置づけるのが実態に即している。

---

## 14. Architecture Alternatives

Legacy変更最小化を重視し、比較する。

| 案 | 内容 | 評価 |
|---|---|---|
| A. G-SYS内部データのみ利用 | 既存MS_STK.SOLD_QTY等をPortalが都度READするのみ、蓄積しない | Legacy変更ゼロで最も安全だが、8章の結論どおりHistory自体が無いため、13章のB以降は実現不可能。**A（表示改善）レベルの改善にしか使えない** |
| B. Portal側にSales History蓄積 | Portal（Prototype DB）が日次でMS_STK.SOLD_QTY等をSnapshotし、独自にHistoryを構築する | Legacy変更ゼロ（READ ONLYのまま）。**Portalが新たなSystem of Recordになる**のはこのSales History部分のみ — Current Stock/Current SOLD_QTYの正（Source of Truth）は引き続きLegacy。責任範囲: Portalは「観測した時点の値の記録」を保証するのみで、Legacy側の値そのものを保証・訂正する権限は持たない |
| C. Tempostar DataをPortalでも受ける | Tempostar CSVをG-SYSと並行してPortalにも投入する | Legacy非依存で最新性を上げられる可能性があるが、Tempostar側との新規連携契約・認証情報管理が必要（External System Dependency増）。CSVフォーマットの二重メンテナンスリスク |
| D. G-SYSにHistorical Tableを追加 | Legacy DBに新Table/列を追加 | **Legacy変更を伴うため、標準constraint（Legacy Source変更禁止）に抵触し不採用** |
| E. その他（Logizero等の外部Warehouse Systemを参照） | InvTempostarStkUploadBatchが触れているLogizero経由で在庫の別ソースを得る | 7章で存在を確認したのみで詳細未調査。External System Dependency大、次々Phase以降の検討候補 |

**結論**: Legacy変更ゼロを維持する前提では、**B（Portal側Sales History蓄積）が唯一、13章のB以降のOptionを技術的に実現可能にする方式**。A単体では表示改善止まり。Cは新規External連携、Dは不採用、Eは調査不足。**まだ実装しない**（本Phaseの指示どおり）。

---

## 15. Module / Estimate Dependency（decision-package 16章との接続）

`customer-review-decision-package.md` 16章の方針に基づき、Stock/Sales Data Improvementを機能領域として整理する。

| # | 機能領域 | Core/Optional | 他機能へのDependency | 単独導入可能性 | Legacy影響 | External System依存 | Customer Decision依存 |
|---|---|---|---|---|---|---|---|
| 13 | Stock/Sales Data Improvement（A: 表示改善） | Optional | 無（Ordering非依存で成立、10章） | 可 | 低（READ ONLY） | 無 | 低 |
| 14 | Stock/Sales Data Improvement（B: History蓄積） | Optional | 13のAとは独立に単独導入可 | 可 | 低（READ ONLY） | 間接（Tempostar由来のSOLD_QTYを記録するのみ） | 高 |
| 15 | Stock/Sales Data Improvement（C/D/E: Trend可視化・計算式改善・Alert改善） | Optional | **Bへの Technical Dependency**（C）、Bおよびcalc4資産への高いTechnical Dependency（D）、D-5確定へのBusiness/Estimate Dependency（E） | 不可（単独では成立しない） | D以外は低、Dは既存Formula資産の扱いに要注意 | 無 | 高 |

**Business/Estimate DependencyとTechnical Dependencyの区別**（16.2章の原則を適用）: OrderingとStock/Sales Data Improvementの関係は、両者が「同じ発注判断の材料を扱う」という**Business/Estimate上の関連**はあるが、Ordering側のCodeがStock/Sales Improvement実装済みであることを前提にしていないため**Technical Dependencyは無い**（10章で確認済み）。一方、13章のC・D・EはBという同一Optionの技術的成果物（蓄積されたHistory Table）そのものを直接参照する設計にならざるを得ないため、これは**Technical Dependency**である。

---

## 16. Customer Review

新規CUSTOMER REVIEW項目を整理する（既存D-5との重複なし、17章で正式Theme化）。

| 論点 | 分類 | 出典 |
|---|---|---|
| 在庫データ反映の即時性改善が必要か、必要な場合どの程度か | D（Future Decision） | 9章・13章A |
| 日次/週次単位のSales Data蓄積・可視化機能が必要か | D | 8章・13章B/C |
| 過去Sales Trendをどの程度の期間参照できる必要があるか | D | 8章・13章B/C |
| calc4（Recommended Qty）にSales Trendを組み込むべきか | D | 10章・13章D |
| 欠品/長期欠品の正式定義（**既存D-5、重複登録なし**） | D（既存） | 11章 |
| Portal側でSales Historyを蓄積する場合のSystem of Record責任分担の承認 | D | 14章 |

---

## 17. Ernest確認候補（本Phase由来）

`ernest-current-operation-question-sheet.md`への追加候補（重複確認済み、既存Q1-23とは非重複）。

| 論点 | 出典 |
|---|---|
| `MON_PRC_STK_UPD`フラグ（前月在庫確定のゲート）を実際に誰が・いつ"UPDATE"へ切り替えているか、その操作は「Stock Standard Upload」という別の手順を指すのか | 4.2章 |
| `MS_COMM(CATE_ID_MONTH_TEMP)`の対象月手動オーバーライドが実際に使われているか、月替わり時の運用手順 | 6章 |
| SlTempostarImportBatch等Tempostar関連Batch群の実際の起動頻度・トリガー方法（既存Q13と同種、Tempostar特化） | 7章 |
| Tempostar CSVがG-SYS Import Folderへ実際にどう転送されているか（File Transfer方式） | 7章 |
| 実際の「月替わり後のタイムラグ」の具体的な日数・発生頻度（Written Sourceに記載なし） | 9章 |

---

## 18. Risks

- **「10日」という数値の出所不明**: 本Phaseの指示に含まれていたが、Written Meeting Sourceには存在しない。Techlead（ChatGPT）または顧客との口頭でのやり取りに由来する可能性があり、事実確認をしないまま次Phase以降の設計前提にしてしまうリスクがある。
- **HIS_STK_LISTの誤解リスク**: 「History Tableが存在する」という事実だけを見て、Sales Trend機能がLegacyに既に実装済みであるかのように誤解される可能性がある。8章で明記したとおり、体系的な用途には使えない。
- **B（Sales History蓄積）着手時のTempostarへの間接依存**: SOLD_QTY自体がTempostar CSV由来であるため、PortalのHistory蓄積もTempostar側の更新頻度・精度に上限を画される。
- **Legacy Formula改変（Option D）のリスク**: `calc4`は既に発注機能の中核として移植・Golden Test済みの資産であり、Sales Trendを組み込む改修は移植済みロジックとの整合を慎重に検証する必要がある。

---

## 19. Recommended Next Step

提案のみ、確定ではない。Techlead/ユーザーの確認を前提とする。

- 16章のCUSTOMER REVIEW・17章のErnest Question回答を待つ。
- 回答が得られた場合、13章のOption A（表示改善）は他Optionへの前提を作らない最小範囲であるため、次Phase候補として検討しやすい。
- Option B（History蓄積）は「Portalが新たなSystem of Recordになる」という重い意思決定を伴うため、Gulliver社の明確な合意が先に必要。

---

## 20. Phase 8-B `price_change_set.status` 軽微監査結果（指示14章）

READ ONLYでの監査のみ、Source変更は一切行っていない。

| 確認項目 | 結果 |
|---|---|
| `SUBMITTED`は何に対するSubmitか | `PriceChangeSet.java`のJavadocに明記済み: 「G-SYSへの反映のためSubmitされた」ことを表す構造的Placeholder。実Excel Artifact生成・Import等は一切実装されていない（Phase 8-B自身の宣言どおり） |
| `CANCELLED`は何をCancelする意味か | 未反映（DRAFT/SUBMITTED）のChange Setを取り下げる意味として設計されているが、取消権限（誰が・いつまで）は`target-price-change-workflow.md` 17章PC-8としてCUSTOMER REVIEW未確定のまま |
| 現在これらへの実際のState Transitionが存在するか | **存在しない**。`grep`で`STATUS_SUBMITTED`/`STATUS_APPLIED`/`STATUS_FAILED`/`STATUS_CANCELLED`の参照箇所を全探索した結果、宣言元の`PriceChangeSet.java`以外に一切の参照が無いことを確認した。DRAFTのみが実際に使用されている |
| Phase 8-AのCustomer Review未確定事項を先回りして固定していないか | **していない**。PENDING_APPROVAL/APPROVED/SCHEDULEDは意図的にSchemaから除外されている（`target-price-change-workflow.md` 6章の最小State Skeletonどおり）。DB CHECK制約（V16）は5値を許容するが、これは「将来の到達点を構造的に宣言している」だけであり、Approval要否・Cancel権限等の未確定Business Ruleを固定するものではない |

**結論**: Phase 8-B実装は、Phase 8-Aで意図した境界を越えていない。ただし、宣言済みだが到達不能な4State（SUBMITTED/APPLIED/FAILED/CANCELLED）がDB制約・Enumとして存在する点は、実装が先行しているように見えなくもないため、将来のReview時に「これは意図的なPlaceholderである」という本監査結果を参照できるようにした。

---

## Final Classification Table

| # | 論点 | 分類 | 状態 |
|---|---|---|---|
| S-1 | 「10日」等の遅延具体値 | Ernest/Gulliver確認要（Written Sourceに存在せず） | 17章・16章 |
| S-2 | `MON_PRC_STK_UPD`フラグの実際の操作者・頻度 | B（Ernest Q候補） | 17章 |
| S-3 | Tempostar Batch群の起動頻度・File Transfer方式 | B（Ernest Q候補、既存Q13と同種） | 17章 |
| S-4 | 在庫データ即時性改善の要否 | D（Gulliver Future Decision） | 16章 |
| S-5 | 日次/週次Sales蓄積・可視化の要否 | D | 16章 |
| S-6 | 過去Trend参照期間 | D | 16章 |
| S-7 | calc4へのSales Trend組み込み要否 | D | 16章 |
| S-8 | 欠品/長期欠品の正式定義 | D（既存D-5、重複なし） | 11章 |
| S-9 | Portal Sales History System of Record化の承認 | D | 14章・16章 |

**Phase 8-C時点で変更したFrontend/Backend/DB Migration/Legacy Source: 0件。** 本Documentと既存3 QA Documentの更新のみ（実装はPhase 8-Hで下記のとおり一部着手）。

---

## Phase 8-H 実装結果: Stock / Sales Visibility Foundation

**Status**: 本Phaseで確認したSource Fact（`MS_STK.SOLD_QTY`＝当月累計・`MS_STK.STK_QTY`＝現在庫・Legacy Sales Historyの不在）に基づき、**Current Snapshot Visibilityのみ**を実装した。Sales/Stock History蓄積・Trend Graph・Forecast・Alert・自動発注は一切実装していない（Phase 8-H 20章の禁止事項どおり）。

### 実装したFoundation

- **在庫・販売確認**（`/stock-sales`）: SKU単位の一覧、Backend Filter（SKU/Item Keyword・Brand・Supplier・在庫数量範囲・当月販売数量範囲）・Backend Pagination。列: SKU/商品名/ブランド/メーカー/現在庫/**当月販売数量**（Tooltipで「当月分の累計出荷数量、日次・過去傾向ではない」旨を明示）/発注残/入荷予定数/G-SYSデータ更新日時。
- SKU行からDrawerで詳細表示（新規Route追加なし）。推奨発注数（calc4、既存Logic）を「参考値」として表示。Warehouse Stock（Phase 8-G）・既存SKU Detail画面（`/items/:sku`）へのNavigationのみ（Business Joinはしない）。

### Backend実装

- 既存`LegacyStockReadRepository`（Order Candidate List/SKU Detailが依存する`RecommendedQtyReadQuery.sql`）を再利用。**新しい計算Logicは一切追加していない** — `RecommendedQtyReadQuery.sql`をSubqueryとして包み込み、Backend Pagination（`LIMIT`/`OFFSET`）と2つの純粋数値範囲Filter（在庫数量・当月販売数量）のみを外側に追加した。SKU/Item Keyword・Brand・Supplier FilterはOrder Candidate Listと**全く同じParam**（`:keyword`/`:brandCode`/`:supplierCode`）を再利用。
- `RecommendedQtyReadQuery.sql`へ`update_datetime`列を追加（同じ`'XX'`集約行の既存Timestamp、追加のみでJOIN/WHERE変更なし）。`LegacyStockRow`へ`updateDatetime`Fieldを追加（既存呼び出し元＝Order Candidate List/SKU Detail/Create Draftは無変更で動作継続、全Backend Testで回帰確認済み）。
- 推奨発注数（Reference値）は既存`RecommendedQtyCalculator.calc4()`をそのまま呼び出し。**Recommended Qty Logicの変更ゼロ**（SKU Detailと同一SKUで完全に同じ値を返すことをTestで確認）。

### Portal DB追加

**ゼロ**。Legacy Adapter→Query→DTO→UIで完結。Sales/Stock History Persistence Tableは作成していない。

### Test

- Backend: `StockSalesServiceIntegrationTest`（13件、List/Filter/Pagination/SOLD_QTY/STK_QTY/Open PO・Arrival分離/Timestamp/calc4一致/未知SKU）。既存Test（`SkuDetailServiceIntegrationTest`・`OrderDraftServiceIntegrationTest`含む）全Green、既存425件＋新規13件＝**全438件Green**。新規Legacy書込み経路を追加していないため`LegacyReadOnlyIntegrationTest`への追加は不要と判断。
- Frontend: `stock-sales-visibility-foundation.spec.ts`（12 E2E Scenario、Navigation・Search・数値範囲Filter・Drawer・SOLD_QTY表記確認・Warehouse Stock/SKU Detail Navigation・Pagination・既存Ordering/Price Change/Arrival/Warehouse Stock/SKU Detail回帰）。既存E2E（Phase 8-G・Price Change・Stock Judgement等、計26件）と合わせて全Green確認。

**変更したFrontend/Backend: Phase 8-H分（上記のとおり）。DB Migration（Flyway、Portal Prototype DB）: 0件。Legacy（`phasep-gulliver`）Source変更: 0件。**
