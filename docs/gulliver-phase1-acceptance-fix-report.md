# G-OPS Phase1 Acceptance Fix Report

本ドキュメントは `docs/gulliver-phase1-acceptance-review.md`（変更なし）で発見された明確な不具合・UI上の不整合・分かりにくさに対する修正内容を記録したものです。
今回は新機能開発ではなく、既存Phase1機能を顧客に実際に見せられる完成度まで引き上げるための修正（Acceptance Fix）です。

**絶対ルール（Legacy変更禁止・Production接続禁止等）はすべて遵守しています。詳細は本ドキュメント末尾を参照してください。**

## 1. C-1: PDF Download Label

### Acceptance Review Issue
正式PO PDF生成後のDownloadボタンが「EXCELダウンロード」と表示され、実際にはPDFがダウンロードされるにもかかわらずExcelのラベルのままだった。

### Root Cause
`OrderHistoryDetailPage.tsx`のPDFダウンロードボタンが、Excelダウンロードボタンと**同じi18nキー**（`officialPoIntegration.downloadButton`）を参照していた。

### Fix
- i18nキーを`downloadExcelButton`（Excel用）と`downloadPdfButton`（PDF用）に分離。
- ja: 「Excelをダウンロード」／「PDFをダウンロード」、en: "Download Excel"／"Download PDF"。
- Artifact TypeとLabelを一致させ、Presentation Layerのみの変更（内部Enum/API/DB値は無変更）。

### Test
- `official-po-integration.spec.ts` Scenario F を拡張し、Excel/PDF両方のDownloadボタンが**同一画面上で同時に表示された状態**で、それぞれ正確に異なるテキスト（"Excelをダウンロード"/"PDFをダウンロード"）を持つことを明示的にアサートするよう修正（誤ったArtifactをDownloadすると誤認する問題の再発防止）。

### Browser Verification
`docs/gulliver-phase1-acceptance-fix-screenshots/04-s1-pdf-excel-download-labels-distinct.png` で、Excel/PDFセクションが同一画面上に並び、それぞれ正しいラベルで表示されていることを確認。

---

## 2. C-2: Email Send Status整合性

### Acceptance Review Issue
「デモ送信より先にメーカー送信すると、実際には送信済みなのに未完了表示になる」。

### Root Cause（追跡結果）
`EmailSendService.getStatus()`/`send()`は、どのRevisionのメール送信状態を見るかを`OfficialPoIntegrationService.targetRevisionNo(order)`で都度再計算していた。この関数は「最後に実際に送信されたRevisionの1つ先（まだ準備中のRevision）」を返す設計（Official PO再発行の対象Revisionを決めるために作られたもの）で、`order.getCurrentRevisionNo()`が`null`のときは`1`を返す。

ところが「デモ送信」（`OrderStatusTransitionService.demoSend`）は、それ自体が**初めて`PortalOrderRevision`（Revision 1）を作成する唯一の処理**であるため、デモ送信が実行された瞬間に`currentRevisionNo`が`null`→`1`に変わり、`targetRevisionNo`の計算結果が`1`→`2`に**繰り上がってしまう**。

その結果、
- デモ送信より先にメール送信（Revision 1として記録）
- その後デモ送信（`currentRevisionNo`が1になる）
- 状態確認（`getStatus`）は新たに`targetRevisionNo=2`を計算し、Revision 2のメール送信記録を探すが存在しない → 「未送信」と誤表示

**Send StatusのSource of Truth（修正後）**: メール送信のRevisionは、Order状態から都度再計算する値ではなく、**その注文に実際に存在する最新のOfficial PO Integration Request（Excel/PO番号確定などが実際に行われたRevision）のRevision番号**を正とする。Integration Requestが1件も存在しない場合のみ、フォールバックとして`targetRevisionNo`を使用する（この場合はメール送信自体がまだ実行不可能な状態なので、フォールバック値が実際の送信記録と衝突することはない）。

**状態遷移の明文化**:
- メールSend対象のRevision = `MAX(official_po_integration_request.revision_no WHERE portal_order_id = :orderId)`（存在する場合）
- デモ送信／EDI記録は、この値に一切影響しない（Integration Requestを作成・変更する処理ではないため）。
- 正式PO再発行（Reissue）が行われた場合のみ、新しい（より大きい）Revisionの Integration Requestが作成され、メールSend対象のRevisionもそれに追従する（＝再発行後は新Revisionに対して改めてメール送信が必要、という意図した挙動）。

