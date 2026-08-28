# Supplier Contact / Mail Template Foundation — Phase 7-C3 実装結果

`docs/target-production-procurement-workflow.md`（Phase 7-B設計 9章/10章）のうち、Phase 7-C3で実装したFoundation部分（Supplier Contact Master / Mail Template Master / Mail Preview / Recipient Resolution）の実装結果の詳細記録です。

Scope: 将来の「Official PO Integration CONFIRMED → Supplier Email送信 → Supplier Response受信」に必要な基盤をPortal側に実装。**実メール送信・SYS_SEND_MAIL INSERT・SMTP接続・Legacy DB WRITEはいずれも行っていない。**

## 1. Supplier Contact Model

`supplier_contact`（V10 migration, Prototype PostgreSQLのみ）:

```
id, supplier_code, brand_code (nullable), contact_name, email,
contact_type (TO/CC), language (ja/en), region (nullable),
procurement_type (nullable), is_primary, is_active,
created_by, created_at, updated_by, updated_at
```

Legacy Supplier/Brand Master (`ms_comm`) へのFKは貼らず（Legacy DB跨ぎのため）、Backend Validationで存在確認（14章）。Uniqueness: `(supplier_code, COALESCE(brand_code,''), lower(email))` が**有効な行の間でのみ**一意（部分UNIQUE index）。同一identityで一度Inactiveにした担当者を再登録することは許容。

## 2. Contact Resolution

`SupplierContactResolutionService`: 優先順位 (1) supplier+brand完全一致 (2) supplier単独（brand_code IS NULL） (3) fallbackなし。**勝った優先順位内の複数有効行はすべて返す**（曖昧扱いにしない）— 9章「Supplier側の複数宛先も将来拡張可能に」の明示的要求どおり。「曖昧な場合はWarning/Blocked」という設計指示は、複数化が正しい挙動であるSupplier Contactではなく、**唯一無二であるべきMail Templateの解決**（4章）に適用した（設計判断として明記）。

## 3. Admin CC設計

Target Rule: From=ログインユーザー / To=Supplier Contact Master / **CC=ADMIN**。「ADMIN複数人の場合、誰をCCするか」はCUSTOMER REVIEW（3章/18章）のため、このPhaseでは**全Active ADMIN**（`portal_user` role=ADMIN AND enabled=true の全メールアドレス）という最も単純・安全なDefaultを暫定実装（`AdminCcResolutionService`）。将来CUSTOMER REVIEWが確定した際、このメソッドの中身だけを差し替えればよい設計。Previewでは実際に解決されたCC一覧を表示する。

## 4. Mail Template Model

`mail_template`（V10 migration）:

```
id, template_name, template_type, supplier_code (nullable),
brand_code (nullable), language, subject_template, body_template,
attachment_type (nullable), is_active,
created_by, created_at, updated_by, updated_at
```

`template_type`は4候補（PURCHASE_ORDER/PURCHASE_ORDER_REVISION/FOLLOW_UP/CANCELLATION）を許可するがResolution Logicがあるのは`PURCHASE_ORDER`のみ（6章の指示どおり最小実装）。

## 5. Template Resolution

`MailTemplateResolutionService`: specific-first (1) supplier+brand+language (2) supplier+language (3) language default。**勝った優先順位内に複数の有効Templateが存在する場合はAMBIGUOUS**（自動選択しない）。DB側でも`(template_type, COALESCE(supplier_code,''), COALESCE(brand_code,''), language)`の部分UNIQUE indexにより、通常の運用では同一tierに2件の有効Templateが物理的に作成できない設計にした（AMBIGUOUS分岐はこの制約をすり抜けた場合のためのdefense-in-depthとして、Mockitoスタブによる純粋Unit Testで別途検証 - 実DBでは到達しないことを`MailTemplateServiceIntegrationTest`で確認済み）。

## 6. Template Variables

