# Supplier Response Revision / Agreement Workflow — Phase 7-C5 実装結果

`docs/target-production-procurement-workflow.md`（Target設計 11章 Supplier Response（Revision方式）/ 12章 Confirmed Qty=0 / Revision & Correction）のうち、Phase 7-C5で実装したFoundationの詳細記録です。

Scope: 既存の一発勝負のDemo Supplier Response（1 Order = 1 Response）を、複数のOrder Revision ⇄ Supplier Responseの往復に対応する形へ拡張し、Agreement（差異を受け入れて合意）とRevision作成（修正版を作って再送信）の2つの明示的Business Actionを追加。**Legacy DB WRITE・Official PO Import・Import Folder Write・SMTP・SYS_SEND_MAIL INSERT・External Mail API・Production接続は、いずれも本Phaseのコードパスに一切存在しない。**

## 0. 実装前Source監査（要点）

既存の `portal_order` / `portal_order_detail` / `supplier_response` / `supplier_response_detail` / `order_attention` / `audit_event`、`SupplierResponseService` / `SupplierWorkflowController` / `SupplierResponsePage.tsx`、7-C1 Approval Workflow、7-C2A Integration Request、7-C3 Mail Previewを完全監査した。最重要な発見は次の2点：

1. `supplier_response.portal_order_id BIGINT NOT NULL UNIQUE` - 1 Order = 1 Responseのハード制約。Revision導入にはこれを緩和する必要がある。
2. `OfficialPoIntegrationService`の`revisionNo`は`private static final int CURRENT_REVISION_NO = 1`とハードコードされていた（7-C2Aのコメントで「7-C5で本格導入」と明記済み）- Order Revisionとの整合を取る改修が必要。

## 1. 設計原則（最重要）

**「Revisionは実際にSendされた瞬間にのみ確定するSnapshot」** という設計を採用した。`portal_order_detail`（Draft編集画面が直接編集する「現在の状態」テーブル）は一切構造変更していない。

- Revision 1は初回Demo Send時に、`portal_order_detail`の現在値から自動的にSnapshotされる（`supplier_response_detail.ordered_qty`が既に同じタイミングでSnapshotされている、その仕組みを踏襲・横展開しただけ）。
- 「修正版を作成」（`POST /api/orders/{id}/revisions`）は、Orderを`SUPPLIER_CONFIRMED`から`DRAFT`へ戻すだけの操作であり、この時点ではRevision 2はまだ作られない。ADMINは既存のDraft編集画面（新規画面なし）で内容を修正し、既存の承認Workflow（7-C1をそのまま再利用、新しい承認段階は追加しない）を経て、次のDemo Send時に初めてRevision 2としてSnapshotされる。
- これにより「無理な全面Rewriteは禁止」（0章）と「新画面乱立を避ける」（22章）の両方を満たしつつ、11.1章が求める「過去Revisionは不変」を実現している。

## 2. Order Revision Model

新設2テーブル（V11 migration, Prototype PostgreSQLのみ）:

```
portal_order_revision:
  id, portal_order_id, revision_no, revision_type(INITIAL/CORRECTION),
  reason(CORRECTIONのみ必須・INITIALはNULL固定 - CHECK制約で強制),
  created_by, created_at

portal_order_revision_detail:
  id, revision_id, sku_code, item_name_snapshot, recommended_qty,
  ordered_qty, requested_delivery, unit_price, created_at
```

`line_no`と`currency`はあえて複製していない - `line_no`は表示順の関心事に過ぎず（SKU経由で`portal_order_detail`と突き合わせれば再導出できる）、`currency`はOrder単位（`portal_order.currency`）で行ごとに変わり得ないため、`supplier_response_detail`（Technical Design 5.4）が確立した「行ごとに変わり得る値だけをSnapshotする」原則をそのまま踏襲した。

`portal_order`に`current_revision_no`（nullable Integer）を追加 - 未送信のOrderはNULL、初回Send後は1、以降のSendごとに+1。`prototypePoNo`/`officialPoNo`と同じ「マイルストーンまで未割当」慣習に合わせた設計。

## 3. Migration（既存データ）

既にSend済み（`supplier_response`行を持つ）Orderは、その時点の`portal_order_detail`/`supplier_response_detail`から逆算してRevision 1をbackfillした。Revision 2以降の概念は本Phase以前には一切存在しなかったため、「現在の状態」と「実際にSendされた内容」は既存データにおいて常に一致しており、データロスなくbackfillできる（0章のMigration安全性監査の結論）。未送信（DRAFT/PENDING_APPROVAL/APPROVED）のOrderはRevisionを持たない（NULLのまま）。

