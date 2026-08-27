# G-SYS — Full System Specification

> **G-SYS** — Inventory, Purchase Order & Shipment Management System with Tempostar / Logizero external integration.

---

## 1. Purpose & Scope

**G-SYS** is an internal operations platform used by purchasing, logistics, warehouse, EC (e-commerce), pricing, and accounting staff. It manages the full physical-goods pipeline:

- **Purchasing** — issue and track Purchase Orders through their lifecycle.
- **Import / shipment** — track invoices, bills of lading, containers, customs, and arrivals.
- **Inventory** — maintain live stock per warehouse, compute reorder recommendations, and reconcile stock against transactions.
- **Pricing** — import price lists and manage per-marketplace sale prices (Rakuten, Yahoo, Amazon, Qoo10, Ponpare, Wowma).
- **External sync** — push/pull stock and price data to **Tempostar** (inventory platform) and **Logizero** (logistics/WMS) via CSV + browser automation + SFTP.
- **Reporting** — generate Excel/CSV exports (stock list, arrival list, arrival schedule, tariff quota, product description, price list).
- **Notifications** — queue and send operational email (batch-driven).

The system is **automation-heavy**: a large share of work runs as scheduled batch jobs rather than manual UI actions.

---

## 2. Technology Stack

| Layer              | Technology                                   | Version                   |
| ------------------ | -------------------------------------------- | ------------------------- |
| Language           | Java                                         | 1.8                       |
| Framework          | Spring Boot                                  | 1.5.15.RELEASE            |
| Packaging          | WAR (external servlet container)             | —                         |
| Web / MVC          | Spring MVC + Thymeleaf (server-rendered)     | starter                   |
| Templating extras  | thymeleaf-extras-springsecurity4             | —                         |
| Persistence        | Spring Data JPA / Hibernate                  | starter                   |
| Database           | MySQL (mysql-connector-java)                 | 8.x server (`goo` schema) |
| Security           | Spring Security (JDBC auth)                  | starter                   |
| Batch              | Spring Batch Core                            | —                         |
| Excel              | Apache POI + poi-ooxml                       | 3.9                       |
| CSV                | OpenCSV 3.7 + commons-csv 1.8                | —                         |
| Browser automation | Selenium Java                                | 4.11.0                    |
| File transfer      | Spring Integration SFTP                      | —                         |
| Mail               | spring-boot-starter-mail                     | —                         |
| Cloud backup       | Google Drive API (google-api-services-drive) | v3                        |
| Boilerplate        | Lombok                                       | 1.16.18                   |
| JSON               | org.json, json-simple                        | —                         |
| Logging            | Log4j2 API                                   | —                         |
| Utils              | Guava 22.0, commons-collections 3.2.2        | —                         |

**Front-end libraries** (static assets): jQuery, Bootstrap, DataTables, **Handsontable** (inline grid editing), Parsley (validation), Select2, Numeral.

---

## 3. High-Level Architecture

```
Browser (Thymeleaf pages + Handsontable grids + jQuery/AJAX)
        │
        ▼
┌───────────────────────────────────────────────┐
│  Web Layer                                     │
│   • DefaultController   (login / errors)       │
│   • UsersController     (page controllers)     │
│   • AllUsersRestController (/api/** JSON)      │
└───────────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────────┐
│  Business Layer                                │
│   • services/*        (imports, uploads, checks)│
│   • utilities/BusinessLogicUtil  (stock lifecycle)│
│   • utilities/Formula (pricing/order math)     │
│   • utilities/export/* (Excel report builders) │
│   • customquery/*     (targeted SQL updates)   │
└───────────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────────┐
│  Data Layer                                    │
│   • repository/* (37 JPA + Customized*/Impl)   │
│   • model/* (26 entities) + model/pk/* (12 PKs)│
│   • MySQL "goo" schema                          │
└───────────────────────────────────────────────┘
        ▲                         ▲
        │                         │
┌───────────────┐        ┌──────────────────────┐
│ Batch Layer   │        │ External Systems      │
│ AbstBatch →   │        │ • Tempostar (Selenium)│
│ 50+ batch jobs│◄──────►│ • Logizero (SFTP)     │
│ (cron-driven) │        │ • Google Drive (backup)│
└───────────────┘        │ • SMTP mail server    │
                         └──────────────────────┘
```

