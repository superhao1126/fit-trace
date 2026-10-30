-- AlterTable
ALTER TABLE "users" ALTER COLUMN "email" DROP NOT NULL,
ALTER COLUMN "passwordHash" DROP NOT NULL;

-- AlterTable
ALTER TABLE "workout_records" ADD COLUMN     "clientRequestId" TEXT;

-- CreateTable
CREATE TABLE "wechat_identities" (
    "id" TEXT NOT NULL,
    "appId" TEXT NOT NULL,
    "openId" TEXT NOT NULL,
    "unionId" TEXT,
    "userId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "wechat_identities_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "wechat_binding_codes" (
    "id" TEXT NOT NULL,
    "codeHash" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "sessionId" TEXT NOT NULL,
    "tokenVersion" INTEGER NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "consumedAt" TIMESTAMP(3),

    CONSTRAINT "wechat_binding_codes_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "wechat_identities_appId_openId_key" ON "wechat_identities"("appId", "openId");

-- CreateIndex
CREATE UNIQUE INDEX "wechat_identities_appId_userId_key" ON "wechat_identities"("appId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "wechat_binding_codes_codeHash_key" ON "wechat_binding_codes"("codeHash");

-- CreateIndex
CREATE INDEX "wechat_binding_codes_expiresAt_idx" ON "wechat_binding_codes"("expiresAt");

-- CreateIndex
CREATE UNIQUE INDEX "workout_records_userId_clientRequestId_key" ON "workout_records"("userId", "clientRequestId");

-- AddForeignKey
ALTER TABLE "wechat_identities" ADD CONSTRAINT "wechat_identities_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "wechat_binding_codes" ADD CONSTRAINT "wechat_binding_codes_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

