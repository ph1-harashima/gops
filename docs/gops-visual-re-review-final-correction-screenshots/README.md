# G-OPS Visual Re-Review Final Correction — Screenshot Evidence

Real-browser verification of the 2 P2 Findings corrected in this pass
(Mobile Supplier Response UX, Order History Search), driven entirely through
the normal Business UI (Claude in Chrome). Local/Demo environment only; no
Production/UAT connection, no deploy. See
`docs/gops-visual-re-review-final-correction.md` for the full Root Cause /
Fix / Verification writeup this table summarizes.

Login: `admin01` (ADMIN, 鈴木 花子) for approval/search; drafting itself uses
`purchase01` (OPERATOR, 佐藤 太郎), matching the role-gated workflow.

---

## Finding A — Mobile Supplier Response (Order: PO-DEMO-20260921-0034 / KT-BOWL-001, KITCHENNE)

Viewport: 500×715 (Claude-in-Chrome's own window-resize floor could not reach
exactly 390×844 in this environment — same documented tooling limitation as
the prior Findings Fix round; the `sm`/600px CSS breakpoint driving the Card
layout is identical either way, and every acceptance check below was
additionally confirmed via direct DOM measurement, not visual inspection
alone).

| # | File | URL | Expected | Actual |
|---|------|-----|----------|--------|
| A01 | A01-mobile-supplier-response-top.png | /orders/41/supplier-response | Card layout for the single SKU line, SKU code and product name each fully readable on their own line, no per-character wrapping | Matches |
| A02 | A02-mobile-supplier-response-input.png | /orders/41/supplier-response | Confirmed Qty and Confirmed Delivery Date both directly editable, full-width, comfortable to read/tap | Matches — Qty `9`, Delivery Date `2026/10/10` (date entered via DOM `value`, not raw keystrokes — see §4 of the correction doc) both visible and correctly set |
| A03 | A03-mobile-supplier-response-bottom.png | /orders/41/supplier-response | Scrolled to the true bottom of the page's own scroll container (`scrollTop === scrollHeight - clientHeight`, not a fixed number of wheel ticks): 回答状況 summary, 差異 section, and Save button all visible together | Matches |
| A04 | A04-mobile-supplier-response-warning.png | /orders/41/supplier-response | Warning still shown; Save clicked for real (not just visually inspected) and succeeds | Matches — "回答を保存しました。" toast; 確認事項 correctly shows 数量変更あり/納期変更あり, 差異 reads `11 → 9` / `null → 2026-10-10` |

**Horizontal overflow**: `document.documentElement.scrollWidth === clientWidth`
(500 === 500) confirmed via `javascript_tool` throughout.

**Save hit-test**: `document.elementFromPoint()` at the Save button's own
screen center, at true max scroll, returned the `<button data-testid=
"save-response-button">` element itself (`hitTestIsBtn: true`) — the
Warning Toast (`MuiSnackbar-root`, `position: fixed`, `z-index: 1400`) does
not intercept the click.

---

## Finding B — Order History Search (Order: PO-DEMO-20260921-0030, existing from the prior round)

Viewport: 1305×924 (Desktop).

| # | File | Search input | Request parameter | Result count | Target Order | Expected | Actual |
|---|------|--------------|--------------------|---------------|---------------|----------|--------|
| B01 | B01-order-history-search-formal-po.png | `ALPOUT696` (+ Enter) | `GET /api/orders/history?orderNoKeyword=ALPOUT696&page=0&size=20` → `200` | 1 of 40 | PO-DEMO-20260921-0030 | Only the matching 正式PO番号's Order remains; every unrelated Order (ALPOUT695/697, PO-DEMO-...) disappears | Matches |
| B02 | B02-order-history-search-portal-no.png | `PO-DEMO-20260921-0030` (+ Enter) | same endpoint, `orderNoKeyword=PO-DEMO-20260921-0030` | 1 of 40 | PO-DEMO-20260921-0030 | Portal管理番号 search narrows the same way as 正式PO番号 search | Matches |
| B03 | B03-order-history-search-clear.png | (cleared, + Enter) | `GET /api/orders/history?page=0&size=20` (no `orderNoKeyword`) | 40 of 40 | — | Clearing the field and committing returns to the full unfiltered list | Matches |

Also verified live (not separately screenshotted, since the required set is
already minimal per the task's own instruction): partial-keyword search
(`ALPOUT` → 5 matching rows, case-insensitive, includes `PO-DEMO-20260921-0006`
— an Order outside Page 1 of the unfiltered 40-row list, proving the search
reaches every Page server-side) and the exact `ALPOUT694` case (network
request captured directly via `read_network_requests`, confirming the same
`GET /api/orders/history?orderNoKeyword=...` shape each time).

**Root cause of the original (misleading) screenshots**: not an application
defect — see `docs/gops-visual-re-review-final-correction.md` §5 for the
full audit trail. In short, one earlier capture never pressed Enter (this
field's own documented "commit on Enter/blur" design), and another used
click coordinates from a different window size than was actually active.
