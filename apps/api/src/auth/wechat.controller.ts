import { Body, Controller, Post, Req, UseGuards } from '@nestjs/common';
import { Equals, IsOptional, IsString, Length, Matches } from 'class-validator';
import type { Request } from 'express';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import type { JwtPayload } from './auth.types';
import { WechatService } from './wechat.service';
class WechatLoginDto {
  @IsString() @Length(1, 256) code!: string;
  @Equals(true) acceptedTerms!: boolean;
  @IsOptional() @Matches(/^[a-f0-9]{32}$/) bindingCode?: string;
}
@Controller('auth/wechat')
export class WechatController {
  constructor(private readonly wechat: WechatService) {}
  @Post('login') async login(@Body() dto: WechatLoginDto, @Req() req: Request) {
    return { data: await this.wechat.login(dto.code, req.ip ?? 'unknown', dto.bindingCode) };
  }
  @UseGuards(JwtAuthGuard)
  @Post('binding-code')
  async binding(@CurrentUser() payload: JwtPayload) {
    return { data: await this.wechat.bindingCode(payload) };
  }
}
