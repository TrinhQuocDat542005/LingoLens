CREATE TABLE learning_entries (
    user_id BIGINT NOT NULL REFERENCES users(id),
    word VARCHAR(100) NOT NULL,
    content TEXT,
    saved_at BIGINT,
    first_learned_at BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, word)
);
CREATE TABLE sync_receipts (
    user_id BIGINT NOT NULL REFERENCES users(id),
    operation_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    operation_kind VARCHAR(30) NOT NULL,
    remote_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, operation_id)
);