**Package root:** `jp.ne.glv`

| Package                      | Responsibility                                                               |
| ---------------------------- | ---------------------------------------------------------------------------- |
| `controller`                 | Server-rendered page controllers                                             |
| `restcontroller`             | JSON `/api/**` endpoints for grids & workflow actions                        |
| `services` / `services.core` | Business services + import/batch framework (`AbsImportService`, `AbstBatch`) |
| `batch`                      | Concrete batch jobs (imports, exports, syncs, purges)                        |
| `repository` (+ `impl`)      | JPA repositories + custom query implementations                              |
| `model` (+ `pk`)             | Entities and composite-key classes                                           |
| `utilities` (+ `export`)     | Business helpers, formulas, Excel/CSV export builders                        |
| `customquery`                | Hand-written targeted update queries                                         |
| `config`                     | MVC + SFTP configuration                                                     |
| `security`                   | Spring Security JDBC auth & access rules                                     |
| `dto`, `io`                  | Data transfer types and file-handling helpers                                |

---

## 4. Domain Model

26 JPA entities + 12 composite-key classes. Naming prefixes:
**`Ms`** = Master · **`Tr`** = Transaction · **`Wk`** = Working/staging · **`His`** = History · **`Mig`** = Migration · **`Sys`** = System.

### 4.1 Master Data

| Entity                        | Purpose                                                                                                                                                                                                                                                                                                |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `MsItem`                      | **Central item master** — the largest table. Identity/grouping, per-channel pricing, cost averages, EC metadata, marketplace item IDs, ship flags, audit. Drives stock list, price list, product description, and marketplace output simultaneously.                                                   |
| `MsStk`                       | **Live stock per warehouse+item** (composite key `whCd+itemCd`). Holds current qty, standard/safety stock, 20 PO slots (`poNo1..20`, `poQty`, `arrQty`, `ordrWeek`, `delivWeek`), 10 ship slots, precomputed display columns, and formula fields. Treated as a **derived aggregate**, not hand-edited. |
| `MsComm`                      | **Configuration backbone** (composite key `categoryId+codeId`). Brands, suppliers, warehouses, currencies, mail groups/recipients, file import/export paths, lead times, tariff settings, notice banners, monthly-processing flags. Changes take effect at runtime with no code deploy.                |
| `MsFormula`                   | Formula definitions for stock/order recommendation logic.                                                                                                                                                                                                                                              |
| `MsUser`                      | Authentication + UI access control (drives `USER_TYPE` authorities).                                                                                                                                                                                                                                   |
| `MsItemGrp`, `MsItemCategory` | Item grouping and category support.                                                                                                                                                                                                                                                                    |

### 4.2 Transactional Data

| Entity               | Purpose                                                                                                 |
| -------------------- | ------------------------------------------------------------------------------------------------------- |
| `TrPo` / `TrPoDtl`   | Purchase Order header / detail lines. Root of the procurement flow.                                     |
| `TrInv` / `TrInvDtl` | Invoice header / detail lines.                                                                          |
| `TrInvPo`            | Invoice ↔ PO linkage bridge.                                                                            |
| `TrInvAddCost`       | Additional landed-cost rows on an invoice.                                                              |
| `TrBl` / `TrBlDtl`   | Bill of Lading header / detail.                                                                         |
| `TrArr`              | **Arrival / shipment tracking record** — the broadest and most operationally central entity (see §4.5). |
| `TrPackingInv`       | Packing-invoice linkage.                                                                                |
| `TrItemYahooDtl`     | Yahoo marketplace item detail (export/import).                                                          |
| `TrProdDesc`         | Product description rows (customs/documentation).                                                       |

### 4.3 Working / History / Migration / System

