import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 7-F: Global Header hierarchy, Sticky Table Header, and Mail
 * Template Variable UX - found via manual demo review ("どこまでがNavigation
 * Menuで、どれがUser情報か分かりにくい" / "スクロールするとColumn Headerが
 * 消える" / "業務ユーザーにSyntaxを覚えさせない").
 *
 * Same login/logout Role-switch pattern and selector policy (data-testid /
 * getByRole / getByLabel only) as the other 7-C series / 7-E specs.
 * Prerequisite: Demo Reset run first.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

async function login(page: Page, username: string, password: string) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

test.describe('Phase 7-F: Global Header hierarchy', () => {
  test('A: OPERATOR Header - Navigation Area and User/Session Area are visually distinct groups', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    const navArea = page.getByTestId('header-nav-area')
    const userArea = page.getByTestId('header-user-area')
    await expect(navArea).toBeVisible()
    await expect(userArea).toBeVisible()
    // マスタメンテナンス is ADMIN-only - absent from OPERATOR's Nav Area, but
    // Dashboard/Candidates/History are present in it (not the User Area).
    await expect(navArea.getByTestId('nav-dashboard')).toBeVisible()
    await expect(navArea.getByTestId('nav-candidates')).toBeVisible()
    await expect(navArea.getByTestId('nav-history')).toBeVisible()
    await expect(navArea.getByTestId('nav-master-maintenance')).toHaveCount(0)
    await expect(userArea.getByTestId('current-user-display')).toBeVisible()
    await expect(userArea.getByTestId('current-user-role')).toBeVisible()
    await expect(userArea.getByTestId('nav-logout')).toBeVisible()
    // A Divider physically separates the two groups.
    await expect(page.getByTestId('header-area-divider')).toBeVisible()
  })

  test('B: ADMIN Header - user name / Role / Logout read as one group, and マスタメンテナンス is in the Nav Area', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)

    const navArea = page.getByTestId('header-nav-area')
    const userArea = page.getByTestId('header-user-area')
    await expect(navArea.getByTestId('nav-master-maintenance')).toBeVisible()
    await expect(userArea.getByTestId('current-user-display')).toHaveText('鈴木 花子')
    await expect(userArea.getByTestId('current-user-role')).toHaveText('管理者')
    await expect(userArea.getByTestId('nav-logout')).toBeVisible()
    // All three sit inside the SAME User Area container, not scattered
    // across the row - the exact ambiguity the manual review flagged.
    const displayBox = await userArea.getByTestId('current-user-display').boundingBox()
    const roleBox = await userArea.getByTestId('current-user-role').boundingBox()
    const logoutBox = await userArea.getByTestId('nav-logout').boundingBox()
    expect(displayBox && roleBox && logoutBox).toBeTruthy()
  })

  test('C: Role Badge is a non-interactive Chip, not a Link/Menu item', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)

    const roleChip = page.getByTestId('current-user-role')
    // A Chip with no onClick renders as a <div>, never as <a>/<button> -
    // confirms it cannot be mistaken for a clickable Nav/Menu item.
    await expect(roleChip).toHaveJSProperty('tagName', 'DIV')
    await expect(page.locator('a', { hasText: '管理者' })).toHaveCount(0)
  })

  test('D: Master Maintenance screens use the same Global Header as every other screen', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/admin/supplier-contacts')
    await expect(page.getByTestId('header-nav-area')).toBeVisible()
    await expect(page.getByTestId('header-user-area')).toBeVisible()
    await expect(page.getByTestId('nav-dashboard')).toBeVisible()

    await page.goto('/admin/mail-templates')
    await expect(page.getByTestId('header-nav-area')).toBeVisible()
    await expect(page.getByTestId('header-user-area')).toBeVisible()
  })
})

