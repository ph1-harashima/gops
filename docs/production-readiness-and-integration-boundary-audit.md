# Production Readiness & Integration Boundary Audit（Phase 8-K）

**Status**: 監査・設計整理のみ。Frontend/Backend/DB Migration/API変更は0件。Legacy（`phasep-gulliver`）はREAD/Grepのみで一切変更していない。新Business Ruleの決定は行っていない。

**目的**: Phase 8-JまでにPortalが到達した機能範囲（Ordering/Approval/Manufacturer Communication/Supplier Response/Fulfillment/Follow-up/Price Change Foundation/Stock・Sales・Arrival・Warehouse Stock Visibility/Dashboard/Audit/Role Foundation/Scalability・Navigation・Documentation Consolidation）を前提に、Prototype/Demo FoundationとProduction Readyの間に残るGapを体系的に洗い出し、①今すぐ準備可能なもの、②Customer Review待ち、③Ernest確認待ち、④External Spec待ちを分離し、⑤Ordering系Production化のCritical Pathを明確化し、⑥Estimateへ反映可能なScope構造を整理し、⑦次に実装すべきProduction Foundationを決めるための一次資料とする。

**一次情報**: `docs/requirements-coverage-and-remaining-gap-audit.md`（Phase 8-I/8-J）、`docs/customer-review-decision-package.md`、`docs/customer-review-question-sheet.md`、`docs/ernest-current-operation-question-sheet.md`、`docs/9-17-demo-script.md`、`docs/demo-walkthrough/README.md`/`README_EN.md`、`docs/G-SYS_Online-Ordering_Prototype_Technical_Design.md`、`docs/target-price-change-workflow.md`、`docs/official-po-integration-detailed-design.md`、`docs/official-po-integration-foundation.md`、`docs/supplier-contact-mail-template-foundation.md`、`docs/fulfillment-follow-up-foundation.md`、`docs/role-approval-implementation.md`、`docs/excel-legacy-concurrency-control.md`、`docs/target-production-procurement-workflow.md`、および実Source（`backend/src/main/resources/application.yml`、`backend/src/main/java/com/glv/gsysportal/config/SecurityConfig.java`、`backend/src/main/java/com/glv/gsysportal/safety/SafetyGuardEnvironmentPostProcessor.java`、`backend/demo-data/03-readonly-user.sql`、`backend/docker-compose.yml`、`backend/pom.xml`）。

---

## 1. Executive Conclusion

- Portalは「Ordering意思決定〜メーカー合意〜Fulfillment確認」までの業務コアをPrototype/Demoとして完結させているが、**Production化に必要な外部接続（Legacy Write/Email/EDI）は意図的にすべて未実装**であり、これは実装漏れではなく「Legacy READ ONLY・実送信禁止」という本エンゲージメント全体の絶対制約に忠実に従った結果である。
- **最大のBlockerは実装ではなくSource外の実運用情報**: Official PO Excelの実際の作成者・Import Folder Path・起動Trigger方式・PO番号採番規則がいずれもSource上確認不能（`official-po-integration-detailed-design.md` 21章で既に特定済み）であり、これが解決しない限りOfficial PO Handoffの実装は着手すらできない。
- 現在の`SafetyGuardEnvironmentPostProcessor`は、Host/DB名Allowlistにより**このコードベースを絶対にProduction/Legacy本番へ接続させない**設計になっている。これはDemo期間中の事故防止として正しく機能しているが、逆に言えば「Production化」は必ずこのGuard自体の意図的な設計変更（Allowlist拡張）を伴う、後戻りできない一線であることを明記する。
- Retry/Idempotency/Health Check/Monitoring/Secret管理のいずれも、現Repositoryには**技術的Foundationが一切存在しない**（`@Scheduled`/`@Retryable`/actuator依存、いずれも0件）。これらはCustomer回答を必要としない「今すぐ安全に作れるProduction Foundation」の主要候補である（19章）。
- Hosting/Networkは、Legacy側は「AWS利用の示唆はあるが未確定」（`target-production-procurement-workflow.md` 18-6）、Portal側は「EC2/RDS/S3/CloudWatchが候補として提案されているのみで未確定」（同336行）と、いずれも**未決定のまま**である。

---

## 2. Production Readiness分類サマリ

指示の10分類（A. PRODUCTION READY 〜 J. NOT REQUIRED）で、3章のOrdering Critical Path 18項目 + 4〜17章で扱う技術領域を対象に集計する。件数は完了報告25章で報告する。分類基準：

| 記号 | 意味 | 本Documentでの適用範囲 |
|---|---|---|
| A | PRODUCTION READY | そのままProduction投入可能（該当なしの見込み — 9章参照） |
| B | PRODUCTION FOUNDATION READY | Demoとして機能完結、Production化には追加実装が必要だがBusiness Rule上の障害はない |
| C | IMPLEMENTATION READY | 今すぐ実装着手可能（Customer/Ernest/External回答不要） |
| D | CUSTOMER REVIEW WAIT | Gulliver社の意思決定が必要 |
| E | CURRENT OPERATION WAIT | Ernestへの現行運用確認が必要 |
| F | EXTERNAL SPEC WAIT | Vendor/外部System仕様確認が必要 |
| G | INFRASTRUCTURE WAIT | Hosting/Network情報が未確定 |
| H | SECURITY / CONFIG WAIT | Secret管理・Production認証情報が未整備 |
| I | BLOCKED | 現時点で安全に進められない |
| J | NOT REQUIRED | Portalのスコープ外、または既存Legacy運用のままでよい |

---

## 3. Ordering Production Critical Path

