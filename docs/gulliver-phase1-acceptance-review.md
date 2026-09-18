# G-OPS Phase1 Acceptance Review

本ドキュメントは、実装済みのG-OPS Phase1機能について、**技術的にTestがGreenか**ではなく、
**Gulliver社の担当者が実際にこの画面で業務できるか**という利用者目線での確認（Acceptance Review）結果をまとめたものです。

**今回は監査・操作確認のみです。実装（Source変更・DB Migration・Business Logic変更・Legacy変更・Production接続）は一切行っていません。**

## 0. 実施方法・前提

- 実施環境: Local / Demo環境のみ（`localhost:5173` フロントエンド、`localhost:8080` バックエンド、Demo Postgres/Legacy互換MySQL）。Production接続なし。
- **ツールに関する開示**: 本レビューはブラウザで実際の画面を操作して行いましたが、このセッションでは Claude in Chrome 拡張機能が未接続だったため、代わりに Playwright が駆動する実際の Chromium ブラウザ（同じ `localhost:5173`/`localhost:8080` に対して、通常のユーザー操作と全く同じHTTPリクエスト・画面遷移を行う）を使用しました。表示されている画面・データ・挙動はすべて実際にサーバーから返された本物のレンダリング結果であり、モックやスタブではありません。
- 実施したデータ操作（Draft作成・承認・Official PO発行・メール送信シミュレーション等）は、既存のE2Eテストスイートが日常的に行っているものと同種の、Demo環境内の通常の業務操作です。Legacy書き込み・Production接続は一切発生していません。
- 各主要StepでScreenshotを取得し、`docs/gulliver-phase1-acceptance-review-screenshots/` 配下に保存しました（一覧は7章）。

## 1. Acceptance Review総合結果

G-OPS Phase1で実装した4つの業務フロー（通常発注／Official PO変更／Cancel／Email Override）は、
**いずれも実際に最初から最後まで操作でき、業務として成立するレベルに達しています。**

特筆すべき点として、
- 承認・キャンセル・再発行など「危険操作」には必ず確認ダイアログがあり、かつその説明文が「これはG-OPS内部の状態変更であり、G-SYSへの実際の登録・取消は一切行われません」等、**G-SYSとの境界を明示的に説明している**点は高く評価できます。
- メール送信前にTo/CCを画面上で確認・一時的に上書きでき、かつ「Master登録内容は変更されません」と明記されている点も、実務上の安心材料として機能しています。

一方で、
- **PDFダウンロードボタンに「EXCELダウンロード」という誤ったラベルが表示される**（C-1、実際にダウンロードされるのはPDF）、
- **メール送信を「デモ送信」より先に行うと、送信済みであるにもかかわらず画面上「メール送信: 未完了」と表示される**（C-2、操作順序依存の表示不整合）

の2点は、実際の業務運用に入る前に対応または業務フロー上の手順として確定させておくべき実質的な問題として検出されました。これらは自動テストスイートが常に「デモ送信→メール送信」の順で実行していたため、これまで検出されていませんでした。

また、"PASS"「Batch」「SUPERSEDED」「PENDING」「Legacy」など、開発用語・内部Status名がところどころ日本語UIに直接露出しており、日本語化の仕上げがまだ完全ではありません。

これらを除けば、日本語の自然さ、English切替時のレイアウト維持、Statusの分かりやすさ、Auditからの追跡可能性は、いずれも良好でした。

## 2. A/B/C/D件数

| 分類 | 件数 | 説明 |
|---|---|---|
| A. このままでよい | 12 | 主要画面・主要フローの大部分 |
| B. UI/文言の軽微改善が必要 | 10 | 用語統一・表記揺れ・軽微な表示ラグ等 |
| C. 業務上の改善が必要 | 5 | ラベル誤り・状態不整合・二重の送信/PO番号導線・マスタ一覧の肥大化 |
| D. 顧客確認が必要 | 7 | 正式フォーマット・業務フロー・運用ルールに関する未確定事項 |

## 3. B一覧（UI/文言の軽微改善）

