import { recordRange } from '../common/dto/record-range-query.dto';
import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import type { ProgressPhoto as ProgressPhotoResponse } from '@fit-trace/shared';
import type { Prisma, ProgressPhoto } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { UploadsService } from '../uploads/uploads.service';
import { CreateProgressPhotoDto } from './dto/create-progress-photo.dto';
import { ListProgressPhotosDto } from './dto/list-progress-photos.dto';

@Injectable()
export class ProgressPhotosService {
  private readonly logger = new Logger('ProgressPhotos');

  constructor(
    private readonly prisma: PrismaService,
    private readonly uploads: UploadsService,
  ) {}

  async create(userId: string, dto: CreateProgressPhotoDto): Promise<ProgressPhotoResponse> {
    const photo = await this.prisma.progressPhoto.create({
      data: {
        userId,
        type: dto.type,
        imageUrl: dto.imageUrl,
        recordedAt: dto.recordedAt ? new Date(dto.recordedAt) : new Date(),
        note: dto.note?.trim() || null,
      },
    });
    this.logger.log(
      `新增身材照片 user=${this.short(userId)} id=${this.short(photo.id)} 类型=${photo.type}`,
    );
    return this.toResponse(photo);
  }

  async list(
    userId: string,
    query: ListProgressPhotosDto,
  ): Promise<{
    data: ProgressPhotoResponse[];
    meta: { page: number; pageSize: number; total: number };
  }> {
    const where: Prisma.ProgressPhotoWhereInput = {
      userId,
      ...(query.type ? { type: query.type } : {}),
      recordedAt: recordRange(query),
    };
    const [photos, total] = await this.prisma.$transaction([
      this.prisma.progressPhoto.findMany({
        where,
        orderBy: [{ recordedAt: 'desc' }, { createdAt: 'desc' }],
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize,
      }),
      this.prisma.progressPhoto.count({ where }),
    ]);
    return {
      data: photos.map((photo) => this.toResponse(photo)),
      meta: { page: query.page, pageSize: query.pageSize, total },
    };
  }

  async findOne(userId: string, id: string): Promise<ProgressPhotoResponse> {
    return this.toResponse(await this.findOwnedPhoto(userId, id));
  }

  async remove(userId: string, id: string): Promise<void> {
    const photo = await this.findOwnedPhoto(userId, id);
    await this.prisma.progressPhoto.delete({ where: { id } });
    await this.uploads.removeByUrl(photo.imageUrl);
    this.logger.log(`删除身材照片 user=${this.short(userId)} id=${this.short(id)}`);
  }

  private short(id: string): string {
    return id.slice(0, 8);
  }

  private async findOwnedPhoto(userId: string, id: string): Promise<ProgressPhoto> {
    const photo = await this.prisma.progressPhoto.findFirst({ where: { id, userId } });
    if (!photo) {
      throw new NotFoundException({ code: 'PROGRESS_PHOTO_NOT_FOUND', message: '身材照片不存在' });
    }
    return photo;
  }

  private toResponse(photo: ProgressPhoto): ProgressPhotoResponse {
    return {
      id: photo.id,
      type: photo.type,
      imageUrl: photo.imageUrl,
      recordedAt: photo.recordedAt.toISOString(),
      note: photo.note,
      createdAt: photo.createdAt.toISOString(),
      updatedAt: photo.updatedAt.toISOString(),
    };
  }
}
