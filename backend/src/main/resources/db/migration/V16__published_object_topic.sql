-- Public camera mapping; existing vocabulary and lesson snapshots are untouched.
INSERT INTO topics(id,slug,title,description,icon_key,display_order,is_published,created_at,updated_at)
SELECT '00000000-0000-4000-8000-000000000100','objects','Đồ vật',
       'Đồ vật quen thuộc: sách, chai và điện thoại.','objects',10,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM topics WHERE slug='objects');

INSERT INTO topic_vocabularies(topic_id,vocabulary_id,display_order)
SELECT t.id,v.id,CASE v.word WHEN 'book' THEN 1 WHEN 'bottle' THEN 2 ELSE 3 END
FROM topics t CROSS JOIN vocabularies v
WHERE t.slug='objects' AND v.word IN ('book','bottle','phone')
AND NOT EXISTS (SELECT 1 FROM topic_vocabularies tv WHERE tv.topic_id=t.id AND tv.vocabulary_id=v.id);
