CREATE TABLE enrollments (
 user_id BIGINT NOT NULL REFERENCES users(id), course_id UUID NOT NULL REFERENCES courses(id),
 status VARCHAR(20) NOT NULL CHECK(status IN ('ACTIVE','PAUSED','COMPLETED')), enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL,
 completed_at TIMESTAMP WITH TIME ZONE, PRIMARY KEY(user_id,course_id)
);
CREATE TABLE lesson_attempts (
 id UUID PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), lesson_revision_id UUID NOT NULL REFERENCES lesson_revisions(id),
 mode VARCHAR(20) NOT NULL CHECK(mode IN ('LESSON','PRACTICE')), score_percent SMALLINT NOT NULL CHECK(score_percent BETWEEN 0 AND 100),
 passed BOOLEAN NOT NULL, submitted_offline BOOLEAN NOT NULL, client_completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
 received_at TIMESTAMP WITH TIME ZONE NOT NULL, request_hash VARCHAR(64) NOT NULL, UNIQUE(id,lesson_revision_id)
);
CREATE TABLE attempt_answers (
 attempt_id UUID NOT NULL, question_id UUID NOT NULL, lesson_revision_id UUID NOT NULL, submitted_answer JSONB NOT NULL,
 is_correct BOOLEAN NOT NULL, awarded_weight SMALLINT NOT NULL CHECK(awarded_weight>=0), PRIMARY KEY(attempt_id,question_id),
 FOREIGN KEY(attempt_id,lesson_revision_id) REFERENCES lesson_attempts(id,lesson_revision_id),
 FOREIGN KEY(question_id,lesson_revision_id) REFERENCES questions(id,lesson_revision_id)
);
CREATE TABLE lesson_progress (
 user_id BIGINT NOT NULL REFERENCES users(id), lesson_id UUID NOT NULL REFERENCES lessons(id),
 best_score_percent SMALLINT NOT NULL CHECK(best_score_percent BETWEEN 0 AND 100), first_passed_at TIMESTAMP WITH TIME ZONE,
 last_attempt_id UUID REFERENCES lesson_attempts(id), updated_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY(user_id,lesson_id)
);
CREATE TABLE xp_events (
 id UUID PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), reason VARCHAR(30) NOT NULL CHECK(reason='LESSON_FIRST_PASS'),
 source_key VARCHAR(100) NOT NULL, amount INT NOT NULL CHECK(amount>0), rules_version SMALLINT NOT NULL CHECK(rules_version>=1),
 awarded_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE(user_id,reason,source_key)
);
CREATE INDEX idx_attempts_account_time ON lesson_attempts(user_id,received_at);
CREATE INDEX idx_xp_account_time ON xp_events(awarded_at,user_id);
