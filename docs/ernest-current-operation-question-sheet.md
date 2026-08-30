# Ernest Current Operation Question Sheet — Phase 7-D2（Phase 7-J／8-A追加あり）

**Status**: Docs Only（Phase 7-D2、Phase 7-JでEDI関連4項目、Phase 8-Aで価格変更関連6項目を追加）。Code変更・DB Migration・Legacy変更は一切なし。

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

- **What we already know**: Import Pipeline自体の構造（Upload/Work/Backupフォルダ構成）はSourceから確認済み。Import Folderへファイルが配置されればBatchが処理することも構造上明らか。**（Phase 7-J追加）G-SYS自身にOfficial PO Excel/PDFを生成する機能は存在しない**（`jp.ne.glv.utilities.export`配下にArrivalList/PriceList/StockList等の帳票Export Classはあるが、PO相当のExport Classはない）ため、G-SYSは常にImport（受け取る）側であることもSource確認済み。`testfile/OfficialPO.xlsx`はImport Batchの単体Test用Fixtureであり、正式な生成Templateではないことも確認済み。
- **What we need to confirm**:
  - 実際に「誰が」（部署・担当者）「どのタイミングで」（発注確定直後か、日次でまとめてか）Official PO ExcelをUploadフォルダへ配置しているか。
  - **（Phase 7-J追加）実際にメーカーへ送付しているPO文書（Excel／PDF／その他）は何か。それは、G-SYSのImport Folderへ配置しているOfficial PO Excelと同一のファイルか、それとも別に作成しているものか。**
- **Why it matters**: 7-C2BでPortalがこの人手作業をどこまで代替すべきか、投入Timing設計（即時か、バッチ化か）を決めるために必須。メーカー送付物とG-SYS投入物が別物であれば、Portalが生成すべき成果物も2種類になりうる。

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

## E. EDI発注関連（4項目、Phase 7-J追加）

Phase 7-Jの手動確認で、メーカーとの発注方法が約90%Email・約10%メーカー側EDI Systemという業務前提が判明した。EDI関連の概念（Channel区分・Master等）はSource上一切存在しない（`\bEDI\b`でSource全体検索し0件）ため、以下はすべて実運用のヒアリングであり、Sourceからの裏付けは持てない。

### Q14. 〔P2〕EDI Supplierへの実際の発注物・保存有無

- **What we already know**: 該当なし（G-SYSにEDIという概念自体が存在しない）。
- **What we need to confirm**: メーカー側のEDI Systemで発注している場合、G-SYS側でもPO File（Excel/PDF等）を作成・保存しているか。それとも、EDI発注の場合はTR_PO登録のみで、目に見えるPO文書自体を作らないか。
- **Why it matters**: EDI発注時にPortal側でPO文書生成が必要かどうかの判断材料。

### Q15. 〔P2〕EDI対象Supplierの識別方法

- **What we already know**: 該当なし。Legacy Supplier Master（`MS_COMM CATE_ID='MS_SUPPL'`）をPortalが読む範囲ではコード・名称のみで、発注方法を区別する項目は確認できない。
- **What we need to confirm**: どのSupplierがEDI対象か、現在どこで・どうやって判別しているか（Master項目、担当者の記憶、別Excel台帳等）。
- **Why it matters**: 将来Portalが正式にChannel区分を持つ場合の初期データ整備方法。

### Q16. 〔P2〕EDIの実際の発注方式

- **What we already know**: 該当なし。
- **What we need to confirm**: EDIとは具体的にどの方式か（担当者による手操作、メーカー側Web Portalへの手入力、File授受（SFTP等）、API連携等）。Supplierによって方式が異なるか。
- **Why it matters**: 将来の実EDI連携設計（本Phaseでは対象外）の前提情報。

### Q17. 〔P2〕EDI発注後のSupplier Response管理方法

- **What we already know**: 該当なし。
- **What we need to confirm**: EDIで発注した場合、メーカーからの回答（数量・納期の確定連絡等）は現在どう受け取り、どう記録しているか（EDI Systemの画面で完結するか、別途メールや電話で確認しているか）。
- **Why it matters**: PortalのSupplier Response画面がEDI発注後もそのまま使える設計になっているか（Phase 7-H Foundationで「Channel非依存」とした前提）を実運用と付き合わせるため。

---

## F. 価格変更関連（6項目、Phase 8-A追加）

Phase 7-Jの`docs/legacy-price-change-reverse-engineering.md`（Source Reverse Engineering）11章で「Source調査で確認できなかった事項」として挙げたQ-P1〜Q-P6のうち、Ernestが技術保守担当として確認できる可能性が高いものをここに正式なQuestionとして展開する（Q-P7〔Tempostar連携詳細〕は価格変更Target Design自体には直結しないため、次々Phase以降に送る）。9/17デモは発注(Ordering)機能のみが対象であり価格変更は含まれないため、全項目P3（本番設計まででよい）とする。

### Q18. 〔P3〕Price List Import Folderの配置・トリガー方法

