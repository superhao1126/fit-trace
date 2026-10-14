CREATE TABLE "meal_favorites" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "foods" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "meal_favorites_pkey" PRIMARY KEY ("id")
);
CREATE INDEX "meal_favorites_userId_updatedAt_idx" ON "meal_favorites"("userId", "updatedAt");
ALTER TABLE "meal_favorites" ADD CONSTRAINT "meal_favorites_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
