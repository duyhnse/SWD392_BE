-- Every STUDENT account has a student code (MSSV): it is how lecturers add students to a buổi thi (D28, D40).
-- Lecturers and admins have none. Existing students without one get a placeholder that an admin should correct.
UPDATE "users" SET "student_code" = 'TMP' || upper(substr(replace("user_id"::text, '-', ''), 1, 12))
WHERE "role_id" = 3 AND "student_code" IS NULL;
ALTER TABLE "users"
  ADD CONSTRAINT "ck_users_student_code_required" CHECK ("role_id" <> 3 OR "student_code" IS NOT NULL);
