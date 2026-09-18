# G-OPS Phase1 Revision Consistency Audit

本ドキュメントは、Acceptance Fix（`docs/gulliver-phase1-acceptance-fix-report.md`、変更なし）のC-2で発見された
`targetRevisionNo`同型問題について、Official PO関連処理全体を横断監査し、RevisionのSource of Truthを統一した結果を記録したものです。

**今回はRevision整合性のみを対象とします。PDF Layout・Reissue/Cancel業務Rule・Email Override権限・Default CC Rule・
Domestic/Overseas計算式・Demo/Manufacturer SendのProduction運用決定・PO番号Business Rule・UI Designの大規模変更・
Toast改善には一切手を加えていません。Legacy変更・Legacy DB WRITE・Production接続・Production Deployも一切行っていません。**

## 1. Revision関連処理一覧（監査対象）

`targetRevisionNo` / `currentRevisionNo` / `revision + 1` / `latestRevision` / `latest request` / `latest document`
というキーワードで、以下のクラスをすべて検索・監査した。

| クラス | メソッド | 分類 |
|---|---|---|
| `OfficialPoIntegrationService` | `requestIntegration` | 新Revision作成 |
| `OfficialPoIntegrationService` | `reissue` | 新Revision作成 |
| `OfficialPoIntegrationService` | `getIntegration` | 既存Revision参照（読み取り専用） |
| `OfficialPoIntegrationService` | `cancel` | 既存Revision操作 |
| `OfficialPoIntegrationService` | `getRevisionHistory` | 全Revision参照（履歴） |
| `OfficialPoIntegrationService` | `setIntegrationIntent` | 既存Revision操作 |
| `OfficialPoIntegrationService` | `confirmOfficialPoNumber` | 既存Revision操作 |
| `OfficialPoIntegrationService` | `generateExcel` | 既存Revision操作 |
| `OfficialPoIntegrationService` | `generatePdf` | 既存Revision操作 |
| `OfficialPoIntegrationService` | `downloadPdf` | 既存Revision操作（Download） |
| `OfficialPoIntegrationService` | `placeToImportFolder` | 既存Revision操作 |
| `OfficialPoIntegrationService` | `confirmImport` | 既存Revision操作 |
| `OfficialPoIntegrationService` | `downloadExcel` | 既存Revision操作（Download） |
| `LegacyPoConcurrencyService` | `captureBaseline` | 新Revision（Baseline）作成 |
| `LegacyPoConcurrencyService` | `compare` | 既存Revision操作 |
| `LegacyPoConcurrencyService` | `canProceedToHandoff` | 既存Revision参照（`compare`経由） |
| `EmailSendService` | `getStatus` | 既存Revision操作 |
| `EmailSendService` | `send` | 既存Revision操作 |
| `MailPreviewService` | `preview` | 既存Revision操作（読み取り専用） |

対象とした業務機能（指示書2章のリスト）: Official PO Excel Generate／PDF Generate／Download／Import Folder
Placement／Integration Request／Integration Status／Mail Preview／Manufacturer Send／Demo Send・Send Record／
Reissue／Revision Required／Replace／Cancel／Revision History／Audit／File Naming。

Demo Send / Send Record（`OrderStatusTransitionService.demoSend`/`recordEdiSend`）自体は、Revisionを
**参照も算出もしない**（`PortalOrderRevision`スナップショットを新規作成する側であり、Official PO側のRevision解決とは
無関係）ため、監査対象から除外（問題なしと確認済み）。

## 2. targetRevisionNo同型問題が見つかった箇所

