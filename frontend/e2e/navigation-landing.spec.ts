import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 7-E Section 1 audit: every existing E2E spec's login() helper only
 * asserts `nav-dashboard` (the Nav BUTTON, always rendered in the persistent
 * AppBar regardless of which page is showing) is visible - none of them
 * verify the actual landing URL or Dashboard page CONTENT. Source review
 * (LoginPage.tsx / App.tsx) confirmed there is no explicit post-login
 * navigate() anywhere - the landing page is simply whatever URL the browser
 * already had when App.tsx's `{!user ? <LoginPage/> : <Routes>...}` switches
 * over once AuthContext's `user` becomes non-null. This spec closes that
 * coverage gap without changing any application behavior - Dashboard-as-
 * landing-page for a normal `/` login, and direct-URL-access preservation,
 * both already work correctly by simply never redirecting away from
 * whatever URL was in the bar (docs/demo-walkthrough/README.md 31-33行:
 * "ログイン後、最初に表示される画面" is Dashboard).
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

async function fillLoginForm(page: Page, username: string, password: string) {
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
}

test.describe('Navigation / Login Landing (Phase 7-E Section 1)', () => {
  test('A: OPERATOR - normal login (goto /) lands on Dashboard (URL + content)', async ({ page }) => {
    await page.goto('/')
    await fillLoginForm(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { name: 'ダッシュボード', level: 1 })).toBeVisible()
  })

  test('B: ADMIN - normal login (goto /) lands on Dashboard (URL + content)', async ({ page }) => {
    await page.goto('/')
    await fillLoginForm(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { name: 'ダッシュボード', level: 1 })).toBeVisible()
  })

  test('C: 未認証で直接URLへアクセス -> login -> 元のURLへ戻る (returnTo相当の既存挙動)', async ({ page }) => {
    // No explicit "return to originating URL" mechanism exists - and none is
    // needed, because the app never navigates away from the current URL on
    // auth state changes in the first place (verified via source: LoginPage
    // has no navigate() call). Direct-URL access before login already
    // preserves itself for free.
    await page.goto('/candidates')
    await expect(page.getByLabel('ユーザー名')).toBeVisible()
    await fillLoginForm(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await expect(page).toHaveURL(/\/candidates(\?.*)?$/)
    await expect(page.getByRole('heading', { name: '発注候補一覧', level: 1 })).toBeVisible()
  })

  test('D: logout -> login -> Dashboardへ (URL/content両方)', async ({ page }) => {
    await page.goto('/')
    await fillLoginForm(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await expect(page.getByTestId('nav-dashboard')).toBeVisible()

    await page.getByTestId('nav-logout').click()
    await expect(page.getByLabel('ユーザー名')).toBeVisible()
    // Logout does not navigate either - it stays on whatever URL was
    // current (here, still `/`) and simply swaps the rendered tree back to
    // LoginPage once `user` becomes null.
    await expect(page).toHaveURL(/\/$/)

    await fillLoginForm(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { name: 'ダッシュボード', level: 1 })).toBeVisible()
  })

  test('E: ログイン中にBrowser refresh -> 同じ画面に留まる（Dashboardへ戻されない）', async ({ page }) => {
    await page.goto('/')
    await fillLoginForm(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-candidates').click()
    await expect(page).toHaveURL(/\/candidates(\?.*)?$/)

    await page.reload()
    // Session cookie survives the reload (JSESSIONID), AuthContext re-fetches
    // the current user on mount, and the Router re-renders the SAME URL -
    // refresh must not silently bounce the user back to Dashboard.
    await expect(page).toHaveURL(/\/candidates(\?.*)?$/)
    await expect(page.getByRole('heading', { name: '発注候補一覧', level: 1 })).toBeVisible()
  })
})