| # | 機能 | Demoで動くか | Production不足 | Customer Decision | Ernest確認 | External Spec | Infra/Security | 実装可能時期 | Primary Status |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Candidate List | ○ | Legacy Production DB接続のみ（Query自体は完成） | 無 | 無 | 無 | G（Legacy接続情報） | Legacy接続情報確定後、即 | B+G |
| 2 | Recommended Qty (calc4) | ○ | 無（Legacy Formula逐語移植、変更不要） | Appendix I（Warning閾値等） | 無 | 無 | 無 | 即 | B |
| 3 | Draft | ○ | 無 | 無 | 無 | 無 | 無 | 即 | B |
| 4 | Preview | ○ | 無 | 無 | 無 | 無 | 無 | 即 | B |
| 5 | Approval | ○ | 無（2段階Workflow完成） | 複数段階承認・自己承認範囲 | 無 | 無 | 無 | 即 | B+D |
| 6 | Revision | ○ | 無 | Revision再投入許容範囲（Official PO再投入との関係） | 無 | 無 | 無 | 即 | B+D |
| 7 | Order Detail | ○ | 無 | 無 | 無 | 無 | 無 | 即 | B |
| 8 | Audit | ○ | Production Retention期間未確定 | Audit Log保持期間 | 無 | 無 | 無 | 即（期間はDefault値で先行可） | B+D |
| 9 | Manufacturer Communication (Demo Send) | ○（Demoのみ） | 実送信機構が丸ごと未実装（5章） | Fromアドレス/Reply-To方式/CC対象 | SMTP資格情報・既存SYS_SEND_MAIL利用可否 | 無 | H（SMTP Credential） | Customer+Ernest回答後 | D+E+H |
| 10 | Official PO | ×（Preflightのみ） | Handoff機構が丸ごと未実装（4章） | PO番号採番規則・Excel作成者 | Import Folder Path・起動Trigger・実行頻度 | 無 | G（Filesystem/Network経路） | Ernest回答が最優先Blocker | E+D+G |
| 11 | Email | ×（Preview止まり） | SMTP接続・添付・Retry・Bounce処理すべて未実装（5章） | Template文面確定・CC対象 | 既存メール送信方法の実態 | 無 | H | Customer+Ernest回答後 | D+E+H |
| 12 | EDI | ×（Channel記録のみ） | 実連携が丸ごと未実装（6章） | 対象Supplier範囲 | 現状のEDI実態 | **Vendor Interface仕様が皆無** | 無 | External Spec確定後 | F |
| 13 | Supplier Response | ○ | 無 | 曖昧時のBlocked/Warning基準 | 無 | 無 | 無 | 即 | B |
| 14 | Agreement | ○ | 無 | 双方合意の正式判定条件 | 無 | 無 | 無 | 即 | B+D |
| 15 | Fulfillment | ○ | Production Legacy DB接続のみ | 未納/一部納品の正式業務定義 | 無 | 無 | G | Legacy接続情報確定後 | B+D+G |
| 16 | Follow-up | ○（Preview止まり） | 実送信は9番と共通Blocker | 問い合わせTiming・分類 | 誰がClose対応するか | 無 | H | Customer+Ernest回答後 | D+E+H |
| 17 | Legacy Concurrency | ○ | Production Legacy DB接続のみ | 手作業Excel継続許可可否 | 無 | 無 | G | Legacy接続情報確定後 | B+D+G |
| 18 | User / Role / Permission | ○（Foundationのみ） | User CRUD UI/API不在、SSO/Password運用未確定（7章） | Role階層・User管理方式 | 無 | SSO Provider仕様（採用する場合のみ） | H（Password/Session Policy） | Customer回答後 | D+H |

**結論**: 18項目中、**Customer/Ernest/External回答を一切要さず"今すぐ"Production化を進められる純粋な項目は実質ゼロ**（1・7・15・17がLegacy Production接続情報という同一のInfrastructure Gapのみで足止めされている点を除く）。9・10・11・16（Official PO/Email/Follow-up実送信）は同一の「実送信・実Handoff」という一塊のBlockerで、Ernest確認が最優先の先行タスクである。

---

## 4. Official PO Production Boundary

`official-po-integration-detailed-design.md`（Phase 7-C2-Design、全24章）が既にSource Fact・実装方式・CUSTOMER REVIEW項目を極めて詳細に整理済みであり、本Phaseはこれを実装Boundaryの観点で再確認する。

### 4.1 Source Fact（再確認、変更なし）

- Legacy G-SYSのOfficial PO確定は`PrOfficialPoImportBatch`という**standalone CLIバッチ**によるExcel Import（Delete&Recreate方式）であり、Web APIではない。
- **Excel Generator/Download機能はSource上に存在しない**（指示が明示した前提を再確認: Import側の実装は確認できるが、Excelを「生成する」側の実装はLegacy Sourceのどこにも見つからない）。したがって「現在のOfficial PO Excelを誰がどうやって作っているか」はSource外の完全な業務運用情報である。
- Portalの`prototypePoNo`（`PO-DEMO-yyyyMMdd-####`）は**G-SYS Official PO No.とは無関係な、Portal限定の識別子**であり、これをそのままG-SYS側へ書き込んではならない。
- Currency判定はCell値ではなくExcelの表示形式（Number Format文字列）から行われる — PortalがExcelを生成する場合、この点を誤ると全行がCurrency不明エラーになる技術的落とし穴がある。

### 4.2 実装可能範囲と回答待ち範囲の分離

