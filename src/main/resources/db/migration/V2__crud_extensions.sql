-- ============================================================================
-- CRUD milestone additions — SWD_Docs/requirements/15_CRUD_CATALOGUE.md §4
-- (decisions D28–D34). Mirrors aives.dbml.
-- ============================================================================

-- Users: avatar (D33) and Google linking data (D34)
ALTER TABLE "users"
  ADD COLUMN "google_email" varchar(150),
  ADD COLUMN "google_linked_at" timestamptz,
  ADD COLUMN "avatar_key" varchar(500),
  ADD COLUMN "avatar_updated_at" timestamptz;

ALTER TABLE "courses" ADD COLUMN "updated_at" timestamptz NOT NULL DEFAULT (now());

-- FG7 language & speech configuration
CREATE TABLE "system_settings" (
  "setting_key" varchar(100) PRIMARY KEY,
  "setting_value" jsonb NOT NULL,
  "description" text,
  "updated_by" uuid REFERENCES "users" ("user_id"),
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);
INSERT INTO "system_settings" ("setting_key", "setting_value", "description") VALUES
  ('default_language', '"VI"', 'Default UI / exam language for new courses'),
  ('stt.provider', '"mock"', 'Speech-to-text provider (mock, openai, deepgram, fptai, whisper-sidecar)'),
  ('stt.language.vi', '"vi"', 'STT language code for Vietnamese questions'),
  ('stt.language.en', '"en"', 'STT language code for English questions'),
  ('tts.provider', '"mock"', 'Text-to-speech provider (mock, fptai, openai, edge-sidecar)'),
  ('tts.voice.vi', '""', 'Vietnamese voice name'),
  ('tts.voice.en', '""', 'English voice name');

-- Buổi thi: richer record, results release, retakes, optimistic locking (D29–D32)
ALTER TABLE "viva_exams"
  ADD COLUMN "description" text,
  ADD COLUMN "instructions" text,
  ADD COLUMN "location" varchar(150),
  ADD COLUMN "question_pool_mode" varchar(30) NOT NULL DEFAULT 'COURSE_BANK'
    CHECK ("question_pool_mode" IN ('COURSE_BANK', 'SELECTED')),
  ADD COLUMN "results_released" boolean NOT NULL DEFAULT false,
  ADD COLUMN "results_released_at" timestamptz,
  ADD COLUMN "retake_of_viva_exam_id" uuid REFERENCES "viva_exams" ("viva_exam_id"),
  ADD COLUMN "cancel_reason" text,
  ADD COLUMN "version" int NOT NULL DEFAULT 1,
  ADD COLUMN "updated_at" timestamptz NOT NULL DEFAULT (now());

-- Cấu trúc đề
CREATE TABLE "viva_exam_blueprint_items" (
  "blueprint_item_id" uuid PRIMARY KEY,
  "viva_exam_id" uuid NOT NULL REFERENCES "viva_exams" ("viva_exam_id") ON DELETE CASCADE,
  "topic_id" uuid REFERENCES "topics" ("topic_id"),
  "bloom_level" varchar(30) CHECK ("bloom_level" IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE')),
  "question_count" int NOT NULL CHECK ("question_count" BETWEEN 1 AND 10),
  "sort_order" int NOT NULL DEFAULT 0
);
CREATE INDEX ON "viva_exam_blueprint_items" ("viva_exam_id");

-- Lecturer-selected question pool
CREATE TABLE "viva_exam_questions" (
  "viva_exam_id" uuid NOT NULL REFERENCES "viva_exams" ("viva_exam_id") ON DELETE CASCADE,
  "question_id" uuid NOT NULL REFERENCES "questions" ("question_id"),
  PRIMARY KEY ("viva_exam_id", "question_id")
);

ALTER TABLE "viva_exam_students"
  ADD COLUMN "added_at" timestamptz NOT NULL DEFAULT (now()),
  ADD COLUMN "added_by" uuid REFERENCES "users" ("user_id");

ALTER TABLE "exam_sessions"
  ADD COLUMN "cancel_reason" varchar(30)
    CHECK ("cancel_reason" IN ('NO_SHOW', 'REMOVED_BY_LECTURER', 'EXAM_CANCELLED')),
  ADD COLUMN "consent_recorded_at" timestamptz,
  ADD CONSTRAINT "ck_exam_sessions_cancel_reason" CHECK (("status" = 'CANCELLED') = ("cancel_reason" IS NOT NULL));

-- FG5 phúc khảo
CREATE TABLE "grade_disputes" (
  "dispute_id" uuid PRIMARY KEY,
  "evaluation_id" uuid NOT NULL REFERENCES "grade_evaluations" ("evaluation_id"),
  "student_id" uuid NOT NULL REFERENCES "users" ("user_id"),
  "reason" text NOT NULL,
  "question_grade_ids" jsonb,
  "status" varchar(30) NOT NULL DEFAULT 'OPEN' CHECK ("status" IN ('OPEN', 'RESOLVED', 'REJECTED')),
  "resolution" text,
  "resolved_by" uuid REFERENCES "users" ("user_id"),
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "resolved_at" timestamptz
);
CREATE UNIQUE INDEX "ux_grade_disputes_one_open" ON "grade_disputes" ("evaluation_id") WHERE "status" = 'OPEN';
CREATE INDEX ON "grade_disputes" ("student_id");
