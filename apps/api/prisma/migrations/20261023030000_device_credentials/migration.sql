-- AlterTable
ALTER TABLE "auth_sessions" ADD COLUMN     "emailVerifiedAt" TIMESTAMP(3);

-- CreateTable
CREATE TABLE "device_credentials" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "publicKey" TEXT NOT NULL,
    "scope" TEXT NOT NULL,
    "deviceName" TEXT NOT NULL,
    "tokenVersion" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastUsedAt" TIMESTAMP(3),
    "revokedAt" TIMESTAMP(3),

    CONSTRAINT "device_credentials_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "device_auth_challenges" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "purpose" TEXT NOT NULL,
    "credentialId" TEXT NOT NULL,
    "sessionId" TEXT,
    "tokenVersion" INTEGER NOT NULL,
    "publicKey" TEXT,
    "deviceName" TEXT,
    "scope" TEXT NOT NULL,
    "payload" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "consumedAt" TIMESTAMP(3),

    CONSTRAINT "device_auth_challenges_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "device_credentials_userId_idx" ON "device_credentials"("userId");

-- CreateIndex
CREATE INDEX "device_auth_challenges_expiresAt_idx" ON "device_auth_challenges"("expiresAt");

-- AddForeignKey
ALTER TABLE "device_credentials" ADD CONSTRAINT "device_credentials_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "device_auth_challenges" ADD CONSTRAINT "device_auth_challenges_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
