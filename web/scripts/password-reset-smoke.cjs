// Run only against an isolated preview. All API calls are deterministic test doubles.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.RESET_BASE_URL || 'http://127.0.0.1:18481';
const output = process.env.RESET_REPORT_DIR || '/evidence';
if (!['127.0.0.1', 'localhost'].includes(new URL(base).hostname)) throw Error('loopback preview required');
fs.mkdirSync(output, { recursive: true });
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.BROWSER_EXECUTABLE || '/ms-playwright/chromium-1148/chrome-linux/chrome', headless: true });
  const results = [];
  async function setup(options = {}) {
    const context = await browser.newContext({ locale: options.locale || 'zh-CN', viewport: { width: options.width || 390, height: 844 } });
    const page = await context.newPage(), calls = [], pending = [], errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.route('**/*', async route => {
      const u = new URL(route.request().url());
      if (u.pathname.startsWith('/api/')) {
        const kind = u.pathname.endsWith('/password-reset-code') ? 'send' : u.pathname.endsWith('/password-reset') ? 'reset' : '';
        if (kind) {
          calls.push(kind);
          if (options.delay === kind) {
            const status = await new Promise(resolve => pending.push(resolve));
            return route.fulfill({ status, ...(status === 204 ? { body: '' } : { json: { message: '验证码已失效，请重新获取' } }) }).catch(() => {});
          }
          return route.fulfill(options.reject && kind === 'reset' ? { status: 400, json: { message: '验证码已失效，请重新获取' } } : { status: 204, body: '' });
        }
        if (u.pathname.endsWith('/auth/me')) return route.fulfill({ status: 401, json: {} });
        if (u.pathname.endsWith('/auth/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test' } });
        return route.fulfill({ json: [] });
      }
      return u.origin === new URL(base).origin ? route.continue() : route.abort();
    });
    await page.goto(base + '/login?role=ADMIN');
    await page.locator('.password-reset-toggle').click();
    const form = page.locator('.password-reset-form');
    const input = name => form.locator(`[name=${name}]`);
    await input('username').fill('reset_test'); await input('email').fill('reset@example.test');
    const ready = async () => { for (let i = 0; i < 100 && !pending.length; i++) await page.waitForTimeout(20); assert(pending.length); };
    const fill = async (password = 'Valid123') => { await input('verificationCode').fill('123456'); await input('newPassword').fill(password); await input('confirmPassword').fill(password); };
    return { context, page, form, input, calls, pending, ready, fill, errors, send: form.locator('button[type=button]'), submit: form.locator('.login-submit') };
  }
  async function test(name, options, body) {
    const x = await setup(options);
    try { await body(x); assert.deepEqual(x.errors, []); results.push({ name, status: 'PASS' }); }
    catch (e) { results.push({ name, status: 'FAIL', error: e.message }); }
    finally { for (const resolve of x.pending) resolve(204); await x.context.close(); }
  }
  await test('validation-boundaries-and-focus', {}, async x => {
    await x.input('username').fill('reset@example.test'); await x.send.click();
    assert.equal(await x.page.evaluate(() => document.activeElement.name), 'username'); assert.equal(x.calls.length, 0);
    assert.match(await x.form.innerText(), /不是邮箱或昵称/);
    await x.input('username').fill('reset_test'); await x.fill();
    for (const password of ['Abc1234', 'Abc12345678901234', 'Abc1234!', '１２３４Ａｂｃｄ', '12345678', 'abcdefgh', ' Valid12']) {
      await x.fill(password); await x.submit.click(); assert.equal(x.calls.length, 0);
      assert.equal(await x.input('newPassword').getAttribute('aria-invalid'), 'true');
      assert.equal(await x.input('newPassword').inputValue(), password);
    }
    await x.fill(); await x.input('verificationCode').fill('12345'); await x.submit.click();
    assert.equal(await x.page.evaluate(() => document.activeElement.name), 'verificationCode');
    await x.input('verificationCode').fill('１２３４５６'); await x.submit.click(); assert.equal(x.calls.length, 0);
    await x.fill(); await x.input('confirmPassword').fill('Different123'); await x.submit.click(); assert.equal(x.calls.length, 0);
    await x.page.screenshot({ path: output + '/mobile-errors.png', fullPage: true });
  });
  for (const [width, locale, password] of [[390, 'zh-CN', 'Valid123'], [1366, 'en-US', 'Abcd1234Abcd1234']]) {
    await test(`success-${width}-${locale}`, { width, locale }, async x => {
      await x.send.click(); await x.form.locator('.verification-code-status').waitFor();
      await x.fill(password); await x.input('confirmPassword').press('Enter'); await x.form.waitFor({ state: 'detached' });
      assert.deepEqual(x.calls, ['send', 'reset']); assert(await x.page.locator('.form-success').count());
      assert.equal(await x.page.locator('.login-form input[autocomplete=username]').inputValue(), 'reset_test');
      assert.equal(await x.page.locator('.login-form input[type=password]').inputValue(), '');
      await x.page.locator('.password-reset-toggle').click();
      for (const name of ['verificationCode', 'newPassword', 'confirmPassword']) assert.equal(await x.input(name).inputValue(), '');
      await x.page.screenshot({ path: output + `/success-${width}.png`, fullPage: true });
    });
  }
  await test('identity-change-and-cooldown-reopen', {}, async x => {
    await x.send.click(); await x.form.locator('.verification-code-status').waitFor(); await x.fill();
    await x.input('email').fill('other@example.test');
    assert.equal(await x.form.locator('.verification-code-status').count(), 0); assert.equal(await x.input('verificationCode').inputValue(), ''); assert.equal(await x.send.isDisabled(), false);
    await x.input('email').fill('reset@example.test'); assert(await x.send.isDisabled());
    await x.page.locator('.password-reset-toggle').click(); await x.page.locator('.password-reset-toggle').click();
    assert(await x.send.isDisabled()); assert.equal(await x.input('newPassword').inputValue(), '');
  });
  for (const oldStatus of [204, 400]) await test(`late-send-${oldStatus}`, { delay: 'send' }, async x => {
    await x.send.click(); await x.ready(); const first = x.pending.shift();
    await x.input('email').fill('other@example.test'); await x.send.click(); await x.ready();
    first(oldStatus); await x.page.waitForTimeout(100);
    assert(await x.submit.isDisabled()); assert.equal(await x.form.locator('.verification-code-status').count(), 0);
    assert.equal(await x.form.locator('.form-error').count(), 0);
    x.pending.shift()(204); await x.form.locator('.verification-code-status').waitFor();
  });
  await test('cancel-send-reopen', { delay: 'send' }, async x => {
    await x.send.click(); await x.ready(); await x.fill();
    await x.page.locator('.password-reset-toggle').click(); await x.page.locator('.password-reset-toggle').click();
    x.pending.shift()(204); await x.page.waitForTimeout(100);
    assert.equal(await x.form.locator('.verification-code-status').count(), 0); assert.equal(await x.input('newPassword').inputValue(), '');
  });
  await test('frozen-reset-and-double-submit', { delay: 'reset' }, async x => {
    await x.fill(); await x.submit.click(); await x.ready();
    assert(await x.input('username').isDisabled()); assert(await x.send.isDisabled());
    await x.form.dispatchEvent('submit'); assert.deepEqual(x.calls, ['reset']);
    x.pending.shift()(204); await x.form.waitFor({ state: 'detached' });
  });
  await test('cancel-reset-is-uncertain', { delay: 'reset' }, async x => {
    await x.fill(); await x.submit.click(); await x.ready(); await x.page.locator('.password-reset-toggle').click();
    assert.match(await x.page.locator('.password-reset').innerText(), /改密结果尚未确认/);
    await x.page.locator('.password-reset-toggle').click(); x.pending.shift()(204); await x.page.waitForTimeout(100);
    assert(await x.form.isVisible()); assert.equal(await x.input('newPassword').inputValue(), ''); assert.equal(await x.page.locator('.form-success').count(), 0);
  });
  await test('reset-timeout-no-retry', { delay: 'reset' }, async x => {
    await x.fill(); await x.submit.click(); await x.ready();
    await x.page.getByText('改密结果尚未确认', { exact: false }).waitFor({ timeout: 12000 });
    assert.deepEqual(x.calls, ['reset']); assert.equal(await x.page.locator('.form-success').count(), 0);
  });
  await test('explicit-error-keeps-editable-form', { reject: true }, async x => {
    await x.fill(); await x.submit.click(); await x.form.locator('.form-error').waitFor();
    assert.match(await x.form.locator('.form-error').innerText(), /验证码已失效/); assert.equal(await x.input('username').isDisabled(), false);
  });
  await test('unmount-drops-late-response', { delay: 'send' }, async x => {
    await x.send.click(); await x.ready(); await x.page.goto(base + '/register'); x.pending.shift()(204);
    await x.page.goto(base + '/login?role=ADMIN'); await x.page.locator('.password-reset-toggle').click();
    assert.equal(await x.input('verificationCode').inputValue(), ''); assert.equal(await x.form.locator('.verification-code-status').count(), 0);
  });
  await browser.close();
  fs.writeFileSync(output + '/browser.json', JSON.stringify({ base, results, apiMode: 'deterministic interception' }, null, 2));
  console.log(JSON.stringify(results)); if (results.some(x => x.status !== 'PASS')) process.exitCode = 1;
})().catch(e => { console.error(e); process.exit(1); });
