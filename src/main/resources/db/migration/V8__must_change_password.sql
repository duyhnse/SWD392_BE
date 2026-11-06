-- V8 (D62): accounts created by someone else (admin form, account CSV, class list CSV) start with a password the
-- owner did not choose — the one in the file or the default below — and must change it at their first login.

ALTER TABLE "users" ADD COLUMN "must_change_password" boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN "users"."must_change_password" IS 'Set when an admin / import created the account; cleared by a password change or reset (D62)';

INSERT INTO "system_settings" ("setting_key", "setting_value", "description") VALUES
  ('account.default_password', '"Aives@2026"',
   'Initial password of accounts created by an admin or an import when no password is given; the user must change it at the first login (D62)')
ON CONFLICT ("setting_key") DO NOTHING;
