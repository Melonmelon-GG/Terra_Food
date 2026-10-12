-- Retain an idempotent request after deletion: an old retry must never republish private history.
ALTER TABLE food_checkin_idempotency DROP FOREIGN KEY fk_checkin_idem_checkin;
ALTER TABLE food_checkin_idempotency ADD CONSTRAINT fk_checkin_idem_checkin
  FOREIGN KEY (checkin_id) REFERENCES food_checkin(id) ON DELETE SET NULL;
-- Merge audit records identify both pre-existing and newly moved relationships.
CREATE TABLE food_tag_merge_relation (
  source_tag_id BIGINT NOT NULL, target_tag_id BIGINT NOT NULL, food_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL, target_existed BOOLEAN NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(source_tag_id,target_tag_id,food_id), INDEX idx_tag_merge_food(food_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE food_tag_governance (id TINYINT PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO food_tag_governance VALUES(1);

-- Repair aliases left on previously merged tags. Cyclic/broken chains are not guessed.
WITH RECURSIVE canonical(source_id,target_id,next_id,status,path,depth) AS (
 SELECT id,id,merged_into_id,status,CAST(CONCAT('/',id,'/') AS CHAR(4096)),0
 FROM food_tag WHERE status='MERGED'
 UNION ALL
 SELECT c.source_id,t.id,t.merged_into_id,t.status,CONCAT(c.path,t.id,'/'),c.depth+1
 FROM canonical c JOIN food_tag t ON t.id=c.next_id
 WHERE c.status='MERGED' AND c.depth<32 AND LOCATE(CONCAT('/',t.id,'/'),c.path)=0
)
UPDATE food_tag_alias a JOIN canonical c ON c.source_id=a.tag_id AND c.status<>'MERGED'
SET a.tag_id=c.target_id;
