import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { compare, hash } from 'bcryptjs';
import { randomBytes } from 'node:crypto';
import { UsersService, type PublicUser } from '../users/users.service';
import { LoginDto } from './dto/login.dto';
import { EmailLoginDto } from './dto/email-login.dto';
import { RegisterDto } from './dto/register.dto';
import { VerifyResetCodeDto } from './dto/verify-reset-code.dto';
import { ResetPasswordDto } from './dto/reset-password.dto';
import { AuthSecurityService } from './auth-security.service';
import { EmailVerificationService } from './email-verification.service';
import type { AuthResult } from './auth.types';
import { AuthSessionService } from './auth-session.service';
import { ChangePasswordDto } from './dto/change-password.dto';
import { PasswordEmailCodeDto, SetPasswordDto } from './dto/set-password.dto';
import type { JwtPayload } from './auth.types';
import type { EmailCaptchaDto } from './dto/email-captcha.dto';

@Injectable()
export class AuthService {
  private readonly logger = new Logger('Auth');

  constructor(
    private readonly users: UsersService,
    private readonly sessions: AuthSessionService,
    private readonly security: AuthSecurityService,
    private readonly verification: EmailVerificationService,
  ) {}

  async register(dto: RegisterDto, ip: string): Promise<AuthResult> {
    await this.security.limit('register-ip', ip, 20, 3600);
    const passwordHash = await hash(dto.password, 12);
    const user = await this.verification.withCode(dto.email, 'REGISTER', dto.emailCode, (tx) =>
      this.users.create(
        {
          email: dto.email,
          passwordHash,
          ...(dto.nickname ? { nickname: dto.nickname } : {}),
        },
        tx,
      ),
    );

    this.logger.log(`注册成功 email=${user.email} user=${user.id.slice(0, 8)}`);
    return this.buildAuthResult(user);
  }

  async loginByEmail(dto: EmailLoginDto, ip: string): Promise<AuthResult> {
    if (dto.acceptedTerms !== true) {
      throw new BadRequestException({
        code: 'AGREEMENT_REQUIRED',
        message: '请先阅读并同意用户协议和隐私政策',
      });
    }
    await this.security.limit('email-login-ip', ip, 40, 900);
    await this.security.limit('email-login-email', dto.email, 20, 900);
    const result = await this.verification.withCode(
      dto.email,
      'LOGIN',
      dto.emailCode,
      async (tx) => {
        let user = await tx.user.findUnique({ where: { email: dto.email } });
        if (!user) {
          // 自动注册不设置可猜测的初始密码；用户可通过邮箱验证设置自己的密码。
          user = await tx.user.upsert({
            where: { email: dto.email },
            update: {},
            create: {
              email: dto.email,
              passwordHash: await hash(randomBytes(32).toString('hex'), 12),
              hasPassword: false,
            },
          });
        }
        const publicUser = await this.users.findPublicById(user.id, tx);
        if (!publicUser) {
          throw new UnauthorizedException({
            code: 'UNAUTHORIZED',
            message: '账号不可用，请重新登录',
          });
        }
        // 验证码消费、自动注册与设备会话写入同一事务，失败时可重试验证码。
        await this.security.loginSucceeded(dto.email, ip, tx);
        return this.sessions.create(publicUser, user.tokenVersion, tx, true);
      },
    );
    this.logger.log(
      `邮箱验证登录成功 email=${result.user.email} user=${result.user.id.slice(0, 8)}`,
    );
    return result;
  }

  async login(dto: LoginDto, ip: string): Promise<AuthResult> {
    await this.security.limit('login-ip', ip, 40, 900);
    await this.security.limit('login-email', dto.email, 20, 900);
    if (await this.security.needsCaptcha(dto.email, ip)) {
      await this.security.verifyCaptcha(dto.captchaId, dto.captchaCode, ip);
    }
    const user = await this.users.findByEmail(dto.email);
    // 不存在的账户也执行密码比较，降低账户存在性时序差异。
    const passwordHash = user?.passwordHash ?? (await this.dummyPasswordHash());
    if (!(await compare(dto.password, passwordHash)) || !user) {
      await this.security.loginFailed(dto.email, ip);
      this.logger.warn(`登录失败 email=${dto.email} 原因=${user ? '密码错误' : '邮箱不存在'}`);
      if (await this.security.needsCaptcha(dto.email, ip)) {
        throw new ForbiddenException({
          code: 'LOGIN_CAPTCHA_REQUIRED',
          message: '邮箱或密码错误，请完成安全验证后重试',
        });
      }
      throw new UnauthorizedException({
        code: 'INVALID_CREDENTIALS',
        message: '邮箱或密码错误',
      });
    }

    this.logger.log(`登录成功 email=${user.email} user=${user.id.slice(0, 8)}`);
    const publicUser = await this.users.findPublicById(user.id);
    if (!publicUser) {
      throw new UnauthorizedException({
        code: 'INVALID_CREDENTIALS',
        message: '邮箱或密码错误',
      });
    }
    await this.security.loginSucceeded(dto.email, ip);
    return this.buildAuthResult(publicUser, user.tokenVersion);
  }

