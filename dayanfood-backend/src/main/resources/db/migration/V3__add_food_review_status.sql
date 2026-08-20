ALTER TABLE food
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' AFTER heat,
    ADD COLUMN reviewed_by VARCHAR(50) NULL AFTER review_status,
    ADD COLUMN reviewed_at DATETIME NULL AFTER reviewed_by,
    ADD INDEX idx_food_review_status (review_status);

UPDATE food
SET reviewed_at = created_at
WHERE review_status = 'APPROVED';
