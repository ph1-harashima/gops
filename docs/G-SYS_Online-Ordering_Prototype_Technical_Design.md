# G-SYS Online Ordering Prototype — Technical Design

更新日：2026-08-27
ステータス：Implementation Step 0/1 Baseline確定済み（17章参照）。Step 2以降は未着手。
正本：`G-SYS_Online-Ordering_Prototype_Requirements.md`（以下「要件MD」）
根拠：Phase 0 / Phase 0.5 Legacy Source Audit（要件MD 23章・27章・32章）＋ Implementation Step 0/1実装結果（要件MD 34章・本書17章）

本書は9/17 Prototypeの技術設計を確定するための文書である。Order Candidate List Vertical Slice（Step 0/1）は実装・Regression確認済みでBaseline確定した（17章）。それ以外の画面・機能は本書確定後、別指示で実装を開始する。

---

# 0. Technical Designの目的とApproach

最優先は「本番システムとして完璧なArchitecture」ではなく、**「Legacyを壊さず、9/17にEnd-to-Endで確実に動くPrototype」**である。ただし、将来の本開発・API Gateway・AI Agent・英語UI追加を阻害しない構造とする（要件MD 3章・4章・26章の原則に準拠）。

## 絶対条件（変更不可）

- Legacy Application変更禁止
- Legacy DB WRITE禁止（**共有中のLegacy実インスタンスに対して**。7章のDemo Data方式で言及する「使い捨てのLegacy互換インスタンス」は対象外。7章で詳細に境界を定義する）
- Legacy PO登録禁止（`TrPo` / `TrPoDtl`への正式書込み禁止）
- Legacy Stock更新禁止
- Legacy Arrival更新禁止
- 実メール送信禁止
- Legacy Batch変更禁止
- Tempostar / Logizero変更禁止

LegacyはREAD ONLY。Prototype固有データのみPrototype DBへWRITEする。

---

# 1. Architecture

```
┌─────────────────────────┐
│   React Frontend (TS)    │
└────────────┬─────────────┘
             │ REST / JSON
             ▼
┌─────────────────────────────────────────────┐
│   New Service API (Java 21 / Spring Boot 3)   │
│                                               │
│   controller → service → domain              │
└───────────────┬───────────────┬─────────────┘
                │               │
        READ ONLY│               │READ / WRITE
                ▼               ▼
┌───────────────────────┐  ┌─────────────────────┐
│   Legacy Adapter        │  │  Prototype Repository │
│  (JdbcTemplate, 縮小Read │  │  (Spring Data JPA)     │
│   Query + 移植計算ロジック)│  │                      │
└───────────┬─────────────┘  └──────────┬───────────┘
            ▼                            ▼
┌───────────────────────┐  ┌─────────────────────┐
│  Legacy MySQL(READ ONLY)│  │   Prototype DB (RDBMS) │
│  スキーマ名: goo         │  │  Draft / Workflow /   │
│                         │  │  Supplier Response /  │
│                         │  │  Attention / Audit     │
└───────────────────────┘  └─────────────────────┘
```

- FrontendはNew Service APIのみを呼び出す。Legacy DB・Prototype DBへ直接接続しない（要件MD 26章-27, 30.1）。
- Legacy AdapterとPrototype Repositoryは**別DataSource・別TransactionManager**として明確に分離する（10章）。
- New Service APIが唯一の業務窓口であるため、将来のAPI Gateway化・AI Agent追加（要件MD 4章）は「New Service APIの手前にGateway/Agentを挟む」だけで実現可能であり、本設計を阻害しない。

---

# 2. Technology Stack

## 2.1 Frontend

| 項目 | 採用 | 理由 |
|---|---|---|
| Framework | React 18 + TypeScript | 要件MD 2.2で確定済み |
| Build | Vite | 起動・HMRが速く9/17までの反復開発に有利 |
| Routing | React Router | 7画面の単純なRoute構成に十分 |
| Data Fetching | TanStack Query（React Query） | ローディング/エラー状態・再検証を自前実装せず時短 |
| UI Component | MUI（Material UI） | 「デザイン作り込みは不要」という前提のもと、Table/Form/Dialog等を自前CSSなしで即使える。日本語表示・i18nとの親和性も高い |
| i18n | react-i18next | 11章参照 |
| Form/Validation | React Hook Form + Zod | Order Qty等のBusiness Rule Validation（要件MD 13章）をFrontend側でも軽量に再現 |

## 2.2 Backend

| 項目 | 採用 | 理由 |
|---|---|---|
| Language | Java 21 | 要件MD 2.2で確定済み |
| Framework | Spring Boot 3.3系 | 要件MD 2.2で確定済み。Java 21対応、Jakarta EE namespace |
| Web | Spring Web (REST) | |
| Prototype DB Access | Spring Data JPA + Hibernate | Prototype DBはPrototype専用の新規スキーマのため、JPAのメリット（マイグレーション追従、型安全）を素直に享受できる |
| Legacy DB Access | Spring JDBC（`NamedParameterJdbcTemplate`）＋手書きSQL | 5章参照。JPA Entityは使わない |
| Migration | Flyway（Prototype DBのみ。Legacyには一切適用しない） | |
| Auth | Spring Security（Session/Form Login） | 8章参照 |
| その他 | Lombok、Jackson、SLF4J | Legacyと共通の技術で開発者の学習コストを抑える |

## 2.3 Legacy

Existing MySQL（スキーマ名`goo`、READ ONLY接続）。接続情報はLegacyの`application.properties`から実在するJDBC URLパターン（`jdbc:mysql://<host>:3306/goo?useSSL=false&zeroDateTimeBehavior=convertToNull&useUnicode=yes&characterEncoding=UTF-8`）を踏襲するが、**認証情報はPrototype側で新規に払い出したREAD ONLY専用DBユーザーを使う**（Legacyの`application.properties`に平文保存されている既存アカウントを流用しない。5章参照）。

## 2.4 Prototype Database 比較・選定

| 観点 | PostgreSQL | MySQL | H2（Embedded） |
|---|---|---|---|
| NULL/0区別（confirmed_qty要件） | ◎ 標準的にNULL処理が堅牢、CHECK制約が柔軟 | ○ 可能だが、Legacyの`goo`スキーマとの誤接続混同リスクに注意が必要 | ○ 可能 |
| Legacyとの混同リスク | 低（別DB製品のため誤接続に気づきやすい） | **中〜高（同一MySQLのため、接続文字列を誤るとLegacy `goo`へ書き込む事故が起きやすい）** | 低（別プロセス、別ファイル） |
| Spring Boot 3 / JPA親和性 | 高 | 高 | 高（テスト用途は特に強い） |
| Docker/セットアップ手間 | 低（`docker-compose up`1コマンド） | 低（同上） | **最低（インストール不要、ファイルDBで即動作）** |
| 複数人・並行アクセス | 良好 | 良好 | 弱い（デモの同時アクセスが少数なら実用上問題なし） |
| JSON/Snapshot列の扱い | JSONB等が使える（将来Snapshotを構造化したい場合に有利） | JSON型はあるが機能が弱め | 限定的 |
| 将来の本番移行 | 高い（多くの新規Javaプロジェクトの標準） | Legacyと同エンジンだが別スキーマ運用が前提 | 本番非推奨（あくまで検証用） |

### 選定：**PostgreSQL（Docker Composeで1コンテナ起動）**

理由：
1. `confirmed_qty = 0`と`confirmed_qty = null`の区別（要件MD 15章の中核要件）を、CHECK制約・型システムで堅牢に表現できる。
2. **LegacyもMySQLであるため、Prototype DBを同じMySQLにすると「接続文字列の設定ミスで実はLegacyの`goo`スキーマに書き込んでいた」という事故のリスクが構造的に高くなる。** PostgreSQLを採用することで、DB製品レベルで物理的に隔離され、誤接続によるLegacy DB WRITE事故を防止できる（絶対条件の技術的な安全網になる）。
3. `docker-compose up`一発で立ち上がり、過剰なInfrastructure導入には当たらない。
4. H2は初期セットアップの速さでは優位だが、Supplier Response等の複数ユーザー同時操作をデモする可能性、および将来の本開発への接続性を考えるとPostgreSQLを標準とする。

**Fallback**：Docker環境が使えない開発機がある場合に限り、H2（ファイルモード）をローカル開発用プロファイルとして許容する（Flyway/JPAはそのまま両対応可能なスキーマ設計とする）。

---

# 3. Project Structure

## 3.1 Backend

