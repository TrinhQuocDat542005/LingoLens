-- Additive analytics only. Existing accounts do not consent automatically.
ALTER TABLE learner_settings ADD COLUMN analytics_opt_in BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE learner_settings ADD COLUMN analytics_consent_version VARCHAR(30);
ALTER TABLE learner_settings ADD COLUMN analytics_consent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE learner_settings ADD COLUMN analytics_consent_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE learner_settings ADD CONSTRAINT ck_analytics_consent CHECK (
 analytics_consent_revision >= 0 AND (NOT analytics_opt_in OR (analytics_consent_version IS NOT NULL AND analytics_consent_at IS NOT NULL))
);
CREATE TABLE analytics_events (
 id UUID PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 event_name VARCHAR(40) NOT NULL CHECK(event_name IN ('app_open','lesson_started','lesson_completed','lesson_passed','review_session_completed')),
 session_id UUID, attempt_id UUID,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, received_at TIMESTAMP WITH TIME ZONE NOT NULL,
 app_version VARCHAR(40) NOT NULL, timezone VARCHAR(64) NOT NULL,
 properties JSONB NOT NULL, payload_hash VARCHAR(64) NOT NULL,
 consent_revision BIGINT NOT NULL CHECK(consent_revision>=0),
 CONSTRAINT ck_analytics_source CHECK (
  (event_name IN ('app_open','review_session_completed') AND session_id IS NOT NULL) OR
  (event_name IN ('lesson_started','lesson_completed','lesson_passed') AND attempt_id IS NOT NULL)
 )
);
CREATE INDEX idx_analytics_account_time ON analytics_events(user_id,occurred_at);
CREATE INDEX idx_analytics_received ON analytics_events(received_at);
