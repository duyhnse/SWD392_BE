-- Incremental speech-to-text (D74): ~25 s parts of a long answer are transcribed while the student is still speaking,
-- so at "done" only the last part is left. The full recording is still stored with the turn (evidence).
CREATE TABLE "answer_parts" (
  "turn_id" uuid NOT NULL REFERENCES "exam_turns" ("turn_id"),
  "part_index" int NOT NULL CHECK ("part_index" BETWEEN 0 AND 60),
  "status" varchar(20) NOT NULL DEFAULT 'PENDING' CHECK ("status" IN ('PENDING', 'DONE', 'FAILED')),
  "transcript" text,
  "error" text,
  "size_bytes" int NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "done_at" timestamptz,
  PRIMARY KEY ("turn_id", "part_index")
);
COMMENT ON TABLE "answer_parts" IS 'Transcripts of the ~25 s parts of an answer sent while recording (D74); never graded on their own';