- **What we already know**: Import Pipeline自体の構造（Upload/Work/Backupフォルダ構成、`AbstImportBatch`基盤）はOfficial PO Importと共通であることをSourceで確認済み（`docs/legacy-price-change-reverse-engineering.md` 2章）。起動方式（cron/手動/外部Watcher等）を示すコードはSource上に見つかっていない。
- **What we need to confirm**: Price List Import（`PRC_LIST`コード）用のImport Folderへ、実際に「誰が」「どのタイミングで」Excelを配置しているか。Official PO Importと同じ運用か、別のフローか。
- **Why it matters**: 将来Portalが価格変更用Artifactを生成する場合の投入Timing設計の前提（`docs/target-price-change-workflow.md` 12章）。

### Q19. 〔P3〕Price List Export/Import列フォーマットの一致

- **What we already know**: Export（`PriceList.java`）とImport（`MsPriceListImportBatch`）は別クラスであり、Sourceのみからは列フォーマットの完全一致は確認できていない（RE 11章Q-P2）。
- **What we need to confirm**: 実際の運用で「Export結果のExcelをそのまま編集してImportに使っている」のか、それとも別フォーマットとして扱われているのか。
- **Why it matters**: 将来Portalが生成するImport用Artifactの列フォーマットを設計する際、Export側の列と合わせるべきか独自に定義してよいかの判断材料（`docs/target-price-change-workflow.md` 12章・17章PC-15）。

### Q20. 〔P3〕Item Group価格変更の配下SKUへの連動有無

- **What we already know**: `MS_ITEM_GRP`（Item Group）単位の価格一括設定はSourceで確認済みだが、Import Batch自体には配下`MS_ITEM`へ自動反映するロジックは見つかっていない（RE 4章・7章・11章Q-P3）。
- **What we need to confirm**: 実運用上、Item Group価格を変更した際に配下SKUの価格も実際に連動して変わっているか。連動しているなら別の仕組み（別Batch・手動運用等）があるはずで、その実態を確認したい。
- **Why it matters**: `docs/target-price-change-workflow.md` 9章（Bulk Price Change設計）・17章PC-10で、Item Group単位一括変更を新Portalでどう扱うべきかの前提。

### Q21. 〔P3〕Item GroupとBrandの対応関係・Master構造

- **What we already know**: `MsItemGrp`は`itemGrpCd`単位で価格を保持するが、Item GroupとBrandの対応関係はSourceからは特定できていない（RE 8章・11章Q-P4）。
- **What we need to confirm**: Item GroupとBrandは1対1か、1つのBrandに複数のItem Groupが対応するのか、そもそも別概念か。
- **Why it matters**: `docs/target-price-change-workflow.md` 9章・17章PC-13で、「Brand単位の一括価格変更」が技術的に成立するかどうかの前提。

### Q22. 〔P3〕価格変更の実施頻度・実施者・承認者（現状）

- **What we already know**: 変更幅超過時、Excel内`APPROVAL`列に`ACCEPT`と手入力しないとImportがエラーになる仕組みはSourceで確認済み（RE 4章）。ただし、この`ACCEPT`を実際に「誰が」「どういう基準で」入力しているかはSourceからは分からない。
- **What we need to confirm**: 価格変更は現在どのくらいの頻度で行われているか。実施者・（変更幅超過時の）承認者は誰か。
- **Why it matters**: `docs/target-price-change-workflow.md` 11章（Approval設計）・17章PC-6で、Approval Workflowの要否を検討する際の現状把握として必須。

### Q23. 〔P3〕Price関連Export/Import Endpoint・Batch起動のRole/Permission制限

- **What we already know**: `MsPriceListImportBatch`自体にRole/Permissionチェックのコードは無い（Batchである以上、誰でもImport Folderへ配置できればDBが更新される構造、RE 10章）。Export側（`/api/export/{type}`）のRole制限有無はSpring Security設定側の調査が必要でRE時点では未確認（RE 11章Q-P6）。
- **What we need to confirm**: 現在、価格変更のExport/Import操作を実行できる担当者はどう制限されているか（システム的な制限か、運用上の取り決めのみか）。
- **Why it matters**: `docs/target-price-change-workflow.md` 11章で、既存OPERATOR/ADMIN Roleで価格変更のAccess Controlを表現できるかの判断材料。

---

## 優先度別サマリ

| 優先度 | 件数 | 項目 |
|---|---|---|
| P1 | 6 | Q1, Q2, Q4, Q7, Q8, Q13 |
| P2 | 9 | Q3, Q9, Q10, Q11, Q12, Q14, Q15, Q16, Q17 |
| P3 | 8 | Q5, Q6, Q18, Q19, Q20, Q21, Q22, Q23 |
| **合計** | **23** | |

## Theme別サマリ

| Theme | 件数 | 質問番号 |
|---|---|---|
| PO番号・Official PO Import運用 | 7 | Q1-Q7 |
| メーカーメール送信 | 4 | Q8-Q11 |
| Cancellation | 1 | Q12 |
| Infrastructure | 1 | Q13 |
| EDI発注（Phase 7-J追加） | 4 | Q14-Q17 |
| 価格変更（Phase 8-A追加） | 6 | Q18-Q23 |
