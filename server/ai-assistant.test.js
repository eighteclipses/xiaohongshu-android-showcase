const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const { generateNote, parseResult, mountAiRoutes } = require('./ai-assistant');
const valid = { title: '学习计划', content: '先列出任务，再安排休息。', topics: ['学习', '#校园', '学习'] };

test('DeepSeek request uses JSON output, disables thinking, and does not expose key', async () => {
  const result = await generateNote({ idea: '大学生学习计划', style: 'guide' }, { apiKey: 'test-secret', fetchImpl: async (url, options) => {
    assert.equal(url, 'https://api.deepseek.com/chat/completions');
    assert.equal(options.headers.Authorization, 'Bearer test-secret');
    const request = JSON.parse(options.body);
    assert.equal(request.response_format.type, 'json_object');
    assert.equal(request.thinking.type, 'disabled');
    assert.equal(request.messages[1].role, 'user');
    return { ok: true, json: async () => ({ choices: [{ finish_reason: 'stop', message: { content: JSON.stringify(valid) } }] }) };
  } });
  assert.deepEqual(result.topics, ['学习', '校园']);
  assert.equal(result.provider, 'DeepSeek');
  assert.equal(result.ai_generated, true);
  assert.ok(!JSON.stringify(result).includes('test-secret'));
});

test('AI rejects empty/oversized input, invalid styles and missing credentials before network', async () => {
  const options = { apiKey: 'key', fetchImpl: () => { throw Error('must not call network'); } };
  for (const input of [{ idea: '' }, { idea: 'x'.repeat(2001) }, { idea: 'hello', style: 'invalid' }]) {
    await assert.rejects(generateNote(input, options), error => error.status === 400);
  }
  await assert.rejects(generateNote({ idea: 'test' }, { apiKey: '' }), error => error.status === 503);
});

test('AI handles malformed output, upstream failure, token limit and timeout safely', async () => {
  assert.throws(() => parseResult('not JSON'), /格式/);
  assert.throws(() => parseResult('{"title":"a","content":"b","topics":null}'), /不完整/);
  await assert.rejects(generateNote({ idea: 'test' }, { apiKey: 'secret', fetchImpl: async () => ({ ok: false, status: 402 }) }), /额度不足/);
  await assert.rejects(generateNote({ idea: 'test' }, { apiKey: 'secret', fetchImpl: async () => ({ ok: true, json: async () => ({ choices: [{ finish_reason: 'length' }] }) }) }), /未完成/);
  await assert.rejects(generateNote({ idea: 'test' }, { apiKey: 'secret', timeoutMs: 10,
    fetchImpl: (url, options) => new Promise((resolve, reject) => options.signal.addEventListener('abort', () => reject(Error('aborted')))) }), error => error.status === 504);
});

test('AI route requires login and blocks concurrent requests without publishing posts', async () => {
  const app = express(); app.use(express.json());
  let release;
  let started;
  const ready = new Promise(resolve => { started = resolve; });
  mountAiRoutes(app, (req, res, next) => {
    if (!req.headers.authorization) return res.status(401).end();
    req.user = { id: 'test-user' }; next();
  }, () => { started(); return new Promise(resolve => { release = resolve; }); });
  const server = await new Promise(resolve => { const instance = app.listen(0, '127.0.0.1', () => resolve(instance)); });
  const url = `http://127.0.0.1:${server.address().port}/api/ai/note-assistant`;
  try {
    assert.equal((await fetch(url, { method: 'POST' })).status, 401);
    const request = { method: 'POST', headers: { Authorization: 'test', 'Content-Type': 'application/json' }, body: JSON.stringify({ idea: 'test' }) };
    const first = fetch(url, request);
    await ready;
    const duplicate = await fetch(url, request);
    assert.equal(duplicate.status, 429);
    release(valid);
    const result = await first;
    assert.equal(result.status, 200);
    assert.equal((await result.json()).data.title, valid.title);
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});
