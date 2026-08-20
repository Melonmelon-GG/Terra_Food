ALTER TABLE food
    ADD INDEX idx_food_review_location (review_status, latitude, longitude),
    ADD INDEX idx_food_review_id (review_status, id);
