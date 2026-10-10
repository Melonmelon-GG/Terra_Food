const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const base = process.env.SHARE_TEST_URL || 'http://127.0.0.1:5173'
;(async () => {
 const browser = await chromium.launch({ channel: process.env.SHARE_TEST_BROWSER || 'chrome', headless:true })
 try {
  const page = await browser.newPage({locale:'zh-CN'})
  const errors=[]; page.on('pageerror', e=>errors.push(e.message))
  const user={id:1,username:'tester',displayName:'测试用户',role:'USER',active:true,createdAt:'2026-01-01'}
  const dish=id=>({id,name:'菜品'+id,region:{id:1,name:'测试市',province:'测试省'},summary:'测试',story:'测试',ingredients:'食材',creator:user,heat:0,reviewStatus:'APPROVED'})
  let favorite=false, failWishlist=true, saveRequests=0, saveFailure=true, deleteFailure=true
  let etchings=[]
  let releaseOld, oldStarted
  const oldRequest=new Promise(resolve=>oldStarted=resolve)
  const oldResponse=new Promise(resolve=>releaseOld=resolve)
  await page.route('**/api/**',async route=>{
   const p=new URL(route.request().url()).pathname, method=route.request().method()
   if(p==='/api/auth/me')return route.fulfill({json:user})
   if(p==='/api/auth/csrf')return route.fulfill({json:{token:'test-token',headerName:'X-CSRF-TOKEN'}})
   if(p==='/api/foods/mine/page')return route.fulfill({json:{items:[],total:0,page:1,pageSize:20}})
   if(p==='/api/profile/favorites/page' || p==='/api/profile/wishlist/page' || p==='/api/profile/check-ins' || p.endsWith('/comments/page'))return route.fulfill({json:{items:[],total:0,page:1,pageSize:20}})
   if(p==='/api/foods/1'){oldStarted();await oldResponse;return route.fulfill({json:dish(1)})}
   if(/^\/api\/foods\/\d+$/.test(p))return route.fulfill({json:dish(Number(p.split('/').pop()))})
   if(p.endsWith('/likes'))return route.fulfill({json:{likeCount:0,likedByMe:false}})
   if(p.includes('/wishlist/') && p.endsWith('/status'))return route.fulfill({status:failWishlist?500:200,json:{listed:false}})
   if(p.includes('/favorites/')){
    if(method==='POST')favorite=true
    if(method==='DELETE')favorite=false
    return route.fulfill({json:{favorited:favorite}})
   }
   if(p==='/api/etchings/me')return route.fulfill({json:etchings})
   if(p==='/api/etchings' && method==='POST'){
    saveRequests++
    const payload=route.request().postDataJSON()
    assert.equal(payload.layerOne.length,169)
    assert(payload.layerOne.some(Boolean))
    if(saveFailure)return route.fulfill({status:400,json:{message:'每位用户最多保存12枚自制蚀刻章'}})
    const saved={...payload,id:7,selected:false,createdAt:'2026-09-08',updatedAt:'2026-09-08'}
    etchings.push(saved); return route.fulfill({status:201,json:saved})
   }
   if(p==='/api/etchings/7' && method==='DELETE'){
    if(deleteFailure)return route.fulfill({status:500,json:{}})
    etchings=[];return route.fulfill({status:204,body:''})
   }
   if(p==='/api/etchings/7' && method==='PUT'){
    etchings[0]={...etchings[0],...route.request().postDataJSON()}
    return route.fulfill({json:etchings[0]})
   }
   return route.fulfill({json:[]})
  })
  await page.goto(base+'/foods/2')
  await page.waitForFunction(()=>document.querySelector('.collection-button') && !document.querySelector('.collection-button').disabled)
  await page.locator('.collection-button').first().click()
  await page.waitForFunction(()=>document.querySelector('.collection-button').classList.contains('active'))
  assert.equal(favorite,true)
  await page.locator('.collection-button').first().click()
  await page.waitForFunction(()=>!document.querySelector('.collection-button').classList.contains('active'))
  assert.equal(favorite,false)
  // A slow previous dish must not replace the current dish or its share-card source.
  await page.evaluate(()=>{history.pushState({},'', '/foods/1');dispatchEvent(new PopStateEvent('popstate'))})
  await oldRequest
  await page.evaluate(()=>{history.pushState({},'', '/foods/2');dispatchEvent(new PopStateEvent('popstate'))})
  await page.getByRole('heading',{name:'菜品2',exact:true}).waitFor()
  releaseOld()
  await page.waitForResponse(r=>new URL(r.url()).pathname==='/api/foods/1')
  await page.waitForTimeout(100)
  assert.equal(await page.locator('.detail-hero h1').textContent(),'菜品2')
  await page.goto(base+'/profile')
  await page.locator('.archive-tabs button').filter({hasText:'蚀刻章'}).click()
  await page.locator('.etching-create-button').click()
  await page.locator('.etching-tools > label input').fill('新章')
  await page.locator('.etching-studio footer button').last().click()
  assert.equal(saveRequests,0)
  assert(await page.getByText('请至少为一个章格上色后再保存。').isVisible())
  await page.locator('.etching-grid-cell').first().click()
  await page.locator('.etching-studio footer button').last().click()
  await page.getByText('每位用户最多保存12枚自制蚀刻章').waitFor()
  saveFailure=false
  await page.locator('.etching-studio footer button').last().click()
  await page.locator('.custom-etching-item').waitFor()
  assert.equal(await page.locator('.etching-studio').count(),0)
  assert.equal(saveRequests,2)
  await page.reload()
  await page.locator('.archive-tabs button').filter({hasText:'蚀刻章'}).click()
  await page.locator('.custom-etching-item').waitFor()
  assert(await page.locator('.custom-etching-item').textContent().then(s=>s.includes('新章')))
  await page.locator('.custom-etching-item > div button').first().click()
  await page.locator('.etching-studio footer button').last().click()
  await page.waitForFunction(()=>!document.querySelector('.etching-studio'))
  page.on('dialog',d=>d.accept())
  await page.locator('.custom-etching-item > div button').last().click()
  await page.locator('.etching-error').waitFor()
  assert.equal(await page.locator('.custom-etching-item').count(),1)
  deleteFailure=false
  await page.locator('.custom-etching-item > div button').last().click()
  await page.waitForFunction(()=>!document.querySelector('.custom-etching-item'))
  assert.deepEqual(errors,[])
  console.log('PASS: favorite add/remove despite wishlist failure; route race; empty canvas; server validation message; save/reload/unchanged update; failed delete/retry.')
 } finally { await browser.close() }
})().catch(e=>{console.error(e);process.exitCode=1})
