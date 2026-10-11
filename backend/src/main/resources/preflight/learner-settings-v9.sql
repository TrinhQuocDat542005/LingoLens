-- Run on the target database BEFORE deploying V9, using psql ON_ERROR_STOP.
-- Findings require an operator decision. Never repair or default legacy values here.
BEGIN TRANSACTION READ ONLY;

SELECT id AS user_id,
       target_level,
       daily_goal,
       concat_ws('; ',
           CASE WHEN target_level IS NULL THEN 'TARGET_LEVEL_NULL'
                WHEN target_level NOT IN ('A1', 'B1', 'B2') THEN 'TARGET_LEVEL_UNSUPPORTED' END,
           CASE WHEN daily_goal IS NULL THEN 'DAILY_GOAL_NULL'
                WHEN daily_goal NOT BETWEEN 1 AND 50 THEN 'DAILY_GOAL_OUT_OF_RANGE' END
       ) AS errors
FROM users
WHERE target_level IS NULL
   OR target_level NOT IN ('A1', 'B1', 'B2')
   OR daily_goal IS NULL
   OR daily_goal NOT BETWEEN 1 AND 50
ORDER BY id;

COMMIT;