| 項目 | 実装可能範囲（今すぐ着手可） | 回答待ち範囲 |
|---|---|---|
| 実際のOfficial PO Artifactは何か | Excel Contract自体は`testfile/OfficialPO.xlsx`実測により**確定済み**（3章） | 誰がこのExcelを実際に生成しているか（人手か別ツールか）はErnest確認待ち |
| 誰が作成するか | 該当なし | **Ernest確認待ち（最優先）** |
| どこで作成するか | 該当なし | 同上 |
| PO Numberの実運用生成元 | 区切り文字位置（Index4/8）・上限長はSource確認済み | 区切り文字の正体・ID Codeの意味・命名規則の全体像はErnest/Gulliver確認待ち |
| G-SYS Import用ExcelとSupplier向けPOが同一か | 該当なし | Ernest確認待ち（Excel Contract自体は同一と推定できるが実運用未確認） |
| PortalがArtifactを作るべきか | **Excel Generator（POI Writer）はContract確定済みのため実装可能** — ただしPO番号採番規則（Ernest確認待ち）が確定するまでは実際のPO No.を書き込めないため、Foundationとしての実装に留まる（Phase 7-C2Aで既に着手・Foundation化済み、23章） | 正式PO番号の値そのもの |
| PortalがHandoffするだけか | **Import Folder Pathへの書き込み動線（方式A: Excel Import Pipelineへの投入）はSource上安全に設計可能**（Legacy Batch自体は無変更） | Import Folderの実Path・起動Trigger方式（Ernest確認待ち） |

### 4.3 Production化のCritical Path（Official PO単体）

```
Ernest確認（誰が/どこで/PO番号規則/Import Folder Path/起動頻度）
  → PO番号採番規則の確定（Gulliver最終決定）
  → Excel Generator本実装（Contract自体は確定済みのため技術Riskは低い）
  → Integration Worker実装（16章推奨のB案: 専用Worker、Portal Web層と分離）
  → Success Detection Polling実装（TR_PO/TR_PO_DTL照合、既存Legacy READ ONLY接続を再利用）
  → Preflight Check本実装（既にFoundation実装済み、7-C6のConcurrency Detectionと統合）
  → Integration Test（Legacy Demo環境での往復確認）
  → UAT（実Import Folder相当のStaging環境）
```

**現時点でBlockingしているのはErnest確認の1点のみ**であり、それ以降のTechnical Implementationは既にFoundation（Phase 7-C2A/7-C6）が実装済みで、設計（`official-po-integration-detailed-design.md`）も完成している。

---

## 5. Real Email Production Boundary

`supplier-contact-mail-template-foundation.md`（Phase 7-C3実装結果）を基準に、現在のDemo Manufacturer CommunicationとProduction SMTP送信の差分を整理する。

| 項目 | 現状（Demo/Foundation） | Production化に必要な追加 | 分類 |
|---|---|---|---|
| SMTP / Mail Provider | 未実装（接続コード自体が存在しない） | Provider選定（**本Phaseでは選定しない**、指示どおり） | H（Infra決定） |
| Authentication | 未実装 | SMTP認証情報のSecret管理（8章） | H |
| Sender Address | Preview上は「ログインユーザー」想定のみ | Fromを実アドレスにするかReply-To方式にするかCUSTOMER REVIEW未確定 | D |
| Reply-To | 未実装 | 同上 | D |
| Supplier Contact | **Foundation実装済み**（`supplier_contact`テーブル、優先順位付きResolution） | Production Supplier Contactデータの実投入（運用上の初期データ整備） | C（機構は完成、データ投入はOperationタスク） |
| Attachment | メタデータのみ（`generated: false`、実File添付なし） | 実PDF/Excel生成＋添付機構 | C（Official PO Excel Generator実装と連動、4章） |
| Template | **Foundation実装済み**（`mail_template`テーブル、優先順位付きResolution、AMBIGUOUS検出済み） | Template本文の正式内容確定 | D |
| Retry | **技術的機構が皆無**（`@Retryable`等の依存自体がpom.xmlに存在しない、13章） | Idempotency Foundation実装 | C |
| Failure Handling | 未実装 | 送信失敗時のAttention化・再送UI | C（既存Attention機構を再利用可能） |
| Duplicate Send Prevention | 未実装（Send API自体が存在しない） | Idempotency Key設計（Official PO同様`portal_order_id + revision_no`パターンを再利用可能） | C |
| Audit | **既存機構で対応可能**（`MAIL_PREVIEW_GENERATED`と同じパターンで`MAIL_SENT`を追加すればよい、新規Audit基盤は不要） | 実装のみ | C |
| Sent Artifact retention | 未実装 | 送信済みメール本文・添付の保管方針（Backup/Retention、16章） | D（保持期間はBusiness Rule） |
| Bounce / Delivery Failure | 未実装 | Bounce検知機構（SMTP ProviderのWebhook等、Provider選定に従属） | H |
| Environment Separation | **SafetyGuardの`local/demo/test`Profile Allowlistにより、実装されたとしても現状のコードベースでは絶対に実送信が起動しない**（Production Profileが存在しない） | Production Profile自体の新設（20章のCritical Pathの一部） | G+H |

**結論**: Supplier Contact/Mail Template Masterおよび Resolution Logic（誰に・どのTemplateを・誰をCCに）は**既にProduction相当の完成度でFoundation実装済み**である。残るGapは「実際にSMTPで送る」という最後の1ステップと、それに付随するRetry/Failure Handling/Secret管理であり、これらは**SMTP Provider選定（Customer/Infra決定）を待たずにIdempotency Foundation・Audit拡張・Failure Handling UIまでは実装可能**（19章）。

---

## 6. EDI Production Boundary

`PortalOrder.communicationChannel`（Phase 7-H）に`"EDI"`という値を記録できるのみで、**実際のEDI連携コードは一切存在しない**（File生成・API呼び出し・SFTP接続、いずれもゼロ）。

| 項目 | 現状 | 分類 |
|---|---|---|
| Supplier identification | 未確認（どのSupplierが実際にEDIを使うか不明） | E（Ernest） |
| Interface method | 未確認（Web Portal手入力／File授受／API連携のいずれか不明、`ernest-current-operation-question-sheet.md` Q16） | E→F（Ernest未回答ならVendor確認へ昇格） |
| File format | Source上に存在しない | F |
| API/SFTP/Web upload | Source上に存在しない | F |
| Authentication | Source上に存在しない | F |
| Acknowledgement | Source上に存在しない | F |
| Error handling | Source上に存在しない | F |
| Retry | Source上に存在しない | F |
| Idempotency | Source上に存在しない | F |
| Supplier response flow | Source上に存在しない（Supplier ResponseはPortal内Business Actionとして別途実装済みだが、EDI経由での自動取込は無関係） | F |

