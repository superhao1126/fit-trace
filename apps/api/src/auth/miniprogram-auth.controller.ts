import { Body, Controller, Post, Req } from '@nestjs/common';
import { Transform } from 'class-transformer';
import { IsEmail, MaxLength } from 'class-validator';
import type { Request } from 'express';
import { AuthService } from './auth.service';
import { EmailVerificationService } from './email-verification.service';
import { EmailLoginDto } from './dto/email-login.dto';

class MiniprogramEmailDto {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim().toLowerCase() : value,
  )
  @IsEmail({}, { message: '请输入正确的邮箱地址' })
  @MaxLength(254)
  email!: string;
}
@Controller('auth/miniprogram')
export class MiniprogramAuthController {
  constructor(
    private readonly auth: AuthService,
    private readonly verification: EmailVerificationService,
  ) {}
  @Post('email-code') async send(@Body() dto: MiniprogramEmailDto, @Req() req: Request) {
    return {
      data: await this.verification.sendMiniprogramLoginCode(dto.email, req.ip ?? 'unknown'),
    };
  }
  @Post('login') async login(@Body() dto: EmailLoginDto, @Req() req: Request) {
    return { data: await this.auth.loginByEmail(dto, req.ip ?? 'unknown') };
  }
}
