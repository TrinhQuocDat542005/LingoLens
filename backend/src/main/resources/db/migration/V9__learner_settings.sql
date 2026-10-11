-- Expand only: preserve every valid legacy value; reject invalid rows rather than repair.
-- PostgreSQL executes this migration atomically, including failed backfills.
CREATE TABLE learner_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    target_level VARCHAR(10) NOT NULL,
    learning_goal VARCHAR(30) NOT NULL DEFAULT 'UNSET',
    daily_new_word_goal INT NOT NULL DEFAULT 5,
    daily_review_goal SMALLINT NOT NULL DEFAULT 10,
    timezone VARCHAR(64),
    onboarding_completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT learner_settings_target_level_must_be_A1_B1_B2 CHECK (target_level IN ('A1','B1','B2')),
    CONSTRAINT learner_settings_daily_new_word_goal_must_be_1_to_50 CHECK (daily_new_word_goal BETWEEN 1 AND 50),
    CONSTRAINT learner_settings_daily_review_goal_must_be_5_to_100 CHECK (daily_review_goal BETWEEN 5 AND 100)
);

INSERT INTO learner_settings(user_id,target_level,daily_new_word_goal)
SELECT id,target_level,daily_goal FROM users;

-- New accounts only. No UPDATE of existing users.
ALTER TABLE users ALTER COLUMN target_level SET DEFAULT 'A1';
