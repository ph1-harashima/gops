# Excel / Legacy Concurrency Control Foundation — Phase 7-C6 実装結果

`docs/target-production-procurement-workflow.md` 15章（Excel Coexistence）・`docs/official-po-integration-detailed-design.md` 11.2章/17章で構想されていた、Portal導入後も並行して存在しうる「手作業Official PO Excel」「Portal生成Official PO」「G-SYS上の既存PO」「Portal Revision」の整合をどう検出するかについて、実装した Optimistic Concurrency / Preflight Foundation の詳細記録です。

Scope: 「Portalが確認したG-SYS PO」と「実際にHandoffする直前のG-SYS PO」に差異がないことを確認する機構を実装。**実際のLegacy Handoffは行わない**（7-C2B スコープ）。**Legacy DB WRITE・Official PO Import・Import Folder Write・SMTP・SYS_SEND_MAIL INSERT・External Mail API・Production接続はいずれも本Phaseのコードパスに一切存在しない。**

## 0. 実装前監査

Phase 7-A / 7-C2-Design / 7-C2A / 7-C5 / 7-C7Aと現在Sourceを再確認した。

- `jp.ne.glv.model.TrPo` / `TrPoDtl`: 実装時点で使用可能な全カラムを再確認（`poNo`/`status`/`poType`/`supplierCd`/`brandCd`/`shipVia`/`shipTerm`/`paymentTerm`/`ordrDate`/`delivWeek`/`delivDate`/`ccy`/`amtTtl`/`delFlg`、および`TrPoDtl`の`itemCd`/`series`/`modelNo`/`model`/`color`/`description`/`qtyPo`/`prcUnit`/`amtLine`/`delFlg`）。`delivWeek`/`delivDate`はいずれも実Legacy上**プレーンなVARCHAR**（`delivDate`も実際の日付型ではなく自由記述）であることを確認 - Canonical Snapshotではこの2つを独立した文字列Fieldとして扱う。
- `PrOfficialPoImportBatch.java`（Delete & Recreate、~1313行）: 883-1057行の再Import処理を再確認。**重要な発見**: 952行目 `int lineNo = 1;` から、`TR_PO_DTL.LINE_NO`は再Import実行のたびにその回のExcel行順で1から振り直される、すなわち**Delete&Recreateを跨いでLine No.は安定した識別子ではない**ことを確定させた（29章のSTOP条件候補「Delete & RecreateでLine Identityを追跡不能」の直接的な検証根拠）。
- Existing PO Import Guard / Invoice Guard / Stock-in Guard（本ファイル11.1章相当、Phase 7-C2-Design時点の監査を再確認・変更なし）。
- `portal_order_revision`（7-C5）・`official_po_integration_request`（7-C2A）: `revisionNo`が両テーブルおよび`PortalOrder.currentRevisionNo`を通じて**同一の概念**であることを再確認し、本Phaseの`legacy_po_baseline.revision_no`もこの計算式（`currentRevisionNo == null ? 1 : currentRevisionNo + 1`）に完全準拠させた（`OfficialPoIntegrationService.targetRevisionNo`として抽出・共有）。
- `FulfillmentReadRepository`（7-C7A）: 同じTR_PO/TR_PO_DTLテーブルを参照するが、参照列が完全に異なる（Fulfillmentは数量のみ、Concurrencyは商流全体）ため、Row型を共有せず独立したRepositoryとした理由を2章に記録。
- `OfficialPoPreflightReadRepository`（7-C2A）: Supplier/Brand/Item Masterの存在確認のみを行う、officialPoNo確定**前**のPreflightであり、本Phaseの「officialPoNo確定後の変化検出」とは時間軸が異なる別レイヤーとして整理（既存の役割分担を変更していない）。

## 1. 問題定義

想定Scenario（指示どおり）:

```
T1: PortalがG-SYS POを確認
T2: 担当者がExcelを手動ImportしG-SYS POを変更
T3: Portalが古い状態を前提にRevisionをHandoff
```

G-SYSには実効的なOptimistic Lock機構がない（TR_PO.VERSIONはJPA `@Transient`指定 - 実際にはDBカラムとして機能していないことをSource上で確認済み、7-A時点の既存知見の再確認）ため、**Portal側でSnapshot/Fingerprint方式のOptimistic Concurrencyを実装**した。

## 2. Legacy PO Snapshot

`LegacyPoConcurrencyReadRepository`（Legacy READ ONLY、新規）が、実Sourceに存在するFieldのみを使ってCanonical Snapshotを構築する。