| Entity         | Purpose                                                                      |
| -------------- | ---------------------------------------------------------------------------- |
| `WkStk`        | Working stock (backup ID + `whCd+itemCd`), used for import/calc staging.     |
| `WkCpo`        | Working customer PO.                                                         |
| `HisStkList`   | Stock list change history snapshots.                                         |
| `HisArrList`   | Arrival list change history snapshots.                                       |
| `MigStkList`   | Stock-list data migration support.                                           |
| `ITariffQuota` | Projection interface for tariff-quota export.                                |
| `SysSendMail`  | **Mail queue** — most mail is written here and sent later by the mail batch. |

### 4.4 Composite Keys (`model/pk`)

`MsCommPK`, `MsStkPK`, `TrArrPK`, `TrInvPK`, `TrInvDtlPK`, `TrInvPoPK`, `TrInvAddCostPK`, `TrPoDtlPK`, `TrBlDtlPK`, `TrPackingInvPK`, `WkStkPK`, `WkCpoPK`.

### 4.5 `TrArr` — the operational hub

Key `supplierCd+poNo+invNo`. It stitches purchasing, logistics, warehouse, and accounting together. Field groups:

- **Transport/docs:** `blNo`, `shipNo`, `contrNo`/`contrSize`, `packingNo`, `declarationNo`.
- **Type/billing:** `tranType`, `billType`, `poType`, `invType`, `brandCd`.
- **Milestone dates:** `ordrDate`, `etd`, `eta`, `etaWh` (+ send flag/datetime), `stkInDate`, plus many `iniPrcList*`, `costCalc*`, and `compPrcList*` deadline/actual date pairs.
- **Money/cost:** PO amount & currency, freight, invoice totals, customs (`custAmt`, `custCcyRate`, `custAmtJpy`, `custTariff`, consumption taxes), demurrage/drayage/devanning/freight, remittance (`rmtCcy`, `rmtRate`, `rmtAmt`).
- **Warehouse/ops:** `recvNo`, `noPallets`, inspection, `whRepStatus/Result/UserId`.
- **Customs/broker:** `custBroker`, `custPic`, `vesselNo`, notes/category, insurance.
- **Lifecycle:** `delFlg` + standard audit fields.

---

## 5. Core Business Logic

### 5.1 Stock lifecycle — `utilities/BusinessLogicUtil`

The single most important shared helper. Stock is **recomputed** from PO + invoice + arrival data rather than hand-maintained. Key methods:

| Method                                  | Effect                                                        |
| --------------------------------------- | ------------------------------------------------------------- |
| `refreshMsStkTransactions(poNo)`        | Recalculates PO and arrival quantities across `MsStk`.        |
| `refreshMsStkShipQtyTransactions(poNo)` | Rebuilds ship-number / ship-quantity slots.                   |
| `refreshTrArr(poNo)`                    | Creates/updates arrival rows from existing PO + invoice data. |
| `deleteForNonTransactionTrArr(poNo)`    | Removes stale arrival rows when PO/invoice no longer exist.   |
| `deleteMsStkTransactions(poNo)`         | Clears PO/arrival slots from stock rows of a deleted PO.      |
| `deleteTrArrByPoNo(poNo)`               | Bulk delete arrival rows for a PO.                            |

Also: PO-number parsing (→ supplier / brand / ID code), credit-PO/credit-invoice conversions, and quantity/safety/order-qty helpers.

### 5.2 Pricing & order math — `utilities/Formula`

- Tax-included ↔ tax-excluded conversion.
- Profit-rate computation for sell/sale scenarios.
- Shipping-adjusted pricing; **Amazon** adjustment via handling-fee tiers.
- Tariff totals.
- Stock/order **recommendation** from stock standard, lead time, and open quantities; safety-stock and logical-stock totals.

Supporting helpers: `FormulaParser`, `OrderQuantityCalculator`, `StockCalculationHelper`, `ExcelUtil`, `ObjectMerger`, `SortJsonArray`, `ExportCSVFiles`. Unit-tested in `FormulaTest.java`.

---

## 6. Web / API Surface

### 6.1 Page routes (`UsersController`, `DefaultController`)

