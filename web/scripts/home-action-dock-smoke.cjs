const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const base = process.env.HOME_TEST_URL || 'http://127.0.0.1:5173'
const screenshotDirectory = process.env.HOME_TEST_SCREENSHOT_DIR
const pixel = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360"><rect width="640" height="360" fill="#a05242"/></svg>',
)

;(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      const page = await browser.newPage({ viewport, locale: 'zh-CN' })
      const errors = []
      page.on('pageerror', error => errors.push(error.message))
      await page.addInitScript(() => {
        navigator.geolocation.getCurrentPosition = success => success({
          coords: { latitude: 31.23, longitude: 121.47, accuracy: 50 },
          timestamp: Date.now(),
        })
      })
      const user = { id: 1, username: 'tester', displayName: '测试用户', role: 'USER', active: true }
      const food = {
        id: 1, name: '龙门红烧肉', region: { id: 1, name: '龙门', province: '大炎' },
        latitude: 31.23, longitude: 121.47, summary: '慢火煨出的家乡味。',
        story: '掌故', ingredients: '五花肉', imageUrl: pixel, heat: 31,
        reviewStatus: 'APPROVED', contentVersion: 1, createdBy: 'tester', creator: user,
        createdAt: '2026-09-12',
      }
      await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname
        if (path === '/api/auth/me') return route.fulfill({ json: user })
        if (path === '/api/foods/catalog') return route.fulfill({ json: { items: [food], total: 1, page: 1, pageSize: 30 } })
        if (path === '/api/foods/map-clusters') return route.fulfill({ json: { dataVersion: 1, total: 1, zoom: 4, items: [] } })
        if (path === '/api/regions') return route.fulfill({ json: [food.region] })
        if (path === '/api/food-tags') return route.fulfill({ json: [] })
        return route.fulfill({ json: [] })
      })

      assert.equal((await page.goto(base + '/')).status(), 200)
      await page.locator('.explorer-card').waitFor()
      assert.equal(await page.locator('.explorer-sidebar').count(), 0)
      assert.equal(await page.locator('.explorer-toolbar .explorer-search').count(), 0)
      assert.equal(await page.locator('#app > header .header-food-search').count(), 1)
      const header = await page.locator('#app > header').boundingBox()
      const search = await page.locator('.header-food-search').boundingBox()
      assert(header && search && search.y >= header.y && search.y + search.height <= header.y + header.height)
      const toolbar = await page.locator('.explorer-toolbar').boundingBox()
      assert(toolbar && toolbar.y < 110)

      const card = await page.locator('.explorer-card').boundingBox()
      const photo = await page.locator('.explorer-card-photo').boundingBox()
      const body = await page.locator('.explorer-card-body').boundingBox()
      assert(card && photo && body)
      assert(Math.abs(photo.width - card.width) <= 2 && Math.abs(photo.height - card.height) <= 2)
      assert(Math.abs(body.y + body.height - card.y - card.height) <= 2)
      assert.notEqual(await page.locator('.explorer-card-body').evaluate(node => getComputedStyle(node).backdropFilter), 'none')

      const trigger = page.getByRole('button', { name: '展开快捷操作' })
      await trigger.click()
      assert.equal(await page.locator('.home-action-list button').count(), 4)
      assert.equal(await page.locator('.agent-panel').count(), 0)
      if (screenshotDirectory) {
        fs.mkdirSync(screenshotDirectory, { recursive: true })
        await page.screenshot({ path: path.join(screenshotDirectory, `home-${viewport.width}.png`), fullPage: true })
      }
      await page.getByRole('button', { name: '余 AI', exact: true }).click()
      await page.locator('.agent-panel').waitFor()
      await page.locator('.agent-panel header button').click()

      await page.getByRole('button', { name: '展开快捷操作' }).click()
      await page.getByRole('button', { name: '音乐', exact: true }).click()
      await page.locator('#MusicControl .control-bar').waitFor()
      await page.locator('#MusicControl .minimize-btn').click()

      await page.getByRole('button', { name: '展开快捷操作' }).click()
      await page.getByRole('button', { name: '定位', exact: true }).click()
      await page.getByText('已定位到你附近，请点击地图确认菜品的具体位置。').waitFor()

      await page.getByRole('button', { name: '展开快捷操作' }).click()
      await page.getByRole('button', { name: '＋ 收录珍馐', exact: true }).click()
      await page.getByText('请先在地图上点击选择坐标，再收录珍馐。').waitFor()
      assert.deepEqual(errors, [])
      await page.close()
    }
    console.log('PASS desktop/mobile: no sidebar, top search, four-action dock, agent/location/add actions, full-image frosted cards')
  } finally {
    await browser.close()
  }
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
