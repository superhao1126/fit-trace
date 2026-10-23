import { DeviceCredentialService } from './device-credential.service';
import { DeviceCredentialController } from './device-credential.controller';
import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { UsersModule } from '../users/users.module';
import { UsersController } from '../users/users.controller';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { AuthSecurityService } from './auth-security.service';
import { EmailVerificationService } from './email-verification.service';
import { MailService } from './mail.service';
import { accessTokenLifetime } from './auth-config';
import { AuthSessionService } from './auth-session.service';
import { EmailCaptchaService } from './email-captcha.service';

@Module({
  imports: [
    UsersModule,
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => {
        const secret = config.get<string>('JWT_SECRET');
        if (!secret) {
          throw new Error('缺少 JWT_SECRET 配置');
        }
        return {
          secret,
          signOptions: {
            expiresIn: accessTokenLifetime(config.get('JWT_EXPIRES_IN_SECONDS')),
          },
        };
      },
    }),
  ],
  controllers: [AuthController, UsersController, DeviceCredentialController],
  providers: [
    AuthService,
    DeviceCredentialService,
    AuthSessionService,
    JwtAuthGuard,
    AuthSecurityService,
    EmailVerificationService,
    EmailCaptchaService,
    MailService,
  ],
  exports: [JwtModule, JwtAuthGuard],
})
export class AuthModule {}
