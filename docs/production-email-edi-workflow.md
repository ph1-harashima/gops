# Production Email / EDI Workflow — Implementation Result (Phase 9-D〜9-G)

`docs/production-po-workflow-implementation.md`の続き。Phase 9-D（Email/EDI分岐）、9-E（実Email送信）、9-F（Audit/Retry/Idempotency連携確認）、9-G（Order Detail UI整理）の実装結果を記録する。

**FACT / WORKING ASSUMPTION / CUSTOMER CONFIRMATION REQUIREDの区別は`docs/production-po-workflow-implementation.md`と同じ規約に従う。**

---

## 1. Phase 9-D: Email / EDI 分岐

### 1.1 Manufacturer Channel Master（新規）

- **FACT（既存docs再確認）**: Legacyには、メーカーの発注方法（Email/EDI）を表すMaster・列は一切存在しない（`docs/customer-review-decision-package.md`既存記載、`\bEDI\b`でSource全体検索し0件・Phase 7-H監査で確認済み）。
- **WORKING ASSUMPTION（今回採用）**: 約90% Email / 約10% EDIで、メーカーごとにCommunication Channelを持つ。
- 実装: 新規Portal専用Master `manufacturer_channel`（`supplier_code`[+`brand_code`] → `EMAIL`/`EDI`）。既存の`supplier_contact`（Phase 7-C3）と同じ形状・同じUniqueness方針（Active行内で一意、非Active化してからの再登録は許容）。
- 解決順位（`ManufacturerChannelResolutionService`）: Brand指定Row優先 → Supplier単独Row → 該当なしはnull（**自動推定しない** — 7-C6 13章の既存方針を踏襲）。
- 初期データは空。**CUSTOMER CONFIRMATION REQUIRED**: 実際にどのメーカーがEmail/EDIかは、お客様確認後にADMINが手動登録する前提。

### 1.2 EDI状態管理

- `portal_order.edi_status`（`WAITING_INPUT`/`COMPLETED`）+ `edi_completed_by`/`edi_completed_at`。
- `WAITING_INPUT`は、既存の`recordEdiSend`（Phase 7-H実装済み、"EDI発注済みとして記録"）が呼ばれた時点で自動的にセットされる。
- `COMPLETED`は、新規Business Action `completeEdiInput`（ADMIN/OPERATOR、既存`edi-send`と同じPermission）による明示操作でのみセットされる。
- **実際のEDIファイル生成・API連携は一切実装していない**（Working Assumption §6の明示指示どおり）。Legacy側のEDI業務システムでの入力を、Portal側で「完了しました」と記録するだけの機構。

### 1.3 UI分岐

- Order Detail画面が、解決されたChannelに応じて表示を出し分ける: `EMAIL` → 実Email送信Section（9-E）、`EDI` → EDI状態トラッカー、未解決 → 中立的な案内（Master未登録の旨）。
- **既存のDemo Send/EDI Send（記録のみ）ボタンは変更していない**（Phase 7-H実装済み、既にE2E Testが存在する既存機能 — 新Featureは既存機能を置き換えず並存する）。

---

## 2. Phase 9-E: 実Email送信

### 2.1 設計方針

- **Mail Preview（Phase 7-C3実装済み）の解決ロジックをそのまま再利用**し、送信内容の「唯一の情報源」を一つに保つ。送信専用の別Compose UIは実装していない — Previewで表示された内容がそのまま送信される。
- Port+Adapter方式（`EmailSenderPort`）: `LoggingEmailSenderAdapter`（local/demo/test、一切ソケットを開かない。この構造自体が§12の要求する「Email Send Mock」のテスト手段を兼ねる） + `SmtpEmailSenderAdapter`（`@Profile("production")`、標準の`spring.mail.*`設定を使った実`JavaMailSender`実装。`SafetyGuardEnvironmentPostProcessor`によりこのProfileでの起動自体が拒否されるため、本環境では物理的に到達不可能）。
- `spring-boot-starter-mail`を追加したが、`MailSenderAutoConfiguration`は明示的に除外（`application.yml`）— `JavaMailSender`が生成されるのは`SmtpEmailSenderAdapter`の中だけ、という一本化を維持。

### 2.2 送信対象・Gate

