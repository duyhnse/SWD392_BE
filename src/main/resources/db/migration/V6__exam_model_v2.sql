-- ============================================================================
-- Exam model v2 — SWD_Docs/requirements/12_DECISIONS_AND_FIXES.md D41–D52, 15 §2–§4, 16 (AI node).
--   * topics            → chapters (numbered, lecturer CRUD)                       D42
--   * exam_templates    (đề thi: rows chương × Bloom × số câu × thời gian/câu)      D45, D46
--   * viva_exams        (buổi thi) = template + check-in window + roster + rules   D47, D51
--   * exam_sessions     → exam_attempts, created at check-in; questions drawn then  D48, D50
--   * session_questions → attempt_questions with a full content/rubric snapshot    D49
--   * attempt_recordings (video/audio chunks), ai_jobs (AI node async jobs)        D50, D41
-- ============================================================================

-- ---------------------------------------------------------------------------- chapters (D42)
ALTER TABLE "topics" RENAME TO "chapters";
ALTER TABLE "chapters" RENAME COLUMN "topic_id" TO "chapter_id";
ALTER TABLE "chapters" RENAME COLUMN "name" TO "title";
ALTER TABLE "chapters" ADD COLUMN "chapter_no" int;
ALTER TABLE "chapters" ADD COLUMN "updated_at" timestamptz NOT NULL DEFAULT (now());
UPDATE "chapters" c SET "chapter_no" = r.n
FROM (SELECT "chapter_id", row_number() OVER (PARTITION BY "course_id" ORDER BY "sort_order", "created_at", "chapter_id") AS n
      FROM "chapters") r
WHERE r."chapter_id" = c."chapter_id";
ALTER TABLE "chapters" ALTER COLUMN "chapter_no" SET NOT NULL;
ALTER TABLE "chapters" ADD CONSTRAINT "ck_chapters_no" CHECK ("chapter_no" BETWEEN 1 AND 99);
ALTER TABLE "chapters" DROP COLUMN "sort_order";
ALTER INDEX "topics_pkey" RENAME TO "chapters_pkey";
ALTER INDEX "topics_course_id_name_idx" RENAME TO "ux_chapters_course_title";
CREATE UNIQUE INDEX "ux_chapters_course_no" ON "chapters" ("course_id", "chapter_no");

ALTER TABLE "questions" RENAME COLUMN "topic_id" TO "chapter_id";
ALTER INDEX "questions_course_id_topic_id_bloom_level_idx" RENAME TO "ix_questions_course_chapter_bloom";
ALTER TABLE "ai_generation_requests" RENAME COLUMN "topic_id" TO "chapter_id";
COMMENT ON COLUMN "questions"."chapter_id" IS 'Every question belongs to exactly one chapter, chosen by the lecturer — never by AI (D42)';
COMMENT ON COLUMN "questions"."is_locked" IS 'Drawn into an attempt (BR-Q8, D49); change only via successor';

-- ---------------------------------------------------------------------------- exam templates (D45, D46)
CREATE TABLE "exam_templates" (
  "exam_template_id" uuid PRIMARY KEY,
  "course_id" uuid NOT NULL REFERENCES "courses" ("course_id"),
  "title" varchar(200) NOT NULL,
  "description" text,
  "language" varchar(30) NOT NULL CHECK ("language" IN ('VI', 'EN')),
  "max_followups_per_question" int NOT NULL DEFAULT 2 CHECK ("max_followups_per_question" BETWEEN 0 AND 5),
  "max_answer_sec" int NOT NULL DEFAULT 120 CHECK ("max_answer_sec" BETWEEN 30 AND 600),
  "silence_warning_sec" int NOT NULL DEFAULT 15 CHECK ("silence_warning_sec" BETWEEN 5 AND 120),
  "show_question_text" boolean NOT NULL DEFAULT true,
  "pass_score" numeric(4,2) CHECK ("pass_score" BETWEEN 0 AND 10),
  "rubric_id" uuid REFERENCES "rubrics" ("rubric_id"),
  "question_pool_mode" varchar(30) NOT NULL DEFAULT 'COURSE_BANK' CHECK ("question_pool_mode" IN ('COURSE_BANK', 'SELECTED')),
  "is_locked" boolean NOT NULL DEFAULT false,
  "is_archived" boolean NOT NULL DEFAULT false,
  "created_by" uuid NOT NULL REFERENCES "users" ("user_id"),
  "version" int NOT NULL DEFAULT 1,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);
