-- D71: every AI model goes through OpenRouter, whose ids carry the vendor ("google/…"). The LLM becomes Gemini 3.8 Flash.
UPDATE "system_settings" SET "setting_value" = '"google/gemini-3.8-flash"', "updated_at" = now()
  WHERE "setting_key" IN ('ai.model.interview', 'ai.model.generation', 'ai.model.grading');
UPDATE "system_settings" SET "setting_value" = '"google/gemini-3.1-flash-lite"', "updated_at" = now()
  WHERE "setting_key" = 'ai.model.interview_fallback' AND "setting_value" <> '""';
UPDATE "system_settings" SET "setting_value" = to_jsonb('google/' || ("setting_value" #>> '{}')), "updated_at" = now()
  WHERE "setting_key" IN ('ai.model.stt', 'ai.model.tts', 'ai.model.embedding') AND position('/' IN "setting_value" #>> '{}') = 0;