**結論**: EDIはExternal Specが皆無のため、指示どおり**実装候補にしない**。Ernest Q16（現状把握）の回答が唯一の次の一手であり、それすらVendor仕様確認へ発展する可能性が高い。Production Critical Pathからは実質的に切り離して扱う（21章のEstimate Boundaryでも別枠とする）。

---

## 7. User / Role / Permission

`role-approval-implementation.md`（Phase 7-C1）を基準に、Business RoleとTechnical Authenticationを分離して監査する。

### 7.1 Technical Authentication（現状）

| 項目 | 現状 | Production Gap |
|---|---|---|
| Authentication | Spring Security Session Form Login、`portal_user.password_hash`（BCrypt） | SSO/Identity Provider連携は未実装・未検討（Customer Decision待ち） |
| User Master | `portal_user`（username/display_name/password_hash/role/email/active） | **CRUD UI/API自体が存在しない**（Flyway seedのみ、`requirements-coverage-and-remaining-gap-audit.md`で継続確認済みのGap） |
| Session management | Spring Security Defaultのまま（明示的なTimeout/同時セッション数制御コードなし） | Production Session Policy未定義 |
| Password / SSO | BCrypt Hashのみ。SSO/OAuth/SAML等は未実装 | Identity Provider要否がCustomer Decision |
| Disabled users | `portal_user.active`列は存在するが、無効化UIは未実装 | User CRUD実装と同時に必要 |

### 7.2 Business Role（現状）

| 項目 | 現状 | Production Gap |
|---|---|---|
| Role assignment | `OPERATOR`/`ADMIN`の2値、DB直接値のみ（UIから変更不可） | Role変更UI未実装 |
| Approval permission | `@PreAuthorize("hasRole('ADMIN')")`でBackend強制済み、HTTPレベル実測済み | 無（このまま） |
| Send permission | Demo Send自体がADMIN限定ではなく、実送信機能自体が未実装（5章） | 実送信実装時に権限設計要 |
| View permission | Candidate List等一部エンドポイントはpermitAll（意図的、7-C1時点の設計） | Production公開時の再検討要否はCustomer Decision |
| Admin function | Master管理（Supplier Contact/Mail Template）はADMIN限定、実測済み | 無 |
| Audit identity | `AuditEvent.performedBy`は`username`ベース、Display Name解決は読み取り時に別途行う（実装済み） | 無 |

### 7.3 User Management CRUDの要否判断

Source/Documentだけでは「User Management CRUDが本当に必要か」「既存Identity Provider（Gulliver社内AD/SSO等）との連携が必要か」を確定できない。**Questionとして分類する**（既存のB-7〜B-14、Ernest/Gulliver未回答のまま — 新規Question追加はしない、25章で確認）。

---

## 8. Security / Secrets

Repository実監査結果（`application.yml`, `SecurityConfig.java`, `.gitignore`）:

| 項目 | 現状 | Production Gap |
|---|---|---|
| Legacy DB credentials | `application.yml`にDemo用平文（`gsys_portal_ro`/`gsys_portal_ro_demo_pw`）が**Git管理下にそのまま存在** | Production Secretは環境変数/Secret Manager経由へ移行必須（製品は本Phaseで選定しない） |
| Portal DB credentials | 同上（`gsys_portal`/`gsys_portal_demo_pw`） | 同上 |
| SMTP credentials | 未実装のため存在しない | 実装時にSecret管理が前提 |
| External API credentials | 未実装のため存在しない | EDI/SSO実装時に必要（6章/7章） |
| Secret storage | **現状皆無**（`.gitignore`にも`.env`等のSecret除外パターンなし、平文がそのままCommitされる構成） | Secret Manager導入（AWS Secrets Manager/Vault等、製品未選定） |
| Environment variables | `application.yml`は単一Fileのみ、Profile別override File（`application-demo.yml`等）も存在しない — SafetyGuardのProfile Allowlist（`local`/`demo`/`test`）のみがDemo/Local/Testを区別する仕組みで、値自体は全Profile共通 | Production Profile用の値分離が必要（20章） |
| Encryption | DB接続はTLS設定なし（`useSSL=false`が明示的にLegacy MySQL接続文字列に含まれる） | Production接続はTLS必須化を要検討（Infra決定次第） |
| Log masking | **Logback独自設定が存在せず**（Spring Boot Defaultログのみ）、Password等のMasking機構なし | 実装時に要検討（現状Passwordをログ出力するコードパス自体は未確認だが、専用の保証機構がない） |

**確認事項（指示9章の明示要求）**: Repository内には**Production Secretは一切存在しない**（存在するのはDemo/Local専用の、コミットして問題ない旨が既存Documentで繰り返し明記されている値のみ）。これは既存の複数Phase報告（Phase 7-C1 16章「Demo Userパスワードハッシュ」等）と整合する。

---

## 9. Hosting / Network

| 項目 | 現状（Source確認結果） | 分類 |
|---|---|---|
| Portal Hosting | **未確定**。`target-production-procurement-workflow.md`336行が「New Portal候補: EC2/RDS PostgreSQL/S3/CloudWatch」と記載するが、これは**提案（Target Proposal）であり確定事実ではない** | G |
| Legacy G-SYS Hosting | **未確定**。同Document337行「現行G-SYSのEC2/S3使用は仕様書から確認できていない...未確定」と明記済み。7-A §18-6で「AWS利用の示唆」はあるが、Source確認済みの事実ではない | G |
| Legacy MySQL reachability | 現状はLegacy Demo MySQL（Docker、`localhost:33061`）のみが接続先。実Legacy MySQLへのNetwork経路は未確認 | G |
| Network path | 未確認 | G |
| Firewall | 未確認 | G |
| VPN / Private network | 未確認 | G |
| DNS | 未確認（Production Domain自体も未確定） | G |
| TLS | Portal Server自体のTLS終端方式（Reverse Proxy等）は未実装・未設計 | G |
| Reverse proxy | 未実装・未設計 | G |
| Production domain | 未確定 | G |