| Route                                                        | Screen                               |
| ------------------------------------------------------------ | ------------------------------------ |
| `/`, `/login`                                                | Login landing                        |
| `/access_denied`, error 403/404/500                          | Error pages                          |
| `/stock-list`                                                | **Main working screen** — stock grid |
| `/stock-list/item-detail/{itemCode}` (+ `/add`, `/update`)   | Item master detail/create/edit       |
| `/stock-list/inventory-detail/{itemCode}`                    | Inventory detail                     |
| `/stock-list/delete/{itemCode}`                              | Delete item                          |
| `/order-judgement`                                           | Reorder decision screen              |
| `/arrival-list` (+ `/{invoiceNo}/{blLineNo}`)                | Arrival tracking list / detail       |
| `/arrival-list/redirect/{paramType}`                         | Arrival navigation                   |
| `/arrival-list/remittance/update`                            | Remittance update (JSON)             |
| `/common-master` (+ `/add`, `/detail`, `/update`, `/delete`) | Admin lookup-table maintenance       |

### 6.2 REST API (`AllUsersRestController`, `/api/**`)

**Retrieval:** `getObjectList` (generic stock/arrival/common/order lookup), `findMsItemByItemCd/{itemCd}`, `getBlNoDetails`, `getAddCostDetails`, `checkBrandDuplicate`, `checkProdDescItem`.

**Inline editing (Handsontable):** `modifyFromHandsonTable/{objectType}` (`stock`/`arrival`/`common`), `updatePrice`, `updateStandardQty`, `updateEtaWh`, `updateCustAmountJpy`, `updateFlag` (sale/set), `setDisplayOrder`.

**Arrival maintenance:** `updateEtaWhSendFlg`, `deleteTrArr` (→ re-runs `BusinessLogicUtil` reconciliation).

**Common master:** `deleteCommonMaster`, `common-master/check/{brandName}`, `translate`.

**Exports:** `export/{type}` (stock list, arrival list, arrival schedule, tariff quota, product description, price list, page-creation direction, history).

**Operational jobs (UI-triggered):** `purgeOldMail`, `validateStkQtyDiscrepancy`, `purgeOldMonthEndStk`, `purgeOldWkCpo`, `run`, `manual-run/{isAgree}`.

**Price list:** `isPricelistRequested`, `updatePricelistStatus`.

> `AllUsersRestController` is the highest-regression-risk surface — nearly all mutations flow through it into `MsItem`/`MsStk`/`TrArr` repos + `BusinessLogicUtil`.

---

## 7. Import / Export Engine

### 7.1 Import framework — `services/core/AbsImportService`

Common file-import mechanics for all import jobs:

1. Discover files from the upload dir configured in `MsComm`.
2. Move to a work folder; validate format & size.
3. Parse workbook/CSV; collect **file-level** and **cell-level** errors (`ErrorFile`, `ErrorCell`).
4. Detect duplicate keys.
5. Write backup + `error_`-prefixed error files.
6. Queue success/failure mail into `SysSendMail`.
7. Clean/move processed files.

Files are prefixed with a timestamp-based process ID; upload/work/backup folders are **all driven by `MsComm`** config.

Key concrete imports:

- **`MsPriceListImportService`** — validates and applies price-list Excel into `MsItem`, using `MsComm` price-change limits + approval logic; queues a summary mail.
- **`MsInvTempostarStkUploadService`** — Selenium ChromeDriver logs into Tempostar and uploads stock CSVs one by one (copy→submit→backup→cleanup), with per-upload mail.

### 7.2 Export engine — `utilities/export/*`

Not simple row dumps — each builder loads a **template workbook** from `testfile/template`, clones sheets, and preserves formatting/formulas/widths/merges/colors. Common base: `ExcelExportable`. Color codes encode business states (new / set / discon / special price).

