CREATE INDEX idx_food_daily_visit_user_time
    ON food_daily_visit (user_id, visited_at DESC);