| # | 箇所 | 修正前の実装 | 問題 |
|---|---|---|---|
| 1 | `EmailSendService.getStatus`/`send` | `targetRevisionNo(order)` | **Acceptance Fix C-2で既に発見・修正済み**（今回の監査で他箇所と統一） |
| 2 | `OfficialPoIntegrationService.setIntegrationIntent` | `targetRevisionNo(order)` | 既存Requestへの注釈付け操作なのに予測値を使用 |
| 3 | `OfficialPoIntegrationService.confirmOfficialPoNumber` | `targetRevisionNo(order)` | 既存Requestへの操作なのに予測値を使用（PDF Downloadで実際にエラー再現） |
| 4 | `OfficialPoIntegrationService.generateExcel` | `targetRevisionNo(order)` | 同上 |
| 5 | `OfficialPoIntegrationService.generatePdf` | `targetRevisionNo(order)` | 同上（Acceptance Fix中の実操作で`IntegrationRequestRequiredException`として実際に再現・確認済み） |
| 6 | `OfficialPoIntegrationService.placeToImportFolder` | `targetRevisionNo(order)`（File Name・Idempotency Keyにも使用） | 同上、かつFile Nameが誤ったRevision番号になり得た |
| 7 | `OfficialPoIntegrationService.confirmImport` | `targetRevisionNo(order)` | 同上 |
| 8 | `LegacyPoConcurrencyService.compare` | `targetRevisionNo(order)` | 既存Baselineとの比較なのに予測値を使用 - Demo Send後にBaselineが「NOT_BASELINED」へ誤判定される（既存テスト`revision1BaselineIsNeverReusedForRevision2`、およびE2E `legacy-po-concurrency-control.spec.ts`の既存「Scenario E: Rev1/Rev2 Baseline混同なし」の両方が、この誤動作をそのまま期待値として固定していたことも判明） |

`OfficialPoIntegrationService.requestIntegration`/`reissue`（新Revision作成）、`LegacyPoConcurrencyService.captureBaseline`
（新Baseline作成）、`getIntegration`/`getRevisionHistory`/`downloadExcel`/`downloadPdf`（既に`findFirstByPortalOrderIdOrderByRevisionNoDesc`
を使用）、`MailPreviewService.preview`（同左）は、監査の結果、当初から正しい方式を使用していたことを確認した。

## 3. Root Cause

`targetRevisionNo(order)`は「まだ準備中の、次に作成すべきRevision番号」（`currentRevisionNo`が`null`なら1、
そうでなければ`currentRevisionNo + 1`）を返す関数である。この関数は本来、**新しいOfficial PO Integration
Requestを作成する瞬間**（`requestIntegration`／Baseline初回捕捉）にのみ正しい。

しかし多くのメソッドが、**既に存在するRequest/Baselineを読み書きする際にも**この同じ関数を使い、その場で
再計算していた。デモ送信（`OrderStatusTransitionService.demoSend`）は、初めて`PortalOrderRevision`（Revision 1）
を作成する唯一の処理であるため、デモ送信が実行された瞬間に`currentRevisionNo`が`null`→`1`に変わり、
`targetRevisionNo`の計算結果が`1`→`2`に**繰り上がってしまう**。修正・再発行（Reissue）が一切起きていないにも
かかわらず、「既存Revision操作」系のメソッドが軒並み「Revision 2」を探しに行き、実際にはRevision 1しか
存在しないため`IntegrationRequestRequiredException`（Excel/PDF/PO番号確定/Import Folder配置/取込確認）や
誤った比較結果（`NOT_BASELINED`）を引き起こしていた。

## 4. Revision Source of Truth

**新しい重複データモデルは作成していない。** 既存の`OfficialPoIntegrationRequestRepository`
（`official_po_integration_request`テーブル、Flyway管理・変更なし）を唯一のSource of Truthとし、以下の
2つの解決メソッドに整理した（`OfficialPoIntegrationService`に追加、いずれもprivate/package内static）。

```java
// 新Revision作成専用（既存、変更なし）
static int targetRevisionNo(PortalOrder order) {
    return order.getCurrentRevisionNo() == null ? 1 : order.getCurrentRevisionNo() + 1;
}

// 既存Revision操作専用（今回新規追加）- 実際に存在する最新Requestを返す。
// 存在しなければ IntegrationRequestRequiredException をthrow。
private OfficialPoIntegrationRequest resolveCurrentRequest(PortalOrder order) {
    return integrationRequestRepository.findFirstByPortalOrderIdOrderByRevisionNoDesc(order.getId())
            .orElseThrow(() -> new IntegrationRequestRequiredException(order.getId(), targetRevisionNo(order)));
}

// 上記のnon-throwing版（Revision番号のみ必要な呼び出し元向け）- 今回新規追加。
static int resolveCurrentRevisionNo(OfficialPoIntegrationRequestRepository repo, PortalOrder order) {
    return repo.findFirstByPortalOrderIdOrderByRevisionNoDesc(order.getId())
            .map(OfficialPoIntegrationRequest::getRevisionNo)
            .orElseGet(() -> targetRevisionNo(order));
}
```

