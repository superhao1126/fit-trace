import { Type } from 'class-transformer';
import { IsDateString, Matches, IsInt, IsOptional, Max, Min } from 'class-validator';

export class DashboardQueryDto {
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/, { message: '日期格式应为 YYYY-MM-DD' })
  @IsDateString({ strict: true }, { message: '请选择正确的日期' })
  date?: string;

  /** UTC 以东为正，单位为分钟。 */
  @Type(() => Number)
  @IsOptional()
  @IsInt()
  @Min(-720)
  @Max(840)
  tzOffset = 480;
}