Header: `officialPoNo` / `status` / `poType` / `supplierCode` / `brandCode` / `orderDate` / `currency` / `delivWeek` / `delivDate` / `amtTtl`
Line（SKU単位）: `skuCode` / `orderedQty` / `unitPrice`

**Line識別子はSKU（item_cd）であり、TR_PO_DTL.line_noではない**（0章の監査結果どおり）。同一PO内に同一item_cdの行が複数存在する場合（実運用では想定外だが）はqty合算・単価は先勝ちで集約する、というシンプルな規約を採用した（`LegacyPoSnapshotFactory`）。

**Canonical Ordering**: SKU昇順に必ずソートしてからSnapshotを構築する（DB取得順序に依存しない）。SQL自体にも`ORDER BY item_cd`を付けているが、Java側で改めて明示的にソートし直す二重保証とした。

**既存Repositoryとの関係**: `FulfillmentReadRepository`（7-C7A）と同じTR_PO/TR_PO_DTLを参照するが、あえて独立した新規Repositoryとした。Fulfillmentは数量計算のみに必要な最小限の列（status/qty_po）しか読まないのに対し、Concurrency Detectionは商流全体（価格・納期・通貨等）を比較対象にする必要があり、両者の関心事は disjoint（重複が薄い）と判断したため。テーブル・READ ONLY保証・NamedParameterJdbcTemplate利用など、パターンレベルでは完全に既存Repository群を踏襲している。

## 3. Fingerprint

Canonical SnapshotをJackson ObjectMapperで決定論的にJSON化し、そのJSON文字列のSHA-256ハッシュ（16進64文字）をFingerprintとする（`LegacyPoFingerprintCalculator`）。

**重要な性質確認（Unit Testで検証済み）**:
- 同じBusiness Data + 異なるDB取得順序 → 同じFingerprint（`LegacyPoFingerprintCalculatorTest.sameDataDifferentFetchOrderProducesSameFingerprint`）
- Qty変更・Price変更・Line追加/削除 → 異なるFingerprint（4種のTestで個別検証）

FingerprintはあくまでConcurrency Detection用途であり、Security用途（改ざん防止等）ではない - この区別を`LegacyPoFingerprintCalculator`のJavadocに明記した。

## 4. Baseline Model

新規テーブル `legacy_po_baseline`（Prototype PostgreSQL、V13 Migration）:

```
id / portal_order_id / revision_no / official_po_no / fingerprint / snapshot_json / captured_by / captured_at
```

`official_po_integration_request`へのField追加ではなく、指示どおり**別Entity**として実装した。理由: Integration Requestは「Integration Workflowの状態」を表す1レコード（PENDING/GENERATED/...）であるのに対し、Baselineは「ある時点でPortalが確認したG-SYSの中身そのもの」という性質が異なるデータであり、また再確認（re-capture）が複数回起こりうる（append-only、`audit_event`/`portal_order_revision`と同じ規約）ため、1レコード=1状態のIntegration Requestに無理に同居させるより独立させた方が将来の監査にも耐えると判断した。

**Compare時に使うBaselineは「その(portalOrderId, revisionNo)に対する最新のcapture」** - 再確認のたびに新しい行が追加され、古いBaselineは履歴として残る。

## 5. officialPoNo未設定

`captureBaseline`は`officialPoNo == null`の場合`OfficialPoNotLinkedException`（409, `OFFICIAL_PO_NOT_LINKED`）を投げ、Legacy照会を一切実行しない。`compare`も同様に`officialPoNo == null`ならLegacy照会前に即座に`NOT_LINKED`を返す。UIには「G-SYS正式PO未連携のため、整合確認はできません。」を表示する。

## 6. PO_NOT_FOUND

`compare`はofficialPoNoが設定されているがLegacyに見つからない場合、`PO_NOT_FOUND`を返す。**この判定はNOT_BASELINEDより優先される**（8章参照）。「新規POなので存在しない」場合と「存在するはずなのに無い」場合の意味の違いはCUSTOMER REVIEW（22章）のまま - 本Phaseは自動でERROR/BLOCKED判定をしない。

## 7. Diff Engine

`LegacyPoDiffEngine`（Pure Function、DB/Spring非依存、Unit Test 6件）が、BaselineとCurrentの2つのCanonical Snapshotを比較し、構造化Diffを生成する:

```
LegacyPoDiffEntry(field, skuCode, baselineValue, currentValue, diffType)
diffType ∈ {HEADER_CHANGED, LINE_CHANGED, LINE_ADDED, LINE_REMOVED}
```

