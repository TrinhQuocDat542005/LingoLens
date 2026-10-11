CREATE TABLE topics (
 id UUID PRIMARY KEY, slug VARCHAR(80) NOT NULL UNIQUE, title VARCHAR(120) NOT NULL,
 description TEXT NOT NULL, icon_key VARCHAR(50) NOT NULL, display_order INT NOT NULL CHECK(display_order>=0),
 is_published BOOLEAN NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE topic_vocabularies (
 topic_id UUID NOT NULL REFERENCES topics(id), vocabulary_id BIGINT NOT NULL REFERENCES vocabularies(id),
 display_order INT NOT NULL CHECK(display_order>=0), PRIMARY KEY(topic_id,vocabulary_id)
);
CREATE TABLE courses (
 id UUID PRIMARY KEY, code VARCHAR(60) NOT NULL UNIQUE, title VARCHAR(150) NOT NULL, description TEXT NOT NULL,
 level VARCHAR(10) NOT NULL, status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','PUBLISHED','ARCHIVED')),
 published_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE course_units (
 id UUID PRIMARY KEY, course_id UUID NOT NULL REFERENCES courses(id), topic_id UUID NOT NULL REFERENCES topics(id),
 title VARCHAR(150) NOT NULL, position INT NOT NULL CHECK(position>=1), UNIQUE(course_id,position)
);
CREATE TABLE lessons (
 id UUID PRIMARY KEY, unit_id UUID NOT NULL REFERENCES course_units(id), title VARCHAR(150) NOT NULL,
 position INT NOT NULL CHECK(position>=1), estimated_minutes SMALLINT NOT NULL CHECK(estimated_minutes BETWEEN 1 AND 30), UNIQUE(unit_id,position)
);
CREATE TABLE lesson_revisions (
 id UUID PRIMARY KEY, lesson_id UUID NOT NULL REFERENCES lessons(id), revision_no INT NOT NULL CHECK(revision_no>=1),
 introduction TEXT NOT NULL, sentence_patterns JSONB NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','PUBLISHED','ARCHIVED')),
 published_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(lesson_id,revision_no)
);
CREATE TABLE lesson_vocabularies (
 lesson_revision_id UUID NOT NULL REFERENCES lesson_revisions(id), vocabulary_id BIGINT NOT NULL REFERENCES vocabularies(id),
 position INT NOT NULL CHECK(position>=1), content_snapshot JSONB NOT NULL,
 PRIMARY KEY(lesson_revision_id,vocabulary_id), UNIQUE(lesson_revision_id,position)
);
CREATE TABLE questions (
 id UUID PRIMARY KEY, lesson_revision_id UUID NOT NULL REFERENCES lesson_revisions(id),
 type VARCHAR(20) NOT NULL CHECK(type IN ('MEANING_CHOICE','LISTEN_CHOICE','FILL_GAP')),
 prompt TEXT NOT NULL, speech_text TEXT, explanation TEXT NOT NULL, answer_key JSONB NOT NULL,
 position INT NOT NULL CHECK(position>=1), weight SMALLINT NOT NULL CHECK(weight BETWEEN 1 AND 10),
 UNIQUE(id,lesson_revision_id), UNIQUE(lesson_revision_id,position)
);
CREATE TABLE question_options (
 question_id UUID NOT NULL REFERENCES questions(id), option_key VARCHAR(8) NOT NULL, label TEXT NOT NULL,
 position SMALLINT NOT NULL CHECK(position>=1), PRIMARY KEY(question_id,option_key), UNIQUE(question_id,position)
);
