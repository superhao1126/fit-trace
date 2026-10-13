import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createToastController } from '../src/components/ui/toast.ts';

function fixture() {
  const jobs = [];
  const cancelled = [];
  const toast = createToastController({
    schedule(callback, delay) {
      jobs.push({ callback, delay });
      return jobs.length;
    },
    cancel(timer) {
      cancelled.push(timer);
    },
  });
  return { toast, jobs, cancelled };
}

test('a queued timeout from an earlier notice cannot dismiss a newer failure', () => {
  const { toast, jobs, cancelled } = fixture();
  toast.success('已保存');
  toast.error('网络连接异常，请重试');
  assert.deepEqual(cancelled, [1]);
  jobs[0].callback();
  assert.equal(toast.notice.value.message, '网络连接异常，请重试');
  assert.equal(toast.notice.value.kind, 'error');
  jobs[1].callback();
  assert.equal(toast.notice.value, null);
});

test('long warnings remain readable longer while the duration stays bounded', () => {
  const { toast, jobs } = fixture();
  toast.warning('请输入训练名称');
  toast.warning('本次填写的内容已经保留，可以检查网络连接后再次保存。'.repeat(8));
  assert.ok(jobs[1].delay > jobs[0].delay);
  assert.equal(jobs[1].delay, 8000);
});

test('empty messages do not replace a current notice or cancel its dismissal', () => {
  const { toast, jobs, cancelled } = fixture();
  toast.warning('请输入体重');
  toast.success('  ');
  assert.equal(toast.notice.value.message, '请输入体重');
  assert.equal(jobs.length, 1);
  assert.equal(cancelled.length, 0);
});

test('explicit duration and manual clear are respected', () => {
  const { toast, jobs, cancelled } = fixture();
  toast.error({ message: '失败，请重试', duration: 3000 });
  assert.equal(jobs[0].delay, 3000);
  toast.clear();
  assert.equal(toast.notice.value, null);
  assert.deepEqual(cancelled, [1]);
  toast.info({ message: '持续提示', duration: 0 });
  assert.equal(jobs.length, 1);
  assert.equal(toast.notice.value.message, '持续提示');
  toast.clear();
  assert.equal(toast.notice.value, null);
});