```
backend/
├── pom.xml
└── src/main/java/com/glv/gsysportal/
    ├── GsysPortalApplication.java
    │
    ├── controller/                  # 薄いRESTエントリポイント（9章のAPI群）
    │   ├── DashboardController.java
    │   ├── OrderCandidateController.java
    │   ├── ItemController.java          # SKU Detail用
    │   ├── OrderDraftController.java
    │   ├── OrderPreviewController.java
    │   ├── SupplierResponseController.java
    │   ├── OrderHistoryController.java
    │   └── AttentionController.java
    │
    ├── service/                     # 業務ロジック・Status遷移・Transaction境界（10章）
    │   ├── DashboardService.java
    │   ├── OrderCandidateService.java
    │   ├── OrderDraftService.java
    │   ├── OrderPreviewService.java
    │   ├── DemoSendService.java
    │   ├── SupplierResponseService.java
    │   ├── AttentionService.java
    │   └── AuditEventService.java       # 全Serviceから呼ばれる横断的Audit記録
    │
    ├── domain/                      # Prototype DBのドメインモデル（JPA Entity）
    │   ├── PortalOrder.java
    │   ├── PortalOrderDetail.java
    │   ├── SupplierResponse.java
    │   ├── SupplierResponseDetail.java
    │   ├── OrderAttention.java
    │   ├── AuditEvent.java
    │   ├── PortalUser.java
    │   └── enums/
    │       ├── OrderStatus.java
    │       ├── AttentionType.java
    │       └── AuditEventType.java
    │
    ├── repository/
    │   ├── prototype/                # Spring Data JPA（Prototype DB、READ/WRITE）
    │   │   ├── PortalOrderRepository.java
    │   │   ├── PortalOrderDetailRepository.java
    │   │   ├── SupplierResponseRepository.java
    │   │   ├── OrderAttentionRepository.java
    │   │   └── AuditEventRepository.java
    │   │
    │   └── legacy/                   # JdbcTemplate（Legacy MySQL、READ ONLY厳守）
    │       ├── LegacyStockReadRepository.java   # getBaseStockList/getStockListOrder縮小版
    │       ├── LegacyBrandRepository.java
    │       ├── LegacyItemRepository.java
    │       └── row/                              # RowMapper用DTO（JPA Entity化しない）
    │           ├── LegacyStockRow.java
    │           └── LegacyBrandRow.java
    │
    ├── legacy/                      # Legacyから移植した計算ロジック（一字一句変更禁止ゾーン）
    │   ├── calc/
    │   │   ├── OrderQuantityCalculator.java     # Legacyから verbatim 移植
    │   │   ├── StockCalculationHelper.java      # Legacyから verbatim 移植
    │   │   └── FormulaParser.java               # Legacyから verbatim 移植
    │   └── query/
    │       └── RecommendedQtyReadQuery.sql       # 縮小Read Query（コメントでLegacy原典の行番号を明記）
    │
    ├── dto/                          # API Request/Response契約
    │   ├── request/
    │   └── response/
    │
    ├── mapper/                       # domain ⇄ dto、legacy row ⇄ domain の変換
    │
    ├── config/
    │   ├── LegacyDataSourceConfig.java   # READ ONLY DataSource + TransactionManager
    │   ├── PrototypeDataSourceConfig.java
    │   ├── SecurityConfig.java
    │   └── JacksonConfig.java
    │
    └── exception/
        ├── GlobalExceptionHandler.java
        └── ...（業務例外クラス群）
```

**Legacy Read / Prototype Writeの分離**は、`repository/legacy`（JdbcTemplate、`@Transactional(readOnly = true)`固定）と`repository/prototype`（JPA、通常のRead/Write）というパッケージ単位の物理分離で保証する。`legacy/calc`配下は「移植コード、フォーミュラ変更禁止」であることをパッケージ名とファイル冒頭コメントで明示する。

## 3.2 Frontend

```
frontend/
├── package.json
├── vite.config.ts
└── src/
    ├── app/
    │   ├── App.tsx                 # Router定義
    │   ├── routes.tsx
    │   └── providers/              # QueryClientProvider, i18nProvider, AuthProvider
    │
    ├── features/
    │   ├── dashboard/
    │   ├── candidates/             # Order Candidate List
    │   ├── sku-detail/
    │   ├── draft/                  # Order Draft
    │   ├── po-preview/
    │   ├── supplier-response/
    │   └── history/                # Order History
    │       └── (各featureの中に) pages/ / components/ / hooks/ / api.ts
    │
    ├── shared/
    │   ├── api/                    # axios instance、共通エラーハンドリング
    │   ├── components/             # StatusBadge, AttentionBadge, DataTable, ConfirmDialog等
    │   ├── i18n/
    │   │   └── locales/
    │   │       ├── ja/*.json
    │   │       └── en/*.json       # 9/17時点はプレースホルダーのみ
    │   └── types/
    │
    └── main.tsx
```

---

# 4. Legacy Adapter Technical Design

## 4.1 DataSource / READ ONLY保証

Spring Bootで**2つの独立したDataSource**を構成する（`@Primary`は Prototype 側に付与）。

```java
@Bean
DataSource legacyDataSource() { ... }        // Legacy MySQL, READ ONLY
@Bean
PlatformTransactionManager legacyTransactionManager(DataSource legacyDataSource) { ... }

@Bean @Primary
DataSource prototypeDataSource() { ... }     // Prototype PostgreSQL, R/W
@Bean @Primary
PlatformTransactionManager prototypeTransactionManager(DataSource prototypeDataSource) { ... }
```

READ ONLYの保証は**三段構え**とする（4章冒頭の「絶対条件」を実装レベルで担保するため、単一の対策に依存しない）。

1. **DBユーザー権限（最強の保証）**：Legacy接続用に`GRANT SELECT ON goo.* TO 'gsys_portal_ro'@'%';`のようなSELECT専用ユーザーを新規発行する（既存の`root`/`goo`アカウントは使わない）。INSERT/UPDATE/DELETE/DDL権限を一切与えない。
2. **アプリケーション層**：`repository.legacy`配下の全メソッドに`@Transactional(readOnly = true, transactionManager = "legacyTransactionManager")`を付与。
3. **接続文字列**：JDBC URLに`?allowMultiQueries=false`等を付与し、コネクションプール（HikariCP）は`legacyDataSource`専用に分離し、`prototypeDataSource`のプールと混線しない構成にする。

`[CONFIRMED]`（Implementation Step 0/1で実証済み・必須Safety Ruleとして確定）上記1〜3はすべてIntegration Testで実際に検証した（INSERT/UPDATE/DELETE/DDLの4パターンが拒否されることを確認、4件PASS）。

**Safety Rule（実装中に実際の事故として発生、必須遵守）**：Legacy DataSourceとPrototype DataSourceが同一アプリケーション内に共存する構成では、**`@Primary`のみに依存すると、Legacy Adapter側のBean注入が意図せず`@Primary`側（Prototype）へ解決されてしまう**（Javaのパラメータ名情報がリフレクションで欠落した場合に発生しうる）。実装中に実際にこの誤接続が発生し、Legacy Adapterの`JdbcTemplate`がPostgreSQL（Prototype）を向いてしまう事故を確認した。**Legacy Adapter側の全DataSource注入箇所には、パラメータ名に依存せず明示的な`@Qualifier("legacyDataSource")`を必ず付与すること。**

**Safety Guard（未実装・要対応）**：Application起動時にLegacy DB接続先（JDBC URLのホスト部）を検証し、`localhost` / `127.0.0.1` / Docker Compose内部Service名以外を検出した場合にApplication起動を失敗させるガードを実装することが求められている（Critical Safety Rules、2026-08-27指示）。**本Baseline確定時点ではこのGuardは未実装**であり、Prototype DBへの本格書込み（Draft等）やLegacy接続先を切り替え得る次Step以降の作業に着手する前に実装する必須タスクとして残す（17章E参照）。

## 4.2 Repository構造 / Query配置

- **Legacy EntityはJPA化しない**。理由：(a) HibernateがLegacyスキーマに対して意図せずCascade/Dirty Checking/Flushを行うリスクを排除する、(b) 「縮小Read Query」という要件MD 3.1.1/30.2の方針に対し、1本のSQLファイルとして原典との差分レビューをしやすくする。
- `repository/legacy/row/LegacyStockRow.java`のような**素のDTO（Projection）**へ`RowMapper`でマッピングする。
- `legacy/query/RecommendedQtyReadQuery.sql`に、Legacyの`MsStkRepositoryImpl.getBaseStockList()` / `getStockListOrder()`（Phase 0監査C章）から抽出した必要最小列のSELECTを配置する。ファイル冒頭コメントに「Legacy原典：`MsStkRepositoryImpl.java` 1560-2712行目 / 3642-4737行目を参照して抽出。JOIN条件・倉庫除外条件・Formula取得条件は変更していない」と明記する。

必要列（Phase 0.5監査C章で確定済み、変更しない）：

```sql
-- MS_ITEM: ITEM_CD, BRAND_CD, LEAD_TIME
-- MS_STK : STK_STANDARD, PO_QTY_1..20, ARR_QTY_1..10, SHIP_QTY_1..10,
--          WH1..WH5, WH7..WH9, WH12 の STK_QTY（WH6=Damaged, WH10=Private Auction, WH11=Disposalは除外）
-- MS_FORMULA: FORMULA_11..14（LEFT JOIN ON MS_FORMULA.ID = MS_ITEM.ITEM_CD）
```

