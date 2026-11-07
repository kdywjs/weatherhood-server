CREATE TABLE IF NOT EXISTS weather_batch (
    grid_x INTEGER NOT NULL,
    grid_y INTEGER NOT NULL,
    source VARCHAR(40) NOT NULL,
    published_at VARCHAR(40) NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    latest_target TIMESTAMP WITH TIME ZONE NOT NULL,
    payload CLOB NOT NULL,
    PRIMARY KEY (grid_x, grid_y, source, published_at)
);
CREATE TABLE IF NOT EXISTS weather_location (
    grid_x INTEGER NOT NULL,
    grid_y INTEGER NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (grid_x, grid_y)
);
CREATE TABLE IF NOT EXISTS kma_usage (
    usage_day DATE PRIMARY KEY,
    calls INTEGER NOT NULL,
    reserved_bytes BIGINT NOT NULL
);