### Fix
`backend/src/main/java/com/glv/gsysportal/service/EmailSendService.java`に`emailRevisionNo(orderId, order)`ヘルパーを追加し、`getStatus()`と`send()`の両方をこれに置き換え。

### Test
**テストを現在の正しい操作順へ固定して問題を隠すことはしていません。** 新規`EmailSendStatusConsistencyIntegrationTest.java`で、指示された4つの操作順序をすべて実際のService経由でテスト：
- Scenario A: Demo Send → Manufacturer Send → SENT
- Scenario B: Manufacturer Send → Demo Send → **SENT のまま**（修正前はここで失敗していたケース）。加えて、Demo Send後に再度Sendを呼んでも同一の送信結果を返すこと（idempotent、エラーにならないこと）も検証。
- Scenario C: Manufacturer Send only → SENT
- Scenario D: Demo Send only → 未送信（null）

4テストとも green。

### Browser Verification
`docs/gulliver-phase1-acceptance-fix-screenshots/02-s5-after-demo-send-status-recheck.png` で、メーカー送信 → デモ送信の順で操作した後も、画面の「メール送信」欄が「完了」のままであることを確認（修正前は「未完了」に戻っていた）。

### 追加で発見した関連事象（Known Limitationとして記録、今回のスコープ外）
このC-2調査の過程で、**同一の`targetRevisionNo`再計算パターンが、Official PO Integrationの他の操作（PDF/Excel生成、PO番号確定、Import Folder配置等）にも存在する**ことを発見しました。具体的には、Excel/PDFを生成 → デモ送信 → （実際の修正なしに）再度PDF生成、のような順序で操作すると、`IntegrationRequestRequiredException`が発生します。

ただし、既存のすべてのE2Eテスト・実装ドキュメントが前提とする業務フローは「Official PO関連の準備（PO番号確定・Excel・PDF・Import Folder配置）はすべてデモ送信より前に完了させる」であり、この順序を守る限り問題は発生しません。今回はユーザー指示で明示的にスコープが「Email Send Status」に限定されていたこと、また`OfficialPoIntegrationService`全体への修正はBusiness Logicへの影響範囲が大きく、慎重な設計・追加テストが必要なため、**今回は変更せず、Known Limitationとして13章に記録**します。将来、デモ送信後にPO準備操作を行う業務フローが必要になった場合は、同種の修正（実際に存在するIntegration Requestを正とする）を追加適用することを推奨します。

---

## 3. C-3: Demo Send / Manufacturer Sendの整理

### 実ソース監査結果
| | Demo Send（`demoSend`） | Manufacturer Send（Email Send, Phase 9-E） |
|---|---|---|
| 何のために存在するか | Order Statusを進め、メーカー回答（Supplier Response）の受付を開始する内部トリガー。初めての`PortalOrderRevision`スナップショットを作成する唯一の処理。 | 実際に（Demo/Test環境ではLoggingEmailSenderAdapter経由で模擬的に）メーカーへメールを送信する。 |
| どのStatusを更新するか | `PortalOrder.status`を`APPROVED`→`AWAITING_SUPPLIER`(`SENT`)へ進める。`communicationChannel`を`EMAIL`に設定。 | `order_email`テーブルの送信記録のみ。`PortalOrder.status`には一切影響しない。 |
| Productionで両方必要か | **要顧客確認**（D項目として11章に残す） | 同上 |
| Demo環境専用か | 実際のメール送信は行わないが、**Status遷移自体は全環境で必要な操作**（これがないとSupplier Responseに進めない）。名前に反してDemo専用の補助操作ではない。 | Demo/Test環境ではLoggingEmailSenderAdapterのみ有効（実SMTP未接続）。Production化にはアダプタ切替が必要（既存の実装レポートに記載済み）。 |

### 発見した具体的な混乱の原因
両方のボタンが**ほぼ同じ文言**を使っていた：
- PO Preview画面の「デモ送信」ボタン: 旧文言「メーカーへ送信」
- 発注詳細画面のOfficial PO再発行/Revision History下の実送信セクション見出し: 「メーカーへ送信」