## 4.3 Recommended Qty計算ロジック再利用方式

`OrderQuantityCalculator.java` / `StockCalculationHelper.java` / `FormulaParser.java`（計635行）を実ソース確認した結果：

| クラス | 依存 | Java 21互換性 |
|---|---|---|
| `OrderQuantityCalculator` | `java.math.BigDecimal/RoundingMode`, `java.util.List`, SLF4J | ✅ 完全互換（Spring非依存・Java8固有API不使用） |
| `FormulaParser` | `java.util.ArrayList/List`, `java.util.regex.*`, SLF4J | ✅ 完全互換 |
| `StockCalculationHelper` | 上記＋`org.json.simple.JSONObject`（json-simple） | ✅ 互換。唯一の外部依存 |

**結論：`OrderQuantityCalculator`と`FormulaParser`は追加依存なしでJava 21へそのまま移植可能。`StockCalculationHelper`のみ`com.googlecode.json-simple:json-simple`への依存が必要。**

`[PROTOTYPE DECISION]` 3クラスとも**一字一句変更せず(verbatim)コピー**し、`json-simple`（軽量・推移依存なし・MIT系ライセンス）をBackendの依存関係へそのまま追加する。`JSONObject`が`HashMap`を継承する薄いラッパーであるため、`Map`への置き換えも技術的には可能だが、「計算式を勝手に変更しない」という絶対ルールを最も安全に満たすのは**シグネチャも含め一切変更しない**方式であるため、これを採用する。

**確認方法（実装Step 0で実施）**：この3ファイルをそのままJava 21プロジェクトへ配置し、`mvn compile`が通ることを最初の検証ステップとする（14章Test Strategy、13章Implementation Order参照）。

`[CONFIRMED]`（Implementation Step 0/1で実証済み、2026-08-27）上記は実際に検証済みである。`backend/src/main/java/com/glv/gsysportal/legacy/calc/`へ3ファイルをverbatim配置し、Java 21（JDK 23上で`maven.compiler.release=21`指定）でコンパイル成功を確認した。パッケージ宣言以外は一切変更していない。

**訂正**：当初「Legacyの既存`FormulaTest.java`のテストケースをそのまま移植してGolden Testとして使う」という方針であったが、実装時に`jp.ne.glv.utilities.FormulaTest.java`の**全体がコメントアウトされており、かつ内容はcalc4ではなく別クラス（`Formula.java`のPRC_LIST/PRC_SELL等の価格計算）のテストである**ことが判明した。Legacyには**calc4/OrderQuantityCalculatorの既存自動テストは存在しない**。この訂正を踏まえ、Default 4EU式から手計算し、独立したPython実装でクロスチェックした期待値によるGolden Testを新規作成した（Normal / Stock不足 / Stock余剰 / Formula未設定Default / 0値 / Boundary×2 / Open Qty上限ケース、計9件PASS）。詳細は17章参照。

---

# 5. Prototype Database Physical Design

要件MD 29章の論理モデルを、PostgreSQLを前提に物理化する。すべてのタイムスタンプは`TIMESTAMPTZ`。`created_by`/`updated_by`/`performed_by`/`acknowledged_by`は8章`portal_user.username`を参照する文字列（FK制約は付けない＝Prototype User削除時にもAudit記録を保持するため、あえて緩い参照とする）。

## 5.1 `portal_order`

| 列名 | 型 | 制約 |
|---|---|---|
| id | BIGSERIAL | PK |
| draft_no | VARCHAR(30) | NOT NULL, UNIQUE |
| prototype_po_no | VARCHAR(30) | UNIQUE（部分Index `WHERE prototype_po_no IS NOT NULL`）。DRAFT中はNULL、Confirm Order時に採番。`[PROTOTYPE DECISION]`（Implementation Step 3で確定）Postgres SEQUENCE `prototype_po_no_seq`から`nextval()`で採番し、`PO-DEMO-<yyyyMMdd>-<seq、4桁以上0埋め>`形式とする（同時実行下でもCollisionしない）。Return to Draft／再Confirmでは既存値を再利用し、削除・再採番しない |
| supplier_code | VARCHAR(10) | NOT NULL |
| supplier_name_snapshot | VARCHAR(200) | NOT NULL |
| brand_code | VARCHAR(10) | NOT NULL |
| brand_name_snapshot | VARCHAR(200) | NOT NULL |
| order_date | DATE | NOT NULL |
| requested_delivery | DATE | NULL |
| currency | VARCHAR(10) | NULL |
| status | VARCHAR(30) | NOT NULL, CHECK IN ('DRAFT','READY_TO_ORDER','SENT','AWAITING_SUPPLIER','SUPPLIER_CONFIRMED','COMPLETED') |
| remark | TEXT | NULL |
| total_qty | INTEGER | NOT NULL DEFAULT 0 |
| total_amount | NUMERIC(14,2) | NOT NULL DEFAULT 0 |
| data_source | VARCHAR(10) | NOT NULL DEFAULT 'DEMO', CHECK IN ('DEMO','LEGACY')（6章参照） |
| created_by / updated_by | VARCHAR(50) | NOT NULL |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| version | INTEGER | NOT NULL DEFAULT 0（楽観ロック） |

Index：`status`, `supplier_code`, `brand_code`, `order_date`。

## 5.2 `portal_order_detail`

| 列名 | 型 | 制約 |
|---|---|---|
| id | BIGSERIAL | PK |
| portal_order_id | BIGINT | NOT NULL, FK→portal_order(id) ON DELETE CASCADE |
| line_no | INTEGER | NOT NULL |
| sku | VARCHAR(30) | NOT NULL |
| item_name_snapshot | VARCHAR(200) | NOT NULL |
| recommended_qty | INTEGER | NOT NULL（Draft作成時点のcalc4スナップショット、以後不変） |
| order_qty | INTEGER | NOT NULL, CHECK (order_qty >= 0) |
| unit_price | NUMERIC(13,5) | NULL |
| amount | NUMERIC(14,2) | NOT NULL DEFAULT 0（`order_qty * unit_price`、Service層で計算・保存） |
| current_stock_snapshot | INTEGER | NULL |
| safety_stock_snapshot | INTEGER | NULL |
| open_po_snapshot | INTEGER | NULL |
| recent_sales_snapshot | INTEGER | NULL（＝当月販売数のスナップショット） |
| lead_time_snapshot | VARCHAR(10) | NULL |
| item_status_snapshot | VARCHAR(30) | NULL |
| data_source | VARCHAR(10) | NOT NULL DEFAULT 'DEMO', CHECK IN ('DEMO','LEGACY') |
| is_removed | BOOLEAN | NOT NULL DEFAULT false（"Remove Item"は物理削除せずSoft Delete、要件MD 13章の実装設計事項をここで確定） |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

Unique：`(portal_order_id, line_no)`。Index：`sku`, `portal_order_id`。

## 5.3 `supplier_response`

`[PROTOTYPE DECISION]`（本書で確定）Supplier Responseの複数回更新は、**「1 Order＝1 supplier_responseヘッダー行を最新値として上書き更新」＋「変更履歴は`audit_event`から再構成」**という方式で実現する（append-onlyの版管理テーブルは9/17時点では作らない）。理由：Order Draft / PO Preview / Supplier Response画面はいずれも「現在値」しか必要とせず、履歴表示はOrder History画面が担当し、そこは元々`audit_event`を参照する設計のため、二重に履歴を持つ必要がない。

| 列名 | 型 | 制約 |
|---|---|---|
| id | BIGSERIAL | PK |
| portal_order_id | BIGINT | NOT NULL, UNIQUE, FK→portal_order(id)（1 Order = 1 現在Responseヘッダー） |
| response_date | DATE | NULL |
| response_note | TEXT | NULL |
| response_status | VARCHAR(20) | NOT NULL DEFAULT 'PARTIAL', CHECK IN ('PARTIAL','CONFIRMED') |
| received_by | VARCHAR(50) | NULL |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

## 5.4 `supplier_response_detail`

| 列名 | 型 | 制約 |
|---|---|---|
| id | BIGSERIAL | PK |
| supplier_response_id | BIGINT | NOT NULL, FK→supplier_response(id) ON DELETE CASCADE |
| portal_order_detail_id | BIGINT | NOT NULL, FK→portal_order_detail(id) |
| ordered_qty | INTEGER | NOT NULL（Demo Send時点のorder_qtyをOriginalとしてコピー、以後不変） |
| confirmed_qty | INTEGER | **NULL許容、DEFAULT指定なし**（NULL＝未回答、0＝正式に0回答。要件MD 15章の中核要件） |
| requested_delivery | DATE | NULL（Original、以後不変） |
| confirmed_delivery | DATE | NULL |
| response_note | TEXT | NULL |
| is_confirmed | BOOLEAN | NOT NULL DEFAULT false（当該明細の回答完了フラグ） |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

Unique：`(supplier_response_id, portal_order_detail_id)`。

