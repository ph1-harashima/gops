# G-OPS Production Backup Real Data Audit — Stage 0: Archive Inspection

Scope: read-only inspection of the archive Ernest provided
(`goo_20260916.7z`) - listing metadata only, no extraction, no restore, no
connection to any existing database. This document is the Stage 0
deliverable; Stage 1 (extraction/restore) requires a separate Go decision
and has not started.

## 1. Backup Provenance

- Received from: Ernest (client-side), described as a Gulliver Production
  DB backup as of 2026-09-16.
- File: `docs/real-data-audit/goo_20260916.7z`
- This is a **backup file**, not a live Production connection - no
  Production/UAT system was reached to obtain or inspect it.
- Naming matches an already-documented, recurring convention: the
  project's own `docs/GSYS_Specification.md` (§15, "Testing & Seed Data",
  dated 2026-08-26 - a month before this file) already references a prior
  backup of the identical shape, `goo_20251126.dmp`. This is not the first
  time a `goo_YYYYMMDD.dmp` backup has been provided by this client - it is
  an established, expected deliverable pattern, not a one-off.

## 2. Archive Inspection

Inspected via `7z l -slt` (archive listing only - the archive was **not**
extracted):

| Property | Value |
|---|---|
| Archive format | 7z |
| Physical (compressed) size | 59,624,191 bytes (~56.9 MB) |
| Files inside archive | 1 |
| Inner file name | `goo_20260916.dmp` |
| Inner file size (uncompressed) | 5,221,243,233 bytes (~4.86 GB) |
| Packed size | 59,624,053 bytes |
| Compression ratio | ~87.6 : 1 |
| Compression method | LZMA2:24 |
| Solid archive | No |
| Encrypted | **No** (`Encrypted = -` in the archive header - no password
  required to list or extract) |
| CRC | B85DE545 |
| Inner file Modified timestamp | 2026-09-17 01:00:12 (JST-consistent with
  a dump taken just after 2026-09-16 business close) |

The extreme compression ratio (~88:1) is consistent with a plain-text SQL
dump (`mysqldump`-style repetitive `INSERT INTO ... VALUES (...)` text
compresses this well; a binary/physical dump format typically would not),
but this is an inference from the compression ratio alone, not a confirmed
fact - actual content was never read.

## 3. Backup Format (inferred, unconfirmed)

Per this project's own specification (`docs/GSYS_Specification.md` line 33,
79): the Legacy Gulliver DB is **MySQL 8.x**, schema name literally `goo`.
The `.dmp` extension and `goo_` filename prefix exactly match the
already-documented prior backup (`goo_20251126.dmp`), strongly suggesting
this file is the same kind of artifact - most likely a `mysqldump` (or
similar MySQL export tool) output of the `goo` schema.

**Not yet confirmed** (requires Stage 1 extraction, out of scope here):
exact dump tool/format (`mysqldump` plain SQL vs. a binary/physical dump),
exact MySQL server version the dump was taken from, exact schema/table list,
charset/collation, or any dump-tool header metadata. Per this task's own
explicit instruction ("まだ展開しない"), the file was not decompressed even
partially to check these - they are Stage 1 items.

## 4. Local Environment Collision Check

Confirmed via `docker ps -a` and `netstat`, read-only:

| Existing instance | Type | Port | Database name(s) |
|---|---|---|---|
| `gsys-prototype-postgres` | PostgreSQL 16 | 54321 | `gsys_portal` (G-OPS Portal DB) |
| `gsys-legacy-demo-mysql` | MySQL 8.0.46 | 33061 | `legacy_demo` |

