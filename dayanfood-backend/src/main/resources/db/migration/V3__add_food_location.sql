ALTER TABLE food
    ADD COLUMN latitude DECIMAL(10, 7) NULL AFTER region_id,
    ADD COLUMN longitude DECIMAL(10, 7) NULL AFTER latitude;

UPDATE food SET latitude = 30.5728000, longitude = 104.0668000 WHERE id = 1;
UPDATE food SET latitude = 23.3541000, longitude = 116.6820000 WHERE id = 2;
UPDATE food SET latitude = 31.2989000, longitude = 120.5853000 WHERE id = 3;
UPDATE food SET latitude = 34.3416000, longitude = 108.9398000 WHERE id = 4;
