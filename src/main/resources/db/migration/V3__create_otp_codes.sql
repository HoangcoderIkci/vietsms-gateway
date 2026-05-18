CREATE TABLE otp_codes (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key_id    BIGINT       NOT NULL,
    phone         VARCHAR(16)  NOT NULL,
    code_hash     VARCHAR(60)  NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    max_attempts  INT          NOT NULL DEFAULT 3,
    expires_at    TIMESTAMP    NOT NULL,
    verified_at   TIMESTAMP,
    locked        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_otp_api_key FOREIGN KEY (api_key_id) REFERENCES api_keys(id)
);

CREATE INDEX idx_otp_phone_active ON otp_codes (phone, verified_at, locked);
CREATE INDEX idx_otp_phone_created ON otp_codes (phone, created_at);
