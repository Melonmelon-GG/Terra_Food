// Request/control-flow assertions only. No screenshots, live writes or model calls.
const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const base = process.env.MAP_TEST_URL || 'http://127.0.0.1:18530'
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBz0AAAAASUVORK5CYII=', 'base64')
const results = []
async function fixture(browser, primary, fallback, width = 390) {
  const context = await browser.newContext({ viewport: { width, height: 844 }, locale: 'zh-CN' })
  const page = await context.newPage(); page.setDefaultTimeout(25_000)
  const requests = { primary: 0, annotation: 0, fallback: 0, writes: 0, geocoder: 0 }, errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url())
    if (url.pathname.startsWith('/api/')) {
      if (request.method() !== 'GET') { requests.writes++; return route.abort() }
      if (url.pathname === '/api/auth/me') return route.fulfill({ status: 401, json: { message: 'anonymous' } })
      let json = []
      if (url.pathname === '/api/foods/catalog') json = { items: [], total: 0, page: 1, pageSize: 30 }
      if (url.pathname === '/api/foods/map-clusters') json = { items: [], total: 0, dataVersion: 1, zoom: 4 }
      return route.fulfill({ json })
    }
    if (/tianditu\.gov\.cn$/.test(url.hostname) && url.pathname === '/DataServer') {
      if (url.searchParams.get('T') === 'cva_w') requests.annotation++; else requests.primary++
      return primary ? route.fulfill({ contentType: 'image/png', body: png }) : route.abort()
    }
    if (url.hostname === 'tile.openstreetmap.org') {
      requests.fallback++
      return fallback ? route.fulfill({ contentType: 'image/png', body: png }) : route.abort()
    }
    if (url.hostname === 'api.tianditu.gov.cn' && url.pathname === '/geocoder') {
      requests.geocoder++
      return route.fulfill({ json: { status: '0', result: { addressComponent: { province: '湖北省', city: '武汉市' }, formatted_address: '武汉市测试地址' } } })
    }
    if (url.origin !== new URL(base).origin) return route.abort()
    return route.continue()
  })
  await page.goto(base)
  await page.locator('.leaflet-control-zoom-in').waitFor({ state: 'attached' })
  return { page, context, requests, errors }
}
async function run(id, callback) {
  try { await callback(); results.push({ id, status: 'PASS' }); console.log('PASS', id) }
  catch (error) { results.push({ id, status: 'FAIL', error: error.name }); console.error('FAIL', id, error.name); process.exitCode = 1 }
}
;(async () => {
  const options = { headless: true }
  if (process.env.CHROMIUM_PATH) options.executablePath = process.env.CHROMIUM_PATH
  const browser = await chromium.launch(options)
  try {
    await run('MAP-CONFIG-01-primary-and-annotation-mobile', async () => {
      const f = await fixture(browser, true, false)
      try {
        await f.page.locator('.map-loading').waitFor({ state: 'hidden' })
        assert(f.requests.primary > 0 && f.requests.annotation > 0)
        assert.equal(f.requests.fallback, 0); assert.equal(f.requests.writes, 0); assert.deepEqual(f.errors, [])
        const bounds = await f.page.locator('.food-map').boundingBox()
        await f.page.mouse.click(bounds.x + bounds.width * .5, bounds.y + bounds.height * .4)
        await f.page.waitForFunction(() => document.body.textContent.includes('武汉市测试地址'))
        assert(f.requests.geocoder > 0)
      } finally { await f.context.close() }
    })
    await run('MAP-CONFIG-02-primary-failure-fallback', async () => {
      const f = await fixture(browser, false, true, 1366)
      try {
        await f.page.waitForResponse(response => response.url().includes('tile.openstreetmap.org'))
        await f.page.locator('.map-loading').waitFor({ state: 'hidden' })
        assert(f.requests.primary > 0 && f.requests.fallback > 0); assert.deepEqual(f.errors, [])
      } finally { await f.context.close() }
    })
    await run('MAP-CONFIG-03-total-failure-and-manual-retry', async () => {
      const f = await fixture(browser, false, false)
      try {
        const retry = f.page.locator('.map-loading button')
        await retry.waitFor({ state: 'visible' })
        const old = f.requests.primary
        await retry.click()
        await f.page.waitForTimeout(500)
        assert(f.requests.primary > old); assert.equal(f.requests.writes, 0); assert.deepEqual(f.errors, [])
      } finally { await f.context.close() }
    })
  } finally {
    await browser.close()
    fs.writeFileSync(process.env.MAP_TEST_REPORT || '/tmp/map-configuration-results.json', JSON.stringify(results, null, 2))
  }
  if (results.length !== 3 || results.some(result => result.status !== 'PASS')) process.exitCode = 1
})().catch(error => { console.error(error.name); process.exitCode = 1 })
