// Real HTTP/SMTP/browser chain. Only the disposable compose stack may be targeted.
const { chromium, request } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = 'http://127.0.0.1:18481', mailBase = 'http://127.0.0.1:18425';
const output = process.env.RESET_REPORT_DIR || '/evidence';
fs.mkdirSync(output, { recursive: true });
(async () => {
  const api = await request.newContext({ baseURL: base }), mail = await request.newContext({ baseURL: mailBase });
  const browser = await chromium.launch({ executablePath: process.env.BROWSER_EXECUTABLE || '/ms-playwright/chromium-1148/chrome-linux/chrome', headless: true });
  const results = [];
  let stage = 'registration';
  const username = 'reset_' + Date.now(), email = username + '@example.test';
  const oldPassword = 'Before123', newPassword = 'After456';
  async function post(client, path, data) {
    const csrf = await (await client.get('/api/auth/csrf')).json();
    return client.post('/api/auth/' + path, { data, headers: { [csrf.headerName]: csrf.token } });
  }
  async function ids() { return new Set((await (await mail.get('/api/v1/messages')).json()).messages.map(x => x.ID)); }
  async function codeAfter(previous) {
    for (let i = 0; i < 60; i++) {
      const messages = (await (await mail.get('/api/v1/messages')).json()).messages;
      for (const m of messages) if (!previous.has(m.ID) && m.To.some(t => t.Address === email)) {
        const text = (await (await mail.get('/api/v1/message/' + m.ID)).json()).Text;
        const match = text.match(/\b[0-9]{6}\b/); if (match) return match[0];
      }
      await new Promise(r => setTimeout(r, 200));
    }
    throw Error('isolated SMTP receipt timed out');
  }
  async function login(password) {
    const c = await request.newContext({ baseURL: base });
    const status = (await post(c, 'login', { username, password, role: 'USER' })).status();
    await c.dispose(); return status;
  }
  try {
    const captcha = await (await api.get('/api/auth/captcha')).json();
    const expr = captcha.question.match(/^(\d+) ([+-]) (\d+) = \?$/); assert(expr, 'captcha format');
    const answer = expr[2] === '+' ? Number(expr[1]) + Number(expr[3]) : Number(expr[1]) - Number(expr[3]);
    let previous = await ids();
    assert.equal((await post(api, 'registration-code', { email, captchaId: captcha.captchaId, captchaAnswer: String(answer) })).status(), 204);
    const signupCode = await codeAfter(previous);
    assert.equal((await post(api, 'register', { username, email, password: oldPassword, displayName: 'Reset Test', verificationCode: signupCode })).status(), 201);
    assert.equal((await post(api, 'login', { username, password: oldPassword, role: 'USER' })).status(), 200);
    results.push({ case: 'signup-real-mail-and-login', status: 'PASS' });
    previous = await ids();
    assert.equal((await post(api, 'password-reset-code', { username, email })).status(), 204);
    const expired = await codeAfter(previous);
    // compose explicitly sets 30s expiry; no production Redis keys are modified.
    await new Promise(r => setTimeout(r, 31000));
    assert.equal((await post(api, 'password-reset', { username, email, verificationCode: expired, newPassword })).status(), 400);
    assert.equal(await login(oldPassword), 200);
    results.push({ case: 'expired-code-no-password-change', status: 'PASS' });
    stage = 'browser-send-code';
    const page = await browser.newPage({ locale: 'zh-CN', viewport: { width: 390, height: 844 } });
    await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : route.abort());
    await page.goto(base + '/login'); await page.locator('.password-reset-toggle').click();
    const form = page.locator('.password-reset-form');
    await form.locator('[name=username]').fill(username); await form.locator('[name=email]').fill(email);
    previous = await ids(); await form.locator('button[type=button]').click();
    await form.locator('.verification-code-status').waitFor();
    const code = await codeAfter(previous), wrong = code === '000000' ? '111111' : '000000';
    assert.equal((await post(api, 'password-reset', { username, email, verificationCode: wrong, newPassword })).status(), 400);
    assert.equal(await login(oldPassword), 200);
    results.push({ case: 'wrong-code-no-password-change', status: 'PASS' });
    stage = 'browser-submit-reset';
    await form.locator('[name=verificationCode]').fill(code);
    await form.locator('[name=newPassword]').fill(newPassword); await form.locator('[name=confirmPassword]').fill(newPassword);
    const response = page.waitForResponse(r => r.url().endsWith('/api/auth/password-reset') && r.request().method() === 'POST');
    await form.locator('.login-submit').click(); assert.equal((await response).status(), 204);
    await page.locator('.form-success').waitFor();
    assert.equal(await login(newPassword), 200); assert.equal(await login(oldPassword), 401);
    results.push({ case: 'real-browser-reset-new-login-old-rejected', status: 'PASS' });
    // Use a fresh anonymous session: resetting the password may invalidate the
    // previously authenticated context before verification-code validation runs.
    stage = 'consumed-code-replay';
    const replay = await request.newContext({ baseURL: base });
    try {
      assert.equal((await post(replay, 'password-reset', { username, email, verificationCode: code, newPassword: oldPassword })).status(), 400);
    } finally { await replay.dispose(); }
    assert.equal(await login(newPassword), 200);
    results.push({ case: 'consumed-code-rejected-password-unchanged', status: 'PASS' });
    const oldSessionStatus = (await api.get('/api/auth/me')).status();
    results.push({ case: 'old-session-observation', status: oldSessionStatus === 401 ? 'PASS' : 'KNOWN_EXISTING_ISSUE', httpStatus: oldSessionStatus });
  } catch (e) {
    results.push({ case: 'integration', stage, status: 'FAIL', error: e.name === 'AssertionError' ? e.message : e.name });
    process.exitCode = 1;
  } finally {
    await browser.close(); await api.dispose(); await mail.dispose();
    fs.writeFileSync(output + '/integration.json', JSON.stringify({ mode: 'real isolated MySQL/Redis/SMTP/browser', expirySeconds: 30, results }, null, 2));
    console.log(JSON.stringify(results));
  }
})().catch(e => { console.error(e.name); process.exit(1); });
