-- Where the current profile picture came from (D37): UPLOAD (the user / an admin) or GOOGLE (adopted when the
-- Google account was linked; removed again on unlink). Existing pictures were uploaded.
ALTER TABLE "users"
  ADD COLUMN "avatar_source" varchar(20) CHECK ("avatar_source" IN ('UPLOAD', 'GOOGLE'));
UPDATE "users" SET "avatar_source" = 'UPLOAD' WHERE "avatar_key" IS NOT NULL;
ALTER TABLE "users"
  ADD CONSTRAINT "ck_users_avatar_source" CHECK (("avatar_key" IS NULL) = ("avatar_source" IS NULL));
