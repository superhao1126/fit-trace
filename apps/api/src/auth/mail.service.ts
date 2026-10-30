import { Injectable, Logger, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createTransport } from 'nodemailer';
import type { EmailCodePurpose } from '@fit-trace/shared';

@Injectable()
export class MailService {
  private readonly logger = new Logger(MailService.name);
  constructor(private readonly config: ConfigService) {}

  assertConfigured(): void {
    if (
      !['SMTP_HOST', 'SMTP_USER', 'SMTP_PASS', 'SMTP_FROM'].every((key) =>
        this.config.get<string>(key),
      )
    ) {
      throw new ServiceUnavailableException({
        code: 'MAIL_NOT_CONFIGURED',
        message: '验证码邮件服务尚未配置，请联系管理员',
      });
    }
  }

  async sendCode(email: string, purpose: EmailCodePurpose, code: string): Promise<void> {
    this.assertConfigured();
    const secure = this.config.get<string>('SMTP_SECURE', 'true') === 'true';
    const transporter = createTransport({
      host: this.config.get<string>('SMTP_HOST'),
      port: Number(this.config.get<string>('SMTP_PORT', secure ? '465' : '587')),
      secure,
      requireTLS: !secure,
      auth: {
        user: this.config.get<string>('SMTP_USER'),
        pass: this.config.get<string>('SMTP_PASS'),
      },
      connectionTimeout: 10000,
      greetingTimeout: 10000,
      socketTimeout: 15000,
    });
    try {
      await transporter.sendMail({
        from: this.config.get<string>('SMTP_FROM'),
        to: email,
        subject: `循形Fitness ${{ LOGIN: '登录', REGISTER: '注册', RESET_PASSWORD: '重置密码' }[purpose]}验证码`,
        text: `你的验证码是 ${code}，10 分钟内有效，仅可使用一次。请勿将验证码提供给他人。如果不是你本人操作，请忽略此邮件`,
      });
    } catch {
      this.logger.error('验证码邮件发送失败，请检查 SMTP 配置和服务状态');
      throw new ServiceUnavailableException({
        code: 'MAIL_SEND_FAILED',
        message: '验证码发送失败，请稍后重试',
      });
    } finally {
      transporter.close();
    }
  }
}