単なる"fingerprint mismatch"では終わらせず、Header各Field（status/poType/supplierCode/brandCode/orderDate/currency/delivWeek/delivDate/amtTtl/lineCount）とLine各Field（orderedQty/unitPrice）を個別に比較し、UI表示可能な粒度の差分リストを返す。

## 8. Concurrency Result

`LegacyPoConcurrencyResponse.result` ∈ `{UNCHANGED, CHANGED, NOT_BASELINED, NOT_LINKED, PO_NOT_FOUND}`。

`compare`内での判定順序（重要な設計判断）: **NOT_LINKED → PO_NOT_FOUND → NOT_BASELINED → UNCHANGED/CHANGED**。PO_NOT_FOUNDをNOT_BASELINEDより先に判定する理由: LegacyにPOが存在しないという事実は「まだBaselineを取っていない」より根本的な情報であり、かつBaseline Capture自体がPO存在を前提条件とするため、PO_NOT_FOUND状態のOrderはそもそもNOT_BASELINEDへ到達しようがない（PO_NOT_FOUND→（PO登録後）→NOT_BASELINED→（Capture後）→UNCHANGED/CHANGED、という一方向の状態進行になる）。

Integration Status（PENDING/GENERATED/...）とは完全に別軸で、FAILEDに混ぜていない。

## 9. Baseline API

`POST /api/orders/{id}/official-po/baseline`（ADMIN限定）。前提条件:

1. `officialPoNo != null`（5章）
2. Legacy POが実在（6章とは非対称 - Captureは存在必須、Compareは不在も許容）
3. その(orderId, targetRevisionNo)に対応する`official_po_integration_request`行が存在（"G-SYS連携準備"を先に実行済みであること）

3つとも満たさない場合はそれぞれ専用のExceptionを投げる（`OfficialPoNotLinkedException`/`LegacyPoNotFoundForBaselineException`/`IntegrationRequestRequiredException`）。Legacy WRITEは一切発生しない。呼ぶたびに新しいBaseline行を追加し、`LEGACY_PO_BASELINE_CAPTURED`を毎回Auditする（18章）。

## 10. Compare API

`GET /api/orders/{id}/official-po/concurrency`（全認証ユーザー閲覧可）。副作用なしのREAD（ただしCHANGED検出時のみAudit書き込みが発生する、18章）。既存のFulfillment/Official PO Integration Sectionと同じ「Order Detail読み込み時に自動フェッチされるGET」という設計に合わせた。

## 11. Pre-Handoff Gate

`LegacyPoConcurrencyService.canProceedToHandoff(orderId)`をServiceレベルのメソッドとして実装した（Controller Endpointは今回追加していない - 指示どおり「Serviceレベルで判定を作れるなら実装する」の範囲）。判定式:

```
canProceedToHandoff = (Preflight結果 != BLOCKED) AND (Concurrency結果 == UNCHANGED)
```

`OfficialPoIntegrationService.getIntegration`と`LegacyPoConcurrencyService.compare`の結果を組み合わせるだけの薄いロジックで、7-C2B（実Handoff）が呼び出すことを想定したFoundation。本Phaseでは統合テストのみで検証。

## 12. 新規POの場合

G-SYSにまだ存在しない新規POは、Compareが`PO_NOT_FOUND`を返す（6章）。これを自動でBLOCKED/ERROR扱いにはしない - Existing PO Update と New Official PO の区別自体は13章のIntegration Intentで表現する設計とした。

## 13. Integration Intent

`official_po_integration_request.integration_intent`列（NULL許容、`NEW`/`UPDATE`のCHECK制約）を追加し、`PUT /api/orders/{id}/official-po/intent`（ADMIN限定）で明示的に設定できるようにした。自動推定は一切行わない（正式PO番号規則が未確定のため）。バックエンドAPI/テストのみ実装し、**フロントエンドUIへの結線は本Phaseでは見送った**（スコープの都合、Foundationとしては完結しているため）- 将来7-C2Bで実際にIntent別の分岐処理（Handoff方式の違い等）が必要になった時点でUI化する。

## 14. Manual Excel Coexistence（テスト戦略）

Section 14の5シナリオをBackend統合テスト + E2Eテストで分担して検証した:

- **Scenario 1（Baseline取得→Fixture変更→Compare→CHANGED）・Scenario 2（Qty変更→LINE_CHANGED）**: E2E Scenario C（PO-CONC-01のqty_poを`docker exec ... mysql`で直接変更）。
- **Scenario 3（Line追加→LINE_ADDED）**: E2E Scenario D-1（PO-CONC-02へ2行目を`INSERT`）。
- **Scenario 4（Line削除→LINE_REMOVED）**: E2E Scenario D-2（PO-CONC-03の2行目を`del_flg=1`で論理削除）。
- **Scenario 5（変更なし→UNCHANGED）**: Backend統合テスト（`captureThenCompareImmediatelyIsUnchanged`）+ E2E Scenario B。