test.describe('Phase 7-F: Sticky Table Header', () => {
  /** Scrolls the given TableContainer down, then confirms its own
   * `stickyHeader` row is still within the viewport (not scrolled away) and
   * sits below the fixed Global Header (never overlapping it). */
  async function assertHeaderStaysVisibleOnScroll(page: Page, tableTestId: string) {
    const container = page.getByTestId(tableTestId)
    const headerCell = container.locator('.MuiTableCell-stickyHeader').first()
    await expect(headerCell).toBeVisible()

    await container.evaluate((el) => { el.scrollTop = el.scrollHeight })

    await expect(headerCell).toBeVisible()
    const headerBox = await headerCell.boundingBox()
    const appBarBox = await page.locator('.MuiAppBar-root').boundingBox()
    const bannerBox = await page.getByText('Demo Environment', { exact: false }).boundingBox()
    expect(headerBox).toBeTruthy()
    expect(appBarBox).toBeTruthy()
    expect(bannerBox).toBeTruthy()
    // I: never overlaps the Global Header (AppBar + demo banner, neither of
    // which is inside the scrolling region) - the sticky Table header's own
    // top must be at or below whichever of the two extends furthest down.
    const globalHeaderBottom = Math.max(appBarBox!.y + appBarBox!.height, bannerBox!.y + bannerBox!.height)
    expect(headerBox!.y).toBeGreaterThanOrEqual(globalHeaderBottom - 1)
  }

  test('E: Order Candidate List keeps its Column Header visible on scroll', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.goto('/candidates')
    await expect(page.getByTestId('candidate-row-HM-MUG-001')).toBeVisible()
    await assertHeaderStaysVisibleOnScroll(page, 'candidate-list-table-container')
  })

  test('F: Order List keeps its Column Header visible on scroll', async ({ page }) => {
    // A fresh Demo Reset leaves /orders/history empty, which renders the
    // "empty" Alert instead of the Table at all - one minimal Draft is
    // enough to make the List (and therefore its sticky header) exist, the
    // scroll behavior itself doesn't depend on row count.
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.goto('/candidates')
    await page.getByTestId('candidate-checkbox-HM-MUG-001').locator('input').check()
    await page.getByTestId('create-draft-button').click()
    // create-draft-button already persists the Draft (POST) - the freshly
    // loaded screen exactly matches what was just saved, so isDirty is
    // false and Save stays correctly disabled (Phase 7-E's own fix); no
    // further Save action is needed for the row to exist in Order History.
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+/)

    await page.goto('/orders/history')
    await assertHeaderStaysVisibleOnScroll(page, 'order-history-table-container')
  })

  test('G: Supplier Contact List keeps its Column Header visible on scroll', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/admin/supplier-contacts')
    await assertHeaderStaysVisibleOnScroll(page, 'supplier-contact-table-container')
  })

  test('H: Mail Template List keeps its Column Header visible on scroll', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/admin/mail-templates')
    await assertHeaderStaysVisibleOnScroll(page, 'mail-template-table-container')
  })

  test('I: sticky Table header has an opaque background (underlying row text does not show through)', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.goto('/candidates')
    const bg = await page.locator('.MuiTableCell-stickyHeader').first().evaluate((el) => getComputedStyle(el).backgroundColor)
    expect(bg).not.toBe('rgba(0, 0, 0, 0)')
    expect(bg).not.toBe('transparent')
  })

  // Found via customer feedback: on a short-viewport monitor (a TV
  // projection was the reported case), Candidate List / Order List rows
  // stopped appearing entirely. Root cause: TableContainer's
  // `flex: 1, minHeight: 0` let the scrolling Table area shrink toward 0px
  // whenever the fixed Header/Banner/Title/Filter chrome above it already
  // consumed most of a short viewport - a `minHeight: 0` genuinely means
  // "may shrink all the way to nothing". Fix: a `minHeight: 220` floor
  // (enough for the sticky header row + a few data rows) on every List
  // screen sharing this pattern, so the Table area never collapses below a
  // usable size - if the viewport is shorter than the total content needs,
  // the OUTER page scrolls (App.tsx's own overflow:auto region) instead of
  // the Table area being squeezed to invisible.
  test('J: Candidate List keeps header + rows usable on a short (TV-projection-scale) viewport', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.setViewportSize({ width: 1280, height: 500 })

    await page.goto('/candidates')
    const candidateContainer = page.getByTestId('candidate-list-table-container')
    await expect(candidateContainer).toBeVisible()
    const candidateBox = await candidateContainer.boundingBox()
    expect(candidateBox).toBeTruthy()
    // Must retain at least enough height for the sticky header row plus a
    // couple of data rows - not squeezed toward 0.
    expect(candidateBox!.height).toBeGreaterThanOrEqual(150)
    // The header row itself and at least the first data row must both be
    // simultaneously reachable within this bounded region (not just
    // theoretically present in the DOM).
    await expect(page.locator('.MuiTableCell-stickyHeader').first()).toBeInViewport()
    await expect(page.locator('table tbody tr').first()).toBeInViewport()
  })

  test('K: Order List keeps its Table area usable on a short (TV-projection-scale) viewport', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.setViewportSize({ width: 1280, height: 500 })

    // Order History List renders an "empty" Alert instead of the Table when
    // there are no Orders yet (fresh Demo Reset) - wait for whichever of the
    // two actually resolves rather than racing the initial fetch.
    await page.goto('/orders/history')
    const historyContainer = page.getByTestId('order-history-table-container')
    const emptyAlert = page.getByText('該当する発注が見つかりませんでした。')
    await expect(historyContainer.or(emptyAlert)).toBeVisible()
    test.skip(await emptyAlert.isVisible(), 'No Orders in the current Demo Data (Table area not rendered)')

    const historyBox = await historyContainer.boundingBox()
    expect(historyBox).toBeTruthy()
    expect(historyBox!.height).toBeGreaterThanOrEqual(150)
    await expect(page.locator('.MuiTableCell-stickyHeader').first()).toBeInViewport()
  })
})

