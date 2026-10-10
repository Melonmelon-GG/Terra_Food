CREATE TABLE auth_challenge_redemption (
    generation CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    purpose VARCHAR(24) NOT NULL,
    identity_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    redeemed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_challenge_redemption_time (redeemed_at)
);
-- Keep redemption tombstones while a Redis backup can still be restored. Never delete by a
-- short code TTL alone: an old Redis snapshot must not make an already used challenge valid.