| # | 内容 | 該当画面 |
|---|---|---|
| B-1 | 「最終チェック結果」のChipが英語の「PASS」のまま表示される | 発注詳細（G-SYS正式PO連携セクション） |
| B-2 | 確認ダイアログ本文に内部Status名（SUPERSEDED／PENDING）や「Workflow」が英語のまま埋め込まれている（一覧表示側は「旧版（再発行済み）」「有効」等、正しく日本語化されているのと対照的） | Official PO再発行・キャンセルの確認ダイアログ |
| B-3 | 「G-SYS取込確認」のメッセージに開発用語「Batch」がそのまま表示される | 発注詳細（G-SYS取込確認） |
| B-4 | セクション見出し・ボタンで日英表記が混在（「Official PO 再発行 / Revision History」「OFFICIAL POを再発行」「OFFICIAL POをキャンセル」など、英単語が全角混じり・大文字のまま） | 発注詳細（Official PO再発行/Revision History） |
| B-5 | 「Demo Data（Legacy互換）」「履歴（Legacy PO実績）」など、"Legacy"という内部用語がそのまま業務ユーザー向け画面に表示される | 発注候補一覧、SKU詳細 |
| B-6 | Toアドレスを手動で上書きした後も、「自動選択された宛先です」という補足文言が変化せず残る | メールプレビュー（Email Override時） |
| B-7 | 連続操作時にトースト通知が重なって表示され、一瞬読みにくくなることがある | 複数画面（Excel生成→PDF生成→配置など連続操作時） |
| B-8 | ドラフト保存直後、「保存されていない変更があります」の警告バナーと「保存しました」の成功トーストが同時に見える瞬間がある（描画タイミングによる一時的な表示と推測） | 発注ドラフト |
| B-9 | English切替時、ナビゲーション項目名が長く（「ORDER CANDIDATES」「MASTER MAINTENANCE」等）、画面幅によって折り返しが発生する | 全画面（Englishモード） |
| B-10 | メーカー回答画面で未回答の段階の「差異」欄が「未回答: 6 → 未回答」という不自然な表記になる | メーカー回答（未回答時） |

## 4. C一覧（業務上の改善が必要）

| # | 内容 | 該当画面 | 詳細 |
|---|---|---|---|
| C-1 | **正式PO PDFのダウンロードボタンのラベルが「EXCELダウンロード」と表示される**（実際にはPDFがダウンロードされる） | 発注詳細（正式PO PDFセクション） | `OrderHistoryDetailPage.tsx`のPDFダウンロードボタンが、Excelダウンロードボタンと同じi18nキー（`officialPoIntegration.downloadButton`）を参照しているため。ユーザーが誤ったファイル形式を期待する可能性がある。 |
| C-2 | **メール送信（正式送信）を「デモ送信」より先に行うと、その後デモ送信した時点で画面が「メール送信: 未完了」と表示され、実際には送信済みなのに未送信に見える** | 発注詳細（at-a-glanceパネル） | メール送信対象を決めるRevision番号の算出方法が、デモ送信（初回Order Revisionの確定）の前後で変わるため（`OfficialPoIntegrationService.targetRevisionNo`をメール送信/状態照会の両方が参照しており、Revision確定前後で解決値が変わる）。既存の自動テストは全て「デモ送信→メール送信」の順で実行しているため、これまで検出されていなかった。 |
| C-3 | 同一注文の画面上に「デモ送信」（PO Preview画面）と「メールプレビュー→メーカーへ送信」（発注詳細画面）という、外形の似た2つの送信手段が存在する | PO Preview / 発注詳細 | それぞれに説明文（"デモ送信とは別の機能です"等）はあるが、初めて触れる担当者にはどちらが「メーカーに実際に届く操作」なのか一見して分かりにくい。 |
| C-4 | マスタ管理系画面（メーカー通信方法／メーカー担当者／メールテンプレート）に検索・フィルタ・無効行の非表示機能がなく、運用が進むと一覧が肥大化し使いにくくなる | マスタメンテナンス配下の各画面 | デモ環境での繰り返しテストにより、既に無効行が数十件蓄積して一覧が見づらくなっていることからも実証される。 |
| C-5 | PO番号が二重に存在する（Demo Send発行の内部PO番号「PO-DEMO-...」と、G-SYS連携の「正式PO番号」） | 発注詳細 | 画面上に説明文はあるものの、メーカーとのやり取りや社内文書でどちらの番号を正式に使うべきか、業務運用上の取り決めが必要。 |

## 5. D一覧（顧客確認が必要）と現在のWorking Assumption