CREATE INDEX "ix_exam_templates_course" ON "exam_templates" ("course_id");
COMMENT ON TABLE "exam_templates" IS 'Đề thi: what is asked and how it is graded, no concrete questions (D45)';
COMMENT ON COLUMN "exam_templates"."rubric_id" IS 'Optional: grade every question of this template with this rubric instead of the question''s own';
COMMENT ON COLUMN "exam_templates"."is_locked" IS 'true once a buổi thi using it is published; duplicate to change (D49)';

CREATE TABLE "exam_template_items" (
  "template_item_id" uuid PRIMARY KEY,
  "exam_template_id" uuid NOT NULL REFERENCES "exam_templates" ("exam_template_id") ON DELETE CASCADE,
  "chapter_id" uuid REFERENCES "chapters" ("chapter_id"),
  "bloom_level" varchar(30) CHECK ("bloom_level" IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE')),
  "question_count" int NOT NULL CHECK ("question_count" BETWEEN 1 AND 10),
  "seconds_per_question" int NOT NULL CHECK ("seconds_per_question" BETWEEN 30 AND 1800),
  "rubric_id" uuid REFERENCES "rubrics" ("rubric_id"),
  "sort_order" int NOT NULL DEFAULT 0
);
CREATE INDEX "ix_exam_template_items_template" ON "exam_template_items" ("exam_template_id");
COMMENT ON COLUMN "exam_template_items"."seconds_per_question" IS 'Time budget of one main question incl. its follow-ups (D46)';
COMMENT ON COLUMN "exam_template_items"."rubric_id" IS 'Optional per-row rubric override (beats the template rubric)';

CREATE TABLE "exam_template_questions" (
  "exam_template_id" uuid NOT NULL REFERENCES "exam_templates" ("exam_template_id") ON DELETE CASCADE,
  "question_id" uuid NOT NULL REFERENCES "questions" ("question_id"),
  PRIMARY KEY ("exam_template_id", "question_id")
);
COMMENT ON TABLE "exam_template_questions" IS 'Lecturer-selected pool when question_pool_mode = SELECTED';

-- One template per existing buổi thi; the template id reuses the exam id (unique across the two tables).
INSERT INTO "exam_templates" ("exam_template_id", "course_id", "title", "description", "language",
                              "max_followups_per_question", "max_answer_sec", "silence_warning_sec",
                              "show_question_text", "question_pool_mode", "is_locked", "created_by",
                              "created_at", "updated_at")
SELECT e."viva_exam_id", e."course_id", left('Đề — ' || e."title", 200), NULL, e."language",
       e."max_followups_per_question", e."answer_time_limit_sec", greatest(5, least(120, e."silence_warning_sec")),
       e."show_question_text", e."question_pool_mode", e."status" <> 'DRAFT', e."created_by", e."created_at", e."updated_at"
FROM "viva_exams" e;

INSERT INTO "exam_template_items" ("template_item_id", "exam_template_id", "chapter_id", "bloom_level",
                                   "question_count", "seconds_per_question", "sort_order")
SELECT b."blueprint_item_id", b."viva_exam_id", b."topic_id", b."bloom_level", b."question_count",
       greatest(30, least(1800, e."time_limit_per_student_sec" / e."main_question_count")), b."sort_order"
FROM "viva_exam_blueprint_items" b JOIN "viva_exams" e ON e."viva_exam_id" = b."viva_exam_id";

INSERT INTO "exam_template_items" ("template_item_id", "exam_template_id", "question_count", "seconds_per_question", "sort_order")
SELECT gen_random_uuid(), e."viva_exam_id", e."main_question_count",
       greatest(30, least(1800, e."time_limit_per_student_sec" / e."main_question_count")), 0
FROM "viva_exams" e
WHERE NOT EXISTS (SELECT 1 FROM "viva_exam_blueprint_items" b WHERE b."viva_exam_id" = e."viva_exam_id");

INSERT INTO "exam_template_questions" ("exam_template_id", "question_id")
SELECT "viva_exam_id", "question_id" FROM "viva_exam_questions";

DROP TABLE "viva_exam_blueprint_items";
DROP TABLE "viva_exam_questions";

-- ---------------------------------------------------------------------------- buổi thi (D47, D51)
ALTER TABLE "viva_exams" ADD COLUMN "exam_template_id" uuid REFERENCES "exam_templates" ("exam_template_id");
UPDATE "viva_exams" SET "exam_template_id" = "viva_exam_id";
ALTER TABLE "viva_exams" ALTER COLUMN "exam_template_id" SET NOT NULL;
CREATE INDEX "ix_viva_exams_template" ON "viva_exams" ("exam_template_id");

ALTER TABLE "viva_exams" DROP CONSTRAINT "ck_viva_exams_config";
ALTER TABLE "viva_exams"
  DROP COLUMN "language",
  DROP COLUMN "main_question_count",
  DROP COLUMN "max_followups_per_question",
  DROP COLUMN "time_limit_per_student_sec",
  DROP COLUMN "answer_time_limit_sec",
  DROP COLUMN "silence_warning_sec",
  DROP COLUMN "topic_ids",
  DROP COLUMN "bloom_levels",
  DROP COLUMN "selection_strategy",
  DROP COLUMN "show_question_text",
  DROP COLUMN "question_pool_mode";
ALTER TABLE "viva_exams" RENAME COLUMN "window_start" TO "checkin_opens_at";
ALTER TABLE "viva_exams" RENAME COLUMN "window_end" TO "checkin_closes_at";
ALTER TABLE "viva_exams"
  ADD COLUMN "max_disconnects" int NOT NULL DEFAULT 3,
  ADD COLUMN "max_frozen_sec" int NOT NULL DEFAULT 180,
  ADD COLUMN "replace_main_after_sec" int NOT NULL DEFAULT 20,
  ADD CONSTRAINT "ck_viva_exams_rules" CHECK (
    "checkin_closes_at" > "checkin_opens_at"
    AND "reconnect_grace_sec" BETWEEN 10 AND 600
    AND "max_disconnects" BETWEEN 0 AND 20
    AND "max_frozen_sec" BETWEEN 0 AND 1800
    AND "replace_main_after_sec" BETWEEN 0 AND 600);
COMMENT ON COLUMN "viva_exams"."checkin_opens_at" IS 'Students may START an attempt from here …';
COMMENT ON COLUMN "viva_exams"."checkin_closes_at" IS '… until here. No fixed end: each attempt ends at its own deadline (D47)';
COMMENT ON COLUMN "viva_exams"."reconnect_grace_sec" IS 'Offline longer than this → attempt INTERRUPTED (D51)';
COMMENT ON COLUMN "viva_exams"."max_disconnects" IS 'More disconnects than this → attempt INTERRUPTED (D51)';
COMMENT ON COLUMN "viva_exams"."max_frozen_sec" IS 'Total frozen (offline) time allowed per attempt (D51)';
COMMENT ON COLUMN "viva_exams"."replace_main_after_sec" IS 'Offline longer than this during a MAIN turn → question replaced when the pool allows (D51)';
COMMENT ON COLUMN "viva_exams"."status" IS 'DRAFT → READY (published) → OPEN (check-in) → CLOSED; CANCELLED';
COMMENT ON COLUMN "viva_exam_students"."seq_no" IS 'Order in the roster (display only since D48)';

-- ---------------------------------------------------------------------------- attempts (D48, D50)
-- Attempts now exist only from check-in: drop the pre-generated sessions nobody started.
DELETE FROM "session_questions" sq USING "exam_sessions" s
WHERE sq."session_id" = s."session_id" AND s."started_at" IS NULL
  AND NOT EXISTS (SELECT 1 FROM "exam_turns" t WHERE t."session_id" = s."session_id")
  AND NOT EXISTS (SELECT 1 FROM "grade_evaluations" g WHERE g."session_id" = s."session_id");
DELETE FROM "exam_sessions" s
WHERE s."started_at" IS NULL
  AND NOT EXISTS (SELECT 1 FROM "session_questions" sq WHERE sq."session_id" = s."session_id")
  AND NOT EXISTS (SELECT 1 FROM "session_events" ev WHERE ev."session_id" = s."session_id");

ALTER TABLE "exam_sessions" RENAME TO "exam_attempts";
ALTER TABLE "exam_attempts" RENAME COLUMN "session_id" TO "attempt_id";
ALTER INDEX "exam_sessions_pkey" RENAME TO "exam_attempts_pkey";
ALTER INDEX "exam_sessions_viva_exam_id_student_id_idx" RENAME TO "ux_exam_attempts_exam_student";
ALTER TABLE "exam_attempts" DROP CONSTRAINT "exam_sessions_status_check";
ALTER TABLE "exam_attempts" DROP CONSTRAINT "exam_sessions_cancel_reason_check";
ALTER TABLE "exam_attempts" DROP CONSTRAINT "ck_exam_sessions_cancel_reason";
ALTER TABLE "exam_attempts" RENAME CONSTRAINT "exam_sessions_end_reason_check" TO "ck_exam_attempts_end_reason";
UPDATE "exam_attempts" SET "status" = 'CANCELLED', "cancel_reason" = 'Migrated: never started'
WHERE "status" = 'SCHEDULED';
ALTER TABLE "exam_attempts" ALTER COLUMN "status" SET DEFAULT 'IN_PROGRESS';
ALTER TABLE "exam_attempts" ALTER COLUMN "cancel_reason" TYPE text;
UPDATE "exam_attempts" SET "started_at" = coalesce("started_at", "created_at");
ALTER TABLE "exam_attempts" ALTER COLUMN "started_at" SET NOT NULL;
ALTER TABLE "exam_attempts" DROP COLUMN "full_audio_storage_key";
ALTER TABLE "exam_attempts"
  ADD COLUMN "selection_seed" bigint,
  ADD COLUMN "disconnect_count" int NOT NULL DEFAULT 0,
  ADD COLUMN "frozen_sec_total" int NOT NULL DEFAULT 0,
  ADD COLUMN "client_info" varchar(300),
  ADD CONSTRAINT "ck_exam_attempts_status" CHECK ("status" IN ('IN_PROGRESS', 'INTERRUPTED', 'COMPLETED', 'CANCELLED')),
  ADD CONSTRAINT "ck_exam_attempts_cancel_reason" CHECK (("status" = 'CANCELLED') = ("cancel_reason" IS NOT NULL)),
  ADD CONSTRAINT "ck_exam_attempts_counters" CHECK ("disconnect_count" >= 0 AND "frozen_sec_total" >= 0);
COMMENT ON TABLE "exam_attempts" IS 'Lượt thi: one per roster student per buổi thi, created at check-in (D48, D50)';
COMMENT ON COLUMN "exam_attempts"."started_at" IS 'Check-in time = start of the attempt';
COMMENT ON COLUMN "exam_attempts"."deadline_at" IS 'Latest possible end: started_at + Σ question budgets + frozen time (D46, D51)';
COMMENT ON COLUMN "exam_attempts"."selection_seed" IS 'Random seed used to draw the questions (reproducible, D48)';
COMMENT ON COLUMN "exam_attempts"."cancel_reason" IS 'Why a lecturer voided the attempt';

ALTER TABLE "session_questions" RENAME TO "attempt_questions";
ALTER TABLE "attempt_questions" RENAME COLUMN "session_question_id" TO "attempt_question_id";
ALTER TABLE "attempt_questions" RENAME COLUMN "session_id" TO "attempt_id";
ALTER INDEX "session_questions_pkey" RENAME TO "attempt_questions_pkey";
DROP INDEX "session_questions_session_id_order_no_idx";
ALTER INDEX "session_questions_session_id_question_id_idx" RENAME TO "ux_attempt_questions_attempt_question";
ALTER TABLE "attempt_questions" DROP CONSTRAINT "session_questions_status_check";
ALTER TABLE "attempt_questions"
  ADD COLUMN "template_item_id" uuid REFERENCES "exam_template_items" ("template_item_id") ON DELETE SET NULL,
  ADD COLUMN "chapter_id" uuid REFERENCES "chapters" ("chapter_id"),
  ADD COLUMN "chapter_no" int,
  ADD COLUMN "chapter_title" varchar(150),
  ADD COLUMN "bloom_level" varchar(30) CHECK ("bloom_level" IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE')),
  ADD COLUMN "language" varchar(30) CHECK ("language" IN ('VI', 'EN')),
  ADD COLUMN "content" text,
  ADD COLUMN "reference_answer" text,
  ADD COLUMN "question_version" int,
  ADD COLUMN "rubric_snapshot" jsonb,
  ADD COLUMN "time_budget_sec" int,
  ADD COLUMN "time_used_sec" int NOT NULL DEFAULT 0,
  ADD COLUMN "started_at" timestamptz,
  ADD COLUMN "ended_at" timestamptz,
  ADD COLUMN "replaces_attempt_question_id" uuid REFERENCES "attempt_questions" ("attempt_question_id"),
  ADD COLUMN "void_reason" varchar(30);

UPDATE "attempt_questions" aq SET
  "chapter_id" = q."chapter_id", "chapter_no" = c."chapter_no", "chapter_title" = c."title",
  "bloom_level" = q."bloom_level", "language" = q."language", "content" = q."content",
  "reference_answer" = q."reference_answer", "question_version" = q."version",
  "rubric_snapshot" = coalesce((
    SELECT jsonb_build_object('rubricId', r."rubric_id", 'name', r."name", 'criteria', coalesce((
      SELECT jsonb_agg(jsonb_build_object('criterionId', rc."criterion_id", 'name', rc."name",
                                          'description', rc."description", 'maxScore', rc."max_score",
                                          'weightPercent', rc."weight_percent", 'sortOrder', rc."sort_order")
                       ORDER BY rc."sort_order", rc."name")
      FROM "rubric_criteria" rc WHERE rc."rubric_id" = r."rubric_id"), '[]'::jsonb))
    FROM "rubrics" r WHERE r."rubric_id" = q."rubric_id"), '{"criteria": []}'::jsonb),
  "time_budget_sec" = greatest(30, least(1800, coalesce(
    (SELECT max(i."seconds_per_question") FROM "exam_template_items" i
     JOIN "exam_attempts" a ON a."attempt_id" = aq."attempt_id"
     JOIN "viva_exams" e ON e."viva_exam_id" = a."viva_exam_id"
     WHERE i."exam_template_id" = e."exam_template_id"), 180)))
FROM "questions" q JOIN "chapters" c ON c."chapter_id" = q."chapter_id"
WHERE q."question_id" = aq."question_id";

ALTER TABLE "attempt_questions"
  ALTER COLUMN "chapter_id" SET NOT NULL,
  ALTER COLUMN "chapter_no" SET NOT NULL,
  ALTER COLUMN "chapter_title" SET NOT NULL,
  ALTER COLUMN "language" SET NOT NULL,
  ALTER COLUMN "content" SET NOT NULL,
  ALTER COLUMN "rubric_snapshot" SET NOT NULL,
  ALTER COLUMN "time_budget_sec" SET NOT NULL,
  ADD CONSTRAINT "ck_attempt_questions_status"
    CHECK ("status" IN ('PENDING', 'IN_PROGRESS', 'DONE', 'SKIPPED', 'NOT_REACHED', 'VOIDED')),
  ADD CONSTRAINT "ck_attempt_questions_void" CHECK (("status" = 'VOIDED') = ("void_reason" IS NOT NULL)),
  ADD CONSTRAINT "ck_attempt_questions_time" CHECK ("time_budget_sec" BETWEEN 30 AND 1800 AND "time_used_sec" >= 0);
CREATE UNIQUE INDEX "ux_attempt_questions_order" ON "attempt_questions" ("attempt_id", "order_no") WHERE "status" <> 'VOIDED';
COMMENT ON TABLE "attempt_questions" IS 'Questions drawn for one attempt with a snapshot of everything grading needs (D49)';
COMMENT ON COLUMN "attempt_questions"."rubric_snapshot" IS '{rubricId, name, criteria:[{criterionId, name, description, maxScore, weightPercent, sortOrder}]} at check-in';
COMMENT ON COLUMN "attempt_questions"."time_used_sec" IS 'Answering time charged so far (playback, AI processing and frozen time excluded, D46)';
COMMENT ON COLUMN "attempt_questions"."void_reason" IS 'REPLACED_AFTER_DISCONNECT | VOIDED_BY_LECTURER';

ALTER TABLE "exam_turns" RENAME COLUMN "session_id" TO "attempt_id";
ALTER TABLE "exam_turns" RENAME COLUMN "session_question_id" TO "attempt_question_id";
ALTER INDEX "exam_turns_session_id_turn_order_idx" RENAME TO "ux_exam_turns_attempt_order";
ALTER INDEX "exam_turns_session_question_id_followup_index_idx" RENAME TO "ux_exam_turns_thread_followup";
ALTER TABLE "exam_turns"
  ADD COLUMN "question_audio_ms" int,
  ADD COLUMN "answer_counted_ms" int;
COMMENT ON COLUMN "exam_turns"."question_audio_ms" IS 'TTS length = playback allowance not charged to the student (D46)';
COMMENT ON COLUMN "exam_turns"."answer_counted_ms" IS 'Answering time charged to the thread budget for this turn (D46)';

ALTER TABLE "session_events" RENAME TO "attempt_events";
ALTER TABLE "attempt_events" RENAME COLUMN "session_id" TO "attempt_id";
ALTER INDEX "session_events_pkey" RENAME TO "attempt_events_pkey";
CREATE INDEX "ix_attempt_events_attempt" ON "attempt_events" ("attempt_id", "created_at");
COMMENT ON COLUMN "attempt_events"."type" IS 'CHECKED_IN, QUESTION_ASKED, ANSWER_SUBMITTED, STT_FAILED, LLM_FAILED, TTS_FAILED, SILENCE_WARNING, DISCONNECTED, RECONNECTED, QUESTION_REPLACED, INTERRUPTED, LECTURER_SKIPPED, LECTURER_ENDED, LECTURER_RESUMED, TIME_UP, ATTEMPT_COMPLETED';

ALTER TABLE "grade_evaluations" RENAME COLUMN "session_id" TO "attempt_id";
ALTER TABLE "grade_evaluations" RENAME CONSTRAINT "grade_evaluations_session_id_key" TO "ux_grade_evaluations_attempt";
ALTER TABLE "question_grades" RENAME COLUMN "session_question_id" TO "attempt_question_id";
ALTER INDEX "question_grades_evaluation_id_session_question_id_idx" RENAME TO "ux_question_grades_thread";

-- ---------------------------------------------------------------------------- recordings (FG5, D50)
CREATE TABLE "attempt_recordings" (
  "recording_id" uuid PRIMARY KEY,
  "attempt_id" uuid NOT NULL REFERENCES "exam_attempts" ("attempt_id"),
  "kind" varchar(30) NOT NULL CHECK ("kind" IN ('CAMERA', 'SCREEN', 'AUDIO')),
  "chunk_index" int NOT NULL CHECK ("chunk_index" >= 0),
  "storage_key" varchar(500) NOT NULL,
  "content_type" varchar(100) NOT NULL,
  "size_bytes" bigint NOT NULL,
  "duration_ms" int,
  "sha256" varchar(64) NOT NULL,
  "client_started_at" timestamptz,
  "uploaded_at" timestamptz NOT NULL DEFAULT (now())
);
CREATE UNIQUE INDEX "ux_attempt_recordings_chunk" ON "attempt_recordings" ("attempt_id", "kind", "chunk_index");
COMMENT ON TABLE "attempt_recordings" IS 'Student camera (video + mic) / screen recording of an attempt in chunks of ~30 s (FG5, D50)';

-- ---------------------------------------------------------------------------- AI node jobs (D41, 16)
CREATE TABLE "ai_jobs" (
  "job_id" uuid PRIMARY KEY,
  "job_type" varchar(40) NOT NULL CHECK ("job_type" IN ('GRADE_THREAD', 'SUMMARIZE_EVALUATION', 'INDEX_MATERIAL', 'GENERATE_QUESTIONS')),
  "subject_type" varchar(40) NOT NULL,
  "subject_id" uuid NOT NULL,
  "status" varchar(30) NOT NULL DEFAULT 'QUEUED' CHECK ("status" IN ('QUEUED', 'SUBMITTED', 'SUCCEEDED', 'FAILED', 'TIMED_OUT')),
  "request" jsonb NOT NULL,
  "result" jsonb,
  "error_code" varchar(60),
  "error_message" text,
  "submit_attempts" int NOT NULL DEFAULT 0,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "submitted_at" timestamptz,
  "finished_at" timestamptz
);
CREATE INDEX "ix_ai_jobs_subject" ON "ai_jobs" ("subject_type", "subject_id");
CREATE INDEX "ix_ai_jobs_open" ON "ai_jobs" ("status", "created_at") WHERE "status" IN ('QUEUED', 'SUBMITTED');
COMMENT ON TABLE "ai_jobs" IS 'Asynchronous work sent to the AI node; the result arrives by signed callback or polling (16 §4)';

-- ---------------------------------------------------------------------------- settings (D46)
INSERT INTO "system_settings" ("setting_key", "setting_value", "description") VALUES
  ('exam.seconds.REMEMBER', '"120"', 'Default time budget (s) of a REMEMBER main question incl. follow-ups'),
  ('exam.seconds.UNDERSTAND', '"180"', 'Default time budget (s) of an UNDERSTAND main question incl. follow-ups'),
  ('exam.seconds.APPLY', '"240"', 'Default time budget (s) of an APPLY main question incl. follow-ups'),
  ('exam.seconds.ANALYZE', '"300"', 'Default time budget (s) of an ANALYZE main question incl. follow-ups'),
  ('exam.seconds.ANY', '"180"', 'Default time budget (s) of a template row without a Bloom level')
ON CONFLICT ("setting_key") DO NOTHING;