| Export               | Notes                                                                                                                                                                                                                   |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `StockList`          | Most complex. Brand-grouped (sheet per brand), item-group headers, conditional fills, computes stock standard/safety/PO/arrival qty + marketplace prices. Bridges `MsItem`/`MsStk`/`MsComm`/`MsFormula`/`WkCpo`/`TrPo`. |
| `PriceList`          | Brand + item-group grouping, group headers/footers, carries group formulas into item rows, colors pricing cells.                                                                                                        |
| `ArrivalList`        | Live or historical arrival rows; sorts by ETA/ETA-WH/BL/invoice/PO; footer formulas.                                                                                                                                    |
| `ArrivalSchedule`    | Two modes: normal schedule vs. cost-calculation; template sheet cloned per arrival code.                                                                                                                                |
| `ProductDescription` | Merged cells per model, embedded product images, auto row height, total qty/amount — customs/documentation.                                                                                                             |
| `TariffQuota`        | Fills declaration data, computes remaining quota, blocks quantities exceeding the limit.                                                                                                                                |
| CSV                  | via `ExportCSVFiles` (e.g. Tempostar/Logizero feeds).                                                                                                                                                                   |

---

## 8. Batch / Automation Layer

Root abstraction **`services/core/AbstBatch`** standardizes lifecycle logging, `initialize()`/`execute()` contract, exception mail, file cleanup, and key generation. Jobs are packaged with per-job `batch_pom_*.xml` descriptors at the repo root and are **externally scheduled** (OS/scheduler cron; no in-app `@Scheduled`). 50+ jobs; families:

| Family                      | Jobs (examples)                                                                                                                                                                                           |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Price list**              | `MsPriceListImportBatch`, `RunPricelistBatch`                                                                                                                                                             |
| **Item master**             | `MsItemMasterBulkUpload`, `MsItemMasterBulkDelete`, `MsItemMasterIdBulkChange`, `MsItemColImportBatch`, `MsItemCategoryImportBatch`, `MsECItemAttributeImportBatch`, `MsItemBulkUpdateColumnsCGToEEBatch` |
| **Stock standard / cost**   | `MsStkStandardImportBatch`, `MsStandardImportBatch` (abstract), `MsMoveAveCostImportBatch`, `MsLastMonthQtyUpdate`                                                                                        |
| **Month-end**               | `MsMonthEndStockUpdate`, `MsMonthEndStockPurgeBatch`                                                                                                                                                      |
| **Stock list download**     | `MsStkListDownloadBatch`, `MsStkListDownloadRemoteBatch`                                                                                                                                                  |
| **Tempostar**               | `InvTempostarStkUploadBatch`, `SlTempostarDownloadBatch`, `SlTempostarImportBatch`, `SlTempostarPriceChangeDownloadBatch` (+ Second), `CheckStuckTempostarFile`, `InvLogizeroTempostarStkUploadBatch`     |
| **Logizero**                | `InvLogizeroStkDownloadBatch`, `InvLogizeroStkImportBatch`, `MIMOSA_InvLogizero*` variants                                                                                                                |
| **PO / import**             | `PrInitialPoImportBatch`, `PrOfficialPoImportBatch`, `PrCreditPoImportBatch`, `PrBLInvImportBatch`, `PrStkInReportImportBatch`, `PrProductDescriptionImportBatch`                                         |
| **Order judgement**         | `MsOrderJudgementImportBatch` (+ abstract)                                                                                                                                                                |
| **Mail**                    | `SysSendMailBatch`, `SysSendMailPurgeBatch`, `PrEtaWhMailBatch`, `CheckSMTPErrorFromDailyBatch`                                                                                                           |
| **Migration**               | `MigStkListImpBatch1/2/3`, `MigArrListImpBatch1/2`                                                                                                                                                        |
| **System / purge / backup** | `SysPurgeBatch`, `WkCpoPurgeBatch`, `SysGoogleDriveBackUp`                                                                                                                                                |

> Because so much runs in batch, a single shared-rule change (e.g. in `BusinessLogicUtil` or a `MsComm` value) can cascade across DB state, workbook formatting, mail, and file cleanup.

---

## 9. External Integrations

| System                             | Direction                                          | Mechanism                                                                                       |
| ---------------------------------- | -------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| **Tempostar** (inventory platform) | Upload stock/price CSV; download price-change data | Selenium ChromeDriver browser automation (`chromeDriverPath`, `chromeForTestingPath`)           |
| **Logizero** (logistics/WMS)       | Download inventory; upload stock                   | SFTP (Spring Integration) + login URL/credentials in properties                                 |
| **SFTP server**                    | File exchange for Logizero feeds                   | `SftpConfig` → `SftpRemoteFileTemplate`, private-key auth, host-key checking currently disabled |
| **Google Drive**                   | Scheduled DB/file backup                           | `SysGoogleDriveBackUp` (Drive API v3)                                                           |
| **SMTP**                           | Outbound operational mail                          | `spring-boot-starter-mail`, queued via `SysSendMail`                                            |

