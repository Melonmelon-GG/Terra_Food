const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const base = process.env.SHARE_TEST_URL || 'http://127.0.0.1:5173'
;(async () => {
 const browser = await chromium.launch(process.env.SHARE_TEST_EXECUTABLE
  ? { executablePath: process.env.SHARE_TEST_EXECUTABLE, headless: true }
  : { channel: process.env.SHARE_TEST_BROWSER || 'chrome', headless: true })
 try {
  for (const width of [1440,390]) {
   const page = await browser.newPage({viewport:{width,height:900},locale:'zh-CN'})
   const errors=[]
   page.on('pageerror',e=>{ errors.push(e.message); console.error('BROWSER_PAGE_ERROR',e.message) })
   page.on('console',message=>{ if(message.type()==='error') console.error('BROWSER_CONSOLE_ERROR',message.text()) })
   let fail=true
   let food={id:7,name:'测试菜品',region:{id:1,name:'城市',province:'省份'},latitude:30,longitude:110,address:'旧地址',summary:'简介',story:'掌故',ingredients:'食材',imageUrl:'',remark:'',heat:0,reviewStatus:'APPROVED',createdAt:'2026-09-09'}
   await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname
    if(path==='/api/auth/me')return route.fulfill({json:{id:1,username:'owner',displayName:'用户',role:'ADMIN',active:true}})
    if(path==='/api/profile/foods/7'){
     if(fail)return route.fulfill({status:500,json:{message:'保存失败，请重试'}})
     const payload=route.request().postDataJSON();assert.equal(payload.name,'已纠错菜品');assert.equal(payload.address,'新地址')
     food={...food,...payload,reviewStatus:'PENDING'};return route.fulfill({json:food})
    }
    if(path==='/api/profile/foods')return route.fulfill({json:[food]})
    if(path==='/api/profile/check-ins')return route.fulfill({json:{items:[],total:0,page:1,pageSize:20}})
    if(path==='/api/profile/favorites/page' || path==='/api/profile/wishlist/page')return route.fulfill({json:{items:[],total:0,page:1,pageSize:20}})
    if(path==='/api/regions')return route.fulfill({json:[food.region]})
    return route.fulfill({json:[]})
   })
   const response=await page.goto(base+'/profile');assert.equal(response.status(),200)
   await page.locator('.profile-page').waitFor()
   await page.locator('.profile-food-card').waitFor()
   await page.locator('.profile-food-card button').click()
   await page.locator('.profile-edit-modal input').first().fill('已纠错菜品')
   await page.locator('.profile-edit-modal input[maxlength="500"]').first().fill('新地址')
   await page.locator('.profile-edit-modal .primary-button').click()
   await page.getByText('保存失败，请重试',{exact:true}).waitFor()
   assert.equal(await page.locator('.profile-edit-modal input').first().inputValue(),'已纠错菜品')
   fail=false
   await page.locator('.profile-edit-modal .primary-button').click()
   await page.locator('.profile-food-card .is-pending').waitFor()
   assert.equal(await page.locator('.profile-food-card a').count(),0)
   await page.getByRole('status').waitFor()
   await page.reload()
   await page.locator('.profile-food-card .is-pending').waitFor()
   await page.locator('.profile-food-card button').click()
   assert.equal(await page.locator('.profile-edit-modal input').first().inputValue(),'已纠错菜品')
   assert.deepEqual(errors,[])
   await page.close()
  }
  console.log('PASS desktop/mobile: edit, failed save retains input, retry, pending status, hidden public link, reload persistence')
 } finally { await browser.close() }
})().catch(error=>{console.error(error);process.exitCode=1})
