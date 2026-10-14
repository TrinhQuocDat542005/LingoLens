CREATE UNIQUE INDEX uq_analytics_attempt ON analytics_events(user_id,event_name,attempt_id)
 WHERE event_name IN ('lesson_started','lesson_completed','lesson_passed');
CREATE UNIQUE INDEX uq_analytics_session ON analytics_events(user_id,event_name,session_id)
 WHERE event_name IN ('app_open','review_session_completed');
