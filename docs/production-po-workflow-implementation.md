# Production-Oriented PO Workflow — Implementation Result (Phase 9-A〜9-G)

このドキュメントは、Gulliver社との要件定義未確定を前提に、ユーザーから提示された **Working Assumption**（2026-09-04）に基づき実装した、発注Workflowの Production 指向実装（Phase 9-A〜9-G、社内呼称）の結果を記録する。

**重要な区別（本ドキュメント全体を通じて維持する）**:

- **FACT**: `phasep-gulliver/` の Source を直接読んで確認した事実。
- **WORKING ASSUMPTION**: 今回ユーザーから明示的に提示された、最終確定ではない前提。Gulliver社との後続要件定義で変更され得る。
- **CUSTOMER CONFIRMATION REQUIRED**: 今回のWorking Assumptionでも解消されず、依然としてお客様確認待ちの事項。

先行する `docs/official-po-integration-detailed-design.md`（Phase 7-C2-Design）と `docs/official-po-integration-foundation.md`（Phase 7-C2A）が構築した Foundation（Integration Request/State Model、Excel Generator、Preflight）を、実際に動く Production 指向の Workflow へ配線した。**Legacy (`phasep-gulliver/`) は一切変更していない。**

---

## 1. Working Assumption（実装の前提として採用したもの）

ユーザー提示のWorking Assumptionを、実装上の決定に対応付けて整理する。

| # | Working Assumption | 実装上の対応 |
|---|---|---|
| 1 | Official POは購買担当スタッフ（Staff）が作成する | PO番号確定UIをADMIN操作として実装（既存の「G-SYS連携準備」と同じPermission方針を踏襲）。OPERATORへの拡大可否はCUSTOMER CONFIRMATION REQUIRED（既存の自己承認Scope論点と同じ） |
| 2 | PO番号はG-SYS側に11文字目以降のBusiness Ruleがなく、30文字以内であること以外に明確なRuleがない | PO番号Validationは「1〜30文字」の長さチェックのみ。ID Code・区切り文字構造は一切検証しない（`InvalidOfficialPoNumberException`のJavadocに明記） |
| 3 | PO番号はStaffが入力できる、または将来自動採番へ変更可能な構造とする | Staff入力UI（`confirmOfficialPoNumber`）として実装。採番方式を自動生成に切り替える場合も、同じService Method・同じDBカラムを差し替えるだけで対応可能な構造 |
| 4 | メーカーへの発注方法は約90% Email / 約10% EDI、メーカーごとにCommunication Channelを持つ | 新規Master `manufacturer_channel`（Supplier[+Brand]→EMAIL/EDI）を実装。Legacyに対応するMasterは存在しない（FACT、Source全体検索で確認済み・`docs/customer-review-decision-package.md`既存記載どおり） |
| 5 | EDI対象メーカーは自動連携せず、業務状態（入力待ち/入力完了）を管理する | `portal_order.edi_status`（WAITING_INPUT/COMPLETED）+ 完了者/日時。実際のEDIファイル生成・API連携は一切実装していない |
| 6 | Official PO Excel → Import Folder配置 → 既存Batch取込、という連携方式 | `docs/official-po-integration-detailed-design.md` 22章の推奨方式Aをそのまま実装（Excel Import Pipeline）。Import Folder Pathは設定値、Production Pathはコードに一切埋め込んでいない |
| 7 | Production接続禁止（AWS EC2/VPNux/Production MySQL/Production Import Folder） | 本実装中、一度も接続していない（15章参照）。`SafetyGuardEnvironmentPostProcessor`のAllowlistは一切変更していない |

---

## 2. Phase 9-A: Official PO Number / Excel Generation

### 2.1 PO番号確定

- `OfficialPoIntegrationService.confirmOfficialPoNumber`: 既存の Integration Request（`OfficialPoIntegrationService.requestIntegration`で作成済み）に対し、PO番号 + Delivery Week/Date + Ship Via/Term + Payment Term を確定する。
- Validation: 空文字/30文字超のみ拒否。ID Code・区切り文字の構造検証は一切行わない（Working Assumption #2）。
- 編集可能なのは Integration Status が `PENDING`/`GENERATED` の間のみ。`SUBMITTED`以降はLock（`OfficialPoAlreadySubmittedException`）— 既にLegacyへ投入した値を後から勝手に変えられないようにするため。
- 値の変更時のみ `OFFICIAL_PO_NUMBER_CONFIRMED` Auditを記録（毎回の呼び出しでは記録しない）。
- `portal_order.official_po_no`（既存V9カラム）と`official_po_integration_request.official_po_no`の両方を同時に更新 — 前者はMailTemplateRendererの`{{poNo}}`が参照する値（7-C3設計どおり）。

### 2.2 Excel生成

- `OfficialPoExcelGenerationService`（新規）: PortalOrder + Integration Requestの確定値からExcelを生成。
- **Legacy Source再確認**（FACT、本Phaseで再確認・変更なし）: `phasep-gulliver/gulliver/src/main/java/jp/ne/glv/batch/PrOfficialPoImportBatch.java`のROW_IDX_/COL_IDX_定数を直接確認。既存の`OfficialPoExcelGenerator`（7-C2A実装済み）のセル位置マッピングと完全一致することを確認済み（既存Contract Test `OfficialPoExcelGeneratorContractTest`が既にこれを検証していた — 今回そのGeneratorを初めて実データで実行）。
- Brand Nameは`portal_order.brand_name_snapshot`ではなく、`OfficialPoPreflightReadRepository.findBrandName()`でLegacyから都度取得した値を使用（Legacy側の大文字小文字を無視した完全一致要求に対し、Portal側のSnapshotがズレるリスクを避けるため）。
- Series/Model No./Model/Color/Country of Origin/Box寸法/Weightは、PortalのOrder明細データに対応する項目がないため未設定（Excel Contract上は全て任意項目 — 3.3章）。Descriptionのみ`itemNameSnapshot`を流用。
- 生成したExcelは、Portal自身のローカルファイルストア（`OfficialPoExcelStorageService`、Profile別ディレクトリ）に保存。Import Folderへの配置とは別工程（Phase 9-B）。

