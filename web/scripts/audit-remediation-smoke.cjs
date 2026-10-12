const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const base = process.env.AUDIT_TEST_URL || 'http://127.0.0.1:5173'
const selected = new Set((process.env.AUDIT_CASES || '').split(',').filter(Boolean))
const results = []
const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBz0AAAAASUVORK5CYII=', 'base64')
const user = { id: 1, username: 'audit-a', displayName: '审计甲', role: 'USER', active: true }
const food = { id: 7, name: '审计菜品', region: { id: 1, province: '湖北', name: '武汉' }, latitude: 30.5, longitude: 114.3, summary: '简介', story: '掌故', ingredients: '食材', imageUrl: '/uploads/original.png', heat: 10, reviewStatus: 'APPROVED', contentVersion: 1, creator: user, createdBy: user.username, createdAt: '2026-09-20' }
async function fixture(browser, custom, options = {}) {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 }, locale: 'zh-CN', ...options })
  page.setDefaultTimeout(15000)
  const requests = [], errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('dialog', dialog => dialog.accept())
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url()), pathname = url.pathname
    if (pathname.startsWith('/api/')) {
      requests.push({ path: pathname, query: Object.fromEntries(url.searchParams), method: request.method() })
      if (custom && await custom(route, url)) return
      let json = []
      if (pathname === '/api/auth/me') json = user
      else if (pathname === '/api/auth/csrf') json = { token: 'test-only', headerName: 'X-CSRF-TOKEN' }
      else if (pathname === '/api/foods/catalog' || pathname === '/api/foods/mine/page') json = { items: [food], total: 1, page: 1, pageSize: 20 }
      else if (pathname === '/api/foods/map-clusters') json = { items: [], total: 1, dataVersion: 1, zoom: 4 }
      else if (pathname === '/api/regions') json = [food.region]
      else if (/^\/api\/foods\/7$/.test(pathname)) json = food
      else if (/comments\/page|check-ins$|favorites\/page|wishlist\/page/.test(pathname)) json = { items: [], total: 0, page: 1, pageSize: 20 }
      else if (pathname.endsWith('/like')) json = { likedByMe: false, likeCount: 0 }
      else if (pathname.endsWith('/favorite')) json = { favorited: false }
      return route.fulfill({ json })
    }
    if (pathname.startsWith('/uploads/')) return route.fulfill({ contentType: 'image/png', body: pixel })
    if (pathname === '/audio/music-manifest.json') return route.fulfill({ json: [] })
    if (url.origin !== new URL(base).origin && /\.(png|jpg)(\?|$)/.test(url.href)) return route.fulfill({ contentType: 'image/png', body: pixel })
    if (url.origin !== new URL(base).origin) return route.abort()
    return route.continue()
  })
  return { page, requests, errors }
}
async function run(id, fn) {
  if (selected.size && !selected.has(id)) return
  const start = Date.now()
  try { await fn(); results.push({ id, status: 'passed', durationMs: Date.now() - start }); console.log('PASS', id) }
  catch (error) { results.push({ id, status: 'failed', message: String(error), durationMs: Date.now() - start }); console.error('FAIL', id, error); process.exitCode = 1 }
}
;(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.AUDIT_TEST_EXECUTABLE ? { executablePath: process.env.AUDIT_TEST_EXECUTABLE } : {}) })
  try {
    await run('FE-MOBILE-ENTRY', async () => {
      for (const width of [320, 390, 768]) {
        const { page, errors } = await fixture(browser, undefined, { viewport: { width, height: 844 } })
        try {
          await page.goto(base + '/')
          await page.locator('.explorer-card').waitFor()
          await page.locator('.discovery-filter-trigger').click()
          const dialog = page.getByRole('dialog')
          await dialog.waitFor()
          const bounds = await dialog.boundingBox()
          assert(bounds.x >= 0 && bounds.x + bounds.width <= width + 1)
          await page.keyboard.press('Escape')
          await page.locator('.home-action-trigger').click()
          await page.getByRole('button', { name: /已收藏的珍馐/ }).click()
          await page.getByRole('dialog', { name: '已收藏的珍馐' }).waitFor()
          await page.keyboard.press('Escape')
          assert.deepEqual(errors, [])
        } finally { await page.close() }
      }
    })
    await run('FE-CHECKIN-FROZEN', async () => {
      let release, submitted
      const held = new Promise(resolve => { release = resolve })
      const { page, errors } = await fixture(browser, async (route, url) => {
        if (url.pathname === '/api/foods/7/check-ins') {
          submitted = route.request().postDataJSON()
          await held
          await route.fulfill({ status: 503, json: { message: 'temporary fixture failure' } })
          return true
        }
        return false
      })
      try {
        await page.goto(base + '/foods/7')
        const form = page.locator('.comment-form')
        await form.getByRole('button', { name: '打卡', exact: true }).click()
        assert.equal(await form.locator('textarea').getAttribute('required'), null)
        await form.locator('select').selectOption('PRIVATE')
        await form.locator('textarea').fill('尚未保存的私人心得')
        const outgoing = page.waitForRequest('**/api/foods/7/check-ins')
        await form.getByRole('button', { name: '保存打卡', exact: true }).click()
        await outgoing
        assert.equal(await form.locator('textarea').isDisabled(), true)
        assert.equal(await form.locator('input[type=date]').isDisabled(), true)
        assert.equal(await form.locator('select').isDisabled(), true)
        assert.equal(submitted.visibility, 'PRIVATE')
        release()
        await page.locator('.comment-message').waitFor()
        assert.equal(await form.locator('textarea').inputValue(), '尚未保存的私人心得')
        assert.deepEqual(errors, [])
      } finally { release(); await page.close() }
    })
    await run('FE-QUERY', async () => {
      const { page, requests, errors } = await fixture(browser)
      try {
        await page.goto(base + '/')
        await page.locator('.explorer-card').waitFor()
        await page.locator('.leaflet-control-zoom-in').waitFor()
        const mapId = await page.locator('.food-map').evaluate(node => node._leaflet_id)
        await page.locator('.header-food-search input').fill('牛肉')
        await page.locator('.header-food-search input').press('Enter')
        await page.waitForURL('**/?q=*')
        await page.waitForFunction(() => performance.getEntriesByName('terra:home-mounted').length === 1)
        assert.equal(await page.locator('.food-map').evaluate(node => node._leaflet_id), mapId)
        await page.locator('.header-food-search input').fill('未提交面条')
        const nextMap = page.waitForRequest(request => new URL(request.url()).pathname === '/api/foods/map-clusters')
        await page.locator('.leaflet-control-zoom-in').click()
        const mapRequest = await nextMap
        assert.equal(new URL(mapRequest.url()).searchParams.get('keyword'), '牛肉')
        await page.goBack()
        await page.waitForURL(base + '/')
        assert.equal(await page.locator('.header-food-search input').inputValue(), '')
        assert.equal(await page.locator('.food-map').evaluate(node => node._leaflet_id), mapId)
        await page.locator('.discovery-filter-trigger').click()
        await page.getByRole('dialog').waitFor()
        assert.equal(await page.getByRole('dialog').locator('select').count(), 2)
        await page.keyboard.press('Escape')
        assert.equal(await page.getByRole('dialog').count(), 0)
        assert(requests.filter(item => item.path === '/api/foods/catalog').length >= 2)
        assert.deepEqual(errors, [])
      } finally { await page.close() }
    })
    await run('FE-UPLOAD-KEY', async () => {
      const keys = []; let uploads = 0, first = true
      const { page, errors } = await fixture(browser, async (route, url) => {
        if (url.pathname === '/api/images') { uploads++; await route.fulfill({ json: { url: '/uploads/new.png' } }); return true }
        if (url.pathname === '/api/foods' && route.request().method() === 'POST') {
          keys.push(route.request().headers()['idempotency-key'])
          assert.equal(route.request().headers()['x-expected-user-id'], '1')
          if (first) { first = false; await route.fulfill({ status: 503, json: { message: 'retry' } }) }
          else await route.fulfill({ json: { ...food, ...route.request().postDataJSON() } })
          return true
        }
        return false
      })
      try {
        await page.goto(base + '/')
        await page.locator('.explorer-card').waitFor()
        await page.locator('.leaflet-control-zoom-in').waitFor()
        await page.locator('.food-map').click({ force: true })
        await page.locator('.food-map-picked-marker').waitFor()
        await page.locator('.home-action-trigger').click()
        await page.getByRole('button', { name: '＋ 收录珍馐', exact: true }).click()
        const form = page.locator('.upload-modal')
        await form.waitFor()
        await form.getByLabel('菜品名称', { exact: true }).fill('审计上传')
        await form.getByLabel('一句话简介', { exact: true }).fill('简介')
        await form.getByLabel('主要食材', { exact: true }).fill('食材')
        await form.getByLabel('珍馐掌故', { exact: true }).fill('掌故')
        await form.locator('input[type=file]').setInputFiles({ name: 'audit.png', mimeType: 'image/png', buffer: pixel })
        await form.locator('.primary-button').click()
        await form.locator('.form-error').filter({ hasText: 'HTTP 503' }).waitFor()
        const persisted = await page.evaluate(() => JSON.parse(localStorage.getItem('terra-food.draft.foodUpload.v2.1')))
        assert.equal(persisted.idempotencyKey, keys[0]); assert.equal(persisted.imageUrl, '/uploads/new.png')
        await form.locator('.primary-button').click()
        await form.waitFor({ state: 'detached' })
        assert.equal(uploads, 1); assert.equal(keys.length, 2); assert.equal(keys[0], keys[1])
        assert.equal(await page.evaluate(() => localStorage.getItem('terra-food.draft.foodUpload.v2.1')), null)
        assert.deepEqual(errors, [])
      } finally { await page.close() }
    })
    await run('FE-DIARY-PAGING', async () => {
      let items = Array.from({ length: 22 }, (_, index) => ({ id: index + 1, foodId: 7, foodName: `打卡-${index + 1}`, eatenOn: '2026-09-20', note: '审计记录', visibility: 'PRIVATE', version: 0, timezone: 'Asia/Shanghai', createdAt: '2026-09-20' }))
      const { page, requests, errors } = await fixture(browser, async (route, url) => {
        if (url.pathname === '/api/profile/check-ins') {
          const number = Number(url.searchParams.get('page') || 1)
          await route.fulfill({ json: { items: items.slice((number - 1) * 20, number * 20), total: items.length, page: number, pageSize: 20 } }); return true
        }
        if (/^\/api\/profile\/check-ins\/\d+$/.test(url.pathname) && route.request().method() === 'DELETE') {
          items = items.filter(item => item.id !== Number(url.pathname.split('/').pop()))
          await route.fulfill({ status: 204 }); return true
        }
        return false
      })
      try {
        await page.goto(base + '/profile?tab=diary')
        await page.locator('.checkin-list article').nth(19).waitFor()
        assert.equal(requests.filter(item => ['/api/foods/mine/page', '/api/regions', '/api/profile/footprints', '/api/profile/favorites/page'].includes(item.path)).length, 0)
        await page.locator('.checkin-list article').first().getByRole('button', { name: '删除', exact: true }).click()
        await page.getByRole('heading', { name: '打卡-21', exact: true }).waitFor()
        await page.getByRole('button', { name: '加载更多', exact: true }).click()
        await page.getByRole('heading', { name: '打卡-22', exact: true }).waitFor()
        assert.equal(await page.locator('.checkin-list article').count(), 21)
        assert.equal(await page.getByRole('button', { name: '加载更多', exact: true }).count(), 0)
        assert.deepEqual(errors, [])
      } finally { await page.close() }
    })
    await run('FE-EDIT-IMAGE', async () => {
      let uploads = 0, saved
      const { page, errors } = await fixture(browser, async (route, url) => {
        if (url.pathname === '/api/images') { uploads++; await route.fulfill({ json: { url: '/uploads/replacement.png' } }); return true }
        if (url.pathname === '/api/profile/foods/7') { saved = route.request().postDataJSON(); await route.fulfill({ json: { ...food, ...saved } }); return true }
        return false
      })
      try {
        await page.goto(base + '/profile')
        await page.locator('.profile-food-card').getByRole('button', { name: '编辑', exact: true }).click()
        await page.locator('.profile-edit-modal input[type=file]').setInputFiles({ name: 'replacement.png', mimeType: 'image/png', buffer: pixel })
        await page.locator('.profile-edit-modal .icon-button').click()
        await page.locator('.profile-food-card').getByRole('button', { name: '编辑', exact: true }).click()
        assert.match(await page.locator('.profile-edit-modal .cover-preview').getAttribute('src'), /^blob:/)
        await page.locator('.profile-edit-modal .primary-button').click()
        await page.locator('.profile-edit-modal').waitFor({ state: 'detached' })
        assert.equal(uploads, 1); assert.equal(saved.imageUrl, '/uploads/replacement.png')
        assert.deepEqual(errors, [])
      } finally { await page.close() }
    })
    await run('FE-SESSION-FOREGROUND', async () => {
      let current = user
      const { page, errors } = await fixture(browser, async (route, url) => {
        if (url.pathname === '/api/auth/me') { await route.fulfill({ json: current }); return true }
        return false
      })
      try {
        await page.addInitScript(() => { const original = Storage.prototype.setItem; Storage.prototype.setItem = function(key, value) { if (key === 'dayan-auth-session-change') throw new Error('restricted'); return original.call(this, key, value) } })
        await page.goto(base + '/profile?tab=diary')
        await page.locator('.profile-identity h1').filter({ hasText: '审计甲' }).waitFor()
        current = { ...user, id: 2, username: 'audit-b', displayName: '审计乙' }
        // Wait only for the intentional foreground-event debounce, then emulate
        // returning from a tab that changed the shared session cookie.
        await page.waitForTimeout(1050)
        const confirmed = page.waitForResponse('**/api/auth/me')
        await page.evaluate(() => window.dispatchEvent(new Event('focus')))
        await confirmed
        await page.locator('.profile-identity h1').filter({ hasText: '审计乙' }).waitFor()
        assert.equal(await page.locator('.profile-identity h1').filter({ hasText: '审计甲' }).count(), 0)
        assert.deepEqual(errors, [])
      } finally { await page.close() }
    })
  } finally { await browser.close() }
  if (!results.length) throw new Error('Zero audit cases selected')
  if (process.env.AUDIT_TEST_REPORT) fs.writeFileSync(process.env.AUDIT_TEST_REPORT, JSON.stringify({ base, kind: 'mocked-browser-contracts', results }, null, 2))
  console.log(JSON.stringify({ passed: results.filter(result => result.status === 'passed').length, failed: results.filter(result => result.status === 'failed').length }))
})().catch(error => { console.error(error); process.exitCode = 1 })
