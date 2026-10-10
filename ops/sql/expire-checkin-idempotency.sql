-- Manual maintenance, one bounded batch. Never delete non-expired tombstones.
-- Use the configured maintenance DB account; do not place passwords in this file.
DELETE FROM food_checkin_idempotency WHERE expires_at <= CURRENT_TIMESTAMP
ORDER BY expires_at LIMIT 1000;
SELECT ROW_COUNT() AS expired_rows_deleted;