**API契約上の注意**（9章とも連動）：`confirmed_qty`をPATCH/PUTする際、JSONの`null`と「フィールド省略」を区別できるDTO実装（例：`Optional<Integer>`や`JsonNullable<Integer>`）を用いる。「フィールド省略＝変更なし」「`null`送信＝未回答へ明示的にクリア」を区別しないと、0と未回答の取り違えが発生し得る。

## 5.5 `order_attention`

| 列名 | 型 | 制約 |
|---|---|---|
| id | BIGSERIAL | PK |
| portal_order_id | BIGINT | NOT NULL, FK→portal_order(id) |
| portal_order_detail_id | BIGINT | NULL, FK→portal_order_detail(id)（Order単位のAttentionも許容するためNULL可） |
| attention_type | VARCHAR(30) | NOT NULL, CHECK IN ('QUANTITY_CHANGED','DELIVERY_CHANGED','PARTIAL_CONFIRMATION','DATA_OUTDATED','OTHER_ATTENTION') |
| is_active | BOOLEAN | NOT NULL DEFAULT true |
| detected_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| resolved_at | TIMESTAMPTZ | NULL |
| acknowledged_by | VARCHAR(50) | NULL |
| acknowledged_at | TIMESTAMPTZ | NULL |
| note | TEXT | NULL |

**重複ACTIVE防止**：`CREATE UNIQUE INDEX ON order_attention (portal_order_id, COALESCE(portal_order_detail_id, -1), attention_type) WHERE is_active = true;`（部分Unique Indexで、同一Order/Detail/Typeの二重ACTIVE行を防止しつつ、解消後の再発生は新規行として許容する）。`[CONFIRMED]`（Implementation Step 4実装時に判明・修正）`portal_order_detail_id`を`COALESCE`せず素の列のまま部分Unique Indexに含めると、PostgresはNULLを「互いに異なる値」として扱うため、`portal_order_detail_id IS NULL`のOrder単位Attention（`PARTIAL_CONFIRMATION`）については同一Order・同一Typeで複数ACTIVE行が重複挿入され得るという正当性上の欠陥が当初案にあった。`COALESCE(portal_order_detail_id, -1)`でNULLを固定値に正規化することでOrder単位のAttentionも正しく重複防止される（`V7__supplier_response_and_attention.sql`）。

## 5.6 `audit_event`

| 列名 | 型 | 制約 |
|---|---|---|
| id | BIGSERIAL | PK |
| portal_order_id | BIGINT | NOT NULL, FK→portal_order(id) |
| portal_order_detail_id | BIGINT | NULL, FK→portal_order_detail(id) |
| event_type | VARCHAR(30) | NOT NULL, CHECK IN ('ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED','SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED','ATTENTION_ADDED','ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED','ORDER_DATE_CHANGED','ORDER_RETURNED_TO_DRAFT')（Implementation Step 2でSave Draft用の3種、Step 3でReturn to Draft用の`ORDER_RETURNED_TO_DRAFT`を追加。実装は`V6__po_preview_and_confirm.sql`でCHECK制約をALTER） |
| field_name | VARCHAR(50) | NULL |
| old_value | VARCHAR(500) | NULL |
| new_value | VARCHAR(500) | NULL |
| performed_by | VARCHAR(50) | NOT NULL |
| performed_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| note | TEXT | NULL |

Index：`(portal_order_id, performed_at)`。**Append-Only**とし、アプリケーションDBロールに対して`UPDATE`/`DELETE`権限を付与しない（audit_eventテーブル限定でGRANTを絞る）ことで、改ざん防止を担保する。

## 5.7 `portal_user`（8章 Authentication用）

| 列名 | 型 | 制約 |
|---|---|---|
| id | BIGSERIAL | PK |
| username | VARCHAR(50) | NOT NULL, UNIQUE |
| display_name | VARCHAR(100) | NOT NULL |
| password_hash | VARCHAR(200) | NOT NULL（BCrypt） |
| role | VARCHAR(30) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

---

# 6. Demo Data Architecture

## 6.1 前提（Phase 0.5監査結果）

`goo_dummy_dumpfile.sql`のみでは9/17デモを構成できない（要件MD 8章・32章）。一方で、**Recommended Qtyは固定値ではなく、可能な限りLegacyの実Formula計算を通して算出したい**という要求がある。

## 6.2 方式：Legacy Demo Instance（新規提案・要確認事項）

`[PROTOTYPE DECISION（要確認）]` 以下の方式を提案する。**この方式は「共有中のLegacy実インスタンス」ではなく、Prototype専用の使い捨てMySQLインスタンスを対象とするため絶対条件（Legacy DB WRITE禁止）には抵触しないという解釈に基づく。この解釈自体は実装着手前にTechlead / 顧客へ確認することを推奨する（15章Riskにも記載）。**

`[CONFIRMED]`（Implementation Step 0/1で訂正・確定、2026-08-27）**下記の「goo_dummy_dumpfile.sqlのCREATE TABLE文をそのまま利用」という当初方針は実装時に破棄した。** 実際にDDLを突き合わせた結果、`goo_dummy_dumpfile.sql`のCREATE TABLE定義（`ms_item`, `ms_stk`等）は**現行の`MsItem.java`/`MsStk.java`エンティティと一致しない旧Schema**であることが判明したため（例：`ms_item`にBRAND_CD/LEAD_TIMEが無い、`ms_stk`にSTK_STANDARD/SOLD_QTYが無く`PO_QTY_1..6`/`ARR_QTY_1..6`しか無い等）。以後、`goo_dummy_dumpfile.sql`を現行Schemaの正本として扱わない。実装では代わりに**現行JPA Entityの`@Column`定義を根拠に、必要最小限の列で新規にDDLを構築**した（`backend/demo-data/01-schema.sql`）。

```
Legacy Demo Instance（Docker, MySQL, 使い捨て・Prototype専用）
  ├─ Schema: 現行JPA Entity（MsItem.java/MsStk.java/MsComm.java/TrPo.java/TrPoDtl.java）の
  │          @Column定義を根拠に新規構築（goo_dummy_dumpfile.sqlのDDLは不採用、上記参照）
  │    実装済み：ms_item, ms_stk, ms_comm, ms_formula, tr_po, tr_po_dtl（Order Candidate List
  │             + Recommended Qtyに必要な最小テーブルのみ。tr_arr等その他は次Step以降で追加）
  └─ Seed: demo-data/01-schema.sql + 02-seed.sql + 03-readonly-user.sql（実装済み）
       `[CONFIRMED]`（Implementation Step 0/1実績）
       - MS_COMM: CATE_ID='MS_BRAND' / 'MS_SUPPL'（正しいCATE_ID。goo_dummy_dumpfile.sqlの
         'BRAND'/'SUPPLIER'という不一致は再現していない。Phase 0.5監査Q章で確認済みのバグを踏襲しない）
       - MS_ITEM: 3 Brand（BR_OUTDOOR/BR_HOME/BR_KITCHEN）× 計19 SKU
       - MS_STK: WH_CD='XX'集計行（STK_STANDARD/SOLD_QTY/PO_QTY_1/ARR_QTY_1）＋WH_CD='01'物理行（STK_QTY）
       - TR_PO / TR_PO_DTL: 19件のPO実績を投入（Unit Price/Currency/Supplier導出のため。7章参照）
       - MS_FORMULA: 1 SKU（OD-TENT-001）のみ投入し、Item別Formula経路を実証。残り18 SKUは
         LegacyのハードコードDefault式（DEFAULT_FORMULA_11_4EU等、Phase 0監査C章で確認済みの
         実コードパス）でcalc4を算出する（Formula未設定Itemへの正規のフォールバック挙動）。
       - **文字化け対策**：`02-seed.sql`冒頭に`SET NAMES utf8mb4;`を追加。Docker MySQL初期化スクリプトが
         非対話的に実行される際、これが無いと日本語がSeed投入時点で二重エンコード破損することを
         実装中に発見・修正した（17章F参照）。
```

New Service APIの`legacy.datasource.url`設定を、環境（プロファイル）ごとにこのDemo Instanceまたは（将来）本物のLegacy共有インスタンスへ切り替えるだけで、**Legacy Adapterのコードは一切変更しない**。つまり9/17デモで表示されるRecommended Qtyは、正真正銘Legacyの`calc4`実装を通した値になる。

## 6.3 Data Source識別

- Legacy Adapterから見ると、Demo Instanceも将来の本物Legacy共有インスタンスも「Legacy形式のMySQL」という点で同一であり、コード上の分岐は存在しない（環境変数のみで切替）。
- Prototype DB側（`portal_order_detail.data_source`等）の`DEMO`/`LEGACY`列は、「このスナップショットがどの接続プロファイルから取得されたものか」を記録する目的で保持し、将来Legacy共有インスタンスへ切り替えた際に、過去のDraft/Orderが「Demo Instance時代のデータを参照していた」ことを区別できるようにする。
- 9/17画面上では、Demo環境で動作していることが分かるよう、Dashboardまたはヘッダーに小さく「Demo Environment」等の表示を出すことを推奨する（詳細は12章）。

