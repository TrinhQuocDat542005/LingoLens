CREATE TABLE content_import_receipts (
 pack_id UUID PRIMARY KEY, checksum VARCHAR(64) NOT NULL,
 content_status VARCHAR(20) NOT NULL CHECK(content_status IN ('DEMO','REVIEWED')),
 imported_at TIMESTAMP WITH TIME ZONE NOT NULL
);
ALTER TABLE courses ADD COLUMN content_status VARCHAR(20) NOT NULL CHECK(content_status IN ('DEMO','REVIEWED'));
ALTER TABLE courses ADD COLUMN pack_id UUID NOT NULL;
ALTER TABLE lesson_revisions ADD COLUMN content_status VARCHAR(20) NOT NULL CHECK(content_status IN ('DEMO','REVIEWED'));
ALTER TABLE lesson_revisions ADD COLUMN pack_id UUID NOT NULL;
