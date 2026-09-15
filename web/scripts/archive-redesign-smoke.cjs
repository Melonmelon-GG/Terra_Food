const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const { PNG } = require('pngjs')
const jsQR = require('jsqr')
const base = process.env.SHARE_TEST_URL || 'http://127.0.0.1:5173'
const out = process.env.ARCHIVE_TEST_OUT || path.join(require('node:os').tmpdir(), 'dayan-archive-test')
const shareOnly = process.argv.includes('--share-only')
const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540"><defs><radialGradient id="g"><stop stop-color="#b88755"/><stop offset="1" stop-color="#36251d"/></radialGradient></defs><rect width="960" height="540" fill="url(#g)"/><ellipse cx="480" cy="290" rx="320" ry="185" fill="#ddc6a2"/><ellipse cx="480" cy="277" rx="285" ry="158" fill="#733923"/><g fill="#bd6b39" stroke="#773621" stroke-width="8"><rect x="300" y="160" width="130" height="110" rx="12"/><rect x="460" y="140" width="140" height="110" rx="12"/><rect x="408" y="282" width="140" height="106" rx="12"/></g></svg>'
const user = { id:42, username:'explorer', displayName:'寻味者·龙门', signature:'走过山海，记住每一口家乡的味道。', role:'ADMIN', active:true, createdAt:'2026-09-01' }
const dish = {id:123,name:'龙门红烧肉',region:{id:1,name:'南京',province:'江苏省'},latitude:32,longitude:118,summary:'小火慢炖，浓油赤酱。一道承载着旧日记忆与人间烟火的经典风味。',ingredients:'五花肉、冰糖、黄酒、八角、葱姜',story:'循着街巷里的熟悉味道，记下旅途中的一餐一饭。\n一碗热饭，也是相逢的理由。',imageUrl:'/test-food.svg',creator:user,createdBy:user.username,heat:88,reviewStatus:'APPROVED',contentVersion:2,createdAt:'2026-09-13'}
const pageData = items => ({ items, total:items.length, page:1, pageSize:20 })
async function noOverflow(page, label) {
 const sizes = await page.evaluate(() => ({width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth}))
 assert(sizes.scroll <= sizes.width + 1, label + ' overflows: ' + JSON.stringify(sizes))
}
async function screenshot(page, name) {
 await page.screenshot({ path:path.join(out,name+'.png'), fullPage:true })
}
async function openShare(page) {
 await page.locator('.food-share-button').click()
 await page.waitForFunction(() => document.querySelector('.share-export') && !document.querySelector('.share-export').disabled)
}
async function exportTicket(page, name) {
 const geometry = await page.locator('.share-card').evaluate(card => {
  const box = selector => card.querySelector(selector).getBoundingClientRect()
  const main = box('.ticket-main'), stub = box('.ticket-stub'), photo = box('.share-dish-image')
  const frame = card.getBoundingClientRect()
  const copy = box('.ticket-dish-copy'), ingredients = box('.share-ingredients')
  return { landscape:card.clientWidth > card.clientHeight, stubOnRight:stub.left >= main.right - 1,
   photoRatio:photo.width/photo.height, copyFits:copy.bottom <= ingredients.top,
   photoFits:photo.bottom <= ingredients.top, allInside:ingredients.bottom <= frame.bottom && stub.bottom <= frame.bottom,
   noInset:Math.abs(main.left-frame.left)<2 && Math.abs(main.top-frame.top)<2,
   photoDominates:photo.width >= frame.width * .45 }
 })
 assert(geometry.landscape && geometry.stubOnRight,'ticket must remain landscape with a right-hand stub')
 assert(Math.abs(geometry.photoRatio-16/9)<.015,'ticket photo must remain 16:9')
 assert(geometry.photoDominates,'dish photo should occupy the visual majority of the ticket body')
 assert(geometry.copyFits && geometry.photoFits,'dish contents must not overlap ingredients')
 assert(geometry.allInside && geometry.noInset,'page styles must not add insets or clip ticket contents')
 const pending = page.waitForEvent('download')
 await page.locator('.share-export').click()
 const download = await pending
 const filename = path.join(out,name+'.png')
 await download.saveAs(filename)
 const png = PNG.sync.read(await fs.readFile(filename))
 assert.equal(png.width,2000)
 assert.equal(png.height,1000)
 const decoded = jsQR(new Uint8ClampedArray(png.data),png.width,png.height)
 assert.equal(decoded?.data,base+'/foods/123')
 return filename
}
;(async () => {
 await fs.mkdir(out,{recursive:true})
 const browser = await chromium.launch({channel:process.env.SHARE_TEST_BROWSER || 'chrome',headless:true})
 try {
  for (const width of shareOnly ? [1440,390] : [1440,768,390,320]) {
   const page = await browser.newPage({viewport:{width,height:960},locale:'zh-CN',colorScheme:'light'})
   const errors = []
   page.on('pageerror', e => errors.push(e.message))
   page.on('console', m => { if (m.type() === 'warning' && m.text().includes('Not found')) errors.push(m.text()) })
   page.on('dialog', dialog => dialog.accept())
   let favorite = false
   let missingPhoto = false
   let longText = false
   let reviewed = false
   let failedReview = false
   let holdFavorites = false
   let releaseFavorites
   let favoritesStarted
   const favoritesBlocked = new Promise(resolve => { releaseFavorites = resolve })
   const favoritesRequested = new Promise(resolve => { favoritesStarted = resolve })
   const personalRequests = []
   await page.route('**/test-food.svg', route => route.fulfill({contentType:'image/svg+xml',body:svg}))
   await page.route('**/missing-food.png', route => route.fulfill({status:404,body:''}))
   await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    const p = url.pathname
    const method = route.request().method()
    if (/\/(profile|etchings|users|achievements)\//.test(p) && !p.endsWith('/notifications')) personalRequests.push(p)
    if (p === '/api/auth/me') return route.fulfill({json:user})
    if (p === '/api/foods/123') return route.fulfill({json:{...dish,imageUrl:missingPhoto?'/missing-food.png':dish.imageUrl,name:longText?'这是一个用于测试手机和票根排版的很长很长的菜品名称'.repeat(3):dish.name,summary:longText?dish.summary.repeat(20):dish.summary,ingredients:longText?dish.ingredients.repeat(10):dish.ingredients}})
    if (p.endsWith('/like/status') || p.endsWith('/likes')) return route.fulfill({json:{likedByMe:false,likeCount:128}})
    if (/\/favorites\/\d+/.test(p)) { if(method==='POST')favorite=true; if(method==='DELETE')favorite=false; return route.fulfill({json:{favorited:favorite}}) }
    if (p.endsWith('/status')) return route.fulfill({json:{favorited:favorite,listed:false}})
    if (p.endsWith('/comments/page')) return route.fulfill({json:pageData([{id:1,foodId:123,author:user,content:'看着就好香，记到下一次的旅途里。',createdAt:'2026-09-13'}])})
    if (p === '/api/profile/foods') return route.fulfill({json:[dish,{...dish,id:124,name:'兰州牛肉面',reviewStatus:'PENDING'},{...dish,id:125,name:'南翔小笼',reviewStatus:'REJECTED'}]})
    if (p === '/api/profile/favorites/page') {
     if (holdFavorites) { favoritesStarted(); await favoritesBlocked }
     return route.fulfill({json:pageData([dish])})
    }
    if (p === '/api/profile/check-ins' || p === '/api/profile/wishlist/page') return route.fulfill({json:pageData([])})
    if (p === '/api/regions') return route.fulfill({json:[dish.region]})
    if (p === '/api/users/42') return route.fulfill({json:{...user,foods:[dish],selectedAchievement:null,selectedEtching:null}})
    if (p === '/api/admin/users') return route.fulfill({json:pageData([user])})
    if (p === '/api/admin/foods/123/review') {
     assert.equal(route.request().postDataJSON().expectedVersion,2)
     if(failedReview)return route.fulfill({status:500,json:{message:'Retry'}})
     reviewed=true
     return route.fulfill({status:204,body:''})
    }
    if (p === '/api/admin/foods') {
     const items = url.searchParams.get('status') === 'PENDING' ? (reviewed?[]:[{...dish,reviewStatus:'PENDING'}]) : [dish]
     return route.fulfill({json:{...pageData(items),totalHeat:88,pendingTotal:reviewed?0:1}})
    }
    return route.fulfill({json:[]})
   })
   assert.equal((await page.goto(base+'/foods/123')).status(),200)
   await page.locator('.archive-dossier h1').waitFor()
   await page.locator('.archive-food-visual img').evaluate(img=>img.decode())
   await noOverflow(page,'detail '+width)
   const box = await page.locator('.archive-food-visual').boundingBox()
   assert(Math.abs(box.width/box.height-16/9)<.015)
   assert.equal(await page.locator('.archive-reading img').count(),0)
   assert.equal(await page.locator('.archive-dossier .archive-landmark').getAttribute('src'),'/art/landmarks/lungmen.png')
   assert((await page.locator('.archive-dossier').innerText()).includes('江苏省'))
   await page.waitForFunction(()=>!document.querySelector('.collection-button').disabled)
   await page.locator('.collection-button').first().click()
   await page.waitForFunction(()=>document.querySelector('.collection-button').classList.contains('active'))
   assert(favorite)
   await screenshot(page,'detail-'+width)
   const beforeOpen = personalRequests.length
   await openShare(page)
   assert.equal(personalRequests.length,beforeOpen,'opening a ticket should not fetch personal information')
   const afterOpen = personalRequests.length
   assert.equal(await page.locator('.share-person,.share-seals,.share-stats,.share-credit').count(),0)
   assert(!(await page.locator('.share-card').innerText()).includes(user.displayName))
   await noOverflow(page,'share '+width)
   if(width===1440 || width===390) {
    await page.locator('.share-viewport').screenshot({path:path.join(out,'ticket-preview-'+width+'.png')})
    await exportTicket(page,'ticket-'+width)
   }
   assert.equal(personalRequests.length,afterOpen,'export should not fetch personal information')
   await page.locator('.share-close').click()
   if(!shareOnly) {
    await page.goto(base+'/profile')
    await page.locator('.profile-food-card').first().waitFor()
    await noOverflow(page,'profile '+width)
    await screenshot(page,'profile-'+width)
    await page.locator('.archive-tabs button').filter({hasText:'收藏夹'}).click()
    await page.locator('.favorite-card').waitFor()
    await page.locator('.archive-tabs button').filter({hasText:'想吃清单'}).click()
    await page.locator('.wishlist-form').waitFor()
    if(width===1440) {
     holdFavorites=true
     await page.locator('.archive-tabs button').filter({hasText:'收藏夹'}).click()
     await favoritesRequested
     const wishlistResponse = page.waitForResponse(response=>response.url().includes('/profile/wishlist/page'))
     await page.locator('.archive-tabs button').filter({hasText:'想吃清单'}).click()
     await wishlistResponse
     releaseFavorites()
     holdFavorites=false
     assert(await page.locator('.wishlist-form').isVisible())
    }
    await page.locator('.archive-tabs button').filter({hasText:'蚀刻章'}).click()
    await page.locator('.etching-create-button').click()
    await page.locator('.etching-studio').waitFor()
    await noOverflow(page,'etching '+width)
    await page.goto(base+'/users/42')
    await page.locator('.user-food-card').waitFor()
    await noOverflow(page,'public '+width)
    await page.goto(base+'/about')
    await page.locator('.archive-about').waitFor()
    await noOverflow(page,'about '+width)
    await screenshot(page,'about-'+width)
    await page.goto(base+'/admin')
    await page.locator('.admin-table-wrap tbody tr').first().waitFor()
    await noOverflow(page,'admin table '+width)
    await page.locator('.admin-tab').nth(1).click()
    await page.locator('.archive-review-queue button').first().waitFor()
    await screenshot(page,'admin-'+width)
    await page.locator('.archive-review-queue button').first().click()
    await page.locator('.archive-review-detail').waitFor()
    await noOverflow(page,'admin detail '+width)
    if(width<=800) {
     await screenshot(page,'admin-review-'+width)
     await page.locator('.archive-back-queue').click()
     assert(await page.locator('.archive-review-queue').isVisible())
     await page.locator('.archive-review-queue button').first().click()
    }
    failedReview=true
    await page.locator('.archive-review-actions button').first().click()
    await page.locator('.admin-table-card .error').waitFor()
    failedReview=false
    await page.locator('.admin-tab').nth(1).click()
    await page.locator('.archive-review-queue button').first().click()
    await page.locator('.archive-review-actions button').first().click()
    await page.waitForFunction(()=>!document.querySelector('.archive-review-workspace'))
    assert(reviewed)
   }
   if(width===390) {
    missingPhoto=true
    longText=true
    await page.goto(base+'/foods/123')
    await page.locator('.archive-no-cover').waitFor()
    await noOverflow(page,'long missing detail')
    await openShare(page)
    assert.equal(await page.locator('.share-dish-image img').count(),0)
    await page.locator('.share-import input').setInputFiles({name:'bad.txt',mimeType:'text/plain',buffer:Buffer.from('bad')})
    await page.locator('.share-error').waitFor()
    const image = await fs.readFile(path.join(out,'ticket-390.png'))
    await page.locator('.share-import input').setInputFiles({name:'background.png',mimeType:'image/png',buffer:image})
    await page.waitForFunction(()=>!document.querySelector('.share-export').disabled)
    await page.locator('.share-controls button').first().click()
    await exportTicket(page,'ticket-long-missing')
    await page.keyboard.press('Escape')
    await page.waitForFunction(()=>!document.querySelector('dialog'))
    await page.evaluate(()=>document.documentElement.dataset.theme='dark')
    await noOverflow(page,'dark detail')
    await screenshot(page,'detail-dark-390')
   }
   assert.deepEqual(errors,[])
   await page.close()
  }
  console.log(shareOnly
   ? 'PASS: landscape ticket PNG 2000x1000 + decoded QR; desktop/mobile; 16:9 photo; no personal data; missing image/long text; background validation; Escape.'
   : 'PASS: responsive archive pages; favorites; review failure/retry; 16:9 image; province fallback; landscape ticket PNG 2000x1000 + decoded QR; no personal data; missing image/long text; background validation; Escape.')
 } finally {await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1})
