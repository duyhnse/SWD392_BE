-- D71: the live answer analysis runs on Gemini 3.5 Flash-Lite (1–2 s, the same verdicts as 3.8 Flash in the prod E2E;
-- 3.8 Flash took up to 14 s); question generation and grading stay on Gemini 3.8 Flash.
UPDATE "system_settings" SET "setting_value" = '"google/gemini-3.5-flash-lite"', "updated_at" = now()
  WHERE "setting_key" = 'ai.model.interview' AND "setting_value" = '"google/gemini-3.8-flash"';
