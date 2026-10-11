CREATE UNIQUE INDEX uq_lesson_published_revision ON lesson_revisions(lesson_id) WHERE status='PUBLISHED';

CREATE FUNCTION immutable_lesson_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status IN ('PUBLISHED','ARCHIVED') THEN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Published revision cannot be deleted'; END IF;
  IF (to_jsonb(NEW)-'status') IS DISTINCT FROM (to_jsonb(OLD)-'status')
     OR NEW.status NOT IN ('PUBLISHED','ARCHIVED') THEN
   RAISE EXCEPTION 'Published revision is immutable';
  END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_revision BEFORE UPDATE OR DELETE ON lesson_revisions FOR EACH ROW EXECUTE FUNCTION immutable_lesson_revision();

CREATE FUNCTION immutable_lesson_child() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE revision_id UUID; old_revision UUID; new_revision UUID;
BEGIN
 IF TG_TABLE_NAME='question_options' THEN
  SELECT lesson_revision_id INTO old_revision FROM questions WHERE id=(to_jsonb(OLD)->>'question_id')::uuid;
  SELECT lesson_revision_id INTO new_revision FROM questions WHERE id=(to_jsonb(NEW)->>'question_id')::uuid;
 ELSE
  old_revision := (to_jsonb(OLD)->>'lesson_revision_id')::uuid;
  new_revision := (to_jsonb(NEW)->>'lesson_revision_id')::uuid;
 END IF;
 FOREACH revision_id IN ARRAY ARRAY[old_revision,new_revision] LOOP
  IF EXISTS(SELECT 1 FROM lesson_revisions WHERE id=revision_id AND status IN ('PUBLISHED','ARCHIVED')) THEN
   RAISE EXCEPTION 'Published lesson content is immutable';
  END IF;
 END LOOP;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_vocabulary_snapshot BEFORE INSERT OR UPDATE OR DELETE ON lesson_vocabularies FOR EACH ROW EXECUTE FUNCTION immutable_lesson_child();
CREATE TRIGGER immutable_question BEFORE INSERT OR UPDATE OR DELETE ON questions FOR EACH ROW EXECUTE FUNCTION immutable_lesson_child();
CREATE TRIGGER immutable_option BEFORE INSERT OR UPDATE OR DELETE ON question_options FOR EACH ROW EXECUTE FUNCTION immutable_lesson_child();