- 解決Channelが`EMAIL`の場合のみ送信可能（`EmailChannelNotApplicableException`）。
- Mail PreviewがBLOCKED（担当者/テンプレート/PO番号いずれか未確定）の場合は送信不可（`EmailPreviewBlockedException`）。
- 正式PO Excelが未生成の場合は送信不可（`EmailAttachmentNotReadyException`）。**Import Folderへの配置・G-SYS取込確認までは要求しない** — Excel添付という目的に対しては生成済みであれば十分と判断（Import Folder投入とEmail送信は独立したトラック）。

### 2.3 Attachment

- 現時点では正式PO Excelのみ（`attachment_type = 'OFFICIAL_PO_EXCEL'`）。
- **固定しすぎない設計**（Working Assumption §5の明示指示）: `attachment_type`列はCHECK制約のない自由な文字列。将来PDF等が必要になった場合もSchema変更不要。

### 2.4 Idempotency / Audit

- 既存`IdempotencyService`を再利用（Key = `orderId-revisionNo`）。二重送信防止・Retry時の重複処理防止を1つの仕組みでカバー（Import Folder投入と同じパターン）。
- 送信成功/失敗いずれもAudit記録（`EMAIL_SENT`/`EMAIL_SEND_FAILED`）。

---

## 3. Phase 9-F: Audit / Retry / Idempotency 連携確認

新規Infrastructureはほぼ追加していない（Idempotencyは9-A〜9-Eの各Phase実装時に既存`IdempotencyService`を都度再利用済みのため）。本Phaseは、実装済みの各Retry経路が本当に機能することを、Service層で直接検証する回帰確認に充てた:

- Import Folder投入のFAILED→Retry経路（`LocalFilesystemImportFolderAdapter`自体には人為的な失敗手段がないため、Mockitoで`OfficialPoImportFolderAdapter`をモック化した単体テストで実施）。
- Email送信のFAILED→Retry経路（同様にMockitoで`EmailSenderPort`をモック化）。
- 新規Legacy Read（Phase 9-CのG-SYS Import Confirmation）が既存の`LegacyPoConcurrencyReadRepository`を再利用しているため、既存のREAD ONLY回帰テスト（`LegacyReadOnlyIntegrationTest.updateAgainstTrPoDtlIsRejected`）が既にカバーしていることを確認 — 新規テーブルへの新規テストは不要と判断。
- Correlation ID（Phase 8-L実装済み、`CorrelationIdFilter`）は全リクエストに自動付与されるServlet Filterのため、新規Endpointに対する追加実装は不要（実際のE2E実行ログで確認済み）。

---

## 4. Phase 9-G: Order Detail UI整理

- Order Detail画面の先頭に、正式PO番号・Excel生成状態・G-SYS連携状態・Communication Channel・Email/EDI状態を1行にまとめた「at a glance」パネルを追加。
- 併せて、状態の組み合わせから一意に計算される「次にすべきこと」ヒントを1行表示（`computeNextActionHintKey`、純粋関数、新規Fetchは一切追加していない）。
- Email送信は「Import Folder投入完了」を要求しないため（2.2節）、ヒントの優先順位はEmail送信 > Import Folder投入とした（メーカーへの通知の方が時間的制約が強いという実務判断 — Working Assumptionが明示したものではなく、実装上の判断）。

---

## 5. CUSTOMER CONFIRMATION REQUIRED（本Phase群で新たに確認が必要になった事項）

1. **メーカーごとの実際のChannel（Email/EDI）の実データ** — `manufacturer_channel`Masterは空のまま。
2. **EDI入力完了を誰がいつ確認するか** — 現状は「Portal上でADMIN/OPERATORが手動で完了と記録する」以上の運用は定義していない。
3. **Email送信の実SMTP設定（Production）** — `SmtpEmailSenderAdapter`は実装したが、実際のSMTPサーバー・認証情報・送信ドメインの正当性（SPF/DKIM等）は完全に未確認・未設定。
4. **Attachment形式をPDF等に拡張する必要があるか** — 現状Excel添付のみで運用開始できるかは未確認。

---

## 6. テスト結果

`docs/production-po-workflow-implementation.md` 6章と合算した最終結果:

- Backend Full Test: 499/499。
- Frontend: `tsc -b && vite build`成功、oxlint 0 errors。
- E2E: 全17新規/拡張Scenario（official-po-integration.spec.ts A〜I、edi-workflow-foundation.spec.ts G〜J、manufacturer-channel.spec.ts、email-send.spec.ts）が安定してPASS。
- Legacy変更: 0件。
