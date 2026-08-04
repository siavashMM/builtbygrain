CREATE TABLE rate_limit_buckets (
    policy VARCHAR(80) NOT NULL,
    scope_hash CHAR(64) NOT NULL,
    window_started_epoch_seconds BIGINT NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    expires_at_epoch_seconds BIGINT NOT NULL,
    CONSTRAINT pk_rate_limit_buckets PRIMARY KEY (
        policy,
        scope_hash,
        window_started_epoch_seconds
    ),
    CONSTRAINT ck_rate_limit_bucket_count CHECK (request_count >= 0)
);

CREATE INDEX idx_rate_limit_buckets_expiry
    ON rate_limit_buckets(expires_at_epoch_seconds);
