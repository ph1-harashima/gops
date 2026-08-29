# Ernest Current Operation Question Sheet — Phase 7-D2

**Status**: Docs Only（Phase 7-D2）。Code変更・DB Migration・Legacy変更は一切なし。

**宛先**: Ernest（Phase1社内、G-SYS保守担当）
**目的**: `docs/customer-review-decision-package.md`（Phase 7-D）で「Sourceコードだけでは分からない」と分類した項目のうち、**Gulliver社（顧客）へ聞く前に、Phase1社内・特にG-SYS保守担当のErnestに確認すれば解決できる可能性が高い項目**を切り出したもの。「Sourceから分からない」＝「Gulliver社へ質問する」ではない、という前提で作成している（Phase 7-D2指示）。

**背景**: Ernestは日常的にG-SYSの保守・運用に携わっており、Source Codeには現れない実運用（誰が何をどう操作しているか、環境構成、Batch起動方法等）を把握している可能性が高い。ここに挙げた13項目は、Sourceの調査だけでは確定できなかったが、**Gulliver社の業務判断そのものではなく、現在のG-SYSの技術的な実態・運用手順に関する事実確認**であるため、まずErnestへの確認を優先する。

**用語について**: Ernestは技術者であるため、以下のような用語はそのまま使用している: `TR_PO`, `Official PO Import`, `Upload/Work/Backup`（Importフォルダ構造）, `SYS_SEND_MAIL`, `Batch`, `Import Folder`。

**Sourceで既に分かっていることは聞かない**: 各質問の "What we already know" に、Source監査（Phase 7-A〜7-C6）で確定済みの事実を明記している。ここに書かれていることを重ねて聞く必要はない。

**回答後の扱い**: Ernestの回答は `docs/customer-review-decision-package.md` の該当項目（Classification: B）を更新し、対応する `docs/customer-review-question-sheet.md`（Gulliver向けDraft）から該当質問を削除・簡略化する。詳細は両ファイル参照。

---

## 優先度の定義

| 記号 | 意味 |
|---|---|
| **P1** | 7-C2B（実Handoff）または7-C4（Real Mail Send）のBlocker。回答なしで次の実装に着手すべきでない。 |
| **P2** | 9/17デモ前に把握しておきたい（デモ内容自体は変わらないが、顧客説明の精度に関わる）。 |
| **P3** | 本番設計時までに把握できればよい。 |

---

## A. PO番号・Official PO Import関連（7項目）

### Q1. 〔P1〕正式PO番号の採番規則・ID Codeの意味

- **What we already know**: `PrOfficialPoImportBatch`がOfficial PO Excel Import時に、PO No.文字列からSupplier Code（4桁）・Brand Code（3桁）・ID Code（2桁）部分を解析し、`MS_COMM`（`MS_SUPPL`/`MS_BRAND`）と照合していることはSourceで確認済み（`SSSS-BBB-II...`形式）。G-SYS自身はこの番号を採番していない（Excel側に既に入力された状態で読み込まれる）。
- **What we need to confirm**: 11桁目以降（連番・日付部と推定される部分）の実際の規則。ID Code（2桁）が何を表す値か、対応するMaster（あれば）はどこにあるか。
- **Why it matters**: 7-C2Bで正式なPO Excelを生成する際、PO No.欄に何を書き込むかを決めるための前提。ここが分からないと、たとえPortalが番号を採番する方針になっても、Import Validationを通る番号を生成できない。

### Q2. 〔P1〕Official PO Excelの作成者・配置Folder・投入Timing

- **What we already know**: Import Pipeline自体の構造（Upload/Work/Backupフォルダ構成）はSourceから確認済み。Import Folderへファイルが配置されればBatchが処理することも構造上明らか。
- **What we need to confirm**: 実際に「誰が」（部署・担当者）「どのタイミングで」（発注確定直後か、日次でまとめてか）Official PO ExcelをUploadフォルダへ配置しているか。
- **Why it matters**: 7-C2BでPortalがこの人手作業をどこまで代替すべきか、投入Timing設計（即時か、バッチ化か）を決めるために必須。

### Q3. 〔P2〕修正版発注時の実際の再Import運用

- **What we already know**: Source上、同一PO No.への再Import（Delete & Recreate）は、その PO にInvoice（入荷実績）が絡んでいなければ技術的に成功することを確認済み（`PrOfficialPoImportBatch.java:883-922`）。Invoiceが1件でも紐づくとGuardでブロックされる。
- **What we need to confirm**: 実際の運用として、発注内容を修正する場合に「同じPO No.のExcelを作り直して再投入」しているか。それとも別の方法（新しいPO No.を割り当てる等）を取っているか。
- **Why it matters**: 7-C2B以降、Portal側のRevision（修正版）をG-SYSへどう反映するかの実装方式を左右する。

### Q4. 〔P1〕現在の手作業Official PO Excel運用の実態

- **What we already know**: Import Batch自体は投入元（PortalかExcelか、人手かツールか）を区別しない（Source確認済み）。
- **What we need to confirm**: 現在、Official PO Excelは誰が・どのくらいの頻度で・どのツール（Excel手打ち、他の社内システムからの出力等）で作成しているか。
- **Why it matters**: Portal導入後も手作業Excelを併用する前提で7-C2B/7-C6を設計すべきか、Portal一本化を前提にできるかの判断材料。

### Q5. 〔P3〕Initial PO（打診段階）の現在の利用実態

- **What we already know**: 現行はInitial（打診）とOfficial（確定）を別のExcel Importとして扱う2段階構造であることはSource確認済み。
- **What we need to confirm**: Initial POは現在も実際に使われているか。使われているとすれば、どのタイミングで、誰が作成しているか。
- **Why it matters**: PortalのDraft/承認前段階をG-SYSへ書き込む必要があるかどうかの判断材料（現時点では不要と推定しているが、確認したい）。

