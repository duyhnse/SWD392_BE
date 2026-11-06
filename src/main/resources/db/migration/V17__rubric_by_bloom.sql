-- Rubric per Bloom level at generation: e.g. REMEMBER/UNDERSTAND → R1, APPLY/ANALYZE → R2 (rubric by question type).
ALTER TABLE "ai_generation_requests" ADD COLUMN "rubrics_by_bloom" jsonb;

-- D79: thinking level of the background AI jobs (admin-editable), and room for a long high-thinking generation call.
INSERT INTO "system_settings" ("setting_key", "setting_value", "description") VALUES
  ('ai.reasoning.generation', '"high"', 'AI: how much the model thinks before writing questions from the materials (minimal / low / medium / high)'),
  ('ai.reasoning.grading', '"high"', 'AI: how much the model thinks before suggesting scores and the overall comment (minimal / low / medium / high)')
ON CONFLICT ("setting_key") DO NOTHING;
UPDATE "system_settings" SET "setting_value" = '"180"', "updated_at" = now(),
  "description" = 'AI: seconds for one background AI call — question generation, grading, summary (60–300)'
  WHERE "setting_key" = 'ai.timeout.llm_job_sec' AND "setting_value" = '"120"';