この文言の衝突（`fulfillment-follow-up-foundation.spec.ts`のコメントで既に把握されていた既知の衝突）が、「どちらが正式発注なのか」を分かりにくくしていた実体でした。

### Fix（UI文言の整理のみ、Business Rule非捏造）
- デモ送信ボタンの文言を「メーカーへ送信」→「**メール発注済みとして記録**」に変更（EDI版の兄弟ボタン「EDI発注済みとして記録」と一貫した命名）。
- 確認ダイアログの本文も「メーカーへの発注が完了したこととしてPortal上に記録し、メーカー回答の入力を開始できるようにします。この操作自体では実際のメール送信は行われません」と、実際の挙動に即した説明へ変更。
- 実際のメール送信を行う側（Order Detail）のセクション見出し「メーカーへ送信」はそのまま維持（実際に送信する操作なので、この名称が正しい）。

これにより、「メール発注済みとして記録」（記録のみ、送信なし）と「メーカーへ送信」（実際に送信）が、文言上も明確に区別されます。

**Business Ruleは捏造していません**: 「デモ送信」の実際の機能（Status遷移トリガー）自体は一切変更していません。Productionでこの2つの操作が両方必要か、あるいはどちらかに統合すべきかは、引き続きD項目（Requirement Confirmation）として11章に残します。

### Test
E2Eテスト（`gulliver-phase1-integration.spec.ts`, `core-demo-scenario.spec.ts`ほか）を新しい文言に合わせて更新。新規アサーションとして、デモ送信ボタンが正しく「メール発注済みとして記録」と表示されることを明示的に検証。

### Browser Verification
`docs/gulliver-phase1-acceptance-fix-screenshots/02-s5-after-demo-send-status-recheck.png`（デモ送信後の成功メッセージ「メーカーへの発注を記録しました。」）で、記録操作であることが文言からも分かることを確認。

---

## 4. C-5: PO番号二重管理

### 実ソース監査結果
| | Portal内部PO番号（`prototypePoNo`、"PO-DEMO-..."） | 正式PO番号（`officialPoNo`） |
|---|---|---|
| どちらの値か | `PortalOrder.prototypePoNo` | `OfficialPoIntegrationRequest.officialPoNo`（`PortalOrder.officialPoNo`にもミラー） |
| Source of Truth | `PrototypePoNoGenerator`（Postgresシーケンス`prototype_po_no_seq`から自動採番、デモ送信/EDI記録（Confirm Order）時に一度だけ割り当て） | ADMINが「正式PO番号確定」画面で手入力 |
| なぜ二重に存在するか | `PrototypePoNoGenerator`自身のJavadocに明記：**意図的に**Legacy PO番号と混同されないよう、"PO-DEMO-"接頭辞を付けて設計されている。Official PO連携が始まる**前**（Draft確定時点）から存在する、Portal内部限定の管理番号。 | G-SYSへ実際に登録する（登録される予定の）本物のPO番号。ADMINが確定するまで存在しない。 |
| Revision時の扱い | Revisionが変わっても**変化しない**（Order自体の識別子のため）。 | 再発行（Reissue）のたびに、新しいRevisionの`OfficialPoIntegrationRequest`ごとに個別に確定する（旧Revisionの番号は変わらず保持）。 |
| Excel/PDF/File Name/G-SYS Integration/Emailでどちらを使うか | **一切使用されない**（発注詳細画面のページタイトルにのみ表示） | **すべてこちらを使用**（ファイル名`OfficialPO_{Supplier}_{Brand}_{Date}_{PONo}_{Revision}`、Excel/PDF内の項目、G-SYS Integration Requestの一意キー、メール本文の`{{poNo}}`変数） |

### 監査結果の判断
**同じBusiness Conceptの重複ではありません。** `PrototypePoNoGenerator`のJavadocが示す通り、これは意図的に設計された別概念です：
- Portal内部PO番号 = 「Draftを確定した」という事実に対するPortal専用の追跡番号（G-SYSとは無関係、Official PO連携が始まる前から存在）
- 正式PO番号 = G-SYSへの登録を意図した、実際の業務上のPO番号