## 6.4 Seed方式

`demo-data/01-schema.sql` / `02-seed.sql` / `03-readonly-user.sql`をDocker Composeの`docker-entrypoint-initdb.d/`に配置し、Legacy Demo Instanceコンテナ起動時にアルファベット順で自動投入する（`[CONFIRMED]`実装済み）。`02-seed.sql`冒頭で`SET NAMES utf8mb4;`を明示しないと、非対話的な初期化スクリプト実行時に日本語が文字化けして保存される事象を確認済み（6.2節参照）。Prototype DB側は別途Flyway（`V2__demo_seed.sql`等、本番マイグレーションとは明確にファイル名/コメントで区別）でPortal User（8章）などの最小限のSeedのみ投入する（Draft/Order等の業務データはSeedしない。これらはデモ操作そのもので作られるべきデータのため。Flyway自体はStep 0/1時点では`spring.flyway.enabled=false`とし、Prototype業務テーブル導入時に有効化する）。

---

# 7. Authentication

`[PROTOTYPE DECISION]` **Spring Security セッションベースForm Login＋Prototype DB内`portal_user`テーブル**を採用する。

- SSO/OAuth/JWT等は9/17には過剰。単一アプリケーション・少人数デモのため不要。
- Legacyの`MS_USER`／Spring Security JDBC認証は**再利用しない**（Legacy側の認証情報・権限体系に一切依存しない。要件MD 26章「Legacy Business Ruleを勝手に再実装しない」はここでは適用対象外＝そもそも認証はNew Portal固有の新規要件であるため）。
- `portal_user`テーブル（5.7）に3〜5件のデモアカウント（例：購買担当、営業管理、System Admin相当）をSeedする。パスワードはBCryptハッシュ化。
- ログイン中のユーザー名（`Authentication.getName()`）を`created_by`/`updated_by`/`performed_by`/`acknowledged_by`へ設定するユーティリティ（`CurrentUserProvider`）をService層共通で使う。
- 「名前を選ぶだけでパスワード無し」という簡易案も検討したが、顧客デモにおけるAudit Trailの説得力（「誰が」を実認証で裏付ける）を優先し、簡易Form Loginを採用する。工数増分は軽微（Spring Security標準機能のみ）。

---

# 8. API Physical Design

要件MD 30章を実装可能な形へ落とす。**汎用Status変更APIは作らない**（30.11で確定済み）。全エンドポイントは`/api`配下、認証必須（`/api/auth/**`除く）。

| # | 業務Action | Method | Path | Legacy Read / Prototype Write | Status遷移 |
|---|---|---|---|---|---|
| 1 | Dashboard取得 | GET | `/api/dashboard` | Legacy Read＋Prototype Read（集計） | - |
| 2 | Brand別集計 | GET | `/api/dashboard/brands` | 同上 | - |
| 3 | 発注候補一覧 | GET | `/api/order-candidates` | Legacy Read | - |
| 4 | SKU発注コンテキスト | GET | `/api/items/{sku}/ordering-context` | Legacy Read | - |
| 5 | **Create Draft** | POST | `/api/orders/drafts` | Legacy Read（Snapshot取得）→Prototype Write | (無し)→`DRAFT` |
| 6 | **Save Draft** | PUT | `/api/orders/drafts/{id}` | Prototype Write | `DRAFT`維持 |
| 7 | Add Item | POST | `/api/orders/drafts/{id}/items` | Legacy Read→Prototype Write | `DRAFT`維持 |
| 8 | Remove Item | DELETE | `/api/orders/drafts/{id}/items/{detailId}` | Prototype Write（is_removed=true） | `DRAFT`維持 |
| 9 | **PO Preview** | POST | `/api/orders/drafts/{id}/preview` | Prototype Read＋Validation | 変更なし |
| 10 | **Confirm Order** | POST | `/api/orders/drafts/{id}/confirm` | Prototype Write | `DRAFT`→`READY_TO_ORDER` |
| 11 | **Return to Draft** | POST | `/api/orders/{id}/return-to-draft` | Prototype Write | `READY_TO_ORDER`→`DRAFT` |
| 12 | **Demo Send** | POST | `/api/orders/{id}/demo-send` | Prototype Write | `READY_TO_ORDER`→`SENT`→`AWAITING_SUPPLIER` |
| 12a | Supplier Response取得 | GET | `/api/orders/{id}/supplier-response` | Prototype Read | - |
| 13 | **Save Supplier Response** | PUT | `/api/orders/{id}/supplier-response` | Prototype Write | `AWAITING_SUPPLIER`維持 |
| 14 | **Confirm Supplier Response** | POST | `/api/orders/{id}/supplier-response/confirm` | Prototype Write | `AWAITING_SUPPLIER`→`SUPPLIER_CONFIRMED` |
| 15 | Acknowledge Attention | POST | `/api/attentions/{id}/acknowledge` | Prototype Write | Attention `ACTIVE`→解消 |
| 16 | Order History一覧 | GET | `/api/orders/history` | Prototype Read | - |
| 17 | Order詳細 | GET | `/api/orders/{id}` | Prototype Read | - |
| 18 | Order Event(Timeline) | GET | `/api/orders/{id}/events` | Prototype Read | - |

Implementation Step 4で#1-4（Dashboard/SKU発注コンテキスト）・#7-8（Add Item/Remove Item）・#15（Acknowledge Attention）を除く全Actionを実装・実証済み（13章Test Strategy参照）。#15は要件MD 28章の通りCore Workflow完成を優先し9/17は見送った（`QUANTITY_CHANGED`/`DELIVERY_CHANGED`はACTIVEのまま残り、History/Supplier Response画面でBadge表示のみ行う）。

## 8.1 代表エンドポイント詳細

### `POST /api/orders/drafts`（Create Draft）

- Request: `{ "skus": ["SKU-A","SKU-B"], "supplierCode": "SUP1", "brandCode": "BR1", "orderDate": "2026-09-17" }`
- 処理：`skus`をLegacy Adapterへ渡しRecommended Qty等をREAD → Snapshot化 → `portal_order` + `portal_order_detail`をINSERT（`order_qty = recommended_qty`を初期値、要件MD 13章）→ `audit_event(ORDER_DRAFT_CREATED)`
- Validation：同一Supplierでない複数SKUが混在する場合は拒否 or Supplier単位に自動分割（`[TBD - CUSTOMER REVIEW]` 分割仕様は要件MD 11章の通り未確定のため、9/17は**単一Supplierのみ許可**し、混在時は400 Bad Requestとする簡易実装で妥協する）
- Response: `201 Created` + Draft詳細
- Error: `400`（Order Qty不正、Supplier混在）、`404`（SKU不明）

### `POST /api/orders/drafts/{id}/preview`（Implementation Step 3で確定）

- Requestなし（Bodyを持たない）。Prototype DBの保存済み`portal_order`/`portal_order_detail`のみを正本として使用し、Frontendから送られた値は一切参照しない。
- Validation（Error時は内部Codeのみ返し、日本語文言はFrontend i18nで解決）：

  | Error Code | HTTP | 条件 |
  |---|---|---|
  | `DRAFT_NOT_FOUND` | 404 | 対象Order不在 |
  | `NO_ORDERABLE_ITEMS` | 400 | is_removed=falseかつorder_qty > 0の行が0件 |
  | `MISSING_UNIT_PRICE` | 400 | 上記のorderable行にunit_priceがNULLの行が1件以上 |
  | `INVALID_ORDER_STATUS` | 400 | Status が `DRAFT`/`READY_TO_ORDER` 以外 |

  `[PROTOTYPE DECISION]`（Step 3実装時に拡張、Step 4で確定事項として整理）Status = `DRAFT`のみを許可する当初想定から、`READY_TO_ORDER`も許可する形へ拡張した。Confirm Order成功直後、FrontendがStatus/Prototype PO No.を反映した同じPreview画面を再取得する必要があるため（31.3の一連の流れ、この直後の「READY_TO_ORDER」表示要件を参照）。本Endpointは常にREAD ONLYであり（Status変更・Audit追加・PO No.再採番・DB更新を一切行わない）、業務要件ではなくPrototype固有のTechnical Decisionであるため、要件MD 27.4の顧客確認事項リストからは除外した（要件MD 27.3のRESOLVED一覧を参照）。
- 処理：is_removed=falseかつorder_qty > 0の行のみを抽出し、`amount = unit_price × order_qty`をService層で計算、`skuCount`/`totalQty`/`totalAmount`をこの抽出行から再計算（`portal_order`の保存済み合計は全行対象のため信用しない）。
- Response DTO（`PoPreviewResponse`）はCandidate/Draft DTOを再利用せず専用に新規作成し、Recommended Qty・Current Stock・Safety Stock・当月販売数・Formula・Item Status等の社内判断情報を一切含めない（要件MD 31.2）。Manufacturer Communication（to/cc/subject/body/attachment）は9/17は固定Demo値のみ（`DemoManufacturerCommunicationFactory`）で、`to`/`cc`はRFC 2606の`.invalid`ドメインを使用し実在しないアドレスとする。`demoMode: true`を常に含める。

