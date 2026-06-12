package com.hoangcoder.vietsms.webhook;

import java.util.Arrays;

public enum WebhookEventType {

    SMS_SENT("sms.sent"),
    SMS_DELIVERED("sms.delivered"),
    SMS_FAILED("sms.failed"),
    OTP_LOCKED("otp.locked"),
    WEBHOOK_TEST("webhook.test");

    private final String wire;

    WebhookEventType(String wire) {
        this.wire = wire;
    }

    public String getWire() {
        return wire;
    }

    public static WebhookEventType fromWire(String wire) {
        return Arrays.stream(values())
                .filter(e -> e.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown webhook event type: " + wire));
    }
}
