-- One-time rollout of identity-bound access tokens: require a fresh login.
-- Preserve token hashes, owners and every other field; keep already revoked timestamps.
-- Flyway runs this once, never on each application startup.
UPDATE refresh_tokens SET revoked_at = CURRENT_TIMESTAMP WHERE revoked_at IS NULL;