Legacy Demo MySQLへの直接Test Fixture変更は、Backend JavaテストのSpring管理コネクション（`legacyDataSource`、Layer1のDB権限+Layer2のreadOnly接続で書き込み拒否 - `LegacyReadOnlyIntegrationTest`で実証済み）を経由しては**物理的に不可能**なため、E2E層で`docker exec gsys-legacy-demo-mysql mysql -uroot ...`（Portal Applicationの実行時接続とは完全に別の、Docker管理者経路）を用いた。これはPortal Applicationの実行時READ ONLY保証を一切損なわない、7-C7A以来確立された「Legacy Demo MySQLはTest環境限定でSchema/Fixtureを拡張してよい」という方針の延長線上の作業である。

E2E層のFixture変更は毎回**冪等なリセット**（`resetConcurrencyFixtures`）を先頭で実行してから行うため、Legacy Demo MySQLコンテナの再作成なしに繰り返し実行できる。

## 15. Stale Excel Protection

将来Portalが実際にExcelを生成する際（7-C2B以降）、生成時点のFingerprintをArtifact metadataへ保持できるよう、Fingerprint計算自体を`LegacyPoFingerprintCalculator`という独立した再利用可能なユーティリティとして切り出した。今回はFilesystem Handoffを実装していないため、実際にExcel生成物へ埋め込む配線はまだ存在しない（Foundationのみ）。

## 16. Portal Revisionとの関係

`legacy_po_baseline.revision_no`は必ず`portalOrderId`+`revisionNo`に紐づく。Rev1のBaselineがRev2のCompareに誤って使われないことを、Backend統合テスト（`revision1BaselineIsNeverReusedForRevision2`）とE2E Scenario Eの両方で実証した - Demo Send実行によりOrderのcurrentRevisionNoが1になった直後、次のtargetRevisionNoは2になり、Rev1でCaptureしたBaselineはCompareの対象にならず`NOT_BASELINED`が返ることを確認済み。

## 17. Fulfillmentとの関係

7-C7AのFulfillmentは**現在**のLegacy Dataを都度READする（過去のスナップショットを持たない）。Concurrency Snapshotは**過去のある時点**のLegacy Dataを保持する。役割は完全に分離しており、Fulfillment表示のためにBaseline Snapshotを一切参照しない（`FulfillmentService`と`LegacyPoConcurrencyService`は独立したService、共通のRepositoryも持たない）。

## 18. Audit

`LEGACY_PO_BASELINE_CAPTURED`（Capture呼び出し毎、再Captureも含め毎回）・`LEGACY_PO_CHANGE_DETECTED`（Compareが実際にCHANGEDを検出した時のみ）の2種を追加。UNCHANGED/NOT_LINKED/PO_NOT_FOUND/NOT_BASELINEDでは一切Auditしない（大量生成回避、指示どおり）。

**既知のトレードオフ**（`LegacyPoConcurrencyService.compare`のJavadocに明記）: CompareはGETであり「既に通知済み」の記憶を持たないため、実際に乖離が続いている間にOrder Detailを複数回開くと、その都度`LEGACY_PO_CHANGE_DETECTED`が重複して記録される。これを防ぐ「最終通知カーソル」の永続化は本Phaseのスコープでは過剰と判断し、単純さを優先した。

## 19. UI

Order Detailの「G-SYS正式PO連携」Sectionを拡張（新Section追加ではない、指示どおり）。「G-SYSとの整合確認」というSubsectionを同一Paper内に追加し、表示状態: 未比較（NOT_BASELINED）/変更なし（UNCHANGED）/変更あり（CHANGED、Structured Diff Table付き）/G-SYS未登録（PO_NOT_FOUND）/正式PO番号未設定（NOT_LINKED）。

## 20. Action Label

Baseline Capture ボタンのLabelは「G-SYS現在状態を基準として記録」。「同期」「上書き」「更新」等、WRITEを連想させる語は一切使用していない - この制約をE2E Scenario Gで実際にUI文言をチェックして確認した（`同期|上書き|更新`にマッチするボタンが存在しないことを検証）。

## 21. Conflict Resolution