| # | 確認事項 | 現在のWorking Assumption |
|---|---|---|
| D-1 | 正式PO PDFの体裁（レイアウト・記載項目）がこのままで良いか | 「G-OPS Standard Format」という自己ラベル付きの暫定フォーマット。Gulliver社指定の正式レイアウトではないことを画面上に明記（「正式なGulliver社指定レイアウトは未確定のため、G-OPS独自の暫定フォーマットです」）。Excelと同一データソースから生成。 |
| D-2 | Official PO再発行の業務要件（承認要否、誰が判断すべきか） | 発注内容修正（修正版作成）後、ADMIN権限者が画面のバナーを見て手動で「再発行」ボタンを押した場合のみ再発行される。**自動再発行は一切実装していない**。 |
| D-3 | Official POキャンセルの業務上の意味づけ（Legacyへの通知要否） | G-OPS内部のWorkflow状態変更のみ。Legacy/G-SYSへの取消通知や実際のキャンセル処理は一切行われない。理由の必須入力と監査証跡のみ実装。 |
| D-4 | Email To/CC Overrideの利用範囲・権限 | 現状ADMIN限定。Master登録内容は変更されず、その回の送信のみ上書き。上書きの有無と実際の宛先は監査ログに記録。 |
| D-5 | Default CC（自動的にCCに入るアドレス）の運用ルール | 管理画面での事前設定（prefill）のみ実装。「誰を必ずCCすべきか」というBusiness Ruleは実装しておらず、送信時に自由に編集・削除可能。 |
| D-6 | 「デモ送信」と「メーカーへの正式メール送信」という2つの送信手段の使い分け（業務フロー上どちらを先に行うべきか） | 「デモ送信」はOrder Statusを進めてメーカー回答受付を可能にする内部トリガー、「メーカーへ送信」はメール本文・添付ファイルを伴う実際の（デモ環境では模擬の）送信操作、という役割分担を想定。ただし実行順序依存の表示不整合（C-2）があるため、正式運用前にどちらを先に行う業務フローとするか確定が必要。 |
| D-7 | PO番号の二重管理（Demo Send発行番号 vs 正式PO番号）をどう運用するか | Demo Send発行番号はPortal内部の管理用連番、正式PO番号はG-SYS連携用にADMINが別途入力する番号、という位置づけ。将来的にどちらをメーカー宛の正式文書に記載すべきか要確認。 |

## 6. シナリオ別 詳細評価

### シナリオ1: 通常発注（Candidate → SKU Detail → Draft → Approval → Official PO → Excel → PDF → G-SYS Integration → Mail Preview → Email → Supplier Response → Difference → Agreement → Arrival/Follow-up）

- 利用者が次に何をすればよいかが分かるか: **概ね良好**。承認待ち時の案内文、G-SYS連携の「次にすべきこと」ヒントなど、随所に次のアクションが明示されている。ただしC-3（送信手段の二重性）は例外。
- 必要な情報が同じ画面または自然な遷移先にあるか: **良好**。発注詳細画面が単一のハブとして機能しており、在庫・販売実績・G-SYS連携状況・メール送信状況が同一画面に集約されている。
- 日本語の自然さ: **概ね自然**。B-1/B-2/B-3/B-5/B-10を除き違和感はない。
- English切替: **崩れない**（B-9のナビゲーション折り返しを除く）。
- Status名の理解しやすさ: **良好**（承認待ち・承認済み・メーカー回答待ち・メーカー確定済み・合意済み等、いずれも平易な日本語）。
- ボタンの意味の明確さ: **C-1を除き良好**。
- 危険操作の確認: **良好**（承認・メーカー回答確定はいずれも内容説明付きの確認ダイアログあり）。
- G-SYSとの境界の理解しやすさ: **良好**（随所に「G-SYS正式PO未連携」「デモ送信とは別の機能」等の明示あり）。
- メール送信先の事前確認: **良好**（Mail Previewで編集可能なTo/CC、Master値との差異が明示される）。
- Auditからの追跡可能性: **良好**（操作履歴セクションに、誰が・いつ・何を変更したかが明記される）。
- 開発用語の露出: **B-1/B-3/B-5に該当あり**。
- 情報過多: 発注候補一覧は列数が多い（13列）が、整理されており致命的ではない。

### シナリオ2: Official PO変更（001発行 → 発注内容変更 → Revision Required検知 → Reissue → 002発行 → 001 SUPERSEDED → 002 ACTIVE → Revision History）

- Revision/Reissueの意味の理解しやすさ: **概ね良好**。「発注内容に修正が入りました。Official POの再発行が必要です。」という平易な日本語のバナーで検知結果が明示される。再発行の確認ダイアログも「旧Revisionは削除されず、Revision Historyに残ります」「G-SYSへの実際の登録・送信は一切行われません」と明記されており安心感がある（B-2の英語Status名混入を除けば非常に分かりやすい）。
- Revision Historyテーブルは「旧版（再発行済み）」「有効」等、適切な日本語ラベルで一覧表示され、Excel/PDF/G-SYS連携状況/送信状況が横並びで確認できる。

