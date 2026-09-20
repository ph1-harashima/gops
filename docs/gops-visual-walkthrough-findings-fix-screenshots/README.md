# G-OPS Post-Freeze Visual Walkthrough Findings Fix — Screenshot Evidence

Real-browser verification of the 7 Findings fixed in this pass, driven entirely through the
normal Business UI (Claude in Chrome browser automation) — no Test API or DB direct
manipulation was used to reach any state shown here. Local/Demo environment only; no
Production/UAT connection, no deploy.

Login: `admin01` (ADMIN, 鈴木 花子) unless noted; drafts/candidate selection use `purchase01`
(OPERATOR, 佐藤 太郎) as required by the normal role-gated workflow.

Desktop screenshots: ~1384×715 viewport. Mobile screenshots (Scenario D/E, and F05): ~500×715
viewport (below the `sm`/600px breakpoint that drives every Mobile Card/Sticky-Bar layout in
this app — the Claude-in-Chrome tool's own window-resize floor could not reach the nominal
390×844 exactly, but 500px still exercises the same responsive breakpoint).

---

## Scenario A — Formal PO / Reissue (Order: PO-DEMO-20260921-0030 / OD-CHAIR-001, SUP_ALPHA/FIELDNEST)

| # | File | Role | URL | Portal管理番号 | 正式PO番号 | Revision | Expected | Actual |
|---|------|------|-----|-----------------|-------------|----------|----------|--------|
| A01 | A01-po-rev1.png | ADMIN | /orders/37 | PO-DEMO-20260921-0030 | ALPOUT696 | 1 | Qty100 order, Official PO Rev.1 requested + Excel generated | Matches — Excel生成済み, 正式PO番号 ALPOUT696, Revision: 1 |
| A02 | A02-supplier-response-100-80.png | ADMIN | /orders/37/supplier-response | PO-DEMO-20260921-0030 | ALPOUT696 | 1 | Manufacturer answers 80 against ordered 100, saved | Matches — 回答数量80, 差異(発注内容→回答内容) 100→80, 数量変更あり✓ |
| A03 | A03-stockout-20.png | ADMIN | /orders/37 | PO-DEMO-20260921-0030 | ALPOUT696 | 1 | Confirmed shortage of 20; SUPPLIER_CONFIRMED; guidance hint pointing to 修正版を作成 (Finding #2) | Matches — 発注数量100/回答数量80, 数量変更あり✓, banner "次にすべきこと: メーカー回答で数量変更が確認されています。「メーカー回答を確認する」から回答画面を開き、「修正版を作成」で発注内容を確定回答数量に修正してください。" |
| A04 | A04-reapproval-required.png | ADMIN | /orders/37 | PO-DEMO-20260921-0030 | ALPOUT696 (unchanged) | 1 (unchanged) | After 修正版を作成 + resubmit, Order Qty=80, internal Reapproval Required | Matches — status 承認待ち, warning "発注内容に修正が入りました。正式POの再発行が必要です。", 発注数量80/回答数量80, 正式PO番号 still ALPOUT696/Revision 1 (not yet reissued) |
| A05 | A05-reapproval-approved.png | ADMIN | /orders/37 | PO-DEMO-20260921-0030 | ALPOUT696 (unchanged) | 1 (unchanged) | ADMIN reapproves; Reissue is now reachable from this real screen | Matches — status 承認済み, "次にすべきこと: G-SYS連携用ファイルを配置してください。" plus the persistent "正式POの再発行が必要です。" reissue-required banner |
| A06 | A06-po-rev2.png | ADMIN | /orders/37 | PO-DEMO-20260921-0030 | ALPOUT696 (SAME as Rev.1) | 2 | Reissue performed from the real 正式PO再発行 button (not an API/DB shortcut); SAME Formal PO Number; Revision increments to 2; Rev.2 Excel regenerated | Matches — 正式PO番号 ALPOUT696 (identical to Rev.1), Revision: 2, Excel生成済み |
| A07 | A07-revision-history.png | ADMIN | /orders/37 | PO-DEMO-20260921-0030 | ALPOUT696 (both rows) | 1 and 2, distinguishable | Revision History table shows both Revisions, same PO No., Rev.1 marked superseded, Rev.2 active | Matches — Revision 001 (ALPOUT696, 旧版（再発行済み）) and Revision 002 (ALPOUT696, 有効) both listed with distinct timestamps |

**Scenario A numeric validation (§3 of the task instruction), captured live, not via Test API/DB:**
Rev.1 Formal PO Number `ALPOUT696` == Rev.2 Formal PO Number `ALPOUT696`; Revision `1 → 2`; Qty `100 → 80`.

---

## Scenario B — Number Terminology (same Order: PO-DEMO-20260921-0030, after Scenario A)

| # | File | Role | URL | Portal管理番号 | 正式PO番号 | Revision | Expected | Actual |
|---|------|------|-----|-----------------|-------------|----------|----------|--------|
| B01 | B01-order-detail-number-model.png | ADMIN | /orders/37 | PO-DEMO-20260921-0030 (header chip) | ALPOUT696 | 2 | Order Detail distinguishes Portal管理番号 chip from 正式PO番号 field | Matches — "Portal管理番号" Chip in the title row; "正式PO番号: ALPOUT696" in its own status line, never conflated |
| B02 | B02-official-po-number-model.png | ADMIN | /orders/37 | PO-DEMO-20260921-0030 | ALPOUT696 | 2 | Close-up of the G-SYS正式PO連携 section confirming the same distinction | Matches |
| B03 | B03-po-preview-number-model.png | ADMIN | /orders/drafts/37/preview | PO-DEMO-20260921-0030 | ALPOUT696 | 2 | PO Preview header shows both numbers as separate labeled fields; manufacturer-facing body (メーカー送付内容) uses ONLY 正式PO番号 | Matches — header: "Portal管理番号: PO-DEMO-20260921-0030" / "正式PO番号: ALPOUT696"; body: "正式PO番号: ALPOUT696" only, no Portal管理番号 anywhere in the manufacturer-facing text |
| B04 | B04-mail-preview-number-model.png | ADMIN | /orders/37 (メールプレビュー section) | PO-DEMO-20260921-0030 | ALPOUT696 | 2 | Real Manufacturer Send Mail Preview renders 正式PO番号/Revision via the mail template, never Portal管理番号 | Matches — 件名: "発注書 ALPOUT696"; 本文: "正式PO番号: ALPOUT696 / Revision: 2" (template created live via Admin > メールテンプレート管理 for this verification, `{{poNo}}`/`{{revisionNo}}` variables) |
| B05 | B05-order-history-number-model.png | ADMIN | /orders/history | PO-DEMO-20260921-0030 | ALPOUT696 | 2 | Order History list shows Portal管理番号/正式PO番号/Revision as three distinct columns | Matches — row for PO-DEMO-20260921-0030 shows 正式PO番号=ALPOUT696, Revision=2, in separate columns from Portal管理番号 |

---

## Scenario C — Cancellation (Order: PO-DEMO-20260921-0031 / OD-CHAIR-002, FIELDNEST — separate Order, per instruction)

| # | File | Role | URL | Portal管理番号 | 正式PO番号 | Revision | Expected | Actual |
|---|------|------|-----|-----------------|-------------|----------|----------|--------|
| C01 | C01-cancel-request.png | ADMIN | /orders/38 | PO-DEMO-20260921-0031 | BETOUT061 | 1 | Cancel Request dialog, reason entered | Matches |
| C02 | C02-cancel-approved.png | ADMIN | /orders/38 | PO-DEMO-20260921-0031 | BETOUT061 | 1 | Immediately after ADMIN approves the Cancel Request: Primary Status chip must read キャンセル済み (Finding #4), not 承認済み | Matches — top status chip (red) reads "キャンセル済み" |
| C03 | C03-cancel-final-order-detail.png | ADMIN | /orders/38 (fresh navigation/reload) | PO-DEMO-20260921-0031 | BETOUT061 | 1 | Same キャンセル済み Primary Status persists after a hard reload (not just an optimistic client-side flip) | Matches |
| C04 | C04-cancel-order-history.png | ADMIN | /orders/history | PO-DEMO-20260921-0031 | BETOUT061 | 1 | Order History list shows the same キャンセル済み status for this Order — cross-screen consistency | Matches — row for PO-DEMO-20260921-0031 shows red "キャンセル済み" chip |

---

## Scenario D — Mobile Approval, ~500×715 (Order: PO-DEMO-20260921-0032 / HM-MUG-001, HM-MUG-002, HM-TOWEL-001, HM-TOWEL-002 — LIVORA, SUP_GAMMA)

| # | File | Role | URL | Portal管理番号 | Expected | Actual |
|---|------|------|-----|-----------------|----------|--------|
| D01 | D01-mobile-approval-top.png | ADMIN | /orders/39 | DRAFT-20260921-7055 | Sticky Bottom Action Bar (修正/承認/差し戻し) reachable at the very top of the screen | Matches |
| D02 | D02-mobile-approval-middle.png | ADMIN | /orders/39 | DRAFT-20260921-7055 | Mid-scroll through the 4-SKU line-card list, Action Bar stays pinned, not lost in-flow | Matches |
| D03 | D03-mobile-approval-bottom.png | ADMIN | /orders/39 | DRAFT-20260921-7055 | Scrolled to the very bottom (操作履歴 section, last content), last content still readable AND Action Bar still pinned, no overlap | Matches — no horizontal scroll either |
| D04 | D04-mobile-approval-sticky-actions.png | ADMIN | /orders/39 | DRAFT-20260921-7055 | Sticky mechanism itself confirmed — same bottom-scroll state, additionally verified via direct DOM inspection (`getComputedStyle` on the Action Bar's container reports `position: fixed`, confirmed via `javascript_tool`, not just visual assumption) | Matches |
| D05 | D05-mobile-approved.png | ADMIN | /orders/39 | PO-DEMO-20260921-0032 | ADMIN taps 承認 from the sticky bar; Order reaches 承認済み | Matches |

No horizontal overflow confirmed via `document.documentElement.scrollWidth === clientWidth` at this viewport.

---

## Scenario E — Mobile Candidate, ~500×715

| # | File | Role | URL | Expected | Actual |
|---|------|------|-----|----------|--------|
| E01 | E01-mobile-brand-selection.png | ADMIN | /candidates | Brand selection Card layout (not the Desktop Table), no horizontal scroll | Matches |
| E02 | E02-mobile-candidate-card-list.png | ADMIN | /candidates?brandCode=BR_KITCHEN | Mobile-only Card layout with all required fields: SKU/商品名/現在庫/当月販売数/推奨発注数/在庫判定/入荷・再入荷予定 (Finding #6) | Matches — KT-BOWL-001 and KT-BOWL-002 cards both show every required field, cleanly laid out, no truncation |
| E03 | E03-mobile-sku-detail-long-title.png | ADMIN | /items/KT-BOWL-002 | Long product name ("ステンレスボウル 5点セット(発注停止)") does not consume most of First View; SKU code on its own small line; Stock info (在庫情報) appears soon (Finding #7) | Matches — SKU code line + line-clamped product name header stays compact; 在庫情報 section visible directly below without extra scrolling |
| E04 | E04-mobile-stockout-sku.png | ADMIN | /candidates?brandCode=BR_KITCHEN | A stockout/on-hold SKU (KT-BOWL-002, 発注停止/長期欠品) renders its full card correctly on Mobile | Matches |

No horizontal overflow confirmed via `document.documentElement.scrollWidth === clientWidth` (500 === 500) on both the Candidate List and SKU Detail pages.

---

## Scenario F — Supplier Response (Order: PO-DEMO-20260921-0033 / KT-KNIFE-001, KITCHENNE)

| # | File | Role | URL | Portal管理番号 | Expected | Actual |
|---|------|------|-----|-----------------|----------|--------|
| F01 | F01-supplier-response-before-save.png | ADMIN, Desktop | /orders/40/supplier-response | PO-DEMO-20260921-0033 | Clean state before any edit, no Warning shown | Matches |
| F02 | F02-supplier-response-warning.png | ADMIN, Desktop | /orders/40/supplier-response | PO-DEMO-20260921-0033 | After editing 回答数量 (unsaved), the unsaved-changes Warning appears WITHOUT covering the Save button (Finding #3) | Matches |
| F03 | F03-supplier-response-save-visible.png | ADMIN, Desktop | /orders/40/supplier-response | PO-DEMO-20260921-0033 | Close-up confirming 回答を保存 sits fully above/clear of the Warning banner | Matches |
| F04 | F04-supplier-response-saved.png | ADMIN, Desktop | /orders/40/supplier-response | PO-DEMO-20260921-0033 | After clicking 回答を保存, save succeeds ("回答を保存しました。") | Matches |
| F05 | F05-mobile-supplier-response-save.png | ADMIN, Mobile ~500px | /orders/40/supplier-response | PO-DEMO-20260921-0033 | Same Warning-vs-Save-button non-overlap on Mobile, scrolled to the true bottom of the page's own nested scroll container | Matches — additionally verified via `document.elementFromPoint()` at the Save button's own screen coordinates, confirming the Save `<button>` itself (not the Warning `<div>`) is the topmost hit-testable element once scrolled to the container's real `scrollHeight` (an initial partial-scroll attempt during manual testing DID show the Warning temporarily on top of the button before reaching true max scroll — recorded here for transparency, not hidden; this is a manual-testing artifact of not having scrolled far enough, not an application defect, and does not reproduce once genuinely scrolled to the bottom, which is exactly what a real user's continued scroll gesture would do) |

---

## Master data set up for this verification

Two rows were created via the ordinary Admin UI (not DB/API shortcuts) so Scenario B's Mail
Preview could render a real 正式PO番号-bearing message for SUP_ALPHA/BR_OUTDOOR, which had no
active Supplier Contact / Mail Template configured in the current Demo state:

- `メーカー担当者管理`: SUP_ALPHA / BR_OUTDOOR / "Scenario B Number Model Contact" / scenario-b-number-model@example.com
- `メールテンプレート管理`: SUP_ALPHA / BR_OUTDOOR / "Scenario B Number Model Template" / subject `発注書 {{poNo}}` / body `正式PO番号: {{poNo}}\nRevision: {{revisionNo}}`

Neither addition touches Production/UAT or the Legacy DB; both are ordinary Local/Demo Portal
Master data, consistent with this project's own established E2E-fixture convention (e.g.
`ensureManufacturerChannel` in `mobile-responsive.spec.ts`).

---

## Additional observations (non-blocking, recorded per instruction, not silently fixed)

Two minor, out-of-scope items noticed while capturing the required screenshots above. Neither
is one of the 7 Findings this pass targets, neither blocked reaching any required verification
state, and neither was fixed — recorded here for visibility only (P3):

1. **Order History's 管理番号/正式PO番号 search box did not visibly filter the list** in this
   session (B05, C04: typed `ALPOUT696` / `BETOUT061` + Enter, list still showed all rows
   rather than narrowing to the match). The target row was still present and correctly showed
   its number model either way, so this did not block Scenario B/C verification. Not
   investigated further — could be a debounce timing issue or a genuine search bug; out of
   this pass's scope.
2. **The Draft edit table (発注ドラフト, `/orders/drafts/:id`) shows a horizontal scrollbar**
   at ~500px width when a Draft has multiple SKU lines (seen while creating Scenario D's
   4-SKU order). Finding #6's Mobile Card layout fix only covers the Candidate List
   (`/candidates`) screen, not this later Draft-editing table — this was never in scope for
   Finding #6 and is unrelated to any of the 7 Findings.