## 4. Response-Revision Link

`supplier_response.portal_order_id UNIQUE` 制約を `UNIQUE(portal_order_id, order_revision_id)` へ緩和（`order_revision_id`列を追加、既存行はRevision 1へbackfillしてからNOT NULL化）。1つのRevisionにつき1つのSupplier Response、という新しい1:1関係になった。過去のRevisionに紐づくResponseは二度と書き換えられない（READ ONLY履歴）。

## 5. NULL vs 0（変更なし・再確認のみ）

`confirmedQty`のNULL（未回答）と0（明示的なゼロ回答）の区別は、Step 4から一貫して全レイヤーで維持されていることを本Phaseでも再確認した（DBカラムにDEFAULTなし、DTOは`Integer`、JSON `null`をそのまま透過）。今回変更なし。

## 6. Supply Status

`supplier_response_detail.supply_status`（nullable VARCHAR、CHECK制約で6値：`AVAILABLE / OUT_OF_STOCK / LONG_TERM_OUT_OF_STOCK / DISCONTINUED / WAITING_FOR_ARRIVAL / UNKNOWN`）を新設。**Confirmed Qtyから自動推測しない**（Qty=0だからといってOUT_OF_STOCKを自動設定しない）- NULLと明示的な"UNKNOWN"選択を区別するため、confirmedQtyと同じ「NULLと明示値」の慣習を横展開した。正式な値の定義は引き続きCUSTOMER REVIEW（20章）。

## 7. Difference Detection

`ResponseDifferenceView`（type: QUANTITY_CHANGED/DELIVERY_CHANGED/UNANSWERED、skuCode、orderedValue、confirmedValue、severity）を、現在のRevisionと現在のResponseから毎回その場で計算するAPIレスポンスとして実装（永続化しない）。既存のAttention（「差異に気づいたか」を表す永続シグナル）とは明確に別概念として扱い、両者を混同していない（9章の明示指示どおり）。

## 8. Attention

既存Attention基盤をそのまま再利用。新しいAttention Type `SUPPLY_STATUS_CHANGED` を1つだけ追加（Supply Statusが明示的にAVAILABLE以外へ設定された場合に発火、[PROTOTYPE DECISION]）。既存のQUANTITY_CHANGED/DELIVERY_CHANGED/PARTIAL_CONFIRMATIONのロジックは変更していない。

## 9. Response Confirm（変更なし）

既存のSave/Confirm分割・完了条件（全行confirmedQty非NULL）は変更していない。Confirm単体では`AGREED`にならない - 「Supplier Response確定 ≠ AGREED」を厳格に維持した。

## 10. Agreement

新規Business Action `POST /api/orders/{id}/responses/{responseId}/agree`（ADMIN限定、`@PreAuthorize`）。

- 前提条件: Orderが`SUPPLIER_CONFIRMED`であること、`responseId`が現在のRevisionに紐づくResponseであること（過去の response では 409 `RESPONSE_NOT_AGREEABLE`）。
- 未確認（ACTIVE）のAttentionが1件でもあると409 `UNACKNOWLEDGED_ATTENTION`（ただし`forceAgree=true`でADMINが明示的に上書き可能）。
- 成功時: `SUPPLIER_CONFIRMED → AGREED`、`SUPPLIER_RESPONSE_AGREED` + `STATUS_CHANGED` Auditを記録、`supplier_response.agreed_by/agreed_at`を記録。

## 11. Difference Acceptance（履歴の非破壊性）

差異を受け入れて合意しても、Revision 1（Ordered=10等）は一切書き換わらない。Response 1（Confirmed=8等）も書き換わらない。Agreement（`agreed_by`/`agreed_at`）は`supplier_response`テーブルへ追加された列であり、これら3つの事実（発注内容 / 回答内容 / 合意した事実）が同時に、互いを上書きせず共存する（12章の要求どおり）。

## 12. Revision作成（「修正版を作成」）

`POST /api/orders/{id}/revisions`（ADMIN限定）。前提: Orderが`SUPPLIER_CONFIRMED`であること。Body: `{reason: string(必須), applyConfirmedValues: boolean}`。

