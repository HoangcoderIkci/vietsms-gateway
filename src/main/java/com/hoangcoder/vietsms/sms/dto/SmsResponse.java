package com.hoangcoder.vietsms.sms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsStatus;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmsResponse(
        Long id,
        String to,
        String content,
        SmsStatus status,
        Integer retryCount,
        String errorCode,
        String clientMessageId,
        Instant createdAt,
        Instant sentAt,
        Instant deliveredAt
) {
    public static SmsResponse from(SmsMessage m) {
        return new SmsResponse(
                m.getId(),
                m.getToPhone(),
                m.getContent(),
                m.getStatus(),
                m.getRetryCount(),
                m.getErrorCode(),
                m.getClientMessageId(),
                m.getCreatedAt(),
                m.getSentAt(),
                m.getDeliveredAt()
        );
    }
}