したがって、**Single Source of Truthへの統合は行っていません**（無理な統合はLegacy/G-SYS Contract上の意味を壊すリスクがあり、Business Logicへの影響が大きいと判断したため、監査結果を優先し変更を停止）。

### Fix（UI上の混乱防止のみ）
発注詳細画面のページタイトル（"PO-DEMO-..."が表示される場所）の隣に、**「Portal管理番号」**という説明Chip（Tooltip付き）を追加しました。Tooltip文言：「Portal内部の管理用番号です。G-SYSへ登録する正式PO番号（下部の「G-SYS正式PO連携」セクション）とは別の番号です。」

Business Logic・データモデルの変更は一切行っていません。

### Test / Browser Verification
`docs/gulliver-phase1-acceptance-fix-screenshots/02-s5-after-demo-send-status-recheck.png`で、ページタイトル「発注詳細 - PO-DEMO-...」の右に「Portal管理番号」Chipが表示され、その下に別途「正式PO番号: FIX-S5-...」が表示されていることを確認（両者が視覚的に区別できる）。

---

## 5. C-4: Master Search / Filter

### 対象Master
メーカー通信方法管理（`ManufacturerChannelPage`）、メーカー担当者管理（`SupplierContactPage`）、メールテンプレート管理（`MailTemplatePage`）の3画面（Acceptance Reviewで指摘された、繰り返し利用で件数が肥大化する画面）。

### Fix
各画面に、既存UI Pattern（Candidate List等の検索UI）に準じた最小限の機能を追加：
- 検索テキストボックス（メーカーコード・ブランドコード・担当者名・メールアドレス・テンプレート名を対象に、大文字小文字を区別しない部分一致）
- 「無効な項目も表示」チェックボックス（**デフォルトOFF＝無効行は非表示**。Acceptance Reviewで指摘された「無効行の蓄積で一覧が見づらい」問題を直接解消）
- 「クリア」ボタン

Backendへの変更・APIの追加は一切なし（クライアント側の単純なフィルタリングのみ、高度な管理機能は追加していません）。

### Test
既存E2Eテストが「無効行が既定で非表示になる」ことを前提としていなかった箇所（`header-and-list-ux.spec.ts` Scenario G/H）を、既存の（アクティブ・非アクティブ問わず蓄積している）行を「無効な項目も表示」チェックボックスで可視化する形に更新（新規Masterデータを作成しない、既存データを一切変更しない安全な方式に修正）。

### Browser Verification
`docs/gulliver-phase1-acceptance-fix-screenshots/08-s-master-search-default-hides-inactive.png`（デフォルトで有効な1件のみ表示）、`09-s-master-search-filtered.png`（検索フィルタ適用後）で確認。

---

## 6. B項目（UI/文言仕上げ）10件対応結果

| # | Before | After | 対応方法 |
|---|---|---|---|
| B-1 | 「最終チェック結果」が英語の「PASS」 | 「問題なし」（WARNING→「要確認」、BLOCKED→「登録不可」、既存の`errorPreflightBlocked`文言と統一） | i18n翻訳追加のみ |
| B-2 | 確認ダイアログ内にSUPERSEDED/PENDING/Workflowが英語のまま | 「旧版（再発行済み）」「準備中」「G-OPS内部での状態変更」（既存のRevision History一覧の表記と統一） | i18n翻訳のみ |
| B-3 | 「Batch」という開発用語 | 「G-SYS取込処理」 | i18n翻訳のみ |
| B-4 | 「Official PO 再発行 / Revision History」等、日英混在の見出し・ボタン | 「正式PO 再発行 / Revision History」「正式POを再発行」「正式POをキャンセル」等、画面内の他箇所（正式PO番号・正式PO Excel等）と統一 | i18n翻訳のみ（「Revision」は既存の一貫した業務用語として維持） |
| B-5 | 「Demo Data（Legacy互換）」「履歴（Legacy PO実績）」等、Legacy内部用語の露出 | 「サンプルデータ」「履歴（発注実績）」、Demo Environmentバナーも「検証用のサンプルデータを表示しています」に簡素化。SKU詳細の理論利益率注記からも英語の"Actual Gross Profit"を除去 | i18n翻訳のみ |
| B-6 | Email Override後も「自動選択された宛先です」の文言が残る | 別項目（7章）として対応済み | - |
| B-7 | 連続操作時のToast重複表示 | 監査の上、意図的に現状維持（13章Known Limitations参照） | 監査のみ |
| B-8 | ドラフト保存時、Banner/Toast重複 | 別項目（8章）として対応済み | - |
| B-9 | English Navigationの折り返し | 別項目（9章）として対応済み | - |
| B-10 | Supplier Response未回答表示の不自然さ | 別項目（10章）として対応済み | - |

