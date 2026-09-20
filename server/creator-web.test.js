const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');

function harness(fetch) {
  const elements = new Map();
  const element = id => {
    if (!elements.has(id)) elements.set(id, { value: '', textContent: '', innerHTML: '', disabled: false, files: [],
      classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
      addEventListener() {}, focus() {}, pause() {}, load() {}, removeAttribute() {} });
    return elements.get(id);
  };
  const context = vm.createContext({ fetch, crypto: webcrypto, console, FormData, Blob,
    localStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
    document: { getElementById: element, querySelectorAll() { return []; }, querySelector: element },
    window: { addEventListener() {} }, requestAnimationFrame() {} });
  vm.runInContext(fs.readFileSync(require.resolve('./public/app.js'), 'utf8'), context);
  return { run: code => vm.runInContext(code, context), element, context };
}
const ok = data => ({ ok: true, status: 200, json: async () => ({ code: 200, data }) });

test('web editing preserves privacy, location and topics while saving a draft', async () => {
  const calls = [];
  const h = harness(async (url, options) => { calls.push({ url, ...options }); return ok({}); });
  h.context.existing = { id: 'existing', content: 'original', images: [], is_public: false, topics: ['旅行'], location: '厦门中山路' };
  h.run('openModal(existing)');
  h.element('noteContentInput').value = 'edited';
  await h.run('saveNote(true)');
  assert.equal(calls.length, 1);
  const payload = JSON.parse(calls[0].body);
  assert.equal(calls[0].method, 'PUT');
  assert.equal(payload.is_public, false);
  assert.equal(payload.is_draft, true);
  assert.equal(payload.location, '厦门中山路');
  assert.deepEqual(payload.topics, ['旅行']);
});

test('double publish submits once and retries keep the same client note id', async () => {
  let finish;
  const calls = [];
  const h = harness((url, options) => { calls.push(options); return new Promise(resolve => { finish = resolve; }); });
  h.run('openModal()');
  h.element('noteContentInput').value = 'once';
  const first = h.run('saveNote(false)');
  await h.run('saveNote(false)');
  assert.equal(calls.length, 1);
  finish({ ok: false, status: 503, json: async () => ({ code: 503, message: 'try again' }) });
  await first;
  const retry = h.run('saveNote(false)');
  assert.equal(calls.length, 2);
  assert.equal(JSON.parse(calls[0].body).id, JSON.parse(calls[1].body).id);
  finish(ok({}));
  await retry;
  assert.equal(h.run('state.saving'), false);
});

test('token refresh retries the original request at most once', async () => {
  const calls = [];
  const h = harness(async url => {
    calls.push(url);
    return url.endsWith('/auth/refresh') ? ok({ access_token: 'renewed' })
      : { ok: false, status: 401, json: async () => ({ code: 401, message: 'expired' }) };
  });
  h.run("state.token = 'old'");
  await assert.rejects(h.run("api('/auth/me')"), error => error.status === 401);
  assert.deepEqual(calls, ['/api/auth/me', '/api/auth/refresh', '/api/auth/me']);
});

test('temporary connection errors do not clear login state', async () => {
  const h = harness(async () => { throw new Error('connection offline'); });
  h.run("state.token = 'keep-me'");
  await h.run('loadData()');
  assert.equal(h.run('state.token'), 'keep-me');
  assert.match(h.element('dataError').textContent, /connection offline/);
});