`EmailSendService`は自前で同じクエリを実装していた箇所を、この`resolveCurrentRevisionNo`を呼び出す形に
統合した（指示書8章の「より共通的なResolverへ統合する方が安全なら統合して構いません」に対応）。
`LegacyPoConcurrencyService.compare`も同じ`resolveCurrentRevisionNo`を呼び出すよう修正した。

## 5. 新Revision生成Rule

**Reissue等、明示的に新Revisionを作成する操作だけが`current + 1`（`targetRevisionNo`）を使用してよい。**

- `OfficialPoIntegrationService.requestIntegration`（初回のG-SYS連携準備）
- `OfficialPoIntegrationService.reissue`（内部で`requestIntegration`を再利用 - 新規Requestを作る経路は
  これ一本のみで、パラレルな別経路は存在しない）
- `LegacyPoConcurrencyService.captureBaseline`（新しいBaseline行を追加する処理）

以上の3箇所以外で`targetRevisionNo`を直接呼び出すことは禁止とする（今回、他のすべての呼び出しを
`resolveCurrentRequest`/`resolveCurrentRevisionNo`に置き換えた）。

## 6. 既存Revision操作Rule

Excel／PDF生成・Download・Import Folder配置・Integration Status確認・Cancel・Integration Intent設定・
Email Send／Statusなど、既に存在するOfficial PO Documentを対象とする操作は、**予測Revisionを使わず、
実際に存在する最新Requestを`resolveCurrentRequest`/`resolveCurrentRevisionNo`経由で解決する。**

Revision History（過去のRevisionを一覧・参照する操作）は、`findByPortalOrderIdOrderByRevisionNoAsc`で
**全Revisionを明示的なRevision Noと共に**返す既存実装のままとし、「常に最新Revisionへ読み替える」ことは
していない（3章原則Cを既に満たしていたことを確認）。

なお、**過去の特定RevisionのExcel/PDFを個別にDownloadするAPI/UIは現時点で存在しない**（`downloadExcel`/
`downloadPdf`は常に「現在の最新Revision」を返す設計）。これは新機能追加となるため、今回のスコープでは
実装していない。7章のRegression Testでは、この「常に最新Revisionを正しく返す」という既存の契約が
Reissue前後で壊れていないことを確認し、Remaining Limitationとして8章に記録した。

## 7. 修正内容（ファイル別）

- `backend/src/main/java/com/glv/gsysportal/service/OfficialPoIntegrationService.java`:
  `resolveCurrentRequest`/`resolveCurrentRevisionNo`を追加。`setIntegrationIntent`/`confirmOfficialPoNumber`/
  `generateExcel`/`generatePdf`/`placeToImportFolder`/`confirmImport`/`cancel`/`reissue`を
  `resolveCurrentRequest`経由に統一。`placeToImportFolder`のFile Name/Idempotency Keyの算出も
  `request.getRevisionNo()`（実際の値）を使うよう修正。
- `backend/src/main/java/com/glv/gsysportal/service/LegacyPoConcurrencyService.java`:
  `compare`のRevision解決を`OfficialPoIntegrationService.resolveCurrentRevisionNo`経由に修正。
- `backend/src/main/java/com/glv/gsysportal/service/EmailSendService.java`:
  独自実装だった`emailRevisionNo`を`OfficialPoIntegrationService.resolveCurrentRevisionNo`呼び出しに統合。
