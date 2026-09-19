# Gulliver社 確定Business Rules（BR-01〜BR-10）

**本ドキュメントの性質**: `docs/gulliver-20260917-phase1-gap-analysis.md`（READ ONLY監査）および
`docs/gulliver-20260917-phase1-implementation-report.md`（実装済みWorking Assumption群）で
「要件確定待ち（D）」「顧客確認が必要」と記録されていた項目について、2026-09-17打合せおよびその後の
業務確認により正式に確定したBusiness Ruleを記録する。**これらはWorking Assumptionではなく、
G-OPS Phase1の確定仕様として扱う。**

出所: ユーザー（顧客側担当）から直接提示された確定仕様（本セッションのタスク指示）。
`docs/20260917_打合せ議事メモ.pdf`（Zoom自動要約）は背景確認のため参照したが、本ドキュメントの
確定内容そのものはユーザー提示テキストを一次情報とする。

---

## BR-01: Official PO Excel / PDF

- Official POは **Excel → PDF** の順で作成する。ExcelがOfficial POの元となるDocumentであり、
  PDFはExcelと同一内容を改変不能な発注書としてPDF化したものである。
- 業務フロー: 発注内容確定 → Excel生成 → 同内容をPDF化 → 社内承認 → メーカーへ送信（PDF + Excel両方）。
- PDF: 正式な発注内容確認用。Excel: メーカー側が数量・型番等について回答・修正を行う際の利便性のため併送。
- PDFとExcelの PO No/Supplier/Brand/SKU/Quantity/Price/Delivery等の発注情報は一致すること。

## BR-02: Reissue

- 発行済みOfficial POの内容変更は、Supplier Response上のAgreementだけで完結してはならない。
- 正式フロー: 変更 → 再度社内承認 → Reissue（PO 002発行） → Excel/PDF再生成 → メーカーへ再送。
- 旧Revisionは削除せず、SUPERSEDEDとして保持する。新RevisionのみがACTIVEになる。
- 新Revisionを正式発行できる条件として、再承認を必須とする。既存Approval Workflowを最大限再利用する。

## BR-03: Cancel

- 発行・送信済みOfficial POのCancelは単純な状態変更ではない。
- 正式フロー: Cancel Request（理由必須） → 社内承認 → メーカーへ取消連絡 → CANCELLED → Audit/History保持。
- Cancel前のPOやRevisionは削除しない。
- 既存Approval機構を可能な限り再利用する。Cancel通知はLocal/DemoではSimulationで構わない
  （Production SMTPへ接続しない）。

## BR-04: Email Override

- 発注メールを送信できる権限を持つ担当者は、送信時にTo/CCを変更できる（ADMIN限定ではない）。
- 送信直前に最終送信先確認を必須とする。
- Master Contactと異なるDomainのメールアドレスがTo/CCに含まれる場合、Warningを表示する
  （送信禁止にはしない。Warning→ユーザー確認→送信可能）。
- Auditには Master To/CC・Actual To/CC・Override有無・User・Timestampを保持する（既存どおり）。

## BR-05: Default CC

- Default CCは管理画面で設定し、すべての発注メール作成時に初期値として設定する。
- Default CCは必須CCではない（Mandatory CCではなくInitial Valueである）。送信権限者は追加・変更・削除できる。

## BR-06: Demo Send

- Demo SendはBusiness Functionではなく、初期Prototypeで作成したDemo/Test専用機能。Productionで使用しない。
- Production UIではDemo Sendを表示しない。本番業務フローは G-OPS → Manufacturer Send → 実Email送信 →
  Send Success → 送信済み → Audit のみ。
- Local/Demo/Test環境では既存Testのため内部的に残して構わない。

## BR-07: Portal管理番号 / Official PO番号

- Portal管理番号: G-OPS内部でOrderを一意に識別するSystem Management ID。G-OPSが自動採番。利用者は編集しない。
- Official PO番号: メーカーとの正式発注・Official PO Excel/PDF・G-SYS連携で使用する正式PO番号。
  担当者が手入力するのではなく、G-OPSが自動採番する。

## BR-08: Official PO番号 採番Rule

- フォーマット: `{Supplier略称3文字}{Brand略称3文字}{3桁通番}`（例: `ABCXYZ001`）。
- 通番は Supplier × Brand の組み合わせ単位（ABC×XYZ、ABC×DEF、KLM×XYZ はそれぞれ独立した通番系列）。
- Supplier略称・Brand略称はGulliver社が決定しG-SYS Masterに登録する。G-OPSはREAD ONLYで取得し、
  略称自体を勝手に生成・変更してはならない。
- 同一Supplier×Brandへの複数User同時作成でも、同じSequence Numberが発行されないよう
  Concurrency Controlを実装する（無排他のMAX+1は禁止）。Legacy DB WRITEは禁止。
- 正式PO番号はExcel/PDF/File Name/Manufacturer Send/G-SYS Integration/Revision History/Auditで
  一貫して使用する。既存の「Official PO番号手入力/確定」UIは原則自動採番へ変更する。

## BR-09: Domestic / Overseas Recommended Qty

- Recommended Qtyの計算方式はDomestic/Overseasで異なる。OVERSEAS→現行Legacy計算方式。
  DOMESTIC→新しい国内向け計算方式（**具体式は今回未確定**）。
- 今回やること: Domestic/Overseas判定構造の確認、Strategy切替可能なArchitectureの確認、
  Overseasは現行Legacy Logic維持、Domesticは「計算Rule未確定」と明確に扱う。
- Domesticに対して海外式を暗黙適用して正しいRecommended Qtyであるように見せてはならない。

## BR-10: Company

- Company = Supplier。Supplierとは別のCompany概念は不要。Company Masterを新設しない。
  既存のSupplierを使用する。
- 概念モデル: Supplier(=Company) ├─ Supplier略称3文字 ├─ Contact ├─ Domestic/Overseas
  └─ Brand ├─ Brand略称3文字 └─ Official PO Sequence

---

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01729GZyNPsdrAxL5Exd2WSA
