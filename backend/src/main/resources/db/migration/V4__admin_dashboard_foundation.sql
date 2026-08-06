ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS vocabularies (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(100) NOT NULL UNIQUE,
    translation VARCHAR(255) NOT NULL,
    phonetic VARCHAR(100),
    part_of_speech VARCHAR(50) NOT NULL DEFAULT 'Noun',
    definition TEXT NOT NULL,
    example_sentence TEXT,
    example_sentence_b2 TEXT,
    synonyms TEXT,
    level VARCHAR(10) NOT NULL DEFAULT 'B1',
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recognition_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    detected_label VARCHAR(100) NOT NULL,
    confidence REAL NOT NULL,
    engine VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recognition_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expected_label VARCHAR(100) NOT NULL,
    actual_label VARCHAR(100) NOT NULL,
    confidence REAL,
    note TEXT,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolved_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT REFERENCES users(id),
    admin_email VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100),
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);
CREATE INDEX IF NOT EXISTS idx_vocabularies_published ON vocabularies(is_published);
CREATE INDEX IF NOT EXISTS idx_recognition_history_created_at ON recognition_history(created_at);
CREATE INDEX IF NOT EXISTS idx_recognition_reports_resolved ON recognition_reports(is_resolved);
CREATE INDEX IF NOT EXISTS idx_admin_audit_created_at ON admin_audit_logs(created_at);