  async verifyResetCode(dto: VerifyResetCodeDto, ip: string) {
    await this.security.limit('reset-verify-ip', ip, 30, 900);
    await this.security.limit('reset-verify-email', dto.email, 10, 900);
    return this.verification.verifyResetCode(dto.email, dto.emailCode);
  }

  async resetPassword(dto: ResetPasswordDto, ip: string): Promise<void> {
    await this.security.limit('reset-ip', ip, 20, 3600);
    await this.security.limit('reset-email', dto.email, 10, 900);
    const passwordHash = await hash(dto.password, 12);
    await this.verification.withResetToken(dto.email, dto.resetToken, async (tx) => {
      await tx.user.update({
        where: { email: dto.email },
        data: { passwordHash, hasPassword: true, tokenVersion: { increment: 1 } },
      });
    });
    await this.security.loginSucceeded(dto.email, ip);
  }

  async changePassword(userId: string, dto: ChangePasswordDto): Promise<void> {
    await this.security.limit('change-password-user', userId, 10, 900);
    const user = await this.users.findById(userId);
    if (!user || !user.passwordHash || !(await compare(dto.currentPassword, user.passwordHash))) {
      throw new BadRequestException({
        code: 'CURRENT_PASSWORD_INCORRECT',
        message: '当前密码不正确',
      });
    }
    if (dto.currentPassword === dto.password) {
      throw new BadRequestException({
        code: 'PASSWORD_UNCHANGED',
        message: '新密码不能与当前密码相同',
      });
    }
    await this.users.updatePassword(userId, user.passwordHash, await hash(dto.password, 12));
  }

  private async passwordAccount(payload: JwtPayload) {
    const user = await this.users.findById(payload.sub);
    if (!user || user.tokenVersion !== (payload.ver ?? 0)) {
      throw new UnauthorizedException({ code: 'UNAUTHORIZED', message: '请重新登录后再试' });
    }
    if (!user.email) {
      throw new BadRequestException({
        code: 'EMAIL_ACCOUNT_REQUIRED',
        message: '请使用原邮箱账号管理密码',
      });
    }
    return { ...user, email: user.email };
  }

  async sendPasswordCode(payload: JwtPayload, ip: string, proof?: EmailCaptchaDto) {
    const user = await this.passwordAccount(payload);
    return this.verification.send(user.email, 'RESET_PASSWORD', ip, proof);
  }

  async verifyPasswordCode(payload: JwtPayload, dto: PasswordEmailCodeDto, ip: string) {
    const user = await this.passwordAccount(payload);
    return this.verifyResetCode({ email: user.email, emailCode: dto.emailCode }, ip);
  }

  async setPassword(payload: JwtPayload, dto: SetPasswordDto, ip: string): Promise<AuthResult> {
    await this.security.limit('reset-ip', ip, 20, 3600);
    await this.security.limit('change-password-user', payload.sub, 10, 900);
    const user = await this.passwordAccount(payload);
    const passwordHash = await hash(dto.password, 12);
    return this.verification.withResetToken(user.email, dto.resetToken, (tx) =>
      this.sessions.replaceAfterPasswordChange(payload, user.passwordHash, passwordHash, tx),
    );
  }

  async changePasswordWithSession(
    payload: JwtPayload,
    dto: ChangePasswordDto,
  ): Promise<AuthResult> {
    await this.security.limit('change-password-user', payload.sub, 10, 900);
    const user = await this.passwordAccount(payload);
    if (!user.passwordHash || !(await compare(dto.currentPassword, user.passwordHash))) {
      throw new BadRequestException({
        code: 'CURRENT_PASSWORD_INCORRECT',
        message: '当前密码不正确',
      });
    }
    if (dto.currentPassword === dto.password) {
      throw new BadRequestException({
        code: 'PASSWORD_UNCHANGED',
        message: '新密码不能与当前密码相同',
      });
    }
    return this.sessions.replaceAfterPasswordChange(
      payload,
      user.passwordHash,
      await hash(dto.password, 12),
    );
  }

  private dummyHash?: Promise<string>;
  private dummyPasswordHash(): Promise<string> {
    return (this.dummyHash ??= hash('fit-trace-invalid-account', 12));
  }

  private async buildAuthResult(user: PublicUser, ver = 0): Promise<AuthResult> {
    return this.sessions.create(user, ver);
  }
}
