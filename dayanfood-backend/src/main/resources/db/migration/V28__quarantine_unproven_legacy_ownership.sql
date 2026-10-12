ALTER TABLE food ADD COLUMN ownership_status VARCHAR(24) NOT NULL DEFAULT 'VERIFIED';

-- V22 matched reusable usernames. Keep the original relationship for investigation, but do
-- not authorize private ownership from a pre-V22 row until independent evidence is reviewed.
-- New records written by stable-identity code remain VERIFIED; no person is auto-assigned here.
UPDATE food
SET ownership_status = 'LEGACY_UNVERIFIED'
WHERE created_by_user_id IS NOT NULL
  AND created_at <= COALESCE(
      (SELECT MAX(installed_on) FROM flyway_schema_history WHERE version = '22' AND success = 1),
      CURRENT_TIMESTAMP
  );

CREATE TABLE food_ownership_review (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    food_id BIGINT NOT NULL,
    previous_user_id BIGINT NULL,
    retained_user_id BIGINT NULL,
    previous_status VARCHAR(24) NOT NULL,
    resulting_status VARCHAR(24) NOT NULL,
    evidence_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reviewer VARCHAR(120) NOT NULL,
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_food_ownership_review_food (food_id, id)
);
