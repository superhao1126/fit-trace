import { randomInt, randomUUID } from 'node:crypto';
import { BadRequestException, Injectable } from '@nestjs/common';
import type {
  EmailCodePurpose,
  EmailCodeResult,
  ResetPasswordVerification,
} from '@fit-trace/shared';
import type { EmailVerification, Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { AuthSecurityService, type EmailCodeReservation } from './auth-security.service';
import { MailService } from './mail.service';
import { EmailCaptchaService } from './email-captcha.service';
import type { EmailCaptchaDto } from './dto/email-captcha.dto';

@Injectable()
export class EmailVerificationService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly security: AuthSecurityService,
    private readonly mail: MailService,
    private readonly captcha: EmailCaptchaService,
  ) {}

  async send(
    email: string,
    purpose: EmailCodePurpose,
    ip: string,
    proof?: EmailCaptchaDto,
  ): Promise<EmailCodeResult> {
    return this.sendCode(email, purpose, ip, proof, true);
  }

  /** Mini program email login intentionally has no third-party captcha. */
  async sendMiniprogramLoginCode(email: string, ip: string): Promise<EmailCodeResult> {
    return this.sendCode(email, 'LOGIN', ip, undefined, false);
  }

  private async sendCode(
    email: string,
    purpose: EmailCodePurpose,
    ip: string,
    proof: EmailCaptchaDto | undefined,
    requireCaptcha: boolean,
  ): Promise<EmailCodeResult> {
    this.mail.assertConfigured();
    if (requireCaptcha) await this.captcha.verify(proof, ip);
    await this.security.cleanup();
    const code = randomInt(0, 1000000).toString().padStart(6, '0');
    const codeHash = this.security.digest(`email:${email}:${purpose}:${code}`);
    const data = {
      codeHash,
      expiresAt: new Date(Date.now() + 600000),
      attempts: 0,
      consumedAt: null,
      createdAt: new Date(),
    };
    let reservations: EmailCodeReservation[] = [];
    const row = await this.prisma.$transaction(async (tx) => {
      // 限流与验证码写入原子提交：被限流或数据库失败都不消耗发送额度。
      reservations = await this.security.reserveEmailCode(email, purpose, ip, tx);
      const user = await tx.user.findUnique({ where: { email }, select: { id: true } });
      // 返回相同结果，不通过验证码发送接口暴露账户是否存在。
      if ((purpose === 'RESET_PASSWORD' && !user) || (purpose === 'REGISTER' && user)) return null;
      return tx.emailVerification.upsert({
        where: { email_purpose: { email, purpose } },
        create: { email, purpose, ...data },
        update: data,
      });
    });
    if (!row) return { retryAfterSeconds: 60, expiresInSeconds: 600 };
    try {
      await this.mail.sendCode(email, purpose, code);
    } catch (error) {
      await this.prisma.$transaction(async (tx) => {
        await tx.emailVerification.deleteMany({ where: { id: row.id, codeHash } });
        await this.security.releaseEmailCode(reservations, tx);
      });
      throw error;
    }
    return { retryAfterSeconds: 60, expiresInSeconds: 600 };
  }

  async verifyResetCode(email: string, code: string): Promise<ResetPasswordVerification> {
    const resetToken = randomUUID();
    return this.withCode(email, 'RESET_PASSWORD', code, async (tx) => {
      // 消费邮箱验证码后，将该记录转为短期重置凭证；摘要使用独立用途前缀。
      await tx.emailVerification.update({
        where: { email_purpose: { email, purpose: 'RESET_PASSWORD' } },
        data: {
          codeHash: this.security.digest(`reset-token:${email}:${resetToken}`),
          expiresAt: new Date(Date.now() + 300000),
          consumedAt: null,
          attempts: 0,
        },
      });
      return { resetToken, expiresInSeconds: 300 };
    });
  }

  async withResetToken<T>(
    email: string,
    token: string,
    action: (tx: Prisma.TransactionClient) => Promise<T>,
  ): Promise<T> {
    return this.withSecret(email, 'RESET_PASSWORD', token, action, false);
  }

  async withCode<T>(
    email: string,
    purpose: EmailCodePurpose,
    code: string,
    action: (tx: Prisma.TransactionClient) => Promise<T>,
  ): Promise<T> {
    return this.withSecret(email, purpose, code, action, true);
  }

  private async withSecret<T>(
    email: string,
    purpose: EmailCodePurpose,
    code: string,
    action: (tx: Prisma.TransactionClient) => Promise<T>,
    isEmailCode: boolean,
  ): Promise<T> {
    const result = await this.prisma.$transaction(
      async (tx) => {
        // 锁住这条验证码；消费和业务写入在同一事务中，阻止并发重放。
        const rows = await tx.$queryRaw<
          EmailVerification[]
        >`SELECT * FROM "email_verifications" WHERE "email" = ${email} AND "purpose" = ${purpose}::"EmailCodePurpose" FOR UPDATE`;
        const row = rows[0];
        if (!row || row.consumedAt || row.expiresAt <= new Date())
          return { ok: false as const, expired: true };
        if (isEmailCode && row.attempts >= 5) return { ok: false as const, expired: false };
        const secret = isEmailCode
          ? `email:${email}:${purpose}:${code}`
          : `reset-token:${email}:${code}`;
        if (!this.security.matches(secret, row.codeHash)) {
          if (isEmailCode)
            await tx.emailVerification.update({
              where: { id: row.id },
              data: { attempts: { increment: 1 } },
            });
          return { ok: false as const, expired: false };
        }
        await tx.emailVerification.update({
          where: { id: row.id },
          data: { consumedAt: new Date() },
        });
        return { ok: true as const, value: await action(tx) };
      },
      { timeout: 15000 },
    );
    if (!result.ok)
      throw new BadRequestException({
        code: !isEmailCode
          ? 'RESET_VERIFICATION_INVALID'
          : result.expired
            ? 'EMAIL_CODE_EXPIRED'
            : 'EMAIL_CODE_INVALID',
        message: !isEmailCode
          ? '邮箱验证已失效，请重新验证邮箱'
          : result.expired
            ? '邮箱验证码已过期或未发送，请重新获取'
            : '邮箱验证码错误或尝试次数过多，请重新获取',
      });
    return result.value;
  }
}