すべてPresentation Layer（i18nリソースファイル）のみの変更で、内部Enum/API/DB値は無変更。ja/en双方を維持（英語表示は元々問題なかった箇所が大半のため、必要な箇所のみ変更）。

---

## 7. Override後の説明

### Issue
Email To/CC Override後も、「自動選択された宛先です」という補足文言が変化せず残り、Masterで選択された宛先と今回実際に送信する宛先の違いが分かりにくかった。

### Fix
`toManuallyEdited`/`ccManuallyEdited`という既存のフロントエンド状態を活用し、フィールドが実際に編集された後は補足文言を「**今回の送信のみ、この宛先に変更されています。Masterに登録されている宛先は変更されません。**」に切り替えるよう修正。

### Test
`email-send.spec.ts`の既存Overrideテストに、編集前後で文言が正しく切り替わることを検証するアサーションを追加。

### Browser Verification
既存のOverride機能自体は本Fixで変更していないため、Acceptance Review時点の`39-s4-to-cc-overridden.png`/`41-s4-override-note-audit.png`と合わせて、送信後の監査注記（「宛先が送信時に変更されました」）は元々明確だったことを確認済み。

---

## 8. Toast / Banner重複

### Issue 1（対応済み）: ドラフト保存時のBanner/Toast矛盾
Save成功直後、「保存されていない変更があります」という警告Banner（`isDirty`駆動）と「保存しました」という成功Toastが同時に表示される瞬間があった。

**Root Cause**: Save後の`isDirty`は、再GET（Requirements MD 13章の要求：保存後に実際のDB値と画面値が一致することを再確認する仕組み）が完了して初めてfalseになる。しかしSave成功のToastは、PUTリクエスト自体が成功した瞬間に表示される。この2つの非同期完了タイミングの間に、意味が矛盾する表示が一瞬同時に出ていた。

**Fix**: `justSaved`という一時的なUI状態を追加し、Save成功からPromiseの再GET完了までの間だけ警告Bannerを抑制する（再GET自体は一切省略・偽装せず、そのまま実行）。ユーザーが再GET完了前に別の変更を加えた場合は、Bannerは正しく再表示される。

**Test**: 既存のBusiness Logic（再GETによる実際のDB値確認）は変更していないため、既存テストに影響なし。手動ブラウザ確認で、Save直後にBanner/Toastが同時表示されないことを確認。

### Issue 2（監査の上、現状維持）: 連続操作時のToast重複
Excel生成→PDF生成→Import Folder配置等、複数の操作を短時間に連続実行すると、それぞれの成功Toast（別々の意味を持つ、正しい内容のメッセージ）が画面下部の同じ位置に重なって表示されることがある。

**監査結果**: これは「同一操作に対する同じ意味の重複通知」ではなく、**それぞれ異なる操作に対する、それぞれ正しい内容の一時的な通知**です。MUIのSnackbarは複数同時に開くと同じ位置に重ねて描画される仕様で、アプリ全体で共有されている`Toast`コンポーネント（多数の画面で個別に使用）に、通知をキューイングして順番に1つずつ表示する仕組みは現状実装されていません。

この仕組み自体を全画面横断で作り直すことは、影響範囲が広く（アプリ全体で数十箇所使用）、既存のBusiness Logic・UI設計方針（Toastコンポーネント自体のJavadocに記載された明確な設計方針）に対する大きな変更となるため、**今回のAcceptance Fixのスコープでは対応せず、Known Limitationとして記録**します。実際の人間の操作速度では、通常4秒（Toastのデフォルト表示時間）以内に複数の操作を連続実行することは稀であり、業務上の実害は限定的と判断します。

---

## 9. Supplier Response未回答表示

### Issue
Supplier Response未回答時、「差異」欄に「未回答: 6 → 未回答」という、実際には差異ではないものが差異のように見える表記が出ていた。

