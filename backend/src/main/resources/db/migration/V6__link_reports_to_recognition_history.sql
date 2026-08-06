ALTER TABLE recognition_reports
    ADD COLUMN IF NOT EXISTS recognition_history_id BIGINT;

ALTER TABLE recognition_reports
    ADD CONSTRAINT fk_recognition_reports_history
    FOREIGN KEY (recognition_history_id) REFERENCES recognition_history(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_recognition_reports_history
    ON recognition_reports(recognition_history_id);
