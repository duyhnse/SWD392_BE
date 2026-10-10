-- V7 (D56, D57)
--   * Short display numbers people can say and search: Q-12 (question), R-3 (rubric), D-5 (đề thi),
--     B-7 (buổi thi), L-104 (lượt thi). Numbered in creation order, never reused, unique per table.
--   * A question is always graded with its own rubric (D57): the đề thi / row rubric override columns are no
--     longer read or written. They stay in place (non-destructive); attempts keep their rubric snapshot.

CREATE FUNCTION pg_temp.add_display_no(tbl text, created text) RETURNS void AS $$
BEGIN
  EXECUTE format('CREATE SEQUENCE %I', tbl || '_display_no_seq');
  EXECUTE format('ALTER TABLE %I ADD COLUMN display_no bigint', tbl);
  EXECUTE format('UPDATE %I t SET display_no = n.rn FROM (SELECT ctid AS c, row_number() OVER (ORDER BY %I, ctid) AS rn FROM %I) n WHERE t.ctid = n.c',
                 tbl, created, tbl);
  EXECUTE format('SELECT setval(%L, coalesce((SELECT max(display_no) FROM %I), 0) + 1, false)', tbl || '_display_no_seq', tbl);
  EXECUTE format('ALTER TABLE %I ALTER COLUMN display_no SET DEFAULT nextval(%L), ALTER COLUMN display_no SET NOT NULL',
                 tbl, tbl || '_display_no_seq');
  EXECUTE format('ALTER SEQUENCE %I OWNED BY %I.display_no', tbl || '_display_no_seq', tbl);
  EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I UNIQUE (display_no)', tbl, 'uq_' || tbl || '_display_no');
END $$ LANGUAGE plpgsql;

SELECT pg_temp.add_display_no('questions', 'created_at');
SELECT pg_temp.add_display_no('rubrics', 'created_at');
SELECT pg_temp.add_display_no('exam_templates', 'created_at');
SELECT pg_temp.add_display_no('viva_exams', 'created_at');
SELECT pg_temp.add_display_no('exam_attempts', 'started_at');

COMMENT ON COLUMN "questions"."display_no" IS 'Shown as Q-<n> (D56)';
COMMENT ON COLUMN "rubrics"."display_no" IS 'Shown as R-<n> (D56)';
COMMENT ON COLUMN "exam_templates"."display_no" IS 'Shown as D-<n> (đề thi, D56)';
COMMENT ON COLUMN "viva_exams"."display_no" IS 'Shown as B-<n> (buổi thi, D56)';
COMMENT ON COLUMN "exam_attempts"."display_no" IS 'Shown as L-<n> (lượt thi, D56)';

COMMENT ON COLUMN "exam_templates"."rubric_id" IS 'Unused since V7 (D57): questions are graded with their own rubric';
COMMENT ON COLUMN "exam_template_items"."rubric_id" IS 'Unused since V7 (D57): questions are graded with their own rubric';
COMMENT ON COLUMN "exam_templates"."language" IS 'Always the course language (D57); kept for the attempt runtime';