---

## 3. Phase 9-B: Import Folder Integration

- `OfficialPoImportFolderAdapter`（Port）+ `LocalFilesystemImportFolderAdapter`（local/demo/test、常にローカルディレクトリへ書き込み）+ `ProductionImportFolderAdapter`（`@Profile("production")`スタブ、実運用時のSMB/SFTP等の実装は未確定のため未実装）。
- **接続方式をHardcodeしない**（Working Assumption要求）: Import Folderのルートパスは設定値（`app.official-po.import-folder.base-dir`）。Production Adapterは`SafetyGuardEnvironmentPostProcessor`が"production"Profileでの起動自体を拒否するため、本環境では物理的に到達不可能。
- Idempotency: 既存の`IdempotencyService`（Phase 8-L Foundation）を再利用。Key = `orderId-officialPoNo-revisionNo`。二重配置防止・Retry時の重複処理防止の両方をこの一つの仕組みでカバー。
- Retry: FAILED状態から`markSubmitted`へ直接遷移可能なようEntityのState Machineを拡張（Excelは再生成せず、既存の保存済みバイト列を再配置するだけ）。

---

## 4. Phase 9-C: G-SYS Import Confirmation

- **READ ONLYでの確認可否調査（Working Assumption §4-E該当）**: 可能と判断。既存の`LegacyPoConcurrencyReadRepository`（Phase 7-C6実装済み、`TR_PO`/`TR_PO_DTL`をREAD ONLYで読む）をそのまま再利用— 新規Repositoryは追加していない。
- `OfficialPoIntegrationService.confirmImport`: SUBMITTED状態のRequestに対し、Legacy `TR_PO.STATUS='OFFICIAL'`かつ`TR_PO_DTL`のSKU/Qtyが現在のPortal Order明細と完全一致するかを確認。
  - 一致 → `CONFIRMED`へ遷移、Audit記録。
  - `TR_PO`が存在しない → `NOT_YET_IMPORTED`（エラーではない、状態変更なし）。
  - 存在するが不一致 → `MISMATCH`（差分をSKU単位で返す、状態変更なし）。
- **手動・都度確認方式を採用**（バックグラウンドポーリングは実装していない）: 本環境では実運用のLegacy Production DBに到達できないため、定期実行するJob自体が意味を持たない。かつ、スケジュールされたJobが独自にネットワークリソースへ到達する仕組みを増やすこと自体がProduction Safety上望ましくないと判断した。
- PortalからLegacyへの書き込みは一切行わない（READ ONLYのまま）。

---

## 5. CUSTOMER REVIEW（Phase 9-A〜9-Cで新たに確認が必要になった事項、または既存の未解決事項の再確認）

`docs/official-po-integration-detailed-design.md` 21章の既存CUSTOMER REVIEWリストは、今回のWorking Assumptionで一部が「暫定的に解消（Working Assumptionとして採用）」されたが、以下は依然として未解決:

1. **PO番号の実際の命名規則**（Index 4/8の区切り文字の正体、ID Codeの意味） — Working Assumptionは「G-SYS側に明確なRuleがない」ことを前提として採用したが、これはお客様の実運用が本当にRuleを持たないことを意味するとは限らない。将来的にRuleが判明した場合、Validationを追加できる構造にはしてある（`OfficialPoIntegrationService.confirmOfficialPoNumber`の1箇所を変更するだけで済む）。
2. **Import Folderの実Path・起動Trigger方式** — 引き続きSource外の運用情報。
3. **手作業Excel Uploadを許可し続けるか** — 17章のExcel Coexistence問題は未解決のまま。
4. **EDI対象メーカーの実際の識別** — 今回`manufacturer_channel`Masterを新設したが、初期データは空。どのメーカーが実際にEDIかは、お客様確認後にADMINが手動登録する前提。
5. **PO番号確定の権限（Staff = OPERATOR相当か、ADMIN限定か）** — 今回はADMIN限定として実装（既存の「G-SYS連携準備」と同じPermission方針を踏襲した実装上の判断であり、Working Assumption自体が明示していたわけではない）。

---

## 6. テスト結果

- Backend Full Test: 499/499（`mvn test`、Phase 9-A〜9-Fの新規テストを含む）。
- Frontend: `tsc -b && vite build`成功、oxlint 0 errors（既存4件のwarningは変更なし）。
- E2E: `official-po-integration.spec.ts`（Scenario A〜I、9 tests）、`edi-workflow-foundation.spec.ts`（Scenario G〜J、既存3+新規1）、`manufacturer-channel.spec.ts`（2 tests）、`email-send.spec.ts`（2 tests）— いずれも安定してPASS。
- Legacy変更: 0件（`phasep-gulliver/`への書き込みは一度も実行していない）。

詳細な実装決定・コード構造は各Phaseのコミットメッセージ（`Phase 9-A`〜`Phase 9-G`）を参照。
