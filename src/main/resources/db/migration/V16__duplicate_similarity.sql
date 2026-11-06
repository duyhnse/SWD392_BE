-- D76: a generated question this close in meaning (embedding cosine) to one already in the course is a repeat.
INSERT INTO "system_settings" ("setting_key", "setting_value", "description") VALUES
  ('ai.rag.duplicate_similarity', '"0.885"', 'AI: a new question at least this similar in meaning (0.8–0.98) to one already in the course counts as a repeat; the AI writes another or drops it')
ON CONFLICT ("setting_key") DO NOTHING;