Config lives in `application-{dev,local,prod}.properties`: `chromeDriverPath`, `logizero.*`, `sftp.*`, datasource, JPA, Jackson date/timezone, `session_timeout`, `spring.batch.initializer.enabled`.

---

## 10. Security & Configuration

### 10.1 Security (`security/WebSecurityConfig`)

- **JDBC authentication** against `MS_USER`; authorities from `USER_TYPE`.
- `/login`, static assets (`/resources/**`, `/static/**`, `/dist/**`) → permit all.
- `/api/**` → requires an authenticated business user (any of the user-type authorities).
- `/common-master/**` → **`SYS_ADMIN` only**.
- All other requests → authenticated.
- Batch/monitor endpoints exempted from the security filter: `/api/purgeOldMail`, `/api/purgeOldMonthEndStk`, `/api/purgeOldWkCpo`, `/api/validateStkQtyDiscrepancy`.

**User types (`Const`):** `SYS_ADMIN`, `SLS_ADMIN`, `MANAGEMENT`, `PURCHASE`, `LOGISTIC`, `WH_USER`, `EC_USER`, `PRC_USER`, `ACCOUNT`, `OPERATOR`, `WH_LOG`.

### 10.2 MVC / SFTP config

- `WebMvcConfig` — exposes the current logged-in `MsUser` to controllers (audit fields + permissions).
- `SftpConfig` — `SftpRemoteFileTemplate`, private-key auth.

### 10.3 `Const.java` — the application dictionary (~687 lines)

Central catalog of coded values that are effectively part of the DB contract:

- **`CATE_ID_*`** — `MsComm` category IDs (brands `MS_BRAND`, suppliers `MS_SUPPL`, mail `MS_MAIL`, currency `MS_CCY`, warehouses `MS_WH`, lead time, tariff, file import/export, stock-in stages, price list, EC item, etc.).
- **`CODE_ID_*`** — specific codes: mail codes (`MS_MAIL_*`), warehouse codes (`MS_WH_XX`, `MS_WH_4..14`), file import IDs (`FILE_IMP_*`), file export IDs (`FILE_EXP_*` — arrival list, arrival schedule, stock list, tariff quota, product description, EC mall, etc.), download-mail codes (`DL_ML_*`).
- Stock/PO/invoice/arrival/remittance **status** constants, fixed **column indexes** for `WK_STK`/`WK_CPO`, string lengths, numeric limits.

**Conventions:** `XX` = primary stock warehouse · `00000000` = deposit invoice number · PO lifecycle `INITIAL → OFFICIAL → STOCKIN` · invoice lifecycle `TRANSIT → RECEIVING → STOCK_IN` · soft-delete via `DEL_FLG` · standard audit fields `CREATE_USER_ID`/`CREATE_DATETIME`/`UPDATE_USER_ID`/`UPDATE_DATETIME`.

---

## 11. Key Workflows

### 11.1 Purchase Order → Stock

```
TrPo/TrPoDtl created
   └─► TrInv/TrInvDtl linked (via TrInvPo)  ─► TrBl/TrBlDtl
          └─► BusinessLogicUtil.refreshTrArr → TrArr (arrival record)
                 └─► refreshMsStkTransactions / refreshMsStkShipQtyTransactions
                        └─► MsStk PO/arrival/ship slots recomputed
```

### 11.2 Arrival lifecycle

`PO created → invoice linked → BL/customs/cost milestones recorded → warehouse ETA & stock-in tracked` — all on `TrArr`; edits trigger history writes, stock refresh, and (optionally) mail.

### 11.3 Price list import

`Excel dropped in upload dir → MsPriceListImportBatch/Service validates (format/size/range/limits) → applies to MsItem → queues summary mail`. Per-marketplace change flags (`*SalePriceChangedFlag`) drive downstream marketplace exports.