### `POST /api/orders/drafts/{id}/confirm`（Implementation Step 3で確定）

- Requestなし。`DRAFT → READY_TO_ORDER`。
- Validation：Statusが`DRAFT`であること（それ以外は`409 INVALID_STATUS_TRANSITION` - 二重Confirmに対する冪等性の要）。上記PO PreviewのValidationを同一実装（`PoPreviewValidator`）で再実行する。
- 処理：`prototype_po_no`が未採番（NULL）の場合のみPostgres SEQUENCE `prototype_po_no_seq`から新規採番。既に採番済み（過去にConfirm→Return to Draftを経た再Confirm）の場合は既存値をそのまま再利用し、新規採番しない（5.1参照）。Statusを`READY_TO_ORDER`へ更新し、`audit_event(ORDER_READY)` + `audit_event(STATUS_CHANGED, old=DRAFT, new=READY_TO_ORDER)`を同一トランザクションで保存。
- Response：`{ id, draftNo, prototypePoNo, status, updatedBy, updatedAt }`（`OrderStatusChangeResponse`）。Frontendは成功後にPO Previewを再取得して画面を更新する。

### `POST /api/orders/{id}/return-to-draft`（Implementation Step 3で確定）

- Requestなし。`READY_TO_ORDER → DRAFT`のみ許可（それ以外は`409 INVALID_STATUS_TRANSITION`）。
- 処理：`prototype_po_no`は変更しない（削除・再採番しない）。Statusを`DRAFT`へ戻し、`audit_event(STATUS_CHANGED, old=READY_TO_ORDER, new=DRAFT)` + `audit_event(ORDER_RETURNED_TO_DRAFT)`を同一トランザクションで保存。
- 副作用：以後`PUT /api/orders/drafts/{id}`（Save Draft）が再び許可される。`READY_TO_ORDER`中のSave Draftは`409 ORDER_NOT_EDITABLE`。

### `POST /api/orders/{id}/demo-send`（Implementation Step 4で確定）

- Requestなし。`READY_TO_ORDER`のみ許可（それ以外は`409 INVALID_STATUS_TRANSITION`）。
- 処理：同一Transaction内で`READY_TO_ORDER → SENT`（`STATUS_CHANGED`）→`DEMO_SENT`→`SENT → AWAITING_SUPPLIER`（`STATUS_CHANGED`）の順にAuditを記録し、`portal_order.status`を最終的に`AWAITING_SUPPLIER`へ更新。続けて`supplier_response`（1件）＋`supplier_response_detail`（非削除明細ごとに1件）を初期化する：`ordered_qty`＝この時点の`order_qty`、`requested_delivery`＝この時点の`portal_order.requested_delivery`をSnapshotし、`confirmed_qty`はNULLのまま（初期化のみで`audit_event`は発生させない）。
- SMTP Client / JavaMail等の外部Mail送信コードはこのメソッド、および本アプリケーション全体に一切存在しない。

### `GET /api/orders/{id}/supplier-response`（Implementation Step 4で確定）

- `AWAITING_SUPPLIER`または`SUPPLIER_CONFIRMED`のみ許可（それ以外は`400 INVALID_ORDER_STATUS`）。
- Response（`SupplierResponseView`）：Header（orderId/draftNo/prototypePoNo/supplier/brand/orderDate/status/totalOrderedQty/totalAmount）、Response Header（responseDate/responseNote/responseStatus）、Detail（`detailId`＝`supplier_response_detail.id`、sku/itemName/orderedQty/confirmedQty/requestedDelivery/confirmedDelivery/responseNote/isConfirmed/attentionTypes/warningCodes）、Order-level`orderAttentionTypes`、`summary`（totalCount/answeredCount/unansweredCount/quantityChangedCount/deliveryChangedCount/zeroQtyCount、Backendで都度再計算）。

### `PUT /api/orders/{id}/supplier-response`（Implementation Step 4で確定）

- Request: `{ "responseDate": "2026-09-18", "responseNote": "...", "details": [ { "detailId": 1, "confirmedQty": 9, "confirmedDelivery": "2026-09-26", "responseNote": "..." }, { "detailId": 2, "confirmedQty": null, "confirmedDelivery": null } ] }`（`detailId`は`supplier_response_detail.id`。当初案の`portalOrderDetailId`ではなく、GET/PUTで一貫してこのidを使う）
- `AWAITING_SUPPLIER`のみ許可（`SUPPLIER_CONFIRMED`は`409 INVALID_STATUS_TRANSITION`で編集拒否）。
- **`details`に含まれない行は変更しない（Partial Save）。含まれる行の`confirmedQty`は常にその行の完全な意図値**：`null`＝明示的に未回答（クリア）、任意の整数（0含む）＝実回答。フィールド省略ではなく値そのもので意味を持たせる（実装は`SaveSupplierResponseRequest.LineUpdate`が両フィールドとも必須で受け取る設計）。
- 処理：行ごとに旧値と比較し、`confirmedQty`が変化していれば`audit_event(QUANTITY_CHANGED)`、`confirmedDelivery`が変化していれば`audit_event(DELIVERY_CHANGED)`を記録（値が変化していないSaveはAuditを追加しない）。`confirmedQty`が非nullかつ`orderedQty`と異なる場合、`order_attention(QUANTITY_CHANGED)`をACTIVEで作成（既にACTIVEなら重複作成しない、部分Unique Indexで保証）。`confirmedDelivery`が非nullかつ`requestedDelivery`と異なる場合も同様に`order_attention(DELIVERY_CHANGED)`。全行の`confirmedQty`が非nullになったら`order_attention(PARTIAL_CONFIRMATION)`を自動解消（`audit_event(ATTENTION_RESOLVED)`）、そうでなければ未作成時のみ新規作成（`audit_event(ATTENTION_ADDED)`）。
- Validation：`confirmedQty >= 0`（`400 INVALID_CONFIRMED_QTY`）。`confirmedQty > orderedQty`はErrorにせず、Response側で`CONFIRMED_QTY_EXCEEDS_ORDERED_QTY` Warning Codeを返す（要件MD 12章）。
- Status：`AWAITING_SUPPLIER`のまま。

### `POST /api/orders/{id}/supplier-response/confirm`（Implementation Step 4で確定）

- `AWAITING_SUPPLIER`のみ許可（それ以外は`409 INVALID_STATUS_TRANSITION` - 二重Confirmに対する冪等性の要）。
- Validation：`[PROTOTYPE DECISION]`暫定完了条件は「非削除の全`supplier_response_detail`で`confirmedQty`が非null」のみ。`confirmedDelivery`は必須としない。未充足なら`400 SUPPLIER_RESPONSE_INCOMPLETE`。正式条件は`[TBD - CUSTOMER REVIEW]`（要件MD 27.4）。
- 処理：`supplier_response.response_status: PARTIAL → CONFIRMED`、`portal_order.status: AWAITING_SUPPLIER → SUPPLIER_CONFIRMED`、`audit_event(SUPPLIER_RESPONSE_RECEIVED)` + `audit_event(STATUS_CHANGED)`を同一Transactionで記録。

---

# 9. Transaction Design

`[PROTOTYPE DECISION]` **1業務Action = 1 `@Transactional`メソッド**を徹底する。Status変更・Detail更新・Attention生成・Audit記録は必ず同一トランザクション内で完結させ、途中失敗時は全てロールバックする（「Statusだけ変わってAuditが残らない」等の中途半端な状態を防止）。

```java
@Transactional(transactionManager = "prototypeTransactionManager")
public SupplierResponseResult saveSupplierResponse(Long orderId, SupplierResponseRequest req) {
    // 1. supplier_response / supplier_response_detail upsert
    // 2. 差分検出 → order_attention upsert
    // 3. audit_event insert（差分1件ごと）
    // すべて成功して初めてcommit。一部失敗時は全ロールバック。
}
```

**Legacy DBとのDistributed Transactionは作らない**（絶対条件）。Legacy Readが必要な操作（Create Draft、Add Item等）は、

```
Step A: Legacy Adapter（legacyTransactionManager, readOnly=true）でSnapshotデータを取得（読み取りのみで完結）
Step B: 取得したSnapshotを引数に、Prototype側の書込みトランザクション（prototypeTransactionManager）を開始・完結
```

という**2段階の別トランザクション**として明確に分離する。Step AとStep Bをまたぐ整合性は、「Step Aで取得した値をStep Bへそのままパラメータとして渡す」というアプリケーションコードの単純な受け渡しで担保し、XA/2相コミット等は導入しない。

各業務Actionのトランザクション境界：