- `reason`未入力は400 `REVISION_REASON_REQUIRED`。
- `applyConfirmedValues=true`の場合のみ、現在のResponseの`confirmedQty`（未回答行を除く）を`portal_order_detail.orderQty`へ反映（既存の`ORDER_QTY_CHANGED` Auditイベントをそのまま再利用 - 新規イベント型は追加していない）。デフォルトはfalseで、何も自動変更しない。
- `requestedDelivery`はOrderヘッダ単位（行単位ではない）のため、このフラグでは反映しない - ADMINが既存Draft編集画面で手動入力する。
- 成功時: Orderを`DRAFT`へ戻し、`ORDER_REVISION_CREATED`（`note`に理由）+ `STATUS_CHANGED` Auditを記録。

## 13. Revision編集・承認との関係

Revision 2の「編集」は、既存のDraft編集画面・API（`PUT /api/orders/drafts/{id}`）をそのまま使う - 新しい編集APIは一切追加していない。承認も既存の7-C1単一段階Approval Workflow（DRAFT→PENDING_APPROVAL→APPROVED）をそのまま再利用する。多段階承認や新しい承認ロジックは追加していない（15章の明示的な禁止事項どおり）。

## 14. Integration Requestとの関係

`OfficialPoIntegrationService`の`revisionNo`計算を、ハードコードされた`1`から `order.getCurrentRevisionNo() == null ? 1 : currentRevisionNo + 1` へ変更した。Integration RequestはOrderが`APPROVED`（＝まだSendされる前）の時点で作られるため、「次にSendされた時に確定するはずのRevision番号」を先取りして指す形になる - 初回フローでは従来どおり常に1（後方互換）、修正サイクル後は正しく2以降を指すようになる。過去のIntegration Requestは一切削除・変更しない。

## 15. Mail Previewとの関係

`MailPreviewService`の`{{revisionNo}}`は既にIntegration Requestの`revisionNo`から取得する実装だったため、14章の修正だけで自動的にRevision-aware になった（Mail Preview自体のコード変更は不要）。

## 16. Reopen Agreement

新規Business Action `POST /api/orders/{id}/responses/{responseId}/reopen`（ADMIN限定）。前提: Orderが`AGREED`であること（違反時409 `ORDER_NOT_AGREED`）、`reason`必須（未入力時400 `REOPEN_REASON_REQUIRED`）。

`AGREED → SUPPLIER_CONFIRMED`へ戻す。**`agreed_by`/`agreed_at`は消さない**（直接データ上書きの禁止）- `reopened_by`/`reopened_at`/`reopen_reason`を別列として追加記録する。同じResponseに対して複数回Agree/Reopenが起きた場合、ヘッダ列は「直近1回分」しか保持しないが、完全なN回サイクルの履歴は既存の`audit_event`（`SUPPLIER_RESPONSE_AGREED`/`AGREEMENT_REOPENED`行）から再構成可能 - この設計は`SupplierResponse`自身の既存Javadocが述べる「ヘッダは最新値のみ、完全な履歴はaudit_eventから再構成する」という、このスキーマ全体で一貫した思想の延長。

## 17. Workflow Status Migration

現在実際に到達可能な値: `DRAFT / PENDING_APPROVAL / APPROVED / SENT / AWAITING_SUPPLIER / SUPPLIER_CONFIRMED`（6値）。`COMPLETED`はV1由来でCHECK制約の許可リストには残っているが、Javaの定数・コードパスのどこからも一度も書き込まれていない「死んだ値」であることを監査で確認した - 触らずそのまま残した（本Phaseのスコープ外、既存挙動に影響なし）。今回追加したのは`AGREED`のみ（`SUPPLIER_CONFIRMED → AGREED`は新設のAgreement Actionを経由した場合のみ到達）。「Supplier Response確定 ≠ AGREED」は全経路で維持されている。

## 18. Order Detail UI

Order History Detailページに2つの新セクションを追加:

- **Revision History**（Rev1, Rev2, ...）: 各Revisionのtype（INITIAL/CORRECTION）、理由、作成者・日時、行ごとのSnapshot値を一覧表示。
- **Response History**（Response1, Response2, ...）: 各Responseの状態、現在かどうか、合意者/合意日時、取り消し者/取り消し理由を一覧表示。

いずれもOrderが一度もSendされていない場合は非表示（データが存在しないため）。

## 19. Supplier Response UI

既存のSupplier Response画面（新規画面は作らず、既存画面を拡張）に追加:

- Supply Status選択（回答編集可能時のみ、6候補+未回答）。
- Differences（発注内容 vs 回答内容の差異）パネル。
- `SUPPLIER_CONFIRMED`時: Agreement セクション（合意する/修正版を作成 ボタン、ADMIN限定 - OPERATORには「管理者のみ行えます」の案内のみ表示。Backend側は`@PreAuthorize`で強制、Frontendは利便性のための非表示に過ぎない）。
- `AGREED`時: 合意情報の表示＋Reopen（合意を取り消す）ボタン（ADMIN限定）。
- Response History（自分自身の画面内にも簡易版を表示、Order Detail側の同名セクションと役割は重複するが利用文脈が異なるため両方に置いた）。

過去のResponse（Order Detail経由の`GET /api/orders/{id}/responses/by-revision/{revisionNo}`）はREAD ONLY - 現在のRevisionに対応するResponseのみが編集可能画面として表示される。

## 20. Audit

新規AuditEvent種別3つ: `SUPPLIER_RESPONSE_AGREED` / `ORDER_REVISION_CREATED` / `AGREEMENT_REOPENED`。

`SUPPLIER_RESPONSE_SAVED`と`ORDER_REVISION_UPDATED`は既存イベントとの重複を検討した上で**意図的に追加しなかった**: Saveは既にフィールド単位のQUANTITY_CHANGED/DELIVERY_CHANGED行を書いており、それに加えて「保存が起きた」だけの粒い印を重ねる必要性は薄い（この判断はStep 4当時から一貫している）。Revision 2編集中のQty変更も、既存のDraft編集フロー（`ORDER_QTY_CHANGED`等）をそのまま再利用しているため、「ORDER_REVISION_CREATED行と次のDEMO_SENT行の間にあるORDER_QTY_CHANGED行」というAuditの並び自体が「修正版の編集」であることを示しており、専用イベントは不要と判断した。

## 21. DB Migration（V11）

新設2テーブル + `portal_order.current_revision_no`列 + `supplier_response`への6列追加（`order_revision_id`/`agreed_by`/`agreed_at`/`reopened_by`/`reopened_at`/`reopen_reason`）+ `supplier_response_detail.supply_status`列 + `portal_order`/`audit_event`/`order_attention`各CHECK制約の更新（`AGREED`ステータス、新規Auditイベント3種、`SUPPLY_STATUS_CHANGED` Attention種別）。既存データのMigrationは3章のとおり安全。`DemoResetRunner`のTRUNCATE対象へ`portal_order_revision`/`portal_order_revision_detail`を追加済み。

## 22. Backend Full Test

`mvn clean test`で**275/275 tests, 0 failures, 0 errors, 0 skipped**（Phase 7-C3時点の247から+28: Revision作成/不変性/Response-Revision Link/Difference Detection/Supply Status/Agreement/Reopen/Permission/Integration Request revisionNo整合を追加）。

## 23. Safety / Legacy READ ONLY

本Phase中、`phasep-gulliver`（Legacy）に対する変更は一切なし。Legacy DBへのWRITEは全経路（既存のLegacy READ ONLY Repositoryのみ再利用、新規Legacy接続なし）で発生しない。SMTP・SYS_SEND_MAIL INSERT・External Mail API・Official PO Import・Import Folder Write・Production接続、いずれも本Phaseのコードパスに存在しない。

## 24. Frontend Build / Lint

`tsc -b && vite build`成功。oxlintエラー0（既存warning 4件のみ、うち1件は本Phaseの新規useEffect - 既存の同種warning（CandidateListPage/OrderDraftPage）と同じパターンであり新しい種類の問題ではない）。

## 25. Full E2E / Browser Scenario A-G

新設 `supplier-response-revision-workflow.spec.ts`（7 Scenario）を含む全E2E suiteを実行。

| Scenario | 内容 | 結果 |
|---|---|---|
| A | Order Rev1 → Response1 → Qty一致 → Confirm → Agree → AGREED | ✅ |
| B | Rev1 Qty差異 → Attention → 差異確認（Acknowledge） → 差異のまま合意（force不要） → Rev1のOrdered Qtyは変更されないまま保持 | ✅ |
| C | Rev1 Qty差異 → 修正版作成（applyConfirmedValues=true） → 再承認・再送信 → Rev2確定 → Rev1/Response1が履歴として閲覧可能 | ✅ |
| D | confirmedQty=0（NULLとは別表示） → Supply Status(欠品)選択 → SUPPLY_STATUS_CHANGED Attention表示 | ✅ |
| E | AGREED後のSupplier Response画面は編集不可（入力欄readonly、Save/Confirmボタン非表示） | ✅ |
| F | OPERATORはAgree/Create Revisionボタンが非表示、「管理者のみ行えます」の案内のみ | ✅ |
| G | 一連の操作を通じて実Legacy書き込み経路が存在しないことをUIから再確認（Demo Send成功時の「実際のメールは送信されていません」バナーの存在確認） | ✅ |