### Q6. 〔P3〕Import Error発生時の現在の運用

- **What we already know**: 該当する業務プロセスはSource上見つからない（Exception発生時にどう検知・対応しているかはSourceからは分からない）。
- **What we need to confirm**: Official PO ExcelのImportがエラーになった場合、現在は誰が・どうやって気づき、どう対応しているか。
- **Why it matters**: 7-C2B以降のAttention/Notification設計の宛先・エスカレーション経路を決めるため。

### Q7. 〔P1〕既存Import Batchの起動方法・実行頻度

- **What we already know**: Import Pipelineの構造（Upload/Work/Backup）は確認済み。起動Trigger自体はSourceからは確認できていない。
- **What we need to confirm**: このBatchは定期実行（Cron等）か、ファイル配置を検知して即座に動くか。実行頻度はどのくらいか（1日1回、数分おき等）。
- **Why it matters**: 7-C2Bで「投入結果をどのくらいの間隔でPollingして確認するか」という設計に直結する。

---

## B. メーカーメール送信関連（4項目）

### Q8. 〔P1〕現在のメーカーへのメール送信方法

- **What we already know**: G-SYSに`SYS_SEND_MAIL`という送信キューテーブルと、それを処理する既存Batchが存在することはSource確認済み（添付・CC/BCC対応済み）。
- **What we need to confirm**: 現在、メーカーへの発注連絡メールは、実際にこの`SYS_SEND_MAIL`経由の仕組みを使って送っているか。それとも別の手段（担当者が個別にOutlook等で送信している等）か。
- **Why it matters**: 7-C4（Real Mail Send）の実装方式（既存キューへINSERTするか、Portal独自の送信手段を作るか）の技術的前提。

### Q9. 〔P2〕メーカーの日本/海外判定に使えるLegacy Master情報

- **What we already know**: 現行`MS_COMM`（`MS_SUPPL`）にSupplierのMaster情報があることは確認済みだが、国内/海外を判別するFlagの有無まではSourceから確認できていない。
- **What we need to confirm**: `MS_COMM`または関連Masterに、メーカーが国内か海外かを判別できる項目（国コード、住所、Flag等）は存在するか。
- **Why it matters**: Mail Templateの言語（ja/en）自動判定ロジックの実装可否を左右する。

### Q10. 〔P2〕既存のメール文面（Template）の有無

- **What we already know**: 該当なし（G-SYSにMail Template管理機能は存在しない）。
- **What we need to confirm**: メーカーへの発注連絡・修正連絡等で、現在使っている決まった文面（Wordテンプレート、Outlookの定型文等）があれば、その内容を共有してほしい。
- **Why it matters**: 7-C3で用意したMail Template（発注/修正版/問い合わせ/取消）の初期内容を、ゼロから作るのではなく既存文面を踏襲できる可能性がある。

### Q11. 〔P2〕メーカー担当者の連絡先情報の現在の管理場所

- **What we already know**: 現行G-SYSにSupplierのメール担当者情報を持つMasterは存在しない（Source確認済み、`MS_COMM`にメールアドレス相当の項目なし）。
- **What we need to confirm**: メーカー担当者のメールアドレス・氏名等は、現在どこかに一覧化されているか（Excel台帳、他の社内システム、CRM等）。あれば、そのデータを7-C3のSupplier Contact Masterへ初期投入できないか。
- **Why it matters**: Supplier Contact Masterの初期データ整備方法（ゼロから手入力か、既存データを移行できるか）。

---

## C. Cancellation関連（1項目）

### Q12. 〔P2〕現在のOfficial PO取消の実際の処理方法

- **What we already know**: SourceだけではG-SYS上でのCancellation処理の実際の手順（PO自体をDeleteする操作なのか、Statusを変える操作なのか、別の取消伝票を起票するのか）が確定できない。
- **What we need to confirm**: 発注を取り消す場合、現在G-SYS上では技術的にどのような操作（画面操作・Batch実行等）を行っているか。
- **Why it matters**: Portal側のCancellation機能（未実装）を設計する際、G-SYS側の実際の処理と整合させる必要がある。

---

## D. Infrastructure関連（1項目）

### Q13. 〔P1〕G-SYS本体・Import FolderのHosting環境とNetwork接続情報

- **What we already know**: Current Legacy Specification上、EC2/S3等のクラウド利用は確認できていない（推測で記載しない）。
- **What we need to confirm**: 現在のG-SYSサーバー、および正式PO Excelを配置するImport Folderは、どこにHostingされているか（オンプレミス／クラウド／その他）。Portal（別System）からアクセスする場合、どのようなNetwork経路・認証方式が必要になるか（VPN、専用線、Credential管理方法等）。
- **Why it matters**: 7-C2Bで実際にFileをImport Folderへ投入する接続方式を設計するための必須情報。9/17のBusiness Reviewには不要（Infrastructure Reviewとして別枠で扱ってよい）。

---

## 優先度別サマリ

| 優先度 | 件数 | 項目 |
|---|---|---|
| P1 | 6 | Q1, Q2, Q4, Q7, Q8, Q13 |
| P2 | 5 | Q3, Q9, Q10, Q11, Q12 |
| P3 | 2 | Q5, Q6 |
| **合計** | **13** | |

## Theme別サマリ

| Theme | 件数 | 質問番号 |
|---|---|---|
| PO番号・Official PO Import運用 | 7 | Q1-Q7 |
| メーカーメール送信 | 4 | Q8-Q11 |
| Cancellation | 1 | Q12 |
| Infrastructure | 1 | Q13 |