### 11.4 Tempostar/Logizero sync

`Logizero inventory via SFTP → import to MsStk → generate CSV → Selenium upload to Tempostar → backup + mail`. `CheckStuckTempostarFile` monitors stalled uploads.

### 11.5 Stock discrepancy monitoring

`StkQtyDiscrepancyCheckerService` compares stock quantities and emails a discrepancy notice (via `/api/validateStkQtyDiscrepancy` or batch).

---

## 12. UI / Screens

| Screen                | Template                                                                      | Role                                    |
| --------------------- | ----------------------------------------------------------------------------- | --------------------------------------- |
| Login                 | `default/login.html`                                                          | Auth                                    |
| Stock list            | `all-user/stock-list.html`                                                    | **Primary working grid** (Handsontable) |
| Item detail / add     | `all-user/item-detail.html`, `item-add.html`                                  | Item master maintenance                 |
| Inventory detail      | `all-user/inventory-detail.html`                                              | Per-item stock detail                   |
| Order judgement       | `all-user/order-judgement.html`                                               | Reorder decisions                       |
| Arrival list / detail | `all-user/arrival-list.html`, `arrival-detail.html`                           | Shipment tracking                       |
| Common master         | `common-master/*.html`                                                        | Admin config (SYS_ADMIN)                |
| Shared                | `fragment/header.html`, `modal.html`, `scripts.html`; `default/template.html` | Layout fragments                        |
| Errors                | `error/403,404,500.html`                                                      | Error pages                             |

UI emphasis: fast list search, inline grid edits, Excel-oriented output, and status highlighting for operational staff.

---

## 13. Data Layer

37 repositories = plain Spring Data JPA + `Customized*Repository` contracts + `*Impl` custom-query classes.

**Custom query contracts:** `CustomizedMsItemRepository`, `CustomizedMsOrderRepository`, `CustomizedMsStkRepository`, `CustomizedTrArrRepository`, `CustomizedTrProdDescRepository`, `CustomizedWkStkRepository`, `CustomizedWkCpoRepository`, `CustomizedSysSendMailRepository`, `CustomizedMigStkListRepository`, `CustomizedMsItemCategoryRepository`.

**Convention:** `findBy…` = screen filters · `findAllBy…` = lists/exports/reconciliation scans · projection methods return `Object[]`/`List<String>`/interface views (`ITariffQuota`) · `saveHistory(…)` writes to history tables · native/custom queries for aggregation. **The `Impl`/`customquery` layer often holds the real rule** — reading only the interface can miss it.

`customquery`: `CommonCustomQuery`, `ItemCustomQuery`, `ArrivalCustomQuery` — targeted column-level updates for grid edits and bulk operations.

---

## 14. Maintenance Warnings

- **Template-coupled exports:** changing a column index can silently break an Excel export. File-format changes are high-risk.
- **`MsComm` is live config:** editing a value changes runtime behavior with no deploy — treat config records as part of the contract.
- **`MsStk` is derived:** direct edits can be overwritten by refresh logic; go through `BusinessLogicUtil`.
- **`TrArr` edits fan out:** mail, history writes, stock refresh, downstream exports.
- **Old framework versions** (Spring Boot 1.5, Java 8, POI 3.9): upgrade in stages.
- **`Const` strings are DB contract:** don't change values casually.
- **Safest study order:** `BusinessLogicUtil` → `MsItem`/`MsStk`/`TrArr` → `AllUsersRestController` → export builders → `AbsImportService` + concrete imports → `MsComm` categories → `repository/impl` & `customquery`.

---

## 15. Testing & Seed Data

- `GulliverApplicationTests.java` (context load), `FormulaTest.java` (pricing/order math).
- Seed/fixtures: `goo_dummy_dumpfile.sql`, `gulliver/testfile/*`, template Excel/CSV for stock, arrival, price list, and external-system imports.
- DB backups in repo: `goo_20251126.dmp`, `database(goo_local).7z`.

---

_Generated from source inspection of the application codebase (Spring Boot WAR)._
_Last Updated: 2026-08-26_
