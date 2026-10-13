-- V9 (D63): the AI node keeps the RAG index (passages + embeddings) of course materials; node 1 stores no vectors.
-- material_chunks only ever held data derived from the stored files, which the AI node rebuilds with "Xử lý lại".
-- Citations keep what the lecturer sees: material, location label and excerpt.
ALTER TABLE "question_sources" DROP CONSTRAINT IF EXISTS "question_sources_chunk_id_fkey";
UPDATE "question_sources" SET "chunk_id" = NULL WHERE "chunk_id" IS NOT NULL;
COMMENT ON COLUMN "question_sources"."chunk_id" IS 'Unused since V9 (D63): the passage lives in the AI node''s index';
DROP TABLE IF EXISTS "material_chunks";

-- AI providers, voices and STT language codes are the AI node's configuration (its .env), not business settings.
DELETE FROM "system_settings"
WHERE "setting_key" IN ('stt.provider', 'stt.language.vi', 'stt.language.en', 'tts.provider', 'tts.voice.vi', 'tts.voice.en');