**結論**: Hosting/Networkは**9項目すべてUnconfirmed**。具体的なVendor/Product（AWS/EC2/S3等）を本Documentでも確定事項として扱わない（指示どおり）。これは他のGapと異なり「Customer Decision待ち」というより「そもそも調査自体が未着手」な領域であり、Infrastructure Wait（G）として20章のCritical Pathの早い段階に位置づける。

---

## 10. Legacy READ ONLY Production Access

現状の3層READ ONLY保証（`Technical Design` 4.1章で確立、本Phaseで実装Source側から再確認）:

1. **DB Account層**: `gsys_portal_ro`ユーザーに`GRANT SELECT`のみ、`REVOKE INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX`を明示実行（`backend/demo-data/03-readonly-user.sql`）。
2. **Connection Pool層**: `app.legacy.datasource.read-only: true`（`application.yml`、HikariCPの`readOnly`フラグ）。
3. **Application層**: `SafetyGuardEnvironmentPostProcessor`がHost/DB名Allowlist（`localhost`/`127.0.0.1`/`legacy-demo-mysql`/`prototype-postgres`、DB名`legacy_demo`/`gsys_portal`）を強制し、これ以外への接続そのものを起動時に拒否する。

| 項目 | 現状 | Production化に必要な追加 |
|---|---|---|
| DB account | Demo MySQL専用ユーザーのみ存在 | Production Legacy側にSELECT-only Roleを新設する運用作業（Legacy DBA側の作業、Portal Sourceの変更ではない） |
| SELECT-only grants | Demo環境で確立済みパターンをそのまま踏襲可能 | 同上 |
| allowed schema/table | 現状は`legacy_demo.*`全体にSELECT許可（Demo環境のため） | Production運用では対象Table/Viewを絞る設計が望ましい（Business Decision不要、純粋なDBA作業） |
| network | 未確認（9章） | Legacy DBへのNetwork到達性確立 |
| connection pool | HikariCP、`maximum-pool-size: 5`（Demo値） | Production負荷に応じたPool Size再検討 |
| timeout | 明示的なConnection/Query Timeout設定は`application.yml`に存在しない（HikariCP Default依存） | 明示的なTimeout設定の追加（実装可能、Customer回答不要） |
| failure handling | Legacy DB接続断時は例外がそのままHTTPエラーへ伝播（`requirements-coverage-and-remaining-gap-audit.md` 17章で既に指摘済みのGap） | Graceful Degradation実装（実装可能） |
| audit/logging | Legacy Adapterへの個別Query Logはない（Spring Boot Default INFOログのみ） | 実装可能 |
| health check | **存在しない**（`spring-boot-starter-actuator`依存自体がpom.xmlに無い） | 実装可能（19章） |

**最重要確認**: `SafetyGuardEnvironmentPostProcessor`は現状のAllowlistのままでは**Production Legacy DBへの接続を技術的に拒否する**。Production化の最初の一歩は、このGuardのAllowlistへProduction Host/DB名を安全に追加する設計変更であり、これ自体がGate（後戻り不可能な一線）である。**将来のPrice Change等のWrite機能についても、PortalからLegacy DBへの直接Writeを前提にしない**という指示は、4章のOfficial PO Boundaryで確認したとおり既存設計（方式A: Excel Import Pipeline経由）と完全に整合している。

---

## 11. Portal DB Production Readiness

| 項目 | 現状 | 分類 |
|---|---|---|
| Migration | Flyway、`V1`〜`V12`まで全てForward-only（`role-approval-implementation.md`等で確認済み）。Production投入時も同じMigration列がそのまま適用可能 | C |
| Backup | 未実装・未設計（Demo環境はDocker Volume、Backup機構なし） | G |
| Restore | 同上 | G |
| Retention | Business Data（`portal_order`等）の保持期間ルールは未確定 | D |
| HA | 未設計（Demo環境は単一Postgres Container） | G |
| Connection management | HikariCP、`maximum-pool-size: 5`（Demo値、Production負荷試算が必要） | G |
| Monitoring | 未実装（12章） | C |
| Capacity | 未試算（17章のData Volume Riskと連動） | C（試算自体は今すぐ着手可） |
| Audit retention | `audit_event`テーブルに削除ロジックなし（無期限保持が現状の実質仕様） | D（Business Retention Rule未確定のため） |
| Data deletion policy | 未確定（Portal User情報等、個人情報関連のRetention/削除方針含む） | D |

**結論**: Migration自体はProduction Readyな構造。Backup/HA/Monitoringは技術的に今すぐ設計・実装着手可能（Customer回答不要）。Retention/削除方針はBusiness Rule次第のため、Customer Review Waitとして分離する。

---

## 12. Monitoring / Observability

Portalの具体的Failure Pointに紐づけて整理する（一般論のObservability論を避ける、指示どおり）。