### シナリオ3: Cancel（Official PO → Cancel → Reason → CANCELLED → Audit）

- 危険操作の確認: **非常に良好**。「理由の入力が必須です」「これはG-OPS内部のWorkflow状態変更であり、G-SYSへの実際の登録取消は一切行われません」という説明文により、G-SYSとの境界が極めて明確。理由未入力では確定ボタンが無効化される。
- キャンセル後は赤い注記バナーで理由が表示され、Revision History上にも「キャンセル済み」のStatusと共に記録される。Integration Status（Excel生成済み等）はキャンセルの影響を受けず維持されることも画面から確認できた。

### シナリオ4: Email Override（Master Contact → Mail Preview → To/CC変更 → Send → Audit）

- Email送信先が送信前に確認できるか: **非常に良好**。Master由来の宛先が編集可能な入力欄に事前入力され、「自動選択された宛先です。送信直前であれば、この送信だけ宛先を変更できます（Master登録内容は変更されません）」と明記。
- Auditからの追跡可能性: **良好**。送信後、「宛先が送信時に変更されました（自動選択とは異なる宛先へ送信）」という注記が表示され、誰が・いつ送信したかも明示される。Master（Supplier Contact）自体は変更されないことも確認した。
- 軽微な指摘としてB-6（上書き後も補足文言が更新されない）がある。

## 7. Screenshot一覧

全41枚。`docs/gulliver-phase1-acceptance-review-screenshots/` 配下に保存。

| # | ファイル名 | 内容 |
|---|---|---|
| 01 | 01-s1-dashboard.png | ダッシュボード |
| 02 | 02-s1-candidate-list.png | 発注候補一覧 |
| 03 | 03-s1-sku-detail.png | SKU詳細 |
| 04 | 04-s1-draft.png | 発注ドラフト |
| 05 | 05-s1-preview-unnumbered.png | POプレビュー（未採番） |
| 06 | 06-s1-pending-approval-operator-view.png | 発注詳細（承認待ち・OPERATOR視点） |
| 07 | 07-s1-order-detail-before-approve-admin-view.png | 発注詳細（承認前・ADMIN視点） |
| 08 | 08-s1-approve-dialog.png | 承認確認ダイアログ |
| 09 | 09-s1-approved.png | 承認済み直後の発注詳細 |
| 10 | 10-s1-manufacturer-channel-master.png | メーカー通信方法管理 |
| 11 | 11-s1-supplier-contact-master.png | メーカー担当者管理 |
| 12 | 12-s1-mail-template-master.png | メールテンプレート管理 |
| 13 | 13-s1-official-po-prepared.png | G-SYS連携準備完了直後 |
| 14 | 14-s1-official-po-number-confirmed.png | 正式PO番号確定後 |
| 15 | 15-s1-official-po-excel-generated.png | Excel生成後 |
| 16 | 16-s1-official-po-pdf-generated.png | PDF生成後 |
| 17 | 17-s1-gsys-import-folder-placed.png | G-SYS連携用ファイル配置後 |
| 18 | 18-s1-gsys-import-status-not-yet-matched.png | G-SYS取込確認（未取込） |
| 19 | 19-s1-mail-preview.png | メールプレビュー |
| 20 | 20-s1-email-sent.png | メール送信後 |
| 21 | 21-s1-supplier-response-entry.png | メーカー回答入力画面 |
| 22 | 22-s1-supplier-response-confirmed.png | メーカー回答確定確認ダイアログ |
| 23 | 23-s1-difference.png | 差異表示 |
| 24 | 24-s1-agreement.png | 合意済み |
| 25 | 25-s1-arrival-fulfillment-completed.png | 入荷・完納反映後の発注詳細 |
| 26 | 26-s1-order-detail-english.png | 発注詳細（English） |
| 27 | 27-s2-official-po-001-issued.png | Official PO 001発行後 |
| 28 | 28-s2-create-revision-dialog.png | 修正版作成ダイアログ |
| 29 | 29-s2-revision-required-banner.png | Revision Requiredバナー |
| 30 | 30-s2-reissue-dialog.png | 再発行確認ダイアログ |
| 31 | 31-s2-reissued-002-active.png | 再発行直後（002 ACTIVE） |
| 32 | 32-s2-revision-history-table.png | Revision Historyテーブル |
| 33 | 33-s3-official-po-issued.png | Official PO発行後（Cancel用） |
| 34 | 34-s3-cancel-dialog-empty-reason.png | キャンセル確認ダイアログ（理由未入力） |
| 35 | 35-s3-cancel-dialog-reason-filled.png | キャンセル確認ダイアログ（理由入力済み） |
| 36 | 36-s3-cancelled.png | キャンセル済み |
| 37 | 37-s3-cancel-audit-history-row.png | キャンセルのRevision History行 |
| 38 | 38-s4-mail-preview-master-contact.png | メールプレビュー（Master Contact） |
| 39 | 39-s4-to-cc-overridden.png | To上書き入力後 |
| 40 | 40-s4-email-sent-with-override.png | Override送信後 |
| 41 | 41-s4-override-note-audit.png | Override監査注記 |