### Root Cause
Backendの`OrderRevisionService.computeDifferences()`は、Confirmed Qtyが`null`（未回答）の場合、`TYPE_UNANSWERED`という差異エントリを`severity=INFO`で生成する（**既存の意図的なBusiness Logic**、Confirmed Qty=0との区別を保つための設計）。フロントエンドがこれを「発注数量 → 回答数量」という一律の矢印付きテンプレートで表示していたため、「未回答」というラベルと「未回答」という値が並んで表示され、不自然な文言になっていた。

### Fix
**Business Logic（backend側のnull/0の区別）は一切変更していません。** フロントエンド（`SupplierResponsePage.tsx`）のみを修正し、`type === 'UNANSWERED'`の場合は矢印テンプレートを使わず、「発注数量: 6（メーカー回答はまだありません）」という、差異ではなく未回答であることが明確な独立した文言で表示するよう変更。confirmedQty=0の場合は従来通り「数量変更: 6 → 0」という実際の差異として表示され、nullとは明確に区別される。

### Test
`supplier-response-revision-workflow.spec.ts` Scenario Dを拡張し、
- 未回答時: 矢印を含まない、「メーカー回答はまだありません」を含む表示であること
- confirmedQty=0時: UNANSWERED表示が消え、QUANTITY_CHANGED表示が「→ 0」を含むこと（0がnullと混同されないこと）
の両方を明示的に検証。

### Browser Verification
`docs/gulliver-phase1-acceptance-fix-screenshots/06-s6-unanswered-difference.png`（未回答時）、`07-s6-zero-qty-difference.png`（confirmedQty=0時）で、両者が明確に区別されて表示されることを確認。

---

## 10. English Navigation

### Issue
English表示時、ナビゲーション項目名（"ORDER CANDIDATES"、"MASTER MAINTENANCE"等）が長く、画面幅によってボタン内でテキストが途中改行される。

### Fix
ナビゲーションのStackに`whiteSpace: 'nowrap'`（ボタンラベル内での改行を禁止）と`flexWrap: 'wrap'`（画面幅が足りない場合はStack全体が2行に折り返す）を追加。既存のDesign（Stack/Buttonの構成、間隔）は変更せず、最小限のCSSプロパティ追加のみ。

**試行錯誤の記録**: 最初に`overflowX: 'auto'`（横スクロール）を試したところ、レイアウトが崩れてナビゲーション項目同士が重なって表示される問題が発生したため、この方式は不採用とし、`flexWrap: 'wrap'`方式に変更しました。修正後は日本語表示（1行に収まる）に一切影響がないことも確認しています。

### Test
`i18n-language-switch.spec.ts`、`header-and-list-ux.spec.ts`（Sticky Header判定がAppBarの高さを実測するテストを含む）を実行し、日本語表示・英語表示ともに既存テストが全てGreenであることを確認。

### Browser Verification
`docs/gulliver-phase1-acceptance-fix-screenshots/10-s-english-navigation-nowrap.png`（English、2行に折り返すが単語の途中では改行されない）、`00-ja-navigation-unaffected.png`（Japanese、1行のまま変化なし）で確認。

---

## 11. D項目（Customer Confirmationとして維持）

以下は今回UI上で改善を行った箇所を含め、**「顧客確認済み」とはせず**、引き続きRequirement Confirmationとして残します。

| # | 確認事項 | 現状 |
|---|---|---|
| D-1 | PDF正式Layout | 「G-OPS Standard Format」の暫定フォーマットのまま（正式レイアウト未確定） |
| D-2 | Reissue正式業務Rule | 検知＋ADMIN手動再発行のまま（自動再発行は未実装） |
| D-3 | Cancelの正式な業務上の意味 | G-OPS内部Workflow状態のみ（Legacy通知なし）のまま |
| D-4 | Email Override権限 | ADMIN限定のまま |
| D-5 | Default CC Rule | prefillのみ（Business Rule化なし）のまま |
| D-6 | Demo Send / Manufacturer SendのProduction運用 | 3章で文言整理のみ実施。両方が本当に必要か、統合すべきかは未確定のまま |
| D-7 | PO番号管理方針 | 4章で監査・UI上の説明を追加したのみ。将来的にどちらの番号を正式文書に用いるかは未確定のまま |
| その他 | 実装レポート記載のRequirement Confirmation全項目 | 変更なし（`docs/gulliver-20260917-phase1-implementation-report.md`参照） |