「Portalを正としてG-SYSを上書き」「G-SYSを正としてPortalを上書き」のいずれも実装していない。本Phaseは検出のみ。解決方法はCUSTOMER REVIEWのまま（22章）。

## 22. CUSTOMER REVIEW

- Portal/Excel競合時どちらを正とするか。
- 手作業Excel Uploadを今後も許可するか。
- Conflict解消権限者。
- NEW/UPDATE判定方法（正式PO番号採番規則の確定待ち）。
- Baseline取得Timing（承認直後 / Handoff直前 / 両方か）。
- Handoff直前の再確認を必須にするか（`canProceedToHandoff`をGateとして強制するか、警告に留めるか）。
- Price/Delivery等どのFieldをConflict対象にするか（本Phaseは9Fieldすべてを対象にしたが、業務上「無視してよい差異」があるかは未確認）。
- Conflict時に自動停止するか。
- Excelファイル自体のVersion管理方法。
- Manual Excel利用者への運用ルール。

## 23. Safety

絶対禁止（Legacy DB WRITE/Official PO Import/Import Folder Write/SMTP/SYS_SEND_MAIL INSERT/External API/Production接続）を全て遵守。`LegacyPoConcurrencyReadRepository`は`legacyNamedParameterJdbcTemplate`経由のSELECT文のみ。`LegacyReadOnlyIntegrationTest`に`updateAgainstTrPoDtlIsRejected`を追加し、本Phaseが新たに読むTR_PO_DTLの商流列（qty_po/prc_unit）に対しても書き込みが物理的に拒否されることを実証した。

## 24. DB Migration（V13）

`legacy_po_baseline`テーブル新設、`official_po_integration_request.integration_intent`列追加、`audit_event`のCHECK制約更新。Prototype PostgreSQLのみ、Flyway forward-only。

## 25. Backend Full Test

**337/337 tests, 0 failures, 0 errors**（Phase 7-C7A時点の299から+38: Snapshot canonicalization 3件、Fingerprint stability 5件、Diff Engine 6件、Concurrency Service統合テスト17件、Controller Permission 6件、Legacy READ ONLY追加1件）。

**実装中に発見・修正した既存テストへの副作用**: 当初PO-CONC-01/02/03のTest FixtureにOD-TENT-001/OD-CHAIR-001/OD-BAG-001等の**既存**SKUを再利用したところ、`PoPreviewServiceIntegrationTest.previewSummaryIsComputedServerSideFromOrderableLinesOnly`が実際に失敗した。原因はSourceに実在する`RecommendedQtyReadQuery.sql`の`ROW_NUMBER() OVER (PARTITION BY item_cd ORDER BY ordr_date DESC)`ロジック - 各SKUの単価/仕入先/通貨は「そのSKUを含む最新ordr_dateのPO」から導出される設計であり、新しいordr_dateを持つPO-CONCフィクスチャが既存SKUの価格解決を上書きしてしまった。**修正**: PO-CONC専用の新規Item Code（CC-ITEM-001〜005）を追加し、既存SKUとの重複を完全に排除した。修正後、全テスト2回連続クリーン実行を確認。

## 26. Safety / Legacy READ ONLY

Legacy書き込み経路はゼロ（23章）。`legacy-demo-mysql`のSchema拡張（deliv_week/deliv_date列追加）はdocker init script（`01-schema.sql`、root権限で一度だけ実行）によるものであり、Portal Applicationの実行時接続（`gsys_portal_ro`、SELECT権限のみ）とは無関係。

## 27. Frontend Build / Lint / Full E2E

`tsc -b && vite build`成功、oxlintエラー0（既存warning 4件のみ）。全E2E suite（58 tests）が2回連続で完全green（0 failures）。

## 28. Browser Scenario A-G

| Scenario | 内容 | 結果 |
|---|---|---|
| A | officialPoNoなし → 比較不可表示 | ✅ |
| B | Test Official PO → Baseline Capture → Compare → UNCHANGED | ✅ |
| C | Legacy Demo Fixture Qty変更 → Compare → CHANGED → Structured Diff表示 | ✅ |
| D-1 | Line追加 → Diff表示 | ✅ |
| D-2 | Line削除 → Diff表示 | ✅ |
| E | Rev1/Rev2 Baseline混同なし | ✅ |
| F | OPERATOR Baseline API → 403 | ✅ |
| G | 全操作後、実Legacyの書込み経路が存在しないことを確認（Label文言・READ ONLY経路） | ✅ |

## 29. Legacy変更ゼロ確認

`phasep-gulliver`に対する変更は本Phase中ゼロ。Write/Edit系ツールは一度も呼んでいない。