## 8. 日本語確認結果

主要画面の日本語は自然で、業務用語（承認待ち・合意済み・入荷確認・在庫判定等）も一般的なビジネス日本語として理解しやすい。一方で、B-1/B-2/B-3/B-5/B-10（4章参照）の通り、一部の確認ダイアログ・ステータス表示・セクション見出しに英語の内部用語（PASS、Batch、SUPERSEDED、PENDING、Legacy、Official PO、Revision History等）がそのまま残っており、日本語化が完全ではない箇所がある。いずれも軽微（B分類）で、業務の続行を妨げるものではない。

## 9. English確認結果

`language-switcher-en`で切り替えたOrder Detail画面を確認したところ、レイアウトは崩れず、ラベル・ボタン・ステータス名・バナーメッセージはすべて英語に翻訳されていた（i18nパリティテストの結果と一致）。ナビゲーションバーの項目名がJapaneseより長くなるため、画面幅によっては折り返しが発生する（B-9）が、内容の可読性・操作性には支障がない。

## 10. End-to-End操作結果

4シナリオすべてを実際にブラウザ上で最初から最後まで完走できることを確認した。

- シナリオ1（通常発注フルフロー）: 完走。Candidate→SKU Detail→Draft→承認→正式PO番号確定→Excel→PDF→G-SYS連携用ファイル配置→G-SYS取込確認→メールプレビュー→メール送信→デモ送信→メーカー回答→差異→合意→入荷（完納）まで到達。ただし途中でC-2（メール送信状態表示の不整合）を検出。
- シナリオ2（Official PO変更）: 完走。001発行→発注内容修正→Revision Required検知→再発行→002 ACTIVE/001 SUPERSEDED→Revision History確認まで到達。
- シナリオ3（Cancel）: 完走。発行→理由必須のキャンセル→CANCELLED→Audit（Revision History上に理由付きで記録）まで到達。
- シナリオ4（Email Override）: 完走。Master Contact自動選択→Mail Preview→To上書き→送信→Master不変・Audit記録の確認まで到達。

## 11. Production化前に必ず確定すべき事項

1. **C-1（PDFダウンロードボタンのラベル誤り）の修正** — 実運用でユーザーに誤ったファイル形式を期待させるため、Production化前に必ず修正すべき。
2. **C-2（メール送信状態表示の操作順序依存の不整合）への対応** — 業務フロー上「デモ送信を先に行う」ことを徹底するか、表示ロジック自体を順序非依存に修正するかの方針決定が必要。
3. D-1〜D-7（5章）の顧客確認事項の確定。
4. C-3（送信手段の二重性）・C-5（PO番号の二重管理）について、Gulliver社の実際の業務プロセスに即した運用ルールの明文化（マニュアル・トレーニング資料等）。
5. C-4（マスタ画面の検索・フィルタ機能の不足）— 実運用での長期利用を見据えると、フィルタ・無効行非表示機能の追加を検討すべき。
6. B一覧の日本語化・表記統一（開発用語の除去）を、Production公開前の最終仕上げとして実施することを推奨。
7. 実SMTPアダプタへの切り替え、実G-SYS Import Batchとの結合確認など、既存の実装レポート（`docs/gulliver-20260917-phase1-implementation-report.md`）に記載済みのProductionization残作業。

## 12. Legacy変更 0件

本Acceptance Reviewの実施を通じて、Legacy（`phasep-gulliver/`）のソースコード・DBに対する変更は一切行っていません。すべての操作はDemo環境のPrototype Postgres（使い捨て環境）に対するものであり、Legacy MySQLへの書き込みは発生していません（LegacyはREAD ONLY接続のみ）。

## 13. Production接続 0件

Production DB・Production Import Folder・Production SMTP・Productionデプロイへの接続は一切行っていません。`SafetyGuardEnvironmentPostProcessor`により`local`/`demo`/`test`以外のプロファイルでの起動自体が拒否される既存の安全機構は変更していません。

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