---

## 12. Acceptance Regression（ブラウザ実操作）

**ツールに関する開示**: 本セッションではClaude in Chrome拡張機能が未接続だったため、Acceptance Review時と同様、Playwrightが駆動する実際のChromiumブラウザ（同じ`localhost:5173`/`localhost:8080`に対して通常のユーザー操作と全く同じHTTPリクエスト・画面遷移を行う）を使用して実操作確認を行いました。

- **Scenario 1（通常発注 End-to-End）**: 既存の`gulliver-phase1-integration.spec.ts`のフルフロー（Candidate→SKU Detail→Draft→Approval→Official PO→Excel→PDF→G-SYS Integration→Mail Preview→Email→Supplier Response→Difference→Agreement→Arrival/Follow-up）を実操作で再確認。PDF/Excelのラベルが正しく区別されることも別途確認。Green。
- **Scenario 2（Revision / Reissue）**: 既存の`official-po-integration.spec.ts` Scenario K を実操作で再確認。「正式PO 再発行」「旧版（再発行済み）」等の新しい文言で正しく動作。Green。
- **Scenario 3（Cancel）**: 既存の`official-po-integration.spec.ts` Scenario L を実操作で再確認。「正式POをキャンセル」等の新しい文言で正しく動作。Green。
- **Scenario 4（Email Override）**: 既存の`email-send.spec.ts`の該当テストを実操作で再確認。Override後の文言切り替えを含めて確認。Green。
- **Scenario 5（Manufacturer Send First）**: 新規に実施。Master Contact選択→Mail Preview→**Manufacturer Send（先）**→Status確認（SENT）→**Demo Send（後）**→Status再確認（**SENTのまま**、修正前は「未完了」に戻っていた）。C-2の修正が実際のブラウザ操作で機能することを確認。Green。
- **Scenario 6（Supplier Response null / zero）**: 新規に実施。未回答→「発注数量: 6（メーカー回答はまだありません）」表示を確認→Confirmed Qty = 0で回答→「数量変更: 6 → 0」表示に切り替わり、未回答表示が消えることを確認。Green。

Screenshotは`docs/gulliver-phase1-acceptance-fix-screenshots/`に保存（10枚+日本語ナビ確認用1枚の計11枚）。

---

## 13. Known Limitations（今回のスコープ外として記録）

1. **C-2関連の未修正範囲**: `targetRevisionNo`の同じ設計パターンが、Official PO Integrationの他の操作（PDF/Excel生成、PO番号確定、Import Folder配置）にも存在し、「デモ送信後に、修正なしでこれらの操作を行う」という、既存のどのテスト・ドキュメントにも前提とされていない順序で操作した場合にエラーとなる。正しい運用順序（Official PO関連の準備を先に完了させる）であれば問題なし。（2章参照）
2. **Toast重複（連続操作時）**: 複数の異なる操作を数秒以内に連続実行すると、それぞれ正しい内容のToastが画面上で重なることがある。アプリ全体のToast/Snackbar機構の再設計が必要なため、今回は対応外。（8章参照）
3. **Demo Send / Manufacturer Sendの完全な分離**: 文言レベルでの整理のみ実施。UIコンポーネント・導線としての完全な分離、あるいはProductionでの最終的な統合方針は未確定（D-6として11章に記載）。
4. 実装レポート（`docs/gulliver-20260917-phase1-implementation-report.md`）記載の既存Known Limitationsはすべて未解消のまま。

## 14. Legacy変更 0件

本Acceptance Fixの実施を通じて、Legacy（`phasep-gulliver/`）のソースコード・DBに対する変更は一切行っていません。バックエンドの修正（`EmailSendService.java`）はPrototype PostgreSQLのみに影響する既存メソッドの内部ロジック修正であり、Legacyへの接続経路には一切触れていません。

## 15. Production接続 0件

Production DB・Production Import Folder・Production SMTP・Productionデプロイへの接続は一切行っていません。`SafetyGuardEnvironmentPostProcessor`による既存の安全機構は変更していません。

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