**実装上の注意（E2E）**: Draft作成直後はQty未変更（`isDirty=false`）のため、既存の`save-draft-button`は無効のまま - 本Suiteは意図的にQtyをRecommended Qtyのまま使うため、Saveをクリックせず直接Submit for Approvalへ進む設計にした（既存の`core-demo-scenario.spec.ts`はQtyを変更するため`save-draft-button`を使う、という違いであり、いずれも正しい）。

## 26. CUSTOMER REVIEW（このPhaseで確定していない事項 — Target設計 21章から継続）

- Supply Status（供給状況）の正式な値・定義（Target 11.2章 CUSTOMER REVIEW #4）。
- 差異を「受け入れる」権限・条件の正式なルール（Target 11.3章 CUSTOMER REVIEW #6）。
- Confirmed Qty=0の理由分類の正式リスト（Target 12.1章 CUSTOMER REVIEW #9）。
- Correction（軽微な値訂正）とRevision（修正版作成）の境界線の正式な運用ルール（Target 12.2章 CUSTOMER REVIEW #10）- 本Phaseでは大小を問わずすべて「修正版を作成」に統一し、軽微なCorrectionという別Actionは実装していない。
- Revision再送信時の再承認要否の正式ルール（既存の単一段階承認をそのまま流用したが、これが正式仕様として十分かはCUSTOMER REVIEW）。
- AGREEDの正式な完了条件、Reopenを行える権限の正式な範囲・回数上限。
- 修正版のPO番号採番規則（Official PO No.の採番規則自体が7-C2A以降ずっと未確定のCUSTOMER REVIEW - 7-C5でも変わらず）。
- **[Phase 7-E Section 8で追加] Revision跨ぎのAttentionが「解消済み」として区別されない問題。** `OrderAttention`（`backend/src/main/java/com/glv/gsysportal/domain/OrderAttention.java`）には元々Revisionへの参照Fieldが一切存在せず、`portalOrderId`/`portalOrderDetailId`のみでOrder全体にスコープされる（`OrderHistoryService.findByPortalOrderIdAndActiveTrue`が発注詳細画面の表示に使う唯一のSourceで、これもRevision非スコープ）。QUANTITY_CHANGED/DELIVERY_CHANGED/SUPPLY_STATUS_CHANGEDは「ACTIVEのまま、ユーザーが確認するまで残り続ける」設計（8章）だが、Revision 1で数量差異が発生してAttentionがACTIVEになった後、Revision 2でその差異が解消（発注数量と回答数量が一致）しても、Revision 1由来のAttentionはACTIVEのまま画面に表示され続ける - 「過去のRevisionで発生し既に解消済みのAttention」と「現在のRevisionで未解決のAttention」が画面上で区別されない。実際に7-C6以降のBug Report調査で、Revision 2で数量差異が存在しないOrderに対して「数量変更あり」Attentionが表示され続ける事例として発見（推測ではなくSource確認済み: `SupplierResponseService.applyLineUpdate`のQUANTITY_CHANGED発火ロジック自体は正しく、単に過去分をRevision跨ぎで自動Resolveする仕組みが存在しないだけ）。本Phaseはこの挙動を仕様変更しない（監査のみ、Section 8の指示どおり）。将来的な選択肢: (a) Attentionに`portalOrderRevisionId`を追加しRevision単位でスコープする、(b) Revision作成時に旧Revision由来のACTIVE Attentionを自動的にRESOLVEDへ遷移させる、(c) 画面表示側で「これはどのRevision由来か」を明示するLabelを追加する（データは変えず表示のみ改善）。どれを採るかはCUSTOMER REVIEW。

## 27. Legacy変更ゼロ確認

`phasep-gulliver`に対する変更は本Phase中ゼロ（`git diff -w --numstat`で実質差分0件、HEADも不変）。Write/Edit系ツールは一度も呼んでいない。
