-- FG1: a material may belong to one chapter (topic) of its course; deleting the topic keeps the material (D70).
ALTER TABLE "course_materials" ADD COLUMN "topic_id" uuid;
ALTER TABLE "course_materials" ADD FOREIGN KEY ("topic_id") REFERENCES "topics" ("topic_id") ON DELETE SET NULL;
CREATE INDEX "course_materials_topic_idx" ON "course_materials" ("topic_id");

-- FG1: the shared rubric chosen when asking the AI for questions; every draft of the request gets it (D70).
ALTER TABLE "ai_generation_requests" ADD COLUMN "rubric_id" uuid;
ALTER TABLE "ai_generation_requests" ADD FOREIGN KEY ("rubric_id") REFERENCES "rubrics" ("rubric_id") ON DELETE SET NULL;

-- D70: AI settings edited by the admin (Settings → AI), sent to the AI node with every call. Defaults = measured values.
INSERT INTO "system_settings" ("setting_key", "setting_value", "description") VALUES
  ('ai.model.interview', '"gemini-3.5-flash-lite"', 'AI: LLM for answer analysis + follow-ups during the exam'),
  ('ai.model.interview_fallback', '"gemini-3.1-flash-lite"', 'AI: LLM tried when the exam LLM fails, if the turn still has time (empty = none)'),
  ('ai.model.generation', '"gemini-3.5-flash-lite"', 'AI: LLM that writes questions from the materials'),
  ('ai.model.grading', '"gemini-3.5-flash-lite"', 'AI: LLM for grading suggestions and the summary'),
  ('ai.model.stt', '"gemini-3.5-transcribe"', 'AI: speech-to-text model'),
  ('ai.model.tts', '"gemini-3.8-flash-tts"', 'AI: text-to-speech model'),
  ('ai.model.embedding', '"gemini-embedding-2"', 'AI: embedding model of the material index (changing it re-indexes every material)'),
  ('ai.voice.VI', '"Kore"', 'AI: voice for Vietnamese questions'),
  ('ai.voice.EN', '"Kore"', 'AI: voice for English questions'),
  ('ai.temperature.analysis', '"0.2"', 'AI: temperature of answer analysis / follow-ups (0–1; low = consistent)'),
  ('ai.temperature.generation', '"0.7"', 'AI: temperature of question generation (0–1.2; higher = more varied)'),
  ('ai.temperature.grading', '"0"', 'AI: temperature of grading suggestions (0–1; 0 = same answer, same score)'),
  ('ai.temperature.summary', '"0.3"', 'AI: temperature of the result summary (0–1)'),
  ('ai.timeout.turn_budget_sec', '"11"', 'AI: seconds the AI node has for one answer turn (8–20); the student waits about this long at most'),
  ('ai.timeout.llm_interview_sec', '"6"', 'AI: seconds for the answer analysis inside a turn (3–15)'),
  ('ai.timeout.stt_sec', '"10"', 'AI: seconds for speech-to-text of one answer (5–20)'),
  ('ai.timeout.tts_sec', '"8"', 'AI: seconds to voice one question (4–15); a late follow-up voice comes a moment later'),
  ('ai.timeout.llm_job_sec', '"120"', 'AI: seconds per LLM call of a background job (60–140)'),
  ('ai.interview_reasoning', '"minimal"', 'AI: how much the exam LLM thinks before answering (minimal / low / medium)'),
  ('ai.rag.top_k', '"8"', 'AI: material passages given to question generation (4–12)'),
  ('ai.rag.min_similarity', '"0.55"', 'AI: minimum relevance of a passage (0.3–0.8); below it the topic counts as not covered')
ON CONFLICT ("setting_key") DO NOTHING;