実装済み変数: `{{supplierName}}` `{{contactName}}` `{{poNo}}` `{{orderDate}}` `{{requestedDelivery}}` `{{senderName}}` `{{senderEmail}}` `{{revisionNo}}`。

`{{poNo}}`は**`portal_order.officialPoNo`のみ**を参照し、`prototypePoNo`を代用しない（8章の明示指示）。`officialPoNo`がNULLの場合（本Phase常時）、Subject/Bodyは空文字での誤魔化しではなく**レンダリングそのものをBLOCKED扱い**にして未生成とする。

## 7. Mail Preview

`MailPreviewService`（`POST /api/orders/{id}/mail-preview` — GETではなくPOST。PoPreviewControllerと同じ理由：呼び出しの都度Contact/Template/Admin CCをLive再解決するため、単純なfetchではない）。入力: Portal Order・Official PO Integration Request（revisionNo取得用）・Supplier Contact・Mail Template・Current User。出力: From/To/CC/Subject/Body/Attachment Summary/Issues（Blocker/Warning）。**Send APIは存在しない**（このPhaseの範囲外）。

## 8. Preview Gate

Target Production Gateは「APPROVED + Integration CONFIRMED + officialPoNo確定 + Contact解決 + Template解決」（10章）。CONFIRMEDへ到達する手段が7-C2A/7-C3のどちらにも存在しないため、本番Send CTAは実装していない。Preview自体はGateを満たさなくても（未確定項目を可視化する目的で）呼び出し可能。

## 9. Admin UI

`/admin/supplier-contacts` / `/admin/mail-templates`（ADMIN限定、Backend `@PreAuthorize("hasRole('ADMIN')")`で強制）。一覧・新規作成・編集（Active/Inactive切替を含む）を実装。物理削除UIは実装していない（12章の推奨どおりInactiveを利用）。

## 10. Permission

| 操作 | OPERATOR | ADMIN |
|---|---|---|
| Master一覧閲覧 | × (403) | ○ |
| Master作成/編集 | × (403) | ○ |
| Mail Preview | ○ | ○ |

Master CRUDは読み書きともADMIN限定とした（Phase 7-B 4章のPermission Matrixが「Master管理（Supplier Contact / Mail Template）」をADMIN専用・OPERATOR行なしとしていたことに準拠）。Mail Previewは破壊的操作ではないため全認証ユーザーに開放（13章の許容範囲内）。いずれもBackendで強制（`SupplierContactMailTemplateApiTest`でOPERATOR 403を実測）。

## 11. Legacy Master Validation

`SupplierContactService`/`MailTemplateService`とも、既存の`OfficialPoPreflightReadRepository`（Phase 7-C2Aで新設済み）を再利用してsupplierCode/brandCodeのLegacy存在確認を行う（新しいLegacy Read Repositoryは追加していない）。存在しないCodeは登録できない（400 `SUPPLIER_CODE_NOT_FOUND`/`BRAND_CODE_NOT_FOUND`）。

## 12. Attachment Foundation

Mail Preview Responseの`attachment`フィールドに`{type, fileName, generated: false}`のメタデータのみを含める。実File添付・実メール送信・S3 Uploadはいずれも実装していない（16章）。

## 13. Audit

新規AuditEvent種別1つ：`MAIL_PREVIEW_GENERATED`（Order紐付け、Preview実行毎に記録、noteに"OK"/"BLOCKED"）。`SUPPLIER_CONTACT_CREATED/UPDATED`・`MAIL_TEMPLATE_CREATED/UPDATED`は**意図的に実装していない** — 既存`audit_event`テーブルは`portal_order_id NOT NULL`のOrder専用構造であり、Master変更にはOrderという文脈が存在しないため、無理に既存構造へ詰め込まず、Master自身の`created_by`/`updated_by`/`created_at`/`updated_at`列をその代替とした（17章の明示的許可事項）。

## 14. DB Migration（V10）

