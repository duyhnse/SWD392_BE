-- ============================================================================
-- AIVES baseline schema. Source of truth: SWD_Docs/requirements/aives.dbml
-- Generated from the DBML, then: enums -> varchar + CHECK (D16), extra CHECKs,
-- partial/vector indexes and the roles seed added by hand.
-- Never edit this file after it is merged: add V2__..., V3__... instead.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE "roles" (
  "role_id" smallint PRIMARY KEY,
  "role_name" varchar(20) UNIQUE NOT NULL,
  "role_description" text
);

CREATE TABLE "users" (
  "user_id" uuid PRIMARY KEY,
  "role_id" smallint NOT NULL,
  "full_name" varchar(100) NOT NULL,
  "email" varchar(100) UNIQUE NOT NULL,
  "hashed_password" varchar(255) NOT NULL,
  "student_code" varchar(20) UNIQUE,
  "preferred_language" varchar(30) NOT NULL DEFAULT 'VI' CHECK ("preferred_language" IN ('VI', 'EN')),
  "is_active" boolean NOT NULL DEFAULT true,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "courses" (
  "course_id" uuid PRIMARY KEY,
  "code" varchar(20) UNIQUE NOT NULL,
  "name" varchar(150) NOT NULL,
  "description" text,
  "default_language" varchar(30) NOT NULL DEFAULT 'VI' CHECK ("default_language" IN ('VI', 'EN')),
  "is_active" boolean NOT NULL DEFAULT true,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "course_lecturers" (
  "course_id" uuid,
  "lecturer_id" uuid,
  "assigned_at" timestamptz NOT NULL DEFAULT (now()),
  PRIMARY KEY ("course_id", "lecturer_id")
);

CREATE TABLE "notifications" (
  "notification_id" uuid PRIMARY KEY,
  "user_id" uuid NOT NULL,
  "type" varchar(50) NOT NULL,
  "title" varchar(200) NOT NULL,
  "body" text,
  "payload" jsonb,
  "read_at" timestamptz,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "audit_events" (
  "audit_event_id" uuid PRIMARY KEY,
  "actor_id" uuid,
  "action" varchar(60) NOT NULL,
  "entity_type" varchar(40) NOT NULL,
  "entity_id" uuid NOT NULL,
  "data" jsonb,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "topics" (
  "topic_id" uuid PRIMARY KEY,
  "course_id" uuid NOT NULL,
  "name" varchar(150) NOT NULL,
  "description" text,
  "sort_order" int NOT NULL DEFAULT 0,
  "created_by" uuid NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "course_terms" (
  "course_term_id" uuid PRIMARY KEY,
  "course_id" uuid NOT NULL,
  "term" varchar(100) NOT NULL
);

CREATE TABLE "course_materials" (
  "material_id" uuid PRIMARY KEY,
  "course_id" uuid NOT NULL,
  "file_name" varchar(255) NOT NULL,
  "content_type" varchar(100) NOT NULL,
  "size_bytes" bigint NOT NULL,
  "storage_key" varchar(500) NOT NULL,
  "status" varchar(30) NOT NULL DEFAULT 'UPLOADED' CHECK ("status" IN ('UPLOADED', 'PROCESSING', 'INDEXED', 'FAILED')),
  "page_count" int,
  "error_message" text,
  "uploaded_by" uuid NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "indexed_at" timestamptz
);

CREATE TABLE "material_chunks" (
  "chunk_id" uuid PRIMARY KEY,
  "material_id" uuid NOT NULL,
  "chunk_index" int NOT NULL,
  "content" text NOT NULL,
  "location_label" varchar(100) NOT NULL,
  "page_from" int,
  "page_to" int,
  "token_count" int,
  "embedding" vector(1536)
);

CREATE TABLE "rubrics" (
  "rubric_id" uuid PRIMARY KEY,
  "course_id" uuid NOT NULL,
  "name" varchar(150) NOT NULL,
  "description" text,
  "is_locked" boolean NOT NULL DEFAULT false,
  "created_by" uuid NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "rubric_criteria" (
  "criterion_id" uuid PRIMARY KEY,
  "rubric_id" uuid NOT NULL,
  "name" varchar(150) NOT NULL,
  "description" text NOT NULL,
  "max_score" numeric(5,2) NOT NULL,
  "weight_percent" numeric(5,2) NOT NULL,
  "sort_order" int NOT NULL DEFAULT 0
);

CREATE TABLE "ai_generation_requests" (
  "generation_request_id" uuid PRIMARY KEY,
  "course_id" uuid NOT NULL,
  "topic_id" uuid NOT NULL,
  "requested_by" uuid NOT NULL,
  "material_ids" jsonb NOT NULL,
  "requested_count" int NOT NULL,
  "bloom_levels" jsonb NOT NULL,
  "focus_text" text,
  "language" varchar(30) NOT NULL CHECK ("language" IN ('VI', 'EN')),
  "status" varchar(30) NOT NULL DEFAULT 'PENDING' CHECK ("status" IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'NO_CONTEXT', 'FAILED')),
  "generated_count" int NOT NULL DEFAULT 0,
  "message" text,
  "llm_model" varchar(100),
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "finished_at" timestamptz
);

CREATE TABLE "questions" (
  "question_id" uuid PRIMARY KEY,
  "course_id" uuid NOT NULL,
  "topic_id" uuid NOT NULL,
  "content" text NOT NULL,
  "reference_answer" text,
  "bloom_level" varchar(30) CHECK ("bloom_level" IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE')),
  "language" varchar(30) NOT NULL CHECK ("language" IN ('VI', 'EN')),
  "status" varchar(30) NOT NULL DEFAULT 'DRAFT' CHECK ("status" IN ('DRAFT', 'PUBLISHED', 'DISCARDED', 'RETIRED')),
  "origin" varchar(30) NOT NULL CHECK ("origin" IN ('MANUAL', 'IMPORTED', 'AI_GENERATED')),
  "rubric_id" uuid,
  "ai_original_content" text,
  "ai_original_reference_answer" text,
  "ai_suggested_bloom" varchar(30) CHECK ("ai_suggested_bloom" IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE')),
  "ai_suggested_rubric" jsonb,
  "generation_request_id" uuid,
  "owner_id" uuid NOT NULL,
  "published_by" uuid,
  "published_at" timestamptz,
  "discarded_at" timestamptz,
  "is_locked" boolean NOT NULL DEFAULT false,
  "supersedes_question_id" uuid,
  "version" int NOT NULL DEFAULT 1,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "question_sources" (
  "question_source_id" uuid PRIMARY KEY,
  "question_id" uuid NOT NULL,
  "material_id" uuid NOT NULL,
  "chunk_id" uuid,
  "location_label" varchar(100) NOT NULL,
  "excerpt" text
);

CREATE TABLE "tags" (
  "tag_id" uuid PRIMARY KEY,
  "course_id" uuid NOT NULL,
  "name" varchar(50) NOT NULL
);

CREATE TABLE "question_tags" (
  "question_id" uuid,
  "tag_id" uuid,
  PRIMARY KEY ("question_id", "tag_id")
);

CREATE TABLE "viva_exams" (
  "viva_exam_id" uuid PRIMARY KEY,
  "course_id" uuid NOT NULL,
  "title" varchar(200) NOT NULL,
  "created_by" uuid NOT NULL,
  "examiner_id" uuid NOT NULL,
  "window_start" timestamptz NOT NULL,
  "window_end" timestamptz NOT NULL,
  "language" varchar(30) NOT NULL CHECK ("language" IN ('VI', 'EN')),
  "main_question_count" int NOT NULL,
  "max_followups_per_question" int NOT NULL,
  "time_limit_per_student_sec" int NOT NULL,
  "answer_time_limit_sec" int NOT NULL DEFAULT 120,
  "silence_warning_sec" int NOT NULL DEFAULT 15,
  "reconnect_grace_sec" int NOT NULL DEFAULT 60,
  "topic_ids" jsonb,
  "bloom_levels" jsonb,
  "selection_strategy" varchar(30) NOT NULL DEFAULT 'RANDOM_BALANCED' CHECK ("selection_strategy" IN ('RANDOM_BALANCED', 'ADAPTIVE')),
  "show_question_text" boolean NOT NULL DEFAULT true,
  "status" varchar(30) NOT NULL DEFAULT 'DRAFT' CHECK ("status" IN ('DRAFT', 'READY', 'OPEN', 'CLOSED', 'CANCELLED')),
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "viva_exam_students" (
  "viva_exam_id" uuid,
  "student_id" uuid,
  "seq_no" int NOT NULL,
  PRIMARY KEY ("viva_exam_id", "student_id")
);

CREATE TABLE "exam_sessions" (
  "session_id" uuid PRIMARY KEY,
  "viva_exam_id" uuid NOT NULL,
  "course_id" uuid NOT NULL,
  "student_id" uuid NOT NULL,
  "examiner_id" uuid NOT NULL,
  "status" varchar(30) NOT NULL DEFAULT 'SCHEDULED' CHECK ("status" IN ('SCHEDULED', 'IN_PROGRESS', 'INTERRUPTED', 'COMPLETED', 'CANCELLED')),
  "end_reason" varchar(30) CHECK ("end_reason" IN ('ALL_QUESTIONS_DONE', 'TIME_UP', 'ENDED_BY_LECTURER')),
  "started_at" timestamptz,
  "deadline_at" timestamptz,
  "ended_at" timestamptz,
  "last_seen_at" timestamptz,
  "disconnected_since" timestamptz,
  "full_audio_storage_key" varchar(500),
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "session_questions" (
  "session_question_id" uuid PRIMARY KEY,
  "session_id" uuid NOT NULL,
  "question_id" uuid NOT NULL,
  "order_no" int NOT NULL,
  "status" varchar(30) NOT NULL DEFAULT 'PENDING' CHECK ("status" IN ('PENDING', 'IN_PROGRESS', 'DONE', 'SKIPPED', 'NOT_REACHED'))
);

CREATE TABLE "exam_turns" (
  "turn_id" uuid PRIMARY KEY,
  "session_id" uuid NOT NULL,
  "session_question_id" uuid NOT NULL,
  "question_id" uuid NOT NULL,
  "parent_turn_id" uuid,
  "turn_type" varchar(30) NOT NULL CHECK ("turn_type" IN ('MAIN', 'FOLLOW_UP')),
  "turn_order" int NOT NULL,
  "followup_index" int NOT NULL DEFAULT 0,
  "question_text" text NOT NULL,
  "question_audio_key" varchar(500),
  "language" varchar(30) NOT NULL CHECK ("language" IN ('VI', 'EN')),
  "status" varchar(30) NOT NULL DEFAULT 'ASKED' CHECK ("status" IN ('ASKED', 'PROCESSING', 'ANSWERED', 'NO_ANSWER', 'TRANSCRIPTION_FAILED', 'SKIPPED_BY_LECTURER', 'CANCELLED')),
  "asked_at" timestamptz NOT NULL,
  "answer_submitted_at" timestamptz,
  "response_duration_sec" int,
  "answer_audio_key" varchar(500),
  "student_transcript" text,
  "ai_analysis" jsonb,
  "followup_decision" varchar(30) CHECK ("followup_decision" IN ('FOLLOW_UP', 'NEXT_LIMIT', 'NEXT_TIME', 'NEXT_COMPLETE', 'NEXT_WRONG', 'NEXT_AI_ERROR', 'NEXT_NO_ANSWER')),
  "processing_ms" int,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "session_events" (
  "event_id" uuid PRIMARY KEY,
  "session_id" uuid NOT NULL,
  "turn_id" uuid,
  "type" varchar(40) NOT NULL,
  "actor_id" uuid,
  "payload" jsonb,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "grade_evaluations" (
  "evaluation_id" uuid PRIMARY KEY,
  "session_id" uuid UNIQUE NOT NULL,
  "status" varchar(30) NOT NULL DEFAULT 'PENDING_AI' CHECK ("status" IN ('PENDING_AI', 'AWAITING_REVIEW', 'CONFIRMED', 'DISPUTED')),
  "ai_total_score" numeric(5,2),
  "final_total_score" numeric(5,2),
  "ai_general_feedback" text,
  "lecturer_comment" text,
  "confirmed_by" uuid,
  "confirmed_at" timestamptz,
  "llm_model" varchar(100),
  "version" int NOT NULL DEFAULT 1,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "question_grades" (
  "question_grade_id" uuid PRIMARY KEY,
  "evaluation_id" uuid NOT NULL,
  "session_question_id" uuid NOT NULL,
  "question_id" uuid NOT NULL,
  "rubric_snapshot" jsonb NOT NULL,
  "status" varchar(30) NOT NULL DEFAULT 'PENDING_AI' CHECK ("status" IN ('PENDING_AI', 'AI_SCORED', 'AI_FAILED', 'MISSING_DATA', 'NOT_ASKED', 'CONFIRMED')),
  "ai_score" numeric(5,2),
  "final_score" numeric(5,2),
  "include_in_total" boolean NOT NULL DEFAULT true,
  "ai_strengths" jsonb,
  "ai_weaknesses" jsonb,
  "ai_missing_points" jsonb,
  "ai_feedback" text,
  "ai_error" text,
  "signals" jsonb,
  "lecturer_comment" text,
  "confirmed_by" uuid,
  "confirmed_at" timestamptz
);

CREATE TABLE "criterion_scores" (
  "criterion_score_id" uuid PRIMARY KEY,
  "question_grade_id" uuid NOT NULL,
  "criterion_id" uuid NOT NULL,
  "max_score" numeric(5,2) NOT NULL,
  "weight_percent" numeric(5,2) NOT NULL,
  "ai_score" numeric(5,2),
  "ai_justification" text,
  "final_score" numeric(5,2)
);

CREATE TABLE "grade_change_log" (
  "change_id" uuid PRIMARY KEY,
  "evaluation_id" uuid NOT NULL,
  "question_grade_id" uuid,
  "criterion_score_id" uuid,
  "field" varchar(40) NOT NULL,
  "old_value" text,
  "new_value" text,
  "changed_by" uuid NOT NULL,
  "changed_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE UNIQUE INDEX ON "topics" ("course_id", "name");

CREATE UNIQUE INDEX ON "course_terms" ("course_id", "term");

CREATE UNIQUE INDEX ON "material_chunks" ("material_id", "chunk_index");

CREATE UNIQUE INDEX ON "rubrics" ("course_id", "name");

CREATE INDEX ON "questions" ("course_id", "status");

CREATE INDEX ON "questions" ("course_id", "topic_id", "bloom_level");

CREATE UNIQUE INDEX ON "tags" ("course_id", "name");

CREATE UNIQUE INDEX ON "exam_sessions" ("viva_exam_id", "student_id");

CREATE UNIQUE INDEX ON "session_questions" ("session_id", "order_no");

CREATE UNIQUE INDEX ON "session_questions" ("session_id", "question_id");

CREATE UNIQUE INDEX ON "exam_turns" ("session_id", "turn_order");

CREATE UNIQUE INDEX ON "exam_turns" ("session_question_id", "followup_index");

CREATE UNIQUE INDEX ON "question_grades" ("evaluation_id", "session_question_id");

CREATE UNIQUE INDEX ON "criterion_scores" ("question_grade_id", "criterion_id");

COMMENT ON COLUMN "roles"."role_id" IS '1 ADMIN, 2 LECTURER, 3 STUDENT — seeded, fixed (mirrors Java enum Role)';

COMMENT ON COLUMN "users"."email" IS 'stored lowercase; login id and JWT subject';

COMMENT ON COLUMN "users"."hashed_password" IS 'BCrypt';

COMMENT ON COLUMN "users"."student_code" IS 'e.g. SE190180; STUDENT only';

COMMENT ON COLUMN "users"."is_active" IS 'inactive users cannot log in';

COMMENT ON COLUMN "courses"."code" IS 'e.g. SWD392';

COMMENT ON TABLE "course_lecturers" IS 'PRE: a lecturer may act on a course only if assigned here';

COMMENT ON COLUMN "notifications"."type" IS 'QUESTIONS_PUBLISHED, GRADING_READY, SESSION_INTERRUPTED, ...';

COMMENT ON COLUMN "audit_events"."action" IS 'QUESTION_PUBLISHED, GRADE_CONFIRMED, ...';

COMMENT ON COLUMN "course_terms"."term" IS 'Hotword for STT, e.g. "microservice", "SOLID"';

COMMENT ON TABLE "material_chunks" IS 'HNSW index on embedding using vector_cosine_ops';

COMMENT ON COLUMN "material_chunks"."location_label" IS '"Page 12", "Slide 7", "Section 3.2"';

COMMENT ON COLUMN "material_chunks"."embedding" IS 'pgvector; dimension must match EmbeddingPort';

COMMENT ON COLUMN "rubrics"."is_locked" IS 'true once any question using it is locked (BR-Q8)';

COMMENT ON COLUMN "rubric_criteria"."name" IS 'e.g. "Conceptual accuracy"';

COMMENT ON COLUMN "rubric_criteria"."description" IS 'What full marks look like';

COMMENT ON COLUMN "rubric_criteria"."max_score" IS '> 0, e.g. 10';

COMMENT ON COLUMN "rubric_criteria"."weight_percent" IS 'Sum over rubric = 100 (BR-Q3)';

COMMENT ON COLUMN "ai_generation_requests"."material_ids" IS 'array of material uuids';

COMMENT ON COLUMN "ai_generation_requests"."requested_count" IS '1..20';

COMMENT ON COLUMN "ai_generation_requests"."bloom_levels" IS 'array of bloom_level';

COMMENT ON COLUMN "ai_generation_requests"."focus_text" IS 'optional extra instruction from lecturer';

COMMENT ON COLUMN "ai_generation_requests"."message" IS 'warning (NO_CONTEXT) or error';

COMMENT ON COLUMN "questions"."reference_answer" IS 'Expected key points; required to publish';

COMMENT ON COLUMN "questions"."bloom_level" IS 'required to publish';

COMMENT ON COLUMN "questions"."rubric_id" IS 'required to publish';

COMMENT ON COLUMN "questions"."ai_original_content" IS 'AI wording as generated (BR-Q5)';

COMMENT ON COLUMN "questions"."ai_suggested_rubric" IS '{name, criteria:[{name,description,max_score,weight_percent}]} — not a real rubric until lecturer accepts';

COMMENT ON COLUMN "questions"."owner_id" IS 'Creator; only owner edits (BR-Q6)';

COMMENT ON COLUMN "questions"."is_locked" IS 'Used in a completed session (BR-Q8)';

COMMENT ON COLUMN "questions"."version" IS 'optimistic locking (@Version)';

COMMENT ON COLUMN "question_sources"."excerpt" IS '<= 500 chars of the cited passage';

COMMENT ON COLUMN "viva_exams"."examiner_id" IS 'Lecturer who monitors & grades by default';

COMMENT ON COLUMN "viva_exams"."main_question_count" IS '1..10';

COMMENT ON COLUMN "viva_exams"."max_followups_per_question" IS '0..5';

COMMENT ON COLUMN "viva_exams"."time_limit_per_student_sec" IS '120..3600';

COMMENT ON COLUMN "viva_exams"."topic_ids" IS 'null = all topics';

COMMENT ON COLUMN "viva_exams"."bloom_levels" IS 'null = all levels';

COMMENT ON COLUMN "viva_exam_students"."seq_no" IS 'Examination order — used for anti-overlap';

COMMENT ON COLUMN "exam_sessions"."course_id" IS 'denormalised for authz queries';

COMMENT ON COLUMN "exam_sessions"."deadline_at" IS 'started_at + time limit + accumulated disconnect extensions';

COMMENT ON COLUMN "exam_sessions"."last_seen_at" IS 'heartbeat from student client';

COMMENT ON COLUMN "exam_sessions"."full_audio_storage_key" IS 'P2 (FG5)';

COMMENT ON COLUMN "session_questions"."order_no" IS '1..N, ascending Bloom order';

COMMENT ON COLUMN "exam_turns"."session_question_id" IS 'Thread this turn belongs to';

COMMENT ON COLUMN "exam_turns"."question_id" IS 'The MAIN bank question of the thread — also for follow-ups (rubric lookup)';

COMMENT ON COLUMN "exam_turns"."parent_turn_id" IS 'NULL for MAIN; previous turn in the thread for FOLLOW_UP';

COMMENT ON COLUMN "exam_turns"."turn_order" IS 'Global sequence in session, 1..n';

COMMENT ON COLUMN "exam_turns"."followup_index" IS '0 for MAIN, 1..max for follow-ups';

COMMENT ON COLUMN "exam_turns"."question_audio_key" IS 'TTS output; NULL → client uses browser speech';

COMMENT ON COLUMN "exam_turns"."response_duration_sec" IS 'Recorded audio length';

COMMENT ON COLUMN "exam_turns"."student_transcript" IS 'NULL until STT; "[NO_ANSWER]" for silence';

COMMENT ON COLUMN "exam_turns"."ai_analysis" IS 'Raw validated LLM analysis (coverage, flags, missing points)';

COMMENT ON COLUMN "exam_turns"."processing_ms" IS 'Submit → next question ready (NFR-01)';

COMMENT ON COLUMN "session_events"."type" IS 'SESSION_STARTED, QUESTION_ASKED, ANSWER_SUBMITTED, STT_FAILED, LLM_FAILED, TTS_FAILED, SILENCE_WARNING, DISCONNECTED, RECONNECTED, INTERRUPTED, LECTURER_SKIPPED, LECTURER_ENDED, LECTURER_RESUMED, TIME_UP, SESSION_COMPLETED';

COMMENT ON COLUMN "session_events"."actor_id" IS 'NULL = system';

COMMENT ON COLUMN "grade_evaluations"."ai_total_score" IS '0..10, mean over gradable threads with AI score';

COMMENT ON COLUMN "grade_evaluations"."final_total_score" IS '0..10, computed at confirm';

COMMENT ON COLUMN "question_grades"."rubric_snapshot" IS 'Rubric + criteria copied at grading time (evidence)';

COMMENT ON COLUMN "question_grades"."ai_score" IS '0..10 computed from criterion ai scores';

COMMENT ON COLUMN "question_grades"."final_score" IS '0..10 computed from criterion final scores';

COMMENT ON COLUMN "question_grades"."include_in_total" IS 'false by default for SKIPPED threads';

COMMENT ON COLUMN "question_grades"."ai_strengths" IS 'string[]';

COMMENT ON COLUMN "question_grades"."signals" IS '{total_answer_sec, words, words_per_min, followups_used, no_answer_turns} — display only';

COMMENT ON TABLE "criterion_scores" IS 'Replaces old turn_evaluation_details. Grain = thread x criterion, not turn x criterion.';

COMMENT ON COLUMN "criterion_scores"."max_score" IS 'snapshot';

COMMENT ON COLUMN "criterion_scores"."weight_percent" IS 'snapshot';

COMMENT ON COLUMN "grade_change_log"."field" IS 'final_score, lecturer_comment, include_in_total, status';

ALTER TABLE "users" ADD FOREIGN KEY ("role_id") REFERENCES "roles" ("role_id");

ALTER TABLE "course_lecturers" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "course_lecturers" ADD FOREIGN KEY ("lecturer_id") REFERENCES "users" ("user_id");

ALTER TABLE "notifications" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("user_id");

ALTER TABLE "audit_events" ADD FOREIGN KEY ("actor_id") REFERENCES "users" ("user_id");

ALTER TABLE "topics" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "topics" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("user_id");

ALTER TABLE "course_terms" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "course_materials" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "course_materials" ADD FOREIGN KEY ("uploaded_by") REFERENCES "users" ("user_id");

ALTER TABLE "material_chunks" ADD FOREIGN KEY ("material_id") REFERENCES "course_materials" ("material_id");

ALTER TABLE "rubrics" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "rubrics" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("user_id");

ALTER TABLE "rubric_criteria" ADD FOREIGN KEY ("rubric_id") REFERENCES "rubrics" ("rubric_id");

ALTER TABLE "ai_generation_requests" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "ai_generation_requests" ADD FOREIGN KEY ("topic_id") REFERENCES "topics" ("topic_id");

ALTER TABLE "ai_generation_requests" ADD FOREIGN KEY ("requested_by") REFERENCES "users" ("user_id");

ALTER TABLE "questions" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "questions" ADD FOREIGN KEY ("topic_id") REFERENCES "topics" ("topic_id");

ALTER TABLE "questions" ADD FOREIGN KEY ("rubric_id") REFERENCES "rubrics" ("rubric_id");

ALTER TABLE "questions" ADD FOREIGN KEY ("generation_request_id") REFERENCES "ai_generation_requests" ("generation_request_id");

ALTER TABLE "questions" ADD FOREIGN KEY ("owner_id") REFERENCES "users" ("user_id");

ALTER TABLE "questions" ADD FOREIGN KEY ("published_by") REFERENCES "users" ("user_id");

ALTER TABLE "questions" ADD FOREIGN KEY ("supersedes_question_id") REFERENCES "questions" ("question_id");

ALTER TABLE "question_sources" ADD FOREIGN KEY ("question_id") REFERENCES "questions" ("question_id");

ALTER TABLE "question_sources" ADD FOREIGN KEY ("material_id") REFERENCES "course_materials" ("material_id");

ALTER TABLE "question_sources" ADD FOREIGN KEY ("chunk_id") REFERENCES "material_chunks" ("chunk_id");

ALTER TABLE "tags" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "question_tags" ADD FOREIGN KEY ("question_id") REFERENCES "questions" ("question_id");

ALTER TABLE "question_tags" ADD FOREIGN KEY ("tag_id") REFERENCES "tags" ("tag_id");

ALTER TABLE "viva_exams" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "viva_exams" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("user_id");

ALTER TABLE "viva_exams" ADD FOREIGN KEY ("examiner_id") REFERENCES "users" ("user_id");

ALTER TABLE "viva_exam_students" ADD FOREIGN KEY ("viva_exam_id") REFERENCES "viva_exams" ("viva_exam_id");

ALTER TABLE "viva_exam_students" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("user_id");

ALTER TABLE "exam_sessions" ADD FOREIGN KEY ("viva_exam_id") REFERENCES "viva_exams" ("viva_exam_id");

ALTER TABLE "exam_sessions" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("course_id");

ALTER TABLE "exam_sessions" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("user_id");

ALTER TABLE "exam_sessions" ADD FOREIGN KEY ("examiner_id") REFERENCES "users" ("user_id");

ALTER TABLE "session_questions" ADD FOREIGN KEY ("session_id") REFERENCES "exam_sessions" ("session_id");

ALTER TABLE "session_questions" ADD FOREIGN KEY ("question_id") REFERENCES "questions" ("question_id");

ALTER TABLE "exam_turns" ADD FOREIGN KEY ("session_id") REFERENCES "exam_sessions" ("session_id");

ALTER TABLE "exam_turns" ADD FOREIGN KEY ("session_question_id") REFERENCES "session_questions" ("session_question_id");

ALTER TABLE "exam_turns" ADD FOREIGN KEY ("question_id") REFERENCES "questions" ("question_id");

ALTER TABLE "exam_turns" ADD FOREIGN KEY ("parent_turn_id") REFERENCES "exam_turns" ("turn_id");

ALTER TABLE "session_events" ADD FOREIGN KEY ("session_id") REFERENCES "exam_sessions" ("session_id");

ALTER TABLE "session_events" ADD FOREIGN KEY ("turn_id") REFERENCES "exam_turns" ("turn_id");

ALTER TABLE "session_events" ADD FOREIGN KEY ("actor_id") REFERENCES "users" ("user_id");

ALTER TABLE "grade_evaluations" ADD FOREIGN KEY ("session_id") REFERENCES "exam_sessions" ("session_id");

ALTER TABLE "grade_evaluations" ADD FOREIGN KEY ("confirmed_by") REFERENCES "users" ("user_id");

ALTER TABLE "question_grades" ADD FOREIGN KEY ("evaluation_id") REFERENCES "grade_evaluations" ("evaluation_id");

ALTER TABLE "question_grades" ADD FOREIGN KEY ("session_question_id") REFERENCES "session_questions" ("session_question_id");

ALTER TABLE "question_grades" ADD FOREIGN KEY ("question_id") REFERENCES "questions" ("question_id");

ALTER TABLE "question_grades" ADD FOREIGN KEY ("confirmed_by") REFERENCES "users" ("user_id");

ALTER TABLE "criterion_scores" ADD FOREIGN KEY ("question_grade_id") REFERENCES "question_grades" ("question_grade_id");

ALTER TABLE "criterion_scores" ADD FOREIGN KEY ("criterion_id") REFERENCES "rubric_criteria" ("criterion_id");

ALTER TABLE "grade_change_log" ADD FOREIGN KEY ("evaluation_id") REFERENCES "grade_evaluations" ("evaluation_id");

ALTER TABLE "grade_change_log" ADD FOREIGN KEY ("question_grade_id") REFERENCES "question_grades" ("question_grade_id");

ALTER TABLE "grade_change_log" ADD FOREIGN KEY ("criterion_score_id") REFERENCES "criterion_scores" ("criterion_score_id");

ALTER TABLE "grade_change_log" ADD FOREIGN KEY ("changed_by") REFERENCES "users" ("user_id");

-- ----------------------------------------------------------------------------
-- Constraints and indexes that DBML cannot express (03_DATA_MODEL.md §3, §5)
-- ----------------------------------------------------------------------------
ALTER TABLE "rubric_criteria"
  ADD CONSTRAINT "ck_rubric_criteria_scores" CHECK ("max_score" > 0 AND "weight_percent" > 0 AND "weight_percent" <= 100); -- INV-01

ALTER TABLE "exam_turns"
  ADD CONSTRAINT "ck_exam_turns_main_followup" CHECK (
    ("turn_type" = 'MAIN' AND "parent_turn_id" IS NULL AND "followup_index" = 0)
    OR ("turn_type" = 'FOLLOW_UP' AND "parent_turn_id" IS NOT NULL AND "followup_index" > 0)); -- INV-03

CREATE UNIQUE INDEX "ux_exam_turns_one_open_turn" ON "exam_turns" ("session_id")
  WHERE "status" IN ('ASKED', 'PROCESSING'); -- INV-05

ALTER TABLE "criterion_scores"
  ADD CONSTRAINT "ck_criterion_scores_range" CHECK (
    ("ai_score" IS NULL OR ("ai_score" >= 0 AND "ai_score" <= "max_score"))
    AND ("final_score" IS NULL OR ("final_score" >= 0 AND "final_score" <= "max_score"))); -- INV-07

ALTER TABLE "viva_exams"
  ADD CONSTRAINT "ck_viva_exams_config" CHECK (
    "window_end" > "window_start"
    AND "main_question_count" BETWEEN 1 AND 10
    AND "max_followups_per_question" BETWEEN 0 AND 5
    AND "time_limit_per_student_sec" BETWEEN 120 AND 3600
    AND "answer_time_limit_sec" BETWEEN 30 AND 600);

CREATE INDEX "ix_material_chunks_embedding" ON "material_chunks" USING hnsw ("embedding" vector_cosine_ops);

-- Fixed system roles (D14). Ids mirror the Java enum swd392.group6.AIVES.user.Role.
INSERT INTO "roles" ("role_id", "role_name", "role_description") VALUES
  (1, 'ADMIN', 'System administrator with full management access'),
  (2, 'LECTURER', 'Course instructor managing materials, question banks and grading'),
  (3, 'STUDENT', 'Candidate taking viva exam sessions');
