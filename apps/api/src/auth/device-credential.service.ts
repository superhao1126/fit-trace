import { createHash, createPublicKey, randomBytes, randomUUID, verify } from 'node:crypto';
import { ForbiddenException, Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { UsersService } from '../users/users.service';
import { AuthSessionService } from './auth-session.service';
import { AuthSecurityService } from './auth-security.service';
import { EmailVerificationService } from './email-verification.service';
import type { JwtPayload } from './auth.types';
import type {
  RegistrationOptionsDto,
  CredentialLoginOptionsDto,
  CredentialVerifyDto,
} from './dto/device-credential.dto';

@Injectable()
export class DeviceCredentialService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly sessions: AuthSessionService,
    private readonly users: UsersService,
    private readonly security: AuthSecurityService,
    private readonly verification: EmailVerificationService,
    private readonly config: ConfigService,
  ) {}

  private denied() {
    return new UnauthorizedException({
      code: 'DEVICE_CREDENTIAL_INVALID',
      message: '快捷登录不可用，请使用邮箱登录后重新开启',
    });
  }
  private key(publicKey: string) {
    try {
      const key = createPublicKey({
        key: Buffer.from(publicKey, 'base64'),
        format: 'der',
        type: 'spki',
      });
      if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails?.namedCurve !== 'prime256v1')
        throw this.denied();
      return key;
    } catch {
      throw this.denied();
    }
  }
  // 所有凭据状态变更与签发都锁同一用户行；与密码变更的用户更新串行。
  private async account(tx: Prisma.TransactionClient, userId: string, version: number) {
    const rows = await tx.$queryRaw<
      Array<{ tokenVersion: number }>
    >`SELECT "tokenVersion" FROM "users" WHERE "id" = ${userId} FOR UPDATE`;
    if (!rows[0] || rows[0].tokenVersion !== version) throw this.denied();
  }
  private async session(tx: Prisma.TransactionClient, user: JwtPayload) {
    await this.account(tx, user.sub, user.ver ?? 0);
    const session = user.sid ? await tx.authSession.findUnique({ where: { id: user.sid } }) : null;
    if (
      !session ||
      session.revokedAt ||
      session.userId !== user.sub ||
      session.tokenVersion !== (user.ver ?? 0)
    )
      throw this.denied();
    return session;
  }
  private audience() {
    return this.config.get<string>('DEVICE_CREDENTIAL_AUDIENCE') ?? 'fittrace-api-v1';
  }
  private belongsHere(payload: string): boolean {
    try {
      const value = JSON.parse(payload) as { version?: unknown; audience?: unknown };
      return value.version === 1 && value.audience === this.audience();
    } catch {
      return false;
    }
  }
  private challenge(purpose: string, credentialId: string, scope: string, binding: string) {
    const id = randomUUID();
    const expiresAt = new Date(Date.now() + 120000);
    const payload = JSON.stringify({
      version: 1,
      audience: this.audience(),
      purpose,
      scope,
      challengeId: id,
      credentialId,
      binding,
      nonce: randomBytes(32).toString('base64url'),
      expiresAt: expiresAt.toISOString(),
    });
    return { id, expiresAt, payload };
  }
  private response(row: { id: string; payload: string; expiresAt: Date }) {
    return { challengeId: row.id, payload: row.payload, expiresAt: row.expiresAt.toISOString() };
  }
  private summary(row: {
    id: string;
    scope: string;
    deviceName: string;
    createdAt: Date;
    lastUsedAt: Date | null;
  }) {
    return {
      id: row.id,
      scope: row.scope,
      deviceName: row.deviceName,
      createdAt: row.createdAt.toISOString(),
      lastUsedAt: row.lastUsedAt?.toISOString() ?? null,
    };
  }
  async registrationOptions(user: JwtPayload, dto: RegistrationOptionsDto) {
    await this.security.limit('device-register-user', user.sub, 10, 900);
    this.key(dto.publicKey);
    const action = async (tx: Prisma.TransactionClient, verifiedEmail = false) => {
      const current = await this.session(tx, user);
      if (
        !verifiedEmail &&
        (!current.emailVerifiedAt || Date.now() - current.emailVerifiedAt.getTime() > 300000)
      ) {
        throw new ForbiddenException({
          code: 'EMAIL_VERIFICATION_REQUIRED',
          message: '请先验证当前账号邮箱，再开启指纹登录',
        });
      }
      if (verifiedEmail) {
        await tx.authSession.update({
          where: { id: current.id },
          data: { emailVerifiedAt: new Date() },
        });
      }
      const credentialId = randomUUID();
      const row = this.challenge(
        'register',
        credentialId,
        dto.scope,
        `${user.sub}:${createHash('sha256').update(dto.publicKey).digest('hex')}`,
      );
      await tx.deviceAuthChallenge.create({
        data: {
          ...row,
          userId: user.sub,
          purpose: 'register',
          credentialId,
          sessionId: user.sid,
          tokenVersion: user.ver ?? 0,
          publicKey: dto.publicKey,
          scope: dto.scope,
          deviceName: dto.deviceName,
        },
      });
      return { ...this.response(row), credentialId };
    };
    // 优先使用服务端近期证明；系统验证取消后重试不重复消费已用过的邮箱码。
    try {
      return await this.prisma.$transaction((tx) => action(tx));
    } catch (error) {
      if (
        !(error instanceof ForbiddenException) ||
        (error.getResponse() as { code?: string }).code !== 'EMAIL_VERIFICATION_REQUIRED' ||
        !dto.emailCode
      )
        throw error;
      // 验证码消费、近期证明与挑战创建同事务；不接收客户端认证时间戳。
      if (!user.email)
        throw new ForbiddenException({
          code: 'EMAIL_ACCOUNT_REQUIRED',
          message: '此功能需要邮箱账号',
        });
      return this.verification.withCode(user.email, 'LOGIN', dto.emailCode, (tx) =>
        action(tx, true),
      );
    }
  }
  async registrationVerify(user: JwtPayload, dto: CredentialVerifyDto) {
    await this.security.limit('device-register-verify', user.sub, 20, 900);
    return this.prisma.$transaction(async (tx) => {
      await this.session(tx, user);
      const row = await tx.deviceAuthChallenge.findUnique({ where: { id: dto.challengeId } });
      if (
        !row ||
        row.purpose !== 'register' ||
        !this.belongsHere(row.payload) ||
        row.userId !== user.sub ||
        row.sessionId !== user.sid ||
        row.tokenVersion !== (user.ver ?? 0) ||
        !row.publicKey ||
        row.expiresAt <= new Date()
      )
        throw this.denied();
      if (
        !verify(
          'sha256',
          Buffer.from(row.payload, 'utf8'),
          this.key(row.publicKey),
          Buffer.from(dto.signature, 'base64url'),
        )
      )
        throw this.denied();
      if (row.consumedAt) {
        const existing = await tx.deviceCredential.findUnique({ where: { id: row.credentialId } });
        if (!existing || existing.revokedAt || existing.publicKey !== row.publicKey)
          throw this.denied();
        return this.summary(existing);
      }
      const consumed = await tx.deviceAuthChallenge.updateMany({
        where: { id: row.id, consumedAt: null, expiresAt: { gt: new Date() } },
        data: { consumedAt: new Date() },
      });
      if (consumed.count !== 1) throw this.denied();
      const credential = await tx.deviceCredential.create({
        data: {
          id: row.credentialId,
          userId: user.sub,
          publicKey: row.publicKey,
          scope: row.scope,
          deviceName: row.deviceName ?? 'Android',
          tokenVersion: row.tokenVersion,
        },
      });
      return this.summary(credential);
    });
  }
  async loginOptions(dto: CredentialLoginOptionsDto, ip: string) {
    await this.security.limit('device-options-ip', ip, 40, 900);
    await this.security.limit('device-options-id', dto.credentialId, 20, 900);
    const credential = await this.prisma.deviceCredential.findUnique({
      where: { id: dto.credentialId },
      include: { user: { select: { tokenVersion: true } } },
    });
    const row = this.challenge('login', dto.credentialId, dto.scope, dto.credentialId);
    // 无效凭据也返回同形随机挑战，不泄露账户或绑定是否存在。
    if (
      credential &&
      !credential.revokedAt &&
      credential.scope === dto.scope &&
      credential.tokenVersion === credential.user.tokenVersion
    ) {
      await this.prisma.deviceAuthChallenge.create({
        data: {
          ...row,
          userId: credential.userId,
          purpose: 'login',
          credentialId: credential.id,
          tokenVersion: credential.tokenVersion,
          scope: dto.scope,
        },
      });
    }
    return this.response(row);
  }
  async loginVerify(dto: CredentialVerifyDto, ip: string) {
    await this.security.limit('device-verify-ip', ip, 40, 900);
    await this.security.limit('device-verify-challenge', dto.challengeId, 8, 120);
    return this.prisma.$transaction(async (tx) => {
      const row = await tx.deviceAuthChallenge.findUnique({ where: { id: dto.challengeId } });
      if (
        !row ||
        row.purpose !== 'login' ||
        !this.belongsHere(row.payload) ||
        row.consumedAt ||
        row.expiresAt <= new Date()
      )
        throw this.denied();
      await this.account(tx, row.userId, row.tokenVersion);
      const credential = await tx.deviceCredential.findUnique({ where: { id: row.credentialId } });
      if (
        !credential ||
        credential.revokedAt ||
        credential.userId !== row.userId ||
        credential.scope !== row.scope ||
        credential.tokenVersion !== row.tokenVersion
      )
        throw this.denied();
      if (
        !verify(
          'sha256',
          Buffer.from(row.payload, 'utf8'),
          this.key(credential.publicKey),
          Buffer.from(dto.signature, 'base64url'),
        )
      )
        throw this.denied();
      const consumed = await tx.deviceAuthChallenge.updateMany({
        where: { id: row.id, consumedAt: null, expiresAt: { gt: new Date() } },
        data: { consumedAt: new Date() },
      });
      if (consumed.count !== 1) throw this.denied();
      const user = await this.users.findPublicById(row.userId, tx);
      if (!user) throw this.denied();
      await tx.deviceCredential.update({
        where: { id: credential.id },
        data: { lastUsedAt: new Date() },
      });
      return this.sessions.create(user, row.tokenVersion, tx);
    });
  }
  async list(user: JwtPayload) {
    return this.prisma.$transaction(async (tx) => {
      await this.session(tx, user);
      const rows = await tx.deviceCredential.findMany({
        where: { userId: user.sub, tokenVersion: user.ver ?? 0, revokedAt: null },
        orderBy: { createdAt: 'desc' },
      });
      return rows.map((row) => this.summary(row));
    });
  }
  async revoke(user: JwtPayload, id: string) {
    await this.prisma.$transaction(async (tx) => {
      await this.session(tx, user);
      // 关闭尚未确认的绑定时，同时取消注册挑战，避免超时请求稍后重新创建凭据。
      await tx.deviceAuthChallenge.updateMany({
        where: { credentialId: id, userId: user.sub, purpose: 'register', consumedAt: null },
        data: { consumedAt: new Date() },
      });
      await tx.deviceCredential.updateMany({
        where: { id, userId: user.sub, revokedAt: null },
        data: { revokedAt: new Date() },
      });
    });
  }
}
