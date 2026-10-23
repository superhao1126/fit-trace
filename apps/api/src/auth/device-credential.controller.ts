import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  ParseUUIDPipe,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import type { JwtPayload } from './auth.types';
import { DeviceCredentialService } from './device-credential.service';
import {
  RegistrationOptionsDto,
  CredentialLoginOptionsDto,
  CredentialVerifyDto,
} from './dto/device-credential.dto';

@Controller('auth/device-credentials')
export class DeviceCredentialController {
  constructor(private readonly devices: DeviceCredentialService) {}
  @UseGuards(JwtAuthGuard)
  @Post('registration-options')
  async registrationOptions(@CurrentUser() user: JwtPayload, @Body() dto: RegistrationOptionsDto) {
    return { data: await this.devices.registrationOptions(user, dto) };
  }
  @UseGuards(JwtAuthGuard)
  @Post('registration-verify')
  async registrationVerify(@CurrentUser() user: JwtPayload, @Body() dto: CredentialVerifyDto) {
    return { data: await this.devices.registrationVerify(user, dto) };
  }
  @Post('login-options')
  async loginOptions(@Body() dto: CredentialLoginOptionsDto, @Req() req: Request) {
    return {
      data: await this.devices.loginOptions(dto, req.ip ?? req.socket.remoteAddress ?? 'unknown'),
    };
  }
  @Post('login-verify')
  async loginVerify(@Body() dto: CredentialVerifyDto, @Req() req: Request) {
    return {
      data: await this.devices.loginVerify(dto, req.ip ?? req.socket.remoteAddress ?? 'unknown'),
    };
  }
  @UseGuards(JwtAuthGuard)
  @Get()
  async list(@CurrentUser() user: JwtPayload) {
    return { data: await this.devices.list(user) };
  }
  @UseGuards(JwtAuthGuard)
  @Delete(':id')
  async revoke(@CurrentUser() user: JwtPayload, @Param('id', ParseUUIDPipe) id: string) {
    await this.devices.revoke(user, id);
    return { data: null };
  }
}