| Failure Point | 現状の可視化手段 | Gap |
|---|---|---|
| Application logs | Spring Boot Default（`logging.level.com.glv.gsysportal: INFO`）のみ | 構造化ログ（JSON等）・集約先が未設計 |
| Error logs | 個別のCatch/例外ハンドリングに依存、集約なし | 未設計 |
| Mail send failure | **実送信自体が未実装のため、Failure自体が発生し得ない**（5章） | 実送信実装と同時に必要 |
| External integration failure | Official PO/EDIとも実連携が未実装のため同上 | 同上 |
| Legacy DB unavailable | 例外がそのままHTTPエラーへ伝播（10章で既述） | Health Check・Circuit Breaker相当の実装候補（19章） |
| Portal DB unavailable | 同上（Spring Data JPA Defaultの例外伝播） | 同上 |
| Health endpoint | **存在しない**（actuator依存なし） | 実装可能（19章の最有力候補） |
| Metrics | 存在しない | 実装候補（Micrometer等、製品選定はInfra決定次第） |
| Alert | 存在しない | Infra決定次第 |
| Audit log | `audit_event`テーブルで**Business操作の監査証跡は既に充実**（Draft作成〜Approval〜Response〜Agreement〜Fulfillment/Follow-upまで一貫して記録済み） | Technical Failureの監査（上記各項目）とは別軸、混同しない |

---

## 13. Retry / Idempotency

pom.xml確認結果: **`resilience4j`/`spring-retry`等の依存は一切存在しない**。`@Scheduled`/`@Retryable`アノテーションもコードベース全体で0件（`grep`確認済み）。

| 対象 | 現状 | Idempotency Foundation評価 |
|---|---|---|
| Email send | 未実装 | Send API自体が無いため評価対象外、実装時に同時設計 |
| Official PO handoff | Foundation設計は`official-po-integration-detailed-design.md` 14章で**Key候補=`portal_order_id + revision_no`**（13章のUNIQUE制約と同一）まで具体化済み。実装は7-C2B以降未着手 | **設計済み、実装のみ残る** — Business Rule決定不要 |
| EDI | 未実装（6章） | External Spec確定後 |
| Future external integration | 汎用的な二重実行防止パターン（Outbox Table + UNIQUE制約）はOfficial PO設計で確立済みのため、他の外部連携にも転用可能 | 転用設計は今すぐ整理可能 |
| Fulfillment follow-up | Follow-up Case作成自体は冪等性を要さない単純CRUD（重複作成はBusiness的に許容） | 対象外 |

**二重送信・二重実行リスク**: 現状Send/Handoff API自体が存在しないため、二重実行の実害はまだ発生し得ない。しかし「Technical Idempotency Foundation」（19章の候補）として、Outbox Table + UNIQUE制約パターンをOfficial PO以外の将来のWrite/Send操作にも再利用できる**共通Idempotency Utility**として先に整備することは、Business Ruleを決めずに安全に着手できる。

---

## 14. Error Handling / Recovery

| Scenario | 現状 | Manual Recovery主体 |
|---|---|---|
| Legacy unavailable | 例外がHTTPエラーへ伝播（10章） | 未確定 — **Customer/Operation確認として分離**（誰が一次対応するか） |
| Mail failure | 未実装のため対象外（5章実装時に設計） | 同上 |
| External interface failure | 未実装のため対象外（6章） | 同上 |
| Portal DB failure | Spring Data JPA Default例外伝播 | 同上 |
| Partial operation | Official PO ImportはFile単位で全Validation通過が前提（`official-po-integration-detailed-design.md` 10章、Partial Importは基本的に発生しない設計） | 該当なし（Source Fact） |
| Retry | 13章のとおり未実装 | — |
| Manual recovery | 現状Attention機構（既存）がADMIN向けの「要人手確認」表示として機能しており、Official PO Failure時もこのパターンを踏襲する設計（`official-po-integration-detailed-design.md` 10.1章） | **「誰が」対応するかはCustomer Review未確定**（21章のCUSTOMER REVIEW #7と同一） |

**結論**: Recovery機構の「型」（Attention化）は既存Foundation（Order Attention）で確立済みであり、技術的には転用可能。「誰が対応するか」というOperation Roleの決定のみがCustomer/Operation確認待ち。

---

## 15. Deployment / Release

| 項目 | 現状 | Gap |
|---|---|---|
| Build | Maven（`mvnw.cmd`）/ npm（Vite）、いずれも動作確認済み | 無 |
| Test | Backend 429 tests（Phase 8-J時点）、Frontend E2E 19 spec files、いずれもLocal Docker前提 | Production/CI環境での実行は未整備（CI Pipeline自体が本Repositoryに存在しない — `.github/workflows`等未確認） |
| Environment config | `application.yml`単一File、Profile別値分離なし（8章） | Production Profile自体が存在しない |
| Migration | Flyway、Forward-only（11章） | 無（そのまま使える） |
| Startup | `SafetyGuardEnvironmentPostProcessor`によるProfile/Host/DB Guard（Local/Demo/Test限定） | Production Profile追加時にGuard自体の設計変更が必要（10章） |
| Rollback | 未設計（Flyway Migrationの巻き戻し手順・Application Versionのロールバック手順、いずれもDocument化されていない） | G |
| release procedure | **既存Documentに存在しない**（Phase 8-J Documentation Inventoryで確認済み、Handover/Release Notes自体が未作成） | Gapとして明記（新規作成はしない、指示どおり） |

**結論**: BuildとMigrationは健全。CI Pipeline・Production Profile・Rollback手順・Release Procedureはいずれも**未着手のGap**として明記する。新規Handover/Release Notes Documentは本Phaseでも作成しない（作成要否のみ判断: **必要性あり、ただし作成はCustomer Reviewで実装Scopeが固まった後が適切**）。

---

## 16. Backup / Recovery

| 対象 | 現状 | 分類 |
|---|---|---|
| Backup対象 | Portal PostgreSQL（`portal_order`等Business Data）、`portal_user`/`supplier_contact`/`mail_template`（設定的Master Data） | G（Backup機構自体が未実装） |
| Restore | 未設計 | G |
| Recovery point (RPO) | 未確定 | D（Business要件） |
| Recovery time (RTO) | 未確定 | D |
| Artifact retention | **Official PO Artifact自体がまだ未確定**（4章のとおり、PortalがExcelを生成する設計はFoundationのみ）。Artifact Backupは、Artifactそのものの定義が固まってから設計すべき | D（回答待ち、指示どおり） |

