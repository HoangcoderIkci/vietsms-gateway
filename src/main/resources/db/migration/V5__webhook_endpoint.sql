CREATE TABLE webhook_endpoint (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key_id  BIGINT        NOT NULL,
    url         VARCHAR(2048) NOT NULL,
    secret      VARCHAR(64)   NOT NULL,
    events      VARCHAR(255)  NOT NULL,
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL,
    CONSTRAINT fk_webhook_endpoint_api_key FOREIGN KEY (api_key_id) REFERENCES api_keys(id)
);

CREATE INDEX idx_webhook_endpoint_key ON webhook_endpoint(api_key_id);
