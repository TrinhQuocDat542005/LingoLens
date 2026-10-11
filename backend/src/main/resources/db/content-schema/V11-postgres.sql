CREATE UNIQUE INDEX uq_user_active_enrollment ON enrollments(user_id) WHERE status='ACTIVE';
CREATE FUNCTION stable_completed_lesson() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM lesson_progress WHERE lesson_id=OLD.id) AND
    (TG_OP='DELETE' OR NEW.unit_id IS DISTINCT FROM OLD.unit_id OR NEW.position IS DISTINCT FROM OLD.position) THEN
  RAISE EXCEPTION 'Lesson with progress cannot be deleted or moved';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER stable_lesson BEFORE UPDATE OR DELETE ON lessons FOR EACH ROW EXECUTE FUNCTION stable_completed_lesson();
