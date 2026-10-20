-- AIV-29: camera / screen / answer audio are kept this many days after the attempt ended (confirmed grades only).
INSERT INTO "system_settings" ("setting_key", "setting_value", "description") VALUES
  ('retention.recording_days', '"365"', 'Days the recordings and answer audio of a finished, graded attempt are kept (30–3650); transcripts and grades stay')
ON CONFLICT ("setting_key") DO NOTHING;