| Action | トランザクション内容 |
|---|---|
| Create Draft | portal_order INSERT + portal_order_detail INSERT(複数) + audit_event(ORDER_DRAFT_CREATED) |
| Save Draft | portal_order/detail UPDATE + audit_event(ORDER_QTY_CHANGED等、変更フィールドごと) |
| Confirm Order | status更新 + prototype_po_no採番（初回のみ、既存値があれば再利用） + audit_event(ORDER_READY, STATUS_CHANGED) |
| Return to Draft | status更新（prototype_po_noは変更しない） + audit_event(STATUS_CHANGED, ORDER_RETURNED_TO_DRAFT) |
| Demo Send | status更新(READY_TO_ORDER→SENT→AWAITING_SUPPLIER) + supplier_response/supplier_response_detail初期化 + audit_event(STATUS_CHANGED×2, DEMO_SENT) |
| Save Supplier Response | supplier_response_detail更新（差分がある行のみ） + order_attention作成/自動解消（重複防止） + audit_event(変更フィールドごとにQUANTITY_CHANGED/DELIVERY_CHANGED、ATTENTION_ADDED/ATTENTION_RESOLVED) |
| Confirm Supplier Response | supplier_response.response_status更新 + portal_order.status更新 + audit_event(SUPPLIER_RESPONSE_RECEIVED, STATUS_CHANGED) |
| Acknowledge Attention | `[Step 4未実装]` order_attention更新 + audit_event(ATTENTION_RESOLVED) - Core Workflow完成を優先し9/17は見送り |

---

# 10. i18n

`[PROTOTYPE DECISION]` **react-i18next**を採用する。

```
frontend/src/shared/i18n/
├── index.ts                # i18next初期化（デフォルト言語=ja）
└── locales/
    ├── ja/
    │   ├── common.json
    │   ├── dashboard.json
    │   ├── candidates.json
    │   ├── draft.json
    │   ├── poPreview.json
    │   ├── supplierResponse.json
    │   ├── history.json
    │   ├── status.json         # 要件MD 28.3のMapping
    │   ├── attention.json      # 要件MD 28.5のMapping
    │   └── validation.json
    └── en/
        └── common.json         # 9/17時点はTODOプレースホルダーのみ
```

- Backend APIは内部Code（英語）のみ返却し、日本語表示文言は一切返さない（要件MD 30.12で確定済み）。
- Status/Attentionの表示変換は`status.json`/`attention.json`に要件MD 28.3〜28.5の対応表をそのまま格納する。
- 9/17時点ではjaリソースのみ完成させればよい（要件MD 28.2）。enディレクトリの存在自体が「将来拡張を阻害しない」ことの担保になる。

---

# 11. Frontend UI Technical Structure

| 画面 | Route | 主なAPI | 備考 |
|---|---|---|---|
| Dashboard | `/` | `GET /api/dashboard`, `/api/dashboard/brands` | KPIクリックで各Filter付きRouteへ遷移 |
| Order Candidate List | `/candidates` | `GET /api/order-candidates` | Brand/Supplier等のQuery Param、複数選択→Create Draft |
| SKU Detail | `/items/:sku` | `GET /api/items/:sku/ordering-context` | 参照専用 |
| Order Draft | `/orders/drafts/:id` | Draft系API群 | Recommended Qty(readonly) / Order Qty(editable) |
| PO Preview | `/orders/drafts/:id/preview` | Preview/Confirm/Demo Send API | 原則READ ONLY |
| Supplier Response | `/orders/:id/supplier-response` | Supplier Response API群 | |
| Order History | `/orders/history`, `/orders/:id` | History API群 | READ ONLY |

- Component構成の作り込みは行わず、MUIの`DataGrid`/`Table`/`Dialog`/`Stepper`等を素直に利用する。
- `shared/components/StatusBadge`、`AttentionBadge`はStatus/Attention Enumを受け取り、i18nキー変換込みで表示する共通部品として最初に作る（複数画面で即再利用できるため優先度高）。
- Demo環境である旨のバナー（6.3節）はAppShell（共通Header）に1箇所実装する。

---

# 12. Implementation Order（Vertical Slice）

「全画面を作って最後に接続する」進め方は禁止。**Legacy Read → Recommended Qty → Candidate → Draft → Preview → Demo Send → Supplier Response → Audit/History**の一本を最短で通す。

| Step | 内容 | 目的・ゴール |
|---|---|---|
| 0 | `OrderQuantityCalculator`/`StockCalculationHelper`/`FormulaParser`をJava 21プロジェクトへverbatim配置し`mvn compile`成功を確認 | 4.3節の前提を実証（半日以内で判明する高速スパイク） |
| 1 | Legacy Adapter（単一SKU）実装＋Legacy Demo Instance接続確認 | Legacy READ ONLY接続の疎通、`calc4`が期待値と一致することを確認（Golden Test、13章） |
| 2 | `GET /api/items/{sku}/ordering-context` バックエンドのみE2E | Recommended Qty計算の全経路を1エンドポイントで確認 |
| 3 | `GET /api/order-candidates` ＋ Frontend最小ページ | Frontend-Backend疎通、認証の仮配線含む |
| 4 | Prototype DB Flyway Migration ＋ Create Draft API ＋ Draft画面 | 初めてのPrototype DB書込み経路を実証 |
| 5 | PO Preview ＋ Confirm Order | Status遷移(DRAFT→READY_TO_ORDER)を実証 |
| 6 | Demo Send | Status遷移(→SENT→AWAITING_SUPPLIER)、Mail Previewダミー表示 |
| 7 | Supplier Response 保存・確定 | 2本目の書込み経路、Attention生成ロジックを実証 |
| 8 | Order History / Audit Timeline | audit_eventの参照、これまでの全操作が追跡できることを確認 |
| 9 | SKU Detail画面 | Step2のAPIを流用するだけなので低リスク、任意のタイミングで挿入可 |
| 10 | Dashboard | 他画面すべてに依存するため意図的に最後 |
| 11 | Authentication本配線（Step4以降並行可） | created_by等の実値化 |
| 12 | i18nリソース仕上げ・9/17 Demo Scenarioリハーサル | 20章Demo Scenarioの通しE2Eテスト |

---

# 13. Test Strategy

| レベル | 手法 | 対象 |
|---|---|---|
| Unit Test | JUnit 5 | `OrderQuantityCalculator`/`FormulaParser`。**`[CONFIRMED]`（Implementation Step 0/1で訂正）Legacyの`FormulaTest.java`は全体がコメントアウトされ、かつcalc4ではなく別クラス（`Formula.java`の価格計算）のテストであり利用不可と判明した。代わりに独立検証済みの期待値によるGolden Testを新規作成し実装済み（9件PASS、`OrderQuantityCalculatorGoldenTest.java`）** |
| Backend Integration Test | Testcontainers（PostgreSQL＋MySQL） | Legacy Adapterの縮小Read Queryが既知の入力に対し期待通りの`calc4`を返すか。Prototype RepositoryのCRUD |
| API Test | `@SpringBootTest` + MockMvc/RestAssured | 8章の全Endpoint、特にStatus遷移の正常系・異常系（飛び越し禁止等） |
| Frontend | Vitest + React Testing Library | Order Qty Validation、Confirmed Qty null/0表示の見分け等の重要ロジック |
| E2E | Playwright | 20章 Demo Scenarioを1本のシナリオテストとして自動化、9/17前の最終ゲートとする |

## Legacy READ ONLY保証のテスト

1. **DBレベル**：Legacy Adapter用DBユーザーで`INSERT`/`UPDATE`/`DELETE`を試みるIntegration Testを用意し、`Access Denied`例外が発生することを確認する。
2. **アプリケーションレベル**：ArchUnit等で「`repository.legacy`パッケージ配下はJPAの`save`/`delete`系メソッド呼び出しおよびSELECT以外のSQL文字列を含まない」ことを静的に検証するルールを追加する（Nice to have）。
3. **環境レベル**：自動テストの`legacyDataSource`は必ずLegacy Demo Instance（Testcontainers）を指し、共有中のLegacy実インスタンスには物理的に接続できない構成とする。

## calc4のGolden Test

`[CONFIRMED]`（Implementation Step 0/1で訂正・実装済み）Legacyに`FormulaTest.java`という名前のテストは存在するが、**内容がcalc4と無関係（別クラスFormula.javaの価格計算、かつ全体コメントアウト）** と判明したため、移植は不可能だった。代わりに、Default 4EU式から手計算し独立したPython実装でクロスチェックした期待値（Normal / Stock不足 / Stock余剰 / Formula未設定Default / 0値 / Boundary×2 / Open Qty上限ケース）による`OrderQuantityCalculatorGoldenTest.java`を新規作成し、**9件PASS**を9/17前の必須ゲートとして満たした。

---

# 14. Risk（9/17までの技術Risk）

