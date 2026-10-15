/* global setTimeout, clearTimeout */
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { EmailCaptchaClient } from '../src/utils/email-captcha.ts';
const config = { enabled: true, region: 'cn', prefix: 'prefix', sceneId: 'web', appSceneId: 'app' };
function fixture(t) {
  let options;
  const nodes = [];
  const oldWindow = globalThis.window,
    oldDocument = globalThis.document;
  globalThis.window = {
    setTimeout,
    clearTimeout,
    innerWidth: 390,
    initAliyunCaptcha: (value) => {
      options = value;
    },
  };
  globalThis.document = {
    createElement: () => ({
      remove() {
        this.removed = true;
      },
      click() {
        this.clicks = (this.clicks ?? 0) + 1;
      },
    }),
    body: { append: (...items) => nodes.push(...items) },
  };
  const client = new EmailCaptchaClient(async () => config);
  t.after(() => {
    client.dispose();
    globalThis.window = oldWindow;
    globalThis.document = oldDocument;
  });
  return { client, nodes, options: () => options };
}
async function initialized(f) {
  await f.client.prepare();
  const result = f.client.verify();
  // prepare includes asynchronous config and SDK loading.
  for (let i = 0; i < 6; i++) await Promise.resolve();
  assert.ok(f.options());
  return { result };
}
test('page preparation initializes without opening; a warm click starts immediately and prepares the next attempt', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'Date'], now: 1000 });
  const f = fixture(t);
  await f.client.prepare();
  const first = f.options();
  first.getInstance({});
  t.mock.timers.tick(2100);
  assert.equal(f.nodes[1].clicks, undefined);
  const { result } = await initialized(f);
  assert.equal(f.options(), first);
  assert.equal(f.nodes[1].clicks, 1);
  first.getInstance({});
  assert.equal(f.nodes[1].clicks, 1);
  first.success('first');
  assert.equal((await result).captchaVerifyParam, 'first');
  await f.client.prepare();
  assert.notEqual(f.options(), first);
  f.options().getInstance({});
  // 用户填写邮箱验证码期间不会自动打开，也不会开始验证超时计时。
  t.mock.timers.tick(120001);
  assert.equal(f.nodes[3].clicks, undefined);
  const next = await initialized(f);
  assert.equal(f.nodes[3].clicks, 1);
  f.options().success('next');
  assert.equal((await next.result).captchaVerifyParam, 'next');
  f.client.dispose();
  assert.ok(f.nodes.every((node) => node.removed));
});
test('an early click waits only for the remaining warmup; disposing cancels a prepared challenge', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'Date'], now: 1000 });
  const f = fixture(t);
  await f.client.prepare();
  f.options().getInstance({});
  t.mock.timers.tick(1000);
  const { result } = await initialized(f);
  t.mock.timers.tick(1099);
  assert.equal(f.nodes[1].clicks, undefined);
  t.mock.timers.tick(1);
  assert.equal(f.nodes[1].clicks, 1);
  f.options().onClose('userDismiss');
  assert.equal(await result, null);
  await f.client.prepare();
  const unused = f.options();
  f.client.dispose();
  unused.getInstance({ hide: () => {} });
  t.mock.timers.tick(2100);
  assert.equal(f.nodes[3].clicks, undefined);
  assert.ok(f.nodes.every((node) => node.removed));
});
test('closing a popup does not hide it twice or cancel the next prepared attempt', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'Date'], now: 1000 });
  const f = fixture(t);
  await f.client.prepare();
  let hides = 0;
  f.options().getInstance({
    hide: () => {
      hides++;
      // 模拟 SDK 共用配置：异步关闭通知会读取当时最新的回调。
      return new Promise((resolve) => {
        setTimeout(() => {
          f.options().onClose('userDismiss');
          resolve();
        }, 400);
      });
    },
  });
  t.mock.timers.tick(2100);
  const first = await initialized(f);
  f.options().onClose('userDismiss');
  assert.equal(await first.result, null);
  assert.equal(hides, 0);
  await f.client.prepare();
  f.options().getInstance({});
  // 预初始化阶段的关闭通知不能消费下一次验证。
  f.options().onClose('userDismiss');
  t.mock.timers.tick(2100);
  const second = await initialized(f);
  assert.equal(f.nodes[3].clicks, 1);
  f.options().success('second-click');
  assert.equal((await second.result).captchaVerifyParam, 'second-click');
});
test('a new instance is initialized only after the previous asynchronous hide completes', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'Date'], now: 1000 });
  const f = fixture(t);
  await f.client.prepare();
  const old = f.options();
  old.getInstance({
    hide: () =>
      new Promise((resolve) => {
        setTimeout(() => {
          f.options().onClose('userDismiss');
          resolve();
        }, 400);
      }),
  });
  t.mock.timers.tick(2100);
  const first = await initialized(f);
  old.success('first');
  t.mock.timers.tick(399);
  assert.equal(f.options(), old);
  assert.equal(f.nodes.length, 2);
  t.mock.timers.tick(1);
  assert.equal((await first.result).captchaVerifyParam, 'first');
  await f.client.prepare();
  assert.notEqual(f.options(), old);
  f.options().getInstance({});
  t.mock.timers.tick(2100);
  const next = await initialized(f);
  assert.equal(f.nodes[3].clicks, 1);
  f.options().success('next');
  assert.equal((await next.result).captchaVerifyParam, 'next');
});
test('old prepared challenges are refreshed and background initialization failure is surfaced on click', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'Date'], now: 1000 });
  const f = fixture(t);
  await f.client.prepare();
  const first = f.options();
  first.getInstance({});
  t.mock.timers.tick(600000);
  const { result } = await initialized(f);
  assert.notEqual(f.options(), first);
  assert.ok(f.nodes.slice(0, 2).every((node) => node.removed));
  f.options().getInstance({});
  t.mock.timers.tick(2100);
  assert.equal(f.nodes[3].clicks, 1);
  f.options().success('fresh');
  await result;
  await f.client.prepare();
  f.options().onError();
  await assert.rejects(f.client.verify(), /加载失败/);
  f.client.dispose();
  assert.ok(f.nodes.every((node) => node.removed));
});
test('disabled config does not load SDK and failed configuration can retry', async () => {
  let attempts = 0;
  const client = new EmailCaptchaClient(async () => {
    if (++attempts === 1) throw new Error('offline');
    return { enabled: false };
  });
  await assert.rejects(client.verify(), /offline/);
  assert.equal(await client.verify(), undefined);
  client.dispose();
  assert.equal(await client.verify(), null);
});
test('verification works without randomUUID or crypto; repeated attempts get distinct DOM IDs', async (t) => {
  const f = fixture(t);
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  t.after(() => {
    if (descriptor) Object.defineProperty(globalThis, 'crypto', descriptor);
    else delete globalThis.crypto;
  });
  t.mock.method(Date, 'now', () => 1234567890);
  for (const value of [{}, undefined]) {
    Object.defineProperty(globalThis, 'crypto', { configurable: true, value });
    const { result } = await initialized(f);
    f.options().success('valid');
    assert.equal((await result).captchaVerifyParam, 'valid');
  }
  assert.equal(new Set(f.nodes.map((node) => node.id)).size, f.nodes.length);
  f.client.dispose();
  assert.ok(f.nodes.every((node) => node.removed));
});
test('opaque proof is unchanged, browser selects web scene even when app scene is configured, cleanup exceptions do not lose success', async (t) => {
  const f = fixture(t);
  const { result } = await initialized(f);
  assert.equal(f.options().SceneId, 'web');
  f.options().getInstance({
    hide: () => {
      throw new Error('cleanup');
    },
  });
  f.options().success(' opaque-token ');
  assert.deepEqual(await result, { captchaVerifyParam: ' opaque-token ', captchaPlatform: 'web' });
  f.client.dispose();
  assert.ok(f.nodes.every((node) => node.removed));
});
test('cancel and unmount ignore late success; automatic close does not cancel a successful proof', async (t) => {
  const f = fixture(t);
  let run = await initialized(f);
  const old = f.options();
  old.onClose('userDismiss');
  old.success('late-proof');
  assert.equal(await run.result, null);
  run = await initialized(f);
  f.options().onClose('verifyComplete');
  f.options().success('valid');
  assert.equal((await run.result).captchaVerifyParam, 'valid');
  run = await initialized(f);
  f.client.dispose();
  f.options().success('after-unmount');
  assert.equal(await run.result, null);
  assert.ok(f.nodes.every((node) => node.removed));
});
test('SDK error and empty proof reject and clean up; a later attempt works', async (t) => {
  const f = fixture(t);
  let run = await initialized(f);
  f.options().onError();
  await assert.rejects(run.result, /加载失败/);
  run = await initialized(f);
  f.options().success(' ');
  await assert.rejects(run.result, /未完成/);
  run = await initialized(f);
  f.options().success('retry');
  assert.equal((await run.result).captchaVerifyParam, 'retry');
  f.client.dispose();
  assert.ok(f.nodes.every((node) => node.removed));
});
