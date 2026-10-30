import 'reflect-metadata';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require = createRequire(import.meta.url);
const { Module, ValidationPipe } = require('@nestjs/common');
const { NestFactory } = require('@nestjs/core');
const { MiniprogramAuthController } = require('../dist/auth/miniprogram-auth.controller.js');
const { AuthService } = require('../dist/auth/auth.service.js');
const { EmailVerificationService } = require('../dist/auth/email-verification.service.js');
const { HttpExceptionFilter } = require('../dist/common/filters/http-exception.filter.js');
const { ResponseInterceptor } = require('../dist/common/interceptors/response.interceptor.js');
test('mini email routes normalize email, restrict code purpose and enforce six-digit code plus consent', async () => {
  const sent = [],
    logins = [];
  class TestModule {}
  Module({
    controllers: [MiniprogramAuthController],
    providers: [
      {
        provide: EmailVerificationService,
        useValue: {
          sendMiniprogramLoginCode: async (...args) => {
            sent.push(args);
            return { retryAfterSeconds: 60, expiresInSeconds: 600 };
          },
        },
      },
      {
        provide: AuthService,
        useValue: {
          loginByEmail: async (...args) => {
            logins.push(args);
            return { user: { id: 'existing-owner', email: args[0].email } };
          },
        },
      },
    ],
  })(TestModule);
  const app = await NestFactory.create(TestModule, { logger: false, abortOnError: false });
  app.useGlobalPipes(
    new ValidationPipe({ transform: true, whitelist: true, forbidNonWhitelisted: true }),
  );
  app.useGlobalFilters(new HttpExceptionFilter());
  app.useGlobalInterceptors(new ResponseInterceptor());
  try {
    await app.listen(0, '127.0.0.1');
    const base = await app.getUrl();
    const post = (path, data) =>
      globalThis.fetch(base + '/auth/miniprogram/' + path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      });
    assert.equal((await post('email-code', { email: ' USER@Example.com ' })).status, 201);
    assert.equal(sent[0][0], 'user@example.com');
    for (const data of [
      { email: 'invalid' },
      { email: 'user@example.com', purpose: 'RESET_PASSWORD' },
      { email: 'user@example.com', skipCaptcha: true },
      { email: 'user@example.com', captchaVerifyParam: 'x' },
    ])
      assert.equal((await post('email-code', data)).status, 400);
    const valid = { email: ' USER@Example.com ', emailCode: '123456', acceptedTerms: true };
    for (const data of [
      { ...valid, acceptedTerms: false },
      { ...valid, acceptedTerms: 'true' },
      { ...valid, emailCode: '123' },
      { ...valid, bindingCode: 'x' },
      { ...valid, openId: 'x' },
    ])
      assert.equal((await post('login', data)).status, 400);
    const response = await post('login', valid);
    assert.equal(response.status, 201);
    assert.equal((await response.json()).data.user.id, 'existing-owner');
    assert.equal(logins.length, 1);
    assert.equal(logins[0][0].email, 'user@example.com');
  } finally {
    await app.close();
  }
});