---

## 17. Production Data Volume

Phase 8-J Backend Pagination Auditを踏まえた再評価。

| 画面 | Production Volume Risk | Pagination状態 | Performance Test要否 |
|---|---|---|---|
| Order History | **中**（発注履歴は業務継続で無制限増加） | Phase 8-JでDB-level Pagination化済み（`Specification`+`Pageable`） | 実データ量到達後の実測を推奨（今すぐは不要） |
| Candidate List | **低〜中**（Legacy Master品目数に比例、無制限増加ではない） | **意図的にPagination未適用のまま維持**（Phase 8-Jの判断を継続）。`recommendedQty`(calc4)がJava側Legacy Formula逐語移植のため、SQL側への複製は行わない | Legacy品目数が実際に増えた時点で再評価（現時点で強制しない） |
| Price Change | 低（Draft Batch単位、業務プロセスに比例） | Pagination未適用（Phase 8-J audit時点でB分類のまま） | 不要 |
| Arrival | 中（Invoice/PO実績の累積） | Backend Pagination済み（Phase 8-G） | 不要 |
| Warehouse Stock | 低（SKU×倉庫の組み合わせ、有限） | Backend Pagination済み（Phase 8-G） | 不要 |
| Stock/Sales | 低（SKU数に比例、有限） | Backend Pagination済み（Phase 8-H） | 不要 |
| Audit | **高（唯一の無期限蓄積テーブル）** | Pagination対象のList UI自体が存在しない（Order Detail内Timelineのみ、1 Order分に限定されるため個別リスクは低い）が、**テーブル自体のRetention方針が無いため長期的にはDB全体の肥大化リスク** | Retention Policy確定後（11章のCustomer Review Wait） |

**Candidate List維持確認**: 指示どおり、calc4のSQL複製は行わない判断を本Phaseでも維持する。

---

## 18. Customer / Ernest / External Blocking Matrix

### A. Gulliver Decision（Customer Review Wait, 抜粋・Production関連のみ）

| Blocking Function | Why Blocking | 回答後に可能になること | Priority |
|---|---|---|---|
| Official PO番号採番規則 | Excelへ書き込む実PO No.が決まらない | Excel Generator本実装 | **最高（4章の唯一のCritical Path起点）** |
| SMTP Fromアドレス/Reply-To方式 | Mail Template/Sender設計が確定しない | 実送信実装着手 | 高 |
| Audit/Business Data Retention期間 | Backup/Recovery設計・Portal DB Capacity試算が確定しない | Backup/Recovery設計完了 | 中 |
| User Management要否・SSO要否 | Role Foundation拡張方針が決まらない | User CRUD実装可否判断 | 中 |
| Legacy書込Operation責任者 | Manual Recovery設計が確定しない | Error Recovery UI実装 | 中 |

### B. Ernest Current Operation（抜粋・Production関連のみ）

| Blocking Function | Why Blocking | 回答後に可能になること | Priority |
|---|---|---|---|
| Official PO Excel作成者・Import Folder・起動Trigger（Q1/Q2/Q4/Q7既存P1） | Portal→G-SYS Handoff方式全体が確定しない | Integration Worker実装着手 | **最高** |
| 現在のメーカーへのメール送信方法（Q8既存P1） | 実送信のFrom/Provider設計が確定しない | 実送信実装着手 | 高 |
| G-SYS本体・Import FolderのHosting/Network（Q13既存P1） | Legacy Production接続方式が確定しない | Legacy READ ONLY Production Access実装（10章） | 高 |
| EDIの実際の発注方式（Q16） | EDI実装可否自体が判断できない | Vendor Spec確認可否判断 | 中（Externalへ発展する可能性） |

### C. External Specification

| Blocking Function | Why Blocking | 回答後に可能になること | Priority |
|---|---|---|---|
| EDI Vendor Interface仕様 | 実装候補にすらできない（6章） | EDI実装着手 | 低（Ernest回答が前提のため今は動けない） |
| Logizero/Tempostar API仕様（既存、L-4/L-5b） | Slide 13要望への対応可否が判断できない | Warehouse Integration検討着手 | 低（Ordering Critical Pathとは別軸） |
| SMTP Provider仕様（選定後） | Bounce/Delivery Failure処理の実装方式が確定しない | Failure Handling本実装 | 中（Provider選定はInfra決定） |

---

## 19. Immediate Implementation Candidates

Customer/Ernest/External回答無しで安全に実装できるProduction Foundationを、Source上の不足が実際に確認できたものに限定して抽出する（不要なEnterprise機能の水増しはしない）。

| # | 候補 | Source上の不足確認 | Customer Value | Risk |
|---|---|---|---|---|
| 1 | Health Check Endpoint | `spring-boot-starter-actuator`依存が存在しない（10章/12章で確認済み） | 高（Deployment/Monitoringの前提条件） | 低 |
| 2 | Legacy/Portal DB接続の明示的Timeout設定 | `application.yml`にTimeout関連プロパティが一切ない（10章） | 中 | 低 |
| 3 | Technical Idempotency Foundation（Outbox+UNIQUE制約の共通化） | Official PO設計（13章）で確立済みパターンをUtility化する余地あり | 中（将来のEmail/EDI実装を早める） | 低 |
| 4 | 構造化Error Handling（Legacy/Portal DB unavailable時のGraceful Degradation） | 例外がそのままHTTPエラーへ伝播（10章/12章） | 中 | 低 |
| 5 | Production Logging整備（構造化ログ） | Spring Boot Defaultのみ（12章） | 中 | 低 |
| 6 | Deployment Config分離（Production Profile Skeleton） | Profile別Config File自体が存在しない（8章/15章） | 高（Production化の前提） | 中（SafetyGuardの設計変更を伴うため慎重な実装が必要） |
| 7 | Portal DB Migration/Backup手順の文書化（実装ではなくRunbook作成） | Backup機構・手順いずれも未設計（11章/16章） | 中 | 低 |
| 8 | Integration Adapter Interface抽象化（Official PO/Email/EDIが将来共有できるRetry/Idempotency層） | 3種の外部連携すべてが個別に同じ問題（二重実行防止）を抱えている（13章） | 中〜高（将来の実装コスト削減） | 低〜中 |