- テスト修正: `OfficialPoImportFolderPlacementRetryTest`（Mockのスタブ対象Repositoryメソッドを更新）、
  `LegacyPoConcurrencyServiceIntegrationTest.revision1BaselineIsNeverReusedForRevision2`
  （旧バグをそのまま期待値にしていたテストを、実際のReissueサイクルで検証する形に修正）、
  `legacy-po-concurrency-control.spec.ts`「Scenario E: Rev1/Rev2 Baseline混同なし」（E2E。同じく
  「Demo Send単独で対象Revisionが2へ進み、Baselineが`NOT_BASELINED`になる」ことをそのまま期待値に
  していた旧バグ固定テストだったため、実際の修正＋Reissueサイクルを経て初めてRevision 2が生成される
  形に修正 - 修正後は「Demo Send単独ではRevision 1のBaselineがUNCHANGEDのまま」という新規Assertionも追加）。
- 新規テスト: `OfficialPoRevisionConsistencyIntegrationTest.java`（Backend、Scenario A〜F）、
  `official-po-integration.spec.ts` Scenario M（E2E）。

## 8. Excel修正結果

`generateExcel`が`resolveCurrentRequest`経由になり、デモ送信・メーカー送信後の再呼び出しでも常に現在の
対象Revisionを正しく解決する。`OfficialPoRevisionConsistencyIntegrationTest.scenarioB`で、メーカー送信後に
`generateExcel`を再度呼んでも例外にならず、Revision 1のまま応答することを確認。

## 9. PDF修正結果

`generatePdf`も同様に修正。Acceptance Fix時の実ブラウザ操作で発見した「デモ送信後にPDF生成すると
`IntegrationRequestRequiredException`」という実際のエラーが解消されたことを、`scenarioA`/`scenarioB`で確認。

## 10. Download修正結果

`downloadExcel`/`downloadPdf`は元々`findFirstByPortalOrderIdOrderByRevisionNoDesc`を使用しており、
本質的な修正は不要だった。Reissue後に正しくRevision 2のArtifact（別のPO番号・別のFileKey）を返すことを
`scenarioC`で確認（過去Revisionの個別Download機能自体は8章参照）。

## 11. Import Folder修正結果

`placeToImportFolder`を`resolveCurrentRequest`経由に修正し、Idempotency KeyとFile Name生成の両方で
`request.getRevisionNo()`（実際の値）を使うよう修正。File Naming（14章）も参照。

## 12. Integration Status修正結果

`confirmImport`（G-SYS取込確認）を`resolveCurrentRequest`経由に修正。デモ送信後でも正しいRevisionの
Integration Statusを確認できることを確認。

## 13. Email Send再確認結果

Acceptance Fix C-2の実装（`findFirstByPortalOrderIdOrderByRevisionNoDesc`を直接呼ぶ独自実装）を再監査し、
今回のSource of Truthと完全に整合していたため、`OfficialPoIntegrationService.resolveCurrentRevisionNo`
呼び出しに置き換えて一本化した（C-2の挙動自体は変更していない - `EmailSendStatusConsistencyIntegrationTest`
の既存4テストがそのままGreenであることで確認済み）。C-2の再発なし。

## 14. Reissue修正/確認結果

`reissue`は元々新Revision作成の正しい経路だったため、内部の重複コード（`findFirstByPortalOrderIdOrderByRevisionNoDesc`
の直書き）を`resolveCurrentRequest`に置き換えたのみ（ロジック自体は無変更）。`scenarioD`/`scenarioE`/`scenarioF`で
Reissue前後の状態遷移が正しいことを確認。

## 15. Cancel修正/確認結果

`cancel`も同様に内部の重複コードを`resolveCurrentRequest`に置き換えたのみ。`scenarioF`で、Revision 2を
CancelしてもRevision 1のSUPERSEDED状態・PO番号・Artifactは一切影響を受けないこと、CancelledなDocumentへの
再Cancelが正しく拒否されることを確認。

## 16. File Naming確認結果

`placeToImportFolder`のFile Name生成が`request.getRevisionNo()`（実際の値）を使うよう修正された。
`downloadExcel`/`downloadPdf`は元々`request.getRevisionNo()`を使用済みで問題なし。`scenarioC`で、
Revision 1のArtifactが`_001`、Revision 2のArtifactが`_002`で終わるFile Nameを持つことを確認
（Download／Demo Send実行有無に関わらずRevision番号が変化しないことも確認）。

