import {
  BadRequestException,
  ConflictException,
  Injectable,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Prisma } from '@prisma/client';
import { createHash, randomBytes } from 'node:crypto';
import { PrismaService } from '../prisma/prisma.service';
import { UsersService } from '../users/users.service';
import { AuthSessionService } from './auth-session.service';
import { AuthSecurityService } from './auth-security.service';
import type { JwtPayload } from './auth.types';

@Injectable()
export class WechatService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: ConfigService,
    private readonly users: UsersService,
    private readonly sessions: AuthSessionService,
    private readonly security: AuthSecurityService,
  ) {}

  private digest(code: string) {
    return createHash('sha256').update(code).digest('hex');
  }

  async bindingCode(payload: JwtPayload) {
    await this.security.limit('wechat-bind-user', payload.sub, 10, 900);
    const session = payload.sid
      ? await this.prisma.authSession.findUnique({
          where: { id: payload.sid },
          include: { user: true },
        })
      : null;
    if (
      !session ||
      session.userId !== payload.sub ||
      session.revokedAt ||
      !session.emailVerifiedAt ||
      !session.user.email ||
      session.tokenVersion !== session.user.tokenVersion
    ) {
      throw new UnauthorizedException({
        code: 'EMAIL_VERIFICATION_REQUIRED',
        message: '请先在原账号使用邮箱验证码登录，再生成关联码',
      });
    }
    const code = randomBytes(16).toString('hex');
    const expiresAt = new Date(Date.now() + 5 * 60_000);
    await this.prisma.$transaction(async (tx) => {
      await tx.wechatBindingCode.updateMany({
        where: { userId: payload.sub, consumedAt: null },
        data: { consumedAt: new Date() },
      });
      await tx.wechatBindingCode.create({
        data: {
          codeHash: this.digest(code),
          userId: payload.sub,
          sessionId: session.id,
          tokenVersion: session.tokenVersion,
          expiresAt,
        },
      });
    });
    return { code, expiresAt: expiresAt.toISOString() };
  }

  /** A first-login empty shell may be replaced after proving both identities.
   * Never copy business records or delete the source account.
   */
  private async reassignEmptyIdentity(
    tx: Prisma.TransactionClient,
    identity: { id: string; userId: string },
    targetUserId: string,
  ) {
    // Serialize competing account changes and FK-backed record creation while checking.
    await tx.$queryRaw`SELECT "id" FROM "users" WHERE "id" = ${identity.userId} FOR UPDATE`;
    const account = await tx.user.findUnique({
      where: { id: identity.userId },
      include: { _count: true, trainingPlan: { select: { id: true } } },
    });
    const populated =
      !account ||
      account.email !== null ||
      account.passwordHash !== null ||
      account.hasPassword !== false ||
      account.nickname !== null ||
      account.avatarUrl !== null ||
      account.gender !== 'UNSPECIFIED' ||
      account.goalType !== null ||
      account.goalStartWeight !== null ||
      account.targetWeight !== null ||
      account.targetDate !== null ||
      account.goalStartedAt !== null ||
      account.trainingPlan ||
      account._count.wechatIdentities !== 1 ||
      account._count.deviceCredentials > 0 ||
      account._count.bodyRecords > 0 ||
      account._count.mealRecords > 0 ||
      account._count.mealFavorites > 0 ||
      account._count.workoutRecords > 0 ||
      account._count.progressPhotos > 0 ||
      account._count.trainingTemplates > 0;
    if (populated)
      throw new ConflictException({
        code: 'WECHAT_ALREADY_BOUND',
        message: '此微信关联的账号已有资料或记录，不能自动改绑或合并，请先使用原微信账号查看数据',
      });
    const changed = await tx.user.updateMany({
      where: { id: account.id, tokenVersion: account.tokenVersion, updatedAt: account.updatedAt },
      data: { tokenVersion: { increment: 1 } },
    });
    if (!changed.count)
      throw new ConflictException({
        code: 'WECHAT_BINDING_CONFLICT',
        message: '账号状态已变化，请重试',
      });
    await tx.authSession.updateMany({
      where: { userId: account.id, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    await tx.wechatIdentity.update({ where: { id: identity.id }, data: { userId: targetUserId } });
  }

  async login(code: string, ip: string, bindingCode?: string) {
    await this.security.limit('wechat-login-ip', ip, 40, 900);
    const appId = this.config.get<string>('WECHAT_APP_ID');
    const secret = this.config.get<string>('WECHAT_APP_SECRET');
    if (!appId || !secret)
      throw new ServiceUnavailableException({
        code: 'WECHAT_NOT_CONFIGURED',
        message: '微信登录尚未配置',
      });
    let identity: { openid?: string; unionid?: string; errcode?: number };
    try {
      const query = new URLSearchParams({
        appid: appId,
        secret,
        js_code: code,
        grant_type: 'authorization_code',
      });
      const response = await fetch(`https://api.weixin.qq.com/sns/jscode2session?${query}`, {
        signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) throw new Error('upstream');
      identity = (await response.json()) as typeof identity;
    } catch {
      throw new ServiceUnavailableException({
        code: 'WECHAT_UNAVAILABLE',
        message: '微信登录服务暂不可用，请重试',
      });
    }
    if (
      !identity ||
      typeof identity !== 'object' ||
      identity.errcode ||
      typeof identity.openid !== 'string' ||
      !identity.openid
    )
      throw new UnauthorizedException({
        code: 'WECHAT_CODE_INVALID',
        message: '微信登录凭证失效，请重试',
      });
    const openId = identity.openid;
    try {
      return await this.prisma.$transaction(
        async (tx) => {
          const existing = await tx.wechatIdentity.findUnique({
            where: { appId_openId: { appId, openId } },
            include: { user: true },
          });
          let user = existing?.user;
          if (bindingCode) {
            const bind = await tx.wechatBindingCode.findUnique({
              where: { codeHash: this.digest(bindingCode) },
            });
            if (!bind || bind.consumedAt || bind.expiresAt.getTime() <= Date.now())
              throw new BadRequestException({
                code: 'BINDING_CODE_INVALID',
                message: '关联码无效或已过期',
              });
            const source = await tx.authSession.findUnique({
              where: { id: bind.sessionId },
              include: { user: true },
            });
            if (
              !source ||
              source.revokedAt ||
              !source.emailVerifiedAt ||
              source.userId !== bind.userId ||
              source.user.tokenVersion !== bind.tokenVersion ||
              source.tokenVersion !== bind.tokenVersion
            )
              throw new UnauthorizedException({
                code: 'BINDING_SESSION_INVALID',
                message: '原账号会话已失效，请重新生成关联码',
              });
            if (existing && existing.userId !== bind.userId) {
              await this.reassignEmptyIdentity(tx, existing, bind.userId);
            }
            const consumed = await tx.wechatBindingCode.updateMany({
              where: { id: bind.id, consumedAt: null, expiresAt: { gt: new Date() } },
              data: { consumedAt: new Date() },
            });
            if (!consumed.count)
              throw new BadRequestException({
                code: 'BINDING_CODE_INVALID',
                message: '关联码已使用',
              });
            user = source.user;
          }
          if (!user) user = await tx.user.create({ data: { hasPassword: false } });
          if (!existing)
            await tx.wechatIdentity.create({
              data: {
                appId,
                openId,
                unionId: typeof identity.unionid === 'string' ? identity.unionid : null,
                userId: user.id,
              },
            });
          const publicUser = await this.users.findPublicById(user.id, tx);
          if (!publicUser) throw new UnauthorizedException();
          // 微信验证只证明微信身份，不升级邮箱验证状态。
          return this.sessions.create(publicUser, user.tokenVersion, tx, false);
        },
        { isolationLevel: Prisma.TransactionIsolationLevel.Serializable },
      );
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        ['P2002', 'P2034'].includes(error.code)
      )
        throw new ConflictException({
          code: 'WECHAT_BINDING_CONFLICT',
          message: '账号关联发生冲突，请重新登录后重试',
        });
      throw error;
    }
  }
}