**除外した候補**: Circuit Breaker専用ライブラリ導入、独自Secret Manager実装、Metrics/Alert基盤のフル実装（Provider未選定のため今は水増しになる）、CI/CD Pipeline全体構築（Repository構成・Hosting決定と強く連動するため単独では着手困難）。

---

## 20. Production Readiness Critical Path

```
[最優先] Ernest確認（Official PO作成者/Import Folder/起動Trigger/メール送信方法/Hosting）
   ↓
Gulliver最終決定（PO番号採番規則/Fromアドレス方式/Retention期間/User管理方針）
   ↓
Hosting/Network調査・決定（9章、現状ゼロから — Vendor/Product未選定）
   ↓
Secret管理基盤の選定・導入（8章、Vendor未選定）
   ↓
Production Profile / SafetyGuard Allowlist拡張（設計変更を伴う一線、10章/15章）
   ↓
Official PO Excel Generator本実装 + Integration Worker実装（4章、Foundation完成済みのため相対的に早い）
   ↓
Email/EDI Production統合（5章/6章、EDIはExternal Spec依存のため並行して遅れる可能性が高い）
   ↓
Integration Test（Legacy Demo環境 → Staging相当環境）
   ↓
UAT
   ↓
Release（15章のRelease Procedure整備が前提）
```

**Immediate Implementation Candidates（19章）はこのCritical Pathと並行して、Ernest/Gulliver回答を待たずに今すぐ着手できる**（Health Check・Idempotency Foundation・Error Handling・Logging・Migration/Backup Runbook）。

---

## 21. Estimate Boundary

金額・人日は算出しない（指示どおり）。将来のOrdering見積（既存108人日等）を**再評価できる構造**として、実装状況を7分類へ整理する。過去見積値そのものは変更しない。

| 分類 | 該当機能 |
|---|---|
| Already implemented | Candidate List/Recommended Qty/Draft/Preview/Approval/Revision/Order Detail/Audit/Supplier Response/Agreement/Fulfillment/Follow-up(Preview除く)/Price Change Foundation/Stock・Sales・Arrival・Warehouse Stock Visibility/Dashboard/Role Foundation |
| Production hardening | Health Check/Idempotency Foundation/Error Handling/Logging/Timeout設定/Migration・Backup Runbook（19章の8候補） |
| Customer-dependent implementation | Official PO番号確定後のExcel Generator本実装/Email Template確定後の実送信/User Management（要否確定後）/Retention Policy確定後のBackup設計 |
| External integration | Official PO Integration Worker（Ernest確認後）/実SMTP接続（Provider選定後）/EDI実連携（Vendor Spec確認後） |
| Infrastructure / deployment | Hosting/Network調査・決定/Secret管理基盤導入/Production Profile整備/SafetyGuard Allowlist拡張/CI Pipeline構築 |
| Test / UAT | Integration Test（Legacy Demo→Staging）/UAT計画 |
| Documentation / handover | Release Procedure文書化/Handover Document作成（Scope確定後） |

---

## 22. Recommended Next Implementation Phase

Customer/Ernest/External回答を待たずに今すぐ着手できる候補をTop 3に絞る（19章の8候補から選定）。

### 候補1: Health Check + Structured Error Handling Foundation
- **Customer Value**: 中（直接業務価値ではないが、Deployment/Monitoringの前提条件として必須）
- **Production necessity**: 高（現状Health Endpointが皆無ではProduction Deployment自体が組めない）
- **Dependency**: 無
- **Risk**: 低
- **Implementation scope**: `spring-boot-starter-actuator`導入、Legacy/Portal DB接続失敗時のGraceful Degradation実装

### 候補2: Technical Idempotency Foundation（共通Outbox/UNIQUE制約パターンのUtility化）
- **Customer Value**: 中（将来のEmail/Official PO実装を安全かつ早く進められる）
- **Production necessity**: 高（Official PO設計で既に必要性が確定済み、13章）
- **Dependency**: 無（設計は`official-po-integration-detailed-design.md` 14章で既に具体化済み）
- **Risk**: 低
- **Implementation scope**: 汎用Idempotency Key Utility + Outbox Table Pattern（Official PO専用ではなく将来のEmail/EDIにも再利用可能な形で抽象化）

### 候補3: Production Deployment Runbook整備（Migration/Backup手順の文書化のみ、実装は最小限）
- **Customer Value**: 低〜中（直接の機能追加ではないが、後続のProduction化作業の土台）
- **Production necessity**: 中
- **Dependency**: 無
- **Risk**: 低
- **Implementation scope**: 既存Flyway Migration一覧の確定手順化、Backup対象/頻度の技術的Draft（Retention期間はCustomer Review待ちのままでよい — 「何を」Backupすべきかの技術リストは今すぐ作れる）

**Customer回答待ち項目は無理に推薦しない**（Official PO Excel Generator本実装・実SMTP接続・User Management等はいずれもCustomer/Ernest回答が前提のため、Top 3から除外した）。

---

**変更したFrontend/Backend/DB Migration/API/Legacy Source: 0件。** 本Documentの新規作成と、`requirements-coverage-and-remaining-gap-audit.md`等既存Documentへの反映のみ（詳細は完了報告参照）。
