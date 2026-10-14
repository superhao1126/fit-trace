/* global fetch */
import 'reflect-metadata';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { after, before, test } from 'node:test';
import { BadRequestException, Controller, Get, HttpException, Module } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';

const require = createRequire(import.meta.url);
const { ResponseInterceptor } = require('../dist/common/interceptors/response.interceptor.js');
const { HttpExceptionFilter } = require('../dist/common/filters/http-exception.filter.js');
const { JwtService } = require('@nestjs/jwt');
const { PrismaService } = require('../dist/prisma/prisma.service.js');
const modules = [
  ['auth', 'AuthController'],
  ['users', 'UsersController'],
  ['body-records', 'BodyRecordsController'],
  ['meals', 'MealsController'],
  ['training', 'TrainingController'],
  ['workouts', 'WorkoutsController'],
  ['progress-photos', 'ProgressPhotosController'],
  ['uploads', 'UploadsController'],
  ['ai', 'AiController'],
  ['insights', 'InsightsController'],
  ['dashboard', 'DashboardController'],
  ['health', 'HealthController'],
];
const controllers = modules.map(([folder, name]) => {
  const filename = folder === 'dashboard' ? 'dashboard' : folder;
  return require(`../dist/${folder}/${filename}.controller.js`)[name];
});
const meta = { page: 1, pageSize: 20, total: 1 };
const record = { id: 'fixture' };
const service = new Proxy(
  {},
  {
    get: (_, name) => {
      // Nest checks lifecycle hooks and promise-like values on providers.
      if (typeof name !== 'string' || name === 'then' || name.startsWith('on')) return undefined;
      if (name === 'getConfig') return () => record;
      return async () => {
        if (name === 'list') return { data: [record], meta };
        if (name === 'latest') return null;
        return record;
      };
    },
  },
);
const serviceTokens = new Set(
  controllers.flatMap((controller) => Reflect.getMetadata('design:paramtypes', controller) ?? []),
);

class ErrorController {
  validation() {
    throw new BadRequestException({ message: ['字段格式不正确', '另一个校验错误'] });
  }
  business() {
    throw new HttpException({ code: 'FILE_REQUIRED', message: '请选择要上传的图片' }, 400);
  }
  plain() {
    throw new BadRequestException('参数错误');
  }
  upstream() {
    throw new HttpException({ code: 'AI_UNAVAILABLE', message: '识别服务暂时不可用' }, 503);
  }
  internal() {
    throw new Error('private database credentials');
  }
  httpInternal() {
    throw new HttpException('private upstream response', 502);
  }
}
Controller('contract')(ErrorController);
for (const method of Object.getOwnPropertyNames(ErrorController.prototype).filter(
  (name) => name !== 'constructor',
)) {
  Get(method)(
    ErrorController.prototype,
    method,
    Object.getOwnPropertyDescriptor(ErrorController.prototype, method),
  );
}
class TestModule {}
Module({
  controllers: [...controllers, ErrorController],
  providers: [
    {
      provide: PrismaService,
      useValue: { user: { findUnique: async () => ({ tokenVersion: 0 }) } },
    },
    ...Array.from(serviceTokens, (provide) => ({ provide, useValue: service })),
    {
      provide: JwtService,
      useValue: {
        verifyAsync: async (token) => {
          if (token !== 'valid') throw new Error('invalid token');
          return { sub: 'owner' };
        },
      },
    },
  ],
})(TestModule);
let app;
let baseUrl;
before(async () => {
  app = await NestFactory.create(TestModule, { logger: false, abortOnError: false });
  app.setGlobalPrefix('api/v1');
  app.useGlobalFilters(new HttpExceptionFilter());
  app.useGlobalInterceptors(new ResponseInterceptor());
  await app.listen(0, '127.0.0.1');
  baseUrl = `${await app.getUrl()}/api/v1`;
});
after(async () => app?.close());

const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];
for (const controller of controllers) {
  const prefix = Reflect.getMetadata('path', controller);
  for (const name of Object.getOwnPropertyNames(controller.prototype)) {
    const handler = controller.prototype[name];
    const method = Reflect.getMetadata('method', handler);
    if (method === undefined) continue;
    const path = Reflect.getMetadata('path', handler).replace(':id', 'fixture').replace(/^\/+/, '');
    const verb = methods[method];
    test(`${verb} ${prefix}/${path}: actual controller returns the uniform success envelope`, async () => {
      const response = await fetch(`${baseUrl}/${prefix}/${path}`, {
        method: verb,
        headers: { Authorization: 'Bearer valid', 'Content-Type': 'application/json' },
        ...(verb === 'POST' || verb === 'PATCH' ? { body: '{}' } : {}),
      });
      assert.equal(response.status, verb === 'POST' ? 201 : 200);
      const body = await response.json();
      assert.deepEqual(Object.keys(body).sort(), ['code', 'data', 'message', 'meta']);
      assert.equal(body.code, 'OK');
      assert.equal(body.message, '请求成功');
      assert.deepEqual(body.meta, name === 'list' ? meta : null);
      assert.deepEqual(
        body.data,
        name === 'list'
          ? [record]
          : [
                'remove',
                'removeFavorite',
                'latest',
                'resetPassword',
                'logout',
                'changePassword',
              ].includes(name)
            ? null
            : prefix === 'health'
              ? { status: 'ok' }
              : record,
      );
    });
  }
}
for (const [path, status, code, message] of [
  ['contract/validation', 400, 'VALIDATION_ERROR', '字段格式不正确'],
  ['contract/business', 400, 'FILE_REQUIRED', '请选择要上传的图片'],
  ['contract/plain', 400, 'HTTP_400', '参数错误'],
  ['contract/upstream', 503, 'AI_UNAVAILABLE', '识别服务暂时不可用'],
  ['contract/internal', 500, 'INTERNAL_ERROR', '服务器内部错误'],
  ['contract/httpInternal', 502, 'HTTP_502', '上游服务响应异常'],
  ['does-not-exist', 404, 'HTTP_404'],
  ['auth/me', 401, 'UNAUTHORIZED', '请先登录或重新登录'],
]) {
  test(`${path}: errors keep HTTP status and use the same four fields`, async () => {
    const response = await fetch(`${baseUrl}/${path}`);
    assert.equal(response.status, status);
    const body = await response.json();
    assert.deepEqual(Object.keys(body).sort(), ['code', 'data', 'message', 'meta']);
    assert.equal(body.code, code);
    if (message) assert.equal(body.message, message);
    assert.equal(typeof body.message, 'string');
    assert.equal(body.data, null);
    assert.equal(body.meta, null);
    assert.ok(!JSON.stringify(body).includes('private'));
  });
}
