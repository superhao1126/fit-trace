import { createHash, randomBytes } from 'node:crypto';
import { ConflictException, Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import type { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { UsersService, type PublicUser } from '../users/users.service';
import type { AuthResult, JwtPayload } from './auth.types';

@Injectable()
export class AuthSessionService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly users: UsersService,
    private readonly jwt: JwtService,
  ) {}

  async create(
    user: PublicUser,
    tokenVersion: number,
    transaction: Prisma.TransactionClient = this.prisma,
    emailVerified = false,
  ): Promise<AuthResult> {
    const refreshToken = randomBytes(32).toString('hex');
    const session = await transaction.authSession.create({
      data: {
        userId: user.id,
        tokenVersion,
        tokenHash: this.digest(refreshToken),
        ...(emailVerified ? { emailVerifiedAt: new Date() } : {}),
      },
    });
    return this.result(user, session.id, tokenVersion, refreshToken);
  }

  async refresh(refreshToken: string): Promise<AuthResult> {
    const session = await this.prisma.authSession.findUnique({
      where: { tokenHash: this.digest(refreshToken) },
      include: { user: { select: { tokenVersion: true } } },
    });
    if (!session || session.revokedAt || session.tokenVersion !== session.user.tokenVersion) {
      throw this.unauthorized();
    }
    const user = await this.users.findPublicById(session.userId);
    if (!user) throw this.unauthorized();
    // 条件更新避免退出与续期并发时重新激活已撤销的会话。
    const updated = await this.prisma.authSession.updateMany({
      where: { id: session.id, revokedAt: null },
      data: { lastUsedAt: new Date() },
    });
    if (!updated.count) throw this.unauthorized();
    return this.result(user, session.id, session.tokenVersion, refreshToken);
  }

  async replaceAfterPasswordChange(
    payload: JwtPayload,
    expectedHash: string,
    passwordHash: string,
    transaction?: Prisma.TransactionClient,
  ): Promise<AuthResult> {
    const replace = async (tx: Prisma.TransactionClient) => {
      const version = payload.ver ?? 0;
      if (payload.sid) {
        const current = await tx.authSession.findUnique({ where: { id: payload.sid } });
        if (
          !current ||
          current.revokedAt ||
          current.userId !== payload.sub ||
          current.tokenVersion !== version
        )
          throw this.unauthorized();
      }
      const changed = await tx.user.updateMany({
        where: { id: payload.sub, passwordHash: expectedHash, tokenVersion: version },
        data: { passwordHash, hasPassword: true, tokenVersion: { increment: 1 } },
      });
      if (!changed.count) {
        throw new ConflictException({
          code: 'PASSWORD_CHANGED',
          message: '密码已发生变化，请重新验证后再试',
        });
      }
      // 原设备凭证与其他设备一并撤销，原子签发当前设备的新凭证。
      await tx.authSession.updateMany({
        where: { userId: payload.sub, revokedAt: null },
        data: { revokedAt: new Date() },
      });
      const user = await this.users.findPublicById(payload.sub, tx);
      if (!user) throw this.unauthorized();
      return this.create(user, version + 1, tx);
    };
    return transaction ? replace(transaction) : this.prisma.$transaction(replace);
  }

  async upgrade(payload: JwtPayload): Promise<AuthResult> {
    // 新版短期访问令牌不能自行换取另一份长期凭证。
    if (payload.sid !== undefined) throw this.unauthorized();
    const user = await this.users.findPublicById(payload.sub);
    if (!user) throw this.unauthorized();
    return this.create(user, payload.ver ?? 0);
  }

  async logout(refreshToken: string): Promise<void> {
    await this.prisma.authSession.updateMany({
      where: { tokenHash: this.digest(refreshToken), revokedAt: null },
      data: { revokedAt: new Date() },
    });
  }

  private async result(
    user: PublicUser,
    sid: string,
    ver: number,
    refreshToken: string,
  ): Promise<AuthResult> {
    const accessToken = await this.jwt.signAsync({ sub: user.id, email: user.email, ver, sid });
    const { exp } = this.jwt.decode<{ exp: number }>(accessToken);
    return {
      accessToken,
      accessTokenExpiresAt: new Date(exp * 1000).toISOString(),
      refreshToken,
      user,
    };
  }

  private digest(token: string): string {
    return createHash('sha256').update(token).digest('hex');
  }

  private unauthorized(): UnauthorizedException {
    return new UnauthorizedException({
      code: 'UNAUTHORIZED',
      message: '登录会话已失效，请重新登录',
    });
  }
}
