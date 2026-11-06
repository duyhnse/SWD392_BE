-- One active login per account (D38). Each JWT carries its session id ("sid"); every request checks
-- that the session is still open. A new login closes the user's other sessions.
CREATE TABLE "user_sessions" (
  "session_id" uuid PRIMARY KEY,
  "user_id" uuid NOT NULL REFERENCES "users" ("user_id") ON DELETE CASCADE,
  "device_id" varchar(64),
  "user_agent" varchar(300),
  "ip_address" varchar(45),
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "last_seen_at" timestamptz NOT NULL DEFAULT (now()),
  "expires_at" timestamptz NOT NULL,
  "revoked_at" timestamptz,
  "revoke_reason" varchar(30) CHECK ("revoke_reason" IN ('LOGOUT', 'REPLACED', 'PASSWORD_CHANGED', 'DEACTIVATED'))
);
CREATE INDEX "ix_user_sessions_open" ON "user_sessions" ("user_id") WHERE "revoked_at" IS NULL;