| Risk | Level | 原因 | 影響 | 回避策 | 判断期限 |
|---|---|---|---|---|---|
| Legacy Demo Instance方式の合意 | ~~HIGH~~ **RESOLVED** | 「使い捨てLegacy互換インスタンスへの書込みは絶対条件のLegacy DB WRITE禁止に抵触しない」という解釈 | - | **2026-08-27、ユーザーより正式Implementation Decisionとして承認済み**（9条件付き、要件MD 34章E参照） | 解消 |
| Legacy Query再現性 | ~~HIGH~~ **MEDIUM（部分解消）** | `getBaseStockList`/`getStockListOrder`は1000行超、必要列抽出の過程で列・JOIN条件を誤る可能性 | calc4がLegacyと不一致になる | calc4計算ロジック自体はGolden Test 9件PASSで検証済み。ただし縮小Read Query（列抽出範囲）の原SQLとの一行単位レビューは未実施のまま残存 | 次Step |
| MS_FORMULA DDL不在 | ~~MEDIUM~~ **RESOLVED** | `goo_dummy_dumpfile.sql`にMS_FORMULAのCREATE TABLE文が存在しない | - | 実Entity（`MsFormula.java`）からDDLを新規構築し1 SKU分Seedして実証済み（Item別Formula経路・Default経路の両方をAPI応答で確認） | 解消 |
| calc4のJava8→21移植 | ~~LOW~~ **RESOLVED** | 未コンパイル確認だった | - | Step 0で実コンパイル・Golden Test PASSまで確認済み | 解消 |
| Unit Price/Currency不足 | ~~MEDIUM~~ **RESOLVED** | 実TR_PO_DTLがほぼ空 | - | Demo SeedにTR_PO/TR_PO_DTLを含め、19件のPO実績から実際にUnit Price/Currencyを返却することをOrder Candidate APIで確認済み | 解消（Step 0/1） |
| Supplier構造の脆弱性 | ~~MEDIUM~~ **MEDIUM（Prototype暫定回避のみ、Master構造は未解決）** | MS_ITEMにSUPPLIER_CD列がなく、Item⇔Supplierの恒久マスタが存在しない | Draft作成時のSupplier単位分割がデモしづらい | Demo SeedでItem毎に明示的にPO実績（Supplier付き）を用意し、最新PO履歴からの導出クエリで実装・動作確認済み。**ただしこれはPrototype固有の暫定設計判断であり、Legacyの正式なSupplier Master構造問題（要件MD 27.3 TBD）は未解決のまま** | 次Step（Draft実装時に再検討） |
| PO Excel Template | LOW | 生成コード自体がLegacyに存在しない（Phase 0監査I章） | 9/17スコープ外の機能のため影響小 | 要件MDの通りWeb Preview優先、Excel生成は任意実装のまま | 影響軽微、判断不要 |
| Frontend/Backend接続初期設定 | LOW〜MEDIUM | CORS/Proxy設定漏れは後工程で発覚しがち | 統合フェーズでの手戻り | Implementation Order Step 3で早期に疎通確認 | Step 3 |
| Authentication | LOW | シンプルなForm Loginのため技術的難度は低い | - | - | - |

---

# 15. Scope Cut候補（優先順位付き）

Core Demo Scenario（**Candidate → Draft → Preview → Demo Send → Supplier Response → History**）を破壊しないものから順に削減候補とする。

1. **Dashboard**（Coreの外、単純なリンク集約ページへ縮小可能）
2. **Sales Trend Chart**（要件MD 21章で元々「可能であれば実装」）
3. **Excel PO生成**（要件MD 21章で元々「可能であれば実装」、4.3節の通りLegacyに再利用元自体が無い）
4. **Mail Previewの作り込み**（Attachment/Body装飾を省略し、最低限のTo/CC/Subject表示のみに縮小）
5. **SKU Detail画面**（参照専用画面のため、Candidate Listの情報量を増やせば代替可能。ただしDemo Scenario台本(20章)には明示的に含まれるため優先度は低い）
6. **`calc4Alt`のUI表示**（そもそも通常画面では非表示方針、要件MD 7章 CUSTOMER REVIEW次第で完全に不要）
7. **Order Candidate Listの高度なFilter**（Brand以外のSupplier/Status/Keyword/Attentionのみ Filterを削減し、Brand単一Filterのみに縮小）
8. **Attention確認UIの作り込み**（Acknowledge操作自体は残すが、リッチな履歴表示は簡易ボタンへ縮小）
9. **英語i18nリソースの拡充**（ja固定のまま、en/はTODOプレースホルダーのままでよい。要件MD 28.2で元々必須外）

**Core（Candidate→Draft→Preview→Demo Send→Supplier Response→History）とRecommended Qty計算（4章）は一切削らない。**

---

# 16. Open Issues（本書内で明示した要確認事項）

- ~~6.2節：Legacy Demo Instance方式（Legacy互換だが別インスタンス）が「Legacy DB WRITE禁止」の対象外であるという解釈の確認~~ → **`[CONFIRMED]`2026-08-27、ユーザーより正式Implementation Decisionとして承認済み**（Critical Safety Rules指示の一部。既存Legacy DBとは別Instance/別Databaseとして扱う、Seed投入時のみ管理者権限、Application実行時はSELECT専用User、Credential完全分離、という9条件つきで承認）。17章参照。
- 8.1節：Create Draft時、異なるSupplier混在をどう扱うか（9/17は単純に拒否する簡易実装で妥協するかの確認）
- 13.5節：Supplier Response完了条件（Delivery必須か等）の確定待ち（要件MD 27.4 CUSTOMER REVIEW）
- `calc4Alt`の扱い（要件MD 27.4 CUSTOMER REVIEW、確定次第UI設計へ反映）
- **（新規）Safety Guard未実装**：Legacy DB接続先のホスト検証によるApplication起動失敗ガードが要求されているが、Step 0/1のBaseline確定時点では未実装（4.1節参照）。次Step着手前に実装必須。
- **（新規）Supplierの正式Master構造**：Step 0/1ではTR_PO/TR_PO_DTL最新PO履歴からの導出という暫定回避策で対応したが、Legacyの正式なSupplier Master/Entity構造は未確定のまま（要件MD 27.3参照）。

---

# 17. Implementation Step 0/1 Baseline確定事項（2026-08-27）

`[CONFIRMED]` Order Candidate List Vertical Slice（Legacy Formula → Demo Legacy Data → Legacy Adapter → API → 日本語画面）を実装・全Regression PASSの状態で確定した。要件MD 34章に対応するサマリを掲載。詳細な実装ファイルはリポジトリの`backend/`・`frontend/`を参照。

## A. calc4再利用（4.3節を参照・確定）

verbatim移植・Java 21コンパイル成功。Legacy `FormulaTest.java`はcalc4のTestとして利用不可と判明（訂正）。独自Golden Test 9件PASS。

## B. Legacy Demo Schema（6.2節を参照・確定）

`goo_dummy_dumpfile.sql`のDDLは現行JPA Entityと不一致と判明。実Entityの`@Column`定義からDDLを新規構築（`backend/demo-data/01-schema.sql`）。

## C. Demo Data（6.2/6.4節を参照・確定）

19 SKU/3 Brand/3 Supplier。Recommended QtyはSeedせず、Formula入力値から実行時算出。Default経路・Item別Formula経路の両方を実データで検証。`dataSource=DEMO_LEGACY`。

## D. Legacy READ ONLY（4.1節を参照・確定）

3層保証（DBユーザーSELECT専用／HikariCP readOnly=true／`@Transactional(readOnly=true)`）。Integration Test 4件PASSで実証。

## E. DataSource Safety（4.1節を参照・確定、Safety Guardは未実装）

Legacy Adapter側の全DataSource注入箇所への明示的`@Qualifier`付与を必須Safety Ruleとして確定。Application起動時のLegacy接続先検証Guardは**未実装**（要対応事項として16章に記載）。

## F. Character Encoding（6.4節を参照・確定）

`docker-entrypoint-initdb.d`のmysqlクライアント非対話実行時、`SET NAMES utf8mb4;`が無いと日本語Seedデータが二重エンコード破損することを発見・修正。`02-seed.sql`冒頭に追加済み。

## G. Supplier（4.2節を参照・確定、Master構造はTBD）

MS_ITEMにSupplier情報が存在しないため、TR_PO/TR_PO_DTL最新PO履歴からの導出をPrototype Step 0/1の暫定設計判断として採用。正式なSupplier Master/Entity構造は要件MD 27.3の`[TBD - SOURCE REVIEW]`のまま。

## Regression結果

Backend: `mvn test`（Golden Test 9件 + READ ONLY Test 4件、計13件PASS）／`mvn package`成功。Frontend: `npm run build`成功。`GET /api/order-candidates`：複数SKU取得・calc4算出・`dataSource=DEMO_LEGACY`・日本語文言APIレスポンス非含有をいずれも確認。Legacy（`phasep-gulliver`）側の変更はゼロ（`git status`比較で確認）。

---

以上がPrototype Technical Designである。Step 0/1の内容は本書に反映済みのBaselineとして確定した。Step 2以降の実装は別指示で開始する。
