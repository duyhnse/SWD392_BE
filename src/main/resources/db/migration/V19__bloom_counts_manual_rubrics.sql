-- D82: the lecturer asks for a number of questions per Bloom level (0 = none of that level); AI drafts come without a
-- rubric and the AI no longer suggests one — rubrics are written by lecturers only.
ALTER TABLE "ai_generation_requests" ADD COLUMN "counts_by_bloom" jsonb;
UPDATE "ai_generation_requests" SET "counts_by_bloom" = '{}'::jsonb;
ALTER TABLE "ai_generation_requests" ALTER COLUMN "counts_by_bloom" SET NOT NULL;
ALTER TABLE "ai_generation_requests" DROP COLUMN "rubric_id";
ALTER TABLE "ai_generation_requests" DROP COLUMN "rubrics_by_bloom";
ALTER TABLE "questions" DROP COLUMN "ai_suggested_rubric";
