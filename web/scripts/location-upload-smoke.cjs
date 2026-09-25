const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const base = process.env.LOCATION_URL || 'http://127.0.0.1:18384'
const baseline = process.env.LOCATION_BASELINE === '1'
const results = []
const user = { id: 1, username: 'fixture', displayName: '测试', role: 'USER', active: true }
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBz0AAAAASUVORK5CYII=', 'base64')
async function fixture(browser, mode = 'denied', tiles = false, width = 390, delayedAddress = false) {
  const page = await browser.newPage({ viewport: { width, height: 844 }, locale: 'zh-CN' })
  page.setDefaultTimeout(8000)
  const errors = [], writes = []
  page.on('pageerror', e => errors.push(e.message))
  page.on('dialog', d => d.accept())
  await page.addInitScript(mode => {
    window.geoCalls = []
    Object.defineProperty(navigator, 'geolocation', { configurable: true, value: {
      getCurrentPosition(success, failure, options) {
        window.geoCalls.push(options)
        window.geoSuccess = () => success({ coords: { latitude: 30.5, longitude: 114.3, accuracy: 35 } })
        if (mode === 'success') window.geoSuccess()
        else if (mode === 'denied') failure({ code: 1, PERMISSION_DENIED: 1 })
        else if (mode === 'throws') throw new Error('simulated browser failure')
        else if (mode === 'invalid') success({ coords: { latitude: NaN, longitude: 114, accuracy: 3 } })
      },
    } })
  }, mode)
  await page.route('**/*', async route => {
    const r = route.request(), u = new URL(r.url())
    if (u.pathname.startsWith('/api/')) {
      let json = []
      if (u.pathname === '/api/auth/me') json = user
      else if (u.pathname === '/api/auth/csrf') json = { token: 'fixture', headerName: 'X-CSRF-TOKEN' }
      else if (u.pathname === '/api/foods/catalog') json = { items: [], total: 0, page: 1, pageSize: 30 }
      else if (u.pathname === '/api/foods/map-clusters') json = { items: [], total: 0, dataVersion: 1, zoom: 4 }
      else if (u.pathname === '/api/regions') json = [{ id: 1, province: '湖北', name: '武汉', centerLatitude: 30.5, centerLongitude: 114.3 }]
      else if (u.pathname === '/api/foods' && r.method() === 'POST') {
        writes.push(r.postDataJSON()); json = { ...r.postDataJSON(), id: 999, reviewStatus: 'PENDING' }
      }
      return route.fulfill({ json })
    }
    if (u.pathname === '/audio/music-manifest.json') return route.fulfill({ json: [] })
    if (u.origin !== new URL(base).origin) {
      if (delayedAddress && /reverse|geocoder/.test(u.pathname)) {
        await new Promise(resolve => setTimeout(resolve, 2500))
        return route.fulfill({ json: { features: [{ properties: { state: '旧省', city: '旧市', street: '旧地址' } }] } }).catch(() => {})
      }
      if (tiles && /\.png|DataServer/.test(u.href)) return route.fulfill({ contentType: 'image/png', body: png })
      return route.abort()
    }
    return route.continue()
  })
  await page.goto(base)
  await page.locator('.leaflet-control-zoom-in').waitFor({ state: 'attached' })
  return { page, errors, writes }
}
async function add(page) {
  await page.locator('.home-action-trigger').click()
  await page.locator('.home-action-list').getByRole('button', { name: /收录珍馐/ }).click()
}
async function run(id, fn) {
  if (process.env.LOCATION_CASES && !process.env.LOCATION_CASES.split(',').includes(id)) return
  try { await fn(); results.push({ id, status: 'PASS' }); console.log('PASS', id) }
  catch (e) { results.push({ id, status: 'FAIL', message: String(e) }); console.error(e); process.exitCode = 1 }
}
;(async () => {
  const browser = await chromium.launch({ executablePath: '/ms-playwright/chromium-1148/chrome-linux/chrome', headless: true })
  try {
    if (baseline) {
      await run('BASELINE-add-blocked-and-tile-overlay', async () => {
        const { page } = await fixture(browser)
        await add(page)
        assert.equal(await page.locator('.upload-modal').count(), 0)
        const box = await page.locator('.food-map').boundingBox()
        const blocked = await page.evaluate(({x,y}) => !!document.elementFromPoint(x,y)?.closest('.map-loading'), {x: box.x+box.width/2, y: box.y+box.height*.4})
        assert.equal(blocked, true)
        await page.screenshot({ path: '/evidence/baseline-blocked.png', fullPage: true })
        await page.close()
      })
    } else {
      await run('LOCATION-city-and-draft', async () => {
        const {page,errors}=await fixture(browser)
        await add(page);const modal=page.locator('.upload-modal');await modal.waitFor()
        await modal.locator('.location-entry select').selectOption('1')
        await modal.waitFor({state:'hidden'})
        assert.equal(await page.locator('.food-map-picked-marker').count(),0)
        await add(page);await modal.waitFor()
        assert.equal(await modal.locator('input[type=number]').first().inputValue(),'')
        await modal.locator('input[type=number]').nth(0).fill('30.52')
        await modal.locator('input[type=number]').nth(1).fill('114.31')
        await modal.locator('input[maxlength="500"]').first().fill('用户明确填写的地址')
        await modal.locator('.modal-title .icon-button').click()
        await add(page);await modal.waitFor()
        assert.equal(await modal.locator('input[type=number]').first().inputValue(),'30.52')
        assert.equal(await modal.locator('input[maxlength="500"]').first().inputValue(),'用户明确填写的地址')
        assert.deepEqual(errors,[]);await page.close()
      })
      await run('LOCATION-late-address', async () => {
        const {page,errors}=await fixture(browser,'denied',false,390,true)
        const box=await page.locator('.food-map').boundingBox()
        await page.locator('.food-map').click({position:{x:box.width/2,y:box.height*.4}})
        await page.locator('.food-map-picked-marker').waitFor()
        await add(page);const modal=page.locator('.upload-modal');await modal.waitFor()
        await modal.locator('input[maxlength="500"]').first().fill('新地址不得被旧响应覆盖')
        await page.waitForTimeout(3000)
        assert.equal(await modal.locator('input[maxlength="500"]').first().inputValue(),'新地址不得被旧响应覆盖')
        assert.deepEqual(errors,[]);await page.close()
      })
      await run('LOCATION-denied-manual-create', async () => {
        const {page, errors, writes} = await fixture(browser)
        await add(page); const modal=page.locator('.upload-modal'); await modal.waitFor()
        await modal.getByRole('button', {name:'使用当前位置',exact:true}).click()
        await modal.getByText(/定位权限未开启/).waitFor()
        const numbers=modal.locator('input[type=number]')
        await numbers.nth(0).fill('30.5'); await numbers.nth(1).fill('114.3')
        await modal.locator('input[maxlength="100"]').first().fill('定位回归样本')
        await modal.locator('textarea[maxlength="1000"]').first().fill('简介')
        await modal.locator('input[required][maxlength="500"]').fill('食材')
        await modal.locator('textarea[required]').last().fill('掌故')
        await modal.locator('button.primary-button').click()
        await modal.waitFor({state:'hidden'})
        assert.equal(writes.length,1); assert.equal(writes[0].latitude,30.5)
        assert.equal(writes[0].longitude,114.3);assert.deepEqual(errors,[])
        await page.close()
      })
      await run('LOCATION-success-and-late-callback', async () => {
        const {page,errors}=await fixture(browser,'pending')
        await add(page);const modal=page.locator('.upload-modal');await modal.waitFor()
        await modal.getByRole('button',{name:'使用当前位置',exact:true}).click()
        const fields=modal.locator('input[type=number]')
        await fields.nth(0).fill('31');await fields.nth(1).fill('115')
        await page.evaluate(()=>window.geoSuccess())
        assert.equal(await fields.nth(0).inputValue(),'31')
        await modal.getByRole('button',{name:'使用当前位置',exact:true}).click()
        await page.evaluate(()=>window.geoSuccess())
        assert.equal(await fields.nth(0).inputValue(),'30.5')
        assert.equal(await page.evaluate(()=>window.geoCalls[0].enableHighAccuracy),false)
        assert.deepEqual(errors,[]);await page.close()
      })
      await run('LOCATION-no-callback-deadline', async () => {
        const {page,errors}=await fixture(browser,'pending')
        await add(page);const modal=page.locator('.upload-modal');await modal.waitFor()
        await modal.getByRole('button',{name:'使用当前位置',exact:true}).click()
        await modal.getByText(/定位等待超时/).waitFor({timeout:15000})
        await page.evaluate(()=>window.geoSuccess())
        assert.equal(await modal.locator('input[type=number]').first().inputValue(),'')
        assert.deepEqual(errors,[]);await page.close()
      })
      await run('LOCATION-browser-errors', async () => {
        for (const mode of ['throws','invalid']) {
          const {page,errors}=await fixture(browser,mode)
          await add(page);const modal=page.locator('.upload-modal');await modal.waitFor()
          await modal.getByRole('button',{name:'使用当前位置',exact:true}).click()
          await modal.getByText(/暂时无法获取当前位置/).waitFor()
          assert.deepEqual(errors,[]);await page.close()
        }
      })
      await run('LOCATION-tiles-down-manual-pick', async () => {
        for (const width of [390,1366]) {
          const {page,errors}=await fixture(browser,'denied',false,width)
          const box=await page.locator('.food-map').boundingBox()
          await page.locator('.food-map').click({position:{x:box.width/2,y:box.height*.4}})
          await page.locator('.food-map-picked-marker').waitFor()
          await add(page);const modal=page.locator('.upload-modal');await modal.waitFor()
          assert.notEqual(await modal.locator('input[type=number]').first().inputValue(),'')
          await page.screenshot({path:`/evidence/fixed-${width}.png`,fullPage:true})
          assert.deepEqual(errors,[]);await page.close()
        }
      })
    }
  } finally {
    await browser.close()
    fs.writeFileSync('/evidence/'+(baseline?'baseline':'regression')+'.json',JSON.stringify({results,limitations:['API and geolocation are deterministic browser fixtures; no production writes or real-device GPS proof.']},null,2))
  }
})().catch(e=>{console.error(e);process.exitCode=1})