// Mail Template's active-uniqueness constraint is on
// (templateType, supplierCode, brandCode, language) - supplierCode is
// Legacy-validated (SUPPLIER_CODE_NOT_FOUND if it doesn't resolve against
// MS_COMM), so a fabricated per-run-unique code doesn't work either.
// Instead this test deactivates its own leftover row (if a prior run of
// this same spec left one - mail_template is real Master data Demo Reset
// never wipes, same established fact as supplier-contact-mail-template.spec.ts)
// before creating a fresh one, and deactivates what it creates afterward.
const TEST_TEMPLATE_NAME = 'Phase 7-F Variable UX Test'

async function deactivateLeftoverTestTemplate(page: import('@playwright/test').Page) {
  const list = await (await page.request.get('/api/admin/mail-templates')).json()
  const match = list.find((t: { templateName: string; active: boolean }) => t.templateName === TEST_TEMPLATE_NAME && t.active)
  if (match) {
    await page.request.put(`/api/admin/mail-templates/${match.id}`, { data: { ...match, active: false } })
  }
}

test.describe('Phase 7-F: Mail Template Variable UX', () => {
  test('Q/R/S: Variable Chip click-to-insert into Subject and Body, survives Save -> re-edit', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await deactivateLeftoverTestTemplate(page)
    await page.goto('/admin/mail-templates')
    await page.getByTestId('mail-template-create-button').click()

    await page.getByTestId('mail-template-templateName').locator('input').fill(TEST_TEMPLATE_NAME)

    const subjectInput = page.getByTestId('mail-template-subjectTemplate').locator('input')
    await subjectInput.fill('見積送付 ')
    await subjectInput.click()
    await page.getByTestId('mail-template-variable-poNo').click()
    await expect(subjectInput).toHaveValue('見積送付 {{poNo}}')

    const bodyTextarea = page.getByTestId('mail-template-bodyTemplate').locator('textarea').first()
    await bodyTextarea.fill('お世話になっております。')
    await bodyTextarea.click()
    await page.getByTestId('mail-template-variable-supplierName').click()
    await expect(bodyTextarea).toHaveValue('お世話になっております。{{supplierName}}')

    await page.getByTestId('mail-template-save').click()
    await expect(page.getByTestId('mail-template-save')).toHaveCount(0)

    // S: the row for this new template shows up; re-open it and confirm the
    // {{}} tokens survived the Save -> GET round trip unmangled. Matches
    // `.last()` - a deactivated leftover from an earlier run of this same
    // test can still be a same-name row in the table (deactivating doesn't
    // hide it, just flips its status Chip), and the freshly created row is
    // the most recently inserted (highest id, last in the List).
    const row = page.getByRole('row', { name: TEST_TEMPLATE_NAME }).last()
    await row.getByRole('button', { name: '編集' }).click()
    await expect(page.getByTestId('mail-template-subjectTemplate').locator('input')).toHaveValue('見積送付 {{poNo}}')
    await expect(page.getByTestId('mail-template-bodyTemplate').locator('textarea').first()).toHaveValue('お世話になっております。{{supplierName}}')
    await page.getByRole('button', { name: 'キャンセル' }).click()

    await deactivateLeftoverTestTemplate(page)
  })

  test('Drag & Drop: dragging a Variable Chip onto Body inserts the token (Click-to-insert still the primary path)', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/admin/mail-templates')
    await page.getByTestId('mail-template-create-button').click()

    const bodyTextarea = page.getByTestId('mail-template-bodyTemplate').locator('textarea').first()
    await bodyTextarea.fill('納期: ')

    // Playwright's dragTo() drives a mouse-event-based simulation that does
    // not reliably dispatch real HTML5 DragEvents for a non-native
    // (MuiChip, not <input type=file>) drag source - confirmed via live
    // reproduction (dragTo() left the field unchanged, while dispatching
    // the actual dragstart/dragover/drop DragEvent sequence below - exactly
    // what a real browser drag gesture fires - correctly inserted the
    // token). This dispatches that real sequence to verify the FEATURE
    // (handleVariableDrop in MailTemplatePage.tsx), not Playwright's own
    // drag simulation.
    await page.evaluate(() => {
      const chip = document.querySelector('[data-testid="mail-template-variable-requestedDelivery"]') as HTMLElement
      const body = document.querySelector('[data-testid="mail-template-bodyTemplate"] textarea') as HTMLTextAreaElement
      const dt = new DataTransfer()
      chip.dispatchEvent(new DragEvent('dragstart', { bubbles: true, cancelable: true, dataTransfer: dt }))
      body.dispatchEvent(new DragEvent('dragover', { bubbles: true, cancelable: true, dataTransfer: dt }))
      body.dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: dt }))
    })
    await expect(bodyTextarea).toHaveValue('納期: {{requestedDelivery}}')

    await page.getByRole('button', { name: 'キャンセル' }).click()
  })
})
