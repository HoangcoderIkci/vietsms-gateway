CREATE TABLE webhook_delivery (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    endpoint_id    BIGINT       NOT NULL,
    event_type     VARCHAR(32)  NOT NULL,
    payload        CLOB         NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    next_retry_at  TIMESTAMP,
    last_error     VARCHAR(512),
    created_at     TIMESTAMP    NOT NULL,
    delivered_at   TIMESTAMP,
    CONSTRAINT fk_webhook_delivery_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(id)
);

CREATE INDEX idx_webhook_delivery_due ON webhook_delivery(status, next_retry_at);
