CREATE TABLE review_cards (
 user_id BIGINT NOT NULL REFERENCES users(id), word VARCHAR(100) NOT NULL,
 due_at BIGINT NOT NULL, interval_days INT NOT NULL DEFAULT 0, ease DOUBLE PRECISION NOT NULL DEFAULT 2.5,
 repetitions INT NOT NULL DEFAULT 0, lapses INT NOT NULL DEFAULT 0, stage VARCHAR(20) NOT NULL DEFAULT 'NEW',
 last_reviewed_at BIGINT, PRIMARY KEY(user_id, word)
);
CREATE TABLE review_events (
 user_id BIGINT NOT NULL REFERENCES users(id), operation_id VARCHAR(36) NOT NULL, word VARCHAR(100) NOT NULL,
 grade VARCHAR(10) NOT NULL, reviewed_at BIGINT NOT NULL, PRIMARY KEY(user_id, operation_id)
);
CREATE INDEX idx_review_events_account_time ON review_events(user_id, reviewed_at);
INSERT INTO review_cards(user_id, word, due_at) SELECT user_id, word, saved_at FROM learning_entries WHERE deleted = FALSE AND saved_at IS NOT NULL;
