import { IsIn, IsOptional, IsString, IsUUID, Matches, MaxLength, MinLength } from 'class-validator';

export class RegistrationOptionsDto {
  @IsString()
  @MaxLength(256)
  @MinLength(80)
  publicKey!: string;
  @IsIn(['android-dev', 'android-prod'])
  scope!: string;
  @IsString()
  @MaxLength(80)
  @MinLength(1)
  deviceName!: string;
  @IsOptional()
  @Matches(/^\d{6}$/)
  emailCode?: string;
}
export class CredentialLoginOptionsDto {
  @IsUUID() credentialId!: string;
  @IsIn(['android-dev', 'android-prod']) scope!: string;
}
export class CredentialVerifyDto {
  @IsUUID() challengeId!: string;
  @IsString()
  @Matches(/^[A-Za-z0-9_-]{80,144}$/)
  signature!: string;
}