## 17. Scenario結果

すべてBackend（`OfficialPoRevisionConsistencyIntegrationTest`）+ 一部E2E（`official-po-integration.spec.ts`
Scenario M）で実操作確認済み、全件Green。

- **Scenario A**（001生成→Demo Send→Excel/PDF Download→すべて001対象）: Green
  （`scenarioA_demoSendThenDownloads_bothStayOnRevision1`）
- **Scenario B**（001生成→Manufacturer Send→PDF/Excel再Download→001のまま）: Green
  （`scenarioB_manufacturerSendThenDownloads_staysOnRevision1`）
- **Scenario C**（001→Reissue→002→001/002 Downloadがそれぞれ正しいArtifact）: Green
  （`scenarioC_reissue_eachRevisionKeepsItsOwnArtifact`。個別Revision Download APIが存在しないため、
  「現在のAPIが常に正しい最新Revisionを返すこと」と「モデルレベルで両Revisionが自身のArtifactを
  保持していること」を検証 - 8章参照）
- **Scenario D**（001→Reissue 002→001 SUPERSEDED/002 ACTIVE→Manufacturer Sendが002を対象）: Green
  （Backend: `scenarioD_manufacturerSendAfterReissue_targetsTheNewActiveRevision`、
  E2E: Scenario M - 実ブラウザでRevision History上「Revision 2: 送信済み／Revision 1: 送信済みでない」を確認）
- **Scenario E**（001→Demo Send→Reissue→002、003等へ不当に進まない）: Green
  （`scenarioE_demoSendBeforeReissue_revisionNeverOverAdvancesPast2`）
- **Scenario F**（001→002→Cancel 002→Revision History保持）: Green
  （`scenarioF_cancelRevision2_bothRevisionsKeepCorrectStateAndHistory`）

## 18. Backend Test PASS数

560/560（既存554 + 今回追加6）。

## 19. Frontend Test結果

TypeScriptビルド: エラー0件。i18n parity: 39/39 Green。

## 20. E2E PASS/SKIP/FAIL

172/0/0（既存171 + 今回追加のScenario M 1件）。

## 21. i18n parity

Green（39/39）。

## 22. Legacy変更 0件

本監査・修正の実施を通じて、Legacy（`phasep-gulliver/`）のソースコード・DBに対する変更は一切行っていません。
すべての修正はPrototype PostgreSQLのみに影響する既存メソッドの内部ロジック修正（Revision解決方法の統一）
であり、Legacyへの接続経路には一切触れていません。

## 23. Production接続 0件

Production DB・Production Import Folder・Production SMTP・Productionデプロイへの接続は一切行っていません。

---

## Remaining Limitation（今回のスコープ外として記録）

1. **過去Revision個別Download機能の不在**: Excel/PDFのDownloadは常に「現在の最新Revision」のみを返す。
   特定の過去Revision（例: SUPERSEDED状態のRevision 1）のArtifactを個別にDownloadするUI/APIは存在しない。
   新機能追加となるため今回は実装していない。将来必要になった場合、`resolveCurrentRequest`と同じ
   Source of Truth（`OfficialPoIntegrationRequestRepository`）の上に、明示的なRevision No.を受け取る
   新しいAPIを追加する形が自然である。
2. **Revision History画面のキャッシュ不整合（Frontendのみ、Backendは正しい）**: メーカーへメールを送信した
   直後、発注詳細画面のRevision Historyテーブルが「送信状況」列を即座に更新しない（React Queryの
   Cache Invalidationが`useSendEmail`のonSuccessで`['official-po-revisions', orderId]`を対象に
   含んでいないため）。Postgres上のデータ自体（`order_email.revision_no`）は常に正しく記録されている
   ことを直接確認済みで、Revision Source of Truthの問題ではない。画面を再読み込みすれば正しい値が
   表示される。今回は「Revision整合性のみ」というスコープに厳密に従い、UI/Reactの変更を伴うこの修正は
   行っていない。
3. **Domestic/Overseas計算式・PO番号Business Rule等**: 指示書9章の「変更禁止」対象はすべて無変更。

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
