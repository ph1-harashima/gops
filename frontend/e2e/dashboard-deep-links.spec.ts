import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 6-D: Dashboard Deep Link audit
 * (docs/production-ux-workflow-redesign.md 10章). Confirms each KPI/Brand
 * row Deep Link both applies the right Filter AND that the resulting List
 * count matches the number shown on the Dashboard - the "非常に重要" check
 * from the implementation instructions (11章). All Filters reuse Phase
 * 6-A's URL Query Parameter mechanism; nothing here talks to a new Backend
 * endpoint.
 */

const DEMO_USERNAME = 'purchase01'
const DEMO_PASSWORD = 'DemoPass123!'

async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(DEMO_USERNAME)
  await page.getByLabel('パスワード').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

/** The clickable KPI tile itself - matched by its Label's EXACT text (MUI
 * Paper wraps an h4 value + a body2 Label; "欠品" and "長期欠品" would both
 * substring-match a loose lookup, so this must be exact). */
function kpiTile(page: Page, label: string) {
  // Narrow to Paper elements with an <h4> (the 6 KPI tiles) BEFORE the exact
  // Label match - the Brand breakdown table below is also a MuiPaper-root
  // and contains every Label as a column header, but has no <h4>.
  return page.locator('.MuiPaper-root').filter({ has: page.locator('h4') }).filter({ has: page.getByText(label, { exact: true }) })
}

async function kpiValue(page: Page, label: string): Promise<number> {
  const text = await kpiTile(page, label).locator('h4').textContent()
  return Number(text?.trim() ?? 'NaN')
}

async function waitForListSettled(page: Page, emptyText: RegExp) {
  await expect(page.getByText(emptyText)).toBeVisible()
}

