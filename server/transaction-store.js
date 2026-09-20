const { AsyncLocalStorage } = require('node:async_hooks');

// Readers see committed state only; each writer works on a private snapshot.
function createStore(initial, persist) {
  let current = initial;
  let queue = Promise.resolve();
  const context = new AsyncLocalStorage();
  const value = () => context.getStore() || current;
  const store = new Proxy({}, {
    get: (_, key) => value()[key], set: (_, key, item) => { value()[key] = item; return true; },
    ownKeys: () => Reflect.ownKeys(value()),
    getOwnPropertyDescriptor: (_, key) => ({ configurable: true, enumerable: true, writable: true, value: value()[key] })
  });
  function run(work) {
    const execute = async () => {
      const before = JSON.stringify(current);
      const draft = JSON.parse(before);
      return context.run(draft, async () => {
        const result = await work(store);
        if (JSON.stringify(draft) !== before) await persist(draft);
        current = draft;
        return result;
      });
    };
    const result = queue.then(execute, execute);
    queue = result.catch(() => {});
    return result;
  }
  function middleware(req, res, next) {
    if (!req.path.startsWith('/api/') || ['GET', 'HEAD', 'OPTIONS'].includes(req.method)
      || req.path.startsWith('/api/ai/')) return next();
    const json = res.json.bind(res);
    run(() => new Promise((resolve, reject) => {
      const closed = () => reject(Object.assign(new Error('Request closed'), { closed: true }));
      res.once('close', closed);
      res.json = body => {
        res.removeListener('close', closed);
        const result = { status: res.statusCode, body };
        if (res.statusCode >= 400) reject(Object.assign(new Error('Request rejected'), { result }));
        else resolve(result);
        return res;
      };
      try { next(); } catch (error) { reject(error); }
    })).then(result => { res.json = json; if (!res.destroyed) res.status(result.status).json(result.body); })
      .catch(error => {
        res.json = json;
        if (res.destroyed || error.closed) return;
        if (error.result) return res.status(error.result.status).json(error.result.body);
        console.error('Transaction failed:', error.code || error.name);
        res.status(503).json({ code: 503, message: '保存失败，数据未更改，请稍后重试' });
      });
  }
  return { store, run, middleware, replace: data => { current = data; },
    write: () => context.getStore() ? Promise.resolve() : persist(current), flush: () => queue };
}
module.exports = { createStore };
