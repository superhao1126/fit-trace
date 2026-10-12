ALTER TABLE "workout_records" ADD COLUMN "exercises" JSONB;
CREATE TABLE "training_templates" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "name" TEXT NOT NULL,
  "durationMinutes" INTEGER NOT NULL,
  "exercises" JSONB NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "training_templates_pkey" PRIMARY KEY ("id")
);
CREATE INDEX "training_templates_userId_idx" ON "training_templates"("userId");
ALTER TABLE "training_templates" ADD CONSTRAINT "training_templates_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
CREATE TABLE "training_plans" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "days" JSONB NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "training_plans_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "training_plans_userId_key" ON "training_plans"("userId");
ALTER TABLE "training_plans" ADD CONSTRAINT "training_plans_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