test.describe('Phase 6-D: Dashboard Deep Links', () => {
  /** Freeze Blocker-1: the KPI used to jump straight to the all-Brand
   * flat SKU List (?recommendedOnly=true), the one entry point into
   * 発注候補 that skipped the Brand-first landing every other entry point
   * (Global Nav, Brand row "戻る") already used. It now lands on the Brand
   * List first, same as those - the all-Brand flat List is still reachable,
   * just one explicit "すべての発注候補を表示" click away, where the same
   * count-matches check this test always did still applies. */
  test('発注候補 KPI -> Brand一覧 -> すべての発注候補を表示 -> Candidate List, count matches', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '発注候補')

    await kpiTile(page, '発注候補').click()
    await expect(page).toHaveURL(/\/candidates$/)
    await expect(page.getByTestId('order-candidate-brand-list-table-container')).toBeVisible()

    await page.getByTestId('candidates-view-all-button').click()
    await expect(page).toHaveURL(/\/candidates\?recommendedOnly=true/)
    await expect(page.getByTestId('recommended-only-checkbox').locator('input')).toBeChecked()
    await waitForListSettled(page, /件の発注候補|発注候補が見つかりませんでした/)

    const resultText = await page.getByText(/件の発注候補/).textContent()
    const actual = Number(resultText?.match(/(\d+)件/)?.[1])
    expect(actual).toBe(expected)
  })

  test('発注作成中 KPI -> Order List with status=DRAFT, count matches', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '発注作成中')

    await kpiTile(page, '発注作成中').click()
    await expect(page).toHaveURL(/\/orders\/history\?status=DRAFT/)
    await waitForListSettled(page, /件の発注|該当する発注が見つかりませんでした/)

    if (expected === 0) {
      await expect(page.getByText('該当する発注が見つかりませんでした。')).toBeVisible()
    } else {
      const resultText = await page.getByText(/件の発注$|件の発注[^候]/).first().textContent()
      const actual = Number(resultText?.match(/(\d+)件/)?.[1])
      expect(actual).toBe(expected)
    }
  })

  test('承認待ち KPI -> Order List with status=PENDING_APPROVAL, count matches (Phase 7-C1 14章)', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '承認待ち')
    test.skip(expected === 0, 'No PENDING_APPROVAL orders in the current Demo Data')

    await kpiTile(page, '承認待ち').click()
    await expect(page).toHaveURL(/\/orders\/history\?status=PENDING_APPROVAL/)
    await waitForListSettled(page, /件の発注/)
    const resultText = await page.getByText(/件の発注$|件の発注[^候]/).first().textContent()
    const actual = Number(resultText?.match(/(\d+)件/)?.[1])
    expect(actual).toBe(expected)
  })

  test('メーカー回答待ち KPI -> Order List with status=AWAITING_SUPPLIER, count matches, and leads on into Supplier Response', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, 'メーカー回答待ち')
    test.skip(expected === 0, 'No AWAITING_SUPPLIER orders in the current Demo Data')

    await kpiTile(page, 'メーカー回答待ち').click()
    await expect(page).toHaveURL(/\/orders\/history\?status=AWAITING_SUPPLIER/)
    await waitForListSettled(page, /件の発注/)
    const resultText = await page.getByText(/件の発注$|件の発注[^候]/).first().textContent()
    const actual = Number(resultText?.match(/(\d+)件/)?.[1])
    expect(actual).toBe(expected)

    // 発注一覧 -> 発注詳細 -> メーカー回答を入力 (5章)
    await page.locator('table tbody tr').first().click()
    await expect(page).toHaveURL(/\/orders\/\d+/)
    const action = page.getByTestId('order-detail-primary-action')
    await expect(action).toHaveText('メーカー回答を入力')
    await action.click()
    await expect(page).toHaveURL(/\/supplier-response/)
  })

  test('要確認 KPI -> Order List with hasAttention=true, count matches', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '要確認')
    test.skip(expected === 0, 'No orders with an active Attention in the current Demo Data')

    await kpiTile(page, '要確認').click()
    await expect(page).toHaveURL(/\/orders\/history\?hasAttention=true/)
    await expect(page.getByTestId('has-attention-only-checkbox').locator('input')).toBeChecked()
    await waitForListSettled(page, /件の発注/)
    const resultText = await page.getByText(/件の発注$|件の発注[^候]/).first().textContent()
    const actual = Number(resultText?.match(/(\d+)件/)?.[1])
    expect(actual).toBe(expected)

    // Every visible row must actually carry an Attention badge - the
    // Filter must narrow, not just decorate.
    const rowCount = await page.locator('table tbody tr').count()
    for (let i = 0; i < rowCount; i++) {
      await expect(page.locator('table tbody tr').nth(i).locator('.MuiChip-root').first()).toBeVisible()
    }
  })

  // Phase 7-F Header/List UX Audit: previously this test asserted the KNOWN
  // GAP (unfiltered List, count deliberately not matching) as the expected
  // behavior. CandidateListPage now has outOfStockOnly/longTermOutOfStockOnly
  // client-side Filters reusing the EXACT SAME provisional Predicate
  // DashboardService already counts with (no Business Rule change - see
  // that Filter's own comment) - so both KPIs' Deep Link now lands on a
  // correctly pre-filtered, count-matching List instead.
  test('欠品 KPI -> Candidate List with outOfStockOnly, count matches', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '欠品')

    await kpiTile(page, '欠品').click()
    await expect(page).toHaveURL(/\/candidates\?outOfStockOnly=true/)
    await expect(page.getByTestId('out-of-stock-only-checkbox').locator('input')).toBeChecked()
    await expect(page.getByTestId('filter-chip-outOfStockOnly')).toBeVisible()
    await waitForListSettled(page, /件の発注候補|発注候補が見つかりませんでした/)

    const resultText = await page.getByText(/件の発注候補/).textContent()
    expect(Number(resultText?.match(/(\d+)件/)?.[1])).toBe(expected)
  })

  test('長期欠品 KPI -> Candidate List with longTermOutOfStockOnly, count matches', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '長期欠品')

    await kpiTile(page, '長期欠品').click()
    await expect(page).toHaveURL(/\/candidates\?longTermOutOfStockOnly=true/)
    await expect(page.getByTestId('long-term-out-of-stock-only-checkbox').locator('input')).toBeChecked()
    await expect(page.getByTestId('filter-chip-longTermOutOfStockOnly')).toBeVisible()
    await waitForListSettled(page, /件の発注候補|発注候補が見つかりませんでした/)

    const resultText = await page.getByText(/件の発注候補/).textContent()
    expect(Number(resultText?.match(/(\d+)件/)?.[1])).toBe(expected)
  })

  test('Brand row Deep Links: 発注候補 carries recommendedOnly, 欠品 carries outOfStockOnly, 要確認 carries hasAttention', async ({ page }) => {
    await login(page)
    const brandNameCell = page.locator('table tbody tr').first().locator('td').first()
    const brandName = (await brandNameCell.textContent())?.trim()

    const firstBrandCandidateCell = page.locator('table tbody tr').first().locator('td').nth(1).getByRole('button')
    const brandCandidateCount = Number((await firstBrandCandidateCell.textContent())?.trim())
    await firstBrandCandidateCell.click()
    await expect(page).toHaveURL(/\/candidates\?brandCode=.+&recommendedOnly=true/)
    await waitForListSettled(page, /件の発注候補|発注候補が見つかりませんでした/)
    if (brandCandidateCount > 0) {
      const resultText = await page.getByText(/件の発注候補/).textContent()
      expect(Number(resultText?.match(/(\d+)件/)?.[1])).toBe(brandCandidateCount)
    }

    // Phase 7-F: Brand + 状態 Filter Chips both visible, each independently
    // removable - the "現在の条件が見えるように" requirement.
    await expect(page.getByTestId('filter-chip-brandCode')).toContainText(brandName ?? '')
    await expect(page.getByTestId('filter-chip-recommendedOnly')).toBeVisible()

    await page.goto('/')
    const firstBrandOutOfStockCell = page.locator('table tbody tr').first().locator('td').nth(2).getByRole('button')
    const brandOutOfStockCount = Number((await firstBrandOutOfStockCell.textContent())?.trim())
    await firstBrandOutOfStockCell.click()
    await expect(page).toHaveURL(/\/candidates\?brandCode=.+&outOfStockOnly=true/)
    await waitForListSettled(page, /件の発注候補|発注候補が見つかりませんでした/)
    if (brandOutOfStockCount > 0) {
      const resultText = await page.getByText(/件の発注候補/).textContent()
      expect(Number(resultText?.match(/(\d+)件/)?.[1])).toBe(brandOutOfStockCount)
    }
    await expect(page.getByTestId('filter-chip-brandCode')).toContainText(brandName ?? '')
    await expect(page.getByTestId('filter-chip-outOfStockOnly')).toBeVisible()

    // Removing the 状態 Chip drops that Filter but keeps Brand. MUI's Chip
    // delete affordance is its `MuiChip-deleteIcon` SVG (aria-hidden, no own
    // role) - the outer Chip itself carries role="button" for keyboard
    // support, so the click must target the icon specifically, not the
    // whole Chip (which has no onClick of its own and would no-op).
    await page.getByTestId('filter-chip-outOfStockOnly').locator('.MuiChip-deleteIcon').click()
    await expect(page).not.toHaveURL(/outOfStockOnly=true/)
    await expect(page.getByTestId('filter-chip-brandCode')).toBeVisible()

    await page.goto('/')
    const firstBrandAttentionCell = page.locator('table tbody tr').first().locator('td').nth(5).getByRole('button')
    await firstBrandAttentionCell.click()
    await expect(page).toHaveURL(/\/orders\/history\?brandCode=.+&hasAttention=true/)
  })

  /**
   * G-OPS Brand -> Order Candidates 業務導線監査: the Requirement's own
   * primary flow (`G-SYS_Online-Ordering_Prototype_Requirements.md` 10章
   * 「Brandを選択すると、発注候補SKU一覧へ遷移する」) is plain Brand Name
   * selection - no additional Filter (`recommendedOnly` etc.) attached -
   * showing every one of that Brand's Candidate SKUs. The existing "Brand
   * row Deep Links" test above only ever clicks the Candidates COUNT cell
   * (which deliberately carries `recommendedOnly=true`), so this literal
   * Requirement flow had no dedicated coverage. Also confirms the Brand
   * Filter survives a SKU Detail round trip AND a Draft creation round
   * trip (Navigation Context / Phase 6-A returnTo, Phase 8-M), and that the
   * KPI's own all-Brand entry point (Section E: 全候補一覧) is untouched by
   * any of this.
   */
  test('Scenario 1-5: Dashboard Brand Name click -> Brand-only Candidate List, Context survives SKU Detail / Draft Back, all-Brand KPI entry lands on Brand List first (Freeze Blocker-1)', async ({ page }) => {
    await login(page)
    const brandNameCell = page.locator('table tbody tr').first().locator('td').first().getByRole('button')
    const brandName = (await brandNameCell.textContent())?.trim()

    // Scenario 1/2: plain Brand Name click -> /candidates?brandCode=X ONLY
    // (no recommendedOnly/outOfStockOnly riding along) -> every visible row
    // belongs to that exact Brand.
    await brandNameCell.click()
    await expect(page).toHaveURL(/\/candidates\?brandCode=[^&]+$/)
    const brandFilteredListUrl = page.url()

    await expect(page.getByTestId('filter-chip-brandCode')).toContainText(brandName ?? '')
    // Stage 4 Targeted Real-Data Remediation: the Brand Filter is now a
    // typed code TextField, not a Select (Candidate List Backend
    // Pagination means the full Brand option list is no longer fetched
    // client-side - see CandidateListPage.tsx's own comment on this same
    // change) - it shows the raw brandCode query param value, same
    // convention useStockSalesList's own Brand/Supplier fields already
    // use. The resolved NAME is still checked immediately above via the
    // Filter Chip (which resolves it via Dashboard's own Brand list,
    // unaffected by this change) - this assertion is now scoped to what a
    // plain TextField can actually show.
    const brandCode = new URL(brandFilteredListUrl).searchParams.get('brandCode')
    await expect(page.getByTestId('candidate-filter-brand').locator('input')).toHaveValue(brandCode ?? '')

    await expect(page.getByText(/件の発注候補|発注候補が見つかりませんでした/)).toBeVisible()
    const brandColumnCells = page.locator('table tbody tr td:nth-child(4)')
    const rowCount = await brandColumnCells.count()
    for (let i = 0; i < rowCount; i++) {
      await expect(brandColumnCells.nth(i)).toHaveText(brandName ?? '')
    }

    // Scenario 3: SKU Detail -> Back preserves the Brand-only Filter.
    const firstSkuButton = page.locator('table tbody tr').first().locator('td').nth(1).getByRole('button')
    await firstSkuButton.click()
    await expect(page).toHaveURL(/\/items\/.+returnTo=.*brandCode/)
    await page.getByRole('button', { name: '発注候補一覧へ戻る' }).click()
    await expect(page).toHaveURL(brandFilteredListUrl)
    await expect(page.getByTestId('filter-chip-brandCode')).toContainText(brandName ?? '')

    // Scenario 4: Draft creation -> Back preserves the Brand-only Filter
    // (returnTo carries brandCode "as far as possible", per the
    // Requirement's own "可能な範囲でBrand Context維持").
    const firstCheckbox = page.locator('table tbody tr').first().locator('input[type="checkbox"]')
    await firstCheckbox.check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+\?returnTo=.*brandCode/)
    await page.getByRole('button', { name: '発注候補一覧へ戻る' }).click()
    await expect(page).toHaveURL(brandFilteredListUrl)
    await expect(page.getByTestId('filter-chip-brandCode')).toContainText(brandName ?? '')

    // Scenario 5 (Freeze Blocker-1 fix): the Dashboard's own all-Brand KPI
    // entry (発注候補 tile) now lands on the Brand List first, the SAME
    // Brand-first entry point every other 発注候補 route (Global Nav, this
    // very Brand-row flow) already used - it no longer skips straight to an
    // unfiltered-by-Brand flat Candidate List. Brand selection is still
    // optional from there (the Brand List's own "すべての発注候補を表示"
    // button reaches the all-Brand flat List, unchanged) - just no longer
    // the KPI's own default destination.
    await page.goto('/')
    await kpiTile(page, '発注候補').click()
    await expect(page).toHaveURL(/\/candidates$/)
    await expect(page.getByTestId('order-candidate-brand-list-table-container')).toBeVisible()
  })

  test('Browser Back/Forward preserves the Dashboard Deep Link Filter', async ({ page }) => {
    await login(page)
    await kpiTile(page, 'メーカー回答待ち').click()
    const filteredUrl = page.url()
    await page.goBack()
    await expect(page).toHaveURL(/\/$/)
    await page.goForward()
    await expect(page).toHaveURL(filteredUrl)
  })

  /** Phase 8-J 11章/13章: Dashboard's information design predated Phase
   * 8-B/8-D (Price Change Foundation) - this is the first Dashboard entry
   * point into it. */
  test('価格変更（下書き） KPI -> Price Change List with status=DRAFT, count matches', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '価格変更（下書き）')

    await kpiTile(page, '価格変更（下書き）').click()
    await expect(page).toHaveURL(/\/price-changes\?status=DRAFT/)
    // Freeze Blocker-2 audit (docs/gops-phase1-final-cleanup-report.md):
    // this test's own manual "read 読み込み中 -> then call the ROW locator's
    // .count() once" idiom is not auto-retrying - a `.count()` call sampled
    // the instant the loading spinner disappears can still read 0 if
    // React hasn't finished the next paint yet, independent of how many
    // rows actually exist (confirmed live via Browser: the same navigation
    // reliably rendered all rows correctly, just not always within that one
    // synchronous instant). `toHaveCount` polls/retries until it matches or
    // times out - the correct primitive for "eventually this many rows",
    // matching every other List assertion in this suite.
    await expect(page.locator('[data-testid="price-change-list-table-container"] table tbody tr')).toHaveCount(expected, { timeout: 20000 })
  })

  /** Phase 8-J 11章/13章: plain Navigation Cards (no count) into Arrival/
   * Warehouse Stock/Stock-Sales - Dashboard had zero entry point into any
   * of these 3 screens (Phase 8-G/8-H) until this Phase. */
  test('Navigation Cards reach 入荷確認/倉庫在庫/在庫・販売確認, with no count attached to any of them', async ({ page }) => {
    await login(page)

    await expect(page.getByTestId('nav-card-arrivals')).toBeVisible()
    await expect(page.getByTestId('nav-card-warehouseStock')).toBeVisible()
    await expect(page.getByTestId('nav-card-stockSales')).toBeVisible()
    // No <h4> (the KPI tiles' value element) inside any Navigation Card -
    // confirms these are genuinely count-less per §13's "plain Navigation
    // Card" allowance, not a KPI tile in disguise.
    await expect(page.getByTestId('nav-card-arrivals').locator('h4')).toHaveCount(0)

    await page.getByTestId('nav-card-arrivals').click()
    await expect(page).toHaveURL(/\/arrivals$/)

    await page.goto('/')
    await page.getByTestId('nav-card-warehouseStock').click()
    await expect(page).toHaveURL(/\/warehouse-stock$/)

    await page.goto('/')
    await page.getByTestId('nav-card-stockSales').click()
    await expect(page).toHaveURL(/\/stock-sales$/)
  })
})
