-- D71 tuning from the prod E2E: Gemini 3.8 Flash needs up to ~7 s to analyse a full answer (inside the 11 s turn), and the
-- relevance threshold is only a floor now (the generation LLM judges whether the passages cover the topic).
UPDATE "system_settings" SET "setting_value" = '"8"', "updated_at" = now(),
  "description" = 'AI: seconds for the answer analysis inside a turn (3–15); it never goes past the turn budget'
  WHERE "setting_key" = 'ai.timeout.llm_interview_sec' AND "setting_value" = '"6"';
UPDATE "system_settings" SET "setting_value" = '"0.45"', "updated_at" = now(),
  "description" = 'AI: minimum relevance of a passage (0.3–0.8), a floor only; the AI then judges whether the topic is really covered'
  WHERE "setting_key" = 'ai.rag.min_similarity' AND "setting_value" IN ('"0.55"', '"0.45"');
UPDATE "system_settings" SET "setting_value" = '"15"', "updated_at" = now(),
  "description" = 'AI: seconds to voice one question (4–15); main questions are voiced in the background, a follow-up within the turn budget'
  WHERE "setting_key" = 'ai.timeout.tts_sec' AND "setting_value" = '"8"';