`supplier_contact`・`mail_template`の2テーブル新設＋部分UNIQUE index2件、`audit_event.event_type`のCHECK制約に`MAIL_PREVIEW_GENERATED`追加。**両Masterテーブルとも`DemoResetRunner`のTRUNCATE対象に加えていない**（`portal_user`と同じ「設定的Master データ」区分として、Demo Reset間で保持する設計判断 — Business Workflowデータではないため）。

## 15. Backend Full Test

`mvn clean test`で**247/247 tests, 0 failures, 0 errors, 0 skipped**（Phase 7-C2A時点の202から+45: Contact CRUD/Resolution/Template CRUD/Resolution(Ambiguous含む)/Mail Preview/Permission Testを追加）。

## 16. Safety/READ ONLY

Legacy Master Validationは既存のREAD ONLY Repositoryを再利用（新規Legacy接続なし）。SafetyGuard関連テストは既存どおり全件成功。SMTP・SYS_SEND_MAIL INSERT・External Mail API・Legacy DB WRITE・Import Folder Write・Production接続、いずれも本Phaseのコードパスに一切存在しない。

## 17. Frontend Build/Lint

`tsc -b && vite build`成功。oxlintエラー0（既存warning 4件のみ、本Phase起因ではない）。

## 18. Full E2E

36 tests（既存6ファイル＋新設`supplier-contact-mail-template.spec.ts`）、2回連続実行で安定（各回33-34 passed、pre-existing dynamic skip 2-3件は本Phase起因ではない）。

## 19. Browser Scenario A-F

| Scenario | 内容 | 結果 |
|---|---|---|
| A | ADMIN: Contact作成→Template作成→Order Detail→Mail Preview→To/CC解決確認（Subject/BodyはofficialPoNo未確定のためBLOCKED表示のまま、フェイクの内容を表示しないことを確認） | ✅ |
| B | OPERATOR: Master変更/閲覧APIを直接叩く→403、Nav項目も非表示 | ✅ |
| C | Contact未設定のOrder→Preview BLOCKED（SUPPLIER_CONTACT_NOT_FOUND） | ✅ |
| D | Contact設定済みだがTemplate未設定→Preview BLOCKED（MAIL_TEMPLATE_NOT_FOUND） | ✅ |
| E | officialPoNo未設定→PreviewでOFFICIAL_PO_NO_NOT_ASSIGNEDを明示 | ✅ |
| F | 一連の操作を通じて実メールが一切送信されていないことを確認（Send API自体が存在しないことをもって確認） | ✅ |

**実装上の注意（E2E）**: Supplier Contact/Mail Templateは実Demo Dataと異なりDemo Resetで消えない永続Masterデータのため、Scenario CとDはA（Contact/Templateを実際に作成する）より**先に実行**するようFile内の宣言順を並べ替えた（Scenario名のラベルはA〜Fのまま、宣言順のみC→D→A→B→E→Fに変更）。また、AとDが作成したMaster行はテスト末尾でInactive化し、Suiteを繰り返し実行しても状態が汚染されないようにしている。

## 20. CUSTOMER REVIEW（このPhaseで確定していない事項 — 7-B 9章/18章から継続）

- ADMIN複数時のCC対象の正式ルール（全Active ADMIN以外の候補：Order担当ADMIN/Primary ADMIN/Supplier・Region担当ADMIN）。
- Supplier側複数To宛先の正式運用。
- Supplier担当者のBrand単位/全社単位の正式な割当方針。
- Mail Template本文の編集可否（現状ADMINなら誰でも編集可能）。
- 送信前の最終確認要否。
- Fromをログインユーザー実アドレスにするか、Reply-To方式にするか。
- 日本/海外Supplierの正式判定方法。
- PURCHASE_ORDER_REVISION/FOLLOW_UP/CANCELLATIONの各Template詳細仕様。

## 21. Legacy変更ゼロ確認

`phasep-gulliver`に対する変更は本Phase中ゼロ（`git diff -w --numstat`で実質差分0件、HEADも不変）。Write/Edit系ツールは一度も呼んでいない。