Both containers are currently running/healthy and were left completely
untouched - no command was issued against either beyond a read-only
`SHOW DATABASES`/`docker ps` for this collision check. No existing local
database is named `goo`, `goo_prod_snapshot_*`, or anything resembling the
proposed isolated name below - **zero collision risk** with the proposed
design in §5. Port 3306 (MySQL's default) and the proposed isolated port
(§5) are both currently free (`netstat` shows nothing listening on either).
Disk: 274 GB free on the `C:` volume - comfortably sufficient for a ~5 GB
dump plus a restored copy with indexes.

## 5. Isolated Restore Proposal (design only - not executed)

```
goo_20260916.7z (this backup)
        │  (Stage 1, pending Go decision)
        ▼
Dedicated, NEW local MySQL container/instance
        │
        ▼
Dedicated database name: goo_prod_snapshot_20260916
```

- **New, separate Docker container** (e.g. `gsys-prod-snapshot-mysql`,
  `mysql:8.0` image matching the Legacy Demo instance's own major version
  unless Stage 1's extraction reveals a different source version) - never
  reusing `gsys-legacy-demo-mysql`.
- **Dedicated port**: `33199` (clearly distinct from the existing `33061`
  Legacy Demo port and the default `3306`, avoiding any near-miss
  confusion between "the demo instance" and "the real snapshot instance").
- **Dedicated database name**: `goo_prod_snapshot_20260916` - distinct from
  `goo` (the name the dump's own schema likely uses internally) so that
  even the restore command's own target name makes the snapshot's identity
  and date unambiguous at a glance, and distinct from `legacy_demo` and any
  past `goo_local`-style dev database.
- **No G-OPS backend configuration** would point at this instance at any
  point in Stage 1 - it exists solely for standalone, manual SQL audit
  querying, never wired into any Spring profile/datasource.

## 6. Read-Only Audit Proposal (design only - not executed)

- After an eventual restore, a **dedicated MySQL user** would be created
  with `SELECT`-only privileges scoped to `goo_prod_snapshot_20260916`
  alone (e.g. `GRANT SELECT ON goo_prod_snapshot_20260916.* TO
  'goo_snapshot_reader'@'localhost'`).
- The restore/admin user (`root` or equivalent) would **never** be used for
  the audit queries themselves - only for the one-time restore and user
  creation.
- All audit queries would be plain `SELECT`/`COUNT`/`GROUP BY` - no
  `UPDATE`/`DELETE`/`INSERT`/DDL of any kind against the snapshot.

## 7. Planned Queries (to run only after Stage 1 restore + Go decision)

Organized to answer §6 A-I of the task instruction once data is available:

- **A. Cardinality**: `SELECT COUNT(*)` per core table (Supplier, Brand,
  SKU/Item, Stock, PO header, PO detail, Arrival).
- **B. Supplier/Brand cardinality**: `GROUP BY` + `COUNT(DISTINCT ...)` in
  both directions to establish real 1:1 / 1:N / N:M shape, not assumed from
  code alone.
- **C. Manufacturer vs. Supplier**: compare whether Legacy actually
  maintains these as distinct concepts/tables or one overloaded one.
- **D. Official PO number**: `LENGTH()`/regex pattern distribution,
  `COUNT(*) - COUNT(DISTINCT ...)` for duplicates, `COUNT(*) WHERE ... IS
  NULL`, with any reported examples anonymized/minimal per §8 below.
- **E. Stock/Sales**: `MS_STK` row volume, `SOLD_QTY` distribution
  (min/max/percentiles), NULL rates, `UPDATE_DATETIME` recency/range.
- **F. Arrival**: `TR_ARR` ETA/ETA_WH population rate (how much is real vs.
  NULL in production, unlike the Prototype's own thinner seed data).
- **G. Item Status**: `MS_ITEM.ITEM_STATUS`/`DISCON` actual value
  distribution and counts.
- **H. Scale**: row counts for Order History/Candidate/Stock-Sales-shaped
  queries specifically to inform whether G-OPS's existing Pagination/Search
  design holds up at real Production volume.
- **I. Data Quality**: NULL/blank/duplicate/unexpected-code/orphan-FK scans
  across the tables above.

None of these have been run - no restore has happened yet.

## 8. Data Handling Precautions

- This document and any future audit report will contain **only**
  aggregate counts, distributions, schema/relationship facts, and, where an
  example is genuinely needed, a minimally-anonymized one (e.g. a PO number
  *pattern*, never a real full PO number tied to a real order).
- No personal names, email addresses, phone numbers, postal addresses,
  credentials, or secrets will be transcribed into any committed document,
  in this Stage or any future one.
- The archive and any future extracted `.dmp`/restored DB are treated as
  containing real customer/business data at all times, regardless of how
  the file is labeled.

## 9. Risks

- **Accidental Git commit of real Production data**: mitigated now by an
  explicit `.gitignore` addition (`docs/real-data-audit/*.7z`, `*.dmp`,
  `*.sql`) so this file - and any future extracted dump - can never be
  staged even by a broad `git add`, not just by manual discipline. `git
  status` confirms `docs/real-data-audit/` was untracked before this
  change and remains untracked/ignored after it.
- **Accidental collision with existing Demo/Test DBs**: mitigated by the
  dedicated-instance, dedicated-port, dedicated-name design in §5 - no
  Stage 1 action would ever target `legacy_demo` or the Portal Postgres
  instance.
- **Unconfirmed dump format**: the exact tool/format is still an inference
  (§3) - Stage 1's first step should be a minimal, still-read-only format
  confirmation (e.g. reading just the dump's own header/first lines) before
  attempting a full restore, in case it is not plain MySQL SQL text.
- **Size**: ~4.86 GB uncompressed dump; disk capacity is not a concern
  (274 GB free), but a full logical restore of this size may take a
  non-trivial amount of time - worth sizing expectations before Stage 1.

## 10. Go / No-Go Recommendation for Restore

**No blocking issue was found during this Stage 0 inspection**: the archive
is not encrypted, contains exactly one file matching this project's own
already-established backup-naming convention, no local collision risk
exists, and a concrete isolated restore design (dedicated instance/port/DB
name, dedicated read-only audit user) is ready. The only open item is
confirming the exact dump format/tool at the very start of Stage 1 (still
read-only, before any actual restore command).

Recommendation: **READY FOR ISOLATED RESTORE**, pending explicit
ChatGPT/Techlead review and go-ahead - Stage 1 has not been started and
will not start without that explicit instruction.
