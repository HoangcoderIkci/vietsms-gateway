CREATE TABLE api_keys (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_prefix      VARCHAR(8)   NOT NULL UNIQUE,
    key_hash        VARCHAR(60)  NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    owner_email     VARCHAR(128),
    rate_limit_rpm  INT          NOT NULL DEFAULT 10,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at      TIMESTAMP
);

CREATE INDEX idx_api_keys_prefix_active ON api_keys (key_prefix, active);
