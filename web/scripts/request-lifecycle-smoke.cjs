// Disposable loopback preview only. Every API call is intercepted.
const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const base = process.env.REQUEST_URL || 'http://127.0.0.1:18528'
const output = process.env.REQUEST_OUTPUT || '/evidence'
const baseline = process.env.REQUEST_BASELINE === '1'
assert(['127.0.0.1', 'localhost'].includes(new URL(base).hostname))
fs.mkdirSync(output, { recursive: true })
const A = { id: 91001, username: 'fixture_a', displayName: '样本甲', role: 'USER', active: true, pendingReviews: [] }
const B = { ...A, id: 91002, username: 'fixture_b', displayName: '样本乙' }
const food = { id: 1, name: '调查样本菜', region: { id: 1, province: '湖北', name: '武汉', centerLatitude: 30.5, centerLongitude: 114.3 }, latitude: 30.5, longitude: 114.3, summary: '固定样本', story: '样本', ingredients: '米饭', heat: 1, reviewStatus: 'APPROVED', contentVersion: 1, createdBy: A.username, creator: A, createdAt: '2026-10-16T00:00:00' }
const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBz0AAAAASUVORK5CYII=', 'base64')
const results = []
async function fixture(browser, options = {}) {
  const context = await browser.newContext({ locale: 'zh-CN', viewport: { width: options.width || 1366, height: 844 } })
  const page = await context.newPage()
  const state = { user: options.anonymous ? null : { ...A, role: options.role || 'USER' }, authDelay: options.authDelay ?? 80, authStatus: 200, manifestDelay: options.manifestDelay || 0, tags: [], regions: [food.region], requests: [], errors: [], unexpected: [], phase: 'startup', started: Date.now() }
  await page.addInitScript(noStructuredClone => {
    if (noStructuredClone) window.structuredClone = undefined
    const outside = new Set()
    window.__outsideListeners = outside
    const add = document.addEventListener, remove = document.removeEventListener
    // Production builds minify function names; identify the module, not `outside`.
    document.addEventListener = function(type, listener, ...args) { if (type === 'click' && /BackgroundMusic/.test(new Error().stack || '')) outside.add(listener); return add.call(this, type, listener, ...args) }
    document.removeEventListener = function(type, listener, ...args) { if (type === 'click') outside.delete(listener); return remove.call(this, type, listener, ...args) }
  }, !!options.noStructuredClone)
  page.setDefaultTimeout(12000)
  page.on('pageerror', error => state.errors.push(error.message))
  const rows = new Map()
  page.on('request', request => {
    const u = new URL(request.url())
    if (!u.pathname.startsWith('/api/') && u.pathname !== '/audio/music-manifest.json') return
    const row = { method: request.method(), path: u.pathname, query: u.search, phase: state.phase, ms: Date.now() - state.started }
    state.requests.push(row); rows.set(request, row)
  })
  page.on('requestfailed', request => { if (rows.has(request)) rows.get(request).failure = request.failure()?.errorText })
  await page.route('**/*', async route => {
    const request = route.request(), u = new URL(request.url()), p = u.pathname, method = request.method()
    if (p.startsWith('/api/')) {
      let json
      if (p === '/api/auth/me') {
        const user = state.user, delay = state.authDelay, status = state.authStatus
        await new Promise(resolve => setTimeout(resolve, delay))
        return route.fulfill(status !== 200 ? { status, json: { message: 'fixture unavailable' } } : user ? { json: user } : { status: 401, json: { message: 'anonymous fixture' } }).catch(() => {})
      }
      if (p === '/api/auth/csrf') json = { token: 'fixture', headerName: 'X-CSRF-TOKEN' }
      else if (p === '/api/achievements/notifications') json = []
      else if (p === '/api/regions') json = state.regions
      else if (p === '/api/admin/foods') json = { items: [food], total: 1, page: 1, pageSize: 10, pendingTotal: 0, totalHeat: 1 }
      else if (p === '/api/admin/users') json = { items: [state.user], total: 1, page: 1, pageSize: 10 }
      else if (p === '/api/foods/import' && method === 'POST') {
        state.regions.push({ ...food.region, id: 2, name: '新地区' })
        json = { totalRows: 1, importedCount: 1, skippedCount: 0, failedCount: 0, issues: [] }
      }
      else if (p === '/api/foods/catalog') json = { items: [food, { ...food, id: 2, name: '样本菜二' }], total: 90, page: Number(u.searchParams.get('page') || 1), pageSize: 30 }
      else if (p === '/api/foods/map-clusters') json = { items: [], total: 2, dataVersion: 1, zoom: 4 }
      else if (/^\/api\/foods\/\d+$/.test(p)) {
        const id = Number(p.split('/').at(-1))
        await new Promise(resolve => setTimeout(resolve, options.slowFood === id ? 2200 : 40))
        json = { ...food, id }
      } else if (/\/visits$/.test(p) && method === 'POST') return route.fulfill({ json: { heat: food.heat } }).catch(() => {})
      else if (/\/like\/status$/.test(p)) json = { likedByMe: false, likeCount: 1 }
      else if (/\/favorites\/\d+\/status$/.test(p)) json = { favorited: false }
      else if (/\/wishlist\/foods\/\d+\/status$/.test(p)) json = { listed: false }
      else if (/\/comments\/page$/.test(p)) json = { items: [], total: 0, page: 1, pageSize: 20 }
      else if (p === '/api/food-tags' && method === 'GET') json = state.user?.id === A.id ? state.tags : []
      else if (p === '/api/food-tags' && method === 'POST') { json = { ...request.postDataJSON(), id: 10, status: 'APPROVED', version: 1 }; state.tags.push(json) }
      else if (p === '/api/agent/chat' && method === 'POST') {
        assert.equal(request.postDataJSON().currentFoodId, 1)
        await new Promise(resolve => setTimeout(resolve, 2200))
        json = { reply: '甲账号的迟到回复' }
      }
      else { state.unexpected.push(method + ' ' + p); json = [] }
      if (options.catalogDelay && p === '/api/foods/catalog') await new Promise(resolve => setTimeout(resolve, options.catalogDelay))
      return route.fulfill({ json }).catch(() => {})
    }
    if (p === '/audio/music-manifest.json') {
      await new Promise(resolve => setTimeout(resolve, state.manifestDelay))
      return route.fulfill({ json: [{ name: '固定曲目', artist: 'fixture', src: '/audio/fixture.mp3' }] }).catch(() => {})
    }
    if (u.origin !== new URL(base).origin) return route.fulfill({ contentType: 'image/png', body: pixel }).catch(() => {})
    return route.continue()
  })
  const count = (p, method = 'GET') => state.requests.filter(r => r.path === p && r.method === method).length
  const phase = name => { state.phase = name }
  async function focus() {
    await page.waitForTimeout(1100)
    await page.evaluate(() => { window.dispatchEvent(new Event('focus')); window.dispatchEvent(new Event('pageshow')); document.dispatchEvent(new Event('visibilitychange')) })
    await page.waitForTimeout(state.authDelay + 250)
  }
  async function openUpload() { await page.locator('.home-action-trigger').click(); await page.locator('.home-action-list').getByRole('button', { name: /收录珍馐/ }).click(); await page.locator('.upload-modal').waitFor() }
  return { page, context, state, count, phase, focus, openUpload }
}
async function run(browser, name, options, action) {
  if (process.env.REQUEST_CASES && !process.env.REQUEST_CASES.split(',').includes(name)) return
  const x = await fixture(browser, options)
  const row = { name, status: 'RUNNING', observations: {} }
  try {
    await action(x, row.observations)
    assert.deepEqual(x.state.errors, [])
    assert.deepEqual(x.state.unexpected, [])
    row.status = 'PASS'
  } catch (error) { row.status = 'FAIL'; row.error = String(error); process.exitCode = 1 }
  finally {
    row.requests = x.state.requests
    await x.context.close()
    fs.writeFileSync(path.join(output, name + '.json'), JSON.stringify(row, null, 2))
    results.push(row); console.log(name, row.status, row.error || '')
  }
}
async function screenshot(page, name) {
  await page.screenshot({ path: path.join(output, name + '.png'), fullPage: true })
  return page.locator('#app > header, .explorer-catalog, .food-map, .detail-hero').evaluateAll(nodes => nodes.map(node => {
    const rect = node.getBoundingClientRect(), css = getComputedStyle(node)
    return { class: node.className, x: rect.x, y: rect.y, width: rect.width, height: rect.height, color: css.color, background: css.backgroundColor, font: css.font }
  }))
}
;(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: '/ms-playwright/chromium-1148/chrome-linux/chrome' })
  try {
    for (const width of [390, 1366]) await run(browser, 'home-' + width, { width, catalogDelay: 100, noStructuredClone: width === 390 }, async (x, observation) => {
      const { page, count, phase, focus } = x
      await page.goto(base); await page.locator('.explorer-card').first().waitFor(); await page.waitForTimeout(1300)
      observation.layout = await screenshot(page, 'home-' + width)
      observation.initial = { regions: count('/api/regions'), catalog: count('/api/foods/catalog'), map: count('/api/foods/map-clusters'), music: count('/audio/music-manifest.json') }
      phase('foreground'); for (let i = 0; i < 3; i++) await focus()
      observation.afterFocus = { me: count('/api/auth/me'), notifications: count('/api/achievements/notifications'), music: count('/audio/music-manifest.json') }
      phase('pagination'); await page.getByRole('button', { name: '下一页', exact: true }).click(); await page.waitForTimeout(500)
      observation.afterPage = { catalog: count('/api/foods/catalog'), map: count('/api/foods/map-clusters') }
      if (!baseline) {
        assert.deepEqual(observation.initial, { regions: 1, catalog: 1, map: 1, music: 1 })
        assert.equal(observation.afterFocus.me, 4)
        assert.equal(observation.afterFocus.notifications, 1)
        assert.equal(observation.afterFocus.music, 1)
        assert.deepEqual(observation.afterPage, { catalog: 2, map: 1 })
      }
    })
    await run(browser, 'detail-identity-and-draft', { width: 390, authDelay: 1600 }, async (x, observation) => {
      const { page, state, count, phase, focus } = x
      await page.goto(base + '/foods/1'); await page.locator('.detail').waitFor(); await page.waitForTimeout(2200)
      observation.layout = await screenshot(page, 'detail')
      if (await page.locator('.mobile-comment-composer').getAttribute('open') === null) await page.locator('.mobile-comment-composer summary').click()
      await page.locator('.comment-form textarea').fill('保留同账号草稿')
      await page.locator('.agent-orb').click(); await page.locator('.agent-panel textarea').fill('保留助手输入')
      state.authDelay = 120
      phase('same-account-foreground'); await focus(); await focus()
      observation.sameAccount = { details: count('/api/foods/1'), comments: count('/api/foods/1/comments/page'), likes: count('/api/foods/1/like/status'), visits: count('/api/foods/1/visits', 'POST'), music: count('/audio/music-manifest.json'), draft: await page.locator('.comment-form textarea').inputValue(), agentDraft: await page.locator('.agent-panel textarea').inputValue().catch(() => '(panel remounted)') }
      if (!baseline) {
        assert.deepEqual(observation.sameAccount, { details: 1, comments: 1, likes: 1, visits: 1, music: 1, draft: '保留同账号草稿', agentDraft: '保留助手输入' })
        phase('switch-account'); state.user = B; await focus()
        assert.equal(await page.locator('.comment-form textarea').inputValue(), '')
        assert.equal(count('/api/foods/1'), 1)
        assert.equal(count('/api/foods/1/visits', 'POST'), 2)
        assert.equal(await page.locator('.agent-panel').count(), 0)
      }
    })
    if (!baseline) {
      await run(browser, 'cancel-detail-on-route-change', { slowFood: 1 }, async ({ page, state, count, phase }) => {
        await page.goto(base); await page.locator('.explorer-card-link').first().waitFor(); await page.waitForTimeout(400)
        await page.locator('.explorer-card-link').first().click(); await page.waitForTimeout(250)
        phase('leave-pending-detail'); await page.goBack(); await page.locator('.explorer-card-link').nth(1).click()
        await page.locator('.detail').waitFor(); await page.waitForTimeout(2500)
        assert.equal(count('/api/foods/1'), 1); assert.equal(count('/api/foods/2'), 1)
        assert(state.requests.some(r => r.path === '/api/foods/1' && r.failure), 'old read cancelled')
        assert.equal(count('/api/foods/1/visits', 'POST'), 0)
      })
      await run(browser, 'music-unmount-listeners', { manifestDelay: 2200 }, async ({ page, count, phase }) => {
        await page.goto(base); await page.locator('.explorer-card').first().waitFor(); await page.waitForTimeout(300)
        phase('unmount-before-manifest'); await page.locator('#app > header a[href="/about"]').click(); await page.waitForTimeout(2600)
        assert.equal(await page.evaluate(() => window.__outsideListeners.size), 0)
        for (let i = 0; i < 2; i++) {
          await page.locator('#app > header .brand').click(); await page.locator('.explorer-card').first().waitFor(); await page.waitForTimeout(200)
          assert.equal(await page.evaluate(() => window.__outsideListeners.size), 1)
          await page.locator('#app > header a[href="/about"]').click(); await page.waitForTimeout(150)
          assert.equal(await page.evaluate(() => window.__outsideListeners.size), 0)
        }
        assert.equal(count('/audio/music-manifest.json'), 1)
      })
      await run(browser, 'tag-cache-and-identity', {}, async ({ page, state, count, focus, openUpload }) => {
        await page.goto(base); await page.locator('.explorer-card').first().waitFor(); await page.waitForTimeout(400)
        for (let i = 0; i < 2; i++) { await page.locator('.discovery-filter-trigger').click(); await page.waitForTimeout(150); await page.locator('.discovery-drawer header button').click() }
        assert.equal(count('/api/food-tags'), 1)
        await openUpload()
        await page.locator('.tag-kind input').first().fill('新样本标签')
        await page.locator('.tag-kind').first().getByRole('button', { name: '创建并选择', exact: true }).click()
        await page.getByText('新样本标签', { exact: true }).waitFor()
        assert.equal(count('/api/foods', 'POST'), 0)
        await page.locator('.modal-title .icon-button').click()
        await page.locator('.discovery-filter-trigger').click(); await page.getByText('新样本标签', { exact: true }).waitFor()
        assert.equal(count('/api/food-tags'), 2)
        await page.locator('.discovery-drawer header button').click()
        state.user = B; await focus(); await openUpload(); await page.waitForTimeout(250)
        assert.equal(await page.getByText('新样本标签', { exact: true }).count(), 0)
        assert.equal(count('/api/food-tags'), 3)
      })
      await run(browser, 'foreground-failure-backoff', {}, async ({ page, state, count, focus }) => {
        await page.goto(base + '/foods/1'); await page.locator('.detail').waitFor(); await page.waitForTimeout(400)
        const before = count('/api/auth/me')
        state.authStatus = 503
        await focus(); await focus(); await focus()
        assert.equal(count('/api/auth/me'), before + 1, 'failure cooldown applies to foreground events')
        assert.equal(count('/api/foods/1'), 1)
        assert.equal(count('/audio/music-manifest.json'), 1)
        assert.equal(await page.locator('.agent-orb').isVisible(), false)
      })
      await run(browser, 'late-agent-cross-account', {}, async ({ page, state, count, focus }) => {
        await page.goto(base + '/foods/1'); await page.locator('.detail').waitFor(); await page.waitForTimeout(400)
        await page.locator('.agent-orb').click(); await page.locator('.agent-panel textarea').fill('仅属于甲的请求')
        await page.locator('.agent-panel form button').click(); await page.waitForTimeout(100)
        state.user = B; await focus(); await page.waitForTimeout(1400)
        await page.locator('.agent-orb').click()
        assert.equal(await page.getByText('甲账号的迟到回复', { exact: true }).count(), 0)
        assert.equal(await page.getByText('仅属于甲的请求', { exact: true }).count(), 0)
        assert.equal(count('/api/foods/1'), 1)
        assert.equal(count('/api/agent/chat', 'POST'), 1)
      })
      await run(browser, 'region-invalidation-after-import', { role: 'ADMIN' }, async ({ page, count }) => {
        await page.goto(base + '/admin'); await page.locator('.admin-import-actions input[type=file]').waitFor({ state: 'attached' }); await page.waitForTimeout(250)
        assert.equal(count('/api/regions'), 1)
        await page.locator('.admin-import-actions input[type=file]').setInputFiles({ name: 'fixture.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('API fixture only') })
        await page.locator('.import-result').waitFor(); await page.waitForTimeout(250)
        assert.equal(count('/api/regions'), 2)
        assert.match(await page.locator('.admin-stats article').nth(1).innerText(), /2/)
      })
    }
  } finally { await browser.close() }
  fs.writeFileSync(path.join(output, 'summary.json'), JSON.stringify({ baseline, results: results.map(({ name, status, observations, error }) => ({ name, status, observations, error })), limitations: ['Deterministic API/auth/tile fixtures; no production writes or real-device evidence'] }, null, 2))
  const expected = process.env.REQUEST_CASES ? process.env.REQUEST_CASES.split(',').length : baseline ? 3 : 9
  if (results.length !== expected || results.some(r => r.status !== 'PASS')) process.exitCode = 1
})().catch(error => { console.error(error); process.exitCode = 1 })
